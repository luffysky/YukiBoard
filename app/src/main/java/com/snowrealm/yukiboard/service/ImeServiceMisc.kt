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

internal fun YukiBoardIMEService.unregisterClipboardFraudListener() {
    val listener = clipboardListener ?: return
    try {
        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.removePrimaryClipChangedListener(listener)
    } catch (_: Exception) { }
    clipboardListener = null
}

internal fun YukiBoardIMEService.commitImageContent(url: String) {
    imeImageCommitter.commit(
        url = url,
        editorInfoProvider = { currentInputEditorInfo },
        inputConnectionProvider = { currentInputConnection },
    )
}

internal fun YukiBoardIMEService.updatePanelSelection(panel: PanelType?) {
    imePanelCoordinator.syncPanelSelection(panel) { selectedPanel ->
        activePanel = selectedPanel
        // toolbarView.activePanel 由 renderImeUiState collect 驅動（Sprint 3 Phase 1）
    }
}

internal fun YukiBoardIMEService.restorePanelOverlayOrder() {
    if (isPanelBehindKeyboard) {
        isPanelBehindKeyboard = false
        imeCoordinator.setPanelBehindKeyboard(false)
        panelContainer.bringToFront()
    }
    characterView?.bringToFront()
    yukiSpeechBubble?.bringToFront()
}

internal fun YukiBoardIMEService.restoreKeyboardBackgroundAfterPanelClose() {
    val bgHas = backgroundView?.reloadFromStorage() ?: false
    val useCustomBg = prefs.useCustomBackground && bgHas
    val glassKeys = useCustomBg || prefs.transparentKeyboard
    keyboardView.hasCustomBackground = glassKeys
    keyboardView.invalidate()
}

internal fun YukiBoardIMEService.showTransparencyOverlay() {
    imeTransparencyOverlay.onBgOverlayAlphaChanged = { p ->
        keyboardView.bgOverlayAlpha = p
        backgroundView?.overlayAlpha = p
        keyboardView.invalidate()
    }
    imeTransparencyOverlay.onKeyOpacityChanged = { p ->
        keyboardView.keyOpacity = p
        keyboardView.invalidate()
    }
    imeTransparencyOverlay.onKeyTextOpacityChanged = { p ->
        keyboardView.keyTextOpacity = p
        keyboardView.invalidate()
    }
    imeTransparencyOverlay.show(rootContainer, toolbarView)
}

internal fun YukiBoardIMEService.toggleOneHandMode() {
    val current = keyboardView.oneHandSide
    val next = when (current) {
        KeyboardView.OneHandSide.NONE -> KeyboardView.OneHandSide.RIGHT
        KeyboardView.OneHandSide.RIGHT -> KeyboardView.OneHandSide.LEFT
        KeyboardView.OneHandSide.LEFT -> KeyboardView.OneHandSide.NONE
    }
    keyboardView.oneHandSide = next
    prefs.oneHandSide = next.name
    val msg = when (next) {
        KeyboardView.OneHandSide.NONE -> "全寬鍵盤"
        KeyboardView.OneHandSide.LEFT -> "左手模式"
        KeyboardView.OneHandSide.RIGHT -> "右手模式"
    }
    keyboardView.showToast(msg)
}

internal fun YukiBoardIMEService.togglePanelLayer() {
    isPanelBehindKeyboard = !isPanelBehindKeyboard
    imeCoordinator.setPanelBehindKeyboard(isPanelBehindKeyboard)
    if (isPanelBehindKeyboard) {
        keyboardView.bringToFront()
    } else {
        panelContainer.bringToFront()
    }
    imePanelControlBar.updateSwapButton(isPanelBehindKeyboard)
    characterView?.bringToFront()
    yukiSpeechBubble?.bringToFront()
}

internal fun YukiBoardIMEService.calculateKeyboardHeight(): Int {
    val isLandscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenH = resources.displayMetrics.heightPixels
    return if (isLandscape) {
        // 橫向：螢幕高度較小，使用較高比例（至少 0.55，最多 0.75）
        val landscapeRatio = (prefs.keyboardHeightRatio * 1.6f).coerceIn(0.55f, 0.75f)
        (screenH * landscapeRatio).toInt()
    } else {
        (screenH * prefs.keyboardHeightRatio).toInt()
    }
}

