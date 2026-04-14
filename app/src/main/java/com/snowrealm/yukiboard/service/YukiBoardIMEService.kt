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

class YukiBoardIMEService : InputMethodService() {

    internal lateinit var rootContainer: LinearLayout
    internal lateinit var toolbarView: ToolbarView
    internal lateinit var panelContainer: FrameLayout
    internal lateinit var topPanelContainer: FrameLayout
    internal lateinit var keyboardContainer: FrameLayout
    internal lateinit var imePanelControlBar: ImePanelControlBar
    internal lateinit var imeTransparencyOverlay: ImeTransparencyOverlay
    internal lateinit var imeFloatingModeHelper: ImeFloatingModeHelper
    internal lateinit var imeFraudModelInit: ImeFraudModelInit
    internal val imeGameLauncher = ImeGameLauncher()
    internal lateinit var imeImageCommitter: ImeImageCommitter
    internal var floatingBall: FloatingBallView? = null
    internal var floatingOuterFrame: FrameLayout? = null
    internal var isPanelBehindKeyboard = false
    internal lateinit var keyboardView: KeyboardView
    internal lateinit var inputRouter: InputRouter
    internal lateinit var snippetManager: SnippetManager
    internal lateinit var shakeDetector: ShakeDetector
    internal lateinit var gyroController: GyroscopeCursorController
    internal lateinit var appModeDetector: AppModeDetector
    internal lateinit var imePanelFactory: ImePanelFactory
    internal lateinit var imeCoordinator: ImeSessionCoordinator
    internal lateinit var imeCandidateInteractionHandler: ImeCandidateInteractionHandler
    internal lateinit var imePanelCleanup: ImePanelCleanup
    internal lateinit var imePanelCoordinator: ImePanelCoordinator
    internal lateinit var imePanelHost: ImePanelHost
    internal lateinit var imePanelRouter: ImePanelRouter
    internal lateinit var imeSessionHooks: ImeSessionHooks
    internal lateinit var imeThemeRenderDelegate: ImeThemeRenderDelegate
    internal lateinit var imeVoiceController: ImeVoiceController
    internal lateinit var prefs: YukiPreferences

