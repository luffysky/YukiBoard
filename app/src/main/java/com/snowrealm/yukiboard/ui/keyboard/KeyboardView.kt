package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.TextView
import com.snowrealm.yukiboard.input.router.CandidateSource
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 自訂鍵盤 View — 純 Canvas 繪製。
 *
 * 直向：全寬鍵盤。
 * 橫向：左側主鍵盤（~72%）+ 右側工具面板（~28%），中間分隔線。
 */
class KeyboardView(context: Context) : View(context) {

    // -------------------------------------------------------------------------
    // 公開常數（keycodes）
    // -------------------------------------------------------------------------
    companion object {
        const val KEYCODE_DELETE      = -5
        const val KEYCODE_ENTER       = -4
        const val KEYCODE_SPACE       = -3
        const val KEYCODE_SHIFT       = -2
        const val KEYCODE_SWITCH_LANG = -1
        const val KEYCODE_CTRL        = -6
        const val KEYCODE_TAB         = -7
        const val KEYCODE_ARROW_LEFT  = -8
        const val KEYCODE_ARROW_RIGHT = -9
        const val KEYCODE_ARROW_UP    = -10
        const val KEYCODE_ARROW_DOWN  = -11
        const val KEYCODE_COMMA       = 44
        const val KEYCODE_PERIOD      = 46

        // Ctrl 組合鍵碼
        const val KEYCODE_CTRL_C = -100
        const val KEYCODE_CTRL_V = -101
        const val KEYCODE_CTRL_X = -102
        const val KEYCODE_CTRL_A = -103
        const val KEYCODE_CTRL_Z = -104
        const val KEYCODE_CTRL_Y = -105

        // 設計者模式快捷鍵
        const val KEYCODE_DESIGN_GROUP     = -200
        const val KEYCODE_DESIGN_UNGROUP   = -201
        const val KEYCODE_DESIGN_DUPLICATE = -202
        const val KEYCODE_DESIGN_LOCK      = -203
        const val KEYCODE_DESIGN_HIDE      = -204
        const val KEYCODE_DESIGN_ALIGN_L   = -205
        const val KEYCODE_DESIGN_ALIGN_C   = -206
        const val KEYCODE_DESIGN_ALIGN_R   = -207
        const val KEYCODE_DESIGN_ZOOM_IN   = -208
        const val KEYCODE_DESIGN_ZOOM_OUT  = -209
        const val KEYCODE_DESIGN_ZOOM_FIT  = -210
        const val KEYCODE_DESIGN_BOLD      = -211
        const val KEYCODE_DESIGN_ITALIC    = -212
        const val KEYCODE_DESIGN_UNDERLINE = -213
        const val KEYCODE_DESIGN_FONT_UP   = -214
        const val KEYCODE_DESIGN_FONT_DOWN = -215
        const val KEYCODE_DESIGN_LAYER_UP   = -216
        const val KEYCODE_DESIGN_LAYER_DOWN = -217
        const val KEYCODE_DESIGN_LAYER_TOP  = -218
        const val KEYCODE_DESIGN_LAYER_BOT  = -219
        const val KEYCODE_DESIGN_FLIP_H     = -220
        const val KEYCODE_DESIGN_FLIP_V     = -221

        // 開發者模式快捷鍵
        const val KEYCODE_DEV_FIND      = -300
        const val KEYCODE_DEV_COMMENT   = -301
        const val KEYCODE_DEV_HOME      = -302
        const val KEYCODE_DEV_END       = -303
        const val KEYCODE_DEV_FORMAT    = -304
        const val KEYCODE_DEV_LINE_UP   = -305
        const val KEYCODE_DEV_LINE_DOWN = -306
        const val KEYCODE_DEV_FOLD      = -307
        const val KEYCODE_DEV_UNFOLD    = -308
        const val KEYCODE_DEV_INDENT    = -309
        const val KEYCODE_DEV_UNINDENT  = -310
        const val KEYCODE_DEV_ARROW     = -311
        const val KEYCODE_DEV_FAT_ARROW = -312
        const val KEYCODE_DEV_DSLASH    = -313
        const val KEYCODE_DEV_COMMENT_BLOCK = -314

        // 返回一般模式
        const val KEYCODE_BACK_NORMAL = -400

        // 數字/符號鍵盤切換
        const val KEYCODE_SWITCH_NUMBERS = -500  // 切到數字符號鍵盤
        const val KEYCODE_SWITCH_SYMBOLS = -501  // 切到更多符號鍵盤
        const val KEYCODE_SWITCH_ABC     = -502  // 切回字母鍵盤
        const val KEYCODE_TOGGLE_KANA    = -503  // 日語 あ⇔ア 切換
        const val KEYCODE_SWITCH_NUMPAD  = -504  // 切到數字鍵盤（計算機）

        private const val LONG_PRESS_DELAY = 400L
        private const val CANDIDATE_BAR_MAX = 6
        private const val EXPAND_GRID_COLS = 5
    }

    // -------------------------------------------------------------------------
    // 回呼
    // -------------------------------------------------------------------------
    private var onKeyPressListener: ((keyCode: Int, label: String) -> Unit)? = null
    private var onModeChangedListener: ((KeyboardMode) -> Unit)? = null

    fun setOnKeyPressListener(listener: (keyCode: Int, label: String) -> Unit) {
        onKeyPressListener = listener
    }

    fun setOnModeChangedListener(listener: (KeyboardMode) -> Unit) {
        onModeChangedListener = listener
    }

    /** 直接提交文字（不走 InputRouter），用於長按彈出的注音符號/聲調直接��出 */
    private var onRawCommitListener: ((text: String) -> Unit)? = null
    fun setOnRawCommitListener(listener: (text: String) -> Unit) {
        onRawCommitListener = listener
    }

    private var onCandidateSelectListener: ((index: Int) -> Unit)? = null
    fun setOnCandidateSelectListener(listener: (index: Int) -> Unit) {
        onCandidateSelectListener = listener
    }

    private var onCandidateExpandListener: (() -> Unit)? = null
    fun setOnCandidateExpandListener(listener: () -> Unit) {
        onCandidateExpandListener = listener
    }

    /** 候選字朗讀回呼 — 選字時觸發，由 IME Service 判斷是否 TTS */
    private var onCandidateSpeakListener: ((text: String) -> Unit)? = null
    fun setOnCandidateSpeakListener(listener: (text: String) -> Unit) {
        onCandidateSpeakListener = listener
    }

    private var onLanguageSelectedListener: ((RadialLanguageMenu.LanguageOption) -> Unit)? = null
    fun setOnLanguageSelectedListener(listener: (RadialLanguageMenu.LanguageOption) -> Unit) {
        onLanguageSelectedListener = listener
    }

    private var onFloatingToggleListener: (() -> Unit)? = null
    fun setOnFloatingToggleListener(listener: () -> Unit) {
        onFloatingToggleListener = listener
    }

    /** 當前輸入法模式（供環形語言選單顯示用） */
    var currentInputMode: com.snowrealm.yukiboard.input.router.InputMode =
        com.snowrealm.yukiboard.input.router.InputMode.ENGLISH

    // -------------------------------------------------------------------------
    // 狀態
    // -------------------------------------------------------------------------
    private var keyRows: List<List<KeyModel>> = KeyboardState.defaultQwertyRows()
    private var sideRows: List<List<KeyModel>> = emptyList()
    internal var candidates: List<String> = emptyList()
        private set
    private var allCandidates: List<String> = emptyList()
    internal var candidateSourceMap: Map<String, CandidateSource> = emptyMap()
        private set
    private var isSwipeCandidateMode: Boolean = false
    private var isCandidateExpanded: Boolean = false
    // 候選列橫向捲動
    private var candidateScrollX = 0f
    private var candidateLastTouchX = 0f
    private var candidateDownX = 0f   // ACTION_DOWN 時的 x，用於選字
    private var candidateIsDragging = false
    private var candidateTouchActive = false
    // 候選字���度快取（每次 draw 時更新）
    private var candWidthsCache: List<Float> = emptyList()
    private var candFixedLeftCache = 0f
    private var candGapCache = 0f
    // 右側展開箭頭���域
    private val expandArrowWidth by lazy { 28f * density }
    private var hasExpandArrow = false
    /** 是否有自訂背景圖（設為 true 時鍵盤背景變半透明） */
    var hasCustomBackground: Boolean = false
        set(value) {
            field = value
            if (value) {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(LAYER_TYPE_HARDWARE, null)
            }
        }
    /** 背景遮罩透明度（0=全透明看圖, 255=全黑） */
    var bgOverlayAlpha: Int = 100
    /** 自訂字體顏色（0 = 跟隨主題） */
    var customKeyLabelColor: Int = 0
    /** 按鍵大小比例 70~130（百分比），調整按鍵內縮間距，鍵盤高度不變 */
    var keyScalePercent: Int = 100
        set(value) { field = value.coerceIn(70, 130); invalidate() }
    /** 按鍵背景不透明度 0~255 */
    var keyOpacity: Int = 255
        set(value) { field = value.coerceIn(0, 255); invalidate() }
    /** 個別按鍵尺寸倍率 (keyId -> (widthMul, heightMul)) */
    var perKeySizes: Map<String, Pair<Float, Float>> = emptyMap()
        set(value) { field = value; invalidate() }
    private var expandScrollOffset: Float = 0f    // 展開面板捲動偏移
    private var expandTouchStartY: Float = 0f     // 觸控起始 Y（用於滑動偵測）
    private var expandLastTouchY: Float = 0f      // 上一次觸控 Y
    private var expandIsScrolling: Boolean = false // 是否正在滑動中
    private var composingText: String = ""
    private var theme: KeyboardTheme = KeyboardTheme.default()
    private var isShifted: Boolean = false
    private var isCapsLock: Boolean = false
    private var isCtrlActive: Boolean = false
    var currentMode: KeyboardMode = KeyboardMode.NORMAL
        private set