internal fun YukiBoardIMEService.adjustKeyboardHeight(deltaY: Float) {
    val screenH = resources.displayMetrics.heightPixels
    val currentRatio = prefs.keyboardHeightRatio
    // deltaY 負 = 手指往上 = 鍵盤變大
    val newRatio = (currentRatio - deltaY / screenH).coerceIn(
        YukiPreferences.MIN_HEIGHT_RATIO,
        YukiPreferences.MAX_HEIGHT_RATIO
    )
    prefs.keyboardHeightRatio = newRatio

    val lp = keyboardContainer.layoutParams
    lp.height = (screenH * newRatio).toInt()
    keyboardContainer.layoutParams = lp
}

internal fun YukiBoardIMEService.applyKeyboardWidth(container: View) {
    val widthRatio = prefs.keyboardWidthRatio
    if (widthRatio < 1.0f) {
        val screenW = resources.displayMetrics.widthPixels
        val kbWidth = (screenW * widthRatio).toInt()
        container.layoutParams = FrameLayout.LayoutParams(kbWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
    }
}

internal fun YukiBoardIMEService.showImeDialog(dialog: android.app.AlertDialog) {
    // 先隱藏 IME 鍵盤，騰出螢幕空間
    requestHideSelf(0)

    dialog.window?.let { w ->
        if (android.provider.Settings.canDrawOverlays(this)) {
            // 使用 Overlay 視窗 — 不被 IME 擋住
            w.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            // Fallback: 用 IME token（可能被擋，但至少能顯示）
            w.attributes?.token = window?.window?.decorView?.windowToken
            w.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        }
        // ADJUST_PAN 確保輸入框不被鍵盤遮擋（ADJUST_RESIZE 在 overlay 視窗中常失效）
        w.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        // 把 Dialog 放到螢幕上半部，留空間給系統鍵盤
        w.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
        val lp = w.attributes
        lp.y = (48 * resources.displayMetrics.density).toInt()  // 頂部留 48dp
        w.attributes = lp
    }

    dialog.setOnDismissListener {
        // Dialog 關閉後不需手動恢復鍵盤 — 使用者點輸入框時系統會自動呼叫 IME
    }

    dialog.show()

    // 提示使用者授權（僅顯示一次）
    if (!android.provider.Settings.canDrawOverlays(this) && !prefs.overlayPermissionHintShown) {
        prefs.overlayPermissionHintShown = true
        keyboardView.post {
            keyboardView.showToast("請前往設定開啟「顯示在其他應用程式上層」權限，對話框才能正常輸入")
        }
    }
}

internal fun YukiBoardIMEService.applyTransparentMode() {
    val transparent = prefs.transparentKeyboard
    val w = window?.window ?: return

    if (transparent) {
        // IME 視窗背景透明 → 可看到底層 App
        w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        w.setDimAmount(0f)
        // 根容器也透明
        rootContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        toolbarView.setTransparent(true)
    } else {
        w.setBackgroundDrawable(null) // 還原系統預設
        toolbarView.setTransparent(false)
    }
}

internal fun YukiBoardIMEService.wrapInFloatingMode(content: LinearLayout): View {
    isFloatingMode = true
    imeCoordinator.setFloatingMode(true)
    backgroundView?.visibility = View.GONE

    val result = imeFloatingModeHelper.wrap(
        content = content,
        theme = ThemeManager.getInstance(this).currentTheme,
        onDock = {
            prefs.floatingModeEnabled = false
            prefs.floatingX = -1
            prefs.floatingY = -1
            closePanel()
            backgroundView?.destroy()
            backgroundView = null
            setInputView(onCreateInputView())
        },
        onClose = {
            prefs.floatingModeEnabled = false
            requestHideSelf(0)
        },
        onMinimize = ::minimizeToFloatingBall,
    )
    floatingWrapper = result.wrapper
    floatingOuterFrame = result.outerFrame
    return result.outerFrame
}

internal fun YukiBoardIMEService.minimizeToFloatingBall() {
    val outer = floatingOuterFrame ?: return
    val wrapper = floatingWrapper ?: return

    // 記住 wrapper 目前位置，球出現在同位置
    val wlp = wrapper.layoutParams as? FrameLayout.LayoutParams
    val ballX = wlp?.leftMargin ?: 0
    val ballY = wlp?.topMargin ?: 0

    // 隱藏 wrapper
    wrapper.visibility = View.GONE

    // 建立懸浮球
    val ball = FloatingBallView(this).also { floatingBall = it }
    ball.theme = ThemeManager.getInstance(this).currentTheme

    val screenW = resources.displayMetrics.widthPixels
    val outerH = outer.layoutParams?.height ?: (resources.displayMetrics.heightPixels * 0.65f).toInt()
    val ballLp = FrameLayout.LayoutParams(ball.ballSize, ball.ballSize).apply {
        gravity = Gravity.NO_GRAVITY
        leftMargin = (ballX + (wlp?.width ?: 0) / 2 - ball.ballSize / 2)
            .coerceIn(0, screenW - ball.ballSize)
        topMargin = ballY.coerceIn(0, outerH - ball.ballSize)
    }
    outer.addView(ball, ballLp)

    // 球拖動
    ball.onDragMove = { dx, dy ->
        val lp = ball.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = (lp.leftMargin + dx.toInt()).coerceIn(0, screenW - ball.ballSize)
        lp.topMargin = (lp.topMargin + dy.toInt()).coerceIn(0, outerH - ball.ballSize)
        ball.layoutParams = lp
    }

    // 球點擊展開
    ball.onExpand = {
        expandFromFloatingBall()
    }
}

internal fun YukiBoardIMEService.expandFromFloatingBall() {
    val outer = floatingOuterFrame ?: return
    val ball = floatingBall ?: return
    val wrapper = floatingWrapper ?: return

    // 把 wrapper 移到球的位置附近
    val ballLp = ball.layoutParams as? FrameLayout.LayoutParams
    if (ballLp != null) {
        val wlp = wrapper.layoutParams as? FrameLayout.LayoutParams
        if (wlp != null) {
            wlp.gravity = Gravity.NO_GRAVITY
            wlp.leftMargin = ballLp.leftMargin
            wlp.topMargin = ballLp.topMargin
            wrapper.layoutParams = wlp
            prefs.floatingX = wlp.leftMargin
            prefs.floatingY = wlp.topMargin
        }
    }

    // 移除球，顯示 wrapper
    outer.removeView(ball)
    floatingBall = null
    wrapper.visibility = View.VISIBLE
}

internal fun YukiBoardIMEService.toggleBluetooth() {
    val bt = btKeyboard
    if (bt != null && bt.connectionState != ConnectionState.IDLE) {
        bt.stop()
        updateRemotePanelState()
        return
    }

    val newBt = BluetoothHidKeyboard(applicationContext).also { btKeyboard = it }
    newBt.setOnStateChangedListener { state ->
        updateRemotePanelState()
    }
    newBt.setOnRemoteKeyListener { event ->
        // 收到遠端按鍵 → 注入本地 InputConnection
        handleRemoteKeyEvent(event)
    }
    newBt.setOnRemoteTextListener { text ->
        currentInputConnection?.commitText(text, 1)
    }
    newBt.start()
    updateRemotePanelState()
}

internal fun YukiBoardIMEService.toggleWifi() {
    val wifi = wifiServer
    if (wifi != null && wifi.connectionState != ConnectionState.IDLE) {
        wifi.stop()
        updateRemotePanelState()
        return
    }

    val newWifi = WifiKeyboardServer(applicationContext).also { wifiServer = it }
    newWifi.setOnStateChangedListener { state ->
        updateRemotePanelState()
    }
    newWifi.setOnRemoteKeyListener { event ->
        handleRemoteKeyEvent(event)
    }
    newWifi.setOnRemoteTextListener { text ->
        currentInputConnection?.commitText(text, 1)
    }
    newWifi.start()
    updateRemotePanelState()
}

internal fun YukiBoardIMEService.updateRemotePanelState() {
    val btConn = btKeyboard?.connectionState ?: ConnectionState.IDLE
    val wifiConn = wifiServer?.connectionState ?: ConnectionState.IDLE
    val effectiveState = when {
        btConn == ConnectionState.CONNECTED -> btConn
        wifiConn == ConnectionState.CONNECTED -> wifiConn
        btConn == ConnectionState.CONNECTING -> btConn
        wifiConn == ConnectionState.CONNECTING -> wifiConn
        else -> ConnectionState.IDLE
    }
    imeCoordinator.setRemoteConnectionState(effectiveState)
    imePanelBindings.remotePanel?.apply {
        btState = btConn
        wifiState = wifiConn
        wifiUrl = wifiServer?.getConnectionUrl() ?: ""
        connectedDeviceName = btKeyboard?.connectedDevice?.name
            ?: wifiServer?.connectedDevice?.name ?: ""
        invalidate()
    }
}

internal fun YukiBoardIMEService.handleRemoteKeyEvent(event: RemoteKeyEvent) {
    val ic = currentInputConnection ?: return
    if (event.character != null) {
        ic.commitText(event.character.toString(), 1)
    } else {
        val meta = event.modifiers
        sendKey(ic, event.keyCode, meta)
    }
}

internal fun YukiBoardIMEService.openSettings() {
    // 先隱藏 IME 視窗，避免系統在 Activity 顯示後重新拉回 IME 焦點導致 Activity 被關閉
    requestHideSelf(0)
    val intent = Intent(this, SettingsActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    startActivity(intent)
}

internal fun YukiBoardIMEService.enterGameMode() {
    isInGameMode = true
    imeCoordinator.setGameMode(true)
    closePanel()
    keyboardView.visibility = View.GONE
    glSurfaceView?.visibility = View.GONE

    showGameMenu()
}

internal fun YukiBoardIMEService.exitGameMode() {
    isInGameMode = false
    imeCoordinator.setGameMode(false)
    gameLoop?.stop()
    currentGame = null

    gameMenuView?.let { keyboardContainer.removeView(it) }
    gameKeyboardView?.let { keyboardContainer.removeView(it) }
    gameMenuView = null
    gameKeyboardView = null

    keyboardView.visibility = View.VISIBLE
    glSurfaceView?.visibility = View.VISIBLE
}

internal fun YukiBoardIMEService.showGameMenu() {
    gameKeyboardView?.let { keyboardContainer.removeView(it) }
    gameKeyboardView = null
    gameLoop?.stop()

    gameMenuView?.let { keyboardContainer.removeView(it) }

    val menu = GameMenuView(this, KeyboardTheme.default()) { selectedGame ->
        startGame(selectedGame)
    }
    menu.scoreManager = ScoreManager(applicationContext)
    menu.onBackToNormal = {
        exitGameMode()
        keyboardView.switchMode(KeyboardMode.NORMAL)
    }
    keyboardContainer.addView(menu)
    gameMenuView = menu
}

internal fun YukiBoardIMEService.startGame(gameMode: GameMode) {
    gameMenuView?.let { keyboardContainer.removeView(it) }
    gameMenuView = null

    val result = imeGameLauncher.launch(
        context = applicationContext,
        gameMode = gameMode,
        onBackToNormal = {
            exitGameMode()
            keyboardView.switchMode(KeyboardMode.NORMAL)
        },
        onGameEvent = ::handleGameEvent,
    )

    val gkv = result.gameKeyboardView
    keyboardContainer.addView(gkv, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
    gkv.onDetached = {
        gameLoop?.stop()
        gameLoop = null
    }
    gameKeyboardView = gkv
    currentGame = result.game
    gameLoop = result.loop
}

internal fun YukiBoardIMEService.rowsForAutoLayout(): List<List<KeyModel>> {
    return if (inputRouter.autoLayout == InputRouter.AutoLayout.QWERTY) {
        KeyboardState.defaultQwertyRows()
    } else {
        KeyboardState.bopomofoRows()
    }
}

internal fun YukiBoardIMEService.ttsSpeak(text: String, locale: String? = null, onDone: (() -> Unit)? = null) {
    if (speechEngine?.isListening == true) stopVoiceInput()
    ttsEngine?.speak(text, locale, prefs.ttsSpeed, onDone)
}

internal fun YukiBoardIMEService.onTranslateSpeakRequest(text: String, locale: String) {
    if (!prefs.ttsTranslateRead) return
    ttsSpeak(text, locale)
}

internal fun YukiBoardIMEService.onCandidateSpeak(text: String) {
    if (!prefs.ttsCandidateRead) return
    ttsSpeak(text)
}

internal fun YukiBoardIMEService.onYukiSpeak(text: String) {
    if (!prefs.ttsYukiVoice) return
    ttsSpeak(text)
}

internal fun YukiBoardIMEService.forwardKeyToRemote(keyCode: Int, modifiers: Int = 0) {
    val event = RemoteKeyEvent(keyCode, KeyAction.PRESS, modifiers)
    btKeyboard?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendKeyEvent(event)
    wifiServer?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendKeyEvent(event)
}

internal fun YukiBoardIMEService.forwardTextToRemote(text: String) {
    btKeyboard?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendText(text)
    wifiServer?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendText(text)
}

