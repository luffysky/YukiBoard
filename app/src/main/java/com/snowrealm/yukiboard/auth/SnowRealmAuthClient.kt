package com.snowrealm.yukiboard.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.snowrealm.yukiboard.BuildConfig
import com.snowrealm.yukiboard.app.net.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

    private val prefs = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext, "yukiboard_auth", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore 損壞：刪除舊檔案後重建，若仍失敗則 fallback 普通 prefs
        android.util.Log.w("SnowRealmAuth", "EncryptedPrefs failed, rebuilding", e)
        try {
            appContext.getSharedPreferences("yukiboard_auth", Context.MODE_PRIVATE)
                .edit().clear().apply()
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext, "yukiboard_auth", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences("yukiboard_auth_fallback", Context.MODE_PRIVATE)
        }
    }

    private val client = AppHttpClient.newBuilder(AppHttpClient.Profile.STANDARD).build()

    // ─────────────── 登入狀態 ───────────────

    val isLoggedIn: Boolean get() = getToken() != null
    val currentUserId: String? get() = prefs.getString(KEY_USER_ID, null)
    val currentEmail: String? get() = prefs.getString(KEY_EMAIL, null)
    val currentDisplayName: String? get() = prefs.getString(KEY_DISPLAY_NAME, null)

    // ─────────────── 登入 API ───────────────

    /** Email + 密碼登入 */
    suspend fun loginWithEmail(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("username", email)
                put("password", password)
            }
            postAuth("/api/auth/login", body, email)
        }

    /** 第三方 OAuth token 交換（LINE / Google） */
    suspend fun loginWithOAuth(provider: String, oauthToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("provider", provider)
                put("token", oauthToken)
            }
            postAuth("/api/auth/oauth", body)
        }

    /** 發送驗證碼（註冊前必須） */
    suspend fun sendVerificationCode(email: String): SendCodeResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("email", email)
                put("purpose", "register")
            }
            val request = Request.Builder()
                .url("$baseUrl/api/auth/send-code")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext SendCodeResult.Error("空回應")
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        json.decodeFromString<ErrorResponse>(responseBody).error
                    } catch (_: Exception) { null }
                    return@withContext SendCodeResult.Error(errorMsg ?: "發送失敗 (${response.code})")
                }
                val result = json.decodeFromString<SendCodeResponse>(responseBody)
                SendCodeResult.Success(result.codeId)
            } catch (e: Exception) {
                SendCodeResult.Error("網路錯誤: ${e.message}")
            }
        }

    /** 註冊新帳號（需先 sendVerificationCode） */
    suspend fun register(email: String, password: String, name: String, codeId: String, code: String): AuthResult =
        withContext(Dispatchers.IO) {
            val orgName = name.ifBlank { email.substringBefore("@") }
            val body = buildJsonObject {
                put("email", email)
                put("password", password)
                put("displayName", name)
                put("orgName", orgName)
                put("codeId", codeId)
                put("code", code)
            }
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
                displayName = me.displayName.ifBlank { me.userName },
                role = me.role,
                tenantId = me.tenantId,
                avatarUrl = me.avatarUrl,
                plan = parsePlan(me.plan.ifBlank { me.role })
            )
            // 儲存使用者資料
            prefs.edit()
                .putString(KEY_USER_ID, user.id)
                .putString(KEY_EMAIL, user.email)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .putString(KEY_AVATAR_URL, user.avatarUrl)
                .putString(KEY_PLAN, me.plan)
                .putString(KEY_ROLE, user.role)
                .putString("tenant_id", user.tenantId)
                .apply()
            user
        } catch (e: Exception) {
            android.util.Log.e("AuthClient", "fetchMe failed", e)
            null
        }
    }

    /** 登出 */
    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_PLAN)
            .remove(KEY_ROLE)
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

    private fun postAuth(path: String, body: kotlinx.serialization.json.JsonObject, loginEmail: String = ""): AuthResult {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
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

            // 先存 token
            val tenantId = loginResponse.user?.tenantId ?: loginResponse.slug ?: ""
            val userId = loginResponse.user?.id ?: ""
            prefs.edit()
                .putString(KEY_TOKEN, token ?: "")
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, loginEmail)
                .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
                .apply()

            // 用 token 拿完整用戶資料（displayName 只有 /me 有）
            val me = if (!token.isNullOrBlank()) {
                try {
                    val meReq = Request.Builder()
                        .url("$baseUrl/api/auth/me")
                        .header("Cookie", "admin_session=$token")
                        .get().build()
                    val meResp = client.newCall(meReq).execute()
                    if (meResp.isSuccessful) {
                        val meBody = meResp.body?.string()
                        if (meBody != null) json.decodeFromString<MeResponse>(meBody) else null
                    } else null
                } catch (_: Exception) { null }
            } else null

            val user = UserProfile(
                id = me?.userId ?: userId,
                email = me?.userName ?: loginEmail,
                displayName = me?.displayName?.ifBlank { null } ?: me?.userName ?: loginEmail,
                role = me?.role ?: loginResponse.user?.role ?: "",
                tenantId = me?.tenantId ?: tenantId,
                avatarUrl = me?.avatarUrl ?: "",
                plan = parsePlan(me?.plan ?: me?.role)
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
            .putString(KEY_AVATAR_URL, user.avatarUrl)
            .putString(KEY_PLAN, user.plan.name.lowercase())
            .putString(KEY_ROLE, user.role)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + TOKEN_LIFETIME_MS)
            .apply()
    }

    // ─────────────── 資料模型 ───────────────

    sealed class AuthResult {
        data class Success(val user: UserProfile) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    sealed class SendCodeResult {
        data class Success(val codeId: String) : SendCodeResult()
        data class Error(val message: String) : SendCodeResult()
    }

    @Serializable
    data class ErrorResponse(val error: String = "")

    @Serializable
    data class SendCodeResponse(
        val success: Boolean = false,
        val codeId: String = "",
        val channel: String = ""
    )

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
        val displayName: String = "",
        val role: String = "",
        val tenantId: String = "",
        val tenantName: String = "",
        val adminTitle: String = "",
        val avatarUrl: String = "",
        val plan: String = ""
    )

    enum class UserPlan {
        FREE, PLUS, PRO, ENTERPRISE
    }

    @Serializable
    data class UserProfile(
        val id: String,
        val email: String,
        val displayName: String = "",
        val role: String = "",
        val tenantId: String = "",
        val zCoinBalance: Int = 0,
        val avatarUrl: String = "",
        val plan: UserPlan = UserPlan.FREE
    )

    /** 從 prefs 取得當前登入使用者資料 */
    val currentUser: UserProfile?
        get() {
            prefs.getString(KEY_TOKEN, null) ?: return null
            return UserProfile(
                id = prefs.getString(KEY_USER_ID, "") ?: "",
                email = prefs.getString(KEY_EMAIL, "") ?: "",
                displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
                avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: "",
                role = prefs.getString(KEY_ROLE, "") ?: "",
                tenantId = prefs.getString("tenant_id", "") ?: "",
                plan = parsePlan(prefs.getString(KEY_PLAN, null))
            )
        }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_PLAN = "user_plan"
        private const val KEY_ROLE = "user_role"
        private const val TOKEN_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

        fun parsePlan(raw: String?): UserPlan = when (raw?.lowercase()) {
            "plus" -> UserPlan.PLUS
            "pro" -> UserPlan.PRO
            "enterprise" -> UserPlan.ENTERPRISE
            else -> UserPlan.FREE
        }

        @Volatile
        private var instance: SnowRealmAuthClient? = null

        fun getInstance(context: Context): SnowRealmAuthClient {
            return instance ?: synchronized(this) {
                instance ?: SnowRealmAuthClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
