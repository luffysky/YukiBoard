package com.snowrealm.yukiboard.app.session

import android.content.Context
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.claw.provider.AIProvider
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.provider.AIProviderType

/**
 * Session / Auth / Provider 狀態的統一邊界。
 *
 * Sprint 1：收斂散在 auth / provider manager 的 **讀取** 入口。
 * Sprint 7-2：擴 **寫入** API，讓本 Repository 成為 session / provider 狀態的單一寫入 owner，
 *             降低 callsite 直接 reach into `SnowRealmAuthClient.getInstance` / `AIProviderManager.getInstance`。
 *
 * 原則：
 * - 所有寫入皆 delegate 給底層 owner（authClient / providerManager），Repository 不額外存 state
 * - 不持有 `InviteCodeManager`（S7-2 禁止 singleton 化；各 caller 仍自行 `new`）
 * - 不包裝 `CloudSyncManager` / `InsightApiClient`（gateway 拆分是 S7-3 題）
 * - Token 與 userId 以 pass-through 方式提供給 gateway caller，避免它們直接 reach into authClient
 */
class UserSessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val authClient = SnowRealmAuthClient.getInstance(appContext)
    private val providerManager = AIProviderManager.getInstance(appContext)

    // ─── Read: snapshot / status ──────────────────────────────────────────

    fun getSnapshot(): UserSessionSnapshot {
        val user = authClient.currentUser
        val providerType = providerManager.getSelectedProviderType()
        return UserSessionSnapshot(
            isLoggedIn = authClient.isLoggedIn,
            user = user,
            authToken = authClient.getToken(),
            selectedProvider = providerType,
            selectedModel = providerManager.getSelectedModel(providerType),
            hasOwnApiKey = !providerManager.getApiKey(providerType).isNullOrBlank(),
        )
    }

    /**
     * 非同步驗證 session（呼叫 Insight API fetchMe）。
     *
     * 收斂原本散在 AuthSettingsSection / SyncSettingsSection 各自獨立的 fetchMe() 呼叫，
     * 只做一次網路驗證，回傳驗證結果。
     */
    suspend fun verifySession(): VerifiedSession {
        val me = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            authClient.fetchMe()
        }
        return VerifiedSession(
            isVerified = true,
            isConnected = me != null,
            displayName = me?.displayName?.ifBlank { me.email },
            email = me?.email,
            plan = me?.plan,
            localIsLoggedIn = authClient.isLoggedIn,
        )
    }

    fun isLoggedIn(): Boolean = authClient.isLoggedIn

    fun getCurrentUser(): SnowRealmAuthClient.UserProfile? = authClient.currentUser

    fun getSelectedProvider(): AIProviderType = providerManager.getSelectedProviderType()

    fun getSelectedModel(type: AIProviderType): String = providerManager.getSelectedModel(type)

    fun getApiKey(providerType: AIProviderType): String? = providerManager.getApiKey(providerType)

    fun hasValidProviderConfig(): Boolean = providerManager.hasValidConfig()

    fun getCurrentProvider(): AIProvider = providerManager.getCurrentProvider()

    // ─── Read: token / userId pass-through（供 gateway / sync caller 使用） ─

    val currentUserId: String? get() = authClient.currentUserId

    fun getAuthToken(): String? = authClient.getToken()

    /**
     * Pass-through 到 `authClient.refreshIfNeeded()`。僅供 gateway caller（`InsightApiClient`）
     * 的 401 retry 路徑使用，避免 gateway 直達 singleton。
     *
     * 注意：`refreshIfNeeded()` 的內部仍會呼叫 `fetchMe()`；本 Sprint 7-3 不整併此路徑，
     * 留給 Sprint 8 bugfix 處理（與 R-1 / R-6 同源）。
     */
    suspend fun refreshSession(): Boolean = authClient.refreshIfNeeded()

    /**
     * 取得「已驗證」的 userId。若本地 `currentUserId` 存在直接回；否則打一次 `fetchMe()`
     * 補齊並回傳。僅供 gateway caller（`CloudSyncManager`）使用，避免它直接 reach-in
     * `SnowRealmAuthClient.fetchMe()`。
     *
     * 注意：此方法會觸發一次 `/api/auth/me`，是 S7-0 audit R-1 衍生路徑的一部分，
     * Sprint 8 將整併 `SettingsScreenCoordinator.verifySessionAsync` 與此 fetchMe 為單一呼叫。
     */
    suspend fun resolveVerifiedUserId(): String? {
        authClient.currentUserId?.let { return it }
        if (!authClient.isLoggedIn) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            authClient.fetchMe()?.id ?: authClient.currentUserId
        }
    }

    // ─── Write: provider / api key / model（統一進入點） ───────────────────

    /** 切換使用中的 AI Provider。底層仍由 `AIProviderManager` 寫入 secure prefs。 */
    fun saveProviderSelection(type: AIProviderType) {
        providerManager.setSelectedProvider(type)
    }

    /** 儲存特定 Provider 的 API Key。會順便 invalidate 該 provider 的記憶體 cache。 */
    fun saveApiKey(type: AIProviderType, key: String) {
        providerManager.saveApiKey(type, key)
    }

    /** 儲存特定 Provider 的選定 model。 */
    fun saveSelectedModel(type: AIProviderType, model: String) {
        providerManager.setSelectedModel(type, model)
    }

    // ─── Write: session lifecycle ─────────────────────────────────────────

    /**
     * 清除本地 session（登出）。底層呼叫 `authClient.logout()`；
     * Provider 選擇與 API Key 不會被清除（那屬 provider state，與 auth session 是不同 owner）。
     */
    fun clearSession() {
        authClient.logout()
    }
}

data class UserSessionSnapshot(
    val isLoggedIn: Boolean,
    val user: SnowRealmAuthClient.UserProfile?,
    val authToken: String?,
    val selectedProvider: AIProviderType,
    val selectedModel: String,
    val hasOwnApiKey: Boolean,
)

data class VerifiedSession(
    val isVerified: Boolean,
    val isConnected: Boolean,
    val displayName: String?,
    val email: String?,
    val plan: SnowRealmAuthClient.UserPlan?,
    val localIsLoggedIn: Boolean,
)
