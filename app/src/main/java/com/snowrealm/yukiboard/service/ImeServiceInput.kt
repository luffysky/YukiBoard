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

internal suspend fun YukiBoardIMEService.showFraudBubble(
    score: Int,
    hasUrlRisk: Boolean,
    replyWarning: Boolean = false,
    clipboardScan: Boolean = false
) {
    val emoji = fraudAnalyzer.getRiskEmoji(score)
    val urlNote = if (hasUrlRisk) "，連結也要小心" else ""
    val warning = when {
        replyWarning && score >= 60 ->
            "$emoji 等一下！你打的這段話看起來像在回應詐騙…要不要先冷靜一下？"
        replyWarning ->
            "$emoji 雪凜注意到你打的內容有點像在回應可疑訊息，先確認一下唷"
        clipboardScan && score >= 80 ->
            "$emoji 你剛複製的這段文字風險很高！點我幫你分析$urlNote"
        clipboardScan && score >= 60 ->
            "$emoji 你複製的訊息有些地方要注意唷$urlNote"
        clipboardScan ->
            "$emoji 雪凜幫你掃了一下複製的內容，有些用語要留意$urlNote"
        score >= 80 ->
            "$emoji 雪凜很擔心這段對話…點我看看$urlNote"
        score >= 60 ->
            "$emoji 這段對話有些地方要注意唷$urlNote"
        else ->
            "$emoji 雪凜覺得有些用語要留意一下$urlNote"
    }

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        val charAnchor = characterView as? android.view.View ?: return@withContext
        yukiSpeechBubble?.let { bubble ->
            bubble.isFraudWarning = true
            bubble.onBubbleTapped = {
                bubble.hideNow()
                showPanel(com.snowrealm.yukiboard.ui.panel.PanelType.CLAW)
                charAnchor.removeCallbacks(fraudDetectRunnable)
                charAnchor.postDelayed(fraudDetectRunnable, 300)
            }
            bubble.show(warning, charAnchor)
        }
        val reaction = if (replyWarning || score >= 60) "worried" else "surprised"
        characterView?.playReaction(reaction)
    }
}

