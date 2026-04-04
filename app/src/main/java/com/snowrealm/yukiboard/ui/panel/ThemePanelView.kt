package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 主題瀏覽面板 — 在鍵盤內直接預覽和切換主題、背景、字體顏色。
 *
 * 三區塊：主題列表 → 背景控制 → 字體顏色
 * 可上下捲動。
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

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onThemeSelected: ((KeyboardTheme) -> Unit)? = null

    /** 選圖片回呼（由 IME Service 處理 Intent） */
    var onPickBackground: (() -> Unit)? = null
    /** 選影片回呼 */
    var onPickVideo: (() -> Unit)? = null
    /** 清除背景回呼 */
    var onClearBackground: (() -> Unit)? = null
    /** 遮罩深度變更回呼 */
    var onOverlayAlphaChanged: ((Int) -> Unit)? = null
    /** 字體顏色變更回呼 */
    var onLabelColorChanged: ((Int) -> Unit)? = null

    var currentThemeId: String = "snow_dark"
        set(value) { field = value; invalidate() }

    var availableThemes: List<KeyboardTheme> = KeyboardTheme.all()
        set(value) { field = value; invalidate() }

    private val themeRects = mutableListOf<Pair<RectF, Int>>()
    private val cornerR = 10f * density
    private val pad = 8f * density
    private val sectionGap = 12f * density

    // 背景區按鈕
    private var pickImgRect: RectF? = null
    private var pickVideoRect: RectF? = null
    private var clearBgRect: RectF? = null

    // 遮罩滑桿
    private var sliderTrackRect: RectF? = null
    private var isDraggingSlider = false

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
    private val colorRects = mutableListOf<Pair<RectF, Int>>() // rect to color value

    // 捲動
    private var scrollY = 0f
    private var contentHeight = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        themeRects.clear()
        colorRects.clear()
        pickImgRect = null
        pickVideoRect = null
        clearBgRect = null
        sliderTrackRect = null

        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(0f, scrollY)

        var curY = pad

        // ═══ 區塊 1: 主題列表 ═══
        curY = drawSectionTitle(canvas, "主題", curY)
        curY = drawThemeCards(canvas, curY)

        curY += sectionGap

        // ═══ 區塊 2: 背景控制 ═══
        curY = drawSectionTitle(canvas, "鍵盤背景", curY)
        curY = drawBackgroundControls(canvas, curY)

        curY += sectionGap

        // ═══ 區塊 3: 字體顏色 ═══
        curY = drawSectionTitle(canvas, "按鍵字體顏色", curY)
        curY = drawColorPicker(canvas, curY)

        curY += pad
        contentHeight = curY

        canvas.restore()
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        titlePaint.color = theme.candidateTextColorInt
        titlePaint.alpha = 200
        canvas.drawText(title, pad + 4 * density, y + titlePaint.textSize, titlePaint)
        titlePaint.alpha = 255
        return y + titlePaint.textSize + 6 * density
    }

    // ─── 主題卡片 ───

    private fun drawThemeCards(canvas: Canvas, startY: Float): Float {
        val cardW = 120f * density
        val cardH = 72f * density
        val cols = ((width - pad) / (cardW + pad)).toInt().coerceAtLeast(2)
        val actualCardW = (width - pad * (cols + 1)) / cols

        var y = startY
        availableThemes.forEachIndexed { i, t ->
            val col = i % cols
            val row = i / cols
            val x = pad + col * (actualCardW + pad)
            val cy = startY + row * (cardH + pad)

            val rect = RectF(x, cy, x + actualCardW, cy + cardH)
            themeRects.add(rect to i)

            // 卡片背景
            cardPaint.color = t.backgroundColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 選中邊框
            if (t.id == currentThemeId) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * density
                    color = theme.candidateSelectedColorInt
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, borderPaint)
            }

            // 迷你鍵盤預覽
            val keyY = rect.top + 10 * density
            val keyH = 16f * density
            val keyW = 24f * density
            val keyPad = 3f * density
            val keyStartX = rect.left + 10 * density

            for (k in 0 until 3) {
                previewKeyPaint.color = if (k == 2) t.specialKeyColorInt else t.keyColorInt
                val kr = RectF(
                    keyStartX + k * (keyW + keyPad), keyY,
                    keyStartX + k * (keyW + keyPad) + keyW, keyY + keyH
                )
                canvas.drawRoundRect(kr, 3f * density, 3f * density, previewKeyPaint)
                textPaint.color = t.keyLabelColorInt
                textPaint.textSize = 8f * density
                canvas.drawText(listOf("Q", "W", "⏎")[k], kr.centerX(), kr.centerY() + 3f * density, textPaint)
            }

            // 候選列預覽
            val barY = keyY + keyH + 4 * density
            previewKeyPaint.color = t.candidateBarColorInt
            canvas.drawRoundRect(
                RectF(rect.left + 8 * density, barY, rect.right - 8 * density, barY + 8 * density),
                2f * density, 2f * density, previewKeyPaint
            )

            // 主題名稱
            textPaint.textSize = 10f * density
            textPaint.color = t.keyLabelColorInt
            canvas.drawText(t.displayName, rect.centerX(), rect.bottom - 5 * density, textPaint)
            textPaint.textSize = 12f * density

            // 選中勾號
            if (t.id == currentThemeId) {
                checkPaint.color = theme.candidateSelectedColorInt
                canvas.drawText("✓", rect.right - 12 * density, rect.top + 14 * density, checkPaint)
            }

            y = cy + cardH + pad
        }

        return y
    }

    // ─── 背景控制 ───

    private fun drawBackgroundControls(canvas: Canvas, startY: Float): Float {
        var y = startY
        val btnH = 30f * density
        val btnW = (width - pad * 4) / 3f
        val btnR = 6f * density

        // 選圖片
        val r1 = RectF(pad, y, pad + btnW, y + btnH)
        pickImgRect = r1
        drawButton(canvas, r1, "選圖片", btnR, theme.keyColorInt)

        // 選影片
        val r2 = RectF(pad * 2 + btnW, y, pad * 2 + btnW * 2, y + btnH)
        pickVideoRect = r2
        drawButton(canvas, r2, "選影片", btnR, theme.keyColorInt)

        // 清除
        val r3 = RectF(pad * 3 + btnW * 2, y, pad * 3 + btnW * 3, y + btnH)
        clearBgRect = r3
        drawButton(canvas, r3, "清除", btnR, 0xFF3A2020.toInt())

        y += btnH + 10 * density

        // 遮罩深度滑桿
        val alpha = prefs.bgOverlayAlpha
        val pct = alpha / 255f
        val labelText = "遮罩深度: %d%%".format((alpha * 100) / 255)
        textPaint.textSize = 11f * density
        textPaint.color = theme.candidateTextColorInt
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(labelText, pad + 4 * density, y + textPaint.textSize, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        y += textPaint.textSize + 6 * density

        val trackH = 6f * density
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density
        val trackRect = RectF(trackLeft, y, trackRight, y + trackH)
        sliderTrackRect = trackRect

        // 軌道背景
        sliderPaint.color = theme.keyColorInt
        canvas.drawRoundRect(trackRect, trackH / 2, trackH / 2, sliderPaint)

        // 填充部分
        val fillRect = RectF(trackLeft, y, trackLeft + (trackRight - trackLeft) * pct, y + trackH)
        sliderPaint.color = theme.effectiveAccentColor()
        sliderPaint.alpha = 180
        canvas.drawRoundRect(fillRect, trackH / 2, trackH / 2, sliderPaint)
        sliderPaint.alpha = 255

        // 滑桿手把
        val thumbX = trackLeft + (trackRight - trackLeft) * pct
        val thumbR = 8f * density
        sliderPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.color = theme.effectiveAccentColor()
        sliderPaint.style = Paint.Style.STROKE
        sliderPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.style = Paint.Style.FILL

        y += trackH + 14 * density

        return y
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, cornerR: Float, bgColor: Int) {
        cardPaint.color = bgColor
        canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

        // 邊框
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.candidateTextColorInt
        cardPaint.alpha = 40
        canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)
        cardPaint.style = Paint.Style.FILL
        cardPaint.alpha = 255

        btnTextPaint.color = theme.candidateTextColorInt
        canvas.drawText(label, rect.centerX(), rect.centerY() + btnTextPaint.textSize / 3f, btnTextPaint)
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

            // 圓形色塊
            if (entry.color == 0) {
                // 跟隨主題：彩虹漸層
                val gradient = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(
                        0xFFFF6B6B.toInt(), 0xFFFFD700.toInt(), 0xFF4ADE80.toInt(),
                        0xFF7FD8FF.toInt(), 0xFFA78BFA.toInt()
                    ),
                    null, Shader.TileMode.CLAMP
                )
                cardPaint.shader = gradient
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
                cardPaint.shader = null
            } else {
                cardPaint.color = entry.color
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
            }

            // 深色邊框（白色和跟隨主題需要）
            if (entry.color == 0xFFFFFFFF.toInt() || entry.color == 0 || entry.color == 0xFF000000.toInt()) {
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = 1f * density
                cardPaint.color = 0xFF555555.toInt()
                canvas.drawCircle(x, y, dotR - 2 * density, cardPaint)
                cardPaint.style = Paint.Style.FILL
            }

            // 選中框
            if (entry.color == currentColor) {
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = 2.5f * density
                cardPaint.color = theme.candidateSelectedColorInt
                canvas.drawCircle(x, y, dotR, cardPaint)
                cardPaint.style = Paint.Style.FILL
            }

            // 色名
            textPaint.textSize = 8f * density
            textPaint.color = theme.candidateTextColorInt
            textPaint.alpha = 160
            canvas.drawText(entry.label, x, y + dotR + 10 * density, textPaint)
            textPaint.alpha = 255
            textPaint.textSize = 12f * density

            x += dotR * 2 + gap
        }

        return startY + dotR * 2 + 18 * density
    }

    // ─── 觸控 ───

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 將螢幕座標轉為內容座標（考慮捲動）
        val cx = event.x
        val cy = event.y - scrollY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false

                // 滑桿拖曳開始
                sliderTrackRect?.let { track ->
                    val thumbY = track.centerY()
                    if (cy > thumbY - 20 * density && cy < thumbY + 20 * density
                        && cx > track.left - 10 * density && cx < track.right + 10 * density) {
                        isDraggingSlider = true
                        updateSlider(cx)
                        return true
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingSlider) {
                    updateSlider(cx)
                    return true
                }
                val dy = event.y - lastTouchY
                if (!isDragging && Math.abs(dy) > 8 * density) {
                    isDragging = true
                }
                if (isDragging) {
                    scrollY += dy
                    clampScroll()
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingSlider) {
                    isDraggingSlider = false
                    return true
                }

                if (isDragging) {
                    isDragging = false
                    return true
                }

                // 主題卡片
                val themeHit = themeRects.firstOrNull { it.first.contains(cx, cy) }
                if (themeHit != null) {
                    val selected = availableThemes.getOrNull(themeHit.second)
                    if (selected != null) {
                        currentThemeId = selected.id
                        prefs.useCustomBackground = false
                        onThemeSelected?.invoke(selected)
                        performClick()
                    }
                    return true
                }

                // 選圖片
                pickImgRect?.let { if (it.contains(cx, cy)) { onPickBackground?.invoke(); performClick(); return true } }
                // 選影片
                pickVideoRect?.let { if (it.contains(cx, cy)) { onPickVideo?.invoke(); performClick(); return true } }
                // 清除背景
                clearBgRect?.let { if (it.contains(cx, cy)) { onClearBackground?.invoke(); performClick(); return true } }

                // 字體顏色
                val colorHit = colorRects.firstOrNull { it.first.contains(cx, cy) }
                if (colorHit != null) {
                    prefs.customKeyLabelColor = colorHit.second
                    onLabelColorChanged?.invoke(colorHit.second)
                    invalidate()
                    performClick()
                    return true
                }

                performClick()
                return true
            }
        }
        return true
    }

    private fun updateSlider(x: Float) {
        val track = sliderTrackRect ?: return
        val pct = ((x - track.left) / track.width()).coerceIn(0f, 1f)
        val alpha = (pct * 255).toInt()
        prefs.bgOverlayAlpha = alpha
        onOverlayAlphaChanged?.invoke(alpha)
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
}
