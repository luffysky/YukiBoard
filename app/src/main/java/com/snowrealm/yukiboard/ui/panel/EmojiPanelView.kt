package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * Emoji 選擇面板 — 分類顯示 + 滑動捲動 + 最近使用。
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
        EmojiCategory("👤", "人物", PEOPLE),
        EmojiCategory("❤️", "愛心", HEARTS),
        EmojiCategory("🐱", "動物", ANIMALS),
        EmojiCategory("🌿", "自然", NATURE),
        EmojiCategory("🍎", "食物", FOOD),
        EmojiCategory("⚽", "活動", ACTIVITIES),
        EmojiCategory("🚗", "交通", TRAVEL),
        EmojiCategory("💡", "物品", OBJECTS),
        EmojiCategory("🔣", "符號", SYMBOLS),
        EmojiCategory("🏁", "旗幟", FLAGS)
    )

    private var selectedCatIndex = 0
    private var scrollOffset = 0f

    // 按鈕位置快取
    private val catRects = mutableListOf<Pair<RectF, Int>>()
    private val emojiRects = mutableListOf<Pair<RectF, String>>()

    private val catBarHeight = 32f * density
    private val emojiSize = 40f * density
    private val cols get() = (width / emojiSize).toInt().coerceAtLeast(6)

    // ─── 滑動支援 ───
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastTouchY = 0f
    private var isDragging = false
    private val touchSlop = 8f * density
    private var downY = 0f
    private var downX = 0f

    private val maxScroll: Float
        get() {
            val emojis = categories[selectedCatIndex].emojis
            val rows = (emojis.size + cols - 1) / cols
            val contentH = rows * emojiSize + 8 * density
            val visibleH = height - catBarHeight
            return (contentH - visibleH).coerceAtLeast(0f)
        }

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
        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Emoji 網格（先畫，被分類列蓋住上方溢出）
        canvas.save()
        canvas.clipRect(0f, catBarHeight, width.toFloat(), height.toFloat())
        drawEmojiGrid(canvas)
        canvas.restore()

        // 分類列（固定在頂部）
        drawCategoryBar(canvas)

        // fling 動畫
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currY.toFloat().coerceIn(0f, maxScroll)
            postInvalidateOnAnimation()
        }
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
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downY = event.y
                downX = event.x
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (!isDragging && (Math.abs(event.y - downY) > touchSlop || Math.abs(event.x - downX) > touchSlop)) {
                    isDragging = Math.abs(event.y - downY) > Math.abs(event.x - downX)
                }
                if (isDragging) {
                    scrollOffset = (scrollOffset + dy).coerceIn(0f, maxScroll)
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging && event.action == MotionEvent.ACTION_UP) {
                    handleTap(event.x, event.y)
                } else {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vy = velocityTracker?.yVelocity ?: 0f
                    if (Math.abs(vy) > 200f) {
                        scroller.fling(0, scrollOffset.toInt(), 0, (-vy).toInt(), 0, 0, 0, maxScroll.toInt())
                        postInvalidateOnAnimation()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        // 先檢查分類列
        val catHit = catRects.firstOrNull { it.first.contains(x, y) }
        if (catHit != null) {
            selectedCatIndex = catHit.second
            scrollOffset = 0f
            scroller.forceFinished(true)
            invalidate()
            performClick()
            return
        }

        // 再檢查 emoji 格子
        val emojiHit = emojiRects.firstOrNull { it.first.contains(x, y) }
        if (emojiHit != null) {
            onEmojiSelected?.invoke(emojiHit.second)
            performClick()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private data class EmojiCategory(val icon: String, val name: String, val emojis: List<String>)

    companion object {
        // ── 表情與臉 ──
        val SMILEYS = listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢",
            "🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥",
            "😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴",
            "😷","🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","😵‍💫",
            "🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟",
            "🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧",
            "😨","😰","😥","😢","😭","😱","😖","😣","😞","😓",
            "😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀",
            "☠️","💩","🤡","👹","👺","👻","👽","👾","🤖","😺",
            "😸","😹","😻","😼","😽","🙀","😿","😾","🫶","🫠",
            "🫨","🫩","🫤","🫵","🫱","🫲","🫳","🫴","🫰","🫶"
        )
        // ── 手勢與身體 ──
        val GESTURES = listOf(
            "👋","🤚","🖐","✋","🖖","🫱","🫲","🫳","🫴","🫷",
            "🫸","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙",
            "👈","👉","👆","🖕","👇","☝️","🫵","👍","👎","✊",
            "👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏",
            "✍️","💅","🤳","💪","🦾","🦿","🦵","🦶","👂","🦻",
            "👃","🧠","🫀","🫁","🦷","🦴","👀","👁","👅","👄",
            "🫦","🦵","🦶","👣","🫂","🗣","👤","👥"
        )
        // ── 人物 ──
        val PEOPLE = listOf(
            "👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓",
            "👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇",
            "🤦","🤷","👮","🕵","💂","🥷","👷","🫅","🤴","👸",
            "👳","👲","🧕","🤵","👰","🤰","🫃","🫄","🤱","👼",
            "🎅","🤶","🦸","🦹","🧙","🧚","🧛","🧜","🧝","🧞",
            "🧟","🧌","💆","💇","🚶","🧍","🧎","🏃","💃","🕺",
            "🕴","👯","🧖","🧗","🤸","🏌","🏇","⛷","🏂","🏋",
            "🤼","🤽","🤾","🤺","⛹","🚣","🧘","🛀","🛌","👭",
            "👫","👬","💏","💑","👪","👨‍👩‍👦","👨‍👩‍👧","👨‍👩‍👧‍👦","👨‍👩‍👦‍👦","👨‍👩‍👧‍👧",
            "👨‍👦","👨‍👧","👩‍👦","👩‍👧","🗣","👤","👥","🫂"
        )
        // ── 愛心與情感符號 ──
        val HEARTS = listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","🩷",
            "🩵","🩶","❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖",
            "💘","💝","💟","♥️","💋","💌","💐","🌹","🥀","🌺",
            "🌷","🌸","💮","🏵","💑","💏","👩‍❤️‍👨","👨‍❤️‍👨","👩‍❤️‍👩","💒",
            "☮️","✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️","⛎"
        )
        // ── 動物 ──
        val ANIMALS = listOf(
            "🐱","🐶","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨",
            "🐯","🦁","🐮","🐷","🐽","🐸","🐵","🙈","🙉","🙊",
            "🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇",
            "🐺","🐗","🐴","🦄","🫎","🫏","🐝","🪱","🐛","🦋",
            "🐌","🐞","🐜","🪰","🪲","🪳","🦟","🦗","🕷","🕸",
            "🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦐","🦞",
            "🦀","🪸","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🪼",
            "🐊","🐅","🐆","🦓","🫏","🦍","🦧","🦣","🐘","🦛",
            "🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎",
            "🐖","🐏","🐑","🦙","🐐","🦌","🫎","🐕","🐩","🦮",
            "🐕‍🦺","🐈","🐈‍⬛","🪶","🐓","🦃","🦤","🦚","🦜","🦢",
            "🦩","🕊","🐇","🦝","🦨","🦡","🦫","🦦","🦥","🐁",
            "🐀","🐿","🦔","🐾","🐉","🐲","🦎","🐊"
        )
        // ── 自然與天氣 ──
        val NATURE = listOf(
            "🌵","🎄","🌲","🌳","🌴","🪹","🪺","🪵","🌱","🌿",
            "☘️","🍀","🎍","🪴","🎋","🍃","🍂","🍁","🪻","🪷",
            "🌺","🌻","🌹","🥀","🌷","🌸","💐","🍄","🌾","🪨",
            "🌍","🌎","🌏","🌐","🗺","🌑","🌒","🌓","🌔","🌕",
            "🌖","🌗","🌘","🌙","🌚","🌛","🌜","☀️","🌝","🌞",
            "🪐","⭐","🌟","✨","💫","🌠","🌌","☁️","⛅","⛈",
            "🌤","🌥","🌦","🌧","🌨","🌩","🌪","🌫","🌬","🌀",
            "🌈","☔","⚡","❄️","☃️","⛄","☄️","🔥","💧","🌊",
            "🎆","🎇","🧨","✨","🎑","🏞","🌅","🌄","🌠","🎑"
        )
        // ── 食物與飲料 ──
        val FOOD = listOf(
            "🍎","🍐","🍊","🍋","🍋‍🟩","🍌","🍉","🍇","🍓","🫐",
            "🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑",
            "🫛","🥦","🥬","🌽","🌶","🫑","🥒","🥕","🫒","🧄",
            "🧅","🥔","🍠","🫚","🫘","🥜","🌰","🫑","🍞","🥐",
            "🥖","🫓","🥨","🥯","🧇","🥞","🧈","🍳","🥚","🧀",
            "🥓","🥩","🍗","🍖","🌭","🍔","🍟","🍕","🫔","🌮",
            "🌯","🫕","🥙","🧆","🥗","🍝","🍜","🫕","🍲","🍛",
            "🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠",
            "🥮","🍢","🍡","🍧","🍨","🍦","🥧","🧁","🍰","🎂",
            "🍮","🍭","🍬","🍫","🍩","🍪","🌰","🥜","🍯","🫘",
            "☕","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷",
            "🥃","🍸","🍹","🧉","🍾","🫗","🍼","🧊","🥛","🫖"
        )
        // ── 運動與活動 ──
        val ACTIVITIES = listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
            "🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
            "🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷",
            "⛸","🥌","🎿","⛷","🏂","🪂","🤸","🤼","🤽","🤾",
            "🤺","⛹","🏋","🚴","🚵","🎪","🎭","🎨","🎬","🎤",
            "🎧","🎼","🎹","🥁","🪘","🎷","🎺","🪗","🎸","🎻",
            "🪕","🎲","♟","🎯","🎳","🎮","🕹","🎰","🧩","🪅",
            "🪩","🪆","🎗","🎟","🎫","🏅","🥇","🥈","🥉","🏆",
            "📷","📸","📹","🎥","📽","🎞","📺","📻","🎙","🎚",
            "🎛","📱","💻","⌨️","🖥","🖨","🖱","🖲","💾","💿"
        )
        // ── 交通與地點 ──
        val TRAVEL = listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🛵","🏍","🛺","🚲","🛴","🚨",
            "🚔","🚍","🚘","🚖","🛞","🚡","🚠","🚟","🚃","🚋",
            "🚞","🚝","🚄","🚅","🚈","🚂","🚆","🚇","🚊","🚉",
            "✈️","🛫","🛬","🛩","💺","🛰","🚀","🛸","🚁","🛶",
            "⛵","🚤","🛥","🛳","⛴","🚢","⚓","🪝","⛽","🚧",
            "🚦","🚥","🗼","🏰","🗽","🗿","🏛","⛪","🕌","🛕",
            "🕍","⛩","🕋","⛲","⛺","🌁","🌃","🏙","🌄","🌅",
            "🌆","🌇","🌉","🌌","🗻","🏔","⛰","🌋","🏕","🏖",
            "🏜","🏝","🏞","🗺","🧭","🏠","🏡","🏢","🏣","🏤",
            "🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰"
        )
        // ── 物品 ──
        val OBJECTS = listOf(
            "💡","🔦","🕯","🧯","💰","💴","💵","💶","💷","🪙",
            "💳","💎","⚖️","🪜","🧰","🪛","🔧","🔨","⚒","🛠",
            "⛏","🪚","🔩","⚙️","🪤","🧲","🔫","💣","🧨","🪓",
            "🔪","🗡","⚔️","🛡","🚬","⚰️","🪦","⚱️","🏺","🔮",
            "📿","🧿","🪬","💈","⚗️","🔭","🔬","🕳","🩹","🩺",
            "🩻","🩼","💊","💉","🩸","🧬","🦠","🧫","🧪","🌡",
            "🧹","🪠","🧺","🧻","🚽","🚰","🚿","🛁","🛀","🧼",
            "🪥","🪒","🧴","🧽","🪣","🧯","🛒","🎁","🎀","🪄",
            "🪅","🎈","🎏","🎐","🎑","🧧","🎊","🎉","🎎","🎍",
            "🏮","🪔","📦","📫","📪","📬","📭","📮","🗳","✏️",
            "✒️","🖋","🖊","🖌","🖍","📝","📁","📂","🗂","📅",
            "📆","🗒","🗓","📇","📈","📉","📊","📋","📌","📍",
            "📎","🖇","📏","📐","✂️","🗃","🗄","🗑","🔒","🔓",
            "🔏","🔐","🔑","🗝","🔔","🔕","📣","📢","🔈","🔉",
            "🔊","📡","🔍","🔎","💊","🧪","🧫","🧬","🩺","🩹"
        )
        // ── 符號 ──
        val SYMBOLS = listOf(
            "❤️","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤",
            "🔺","🔻","🔸","🔹","🔶","🔷","💠","🔘","🔳","🔲",
            "🏁","🚩","🎌","🏴","🏳","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️","🇹🇼","🇯🇵",
            "✅","❌","❓","❗","‼️","⁉️","💯","🔱","⚜️","〽️",
            "⚠️","🚸","🔰","♻️","✳️","❇️","🌐","💹","Ⓜ️","🅰️",
            "🅱️","🆎","🅾️","🆑","🆒","🆓","ℹ️","🆔","🆕","🆖",
            "🆗","🆘","🆙","🆚","🈁","🈂️","🈷️","🈶","🈯","🉐",
            "🈹","🈚","🈲","🉑","🈸","🈴","🈳","㊗️","㊙️","🈺",
            "♈","♉","♊","♋","♌","♍","♎","♏","♐","♑",
            "♒","♓","⛎","🔀","🔁","🔂","▶️","⏩","⏭","⏯",
            "◀️","⏪","⏮","🔼","⏫","🔽","⏬","⏸","⏹","⏺",
            "⏏","🎦","🔅","🔆","📶","🛜","📳","📴","♀️","♂️",
            "⚧️","✖️","➕","➖","➗","🟰","♾️","‼️","⁉️","❓",
            "❔","❕","❗","〰️","💱","💲","⚕️","♻️","⚜️","🔱",
            "📛","🔰","⭕","✅","☑️","✔️","❌","❎","➰","➿",
            "〽️","✳️","✴️","❇️","©️","®️","™️","#️⃣","*️⃣","0️⃣",
            "1️⃣","2️⃣","3️⃣","4️⃣","5️⃣","6️⃣","7️⃣","8️⃣","9️⃣","🔟"
        )
        // ── 旗幟 ──
        val FLAGS = listOf(
            "🇹🇼","🇯🇵","🇰🇷","🇨🇳","🇭🇰","🇲🇴","🇺🇸","🇬🇧","🇨🇦","🇦🇺",
            "🇳🇿","🇩🇪","🇫🇷","🇮🇹","🇪🇸","🇵🇹","🇳🇱","🇧🇪","🇨🇭","🇦🇹",
            "🇸🇪","🇳🇴","🇩🇰","🇫🇮","🇮🇸","🇮🇪","🇵🇱","🇨🇿","🇭🇺","🇷🇴",
            "🇧🇬","🇭🇷","🇷🇸","🇸🇰","🇸🇮","🇱🇹","🇱🇻","🇪🇪","🇺🇦","🇷🇺",
            "🇹🇷","🇬🇷","🇮🇱","🇸🇦","🇦🇪","🇮🇳","🇹🇭","🇻🇳","🇲🇾","🇸🇬",
            "🇮🇩","🇵🇭","🇲🇲","🇰🇭","🇱🇦","🇧🇩","🇵🇰","🇱🇰","🇳🇵","🇲🇳",
            "🇧🇷","🇦🇷","🇲🇽","🇨🇱","🇨🇴","🇵🇪","🇻🇪","🇪🇨","🇺🇾","🇵🇾",
            "🇧🇴","🇨🇷","🇵🇦","🇨🇺","🇩🇴","🇵🇷","🇯🇲","🇭🇹","🇹🇹","🇧🇸",
            "🇪🇬","🇿🇦","🇳🇬","🇰🇪","🇬🇭","🇪🇹","🇹🇿","🇲🇦","🇩🇿","🇹🇳",
            "🏁","🚩","🎌","🏴","🏳","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️","🇺🇳","🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        )
    }
}
