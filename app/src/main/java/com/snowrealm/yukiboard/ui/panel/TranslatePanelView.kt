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

    /** 外部設定要翻譯的來源文字（通常從游標前方取得） */
    var sourceText: String = ""
        set(value) { field = value; translate(); invalidate() }

    /** 翻譯結果 */
    var translatedText: String = ""
        private set

    /** 點擊翻譯結果時回呼 */
    var onTranslatedSelected: ((String) -> Unit)? = null

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

        // 來源文字
        val srcY = headerH + 8 * density
        textPaint.color = theme.candidateTextColorInt
        val srcDisplay = if (sourceText.isEmpty()) "(從游標前方擷取文字)" else sourceText
        textPaint.alpha = if (sourceText.isEmpty()) 100 else 255
        canvas.drawText(srcDisplay, 12f * density, srcY + textPaint.textSize, textPaint)
        textPaint.alpha = 255

        // 分隔線
        val divY = srcY + 40 * density
        bgPaint.color = theme.keyColorInt
        canvas.drawRect(12 * density, divY, width - 12 * density, divY + density, bgPaint)

        // 翻譯結果（可點擊）
        val resY = divY + 8 * density
        resultRect = RectF(8 * density, resY, width - 8 * density, resY + 50 * density)

        btnPaint.color = (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x18000000
        canvas.drawRoundRect(resultRect, cornerR, cornerR, btnPaint)

        resultPaint.color = theme.keyLabelColorInt
        val resDisplay = translatedText.ifEmpty { "翻譯結果會顯示在這裡" }
        resultPaint.alpha = if (translatedText.isEmpty()) 100 else 255
        canvas.drawText(resDisplay, resultRect.left + 12 * density, resultRect.centerY() + resultPaint.textSize / 3f, resultPaint)
        resultPaint.alpha = 255

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

    private data class LangOption(val code: String, val display: String)
}
