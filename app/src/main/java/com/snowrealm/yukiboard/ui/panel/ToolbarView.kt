package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlin.math.abs

/**
 * 鍵盤上方工具列 — 現代化水平捲動面板按鈕列。
 *
 * 設計：
 * - 膠囊形按鈕，選中時填色 + 展開標籤
 * - 三組視覺分隔：AI 工具 | 內容面板 | 系統工具
 * - 水平滑動捲動（11 按鈕不擠）
 * - 按下回饋（縮放 + 高亮）
 */
class ToolbarView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val barHeight = (40 * density).toInt()

    // 繪製元素
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f * density
    }

    // 面板按鈕分組
    private val groups: List<List<PanelType>> = listOf(
        listOf(PanelType.CLAW, PanelType.VOICE, PanelType.TRANSLATE),        // AI 工具
        listOf(PanelType.EMOJI, PanelType.KAOMOJI, PanelType.STICKER, PanelType.GIF), // 內容
        listOf(PanelType.CLIPBOARD, PanelType.CALCULATOR, PanelType.PHRASE, PanelType.USER_DICT, PanelType.DECRYPT, PanelType.REMOTE, PanelType.THEME, PanelType.SETTINGS), // 系統
    )
    private val flatPanels = groups.flatten()

    // 按鈕幾何
    private val btnPadH = 8f * density    // 按鈕水平內邊距
    private val btnGap = 4f * density     // 按鈕間距
    private val groupGap = 10f * density  // 分組間距
    private val pillRadius = 14f * density
    private val edgePad = 6f * density    // 左右邊緣留白

    // 按鈕區域快取
    private data class BtnRect(val rect: RectF, val panel: PanelType, val isActive: Boolean)
    private val btnRects = mutableListOf<BtnRect>()

    // 捲動狀態
    private var scrollX = 0f
    private var maxScrollX = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownScrollX = 0f
    private var isDragging = false
    private var touchDownTime = 0L
    private val scrollThreshold = 8f * density

    // 按壓狀態
    private var pressedPanel: PanelType? = null

    // 選中動畫
    private var activeAnimProgress = mutableMapOf<PanelType, Float>() // 0→1 展開

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var activePanel: PanelType? = null
        set(value) {
            val old = field
            field = value
            // 觸發展開/收起動畫
            if (old != null && old != value) animateCollapse(old)
            if (value != null) animateExpand(value)
            invalidate()
        }

    var onPanelSelected: ((PanelType) -> Unit)? = null

    private var isTransparent = false
    fun setTransparent(transparent: Boolean) {
        isTransparent = transparent
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, barHeight)
    }

    override fun onDraw(canvas: Canvas) {
        // 不呼叫 super.onDraw — 硬體加速 + clipChildren=false 下會穿透

        // 背景
        if (isTransparent) {
            bgPaint.color = 0x88000000.toInt()  // 半透明黑底，確保圖示可見
        } else {
            bgPaint.color = theme.effectiveToolbarColor()
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 計算按鈕佈局（含捲動偏移）
        btnRects.clear()
        var x = edgePad - scrollX

        val accentColor = theme.effectiveToolbarActiveColor()
        val iconColor = theme.effectiveToolbarIconColor()

        groups.forEachIndexed { gi, group ->
            if (gi > 0) {
                // 畫分組分隔點
                dividerPaint.color = (iconColor and 0x00FFFFFF) or 0x30000000
                val dotY = height / 2f
                canvas.drawCircle(x + groupGap / 2f, dotY, 1.5f * density, dividerPaint)
                x += groupGap
            }

            group.forEach { panel ->
                val isActive = panel == activePanel
                val isPressed = panel == pressedPanel
                val animP = activeAnimProgress[panel] ?: if (isActive) 1f else 0f

                // 計算按鈕寬度：選中時展開（含標籤文字）
                val iconWidth = iconPaint.measureText(panel.icon) + btnPadH * 2
                val labelWidth = labelPaint.measureText(panel.displayName) + 4f * density
                val expandedWidth = iconWidth + labelWidth
                val btnWidth = iconWidth + (expandedWidth - iconWidth) * animP

                val top = 4f * density
                val bottom = height - 4f * density
                val rect = RectF(x, top, x + btnWidth, bottom)
                btnRects.add(BtnRect(rect, panel, isActive))

                // 繪製膠囊背景
                if (isActive || animP > 0.01f) {
                    pillPaint.color = accentColor
                    pillPaint.alpha = (animP * 40).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(rect, pillRadius, pillRadius, pillPaint)

                    // 底部指示條
                    pillPaint.alpha = (animP * 200).toInt().coerceIn(0, 255)
                    val indicatorH = 2.5f * density
                    val indicatorW = btnWidth * 0.6f
                    val indicatorLeft = rect.centerX() - indicatorW / 2f
                    canvas.drawRoundRect(
                        indicatorLeft, bottom - indicatorH,
                        indicatorLeft + indicatorW, bottom,
                        indicatorH, indicatorH, pillPaint
                    )
                }

                // 按壓回饋
                if (isPressed) {
                    pillPaint.color = (iconColor and 0x00FFFFFF) or 0x18000000
                    canvas.drawRoundRect(rect, pillRadius, pillRadius, pillPaint)
                }

                // 圖示
                iconPaint.color = if (isActive) accentColor else iconColor
                val iconCenterX = rect.left + btnPadH + iconPaint.measureText(panel.icon) / 2f
                val iconY = rect.centerY() + iconPaint.textSize * 0.33f
                canvas.drawText(panel.icon, iconCenterX, iconY, iconPaint)

                // 選中時顯示標籤
                if (animP > 0.1f) {
                    labelPaint.color = accentColor
                    labelPaint.alpha = (animP * 220).toInt().coerceIn(0, 255)
                    val labelCenterX = rect.right - labelWidth / 2f
                    val labelY = rect.centerY() + labelPaint.textSize * 0.33f
                    canvas.drawText(panel.displayName, labelCenterX, labelY, labelPaint)
                }

                x += btnWidth + btnGap
            }
        }

        // 計算最大捲動範圍
        val totalWidth = x + scrollX + edgePad
        maxScrollX = (totalWidth - width).coerceAtLeast(0f)
        scrollX = scrollX.coerceIn(0f, maxScrollX)

        // 左右漸層遮罩（提示可捲動）
        if (maxScrollX > 0f) {
            val fadeWidth = 16f * density
            if (scrollX > 2f) {
                val fadeGrad = LinearGradient(
                    0f, 0f, fadeWidth, 0f,
                    theme.effectiveToolbarColor(),
                    theme.effectiveToolbarColor() and 0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = fadeGrad
                canvas.drawRect(0f, 0f, fadeWidth, height.toFloat(), bgPaint)
                bgPaint.shader = null
            }
            if (scrollX < maxScrollX - 2f) {
                val fadeGrad = LinearGradient(
                    width - fadeWidth, 0f, width.toFloat(), 0f,
                    theme.effectiveToolbarColor() and 0x00FFFFFF,
                    theme.effectiveToolbarColor(),
                    Shader.TileMode.CLAMP
                )
                bgPaint.shader = fadeGrad
                canvas.drawRect(width - fadeWidth, 0f, width.toFloat(), height.toFloat(), bgPaint)
                bgPaint.shader = null
            }
        }

        // 頂部細線（與鍵盤區分）
        dividerPaint.color = (iconColor and 0x00FFFFFF) or 0x15000000
        canvas.drawLine(0f, height - 0.5f * density, width.toFloat(), height - 0.5f * density, dividerPaint)
    }

    // =========================================================================
    // 觸控處理（捲動 + 點擊）
    // =========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownScrollX = scrollX
                touchDownTime = SystemClock.uptimeMillis()
                isDragging = false

                // 判斷按壓的按鈕
                pressedPanel = btnRects.firstOrNull { it.rect.contains(event.x + scrollX - (scrollX - touchDownScrollX), event.y) }?.panel
                    ?: btnRects.firstOrNull { it.rect.contains(event.x, event.y) }?.panel
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                if (!isDragging && abs(dx) > scrollThreshold) {
                    isDragging = true
                    pressedPanel = null
                    invalidate()
                }
                if (isDragging && maxScrollX > 0f) {
                    scrollX = (touchDownScrollX - dx).coerceIn(0f, maxScrollX)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = SystemClock.uptimeMillis() - touchDownTime
                val dx = abs(event.x - touchDownX)
                pressedPanel = null

                if (!isDragging && dx < scrollThreshold && elapsed < 400) {
                    // 點擊 → 找命中的按鈕
                    val hit = btnRects.firstOrNull { it.rect.contains(event.x, event.y) }
                    if (hit != null) {
                        val panel = hit.panel
                        if (activePanel == panel) {
                            activePanel = null
                        } else {
                            activePanel = panel
                            // 自動捲動讓選中按鈕可見
                            scrollToMakeVisible(hit.rect)
                        }
                        onPanelSelected?.invoke(panel)
                        performClick()
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedPanel = null
                isDragging = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun scrollToMakeVisible(rect: RectF) {
        val visibleLeft = scrollX
        val visibleRight = scrollX + width
        if (rect.left < visibleLeft + edgePad) {
            scrollX = (rect.left - edgePad * 2).coerceAtLeast(0f)
        } else if (rect.right > visibleRight - edgePad) {
            scrollX = (rect.right + edgePad * 2 - width).coerceAtMost(maxScrollX)
        }
    }

    // =========================================================================
    // 展開/收起動畫
    // =========================================================================

    private fun animateExpand(panel: PanelType) {
        val startTime = SystemClock.uptimeMillis()
        val duration = 200L
        post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - startTime
                val t = (elapsed.toFloat() / duration).coerceAtMost(1f)
                val ease = 1f - (1f - t) * (1f - t) // easeOut
                activeAnimProgress[panel] = ease
                invalidate()
                if (t < 1f) postDelayed(this, 16)
            }
        })
    }

    private fun animateCollapse(panel: PanelType) {
        val startTime = SystemClock.uptimeMillis()
        val duration = 150L
        val startVal = activeAnimProgress[panel] ?: 1f
        post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - startTime
                val t = (elapsed.toFloat() / duration).coerceAtMost(1f)
                activeAnimProgress[panel] = startVal * (1f - t)
                invalidate()
                if (t < 1f) postDelayed(this, 16)
                else activeAnimProgress.remove(panel)
            }
        })
    }
}
