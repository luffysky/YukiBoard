package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.input.handwriting.HandwritingEngine
import com.snowrealm.yukiboard.input.handwriting.StrokeCollector
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 手寫辨識面板 — Canvas 手寫 + 候選列 + 語言切換。
 *
 * 佈局（由上至下）：
 * ┌────────────────────────────────────────────┐
 * │ [中] [英] [日] [混合]     [清除] [⌫] [↩]  │  ← 控制列
 * ├────────────────────────────────────────────┤
 * │ 候選: [你] [休] [体] [妳] [您]             │  ← 候選列
 * ├────────────────────────────────────────────┤
 * │         ✍ 手寫區域 (Canvas)                │
 * └────────────────────────────────────────────┘
 */
class HandwritingPanelView(
    context: Context,
    private val engine: HandwritingEngine
) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 選中候選字後回呼（commitText） */
    var onCandidateSelected: ((String) -> Unit)? = null

    /** 退格回呼 */
    var onBackspace: (() -> Unit)? = null

    /** 確認/換行回呼 */
    var onConfirm: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    // ── 佈局區域 ──
    private val controlBarRect = RectF()
    private val candidateBarRect = RectF()
    private val writingAreaRect = RectF()

    // 控制列按鈕
    private data class BtnInfo(val rect: RectF = RectF(), val label: String, val tag: String)
    private val langButtons = mutableListOf<BtnInfo>()
    private val actionButtons = mutableListOf<BtnInfo>()

    // ── 候選字 ──
    private var candidates = listOf<String>()
    private val candidateRects = mutableListOf<RectF>()

    // ── 筆跡 ──
    private var strokeCollector = StrokeCollector()
    private val drawPaths = mutableListOf<Path>()
    private var currentPath: Path? = null

    // ── 自動辨識計時 ──
    private val autoRecognizeDelay = 1200L  // 1.2s 無筆畫後自動辨識
    private val autoRecognizeRunnable = Runnable { doRecognize() }

    // ── 繪製 Paint ──
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // ── 語言選擇 ──
    private data class LangOption(val tag: String, val label: String)
    private val langOptions = listOf(
        LangOption("zh-Hani-TW", "中"),
        LangOption("en-US", "英"),
        LangOption("ja", "日"),
        LangOption("mixed", "混合")
    )
    private var selectedLangIndex = 0

    // ── 按壓狀態 ──
    private var pressedBtn: String? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        val h = (260 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRegions(w.toFloat(), h.toFloat())
    }

    private fun layoutRegions(w: Float, h: Float) {
        val pad = 6 * density
        val controlH = 38 * density
        val candidateH = 36 * density

        // 控制列
        controlBarRect.set(0f, 0f, w, controlH)

        // 候選列
        candidateBarRect.set(0f, controlH, w, controlH + candidateH)

        // 手寫區域
        writingAreaRect.set(pad, controlH + candidateH + pad, w - pad, h - pad)

        // 語言按鈕（左側）
        langButtons.clear()
        val btnW = 44 * density
        val btnH = 30 * density
        val btnY = (controlH - btnH) / 2f
        langOptions.forEachIndexed { i, lang ->
            val x = pad + i * (btnW + 4 * density)
            langButtons.add(BtnInfo(RectF(x, btnY, x + btnW, btnY + btnH), lang.label, lang.tag))
        }

        // 操作按鈕（右側）
        val actions = listOf("清除" to "clear", "⌫" to "backspace", "↩" to "confirm")
        actionButtons.clear()
        val aBtnW = 44 * density
        actions.forEachIndexed { i, (label, tag) ->
            val x = w - pad - (actions.size - i) * (aBtnW + 4 * density)
            actionButtons.add(BtnInfo(RectF(x, btnY, x + aBtnW, btnY + btnH), label, tag))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()

        // ── 背景 ──
        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, w, height.toFloat(), bgPaint)

        // ── 控制列 ──
        drawControlBar(canvas)

        // ── 候選列 ──
        drawCandidateBar(canvas)

        // ── 手寫區域 ──
        drawWritingArea(canvas)
    }

    private fun drawControlBar(canvas: Canvas) {
        // 背景
        bgPaint.color = adjustAlpha(theme.keyColorInt, 180)
        canvas.drawRect(controlBarRect, bgPaint)

        val textSize = 14 * density
        textPaint.textSize = textSize
        val fm = textPaint.fontMetrics

        // 語言按鈕
        langButtons.forEachIndexed { i, btn ->
            val isSelected = i == selectedLangIndex
            val isPressed = pressedBtn == btn.tag

            btnPaint.color = when {
                isPressed -> 0xFF3A7BD5.toInt()
                isSelected -> 0xFF4A90E2.toInt()
                else -> adjustAlpha(theme.keyColorInt, 120)
            }
            canvas.drawRoundRect(btn.rect, 6 * density, 6 * density, btnPaint)

            textPaint.color = if (isSelected || isPressed) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            val cx = btn.rect.centerX()
            val cy = btn.rect.centerY() - (fm.top + fm.bottom) / 2f
            canvas.drawText(btn.label, cx, cy, textPaint)
        }

        // 操作按鈕
        actionButtons.forEach { btn ->
            val isPressed = pressedBtn == btn.tag
            btnPaint.color = when {
                isPressed -> 0xFF3A7BD5.toInt()
                btn.tag == "confirm" -> 0xFF4CAF50.toInt()
                else -> adjustAlpha(theme.keyColorInt, 120)
            }
            canvas.drawRoundRect(btn.rect, 6 * density, 6 * density, btnPaint)

            textPaint.color = if (btn.tag == "confirm" || isPressed) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            val cx = btn.rect.centerX()
            val cy = btn.rect.centerY() - (fm.top + fm.bottom) / 2f
            canvas.drawText(btn.label, cx, cy, textPaint)
        }
    }

    private fun drawCandidateBar(canvas: Canvas) {
        // 背景
        bgPaint.color = adjustAlpha(theme.backgroundColorInt, 220)
        canvas.drawRect(candidateBarRect, bgPaint)

        // 底部分隔線
        gridPaint.color = adjustAlpha(theme.keyLabelColorInt, 40)
        canvas.drawLine(candidateBarRect.left, candidateBarRect.bottom,
            candidateBarRect.right, candidateBarRect.bottom, gridPaint)

        if (candidates.isEmpty()) {
            // 提示文字
            textPaint.textSize = 13 * density
            textPaint.color = adjustAlpha(theme.keyLabelColorInt, 100)
            val cx = candidateBarRect.centerX()
            val cy = candidateBarRect.centerY() - (textPaint.fontMetrics.top + textPaint.fontMetrics.bottom) / 2f
            canvas.drawText("在下方區域手寫...", cx, cy, textPaint)
            candidateRects.clear()
            return
        }

        // 候選字
        candidateRects.clear()
        textPaint.textSize = 16 * density
        textPaint.color = theme.keyLabelColorInt
        val fm = textPaint.fontMetrics
        val pad = 12 * density
        var x = candidateBarRect.left + pad

        candidates.forEach { text ->
            val tw = textPaint.measureText(text)
            val btnW = tw + 20 * density
            val rect = RectF(x, candidateBarRect.top + 3 * density, x + btnW, candidateBarRect.bottom - 3 * density)
            candidateRects.add(rect)

            // 背景
            btnPaint.color = adjustAlpha(theme.keyColorInt, 160)
            canvas.drawRoundRect(rect, 4 * density, 4 * density, btnPaint)

            // 文字
            val cy = rect.centerY() - (fm.top + fm.bottom) / 2f
            canvas.drawText(text, rect.centerX(), cy, textPaint)

            x += btnW + 6 * density
            if (x > candidateBarRect.right - pad) return@forEach
        }
    }

    private fun drawWritingArea(canvas: Canvas) {
        // 手寫區域背景
        bgPaint.color = adjustAlpha(theme.keyColorInt, 60)
        canvas.drawRoundRect(writingAreaRect, 8 * density, 8 * density, bgPaint)

        // 十字參考線
        gridPaint.color = adjustAlpha(theme.keyLabelColorInt, 25)
        gridPaint.strokeWidth = 1f * density
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
        val cx = writingAreaRect.centerX()
        val cy = writingAreaRect.centerY()
        canvas.drawLine(cx, writingAreaRect.top + 8 * density, cx, writingAreaRect.bottom - 8 * density, gridPaint)
        canvas.drawLine(writingAreaRect.left + 8 * density, cy, writingAreaRect.right - 8 * density, cy, gridPaint)
        gridPaint.pathEffect = null

        // 已完成筆畫
        strokePaint.color = theme.keyLabelColorInt
        strokePaint.strokeWidth = 4 * density
        drawPaths.forEach { canvas.drawPath(it, strokePaint) }

        // 當前筆畫
        currentPath?.let { canvas.drawPath(it, strokePaint) }

        // 無筆畫時的提示
        if (drawPaths.isEmpty() && currentPath == null) {
            textPaint.textSize = 20 * density
            textPaint.color = adjustAlpha(theme.keyLabelColorInt, 50)
            canvas.drawText("✍", cx, cy + 8 * density, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 控制列按鈕
                langButtons.forEach { btn ->
                    if (btn.rect.contains(x, y)) {
                        pressedBtn = btn.tag
                        invalidate()
                        return true
                    }
                }
                actionButtons.forEach { btn ->
                    if (btn.rect.contains(x, y)) {
                        pressedBtn = btn.tag
                        invalidate()
                        return true
                    }
                }

                // 候選列
                candidateRects.forEachIndexed { i, rect ->
                    if (rect.contains(x, y) && i < candidates.size) {
                        onCandidateSelected?.invoke(candidates[i])
                        clearWritingArea()
                        return true
                    }
                }

                // 手寫區域
                if (writingAreaRect.contains(x, y)) {
                    handler.removeCallbacks(autoRecognizeRunnable)
                    strokeCollector.startStroke(event)
                    currentPath = Path().apply { moveTo(x, y) }
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentPath != null && writingAreaRect.contains(x, y)) {
                    strokeCollector.addPoint(event)
                    currentPath?.lineTo(x, y)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                // 按鈕處理
                if (pressedBtn != null) {
                    handleButtonUp(pressedBtn!!, x, y)
                    pressedBtn = null
                    invalidate()
                    return true
                }

                // 完成筆畫
                if (currentPath != null) {
                    strokeCollector.finishStroke(event)
                    drawPaths.add(currentPath!!)
                    currentPath = null
                    invalidate()

                    // 啟動自動辨識計時
                    handler.removeCallbacks(autoRecognizeRunnable)
                    handler.postDelayed(autoRecognizeRunnable, autoRecognizeDelay)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedBtn = null
                currentPath = null
                invalidate()
            }
        }
        return true
    }

    private fun handleButtonUp(tag: String, x: Float, y: Float) {
        // 語言切換
        langButtons.forEachIndexed { i, btn ->
            if (btn.tag == tag && btn.rect.contains(x, y)) {
                selectedLangIndex = i
                applyLanguageSelection()
                return
            }
        }

        // 操作按鈕
        actionButtons.forEach { btn ->
            if (btn.tag == tag && btn.rect.contains(x, y)) {
                when (tag) {
                    "clear" -> clearWritingArea()
                    "backspace" -> onBackspace?.invoke()
                    "confirm" -> {
                        // 若有候選字，先送出第一個
                        if (candidates.isNotEmpty()) {
                            onCandidateSelected?.invoke(candidates[0])
                        }
                        clearWritingArea()
                        onConfirm?.invoke()
                    }
                }
                return
            }
        }
    }

    private fun applyLanguageSelection() {
        val option = langOptions[selectedLangIndex]
        if (option.tag == "mixed") {
            // 混合模式：啟用繁中 + 英文
            engine.setLanguages(listOf("zh-Hani-TW", "en-US"))
        } else {
            engine.setLanguages(listOf(option.tag))
        }
        invalidate()
    }

    private fun doRecognize() {
        if (!strokeCollector.hasStrokes) return

        val ink = strokeCollector.buildInk()
        engine.recognize(ink) { results ->
            handler.post {
                candidates = results
                invalidate()
            }
        }
    }

    /** 手動觸發辨識 */
    fun triggerRecognize() {
        handler.removeCallbacks(autoRecognizeRunnable)
        doRecognize()
    }

    private fun clearWritingArea() {
        handler.removeCallbacks(autoRecognizeRunnable)
        drawPaths.clear()
        currentPath = null
        candidates = emptyList()
        candidateRects.clear()
        strokeCollector = strokeCollector.newCollector()
        invalidate()
    }

    /** 確保預設模型已下載 */
    fun ensureReady(callback: (Boolean) -> Unit) {
        engine.ensureDefaultModel(callback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(autoRecognizeRunnable)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