    /** 語音辨識中的語言（null = 未啟動） */
    var voiceListeningLocale: String? = null
        set(value) { field = value; invalidate() }

    /** 子佈局：NORMAL 模式下可切換數字/符號鍵盤 */
    private enum class SubLayout { ABC, NUMBERS, SYMBOLS, NUMPAD }
    private var subLayout: SubLayout = SubLayout.ABC

    // -------------------------------------------------------------------------
    // 繪製用資源
    // -------------------------------------------------------------------------
    private val density = context.resources.displayMetrics.density
    private val keyDecorator = KeyDecorator(density)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val altHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 10f * density
    }
    private val candidatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
    }
    private val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 14f * density
        color = Color.CYAN
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
    }
    private val sideLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 7f * density
    }

    // 按鍵區域：touchRect（整個格子，用於觸控判定）+ visualRect（扣 padding，用於繪製）
    private data class KeyEntry(val touchRect: RectF, val visualRect: RectF, val key: KeyModel)
    private val keyEntries = mutableListOf<KeyEntry>()
    // 舊 keyRects 保留為 alias 供向下相容
    private val keyRects: List<Pair<RectF, KeyModel>>
        get() = keyEntries.map { it.touchRect to it.key }

    // 佈局常數
    private val candidateBarHeight = 40f * density
    private val keyPaddingH = 5f * density
    private val keyPaddingV = 6f * density
    private val keyCornerRadius = 8f * density
    private val bottomPadding = 8f * density

    // 直向行高 vs 橫向行高（預設值，實際可能被容器高度壓縮）
    private val rowHeightPortrait = 52f * density
    private val rowHeightLandscape = 48f * density

    /** 實際使用的行高，由 onMeasure 動態計算以適配容器 */
    private var actualRowHeight = rowHeightPortrait

    /** 橫向側邊面板占螢幕寬度比例 */
    private val sidePanelRatio = 0.28f

    // -------------------------------------------------------------------------
    // 震動 & 模式提示
    // -------------------------------------------------------------------------
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var modeToastText: String? = null
    private var modeToastEndTime = 0L
    private val modeToastDuration = 1200L

    var isSpaceLongPressed = false
        private set

    private var onSpaceLongPressListener: ((Boolean) -> Unit)? = null
    fun setOnSpaceLongPressListener(listener: (Boolean) -> Unit) {
        onSpaceLongPressListener = listener
    }

    /** 是否啟用按鍵震動，由外部設定 */
    var hapticEnabled = true

    fun vibrateShort() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private fun vibrateModeSwitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    fun showToast(msg: String) {
        modeToastText = msg
        modeToastEndTime = System.currentTimeMillis() + modeToastDuration
        invalidate()
        handler.postDelayed({
            if (modeToastText == msg) {
                modeToastText = null
                invalidate()
            }
        }, modeToastDuration)
    }

    private fun showModeToast(mode: KeyboardMode) {
        modeToastText = "${mode.icon} ${mode.displayName}"
        modeToastEndTime = System.currentTimeMillis() + modeToastDuration
        invalidate()
        handler.postDelayed({
            modeToastText = null
            invalidate()
        }, modeToastDuration)
    }

    // -------------------------------------------------------------------------
    // 方向偵測
    // -------------------------------------------------------------------------
    val isLandscape: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private val rowHeight: Float
        get() = actualRowHeight

    // -------------------------------------------------------------------------
    // 長按相關
    // -------------------------------------------------------------------------
    private val handler = Handler(Looper.getMainLooper())
    private var longPressKey: KeyModel? = null
    private var longPressRect: RectF? = null
    private var isLongPressTriggered = false
    private var pressedKey: KeyModel? = null
    private var popupWindow: PopupWindow? = null

    // 長按滑動選字 — alt-char inline overlay
    private var altPopupActive = false
    private var altPopupChars: List<String> = emptyList()
    private var altPopupRects: List<RectF> = emptyList()
    private var altPopupHoveredIndex = -1
    private var altPopupBgRect = RectF()
    private var altPopupOriginKey: KeyModel? = null

    // 長按滑動選取 — radial menu touch forwarding
    private var activeRadialView: View? = null
    private var radialPopupOffsetX = 0f
    private var radialPopupOffsetY = 0f

    // ─── 滑動路徑視覺 (SwipeTrail) ─────────────────────────────
    private val swipePoints = mutableListOf<PointF>()
    private var isSwipingActive = false
    private var swipeAlpha = 0f
    private var swipeFadeAnimator: android.animation.ValueAnimator? = null
    private var swipeHighlightKey: KeyModel? = null
    private var swipeHighlightAlpha = 0f
    private var highlightFadeAnimator: android.animation.ValueAnimator? = null
    private val swipePath = Path()
    private var swipeColor = 0xFFB39DDB.toInt()
    /** 外部設定：是否啟用滑動路徑視覺 */
    var swipeTrailEnabled = true
    /** 外部設定：是否啟用滑動輸入（滑動產生文字） */
    var swipeInputEnabled = true

    // ─── 滑動停頓偵測 ───
    private var swipeDwellKey: KeyModel? = null
    private var swipeDwellStartMs = 0L
    private val swipeDwellThresholdMs = 300L
    private val swipeDwellHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val swipeDwellRunnable = Runnable {
        swipeDwellKey?.let { onSwipeDwellConfirmed(it) }
    }
    /** 滑動停頓確認回呼（音節確認） */
    var onSwipeSyllableConfirmed: ((String) -> Unit)? = null

    // ─── 滑動輸入解碼 ──────────────────────────────────────
    /** 滑動經過的按鍵序列（去連續重複） */
    private val swipeKeySequence = mutableListOf<KeyModel>()
    /** 滑動起始位置 */
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    /** 是否已被判定為滑動（跨越 ≥2 個不同按鍵 且移動距離 > 閾值） */
    private var isSwipeGesture = false
    private val SWIPE_DISTANCE_THRESHOLD = 12f * density  // 12dp（降低門檻提升滑行靈敏度）

    private var onSwipeInputListener: ((keys: List<KeyModel>) -> Unit)? = null
    /** 設定滑動輸入回呼：收到經過的按鍵序列（去重），由外部解碼成文字 */
    fun setOnSwipeInputListener(listener: (keys: List<KeyModel>) -> Unit) {
        onSwipeInputListener = listener
    }

    // ─── 連擊粒子特效 (ComboTracker J-3) ──────────────────────
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                var life: Float, var color: Int, var size: Float)
    private val comboParticles = mutableListOf<Particle>()
    private var comboGlowAlpha = 0f

    /** 觸發連擊粒子特效 */
    fun triggerComboEffect(level: Int) {
        val count = when (level) { 1 -> 8; 2 -> 16; 3 -> 30; else -> 50 }
        val baseColor = when (level) {
            1 -> 0xFFFFD700.toInt()   // 金色
            2 -> 0xFFFF6600.toInt()   // 橙色
            3 -> 0xFFFF3366.toInt()   // 紅粉
            else -> 0xFFFF00FF.toInt() // 洋紅
        }
        val cx = width / 2f
        val cy = candidateBarHeight * 0.5f
        val random = java.util.Random()
        for (i in 0 until count) {
            val angle = random.nextFloat() * Math.PI.toFloat() * 2
            val speed = (2f + random.nextFloat() * 4f) * density
            comboParticles.add(Particle(
                x = cx + (random.nextFloat() - 0.5f) * width * 0.3f,
                y = cy,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed - 2f * density,
                life = 1f,
                color = baseColor,
                size = (2f + random.nextFloat() * 3f) * density
            ))
        }
        comboGlowAlpha = 0.6f
        invalidate()
    }

    private fun updateAndDrawParticles(canvas: Canvas) {
        if (comboParticles.isEmpty() && comboGlowAlpha <= 0f) return
        val iter = comboParticles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.3f * density // 重力
            p.life -= 0.025f
            if (p.life <= 0f) { iter.remove(); continue }
            val alpha = (p.life * 255).toInt().coerceIn(0, 255)
            keyPaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(p.x, p.y, p.size * p.life, keyPaint)
        }
        // 背景光暈
        if (comboGlowAlpha > 0f) {
            comboGlowAlpha -= 0.015f
            val a = (comboGlowAlpha * 40).toInt().coerceIn(0, 40)
            keyPaint.color = (0xFFD700 and 0x00FFFFFF) or (a shl 24)
            canvas.drawRect(0f, 0f, width.toFloat(), candidateBarHeight, keyPaint)
        }
        if (comboParticles.isNotEmpty() || comboGlowAlpha > 0f) {
            postInvalidateDelayed(32)  // 30fps 夠了，降低到 32ms
        }
    }

    // 外層發光 Paint（粗線 + 半透明，不用 BlurMaskFilter 以保效能）
    private val swipeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 22f * density
    }
    // 內層清晰路徑 Paint
    private val swipeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f * density
    }
    // 起點圓點 Paint
    private val swipeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // 按鍵高亮 Paint
    private val swipeKeyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 刪除鍵長按重複
    private var deleteRepeatCount = 0
    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (pressedKey?.code != KEYCODE_DELETE) return
            deleteRepeatCount++
            onKeyPressListener?.invoke(KEYCODE_DELETE, "⌫")
            if (hapticEnabled && deleteRepeatCount % 3 == 0) vibrateShort()
            // 越按越快：初始 80ms，最快 20ms
            val delay = (80L - (deleteRepeatCount * 5).coerceAtMost(60)).coerceAtLeast(20L)
            handler.postDelayed(this, delay)
        }
    }

    private val longPressRunnable = Runnable {
        val key = longPressKey ?: return@Runnable
        val rect = longPressRect ?: return@Runnable
        when {
            key.code == KEYCODE_DELETE -> {
                isLongPressTriggered = true
                deleteRepeatCount = 0
                handler.post(deleteRepeatRunnable)
            }
            key.code == KEYCODE_TAB -> {
                isLongPressTriggered = true
                vibrateShort()
                showRadialModeMenu(rect)
            }
            key.code == KEYCODE_SWITCH_LANG -> {
                isLongPressTriggered = true
                vibrateShort()
                showRadialLanguageMenu(rect)
            }
            key.code == KEYCODE_SPACE -> {
                isLongPressTriggered = true
                isSpaceLongPressed = true
                vibrateShort()
                onSpaceLongPressListener?.invoke(true)
            }
            key.altChars.isNotEmpty() -> {
                isLongPressTriggered = true
                vibrateShort()
                showAltPopup(key, rect)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 公開更新介面
    // -------------------------------------------------------------------------

    fun updateEditorInfo(info: EditorInfo?) {
        rebuildLayoutForOrientation()
        invalidate()
    }

    /** 強制重置所有觸控狀態，在面板關閉後呼叫確保鍵盤正常接收輸入 */
    fun resetTouchState() {
        candidateTouchActive = false
        candidateIsDragging = false
        pressedKey = null
        isSwipingActive = false
        swipePoints.clear()
        swipeKeySequence.clear()
        isSwipeGesture = false
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(deleteRepeatRunnable)
        invalidate()
    }

    fun updateComposingText(text: String) {
        if (composingText == text) return
        composingText = text
        invalidate()
    }

    fun updateCandidates(list: List<String>, sourceMap: Map<String, CandidateSource> = emptyMap()) {
        if (allCandidates == list && candidateSourceMap == sourceMap) return
        allCandidates = list
        candidates = list  // 不再截斷，全部由橫向捲動顯示
        candidateSourceMap = sourceMap
        isSwipeCandidateMode = false
        candidateScrollX = 0f
        highlightedCandidateIndex = -1
        // 有新候選字時保持展開狀態，空候選時收合
        if (list.isEmpty()) {
            isCandidateExpanded = false
            expandScrollOffset = 0f
        }
        invalidate()
    }

    /** 標記當前候選字為滑動輸入結果（淡紫色 + ≈ 前綴） */
    fun markSwipeCandidates(isSwipe: Boolean) {
        isSwipeCandidateMode = isSwipe
        invalidate()
    }

    fun collapseCandidatePanel() {
        isCandidateExpanded = false
        expandScrollOffset = 0f
        invalidate()
    }

    /** Tab 導航高亮的候選詞索引，-1 = 無高亮 */
    var highlightedCandidateIndex: Int = -1
        private set

    fun highlightCandidate(index: Int) {
        highlightedCandidateIndex = index
        invalidate()
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        keyDecorator.resetSeeds()
        // 套用主題字體大小
        if (newTheme.keyFontSizeSp > 0) {
            labelPaint.textSize = newTheme.keyFontSizeSp * density
        }
        // 更新滑動路徑顏色（跟主題強調色同步）
        val base = newTheme.candidateSelectedColorInt
        val r = (Color.red(base) * 1.2f).toInt().coerceAtMost(255)
        val g = (Color.green(base) * 1.2f).toInt().coerceAtMost(255)
        val b = (Color.blue(base) * 1.2f).toInt().coerceAtMost(255)
        swipeColor = if (r + g + b >= 200) Color.argb(255, r, g, b) else 0xFFB39DDB.toInt()
        invalidate()
    }

    fun updateKeyRows(rows: List<List<KeyModel>>) {
        keyRows = rows
        requestLayout()
        invalidate()
    }

    fun toggleShift() {
        if (isCapsLock) {
            isCapsLock = false
            isShifted = false
        } else if (isShifted) {
            isCapsLock = true
        } else {
            isShifted = true
        }
        invalidate()
    }

    fun autoUnshift() {
        if (isShifted && !isCapsLock) {
            isShifted = false
            invalidate()
        }
    }

    fun isShiftActive(): Boolean = isShifted || isCapsLock

    fun toggleCtrl() {
        isCtrlActive = !isCtrlActive
        invalidate()
    }

    private fun resolveCtrlCombo(label: String): Int? {
        return when (label.lowercase()) {
            "c" -> KEYCODE_CTRL_C
            "v" -> KEYCODE_CTRL_V
            "x" -> KEYCODE_CTRL_X
            "a" -> KEYCODE_CTRL_A
            "z" -> KEYCODE_CTRL_Z
            "y" -> KEYCODE_CTRL_Y
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // 佈局重建（方向切換時）
    // -------------------------------------------------------------------------

    private fun rebuildLayoutForOrientation() {
        if (isLandscape) {
            val (main, side) = getLandscapeLayout(currentMode)
            keyRows = main
            sideRows = side
        } else {
            keyRows = getPortraitLayout(currentMode)
            sideRows = emptyList()
        }
        requestLayout()
    }

    private fun getPortraitLayout(mode: KeyboardMode): List<List<KeyModel>> = when (mode) {
        KeyboardMode.NORMAL -> when (subLayout) {
            SubLayout.ABC -> rowsForCurrentInputMode()
            SubLayout.NUMBERS -> KeyboardState.numberSymbolRows()
            SubLayout.SYMBOLS -> KeyboardState.moreSymbolRows()
            SubLayout.NUMPAD -> KeyboardState.numpadRows()
        }
        KeyboardMode.DESIGNER -> KeyboardState.designerRows()
        KeyboardMode.DEVELOPER -> KeyboardState.developerRows()
        KeyboardMode.GAME -> KeyboardState.gameRows()
    }

    /** 根據 currentInputMode 回傳對應的字母佈局 */
    private fun rowsForCurrentInputMode(): List<List<KeyModel>> = when (currentInputMode) {
        com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> KeyboardState.bopomofoRows()
        com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> KeyboardState.pinyinRows()
        com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
        com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> KeyboardState.cangjieRows()
        com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> KeyboardState.japaneseRows()
        com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> KeyboardState.koreanRows()
        else -> KeyboardState.defaultQwertyRows()
    }

    private fun getLandscapeLayout(mode: KeyboardMode): Pair<List<List<KeyModel>>, List<List<KeyModel>>> =
        when (mode) {
            KeyboardMode.NORMAL -> when (subLayout) {
                SubLayout.ABC -> Pair(
                    rowsForCurrentInputMode(),
                    emptyList()  // 一般模式橫向不加側邊面板，跟直向一樣
                )
                SubLayout.NUMBERS -> Pair(
                    KeyboardState.numberSymbolRows(),
                    emptyList()
                )
                SubLayout.SYMBOLS -> Pair(
                    KeyboardState.moreSymbolRows(),
                    emptyList()
                )
                SubLayout.NUMPAD -> Pair(
                    KeyboardState.numpadRows(),
                    emptyList()
                )
            }
            KeyboardMode.DESIGNER -> Pair(
                KeyboardState.landscapeDesignerRows(),
                KeyboardState.landscapeDesignerSidePanel()
            )
            KeyboardMode.DEVELOPER -> Pair(
                KeyboardState.landscapeDeveloperRows(),
                KeyboardState.landscapeDeveloperSidePanel()
            )
            KeyboardMode.GAME -> Pair(
                KeyboardState.gameRows(),
                emptyList()
            )
        }

    fun switchMode(mode: KeyboardMode) {
        if (mode == currentMode) return
        currentMode = mode
        subLayout = SubLayout.ABC
        rebuildLayoutForOrientation()
        vibrateModeSwitch()
        showModeToast(mode)
        onModeChangedListener?.invoke(mode)
    }

    private fun switchSubLayout(target: SubLayout) {
        if (subLayout == target) return
        subLayout = target
        rebuildLayoutForOrientation()
        invalidate()
    }

    // -------------------------------------------------------------------------
    // 測量
    // -------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }

        val mainRowCount = keyRows.size
        val sideRowCount = sideRows.size
        val effectiveRowCount = if (isLandscape && sideRowCount > mainRowCount) {
            sideRowCount
        } else {
            mainRowCount
        }

        val preferredRowH = if (isLandscape) rowHeightLandscape else rowHeightPortrait
        val preferredHeight = candidateBarHeight + effectiveRowCount * preferredRowH + bottomPadding

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val totalHeight: Int
        if ((heightMode == MeasureSpec.EXACTLY || heightMode == MeasureSpec.AT_MOST)
            && heightSize > 0 && heightSize < preferredHeight) {
            // 容器空間不夠，按比例壓縮行高（保留 bottomPadding）
            totalHeight = heightSize
            actualRowHeight = if (effectiveRowCount > 0) {
                (heightSize - candidateBarHeight - bottomPadding) / effectiveRowCount
            } else {
                preferredRowH
            }
        } else {
            totalHeight = preferredHeight.toInt()
            actualRowHeight = preferredRowH
        }

        setMeasuredDimension(w, totalHeight)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        rebuildLayoutForOrientation()
    }

    // -------------------------------------------------------------------------
    // 繪製
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keyEntries.clear()
        drawBackground(canvas)
        drawCandidateBar(canvas)

        if (isLandscape && sideRows.isNotEmpty()) {
            val dividerX = width * (1f - sidePanelRatio)
            drawKeysInRegion(canvas, keyRows, 0f, dividerX)
            drawDivider(canvas, dividerX)
            drawKeysInRegion(canvas, sideRows, dividerX, width.toFloat())
        } else {
            drawKeysInRegion(canvas, keyRows, 0f, width.toFloat())
        }

        if (isCandidateExpanded && allCandidates.isNotEmpty()) {
            drawExpandedCandidatePanel(canvas)
        }

        // 滑動路徑
        if (swipePoints.size >= 2 || (swipePoints.size == 1 && isSwipingActive)) {
            drawSwipeTrail(canvas)
        }

        // 連擊粒子特效
        updateAndDrawParticles(canvas)

        // 長按 alt-char overlay（最頂層）
        drawAltPopupOverlay(canvas)
    }

    private var bgGradient: LinearGradient? = null
    private var bgGradientWidth = 0
    private var bgGradientHeight = 0
    private val bgPaint = Paint()

    private fun drawBackground(canvas: Canvas) {
        if (hasCustomBackground) {
            // 先清透明，再畫使用者設定的遮罩深度
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val alpha = bgOverlayAlpha.coerceIn(0, 255)
            canvas.drawColor(android.graphics.Color.argb(alpha, 0, 0, 0))
            return
        }
        val startColor = theme.backgroundGradientStartInt
        val endColor = theme.backgroundGradientEndInt
        if (startColor != null && endColor != null) {
            if (bgGradient == null || bgGradientWidth != width || bgGradientHeight != height) {
                bgGradientWidth = width
                bgGradientHeight = height
                bgGradient = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    startColor, endColor,
                    Shader.TileMode.CLAMP
                )
            }
            bgPaint.shader = bgGradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            bgPaint.shader = null
        } else {
            canvas.drawColor(theme.backgroundColorInt)
        }
    }

    /** 將顏色的 alpha 乘以指定比例 */
    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val a = ((color ushr 24) * alphaFactor).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun drawCandidateBar(canvas: Canvas) {
        val barRect = RectF(0f, 0f, width.toFloat(), candidateBarHeight)
        keyPaint.color = if (hasCustomBackground) 0x22000000 else theme.candidateBarColorInt
        canvas.drawRect(barRect, keyPaint)

        hasExpandArrow = false

        // 模式標示
        if (currentMode != KeyboardMode.NORMAL) {
            val modeTag = "${currentMode.icon} ${currentMode.displayName}"
            altHintPaint.color = theme.candidateSelectedColorInt
            altHintPaint.textSize = 10f * density
            altHintPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(modeTag, width.toFloat() - 8f * density, candidateBarHeight * 0.65f, altHintPaint)
            altHintPaint.textSize = 10f * density
        }

        // 模式切換提示
        val toast = modeToastText
        if (toast != null && System.currentTimeMillis() < modeToastEndTime) {
            candidatePaint.color = theme.candidateSelectedColorInt
            candidatePaint.textSize = 16f * density
            canvas.drawText(toast, width / 2f, candidateBarHeight * 0.65f, candidatePaint)
            candidatePaint.textSize = 15f * density
            return
        }

        // 語音辨識中指示
        val vLocale = voiceListeningLocale
        if (vLocale != null) {
            val langName = when {
                vLocale == "auto" -> "多語言"
                vLocale.startsWith("zh-TW") -> "中文(繁體)"
                vLocale.startsWith("zh-CN") -> "中文(簡體)"
                vLocale.startsWith("zh-HK") -> "粵語"
                vLocale.startsWith("ja") -> "日本語"
                vLocale.startsWith("ko") -> "한국어"
                vLocale.startsWith("es") -> "Español"
                vLocale.startsWith("fr") -> "Français"
                vLocale.startsWith("de") -> "Deutsch"
                vLocale.startsWith("pt") -> "Português"
                else -> "English"
            }
            val dots = ".".repeat(((System.currentTimeMillis() / 500) % 4).toInt())
            candidatePaint.color = theme.candidateSelectedColorInt
            candidatePaint.textSize = 15f * density
            canvas.drawText("🎤 $langName 辨識中$dots", width / 2f, candidateBarHeight * 0.65f, candidatePaint)
            postInvalidateDelayed(500)
            return
        }

        // composing text（左側固定區域）
        var fixedLeft = 4f * density
        if (composingText.isNotEmpty()) {
            composingPaint.color = theme.composingTextColorInt
            val displayText = "[$composingText]"
            composingPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(displayText, fixedLeft, candidateBarHeight * 0.65f, composingPaint)
            fixedLeft += composingPaint.measureText(displayText) + 8f * density
        }

        // 候選字區域（橫向捲動）
        if (candidates.isEmpty()) return

        val candPadH = 14f * density
        val candGap = 2f * density
        candidatePaint.textAlign = Paint.Align.CENTER
        candidatePaint.textSize = 15f * density

        // 計算每個候選字寬度並快取（供 touch 用，含前綴）
        val candWidths = candidates.mapIndexed { i, text ->
            val display = when {
                isSwipeCandidateMode && i == 0 -> "≈$text"
                else -> text
            }
            candidatePaint.measureText(display) + candPadH * 2
        }
        candWidthsCache = candWidths
        candFixedLeftCache = fixedLeft
        candGapCache = candGap

        val totalCandWidth = candWidths.sum() + candGap * (candidates.size - 1).coerceAtLeast(0)

        // 右側預留展開箭頭空間（候選 > 5 個時顯示）
        val showArrow = allCandidates.size > 5
        hasExpandArrow = showArrow
        val arrowSpace = if (showArrow) expandArrowWidth else 0f
        val scrollAreaWidth = width.toFloat() - fixedLeft - arrowSpace
        val maxScroll = (totalCandWidth - scrollAreaWidth).coerceAtLeast(0f)
        candidateScrollX = candidateScrollX.coerceIn(0f, maxScroll)

        // clip 候選字區域（不含箭頭區域）
        canvas.save()
        canvas.clipRect(fixedLeft, 0f, width.toFloat() - arrowSpace, candidateBarHeight)

        var drawX = fixedLeft - candidateScrollX
        candidates.forEachIndexed { i, text ->
            val cw = candWidths[i]
            val left = drawX
            val right = drawX + cw
            val cx = left + cw / 2f

            if (right > fixedLeft && left < width.toFloat() - arrowSpace) {
                // 候選字來源色彩（滑動模式統一淡紫色）
                val sourceColor = if (isSwipeCandidateMode) 0xFFB39DDB.toInt()
                else when (candidateSourceMap[text]) {
                    CandidateSource.CORRECTION -> 0xFFFF9933.toInt()  // 橙色：糾錯
                    CandidateSource.ADAPTIVE   -> 0xFF66CC88.toInt()  // 綠色：自適應
                    CandidateSource.PREDICTION -> 0xFF66AAFF.toInt()  // 藍色：預測
                    CandidateSource.USER_DICT  -> 0xFFFFD700.toInt()  // 金色：使用者詞庫
                    CandidateSource.SENTENCE   -> 0xFFBB88FF.toInt()  // 紫色：整句
                    CandidateSource.AI         -> 0xFFE8B84D.toInt()  // 金色：AI ✨
                    else -> 0 // DICTIONARY 或無來源 → 用主題預設色
                }

                if (i == 0 && candidates.size > 1) {
                    keyPaint.color = if (sourceColor != 0) sourceColor else theme.candidateSelectedColorInt
                    keyPaint.alpha = 40
                    val pill = RectF(left + 2 * density, 4 * density, right - 2 * density, candidateBarHeight - 4 * density)
                    canvas.drawRoundRect(pill, 6 * density, 6 * density, keyPaint)
                    keyPaint.alpha = 255
                    candidatePaint.color = if (sourceColor != 0) sourceColor else theme.candidateSelectedColorInt
                } else {
                    candidatePaint.color = if (sourceColor != 0) sourceColor else theme.candidateTextColorInt
                }
                // AI 候選字用加深底色 + 右上角小圓點標記，不加 ✨ 前綴
                if (candidateSourceMap[text] == CandidateSource.AI) {
                    keyPaint.color = 0xFFE8B84D.toInt()
                    keyPaint.alpha = 70
                    val pill = RectF(left + 2 * density, 4 * density, right - 2 * density, candidateBarHeight - 4 * density)
                    canvas.drawRoundRect(pill, 6 * density, 6 * density, keyPaint)
                    keyPaint.alpha = 255
                    // 右上角小圓點
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFE8B84D.toInt()
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(right - 6f * density, 6f * density, 3f * density, dotPaint)
                }
                val displayText = when {
                    isSwipeCandidateMode && i == 0 -> "≈$text"
                    else -> text
                }
                canvas.drawText(displayText, cx, candidateBarHeight * 0.65f, candidatePaint)

                // 底部來源指示條（非字典來源才顯示）
                if (sourceColor != 0 && candidateSourceMap[text] != CandidateSource.AI) {
                    keyPaint.color = sourceColor
                    keyPaint.alpha = 180
                    val indicatorH = 2f * density
                    val indicatorW = (cw * 0.5f).coerceAtMost(20f * density)
                    canvas.drawRoundRect(
                        cx - indicatorW / 2f, candidateBarHeight - indicatorH - 1f * density,
                        cx + indicatorW / 2f, candidateBarHeight - 1f * density,
                        indicatorH, indicatorH, keyPaint
                    )
                    keyPaint.alpha = 255
                }

                if (i > 0) {
                    dividerPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x20000000
                    canvas.drawLine(left, 8 * density, left, candidateBarHeight - 8 * density, dividerPaint)
                }
            }

            drawX = right + candGap
        }

        canvas.restore()

        // 右側漸層遮罩
        if (maxScroll > 0f) {
            val fadeW = 20f * density
            val fadeRight = width.toFloat() - arrowSpace
            val fadeLeft = fadeRight - fadeW
            val fadePaint = Paint()
            fadePaint.shader = android.graphics.LinearGradient(
                fadeLeft, 0f, fadeRight, 0f,
                0x00000000, (theme.candidateBarColorInt and 0x00FFFFFF) or 0xCC000000.toInt(),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(fadeLeft, 0f, fadeRight, candidateBarHeight, fadePaint)
        }

        // 展開箭頭 ▼
        if (showArrow) {
            val arrowLeft = width.toFloat() - arrowSpace
            // 分隔線
            dividerPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x30000000
            canvas.drawLine(arrowLeft, 6 * density, arrowLeft, candidateBarHeight - 6 * density, dividerPaint)
            // 箭頭
            candidatePaint.textAlign = Paint.Align.CENTER
            candidatePaint.textSize = 14f * density
            candidatePaint.color = theme.candidateTextColorInt
            val arrowText = if (isCandidateExpanded) "▲" else "▼"
            canvas.drawText(arrowText, arrowLeft + arrowSpace / 2f, candidateBarHeight * 0.65f, candidatePaint)
            candidatePaint.textSize = 15f * density
        }
    }

    /**
     * 根據螢幕觸控 x 座標算出對應的候選字 index。
     * 使用與 drawCandidateBar 相同的 width 計算邏輯，避免座標系不匹配。
     */
    private fun hitTestCandidateIndex(touchX: Float): Int {
        val widths = candWidthsCache
        if (widths.isEmpty()) return -1
        val gap = candGapCache
        // 觸控的 x 在邏輯座標系的位置 = 螢幕 x - fixedLeft + scrollX
        val logicalX = touchX - candFixedLeftCache + candidateScrollX
        if (logicalX < 0) return -1
        var accum = 0f
        for (i in widths.indices) {
            val right = accum + widths[i]
            if (logicalX < right) return i
            accum = right + gap
        }
        return -1
    }

    private fun drawExpandedCandidatePanel(canvas: Canvas) {
        val panelTop = candidateBarHeight
        val panelBottom = height.toFloat()
        val panelHeight = panelBottom - panelTop

        // 半透明背景覆蓋整個鍵盤區域
        keyPaint.color = (theme.backgroundColorInt and 0x00FFFFFF) or 0xEE000000.toInt()
        canvas.drawRect(0f, panelTop, width.toFloat(), panelBottom, keyPaint)

        val cellPadding = 4f * density
        val cellWidth = width.toFloat() / EXPAND_GRID_COLS
        val cellHeight = 42f * density
        val contentPadding = 8f * density

        // 計算總內容高度
        val totalRows = (allCandidates.size + EXPAND_GRID_COLS - 1) / EXPAND_GRID_COLS
        val totalContentHeight = totalRows * cellHeight + contentPadding * 2
        val maxScroll = maxOf(0f, totalContentHeight - panelHeight)
        expandScrollOffset = expandScrollOffset.coerceIn(0f, maxScroll)

        // 裁剪繪製區域
        canvas.save()
        canvas.clipRect(0f, panelTop, width.toFloat(), panelBottom)

        val startY = panelTop + contentPadding - expandScrollOffset

        allCandidates.forEachIndexed { i, text ->
            val col = i % EXPAND_GRID_COLS
            val row = i / EXPAND_GRID_COLS
            val left = col * cellWidth + cellPadding
            val top = startY + row * cellHeight + cellPadding
            val right = (col + 1) * cellWidth - cellPadding
            val bottom = top + cellHeight - 2 * cellPadding

            // 只繪製可見的候選字
            if (bottom < panelTop || top > panelBottom) return@forEachIndexed

            // 候選字背景
            keyPaint.color = theme.keyColorInt
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 6 * density, 6 * density, keyPaint)

            // 候選字文字（來源色彩）
            val srcColor = when (candidateSourceMap[text]) {
                CandidateSource.CORRECTION -> 0xFFFF9933.toInt()
                CandidateSource.ADAPTIVE   -> 0xFF66CC88.toInt()
                CandidateSource.PREDICTION -> 0xFF66AAFF.toInt()
                CandidateSource.USER_DICT  -> 0xFFFFD700.toInt()
                CandidateSource.SENTENCE   -> 0xFFBB88FF.toInt()
                else -> theme.candidateTextColorInt
            }
            candidatePaint.color = srcColor
            canvas.drawText(text, left + (right - left) / 2f, top + (bottom - top) * 0.65f, candidatePaint)
        }

        // 捲動指示條（若可捲動）
        if (maxScroll > 0f) {
            val scrollBarWidth = 4f * density
            val scrollBarHeight = panelHeight * (panelHeight / totalContentHeight)
            val scrollBarY = panelTop + (expandScrollOffset / maxScroll) * (panelHeight - scrollBarHeight)
            keyPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x66000000
            canvas.drawRoundRect(
                RectF(width - scrollBarWidth - 2 * density, scrollBarY,
                      width - 2 * density, scrollBarY + scrollBarHeight),
                scrollBarWidth / 2, scrollBarWidth / 2, keyPaint
            )
        }

        canvas.restore()
    }

    private fun drawDivider(canvas: Canvas, x: Float) {
        val effectiveLabelColorGlobal = if (customKeyLabelColor != 0) customKeyLabelColor else theme.keyLabelColorInt
        dividerPaint.color = (effectiveLabelColorGlobal and 0x00FFFFFF) or 0x33000000
        canvas.drawLine(x, candidateBarHeight, x, height.toFloat(), dividerPaint)
    }

    /**
     * 在指定水平區域內繪製按鍵行。
     * left/right 定義繪製區域的水平邊界。
     */
    private fun drawKeysInRegion(
        canvas: Canvas,
        rows: List<List<KeyModel>>,
        left: Float,
        right: Float
    ) {
        val regionWidth = right - left
        val shouldUpper = isShifted || isCapsLock
        val rh = rowHeight

        // 側邊面板用較小字體
        val isSide = (left > 0f)
        val useLabelPaint = if (isSide) sideLabelPaint else labelPaint
        if (isSide) {
            sideLabelPaint.textSize = 12f * density
        } else {
            labelPaint.textSize = (if (theme.keyFontSizeSp > 0) theme.keyFontSizeSp else 18f) * density
        }

        val totalUnits = rows.maxOfOrNull { row ->
            row.sumOf { it.widthWeight.toDouble() }.toFloat()
        } ?: 10f

        rows.forEachIndexed { rowIndex, row ->
            val top = candidateBarHeight + rowIndex * rh
            var x = left
            val unitWidth = regionWidth / totalUnits

            // keyScalePercent: 100=正常，70=按鍵縮小（padding 加大），130=按鍵放大（padding 縮小）
            val scaleInv = (200 - keyScalePercent) / 100f  // 130→0.7, 100→1.0, 70→1.3
            val padH = keyPaddingH * scaleInv
            val padV = keyPaddingV * scaleInv

            row.forEach { key ->
                val keyWidth = unitWidth * key.widthWeight
                val touchRect = RectF(x, top, x + keyWidth, top + rh)

                // 個別按鍵尺寸倍率
                val keyId = if (key.code != 0) key.code.toString() else key.label
                val (pkW, pkH) = perKeySizes[keyId] ?: (1f to 1f)
                val adjPadH = padH + (keyWidth * (1f - pkW) / 2f).coerceAtLeast(0f)
                val adjPadV = padV + (rh * (1f - pkH) / 2f).coerceAtLeast(0f)

                val rect = RectF(
                    x + adjPadH,
                    top + adjPadV,
                    x + keyWidth - adjPadH,
                    top + rh - adjPadV
                )
                keyEntries.add(KeyEntry(touchRect, rect, key))

                // 按鍵背景
                val isPressed = pressedKey == key
                val isShiftKey = key.code == KEYCODE_SHIFT
                val isCtrlKey = key.code == KEYCODE_CTRL
                val baseKeyColor = when {
                    isPressed -> theme.keyPressedColorInt
                    isShiftKey && (isShifted || isCapsLock) -> theme.keyPressedColorInt
                    isCtrlKey && isCtrlActive -> theme.candidateSelectedColorInt
                    key.isSpecial -> theme.specialKeyColorInt
                    else -> theme.keyColorInt
                }
                // 套用按鍵不透明度
                val rawKeyColor = if (keyOpacity < 255) {
                    val a = ((baseKeyColor ushr 24) * keyOpacity / 255).coerceIn(0, 255)
                    (a shl 24) or (baseKeyColor and 0x00FFFFFF)
                } else baseKeyColor
                val cr = theme.keyCornerRadiusDp * density

                if (hasCustomBackground) {
                    // 自訂背景模式：不畫實底，只畫淡淡的玻璃折射框線
                    if (isPressed) {
                        // 按下時顯示微弱高亮
                        keyPaint.style = Paint.Style.FILL
                        keyPaint.color = 0x33FFFFFF
                        canvas.drawRoundRect(rect, cr, cr, keyPaint)
                    }
                    // 液態玻璃折射框線（淡白色 + 淡亮頂邊）
                    keyPaint.style = Paint.Style.STROKE
                    keyPaint.strokeWidth = 0.5f * density
                    keyPaint.color = 0x30FFFFFF
                    canvas.drawRoundRect(rect, cr, cr, keyPaint)
                    // 頂部亮邊（模擬折射高光）
                    val topLine = RectF(rect.left + 2 * density, rect.top, rect.right - 2 * density, rect.top + 0.5f * density)
                    keyPaint.color = 0x40FFFFFF
                    keyPaint.style = Paint.Style.FILL
                    canvas.drawRoundRect(topLine, 1f * density, 1f * density, keyPaint)
                    keyPaint.style = Paint.Style.FILL
                } else {
                    keyPaint.color = rawKeyColor
                    keyPaint.style = Paint.Style.FILL

                    // 按鍵陰影（淺色主題才明顯）
                    if (theme.keyShadowRadiusDp > 0f && theme.keyShadowColorInt != 0) {
                        keyPaint.setShadowLayer(
                            theme.keyShadowRadiusDp * density,
                            0f, 1f * density,
                            theme.keyShadowColorInt
                        )
                    }
                    canvas.drawRoundRect(rect, cr, cr, keyPaint)
                    keyPaint.clearShadowLayer()

                    // 按鍵邊框
                    if (theme.keyBorderWidthDp > 0 && theme.keyBorderColorInt != 0) {
                        keyPaint.style = Paint.Style.STROKE
                        keyPaint.strokeWidth = theme.keyBorderWidthDp * density
                        keyPaint.color = theme.keyBorderColorInt
                        canvas.drawRoundRect(rect, cr, cr, keyPaint)
                        keyPaint.style = Paint.Style.FILL
                    }
                }

                // 液態玻璃 + 主題裝飾
                keyDecorator.decorate(canvas, rect, theme, rowIndex * 100 + row.indexOf(key), key.isSpecial, isPressed, cr)

                // Label
                val ctrlHint = if (isCtrlActive && !key.isSpecial) resolveCtrlCombo(key.label) else null
                val displayLabel = when {
                    isCtrlKey -> if (isCtrlActive) "Ctrl ✓" else "Ctrl"
                    isShiftKey && isCapsLock -> "⇪"
                    isShiftKey && isShifted -> "⇧"
                    ctrlHint != null -> "^${key.label.uppercase()}"
                    // ABC 按鈕依 inputMode 動態顯示
                    key.code == KEYCODE_SWITCH_ABC -> when (currentInputMode) {
                        com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> "注音"
                        com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> "あ"
                        com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> "가"
                        com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> "拼音"
                        com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
                        com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> "倉頡"
                        else -> "ABC"
                    }
                    shouldUpper && !key.isSpecial && key.label.length == 1 -> key.label.uppercase()
                    else -> key.label
                }

                val effectiveLabelColor = if (customKeyLabelColor != 0) customKeyLabelColor else theme.keyLabelColorInt
                useLabelPaint.color = if (ctrlHint != null) theme.candidateSelectedColorInt else effectiveLabelColor
                val textY = rect.top + (rect.height() + useLabelPaint.textSize) / 2f - useLabelPaint.descent()
                canvas.drawText(displayLabel, rect.centerX(), textY, useLabelPaint)

                // Alt hint：右上角顯示第一個長按字元
                if (key.altChars.isNotEmpty()) {
                    altHintPaint.color = (effectiveLabelColor and 0x00FFFFFF) or 0x80000000.toInt()
                    val hint = key.altChars.first()
                    canvas.drawText(hint, rect.right - 3f * density, rect.top + 11f * density, altHintPaint)
                }

                x += keyWidth
            }
        }
    }

    // -------------------------------------------------------------------------
    // 長按彈窗
    // -------------------------------------------------------------------------

    private fun showAltPopup(key: KeyModel, anchorRect: RectF) {
        dismissPopup()

        val allChars = listOf(key.label) + key.altChars
        altPopupChars = allChars
        altPopupOriginKey = key
        altPopupActive = true

        // 計算每個字元的格子位置（水平排列，居中於按鍵上方）
        val cellW = 42f * density
        val cellH = 50f * density
        val padding = 6f * density
        val gap = 2f * density
        val totalW = allChars.size * cellW + (allChars.size - 1) * gap + padding * 2

        val startX = (anchorRect.centerX() - totalW / 2f).coerceIn(0f, width - totalW)
        val startY = (anchorRect.top - cellH - padding * 2 - 4f * density).coerceAtLeast(0f)

        altPopupBgRect = RectF(startX, startY, startX + totalW, startY + cellH + padding * 2)

        altPopupRects = allChars.mapIndexed { i, _ ->
            RectF(
                startX + padding + i * (cellW + gap),
                startY + padding,
                startX + padding + i * (cellW + gap) + cellW,
                startY + padding + cellH
            )
        }

        // 預設 hover 在原始字元（index 0）
        altPopupHoveredIndex = 0
        invalidate()
    }

    private fun dismissAltPopup() {
        altPopupActive = false
        altPopupHoveredIndex = -1
        altPopupChars = emptyList()
        altPopupRects = emptyList()
        altPopupOriginKey = null
        invalidate()
    }

    private fun commitAltPopupSelection() {
        val idx = altPopupHoveredIndex
        if (idx in altPopupChars.indices) {
            val ch = altPopupChars[idx]
            val originKey = altPopupOriginKey
            // 如果原始按鍵是注音/聲調，altChar 全部直接 raw commit（英文/數字/符號）
            val isOriginBopomofo = originKey != null && originKey.label.length == 1 &&
                    isBopomofoOrTone(originKey.label[0])
            if (isOriginBopomofo) {
                onRawCommitListener?.invoke(ch)
            } else {
                val output = if ((isShifted || isCapsLock) && ch.length == 1) ch.uppercase() else ch
                onKeyPressListener?.invoke(ch[0].code, output)
                autoUnshift()
            }
        }
        dismissAltPopup()
    }

    /** 判斷是否為注音符號或聲調符號 */
    private fun isBopomofoOrTone(ch: Char): Boolean =
        ch in '\u3105'..'\u312F' ||  // ㄅ-ㄩ
        ch == '\u02CA' ||            // ˊ
        ch == '\u02C7' ||            // ˇ
        ch == '\u02CB' ||            // ˋ
        ch == '\u02D9'               // ˙

    /** 根據手指位置更新 alt popup 的 hover 狀態 */
    private fun updateAltPopupHover(x: Float, y: Float) {
        // 檢查是否在 popup 區域或其下方（原始按鍵區域）的延伸範圍內
        val extendedRect = RectF(
            altPopupBgRect.left - 20f * density,
            altPopupBgRect.top - 10f * density,
            altPopupBgRect.right + 20f * density,
            altPopupBgRect.bottom + 80f * density  // 向下延伸到按鍵區域
        )
        if (!extendedRect.contains(x, y)) {
            altPopupHoveredIndex = -1
            invalidate()
            return
        }

        // 找最近的格子（主要依 X 軸，只要 Y 在擴展範圍就行）
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        altPopupRects.forEachIndexed { i, rect ->
            val cx = rect.centerX()
            val dist = Math.abs(x - cx)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        // 如果 X 軸距離太遠（超出最外側格子太多），不選
        if (bestIdx >= 0 && bestDist > 40f * density) {
            bestIdx = -1
        }
        if (altPopupHoveredIndex != bestIdx) {
            altPopupHoveredIndex = bestIdx
            if (bestIdx >= 0 && hapticEnabled) vibrateShort()
            invalidate()
        }
    }

    private val altBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val altCellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val altTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private fun drawAltPopupOverlay(canvas: Canvas) {
        if (!altPopupActive) return

        // 陰影背景
        altBgPaint.color = theme.candidateBarColorInt
        altBgPaint.setShadowLayer(12f * density, 0f, 3f * density, 0x66000000)
        setLayerType(LAYER_TYPE_SOFTWARE, altBgPaint)
        canvas.drawRoundRect(altPopupBgRect, 10f * density, 10f * density, altBgPaint)
        altBgPaint.clearShadowLayer()

        altPopupChars.forEachIndexed { i, ch ->
            val rect = altPopupRects[i]
            val isHovered = i == altPopupHoveredIndex

            // 格子背景
            altCellPaint.color = if (isHovered) theme.candidateSelectedColorInt else theme.keyColorInt
            canvas.drawRoundRect(rect, 8f * density, 8f * density, altCellPaint)

            // 文字
            altTextPaint.textSize = 20f * density
            altTextPaint.color = if (isHovered) theme.keyLabelColorInt else theme.candidateTextColorInt
            altTextPaint.isFakeBoldText = isHovered
            val textY = rect.centerY() + 7f * density
            canvas.drawText(ch, rect.centerX(), textY, altTextPaint)
        }

        // 三角指示器（指向原始按鍵）
        if (altPopupHoveredIndex >= 0) {
            val hRect = altPopupRects[altPopupHoveredIndex]
            val triSize = 6f * density
            val path = android.graphics.Path().apply {
                moveTo(hRect.centerX() - triSize, altPopupBgRect.bottom)
                lineTo(hRect.centerX() + triSize, altPopupBgRect.bottom)
                lineTo(hRect.centerX(), altPopupBgRect.bottom + triSize)
                close()
            }
            altCellPaint.color = theme.candidateSelectedColorInt
            canvas.drawPath(path, altCellPaint)
        }
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    // -------------------------------------------------------------------------
    // 滑動路徑視覺 (SwipeTrail)
    // -------------------------------------------------------------------------

    fun onSwipeStart(x: Float, y: Float) {
        if (!swipeTrailEnabled) return
        swipeFadeAnimator?.cancel()
        swipePoints.clear()
        swipePoints.add(PointF(x, y))
        isSwipingActive = true
        swipeAlpha = 1f
        val startKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
        if (startKey != null) flashKey(startKey)
        invalidate()
    }

    fun onSwipeMove(x: Float, y: Float) {
        if (!isSwipingActive) return
        val last = swipePoints.lastOrNull()
        if (last == null || Math.hypot((x - last.x).toDouble(), (y - last.y).toDouble()) > 4.0 * density) {
            swipePoints.add(PointF(x, y))
            val currentKey = keyEntries.firstOrNull { it.touchRect.contains(x, y) }?.key
            if (currentKey != null && currentKey != swipeHighlightKey) {
                flashKey(currentKey)
                // 停頓偵測：換了按鍵就重新計時
                swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
                if (currentKey != swipeDwellKey) {
                    swipeDwellKey = currentKey
                    swipeDwellStartMs = System.currentTimeMillis()
                    swipeDwellHandler.postDelayed(swipeDwellRunnable, swipeDwellThresholdMs)
                }
            }
            invalidate()
        }
    }

    fun onSwipeEnd() {
        if (!isSwipingActive && swipePoints.isEmpty()) return
        isSwipingActive = false
        swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
        swipeDwellKey = null
        swipeFadeAnimator?.cancel()
        swipeFadeAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 400
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { anim ->
                swipeAlpha = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    swipePoints.clear()
                    invalidate()
                }
            })
            start()
        }
    }

    private fun flashKey(key: KeyModel) {
        swipeHighlightKey = key
        swipeHighlightAlpha = 0.6f
        highlightFadeAnimator?.cancel()
        highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(0.6f, 0f).apply {
            duration = 200
            addUpdateListener { anim ->
                swipeHighlightAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 停頓確認：滑動時在某按鍵停留超過閾值，自動確認音節 */
    private fun onSwipeDwellConfirmed(key: KeyModel) {
        // 視覺回饋：按鍵強閃爍
        swipeHighlightKey = key
        swipeHighlightAlpha = 1.0f
        highlightFadeAnimator?.cancel()
        highlightFadeAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 0f).apply {
            duration = 350
            addUpdateListener { anim ->
                swipeHighlightAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
        // 短震動
        if (hapticEnabled) vibrateShort()
        // 通知音節確認
        onSwipeSyllableConfirmed?.invoke(key.label)
        swipeDwellKey = null
    }

    private fun drawSwipeTrail(canvas: Canvas) {
        if (swipePoints.isEmpty()) return
        val alpha = (swipeAlpha * 255).toInt().coerceIn(0, 255)
        if (alpha == 0) return

        // 構建平滑貝茲路徑
        swipePath.reset()
        if (swipePoints.size == 1) {
            val p = swipePoints[0]
            swipeDotPaint.color = swipeColor
            swipeDotPaint.alpha = alpha
            canvas.drawCircle(p.x, p.y, 10f * density, swipeDotPaint)
            return
        }

        swipePath.moveTo(swipePoints[0].x, swipePoints[0].y)
        for (i in 1 until swipePoints.size - 1) {
            val curr = swipePoints[i]
            val next = swipePoints[i + 1]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f
            swipePath.quadTo(curr.x, curr.y, midX, midY)
        }
        swipePath.lineTo(swipePoints.last().x, swipePoints.last().y)

        // 外層發光（粗線 + 半透明）
        swipeGlowPaint.color = swipeColor
        swipeGlowPaint.alpha = (alpha * 0.25f).toInt()
        canvas.drawPath(swipePath, swipeGlowPaint)

        // 內層清晰路徑
        swipeLinePaint.color = swipeColor
        swipeLinePaint.alpha = alpha
        canvas.drawPath(swipePath, swipeLinePaint)

        // 起點光圈
        val start = swipePoints[0]
        swipeDotPaint.color = swipeColor
        swipeDotPaint.alpha = (alpha * 0.4f).toInt()
        canvas.drawCircle(start.x, start.y, 14f * density, swipeDotPaint)
        swipeDotPaint.alpha = (alpha * 0.9f).toInt()
        canvas.drawCircle(start.x, start.y, 6f * density, swipeDotPaint)

        // 按鍵高亮
        val highlightKey = swipeHighlightKey
        if (highlightKey != null && swipeHighlightAlpha > 0f) {
            val keyEntry = keyEntries.firstOrNull { it.key == highlightKey }
            if (keyEntry != null) {
                swipeKeyHighlightPaint.color = swipeColor
                swipeKeyHighlightPaint.alpha = (swipeHighlightAlpha * 255).toInt()
                canvas.drawRoundRect(keyEntry.visualRect, 8f * density, 8f * density, swipeKeyHighlightPaint)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 環形模式選單
    // -------------------------------------------------------------------------

    private fun showRadialModeMenu(anchorRect: RectF) {
        dismissPopup()

        val menu = RadialModeMenu(context, theme, currentMode, onModeSelected = { selectedMode ->
            dismissPopup()
            activeRadialView = null
            if (selectedMode != currentMode) {
                switchMode(selectedMode)
            }
        }, onFloatingToggle = {
            dismissPopup()
            activeRadialView = null
            onFloatingToggleListener?.invoke()
        })

        val menuSize = (180 * density + 40 * density).toInt()
        val popup = PopupWindow(
            menu,
            menuSize,
            menuSize,
            false  // 非 focusable，觸控由 KeyboardView 轉發
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(null)
            setOnDismissListener {
                popupWindow = null
                activeRadialView = null
            }
        }

        val xOff = (anchorRect.centerX() - (110 * density)).toInt().coerceAtLeast(0)
        val yOff = -(height - anchorRect.top.toInt() + (200 * density).toInt())
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        popupWindow = popup

        // 記錄 radial view 和座標偏移，讓 onTouchEvent 可以轉發
        activeRadialView = menu
        // popup 顯示位置相對於本 View 的偏移
        // showAtLocation 用 Gravity.BOTTOM|START：xOff 從左邊起算，yOff 從底部起算
        // 取得實際螢幕座標來計算偏移
        val viewLoc = IntArray(2)
        getLocationOnScreen(viewLoc)
        menu.post {
            val menuLoc = IntArray(2)
            menu.getLocationOnScreen(menuLoc)
            radialPopupOffsetX = (menuLoc[0] - viewLoc[0]).toFloat()
            radialPopupOffsetY = (menuLoc[1] - viewLoc[1]).toFloat()
        }
    }

    private fun showRadialLanguageMenu(anchorRect: RectF) {
        dismissPopup()

        val menu = RadialLanguageMenu(context, theme, currentInputMode) { selectedOption ->
            dismissPopup()
            activeRadialView = null
            onLanguageSelectedListener?.invoke(selectedOption)
        }

        val menuSize = (220 * density + 50 * density).toInt()
        val popup = PopupWindow(
            menu,
            menuSize,
            menuSize,
            false  // 非 focusable，觸控由 KeyboardView 轉發
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            setBackgroundDrawable(null)
            setOnDismissListener {
                popupWindow = null
                activeRadialView = null
            }
        }

        val xOff = (anchorRect.centerX() - menuSize / 2f).toInt().coerceAtLeast(0)
        val yOff = -(height - anchorRect.top.toInt() + (menuSize / 2f + 20 * density).toInt())
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        popupWindow = popup

        // 記錄 radial view 和座標偏移
        activeRadialView = menu
        val viewLoc = IntArray(2)
        getLocationOnScreen(viewLoc)
        menu.post {
            val menuLoc = IntArray(2)
            menu.getLocationOnScreen(menuLoc)
            radialPopupOffsetX = (menuLoc[0] - viewLoc[0]).toFloat()
            radialPopupOffsetY = (menuLoc[1] - viewLoc[1]).toFloat()
        }
    }

    // -------------------------------------------------------------------------
    // 觸控
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 候選列觸控（含展開箭頭）
                if (y < candidateBarHeight && candidates.isNotEmpty()) {
                    candidateLastTouchX = x
                    candidateDownX = x
                    candidateIsDragging = false
                    candidateTouchActive = true
                    return true
                }
                candidateTouchActive = false
                // 展開面板：記錄觸控起始位置
                if (isCandidateExpanded && y > candidateBarHeight) {
                    expandTouchStartY = y
                    expandLastTouchY = y
                    expandIsScrolling = false
                    return true
                }
                isLongPressTriggered = false
                isSwipeGesture = false
                swipeKeySequence.clear()
                swipeDownX = x
                swipeDownY = y
                val hit = keyEntries.firstOrNull { it.touchRect.contains(x, y) }
                if (hit != null) {
                    pressedKey = hit.key
                    longPressKey = hit.key
                    longPressRect = hit.touchRect
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
                    if (hapticEnabled) vibrateShort()
                    invalidate()
                    // 記錄滑動起始按鍵
                    if (swipeInputEnabled && !hit.key.isSpecial) {
                        swipeKeySequence.add(hit.key)
                    }
                }
                // 滑動路徑起點
                if (y >= candidateBarHeight) {
                    onSwipeStart(x, y)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Alt popup 滑動選字
                if (altPopupActive) {
                    updateAltPopupHover(x, y)
                    return true
                }
                // Radial menu 觸控轉發
                val radial = activeRadialView
                if (radial != null) {
                    val localX = x - radialPopupOffsetX
                    val localY = y - radialPopupOffsetY
                    val translated = MotionEvent.obtain(
                        event.downTime, event.eventTime, MotionEvent.ACTION_MOVE,
                        localX, localY, event.metaState
                    )
                    radial.dispatchTouchEvent(translated)
                    translated.recycle()
                    return true
                }
                // 候選列橫向捲動
                if (candidateTouchActive && candidates.isNotEmpty()) {
                    val dx = candidateLastTouchX - x
                    if (!candidateIsDragging && Math.abs(dx) > 8 * density) {
                        candidateIsDragging = true
                    }
                    if (candidateIsDragging) {
                        candidateScrollX += dx
                        candidateLastTouchX = x
                        invalidate()
                    }
                    return true
                }
                // 展開面板：滑動捲動
                if (isCandidateExpanded && y > candidateBarHeight) {
                    val dy = expandLastTouchY - y
                    if (!expandIsScrolling && Math.abs(y - expandTouchStartY) > 8 * density) {
                        expandIsScrolling = true
                    }
                    if (expandIsScrolling) {
                        expandScrollOffset += dy
                        expandLastTouchY = y
                        invalidate()
                    }
                    return true
                }
                val currentRect = longPressRect
                if (currentRect != null && !currentRect.contains(x, y)) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(deleteRepeatRunnable)
                    pressedKey = null
                    invalidate()
                }
                // 滑動輸入：追蹤經過的按鍵
                if (swipeInputEnabled && !isLongPressTriggered && y >= candidateBarHeight) {
                    val moveHit = keyEntries.firstOrNull { it.touchRect.contains(x, y) }
                    if (moveHit != null && !moveHit.key.isSpecial) {
                        val lastKey = swipeKeySequence.lastOrNull()
                        if (lastKey == null || lastKey.label != moveHit.key.label) {
                            swipeKeySequence.add(moveHit.key)
                        }
                    }
                    // 判定為滑動：距離超過閾值 且 經過 ≥2 個不同按鍵
                    val dist = Math.hypot((x - swipeDownX).toDouble(), (y - swipeDownY).toDouble())
                    if (!isSwipeGesture && dist > SWIPE_DISTANCE_THRESHOLD && swipeKeySequence.size >= 2) {
                        isSwipeGesture = true
                        // 取消長按（滑動中不觸發長按）
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
                // 更新滑動路徑
                if (y >= candidateBarHeight) {
                    onSwipeMove(x, y)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(deleteRepeatRunnable)
                pressedKey = null
                if (isSpaceLongPressed) {
                    isSpaceLongPressed = false
                    onSpaceLongPressListener?.invoke(false)
                }
                invalidate()

                // Alt popup 放開 → 選字
                if (altPopupActive) {
                    commitAltPopupSelection()
                    isLongPressTriggered = false
                    performClick()
                    return true
                }
                // Radial menu 放開 → 轉發 UP 給 menu，然後關閉
                val radial = activeRadialView
                if (radial != null) {
                    val localX = x - radialPopupOffsetX
                    val localY = y - radialPopupOffsetY
                    val translated = MotionEvent.obtain(
                        event.downTime, event.eventTime, MotionEvent.ACTION_UP,
                        localX, localY, event.metaState
                    )
                    radial.dispatchTouchEvent(translated)
                    translated.recycle()
                    activeRadialView = null
                    dismissPopup()
                    isLongPressTriggered = false
                    performClick()
                    return true
                }

                if (isLongPressTriggered) {
                    isLongPressTriggered = false
                    // 滑動路徑結束
                    onSwipeEnd()
                    return true
                }

                // 滑動輸入：滑動結束，送出按鍵序列
                if (isSwipeGesture && swipeKeySequence.size >= 2) {
                    onSwipeInputListener?.invoke(swipeKeySequence.toList())
                    swipeKeySequence.clear()
                    isSwipeGesture = false
                    // 滑動路徑結束
                    onSwipeEnd()
                    performClick()
                    return true
                }
                swipeKeySequence.clear()
                isSwipeGesture = false

                // 展開面板觸控選字（滑動中不選字）
                if (isCandidateExpanded && y > candidateBarHeight && allCandidates.isNotEmpty()) {
                    if (expandIsScrolling) {
                        expandIsScrolling = false
                        performClick()
                        return true
                    }
                    val cellWidth = width.toFloat() / EXPAND_GRID_COLS
                    val cellHeight = 42f * density
                    val contentPadding = 8f * density
                    val startY = candidateBarHeight + contentPadding - expandScrollOffset
                    val col = (x / cellWidth).toInt().coerceIn(0, EXPAND_GRID_COLS - 1)
                    val row = ((y - startY) / cellHeight).toInt()
                    if (row >= 0) {
                        val index = row * EXPAND_GRID_COLS + col
                        if (index in allCandidates.indices) {
                            onCandidateSpeakListener?.invoke(allCandidates[index])
                            onCandidateSelectListener?.invoke(index)
                            // 選字後保持展開，不關閉面板
                            invalidate()
                            performClick()
                            return true
                        }
                    }
                    // 點擊空白區域（超出候選字範圍）收合
                    isCandidateExpanded = false
                    expandScrollOffset = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // 候選列觸控選字（支援點擊 + 滑動釋放選字）
                if (candidateTouchActive && candidates.isNotEmpty()) {
                    candidateTouchActive = false
                    val totalDragX = Math.abs(x - candidateDownX)

                    // 展開箭頭：點擊右側 ▼ 區域
                    if (!candidateIsDragging && hasExpandArrow && candidateDownX >= width.toFloat() - expandArrowWidth) {
                        isCandidateExpanded = !isCandidateExpanded
                        expandScrollOffset = 0f
                        invalidate()
                        performClick()
                        return true
                    }

                    // 大幅水平滑動（>80dp）只當捲動，不選字
                    if (candidateIsDragging && totalDragX > 80 * density) {
                        candidateIsDragging = false
                        performClick()
                        return true
                    }
                    candidateIsDragging = false

                    // 用釋放位置 x 算候選字 index（滑動選字）
                    val idx = hitTestCandidateIndex(x)
                    if (idx >= 0 && idx < candidates.size) {
                        onCandidateSpeakListener?.invoke(candidates[idx])
                        onCandidateSelectListener?.invoke(idx)
                        performClick()
                        return true
                    }
                    performClick()
                    return true
                }

                val hit = keyEntries.firstOrNull { it.touchRect.contains(x, y) }
                if (hit != null) {
                    val key = hit.key
                    when {
                        key.code == KEYCODE_BACK_NORMAL -> switchMode(KeyboardMode.NORMAL)
                        key.code == KEYCODE_SWITCH_NUMBERS -> switchSubLayout(SubLayout.NUMBERS)
                        key.code == KEYCODE_SWITCH_SYMBOLS -> switchSubLayout(SubLayout.SYMBOLS)
                        key.code == KEYCODE_SWITCH_ABC -> switchSubLayout(SubLayout.ABC)
                        key.code == KEYCODE_SWITCH_NUMPAD -> switchSubLayout(SubLayout.NUMPAD)
                        key.code == KEYCODE_SHIFT -> toggleShift()
                        key.code == KEYCODE_CTRL -> toggleCtrl()
                        isCtrlActive && !key.isSpecial -> {
                            val ctrlCode = resolveCtrlCombo(key.label)
                            if (ctrlCode != null) {
                                onKeyPressListener?.invoke(ctrlCode, key.label)
                            }
                            isCtrlActive = false
                            invalidate()
                        }
                        else -> {
                            val shouldUpper = (isShifted || isCapsLock) && !key.isSpecial && key.label.length == 1
                            val output = if (shouldUpper) key.label.uppercase() else key.label
                            onKeyPressListener?.invoke(key.code, output)
                            if (!key.isSpecial) autoUnshift()
                        }
                    }
                    performClick()
                }
                // 滑動路徑結束
                onSwipeEnd()
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(deleteRepeatRunnable)
                pressedKey = null
                isLongPressTriggered = false
                if (altPopupActive) dismissAltPopup()
                activeRadialView = null
                if (isSpaceLongPressed) {
                    isSpaceLongPressed = false
                    onSpaceLongPressListener?.invoke(false)
                }
                // 滑動路徑取消
                onSwipeEnd()
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
