package com.snowrealm.yukiboard.app.session

import android.content.Context
import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.claw.provider.AIProviderType

/**
 * Sprint 1 的 session 讀取邊界。
 *
 * 先收斂目前散在 auth / provider manager 的 session 讀取，
 * 後續再慢慢把寫入與事件更新也搬進來。
 */
class UserSessionRepository(context: Context) {

    private val appContext = context.applicationContext
    private val authClient = SnowRealmAuthClient.getInstance(appContext)
    private val providerManager = AIProviderManager.getInstance(appContext)

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

    fun getApiKey(providerType: AIProviderType): String? = providerManager.getApiKey(providerType)
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
