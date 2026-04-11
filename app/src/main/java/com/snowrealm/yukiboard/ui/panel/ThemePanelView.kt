package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.keyboard.KeyboardView
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 主題瀏覽面板 — 在鍵盤內直接預覽和切換主題、背景、字體顏色、按鍵設定。
 *
 * 九區塊：主題列表 → 背景控制 → 背景調整 → 字體顏色 → 按鍵設定 → 透明鍵盤
 *         → 鍵盤尺寸 → 個別按鍵尺寸 → 鍵盤預覽
 * 可上下捲動。設定與 SettingsActivity 雙向同步（都讀寫同一份 prefs）。
 */
class ThemePanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val prefs = YukiPreferences(context)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 13f * density
        isFakeBoldText = true
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }
    private val previewKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val sliderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewBgMatrix = Matrix()

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onThemeSelected: ((KeyboardTheme) -> Unit)? = null
    var onAnimThemeSelected: ((String) -> Unit)? = null
    var onPickBackground: (() -> Unit)? = null
    var onPickVideo: (() -> Unit)? = null
    var onClearBackground: (() -> Unit)? = null
    var onOverlayAlphaChanged: ((Int) -> Unit)? = null
    var onLabelColorChanged: ((Int) -> Unit)? = null
    var onBackgroundSettingsChanged: (() -> Unit)? = null
    /** 按鍵大小/透明度/透明鍵盤等變更（通知 IME 即時套用） */
    var onKeySettingsChanged: (() -> Unit)? = null
    /** 個別按鍵尺寸變更 */
    var onPerKeySizeChanged: (() -> Unit)? = null
    /** 鍵盤高度變更 */
    var onKeyboardHeightChanged: (() -> Unit)? = null

    var currentThemeId: String = "snow_dark"
        set(value) { field = value; invalidate() }

    var availableThemes: List<KeyboardTheme> = KeyboardTheme.all()
        set(value) { field = value; invalidate() }

    var previewBgBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    private val themeRects = mutableListOf<Pair<RectF, Int>>()
    private val animThemeRects = mutableListOf<Pair<RectF, ToolbarAnimRenderer.Style>>()
    private val cornerR = 10f * density
    private val pad = 8f * density
    private val sectionGap = 12f * density

    // 背景區按鈕
    private var pickImgRect: RectF? = null
    private var pickVideoRect: RectF? = null
    private var clearBgRect: RectF? = null

    // 比例模式按鈕
    private var scaleCropRect: RectF? = null
    private var scaleFitRect: RectF? = null

    // 旋轉按鈕
    private var rotBtnRects = arrayOfNulls<RectF>(4)  // 0°, 90°, 180°, 270°

    // 透明鍵盤切換按鈕
    private var transparentToggleRect: RectF? = null

    // 個別按鍵 — 重置全部
    private var resetAllKeySizeRect: RectF? = null
    // 個別按鍵 — 重置選中
    private var resetSelectedKeySizeRect: RectF? = null

    // 滑桿定義
    private enum class SliderId {
        OVERLAY, OFFSET_X, OFFSET_Y,
        KEY_SCALE, KEY_OPACITY,
        TRANSPARENT_ALPHA,
        KB_HEIGHT,
        PER_KEY_WIDTH, PER_KEY_HEIGHT
    }
    private data class SliderDef(val id: SliderId, var trackRect: RectF = RectF())
    private val sliders = SliderId.values().map { SliderDef(it) }
    private var draggingSlider: SliderId? = null

    // 字體顏色
    private data class ColorEntry(val color: Int, val label: String)
    private val labelColors = listOf(
        ColorEntry(0, "主題"),
        ColorEntry(0xFFFFFFFF.toInt(), "白"),
        ColorEntry(0xFF000000.toInt(), "黑"),
        ColorEntry(0xFF7FD8FF.toInt(), "冰藍"),
        ColorEntry(0xFFFF6B9D.toInt(), "粉"),
        ColorEntry(0xFFFFD700.toInt(), "金"),
        ColorEntry(0xFF5EEAD4.toInt(), "薄荷"),
        ColorEntry(0xFFA78BFA.toInt(), "紫"),
        ColorEntry(0xFFFF6B6B.toInt(), "紅"),
        ColorEntry(0xFF4ADE80.toInt(), "綠"),
        ColorEntry(0xFFFF9500.toInt(), "橘")
    )
    private val colorRects = mutableListOf<Pair<RectF, Int>>()

    // 捲動
    private var scrollY = 0f
    private var contentHeight = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // 個別按鍵選擇
    private var selectedKeyId: String? = null
    private val perKeyRects = mutableListOf<Pair<RectF, String>>() // rect -> keyId

    fun reloadPreviewBitmap() {
        val savedBg = KeyboardBackgroundView.getSavedBackground(context)
        previewBgBitmap = if (savedBg != null) {
            if (KeyboardBackgroundView.isVideoFile(savedBg)) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(savedBg.absolutePath)
                    val frame = retriever.getFrameAtTime(0)
                    retriever.release()
                    frame
                } catch (_: Exception) { null }
            } else {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(savedBg.absolutePath, opts)
                } catch (_: Exception) { null }
            }
        } else null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadPreviewBitmap()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        themeRects.clear()
        animThemeRects.clear()
        colorRects.clear()
        perKeyRects.clear()
        pickImgRect = null; pickVideoRect = null; clearBgRect = null
        scaleCropRect = null; scaleFitRect = null
        rotBtnRects = arrayOfNulls(4)
        transparentToggleRect = null
        resetAllKeySizeRect = null; resetSelectedKeySizeRect = null
        sliders.forEach { it.trackRect.setEmpty() }

        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(0f, scrollY)

        var curY = pad

        // ═══ 1: 主題列表 ═══
        curY = drawSectionTitle(canvas, "主題", curY)
        curY = drawThemeCards(canvas, curY)
        curY += sectionGap

        // ═══ 1.5: 動畫主題 ═══
        curY = drawSectionTitle(canvas, "動畫主題", curY)
        curY = drawAnimThemeCards(canvas, curY)
        curY += sectionGap

        // ═══ 2: 背景控制 ═══
        curY = drawSectionTitle(canvas, "鍵盤背景", curY)
        curY = drawBackgroundButtons(canvas, curY)
        curY += sectionGap

        // ═══ 3: 背景調整 ═══
        curY = drawSectionTitle(canvas, "背景調整", curY)
        curY = drawBackgroundAdjust(canvas, curY)
        curY += sectionGap

        // ═══ 4: 字體顏色 ═══
        curY = drawSectionTitle(canvas, "按鍵字體顏色", curY)
        curY = drawColorPicker(canvas, curY)
        curY += sectionGap

        // ═══ 5: 按鍵設定 ═══
        curY = drawSectionTitle(canvas, "按鍵設定", curY)
        curY = drawKeySettings(canvas, curY)
        curY += sectionGap

        // ═══ 6: 透明鍵盤 ═══
        curY = drawSectionTitle(canvas, "透明鍵盤", curY)
        curY = drawTransparentSettings(canvas, curY)
        curY += sectionGap

        // ═══ 7: 鍵盤尺寸 ═══
        curY = drawSectionTitle(canvas, "鍵盤尺寸", curY)
        curY = drawKeyboardSizeSettings(canvas, curY)
        curY += sectionGap

        // ═══ 8: 個別按鍵尺寸 ═══
        curY = drawSectionTitle(canvas, "個別按鍵尺寸", curY)
        curY = drawPerKeySettings(canvas, curY)
        curY += sectionGap

        // ═══ 9: 鍵盤預覽 ═══
        curY = drawSectionTitle(canvas, "鍵盤預覽", curY)
        curY = drawKeyboardPreview(canvas, curY)

        curY += pad
        contentHeight = curY
        canvas.restore()
    }

    // ──────────────── Section drawing ────────────────

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        titlePaint.color = theme.candidateTextColorInt
        titlePaint.alpha = 200
        canvas.drawText(title, pad + 4 * density, y + titlePaint.textSize, titlePaint)
        titlePaint.alpha = 255
        return y + titlePaint.textSize + 6 * density
    }

    // ─── 主題卡片 ───

    private fun drawThemeCards(canvas: Canvas, startY: Float): Float {
        val cardH = 72f * density
        val cols = ((width - pad) / (120f * density + pad)).toInt().coerceAtLeast(2)
        val actualCardW = (width - pad * (cols + 1)) / cols

        var y = startY
        availableThemes.forEachIndexed { i, t ->
            val col = i % cols
            val row = i / cols
            val x = pad + col * (actualCardW + pad)
            val cy = startY + row * (cardH + pad)

            val rect = RectF(x, cy, x + actualCardW, cy + cardH)
            themeRects.add(rect to i)

            cardPaint.color = t.backgroundColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            if (t.id == currentThemeId) {
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2.5f * density
                    color = theme.candidateSelectedColorInt
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, bp)
            }

            val keyY = rect.top + 10 * density
            val keyH = 16f * density; val keyW = 24f * density; val kp = 3f * density
            val kx = rect.left + 10 * density
            for (k in 0 until 3) {
                previewKeyPaint.color = if (k == 2) t.specialKeyColorInt else t.keyColorInt
                val kr = RectF(kx + k * (keyW + kp), keyY, kx + k * (keyW + kp) + keyW, keyY + keyH)
                canvas.drawRoundRect(kr, 3f * density, 3f * density, previewKeyPaint)
                textPaint.color = t.keyLabelColorInt; textPaint.textSize = 8f * density
                canvas.drawText(listOf("Q", "W", "⏎")[k], kr.centerX(), kr.centerY() + 3f * density, textPaint)
            }

            val barY = keyY + keyH + 4 * density
            previewKeyPaint.color = t.candidateBarColorInt
            canvas.drawRoundRect(
                RectF(rect.left + 8 * density, barY, rect.right - 8 * density, barY + 8 * density),
                2f * density, 2f * density, previewKeyPaint
            )

            textPaint.textSize = 10f * density; textPaint.color = t.keyLabelColorInt
            canvas.drawText(t.displayName, rect.centerX(), rect.bottom - 5 * density, textPaint)
            textPaint.textSize = 12f * density

            if (t.id == currentThemeId) {
                checkPaint.color = theme.candidateSelectedColorInt
                canvas.drawText("✓", rect.right - 12 * density, rect.top + 14 * density, checkPaint)
            }
            y = cy + cardH + pad
        }
        return y
    }

    // ─── 動畫主題卡片 ───

    private fun drawAnimThemeCards(canvas: Canvas, startY: Float): Float {
        val animStyles = ToolbarAnimRenderer.Style.entries
        val cardH = 52f * density
        val cols = ((width - pad) / (100f * density + pad)).toInt().coerceAtLeast(3)
        val actualCardW = (width - pad * (cols + 1)) / cols
        val currentAnimStyle = prefs.toolbarAnimStyle

        var y = startY
        animStyles.forEachIndexed { i, style ->
            val col = i % cols
            val row = i / cols
            val x = pad + col * (actualCardW + pad)
            val cy = startY + row * (cardH + pad)
            val rect = RectF(x, cy, x + actualCardW, cy + cardH)
            animThemeRects.add(rect to style)

            // 卡片背景色 — 依動畫主題特色
            val bgColor = when (style) {
                ToolbarAnimRenderer.Style.NONE -> theme.effectivePanelBackground()
                ToolbarAnimRenderer.Style.STARFIELD -> 0xFF0A0A2E.toInt()
                ToolbarAnimRenderer.Style.OCEAN -> 0xFF001830.toInt()
                ToolbarAnimRenderer.Style.EMBER -> 0xFF1A0A00.toInt()
                ToolbarAnimRenderer.Style.MATRIX -> 0xFF050505.toInt()
                ToolbarAnimRenderer.Style.FROST -> 0xFF0A1828.toInt()
                ToolbarAnimRenderer.Style.LAVA -> 0xFF1A0500.toInt()
                ToolbarAnimRenderer.Style.RAINBOW -> 0xFF0A0A14.toInt()
                ToolbarAnimRenderer.Style.THUNDER -> 0xFF0A0520.toInt()
            }
            cardPaint.color = bgColor
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 選中框
            val isActive = style.id == currentAnimStyle
            if (isActive) {
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE; strokeWidth = 2.5f * density
                    color = theme.candidateSelectedColorInt
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, bp)
                checkPaint.color = theme.candidateSelectedColorInt
                canvas.drawText("✓", rect.right - 12 * density, rect.top + 14 * density, checkPaint)
            }

            // 名稱
            textPaint.textSize = 10f * density
            textPaint.color = 0xFFE0E0E0.toInt()
            canvas.drawText(style.displayName, rect.centerX(), rect.bottom - 5 * density, textPaint)
            textPaint.textSize = 12f * density

            y = cy + cardH + pad
        }
        return y
    }

    // ─── 背景按鈕 ───

    private fun drawBackgroundButtons(canvas: Canvas, startY: Float): Float {
        val btnH = 30f * density
        val btnW = (width - pad * 4) / 3f
        val btnR = 6f * density

        val r1 = RectF(pad, startY, pad + btnW, startY + btnH)
        pickImgRect = r1
        drawButton(canvas, r1, "選圖片", btnR, theme.keyColorInt)

        val r2 = RectF(pad * 2 + btnW, startY, pad * 2 + btnW * 2, startY + btnH)
        pickVideoRect = r2
        drawButton(canvas, r2, "選影片", btnR, theme.keyColorInt)

        val r3 = RectF(pad * 3 + btnW * 2, startY, pad * 3 + btnW * 3, startY + btnH)
        clearBgRect = r3
        drawButton(canvas, r3, "清除", btnR, 0xFF3A2020.toInt())

        return startY + btnH + 6 * density
    }

    // ─── 背景調整 ───

    private fun drawBackgroundAdjust(canvas: Canvas, startY: Float): Float {
        var y = startY
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density
        val trackH = 6f * density

        y = drawSliderRow(canvas, y, "遮罩深度", SliderId.OVERLAY,
            prefs.bgOverlayAlpha / 255f, "%d%%".format((prefs.bgOverlayAlpha * 100) / 255),
            trackLeft, trackRight, trackH)

        // 顯示比例切換
        y += 4 * density
        textPaint.textSize = 11f * density
        textPaint.color = theme.candidateTextColorInt
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("顯示比例:", pad + 4 * density, y + textPaint.textSize, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        y += textPaint.textSize + 4 * density

        val btnH = 26f * density
        val btnW = (width - pad * 3) / 2f
        val btnR = 6f * density
        val isCrop = prefs.bgScaleMode == 0

        val rc = RectF(pad, y, pad + btnW, y + btnH)
        scaleCropRect = rc
        cardPaint.color = if (isCrop) theme.effectiveAccentColor() else theme.keyColorInt
        canvas.drawRoundRect(rc, btnR, btnR, cardPaint)
        btnTextPaint.color = if (isCrop) 0xFF111827.toInt() else theme.candidateTextColorInt
        canvas.drawText("保持比例", rc.centerX(), rc.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)

        val rf = RectF(pad * 2 + btnW, y, pad * 2 + btnW * 2, y + btnH)
        scaleFitRect = rf
        cardPaint.color = if (!isCrop) theme.effectiveAccentColor() else theme.keyColorInt
        canvas.drawRoundRect(rf, btnR, btnR, cardPaint)
        btnTextPaint.color = if (!isCrop) 0xFF111827.toInt() else theme.candidateTextColorInt
        canvas.drawText("整張塞入", rf.centerX(), rf.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)

        y += btnH + 8 * density

        // 影片旋轉按鈕
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("影片旋轉:", pad + 4 * density, y + textPaint.textSize, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        y += textPaint.textSize + 4 * density

        val rotLabels = arrayOf("0°", "90°", "180°", "270°")
        val rotDegs = intArrayOf(0, 90, 180, 270)
        val rotBtnW = (width - pad * 2 - 4 * density * 3) / 4f
        for (i in 0..3) {
            val rx = pad + i * (rotBtnW + 4 * density)
            val rect = RectF(rx, y, rx + rotBtnW, y + btnH)
            rotBtnRects[i] = rect
            val selected = prefs.bgRotation == rotDegs[i]
            cardPaint.color = if (selected) theme.effectiveAccentColor() else theme.keyColorInt
            canvas.drawRoundRect(rect, btnR, btnR, cardPaint)
            btnTextPaint.color = if (selected) 0xFF111827.toInt() else theme.candidateTextColorInt
            canvas.drawText(rotLabels[i], rect.centerX(), rect.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)
        }
        y += btnH + 8 * density

        y = drawSliderRow(canvas, y, "水平位置 X", SliderId.OFFSET_X,
            prefs.bgOffsetX, "%d%%".format((prefs.bgOffsetX * 100).toInt()),
            trackLeft, trackRight, trackH)

        y = drawSliderRow(canvas, y, "垂直位置 Y", SliderId.OFFSET_Y,
            prefs.bgOffsetY, "%d%%".format((prefs.bgOffsetY * 100).toInt()),
            trackLeft, trackRight, trackH)

        return y
    }

    // ─── 按鍵設定（大小 + 不透明度） ───

    private fun drawKeySettings(canvas: Canvas, startY: Float): Float {
        var y = startY
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density
        val trackH = 6f * density

        // 按鍵大小 70~130%
        val scalePct = (prefs.keyScalePercent - 70f) / 60f  // 0~1
        y = drawSliderRow(canvas, y, "按鍵大小", SliderId.KEY_SCALE,
            scalePct, "%d%%".format(prefs.keyScalePercent),
            trackLeft, trackRight, trackH)

        // 按鍵不透明度 0~255
        y = drawSliderRow(canvas, y, "按鍵不透明度", SliderId.KEY_OPACITY,
            prefs.keyOpacity / 255f, "%d%%".format((prefs.keyOpacity * 100) / 255),
            trackLeft, trackRight, trackH)

        return y
    }

    // ─── 透明鍵盤 ───

    private fun drawTransparentSettings(canvas: Canvas, startY: Float): Float {
        var y = startY
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density
        val trackH = 6f * density

        // toggle
        val btnH = 28f * density
        val btnR = 6f * density
        val isOn = prefs.transparentKeyboard
        val toggleRect = RectF(pad, y, width - pad, y + btnH)
        transparentToggleRect = toggleRect
        cardPaint.color = if (isOn) theme.effectiveAccentColor() else theme.keyColorInt
        canvas.drawRoundRect(toggleRect, btnR, btnR, cardPaint)
        btnTextPaint.color = if (isOn) 0xFF111827.toInt() else theme.candidateTextColorInt
        val toggleLabel = if (isOn) "透明鍵盤: ON（透視到底層 App）" else "透明鍵盤: OFF"
        canvas.drawText(toggleLabel, toggleRect.centerX(), toggleRect.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)
        y += btnH + 8 * density

        // 透明度滑桿
        if (isOn) {
            y = drawSliderRow(canvas, y, "透明度", SliderId.TRANSPARENT_ALPHA,
                prefs.transparentKeyboardAlpha / 255f,
                "%d%%".format((prefs.transparentKeyboardAlpha * 100) / 255),
                trackLeft, trackRight, trackH)
        }

        return y
    }

    // ─── 鍵盤尺寸 ───

    private fun drawKeyboardSizeSettings(canvas: Canvas, startY: Float): Float {
        var y = startY
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density
        val trackH = 6f * density

        // 鍵盤高度 25~55%
        val heightPct = (prefs.keyboardHeightRatio - 0.25f) / 0.30f
        y = drawSliderRow(canvas, y, "鍵盤高度", SliderId.KB_HEIGHT,
            heightPct, "%d%%".format((heightPct * 100).toInt()),
            trackLeft, trackRight, trackH)

        return y
    }

    // ─── 個別按鍵尺寸 ───

    private fun drawPerKeySettings(canvas: Canvas, startY: Float): Float {
        var y = startY

        // 說明文字
        textPaint.textSize = 10f * density
        textPaint.color = theme.candidateTextColorInt; textPaint.alpha = 160
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("點選按鍵後調整寬度和高度", pad + 4 * density, y + textPaint.textSize, textPaint)
        textPaint.alpha = 255; textPaint.textAlign = Paint.Align.CENTER
        y += textPaint.textSize + 6 * density

        // 繪製迷你鍵盤（可點選）
        val rows = KeyboardState.defaultQwertyRows()
        val pvW = width - pad * 2
        val rowH = 22f * density
        val totalUnits = rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() }.toFloat() }
        val perKeySizes = prefs.getAllPerKeySizes()

        rows.forEachIndexed { rowIdx, row ->
            val top = y + rowIdx * rowH
            var kx = pad
            val unitW = pvW / totalUnits

            row.forEach { key ->
                val keyW = unitW * key.widthWeight
                val keyId = if (key.code != 0) key.code.toString() else key.label
                val kr = RectF(kx + 1 * density, top + 1 * density, kx + keyW - 1 * density, top + rowH - 1 * density)
                perKeyRects.add(kr to keyId)

                // 有自訂尺寸的按鍵用不同顏色
                val hasCustom = perKeySizes.containsKey(keyId)
                val isSelected = keyId == selectedKeyId

                cardPaint.color = when {
                    isSelected -> theme.effectiveAccentColor()
                    hasCustom -> theme.candidateSelectedColorInt
                    key.isSpecial -> theme.specialKeyColorInt
                    else -> theme.keyColorInt
                }
                canvas.drawRoundRect(kr, 3f * density, 3f * density, cardPaint)

                val label = key.label
                if (label.isNotEmpty() && label.length <= 3) {
                    textPaint.textSize = 7f * density
                    textPaint.color = when {
                        isSelected -> 0xFF111827.toInt()
                        else -> theme.keyLabelColorInt
                    }
                    canvas.drawText(label, kr.centerX(), kr.centerY() + 3f * density, textPaint)
                }
                kx += keyW
            }
        }
        y += rows.size * rowH + 6 * density

        // 選中按鍵的調整滑桿
        val sel = selectedKeyId
        if (sel != null) {
            val trackLeft = pad + 4 * density
            val trackRight = width - pad - 4 * density
            val trackH = 6f * density

            // 顯示選中按鍵名
            textPaint.textSize = 11f * density
            textPaint.color = theme.effectiveAccentColor()
            textPaint.textAlign = Paint.Align.LEFT
            val selLabel = findKeyLabel(sel)
            canvas.drawText("選中: $selLabel", pad + 4 * density, y + textPaint.textSize, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
            y += textPaint.textSize + 6 * density

            // 寬度倍率 0.5~2.0
            val curW = prefs.getPerKeyWidth(sel)
            val wPct = (curW - 0.5f) / 1.5f
            y = drawSliderRow(canvas, y, "寬度", SliderId.PER_KEY_WIDTH,
                wPct, "%.0f%%".format(curW * 100),
                trackLeft, trackRight, trackH)

            // 高度倍率 0.5~2.0
            val curH = prefs.getPerKeyHeight(sel)
            val hPct = (curH - 0.5f) / 1.5f
            y = drawSliderRow(canvas, y, "高度", SliderId.PER_KEY_HEIGHT,
                hPct, "%.0f%%".format(curH * 100),
                trackLeft, trackRight, trackH)

            // 重置選中
            val btnH = 24f * density
            val btnR = 6f * density
            val btnW2 = (width - pad * 3) / 2f
            val rr = RectF(pad, y, pad + btnW2, y + btnH)
            resetSelectedKeySizeRect = rr
            drawButton(canvas, rr, "重置此鍵", btnR, 0xFF3A2020.toInt())

            val ra = RectF(pad * 2 + btnW2, y, pad * 2 + btnW2 * 2, y + btnH)
            resetAllKeySizeRect = ra
            drawButton(canvas, ra, "重置全部", btnR, 0xFF3A2020.toInt())

            y += btnH + 6 * density
        } else {
            // 只顯示重置全部
            val btnH = 24f * density
            val btnR = 6f * density
            val ra = RectF(pad, y, width / 2f, y + btnH)
            resetAllKeySizeRect = ra
            drawButton(canvas, ra, "重置全部", btnR, 0xFF3A2020.toInt())
            y += btnH + 6 * density
        }

        return y
    }

    private fun findKeyLabel(keyId: String): String {
        val code = keyId.toIntOrNull()
        if (code != null) {
            return when (code) {
                KeyboardView.KEYCODE_SHIFT -> "Shift"
                KeyboardView.KEYCODE_DELETE -> "刪除"
                KeyboardView.KEYCODE_ENTER -> "Enter"
                KeyboardView.KEYCODE_SPACE -> "空白"
                KeyboardView.KEYCODE_SWITCH_LANG -> "切換"
                KeyboardView.KEYCODE_SWITCH_NUMBERS -> "?123"
                KeyboardView.KEYCODE_TAB -> "Tab"
                KeyboardView.KEYCODE_CTRL -> "Ctrl"
                KeyboardView.KEYCODE_COMMA -> ","
                KeyboardView.KEYCODE_PERIOD -> "."
                else -> "key$code"
            }
        }
        return keyId
    }

    // ─── 字體顏色 ───

    private fun drawColorPicker(canvas: Canvas, startY: Float): Float {
        val dotR = 14f * density
        val gap = 6f * density
        val currentColor = prefs.customKeyLabelColor
        var x = pad + dotR
        val y = startY + dotR

        labelColors.forEach { entry ->
            val rect = RectF(x - dotR, y - dotR, x + dotR, y + dotR)
            colorRects.add(rect to entry.color)

            if (entry.color == 0) {
                val gradient = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                        0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                cardPaint.shader = gradient
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
                cardPaint.shader = null
            } else {
                cardPaint.color = entry.color
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
            }

            if (entry.color == 0xFFFFFFFF.toInt() || entry.color == 0 || entry.color == 0xFF000000.toInt()) {
                cardPaint.style = Paint.Style.STROKE; cardPaint.strokeWidth = 1f * density
                cardPaint.color = 0xFF555555.toInt()
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
                cardPaint.style = Paint.Style.FILL
            }

            if (entry.color == currentColor) {
                cardPaint.style = Paint.Style.STROKE; cardPaint.strokeWidth = 2.5f * density
                cardPaint.color = theme.candidateSelectedColorInt
                canvas.drawCircle(x, y, dotR, cardPaint)
                cardPaint.style = Paint.Style.FILL
            }

            textPaint.textSize = 8f * density; textPaint.color = theme.candidateTextColorInt
            textPaint.alpha = 160
            canvas.drawText(entry.label, x, y + dotR + 10 * density, textPaint)
            textPaint.alpha = 255; textPaint.textSize = 12f * density

            x += dotR * 2 + gap
        }
        return startY + dotR * 2 + 18 * density
    }

    // ─── 鍵盤預覽 ───

    private fun drawKeyboardPreview(canvas: Canvas, startY: Float): Float {
        val pvW = width - pad * 2
        val pvH = 100f * density
        val pvRect = RectF(pad, startY, pad + pvW, startY + pvH)

        cardPaint.color = theme.backgroundColorInt
        canvas.drawRoundRect(pvRect, cornerR, cornerR, cardPaint)

        canvas.save()
        canvas.clipRect(pvRect)

        val bmp = previewBgBitmap
        if (bmp != null && prefs.useCustomBackground) {
            previewBgMatrix.reset()
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val pcx = pvRect.left + pvW / 2f
            val pcy = pvRect.top + pvH / 2f

            if (prefs.bgScaleMode == 1) {
                previewBgMatrix.setScale(pvW / bw, pvH / bh)
                previewBgMatrix.postTranslate(pvRect.left, pvRect.top)
            } else {
                val scale = maxOf(pvW / bw, pvH / bh)
                val sw = bw * scale; val sh = bh * scale
                val maxDx = (sw - pvW).coerceAtLeast(0f)
                val maxDy = (sh - pvH).coerceAtLeast(0f)
                previewBgMatrix.setScale(scale, scale)
                val tx = (-maxDx * prefs.bgOffsetX).coerceIn(-maxDx, 0f)
                val ty = (-maxDy * prefs.bgOffsetY).coerceIn(-maxDy, 0f)
                previewBgMatrix.postTranslate(
                    pvRect.left + tx,
                    pvRect.top + ty
                )
            }
            // 旋轉（以預覽區域中心）— 與 buildFullKeyboardPreview 一致
            if (prefs.bgRotation != 0) {
                previewBgMatrix.postRotate(
                    prefs.bgRotation.toFloat(), pcx, pcy
                )
            }
            canvas.drawBitmap(bmp, previewBgMatrix, null)

            previewKeyPaint.color = Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
            previewKeyPaint.style = Paint.Style.FILL
            canvas.drawRect(pvRect, previewKeyPaint)
        }

        val rows = KeyboardState.defaultQwertyRows()
        val hasBg = bmp != null && prefs.useCustomBackground
        val keyAlpha = prefs.keyOpacity
        val rowH = pvH / rows.size
        val cr = theme.keyCornerRadiusDp * density * 0.5f
        val fontSize = 8f * density
        val totalUnits = rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() }.toFloat() }
        val perKeySizesMap = prefs.getAllPerKeySizes()
        val globalScaleInv = (200 - prefs.keyScalePercent) / 100f

        rows.forEachIndexed { rowIdx, row ->
            val top = pvRect.top + rowIdx * rowH
            var kx = pvRect.left
            val unitW = pvW / totalUnits
            val basePadH = 1.5f * density * globalScaleInv
            val basePadV = 1.5f * density * globalScaleInv

            row.forEach { key ->
                val keyW = unitW * key.widthWeight
                val keyId = if (key.code != 0) key.code.toString() else key.label
                val (pkW, pkH) = perKeySizesMap[keyId] ?: (1f to 1f)
                val adjPadH = basePadH + (keyW * (1f - pkW) / 2f).coerceAtLeast(0f)
                val adjPadV = basePadV + (rowH * (1f - pkH) / 2f).coerceAtLeast(0f)

                val kr = RectF(kx + adjPadH, top + adjPadV, kx + keyW - adjPadH, top + rowH - adjPadV)

                if (hasBg) {
                    previewKeyPaint.style = Paint.Style.STROKE
                    previewKeyPaint.strokeWidth = 0.5f * density
                    previewKeyPaint.color = Color.argb((keyAlpha * 0.3f).toInt().coerceIn(0, 255), 255, 255, 255)
                    canvas.drawRoundRect(kr, cr, cr, previewKeyPaint)
                } else {
                    val baseColor = if (key.isSpecial) theme.specialKeyColorInt else theme.keyColorInt
                    val a = ((baseColor ushr 24) * keyAlpha / 255).coerceIn(0, 255)
                    previewKeyPaint.color = (a shl 24) or (baseColor and 0x00FFFFFF)
                    previewKeyPaint.style = Paint.Style.FILL
                    canvas.drawRoundRect(kr, cr, cr, previewKeyPaint)
                }

                val label = key.label
                if (label.isNotEmpty() && label.length <= 3) {
                    textPaint.textSize = fontSize
                    textPaint.color = if (prefs.customKeyLabelColor != 0) prefs.customKeyLabelColor
                    else theme.keyLabelColorInt
                    val textY = kr.top + (kr.height() + fontSize) / 2f - textPaint.descent()
                    canvas.drawText(label, kr.centerX(), textY, textPaint)
                }
                kx += keyW
            }
        }
        textPaint.textSize = 12f * density

        canvas.restore()

        cardPaint.style = Paint.Style.STROKE; cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.candidateTextColorInt; cardPaint.alpha = 40
        canvas.drawRoundRect(pvRect, cornerR, cornerR, cardPaint)
        cardPaint.style = Paint.Style.FILL; cardPaint.alpha = 255

        return startY + pvH + pad
    }

    // ─── 共用繪製 ───

    private fun drawSliderRow(
        canvas: Canvas, startY: Float, label: String, id: SliderId,
        pct: Float, valueText: String,
        trackLeft: Float, trackRight: Float, trackH: Float
    ): Float {
        var y = startY
        textPaint.textSize = 11f * density
        textPaint.color = theme.candidateTextColorInt
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: $valueText", pad + 4 * density, y + textPaint.textSize, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        y += textPaint.textSize + 6 * density

        val trackRect = RectF(trackLeft, y, trackRight, y + trackH)
        sliders.first { it.id == id }.trackRect.set(trackRect)

        sliderPaint.color = theme.keyColorInt
        sliderPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(trackRect, trackH / 2, trackH / 2, sliderPaint)

        val clampedPct = pct.coerceIn(0f, 1f)
        val fillRect = RectF(trackLeft, y, trackLeft + (trackRight - trackLeft) * clampedPct, y + trackH)
        sliderPaint.color = theme.effectiveAccentColor(); sliderPaint.alpha = 180
        canvas.drawRoundRect(fillRect, trackH / 2, trackH / 2, sliderPaint)
        sliderPaint.alpha = 255

        val thumbX = trackLeft + (trackRight - trackLeft) * clampedPct
        val thumbR = 8f * density
        sliderPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.color = theme.effectiveAccentColor()
        sliderPaint.style = Paint.Style.STROKE; sliderPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.style = Paint.Style.FILL

        return y + trackH + 10 * density
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, cornerR: Float, bgColor: Int) {
        cardPaint.color = bgColor
        canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

        cardPaint.style = Paint.Style.STROKE; cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.candidateTextColorInt; cardPaint.alpha = 40
        canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)
        cardPaint.style = Paint.Style.FILL; cardPaint.alpha = 255

        btnTextPaint.color = theme.candidateTextColorInt
        canvas.drawText(label, rect.centerX(), rect.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)
    }

    // ─── 觸控 ───

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = event.x
        val cy = event.y - scrollY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false

                for (s in sliders) {
                    if (s.trackRect.isEmpty) continue
                    val ty = s.trackRect.centerY()
                    if (cy > ty - 20 * density && cy < ty + 20 * density
                        && cx > s.trackRect.left - 10 * density && cx < s.trackRect.right + 10 * density) {
                        draggingSlider = s.id
                        handleSliderUpdate(s.id, cx, s.trackRect)
                        return true
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val ds = draggingSlider
                if (ds != null) {
                    val track = sliders.first { it.id == ds }.trackRect
                    handleSliderUpdate(ds, cx, track)
                    return true
                }
                val dy = event.y - lastTouchY
                if (!isDragging && Math.abs(dy) > 8 * density) isDragging = true
                if (isDragging) {
                    scrollY += dy; clampScroll(); lastTouchY = event.y; invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingSlider != null) { draggingSlider = null; return true }
                if (isDragging) { isDragging = false; return true }

                // 主題卡片
                val themeHit = themeRects.firstOrNull { it.first.contains(cx, cy) }
                if (themeHit != null) {
                    val selected = availableThemes.getOrNull(themeHit.second)
                    if (selected != null) {
                        currentThemeId = selected.id
                        prefs.useCustomBackground = false
                        // 選靜態主題時清除動畫主題
                        prefs.toolbarAnimStyle = "none"
                        onAnimThemeSelected?.invoke("none")
                        onThemeSelected?.invoke(selected)
                        performClick()
                    }
                    return true
                }

                // 動畫主題卡片
                val animHit = animThemeRects.firstOrNull { it.first.contains(cx, cy) }
                if (animHit != null) {
                    val style = animHit.second
                    prefs.toolbarAnimStyle = style.id
                    onAnimThemeSelected?.invoke(style.id)
                    invalidate()
                    performClick()
                    return true
                }

                // 背景按鈕
                pickImgRect?.let { if (it.contains(cx, cy)) { onPickBackground?.invoke(); performClick(); return true } }
                pickVideoRect?.let { if (it.contains(cx, cy)) { onPickVideo?.invoke(); performClick(); return true } }
                clearBgRect?.let { if (it.contains(cx, cy)) { onClearBackground?.invoke(); performClick(); return true } }

                // 比例模式
                scaleCropRect?.let {
                    if (it.contains(cx, cy)) {
                        prefs.bgScaleMode = 0
                        onBackgroundSettingsChanged?.invoke()
                        invalidate(); performClick(); return true
                    }
                }
                scaleFitRect?.let {
                    if (it.contains(cx, cy)) {
                        prefs.bgScaleMode = 1
                        onBackgroundSettingsChanged?.invoke()
                        invalidate(); performClick(); return true
                    }
                }

                // 旋轉按鈕
                val rotDegs = intArrayOf(0, 90, 180, 270)
                for (i in 0..3) {
                    rotBtnRects[i]?.let {
                        if (it.contains(cx, cy)) {
                            prefs.bgRotation = rotDegs[i]
                            onBackgroundSettingsChanged?.invoke()
                            invalidate(); performClick(); return true
                        }
                    }
                }

                // 透明鍵盤 toggle
                transparentToggleRect?.let {
                    if (it.contains(cx, cy)) {
                        prefs.transparentKeyboard = !prefs.transparentKeyboard
                        onKeySettingsChanged?.invoke()
                        invalidate(); performClick(); return true
                    }
                }

                // 字體顏色
                val colorHit = colorRects.firstOrNull { it.first.contains(cx, cy) }
                if (colorHit != null) {
                    prefs.customKeyLabelColor = colorHit.second
                    onLabelColorChanged?.invoke(colorHit.second)
                    invalidate(); performClick(); return true
                }

                // 個別按鍵選擇
                val keyHit = perKeyRects.firstOrNull { it.first.contains(cx, cy) }
                if (keyHit != null) {
                    selectedKeyId = if (selectedKeyId == keyHit.second) null else keyHit.second
                    invalidate(); performClick(); return true
                }

                // 重置選中按鍵
                resetSelectedKeySizeRect?.let {
                    if (it.contains(cx, cy)) {
                        selectedKeyId?.let { keyId ->
                            prefs.resetPerKeySize(keyId)
                            onPerKeySizeChanged?.invoke()
                        }
                        invalidate(); performClick(); return true
                    }
                }

                // 重置全部
                resetAllKeySizeRect?.let {
                    if (it.contains(cx, cy)) {
                        prefs.resetAllPerKeySizes()
                        selectedKeyId = null
                        onPerKeySizeChanged?.invoke()
                        invalidate(); performClick(); return true
                    }
                }

                performClick(); return true
            }
        }
        return true
    }

    private fun handleSliderUpdate(id: SliderId, x: Float, track: RectF) {
        val pct = ((x - track.left) / track.width()).coerceIn(0f, 1f)
        when (id) {
            SliderId.OVERLAY -> {
                val alpha = (pct * 255).toInt()
                prefs.bgOverlayAlpha = alpha
                onOverlayAlphaChanged?.invoke(alpha)
            }
            SliderId.OFFSET_X -> {
                prefs.bgOffsetX = pct
                onBackgroundSettingsChanged?.invoke()
            }
            SliderId.OFFSET_Y -> {
                prefs.bgOffsetY = pct
                onBackgroundSettingsChanged?.invoke()
            }
            SliderId.KEY_SCALE -> {
                prefs.keyScalePercent = (pct * 60 + 70).toInt()
                onKeySettingsChanged?.invoke()
            }
            SliderId.KEY_OPACITY -> {
                prefs.keyOpacity = (pct * 255).toInt()
                onKeySettingsChanged?.invoke()
            }
            SliderId.TRANSPARENT_ALPHA -> {
                prefs.transparentKeyboardAlpha = (pct * 255).toInt()
                onKeySettingsChanged?.invoke()
            }
            SliderId.KB_HEIGHT -> {
                prefs.keyboardHeightRatio = 0.25f + pct * 0.30f
                onKeyboardHeightChanged?.invoke()
            }
            SliderId.PER_KEY_WIDTH -> {
                val sel = selectedKeyId ?: return
                val w = 0.5f + pct * 1.5f  // 0.5~2.0
                val h = prefs.getPerKeyHeight(sel)
                prefs.setPerKeySize(sel, w, h)
                onPerKeySizeChanged?.invoke()
            }
            SliderId.PER_KEY_HEIGHT -> {
                val sel = selectedKeyId ?: return
                val w = prefs.getPerKeyWidth(sel)
                val h = 0.5f + pct * 1.5f  // 0.5~2.0
                prefs.setPerKeySize(sel, w, h)
                onPerKeySizeChanged?.invoke()
            }
        }
        invalidate()
    }

    private fun clampScroll() {
        val maxScroll = (contentHeight - height).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(-maxScroll, 0f)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private var onDetachCallback: (() -> Unit)? = null

    fun addOnDetachListener(callback: () -> Unit) {
        onDetachCallback = callback
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDetachCallback?.invoke()
    }
}
