package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardBackgroundView
import com.snowrealm.yukiboard.ui.keyboard.KeyboardState
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 主題瀏覽面板 — 在鍵盤內直接預覽和切換主題、背景、字體顏色。
 *
 * 五區塊：主題列表 → 背景控制 → 背景調整 → 字體顏色 → 鍵盤預覽
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
    /** 比例/偏移/鍵盤參數變更回呼（通知 IME 即時套用） */
    var onBackgroundSettingsChanged: (() -> Unit)? = null

    var currentThemeId: String = "snow_dark"
        set(value) { field = value; invalidate() }

    var availableThemes: List<KeyboardTheme> = KeyboardTheme.all()
        set(value) { field = value; invalidate() }

    /** 背景預覽 bitmap（由外部載入後設定） */
    var previewBgBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    private val themeRects = mutableListOf<Pair<RectF, Int>>()
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

    // 滑桿定義
    private enum class SliderId { OVERLAY, OFFSET_X, OFFSET_Y }
    private data class SliderDef(val id: SliderId, var trackRect: RectF = RectF())
    private val sliders = listOf(
        SliderDef(SliderId.OVERLAY),
        SliderDef(SliderId.OFFSET_X),
        SliderDef(SliderId.OFFSET_Y)
    )
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

    /** 載入/重新載入背景預覽 bitmap */
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
        pickImgRect = null; pickVideoRect = null; clearBgRect = null
        scaleCropRect = null; scaleFitRect = null
        sliders.forEach { it.trackRect.setEmpty() }

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

        // ═══ 區塊 2: 背景控制（選圖/選影片/清除） ═══
        curY = drawSectionTitle(canvas, "鍵盤背景", curY)
        curY = drawBackgroundButtons(canvas, curY)
        curY += sectionGap

        // ═══ 區塊 3: 背景調整（遮罩、比例、XY） ═══
        curY = drawSectionTitle(canvas, "背景調整", curY)
        curY = drawBackgroundAdjust(canvas, curY)
        curY += sectionGap

        // ═══ 區塊 4: 字體顏色 ═══
        curY = drawSectionTitle(canvas, "按鍵字體顏色", curY)
        curY = drawColorPicker(canvas, curY)
        curY += sectionGap

        // ═══ 區塊 5: 鍵盤完整預覽 ═══
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

            // mini keys
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

            // candidate bar
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

    // ─── 背景按鈕（選圖片/選影片/清除） ───

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

    // ─── 背景調整（遮罩、比例、XY） ───

    private fun drawBackgroundAdjust(canvas: Canvas, startY: Float): Float {
        var y = startY
        val trackH = 6f * density
        val trackLeft = pad + 4 * density
        val trackRight = width - pad - 4 * density

        // 遮罩深度滑桿
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

        // X 偏移滑桿
        y = drawSliderRow(canvas, y, "水平位置 X", SliderId.OFFSET_X,
            prefs.bgOffsetX, "%d%%".format((prefs.bgOffsetX * 100).toInt()),
            trackLeft, trackRight, trackH)

        // Y 偏移滑桿
        y = drawSliderRow(canvas, y, "垂直位置 Y", SliderId.OFFSET_Y,
            prefs.bgOffsetY, "%d%%".format((prefs.bgOffsetY * 100).toInt()),
            trackLeft, trackRight, trackH)

        return y
    }

    /** 繪製一條滑桿列（標籤 + 軌道 + 手把），回傳底部 Y */
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

        // track bg
        sliderPaint.color = theme.keyColorInt
        sliderPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(trackRect, trackH / 2, trackH / 2, sliderPaint)

        // fill
        val fillRect = RectF(trackLeft, y, trackLeft + (trackRight - trackLeft) * pct, y + trackH)
        sliderPaint.color = theme.effectiveAccentColor(); sliderPaint.alpha = 180
        canvas.drawRoundRect(fillRect, trackH / 2, trackH / 2, sliderPaint)
        sliderPaint.alpha = 255

        // thumb
        val thumbX = trackLeft + (trackRight - trackLeft) * pct
        val thumbR = 8f * density
        sliderPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.color = theme.effectiveAccentColor()
        sliderPaint.style = Paint.Style.STROKE; sliderPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(thumbX, y + trackH / 2, thumbR, sliderPaint)
        sliderPaint.style = Paint.Style.FILL

        return y + trackH + 10 * density
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

    // ─── 鍵盤完整預覽 ───

    private fun drawKeyboardPreview(canvas: Canvas, startY: Float): Float {
        val pvW = width - pad * 2
        val pvH = 100f * density
        val pvRect = RectF(pad, startY, pad + pvW, startY + pvH)

        // 背景
        cardPaint.color = theme.backgroundColorInt
        canvas.drawRoundRect(pvRect, cornerR, cornerR, cardPaint)

        canvas.save()
        canvas.clipRect(pvRect)

        // 背景圖片
        val bmp = previewBgBitmap
        if (bmp != null && prefs.useCustomBackground) {
            previewBgMatrix.reset()
            if (prefs.bgScaleMode == 1) {
                previewBgMatrix.setScale(pvW / bmp.width, pvH / bmp.height)
                previewBgMatrix.postTranslate(pvRect.left, pvRect.top)
            } else {
                val scale = maxOf(pvW / bmp.width, pvH / bmp.height)
                val sw = bmp.width * scale; val sh = bmp.height * scale
                val maxDx = (sw - pvW).coerceAtLeast(0f)
                val maxDy = (sh - pvH).coerceAtLeast(0f)
                previewBgMatrix.setScale(scale, scale)
                previewBgMatrix.postTranslate(
                    pvRect.left - maxDx * prefs.bgOffsetX,
                    pvRect.top - maxDy * prefs.bgOffsetY
                )
            }
            canvas.drawBitmap(bmp, previewBgMatrix, null)

            // 遮罩
            previewKeyPaint.color = Color.argb(prefs.bgOverlayAlpha, 0, 0, 0)
            previewKeyPaint.style = Paint.Style.FILL
            canvas.drawRect(pvRect, previewKeyPaint)
        }

        // 繪製迷你鍵盤按鍵
        val rows = KeyboardState.defaultQwertyRows()
        val hasBg = bmp != null && prefs.useCustomBackground
        val keyAlpha = prefs.keyOpacity
        val rowH = pvH / rows.size
        val cr = theme.keyCornerRadiusDp * density * 0.5f
        val fontSize = 8f * density
        val totalUnits = rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() }.toFloat() }

        rows.forEachIndexed { rowIdx, row ->
            val top = pvRect.top + rowIdx * rowH
            var kx = pvRect.left
            val unitW = pvW / totalUnits
            val padH = 1.5f * density; val padV = 1.5f * density

            row.forEach { key ->
                val keyW = unitW * key.widthWeight
                val kr = RectF(kx + padH, top + padV, kx + keyW - padH, top + rowH - padV)

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

        // 邊框
        cardPaint.style = Paint.Style.STROKE; cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.candidateTextColorInt; cardPaint.alpha = 40
        canvas.drawRoundRect(pvRect, cornerR, cornerR, cardPaint)
        cardPaint.style = Paint.Style.FILL; cardPaint.alpha = 255

        return startY + pvH + pad
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

                // 檢查是否點中任何滑桿
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
                        onThemeSelected?.invoke(selected)
                        performClick()
                    }
                    return true
                }

                // 背景按鈕
                pickImgRect?.let { if (it.contains(cx, cy)) { onPickBackground?.invoke(); performClick(); return true } }
                pickVideoRect?.let { if (it.contains(cx, cy)) { onPickVideo?.invoke(); performClick(); return true } }
                clearBgRect?.let { if (it.contains(cx, cy)) { onClearBackground?.invoke(); performClick(); return true } }

                // 比例模式切換
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

                // 字體顏色
                val colorHit = colorRects.firstOrNull { it.first.contains(cx, cy) }
                if (colorHit != null) {
                    prefs.customKeyLabelColor = colorHit.second
                    onLabelColorChanged?.invoke(colorHit.second)
                    invalidate(); performClick(); return true
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
}
