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
import com.snowrealm.yukiboard.game.*
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
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.theme.ThemeManager
import com.snowrealm.yukiboard.ui.keyboard.CharacterView
import com.snowrealm.yukiboard.ui.keyboard.FloatingKeyboardWrapper
import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode
import com.snowrealm.yukiboard.ui.keyboard.KeyboardResizeHandle
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView
import com.snowrealm.yukiboard.ui.panel.*
import com.snowrealm.yukiboard.ui.settings.SettingsActivity
import com.snowrealm.yukiboard.claw.ClawClient
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

class YukiBoardIMEService : InputMethodService() {

    private lateinit var rootContainer: LinearLayout
    private lateinit var toolbarView: ToolbarView
    private lateinit var panelContainer: FrameLayout
    private lateinit var keyboardContainer: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var inputRouter: InputRouter
    private lateinit var snippetManager: SnippetManager
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var gyroController: GyroscopeCursorController
    private lateinit var appModeDetector: AppModeDetector
    private lateinit var prefs: YukiPreferences

    // GL 流光 + Lottie 角色 + 粒子 + 背景
    private var glSurfaceView: KeyboardGLSurfaceView? = null
    private var characterView: CharacterView? = null
    private var particleView: com.snowrealm.yukiboard.animation.KeyboardParticleView? = null
    private var backgroundView: com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView? = null

    // 面板
    private var activePanel: PanelType? = null
    private var emojiPanel: EmojiPanelView? = null
    private var kaomojiPanel: KaomojiPanelView? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private var translatePanel: TranslatePanelView? = null
    private var gifPanel: GifPanelView? = null
    private var stickerPanel: StickerPanelView? = null
    private var themePanel: ThemePanelView? = null

    // 貼圖同步
    private var stickerRepository: com.snowrealm.yukiboard.sticker.StickerRepository? = null

    // Claw AI
    var clawClient: ClawClient? = null
        private set

    // 語音輸入
    private var speechEngine: SpeechInputEngine? = null

    // 遠端鍵盤
    private var btKeyboard: BluetoothHidKeyboard? = null
    private var wifiServer: WifiKeyboardServer? = null
    private var remotePanel: RemoteKeyboardPanelView? = null

    // 鍵盤尺寸調整 + 懸浮
    private var resizeHandle: KeyboardResizeHandle? = null
    private var floatingWrapper: FloatingKeyboardWrapper? = null
    private var isFloatingMode = false

    // 遊戲相關
    private var gameMenuView: GameMenuView? = null
    private var gameKeyboardView: GameKeyboardView? = null
    private var gameLoop: GameLoopRunner? = null
    private var currentGame: GameLogic? = null
    private var isInGameMode = false

    private var manualModeOverride = false

    override fun onCreate() {
        super.onCreate()
        inputRouter = InputRouter(applicationContext)
        snippetManager = SnippetManager(applicationContext)
        appModeDetector = AppModeDetector()
        prefs = YukiPreferences(applicationContext)

        shakeDetector = ShakeDetector(applicationContext)
        shakeDetector.setOnShakeListener {
            if (prefs.shakeToUndoEnabled) {
                val ic = currentInputConnection ?: return@setOnShakeListener
                sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
                keyboardView.vibrateShort()
            }
        }

        gyroController = GyroscopeCursorController(applicationContext)
        gyroController.setOnCursorMoveListener { direction ->
            if (prefs.gyroscopeCursorEnabled) {
                val ic = currentInputConnection ?: return@setOnCursorMoveListener
                val keyCode = when (direction) {
                    CursorDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    CursorDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                }
                sendKey(ic, keyCode)
            }
        }

        // 貼圖同步（從 Insight Engine 後台拉取）
        stickerRepository = com.snowrealm.yukiboard.sticker.StickerRepository(applicationContext)
        stickerRepository?.syncInBackground()

        // Claw AI（自動從 BuildConfig 匯入 API key）
        clawClient = ClawClient(applicationContext)
    }

