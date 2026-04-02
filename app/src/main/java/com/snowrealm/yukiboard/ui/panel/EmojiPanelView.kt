package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Emoji 選擇面板 — 分類顯示 + 最近使用。
 */
class EmojiPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
    }
    private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 16f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 11f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onEmojiSelected: ((String) -> Unit)? = null

    private val categories = listOf(
        EmojiCategory("😊", "表情", SMILEYS),
        EmojiCategory("👋", "手勢", GESTURES),
        EmojiCategory("❤️", "愛心", HEARTS),
        EmojiCategory("🐱", "動物", ANIMALS),
        EmojiCategory("🍎", "食物", FOOD),
        EmojiCategory("⚽", "活動", ACTIVITIES),
        EmojiCategory("🚗", "交通", TRAVEL),
        EmojiCategory("💡", "物品", OBJECTS),
        EmojiCategory("🔣", "符號", SYMBOLS)
    )

    private var selectedCatIndex = 0
    private var scrollOffset = 0f

    // 按鈕位置快取
    private val catRects = mutableListOf<Pair<RectF, Int>>()
    private val emojiRects = mutableListOf<Pair<RectF, String>>()

    private val catBarHeight = 32f * density
    private val emojiSize = 40f * density
    private val cols get() = (width / emojiSize).toInt().coerceAtLeast(6)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        catRects.clear()
        emojiRects.clear()

        // 背景
        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 分類列
        drawCategoryBar(canvas)

        // Emoji 網格
        drawEmojiGrid(canvas)
    }

    private fun drawCategoryBar(canvas: Canvas) {
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), catBarHeight, bgPaint)

        val catWidth = width.toFloat() / categories.size
        categories.forEachIndexed { i, cat ->
            val rect = RectF(i * catWidth, 0f, (i + 1) * catWidth, catBarHeight)
            catRects.add(rect to i)

            if (i == selectedCatIndex) {
                bgPaint.color = theme.candidateSelectedColorInt
                bgPaint.alpha = 50
                canvas.drawRect(rect, bgPaint)
                bgPaint.alpha = 255
            }

            catPaint.color = if (i == selectedCatIndex) theme.candidateSelectedColorInt else theme.candidateTextColorInt
            canvas.drawText(cat.icon, rect.centerX(), catBarHeight * 0.72f, catPaint)
        }
    }

    private fun drawEmojiGrid(canvas: Canvas) {
        val emojis = categories[selectedCatIndex].emojis
        val cellW = width.toFloat() / cols
        val startY = catBarHeight + 8 * density

        emojis.forEachIndexed { i, emoji ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = startY + row * emojiSize - scrollOffset

            if (y + emojiSize < catBarHeight || y > height) return@forEachIndexed

            val rect = RectF(x, y, x + cellW, y + emojiSize)
            emojiRects.add(rect to emoji)

            emojiPaint.color = theme.keyLabelColorInt
            canvas.drawText(emoji, rect.centerX(), rect.centerY() + emojiPaint.textSize / 3f, emojiPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                // 先檢查分類列
                val catHit = catRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (catHit != null) {
                    selectedCatIndex = catHit.second
                    scrollOffset = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // 再檢查 emoji 格子
                val emojiHit = emojiRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (emojiHit != null) {
                    onEmojiSelected?.invoke(emojiHit.second)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private data class EmojiCategory(val icon: String, val name: String, val emojis: List<String>)

    companion object {
        val SMILEYS = listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢",
            "🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥","😏",
            "😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷",
            "🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","🤯","🤠",
            "🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","☹️",
            "😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰",
            "😥","😢","😭","😱","😖","😣","😞","😓","😩","😫",
            "🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩",
            "🤡","👹","👺","👻","👽","👾","🤖"
        )
        val GESTURES = listOf(
            "👋","🤚","🖐","✋","🖖","🫱","🫲","🫳","🫴","👌",
            "🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
            "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛",
            "🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
            "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠",
            "🫀","🫁","🦷","🦴","👀","👁","👅","👄","🫦"
        )
        val HEARTS = listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝",
            "💟","☮️","✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️"
        )
        val ANIMALS = listOf(
            "🐱","🐶","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨",
            "🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔",
            "🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴",
            "🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪰","🪲",
            "🦟","🦗","🕷","🐢","🐍","🦎","🦂","🦀","🦞","🦐",
            "🦑","🐙","🐠","🐟","🐡","🐬","🐳","🐋","🦈","🐊",
            "🐅","🐆","🦓","🦍","🦧","🐘","🦛","🦏","🐪","🐫"
        )
        val FOOD = listOf(
            "🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈",
            "🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦",
            "🌽","🌶","🫑","🥒","🥬","🥕","🧄","🧅","🥔","🍠",
            "🍞","🥐","🥖","🫓","🥨","🥯","🧇","🥞","🧈","🍳",
            "🥚","🍔","🍟","🌭","🍕","🫔","🌮","🌯","🫕","🥙",
            "🧆","🥗","🍝","🍜","🍲","🍛","🍣","🍱","🥟","🦪",
            "🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧",
            "🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫",
            "🍩","🍪","🌰","🥜","🍯","☕","🍵","🧃","🥤","🧋"
        )
        val ACTIVITIES = listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
            "🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
            "🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷",
            "⛸","🥌","🎿","⛷","🏂","🪂","🤸","🤼","🤽","🤾",
            "🤺","⛹","🏋","🚴","🚵","🎪","🎭","🎨","🎬","🎤",
            "🎧","🎼","🎹","🥁","🪘","🎷","🎺","🪗","🎸","🎻"
        )
        val TRAVEL = listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🛵","🏍","🛺","🚲","🛴","🚨",
            "🚔","🚍","🚘","🚖","🛞","🚡","🚠","🚟","🚃","🚋",
            "🚞","🚝","🚄","🚅","🚈","🚂","🚆","🚇","🚊","🚉",
            "✈️","🛫","🛬","🛩","💺","🛰","🚀","🛸","🚁","🛶",
            "⛵","🚤","🛥","🛳","⛴","🚢","🗼","🏰","🗽","🗿"
        )
        val OBJECTS = listOf(
            "💡","🔦","🕯","🧯","💰","💴","💵","💶","💷","🪙",
            "💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒","🛠",
            "⛏","🪚","🔩","⚙️","🪤","🧲","🔫","💣","🧨","🪓",
            "🔪","🗡","⚔️","🛡","🚬","⚰️","🪦","⚱️","🏺","🔮",
            "📿","🧿","🪬","💈","⚗️","🔭","🔬","🕳","🩹","🩺",
            "🩻","🩼","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡",
            "🧹","🪠","🧺","🧻","🚽","🚰","🚿","🛁","🛀","🧼"
        )
        val SYMBOLS = listOf(
            "❤️","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤",
            "🔺","🔻","🔸","🔹","🔶","🔷","💠","🔘","🔳","🔲",
            "✅","❌","❓","❗","‼️","⁉️","💯","🔱","⚜️","〽️",
            "⚠️","🚸","🔰","♻️","✳️","❇️","🌐","💹","♈","♉",
            "♊","♋","♌","♍","♎","♏","♐","♑","♒","♓",
            "⛎","🔀","🔁","🔂","▶️","⏩","⏭","⏯","◀️","⏪",
            "🔼","⏫","🔽","⏬","⏸","⏹","⏺","⏏","🎦","🔅",
            "🔆","📶","🛜","📳","📴","✖️","➕","➖","➗","🟰"
        )
    }
}
