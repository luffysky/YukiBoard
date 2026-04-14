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

internal fun YukiBoardIMEService.registerClipboardFraudListener() {
    if (clipboardListener != null) return  // 已註冊
    if (!prefs.aiFraudDetect || !prefs.aiFraudPassiveScan) return
    try {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
                if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
                val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
                if (text.length in 10..5000) {
                    scanClipboardForFraud(text)
                }
            } catch (_: Exception) { }
        }
        cm.addPrimaryClipChangedListener(listener)
        clipboardListener = listener
    } catch (_: Exception) { }
}

internal fun YukiBoardIMEService.setupKeyboardOverlays() {
    val bgView = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView(this)
    bgView.scaleMode = prefs.bgScaleMode
    bgView.offsetX = prefs.bgOffsetX
    bgView.offsetY = prefs.bgOffsetY
    val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
    val useCustomBg = prefs.useCustomBackground && savedBg != null
    if (useCustomBg && savedBg != null) {
        if (com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
            bgView.setVideoBackground(savedBg)
        } else {
            bgView.setImageBackground(savedBg)
        }
    }
    keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    backgroundView = bgView

    keyboardContainer.addView(keyboardView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))

    keyboardView.hasCustomBackground = useCustomBg
    keyboardView.bgOverlayAlpha = prefs.bgOverlayAlpha
    keyboardView.keyScalePercent = prefs.keyScalePercent
    keyboardView.keyOpacity = prefs.keyOpacity
    keyboardView.keyTextOpacity = prefs.keyTextOpacity
    keyboardView.perKeySizes = prefs.getAllPerKeySizes()
    keyboardView.hapticEnabled = prefs.keyPressVibration
    keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
    keyboardView.swipeInputEnabled = prefs.swipeInput
    keyboardView.showTouchAreaEnabled = prefs.showTouchArea

    keyboardView.oneHandSide = try {
        KeyboardView.OneHandSide.valueOf(prefs.oneHandSide)
    } catch (_: Exception) { KeyboardView.OneHandSide.NONE }
    keyboardView.onOneHandChanged = { side ->
        prefs.oneHandSide = side.name
    }

    val currentTheme = ThemeManager.getInstance(this).currentTheme
    keyboardView.applyTheme(currentTheme)
    toolbarView.theme = currentTheme
    toolbarView.setAnimStyle(prefs.toolbarAnimStyle)
    toolbarView.hiddenPanels = buildHiddenPanelSet()
    keyboardView.setAnimStyle(prefs.toolbarAnimStyle)

    val pv = com.snowrealm.yukiboard.animation.KeyboardParticleView(this)
    pv.setTheme(currentTheme)
    pv.start()
    keyboardContainer.addView(pv, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    particleView = pv

    val gl = KeyboardGLSurfaceView(this)
    gl.setFlowAlpha(currentTheme.flowLightAlpha)
    gl.setFlowColor(currentTheme.flowLightColorInt)
    keyboardContainer.addView(gl, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    glSurfaceView = gl

    val charSize = (72 * resources.displayMetrics.density).toInt()
    val charView = CharacterView(this)
    if (prefs.vrmCharacterEnabled) {
        charView.initVrmMode()
    } else {
        charView.initLottieMode()
    }
    val charParams = FrameLayout.LayoutParams(charSize, charSize).apply {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
    }
    keyboardContainer.addView(charView, charParams)
    characterView = charView
    if (!prefs.characterAnimEnabled) {
        charView.visibility = View.GONE
    }

    val bubble = com.snowrealm.yukiboard.yuki.YukiSpeechBubble(this)
    val bubbleParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
    keyboardContainer.addView(bubble, bubbleParams)
    yukiSpeechBubble = bubble
    bubble.onSpeakRequest = { text -> onYukiSpeak(text) }

    panelContainer = FrameLayout(this)
    panelContainer.visibility = View.GONE
    keyboardContainer.addView(panelContainer, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        android.view.Gravity.TOP
    ))

    topPanelContainer = FrameLayout(this)
    topPanelContainer.visibility = View.GONE
    rootContainer.addView(topPanelContainer, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ))
    imePanelHost = ImePanelHost(
        panelContainer = panelContainer,
        topPanelContainer = topPanelContainer,
    )
    imePanelRouter = ImePanelRouter(
        imePanelCoordinator = imePanelCoordinator,
        imePanelCleanup = imePanelCleanup,
        imePanelHost = imePanelHost,
    )
}

