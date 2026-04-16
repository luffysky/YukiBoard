package com.snowrealm.yukiboard.app.ime

import com.snowrealm.yukiboard.input.router.CandidateSource
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.panel.PanelType

/**
 * 候選字 UI 狀態（Sprint 3 Phase 2 Round 3 結構升級）。
 *
 * 原本 `ImeUiState.candidates: List<String>` 無法攜帶 `CandidateSource` sourceMap，
 * 導致熱路徑的 AI / DICTIONARY / PREDICTION 著色資訊無法進入 coordinator。
 * Round 3 引入 `CandidateUiState` 讓 coordinator 能承載完整候選語義，
 * 但熱路徑（handleKeyPress / applyInputResult / AI debounce / swipe RMW / HANDWRITING onRecognized）
 * 的接入仍屬 Sprint 4 範圍。
 */
data class CandidateUiState(
    val items: List<String> = emptyList(),
    val sourceMap: Map<String, CandidateSource> = emptyMap(),
) {
    companion object {
        val EMPTY: CandidateUiState = CandidateUiState()
    }
}

/**
 * Sprint 1 的 IME UI 狀態骨架，Sprint 3 Phase 2 Round 3 candidates 結構升級。
 */
data class ImeUiState(
    val inputMode: InputMode = InputMode.ENGLISH,
    val composingText: String = "",
    val candidateState: CandidateUiState = CandidateUiState.EMPTY,
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
