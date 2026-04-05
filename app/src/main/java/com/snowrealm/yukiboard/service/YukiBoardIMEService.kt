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

class YukiBoardIMEService : InputMethodService() {

    private lateinit var rootContainer: LinearLayout
    private lateinit var toolbarView: ToolbarView
    private lateinit var panelContainer: FrameLayout
    private lateinit var keyboardContainer: FrameLayout
    private var panelControlBar: View? = null
    private var isPanelBehindKeyboard = false
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

    // 貼圖同步 + 下載管理
    private var stickerRepository: com.snowrealm.yukiboard.sticker.StickerRepository? = null
    private var stickerDownloadManager: com.snowrealm.yukiboard.sticker.StickerPackDownloadManager? = null

    // 非同步
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Claw AI
    var clawClient: ClawClient? = null
        private set

    // GIF + 翻譯
    private var giphyClient: GiphyApiClient? = null
    private var translateEngine: TranslateEngine? = null

    // 語音輸入
    private var speechEngine: SpeechInputEngine? = null
    private var voiceOverlay: com.snowrealm.yukiboard.ui.speech.VoiceRecognitionOverlay? = null

    // TTS 語音輸出
    private var ttsEngine: com.snowrealm.yukiboard.speech.SpeechOutputEngine? = null

    // AI 候選字 debounce
    private var aiCandidateJob: Job? = null
    private val AI_CANDIDATE_DEBOUNCE_MS = 600L

    // 雪凜系統
    private var yukiCommentEngine: com.snowrealm.yukiboard.yuki.YukiCommentEngine? = null
    private var yukiSpeechBubble: com.snowrealm.yukiboard.yuki.YukiSpeechBubble? = null
    private var emotionalCooldown: com.snowrealm.yukiboard.yuki.EmotionalCooldownManager? = null
    private var toneDetector: com.snowrealm.yukiboard.yuki.ToneDetector? = null
    private var secretMessageManager: com.snowrealm.yukiboard.yuki.SecretMessageManager? = null

    // 懸浮面板
    private var floatingPanelManager: com.snowrealm.yukiboard.ui.floating.FloatingPanelManager? = null
    private var weatherFloatingPanel: com.snowrealm.yukiboard.weather.WeatherFloatingPanel? = null
    private var clawResultFloatingPanel: com.snowrealm.yukiboard.ui.floating.ClawResultFloatingPanel? = null

    // 下一步動作預測
    private var nextActionPredictor: com.snowrealm.yukiboard.input.predict.NextActionPredictor? = null

    // 私房話累積緩衝
    private val sentenceBuffer = StringBuilder()

    // 打字事件追蹤（微吐槽用）
    private var consecutiveDeleteCount = 0
    private var lastDeletedTextLength = 0
    private var lastTypedChar: Char? = null
    private var sameCharCount = 0

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

        // 預熱注音索引（IO 執行緒，避免首次按鍵凍結 UI）
        serviceScope.launch(Dispatchers.IO) {
            inputRouter.warmUpChewing()
        }

        shakeDetector = ShakeDetector(applicationContext)
        shakeDetector.setOnShakeListener {
            if (prefs.shakeToUndoEnabled && !isInGameMode) {
                val ic = currentInputConnection ?: return@setOnShakeListener
                sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
                keyboardView.vibrateShort()
            }
        }

        gyroController = GyroscopeCursorController(applicationContext)
        gyroController.setOnCursorMoveListener { direction ->
            if (prefs.gyroscopeCursorEnabled) {
                val ic = currentInputConnection ?: return@setOnCursorMoveListener
                // F-8: composing 非空時先 forceCommit，避免游標移入 composing 文字中
                if (inputRouter.composingText.isNotEmpty()) {
                    val result = inputRouter.getActiveEngine().forceCommit()
                    if (result is InputResult.CommitText) ic.commitText(result.text, 1)
                    ic.finishComposingText()
                    inputRouter.reset()
                    keyboardView.updateComposingText("")
                    keyboardView.updateCandidates(emptyList())
                }
                val keyCode = when (direction) {
                    CursorDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    CursorDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                }
                sendKey(ic, keyCode)
            }
        }

        // 貼圖同步 + 下載管理
        stickerRepository = com.snowrealm.yukiboard.sticker.StickerRepository(applicationContext)
        stickerRepository?.syncInBackground()
        stickerDownloadManager = com.snowrealm.yukiboard.sticker.StickerPackDownloadManager(applicationContext)

        // Claw AI（自動從 BuildConfig 匯入 API key）
        clawClient = ClawClient(applicationContext)

        // GIF + 翻譯引擎
        giphyClient = GiphyApiClient()
        translateEngine = TranslateEngine()

        // 雪凜系統
        yukiCommentEngine = com.snowrealm.yukiboard.yuki.YukiCommentEngine()
        emotionalCooldown = com.snowrealm.yukiboard.yuki.EmotionalCooldownManager()
        toneDetector = com.snowrealm.yukiboard.yuki.ToneDetector()
        secretMessageManager = com.snowrealm.yukiboard.yuki.SecretMessageManager()

        // 懸浮面板管理 + 註冊懸浮面板
        floatingPanelManager = com.snowrealm.yukiboard.ui.floating.FloatingPanelManager(applicationContext)
        val weatherMgr = com.snowrealm.yukiboard.weather.WeatherManager(applicationContext)
        weatherFloatingPanel = com.snowrealm.yukiboard.weather.WeatherFloatingPanel(applicationContext, weatherMgr)
        clawResultFloatingPanel = com.snowrealm.yukiboard.ui.floating.ClawResultFloatingPanel(applicationContext)
        floatingPanelManager?.registerPanel(weatherFloatingPanel!!)
        floatingPanelManager?.registerPanel(clawResultFloatingPanel!!)

        // 下一步動作預測
        nextActionPredictor = com.snowrealm.yukiboard.input.predict.RuleBasedPredictor()

        // TTS 語音輸出引擎
        initTtsEngine()

        // 懸浮面板回調：AI 結果插入輸入框
        clawResultFloatingPanel?.onInsertToInput = { text ->
            val ic = currentInputConnection
            ic?.commitText(text, 1)
        }
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