internal fun YukiBoardIMEService.scanForFraud() {
    if (!prefs.aiFraudDetect || !prefs.aiFraudPassiveScan) return
    val ic = currentInputConnection ?: return
    val textBefore = ic.getTextBeforeCursor(2000, 0)?.toString() ?: return
    if (textBefore.length < 10) return  // 文字太短不分析
    if (textBefore == lastFraudScanText) return  // 同一段文字不重複掃
    lastFraudScanText = textBefore

    serviceScope.launch(Dispatchers.Default) {
        try {
            val result = fraudAnalyzer.analyzeByRules(textBefore)
            val urlRisks = fraudAnalyzer.scanUrls(textBefore).filter { it.score >= 20 }
            val effectiveScore = maxOf(result.riskScore, urlRisks.maxOfOrNull { it.score } ?: 0)

            if (effectiveScore >= 30 || urlRisks.isNotEmpty()) {
                showFraudBubble(effectiveScore, urlRisks.isNotEmpty())
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.w("Fraud", "Passive scan failed: ${e.message}") }
    }
}

internal fun YukiBoardIMEService.scanReplyForFraud(committedText: String) {
    if (!prefs.aiFraudDetect || !prefs.aiFraudPassiveScan) return
    if (committedText.length < 2) return
    val lower = committedText.lowercase()
    if (lower == lastFraudReplyText) return
    lastFraudReplyText = lower

    // 使用者回覆風險模式（正在被騙的跡象）
    val replyRiskPatterns = listOf(
        // 匯款/轉帳意圖
        listOf("好我匯", "好 我匯", "我轉給你", "我匯給你", "馬上轉", "等下轉",
               "已匯款", "已轉帳", "轉好了", "匯好了", "已經轉了", "已經匯了"),
        // 索取帳號
        listOf("帳號多少", "帳號是什麼", "你的帳號", "匯到哪", "轉到哪", "銀行帳號"),
        // 交出個資
        listOf("身分證", "我的密碼", "驗證碼給你", "OTP", "信用卡號", "卡號是"),
        // 順從高壓話術
        listOf("好我馬上", "我現在就去", "好 我去ATM", "我去超商", "我去買點數",
               "好我不跟別人說", "我不會說出去"),
        // 點擊連結確認
        listOf("我點了", "已經點了", "打開了", "登入了", "已經填了", "已輸入")
    )

    for (patterns in replyRiskPatterns) {
        for (pattern in patterns) {
            if (lower.contains(pattern.lowercase())) {
                serviceScope.launch(Dispatchers.Default) {
                    val score = 65  // 使用者自己打出危險回覆 = 中高風險
                    showFraudBubble(score, false, replyWarning = true)
                }
                return
            }
        }
    }
}

internal fun YukiBoardIMEService.scanClipboardForFraud(text: String) {
    if (!prefs.aiFraudDetect || !prefs.aiFraudPassiveScan) return
    if (text.length < 10) return

    serviceScope.launch(Dispatchers.Default) {
        try {
            val result = fraudAnalyzer.analyzeByRules(text)
            val urlRisks = fraudAnalyzer.scanUrls(text).filter { it.score >= 20 }
            val effectiveScore = maxOf(result.riskScore, urlRisks.maxOfOrNull { it.score } ?: 0)

            if (effectiveScore >= 30 || urlRisks.isNotEmpty()) {
                showFraudBubble(effectiveScore, urlRisks.isNotEmpty(), clipboardScan = true)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.w("Fraud", "Clipboard scan failed: ${e.message}") }
    }
}

internal fun YukiBoardIMEService.applyTheme(theme: KeyboardTheme) {
    imeThemeRenderDelegate.applyTheme(
        theme = theme,
        toolbarAnimStyle = prefs.toolbarAnimStyle,
        refreshThemeState = imeCoordinator::refreshTheme,
        toolbarView = toolbarView,
        keyboardView = keyboardView,
        glSurfaceView = glSurfaceView,
        applyParticleTheme = { particleView?.setTheme(it) },
    )
}

internal fun YukiBoardIMEService.toggleVoiceInput() {
    if (imeVoiceController.shouldStopCurrentListening(speechEngine)) {
        stopVoiceInput()
        return
    }
    if (!imeVoiceController.canStartVoiceInput(voiceOverlay?.isShowing == true)) return

    val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
    if (!imeVoiceController.handleMissingPermission(
            hasRecordAudioPermission = hasPermission,
            openPermissionSettings = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            },
            showPermissionToast = {
                keyboardView.showToast("請授權麥克風權限後再試")
            },
        )
    ) {
        return
    }

    val desiredType = prefs.speechEngineType
    val needRecreate = imeVoiceController.shouldRecreateEngine(speechEngine, desiredType)
    if (needRecreate) {
        speechEngine?.destroy()
        speechEngine = when (desiredType) {
            2 -> {
                val serverUrl = prefs.fasterWhisperServerUrl
                if (serverUrl.isBlank()) {
                    keyboardView.showToast("請先設定 Faster-Whisper Server URL")
                    AndroidSpeechEngine(applicationContext)
                } else {
                    FasterWhisperSpeechEngine(serverUrl)
                }
            }
            1 -> {
                val buildKey = com.snowrealm.yukiboard.BuildConfig.OPENAI_API_KEY
                val apiKey = buildKey.takeIf { it.isNotBlank() }
                    ?: com.snowrealm.yukiboard.claw.provider.AIProviderManager
                        .getInstance(applicationContext)
                        .getApiKey(com.snowrealm.yukiboard.claw.provider.AIProviderType.OPENAI)
                if (apiKey.isNullOrBlank()) {
                    keyboardView.showToast("請先設定 OpenAI API Key")
                    AndroidSpeechEngine(applicationContext)
                } else {
                    WhisperSpeechEngine(apiKey)
                }
            }
            else -> AndroidSpeechEngine(applicationContext)
        }
    }

    val engineName = imeVoiceController.resolveEngineName(speechEngine)
    imeVoiceController.onVoiceInputStarted(
        stopTts = { ttsEngine?.stop() },
        setVoiceInputActive = imeCoordinator::setVoiceInputActive,
        playListening = { characterView?.playListening() },
    )

    voiceFinalHandled = false
    val overlayCallbacks = imeVoiceController.bindOverlayCallbacks(
        stopVoiceInput = ::stopVoiceInput,
        playIdle = { characterView?.playIdle() },
        playListening = { characterView?.playListening() },
    )
    voiceOverlay?.onStop = overlayCallbacks.onStop
    voiceOverlay?.onPause = overlayCallbacks.onPause
    voiceOverlay?.onResume = overlayCallbacks.onResume
    voiceOverlay?.show(engineName)

    val speechLang = prefs.speechLanguage
    val localeHint = imeVoiceController.resolveLocaleHint(speechLang, inputRouter.inputMode)

    // voiceListeningLocale 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）

    val textBefore = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString()
    val silenceThreshold = imeVoiceController.resolveSilenceThreshold(prefs.voiceSilenceSensitivity)
    (speechEngine as? WhisperSpeechEngine)?.let {
        it.contextText = textBefore
        it.silenceAmplitudeThreshold = silenceThreshold
    }
    (speechEngine as? FasterWhisperSpeechEngine)?.let {
        it.contextText = textBefore
        it.silenceAmplitudeThreshold = silenceThreshold
    }

    speechEngine?.startListening(
        locale = localeHint,
        onPartialResult = { partial ->
            if (imeVoiceController.shouldShowPartialResult(voiceFinalHandled)) {
                voiceOverlay?.updatePartial(partial)
            }
        },
        onFinalResult = { result ->
            if (!imeVoiceController.onFinalResultReceived(voiceFinalHandled)) return@startListening
            voiceFinalHandled = true
            // voiceListeningLocale 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）
            if (imeVoiceController.shouldPostProcessResult(result, prefs.voicePostProcessEnabled)) {
                voiceOverlay?.showFinal("整理中…")
                val lang = localeHint
                voicePostProcessor?.process(result, lang) { cleaned ->
                    voiceOverlay?.showFinal(cleaned)
                }
            } else if (imeVoiceController.shouldShowFinalResult(result, prefs.voicePostProcessEnabled)) {
                voiceOverlay?.showFinal(result)
            } else if (imeVoiceController.shouldDismissOverlay(result)) {
                voiceOverlay?.dismiss()
                characterView?.playIdle()
            }
        },
        onError = { errorCode ->
            // voiceListeningLocale 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）
            characterView?.playIdle()
            val errorMsg = imeVoiceController.resolveErrorMessage(errorCode)
            voiceOverlay?.showError(errorMsg)
        }
    )
}

internal fun YukiBoardIMEService.stopVoiceInput() {
    imeVoiceController.onVoiceInputStopped(
        stopListening = { speechEngine?.stopListening() },
        setVoiceInputActive = imeCoordinator::setVoiceInputActive,
        clearVoiceListeningLocale = { /* 由 renderImeUiState collect 驅動（Sprint 3 Phase 1） */ },
        playIdle = { characterView?.playIdle() },
    )
}

internal fun YukiBoardIMEService.initTtsEngine() {
    ttsEngine?.destroy()
    ttsEngine = when (prefs.ttsEngineType) {
        1 -> {
            val apiKey = prefs.googleCloudTtsApiKey
            if (apiKey.isNotBlank()) {
                com.snowrealm.yukiboard.speech.GoogleCloudTtsEngine(apiKey)
            } else {
                com.snowrealm.yukiboard.speech.AndroidTtsEngine(applicationContext).also {
                    it.setVoice(prefs.ttsVoiceName.takeIf { v -> v.isNotEmpty() })
                }
            }
        }
        else -> com.snowrealm.yukiboard.speech.AndroidTtsEngine(applicationContext).also {
            it.setVoice(prefs.ttsVoiceName.takeIf { v -> v.isNotEmpty() })
        }
    }
}

internal fun YukiBoardIMEService.requestAICandidates() {
    aiCandidateJob?.cancel()
    val client = clawClient ?: return
    if (!client.isAvailable() || !prefs.aiPrediction) return

    val composing = inputRouter.composingText
    val context = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: ""
    if (composing.isEmpty() && context.isBlank()) return

    aiCandidateJob = serviceScope.launch {
        val capturedComposing = composing
        delay(AI_CANDIDATE_DEBOUNCE_MS)
        // F-2: 確認上下文沒變，避免把舊的 AI 補字貼到新的候選列
        if (inputRouter.composingText != capturedComposing) return@launch
        try {
            val input = com.snowrealm.yukiboard.claw.ClawInput(
                text = capturedComposing.ifEmpty { context.takeLast(30) },
                context = context,
                appPackage = currentInputEditorInfo?.packageName ?: "",
                language = if (inputRouter.inputMode == com.snowrealm.yukiboard.input.router.InputMode.ENGLISH) "en" else "zh-TW",
                mode = keyboardView.currentMode
            )
            val output = kotlinx.coroutines.withContext(Dispatchers.IO) {
                client.executeSkill("ai_prediction", input)
            }
            if (output.suggestions.isNotEmpty()) {
                // 把 AI 候選附加到現有列表末尾
                val currentCandidates = inputRouter.candidates.toMutableList()
                val currentSourceMap = inputRouter.candidateSourceMap.toMutableMap()
                for (suggestion in output.suggestions.take(3)) {
                    val s = suggestion.trim()
                    if (s.isNotEmpty() && s !in currentCandidates) {
                        currentCandidates.add(s)
                        currentSourceMap[s] = com.snowrealm.yukiboard.input.router.CandidateSource.AI
                    }
                }
                keyboardView.updateCandidates(currentCandidates, currentSourceMap)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // debounce cancelled — 正常行為
        } catch (e: Exception) {
            android.util.Log.w("YukiBoardIME", "AI candidate failed: ${e.message}")
        }
    }
}

internal fun YukiBoardIMEService.handleGameEvent(event: GameEvent, game: GameLogic) {
    val gkv = gameKeyboardView ?: return

    when (event) {
        GameEvent.EAT -> {
            keyboardView.vibrateShort()
            gkv.hudText = "好吃!"
        }
        GameEvent.HIT -> {
            keyboardView.vibrateShort()
            gkv.hudText = "打中!"
        }
        GameEvent.PERFECT -> {
            keyboardView.vibrateShort()
            gkv.hudText = "Perfect!"
        }
        GameEvent.GOOD -> {
            gkv.hudText = "Good!"
        }
        GameEvent.MISS -> {
            gkv.hudText = "Miss..."
        }
        GameEvent.COMBO -> {
            gkv.hudText = "${game.scoreManager.combo}x Combo!"
        }
        GameEvent.GAME_OVER -> {
            val isNewHighScore = game.scoreManager.saveHighScoreIfNeeded(game.gameMode)
            gkv.hudText = if (isNewHighScore) "新紀錄! ${game.scoreManager.currentScore}" else "遊戲結束"
            characterView?.playGameOver()
            gkv.removeCallbacks(showGameMenuRunnable)
            gkv.postDelayed(showGameMenuRunnable, 3000)
        }
        GameEvent.VICTORY -> {
            game.scoreManager.saveHighScoreIfNeeded(game.gameMode)
            gkv.hudText = "勝利! ${game.scoreManager.currentScore} 分"
            characterView?.playVictory()
            gkv.removeCallbacks(showGameMenuRunnable)
            gkv.postDelayed(showGameMenuRunnable, 3000)
        }
    }
}

internal fun YukiBoardIMEService.handleSwipeInput(keys: List<com.snowrealm.yukiboard.ui.keyboard.KeyModel>) {
    if (keys.size < 2) return

    val labels = keys.map { it.label }
    val candidates = inputRouter.swipeSuggest(labels)

    if (candidates.isNotEmpty()) {
        // 累積這次滑動的候選
        swipeAccumulated.add(candidates)

        // 組合候選字列表：多段滑動組合 + 各段候選
        val combined = buildSwipeCombinedCandidates()

        // composing text 顯示所有段的滑動軌跡
        keyboardView.updateComposingText("…")
        keyboardView.updateCandidates(combined)
        keyboardView.markSwipeCandidates(true)

        swipeInputPending = true
        swipeInputCandidates = combined
    }
}

internal fun YukiBoardIMEService.buildSwipeCombinedCandidates(): List<String> {
    if (swipeAccumulated.isEmpty()) return emptyList()
    if (swipeAccumulated.size == 1) return swipeAccumulated[0]

    val result = mutableListOf<String>()

    // 1. 組合前面所有段的 top 候選 + 最新段的各候選
    //    例如 [今,金] + [天,添] → 今天, 今添, 金天
    val prefixParts = swipeAccumulated.dropLast(1)
    val lastPart = swipeAccumulated.last()

    // 取前段 top-1 組成前綴
    val bestPrefix = prefixParts.joinToString("") { it.firstOrNull() ?: "" }
    // 最後一段各候選組合
    for (c in lastPart.take(8)) {
        val combo = bestPrefix + c
        if (combo !in result) result.add(combo)
    }
    // 2. 取前段的替代組合（每段取 top-2）
    if (prefixParts.size == 1 && prefixParts[0].size > 1) {
        for (altIdx in 1 until minOf(3, prefixParts[0].size)) {
            val altPrefix = prefixParts[0][altIdx]
            val combo = altPrefix + (lastPart.firstOrNull() ?: "")
            if (combo !in result) result.add(combo)
        }
    }
    // 3. 附加最新段的單獨候選（方便使用者只選最後一個字）
    for (c in lastPart.take(5)) {
        if (c !in result) result.add(c)
    }

    return result
}

internal fun YukiBoardIMEService.tryExpandSnippet(ic: InputConnection): Boolean {
    if (!prefs.snippetEnabled) return false
    if (keyboardView.currentMode != KeyboardMode.DEVELOPER) return false

    val before = ic.getTextBeforeCursor(20, 0)?.toString() ?: return false
    val lastWord = before.trimEnd().split(Regex("[\\s{}()\\[\\];,.]")).lastOrNull()
        ?: return false

    val result = snippetManager.expand(lastWord) ?: return false
    ic.deleteSurroundingText(result.abbreviationLength, 0)
    ic.commitText(result.expansion, 1)
    if (result.cursorOffset < 0) {
        repeat(-result.cursorOffset) { sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT) }
    }
    return true
}

internal fun YukiBoardIMEService.handleTab(ic: InputConnection) {
    // 優先：Snippet 展開（開發者模式）
    if (tryExpandSnippet(ic)) return

    // 次優先：候選詞有內容時，Tab 移動焦點
    if (inputRouter.candidates.isNotEmpty()) {
        val focused = inputRouter.candidateManager.focusNext()
        if (focused != null) {
            keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
            keyboardView.highlightCandidate(inputRouter.candidateManager.focusedIndex)
            return
        }
    }

    // 最後：發送 Tab 鍵
    val meta = if (keyboardView.isShiftActive()) KeyEvent.META_SHIFT_ON else 0
    sendKey(ic, KeyEvent.KEYCODE_TAB, meta)
}

internal fun YukiBoardIMEService.handleDelete(ic: InputConnection) {
    // 如果有選取的文字，直接刪除選取範圍
    val sel = ic.getSelectedText(0)
    if (!sel.isNullOrEmpty()) {
        ic.commitText("", 1)
        return
    }
    when (val result = inputRouter.processDelete()) {
        is InputResult.ComposingUpdated -> ic.setComposingText(result.composing, 1)
        is InputResult.ComposingCleared -> {
            ic.finishComposingText()
            ic.deleteSurroundingText(1, 0)
        }
        else -> ic.deleteSurroundingText(1, 0)
    }
    keyboardView.updateComposingText(inputRouter.composingText)
    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
}

internal fun YukiBoardIMEService.handleEnter(ic: InputConnection) {
    val result = inputRouter.processEnter()
    applyInputResult(ic, result)
    keyboardView.updateComposingText(inputRouter.composingText)
    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)

    // Enter = 句子結束 → 學習整句模式
    val sentence = sentenceBuffer.toString().trim()
    if (sentence.length >= 2) {
        inputRouter.smartInput.onSentenceCommitted(sentence)
    }
    sentenceBuffer.clear()

    if (result is InputResult.Idle) {
        // 檢查 Editor 是否要求特定 action（搜尋/傳送/完成等）
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ?: 0
        val hasActionLabel = info?.actionLabel != null
        val isMultiLine = (info?.inputType ?: 0) and
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

        // 私房話模式：送出前把輸入框整句加密
        if (prefs.secretMessageMode) {
            val plaintext = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
            if (plaintext.isNotBlank() && secretMessageManager?.isEncryptedMessage(plaintext) != true) {
                val password = prefs.secretMessagePassword
                val encrypted = if (password.isNotEmpty()) {
                    secretMessageManager?.encryptWithPassword(plaintext, password)
                } else {
                    secretMessageManager?.encrypt(plaintext)
                }
                if (encrypted != null) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.commitText(encrypted, 1)
                }
            }
        }

        if (!isMultiLine && (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                || action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                || action == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                || hasActionLabel)) {
            // 搜尋/傳送/前往 — 執行 action（可能關閉鍵盤）
            sendDefaultEditorAction(true)
        } else {
            // 一般輸入 — 送換行符
            ic.commitText("\n", 1)
        }
    }
}

