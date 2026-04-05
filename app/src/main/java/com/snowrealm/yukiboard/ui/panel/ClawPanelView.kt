package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.snowrealm.yukiboard.claw.ClawClient
import com.snowrealm.yukiboard.claw.ClawInput
import com.snowrealm.yukiboard.claw.ClawOutput
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.keyboard.KeyboardMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Claw AI 面板 — 上方 Skill 工具列 + 下方結果卡片。
 * 整合 ClawToolbarView → ClawClient → 結果顯示 的完整流程。
 */
class ClawPanelView(context: Context) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) {
            field = value
            toolbarView.theme = value
            resultView.theme = value
        }

    var clawClient: ClawClient? = null
        set(value) {
            field = value
            toolbarView.remainingTokens = value?.getRemainingTokens() ?: 0
            toolbarView.isLocked = value?.isAvailable() != true
            resultView.isLocked = value?.isAvailable() != true
        }

    /** 取得當前輸入框的文字（由 IMEService 設定） */
    var inputTextProvider: (() -> String)? = null

    /** 取得當前 app package name */
    var appPackageProvider: (() -> String)? = null

    /** 取得當前鍵盤模式 */
    var keyboardModeProvider: (() -> KeyboardMode)? = null

    var onSuggestionSelected: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onLockTapped: (() -> Unit)? = null

    private val toolbarView: ClawToolbarView
    private val resultView: ClawResultCardView

    init {
        orientation = VERTICAL

        toolbarView = ClawToolbarView(context)
        toolbarView.onSkillSelected = { skillId -> executeSkill(skillId) }
        addView(toolbarView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        resultView = ClawResultCardView(context)
        resultView.onSuggestionSelected = { text -> onSuggestionSelected?.invoke(text) }
        resultView.onClose = { onClose?.invoke() }
        resultView.onLockTapped = { onLockTapped?.invoke() }
        addView(resultView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setCurrentMode(mode: String) {
        toolbarView.currentMode = mode
    }

    private fun executeSkill(skillId: String) {
        val client = clawClient ?: return

        if (!client.isAvailable()) {
            onLockTapped?.invoke()
            return
        }

        // 設定 active 狀態
        toolbarView.activeSkillId = skillId

        // 取得輸入文字
        val inputText = inputTextProvider?.invoke() ?: ""
        val appPkg = appPackageProvider?.invoke() ?: ""
        val mode = keyboardModeProvider?.invoke() ?: KeyboardMode.NORMAL

        val input = ClawInput(
            text = inputText,
            context = "",
            appPackage = appPkg,
            language = "zh-TW",
            mode = mode
        )

        // 取消正在進行的任務
        currentJob?.cancel()

        // 顯示載入中
        resultView.showLoading(skillId)

        currentJob = scope.launch {
            try {
                // 嘗試串流模式（邊收邊顯示）
                val lang = detectInputLanguage(input.text)
                val systemPrompt = "You are YukiBoard keyboard assistant. Reply in $lang. Be concise."
                val prompt = "${input.text}\n\nContext: ${input.context}"
                val accumulated = StringBuilder()
                var hasStream = false

                withContext(Dispatchers.IO) {
                    client.streamSkill(prompt, systemPrompt)
                }.collect { chunk ->
                    hasStream = true
                    accumulated.append(chunk)
                    withContext(Dispatchers.Main) {
                        resultView.showStreamingText(accumulated.toString())
                    }
                }

                if (hasStream && accumulated.isNotEmpty()) {
                    // 串流完成 → 解析成建議列表
                    val suggestions = accumulated.toString()
                        .split("\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf(accumulated.toString()) }
                    resultView.showResults(suggestions)
                } else {
                    // 串流不可用 → fallback 到一次性執行
                    val output = withContext(Dispatchers.IO) {
                        client.executeSkill(skillId, input)
                    }
                    resultView.showResults(output.suggestions)
                }
                // 更新 token 額度
                toolbarView.remainingTokens = client.getRemainingTokens()
            } catch (e: Exception) {
                resultView.showError(e.message ?: "執行失敗")
            }
        }
    }

    /** 偵測輸入文字的語言，回傳語言名稱供 system prompt 使用 */
    private fun detectInputLanguage(text: String): String {
        val sample = text.take(100)
        return when {
            sample.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' } -> "Japanese"
            sample.any { it in '\uAC00'..'\uD7AF' } -> "Korean"
            sample.any { it in '\u4E00'..'\u9FFF' } -> "Traditional Chinese (繁體中文)"
            sample.all { it.code < 128 } -> "English"
            else -> "Traditional Chinese (繁體中文)"
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
}

/**
 * 結果卡片區域 — 顯示 AI 回應（橫向捲動卡片），載入中，錯誤等。
 */
class ClawResultCardView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var isLocked: Boolean = false
        set(value) { field = value; invalidate() }

    var onSuggestionSelected: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onLockTapped: (() -> Unit)? = null

    private var suggestions: List<String> = emptyList()
    private var loadingSkillId: String? = null
    private var errorMessage: String? = null
    private var streamingText: String? = null
    private var scrollOffset = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        textAlign = Paint.Align.LEFT
    }
    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * density
        textAlign = Paint.Align.CENTER
    }

    private val cardRects = mutableListOf<Pair<RectF, Int>>()
    private val cardWidth = 160f * density
    private val cardHeight = 110f * density
    private val cardGap = 8f * density
    private val cardCorner = 10f * density
    private val padding = 12f * density

    fun showLoading(skillId: String) {
        loadingSkillId = skillId
        errorMessage = null
        suggestions = emptyList()
        scrollOffset = 0f
        invalidate()
    }

    fun showResults(results: List<String>) {
        suggestions = results
        loadingSkillId = null
        errorMessage = null
        streamingText = null
        scrollOffset = 0f
        invalidate()
    }

    /** 串流模式：顯示正在接收中的文字 */
    fun showStreamingText(text: String) {
        streamingText = text
        loadingSkillId = null
        errorMessage = null
        suggestions = emptyList()
        invalidate()
    }

    fun showError(message: String) {
        errorMessage = message
        loadingSkillId = null
        suggestions = emptyList()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (160 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cardRects.clear()

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        bgPaint.alpha = 240
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 關閉按鈕
        closePaint.color = theme.candidateTextColorInt
        canvas.drawText("\u2715", width - 20f * density, 20f * density, closePaint)

        // 標題
        titlePaint.color = theme.effectiveAccentColor()
        canvas.drawText("\uD83D\uDC3E Claw AI", padding, 20f * density, titlePaint)

        if (isLocked) {
            statusPaint.color = theme.candidateTextColorInt
            canvas.drawText("\uD83D\uDD12 輸入邀請碼解鎖 AI", width / 2f, height / 2f, statusPaint)
            return
        }

        if (loadingSkillId != null) {
            statusPaint.color = theme.effectiveAccentColor()
            val dots = ".".repeat(((System.currentTimeMillis() / 500) % 4).toInt())
            canvas.drawText("⏳ 執行中$dots", width / 2f, height / 2f, statusPaint)
            postInvalidateDelayed(500)
            return
        }

        if (errorMessage != null) {
            statusPaint.color = 0xFFFF6B6B.toInt()
            canvas.drawText("⚠ $errorMessage", width / 2f, height / 2f, statusPaint)
            return
        }

        // 串流模式：邊收邊顯示
        val streaming = streamingText
        if (streaming != null) {
            textPaint.color = theme.keyLabelColorInt
            textPaint.textAlign = Paint.Align.LEFT
            val textPadding = 14f * density
            val maxWidth = width - textPadding * 2
            val lines = wrapText(streaming + " ▍", textPaint, maxWidth)
            var ty = 36f * density + textPaint.textSize
            for (line in lines) {
                if (ty > height - textPadding) break
                canvas.drawText(line, textPadding, ty, textPaint)
                ty += textPaint.textSize * 1.4f
            }
            return
        }

        if (suggestions.isEmpty()) {
            statusPaint.color = theme.candidateTextColorInt
            statusPaint.alpha = 150
            canvas.drawText("↑ 選擇 Skill 開始", width / 2f, height / 2f, statusPaint)
            statusPaint.alpha = 255
            return
        }

        // 卡片區域
        val cardsTop = 30f * density

        canvas.save()
        canvas.clipRect(padding, cardsTop, width.toFloat() - padding, height - padding)

        suggestions.forEachIndexed { i, text ->
            val left = padding + i * (cardWidth + cardGap) + scrollOffset
            val right = left + cardWidth
            if (right < 0 || left > width) return@forEachIndexed

            val rect = RectF(left, cardsTop, right, cardsTop + cardHeight)

            // 卡片背景
            cardPaint.color = theme.keyColorInt
            cardPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, cardCorner, cardCorner, cardPaint)

            // 卡片邊框
            cardPaint.style = Paint.Style.STROKE
            cardPaint.strokeWidth = 1f * density
            cardPaint.color = theme.effectiveAccentColor()
            cardPaint.alpha = 60
            canvas.drawRoundRect(rect, cardCorner, cardCorner, cardPaint)
            cardPaint.style = Paint.Style.FILL
            cardPaint.alpha = 255

            // 文字
            textPaint.color = theme.keyLabelColorInt
            textPaint.textAlign = Paint.Align.LEFT
            val textPadding = 10f * density
            val maxWidth = cardWidth - textPadding * 2
            val lines = wrapText(text, textPaint, maxWidth)
            var ty = rect.top + textPadding + textPaint.textSize
            for (line in lines) {
                if (ty > rect.bottom - textPadding) break
                canvas.drawText(line, rect.left + textPadding, ty, textPaint)
                ty += textPaint.textSize * 1.3f
            }

            // 「點擊插入」提示
            if (lines.isNotEmpty()) {
                val hintPaint = Paint(textPaint)
                hintPaint.textSize = 9f * density
                hintPaint.color = theme.candidateTextColorInt
                hintPaint.alpha = 120
                hintPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("點擊替換 ↩", rect.right - textPadding, rect.bottom - 6f * density, hintPaint)
            }

            cardRects.add(rect to i)
        }

        canvas.restore()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count == 0) break
            lines.add(text.substring(start, start + count))
            start += count
        }
        return lines
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                if (Math.abs(dx) > 8f * density) isDragging = true
                if (isDragging) {
                    scrollOffset += dx
                    val maxScroll = 0f
                    val minScroll = -(suggestions.size * (cardWidth + cardGap) - width + padding * 2).coerceAtLeast(0f)
                    scrollOffset = scrollOffset.coerceIn(minScroll, maxScroll)
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 關閉
                    if (event.x > width - 40f * density && event.y < 30f * density) {
                        onClose?.invoke()
                        return true
                    }
                    // 鎖定
                    if (isLocked) {
                        onLockTapped?.invoke()
                        return true
                    }
                    // 卡片點擊
                    for ((rect, index) in cardRects) {
                        if (rect.contains(event.x, event.y) && index < suggestions.size) {
                            onSuggestionSelected?.invoke(suggestions[index])
                            performClick()
                            return true
                        }
                    }
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
