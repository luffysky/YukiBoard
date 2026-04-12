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
 * 手寫辨識面板 — Canvas 手寫 + 候選列 + 語言切換 + 模型管理。
 *
 * PR1: 基礎手寫辨識
 * PR2: 撤銷筆畫 + 連續手寫 + 壓感粗細
 * PR3: 語言模型管理（下載/刪除）
 *
 * 佈局（由上至下）：
 * ┌──────────────────────────────────────────────────┐
 * │ [中] [英] [日] [混合]  [⚙] [↶] [清除] [⌫] [↩] │ ← 控制列
 * ├──────────────────────────────────────────────────┤
 * │ 候選: [你] [休] [体] [妳] [您]                   │ ← 候選列
 * ├──────────────────────────────────────────────────┤
 * │         ✍ 手寫區域 (Canvas)                      │
 * ├──────────────────────────────────────────────────┤
 * │  模型管理（展開時）：[繁中 ✓] [英文 ✓] [日文 ↓]  │
 * └──────────────────────────────────────────────────┘
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

    /** 辨識結果回呼（同步到候選列） */
    var onRecognized: ((List<String>) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    init {
        // 面板建立時查模型狀態，讓語言按鈕能正確顯示已下載/未下載
        engine.checkAllModelStatus { status ->
            handler.post {
                modelStatus = status
                invalidate()
            }
        }
    }

    // ── 佈局區域 ──
    private val controlBarRect = RectF()
    private val candidateBarRect = RectF()
    private val writingAreaRect = RectF()
    private val modelBarRect = RectF()

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
    private val drawWidths = mutableListOf<Float>()   // 每筆的平均粗細
    private var currentPath: Path? = null
    private var currentPressureSum = 0f
    private var currentPressureCount = 0

    // ── 自動辨識計時 ──
    private val autoRecognizeDelay = 1200L
    private val autoRecognizeRunnable = Runnable { doRecognize() }

    // ── 連續手寫：辨識後自動送出第一候選 + 清畫布 ──
    private var continuousMode = true
    private val autoCommitDelay = 2500L  // 辨識後 2.5s 無新筆畫自動送出
    private val autoCommitRunnable = Runnable { autoCommitFirst() }

    // ── 模型管理 ──
    private var showModelManager = false
    private var modelStatus = mapOf<String, Boolean>()  // tag → downloaded
    private val modelBtnRects = mutableListOf<BtnInfo>()
    private var downloadingTag: String? = null

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
        LangOption("ko-Kore", "韓"),
        LangOption("mixed", "混合")
    )
    private var selectedLangIndex = 0

    // ── 語言列水平捲動 ──
    private var langScrollX = 0f
    private var langTotalWidth = 0f
    private var langVisibleWidth = 0f
    private var langDragging = false
    private var langDragStartX = 0f
    private var langDragStartScroll = 0f

    // ── 按壓狀態 ──
    private var pressedBtn: String? = null

    // ── 壓感設定 ──
    private val minStrokeWidth = 2.5f * density
    private val maxStrokeWidth = 7f * density
    private val defaultStrokeWidth = 4f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        val baseH = 260 * density
        val modelH = if (showModelManager) 44 * density else 0f
        setMeasuredDimension(w, (baseH + modelH).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRegions(w.toFloat(), h.toFloat())
    }

    private fun layoutRegions(w: Float, h: Float) {
        val pad = 6 * density
        val controlH = 38 * density
        val candidateH = 36 * density
        val modelH = if (showModelManager) 44 * density else 0f

        controlBarRect.set(0f, 0f, w, controlH)
        candidateBarRect.set(0f, controlH, w, controlH + candidateH)

        val writingTop = controlH + candidateH + pad
        val writingBottom = (h - pad - modelH).coerceAtLeast(writingTop + 50 * density)
        writingAreaRect.set(pad, writingTop, w - pad, writingBottom)

        // 模型管理列
        if (showModelManager) {
            modelBarRect.set(0f, h - modelH, w, h)
            layoutModelButtons(w, modelBarRect)
        }

        // 操作按鈕（右側）：⚙ ↶ 清除 ⌫ ↩
        val actions = listOf("⚙" to "model_mgr", "↶" to "undo", "清除" to "clear", "⌫" to "backspace", "↩" to "confirm")
        actionButtons.clear()
        val aBtnW = 38 * density
        val btnH = 28 * density
        val btnY = (controlH - btnH) / 2f
        actions.forEachIndexed { i, (label, tag) ->
            val x = w - pad - (actions.size - i) * (aBtnW + 3 * density)
            actionButtons.add(BtnInfo(RectF(x, btnY, x + aBtnW, btnY + btnH), label, tag))
        }

        // 語言按鈕（左側，可水平捲動）
        langButtons.clear()
        val langBtnW = 40 * density
        val gap = 3 * density
        langOptions.forEachIndexed { i, lang ->
            val x = pad + i * (langBtnW + gap)
            langButtons.add(BtnInfo(RectF(x, btnY, x + langBtnW, btnY + btnH), lang.label, lang.tag))
        }
        langTotalWidth = pad + langOptions.size * (langBtnW + gap)
        // 可視區寬 = 最左操作按鈕的 left - pad
        langVisibleWidth = if (actionButtons.isNotEmpty()) actionButtons.first().rect.left - pad else w
        langScrollX = langScrollX.coerceIn(0f, (langTotalWidth - langVisibleWidth).coerceAtLeast(0f))
    }

    private fun layoutModelButtons(w: Float, barRect: RectF) {
        modelBtnRects.clear()
        val pad = 8 * density
        val btnH = 30 * density
        val btnY = barRect.top + (barRect.height() - btnH) / 2f
        var x = pad

        HandwritingEngine.SUPPORTED_LANGUAGES.forEach { (tag, name) ->
            val downloaded = modelStatus[tag] == true
            val isDownloading = downloadingTag == tag
            val label = when {
                isDownloading -> "$name ⏳"
                downloaded -> "$name ✓"
                else -> "$name ↓"
            }
            val tw = textPaint.apply { textSize = 13 * density }.measureText(label)
            val btnW = tw + 24 * density
            modelBtnRects.add(BtnInfo(RectF(x, btnY, x + btnW, btnY + btnH), label, tag))
            x += btnW + 6 * density
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, w, height.toFloat(), bgPaint)

        drawControlBar(canvas)
        drawCandidateBar(canvas)
        drawWritingArea(canvas)

        if (showModelManager) {
            drawModelBar(canvas)
        }
    }

    private fun drawControlBar(canvas: Canvas) {
        bgPaint.color = adjustAlpha(theme.keyColorInt, 180)
        canvas.drawRect(controlBarRect, bgPaint)

        val textSize = 13 * density
        textPaint.textSize = textSize
        val fm = textPaint.fontMetrics

        // 語言按鈕（clip + scroll）
        canvas.save()
        canvas.clipRect(0f, 0f, langVisibleWidth, controlBarRect.bottom)
        canvas.translate(-langScrollX, 0f)
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
            canvas.drawText(btn.label, btn.rect.centerX(),
                btn.rect.centerY() - (fm.top + fm.bottom) / 2f, textPaint)
        }
        canvas.restore()

        // 操作按鈕
        actionButtons.forEach { btn ->
            val isPressed = pressedBtn == btn.tag
            val undoDisabled = btn.tag == "undo" && drawPaths.isEmpty()
            val modelMgrActive = btn.tag == "model_mgr" && showModelManager

            btnPaint.color = when {
                isPressed -> 0xFF3A7BD5.toInt()
                btn.tag == "confirm" -> 0xFF4CAF50.toInt()
                modelMgrActive -> 0xFF4A90E2.toInt()
                undoDisabled -> adjustAlpha(theme.keyColorInt, 60)
                else -> adjustAlpha(theme.keyColorInt, 120)
            }
            canvas.drawRoundRect(btn.rect, 6 * density, 6 * density, btnPaint)

            textPaint.color = when {
                btn.tag == "confirm" || isPressed || modelMgrActive -> 0xFFFFFFFF.toInt()
                undoDisabled -> adjustAlpha(theme.keyLabelColorInt, 60)
                else -> theme.keyLabelColorInt
            }
            canvas.drawText(btn.label, btn.rect.centerX(),
                btn.rect.centerY() - (fm.top + fm.bottom) / 2f, textPaint)
        }
    }

    private fun drawCandidateBar(canvas: Canvas) {
        bgPaint.color = adjustAlpha(theme.backgroundColorInt, 220)
        canvas.drawRect(candidateBarRect, bgPaint)

        gridPaint.color = adjustAlpha(theme.keyLabelColorInt, 40)
        canvas.drawLine(candidateBarRect.left, candidateBarRect.bottom,
            candidateBarRect.right, candidateBarRect.bottom, gridPaint)

        if (candidates.isEmpty()) {
            textPaint.textSize = 13 * density
            textPaint.color = adjustAlpha(theme.keyLabelColorInt, 100)
            val hint = if (continuousMode) "連續手寫模式 — 在下方書寫" else "在下方區域手寫..."
            canvas.drawText(hint, candidateBarRect.centerX(),
                candidateBarRect.centerY() - (textPaint.fontMetrics.top + textPaint.fontMetrics.bottom) / 2f, textPaint)
            candidateRects.clear()
            return
        }

        candidateRects.clear()
        textPaint.textSize = 16 * density
        textPaint.color = theme.keyLabelColorInt
        val fm = textPaint.fontMetrics
        val pad = 12 * density
        var x = candidateBarRect.left + pad

        candidates.forEachIndexed { i, text ->
            val tw = textPaint.measureText(text)
            val btnW = tw + 20 * density
            val rect = RectF(x, candidateBarRect.top + 3 * density, x + btnW, candidateBarRect.bottom - 3 * density)
            candidateRects.add(rect)

            // 第一個候選字高亮（連續模式下會自動送出）
            btnPaint.color = if (i == 0 && continuousMode) {
                adjustAlpha(0xFF4A90E2.toInt(), 180)
            } else {
                adjustAlpha(theme.keyColorInt, 160)
            }
            canvas.drawRoundRect(rect, 4 * density, 4 * density, btnPaint)

            textPaint.color = if (i == 0 && continuousMode) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(text, rect.centerX(),
                rect.centerY() - (fm.top + fm.bottom) / 2f, textPaint)

            x += btnW + 6 * density
            if (x > candidateBarRect.right - pad) return@forEachIndexed
        }
    }

    private fun drawWritingArea(canvas: Canvas) {
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

        // 已完成筆畫（壓感粗細）
        strokePaint.color = theme.keyLabelColorInt
        drawPaths.forEachIndexed { i, path ->
            strokePaint.strokeWidth = if (i < drawWidths.size) drawWidths[i] else defaultStrokeWidth
            canvas.drawPath(path, strokePaint)
        }

        // 當前筆畫
        currentPath?.let {
            strokePaint.strokeWidth = getCurrentStrokeWidth()
            canvas.drawPath(it, strokePaint)
        }

        // 無筆畫提示
        if (drawPaths.isEmpty() && currentPath == null) {
            textPaint.textSize = 20 * density
            textPaint.color = adjustAlpha(theme.keyLabelColorInt, 50)
            canvas.drawText("✍", cx, cy + 8 * density, textPaint)
        }

        // 筆畫計數（右下角小字）
        if (drawPaths.isNotEmpty()) {
            textPaint.textSize = 10 * density
            textPaint.color = adjustAlpha(theme.keyLabelColorInt, 60)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${drawPaths.size} 筆",
                writingAreaRect.right - 8 * density,
                writingAreaRect.bottom - 6 * density, textPaint)
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun drawModelBar(canvas: Canvas) {
        bgPaint.color = adjustAlpha(theme.keyColorInt, 200)
        canvas.drawRect(modelBarRect, bgPaint)

        // 頂部分隔線
        gridPaint.color = adjustAlpha(theme.keyLabelColorInt, 40)
        canvas.drawLine(modelBarRect.left, modelBarRect.top,
            modelBarRect.right, modelBarRect.top, gridPaint)

        textPaint.textSize = 13 * density
        val fm = textPaint.fontMetrics

        modelBtnRects.forEach { btn ->
            val isPressed = pressedBtn == "model_${btn.tag}"
            val downloaded = modelStatus[btn.tag] == true
            val isDownloading = downloadingTag == btn.tag

            btnPaint.color = when {
                isPressed -> 0xFF3A7BD5.toInt()
                isDownloading -> 0xFFFFA726.toInt()
                downloaded -> 0xFF4CAF50.toInt()
                else -> adjustAlpha(theme.keyColorInt, 120)
            }
            canvas.drawRoundRect(btn.rect, 6 * density, 6 * density, btnPaint)

            textPaint.color = when {
                downloaded || isDownloading || isPressed -> 0xFFFFFFFF.toInt()
                else -> theme.keyLabelColorInt
            }
            canvas.drawText(btn.label, btn.rect.centerX(),
                btn.rect.centerY() - (fm.top + fm.bottom) / 2f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 語言列區域 → 可能捲動，記錄起點
                if (controlBarRect.contains(x, y) && x < langVisibleWidth) {
                    langDragging = false
                    langDragStartX = x
                    langDragStartScroll = langScrollX
                    // 先標記按鈕（如果在按鈕上）
                    val scrolledX = x + langScrollX
                    langButtons.forEach { btn ->
                        if (btn.rect.contains(scrolledX, y)) {
                            pressedBtn = btn.tag; invalidate(); return true
                        }
                    }
                    // 沒按到按鈕但在語言列區域，也攔截（準備捲動）
                    return true
                }
                // 操作按鈕
                actionButtons.forEach { btn ->
                    if (btn.rect.contains(x, y)) {
                        pressedBtn = btn.tag; invalidate(); return true
                    }
                }

                // 模型管理列
                if (showModelManager) {
                    modelBtnRects.forEach { btn ->
                        if (btn.rect.contains(x, y)) {
                            pressedBtn = "model_${btn.tag}"; invalidate(); return true
                        }
                    }
                }

                // 候選列
                candidateRects.forEachIndexed { i, rect ->
                    if (rect.contains(x, y) && i < candidates.size) {
                        onCandidateSelected?.invoke(candidates[i])
                        clearForContinuous()
                        return true
                    }
                }

                // 手寫區域（加 8dp 邊緣容差，讓邊界附近也能寫）
                val writingTolerance = 8 * density
                val expandedWriting = RectF(
                    writingAreaRect.left - writingTolerance,
                    writingAreaRect.top - writingTolerance,
                    writingAreaRect.right + writingTolerance,
                    writingAreaRect.bottom + writingTolerance
                )
                if (expandedWriting.contains(x, y)) {
                    handler.removeCallbacks(autoRecognizeRunnable)
                    handler.removeCallbacks(autoCommitRunnable)
                    strokeCollector.startStroke(event)
                    currentPath = Path().apply { moveTo(x, y) }
                    currentPressureSum = event.pressure
                    currentPressureCount = 1
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // 語言列水平捲動
                if (pressedBtn != null && langButtons.any { it.tag == pressedBtn }) {
                    val dx = kotlin.math.abs(x - langDragStartX)
                    if (dx > 8 * density) {
                        langDragging = true
                        pressedBtn = null  // 取消按鈕按壓
                    }
                }
                if (langDragging) {
                    val maxScroll = (langTotalWidth - langVisibleWidth).coerceAtLeast(0f)
                    langScrollX = (langDragStartScroll - (x - langDragStartX)).coerceIn(0f, maxScroll)
                    invalidate()
                    return true
                }
                if (currentPath != null) {
                    // 限制在手寫區域內
                    val clampX = x.coerceIn(writingAreaRect.left, writingAreaRect.right)
                    val clampY = y.coerceIn(writingAreaRect.top, writingAreaRect.bottom)
                    strokeCollector.addPoint(event)
                    currentPath?.lineTo(clampX, clampY)
                    // 累計壓力值
                    currentPressureSum += event.pressure
                    currentPressureCount++
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                // 語言列拖動結束
                if (langDragging) {
                    langDragging = false; return true
                }
                // 按鈕
                if (pressedBtn != null) {
                    pressedBtn?.let { handleButtonUp(it, x, y) }
                    pressedBtn = null; invalidate(); return true
                }

                // 完成筆畫
                if (currentPath != null) {
                    strokeCollector.finishStroke(event)
                    currentPath?.let { drawPaths.add(it) }
                    drawWidths.add(getCurrentStrokeWidth())
                    currentPath = null
                    currentPressureSum = 0f
                    currentPressureCount = 0
                    invalidate()

                    // 自動辨識計時
                    handler.removeCallbacks(autoRecognizeRunnable)
                    handler.removeCallbacks(autoCommitRunnable)
                    handler.postDelayed(autoRecognizeRunnable, autoRecognizeDelay)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedBtn = null; currentPath = null; invalidate()
            }
        }
        return true
    }

    private fun handleButtonUp(tag: String, x: Float, y: Float) {
        // 語言切換（DOWN 已驗證位置，UP 只要 tag 匹配即可）
        langButtons.forEachIndexed { i, btn ->
            if (btn.tag == tag) {
                selectedLangIndex = i
                applyLanguageSelection()
                return
            }
        }

        // 操作按鈕（DOWN 已驗證位置）
        actionButtons.forEach { btn ->
            if (btn.tag == tag) {
                when (tag) {
                    "model_mgr" -> toggleModelManager()
                    "undo" -> undoLastStroke()
                    "clear" -> clearWritingArea()
                    "backspace" -> onBackspace?.invoke()
                    "confirm" -> {
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

        // 模型管理按鈕
        if (showModelManager && tag.startsWith("model_")) {
            val langTag = tag.removePrefix("model_")
            modelBtnRects.forEach { btn ->
                if (btn.tag == langTag && btn.rect.contains(x, y)) {
                    handleModelButton(langTag)
                    return
                }
            }
        }
    }

    // ── PR2: 撤銷筆畫 ──

    private fun undoLastStroke() {
        if (drawPaths.isEmpty()) return

        drawPaths.removeAt(drawPaths.lastIndex)
        if (drawWidths.isNotEmpty()) drawWidths.removeAt(drawWidths.lastIndex)
        strokeCollector.undoLastStroke()

        // 有筆畫就重新辨識，沒有就清候選
        handler.removeCallbacks(autoRecognizeRunnable)
        handler.removeCallbacks(autoCommitRunnable)
        if (drawPaths.isNotEmpty()) {
            handler.postDelayed(autoRecognizeRunnable, 600L)
        } else {
            candidates = emptyList()
            candidateRects.clear()
        }
        invalidate()
    }

    // ── PR2: 連續手寫 ──

    /** 辨識結果回來後，若為連續模式，啟動自動送出計時 */
    private fun onRecognitionResult(results: List<String>) {
        candidates = results
        invalidate()

        // 通知 IMEService 同步到候選列
        if (results.isNotEmpty()) {
            onRecognized?.invoke(results)
        }

        if (continuousMode && results.isNotEmpty()) {
            handler.removeCallbacks(autoCommitRunnable)
            handler.postDelayed(autoCommitRunnable, autoCommitDelay)
        }
    }

    private fun autoCommitFirst() {
        if (candidates.isNotEmpty()) {
            onCandidateSelected?.invoke(candidates[0])
            clearForContinuous()
        }
    }

    /** 連續模式清除：清畫布但不清引擎狀態 */
    private fun clearForContinuous() {
        handler.removeCallbacks(autoRecognizeRunnable)
        handler.removeCallbacks(autoCommitRunnable)
        drawPaths.clear()
        drawWidths.clear()
        currentPath = null
        candidates = emptyList()
        candidateRects.clear()
        strokeCollector = strokeCollector.newCollector()
        invalidate()
    }

    // ── PR2: 壓感 ──

    private fun getCurrentStrokeWidth(): Float {
        if (currentPressureCount == 0) return defaultStrokeWidth
        val avgPressure = currentPressureSum / currentPressureCount
        // pressure 0~1 → 對應 minWidth~maxWidth
        return (minStrokeWidth + avgPressure * (maxStrokeWidth - minStrokeWidth))
            .coerceIn(minStrokeWidth, maxStrokeWidth)
    }

    // ── PR3: 模型管理 ──

    private fun toggleModelManager() {
        showModelManager = !showModelManager
        if (showModelManager) {
            engine.checkAllModelStatus { status ->
                handler.post {
                    modelStatus = status
                    requestLayout()
                    invalidate()
                }
            }
        } else {
            requestLayout()
            invalidate()
        }
    }

    private fun handleModelButton(langTag: String) {
        if (downloadingTag != null) return  // 一次只下載一個

        val downloaded = modelStatus[langTag] == true
        if (downloaded) {
            // 已下載 → 刪除
            engine.deleteModel(langTag) { success ->
                handler.post {
                    if (success) {
                        modelStatus = modelStatus.toMutableMap().apply { put(langTag, false) }
                    }
                    layoutRegions(width.toFloat(), height.toFloat())
                    invalidate()
                }
            }
        } else {
            // 未下載 → 下載
            downloadingTag = langTag
            layoutRegions(width.toFloat(), height.toFloat())
            invalidate()

            engine.downloadModel(langTag) { success ->
                handler.post {
                    downloadingTag = null
                    if (success) {
                        modelStatus = modelStatus.toMutableMap().apply { put(langTag, true) }
                    }
                    layoutRegions(width.toFloat(), height.toFloat())
                    invalidate()
                }
            }
        }
    }

    // ── 辨識 ──

    private fun applyLanguageSelection() {
        val option = langOptions[selectedLangIndex]
        val tags = if (option.tag == "mixed") {
            listOf("zh-Hani-TW", "en-US")
        } else {
            listOf(option.tag)
        }

        // 找出尚未下載的模型
        val notDownloaded = tags.filter { modelStatus[it] != true }

        if (notDownloaded.isEmpty()) {
            // 全部已下載 → 直接切換
            engine.setLanguages(tags)
            invalidate()
        } else {
            // 有未下載的 → 自動下載後切換
            downloadingTag = notDownloaded.first()
            invalidate()

            var remaining = notDownloaded.size
            notDownloaded.forEach { tag ->
                engine.downloadModel(tag) { success ->
                    handler.post {
                        if (success) {
                            modelStatus = modelStatus.toMutableMap().apply { put(tag, true) }
                        }
                        remaining--
                        if (remaining == 0) {
                            downloadingTag = null
                            engine.setLanguages(tags)
                            invalidate()
                        }
                    }
                }
            }
        }
    }

    private fun doRecognize() {
        if (!strokeCollector.hasStrokes) return

        val ink = strokeCollector.buildInk()
        engine.recognize(ink) { results ->
            handler.post { onRecognitionResult(results) }
        }
    }

    fun triggerRecognize() {
        handler.removeCallbacks(autoRecognizeRunnable)
        doRecognize()
    }

    private fun clearWritingArea() {
        handler.removeCallbacks(autoRecognizeRunnable)
        handler.removeCallbacks(autoCommitRunnable)
        drawPaths.clear()
        drawWidths.clear()
        currentPath = null
        candidates = emptyList()
        candidateRects.clear()
        strokeCollector = strokeCollector.newCollector()
        invalidate()
    }

    fun ensureReady(callback: (Boolean) -> Unit) {
        engine.ensureDefaultModel(callback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(autoRecognizeRunnable)
        handler.removeCallbacks(autoCommitRunnable)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
