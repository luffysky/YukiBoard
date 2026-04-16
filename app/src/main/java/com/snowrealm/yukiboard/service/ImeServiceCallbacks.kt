package com.snowrealm.yukiboard.service

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.snowrealm.yukiboard.animation.gles.KeyboardGLSurfaceView
import com.snowrealm.yukiboard.app.ime.DestroyCleanup
import com.snowrealm.yukiboard.app.ime.FinishInputCleanup
import com.snowrealm.yukiboard.app.ime.FinishInputViewCleanup
import com.snowrealm.yukiboard.app.ime.ImeCandidateInteractionHandler
import com.snowrealm.yukiboard.app.ime.ImePanelCleanup
import com.snowrealm.yukiboard.app.ime.ImePanelBindings
import com.snowrealm.yukiboard.app.ime.ImePanelCoordinator
import com.snowrealm.yukiboard.app.ime.ImePanelCreation
import com.snowrealm.yukiboard.app.ime.ImePanelFactory
import com.snowrealm.yukiboard.app.ime.ImeFloatingModeHelper
import com.snowrealm.yukiboard.app.ime.ImeFraudModelInit
import com.snowrealm.yukiboard.app.ime.ImeGameLauncher
import com.snowrealm.yukiboard.app.ime.ImeImageCommitter
import com.snowrealm.yukiboard.app.ime.ImePanelControlBar
import com.snowrealm.yukiboard.app.ime.ImeTransparencyOverlay
import com.snowrealm.yukiboard.app.ime.ImePanelHost
import com.snowrealm.yukiboard.app.ime.ImePanelRouter
import com.snowrealm.yukiboard.app.ime.ImeSessionCoordinator
import com.snowrealm.yukiboard.app.ime.ImeSessionHooks
import com.snowrealm.yukiboard.app.ime.ImeThemeRenderDelegate
import com.snowrealm.yukiboard.app.ime.ImeVoiceController
import com.snowrealm.yukiboard.data.CloudSyncManager
import com.snowrealm.yukiboard.game.*
import com.snowrealm.yukiboard.input.router.InputMode
import com.snowrealm.yukiboard.input.router.InputRouter
import com.snowrealm.yukiboard.input.router.InputResult
import com.snowrealm.yukiboard.input.snippet.SnippetManager
import com.snowrealm.yukiboard.sensor.AppModeDetector
import com.snowrealm.yukiboard.sensor.CursorDirection
import com.snowrealm.yukiboard.sensor.GyroscopeCursorController
import com.snowrealm.yukiboard.sensor.ShakeDetector
import com.snowrealm.yukiboard.remote.BluetoothHidKeyboard
import com.snowrealm.yukiboard.remote.ConnectionState
import com.snowrealm.yukiboard.remote.KeyAction
import com.snowrealm.yukiboard.remote.RemoteKeyEvent
import com.snowrealm.yukiboard.remote.RemoteKeyboardService
import com.snowrealm.yukiboard.remote.WifiKeyboardServer
import com.snowrealm.yukiboard.speech.AndroidSpeechEngine
import com.snowrealm.yukiboard.speech.SpeechInputEngine
import com.snowrealm.yukiboard.speech.FasterWhisperSpeechEngine
import com.snowrealm.yukiboard.speech.WhisperSpeechEngine
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.keyboard.KeyModel
import com.snowrealm.yukiboard.ui.keyboard.RadialLanguageMenu
import com.snowrealm.yukiboard.ui.keyboard.CharacterView
import com.snowrealm.yukiboard.ui.keyboard.FloatingBallView
import com.snowrealm.yukiboard.ui.keyboard.FloatingKeyboardWrapper
import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode
import com.snowrealm.yukiboard.ui.keyboard.KeyboardResizeHandle
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView
import com.snowrealm.yukiboard.ui.panel.*
import com.snowrealm.yukiboard.ui.settings.SettingsActivity
import com.snowrealm.yukiboard.claw.ClawClient
import com.snowrealm.yukiboard.ui.settings.YukiPreferences
import com.snowrealm.yukiboard.gif.GiphyApiClient
import com.snowrealm.yukiboard.translate.TranslateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extension functions extracted from YukiBoardIMEService

