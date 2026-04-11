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

    private lateinit var rootContainer: LinearLayout
    private lateinit var toolbarView: ToolbarView
    private lateinit var panelContainer: FrameLayout
    private lateinit var topPanelContainer: FrameLayout
    private lateinit var keyboardContainer: FrameLayout
    private var panelControlBar: View? = null
    private var transparencyOverlay: View? = null
    private var floatingBall: FloatingBallView? = null
    private var floatingOuterFrame: FrameLayout? = null
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
    private var bgUpdateReceiver: android.content.BroadcastReceiver? = null
    private val fraudAnalyzer by lazy { com.snowrealm.yukiboard.security.FraudAIAnalyzer(this) }
    private var lastFraudScanText: String? = null  // 避免重複掃描同一段文字
    private var lastFraudReplyText: String? = null // 避免重複掃描同一段回覆
    private val fraudDetectRunnable = Runnable {
        if (::panelContainer.isInitialized && panelContainer.childCount > 0) {
            (panelContainer.getChildAt(0) as? ClawPanelView)?.triggerSkill("fraud_detect")
        }
    }
    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null

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

    // RIME 詞庫
    private var rimeDictionary: com.snowrealm.yukiboard.input.dictionary.RimeDictionary? = null

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
    private var userDictPanel: UserDictionaryPanelView? = null
    private var decryptPanel: com.snowrealm.yukiboard.ui.panel.DecryptPanelView? = null
    private var dictionaryPanel: DictionaryPanelView? = null
    private var langLearnPanel: LangLearnPanelView? = null

    // 手寫辨識
    private var handwritingEngine: com.snowrealm.yukiboard.input.handwriting.HandwritingEngine? = null
    private var handwritingPanel: HandwritingPanelView? = null
    private var keySoundEngine: KeyboardSoundEngine? = null
    private var voicePostProcessor: com.snowrealm.yukiboard.speech.VoicePostProcessor? = null

    // 鍵盤尺寸調整 + 懸浮
    private var resizeHandle: KeyboardResizeHandle? = null
    private var floatingWrapper: FloatingKeyboardWrapper? = null
    private var isFloatingMode = false

    private var stickerSyncDone = false

    // 遊戲相關
    private val showGameMenuRunnable = Runnable { showGameMenu() }
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

        // 貼圖同步 + 下載管理
        stickerRepository = com.snowrealm.yukiboard.sticker.StickerRepository(applicationContext)
        // syncInBackground() 延遲到第一次開貼圖面板再呼叫
        stickerDownloadManager = com.snowrealm.yukiboard.sticker.StickerPackDownloadManager(applicationContext)

        // Claw AI（自動從 BuildConfig 匯入 API key）
        clawClient = ClawClient(applicationContext)
        voicePostProcessor = clawClient?.let { com.snowrealm.yukiboard.speech.VoicePostProcessor(it) }

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
        weatherFloatingPanel?.let { floatingPanelManager?.registerPanel(it) }
        clawResultFloatingPanel?.let { floatingPanelManager?.registerPanel(it) }

        // 下一步動作預測
        nextActionPredictor = com.snowrealm.yukiboard.input.predict.RuleBasedPredictor()

        // TTS 語音輸出引擎
        initTtsEngine()

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
        // 確保 IME window 啟用硬體加速（TextureView 影片背景需要）
        window?.window?.let { w ->
            @Suppress("DEPRECATION")
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }

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
        keyboardContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }

        keySoundEngine = KeyboardSoundEngine()

        keyboardView = KeyboardView(this)
        keyboardView.setOnKeyPressListener { keyCode, label ->
            // 按鍵音效
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
        // 長按注音/聲調直接輸出（不走 InputRouter）
        keyboardView.setOnRawCommitListener { text ->
            // 面板輸入模式：長按選字也送到面板
            val rawTarget: PanelInputTarget? = listOfNotNull(
                translatePanel, gifPanel, userDictPanel, decryptPanel,
                dictionaryPanel, langLearnPanel
            ).firstOrNull { it.isInInputMode }
            if (rawTarget != null) {
                rawTarget.onComposedTextInput(text)
                return@setOnRawCommitListener
            }
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
            // 面板輸入模式 helper：找到正在輸入的面板
            fun activePanelInputTarget(): PanelInputTarget? {
                translatePanel?.let { if (it.isInInputMode) return it }
                gifPanel?.let { if (it.isInInputMode) return it }
                userDictPanel?.let { if (it.isInInputMode) return it }
                decryptPanel?.let { if (it.isInInputMode) return it }
                dictionaryPanel?.let { if (it.isInInputMode) return it }
                langLearnPanel?.let { if (it.isInInputMode) return it }
                clipboardPanel?.let { if (it.isInInputMode) return it }
                return null
            }
            fun commitToPanel(target: PanelInputTarget, text: String) {
                target.onComposedTextInput(text)
            }

            // 滑動輸入候選：直接 commit，不走 InputRouter
            if (swipeInputPending && index in swipeInputCandidates.indices) {
                val text = swipeInputCandidates[index]
                val panelTarget = activePanelInputTarget()
                if (panelTarget != null) {
                    commitToPanel(panelTarget, text)
                } else {
                    val ic = currentInputConnection
                    if (ic != null) {
                        // F-6: 先清掉殘留的 composing 狀態
                        ic.finishComposingText()
                        inputRouter.reset()
                        ic.commitText(text, 1)
                    }
                }
                swipeInputPending = false
                swipeInputCandidates = emptyList()
                swipeAccumulated.clear()
                keyboardView.updateComposingText("")
                keyboardView.updateCandidates(emptyList())
                return@setOnCandidateSelectListener
            }

            // AI 候選字是在 Service 層附加的，不在 InputRouter/引擎內
            // 需要用 KeyboardView 的 candidates 列表取得實際文字
            val displayedCandidates = keyboardView.candidates
            val displayedText = displayedCandidates.getOrNull(index)
            val sourceMap = keyboardView.candidateSourceMap
            val isAICandidate = displayedText != null &&
                sourceMap[displayedText] == com.snowrealm.yukiboard.input.router.CandidateSource.AI

            if (isAICandidate && displayedText != null) {
                val panelTarget = activePanelInputTarget()
                if (panelTarget != null) {
                    // AI 候選字送到面板
                    if (inputRouter.composingText.isNotEmpty()) inputRouter.reset()
                    commitToPanel(panelTarget, displayedText)
                } else {
                    // AI 候選字：直接 commit，不走引擎（引擎裡沒有這個候選）
                    val ic = currentInputConnection ?: return@setOnCandidateSelectListener
                    // 先清掉 composing
                    if (inputRouter.composingText.isNotEmpty()) {
                        ic.finishComposingText()
                    }
                    inputRouter.reset()
                    applyInputResult(ic, InputResult.CommitText(displayedText))
                }
                keyboardView.updateComposingText("")
                keyboardView.updateCandidates(emptyList())
                return@setOnCandidateSelectListener
            }

            // 手寫模式下候選字選取：直接 commit，不走 InputRouter
            val hwPanel = handwritingPanel
            if (hwPanel != null && activePanel == PanelType.HANDWRITING) {
                val text = displayedCandidates.getOrNull(index) ?: return@setOnCandidateSelectListener
                currentInputConnection?.commitText(text, 1)
                keyboardView.updateCandidates(emptyList())
                inputRouter.onHandwritingCommit(text)
                return@setOnCandidateSelectListener
            }

            // 如果是 Service 端附加的候選（如 AUTO 雙語候選），Router 不認識 → 直接 commit
            val isExtraCand = displayedText != null && index >= inputRouter.candidates.size
            val result = if (isExtraCand) {
                // 先清掉 composing，然後 directCommit
                inputRouter.reset()
                InputResult.CommitText(displayedText!!)
            } else {
                inputRouter.selectCandidate(index)
            }
            // 面板輸入模式：選字提交到面板而非目標 App
            val activeTarget = activePanelInputTarget()
            if (activeTarget != null && result is InputResult.CommitText) {
                activeTarget.onComposedTextInput(result.text)
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
                    val mode = option.mode
                    // AUTO 模式：同步設定 + 顯示注音佈局（中英都能用）
                    if (mode == InputMode.AUTO) {
                        prefs.autoInputMode = true
                    } else if (prefs.autoInputMode) {
                        // 手動選了其他語言 → 關閉 AUTO
                        prefs.autoInputMode = false
                    }
                    val switchResult = inputRouter.switchTo(mode)
                    // 切換前引擎的未提交文字 → commit 到 InputConnection
                    if (switchResult is InputResult.CommitText) {
                        currentInputConnection?.commitText(switchResult.text, 1)
                    }
                    currentInputConnection?.finishComposingText()
                    characterView?.playSwitchLang()
                    keyboardView.currentInputMode = mode
                    keyboardView.updateKeyRows(rowsForInputMode(mode))
                    keyboardView.updateComposingText("")
                    keyboardView.updateCandidates(emptyList())
                    keyboardView.showToast(if (mode == InputMode.AUTO) "⚡ 已啟動智慧輸入法" else mode.displayName)
                }
                is RadialLanguageMenu.LanguageOption.Emoji -> {
                    // 切換到 Emoji 面板
                    toolbarView.activePanel = com.snowrealm.yukiboard.ui.panel.PanelType.EMOJI
                    toolbarView.onPanelSelected?.invoke(com.snowrealm.yukiboard.ui.panel.PanelType.EMOJI)
                }
            }
        }

        keyboardView.setOnFloatingToggleListener {
            prefs.floatingModeEnabled = !prefs.floatingModeEnabled
            // 重建前清理面板狀態，避免洩漏舊引用
            closePanel()
            backgroundView?.destroy()
            backgroundView = null
            setInputView(onCreateInputView())
        }

        keyboardView.onSwipeSyllableConfirmed = { label ->
            // 注音符號/聲調不直接 commit（等滑動結束由候選字選擇）
            val ch = label.firstOrNull()
            val isBopomofo = ch != null && (ch in '\u3105'..'\u312F' || ch in setOf('ˉ','ˊ','ˇ','ˋ','˙'))
            if (!isBopomofo) {
                currentInputConnection?.commitText(label, 1)
            }
        }

        keyboardView.onStickerHintClick = {
            handlePanelToggle(PanelType.STICKER)
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
        // 背景用螢幕完整寬度，避免鍵盤縮窄時露出黑色
        val screenW = resources.displayMetrics.widthPixels
        keyboardContainer.addView(bgView, FrameLayout.LayoutParams(
            screenW,
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
        keyboardView.keyTextOpacity = prefs.keyTextOpacity
        keyboardView.perKeySizes = prefs.getAllPerKeySizes()
        keyboardView.hapticEnabled = prefs.keyPressVibration
        keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
        keyboardView.swipeInputEnabled = prefs.swipeInput
        keyboardView.showTouchAreaEnabled = prefs.showTouchArea

        // 單手模式
        keyboardView.oneHandSide = try {
            KeyboardView.OneHandSide.valueOf(prefs.oneHandSide)
        } catch (_: Exception) { KeyboardView.OneHandSide.NONE }
        keyboardView.onOneHandChanged = { side ->
            prefs.oneHandSide = side.name
        }

        // 套用當前主題
        val currentTheme = ThemeManager.getInstance(this).currentTheme
        keyboardView.applyTheme(currentTheme)
        toolbarView.theme = currentTheme
        toolbarView.setAnimStyle(prefs.toolbarAnimStyle)
        toolbarView.hiddenPanels = buildHiddenPanelSet()
        keyboardView.setAnimStyle(prefs.toolbarAnimStyle)

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
        // VRM 在 IME SurfaceView 下容易出問題，預設 Lottie；���用者可在設定開啟 VRM
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
        // 依偏好設定控制角色顯示
        if (!prefs.characterAnimEnabled) {
            charView.visibility = View.GONE
        }

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

        // 上方面板容器（翻譯/GIF 等需要打字的面板放這裡，不擋鍵盤）
        topPanelContainer = FrameLayout(this)
        topPanelContainer.visibility = View.GONE
        rootContainer.addView(topPanelContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
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

        // 註冊背景更新廣播接收器
        bgUpdateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                // 不要 clearBackground()，直接重載（reloadFromStorage 內部已有去重邏輯）
                backgroundView?.reloadFromStorage()
                keyboardView.hasCustomBackground = prefs.useCustomBackground
                keyboardView.invalidate()
                themePanel?.reloadPreviewBitmap()
                themePanel?.invalidate()
            }
        }
        val bgFilter = android.content.IntentFilter("com.snowrealm.yukiboard.ACTION_BG_UPDATED")
        registerReceiver(bgUpdateReceiver, bgFilter, android.content.Context.RECEIVER_NOT_EXPORTED)

        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 清除上一個 session 的 composing 殘留
        currentInputConnection?.let { ic ->
            ic.beginBatchEdit()
            ic.finishComposingText()
            ic.endBatchEdit()
        }
        inputRouter.reset()
        keyboardView.updateCandidates(emptyList())
        keyboardView.updateComposingText("")

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
        // backgroundView 的 resume 由下面的 reloadFromStorage() 統一處理
        if (prefs.characterAnimEnabled) {
            characterView?.resumeAnimation()
        }

        // 同步動畫主題（設定頁/主題面板可能已更改）
        toolbarView.setAnimStyle(prefs.toolbarAnimStyle)
        toolbarView.hiddenPanels = buildHiddenPanelSet()
        keyboardView.setAnimStyle(prefs.toolbarAnimStyle)

        // 同步角色動畫顯示偏好（設定頁可能已更改）
        characterView?.visibility = if (prefs.characterAnimEnabled) View.VISIBLE else View.GONE
        yukiSpeechBubble?.visibility = if (prefs.characterAnimEnabled) View.VISIBLE else View.GONE

        // 同步 VRM/Lottie 渲染模式（設定頁可能已切換）
        characterView?.let { cv ->
            val wantVrm = prefs.vrmCharacterEnabled
            if (wantVrm && cv.renderMode != CharacterView.RenderMode.VRM) {
                cv.initVrmMode()  // VRM init 失敗會自動 fallback 回 Lottie
            } else if (!wantVrm && cv.renderMode != CharacterView.RenderMode.LOTTIE) {
                cv.initLottieMode()
            }
        }

        // 同步 TTS 語音音色（設定頁可能已更改）
        (ttsEngine as? com.snowrealm.yukiboard.speech.AndroidTtsEngine)?.let { engine ->
            val savedVoice = prefs.ttsVoiceName
            engine.setVoice(savedVoice.takeIf { it.isNotEmpty() })
        }

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
        keyboardView.keyTextOpacity = prefs.keyTextOpacity
        keyboardView.perKeySizes = prefs.getAllPerKeySizes()
        keyboardView.hapticEnabled = prefs.keyPressVibration
        keyboardView.swipeTrailEnabled = prefs.swipeTrailEnabled
        keyboardView.swipeInputEnabled = prefs.swipeInput
        keyboardView.showTouchAreaEnabled = prefs.showTouchArea

        // 影片背景耗電提示（僅顯示一次）
        val savedBg = com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.getSavedBackground(this)
        if (savedBg != null && com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView.isVideoFile(savedBg)) {
            if (!prefs.videoBgWarnShown) {
                prefs.videoBgWarnShown = true
                keyboardView.showToast("提示：影片背景較耗電，可在主題設定改用圖片")
            }
        }

        // 防詐被動偵測：掃描輸入框上下文 + 註冊剪貼簿監聽
        scanForFraud()
        registerClipboardFraudListener()

        // 遊戲模式恢復
        if (isInGameMode) {
            gameLoop?.resume()
        }
    }

    /**
     * 初始化防詐 TFLite 模型 + 模型自動更新。
     */
    private fun initFraudModel() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // 1. 初始化 TFLite 本地模型
                val localModel = com.snowrealm.yukiboard.security.FraudLocalModel(applicationContext)
                localModel.initialize()
                if (localModel.isAvailable()) {
                    fraudAnalyzer.localModel = localModel
                    android.util.Log.i("YukiBoardIME", "Fraud TFLite model loaded")
                }

                // 2. 檢查 Supabase 是否有新版本（自動下載）
                val supaUrl = com.snowrealm.yukiboard.BuildConfig.YUKIBOARD_SUPABASE_URL
                val supaKey = com.snowrealm.yukiboard.BuildConfig.YUKIBOARD_SUPABASE_ANON_KEY
                if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                    val mgr = com.snowrealm.yukiboard.security.FraudModelManager(applicationContext)
                    val updated = mgr.checkAndUpdateIfNeeded(
                        supaUrl, supaKey,
                        com.snowrealm.yukiboard.BuildConfig.VERSION_CODE
                    )
                    if (updated) {
                        // 模型已更新，重新載入
                        localModel.release()
                        val newModel = com.snowrealm.yukiboard.security.FraudLocalModel(applicationContext)
                        newModel.initialize()
                        if (newModel.isAvailable()) {
                            fraudAnalyzer.localModel = newModel
                            android.util.Log.i("YukiBoardIME", "Fraud model updated and reloaded")
                        }
                    }
                }
                // 3. 同步政府公開防詐資料（MODA + 165 + OpenPhish）
                val openDataSync = fraudAnalyzer.openDataSync
                if (openDataSync.needsSync()) {
                    val count = openDataSync.sync()
                    android.util.Log.i("YukiBoardIME", "Fraud open data synced: $count domains")

                    // 上傳到 Supabase 供其他用戶使用
                    if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                        openDataSync.uploadToSupabase(supaUrl, supaKey)
                        openDataSync.downloadFromSupabase(supaUrl, supaKey)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("YukiBoardIME", "Fraud model init: ${e.message}")
            }
        }
    }

    /**
     * 被動防詐偵測 — 讀取輸入框上方文字，背景跑規則引擎。
     * 規則引擎 <1ms 零成本，每次 commit 都可掃。
     * 風險 ≥ 30 → 雪凜泡泡警告；點泡泡 → 開 Claw 面板深度分析。
     */
    private fun scanForFraud() {
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

    /**
     * 回覆風險偵測 — 掃描使用者正在打的文字。
     * 如果使用者自己打出「好我轉帳」「帳號多少」等回覆，代表可能正在被騙。
     */
    private fun scanReplyForFraud(committedText: String) {
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

    /**
     * 剪貼簿防詐掃描 — 使用者複製文字時自動掃描。
     * 聊天 App 的對方訊息只有複製後才能讀到。
     */
    private fun scanClipboardForFraud(text: String) {
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

    /**
     * 顯示防詐雪凜氣泡（統一入口）。
     */
    private suspend fun showFraudBubble(
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

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            val charAnchor = characterView as? android.view.View ?: return@withContext
            yukiSpeechBubble?.let { bubble ->
                bubble.isFraudWarning = true
                bubble.onBubbleTapped = {
                    bubble.hideNow()
                    showPanel(PanelType.CLAW)
                    charAnchor.removeCallbacks(fraudDetectRunnable)
                    charAnchor.postDelayed(fraudDetectRunnable, 300)
                }
                bubble.show(warning, charAnchor)
            }
            // 雪凜表情反應
            val reaction = if (replyWarning || score >= 60) "worried" else "surprised"
            characterView?.playReaction(reaction)
        }
    }

    /**
     * 註冊剪貼簿監聽器（防詐用）。
     */
    private fun registerClipboardFraudListener() {
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

    /**
     * 移除剪貼簿監聽器。
     */
    private fun unregisterClipboardFraudListener() {
        val listener = clipboardListener ?: return
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            cm?.removePrimaryClipChangedListener(listener)
        } catch (_: Exception) { }
        clipboardListener = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        aiCandidateJob?.cancel()
        aiCandidateJob = null
        unregisterClipboardFraudListener()
        inputRouter.smartInput.persist()
        inputRouter.reset()
        shakeDetector.stop()
        gyroController.deactivate()
        gyroController.unregister()
        manualModeOverride = false

        glSurfaceView?.onPause()
        backgroundView?.pause()
        characterView?.pauseAnimation()

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
        // 重置觸控狀態，避免下次鍵盤有殘留
        if (::keyboardView.isInitialized) {
            keyboardView.resetTouchState()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        // 某些 App 不觸發 onStartInputView，在這裡重置確保鍵盤可用
        if (currentInputConnection != null && ::keyboardView.isInitialized) {
            keyboardView.resetTouchState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgUpdateReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        bgUpdateReceiver = null
        shakeDetector.stop()
        gyroController.unregister()
        gameLoop?.stop()
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

    /** 根據 YukiPreferences 建構要隱藏的功能列項目集合 */
    private fun buildHiddenPanelSet(): Set<PanelType> {
        val hidden = mutableSetOf<PanelType>()
        if (!prefs.toolbarVoice) hidden += PanelType.VOICE
        if (!prefs.toolbarTranslate) hidden += PanelType.TRANSLATE
        if (!prefs.toolbarGif) hidden += PanelType.GIF
        if (!prefs.toolbarEmoji) hidden += PanelType.EMOJI
        if (!prefs.toolbarKaomoji) hidden += PanelType.KAOMOJI
        if (!prefs.toolbarSticker) hidden += PanelType.STICKER
        if (!prefs.toolbarClipboard) hidden += PanelType.CLIPBOARD
        if (!prefs.toolbarRemote) hidden += PanelType.REMOTE
        if (!prefs.toolbarClaw) hidden += PanelType.CLAW
        if (!prefs.toolbarCalculator) hidden += PanelType.CALCULATOR
        if (!prefs.toolbarPhrase) hidden += PanelType.PHRASE
        if (!prefs.toolbarUserDict) hidden += PanelType.USER_DICT
        if (!prefs.toolbarDecrypt) hidden += PanelType.DECRYPT
        if (!prefs.toolbarColorPicker) hidden += PanelType.COLOR_PICKER
        if (!prefs.toolbarUnitConverter) hidden += PanelType.UNIT_CONVERTER
        if (!prefs.toolbarHandwriting) hidden += PanelType.HANDWRITING
        if (!prefs.toolbarMusic) hidden += PanelType.MUSIC
        if (!prefs.toolbarDictionary) hidden += PanelType.DICTIONARY
        if (!prefs.toolbarLangLearn) hidden += PanelType.LANG_LEARN
        if (!prefs.toolbarTheme) hidden += PanelType.THEME
        return hidden
    }

    private fun handlePanelToggle(panel: PanelType) {
        // 再次點擊同一面板 → 關閉
        if (activePanel == panel) {
            closePanel()
            return
        }

        when (panel) {
            PanelType.VOICE -> {
                // VOICE/SETTINGS 不是真正的面板，不呼叫 closePanel 以免殺掉 composing text
                activePanel = null
                toolbarView.activePanel = null
                toggleVoiceInput()
                return
            }
            PanelType.SETTINGS -> {
                activePanel = null
                toolbarView.activePanel = null
                openSettings()
                return
            }
            else -> {
                activePanel = panel
                toolbarView.activePanel = panel
                showPanel(panel)
            }
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
        // 離開面板輸入模式並清除所有引用
        translatePanel?.exitInputMode()
        gifPanel?.exitInputMode()
        userDictPanel?.exitInputMode()
        decryptPanel?.exitInputMode()
        dictionaryPanel?.exitInputMode()
        langLearnPanel?.exitInputMode()
        translatePanel = null
        gifPanel = null
        userDictPanel = null
        decryptPanel = null
        dictionaryPanel = null
        langLearnPanel = null
        panelContainer.removeAllViews()
        panelContainer.visibility = View.GONE
        topPanelContainer.removeAllViews()
        topPanelContainer.visibility = View.GONE

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
                p.onShowDialog = { dialog -> showImeDialog(dialog) }
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
                // 自動進入輸入模式（使用者不需先點輸入框就能直接打字）
                p.enterInputMode()
                p.onTranslatedSelected = { translated ->
                    currentInputConnection?.commitText(translated, 1)
                }
                p.onSpeakRequest = { text, locale ->
                    onTranslateSpeakRequest(text, locale)
                }
                p.onInsertToApp = { translated ->
                    currentInputConnection?.commitText(translated, 1)
                }
                val engine = translateEngine
                if (engine != null) {
                    p.translationProvider = { src, fromLang, toLang, callback ->
                        serviceScope.launch {
                            try {
                                val result = engine.translate(src, fromLang, toLang)
                                callback(result)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                            catch (e: Exception) {
                                callback("翻譯失敗: ${e.message}")
                            }
                        }
                    }
                }
                p
            }
            PanelType.GIF -> {
                val p = GifPanelView(this).also { gifPanel = it }
                p.theme = theme
                p.onShowDialog = { dialog -> showImeDialog(dialog) }
                p.onGifSelected = { gif ->
                    commitImageContent(gif.fullUrl)
                }
                val gifClient = giphyClient
                if (gifClient != null && gifClient.isAvailable()) {
                    p.onSearch = { query, callback ->
                        serviceScope.launch {
                            try {
                                val results = if (query.isBlank()) gifClient.fetchTrending()
                                              else gifClient.search(query)
                                callback(results)
                            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                            catch (e: Exception) {
                                callback(emptyList())
                            }
                        }
                    }
                }
                // 自動進入輸入模式
                p.enterInputMode()
                p
            }
            PanelType.STICKER -> {
                val p = StickerPanelView(this).also { stickerPanel = it }
                p.theme = theme
                p.repository = stickerRepository
                p.downloadManager = stickerDownloadManager
                if (!stickerSyncDone) {
                    stickerRepository?.syncInBackground()
                    stickerSyncDone = true
                }
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
                p.onAnimThemeSelected = { styleId ->
                    toolbarView.setAnimStyle(styleId)
                    keyboardView.setAnimStyle(styleId)
                }
                val themeListener: (com.snowrealm.yukiboard.theme.KeyboardTheme) -> Unit = { newTheme ->
                    p.currentThemeId = newTheme.id
                    p.theme = newTheme
                    p.invalidate()
                }
                tm.addOnThemeChangedListener(themeListener)
                p.addOnDetachListener {
                    tm.removeOnThemeChangedListener(themeListener)
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
        keyboardView.keyTextOpacity = prefs.keyTextOpacity
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
                    // 重建前清理面板狀態
                    closePanel()
                    backgroundView?.destroy()
                    backgroundView = null
                    setInputView(onCreateInputView())
                }
                p
            }
            PanelType.CLAW -> {
                val p = ClawPanelView(this)
                p.theme = theme
                p.clawClient = clawClient
                p.prefs = prefs
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
            PanelType.CALCULATOR -> {
                val p = CalculatorPanelView(this)
                p.theme = theme
                p.onInsertResult = { result ->
                    currentInputConnection?.commitText(result, 1)
                }
                p
            }
            PanelType.PHRASE -> {
                val p = PhrasePanelView(this)
                p.theme = theme
                p.onInsertPhrase = { phrase ->
                    currentInputConnection?.commitText(phrase, 1)
                }
                p
            }
            PanelType.USER_DICT -> {
                val p = UserDictionaryPanelView(this).also { userDictPanel = it }
                p.theme = theme
                p.userDictionary = com.snowrealm.yukiboard.input.dictionary.UserDictionary(this)
                p.onShowDialog = { dialog -> showImeDialog(dialog) }
                p.onExportRequest = { data ->
                    try {
                        val file = java.io.File(getExternalFilesDir(null), "yukiboard_user_dict.txt")
                        file.writeText(data)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this, "已匯出至 ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this, "匯出失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                p.onImportRequest = {
                    try {
                        val file = java.io.File(getExternalFilesDir(null), "yukiboard_user_dict.txt")
                        if (file.exists()) {
                            val count = p.importEntries(file.readText())
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(this, "已匯入 $count 筆詞彙", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(this, "找不到匯入檔案：${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(this, "匯入失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                p.enterInputMode()
                p
            }
            PanelType.DECRYPT -> {
                val p = com.snowrealm.yukiboard.ui.panel.DecryptPanelView(this).also { decryptPanel = it }
                p.theme = theme
                p.onEncryptedResult = { encrypted ->
                    currentInputConnection?.commitText(encrypted, 1)
                }
                p.enterInputMode()
                p
            }
            PanelType.COLOR_PICKER -> {
                val p = ColorPickerPanelView(this)
                p.theme = theme
                p.onInsertColor = { colorCode ->
                    currentInputConnection?.commitText(colorCode, 1)
                }
                p
            }
            PanelType.UNIT_CONVERTER -> {
                val p = UnitConverterPanelView(this)
                p.theme = theme
                p.onInsertResult = { result ->
                    currentInputConnection?.commitText(result, 1)
                }
                p
            }
            PanelType.MUSIC -> {
                val p = MusicPanelView(this)
                p.theme = theme
                p.soundEngine = keySoundEngine
                p.onImportMidi = {
                    com.snowrealm.yukiboard.ui.MidiImportActivity.onMidiImported = { midiSong ->
                        val song = MusicPanelView.Song(
                            name = midiSong.name,
                            artist = "MIDI",
                            bpm = 120,
                            notes = midiSong.notes.map { n ->
                                MusicPanelView.SongNote(n.semitone, n.startMs, n.durationMs)
                            }
                        )
                        android.os.Handler(android.os.Looper.getMainLooper()).post { p.addImportedSong(song) }
                    }
                    try {
                        val intent = android.content.Intent(this, com.snowrealm.yukiboard.ui.MidiImportActivity::class.java)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
                p
            }
            PanelType.HANDWRITING -> {
                if (handwritingEngine == null) {
                    handwritingEngine = com.snowrealm.yukiboard.input.handwriting.HandwritingEngine(this).also { it.init() }
                }
                val p = HandwritingPanelView(this, handwritingEngine!!).also { handwritingPanel = it }
                p.theme = theme
                p.onCandidateSelected = { text ->
                    currentInputConnection?.commitText(text, 1)
                    keyboardView.updateCandidates(emptyList())
                    // 通知 InputRouter 學習這個字
                    inputRouter.onHandwritingCommit(text)
                }
                p.onRecognized = { candidates ->
                    if (candidates.isNotEmpty()) {
                        val sourceMap = candidates.associateWith {
                            com.snowrealm.yukiboard.input.router.CandidateSource.DICTIONARY
                        }
                        keyboardView.updateCandidates(candidates, sourceMap)
                    }
                }
                p.onBackspace = {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
                p.onConfirm = {
                    sendDefaultEditorAction(true)
                }
                // 確保預設模型已下載
                p.ensureReady { ready ->
                    if (!ready) {
                        android.widget.Toast.makeText(this, "手寫模型下載中…", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                p
            }
            PanelType.DICTIONARY -> {
                val p = DictionaryPanelView(this).also { dictionaryPanel = it }
                p.theme = theme
                p.inputTextProvider = {
                    val ic = currentInputConnection
                    val selected = ic?.getSelectedText(0)?.toString()
                    if (!selected.isNullOrEmpty()) selected
                    else ic?.getTextBeforeCursor(200, 0)?.toString()?.trim()?.split("\\s+".toRegex())?.lastOrNull() ?: ""
                }
                p.onInsertText = { text -> currentInputConnection?.commitText(text, 1) }
                p.onSpeak = { text, locale -> ttsSpeak(text, locale) }
                p
            }
            PanelType.LANG_LEARN -> {
                val p = LangLearnPanelView(this).also { langLearnPanel = it }
                p.theme = theme
                p.inputTextProvider = {
                    val ic = currentInputConnection
                    val selected = ic?.getSelectedText(0)?.toString()
                    if (!selected.isNullOrEmpty()) selected
                    else ic?.getTextBeforeCursor(200, 0)?.toString()?.trim()?.split("\\s+".toRegex())?.lastOrNull() ?: ""
                }
                p.onInsertText = { text -> currentInputConnection?.commitText(text, 1) }
                p.onSpeak = { text, locale -> ttsSpeak(text, locale) }
                p
            }
            else -> return
        }

        // 動態偵測：面板有輸入功能（實作 PanelInputTarget）就放在鍵盤上方，不擋鍵盤
        val isInputPanel = panelView is PanelInputTarget
                || panel == PanelType.CLIPBOARD  // 剪貼簿沒有搜尋框但需要顯示在上方
        if (isInputPanel) {
            topPanelContainer.addView(panelView)
            topPanelContainer.visibility = View.VISIBLE
        } else {
            panelContainer.addView(panelView)
            panelContainer.visibility = View.VISIBLE
            // 加入面板控制列（透明度 + 圖層交換）
            showPanelControlBar()
            // 重設圖層順序
            if (isPanelBehindKeyboard) {
                isPanelBehindKeyboard = false
                panelContainer.bringToFront()
            }
        }
    }

    private fun closePanel() {
        // 離開面板輸入模式並清除引用，避免已移除面板繼續攔截按鍵
        translatePanel?.exitInputMode()
        gifPanel?.exitInputMode()
        userDictPanel?.exitInputMode()
        decryptPanel?.exitInputMode()
        dictionaryPanel?.exitInputMode()
        langLearnPanel?.exitInputMode()
        translatePanel = null
        gifPanel = null
        userDictPanel = null
        decryptPanel = null
        dictionaryPanel = null
        langLearnPanel = null
        handwritingPanel = null
        activePanel = null
        toolbarView.activePanel = null
        panelContainer.removeAllViews()
        panelContainer.visibility = View.GONE
        topPanelContainer.removeAllViews()
        topPanelContainer.visibility = View.GONE
        hidePanelControlBar()
        // 回到鍵盤時確保 InputConnection composing 狀態乾淨
        currentInputConnection?.finishComposingText()
        // 確保圖層正常
        if (isPanelBehindKeyboard) {
            isPanelBehindKeyboard = false
            panelContainer.bringToFront()
        }
        // 確保雪凜角色始終在最上層
        characterView?.bringToFront()
        yukiSpeechBubble?.bringToFront()
        // 確保鍵盤重新獲得焦點和觸控
        keyboardView.isEnabled = true
        keyboardView.requestFocus()
        keyboardView.resetTouchState()
        // 恢復自訂背景（面板操作可能影響 z-order）
        val bgHas = backgroundView?.reloadFromStorage() ?: false
        val useCustomBg = prefs.useCustomBackground && bgHas
        val glassKeys = useCustomBg || prefs.transparentKeyboard
        keyboardView.hasCustomBackground = glassKeys
        keyboardView.invalidate()
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
        if (toolbarIndex < 0) return
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

    // ── 鍵盤即時透明度浮動滑桿 ──

    private fun showTransparencyOverlay() {
        hideTransparencyOverlay()
        val dp = resources.displayMetrics.density

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD1A1A2E.toInt())
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        }

        // 標題列：標題 + 關閉按鈕
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(android.widget.TextView(this).apply {
            text = "🎨 透明度調整"
            textSize = 12f
            setTextColor(0xFF7FD8FF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(android.widget.TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(0xFF8899AA.toInt())
            setPadding((8 * dp).toInt(), 0, 0, 0)
            setOnClickListener { hideTransparencyOverlay() }
        })
        overlay.addView(header)

        // 背景遮罩透明度
        val bgLabel = android.widget.TextView(this).apply {
            text = "背景遮罩: %d%%".format((prefs.bgOverlayAlpha * 100) / 255)
            textSize = 11f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        }
        overlay.addView(bgLabel)
        overlay.addView(android.widget.SeekBar(this).apply {
            max = 255
            progress = prefs.bgOverlayAlpha
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.bgOverlayAlpha = p
                    bgLabel.text = "背景遮罩: %d%%".format((p * 100) / 255)
                    keyboardView.bgOverlayAlpha = p
                    backgroundView?.overlayAlpha = p
                    keyboardView.invalidate()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        })

        // 按鍵不透明度
        val keyLabel = android.widget.TextView(this).apply {
            text = "按鍵不透明度: %d%%".format((prefs.keyOpacity * 100) / 255)
            textSize = 11f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
        }
        overlay.addView(keyLabel)
        overlay.addView(android.widget.SeekBar(this).apply {
            max = 255
            progress = prefs.keyOpacity
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.keyOpacity = p
                    keyLabel.text = "按鍵不透明度: %d%%".format((p * 100) / 255)
                    keyboardView.keyOpacity = p
                    keyboardView.invalidate()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        })

        // 文字不透明度
        val textLabel = android.widget.TextView(this).apply {
            text = "文字不透明度: %d%%".format((prefs.keyTextOpacity * 100) / 255)
            textSize = 11f
            setTextColor(0xFFB0D4FF.toInt())
            setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
        }
        overlay.addView(textLabel)
        overlay.addView(android.widget.SeekBar(this).apply {
            max = 255
            progress = prefs.keyTextOpacity
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.keyTextOpacity = p
                    textLabel.text = "文字不透明度: %d%%".format((p * 100) / 255)
                    keyboardView.keyTextOpacity = p
                    keyboardView.invalidate()
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        })

        // 插入到 toolbarView 之後
        val toolbarIndex = rootContainer.indexOfChild(toolbarView)
        if (toolbarIndex < 0) return
        rootContainer.addView(overlay, toolbarIndex + 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        transparencyOverlay = overlay
    }

    private fun hideTransparencyOverlay() {
        transparencyOverlay?.let { rootContainer.removeView(it) }
        transparencyOverlay = null
    }

    /** 單手模式：NONE → LEFT → RIGHT → NONE 循環 */
    private fun toggleOneHandMode() {
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

    /** 交換面板與鍵盤的圖層：��板到後面 → 可打字；再按一次 → 面板回前面 */
    private fun togglePanelLayer() {
        isPanelBehindKeyboard = !isPanelBehindKeyboard
        if (isPanelBehindKeyboard) {
            // 面板移到鍵盤後面：讓 keyboardView 接收觸���
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
            // 面板回到��面
            panelContainer.bringToFront()
            (panelControlBar as? LinearLayout)?.let { bar ->
                val btn = bar.getChildAt(bar.childCount - 1)
                if (btn is android.widget.TextView) {
                    btn.text = "⇅ 輸入"
                    btn.setTextColor(0xFF7FD8FF.toInt())
                }
            }
        }
        // 確保雪凜角色 + 語音泡泡始終在最上層
        characterView?.bringToFront()
        yukiSpeechBubble?.bringToFront()
    }

    private fun applyTheme(theme: KeyboardTheme) {
        toolbarView.theme = theme
        toolbarView.setAnimStyle(prefs.toolbarAnimStyle)
        keyboardView.setAnimStyle(prefs.toolbarAnimStyle)
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

        // 顯示語音辨識 Overlay + 按鈕回呼
        voiceFinalHandled = false
        voiceOverlay?.onStop = { stopVoiceInput() }
        voiceOverlay?.onPause = {
            // 暫停錄音（Whisper/FasterWhisper 支援，Android 內建不支援暫停）
            if (speechEngine is WhisperSpeechEngine || speechEngine is FasterWhisperSpeechEngine) {
                // 暫停靠 isListening flag — 錄音 loop 會跳過讀取
                // 目前引擎沒有 pause API，用 overlay 狀態控制即可
            }
            characterView?.playIdle()
        }
        voiceOverlay?.onResume = {
            characterView?.playListening()
        }
        voiceOverlay?.show(engineName)

        // 語言選擇：auto=依輸入模式推斷, mixed=不指定(引擎自動偵測), 其餘=指定語言
        val speechLang = prefs.speechLanguage
        val localeHint = when (speechLang) {
            "auto" -> when (inputRouter.inputMode) {
                com.snowrealm.yukiboard.input.router.InputMode.ENGLISH -> "en-US"
                com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO,
                com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
                com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> "zh-TW"
                com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> "zh-CN"
                com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> "ja-JP"
                com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> "ko-KR"
                com.snowrealm.yukiboard.input.router.InputMode.GERMAN -> "de-DE"
                com.snowrealm.yukiboard.input.router.InputMode.FRENCH -> "fr-FR"
                com.snowrealm.yukiboard.input.router.InputMode.SPANISH -> "es-ES"
                com.snowrealm.yukiboard.input.router.InputMode.VIETNAMESE -> "vi-VN"
                com.snowrealm.yukiboard.input.router.InputMode.ITALIAN -> "it-IT"
                else -> "zh-TW"
            }
            "mixed" -> ""  // 空字串 = 不指定，引擎自動偵測
            else -> speechLang
        }

        keyboardView.voiceListeningLocale = "auto"

        // 設定上下文文字 + 靜音偵測靈敏度
        val textBefore = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString()
        val silenceThreshold = when (prefs.voiceSilenceSensitivity) {
            0 -> 800    // 高靈敏（安靜環境）
            2 -> 2000   // 低靈敏（嘈雜環境）
            else -> 1200 // 標準
        }
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
                if (!voiceFinalHandled) {
                    voiceOverlay?.updatePartial(partial)
                }
            },
            onFinalResult = { result ->
                if (voiceFinalHandled) return@startListening
                voiceFinalHandled = true
                keyboardView.voiceListeningLocale = null
                if (result.isNotEmpty() && prefs.voicePostProcessEnabled) {
                    voiceOverlay?.showFinal("整理中…")
                    val lang = localeHint  // 與辨識使用相同語言設定（空字串=混合模式）
                    voicePostProcessor?.process(result, lang) { cleaned ->
                        voiceOverlay?.showFinal(cleaned)
                    }
                } else if (result.isNotEmpty()) {
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
    // IME Dialog 輔助（面板用）
    // =========================================================================

    /**
     * 在 IME 上方顯示 AlertDialog。
     *
     * 核心問題：IME 自己就是鍵盤，TYPE_APPLICATION_ATTACHED_DIALOG 會被自己的鍵盤擋住。
     * 解法：先 requestHideSelf 隱藏鍵盤，再用 TYPE_APPLICATION_OVERLAY 顯示 Dialog。
     * 當使用者點擊 Dialog 的 EditText 時，系統會重新呼叫 IME 來服務該 EditText。
     */
    private fun showImeDialog(dialog: android.app.AlertDialog) {
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
        val screenH = resources.displayMetrics.heightPixels
        val kbWidth = (screenW * prefs.keyboardWidthRatio).toInt()

        // outerFrame 佔螢幕 65% 高度，背景透明 → 鍵盤可自由上下移動
        val outerFrame = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 點擊空白區域不攔截（讓觸控穿透到下面的 App）
            isClickable = false
            isFocusable = false
        }
        val outerH = (screenH * 0.65f).toInt()

        // 預設位置：底部居中
        val wrapperParams = FrameLayout.LayoutParams(kbWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
        if (prefs.floatingX >= 0) {
            wrapperParams.gravity = Gravity.NO_GRAVITY
            wrapperParams.leftMargin = prefs.floatingX
            wrapperParams.topMargin = prefs.floatingY
        } else {
            wrapperParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        outerFrame.addView(wrapper, wrapperParams)

        // 拖動回呼：自由移動，限制在 outerFrame 範圍內
        wrapper.onDragMove = { dx, dy ->
            val lp = wrapper.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.NO_GRAVITY
            lp.leftMargin = (lp.leftMargin + dx.toInt()).coerceIn(0, screenW - kbWidth)
            lp.topMargin = (lp.topMargin + dy.toInt()).coerceIn(0, outerH - dragBarH - actionBarH)
            wrapper.layoutParams = lp
            prefs.floatingX = lp.leftMargin
            prefs.floatingY = lp.topMargin
        }

        wrapper.onToggleDock = {
            prefs.floatingModeEnabled = false
            prefs.floatingX = -1
            prefs.floatingY = -1
            closePanel()
            backgroundView?.destroy()
            backgroundView = null
            setInputView(onCreateInputView())
        }

        wrapper.onClose = {
            prefs.floatingModeEnabled = false
            requestHideSelf(0)
        }

        // 調整大小回呼
        val minW = (screenW * 0.5f).toInt()
        val maxW = screenW
        wrapper.onResize = { deltaWidth ->
            val lp = wrapper.layoutParams as FrameLayout.LayoutParams
            val newW = (lp.width + deltaWidth).coerceIn(minW, maxW)
            lp.width = newW
            // 確保不超出右側邊界
            if (lp.leftMargin + newW > screenW) {
                lp.leftMargin = screenW - newW
            }
            wrapper.layoutParams = lp
            prefs.keyboardWidthRatio = newW.toFloat() / screenW
        }

        // 縮小成懸浮球
        wrapper.onMinimize = {
            minimizeToFloatingBall()
        }

        // outerFrame 固定高度 → IME 視窗會變大，給懸浮鍵盤移動空間
        outerFrame.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, outerH
        )
        floatingOuterFrame = outerFrame

        return outerFrame
    }

    /** 懸浮鍵盤 → 懸浮球 */
    private fun minimizeToFloatingBall() {
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

    /** 懸浮球 → 懸浮鍵盤 */
    private fun expandFromFloatingBall() {
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

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (isFloatingMode) {
            // 懸浮模式：告訴系統不要推移 App 畫面，讓鍵盤浮在上面
            outInsets.contentTopInsets = outInsets.visibleTopInsets
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
        }
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
            GameMode.WORDLE -> WordleGame(ctx)
            GameMode.WORD_CHAIN -> WordChainGame(ctx)
            GameMode.CROSSWORD -> CrosswordGame(ctx)
            GameMode.MUSIC_TYPING -> MusicTypingGame(ctx)
            GameMode.YUKI_HOUSE -> YukiHouseGame(ctx)
            GameMode.TYPING_FARM -> TypingFarmGame(ctx)
            GameMode.SPEED_TYPING_BATTLE -> SpeedTypingBattleGame(ctx)
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
            is WordleGame -> game.onGameEvent = callback
            is WordChainGame -> game.onGameEvent = callback
            is CrosswordGame -> game.onGameEvent = callback
            is MusicTypingGame -> game.onGameEvent = callback
            is YukiHouseGame -> game.onGameEvent = callback
            is TypingFarmGame -> game.onGameEvent = callback
            is SpeedTypingBattleGame -> game.onGameEvent = callback
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

    /**
     * 多段滑動候選組合：
     * 假設累積了 [["今","金"], ["天","添"]]
     * 組合出 ["今天","金天","今添", "今","金","天","添"]
     */
    private fun buildSwipeCombinedCandidates(): List<String> {
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

    private var swipeInputPending = false
    private var swipeInputCandidates: List<String> = emptyList()
    /** 累積的滑動候選（每次滑動產生的候選列表） */
    private val swipeAccumulated = mutableListOf<List<String>>()

    private fun handleKeyPress(keyCode: Int, label: String) {
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
        val activeTarget: PanelInputTarget? = when {
            translatePanel?.isInInputMode == true -> translatePanel
            gifPanel?.isInInputMode == true -> gifPanel
            userDictPanel?.isInInputMode == true -> userDictPanel
            decryptPanel?.isInInputMode == true -> decryptPanel
            dictionaryPanel?.isInInputMode == true -> dictionaryPanel
            langLearnPanel?.isInInputMode == true -> langLearnPanel
            clipboardPanel?.isInInputMode == true -> clipboardPanel
            else -> null
        }
        if (activeTarget != null) {
            routeKeyToPanel(activeTarget, keyCode, label)
            return
        }

        // 面板開啟中但使用者按了鍵盤按鍵 → 需要輸入的面板保持開啟，其他面板關閉
        if (activePanel != null) {
            // 動態偵測：有正在輸入的面板就保持開啟
            val hasActiveInput = listOfNotNull(
                translatePanel, gifPanel, userDictPanel, decryptPanel,
                dictionaryPanel, langLearnPanel, clipboardPanel
            ).any { it.isInInputMode }
            val keepOpen = hasActiveInput
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
                                ic.setComposingText("", 0)
                                ic.commitText(trimmed, 1)
                                ic.endBatchEdit()
                            }
                        } else {
                            val commitResult = inputRouter.getActiveEngine().forceCommit()
                            if (commitResult is InputResult.CommitText) {
                                ic.beginBatchEdit()
                                ic.setComposingText("", 0)
                                ic.commitText(commitResult.text, 1)
                                ic.endBatchEdit()
                            } else {
                                ic.setComposingText("", 0)
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
        keyboardView.updateComposingText(inputRouter.composingText)
        keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
    }

    private fun handleEnter(ic: InputConnection) {
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
        // 更新候選列（日文漢字轉換、注音選字等需要此更新）
        keyboardView.updateComposingText(inputRouter.composingText)
        keyboardView.updateCandidates(inputRouter.candidates, inputRouter.candidateSourceMap)
    }

    private fun handleSwitchLang() {
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

    /** AUTO 模式下根據佈局偏好取得鍵盤行 */
    private fun rowsForAutoLayout(): List<List<KeyModel>> {
        return if (inputRouter.autoLayout == InputRouter.AutoLayout.QWERTY) {
            KeyboardState.defaultQwertyRows()
        } else {
            KeyboardState.bopomofoRows()
        }
    }

    /** 根據輸入模式取得對應鍵盤佈局 */
    private fun rowsForInputMode(mode: com.snowrealm.yukiboard.input.router.InputMode): List<List<KeyModel>> = when (mode) {
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

    /**
     * 面板輸入路由：將鍵盤按鍵轉發給面板的 PanelInputTarget。
     * CJK 語言走 InputRouter（注音/拼音/倉頡/日韓需要組字），拉丁語系直接送字元。
     * @return true 表示已處理，呼叫端應 return
     */
    private fun routeKeyToPanel(panel: PanelInputTarget, keyCode: Int, label: String): Boolean {
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

    private fun applyInputResult(ic: InputConnection, result: InputResult) {
        // AUTO 模式切引擎時，注音引擎的 selectedBuffer 可能被暫存，
        // 需要先 commit 出去，否則已選字會消失
        val pending = inputRouter.consumePendingCommit()
        if (!pending.isNullOrEmpty()) {
            ic.beginBatchEdit()
            ic.setComposingText("", 1)  // 清掉舊 composing（含已選字+未完成注音）
            ic.commitText(pending, 1)   // 只 commit 已選字部分
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
                ic.setComposingText("", 0)  // 0 = 丟棄 composing，不 commit，避免重複輸出
                ic.commitText(result.text, 1)
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
                        stickerPanel?.setRecommendedTags(tags)
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
    }
}
