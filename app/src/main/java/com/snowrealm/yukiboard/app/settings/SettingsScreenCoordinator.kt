package com.snowrealm.yukiboard.app.settings

import android.content.Context
import com.snowrealm.yukiboard.app.session.UserSessionRepository
import com.snowrealm.yukiboard.claw.provider.AIProviderManager
import com.snowrealm.yukiboard.data.CloudSyncManager
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sprint 1 的設定頁協調器骨架。
 *
 * 目前先承接共享狀態讀取與刷新，不直接重寫既有 section UI。
 */
class SettingsScreenCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = YukiPreferences(appContext)
    private val themeManager = ThemeManager.getInstance(appContext)
    private val sessionRepository = UserSessionRepository(appContext)
    private val providerManager = AIProviderManager.getInstance(appContext)
    private val cloudSyncManager = CloudSyncManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = buildState()
    }

    /**
     * 非同步驗證 Insight session（只做一次 fetchMe 網路呼叫）。
     *
     * 收斂原本 AuthSettingsSection / SyncSettingsSection 各自獨立的 fetchMe()，
     * 統一由 coordinator 發起，結果寫入 uiState，sections 透過 onSessionVerified 接收。
     */
    suspend fun verifySessionAsync() {
        val verified = sessionRepository.verifySession()
        _uiState.update {
            it.copy(
                isSessionVerified = true,
                isInsightConnected = verified.isConnected,
                isLoggedIn = verified.isConnected || verified.localIsLoggedIn,
                verifiedDisplayName = verified.displayName,
                userPlan = verified.plan,
            )
        }
    }

    fun onSyncStarted() {
        _uiState.update { it.copy(isSyncInProgress = true, syncMessage = null) }
    }

    fun onSyncFinished(message: String?) {
        _uiState.update {
            it.copy(
                isSyncInProgress = false,
                syncMessage = message,
            )
        }
    }

    fun getApiKey(providerType: com.snowrealm.yukiboard.claw.provider.AIProviderType): String? =
        sessionRepository.getApiKey(providerType)

    fun onWarning(message: String?) {
        _uiState.update { it.copy(warningBanner = message) }
    }

    private fun buildState(): SettingsUiState {
        val session = sessionRepository.getSnapshot()
        return SettingsUiState.fromUser(
            user = session.user,
            settingsLanguageCode = prefs.settingsLanguage,
            selectedProvider = providerManager.getSelectedProviderType(),
            currentTheme = themeManager.currentTheme,
            isCloudSyncEnabled = cloudSyncManager.isSyncEnabled,
        )
    }
}
