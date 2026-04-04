package com.snowrealm.yukiboard.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.snowrealm.yukiboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * SnowRealm 帳號系統客戶端。
 *
 * 三階段 Auth：
 * 1. Guest（自帶 API Key）
 * 2. InviteCode（免費額度）
 * 3. SnowRealmAccount（Z幣 + 完整功能）
 *
 * JWT 存於 EncryptedSharedPreferences。
 * API 基底：Insight Engine /api/auth
 */
class SnowRealmAuthClient(context: Context) {

    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.INSIGHT_API_BASE_URL.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext, "yukiboard_auth", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ─────────────── 登入狀態 ───────────────

    val isLoggedIn: Boolean get() = getToken() != null
    val currentUserId: String? get() = prefs.getString(KEY_USER_ID, null)
    val currentEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val currentDisplayName: String? get() = prefs.getString(KEY_DISPLAY_NAME, null)

    // ─────────────── 登入 API ───────────────

    /** Email + 密碼登入 */
    suspend fun loginWithEmail(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = """{"username":"$email","password":"$password"}"""
            postAuth("/api/auth/login", body, email)
        }

    /** 第三方 OAuth token 交換（LINE / Google） */
    suspend fun loginWithOAuth(provider: String, oauthToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = """{"provider":"$provider","token":"$oauthToken"}"""
            postAuth("/api/auth/oauth", body)
        }

    /** 註冊新帳號 */
    suspend fun register(email: String, password: String, name: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = """{"email":"$email","password":"$password","displayName":"$name"}"""
            postAuth("/api/auth/register", body)
        }

    /** 取得目前使用者資料（驗證 token 有效性） */
    suspend fun fetchMe(): UserProfile? = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext null
        val request = Request.Builder()
            .url("$baseUrl/api/auth/me")
            .header("Cookie", "admin_session=$token")
            .get().build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 401) logout()
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            val me = json.decodeFromString<MeResponse>(responseBody)
            val user = UserProfile(
                id = me.userId,
                email = me.userName,
                displayName = me.userName,
                role = me.role,
                tenantId = me.tenantId
            )
            // 儲存使用者資料
            prefs.edit()
                .putString(KEY_USER_ID, user.id)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .apply()
            user
        } catch (_: Exception) { null }
    }

    /** 登出 */
    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    /** Token 是否過期 */
    fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() > expiry
    }

    /** 嘗試自動續期（如果快過期） */
    suspend fun refreshIfNeeded(): Boolean {
        if (!isLoggedIn) return false
        if (!isTokenExpired()) return true
        val me = fetchMe()
        return me != null
    }

    // ─────────────── 內部 ───────────────

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** WebView OAuth 登入後直接存 token（後續呼叫 fetchMe 取用戶資料） */
    fun saveTokenDirectly(token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
            .apply()
    }

    private fun postAuth(path: String, body: String, loginEmail: String = ""): AuthResult {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return AuthResult.Error("空回應")

            if (!response.isSuccessful) {
                // 嘗試解析伺服器錯誤訊息
                val errorMsg = try {
                    json.decodeFromString<ErrorResponse>(responseBody).error
                } catch (_: Exception) { null }
                return AuthResult.Error(errorMsg ?: "登入失敗 (${response.code})")
            }

            // 從 Set-Cookie 取 JWT token
            val token = response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("admin_session=") }
                ?.substringAfter("admin_session=")
                ?.substringBefore(";")

            // 解析回應 body
            val loginResponse = json.decodeFromString<LoginResponse>(responseBody)

            val user = UserProfile(
                id = loginResponse.user?.id ?: "",
                email = loginEmail,
                displayName = loginResponse.user?.displayName ?: loginEmail,
                role = loginResponse.user?.role ?: "",
                tenantId = loginResponse.user?.tenantId ?: loginResponse.slug ?: ""
            )

            saveSession(token, user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("網路錯誤: ${e.message}")
        }
    }

    private fun saveSession(token: String?, user: UserProfile) {
        prefs.edit()
            .putString(KEY_TOKEN, token ?: "")
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
            .apply()
    }

    // ─────────────── 資料模型 ───────────────

    sealed class AuthResult {
        data class Success(val user: UserProfile) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    @Serializable
    data class ErrorResponse(val error: String = "")

    @Serializable
    data class LoginResponse(
        val success: Boolean = false,
        val slug: String? = null,
        val user: LoginUser? = null
    )

    @Serializable
    data class LoginUser(
        val id: String = "",
        val role: String = "",
        val tenantId: String = "",
        val displayName: String = ""
    )

    @Serializable
    data class MeResponse(
        val userId: String = "",
        val userName: String = "",
        val role: String = "",
        val tenantId: String = "",
        val tenantName: String = "",
        val adminTitle: String = ""
    )

    @Serializable
    data class UserProfile(
        val id: String,
        val email: String,
        val displayName: String = "",
        val role: String = "",
        val tenantId: String = "",
        val zCoinBalance: Int = 0
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val TOKEN_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

        @Volatile
        private var instance: SnowRealmAuthClient? = null

        fun getInstance(context: Context): SnowRealmAuthClient {
            return instance ?: synchronized(this) {
                instance ?: SnowRealmAuthClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
