package com.snowrealm.yukiboard.app.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import com.snowrealm.yukiboard.app.session.UserSessionRepository
import com.snowrealm.yukiboard.input.router.CandidateSource
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.panel.PanelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sprint 1 的 IME 協調器骨架。
 *
 * 目前只承接 state 與 lifecycle 入口，暫不搬既有複雜副作用。
 */
class ImeSessionCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val sessionRepository = UserSessionRepository(appContext)
    private val themeManager = ThemeManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(ImeUiState.initial(themeManager.currentTheme))
    val uiState: StateFlow<ImeUiState> = _uiState.asStateFlow()

    fun onInputViewCreated() {
        _uiState.update { it.copy(theme = themeManager.currentTheme) }
    }

    fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        _uiState.update { state ->
            state.copy(
                warningBanner = null,
                theme = themeManager.currentTheme,
            )
        }
    }

    fun onFinishInputView(finishingInput: Boolean) {
        _uiState.update { state ->
            state.copy(
                isVoiceInputActive = false,
                activePanel = null,
                warningBanner = null,
            )
        }
    }

    fun onFinishInput() {
        _uiState.update { state ->
            state.copy(
                composingText = "",
                candidateState = CandidateUiState.EMPTY,
            )
        }
    }

    fun onDestroy() {
        _uiState.update { state ->
            state.copy(
                activePanel = null,
                isVoiceInputActive = false,
                warningBanner = null,
            )
        }
    }

    fun setInputMode(inputMode: InputMode) {
        _uiState.update { it.copy(inputMode = inputMode) }
    }

    fun updateComposingText(text: String) {
        _uiState.update { it.copy(composingText = text) }
    }

    /**
     * 舊版 API：只寫 items，sourceMap 視為空。
     * 熱路徑 `syncImeCoordinatorFromRouter()` 暫時繼續走這條，
     * sourceMap 的 coordinator 接入留待 Sprint 4 熱路徑抽出。
     */
    fun updateCandidates(candidates: List<String>) {
        _uiState.update {
            it.copy(candidateState = CandidateUiState(items = candidates, sourceMap = emptyMap()))
        }
    }

    /**
     * Sprint 3 Phase 2 Round 3 新增：同時寫入 items + sourceMap。
     * 提供給已授權的 callback / helper 在需要攜帶著色資訊時使用。
     */
    fun updateCandidates(items: List<String>, sourceMap: Map<String, CandidateSource>) {
        _uiState.update {
            it.copy(candidateState = CandidateUiState(items = items, sourceMap = sourceMap))
        }
    }

    /**
     * 直接寫入完整 CandidateUiState。提供給未來 InputUiSync 使用。
     */
    fun updateCandidateState(candidateState: CandidateUiState) {
        _uiState.update { it.copy(candidateState = candidateState) }
    }

    fun showPanel(panelType: PanelType?) {
        _uiState.update { it.copy(activePanel = panelType) }
    }

    fun setVoiceInputActive(active: Boolean) {
        _uiState.update { it.copy(isVoiceInputActive = active) }
    }

    fun setFloatingMode(floating: Boolean) {
        _uiState.update { it.copy(isFloatingMode = floating) }
    }

    fun setPanelBehindKeyboard(behind: Boolean) {
        _uiState.update { it.copy(isPanelBehindKeyboard = behind) }
    }

    fun setGameMode(inGame: Boolean) {
        _uiState.update { it.copy(isInGameMode = inGame) }
    }

    fun setRemoteConnectionState(state: com.snowrealm.yukiboard.remote.ConnectionState) {
        _uiState.update { it.copy(remoteConnectionState = state) }
    }

    fun refreshTheme() {
        _uiState.update { it.copy(theme = themeManager.currentTheme) }
    }

    fun getSessionSnapshot() = sessionRepository.getSnapshot()
}
