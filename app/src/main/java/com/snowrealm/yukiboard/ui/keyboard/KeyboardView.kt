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

        private const val LONG_PRESS_DELAY = 400L
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

    // -------------------------------------------------------------------------
    // 狀態
    // -------------------------------------------------------------------------
    private var keyRows: List<List<KeyModel>> = KeyboardState.defaultQwertyRows()
    private var sideRows: List<List<KeyModel>> = emptyList()
    private var candidates: List<String> = emptyList()
    private var composingText: String = ""
    private var theme: KeyboardTheme = KeyboardTheme.default()
    private var isShifted: Boolean = false
    private var isCapsLock: Boolean = false
    private var isCtrlActive: Boolean = false
    var currentMode: KeyboardMode = KeyboardMode.NORMAL
        private set

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
        textSize = 9f * density
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

    // 所有按鍵的點擊區域（主鍵盤 + 側邊面板）
    private val keyRects = mutableListOf<Pair<RectF, KeyModel>>()

    // 佈局常數
    private val candidateBarHeight = 40f * density
    private val keyPaddingH = 3f * density
    private val keyPaddingV = 4f * density
    private val keyCornerRadius = 8f * density

    // 直向行高 vs 橫向行高
    private val rowHeightPortrait = 52f * density
    private val rowHeightLandscape = 42f * density

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
        get() = if (isLandscape) rowHeightLandscape else rowHeightPortrait

    // -------------------------------------------------------------------------
    // 長按相關
    // -------------------------------------------------------------------------
    private val handler = Handler(Looper.getMainLooper())
    private var longPressKey: KeyModel? = null
    private var longPressRect: RectF? = null
    private var isLongPressTriggered = false
    private var pressedKey: KeyModel? = null
    private var popupWindow: PopupWindow? = null

    private val longPressRunnable = Runnable {
        val key = longPressKey ?: return@Runnable
        val rect = longPressRect ?: return@Runnable
        when {
            key.code == KEYCODE_TAB -> {
                isLongPressTriggered = true
                vibrateShort()
                showRadialModeMenu(rect)
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
        composingText = text
        invalidate()
    }

    fun updateCandidates(list: List<String>) {
        candidates = list
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
        KeyboardMode.NORMAL -> KeyboardState.defaultQwertyRows()
        KeyboardMode.DESIGNER -> KeyboardState.designerRows()
        KeyboardMode.DEVELOPER -> KeyboardState.developerRows()
        KeyboardMode.GAME -> KeyboardState.defaultQwertyRows() // 遊戲模式由 GameKeyboardView 接管
    }

    private fun getLandscapeLayout(mode: KeyboardMode): Pair<List<List<KeyModel>>, List<List<KeyModel>>> =
        when (mode) {
            KeyboardMode.NORMAL -> Pair(
                KeyboardState.landscapeQwertyRows(),
                KeyboardState.landscapeNormalSidePanel()
            )
            KeyboardMode.DESIGNER -> Pair(
                KeyboardState.landscapeDesignerRows(),
                KeyboardState.landscapeDesignerSidePanel()
            )
            KeyboardMode.DEVELOPER -> Pair(
                KeyboardState.landscapeDeveloperRows(),
                KeyboardState.landscapeDeveloperSidePanel()
            )
            KeyboardMode.GAME -> Pair(
                KeyboardState.defaultQwertyRows(),
                emptyList()
            )
        }

    fun switchMode(mode: KeyboardMode) {
        if (mode == currentMode) return
        currentMode = mode
        rebuildLayoutForOrientation()
        vibrateModeSwitch()
        showModeToast(mode)
        onModeChangedListener?.invoke(mode)
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

        val totalHeight = (candidateBarHeight + effectiveRowCount * rowHeight).toInt()
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
        keyRects.clear()
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
    }

    private var bgGradient: LinearGradient? = null
    private var bgGradientWidth = 0
    private var bgGradientHeight = 0
    private val bgPaint = Paint()

    private fun drawBackground(canvas: Canvas) {
        val startColor = theme.backgroundGradientStartInt
        val endColor = theme.backgroundGradientEndInt
        if (startColor != null && endColor != null) {
            // 漸層背景（重建 gradient 如果 size 改變）
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

    private fun drawCandidateBar(canvas: Canvas) {
        val barRect = RectF(0f, 0f, width.toFloat(), candidateBarHeight)
        keyPaint.color = theme.candidateBarColorInt
        canvas.drawRect(barRect, keyPaint)

        // 模式標示
        if (currentMode != KeyboardMode.NORMAL) {
            val modeTag = "${currentMode.icon} ${currentMode.displayName}"
            altHintPaint.color = theme.candidateSelectedColorInt
            altHintPaint.textSize = 10f * density
            altHintPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(modeTag, width.toFloat() - 8f * density, candidateBarHeight * 0.65f, altHintPaint)
            altHintPaint.textSize = 9f * density
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

        if (composingText.isNotEmpty()) {
            composingPaint.color = theme.composingTextColorInt
            canvas.drawText("[$composingText]", 16f, candidateBarHeight * 0.65f, composingPaint)
        }

        val slotWidth = width.toFloat() / maxOf(candidates.size, 1)
        candidates.forEachIndexed { i, text ->
            val slotLeft = slotWidth * i
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
    }

    private fun drawDivider(canvas: Canvas, x: Float) {
        dividerPaint.color = (theme.keyLabelColorInt and 0x00FFFFFF) or 0x33000000
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
                val rect = RectF(
                    x + keyPaddingH,
                    top + keyPaddingV,
                    x + keyWidth - keyPaddingH,
                    top + rh - keyPaddingV
                )
                keyRects.add(rect to key)

                // 按鍵背景
                val isPressed = pressedKey == key
                val isShiftKey = key.code == KEYCODE_SHIFT
                val isCtrlKey = key.code == KEYCODE_CTRL
                keyPaint.color = when {
                    isPressed -> theme.keyPressedColorInt
                    isShiftKey && (isShifted || isCapsLock) -> theme.keyPressedColorInt
                    isCtrlKey && isCtrlActive -> theme.candidateSelectedColorInt
                    key.isSpecial -> theme.specialKeyColorInt
                    else -> theme.keyColorInt
                }
                val cr = theme.keyCornerRadiusDp * density
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

                useLabelPaint.color = if (ctrlHint != null) theme.candidateSelectedColorInt else theme.keyLabelColorInt
                val textY = rect.top + (rect.height() + useLabelPaint.textSize) / 2f - useLabelPaint.descent()
                canvas.drawText(displayLabel, rect.centerX(), textY, useLabelPaint)

                // Alt hint
                if (key.altChars.isNotEmpty()) {
                    altHintPaint.color = (theme.keyLabelColorInt and 0x00FFFFFF) or 0x66000000
                    canvas.drawText("·", rect.right - 4f * density, rect.top + 10f * density, altHintPaint)
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

    // -------------------------------------------------------------------------
    // 觸控
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isLongPressTriggered = false
                val hit = keyRects.firstOrNull { it.first.contains(x, y) }
                if (hit != null) {
                    pressedKey = hit.second
                    longPressKey = hit.second
                    longPressRect = hit.first
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val currentRect = longPressRect
                if (currentRect != null && !currentRect.contains(x, y)) {
                    handler.removeCallbacks(longPressRunnable)
                    pressedKey = null
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
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

                // 候選列觸控選字
                if (y < candidateBarHeight && candidates.isNotEmpty()) {
                    val slotWidth = width.toFloat() / candidates.size
                    val index = (x / slotWidth).toInt().coerceIn(0, candidates.size - 1)
                    onCandidateSelectListener?.invoke(index)
                    performClick()
                    return true
                }

                val hit = keyRects.firstOrNull { it.first.contains(x, y) }
                if (hit != null) {
                    val key = hit.second
                    when {
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