internal fun YukiBoardIMEService.handleSpace(ic: InputConnection) {
    // Tab 聚焦候選時，空白鍵 = 確認選中
    if (inputRouter.candidateManager.hasFocus()) {
        val confirmed = inputRouter.candidateManager.confirmFocused()
        if (confirmed != null) {
            applyInputResult(ic, InputResult.CommitText("$confirmed "))
            // 記錄提交並觸發下一詞預測
            inputRouter.selectCandidate(inputRouter.candidateManager.focusedIndex)
            keyboardView.updateComposingText("")
            keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
            return
        }
    }
    applyInputResult(ic, inputRouter.processSpace())
    // 更新候選列（日文漢字轉換、注音選字等需要此更新）
    keyboardView.updateComposingText(inputRouter.composingText)
    keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
}

internal fun YukiBoardIMEService.handleSwitchLang() {
    // 切換語言前：先把 composing 提交到 InputConnection，避免殘留
    val ic = currentInputConnection
    val commitResult = inputRouter.getActiveEngine().forceCommit()
    if (commitResult is com.snowrealm.yukiboard.input.router.InputResult.CommitText) {
        ic?.commitText(commitResult.text, 1)
    }
    ic?.finishComposingText()

    // AUTO 模式下短按切換 → 切鍵盤佈局（英文↔注音），保持 AUTO
    if (inputRouter.inputMode == InputMode.AUTO) {
        inputRouter.toggleAutoLayout()
        keyboardView.updateKeyRows(rowsForAutoLayout())
        keyboardView.showToast(if (inputRouter.autoLayout == InputRouter.AutoLayout.QWERTY) "⚡ 自動·英文鍵盤" else "⚡ 自動·注音鍵盤")
        keyboardView.updateCandidates(emptyList(), emptyMap())
        return
    }

    inputRouter.toggleLanguage()
    characterView?.playSwitchLang()
    keyboardView.currentInputMode = inputRouter.inputMode
    keyboardView.updateKeyRows(rowsForInputMode(inputRouter.inputMode))
    keyboardView.updateCandidates(emptyList(), emptyMap())
}

