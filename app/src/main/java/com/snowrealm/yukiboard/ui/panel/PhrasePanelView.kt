package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 常用語面板 — 分類快捷短語，點擊直接輸出。
 *
 * 分類：問候、回覆、感謝、道歉、工作、社交、表態
 * 支援自訂短語（SharedPreferences）
 */
class PhrasePanelView(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 插入短語到輸入框 */
    var onInsertPhrase: ((String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("yukiboard_phrases", Context.MODE_PRIVATE)

    // 分類
    private data class PhraseCategory(val name: String, val icon: String, val phrases: List<String>)

    private var selectedCategoryIndex = 0

    private val categories = mutableListOf(
        PhraseCategory("問候", "👋", listOf(
            "你好", "早安", "午安", "晚安", "嗨", "哈囉",
            "好久不見", "最近好嗎", "吃飯了嗎", "辛苦了"
        )),
        PhraseCategory("回覆", "💬", listOf(
            "好的", "沒問題", "收到", "了解", "OK", "知道了",
            "好喔", "可以", "沒關係", "再說", "等一下", "馬上"
        )),
        PhraseCategory("感謝", "🙏", listOf(
            "謝謝", "感謝你", "謝謝幫忙", "太感謝了", "感恩",
            "多謝", "非常感謝", "謝謝你的幫助", "辛苦你了"
        )),
        PhraseCategory("道歉", "🙇", listOf(
            "抱歉", "不好意思", "對不起", "sorry", "請見諒",
            "我的錯", "下次注意", "造成不便很抱歉"
        )),
        PhraseCategory("工作", "💼", listOf(
            "收到，我處理", "好的，馬上做", "請問進度如何",
            "麻煩確認一下", "我看一下", "明天回覆你",
            "會議幾點", "可以開會了", "報告完成了", "已寄出"
        )),
        PhraseCategory("社交", "🎉", listOf(
            "生日快樂", "新年快樂", "恭喜", "加油",
            "一起吃飯吧", "什麼時候有空", "在哪裡",
            "到了嗎", "等你喔", "掰掰", "下次見"
        )),
        PhraseCategory("表態", "👍", listOf(
            "讚", "太棒了", "好厲害", "真的假的", "哈哈哈",
            "笑死", "傻眼", "無言", "太可愛了", "好好喔",
            "我也是", "同意", "不太確定"
        )),
    )

    // 繪製相關
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val phrasePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class TabRect(val rect: RectF, val index: Int)
    private data class PhraseRect(val rect: RectF, val phrase: String)

    private val tabRects = mutableListOf<TabRect>()
    private val phraseRects = mutableListOf<PhraseRect>()
    private var pressedPhrase: String? = null
    private var scrollY = 0f
    private var touchDownY = 0f
    private var touchDownScrollY = 0f
    private var isDragging = false

    init {
        loadCustomPhrases()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val tabH = 32f * density
        val gap = 3f * density

        // 背景
        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 分類標籤列
        tabRects.clear()
        val tabW = w / categories.size
        categories.forEachIndexed { i, cat ->
            val rect = RectF(i * tabW + gap, gap, (i + 1) * tabW - gap, tabH - gap)
            tabRects.add(TabRect(rect, i))

            val isSelected = i == selectedCategoryIndex
            tabPaint.color = if (isSelected) 0xFF7C4DFF.toInt() else (theme.keyColorInt and 0x00FFFFFF) or (0x88 shl 24)
            canvas.drawRoundRect(rect, 6f * density, 6f * density, tabPaint)

            textPaint.color = if (isSelected) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
            textPaint.textSize = 11f * density
            val textY = rect.centerY() + textPaint.textSize * 0.35f
            canvas.drawText("${cat.icon} ${cat.name}", rect.centerX(), textY, textPaint)
        }

        // 短語區域
        val phraseAreaTop = tabH
        val phraseAreaH = h - phraseAreaTop
        canvas.save()
        canvas.clipRect(0f, phraseAreaTop, w, h)

        phraseRects.clear()
        val cat = categories.getOrNull(selectedCategoryIndex) ?: return
        val phraseH = 36f * density
        val cols = 2
        val colW = (w - gap * 3) / cols

        cat.phrases.forEachIndexed { i, phrase ->
            val row = i / cols
            val col = i % cols
            val x = gap + col * (colW + gap)
            val y = phraseAreaTop + gap + row * (phraseH + gap) - scrollY

            if (y + phraseH > phraseAreaTop && y < h) {
                val rect = RectF(x, y, x + colW, y + phraseH)
                phraseRects.add(PhraseRect(rect, phrase))

                val isPressed = phrase == pressedPhrase
                phrasePaint.color = if (isPressed) 0xFF7C4DFF.toInt()
                    else (theme.keyColorInt and 0x00FFFFFF) or (0xCC shl 24)
                canvas.drawRoundRect(rect, 8f * density, 8f * density, phrasePaint)

                textPaint.color = if (isPressed) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
                textPaint.textSize = 14f * density
                val textY2 = rect.centerY() + textPaint.textSize * 0.35f
                canvas.drawText(phrase, rect.centerX(), textY2, textPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y
                touchDownScrollY = scrollY
                isDragging = false
                // 檢查標籤點擊
                tabRects.firstOrNull { it.rect.contains(event.x, event.y) }?.let {
                    selectedCategoryIndex = it.index
                    scrollY = 0f
                    invalidate()
                    return true
                }
                pressedPhrase = phraseRects.firstOrNull { it.rect.contains(event.x, event.y) }?.phrase
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - touchDownY
                if (!isDragging && kotlin.math.abs(dy) > 8f * density) {
                    isDragging = true
                    pressedPhrase = null
                }
                if (isDragging) {
                    scrollY = (touchDownScrollY - dy).coerceAtLeast(0f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && pressedPhrase != null) {
                    onInsertPhrase?.invoke(pressedPhrase!!)
                    performClick()
                }
                pressedPhrase = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedPhrase = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun loadCustomPhrases() {
        val custom = prefs.getStringSet("custom_phrases", null)
        if (!custom.isNullOrEmpty()) {
            categories.add(PhraseCategory("自訂", "⭐", custom.toList()))
        }
    }
}
