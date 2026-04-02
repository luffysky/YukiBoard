package com.snowrealm.yukiboard.ui.keyboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 鍵盤 UI 的 ViewModel。
 *
 * 負責：
 * - 維護 [KeyboardState] 並以 [StateFlow] 暴露給 View
 * - 處理 Shift / CapsLock 雙擊切換邏輯
 * - 管理候選詞列表更新
 * - 語言模式切換
 */
class KeyboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(KeyboardState())
    val state: StateFlow<KeyboardState> = _state.asStateFlow()

    // 用於 LiveData 觀察（向舊版相容）
    private val _stateLive = MutableLiveData<KeyboardState>(_state.value)
    val stateLive: LiveData<KeyboardState> = _stateLive

    private var lastShiftTapTime = 0L

    init {
        viewModelScope.launch {
            _state.collect { _stateLive.value = it }
        }
    }

    // -------------------------------------------------------------------------
    // Shift / CapsLock
    // -------------------------------------------------------------------------

    fun onShiftTap() {
        val now = System.currentTimeMillis()
        val doubleTap = (now - lastShiftTapTime) < DOUBLE_TAP_THRESHOLD_MS
        lastShiftTapTime = now

        _state.update { s ->
            when {
                doubleTap          -> s.copy(isCapsLock = true,  isShifted = true)
                s.isCapsLock       -> s.copy(isCapsLock = false, isShifted = false)
                s.isShifted        -> s.copy(isShifted = false)
                else               -> s.copy(isShifted = true)
            }
        }
        rebuildKeyRows()
    }

    fun consumeShiftAfterChar() {
        if (!_state.value.isCapsLock) {
            _state.update { it.copy(isShifted = false) }
            rebuildKeyRows()
        }
    }

    // -------------------------------------------------------------------------
    // 語言模式
    // -------------------------------------------------------------------------

    fun toggleLanguage() {
        _state.update { s ->
            val next = if (s.inputMode == InputMode.ENGLISH) InputMode.BOPOMOFO else InputMode.ENGLISH
            s.copy(inputMode = next)
        }
    }

    // -------------------------------------------------------------------------
    // 候選詞
    // -------------------------------------------------------------------------

    fun updateCandidates(candidates: List<String>) {
        _state.update { it.copy(candidates = candidates, selectedCandidateIndex = -1) }
    }

    fun updateComposing(text: String) {
        _state.update { it.copy(composingText = text) }
    }

    fun selectCandidate(index: Int) {
        _state.update { it.copy(selectedCandidateIndex = index) }
    }

    // -------------------------------------------------------------------------
    // 主題
    // -------------------------------------------------------------------------

    fun applyTheme(theme: KeyboardTheme) {
        _state.update { it.copy(theme = theme) }
    }

    // -------------------------------------------------------------------------
    // 語音輸入
    // -------------------------------------------------------------------------

    fun setVoiceInputActive(active: Boolean) {
        _state.update { it.copy(isVoiceInputActive = active) }
    }

    // -------------------------------------------------------------------------
    // 私有輔助
    // -------------------------------------------------------------------------

    private fun rebuildKeyRows() {
        val shifted = _state.value.isShifted
        val newRows = KeyboardState.defaultQwertyRows().map { row ->
            row.map { key ->
                if (!key.isSpecial) key.copy(label = if (shifted) key.label.uppercase() else key.label.lowercase())
                else key
            }
        }
        _state.update { it.copy(keyRows = newRows) }
    }

    companion object {
        private const val DOUBLE_TAP_THRESHOLD_MS = 300L
    }
}