internal fun YukiBoardIMEService.syncPrefsToViews() {
    glSurfaceView?.onResume()
    if (prefs.characterAnimEnabled) {
        characterView?.resumeAnimation()
    }

    toolbarView.setAnimStyle(prefs.toolbarAnimStyle)
    toolbarView.hiddenPanels = buildHiddenPanelSet()
    keyboardView.setAnimStyle(prefs.toolbarAnimStyle)

    characterView?.visibility = if (prefs.characterAnimEnabled) View.VISIBLE else View.GONE
    yukiSpeechBubble?.visibility = if (prefs.characterAnimEnabled) View.VISIBLE else View.GONE

    characterView?.let { cv ->
        val wantVrm = prefs.vrmCharacterEnabled
        if (wantVrm && cv.renderMode != CharacterView.RenderMode.VRM) {
            cv.initVrmMode()
        } else if (!wantVrm && cv.renderMode != CharacterView.RenderMode.LOTTIE) {
            cv.initLottieMode()
        }
    }

    (ttsEngine as? com.snowrealm.yukiboard.speech.AndroidTtsEngine)?.let { engine ->
        val savedVoice = prefs.ttsVoiceName
        engine.setVoice(savedVoice.takeIf { it.isNotEmpty() })
    }

    applyTransparentMode()

    backgroundView?.scaleMode = prefs.bgScaleMode
    backgroundView?.offsetX = prefs.bgOffsetX
    backgroundView?.offsetY = prefs.bgOffsetY
    backgroundView?.bgRotation = prefs.bgRotation
    val bgHas = backgroundView?.reloadFromStorage() ?: false
    val useCustomBg = prefs.useCustomBackground && bgHas
    val glassKeys = useCustomBg || prefs.transparentKeyboard
    if (keyboardView.hasCustomBackground != glassKeys) {
        keyboardView.hasCustomBackground = glassKeys
        keyboardView.invalidate()
    }
    if (prefs.transparentKeyboard) {
        keyboardView.bgOverlayAlpha = prefs.transparentKeyboardAlpha
    } else {
        backgroundView?.overlayAlpha = prefs.bgOverlayAlpha
        keyboardView.bgOverlayAlpha = prefs.bgOverlayAlpha
    }
    keyboardView.customKeyLabelColor = prefs.customKeyLabelColor
    keyboardView.keyScalePercent = prefs.keyScalePercent
    keyboardView.keyOpacity = prefs.keyOpacity
    keyboardView.keyTextOpacity = prefs.keyTextOpacity
    keyboardView.perKeySizes = prefs.getAllPerKeySizes()
    keyboardView.hapticEnabled = prefs.keyPressVibration
    keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
    keyboardView.swipeInputEnabled = prefs.swipeInput
    keyboardView.showTouchAreaEnabled = prefs.showTouchArea

    val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
    if (savedBg != null && com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
        if (!prefs.videoBgWarnShown) {
            prefs.videoBgWarnShown = true
            keyboardView.showToast("提示：影片背景較耗電，可在主題設定改用圖片")
        }
    }
}

internal fun YukiBoardIMEService.initExternalEngines() {
    stickerRepository = com.snowrealm.yukiboard.sticker.StickerRepository(applicationContext)
    stickerDownloadManager = com.snowrealm.yukiboard.sticker.StickerPackDownloadManager(applicationContext)

    clawClient = ClawClient(applicationContext)
    voicePostProcessor = clawClient?.let { com.snowrealm.yukiboard.speech.VoicePostProcessor(it) }

    giphyClient = GiphyApiClient()
    translateEngine = TranslateEngine()

    yukiCommentEngine = com.snowrealm.yukiboard.yuki.YukiCommentEngine()
    emotionalCooldown = com.snowrealm.yukiboard.yuki.EmotionalCooldownManager()
    toneDetector = com.snowrealm.yukiboard.yuki.ToneDetector()
    secretMessageManager = com.snowrealm.yukiboard.yuki.SecretMessageManager()

    floatingPanelManager = com.snowrealm.yukiboard.ui.floating.FloatingPanelManager(applicationContext)
    val weatherMgr = com.snowrealm.yukiboard.weather.WeatherManager(applicationContext)
    weatherFloatingPanel = com.snowrealm.yukiboard.weather.WeatherFloatingPanel(applicationContext, weatherMgr)
    clawResultFloatingPanel = com.snowrealm.yukiboard.ui.floating.ClawResultFloatingPanel(applicationContext)
    weatherFloatingPanel?.let { floatingPanelManager?.registerPanel(it) }
    clawResultFloatingPanel?.let { floatingPanelManager?.registerPanel(it) }

    nextActionPredictor = com.snowrealm.yukiboard.input.predict.RuleBasedPredictor()

    initTtsEngine()
}

/**
 * Sprint 3 Phase 1: 第一批 uiState 消費端。
 * 由 serviceScope collect ImeSessionCoordinator.uiState 驅動。
 * 只處理 activePanel + isVoiceInputActive，不處理 composingText/candidates。
 */
internal fun YukiBoardIMEService.renderImeUiState(state: com.snowrealm.yukiboard.app.ime.ImeUiState) {
    toolbarView.activePanel = state.activePanel
    keyboardView.voiceListeningLocale = if (state.isVoiceInputActive) "auto" else null
}

/**
 * 啟動 uiState collect，在 onCreateInputView 最末尾呼叫。
 */
internal fun YukiBoardIMEService.startUiStateCollect() {
    uiStateCollectJob?.cancel()
    uiStateCollectJob = serviceScope.launch {
        imeCoordinator.uiState.collect { state ->
            renderImeUiState(state)
        }
    }
}