internal fun YukiBoardIMEService.bindKeyboardViewCallbacks() {
    keyboardView.setOnKeyPressListener { keyCode, label ->
        if (prefs.keySoundEnabled) {
            keySoundEngine?.defaultStyle = when (prefs.keySoundStyle) {
                "marimba"   -> KeyboardSoundEngine.ToneStyle.MARIMBA
                "soft"      -> KeyboardSoundEngine.ToneStyle.SOFT
                "guitar"    -> KeyboardSoundEngine.ToneStyle.GUITAR
                "music_box" -> KeyboardSoundEngine.ToneStyle.MUSIC_BOX
                "harp"      -> KeyboardSoundEngine.ToneStyle.HARP
                "organ"     -> KeyboardSoundEngine.ToneStyle.ORGAN
                "drum"      -> KeyboardSoundEngine.ToneStyle.DRUM
                "edrum"     -> KeyboardSoundEngine.ToneStyle.EDRUM
                "beatbox"   -> KeyboardSoundEngine.ToneStyle.BEATBOX
                else        -> KeyboardSoundEngine.ToneStyle.PIANO
            }
            keySoundEngine?.volumeScale = prefs.keySoundVolume
            keySoundEngine?.playKeyTone(label)
        }
        handleKeyPress(keyCode, label)
    }
    keyboardView.onCaseConvert = convert@{ type ->
        val ic = currentInputConnection ?: return@convert
        val selected = ic.getSelectedText(0)?.toString()
        val target = if (!selected.isNullOrEmpty()) selected
        else {
            val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val lastSpace = before.lastIndexOf(' ')
            if (lastSpace >= 0) before.substring(lastSpace + 1) else before
        }
        if (target.isEmpty()) return@convert
        val converted = when (type) {
            KeyboardView.CaseConvertType.UPPER  -> target.uppercase()
            KeyboardView.CaseConvertType.LOWER  -> target.lowercase()
            KeyboardView.CaseConvertType.INVERT -> target.map {
                if (it.isUpperCase()) it.lowercase() else it.uppercase()
            }.joinToString("")
        }
        if (converted == target) return@convert
        ic.beginBatchEdit()
        if (!selected.isNullOrEmpty()) ic.commitText(converted, 1)
        else { ic.deleteSurroundingText(target.length, 0); ic.commitText(converted, 1) }
        ic.endBatchEdit()
    }
    keyboardView.setOnRawCommitListener { text ->
        val rawTarget: PanelInputTarget? = imePanelBindings.activeRawCommitTarget()
        if (imeCandidateInteractionHandler.routeRawCommitToPanel(rawTarget, text)) {
            return@setOnRawCommitListener
        }
        val ic = currentInputConnection ?: return@setOnRawCommitListener
        imeCandidateInteractionHandler.flushComposingBeforeRawCommit(
            composingText = inputRouter.composingText,
            commitComposing = { composing -> ic.commitText(composing, 1) },
            resetInputState = { inputRouter.reset() },
            clearInputUiState = { clearComposingAndCandidatesUi() },
        )
        ic.commitText(text, 1)
    }
    keyboardView.setOnSwipeInputListener { keys ->
        handleSwipeInput(keys)
    }
    keyboardView.setOnModeChangedListener { mode ->
        manualModeOverride = true
        characterView?.playSwitchLang()
        if (mode == KeyboardMode.GAME) {
            enterGameMode()
            characterView?.playGameStart()
        } else {
            exitGameMode()
        }
    }
    keyboardView.setOnCandidateSpeakListener { text -> onCandidateSpeak(text) }
    keyboardView.setOnCandidateSelectListener { index ->
        if (swipeInputPending && index in swipeInputCandidates.indices) {
            val text = swipeInputCandidates[index]
            val panelTarget = imePanelBindings.activeInputTarget()
            if (imeCandidateInteractionHandler.routeRawCommitToPanel(panelTarget, text)) {
            } else {
                val ic = currentInputConnection
                if (ic != null) {
                    ic.finishComposingText()
                    inputRouter.reset()
                    ic.commitText(text, 1)
                }
            }
            imeCandidateInteractionHandler.clearSwipeState(
                clearPendingFlag = { swipeInputPending = false },
                clearCandidates = { swipeInputCandidates = emptyList() },
                clearAccumulated = { swipeAccumulated.clear() },
                clearInputUiState = { clearComposingAndCandidatesUi() },
            )
            return@setOnCandidateSelectListener
        }

        val displayedCandidates = keyboardView.candidates
        val displayedText = displayedCandidates.getOrNull(index)
        val sourceMap = keyboardView.candidateSourceMap
        val isAICandidate = imeCandidateInteractionHandler.isAICandidate(displayedText, sourceMap)

        if (isAICandidate && displayedText != null) {
            val panelTarget = imePanelBindings.activeInputTarget()
            if (imeCandidateInteractionHandler.routeRawCommitToPanel(panelTarget, displayedText)) {
                if (inputRouter.composingText.isNotEmpty()) inputRouter.reset()
            } else {
                val ic = currentInputConnection ?: return@setOnCandidateSelectListener
                if (inputRouter.composingText.isNotEmpty()) {
                    ic.finishComposingText()
                }
                inputRouter.reset()
                applyInputResult(ic, InputResult.CommitText(displayedText))
            }
            clearComposingAndCandidatesUi()
            return@setOnCandidateSelectListener
        }

        val hwPanel = imePanelBindings.handwritingPanel
        // Sprint 5 Round 3: R11 遷移（Round 3 移除 activePanel local field 前置）
        if (hwPanel != null && imeContainerState.activePanel == PanelType.HANDWRITING) {
            val text = displayedCandidates.getOrNull(index) ?: return@setOnCandidateSelectListener
            currentInputConnection?.commitText(text, 1)
            clearCandidatesUi()
            inputRouter.onHandwritingCommit(text)
            return@setOnCandidateSelectListener
        }

        val isExtraCand = imeCandidateInteractionHandler.isExtraCandidate(
            displayedText = displayedText,
            index = index,
            routerCandidateCount = inputRouter.candidates.size,
        )
        val result = if (isExtraCand) {
            inputRouter.reset()
            InputResult.CommitText(displayedText!!)
        } else {
            inputRouter.selectCandidate(index)
        }
        val activeTarget = imePanelBindings.activeInputTarget()
        if (imeCandidateInteractionHandler.routeCommittedCandidateToPanel(activeTarget, result)) {
        } else {
            val ic = currentInputConnection ?: return@setOnCandidateSelectListener
            applyInputResult(ic, result)
        }
        // Sprint 4 Round 1: candidate select post-commit render 改走 InputUiSync（coordinator 同步承載 sourceMap）
        inputUiSync.renderComposing(inputRouter.composingText)
        inputUiSync.renderCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
        val comboEffect = inputRouter.smartInput.comboTracker.consumeEffect()
        if (comboEffect != null) {
            keyboardView.triggerComboEffect(comboEffect.ordinal + 1)
        }
    }
    keyboardView.setOnSpaceLongPressListener { pressed ->
        if (prefs.gyroscopeCursorEnabled) {
            if (pressed) {
                gyroController.sensitivity = prefs.gyroscopeSensitivity
                gyroController.activate()
            } else {
                gyroController.deactivate()
            }
        }
    }
    keyboardView.setOnLanguageSelectedListener { option ->
        when (option) {
            is RadialLanguageMenu.LanguageOption.Language -> {
                val mode = option.mode
                if (mode == InputMode.AUTO) {
                    prefs.autoInputMode = true
                } else if (prefs.autoInputMode) {
                    prefs.autoInputMode = false
                }
                val switchResult = inputRouter.switchTo(mode)
                if (switchResult is InputResult.CommitText) {
                    currentInputConnection?.commitText(switchResult.text, 1)
                }
                currentInputConnection?.finishComposingText()
                characterView?.playSwitchLang()
                keyboardView.currentInputMode = mode
                keyboardView.updateKeyRows(rowsForInputMode(mode))
                clearComposingAndCandidatesUi()
                keyboardView.showToast(if (mode == InputMode.AUTO) "⚡ 已啟動智慧輸入法" else mode.displayName)
            }
            is RadialLanguageMenu.LanguageOption.Emoji -> {
                // toolbarView.activePanel 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）
                toolbarView.onPanelSelected?.invoke(com.snowrealm.yukiboard.ui.panel.PanelType.EMOJI)
            }
        }
    }
    keyboardView.setOnFloatingToggleListener {
        prefs.floatingModeEnabled = !prefs.floatingModeEnabled
        closePanel()
        backgroundView?.destroy()
        backgroundView = null
        setInputView(onCreateInputView())
    }
    keyboardView.onSwipeSyllableConfirmed = { label ->
        val ch = label.firstOrNull()
        val isBopomofo = ch != null && (ch in '\u3105'..'\u312F' || ch in setOf('ˉ','ˊ','ˇ','ˋ','˙'))
        if (!isBopomofo) {
            currentInputConnection?.commitText(label, 1)
        }
    }
    keyboardView.onStickerHintClick = {
        handlePanelToggle(PanelType.STICKER)
    }
}

