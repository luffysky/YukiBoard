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
            val body = """{"email":"$email","password":"$password"}"""
            postAuth("/api/auth/login", body)
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
            .header("Authorization", "Bearer $token")
            .get().build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 401) logout()
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            json.decodeFromString<UserProfile>(responseBody)
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

    private fun postAuth(path: String, body: String): AuthResult {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return AuthResult.Error("空回應")

            if (!response.isSuccessful) {
                return AuthResult.Error("登入失敗 (${response.code})")
            }

            val authResponse = json.decodeFromString<AuthResponse>(responseBody)
            saveSession(authResponse)
            AuthResult.Success(authResponse.user)
        } catch (e: Exception) {
            AuthResult.Error("網路錯誤: ${e.message}")
        }
    }

    private fun saveSession(auth: AuthResponse) {
        prefs.edit()
            .putString(KEY_TOKEN, auth.token)
            .putString(KEY_USER_ID, auth.user.id)
            .putString(KEY_EMAIL, auth.user.email)
            .putString(KEY_DISPLAY_NAME, auth.user.displayName)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
            .apply()
    }

    // ─────────────── 資料模型 ───────────────

    sealed class AuthResult {
        data class Success(val user: UserProfile) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    @Serializable
    data class AuthResponse(val token: String, val user: UserProfile)

    @Serializable
    data class UserProfile(
        val id: String,
        val email: String,
        val displayName: String = "",
        val avatarUrl: String = "",
        val zCoinBalance: Int = 0
    )

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val TOKEN_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
}
