package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 翻譯面板 — 輸入文字即時翻譯並插入。
 *
 * MVP 架構：
 * - 顯示來源語言 / 目標語言切換
 * - 輸入文字區 + 翻譯結果區
 * - 點擊翻譯結果即插入
 *
 * 翻譯引擎由外部注入（TranslationProvider 介面），
 * 可接 MLKit、Google Translate API、或離線模型。
 */
class TranslatePanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
    }
    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * density
    }
    private val langPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 外部設定要翻譯的來源文字（從游標擷取或手動輸入） */
    var sourceText: String = ""
        set(value) { field = value; translate(); invalidate() }

    /** 翻譯結果 */
    var translatedText: String = ""
        private set

    /** 使用者正在手動輸入翻譯來源 */
    private var isInputMode = false
    private val inputBuffer = StringBuilder()

    /** 點擊翻譯結果時回呼 */
    var onTranslatedSelected: ((String) -> Unit)? = null

    /** 朗讀翻譯結果回呼 — 由 IME Service 注入 TTS */
    var onSpeakRequest: ((text: String, locale: String) -> Unit)? = null

    /** 請求焦點到翻譯輸入框（讓鍵盤打的字進到這裡） */
    var onRequestInputMode: ((Boolean) -> Unit)? = null

    /** 翻譯引擎回呼 — 由 IME Service 注入 */
    var translationProvider: ((source: String, fromLang: String, toLang: String, callback: (String) -> Unit) -> Unit)? = null

    private val languages = listOf(
        LangOption("zh-TW", "繁中"),
        LangOption("en", "English"),
        LangOption("ja", "日本語"),
        LangOption("ko", "한국어"),
        LangOption("es", "Español"),
        LangOption("fr", "Français"),
        LangOption("de", "Deutsch")
    )

    private var fromLangIndex = 0
    private var toLangIndex = 1

    private var resultRect = RectF()
    private var swapRect = RectF()
    private var fromRect = RectF()
    private var toRect = RectF()
    private var sourceRect = RectF()
    private var clearRect = RectF()
    private var translateBtnRect = RectF()
    private var speakSourceRect = RectF()
    private var speakResultRect = RectF()
    private val fromLangRects = mutableListOf<Pair<RectF, Int>>()
    private val toLangRects = mutableListOf<Pair<RectF, Int>>()
    private var showFromPicker = false
    private var showToPicker = false

    private val headerH = 36f * density
    private val cornerR = 8f * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fromLangRects.clear()
        toLangRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 語言選擇列
        drawLangHeader(canvas)

        // 來源文字輸入框
        val srcY = headerH + 6 * density
        val srcH = 44 * density
        sourceRect = RectF(8 * density, srcY, width - 8 * density, srcY + srcH)

        // 輸入框背景
        btnPaint.color = if (isInputMode)
            (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x22000000
        else
            (theme.keyColorInt and 0x00FFFFFF) or 0x30000000
        canvas.drawRoundRect(sourceRect, cornerR, cornerR, btnPaint)

        // 輸入框邊框（聚焦時）
        if (isInputMode) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = theme.candidateSelectedColorInt
            }
            canvas.drawRoundRect(sourceRect, cornerR, cornerR, borderPaint)
        }

        // 來源文字
        val displayText = when {
            isInputMode && inputBuffer.isNotEmpty() -> inputBuffer.toString()
            sourceText.isNotEmpty() -> sourceText
            else -> ""
        }
        val placeholder = if (isInputMode) "輸入要翻譯的文字..." else "點此輸入，或從輸入框擷取文字"
        textPaint.color = theme.candidateTextColorInt
        textPaint.alpha = if (displayText.isEmpty()) 80 else 255
        val srcTextDisplay = displayText.ifEmpty { placeholder }
        canvas.drawText(srcTextDisplay, sourceRect.left + 10 * density, sourceRect.centerY() + textPaint.textSize / 3f, textPaint)
        textPaint.alpha = 255

        // 右側清除按鈕（有文字時顯示）
        if (displayText.isNotEmpty()) {
            val clearSize = 20 * density
            clearRect = RectF(
                sourceRect.right - clearSize - 8 * density,
                sourceRect.centerY() - clearSize / 2,
                sourceRect.right - 8 * density,
                sourceRect.centerY() + clearSize / 2
            )
            swapPaint.color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x88000000.toInt()
            swapPaint.textSize = 14f * density
            canvas.drawText("✕", clearRect.centerX(), clearRect.centerY() + 5 * density, swapPaint)
            swapPaint.textSize = 18f * density
        } else {
            clearRect = RectF()
        }

        // 來源朗讀按鈕 🔊
        if (displayText.isNotEmpty()) {
            val speakSize = 28 * density
            val speakX = if (!clearRect.isEmpty) clearRect.left - speakSize - 4 * density
                         else sourceRect.right - speakSize - 8 * density
            speakSourceRect = RectF(speakX, sourceRect.centerY() - speakSize / 2,
                speakX + speakSize, sourceRect.centerY() + speakSize / 2)
            val speakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 16f * density
                color = theme.candidateSelectedColorInt
            }
            canvas.drawText("🔊", speakSourceRect.centerX(), speakSourceRect.centerY() + 6 * density, speakPaint)
        } else {
            speakSourceRect = RectF()
        }

        // 翻譯按鈕
        val btnY = srcY + srcH + 6 * density
        val btnH = 32 * density
        translateBtnRect = RectF(width - 80 * density, btnY, width - 8 * density, btnY + btnH)
        btnPaint.color = theme.candidateSelectedColorInt
        canvas.drawRoundRect(translateBtnRect, cornerR, cornerR, btnPaint)
        langPaint.color = 0xFFFFFFFF.toInt()
        langPaint.textSize = 11f * density
        canvas.drawText("翻譯 →", translateBtnRect.centerX(), translateBtnRect.centerY() + langPaint.textSize / 3f, langPaint)
        langPaint.textSize = 12f * density

        // 翻譯結果（可點擊）
        val resY = btnY + btnH + 6 * density
        resultRect = RectF(8 * density, resY, width - 8 * density, height - 8 * density)

        btnPaint.color = (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x18000000
        canvas.drawRoundRect(resultRect, cornerR, cornerR, btnPaint)

        resultPaint.color = theme.keyLabelColorInt
        val resDisplay = translatedText.ifEmpty { "翻譯結果會顯示在這裡" }
        resultPaint.alpha = if (translatedText.isEmpty()) 100 else 255
        canvas.drawText(resDisplay, resultRect.left + 12 * density, resultRect.centerY() + resultPaint.textSize / 3f, resultPaint)
        resultPaint.alpha = 255

        // 翻譯結果朗讀按鈕 🔊
        if (translatedText.isNotEmpty()) {
            val speakSize = 32 * density
            speakResultRect = RectF(
                resultRect.right - speakSize - 8 * density,
                resultRect.centerY() - speakSize / 2,
                resultRect.right - 8 * density,
                resultRect.centerY() + speakSize / 2
            )
            val speakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 18f * density
                color = theme.candidateSelectedColorInt
            }
            canvas.drawText("🔊", speakResultRect.centerX(), speakResultRect.centerY() + 7 * density, speakPaint)
        } else {
            speakResultRect = RectF()
        }

        // 語言選擇下拉（如果展開）
        if (showFromPicker) drawLangPicker(canvas, fromRect.left, headerH, fromLangIndex, isFrom = true)
        if (showToPicker) drawLangPicker(canvas, toRect.left, headerH, toLangIndex, isFrom = false)
    }

    private fun drawLangHeader(canvas: Canvas) {
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), headerH, bgPaint)

        val third = width / 3f

        // From language
        fromRect = RectF(4 * density, 4 * density, third - 4 * density, headerH - 4 * density)
        btnPaint.color = theme.keyColorInt
        canvas.drawRoundRect(fromRect, cornerR, cornerR, btnPaint)
        langPaint.color = theme.keyLabelColorInt
        canvas.drawText(languages[fromLangIndex].display + " ▼", fromRect.centerX(), fromRect.centerY() + langPaint.textSize / 3f, langPaint)

        // Swap button
        swapRect = RectF(third + 4 * density, 4 * density, 2 * third - 4 * density, headerH - 4 * density)
        swapPaint.color = theme.candidateSelectedColorInt
        canvas.drawText("⇄", swapRect.centerX(), swapRect.centerY() + swapPaint.textSize / 3f, swapPaint)

        // To language
        toRect = RectF(2 * third + 4 * density, 4 * density, width - 4 * density, headerH - 4 * density)
        btnPaint.color = theme.keyColorInt
        canvas.drawRoundRect(toRect, cornerR, cornerR, btnPaint)
        langPaint.color = theme.keyLabelColorInt
        canvas.drawText(languages[toLangIndex].display + " ▼", toRect.centerX(), toRect.centerY() + langPaint.textSize / 3f, langPaint)
    }

    private fun drawLangPicker(canvas: Canvas, x: Float, y: Float, selectedIdx: Int, isFrom: Boolean) {
        val itemH = 30f * density
        val pickerW = 120f * density

        languages.forEachIndexed { i, lang ->
            val iy = y + i * itemH
            val rect = RectF(x, iy, x + pickerW, iy + itemH)

            if (isFrom) fromLangRects.add(rect to i) else toLangRects.add(rect to i)

            bgPaint.color = if (i == selectedIdx) theme.candidateSelectedColorInt else theme.keyColorInt
            canvas.drawRect(rect, bgPaint)

            langPaint.color = if (i == selectedIdx) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            canvas.drawText(lang.display, rect.centerX(), rect.centerY() + langPaint.textSize / 3f, langPaint)
        }
    }

    private fun translate() {
        if (sourceText.isBlank()) {
            translatedText = ""
            return
        }
        val from = languages[fromLangIndex].code
        val to = languages[toLangIndex].code
        translationProvider?.invoke(sourceText, from, to) { result ->
            translatedText = result
            post { invalidate() }
        } ?: run {
            // 無翻譯引擎時顯示提示
            translatedText = "[翻譯引擎未連接]"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // 語言選擇下拉
            if (showFromPicker) {
                val hit = fromLangRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    fromLangIndex = hit.second
                    showFromPicker = false
                    translate()
                    invalidate()
                    performClick()
                    return true
                }
                showFromPicker = false
                invalidate()
                return true
            }
            if (showToPicker) {
                val hit = toLangRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    toLangIndex = hit.second
                    showToPicker = false
                    translate()
                    invalidate()
                    performClick()
                    return true
                }
                showToPicker = false
                invalidate()
                return true
            }

            // From 語言按鈕
            if (fromRect.contains(event.x, event.y)) {
                showFromPicker = true
                showToPicker = false
                invalidate()
                performClick()
                return true
            }

            // To 語言按鈕
            if (toRect.contains(event.x, event.y)) {
                showToPicker = true
                showFromPicker = false
                invalidate()
                performClick()
                return true
            }

            // Swap 按鈕
            if (swapRect.contains(event.x, event.y)) {
                val tmp = fromLangIndex
                fromLangIndex = toLangIndex
                toLangIndex = tmp
                translate()
                invalidate()
                performClick()
                return true
            }

            // 朗讀來源文字
            if (!speakSourceRect.isEmpty && speakSourceRect.contains(event.x, event.y)) {
                val text = if (isInputMode && inputBuffer.isNotEmpty()) inputBuffer.toString() else sourceText
                if (text.isNotEmpty()) {
                    onSpeakRequest?.invoke(text, languages[fromLangIndex].code)
                }
                performClick()
                return true
            }

            // 朗讀翻譯結果
            if (!speakResultRect.isEmpty && speakResultRect.contains(event.x, event.y)) {
                if (translatedText.isNotEmpty()) {
                    onSpeakRequest?.invoke(translatedText, languages[toLangIndex].code)
                }
                performClick()
                return true
            }

            // 點擊來源輸入框 → 進入手動輸入模式
            if (sourceRect.contains(event.x, event.y)) {
                // 先檢查是否點了清除按鈕
                if (!clearRect.isEmpty && clearRect.contains(event.x, event.y)) {
                    inputBuffer.clear()
                    sourceText = ""
                    translatedText = ""
                    invalidate()
                    performClick()
                    return true
                }
                if (!isInputMode) {
                    isInputMode = true
                    inputBuffer.clear()
                    if (sourceText.isNotEmpty()) {
                        inputBuffer.append(sourceText)
                    }
                    onRequestInputMode?.invoke(true)
                    invalidate()
                }
                performClick()
                return true
            }

            // 翻譯按鈕
            if (translateBtnRect.contains(event.x, event.y)) {
                if (isInputMode && inputBuffer.isNotEmpty()) {
                    sourceText = inputBuffer.toString()
                } else if (sourceText.isNotEmpty()) {
                    translate()
                }
                invalidate()
                performClick()
                return true
            }

            // 點擊翻譯結果 → 插入
            if (resultRect.contains(event.x, event.y) && translatedText.isNotEmpty()) {
                onTranslatedSelected?.invoke(translatedText)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ─────────────── 手動輸入 API ���──────────────

    /** 是否處於翻譯輸入模式 */
    val isInInputMode: Boolean get() = isInputMode

    /** 接收鍵盤字元輸入 */
    fun onCharInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        invalidate()
    }

    /** 接收退格 */
    fun onDeleteInput() {
        if (!isInputMode || inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        invalidate()
    }

    /** 接收空白鍵 — 加空格 */
    fun onSpaceInput() {
        if (!isInputMode) return
        inputBuffer.append(' ')
        invalidate()
    }

    /** 接收 Enter — 觸發翻譯 */
    fun onEnterInput() {
        if (!isInputMode) return
        if (inputBuffer.isNotEmpty()) {
            sourceText = inputBuffer.toString()
        }
        invalidate()
    }

    /** 退出輸入模式 */
    fun exitInputMode() {
        isInputMode = false
        onRequestInputMode?.invoke(false)
        invalidate()
    }

    private data class LangOption(val code: String, val display: String)
}
