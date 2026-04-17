package com.snowrealm.yukiboard.app.ime

import android.content.Context
import com.snowrealm.yukiboard.claw.ClawClient
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode
import com.snowrealm.yukiboard.ui.panel.ClawPanelView
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * Sprint 6-6：CLAW panel 最小邊界 binder。
 *
 * 只負責 [ClawPanelView] 建立、theme / clawClient / prefs 實例注入、
 * 初始 mode 呼叫、3 個 provider（inputText / appPackage / keyboardMode）
 * 與 3 個 callback（onSuggestionSelected / onClose / onLockTapped）wiring。
 *
 * 刻意不做：
 * - 不直接 reach into `ClawClient` / `AIProviderManager` / `UserSessionRepository`
 *   / `SnowRealmAuthClient`（只接受已建構的 `ClawClient?` 實例）
 * - 不碰 invite code / auth / provider 切換 / session 流程
 * - 不碰 floating result panel 顯示語意（由呼叫端於 `onSuggestionSelected`
 *   lambda 內自行操作）
 *
 * 此 binder 為 Sprint 6 最後一個 panel boundary 薄切，必須嚴格不越界成
 * Sprint 7 integration 收斂。
 */
object ImeClawPanelBinder {

    fun bind(
        context: Context,
        theme: KeyboardTheme,
        clawClient: ClawClient?,
        prefs: YukiPreferences?,
        initialMode: String,
        inputTextProvider: () -> String,
        appPackageProvider: () -> String,
        keyboardModeProvider: () -> KeyboardMode,
        onSuggestionSelected: (String) -> Unit,
        onClose: () -> Unit,
        onLockTapped: () -> Unit,
    ): ClawPanelView {
        val panel = ClawPanelView(context)
        panel.theme = theme
        panel.clawClient = clawClient
        panel.prefs = prefs
        panel.setCurrentMode(initialMode)
        panel.inputTextProvider = inputTextProvider
        panel.appPackageProvider = appPackageProvider
        panel.keyboardModeProvider = keyboardModeProvider
        panel.onSuggestionSelected = onSuggestionSelected
        panel.onClose = onClose
        panel.onLockTapped = onLockTapped
        return panel
    }
}