        // 語音辨識 Overlay（液態玻璃氣泡框）
        voiceOverlay = com.snowrealm.yukiboard.ui.speech.VoiceRecognitionOverlay(this).also {
            it.onConfirm = { text ->
                currentInputConnection?.commitText(text, 1)
                characterView?.playHappy()
                keyboardView.voiceListeningLocale = null
            }
            it.onCancel = {
                speechEngine?.cancel()
                characterView?.playIdle()
                keyboardView.voiceListeningLocale = null
            }
        }
        rootContainer.addView(voiceOverlay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 鍵盤容器（FrameLayout 疊加鍵盤 + GL + Lottie + 面板覆蓋）
        keyboardContainer = FrameLayout(this)

        keyboardView = KeyboardView(this)
        keyboardView.setOnKeyPressListener { keyCode, label ->
            handleKeyPress(keyCode, label)
        }
        // 長按注音/聲調直接輸出（不走 InputRouter）
        keyboardView.setOnRawCommitListener { text ->
            val ic = currentInputConnection ?: return@setOnRawCommitListener
            // 先提交正在組合的注音
            val composing = inputRouter.composingText
            if (composing.isNotEmpty()) {
                ic.commitText(composing, 1)
                inputRouter.reset()
                keyboardView.updateComposingText("")
                keyboardView.updateCandidates(emptyList())
            }
            ic.commitText(text, 1)
        }
        // 滑動輸入：收到按鍵序列 → 解碼成候選字
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
            // 滑動輸入候選：直接 commit，不走 InputRouter
            if (swipeInputPending && index in swipeInputCandidates.indices) {
                val text = swipeInputCandidates[index]
                val ic = currentInputConnection
                if (ic != null) {
                    // F-6: 先清掉殘留的 composing 狀態
                    ic.finishComposingText()
                    inputRouter.reset()
                    ic.commitText(text, 1)
                }
                swipeInputPending = false
                swipeInputCandidates = emptyList()
                keyboardView.updateComposingText("")
                keyboardView.updateCandidates(emptyList())
                return@setOnCandidateSelectListener
            }

            val result = inputRouter.selectCandidate(index)
            // GIF/翻譯面板輸入模式：選字提交到面板而非目標 App
            val gpRef = gifPanel
            val tpRef = translatePanel
            if (gpRef != null && gpRef.isInInputMode && result is InputResult.CommitText) {
                gpRef.onComposedTextInput(result.text)
            } else if (tpRef != null && tpRef.isInInputMode && result is InputResult.CommitText) {
                tpRef.onCharInput(result.text)
            } else {
                val ic = currentInputConnection ?: return@setOnCandidateSelectListener
                applyInputResult(ic, result)
            }
            keyboardView.updateComposingText(inputRouter.composingText)
            keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
            // J-3: 連擊粒子特效
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
                    inputRouter.switchTo(option.mode)
                    characterView?.playSwitchLang()
                    keyboardView.currentInputMode = option.mode
                    keyboardView.updateKeyRows(rowsForInputMode(option.mode))
                }
                is RadialLanguageMenu.LanguageOption.Emoji -> {
                    // 切換到 Emoji 面板
                    toolbarView.activePanel = com.snowrealm.yukiboard.ui.panel.PanelType.EMOJI
                    toolbarView.onPanelSelected?.invoke(com.snowrealm.yukiboard.ui.panel.PanelType.EMOJI)
                }
            }
        }

        keyboardView.onSwipeSyllableConfirmed = { label ->
            currentInputConnection?.commitText(label, 1)
        }

        // 自訂圖片/影片背景（最底層）
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

        // 只有啟用自訂背景時才改為玻璃模式
        keyboardView.hasCustomBackground = useCustomBg
        keyboardView.bgOverlayAlpha = prefs.bgOverlayAlpha
        keyboardView.keyScalePercent = prefs.keyScalePercent
        keyboardView.keyOpacity = prefs.keyOpacity
        keyboardView.perKeySizes = prefs.getAllPerKeySizes()
        keyboardView.hapticEnabled = prefs.keyPressVibration
        keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
        keyboardView.swipeInputEnabled = prefs.swipeInput

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

        // 雪凜角色浮動在左上角
        // VRM 在 IME SurfaceView 下容易出問題，預設 Lottie；使用者可在設定開啟 VRM
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

        // 雪凜語音泡泡（顯示微吐槽）
        val bubble = com.snowrealm.yukiboard.yuki.YukiSpeechBubble(this)
        val bubbleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
        keyboardContainer.addView(bubble, bubbleParams)
        yukiSpeechBubble = bubble
        bubble.onSpeakRequest = { text -> onYukiSpeak(text) }

        // 面板容器（覆蓋在鍵盤上方，從頂部對齊）
        panelContainer = FrameLayout(this)
        panelContainer.visibility = View.GONE
        keyboardContainer.addView(panelContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP
        ))

        // 套用儲存的鍵盤高度（橫向使用更高比例，避免太扁）
        val kbHeight = calculateKeyboardHeight()
        rootContainer.addView(keyboardContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            kbHeight
        ))

        // 懸浮模式
        if (prefs.floatingModeEnabled) {
            return wrapInFloatingMode(rootContainer)
        }

        // 底部安全間距（避免貼住螢幕底部）
        val bottomPad = (8 * resources.displayMetrics.density).toInt()
        val bottomSpacer = View(this).apply {
            setBackgroundColor(if (prefs.transparentKeyboard) android.graphics.Color.TRANSPARENT else 0xFF111827.toInt())
        }
        rootContainer.addView(bottomSpacer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, bottomPad
        ))

        // 寬度調整（非滿版時置中）
        applyKeyboardWidth(rootContainer)

        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputRouter.reset()

        // 重新套用鍵盤高度（設定頁可能已修改 / 螢幕旋轉）
        val kbHeight = calculateKeyboardHeight()
        keyboardContainer.layoutParams?.let { lp ->
            if (lp.height != kbHeight) {
                lp.height = kbHeight
                keyboardContainer.requestLayout()
            }
        }

        // 同步鍵盤顯示的語言（保留使用者上次選擇的輸入法）
        keyboardView.currentInputMode = inputRouter.inputMode
        keyboardView.updateKeyRows(rowsForInputMode(inputRouter.inputMode))
        keyboardView.updateEditorInfo(info)

        // 同步智慧輸入開關
        inputRouter.smartInput.smartCorrectionEnabled = prefs.smartCorrection
        inputRouter.smartInput.adaptiveDictEnabled = prefs.adaptiveDict
        inputRouter.smartInput.phrasePredictEnabled = prefs.phrasePredict
        inputRouter.smartInput.habitTrackEnabled = prefs.habitTracking
        inputRouter.currentAppPackage = info?.packageName

        // 同步直打注音模式
        val bopoEngine = inputRouter.getActiveEngine()
        if (bopoEngine is com.snowrealm.yukiboard.input.engine.ChewingInputEngine) {
            bopoEngine.rawBopomofoMode = prefs.rawBopomofoMode
        }

        // 自動解密：如果輸入框已有加密訊息 [YB🔐]，顯示解密內容
        val ic0 = currentInputConnection
        if (ic0 != null && prefs.secretMessageMode) {
            val existing = ic0.getTextBeforeCursor(500, 0)?.toString() ?: ""
            if (secretMessageManager?.isEncryptedMessage(existing) == true) {
                val decrypted = secretMessageManager?.decrypt(existing)
                if (decrypted != null) {
                    val charAnchor = characterView as? View
                    if (charAnchor != null) {
                        yukiSpeechBubble?.show("🔓 $decrypted", charAnchor)
                    }
                }
            }
        }

        if (prefs.appAutoDetectEnabled && !manualModeOverride) {
            val detectedMode = appModeDetector.detectMode(info?.packageName)
            if (detectedMode != keyboardView.currentMode) {
                keyboardView.switchMode(detectedMode)
            }
        }

        if (prefs.shakeToUndoEnabled) shakeDetector.start()
        if (prefs.gyroscopeCursorEnabled) gyroController.register()

        glSurfaceView?.onResume()

        // 透明鍵盤模式
        applyTransparentMode()

        // 重新檢查自訂背景（設定頁可能已更改）
        backgroundView?.scaleMode = prefs.bgScaleMode
        backgroundView?.offsetX = prefs.bgOffsetX
        backgroundView?.offsetY = prefs.bgOffsetY
        val bgHas = backgroundView?.reloadFromStorage() ?: false
        val useCustomBg = prefs.useCustomBackground && bgHas
        // 透明模式也啟用玻璃按鍵風格
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
        keyboardView.perKeySizes = prefs.getAllPerKeySizes()
        keyboardView.hapticEnabled = prefs.keyPressVibration
        keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
        keyboardView.swipeInputEnabled = prefs.swipeInput

        // 遊戲模式恢復
        if (isInGameMode) {
            gameLoop?.resume()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inputRouter.smartInput.persist()
        inputRouter.reset()
        shakeDetector.stop()
        gyroController.deactivate()
        gyroController.unregister()
        manualModeOverride = false

        glSurfaceView?.onPause()

        // ��習累積句子
        val sentence = sentenceBuffer.toString().trim()
        if (sentence.length >= 2) {
            inputRouter.smartInput.onSentenceCommitted(sentence)
        }
        sentenceBuffer.clear()

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
        ttsEngine?.destroy()
        btKeyboard?.destroy()
        wifiServer?.destroy()
        stickerRepository?.destroy()
        stickerDownloadManager?.destroy()
        particleView?.stop()
        backgroundView?.destroy()
        translateEngine?.close()
        serviceScope.cancel()
    }

    // =========================================================================
    // 面板切換系統
    // =========================================================================

    /**
     * 將遠端貼圖以圖片方式傳送給目標 App（透過 commitContent API）。
     * 先下載到 cache，再用 FileProvider 提供 content URI。
     */
    private fun commitImageContent(url: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val stickerDir = java.io.File(cacheDir, "stickers").also { it.mkdirs() }
                // 從 URL path 判斷副檔名（忽略 query 參數）
                val urlPath = try { java.net.URL(url).path } catch (_: Exception) { url }
                val ext = when {
                    urlPath.contains(".gif", true) -> ".gif"
                    urlPath.contains(".webp", true) -> ".webp"
                    urlPath.contains(".png", true) -> ".png"
                    urlPath.contains(".jpg", true) || urlPath.contains(".jpeg", true) -> ".jpg"
                    else -> ".gif"  // Giphy 預設為 GIF
                }
                val mimeType = when (ext) {
                    ".gif" -> "image/gif"
                    ".webp" -> "image/webp"
                    ".jpg" -> "image/jpeg"
                    else -> "image/png"
                }
                val fileName = "sticker_${url.hashCode()}$ext"
                val file = java.io.File(stickerDir, fileName)

                if (!file.exists() || file.length() == 0L) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw Exception("Empty response body")
                }

                val authority = "$packageName.fileprovider"
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    this@YukiBoardIMEService, authority, file
                )
                val editorInfo = currentInputEditorInfo
                val ic = currentInputConnection ?: return@launch

                val inputContentInfo = androidx.core.view.inputmethod.InputContentInfoCompat(
                    contentUri,
                    android.content.ClipDescription("sticker", arrayOf(mimeType)),
                    null
                )
                val flag = if (android.os.Build.VERSION.SDK_INT >= 25)
                    androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                else 0

                // 先嘗試 commitContent（LINE 等 App 雖未宣告 contentMimeTypes 但仍支援）
                val committed = try {
                    androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                        ic, editorInfo, inputContentInfo, flag, null
                    )
                } catch (_: Exception) { false }

                if (!committed) {
                    // commitContent 失敗，改用剪貼簿複製 URI 讓使用者長按貼上
                    launch(Dispatchers.Main) {
                        try {
                            val clip = android.content.ClipData.newUri(
                                contentResolver, "sticker", contentUri
                            )
                            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(clip)
                            android.widget.Toast.makeText(this@YukiBoardIMEService, "GIF 已複製，請長按貼上", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            // 剪貼簿也失敗，直接貼 URL
                            ic.commitText(url, 1)
                        }
                    }
                }
            } catch (e: Exception) {
                // 下載失敗，fallback 傳 URL
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@YukiBoardIMEService, "GIF 下載失敗，已貼上連結", android.widget.Toast.LENGTH_SHORT).show()
                    currentInputConnection?.commitText(url, 1)
                }
            }
        }
    }

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
        // F-5: 切換面板時先提交殘留的 composing
        if (inputRouter.composingText.isNotEmpty()) {
            val ic = currentInputConnection
            val composing = inputRouter.composingText
            ic?.commitText(composing, 1)
            ic?.finishComposingText()
            inputRouter.reset()
            keyboardView.updateComposingText("")
            keyboardView.updateCandidates(emptyList())
        }
        // 離開面板輸入模式
        translatePanel?.exitInputMode()
        gifPanel?.exitInputMode()
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
                // 嘗試從選取文字 → 游標前方 → 游標後方擷取
                val ic = currentInputConnection
                val selected = ic?.getSelectedText(0)?.toString()?.trim() ?: ""
                val source = if (selected.isNotEmpty()) {
                    selected.take(200)
                } else {
                    val before = ic?.getTextBeforeCursor(200, 0)?.toString()?.trim() ?: ""
                    if (before.isNotEmpty()) before.takeLast(100)
                    else {
                        val after = ic?.getTextAfterCursor(200, 0)?.toString()?.trim() ?: ""
                        after.take(100)
                    }
                }
                if (source.isNotEmpty()) p.sourceText = source
                p.onTranslatedSelected = { translated ->
                    currentInputConnection?.commitText(translated, 1)
                }
                p.onSpeakRequest = { text, locale ->
                    onTranslateSpeakRequest(text, locale)
                }
                val engine = translateEngine
                if (engine != null) {
                    p.translationProvider = { src, fromLang, toLang, callback ->
                        serviceScope.launch {
                            val result = engine.translate(src, fromLang, toLang)
                            callback(result)
                        }
                    }
                }
                p
            }
            PanelType.GIF -> {
                val p = GifPanelView(this).also { gifPanel = it }
                p.theme = theme
                p.onGifSelected = { gif ->
                    commitImageContent(gif.fullUrl)
                }
                val gifClient = giphyClient
                if (gifClient != null && gifClient.isAvailable()) {
                    p.onSearch = { query, callback ->
                        serviceScope.launch {
                            val results = if (query.isBlank()) gifClient.fetchTrending()
                                          else gifClient.search(query)
                            callback(results)
                        }
                    }
                }
                p
            }
            PanelType.STICKER -> {
                val p = StickerPanelView(this).also { stickerPanel = it }
                p.theme = theme
                p.repository = stickerRepository
                p.downloadManager = stickerDownloadManager
                p.onStickerSelected = { stickerText ->
                    if (stickerText.startsWith("[STICKER]:")) {
                        val url = stickerText.removePrefix("[STICKER]:")
                        commitImageContent(url)
                    } else {
                        currentInputConnection?.commitText(stickerText, 1)
                    }
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
                p.reloadPreviewBitmap()
                p.onThemeSelected = { selected ->
                    tm.currentTheme = selected
                    prefs.useCustomBackground = false
                    applyTheme(selected)
                    p.previewBgBitmap = null
                }
                p.onPickBackground = {
                    try {
                        val intent = Intent(this, com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity::class.java).apply {
                            putExtra(com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity.EXTRA_PICK_TYPE, "image")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
                p.onPickVideo = {
                    try {
                        val intent = Intent(this, com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity::class.java).apply {
                            putExtra(com.snowrealm.yukiboard.ui.settings.BackgroundPickerActivity.EXTRA_PICK_TYPE, "video")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
                p.onClearBackground = {
                    val dir = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getBackgroundDir(this)
                    dir.listFiles()?.forEach { it.delete() }
                    prefs.useCustomBackground = false
                    backgroundView?.reloadFromStorage()
                    keyboardView.hasCustomBackground = false
                    keyboardView.invalidate()
                    p.previewBgBitmap = null
                }
                p.onOverlayAlphaChanged = { alpha ->
                    backgroundView?.overlayAlpha = alpha
                    keyboardView.bgOverlayAlpha = alpha
                    keyboardView.invalidate()
                }
                p.onLabelColorChanged = { color ->
                    keyboardView.customKeyLabelColor = color
                    keyboardView.invalidate()
                }
                p.onBackgroundSettingsChanged = {
                    // 即時套用比例/偏移變更到實際鍵盤背景
                    backgroundView?.let { bg ->
                        bg.scaleMode = prefs.bgScaleMode
                        bg.offsetX = prefs.bgOffsetX
                        bg.offsetY = prefs.bgOffsetY
                        bg.reloadFromStorage()
                    }
                    keyboardView.invalidate()
                }
                p.onKeySettingsChanged = {
                    keyboardView.keyScalePercent = prefs.keyScalePercent
                    keyboardView.keyOpacity = prefs.keyOpacity
                    // 透明鍵盤即時套用
                    applyTransparentMode()
                    val bgHasNow = backgroundView?.reloadFromStorage() ?: false
                    val glassKeysNow = (prefs.useCustomBackground && bgHasNow) || prefs.transparentKeyboard
                    keyboardView.hasCustomBackground = glassKeysNow
                    keyboardView.invalidate()
                }
                p.onPerKeySizeChanged = {
                    keyboardView.perKeySizes = prefs.getAllPerKeySizes()
                    keyboardView.invalidate()
                }
                p.onKeyboardHeightChanged = {
                    // 重建鍵盤高度需要重新設定 InputView
                    setInputView(onCreateInputView())
                }
                p
            }
            PanelType.CLAW -> {
                val p = ClawPanelView(this)
                p.theme = theme
                p.clawClient = clawClient
                p.setCurrentMode(
                    when (keyboardView.currentMode) {
                        KeyboardMode.DEVELOPER -> "developer"
                        else -> "normal"
                    }
                )
                p.inputTextProvider = {
                    val ic = currentInputConnection
                    val selected = ic?.getSelectedText(0)?.toString()
                    if (!selected.isNullOrEmpty()) selected
                    else ic?.getTextBeforeCursor(500, 0)?.toString() ?: ""
                }
                p.appPackageProvider = {
                    inputRouter.currentAppPackage ?: ""
                }
                p.keyboardModeProvider = {
                    keyboardView.currentMode
                }
                p.onSuggestionSelected = { text ->
                    val ic = currentInputConnection
                    if (ic != null) {
                        // 清除輸入框原有內容，替換為 AI 結果，讓用戶可直接傳送
                        ic.performContextMenuAction(android.R.id.selectAll)
                        ic.commitText(text, 1)
                    }
                    // 同步結果到懸浮面板（供長按懸浮化使用）
                    clawResultFloatingPanel?.showResult("AI", listOf(text))
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

        // 加入面板控制列（透明度 + 圖層交換）
        showPanelControlBar()

        // 重設圖層順序
        if (isPanelBehindKeyboard) {
            isPanelBehindKeyboard = false
            panelContainer.bringToFront()
        }
    }

    private fun closePanel() {
        activePanel = null
        toolbarView.activePanel = null
        panelContainer.removeAllViews()
        panelContainer.visibility = View.GONE
        hidePanelControlBar()
        // 回到鍵盤時確保 InputConnection composing 狀態乾淨
        currentInputConnection?.finishComposingText()
        // 確保圖層正常
        if (isPanelBehindKeyboard) {
            isPanelBehindKeyboard = false
            panelContainer.bringToFront()
        }
    }

    /**
     * 面板控制列：透明度滑桿 + 圖層交換按鈕。
     * 放在 toolbarView 和 keyboardContainer 之間。
     */
    private fun showPanelControlBar() {
        hidePanelControlBar()

        val dp = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xCC1A1A2E.toInt())
            setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }

        // 透明度標籤
        val label = android.widget.TextView(this).apply {
            text = "面板透明度"
            textSize = 10f
            setTextColor(0xFF8899AA.toInt())
            setPadding(0, 0, (6 * dp).toInt(), 0)
        }
        bar.addView(label)

        // 透明度滑桿
        val slider = android.widget.SeekBar(this).apply {
            max = 255
            progress = 255
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    panelContainer.alpha = p / 255f
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        bar.addView(slider)

        // 圖層交換按鈕
        val swapBtn = android.widget.TextView(this).apply {
            text = "⇅ 輸入"
            textSize = 11f
            setTextColor(0xFF7FD8FF.toInt())
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF2A2A4A.toInt())
                cornerRadius = 6 * dp
            }
            setOnClickListener { togglePanelLayer() }
        }
        bar.addView(swapBtn)

        // 插入到 rootContainer，位於 toolbarView 之後、keyboardContainer 之前
        val toolbarIndex = rootContainer.indexOfChild(toolbarView)
        rootContainer.addView(bar, toolbarIndex + 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panelControlBar = bar
    }

    private fun hidePanelControlBar() {
        panelControlBar?.let { rootContainer.removeView(it) }
        panelControlBar = null
        panelContainer.alpha = 1f
    }

    /** 交換面板與鍵盤的圖層：面板到後面 → 可打字；再按一次 → 面板回前面 */
    private fun togglePanelLayer() {
        isPanelBehindKeyboard = !isPanelBehindKeyboard
        if (isPanelBehindKeyboard) {
            // 面板移到鍵盤後面：讓 keyboardView 接收觸控
            keyboardView.bringToFront()
            // 更新按鈕文字
            (panelControlBar as? LinearLayout)?.let { bar ->
                val btn = bar.getChildAt(bar.childCount - 1)
                if (btn is android.widget.TextView) {
                    btn.text = "⇅ 面板"
                    btn.setTextColor(0xFFFFD700.toInt())
                }
            }
        } else {
            // 面板回到前面
            panelContainer.bringToFront()
            (panelControlBar as? LinearLayout)?.let { bar ->
                val btn = bar.getChildAt(bar.childCount - 1)
                if (btn is android.widget.TextView) {
                    btn.text = "⇅ 輸入"
                    btn.setTextColor(0xFF7FD8FF.toInt())
                }
            }
        }
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

    /** 防止 onFinalResult 重複觸發 */
    private var voiceFinalHandled = false

    private fun toggleVoiceInput() {
        // 如果正在辨識 → 停止
        if (speechEngine?.isListening == true) {
            stopVoiceInput()
            return
        }
        // 如果 overlay 正在顯示（等待確認/倒數中）→ 不重複啟動
        if (voiceOverlay?.isShowing == true) return

        // 檢查麥克風權限
        if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            keyboardView.showToast("請授權麥克風權限後再試")
            return
        }

        // 根據設定選擇語音引擎（0=Android, 1=Whisper, 2=Faster-Whisper）
        val desiredType = prefs.speechEngineType
        val needRecreate = when {
            speechEngine == null -> true
            desiredType == 2 && speechEngine !is FasterWhisperSpeechEngine -> true
            desiredType == 1 && speechEngine !is WhisperSpeechEngine -> true
            desiredType == 0 && speechEngine !is AndroidSpeechEngine -> true
            else -> false
        }
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

        // 引擎名稱
        val engineName = when (speechEngine) {
            is FasterWhisperSpeechEngine -> "Faster-Whisper"
            is WhisperSpeechEngine -> "Whisper API"
            else -> "Android 內建"
        }

        // F-1: 語音輸入開始前，先停止 TTS 避免搶麥克風
        ttsEngine?.stop()

        characterView?.playListening()

        // 顯示語音辨識 Overlay + 停止按鈕
        voiceFinalHandled = false
        voiceOverlay?.onStop = {
            stopVoiceInput()
        }
        voiceOverlay?.show(engineName)

        // 中文一律用 zh-TW（繁體優先）
        val localeHint = when (inputRouter.inputMode) {
            com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> "ja-JP"
            com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> "ko-KR"
            else -> "zh-TW"
        }

        keyboardView.voiceListeningLocale = "auto"

        speechEngine?.startListening(
            locale = localeHint,
            onPartialResult = { partial ->
                if (!voiceFinalHandled) {
                    voiceOverlay?.updatePartial(partial)
                }
            },
            onFinalResult = { result ->
                if (voiceFinalHandled) return@startListening
                voiceFinalHandled = true
                keyboardView.voiceListeningLocale = null
                if (result.isNotEmpty()) {
                    voiceOverlay?.showFinal(result)
                } else {
                    voiceOverlay?.dismiss()
                    characterView?.playIdle()
                }
            },
            onError = { errorCode ->
                keyboardView.voiceListeningLocale = null
                characterView?.playIdle()
                val errorMsg = when (errorCode) {
                    FasterWhisperSpeechEngine.ERROR_NO_SERVER -> "語音辨識失敗：未設定 Server URL"
                    WhisperSpeechEngine.ERROR_NO_API_KEY -> "語音辨識失敗：未設定 API Key"
                    WhisperSpeechEngine.ERROR_NO_PERMISSION,
                    FasterWhisperSpeechEngine.ERROR_NO_PERMISSION -> "語音辨識失敗：無麥克風權限"
                    WhisperSpeechEngine.ERROR_AUDIO_INIT,
                    FasterWhisperSpeechEngine.ERROR_AUDIO_INIT -> "語音辨識失敗：錄音初始化失敗"
                    WhisperSpeechEngine.ERROR_NETWORK,
                    FasterWhisperSpeechEngine.ERROR_NETWORK -> "語音辨識失敗：網路錯誤"
                    else -> "語音辨識錯誤 ($errorCode)"
                }
                voiceOverlay?.showError(errorMsg)
            }
        )
    }

    private fun stopVoiceInput() {
        speechEngine?.stopListening()
        keyboardView.voiceListeningLocale = null
        characterView?.playIdle()
        // stopListening 會觸發 onFinalResult → overlay 顯示最終結果
    }

    // =========================================================================
    // TTS 語音輸出
    // =========================================================================

    private fun initTtsEngine() {
        ttsEngine?.destroy()
        ttsEngine = when (prefs.ttsEngineType) {
            1 -> {
                val apiKey = prefs.googleCloudTtsApiKey
                if (apiKey.isNotBlank()) {
                    com.snowrealm.yukiboard.speech.GoogleCloudTtsEngine(apiKey)
                } else {
                    com.snowrealm.yukiboard.speech.AndroidTtsEngine(applicationContext)
                }
            }
            else -> com.snowrealm.yukiboard.speech.AndroidTtsEngine(applicationContext)
        }
    }

    /** TTS 朗讀（帶語言與語速設定） */
    private fun ttsSpeak(text: String, locale: String? = null, onDone: (() -> Unit)? = null) {
        // F-1: TTS 開始前，先停止語音輸入避免搶麥克風
        if (speechEngine?.isListening == true) stopVoiceInput()
        ttsEngine?.speak(text, locale, prefs.ttsSpeed, onDone)
    }

    /** 翻譯面板朗讀回呼 */
    private fun onTranslateSpeakRequest(text: String, locale: String) {
        if (!prefs.ttsTranslateRead) return
        ttsSpeak(text, locale)
    }

    /** 候選字朗讀（無障礙） */
    private fun onCandidateSpeak(text: String) {
        if (!prefs.ttsCandidateRead) return
        ttsSpeak(text)
    }

    /** Yuki 角色語音回應 */
    private fun onYukiSpeak(text: String) {
        if (!prefs.ttsYukiVoice) return
        ttsSpeak(text)
    }

    // =========================================================================
    // AI 候選字 debounce
    // =========================================================================

    /**
     * 輸入時觸發 AI 候選字請求（600ms debounce）。
     * 使用 Claw AI 的 ai_prediction skill，結果附加到候選列末尾並標記 AI 來源。
     */
    private fun requestAICandidates() {
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

    // =========================================================================
    // 鍵盤尺寸調整
    // =========================================================================

    /** 計算鍵盤高度（橫向時使用更高比例，避免按鍵太扁） */
    private fun calculateKeyboardHeight(): Int {
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
    // 透明鍵盤
    // =========================================================================

    private fun applyTransparentMode() {
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
        // 先隱藏 IME 視窗，避免系統在 Activity 顯示後重新拉回 IME 焦點導致 Activity 被關閉
        requestHideSelf(0)
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
        menu.onBackToNormal = {
            exitGameMode()
            keyboardView.switchMode(KeyboardMode.NORMAL)
        }
        keyboardContainer.addView(menu)
        gameMenuView = menu
    }

    private fun startGame(gameMode: GameMode) {
        gameMenuView?.let { keyboardContainer.removeView(it) }
        gameMenuView = null

        // 建立遊戲
        val ctx = applicationContext
        val game: GameLogic = when (gameMode) {
            GameMode.SNAKE -> SnakeGame(ctx)
            GameMode.WHACK_A_MOLE -> WhackAMoleGame(ctx)
            GameMode.RHYTHM -> RhythmGame(ctx)
            GameMode.TYPING_CHALLENGE -> TypingChallengeGame(ctx)
            GameMode.WORD_GUARDIAN -> WordGuardianGame(ctx)
            GameMode.KEYBOARD_TRAVELER -> KeyboardTravelerGame(ctx)
            GameMode.MOOD_MINER -> MoodMinerGame(ctx)
            GameMode.BULLET_HELL -> BulletHellMiniGame(ctx)
            GameMode.ICE_CRYSTAL -> IceCrystalGame(ctx)
            GameMode.WARSHIP_CRUISE -> WarshipCruiseGame(ctx)
            GameMode.SPELLING_DUEL -> SpellingDuelGame(ctx)
            GameMode.KEYBOARD_MINESWEEPER -> KeyboardMinesweeperGame(ctx)
            GameMode.MEMORY_FLIP -> MemoryFlipGame(ctx)
            GameMode.YUKI_QUIZ -> YukiQuizGame(ctx)
            GameMode.COUPLE_QUIZ -> CoupleQuizGame(ctx)
            GameMode.TYPING_RACE -> TypingRaceGame(ctx)
            GameMode.RANDOM_CHALLENGE -> RandomChallengeGame(ctx)
            GameMode.VOICE_GAME -> VoiceGame(ctx)
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

        // ⌨返回：退出遊戲模式回到一般鍵盤
        gkv.onBackToNormal = {
            exitGameMode()
            keyboardView.switchMode(KeyboardMode.NORMAL)
        }

        // 遊戲事件回呼（統一透過反射取 onGameEvent 屬性）
        setGameEventCallback(game)

        keyboardContainer.addView(gkv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        gkv.onDetached = {
            gameLoop?.stop()
            gameLoop = null
        }
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

    private fun setGameEventCallback(game: GameLogic) {
        val callback: (GameEvent) -> Unit = { handleGameEvent(it, game) }
        when (game) {
            is SnakeGame -> game.onGameEvent = callback
            is WhackAMoleGame -> game.onGameEvent = callback
            is RhythmGame -> game.onGameEvent = callback
            is TypingChallengeGame -> game.onGameEvent = callback
            is WordGuardianGame -> game.onGameEvent = callback
            is KeyboardTravelerGame -> game.onGameEvent = callback
            is MoodMinerGame -> game.onGameEvent = callback
            is BulletHellMiniGame -> game.onGameEvent = callback
            is IceCrystalGame -> game.onGameEvent = callback
            is WarshipCruiseGame -> game.onGameEvent = callback
            is SpellingDuelGame -> game.onGameEvent = callback
            is KeyboardMinesweeperGame -> game.onGameEvent = callback
            is MemoryFlipGame -> game.onGameEvent = callback
            is YukiQuizGame -> game.onGameEvent = callback
            is CoupleQuizGame -> game.onGameEvent = callback
            is TypingRaceGame -> game.onGameEvent = callback
            is RandomChallengeGame -> game.onGameEvent = callback
            is VoiceGame -> game.onGameEvent = callback
        }
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

    /**
     * 滑動輸入處理：收到滑過的按鍵序列，解碼成候選字顯示。
     * 選字後自動提交到輸入框。
     */
    private fun handleSwipeInput(keys: List<com.snowrealm.yukiboard.ui.keyboard.KeyModel>) {
        if (keys.size < 2) return

        val labels = keys.map { it.label }
        val candidates = inputRouter.swipeSuggest(labels)

        if (candidates.isNotEmpty()) {
            // 顯示滑動軌跡對應的注音/字母作為 composing text
            val composing = labels.joinToString("")
            keyboardView.updateComposingText(composing)
            keyboardView.updateCandidates(candidates)
            keyboardView.markSwipeCandidates(true)

            // 標記正在滑動輸入模式，選字時直接 commit（不走 InputRouter）
            swipeInputPending = true
            swipeInputCandidates = candidates
        }
    }

    private var swipeInputPending = false
    private var swipeInputCandidates: List<String> = emptyList()

    private fun handleKeyPress(keyCode: Int, label: String) {
        // F-12: 語音輸入中按鍵先停止語音，避免 partial result 互蓋
        if (speechEngine?.isListening == true) {
            stopVoiceInput()
        }
        // 清除滑動輸入待選狀態（開始打字表示放棄滑動候選）
        if (swipeInputPending) {
            swipeInputPending = false
            swipeInputCandidates = emptyList()
        }
        // 翻譯面板輸入模式：攔截鍵盤輸入到翻譯框
        val tp = translatePanel
        if (tp != null && tp.isInInputMode) {
            when (keyCode) {
                KeyboardView.KEYCODE_DELETE -> tp.onDeleteInput()
                KeyboardView.KEYCODE_ENTER -> tp.onEnterInput()
                KeyboardView.KEYCODE_SPACE -> tp.onSpaceInput()
                KeyboardView.KEYCODE_SWITCH_LANG -> handleSwitchLang()
                KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> { }
                else -> if (label.isNotEmpty() && keyCode >= 0) tp.onCharInput(label)
            }
            return
        }

        // GIF 面板輸入模式：攔截鍵盤輸入到搜尋框
        val gp = gifPanel
        if (gp != null && gp.isInInputMode) {
            val isCjk = inputRouter.inputMode != InputMode.ENGLISH
            when (keyCode) {
                KeyboardView.KEYCODE_DELETE -> {
                    if (isCjk && inputRouter.composingText.isNotEmpty()) {
                        inputRouter.processDelete()
                        keyboardView.updateComposingText(inputRouter.composingText)
                        keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                    } else {
                        gp.onDeleteInput()
                    }
                }
                KeyboardView.KEYCODE_ENTER -> {
                    if (isCjk && inputRouter.composingText.isNotEmpty()) {
                        // 先提交組字到搜尋框
                        gp.onComposedTextInput(inputRouter.composingText)
                        inputRouter.reset()
                        keyboardView.updateComposingText("")
                        keyboardView.updateCandidates(emptyList())
                    }
                    gp.onEnterInput()
                }
                KeyboardView.KEYCODE_SPACE -> {
                    if (isCjk && inputRouter.composingText.isNotEmpty()) {
                        val result = inputRouter.processSpace()
                        if (result is InputResult.CommitText) {
                            gp.onComposedTextInput(result.text)
                        }
                        keyboardView.updateComposingText(inputRouter.composingText)
                        keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                    } else {
                        gp.onSpaceInput()
                    }
                }
                KeyboardView.KEYCODE_SWITCH_LANG -> handleSwitchLang()
                KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> { }
                else -> if (label.isNotEmpty() && keyCode >= 0) {
                    if (isCjk) {
                        val result = inputRouter.processInput(label)
                        if (result is InputResult.CommitText) {
                            gp.onComposedTextInput(result.text)
                        }
                        keyboardView.updateComposingText(inputRouter.composingText)
                        keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
                        gp.updateComposingHint(inputRouter.composingText)
                    } else {
                        gp.onCharInput(label)
                    }
                }
            }
            return
        }

        // 面板開啟中但使用者按了鍵盤按鍵 → 先關閉面板回到正常輸入
        if (activePanel != null) {
            closePanel()
        }

        val ic: InputConnection = currentInputConnection ?: return

        // 更新上下文給候選排序用
        // 200 字元以支援整句上下文預測（不只字詞，還有句子級別）
        inputRouter.contextBeforeCursor = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""

        ic.beginBatchEdit()
        try {
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
            KeyboardView.KEYCODE_DESIGN_LOCK       -> sendCtrlShiftKey(ic, KeyEvent.KEYCODE_K)
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

            // 日語 あ⇔ア 切換
            KeyboardView.KEYCODE_TOGGLE_KANA -> {
                val eng = inputRouter.getActiveEngine()
                if (eng is com.snowrealm.yukiboard.input.engine.JapaneseInputEngine) {
                    eng.toggleKatakana()
                    // 切換 50 音佈局（平假名 ⇔ 片假名）
                    KeyboardState.isKatakanaMode = eng.katakanaMode
                    keyboardView.updateKeyRows(KeyboardState.japaneseRows())
                    keyboardView.updateComposingText(inputRouter.composingText)
                    keyboardView.showToast(if (eng.katakanaMode) "片仮名モード" else "平仮名モード")
                }
            }

            // 子佈局切換鍵（由 KeyboardView 內部處理，不需 IME 動作）
            KeyboardView.KEYCODE_SWITCH_NUMBERS,
            KeyboardView.KEYCODE_SWITCH_SYMBOLS,
            KeyboardView.KEYCODE_SWITCH_ABC,
            KeyboardView.KEYCODE_BACK_NORMAL -> { }

            else -> {
                val ch = label.firstOrNull()
                // 非字母、非注音符號 → 直接 commit（數字、標點、符號）
                val isBopomofo = ch != null && (ch in '\u3105'..'\u312F' || ch in '\u02CA'..'\u02CB' || ch == '\u02C7' || ch == '\u02D9')
                if (ch != null && !ch.isLetter() && !isBopomofo) {
                    // 先提交正在組合的文字
                    val composing = inputRouter.composingText
                    if (composing.isNotEmpty()) {
                        applyInputResult(ic, InputResult.CommitText(composing))
                        inputRouter.reset()
                    }
                    ic.commitText(label, 1)
                } else {
                    val result = inputRouter.processInput(label)
                    applyInputResult(ic, result)
                }
            }
        }

        // 觸發角色打字動畫（節流：每 N 次按鍵才播放，避免拖慢輸入）
        characterView?.onKeystroke()

        keyboardView.updateComposingText(inputRouter.composingText)

        // 候選列為空時，僅在開發者模式且有高信心預測時顯示提示
        val candidates = inputRouter.candidates.toMutableList()
        if (candidates.isEmpty() && inputRouter.composingText.isEmpty()) {
            val predictor = nextActionPredictor
            if (predictor != null && keyboardView.currentMode == KeyboardMode.DEVELOPER) {
                val predCtx = com.snowrealm.yukiboard.input.predict.PredictionContext(
                    composingText = inputRouter.composingText,
                    textBeforeCursor = inputRouter.contextBeforeCursor,
                    keyboardMode = keyboardView.currentMode,
                    appPackage = inputRouter.currentAppPackage ?: ""
                )
                val actions = predictor.predict(predCtx).filter { it.confidence >= 0.8f }
                for (action in actions.take(3)) {
                    candidates.add(action.displayText)
                }
            }
        }
        keyboardView.updateCandidates(candidates)

        // AI 候選字 debounce：有組字或上下文時觸發
        requestAICandidates()

        } finally {
            ic.endBatchEdit()
        }
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

    // =========================================================================
    // 輸入處理
    // =========================================================================

    private fun handleDelete(ic: InputConnection) {
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
    }

    private fun handleEnter(ic: InputConnection) {
        val result = inputRouter.processEnter()
        applyInputResult(ic, result)

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
                    val encrypted = secretMessageManager?.encrypt(plaintext)
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

    private fun handleSpace(ic: InputConnection) {
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
    }

    private fun handleSwitchLang() {
        // 切換語言前：先把 composing 提交到 InputConnection，避免殘留
        val ic = currentInputConnection
        val commitResult = inputRouter.getActiveEngine().forceCommit()
        if (commitResult is com.snowrealm.yukiboard.input.router.InputResult.CommitText) {
            ic?.commitText(commitResult.text, 1)
        }
        ic?.finishComposingText()

        inputRouter.toggleLanguage()
        characterView?.playSwitchLang()
        keyboardView.currentInputMode = inputRouter.inputMode
        keyboardView.updateKeyRows(rowsForInputMode(inputRouter.inputMode))
        keyboardView.updateCandidates(emptyList(), emptyMap())
    }

    /** 根據輸入模式取得對應鍵盤佈局 */
    private fun rowsForInputMode(mode: com.snowrealm.yukiboard.input.router.InputMode): List<List<KeyModel>> = when (mode) {
        com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> KeyboardState.bopomofoRows()
        com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> KeyboardState.pinyinRows()
        com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
        com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> KeyboardState.cangjieRows()
        com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> KeyboardState.japaneseRows()
        com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> KeyboardState.koreanRows()
        else -> KeyboardState.defaultQwertyRows()
    }

    private fun applyInputResult(ic: InputConnection, result: InputResult) {
        when (result) {
            is InputResult.CommitText -> {
                // 情緒冷靜期檢查
                if (prefs.yukiEmotionalCooldown) {
                    val cooldownAction = emotionalCooldown?.checkText(result.text)
                    if (cooldownAction is com.snowrealm.yukiboard.yuki.EmotionalCooldownManager.CooldownAction.Intercept) {
                        characterView?.playFreeze()
                        val charAnchor = characterView as? View
                        if (charAnchor != null) {
                            yukiSpeechBubble?.show("等一下。這句話確定要發嗎？", charAnchor)
                        }
                    }
                }

                ic.setComposingText("", 0)
                ic.finishComposingText()
                ic.commitText(result.text, 1)
                characterView?.playHappy()
                forwardTextToRemote(result.text)

                // 累積句子，用於整句學習
                sentenceBuffer.append(result.text)

                // 語氣偵測 + 雪凜回應（負面/悲傷/生氣時）
                if (prefs.toneDetection && result.text.length >= 2) {
                    val detection = toneDetector?.analyze(result.text)
                    if (detection != null && detection.confidence >= 0.3f) {
                        val response = toneDetector?.getYukiResponse(detection.tone)
                        if (response != null) {
                            val charAnchor = characterView as? View
                            if (charAnchor != null) {
                                yukiSpeechBubble?.show(response, charAnchor)
                            }
                        }
                    }
                }

                // 微吐槽檢查
                if (prefs.yukiSarcasticComments && result.text.length == 1) {
                    val comment = yukiCommentEngine?.onKeystroke(
                        result.text[0],
                        ic.getTextBeforeCursor(200, 0)?.length ?: 0
                    )
                    if (comment != null) {
                        val charAnchor = characterView as? View
                        if (charAnchor != null) {
                            yukiSpeechBubble?.show(comment, charAnchor)
                        }
                    }
                }
            }
            is InputResult.ComposingUpdated -> ic.setComposingText(result.composing, 1)
            is InputResult.ComposingCleared -> ic.finishComposingText()
            is InputResult.Idle -> { }
        }
    }
}
