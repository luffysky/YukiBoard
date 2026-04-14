package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.PanelType

/**
 * Sprint 1 的 IME UI 狀態骨架。
 *
 * 這個狀態先描述核心事實，後續再逐步把 service 內分散的欄位搬進來。
 */
data class ImeUiState(
    val inputMode: InputMode = InputMode.ENGLISH,
    val composingText: String = "",
    val candidates: List<String> = emptyList(),
    val activePanel: PanelType? = null,
    val isFloatingMode: Boolean = false,
    val isPanelBehindKeyboard: Boolean = false,
    val isVoiceInputActive: Boolean = false,
    val remoteConnectionState: ConnectionState = ConnectionState.IDLE,
    val isInGameMode: Boolean = false,
    val theme: KeyboardTheme = KeyboardTheme.default(),
    val warningBanner: String? = null,
) {
    companion object {
        fun initial(theme: KeyboardTheme = KeyboardTheme.default()): ImeUiState =
            ImeUiState(theme = theme)
    }
}