    // GL 流光 + Lottie 角色 + 粒子 + 背景
    internal var glSurfaceView: KeyboardGLSurfaceView? = null
    internal var characterView: CharacterView? = null
    internal var particleView: com.snowrealm.yukiboard.animation.KeyboardParticleView? = null
    internal var backgroundView: com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView? = null
    internal var bgUpdateReceiver: android.content.BroadcastReceiver? = null
    internal val fraudAnalyzer by lazy { com.snowrealm.yukiboard.security.FraudAIAnalyzer(this) }
    internal var lastFraudScanText: String? = null  // 避免重複掃描同一段文字
    internal var lastFraudReplyText: String? = null // 避免重複掃描同一段回覆
    internal val fraudDetectRunnable = Runnable {
        if (::panelContainer.isInitialized && panelContainer.childCount > 0) {
            (panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")
        }
    }
    internal var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null

    // 面板
    internal var activePanel: PanelType? = null
    internal val imePanelBindings = ImePanelBindings()

    // 貼圖同步 + 下載管理
    internal var stickerRepository: com.snowrealm.yukiboard.sticker.StickerRepository? = null
    internal var stickerDownloadManager: com.snowrealm.yukiboard.sticker.StickerPackDownloadManager? = null

    // 非同步
    internal val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Claw AI
    var clawClient: ClawClient? = null
        internal set

    // GIF + 翻譯
    internal var giphyClient: GiphyApiClient? = null
    internal var translateEngine: TranslateEngine? = null

    // 語音輸入
    internal var speechEngine: SpeechInputEngine? = null
    internal var voiceOverlay: com.snowrealm.yukiboard.ui.speech.VoiceRecognitionOverlay? = null

    // TTS 語音輸出
    internal var ttsEngine: com.snowrealm.yukiboard.speech.SpeechOutputEngine? = null

    // AI 候選字 debounce
    internal var aiCandidateJob: Job? = null
    internal val AI_CANDIDATE_DEBOUNCE_MS = 600L

    // 雪凜系統
    internal var yukiCommentEngine: com.snowrealm.yukiboard.yuki.YukiCommentEngine? = null
    internal var yukiSpeechBubble: com.snowrealm.yukiboard.yuki.YukiSpeechBubble? = null
    internal var emotionalCooldown: com.snowrealm.yukiboard.yuki.EmotionalCooldownManager? = null
    internal var toneDetector: com.snowrealm.yukiboard.yuki.ToneDetector? = null
    internal var secretMessageManager: com.snowrealm.yukiboard.yuki.SecretMessageManager? = null

    // 懸浮面板
    internal var floatingPanelManager: com.snowrealm.yukiboard.ui.floating.FloatingPanelManager? = null
    internal var weatherFloatingPanel: com.snowrealm.yukiboard.weather.WeatherFloatingPanel? = null
    internal var clawResultFloatingPanel: com.snowrealm.yukiboard.ui.floating.ClawResultFloatingPanel? = null

    // RIME 詞庫
    internal var rimeDictionary: com.snowrealm.yukiboard.input.dictionary.RimeDictionary? = null

    // 下一步動作預測
    internal var nextActionPredictor: com.snowrealm.yukiboard.input.predict.NextActionPredictor? = null

    // 私房話累積緩衝
    internal val sentenceBuffer = StringBuilder()

    // 打字事件追蹤（微吐槽用）
    internal var consecutiveDeleteCount = 0
    internal var lastDeletedTextLength = 0
    internal var lastTypedChar: Char? = null
    internal var sameCharCount = 0

    // 遠端鍵盤
    internal var btKeyboard: BluetoothHidKeyboard? = null
    internal var wifiServer: WifiKeyboardServer? = null
    // 手寫辨識
    internal var handwritingEngine: com.snowrealm.yukiboard.input.handwriting.HandwritingEngine? = null
    internal var keySoundEngine: KeyboardSoundEngine? = null
    internal var voicePostProcessor: com.snowrealm.yukiboard.speech.VoicePostProcessor? = null

    // 鍵盤尺寸調整 + 懸浮
    internal var resizeHandle: KeyboardResizeHandle? = null
    internal var floatingWrapper: FloatingKeyboardWrapper? = null
    internal var isFloatingMode = false

    internal var stickerSyncDone = false

    // uiState collect（Sprint 3 Phase 1）
    internal var uiStateCollectJob: Job? = null

    // 遊戲相關
    internal val showGameMenuRunnable = Runnable { showGameMenu() }
    internal var gameMenuView: GameMenuView? = null
    internal var gameKeyboardView: GameKeyboardView? = null
    internal var gameLoop: GameLoopRunner? = null
    internal var currentGame: GameLogic? = null
    internal var isInGameMode = false

    internal var manualModeOverride = false

    override fun onCreate() {
        super.onCreate()
        inputRouter = InputRouter(applicationContext)
        snippetManager = SnippetManager(applicationContext)
        appModeDetector = AppModeDetector()
        imePanelFactory = ImePanelFactory()
        imeCoordinator = ImeSessionCoordinator(applicationContext)
        imeCandidateInteractionHandler = ImeCandidateInteractionHandler()
        imePanelCleanup = ImePanelCleanup()
        imePanelCoordinator = ImePanelCoordinator(imeCoordinator)
        imeThemeRenderDelegate = ImeThemeRenderDelegate()
        imeVoiceController = ImeVoiceController()
        imeSessionHooks = ImeSessionHooks(
            imeCoordinator = imeCoordinator,
            cloudSyncManager = CloudSyncManager.getInstance(applicationContext),
        )
        prefs = YukiPreferences(applicationContext)
        imeTransparencyOverlay = ImeTransparencyOverlay(this, prefs)
        imePanelControlBar = ImePanelControlBar(this)
        imeFloatingModeHelper = ImeFloatingModeHelper(this, prefs)
        imeFraudModelInit = ImeFraudModelInit(applicationContext, serviceScope)
        imeImageCommitter = ImeImageCommitter(this, serviceScope)

        // RIME 詞庫初始化（IO thread）
        if (prefs.rimeDictEnabled) {
            rimeDictionary = com.snowrealm.yukiboard.input.dictionary.RimeDictionary(applicationContext)
            serviceScope.launch {
                try { rimeDictionary?.init() }
                catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("IME", "RIME init failed: ${e.message}") }
            }
        }

        // 套用使用者選擇的輸入法
        val savedModes = prefs.enabledInputModes
        if (savedModes.isNotBlank()) {
            val modeNames = savedModes.split(",").filter { it.isNotBlank() }
            val modes = modeNames.mapNotNull { name ->
                try { com.snowrealm.yukiboard.input.router.InputMode.valueOf(name) } catch (_: Exception) { null }
            }
            if (modes.isNotEmpty()) inputRouter.enabledModes = modes
        }

        // 預���注音索引（IO 執行緒，避免首次按鍵凍結 UI）
        serviceScope.launch(Dispatchers.IO) {
            try { inputRouter.warmUpChewing() }
            catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("IME", "Chewing warmup failed: ${e.message}") }
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

        initExternalEngines()

        // 防詐 TFLite 模型初始化 + 自動更新
        initFraudModel()

        // 懸浮面板回調：AI 結果插入輸入框
        clawResultFloatingPanel?.onInsertToInput = { text ->
            val ic = currentInputConnection
            ic?.commitText(text, 1)
        }
    }

