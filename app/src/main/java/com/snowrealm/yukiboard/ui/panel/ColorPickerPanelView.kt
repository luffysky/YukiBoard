package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 色碼選擇器面板 — 取色盤 + HEX/RGB 輸出 + 一鍵插入。
 *
 * 佈局：頂部色相列 + 中間 SV 面板 + 底部色碼文字 + 插入按鈕。
 */
class ColorPickerPanelView(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 將選取的色碼插入輸入框 */
    var onInsertColor: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density

    // HSV 狀態
    private var hue = 0f          // 0-360
    private var saturation = 1f   // 0-1
    private var brightness = 1f   // 0-1

    // 區域
    private val hueBarRect = RectF()
    private val svPanelRect = RectF()
    private val hexBtnRect = RectF()
    private val rgbBtnRect = RectF()
    private val hslBtnRect = RectF()

    private enum class DragTarget { NONE, HUE_BAR, SV_PANEL }
    private var dragTarget = DragTarget.NONE

    // 色碼格式
    private enum class ColorFormat { HEX, RGB, HSL }
    private var colorFormat = ColorFormat.HEX

    // 最近使用色
    private val recentColors = mutableListOf<Int>()
    private val maxRecent = 8

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // 漸層快取
    private var hueShader: Shader? = null
    private var svShader: Shader? = null
    private var lastSvHue = -1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        setMeasuredDimension(w, (230 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 8f * density
        val hueBarH = 24f * density
        val recentH = 24f * density
        val bottomH = 40f * density
        val svTop = pad + hueBarH + pad
        val svBottom = h - bottomH - pad - recentH - pad

        // 背景
        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── 色相列 ──
        hueBarRect.set(pad, pad, w - pad, pad + hueBarH)
        if (hueShader == null) {
            val colors = IntArray(13) { i -> Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)) }
            hueShader = LinearGradient(
                hueBarRect.left, 0f, hueBarRect.right, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
        }
        bgPaint.shader = hueShader
        canvas.drawRoundRect(hueBarRect, 4f * density, 4f * density, bgPaint)
        bgPaint.shader = null

        // 色相指標
        val hueX = hueBarRect.left + (hue / 360f) * hueBarRect.width()
        markerPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(hueX, hueBarRect.centerY(), 8f * density, markerPaint)
        markerPaint.color = 0xFF000000.toInt()
        canvas.drawCircle(hueX, hueBarRect.centerY(), 7f * density, markerPaint)

        // ── SV 面板 ──
        svPanelRect.set(pad, svTop, w - pad, svBottom)
        if (lastSvHue != hue) {
            val pureColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            val satShader = LinearGradient(
                svPanelRect.left, 0f, svPanelRect.right, 0f,
                0xFFFFFFFF.toInt(), pureColor, Shader.TileMode.CLAMP
            )
            val valShader = LinearGradient(
                0f, svPanelRect.top, 0f, svPanelRect.bottom,
                0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP
            )
            svShader = ComposeShader(satShader, valShader, PorterDuff.Mode.DARKEN)
            lastSvHue = hue
        }
        bgPaint.shader = svShader
        canvas.drawRoundRect(svPanelRect, 4f * density, 4f * density, bgPaint)
        bgPaint.shader = null

        // SV 指標
        val svX = svPanelRect.left + saturation * svPanelRect.width()
        val svY = svPanelRect.top + (1f - brightness) * svPanelRect.height()
        markerPaint.color = 0xFFFFFFFF.toInt()
        markerPaint.strokeWidth = 3f * density
        canvas.drawCircle(svX, svY, 8f * density, markerPaint)
        markerPaint.color = 0xFF000000.toInt()
        markerPaint.strokeWidth = 1.5f * density
        canvas.drawCircle(svX, svY, 8f * density, markerPaint)
        markerPaint.strokeWidth = 2f

        // ── 最近使用色列 ──
        val recentTop = svBottom + pad * 0.5f
        if (recentColors.isNotEmpty()) {
            val swatchSize = recentH
            val swatchGap = 4f * density
            var rx = pad
            recentColors.forEach { c ->
                bgPaint.color = c
                canvas.drawRoundRect(rx, recentTop, rx + swatchSize, recentTop + swatchSize, 4f * density, 4f * density, bgPaint)
                rx += swatchSize + swatchGap
            }
        }

        // ── 底部：色碼 + 格式切換 + 插入 ──
        val bottomTop = h - bottomH
        val selectedColor = currentColor()

        // 預覽色塊
        bgPaint.color = selectedColor
        val previewRect = RectF(pad, bottomTop + 4f * density, pad + 32f * density, h - 4f * density)
        canvas.drawRoundRect(previewRect, 4f * density, 4f * density, bgPaint)
        // 邊框
        markerPaint.color = 0x80FFFFFF.toInt()
        canvas.drawRoundRect(previewRect, 4f * density, 4f * density, markerPaint)

        // 色碼文字
        val colorText = formatColor(selectedColor)
        textPaint.color = theme.keyLabelColorInt
        textPaint.textSize = 14f * density
        canvas.drawText(colorText, w * 0.4f, bottomTop + bottomH * 0.65f, textPaint)

        // 格式切換按鈕
        val fmtW = 36f * density
        val fmtH = 26f * density
        val fmtY = bottomTop + (bottomH - fmtH) / 2f
        val fmtLabels = listOf("HEX" to hexBtnRect, "RGB" to rgbBtnRect, "HSL" to hslBtnRect)
        var fmtX = w * 0.6f
        fmtLabels.forEachIndexed { i, (label, rect) ->
            rect.set(fmtX, fmtY, fmtX + fmtW, fmtY + fmtH)
            val isActive = (i == 0 && colorFormat == ColorFormat.HEX) ||
                    (i == 1 && colorFormat == ColorFormat.RGB) ||
                    (i == 2 && colorFormat == ColorFormat.HSL)
            bgPaint.color = if (isActive) 0xFF4A90E2.toInt() else (theme.keyColorInt and 0x00FFFFFF) or (0x80 shl 24)
            canvas.drawRoundRect(rect, 4f * density, 4f * density, bgPaint)
            textPaint.textSize = 9f * density
            textPaint.color = if (isActive) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(label, rect.centerX(), rect.centerY() + 3f * density, textPaint)
            fmtX += fmtW + 3f * density
        }

        // 插入按鈕
        val insW = 36f * density
        val insRect = RectF(w - pad - insW, fmtY, w - pad, fmtY + fmtH)
        bgPaint.color = 0xFF4CAF50.toInt()
        canvas.drawRoundRect(insRect, 4f * density, 4f * density, bgPaint)
        textPaint.textSize = 13f * density
        textPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("↩", insRect.centerX(), insRect.centerY() + 4f * density, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = when {
                    hueBarRect.contains(x, y) -> DragTarget.HUE_BAR
                    svPanelRect.contains(x, y) -> DragTarget.SV_PANEL
                    else -> DragTarget.NONE
                }
                if (dragTarget != DragTarget.NONE) {
                    updateFromTouch(x, y)
                    return true
                }
                // 格式切換 / 插入
                if (hexBtnRect.contains(x, y)) { colorFormat = ColorFormat.HEX; invalidate(); return true }
                if (rgbBtnRect.contains(x, y)) { colorFormat = ColorFormat.RGB; invalidate(); return true }
                if (hslBtnRect.contains(x, y)) { colorFormat = ColorFormat.HSL; invalidate(); return true }
                // 最近使用色
                val recentTop = svPanelRect.bottom + 4f * density
                val swatchSize = 24f * density
                val swatchGap = 4f * density
                val pad = 8f * density
                if (y in recentTop..(recentTop + swatchSize)) {
                    recentColors.forEachIndexed { i, c ->
                        val rx = pad + i * (swatchSize + swatchGap)
                        if (x in rx..(rx + swatchSize)) {
                            val hsv = FloatArray(3)
                            Color.colorToHSV(c, hsv)
                            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                            lastSvHue = -1f
                            invalidate()
                            return true
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget != DragTarget.NONE) {
                    updateFromTouch(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragTarget != DragTarget.NONE) {
                    addToRecent(currentColor())
                    dragTarget = DragTarget.NONE
                    invalidate()
                    return true
                }
                // 插入按鈕
                val insW = 36f * density
                val pad = 8f * density
                val bottomH = 40f * density
                val fmtH = 26f * density
                val fmtY = height - bottomH + (bottomH - fmtH) / 2f
                val insRect = RectF(width - pad - insW, fmtY, width - pad, fmtY + fmtH)
                if (insRect.contains(x, y)) {
                    onInsertColor?.invoke(formatColor(currentColor()))
                    performClick()
                    return true
                }
                dragTarget = DragTarget.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                dragTarget = DragTarget.NONE
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(x: Float, y: Float) {
        when (dragTarget) {
            DragTarget.HUE_BAR -> {
                hue = ((x - hueBarRect.left) / hueBarRect.width()).coerceIn(0f, 1f) * 360f
                lastSvHue = -1f
            }
            DragTarget.SV_PANEL -> {
                saturation = ((x - svPanelRect.left) / svPanelRect.width()).coerceIn(0f, 1f)
                brightness = 1f - ((y - svPanelRect.top) / svPanelRect.height()).coerceIn(0f, 1f)
            }
            DragTarget.NONE -> {}
        }
        invalidate()
    }

    private fun currentColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    }

    private fun formatColor(color: Int): String {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return when (colorFormat) {
            ColorFormat.HEX -> "#%02X%02X%02X".format(r, g, b)
            ColorFormat.RGB -> "rgb($r, $g, $b)"
            ColorFormat.HSL -> {
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                "hsl(%.0f, %.0f%%, %.0f%%)".format(hsv[0], hsv[1] * 100, hsv[2] * 100)
            }
        }
    }

    private fun addToRecent(color: Int) {
        recentColors.remove(color)
        recentColors.add(0, color)
        if (recentColors.size > maxRecent) recentColors.removeAt(recentColors.lastIndex)
    }
}