    override fun onEvaluateInputViewShown(): Boolean = true
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean = true

    override fun onCreateInputView(): View {
        // 垂直佈局：工具列 → 面板容器 → 鍵盤容器
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 高度調整把手
        val handle = KeyboardResizeHandle(this).also { resizeHandle = it }
        handle.onHeightDrag = { deltaY -> adjustKeyboardHeight(deltaY) }
        rootContainer.addView(handle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 工具列
        toolbarView = ToolbarView(this)
        toolbarView.onPanelSelected = { panel -> handlePanelToggle(panel) }
        rootContainer.addView(toolbarView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 鍵盤容器（FrameLayout 疊加鍵盤 + GL + Lottie + 面板覆蓋）
        keyboardContainer = FrameLayout(this)

        keyboardView = KeyboardView(this)
        keyboardView.setOnKeyPressListener { keyCode, label ->
            handleKeyPress(keyCode, label)
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
        keyboardView.setOnCandidateSelectListener { index ->
            val ic = currentInputConnection ?: return@setOnCandidateSelectListener
            val result = inputRouter.selectCandidate(index)
            applyInputResult(ic, result)
            keyboardView.updateComposingText(inputRouter.composingText)
            keyboardView.updateCandidates(inputRouter.candidates)
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

        // 自訂圖片/影片背景（最底層）
        val bgView = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView(this)
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
        if (savedBg != null) {
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

        keyboardContainer.addView(keyboardView)

        // 套用當前主題
        val currentTheme = ThemeManager.getInstance(this).currentTheme
        keyboardView.applyTheme(currentTheme)
        toolbarView.theme = currentTheme

        // 動態粒子覆蓋層（在鍵盤上方、GL 下方）
        val pv = com.snowrealm.yukiboard.animation.KeyboardParticleView(this)
        pv.setTheme(currentTheme)
        pv.start()
        keyboardContainer.addView(pv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        particleView = pv

        // GL 流光透明覆蓋層
        val gl = KeyboardGLSurfaceView(this)
        gl.setFlowAlpha(currentTheme.flowLightAlpha)
        gl.setFlowColor(currentTheme.flowLightColorInt)
        keyboardContainer.addView(gl, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        glSurfaceView = gl

        // Lottie 角色浮動在右上角
        val charSize = (56 * resources.displayMetrics.density).toInt()
        val charView = CharacterView(this)
        val charParams = FrameLayout.LayoutParams(charSize, charSize).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        keyboardContainer.addView(charView, charParams)
        characterView = charView

        // 面板容器（覆蓋在鍵盤上方，從頂部對齊）
        panelContainer = FrameLayout(this)
        panelContainer.visibility = View.GONE
        keyboardContainer.addView(panelContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP
        ))

        // 套用儲存的鍵盤高度
        val screenH = resources.displayMetrics.heightPixels
        val kbHeight = (screenH * prefs.keyboardHeightRatio).toInt()
        rootContainer.addView(keyboardContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            kbHeight
        ))

        // 懸浮模式
        if (prefs.floatingModeEnabled) {
            return wrapInFloatingMode(rootContainer)
        }

        // 寬度調整（非滿版時置中）
        applyKeyboardWidth(rootContainer)

        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputRouter.reset()
        keyboardView.updateEditorInfo(info)

        if (prefs.appAutoDetectEnabled && !manualModeOverride) {
            val detectedMode = appModeDetector.detectMode(info?.packageName)
            if (detectedMode != keyboardView.currentMode) {
                keyboardView.switchMode(detectedMode)
            }
        }

        if (prefs.shakeToUndoEnabled) shakeDetector.start()
        if (prefs.gyroscopeCursorEnabled) gyroController.register()

        glSurfaceView?.onResume()

        // 遊戲模式恢復
        if (isInGameMode) {
            gameLoop?.resume()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inputRouter.reset()
        shakeDetector.stop()
        gyroController.deactivate()
        gyroController.unregister()
        manualModeOverride = false

        glSurfaceView?.onPause()

        // 遊戲暫停
        gameLoop?.pause()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        inputRouter.reset()
        shakeDetector.stop()
        gyroController.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeDetector.stop()
        gyroController.unregister()
        gameLoop?.stop()
        speechEngine?.destroy()
        btKeyboard?.destroy()
        wifiServer?.destroy()
        stickerRepository?.destroy()
        particleView?.stop()
        backgroundView?.destroy()
    }

    // =========================================================================
    // 面板切換系統
    // =========================================================================

    private fun handlePanelToggle(panel: PanelType) {
        // 再次點擊同一面板 → 關閉
        if (activePanel == panel) {
            closePanel()
            return
        }

        activePanel = panel
        toolbarView.activePanel = panel

        when (panel) {
            PanelType.VOICE -> {
                closePanel()
                toggleVoiceInput()
                return
            }
            PanelType.SETTINGS -> {
                closePanel()
                openSettings()
                return
            }
            else -> showPanel(panel)
        }
    }

    private fun showPanel(panel: PanelType) {
        panelContainer.removeAllViews()
        panelContainer.visibility = View.VISIBLE

        val theme = ThemeManager.getInstance(this).currentTheme
        val panelView: View = when (panel) {
            PanelType.EMOJI -> {
                val p = EmojiPanelView(this).also { emojiPanel = it }
                p.theme = theme
                p.onEmojiSelected = { emoji ->
                    currentInputConnection?.commitText(emoji, 1)
                }
                p
            }
            PanelType.KAOMOJI -> {
                val p = KaomojiPanelView(this).also { kaomojiPanel = it }
                p.theme = theme
                p.onKaomojiSelected = { kaomoji ->
                    currentInputConnection?.commitText(kaomoji, 1)
                }
                p
            }
            PanelType.CLIPBOARD -> {
                val p = ClipboardPanelView(this).also { clipboardPanel = it }
                p.theme = theme
                p.captureCurrentClip()
                p.onClipSelected = { text ->
                    currentInputConnection?.commitText(text, 1)
                }
                p
            }
            PanelType.TRANSLATE -> {
                val p = TranslatePanelView(this).also { translatePanel = it }
                p.theme = theme
                // 從游標前方取得文字作為翻譯來源
                val before = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString() ?: ""
                p.sourceText = before.trim().takeLast(100)
                p.onTranslatedSelected = { translated ->
                    currentInputConnection?.commitText(translated, 1)
                }
                p
            }
            PanelType.GIF -> {
                val p = GifPanelView(this).also { gifPanel = it }
                p.theme = theme
                p.onGifSelected = { gif ->
                    // MVP: 插入 GIF URL 文字（完整實作需 commitContent）
                    currentInputConnection?.commitText(gif.fullUrl, 1)
                }
                p
            }
            PanelType.STICKER -> {
                val p = StickerPanelView(this).also { stickerPanel = it }
                p.theme = theme
                p.repository = stickerRepository
                p.onStickerSelected = { stickerText ->
                    // 插入 [STICKER]:<url> 格式（同 Insight 私訊）
                    currentInputConnection?.commitText(stickerText, 1)
                }
                p
            }
            PanelType.REMOTE -> {
                val p = RemoteKeyboardPanelView(this).also { remotePanel = it }
                p.theme = theme
                p.btState = btKeyboard?.connectionState ?: ConnectionState.IDLE
                p.wifiState = wifiServer?.connectionState ?: ConnectionState.IDLE
                p.wifiUrl = wifiServer?.getConnectionUrl() ?: ""
                p.connectedDeviceName = btKeyboard?.connectedDevice?.name
                    ?: wifiServer?.connectedDevice?.name ?: ""
                p.onBluetoothToggle = { toggleBluetooth() }
                p.onWifiToggle = { toggleWifi() }
                p
            }
            PanelType.THEME -> {
                val tm = ThemeManager.getInstance(this)
                val p = ThemePanelView(this).also { themePanel = it }
                p.theme = theme
                p.currentThemeId = tm.currentTheme.id
                p.availableThemes = tm.availableThemes
                p.onThemeSelected = { selected ->
                    tm.currentTheme = selected
                    applyTheme(selected)
                }
                p
            }
            PanelType.CLAW -> {
                val p = ClawPanelView(this)
                p.theme = theme
                p.onSuggestionSelected = { text ->
                    currentInputConnection?.commitText(text, 1)
                }
                p.onClose = { closePanel() }
                p.onLockTapped = {
                    startActivity(
                        Intent(this, com.snowrealm.yukiboard.ui.auth.InviteCodeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                p
            }
            else -> return
        }

        panelContainer.addView(panelView)
    }

    private fun closePanel() {
        activePanel = null
        toolbarView.activePanel = null
        panelContainer.removeAllViews()
        panelContainer.visibility = View.GONE
    }

    private fun applyTheme(theme: KeyboardTheme) {
        toolbarView.theme = theme
        keyboardView.applyTheme(theme)
        glSurfaceView?.setFlowAlpha(theme.flowLightAlpha)
        glSurfaceView?.setFlowColor(theme.flowLightColorInt)
        particleView?.setTheme(theme)
    }

    // =========================================================================
    // 語音輸入
    // =========================================================================

    private fun toggleVoiceInput() {
        if (speechEngine?.isListening == true) {
            stopVoiceInput()
            return
        }

        if (speechEngine == null) {
            speechEngine = AndroidSpeechEngine(applicationContext)
        }

        characterView?.playListening()

        val locale = if (inputRouter.inputMode == com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO) {
            "zh-TW"
        } else {
            "en-US"
        }

        speechEngine?.startListening(
            locale = locale,
            onPartialResult = { partial ->
                currentInputConnection?.setComposingText(partial, 1)
            },
            onFinalResult = { result ->
                val ic = currentInputConnection ?: return@startListening
                ic.finishComposingText()
                if (result.isNotEmpty()) {
                    ic.commitText(result, 1)
                    characterView?.playHappy()
                } else {
                    characterView?.playIdle()
                }
            },
            onError = { _ ->
                characterView?.playIdle()
                currentInputConnection?.finishComposingText()
            }
        )
    }

    private fun stopVoiceInput() {
        speechEngine?.stopListening()
        characterView?.playIdle()
    }

    // =========================================================================
    // 鍵盤尺寸調整
    // =========================================================================

    private fun adjustKeyboardHeight(deltaY: Float) {
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

    private fun applyKeyboardWidth(container: View) {
        val widthRatio = prefs.keyboardWidthRatio
        if (widthRatio < 1.0f) {
            val screenW = resources.displayMetrics.widthPixels
            val kbWidth = (screenW * widthRatio).toInt()
            container.layoutParams = FrameLayout.LayoutParams(kbWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            }
        }
    }

    // =========================================================================
    // 懸浮模式
    // =========================================================================

    private fun wrapInFloatingMode(content: LinearLayout): View {
        isFloatingMode = true
        val wrapper = FloatingKeyboardWrapper(this).also { floatingWrapper = it }
        wrapper.theme = ThemeManager.getInstance(this).currentTheme
        wrapper.setWillNotDraw(false)

        val density = resources.displayMetrics.density
        val dragBarH = (24 * density).toInt()
        val actionBarH = (28 * density).toInt()

        // 鍵盤內容放在中間
        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dragBarH
            bottomMargin = actionBarH
        }
        wrapper.addView(content, contentParams)

        // 寬度調整
        val screenW = resources.displayMetrics.widthPixels
        val kbWidth = (screenW * prefs.keyboardWidthRatio).toInt()
        val wrapperParams = FrameLayout.LayoutParams(kbWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        val outerFrame = FrameLayout(this)
        outerFrame.addView(wrapper, wrapperParams)

        // 拖動回呼
        wrapper.onDragMove = { dx, dy ->
            val lp = wrapper.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin += dx.toInt()
            lp.topMargin += dy.toInt()
            lp.gravity = Gravity.NO_GRAVITY  // 解除 gravity 才能自由移動
            wrapper.layoutParams = lp
            prefs.floatingX = lp.leftMargin
            prefs.floatingY = lp.topMargin
        }

        wrapper.onToggleDock = {
            prefs.floatingModeEnabled = false
            // 需要重建 InputView
            setInputView(onCreateInputView())
        }

        wrapper.onClose = {
            prefs.floatingModeEnabled = false
            setInputView(onCreateInputView())
        }

        // 恢復上次位置
        if (prefs.floatingX >= 0) {
            wrapperParams.gravity = Gravity.NO_GRAVITY
            wrapperParams.leftMargin = prefs.floatingX
            wrapperParams.topMargin = prefs.floatingY
        }

        return outerFrame
    }

    // =========================================================================
    // 遠端鍵盤（藍牙 + WiFi）
    // =========================================================================

    private fun toggleBluetooth() {
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

    private fun toggleWifi() {
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

    private fun updateRemotePanelState() {
        remotePanel?.apply {
            btState = btKeyboard?.connectionState ?: ConnectionState.IDLE
            wifiState = wifiServer?.connectionState ?: ConnectionState.IDLE
            wifiUrl = wifiServer?.getConnectionUrl() ?: ""
            connectedDeviceName = btKeyboard?.connectedDevice?.name
                ?: wifiServer?.connectedDevice?.name ?: ""
            invalidate()
        }
    }

    /**
     * 將本地按鍵同時轉發到遠端裝置（手機→電腦）。
     */
    private fun forwardKeyToRemote(keyCode: Int, modifiers: Int = 0) {
        val event = RemoteKeyEvent(keyCode, KeyAction.PRESS, modifiers)
        btKeyboard?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendKeyEvent(event)
        wifiServer?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendKeyEvent(event)
    }

    /**
     * 將本地 commit 的文字同時轉發到遠端裝置。
     */
    private fun forwardTextToRemote(text: String) {
        btKeyboard?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendText(text)
        wifiServer?.takeIf { it.connectionState == ConnectionState.CONNECTED }?.sendText(text)
    }

    /**
     * 處理從遠端收到的按鍵事件（電腦→手機）。
     */
    private fun handleRemoteKeyEvent(event: RemoteKeyEvent) {
        val ic = currentInputConnection ?: return
        if (event.character != null) {
            ic.commitText(event.character.toString(), 1)
        } else {
            val meta = event.modifiers
            sendKey(ic, event.keyCode, meta)
        }
    }

    // =========================================================================
    // 設定頁面
    // =========================================================================

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // =========================================================================
    // 遊戲模式
    // =========================================================================

    private fun enterGameMode() {
        isInGameMode = true
        closePanel()
        keyboardView.visibility = View.GONE
        glSurfaceView?.visibility = View.GONE

        showGameMenu()
    }

    private fun exitGameMode() {
        isInGameMode = false
        gameLoop?.stop()
        currentGame = null

        gameMenuView?.let { keyboardContainer.removeView(it) }
        gameKeyboardView?.let { keyboardContainer.removeView(it) }
        gameMenuView = null
        gameKeyboardView = null

        keyboardView.visibility = View.VISIBLE
        glSurfaceView?.visibility = View.VISIBLE
    }

    private fun showGameMenu() {
        gameKeyboardView?.let { keyboardContainer.removeView(it) }
        gameKeyboardView = null
        gameLoop?.stop()

        gameMenuView?.let { keyboardContainer.removeView(it) }

        val menu = GameMenuView(this, KeyboardTheme.default()) { selectedGame ->
            startGame(selectedGame)
        }
        menu.scoreManager = ScoreManager(applicationContext)
        keyboardContainer.addView(menu)
        gameMenuView = menu
    }

    private fun startGame(gameMode: GameMode) {
        gameMenuView?.let { keyboardContainer.removeView(it) }
        gameMenuView = null

        // 建立遊戲
        val game: GameLogic = when (gameMode) {
            GameMode.SNAKE -> SnakeGame(applicationContext)
            GameMode.WHACK_A_MOLE -> WhackAMoleGame(applicationContext)
            GameMode.RHYTHM -> RhythmGame(applicationContext)
            GameMode.TYPING_CHALLENGE -> TypingChallengeGame(applicationContext)
        }

        // 建立遊戲鍵盤 View
        val gkv = GameKeyboardView(this)
        gkv.setTheme(KeyboardTheme.default())

        // 用 QWERTY 鍵盤行數/列數作為遊戲網格
        val rows = KeyboardState.defaultQwertyRows()
        val gridRows = rows.size
        val gridCols = rows.maxOfOrNull { it.size } ?: 10

        game.init(gridRows, gridCols)
        gkv.attachGame(game)

        // 遊戲事件回呼
        when (game) {
            is SnakeGame -> game.onGameEvent = { handleGameEvent(it, game) }
            is WhackAMoleGame -> game.onGameEvent = { handleGameEvent(it, game) }
            is RhythmGame -> game.onGameEvent = { handleGameEvent(it, game) }
            is TypingChallengeGame -> game.onGameEvent = { handleGameEvent(it, game) }
        }

        keyboardContainer.addView(gkv)
        gameKeyboardView = gkv
        currentGame = game

        // 啟動遊戲迴圈
        val loop = GameLoopRunner()
        loop.attach(game) {
            gkv.invalidate()
        }
        game.start()
        loop.start()
        gameLoop = loop
    }

    private fun handleGameEvent(event: GameEvent, game: GameLogic) {
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
                gkv.postDelayed({ showGameMenu() }, 3000)
            }
            GameEvent.VICTORY -> {
                game.scoreManager.saveHighScoreIfNeeded(game.gameMode)
                gkv.hudText = "勝利! ${game.scoreManager.currentScore} 分"
                characterView?.playVictory()
                gkv.postDelayed({ showGameMenu() }, 3000)
            }
        }
    }

    // =========================================================================
    // 按鍵處理核心
    // =========================================================================

    private fun handleKeyPress(keyCode: Int, label: String) {
        val ic: InputConnection = currentInputConnection ?: return

        when (keyCode) {
            KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> { }

            KeyboardView.KEYCODE_TAB         -> handleTab(ic)
            KeyboardView.KEYCODE_ARROW_LEFT  -> sendArrow(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            KeyboardView.KEYCODE_ARROW_RIGHT -> sendArrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyboardView.KEYCODE_ARROW_UP    -> sendArrow(ic, KeyEvent.KEYCODE_DPAD_UP)
            KeyboardView.KEYCODE_ARROW_DOWN  -> sendArrow(ic, KeyEvent.KEYCODE_DPAD_DOWN)

            KeyboardView.KEYCODE_DELETE      -> handleDelete(ic)
            KeyboardView.KEYCODE_ENTER       -> handleEnter(ic)
            KeyboardView.KEYCODE_SPACE       -> handleSpace(ic)
            KeyboardView.KEYCODE_SWITCH_LANG -> handleSwitchLang()

            KeyboardView.KEYCODE_CTRL_C -> ic.performContextMenuAction(android.R.id.copy)
            KeyboardView.KEYCODE_CTRL_V -> ic.performContextMenuAction(android.R.id.paste)
            KeyboardView.KEYCODE_CTRL_X -> ic.performContextMenuAction(android.R.id.cut)
            KeyboardView.KEYCODE_CTRL_A -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyboardView.KEYCODE_CTRL_Z -> sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
            KeyboardView.KEYCODE_CTRL_Y -> sendCtrlKey(ic, KeyEvent.KEYCODE_Y)

            // 設計者模式
            KeyboardView.KEYCODE_DESIGN_GROUP      -> sendCtrlKey(ic, KeyEvent.KEYCODE_G)
            KeyboardView.KEYCODE_DESIGN_UNGROUP    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_G)
            KeyboardView.KEYCODE_DESIGN_DUPLICATE  -> sendCtrlKey(ic, KeyEvent.KEYCODE_D)
            KeyboardView.KEYCODE_DESIGN_LOCK       -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_L)
            KeyboardView.KEYCODE_DESIGN_HIDE       -> sendCtrlKey(ic, KeyEvent.KEYCODE_H)
            KeyboardView.KEYCODE_DESIGN_ALIGN_L    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_L)
            KeyboardView.KEYCODE_DESIGN_ALIGN_C    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_C)
            KeyboardView.KEYCODE_DESIGN_ALIGN_R    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_R)
            KeyboardView.KEYCODE_DESIGN_ZOOM_IN    -> sendCtrlKey(ic, KeyEvent.KEYCODE_EQUALS)
            KeyboardView.KEYCODE_DESIGN_ZOOM_OUT   -> sendCtrlKey(ic, KeyEvent.KEYCODE_MINUS)
            KeyboardView.KEYCODE_DESIGN_ZOOM_FIT   -> sendCtrlKey(ic, KeyEvent.KEYCODE_0)
            KeyboardView.KEYCODE_DESIGN_BOLD       -> sendCtrlKey(ic, KeyEvent.KEYCODE_B)
            KeyboardView.KEYCODE_DESIGN_ITALIC     -> sendCtrlKey(ic, KeyEvent.KEYCODE_I)
            KeyboardView.KEYCODE_DESIGN_UNDERLINE  -> sendCtrlKey(ic, KeyEvent.KEYCODE_U)
            KeyboardView.KEYCODE_DESIGN_FONT_UP    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_PERIOD)
            KeyboardView.KEYCODE_DESIGN_FONT_DOWN  -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_COMMA)
            KeyboardView.KEYCODE_DESIGN_LAYER_UP   -> sendCtrlKey(ic, KeyEvent.KEYCODE_RIGHT_BRACKET)
            KeyboardView.KEYCODE_DESIGN_LAYER_DOWN -> sendCtrlKey(ic, KeyEvent.KEYCODE_LEFT_BRACKET)
            KeyboardView.KEYCODE_DESIGN_LAYER_TOP  -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_RIGHT_BRACKET)
            KeyboardView.KEYCODE_DESIGN_LAYER_BOT  -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_LEFT_BRACKET)
            KeyboardView.KEYCODE_DESIGN_FLIP_H     -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_H)
            KeyboardView.KEYCODE_DESIGN_FLIP_V     -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_V)

            // 開發者模式
            KeyboardView.KEYCODE_DEV_FIND      -> sendCtrlKey(ic, KeyEvent.KEYCODE_F)
            KeyboardView.KEYCODE_DEV_COMMENT   -> sendCtrlKey(ic, KeyEvent.KEYCODE_SLASH)
            KeyboardView.KEYCODE_DEV_HOME      -> sendKey(ic, KeyEvent.KEYCODE_MOVE_HOME)
            KeyboardView.KEYCODE_DEV_END       -> sendKey(ic, KeyEvent.KEYCODE_MOVE_END)
            KeyboardView.KEYCODE_DEV_FORMAT    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_F)
            KeyboardView.KEYCODE_DEV_LINE_UP   -> sendAltKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            KeyboardView.KEYCODE_DEV_LINE_DOWN -> sendAltKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)
            KeyboardView.KEYCODE_DEV_FOLD      -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_LEFT_BRACKET)
            KeyboardView.KEYCODE_DEV_UNFOLD    -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_RIGHT_BRACKET)
            KeyboardView.KEYCODE_DEV_INDENT    -> handleTab(ic)
            KeyboardView.KEYCODE_DEV_UNINDENT  -> sendKey(ic, KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON)
            KeyboardView.KEYCODE_DEV_ARROW         -> ic.commitText("->", 1)
            KeyboardView.KEYCODE_DEV_FAT_ARROW     -> ic.commitText("=>", 1)
            KeyboardView.KEYCODE_DEV_DSLASH        -> ic.commitText("//", 1)
            KeyboardView.KEYCODE_DEV_COMMENT_BLOCK -> {
                ic.commitText("/*  */", 1)
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
                sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            }

            else -> {
                val result = inputRouter.processInput(label)
                applyInputResult(ic, result)
            }
        }

        // 觸發角色打字動畫
        characterView?.playTyping()

        keyboardView.updateComposingText(inputRouter.composingText)
        keyboardView.updateCandidates(inputRouter.candidates)
    }

    // =========================================================================
    // Snippet
    // =========================================================================

    private fun tryExpandSnippet(ic: InputConnection): Boolean {
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

    // =========================================================================
    // 按鍵發送工具
    // =========================================================================

    private fun sendKey(ic: InputConnection, androidKeyCode: Int, meta: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeyCode, 0, meta))
    }

    private fun sendCtrlKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_CTRL_ON)

    private fun sendCtrlShiftKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

    private fun sendAltKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_ALT_ON)

    private fun sendArrow(ic: InputConnection, androidKeyCode: Int) {
        val meta = if (keyboardView.isShiftActive()) KeyEvent.META_SHIFT_ON else 0
        sendKey(ic, androidKeyCode, meta)
    }

    private fun handleTab(ic: InputConnection) {
        if (tryExpandSnippet(ic)) return
        val meta = if (keyboardView.isShiftActive()) KeyEvent.META_SHIFT_ON else 0
        sendKey(ic, KeyEvent.KEYCODE_TAB, meta)
    }

    // =========================================================================
    // 輸入處理
    // =========================================================================

    private fun handleDelete(ic: InputConnection) {
        when (val result = inputRouter.processDelete()) {
            is InputResult.ComposingUpdated -> ic.setComposingText(result.composing, 1)
            is InputResult.ComposingCleared -> {
                ic.finishComposingText()
                ic.deleteSurroundingText(1, 0)
            }
            else -> ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter(ic: InputConnection) {
        val result = inputRouter.processEnter()
        applyInputResult(ic, result)
        if (result is InputResult.Idle) sendDefaultEditorAction(true)
    }

    private fun handleSpace(ic: InputConnection) {
        applyInputResult(ic, inputRouter.processSpace())
    }

    private fun handleSwitchLang() {
        inputRouter.toggleLanguage()
        characterView?.playSwitchLang()
        val rows = if (inputRouter.inputMode == com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO) {
            KeyboardState.bopomofoRows()
        } else {
            KeyboardState.defaultQwertyRows()
        }
        keyboardView.updateKeyRows(rows)
    }

    private fun applyInputResult(ic: InputConnection, result: InputResult) {
        when (result) {
            is InputResult.CommitText -> {
                ic.setComposingText("", 0)
                ic.finishComposingText()
                ic.commitText(result.text, 1)
                characterView?.playHappy()
                forwardTextToRemote(result.text)
            }
            is InputResult.ComposingUpdated -> ic.setComposingText(result.composing, 1)
            is InputResult.ComposingCleared -> ic.finishComposingText()
            is InputResult.Idle -> { }
        }
    }
}