    override fun onEvaluateInputViewShown(): Boolean = true
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean = true

    override fun onCreateInputView(): View {
        // 重建前先清掉舊的 receiver，避免多次 register 累積洩漏
        bgUpdateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        bgUpdateReceiver = null

        // 確保 IME window 啟用硬體加速（TextureView 影片背景需要）
        window?.window?.let { w ->
            @Suppress("DEPRECATION")
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
        imeCoordinator.onInputViewCreated()

        // 垂直佈局：工具列 → 面板容器 → 鍵盤容器
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 注意：不可設 clipChildren=false，否則 KeyboardView.drawBackground()
            // 的 PorterDuff.Mode.CLEAR 會穿過 keyboardContainer 清掉 toolbar 內容
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
        toolbarView.onPanelLongPressed = { panel ->
            when (panel) {
                PanelType.THEME -> showTransparencyOverlay()
                PanelType.SETTINGS -> toggleOneHandMode()
                else -> {}
            }
        }
        rootContainer.addView(toolbarView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 語音辨識 Overlay（液態玻璃氣泡框）
        voiceOverlay = com.snowrealm.yukiboard.ui.speech.VoiceRecognitionOverlay(this).also {
            it.onConfirm = { text ->
                currentInputConnection?.commitText(text, 1)
                characterView?.playHappy()
                imeCoordinator.setVoiceInputActive(false)
            }
            it.onCancel = {
                speechEngine?.cancel()
                characterView?.playIdle()
                imeCoordinator.setVoiceInputActive(false)
            }
        }
        rootContainer.addView(voiceOverlay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 鍵盤容器（FrameLayout 疊加鍵盤 + GL + Lottie + 面板覆蓋）
        keyboardContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        keySoundEngine = KeyboardSoundEngine()

        keyboardView = KeyboardView(this)
        bindKeyboardViewCallbacks()

        setupKeyboardOverlays()

        // 套用儲存的鍵盤高度（橫向使用更高比例，避免太扁）
        val kbHeight = calculateKeyboardHeight()
        rootContainer.addView(keyboardContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            kbHeight
        ))

        // 懸浮模式
        if (prefs.floatingModeEnabled) {
            startUiStateCollect()
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

        // 註冊背景更新廣播接收器
        bgUpdateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                // 不要 clearBackground()，直接重載（reloadFromStorage 內部已有去重邏輯）
                backgroundView?.reloadFromStorage()
                keyboardView.hasCustomBackground = prefs.useCustomBackground
                keyboardView.invalidate()
                imePanelBindings.themePanel?.reloadPreviewBitmap()
                imePanelBindings.themePanel?.invalidate()
            }
        }
        val bgFilter = android.content.IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
        registerReceiver(bgUpdateReceiver, bgFilter, android.content.Context.RECEIVER_NOT_EXPORTED)

        // Sprint 3 Phase 1: 啟動 uiState collect（activePanel + isVoiceInputActive）
        startUiStateCollect()

        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        imeSessionHooks.onStartInputView(
            info = info,
            restarting = restarting,
        ) {
            // 清除上一個 session 的 composing 殘留
            currentInputConnection?.let { ic ->
                ic.beginBatchEdit()
                ic.finishComposingText()
                ic.endBatchEdit()
            }
            inputRouter.reset()
            keyboardView.updateCandidates(emptyList())
            keyboardView.updateComposingText("")
            // 重置觸控狀態，避免上一個 session 的彈出狀態殘留導致按鍵按不到
            keyboardView.resetTouchState()
        }

        // 重新套用鍵盤高度（設定頁可能已修改 / 螢幕旋轉）
        val kbHeight = calculateKeyboardHeight()
        keyboardContainer.layoutParams?.let { lp ->
            if (lp.height != kbHeight) {
                lp.height = kbHeight
                keyboardContainer.requestLayout()
            }
        }

        // AUTO 模式啟動（onStartInputView 已 reset，不需處理 switchTo 回傳的 commitResult）
        if (prefs.autoInputMode && inputRouter.inputMode != InputMode.AUTO) {
            val sr = inputRouter.switchTo(InputMode.AUTO)
            if (sr is InputResult.CommitText) currentInputConnection?.commitText(sr.text, 1)
        }

        // 同步鍵盤顯示的語言（保留使用者上次選擇的輸入法）
        keyboardView.currentInputMode = inputRouter.inputMode
        keyboardView.updateKeyRows(rowsForInputMode(inputRouter.inputMode))
        keyboardView.updateEditorInfo(info)
        syncImeCoordinatorFromRouter()

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

        syncPrefsToViews()

        // 防詐被動偵測：掃描輸入框上下文 + 註冊剪貼簿監聽
        scanForFraud()
        registerClipboardFraudListener()

        // 遊戲模式恢復
        if (isInGameMode) {
            gameLoop?.resume()
        }
    }

    internal fun initFraudModel() {
        imeFraudModelInit.init(fraudAnalyzer)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        imeSessionHooks.onFinishInputView(
            finishingInput = finishingInput,
            scope = serviceScope,
            cleanup = FinishInputViewCleanup(
                cancelAsyncWork = {
                    aiCandidateJob?.cancel()
                    aiCandidateJob = null
                },
                stopClipboardMonitoring = ::unregisterClipboardFraudListener,
                persistSmartInput = { inputRouter.smartInput.persist() },
                resetInputState = { inputRouter.reset() },
                stopSensors = {
                    shakeDetector.stop()
                    gyroController.deactivate()
                    gyroController.unregister()
                },
                resetManualModeOverride = { manualModeOverride = false },
                pauseVisualEffects = {
                    glSurfaceView?.onPause()
                    backgroundView?.pause()
                    characterView?.pauseAnimation()
                },
                flushSentenceBuffer = {
                    val sentence = sentenceBuffer.toString().trim()
                    if (sentence.length >= 2) {
                        inputRouter.smartInput.onSentenceCommitted(sentence)
                    }
                    sentenceBuffer.clear()
                },
                pauseGameLoop = { gameLoop?.pause() },
            ),
        )
    }

    override fun onFinishInput() {
        super.onFinishInput()
        imeSessionHooks.onFinishInput(
            cleanup = FinishInputCleanup(
                resetInputState = { inputRouter.reset() },
                stopSensors = {
                    shakeDetector.stop()
                    gyroController.unregister()
                },
                resetTouchState = {
                    if (::keyboardView.isInitialized) {
                        keyboardView.resetTouchState()
                    }
                },
            ),
        )
    }

    override fun onWindowShown() {
        super.onWindowShown()
    }

    override fun onDestroy() {
        imeSessionHooks.onDestroy(
            cleanup = DestroyCleanup(
                unregisterReceivers = {
                    bgUpdateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
                    bgUpdateReceiver = null
                },
                stopSensors = {
                    shakeDetector.stop()
                    gyroController.unregister()
                },
                stopGameLoop = { gameLoop?.stop() },
                destroyAudioAndRemote = {
                    speechEngine?.destroy()
                    keySoundEngine?.destroy()
                    voicePostProcessor?.destroy()
                    ttsEngine?.destroy()
                    btKeyboard?.destroy()
                    wifiServer?.destroy()
                    stickerRepository?.destroy()
                    stickerDownloadManager?.destroy()
                    particleView?.stop()
                    backgroundView?.destroy()
                    translateEngine?.close()
                    rimeDictionary?.close()
                    floatingPanelManager?.dismissAll()
                    weatherFloatingPanel?.dismiss()
                    clawResultFloatingPanel?.dismiss()
                    glSurfaceView?.onPause()
                    handwritingEngine?.close()
                },
                cancelScope = {
                    uiStateCollectJob?.cancel()
                    uiStateCollectJob = null
                    serviceScope.cancel()
                },
            ),
        )
        super.onDestroy()
    }

    internal var voiceFinalHandled = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (isFloatingMode) {
            // 懸浮模式：告訴系統不要推移 App 畫面，讓鍵盤浮在上面
            outInsets.contentTopInsets = outInsets.visibleTopInsets
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
        }
    }
    // 遠端鍵盤（藍牙 + WiFi）

