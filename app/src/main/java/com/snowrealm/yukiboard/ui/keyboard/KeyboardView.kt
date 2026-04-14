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

    internal val drawDelegate = KeyboardDrawDelegate(this)
    internal val touchDelegate = KeyboardTouchDelegate(this)
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

        const val KEYCODE_CTRL_C = -100
        const val KEYCODE_CTRL_V = -101
        const val KEYCODE_CTRL_X = -102
        const val KEYCODE_CTRL_A = -103
        const val KEYCODE_CTRL_Z = -104
        const val KEYCODE_CTRL_Y = -105

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

        const val KEYCODE_BACK_NORMAL = -400

        const val KEYCODE_SWITCH_NUMBERS = -500  // 切到數字符號鍵盤
        const val KEYCODE_SWITCH_SYMBOLS = -501  // 切到更多符號鍵盤
        const val KEYCODE_SWITCH_ABC     = -502  // 切回字母鍵盤
        const val KEYCODE_TOGGLE_KANA    = -503  // 日語 あ⇔ア 切換
        const val KEYCODE_SWITCH_NUMPAD  = -504  // 切到數字鍵盤（計算機）
        const val KEYCODE_VOICE         = -12   // 語音輸入
        const val KEYCODE_HANDWRITING   = -13   // 手寫輸入

        internal const val LONG_PRESS_DELAY = 400L
        internal const val CANDIDATE_BAR_MAX = 6
        internal const val EXPAND_GRID_COLS = 5
    }
    enum class CaseConvertType { UPPER, LOWER, INVERT }
    var onCaseConvert: ((CaseConvertType) -> Unit)? = null
    internal var caseMenuVisible = false
    internal var caseMenuAnchor = RectF()
    internal val caseMenuItems = listOf(
        "AA 全大寫" to CaseConvertType.UPPER,
        "aa 全小寫" to CaseConvertType.LOWER,
        "Aa 反轉"   to CaseConvertType.INVERT
    )
    internal var onKeyPressListener: ((keyCode: Int, label: String) -> Unit)? = null
    private var onModeChangedListener: ((KeyboardMode) -> Unit)? = null

    fun setOnKeyPressListener(listener: (keyCode: Int, label: String) -> Unit) {
        onKeyPressListener = listener
    }

    fun setOnModeChangedListener(listener: (KeyboardMode) -> Unit) {
        onModeChangedListener = listener
    }

    internal var onRawCommitListener: ((text: String) -> Unit)? = null
    fun setOnRawCommitListener(listener: (text: String) -> Unit) {
        onRawCommitListener = listener
    }

    internal var onCandidateSelectListener: ((index: Int) -> Unit)? = null
    fun setOnCandidateSelectListener(listener: (index: Int) -> Unit) {
        onCandidateSelectListener = listener
    }

    private var onCandidateExpandListener: (() -> Unit)? = null
    fun setOnCandidateExpandListener(listener: () -> Unit) {
        onCandidateExpandListener = listener
    }

    internal var onCandidateSpeakListener: ((text: String) -> Unit)? = null
    fun setOnCandidateSpeakListener(listener: (text: String) -> Unit) {
        onCandidateSpeakListener = listener
    }

    internal var onLanguageSelectedListener: ((RadialLanguageMenu.LanguageOption) -> Unit)? = null
    fun setOnLanguageSelectedListener(listener: (RadialLanguageMenu.LanguageOption) -> Unit) {
        onLanguageSelectedListener = listener
    }

    internal var onFloatingToggleListener: (() -> Unit)? = null
    fun setOnFloatingToggleListener(listener: () -> Unit) {
        onFloatingToggleListener = listener
    }

    var currentInputMode: com.snowrealm.yukiboard.input.router.InputMode =
        com.snowrealm.yukiboard.input.router.InputMode.ENGLISH
    private var keyRows: List<List<KeyModel>> = KeyboardState.defaultQwertyRows()
    private var sideRows: List<List<KeyModel>> = emptyList()
    internal var candidates: List<String> = emptyList()
        private set
    internal var allCandidates: List<String> = emptyList()
    internal var candidateSourceMap: Map<String, CandidateSource> = emptyMap()
        private set
    var recommendedStickerEmoji: String? = null
        set(value) { field = value; invalidate() }
    var onStickerHintClick: (() -> Unit)? = null
    internal var stickerHintRect: RectF? = null
    internal var isSwipeCandidateMode: Boolean = false
    internal var isCandidateExpanded: Boolean = false
    internal var candidateScrollX = 0f
    internal var candidateLastTouchX = 0f
    internal var candidateDownX = 0f   // ACTION_DOWN 時的 x，用於選字
    internal var candidateIsDragging = false
    internal var candidateTouchActive = false
    // 候選字���度快取（每次 draw 時更新）
    internal var candidateLongPressIndex = -1
    internal var candidateLongPressActive = false
    internal var candidateLongPressX = 0f
    internal val CANDIDATE_LONG_PRESS_DELAY = 400L
    internal val candidateLongPressRunnable = Runnable {
        val idx = hitTestCandidateIndex(candidateLongPressX)
        if (idx >= 0 && idx < candidates.size) {
            candidateLongPressIndex = idx
            candidateLongPressActive = true
            if (hapticEnabled) vibrateShort()
            invalidate()
        }
    }
    internal var candWidthsCache: List<Float> = emptyList()
    internal var candFixedLeftCache = 0f
    internal var candGapCache = 0f
    internal val expandArrowWidth by lazy { 44f * density }
    internal var hasExpandArrow = false
    var hasCustomBackground: Boolean = false
        set(value) {
            field = value
            if (value) {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(LAYER_TYPE_HARDWARE, null)
            }
        }
    var bgOverlayAlpha: Int = 100
    var customKeyLabelColor: Int = 0
    var keyScalePercent: Int = 100
        set(value) { field = value.coerceIn(70, 130); invalidate() }
    var keyOpacity: Int = 255
        set(value) { field = value.coerceIn(0, 255); invalidate() }
    var keyTextOpacity: Int = 255
        set(value) { field = value.coerceIn(0, 255); invalidate() }
    var perKeySizes: Map<String, Pair<Float, Float>> = emptyMap()
        set(value) { field = value; invalidate() }
    internal var expandScrollOffset: Float = 0f    // 展開面板捲動偏移
    internal var expandTouchStartY: Float = 0f     // 觸控起始 Y（用於滑動偵測）
    internal var expandLastTouchY: Float = 0f      // 上一次觸控 Y
    internal var expandIsScrolling: Boolean = false // 是否正在滑動中
    internal var expandDownX = 0f                   // 展開面板 DOWN 位置 X
    internal var expandDownY = 0f                   // 展開面板 DOWN 位置 Y
    internal var composingText: String = ""
    internal var theme: KeyboardTheme = KeyboardTheme.default()

    internal var animRenderer: com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer? = null
    internal var animStyle = com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer.Style.NONE
    private var animRunning = false
    private val animTick = object : Runnable {
        override fun run() {
            if (!animRunning || !isAttachedToWindow) return
            invalidate()
            postDelayed(this, 16)
        }
    }
    fun setAnimStyle(styleId: String) {
        val newStyle = com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer.Style.fromId(styleId)
        animStyle = newStyle
        removeCallbacks(animTick)
        if (newStyle != com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer.Style.NONE) {
            if (animRenderer == null) {
                animRenderer = com.snowrealm.yukiboard.ui.panel.ToolbarAnimRenderer(density)
            }
            animRenderer?.resetStyle()
            animRunning = true
            post(animTick)
        } else {
            animRunning = false
        }
        invalidate()
    }
    internal var isShifted: Boolean = false
    internal var isCapsLock: Boolean = false
    internal var isCtrlActive: Boolean = false
    var currentMode: KeyboardMode = KeyboardMode.NORMAL
        private set

    var voiceListeningLocale: String? = null
        set(value) { field = value; invalidate() }

    /** 單手模式 */
    enum class OneHandSide { NONE, LEFT, RIGHT }
    var oneHandSide: OneHandSide = OneHandSide.NONE
        set(value) { field = value; invalidate() }
    private val oneHandRatio = 0.75f  // 單手模式佔螢幕寬度比例
    var onOneHandChanged: ((OneHandSide) -> Unit)? = null
    internal var oneHandBtnRect = RectF()   // 切換按鈕
    internal var oneHandExpandRect = RectF() // 還原全寬按鈕

    internal enum class SubLayout { ABC, NUMBERS, SYMBOLS, NUMPAD }
    private var subLayout: SubLayout = SubLayout.ABC
    internal val density = context.resources.displayMetrics.density
    internal val keyDecorator = KeyDecorator(density)
    internal val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    internal val altHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 10f * density
    }
    private val cjkTypeface: android.graphics.Typeface = run {
        try {
            android.graphics.Typeface.createFromAsset(
                context.assets, "fonts/NotoSansCJKtc-Regular.otf"
            )
        } catch (e: Exception) {
            android.util.Log.w("KeyboardView", "Failed to load CJK font: ${e.message}")
            android.graphics.Typeface.DEFAULT
        }
    }
    internal val candidatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * density
        typeface = cjkTypeface
    }
    internal val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 14f * density
        typeface = cjkTypeface
        color = Color.CYAN
    }
    internal val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
    }
    internal val sideLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 7f * density
    }

    internal data class KeyEntry(val touchRect: RectF, val visualRect: RectF, val key: KeyModel)
    internal val keyEntries = mutableListOf<KeyEntry>()
    private val keyRects: List<Pair<RectF, KeyModel>>
        get() = keyEntries.map { it.touchRect to it.key }

    internal val candidateBarHeight = 44f * density
    internal val keyPaddingH = 5f * density
    internal val keyPaddingV = 6f * density
    private val keyCornerRadius = 8f * density
    private val bottomPadding = 8f * density

    private val rowHeightPortrait = 52f * density
    private val rowHeightLandscape = 48f * density

    private var actualRowHeight = rowHeightPortrait

    private val sidePanelRatio = 0.28f
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    internal var modeToastText: String? = null
    internal var modeToastEndTime = 0L
    private val modeToastDuration = 1200L

    var isSpaceLongPressed = false
        internal set

    internal var onSpaceLongPressListener: ((Boolean) -> Unit)? = null
    fun setOnSpaceLongPressListener(listener: (Boolean) -> Unit) {
        onSpaceLongPressListener = listener
    }

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

    private var pendingToastClear: Runnable? = null
    val isLandscape: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    internal val rowHeight: Float
        get() = actualRowHeight
    private val handler = Handler(Looper.getMainLooper())
    internal var longPressKey: KeyModel? = null
    internal var longPressRect: RectF? = null
    internal var isLongPressTriggered = false
    internal var pressedKey: KeyModel? = null
    internal var popupWindow: PopupWindow? = null

    internal var altPopupActive = false
    internal var altPopupChars: List<String> = emptyList()
    internal var altPopupRects: List<RectF> = emptyList()
    internal var altPopupHoveredIndex = -1
    internal var altPopupBgRect = RectF()
    internal var altPopupOriginKey: KeyModel? = null

    internal var activeRadialView: View? = null
    internal var radialPopupOffsetX = 0f
    internal var radialPopupOffsetY = 0f

    internal val swipePoints = mutableListOf<PointF>()
    internal var isSwipingActive = false
    internal var swipeAlpha = 0f
    internal var swipeFadeAnimator: android.animation.ValueAnimator? = null
    internal var swipeHighlightKey: KeyModel? = null
    internal var swipeHighlightAlpha = 0f
    internal var highlightFadeAnimator: android.animation.ValueAnimator? = null
    internal val swipePath = Path()
    internal var swipeColor = 0xFFB39DDB.toInt()
    var swipeTrailEnabled = true
    var swipeInputEnabled = true
    var showTouchAreaEnabled = false

    internal var touchAreaX = 0f
    internal var touchAreaY = 0f
    internal var touchAreaSize = 0f  // 接觸面積（MotionEvent.getSize）
    internal var touchAreaPressure = 0f
    private var touchAreaActive = false
    internal val touchAreaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    internal val touchAreaFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    internal var swipeDwellKey: KeyModel? = null
    internal var swipeDwellStartMs = 0L
    internal val swipeDwellThresholdMs = 180L  // 降到 180ms（速度偵測補充）
    internal val swipeDwellHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val swipeDwellRunnable = Runnable {
        swipeDwellKey?.let { touchDelegate.confirmSwipeKey(it) }
        swipeDwellKey = null
    }
    var onSwipeSyllableConfirmed: ((String) -> Unit)? = null

    internal val swipeVelocityPoints = ArrayDeque<Pair<Long, PointF>>()
    internal val VELOCITY_WINDOW_MS = 80L
    internal val DWELL_VELOCITY_THRESHOLD = 300f  // px/s
    internal val confirmedSwipeKeys = mutableListOf<KeyModel>()
    internal var lastConfirmedKey: KeyModel? = null

    internal val swipeKeySequence = mutableListOf<KeyModel>()
    internal var swipeDownX = 0f
    internal var swipeDownY = 0f
    internal var isSwipeGesture = false
    internal val SWIPE_DISTANCE_THRESHOLD = 12f * density  // 12dp（降低門檻提升滑行靈敏度）

    internal var onSwipeInputListener: ((keys: List<KeyModel>) -> Unit)? = null
    fun setOnSwipeInputListener(listener: (keys: List<KeyModel>) -> Unit) {
        onSwipeInputListener = listener
    }

    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                var life: Float, var color: Int, var size: Float)
    private val comboParticles = mutableListOf<Particle>()
    private var comboGlowAlpha = 0f

    internal val swipeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 22f * density
    }
    internal val swipeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f * density
    }
    internal val swipeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    internal val swipeKeyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    internal var deleteRepeatCount = 0
    internal val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || pressedKey?.code != KEYCODE_DELETE) return
            deleteRepeatCount++
            onKeyPressListener?.invoke(KEYCODE_DELETE, "⌫")
            if (hapticEnabled && deleteRepeatCount % 3 == 0) vibrateShort()
            val delay = (80L - (deleteRepeatCount * 5).coerceAtMost(60)).coerceAtLeast(20L)
            handler.postDelayed(this, delay)
        }
    }

    internal val longPressRunnable = Runnable {
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
                touchDelegate.showRadialModeMenu(rect)
            }
            key.code == KEYCODE_SWITCH_LANG -> {
                isLongPressTriggered = true
                vibrateShort()
                touchDelegate.showRadialLanguageMenu(rect)
            }
            key.code == KEYCODE_SPACE -> {
                isLongPressTriggered = true
                isSpaceLongPressed = true
                vibrateShort()
                onSpaceLongPressListener?.invoke(true)
            }
            key.code == KEYCODE_SHIFT -> {
                isLongPressTriggered = true
                vibrateShort()
                caseMenuAnchor = rect
                caseMenuVisible = true
                invalidate()
            }
            key.altChars.isNotEmpty() -> {
                isLongPressTriggered = true
                vibrateShort()
                touchDelegate.showAltPopup(key, rect)
            }
        }
    }

    fun updateEditorInfo(info: EditorInfo?) {
        rebuildLayoutForOrientation()
        invalidate()
    }

    fun updateComposingText(text: String) {
        if (composingText == text) return
        composingText = text
        invalidateCandidateBar()
    }

    fun updateCandidates(list: List<String>, sourceMap: Map<String, CandidateSource> = emptyMap()) {
        if (allCandidates == list && candidateSourceMap == sourceMap) return
        allCandidates = list
        candidates = list  // 不再截斷，全部由橫向捲動顯示
        candidateSourceMap = sourceMap
        isSwipeCandidateMode = false
        candidateScrollX = 0f
        highlightedCandidateIndex = -1
        if (list.isEmpty()) {
            isCandidateExpanded = false
            expandScrollOffset = 0f
        }
        invalidateCandidateBar()
    }

    private fun invalidateCandidateBar() {
        @Suppress("DEPRECATION")
        invalidate(0, 0, width, candidateBarHeight.toInt())
    }

    fun markSwipeCandidates(isSwipe: Boolean) {
        isSwipeCandidateMode = isSwipe
        invalidate()
    }

    fun collapseCandidatePanel() {
        isCandidateExpanded = false
        expandScrollOffset = 0f
        invalidate()
    }

    var highlightedCandidateIndex: Int = -1
        private set

    fun highlightCandidate(index: Int) {
        highlightedCandidateIndex = index
        invalidate()
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        keyDecorator.resetSeeds()
        if (newTheme.keyFontSizeSp > 0) {
            labelPaint.textSize = newTheme.keyFontSizeSp * density
        }
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

    internal fun resolveCtrlCombo(label: String): Int? {
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

    fun switchMode(mode: KeyboardMode) {
        if (mode == currentMode) return
        currentMode = mode
        subLayout = SubLayout.ABC
        rebuildLayoutForOrientation()
        vibrateModeSwitch()
        showModeToast(mode)
        onModeChangedListener?.invoke(mode)
    }

    internal fun switchSubLayout(target: SubLayout) {
        if (subLayout == target) return
        subLayout = target
        rebuildLayoutForOrientation()
        invalidate()
    }

    private fun rebuildLayoutForOrientation() {
        val landscape = isLandscape
        val (main, side) = if (landscape) getLandscapeLayout() else getPortraitLayout() to emptyList()
        keyRows = main
        sideRows = if (landscape) side else emptyList()
        requestLayout()
        invalidate()
    }

    private fun getPortraitLayout(): List<List<KeyModel>> {
        return when (currentMode) {
            KeyboardMode.DESIGNER -> KeyboardState.designerRows()
            KeyboardMode.DEVELOPER -> KeyboardState.developerRows()
            KeyboardMode.GAME -> KeyboardState.gameRows()
            else -> rowsForCurrentInputMode()
        }
    }

    private fun rowsForCurrentInputMode(): List<List<KeyModel>> = when (subLayout) {
        SubLayout.ABC -> when (currentInputMode) {
            com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> KeyboardState.bopomofoRows()
            com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
            com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> KeyboardState.cangjieRows()
            com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> KeyboardState.japaneseRows()
            com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> KeyboardState.koreanRows()
            com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> KeyboardState.pinyinRows()
            else -> KeyboardState.defaultQwertyRows()
        }
        SubLayout.NUMBERS -> KeyboardState.numberSymbolRows()
        SubLayout.SYMBOLS -> KeyboardState.moreSymbolRows()
        SubLayout.NUMPAD -> KeyboardState.numpadRows()
    }

    private fun getLandscapeLayout(): Pair<List<List<KeyModel>>, List<List<KeyModel>>> {
        return when (currentMode) {
            KeyboardMode.DESIGNER -> KeyboardState.landscapeDesignerRows() to KeyboardState.landscapeDesignerSidePanel()
            KeyboardMode.DEVELOPER -> KeyboardState.landscapeDeveloperRows() to KeyboardState.landscapeDeveloperSidePanel()
            else -> {
                val main = when (subLayout) {
                    SubLayout.ABC -> when (currentInputMode) {
                        com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> KeyboardState.bopomofoRows()
                        com.snowrealm.yukiboard.input.router.InputMode.CANGJIE,
                        com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> KeyboardState.cangjieRows()
                        com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> KeyboardState.japaneseRows()
                        com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> KeyboardState.koreanRows()
                        com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> KeyboardState.pinyinRows()
                        else -> KeyboardState.defaultQwertyRows()
                    }
                    SubLayout.NUMBERS -> KeyboardState.numberSymbolRows()
                    SubLayout.SYMBOLS -> KeyboardState.moreSymbolRows()
                    SubLayout.NUMPAD -> KeyboardState.numpadRows()
                }
                main to KeyboardState.landscapeNormalSidePanel()
            }
        }
    }

    private fun showModeToast(mode: KeyboardMode) {
        val text = when (mode) {
            KeyboardMode.NORMAL -> "一般模式"
            KeyboardMode.DESIGNER -> "設計者模式"
            KeyboardMode.DEVELOPER -> "開發者模式"
            KeyboardMode.GAME -> "遊戲模式"
        }
        showToast(text)
    }

    fun showToast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun resetTouchState() {
        candidateTouchActive = false
        candidateIsDragging = false
        pressedKey = null
        isLongPressTriggered = false
        isSwipingActive = false
        swipePoints.clear()
        swipeKeySequence.clear()
        confirmedSwipeKeys.clear()
        lastConfirmedKey = null
        swipeVelocityPoints.clear()
        isSwipeGesture = false
        swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
        swipeDwellKey = null
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(deleteRepeatRunnable)
        altPopupActive = false
        altPopupChars = emptyList()
        altPopupRects = emptyList()
        altPopupHoveredIndex = -1
        caseMenuVisible = false
        activeRadialView = null
        isCandidateExpanded = false
        expandScrollOffset = 0f
        invalidate()
    }

    fun triggerComboEffect(level: Int) {
        val count = when (level) { 1 -> 8; 2 -> 16; 3 -> 30; else -> 50 }
        val baseColor = when (level) { 1 -> 0xFFFFD700.toInt(); 2 -> 0xFFFF6600.toInt(); 3 -> 0xFFFF3366.toInt(); else -> 0xFFFF00FF.toInt() }
        val cx = width / 2f; val cy = candidateBarHeight * 0.5f; val random = java.util.Random()
        for (i in 0 until count) {
            val angle = random.nextFloat() * Math.PI.toFloat() * 2
            val speed = (2f + random.nextFloat() * 4f) * density
            comboParticles.add(Particle(
                x = cx + (random.nextFloat() - 0.5f) * width * 0.3f, y = cy,
                vx = kotlin.math.cos(angle) * speed, vy = kotlin.math.sin(angle) * speed - 2f * density,
                life = 1f, color = baseColor, size = (2f + random.nextFloat() * 3f) * density
            ))
        }
        comboGlowAlpha = 0.6f; invalidate()
    }

    private fun updateAndDrawParticles(canvas: Canvas) {
        if (comboParticles.isEmpty() && comboGlowAlpha <= 0f) return
        val iter = comboParticles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.x += p.vx; p.y += p.vy; p.vy += 0.3f * density; p.life -= 0.025f
            if (p.life <= 0f) { iter.remove(); continue }
            val alpha = (p.life * 255).toInt().coerceIn(0, 255)
            keyPaint.color = (p.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(p.x, p.y, p.size * p.life, keyPaint)
        }
        if (comboGlowAlpha > 0f) {
            comboGlowAlpha -= 0.015f
            val a = (comboGlowAlpha * 40).toInt().coerceIn(0, 40)
            keyPaint.color = (0xFFD700 and 0x00FFFFFF) or (a shl 24)
            canvas.drawRect(0f, 0f, width.toFloat(), candidateBarHeight, keyPaint)
        }
        if (comboParticles.isNotEmpty() || comboGlowAlpha > 0f) postInvalidateDelayed(32)
    }

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keyEntries.clear()
        drawDelegate.drawBackground(canvas)
        drawDelegate.drawCandidateBar(canvas)

        if (isLandscape && sideRows.isNotEmpty()) {
            val dividerX = width * (1f - sidePanelRatio)
            drawDelegate.drawKeysInRegion(canvas, keyRows, 0f, dividerX)
            drawDelegate.drawDivider(canvas, dividerX)
            drawDelegate.drawKeysInRegion(canvas, sideRows, dividerX, width.toFloat())
        } else if (oneHandSide != OneHandSide.NONE) {
            val kbWidth = width * oneHandRatio
            val marginWidth = width - kbWidth
            val kbLeft = if (oneHandSide == OneHandSide.LEFT) 0f else marginWidth
            val kbRight = if (oneHandSide == OneHandSide.LEFT) kbWidth else width.toFloat()
            drawDelegate.drawKeysInRegion(canvas, keyRows, kbLeft, kbRight)
            drawDelegate.drawOneHandMargin(canvas, kbLeft, kbRight, marginWidth)
        } else {
            drawDelegate.drawKeysInRegion(canvas, keyRows, 0f, width.toFloat())
        }

        if (isCandidateExpanded && allCandidates.isNotEmpty()) {
            drawDelegate.drawExpandedCandidatePanel(canvas)
        }

        if (swipePoints.size >= 2 || (swipePoints.size == 1 && isSwipingActive)) {
            drawDelegate.drawSwipeTrail(canvas)
        }

        if (showTouchAreaEnabled && touchAreaActive) {
            drawDelegate.drawTouchArea(canvas)
        }

        updateAndDrawParticles(canvas)

        drawDelegate.drawAltPopupOverlay(canvas)

        if (candidateLongPressActive && candidateLongPressIndex >= 0) {
            drawDelegate.drawCandidateMagnifier(canvas, candidateLongPressIndex)
        }
        if (caseMenuVisible) drawDelegate.drawCaseMenu(canvas)
    }

    private var bgGradient: LinearGradient? = null
    private var bgGradientWidth = 0
    private var bgGradientHeight = 0
    private val bgPaint = Paint()

    /**
     * 單手模式邊欄：切換方向按鈕 + 還原全寬按鈕。
     */
    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val a = ((color ushr 24) * alphaFactor).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * 根據螢幕觸控 x 座標算出對應的候選字 index。
     * 使用與 drawCandidateBar 相同的 width 計算邏輯，避免座標系不匹配。
     */
    internal fun hitTestCandidateIndex(touchX: Float): Int {
        val widths = candWidthsCache
        if (widths.isEmpty()) return -1
        val gap = candGapCache
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

    internal fun dismissAltPopup() {
        altPopupActive = false
        altPopupHoveredIndex = -1
        altPopupChars = emptyList()
        altPopupRects = emptyList()
        altPopupOriginKey = null
        invalidate()
    }

    internal fun commitAltPopupSelection() {
        val idx = altPopupHoveredIndex
        if (idx in altPopupChars.indices) {
            val ch = altPopupChars[idx]
            val output = if ((isShifted || isCapsLock) && ch.length == 1) ch.uppercase() else ch
            onRawCommitListener?.invoke(output)
            autoUnshift()
        }
        dismissAltPopup()
    }

    private fun isBopomofoOrTone(ch: Char): Boolean =
        ch in '\u3105'..'\u312F' ||  // ㄅ-ㄩ
        ch == '\u02CA' ||            // ˊ
        ch == '\u02C7' ||            // ˇ
        ch == '\u02CB' ||            // ˋ
        ch == '\u02D9'               // ˙

    internal fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    /** 滑動停頓確認：記錄這個鍵為使用者意圖 */

    /** 停頓確認：滑動時在某按鍵停留超過閾值，自動確認音節 */

    /** 繪製觸控接觸面積圓圈 */

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (showTouchAreaEnabled) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchAreaX = x; touchAreaY = y
                    touchAreaSize = event.size; touchAreaPressure = event.pressure
                    touchAreaActive = true; invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchAreaActive = false; invalidate()
                }
            }
        }

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> touchDelegate.handleTouchDown(x, y, event)
            MotionEvent.ACTION_MOVE -> touchDelegate.handleTouchMove(x, y, event)
            MotionEvent.ACTION_UP -> touchDelegate.handleTouchUp(x, y, event)
            MotionEvent.ACTION_CANCEL -> touchDelegate.handleTouchCancel(event)
            else -> true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        swipeDwellHandler.removeCallbacks(swipeDwellRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
