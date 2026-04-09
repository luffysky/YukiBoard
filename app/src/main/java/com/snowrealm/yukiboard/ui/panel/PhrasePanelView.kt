package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 常用語面板 — 分類快捷短語，點擊直接輸出。
 *
 * 分類：問候、回覆、感謝、道歉、工作、社交、表態、戀愛、日文、韓文、英文
 * 支援自訂短語（SharedPreferences）
 * Tab 列支援水平捲動。
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
            "好久不見", "最近好嗎", "吃飯了嗎", "辛苦了",
            "早上好", "下午好", "晚上好", "你好嗎",
            "最近怎麼樣", "好久沒聯絡了", "想你了",
            "今天過得好嗎", "好想見你", "在忙嗎"
        )),
        PhraseCategory("回覆", "💬", listOf(
            "好的", "沒問題", "收到", "了解", "OK", "知道了",
            "好喔", "可以", "沒關係", "再說", "等一下", "馬上",
            "我想想", "待會說", "回來再聊", "先這樣",
            "讓我確認一下", "晚點回你", "好啊",
            "隨便你", "都可以", "看你", "你決定就好",
            "我覺得可以", "那就這樣吧", "再看看"
        )),
        PhraseCategory("感謝", "🙏", listOf(
            "謝謝", "感謝你", "謝謝幫忙", "太感謝了", "感恩",
            "多謝", "非常感謝", "謝謝你的幫助", "辛苦你了",
            "真的很感謝", "你太好了", "幫了大忙",
            "沒有你不行", "感謝支持", "謝謝關心",
            "謝謝你一直以來的幫助", "萬分感謝", "受教了"
        )),
        PhraseCategory("道歉", "🙇", listOf(
            "抱歉", "不好意思", "對不起", "sorry", "請見諒",
            "我的錯", "下次注意", "造成不便很抱歉",
            "真的很抱歉", "是我疏忽了", "請原諒我",
            "我會改進", "以後不會了", "打擾了",
            "不好意思耽誤你時間", "讓你久等了"
        )),
        PhraseCategory("工作", "💼", listOf(
            "收到，我處理", "好的，馬上做", "請問進度如何",
            "麻煩確認一下", "我看一下", "明天回覆你",
            "會議幾點", "可以開會了", "報告完成了", "已寄出",
            "請查收", "有空討論一下嗎", "需要幫忙嗎",
            "這件事優先處理", "截止日期是幾號",
            "我先整理一下再回覆", "檔案已上傳",
            "請幫我看一下這份文件", "這個需要修改",
            "今天可以完成", "預計明天交件", "已完成",
            "進度正常", "需要延期", "請核准",
            "會議紀錄已寄出", "請問還有其他問題嗎"
        )),
        PhraseCategory("社交", "🎉", listOf(
            "生日快樂", "新年快樂", "恭喜", "加油",
            "一起吃飯吧", "什麼時候有空", "在哪裡",
            "到了嗎", "等你喔", "掰掰", "下次見",
            "週末有計畫嗎", "要不要一起", "出去玩吧",
            "今天我請客", "去哪吃", "好吃嗎",
            "推薦一下", "要不要來我家", "聚一下",
            "好久沒出去了", "改天約", "下次再一起玩"
        )),
        PhraseCategory("表態", "👍", listOf(
            "讚", "太棒了", "好厲害", "真的假的", "哈哈哈",
            "笑死", "傻眼", "無言", "太可愛了", "好好喔",
            "我也是", "同意", "不太確定",
            "太扯了", "不會吧", "還好嗎", "別擔心",
            "放心", "沒事的", "你可以的", "加油！",
            "超感動", "太好笑了", "好心酸",
            "有道理", "你說得對", "確實", "也對",
            "不意外", "意料之中", "果然"
        )),
        PhraseCategory("戀愛", "❤️", listOf(
            "我喜歡你", "想你了", "你最棒了", "晚安，好夢",
            "早安，今天也要加油", "吃飯了嗎寶貝",
            "你在幹嘛", "好想見你喔", "什麼時候見面",
            "我等你", "你先忙", "注意身體", "多喝水",
            "早點睡", "路上小心", "到家了嗎",
            "今天辛苦了", "永遠支持你", "你是最棒的"
        )),
        PhraseCategory("日文", "🇯🇵", listOf(
            "ありがとう", "すみません", "おはよう",
            "こんにちは", "こんばんは", "おやすみ",
            "いただきます", "ごちそうさま", "お疲れ様",
            "了解です", "大丈夫", "頑張って",
            "よろしくお願いします", "お元気ですか",
            "久しぶり", "また明日", "いってきます",
            "ただいま", "おかえり", "気をつけて",
            "楽しかった", "嬉しい", "ごめんなさい",
            "ちょっと待って", "なるほど", "そうですね"
        )),
        PhraseCategory("韓文", "🇰🇷", listOf(
            "감사합니다", "안녕하세요", "미안합니다",
            "괜찮아요", "좋아요", "알겠습니다",
            "수고하셨습니다", "잘 부탁드립니다",
            "오랜만이에요", "맛있어요", "화이팅",
            "잘 자요", "좋은 아침이에요", "사랑해요",
            "보고 싶어요", "고마워요", "네",
            "아니요", "잠시만요", "어디에요"
        )),
        PhraseCategory("英文", "🇺🇸", listOf(
            "Thank you", "You're welcome", "No problem",
            "Sounds good", "Got it", "On my way",
            "Let me check", "I'll get back to you",
            "Good morning", "Good night", "See you later",
            "How are you", "I'm fine", "Take care",
            "Have a nice day", "Long time no see",
            "That's great", "I agree", "Makes sense",
            "Let me know", "ASAP", "FYI",
            "Sorry for the late reply", "Talk to you later"
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

    // Tab 列捲動
    private var tabScrollX = 0f
    private var tabTouchDownX = 0f
    private var tabTouchDownScrollX = 0f
    private var isTabDragging = false
    private var maxTabScrollX = 0f

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

        // 分類標籤列（水平捲動）
        tabRects.clear()
        val tabPadH = 12f * density
        val tabGap = 4f * density
        textPaint.textSize = 11f * density

        // 計算每個 tab 的寬度（依文字長度）
        val tabWidths = categories.mapIndexed { i, cat ->
            val label = "${cat.icon} ${cat.name}"
            textPaint.measureText(label) + tabPadH * 2
        }
        val totalTabWidth = tabWidths.sum() + tabGap * (categories.size - 1)
        maxTabScrollX = (totalTabWidth - w + gap * 2).coerceAtLeast(0f)
        tabScrollX = tabScrollX.coerceIn(0f, maxTabScrollX)

        // clip tab 區域
        canvas.save()
        canvas.clipRect(0f, 0f, w, tabH)

        var tabX = gap - tabScrollX
        categories.forEachIndexed { i, cat ->
            val tw = tabWidths[i]
            val rect = RectF(tabX, gap, tabX + tw, tabH - gap)
            tabRects.add(TabRect(rect, i))

            if (rect.right > 0f && rect.left < w) {
                val isSelected = i == selectedCategoryIndex
                tabPaint.color = if (isSelected) 0xFF7C4DFF.toInt() else (theme.keyColorInt and 0x00FFFFFF) or (0x88 shl 24)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, tabPaint)

                textPaint.color = if (isSelected) 0xFFFFFFFF.toInt() else theme.keyLabelColorInt
                val textY = rect.centerY() + textPaint.textSize * 0.35f
                canvas.drawText("${cat.icon} ${cat.name}", rect.centerX(), textY, textPaint)
            }

            tabX += tw + tabGap
        }
        canvas.restore()

        // 捲動提示（tab 可捲動時右側顯示淡出漸層）
        if (maxTabScrollX > 0f && tabScrollX < maxTabScrollX - 1f) {
            val fadeW = 20f * density
            val fadeShader = LinearGradient(
                w - fadeW, 0f, w, 0f,
                0x00000000, theme.backgroundColorInt,
                android.graphics.Shader.TileMode.CLAMP
            )
            bgPaint.shader = fadeShader
            canvas.drawRect(w - fadeW, 0f, w, tabH, bgPaint)
            bgPaint.shader = null
        }

        // 短語區域
        val phraseAreaTop = tabH
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
                // 文字太長時縮小字體
                val phraseWidth = textPaint.measureText(phrase)
                if (phraseWidth > colW - 8f * density) {
                    textPaint.textSize = 12f * density
                }
                val textY2 = rect.centerY() + textPaint.textSize * 0.35f
                canvas.drawText(phrase, rect.centerX(), textY2, textPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tabH = 32f * density
        val isInTabArea = event.y < tabH

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownY = event.y
                touchDownScrollY = scrollY
                tabTouchDownX = event.x
                tabTouchDownScrollX = tabScrollX
                isDragging = false
                isTabDragging = false

                if (isInTabArea) {
                    // 先不選 tab，等看是否是拖動
                    return true
                }

                pressedPhrase = phraseRects.firstOrNull { it.rect.contains(event.x, event.y) }?.phrase
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isInTabArea || isTabDragging) {
                    val dx = event.x - tabTouchDownX
                    if (!isTabDragging && kotlin.math.abs(dx) > 8f * density) {
                        isTabDragging = true
                        pressedPhrase = null
                    }
                    if (isTabDragging) {
                        tabScrollX = (tabTouchDownScrollX - dx).coerceIn(0f, maxTabScrollX)
                        invalidate()
                        return true
                    }
                }

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
                if (isTabDragging) {
                    isTabDragging = false
                    return true
                }
                // Tab 點擊（非拖動）
                if (event.y < tabH && !isDragging) {
                    tabRects.firstOrNull { it.rect.contains(event.x, event.y) }?.let {
                        selectedCategoryIndex = it.index
                        scrollY = 0f
                        invalidate()
                        return true
                    }
                }
                pressedPhrase?.takeIf { !isDragging }?.let { phrase ->
                    onInsertPhrase?.invoke(phrase)
                    performClick()
                }
                pressedPhrase = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedPhrase = null
                isTabDragging = false
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