internal fun YukiBoardIMEService.rowsForInputMode(mode: com.snowrealm.yukiboard.input.router.InputMode): List<List<KeyModel>> = when (mode) {
    com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> KeyboardState.bopomofoRows()
    com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> KeyboardState.pinyinRows()
    com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
    com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> KeyboardState.cangjieRows()
    com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> KeyboardState.japaneseRows()
    com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> KeyboardState.koreanRows()
    // AUTO 模式：根據使用者選擇的佈局顯示
    com.snowrealm.yukiboard.input.router.InputMode.AUTO -> rowsForAutoLayout()
    // 所有拉丁語系語言：從 LanguagePack 動態生成佈局（新增語言不需改此處）
    else -> {
        val langId = mode.langId ?: mode.name.lowercase()
        KeyboardState.latinRowsFromPack(langId)
    }
}

internal fun YukiBoardIMEService.routeKeyToPanel(panel: PanelInputTarget, keyCode: Int, label: String): Boolean {
    val isCjk = inputRouter.isCurrentCjk()
    when (keyCode) {
        KeyboardView.KEYCODE_DELETE -> {
            if (isCjk && inputRouter.composingText.isNotEmpty()) {
                inputRouter.processDelete()
                keyboardView.updateComposingText(inputRouter.composingText)
                keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
            } else {
                panel.onDeleteInput()
            }
        }
        KeyboardView.KEYCODE_ENTER -> {
            if (isCjk && inputRouter.composingText.isNotEmpty()) {
                panel.onComposedTextInput(inputRouter.composingText)
                inputRouter.reset()
                keyboardView.updateComposingText("")
                keyboardView.updateCandidates(emptyList())
            }
            panel.onEnterInput()
        }
        KeyboardView.KEYCODE_SPACE -> {
            if (isCjk && inputRouter.composingText.isNotEmpty()) {
                val result = inputRouter.processSpace()
                if (result is InputResult.CommitText) {
                    panel.onComposedTextInput(result.text)
                }
                keyboardView.updateComposingText(inputRouter.composingText)
                keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
            } else {
                panel.onSpaceInput()
            }
        }
        KeyboardView.KEYCODE_SWITCH_LANG -> handleSwitchLang()
        KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> { }
        else -> if (label.isNotEmpty() && keyCode >= 0) {
            if (isCjk) {
                val result = inputRouter.processInput(label)
                if (result is InputResult.CommitText) {
                    panel.onComposedTextInput(result.text)
                }
                keyboardView.updateComposingText(inputRouter.composingText)
                keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                panel.updateComposingHint(inputRouter.composingText)
            } else {
                panel.onCharInput(label)
            }
        }
    }
    return true
}

