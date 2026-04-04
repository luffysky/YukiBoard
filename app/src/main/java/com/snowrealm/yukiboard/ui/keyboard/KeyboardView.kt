package com.snowrealm.yukiboard.ui.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
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

    private var onCandidateSelectListener: ((index: Int) -> Unit)? = null
    fun setOnCandidateSelectListener(listener: (index: Int) -> Unit) {
        onCandidateSelectListener = listener
    }

    private var onCandidateExpandListener: (() -> Unit)? = null
    fun setOnCandidateExpandListener(listener: () -> Unit) {
        onCandidateExpandListener = listener
    }

    private var onLanguageSelectedListener: ((RadialLanguageMenu.LanguageOption) -> Unit)? = null
    fun setOnLanguageSelectedListener(listener: (RadialLanguageMenu.LanguageOption) -> Unit) {
        onLanguageSelectedListener = listener
    }

    /** 當前輸入法模式（供環形語言選單顯示用） */
    var currentInputMode: com.snowrealm.yukiboard.input.router.InputMode =
        com.snowrealm.yukiboard.input.router.InputMode.ENGLISH

    // -------------------------------------------------------------------------
    // 狀態
    // -------------------------------------------------------------------------
    private var keyRows: List<List<KeyModel>> = KeyboardState.defaultQwertyRows()
    private var sideRows: List<List<KeyModel>> = emptyList()
    private var candidates: List<String> = emptyList()
    private var allCandidates: List<String> = emptyList()
    private var isCandidateExpanded: Boolean = false
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

    /** 子佈局：NORMAL 模式下可切換數字/符號鍵盤 */
    private enum class SubLayout { ABC, NUMBERS, SYMBOLS }
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
    private val keyPaddingH = 3f * density
    private val keyPaddingV = 4f * density
    private val keyCornerRadius = 8f * density
    private val bottomPadding = 8f * density

    // 直向行高 vs 橫向行高（預設值，實際可能被容器高度壓縮）
    private val rowHeightPortrait = 52f * density
    private val rowHeightLandscape = 42f * density

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

    fun updateComposingText(text: String) {
        if (composingText == text) return
        composingText = text
        invalidate()
    }

    fun updateCandidates(list: List<String>) {
        if (allCandidates == list) return
        allCandidates = list
        candidates = if (list.size > CANDIDATE_BAR_MAX) list.take(CANDIDATE_BAR_MAX) else list
        highlightedCandidateIndex = -1
        isCandidateExpanded = false
        expandScrollOffset = 0f
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
            SubLayout.ABC -> KeyboardState.defaultQwertyRows()
            SubLayout.NUMBERS -> KeyboardState.numberSymbolRows()
            SubLayout.SYMBOLS -> KeyboardState.moreSymbolRows()
        }
        KeyboardMode.DESIGNER -> KeyboardState.designerRows()
        KeyboardMode.DEVELOPER -> KeyboardState.developerRows()
        KeyboardMode.GAME -> KeyboardState.gameRows()
    }

    private fun getLandscapeLayout(mode: KeyboardMode): Pair<List<List<KeyModel>>, List<List<KeyModel>>> =
        when (mode) {
            KeyboardMode.NORMAL -> when (subLayout) {
                SubLayout.ABC -> Pair(
                    KeyboardState.landscapeQwertyRows(),
                    KeyboardState.landscapeNormalSidePanel()
                )
                SubLayout.NUMBERS -> Pair(
                    KeyboardState.numberSymbolRows(),
                    KeyboardState.landscapeNormalSidePanel()
                )
                SubLayout.SYMBOLS -> Pair(
                    KeyboardState.moreSymbolRows(),
                    KeyboardState.landscapeNormalSidePanel()
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

        // 語言標籤（常駐顯示在左側）
        val langLabel = when (currentInputMode) {
            com.snowrealm.yukiboard.input.router.InputMode.ENGLISH -> "EN"
            com.snowrealm.yukiboard.input.router.InputMode.BOPOMOFO -> "中文 [注音]"
            com.snowrealm.yukiboard.input.router.InputMode.PINYIN -> "中文 [拼音]"
            com.snowrealm.yukiboard.input.router.InputMode.CANGJIE -> "中文 [倉頡]"
            com.snowrealm.yukiboard.input.router.InputMode.SPEED_CANGJIE -> "中文 [速成]"
            com.snowrealm.yukiboard.input.router.InputMode.JAPANESE -> "日本語 [ローマ字]"
            com.snowrealm.yukiboard.input.router.InputMode.KOREAN -> "한국어"
            com.snowrealm.yukiboard.input.router.InputMode.AUTO -> "AUTO"
        }
        altHintPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x60000000
        altHintPaint.textSize = 10f * density
        altHintPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(langLabel, 8f * density, candidateBarHeight * 0.65f, altHintPaint)
        altHintPaint.textAlign = Paint.Align.RIGHT  // 還原
        val langLabelWidth = altHintPaint.measureText(langLabel) + 16f * density

        // composing text（顯示在語言標籤右側）
        var composingWidth = langLabelWidth
        if (composingText.isNotEmpty()) {
            composingPaint.color = theme.composingTextColorInt
            val displayText = "[$composingText]"
            canvas.drawText(displayText, langLabelWidth + 4f * density, candidateBarHeight * 0.65f, composingPaint)
            composingWidth = langLabelWidth + composingPaint.measureText(displayText) + 12f * density
        }

        // 展開按鈕寬度
        val hasMoreCandidates = allCandidates.size > CANDIDATE_BAR_MAX
        val expandBtnWidth = if (hasMoreCandidates) 36f * density else 0f
        val barAvailableWidth = width.toFloat() - expandBtnWidth - composingWidth

        val slotWidth = barAvailableWidth / maxOf(candidates.size, 1)
        candidates.forEachIndexed { i, text ->
            val slotLeft = composingWidth + slotWidth * i
            val cx = slotLeft + slotWidth / 2f

            // 第一個候選加高亮背景
            if (i == 0 && candidates.size > 1) {
                keyPaint.color = theme.candidateSelectedColorInt
                keyPaint.alpha = 40
                val pill = RectF(slotLeft + 4 * density, 4 * density, slotLeft + slotWidth - 4 * density, candidateBarHeight - 4 * density)
                canvas.drawRoundRect(pill, 6 * density, 6 * density, keyPaint)
                keyPaint.alpha = 255
                candidatePaint.color = theme.candidateSelectedColorInt
            } else {
                candidatePaint.color = theme.candidateTextColorInt
            }
            canvas.drawText(text, cx, candidateBarHeight * 0.65f, candidatePaint)

            // 候選詞分隔線
            if (i > 0) {
                dividerPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x20000000
                canvas.drawLine(slotLeft, 8 * density, slotLeft, candidateBarHeight - 8 * density, dividerPaint)
            }
        }

        // ▼ 展開按鈕
        if (hasMoreCandidates) {
            val btnLeft = width.toFloat() - expandBtnWidth
            // 分隔線
            dividerPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x30000000
            canvas.drawLine(btnLeft, 6 * density, btnLeft, candidateBarHeight - 6 * density, dividerPaint)
            // 箭頭符號
            candidatePaint.color = theme.candidateSelectedColorInt
            val arrow = if (isCandidateExpanded) "▲" else "▼"
            canvas.drawText(arrow, btnLeft + expandBtnWidth / 2f, candidateBarHeight * 0.65f, candidatePaint)
        }
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

            // 候選字文字
            candidatePaint.color = theme.candidateTextColorInt
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
        }

        val totalUnits = rows.maxOfOrNull { row ->
            row.sumOf { it.widthWeight.toDouble() }.toFloat()
        } ?: 10f

        rows.forEachIndexed { rowIndex, row ->
            val top = candidateBarHeight + rowIndex * rh
            var x = left
            val unitWidth = regionWidth / totalUnits

            row.forEach { key ->
                val keyWidth = unitWidth * key.widthWeight
                val touchRect = RectF(x, top, x + keyWidth, top + rh)
                val rect = RectF(
                    x + keyPaddingH,
                    top + keyPaddingV,
                    x + keyWidth - keyPaddingH,
                    top + rh - keyPaddingV
                )
                keyEntries.add(KeyEntry(touchRect, rect, key))

                // 按鍵背景
                val isPressed = pressedKey == key
                val isShiftKey = key.code == KEYCODE_SHIFT
                val isCtrlKey = key.code == KEYCODE_CTRL
                val rawKeyColor = when {
                    isPressed -> theme.keyPressedColorInt
                    isShiftKey && (isShifted || isCapsLock) -> theme.keyPressedColorInt
                    isCtrlKey && isCtrlActive -> theme.candidateSelectedColorInt
                    key.isSpecial -> theme.specialKeyColorInt
                    else -> theme.keyColorInt
                }
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

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.candidateBarColorInt)
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            elevation = 8f * density
        }

        val allChars = listOf(key.label) + key.altChars
        allChars.forEach { ch ->
            container.addView(TextView(context).apply {
                text = ch
                textSize = 18f
                setTextColor(theme.keyLabelColorInt)
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                setBackgroundColor(theme.keyColorInt)
                setOnClickListener {
                    val output = if ((isShifted || isCapsLock) && ch.length == 1) ch.uppercase() else ch
                    onKeyPressListener?.invoke(ch[0].code, output)
                    autoUnshift()
                    dismissPopup()
                }
            })
            container.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams((2 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            })
        }

        val popup = PopupWindow(container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setOnDismissListener { popupWindow = null }
        }

        val xOff = anchorRect.left.toInt()
        val yOff = -(height - anchorRect.top.toInt() + (48 * density).toInt())
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        popupWindow = popup
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    // -------------------------------------------------------------------------
    // 環形模式選單
    // -------------------------------------------------------------------------

    private fun showRadialModeMenu(anchorRect: RectF) {
        dismissPopup()

        val menu = RadialModeMenu(context, theme, currentMode) { selectedMode ->
            dismissPopup()
            if (selectedMode != currentMode) {
                switchMode(selectedMode)
            }
        }

        val popup = PopupWindow(
            menu,
            (180 * density + 40 * density).toInt(),
            (180 * density + 40 * density).toInt(),
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(null)
            setOnDismissListener { popupWindow = null }
        }

        val xOff = (anchorRect.centerX() - (110 * density)).toInt().coerceAtLeast(0)
        val yOff = -(height - anchorRect.top.toInt() + (200 * density).toInt())
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        popupWindow = popup
    }

    private fun showRadialLanguageMenu(anchorRect: RectF) {
        dismissPopup()

        val menu = RadialLanguageMenu(context, theme, currentInputMode) { selectedOption ->
            dismissPopup()
            onLanguageSelectedListener?.invoke(selectedOption)
        }

        val menuSize = (220 * density + 50 * density).toInt()
        val popup = PopupWindow(
            menu,
            menuSize,
            menuSize,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(null)
            setOnDismissListener { popupWindow = null }
        }

        val xOff = (anchorRect.centerX() - menuSize / 2f).toInt().coerceAtLeast(0)
        val yOff = -(height - anchorRect.top.toInt() + (menuSize / 2f + 20 * density).toInt())
        popup.showAtLocation(this, Gravity.BOTTOM or Gravity.START, xOff, -yOff)
        popupWindow = popup
    }

    // -------------------------------------------------------------------------
    // 觸控
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 展開面板：記錄觸控起始位置
                if (isCandidateExpanded && y > candidateBarHeight) {
                    expandTouchStartY = y
                    expandLastTouchY = y
                    expandIsScrolling = false
                    return true
                }
                isLongPressTriggered = false
                val hit = keyEntries.firstOrNull { it.touchRect.contains(x, y) }
                if (hit != null) {
                    pressedKey = hit.key
                    longPressKey = hit.key
                    longPressRect = hit.touchRect
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
                    if (hapticEnabled) vibrateShort()
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
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

                if (isLongPressTriggered) {
                    isLongPressTriggered = false
                    return true
                }

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
                            onCandidateSelectListener?.invoke(index)
                            isCandidateExpanded = false
                            expandScrollOffset = 0f
                            invalidate()
                            performClick()
                            return true
                        }
                    }
                    // 點擊空白區域收合
                    isCandidateExpanded = false
                    expandScrollOffset = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // 候選列觸控選字
                if (y < candidateBarHeight && candidates.isNotEmpty()) {
                    // ▼ 展開按鈕
                    val hasMore = allCandidates.size > CANDIDATE_BAR_MAX
                    val expandBtnWidth = if (hasMore) 36f * density else 0f
                    if (hasMore && x >= width.toFloat() - expandBtnWidth) {
                        isCandidateExpanded = !isCandidateExpanded
                        invalidate()
                        performClick()
                        return true
                    }
                    // composing 佔用寬度
                    val compW = if (composingText.isNotEmpty()) {
                        composingPaint.measureText("[$composingText]") + 24f * density
                    } else 0f
                    // 點在 composing 區域內不選字
                    if (x < compW) {
                        performClick()
                        return true
                    }
                    val barAvailableWidth = width.toFloat() - expandBtnWidth - compW
                    val slotWidth = barAvailableWidth / candidates.size
                    val index = ((x - compW) / slotWidth).toInt().coerceIn(0, candidates.size - 1)
                    onCandidateSelectListener?.invoke(index)
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
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(deleteRepeatRunnable)
                pressedKey = null
                isLongPressTriggered = false
                if (isSpaceLongPressed) {
                    isSpaceLongPressed = false
                    onSpaceLongPressListener?.invoke(false)
                }
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