    internal var swipeInputPending = false
    internal var swipeInputCandidates: List<String> = emptyList()
    internal val swipeAccumulated = mutableListOf<List<String>>()

    internal fun handleKeyPress(keyCode: Int, label: String) {
        // F-12: 語音輸入中按鍵先停止語音，避免 partial result 互蓋
        if (speechEngine?.isListening == true) {
            stopVoiceInput()
        }
        // 清除滑動輸入待選狀態（開始打字表示放棄滑動候選）
        if (swipeInputPending) {
            swipeInputPending = false
            swipeInputCandidates = emptyList()
            swipeAccumulated.clear()
        }
        // 面板輸���模式：統一路由所有實作 PanelInputTarget 的面板
        val activeTarget: PanelInputTarget? = imePanelBindings.activeInputTarget()
        if (activeTarget != null) {
            routeKeyToPanel(activeTarget, keyCode, label)
            return
        }

        // 面板開啟中但使用者按了鍵盤按鍵 → 需要輸入的面板保持開啟，其他面板關閉
        if (activePanel != null) {
            // 動態偵測：有正在輸入的面板就保持開啟
            val keepOpen = imePanelBindings.hasActiveInputTarget()
            if (!keepOpen) {
                closePanel()
            }
        }

        val ic: InputConnection = currentInputConnection ?: return

        // 更新上下文給候選排序用
        // 200 字元以支援整句上下文預測（不只字詞，還有句子級別）
        inputRouter.contextBeforeCursor = ic.getTextBeforeCursor(200, 0)?.toString() ?: ""

        ic.beginBatchEdit()
        var needsPostUpdate = true  // 是否需要在 when 結束後更新 composing/candidates
        try {
        when (keyCode) {
            KeyboardView.KEYCODE_SHIFT, KeyboardView.KEYCODE_CTRL -> { needsPostUpdate = false }

            KeyboardView.KEYCODE_TAB         -> { handleTab(ic); needsPostUpdate = false }
            KeyboardView.KEYCODE_ARROW_LEFT  -> { sendArrow(ic, KeyEvent.KEYCODE_DPAD_LEFT); needsPostUpdate = false }
            KeyboardView.KEYCODE_ARROW_RIGHT -> { sendArrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT); needsPostUpdate = false }
            KeyboardView.KEYCODE_ARROW_UP    -> { sendArrow(ic, KeyEvent.KEYCODE_DPAD_UP); needsPostUpdate = false }
            KeyboardView.KEYCODE_ARROW_DOWN  -> { sendArrow(ic, KeyEvent.KEYCODE_DPAD_DOWN); needsPostUpdate = false }

            KeyboardView.KEYCODE_DELETE      -> { handleDelete(ic); needsPostUpdate = false }
            KeyboardView.KEYCODE_ENTER       -> { handleEnter(ic); needsPostUpdate = false }
            KeyboardView.KEYCODE_SPACE       -> { handleSpace(ic); needsPostUpdate = false }
            KeyboardView.KEYCODE_SWITCH_LANG -> { handleSwitchLang(); needsPostUpdate = false }

            KeyboardView.KEYCODE_CTRL_C -> ic.performContextMenuAction(android.R.id.copy)
            KeyboardView.KEYCODE_CTRL_V -> ic.performContextMenuAction(android.R.id.paste)
            KeyboardView.KEYCODE_CTRL_X -> ic.performContextMenuAction(android.R.id.cut)
            KeyboardView.KEYCODE_CTRL_A -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyboardView.KEYCODE_CTRL_Z -> sendCtrlKey(ic, KeyEvent.KEYCODE_Z)
            KeyboardView.KEYCODE_CTRL_Y -> sendCtrlKey(ic, KeyEvent.KEYCODE_Y)

            KeyboardView.KEYCODE_VOICE -> {
                toggleVoiceInput()
                return
            }
            KeyboardView.KEYCODE_HANDWRITING -> {
                if (activePanel == PanelType.HANDWRITING) {
                    closePanel()
                } else {
                    handlePanelToggle(PanelType.HANDWRITING)
                }
                return
            }

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
                    // 三模式循環：平假名 → 片假名 → 羅馬拼音 → 平假名
                    when {
                        KeyboardState.isRomajiMode -> {
                            // romaji → hiragana
                            KeyboardState.isRomajiMode = false
                            KeyboardState.isKatakanaMode = false
                            if (eng.katakanaMode) eng.toggleKatakana()
                            keyboardView.showToast("平仮名モード")
                        }
                        !KeyboardState.isKatakanaMode -> {
                            // hiragana → katakana
                            if (!eng.katakanaMode) eng.toggleKatakana()
                            KeyboardState.isKatakanaMode = true
                            keyboardView.showToast("片仮名モード")
                        }
                        else -> {
                            // katakana → romaji
                            KeyboardState.isKatakanaMode = false
                            KeyboardState.isRomajiMode = true
                            if (eng.katakanaMode) eng.toggleKatakana()
                            keyboardView.showToast("ローマ字モード")
                        }
                    }
                    keyboardView.updateKeyRows(KeyboardState.japaneseRows())
                    keyboardView.updateComposingText(inputRouter.composingText)
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
                    if (inputRouter.composingText.isNotEmpty()) {
                        val spaceResult = inputRouter.processSpace()
                        if (spaceResult is InputResult.CommitText) {
                            val trimmed = spaceResult.text.trimEnd()
                            if (trimmed.isNotEmpty()) {
                                ic.beginBatchEdit()
                                ic.commitText(trimmed, 1)
                                ic.endBatchEdit()
                            }
                        } else {
                            val commitResult = inputRouter.getActiveEngine().forceCommit()
                            if (commitResult is InputResult.CommitText) {
                                ic.beginBatchEdit()
                                ic.commitText(commitResult.text, 1)
                                ic.endBatchEdit()
                            } else {
                                ic.finishComposingText()
                            }
                        }
                        inputRouter.reset()
                        keyboardView.updateComposingText("")
                        keyboardView.updateCandidates(emptyList())
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

        // Delete/Enter/Space 等已在 handler 內自行更新 UI，避免重複
        if (!needsPostUpdate) {
            requestAICandidates()
        } else {

        keyboardView.updateComposingText(inputRouter.composingText)

        // 候選列為空時，僅在開發者模式且有高信心預測時顯示提示
        val candidates = inputRouter.candidates.toMutableList()

        // AUTO 模式：中英混合候選（永遠同時顯示兩種語言的候選）
        if (inputRouter.inputMode == InputMode.AUTO && inputRouter.autoQwertyBuffer.isNotEmpty()) {
            val bilingual = inputRouter.getAutoCombinedCandidates()
            for (bc in bilingual) {
                if (bc !in candidates) candidates.add(bc)
            }
        }
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
        keyboardView.updateCandidates(candidates, inputRouter.candidateSourceMap)

        // AI 候選字 debounce：有組字或上下文時觸發
        requestAICandidates()

        } // end needsPostUpdate else

        } finally {
            ic.endBatchEdit()
        }
    }

    internal fun sendKey(ic: InputConnection, androidKeyCode: Int, meta: Int = 0) {
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, androidKeyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, androidKeyCode, 0, meta))
    }

    internal fun sendCtrlKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_CTRL_ON)

    internal fun sendCtrlShiftKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

    internal fun sendAltKey(ic: InputConnection, androidKeyCode: Int) =
        sendKey(ic, androidKeyCode, KeyEvent.META_ALT_ON)

    internal fun sendArrow(ic: InputConnection, androidKeyCode: Int) {
        val meta = if (keyboardView.isShiftActive()) KeyEvent.META_SHIFT_ON else 0
        sendKey(ic, androidKeyCode, meta)
    }

    internal fun applyInputResult(ic: InputConnection, result: InputResult) {
        // AUTO 模式切引擎時，注音引擎的 selectedBuffer 可能被暫存，
        // 需要先 commit 出去，否則已選字會消失
        val pending = inputRouter.consumePendingCommit()
        if (!pending.isNullOrEmpty()) {
            ic.beginBatchEdit()
            ic.commitText(pending, 1)   // commitText 自動替換 composing region
            ic.endBatchEdit()
        }

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

                ic.beginBatchEdit()
                ic.commitText(result.text, 1)  // commitText 自動替換 composing region
                ic.endBatchEdit()
                characterView?.playHappy()
                forwardTextToRemote(result.text)

                // 被動防詐：每次提交都掃（規則引擎 <1ms 零成本）
                scanForFraud()
                // 回覆風險偵測：使用者自己打的文字也掃
                scanReplyForFraud(result.text)

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

                // 情緒貼圖推薦（提交 ≥2 字時分析情緒，推薦匹配貼圖標籤）
                if (result.text.length >= 2) {
                    val tags = inputRouter.smartInput.getEmotionStickerTags(sentenceBuffer.toString())
                    if (tags.isNotEmpty()) {
                        imePanelBindings.stickerPanel?.setRecommendedTags(tags)
                        keyboardView.recommendedStickerEmoji = "🖼"
                    } else {
                        keyboardView.recommendedStickerEmoji = null
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
        syncImeCoordinatorFromRouter()
    }

    internal fun syncImeCoordinatorFromRouter() {
        imeCoordinator.setInputMode(inputRouter.inputMode)
        imeCoordinator.updateComposingText(inputRouter.composingText)
        imeCoordinator.updateCandidates(inputRouter.candidates)
    }
}
