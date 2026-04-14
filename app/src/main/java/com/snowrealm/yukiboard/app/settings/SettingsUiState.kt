package com.snowrealm.yukiboard.app.settings

import com.snowrealm.yukiboard.auth.SnowRealmAuthClient
import com.snowrealm.yukiboard.claw.provider.AIProviderType
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Sprint 1 的設定頁狀態骨架。
 *
 * 先聚焦跨 section 的共享狀態，避免各區塊直接互相讀寫。
 */
data class SettingsUiState(
    val settingsLanguageCode: String = "zh",
    val isLoggedIn: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val selectedProvider: AIProviderType = AIProviderType.SNOWREALM,
    val currentTheme: KeyboardTheme = KeyboardTheme.default(),
    val isCloudSyncEnabled: Boolean = false,
    val isSyncInProgress: Boolean = false,
    val syncMessage: String? = null,
    val warningBanner: String? = null,
    val isSessionVerified: Boolean = false,
    val isInsightConnected: Boolean = false,
    val verifiedDisplayName: String? = null,
    val userPlan: SnowRealmAuthClient.UserPlan? = null,
) {
    companion object {
        fun initial(
            settingsLanguageCode: String = "zh",
            selectedProvider: AIProviderType = AIProviderType.SNOWREALM,
            currentTheme: KeyboardTheme = KeyboardTheme.default(),
        ): SettingsUiState = SettingsUiState(
            settingsLanguageCode = settingsLanguageCode,
            selectedProvider = selectedProvider,
            currentTheme = currentTheme,
        )

        fun fromUser(
            user: SnowRealmAuthClient.UserProfile?,
            settingsLanguageCode: String,
            selectedProvider: AIProviderType,
            currentTheme: KeyboardTheme,
            isCloudSyncEnabled: Boolean,
        ): SettingsUiState = SettingsUiState(
            settingsLanguageCode = settingsLanguageCode,
            isLoggedIn = user != null,
            displayName = user?.displayName,
            email = user?.email,
            selectedProvider = selectedProvider,
            currentTheme = currentTheme,
            isCloudSyncEnabled = isCloudSyncEnabled,
        )
    }
}
