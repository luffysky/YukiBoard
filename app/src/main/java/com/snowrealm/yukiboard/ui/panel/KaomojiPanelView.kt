package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 顔文字選擇面板 — Tab 橫向捲動 + 內容直向捲動。
 */
class KaomojiPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onKaomojiSelected: ((String) -> Unit)? = null

    private val categories = listOf(
        KaomojiCat("開心", HAPPY),
        KaomojiCat("悲傷", SAD),
        KaomojiCat("生氣", ANGRY),
        KaomojiCat("愛", LOVE),
        KaomojiCat("驚訝", SURPRISE),
        KaomojiCat("可愛", CUTE),
        KaomojiCat("打招呼", GREETING),
        KaomojiCat("動作", ACTION),
        KaomojiCat("動物", ANIMAL),
        KaomojiCat("食物", FOOD),
        KaomojiCat("音樂", MUSIC),
        KaomojiCat("魔法", MAGIC),
        KaomojiCat("耍廢", LAZY),
        KaomojiCat("對不起", SORRY)
    )

    private var selectedCatIndex = 0
    private val catRects = mutableListOf<Pair<RectF, Int>>()
    private val itemRects = mutableListOf<Pair<RectF, String>>()

    private val catBarHeight = 32f * density
    private val itemHeight = 38f * density

    // Tab 橫向捲動
    private var tabScrollX = 0f
    private var tabLastTouchX = 0f
    private var isTabDragging = false

    // 內容直向捲動
    private var contentScrollY = 0f
    private var contentLastTouchY = 0f
    private var isContentDragging = false
    private var touchStartY = 0f
    private var touchStartX = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        catRects.clear()
        itemRects.clear()

        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawTabBar(canvas)
        drawContent(canvas)
    }

    private fun drawTabBar(canvas: Canvas) {
        // Tab 背景
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), catBarHeight, bgPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), catBarHeight)

        val tabPad = 14f * density
        var x = tabScrollX + 6 * density

        categories.forEachIndexed { i, cat ->
            val textW = catPaint.measureText(cat.name)
            val tabW = textW + tabPad * 2
            val rect = RectF(x, 0f, x + tabW, catBarHeight)
            catRects.add(rect to i)

            if (i == selectedCatIndex) {
                // 選中底色
                bgPaint.color = theme.candidateSelectedColorInt
                bgPaint.alpha = 40
                canvas.drawRoundRect(
                    RectF(rect.left + 2 * density, 3 * density, rect.right - 2 * density, catBarHeight - 3 * density),
                    4 * density, 4 * density, bgPaint
                )
                bgPaint.alpha = 255

                // 底部指示條
                indicatorPaint.color = theme.candidateSelectedColorInt
                val indH = 2.5f * density
                canvas.drawRoundRect(
                    RectF(rect.left + 6 * density, catBarHeight - indH - 1 * density, rect.right - 6 * density, catBarHeight - 1 * density),
                    indH, indH, indicatorPaint
                )
            }

            catPaint.color = if (i == selectedCatIndex) theme.candidateSelectedColorInt else theme.candidateTextColorInt
            canvas.drawText(cat.name, rect.centerX(), catBarHeight * 0.62f, catPaint)

            x += tabW + 2 * density
        }

        canvas.restore()
    }

    private fun drawContent(canvas: Canvas) {
        val items = categories[selectedCatIndex].items
        val cols = 2
        val colWidth = width / cols.toFloat()
        val contentTop = catBarHeight

        canvas.save()
        canvas.clipRect(0f, contentTop, width.toFloat(), height.toFloat())

        val startY = contentTop + 4 * density + contentScrollY

        items.forEachIndexed { i, kaomoji ->
            val col = i % cols
            val row = i / cols
            val x = col * colWidth
            val y = startY + row * itemHeight

            if (y + itemHeight < contentTop || y > height) return@forEachIndexed

            val rect = RectF(x + 4 * density, y, x + colWidth - 4 * density, y + itemHeight)
            itemRects.add(rect to kaomoji)

            // hover 效果底色
            textPaint.color = theme.keyLabelColorInt
            textPaint.textSize = 13f * density

            // 自動縮小過長的顔文字
            val textW = textPaint.measureText(kaomoji)
            if (textW > rect.width() - 8 * density) {
                textPaint.textSize = 11f * density
            }

            canvas.drawText(kaomoji, rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
            textPaint.textSize = 13f * density

            // 分隔線
            if (col == 0) {
                bgPaint.color = theme.candidateTextColorInt
                bgPaint.alpha = 15
                canvas.drawRect(colWidth - 0.5f * density, y + 4 * density, colWidth + 0.5f * density, y + itemHeight - 4 * density, bgPaint)
                bgPaint.alpha = 255
            }
        }

        // 捲動指示（如果內容超過可視區域）
        val totalRows = (items.size + cols - 1) / cols
        val totalContentH = totalRows * itemHeight
        val visibleH = height - contentTop
        if (totalContentH > visibleH) {
            val scrollRatio = (-contentScrollY) / (totalContentH - visibleH)
            val barH = (visibleH * visibleH / totalContentH).coerceIn(20 * density, visibleH * 0.5f)
            val barY = contentTop + scrollRatio * (visibleH - barH)
            bgPaint.color = theme.candidateTextColorInt
            bgPaint.alpha = 30
            canvas.drawRoundRect(
                RectF(width - 4 * density, barY, width - 1.5f * density, barY + barH),
                2 * density, 2 * density, bgPaint
            )
            bgPaint.alpha = 255
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x
                touchStartY = y
                tabLastTouchX = x
                contentLastTouchY = y
                isTabDragging = false
                isContentDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - touchStartX
                val dy = y - touchStartY

                if (!isTabDragging && !isContentDragging) {
                    if (touchStartY < catBarHeight && Math.abs(dx) > 8 * density) {
                        isTabDragging = true
                    } else if (touchStartY >= catBarHeight && Math.abs(dy) > 8 * density) {
                        isContentDragging = true
                    }
                }

                if (isTabDragging) {
                    tabScrollX += x - tabLastTouchX
                    clampTabScroll()
                    tabLastTouchX = x
                    invalidate()
                } else if (isContentDragging) {
                    contentScrollY += y - contentLastTouchY
                    clampContentScroll()
                    contentLastTouchY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isTabDragging || isContentDragging) {
                    isTabDragging = false
                    isContentDragging = false
                    performClick()
                    return true
                }

                // Tab 點擊
                if (y < catBarHeight) {
                    val hit = catRects.firstOrNull { it.first.contains(x, y) }
                    if (hit != null) {
                        selectedCatIndex = hit.second
                        contentScrollY = 0f
                        invalidate()
                        performClick()
                        return true
                    }
                }

                // 顔文字點擊
                val contentY = y
                val hit = itemRects.firstOrNull { it.first.contains(x, contentY) }
                if (hit != null) {
                    onKaomojiSelected?.invoke(hit.second)
                    performClick()
                    return true
                }

                performClick()
                return true
            }
        }
        return true
    }

    private fun clampTabScroll() {
        val tabPad = 14f * density
        var totalW = 6 * density
        categories.forEach { cat ->
            totalW += catPaint.measureText(cat.name) + tabPad * 2 + 2 * density
        }
        val minScroll = -(totalW - width).coerceAtLeast(0f)
        tabScrollX = tabScrollX.coerceIn(minScroll, 0f)
    }

    private fun clampContentScroll() {
        val items = categories[selectedCatIndex].items
        val cols = 2
        val totalRows = (items.size + cols - 1) / cols
        val totalH = totalRows * itemHeight
        val visibleH = height - catBarHeight
        val minScroll = -(totalH - visibleH).coerceAtLeast(0f)
        contentScrollY = contentScrollY.coerceIn(minScroll, 0f)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private data class KaomojiCat(val name: String, val items: List<String>)

    companion object {
        val HAPPY = listOf(
            "(◕‿◕)", "(｡◕‿◕｡)", "(◠‿◠)", "(✿◠‿◠)", "(◕ᴗ◕✿)",
            "\\(^▽^)/", "(＾▽＾)", "(*^▽^*)", "(≧▽≦)", "(＾◇＾)",
            "٩(◕‿◕｡)۶", "ヽ(>∀<☆)ノ", "☆*:.｡.o(≧▽≦)o.｡.:*☆",
            "(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧", "(*≧ω≦)", "(っ˘ω˘ς)", "(◕‿◕)♡",
            "(ᵔᴥᵔ)", "( ◜‿◝ )", "٩(^‿^)۶", "٩(˃̶͈̀௰˂̶͈́)و",
            "(ノ´ヮ`)ノ*: ・ﾟ✧", "(☆▽☆)", "(✪▽✪)", "(*˘︶˘*).｡.:*♡",
            "ヽ(o＾▽＾o)ノ", "(ˊᗜˋ*)", "(•‿•)", "ᐠ( ᐛ )ᐟ",
            "~(˘▽˘)~", "(✯◡✯)", "(*´∀`*)", "( ﾟ∀ﾟ)ﾉ",
            "(ᗒᗨᗕ)", "(˶‾᷄ ⁻̫ ‾᷅˵)", "(⌐■_■)", "( ˙▿˙ )",
            "ᕕ( ᐛ )ᕗ", "(ↂ_ↂ)", "( ˶ˆ᎑ˆ˵ )", "(✌゚∀゚)☞",
            "(*ˊᗜˋ*)/", "(ᵔ◡ᵔ)", "( ˃̶̤́ ꒳ ˂̶̤̀ )", "(*´▽`*)",
            "ʕ ꈍᴥꈍʔ", "(✿╹◡╹)", "(ﾟ▽ﾟ)/", "٩(♡ε♡ )۶",
            "( ⸝⸝•ᴗ•⸝⸝ )", "(˃ᆺ˂)", "(≧◡≦)", "(ˊo̴̶̷̤ᴗo̴̶̷̤ˋ)"
        )
        val SAD = listOf(
            "(╥_╥)", "(T_T)", "(；_；)", "(ಥ_ಥ)", "(〒︿〒)",
            "(´;ω;`)", "( ´•̥̥̥ω•̥̥̥` )", "(ノ_<。)", "(╯︵╰,)", "(┬┬﹏┬┬)",
            "( ; ω ; )", "(இ﹏இ)", "(｡•́︿•̀｡)", "(T▽T)", "·°(°˃̣̣̥᷄⌓˂̣̣̥᷅ )°·",
            "(´；ω；`)", "(ᗒᗣᗕ)՞", "( ´ ; ω ; ` )", "(ಥ﹏ಥ)", "( ˃̣̣̥⌓˂̣̣̥)",
            "( ´•︵•` )", "(;´༎ຶД༎ຶ`)", "°(ಗдಗ。)°", "( ´ • ̥̥̥ω• ̥̥̥` )",
            "(｡ŏ﹏ŏ)", "(⌯˃̶᷄ ﹏ ˂̶᷄⌯)", "｡ﾟ(ﾟ´Д`ﾟ)ﾟ｡", "(╥ᆺ╥；)",
            "ˊ·̭ˏ", "(ᵕ̣̣̣̣̣̣﹏ᵕ̣̣̣̣̣̣)", "(´;︵;`)", "(　；∀；)",
            "( ╥ω╥ )", "(ᗒᗩᗕ)", "( ˘̩̩̩ε˘̩ƪ)", "(;ω;)",
            "(╯_╰)", "(ᵕ᷄≀ ̠ᵕ᷅)", "(˚ ˃̣̣̥᷄⌓˂̣̣̥᷅ )", "(⸝⸝⸝ᵒ̴̶̷̥́ ⌑ ᵒ̴̶̷̣̥̀⸝⸝⸝)",
            "( ´ ；ω；ʻ)", "( p_q)", "(ᗒᗣᗕ)᷅", "(〃＞＿＜;〃)",
            "(´°̥̥̥̥̥̥̥̥ω°̥̥̥̥̥̥̥̥`)", "( ⌯᷄ω⌯᷅)", "(;´Д`)", "(´;ω;`)"
        )
        val ANGRY = listOf(
            "(╬ Ò﹏Ó)", "(ノಠ益ಠ)ノ彡┻━┻", "(｀Д´)", "(>_<)", "(◣_◢)",
            "ヽ(`Д´)ノ", "（＃`Д´）", "(╯°□°)╯︵ ┻━┻", "（≧▼≦；）", "（￣^￣）",
            "( •̀ω•́ )σ", "(¬_¬ )", "( ` ω ´ )", "(눈_눈)", "ಠ_ಠ",
            "(ꐦ°᷅д°᷅)", "( ` ε ´ )", "凸(¬‿¬)", "(ﾉ`Д)ﾉ", "ψ(｀∇´)ψ",
            "( ˘•ω•˘ ).oิ", "(◞‸◟；)", "(¬▂¬)", "ᕙ(⇀‸↼‶)ᕗ",
            "(ﾒ` ﾛ ´)︻デ═一", "щ(ﾟДﾟщ)", "(ᗒᗩᗕ)", "(ꐦ°д°)ꐦ",
            "( ≖ᴗ≖✿)", "ᕦ(ò_óˇ)ᕤ", "( ` ᗜ ´ )", "(ꐦ ´͈ ᗨ `͈ )",
            "┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻", "(메765765메)", "(ꐦ○_○ꐦ)",
            "(#`Д´)", "( ` д ´ )", "(‡▼益▼)", "(≖̀‸≖́)✧",
            "( ˃̣̣̥᷄ ⁻̫ ˂̣̣̥᷅)", "(ꐦ •᷄ꇴ•᷅ )", "( •̀ᴗ•́ )و✧", "(`ε´)",
            "(ꐦ ◜◡‾)", "(`皿´)＃", "（▼皿▼＃）", "凸(｀⌒´メ)凸"
        )
        val LOVE = listOf(
            "(◕‿◕)♡", "(♥ω♥*)", "(●♡∀♡)", "(„ಡωಡ„)", "(♡˙︶˙♡)",
            "(灬♥ω♥灬)", "( ˘ ³˘)♥", "(◍•ᴗ•◍)❤", "♡(◡‿◡)", "(๑♡⌓♡๑)",
            "(*♡∀♡)", "σ(≧ε≦σ) ♡", "(ღ˘⌣˘ღ)", "(´,,•ω•,,)♡", "♡＼(￣▽￣)／♡",
            "(っ˘з(˘⌣˘ )", "( ˘⌣˘)♡(˘⌣˘ )", "(♡ >ω< ♡)", "(ɔˆ ³(ˆ⌣ˆc)",
            "₊˚ʚ ᗢ₎ₐ ゚.", "(⺣◡⺣)♡*", "♡(ˆ‿ˆ✿)", "(人´∀`*)", "( ᵕ̤ᴗᵕ̤ )♡",
            "(´♡‿♡`)", "♡˖꒰ᵕ༚ᵕ⑅꒱", "(♥‿♥)", "(○♡∀♡)ﾉ", "ε(*´・∀・`)з゙",
            "(˵ ˘ ³˘(˵ ˘ ε ˘˵)˘ ³˘ ˵)", "♡(> ਊ <)♡",
            "(ˊ˘ˋ*)♡", "(*˘˘*).｡.:*♡", "(ᴗ˳ᴗ)♡", "(˶ˆ꒳ˆ˵)♡",
            "❤(ˆ‿ˆ)❤", "♡(ŐωŐ人)", "(✿ ♥‿♥)", "( ⸝⸝´꒳`⸝⸝)♡",
            "(*♡ᴗ♡*)", "(ˆ ε ˆ♡)", "♡(> ᴗ •)ゝ", "( ˙꒳​˙ )♡",
            "(人♡ω♡)", "♡(*´∀`*)人(*´∀`*)♡", "（´・`♡）", "(♡´艸`)"
        )
        val SURPRISE = listOf(
            "(⊙_⊙)", "Σ(°△°|||)", "(°o°)", "(O_O)", "Σ(ﾟДﾟ)",
            "(ﾟロﾟ)", "( ꒪Д꒪)", "(⊙﹏⊙)", "(*ﾟﾛﾟ)", "(゜o゜;",
            "∑(O_O；)", "w(°ｏ°)w", "(⊙△⊙✿)", "(°△°|||)", "Σ(°Д°;)",
            "(⊙_◎)", "( ˙▿˙ )", "Σ('◉⌓◉')", "(☉_☉)", "(°ロ°) !",
            "∑(; °Д°)", "(ﾟ◇ﾟ)ﾉ", "(ꏿ_ꏿ;)", "( º﹃º )", "(⊙ˍ⊙)",
            "w(ﾟoﾟ)w", "ΣΣ(ﾟДﾟ;)", "(⊙_⊙;)", "(°0°)", "!!(°▽°)!!",
            "∑(ΦдΦ)!!", "(⊙ω⊙)", "(˙◁˙)", "Σ(ꙨꙨ)",
            "(꒪⌓꒪)", "(☍﹏⁰)。", "( Д ) ﾟ ﾟ", "(´⊙ω⊙`)！",
            "(ʘᗩʘ')", "(*ﾟﾛﾟ)ﾉ", "Σ(ノд`ﾉ)", "(°△ °;)",
            "∑(O_O;)", "!(°▽°;)", "(ΟΔΟ;;)", "(ﾟ∀ﾟ ;)"
        )
        val CUTE = listOf(
            "(◕ᴗ◕✿)", "(｡♥‿♥｡)", "(◠‿◠✿)", "(⁎⁍̴̛ᴗ⁍̴̛⁎)", "(ㅅ´ ˘ `)",
            "(・ω・)", "(。・ω・。)", "( ˘•ω•˘ )", "(✧ω✧)", "(●ᴗ●)",
            "(♡ᵕ̤ᴗᵕ̤)", "꒰ ꒡⌓꒡꒱", "(ᵔᴥᵔ)", "（´・ω・`）", "(˶ᵔ ᵕ ᵔ˶)",
            "(⑅˘͈ ᵕ ˘͈ )", "(✿˵◕‿◕˵)", "(◍•ᴗ•◍)", "(˶˃ ᵕ ˂˶)", "(*ˊᗜˋ*)",
            "꒰˘̩̩̩⌣˘̩̩̩๑꒱♡", "(⺣◡⺣)", "(ᵕ̤ᴗᵕ̤)", "(✿ᴗ✿)", "(⸝⸝⸝°_°⸝⸝⸝)",
            "(*˘꒳˘*)", "(ˊo̴̶̷̤ ᴗ o̴̶̷̤ˋ)", "( ⸝⸝•ᴗ•⸝⸝ )੭⁾⁾", "(ᐢ..ᐢ)",
            "(◕દ◕)", "ꔛ(⸝⸝> ᴗ•⸝⸝)", "( ˶ˆ꒳ˆ˵ )", "(꒪˙꒳˙꒪)",
            "(˶ᵔᵕᵔ˶)", "(ᵕ̤ᴗᵕ̤)", "(⑅•ᴗ•⑅)◜..°♡", "(*ᵕ̤ᴗᵕ̤)",
            "(⊃。•́‿•̀。)⊃", "(っˆ‿ˆ)っ", "(*˘˘*)", "( *ˊᵕˋ)",
            "(o˘◡˘o)", "( ¤̴̶̷̤́ ‧̫̮ ¤̴̶̷̤̀ )", "(˵¯͒〰¯͒˵)", "(*•̀ᴗ•́*)و",
            "(*^ω^*)", "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)", "(✿°•∀°•)", "(ˊᵕˋ)♡"
        )
        val GREETING = listOf(
            "(^-^)ノ", "(^_^)/", "ヾ(^∇^)", "(*・ω・)ﾉ", "(✿◠‿◠)ノ",
            "ヽ(・∀・)ﾉ", "(=^・ω・^)ノ", "~(˘▾˘~)", "(づ￣ ³￣)づ", "(つ≧▽≦)つ",
            "ヾ(≧▽≦*)o", "(*^‿^*)/", "(ﾉ´ヮ`)ﾉ*: ・ﾟ✧", "⊂(•‿•⊂ )", "(.❛ ᴗ ❛.)",
            "ヾ(•ω•`)o", "(。・ω・)ノ", "(●'◡'●)ﾉ", "(*˙˘˙)♡", "( ˶ˆ꒳ˆ˵ )ノ",
            "(^▽^)ゞ", "( ´ ▽ ` )ﾉ", "ヽ(✿ﾟ▽ﾟ)ノ", "(◕‿◕)ノ", "٩(ˊᗜˋ*)و ♡",
            "(*´▽`*)", "(^ω^)ノﾞ", "ヾ(＾-＾)ノ", "( ◕◡◕ )っ", "(ノ^_^)ノ",
            "ヾ(˙❥˙)ﾉ", "(ノ・ω・)ノ", "(* ˃ ᵕ ˂ )b", "( ◕ᴗ◕ )ノ",
            "(*°▽°*)", "(˶‾᷄ ⁻̫ ‾᷅˵)ノ", "(ﾉ≧∀≦)ﾉ", "(。>‿‿<。)ノ",
            "(╭ ◜◝ ╮)ノ", "ヽ(^◇^*)/", "(✿^‿^)ノ", "(*◕ᴗ◕*)ノ",
            "(ᵔᴥᵔ)ノ", "ヾ(・ω・`)ノ"
        )
        val ACTION = listOf(
            "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "ᕦ(ò_óˇ)ᕤ", "(ง •_•)ง",
            "(ᗒᗩᗕ)", "┌( ˵ ❛ ^ ❛ )ᓄ", "✧*。٩(ˊᗜˋ*)و✧*。", "ᕙ(⇀‸↼‶)ᕗ",
            "(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧", "( ˘▽˘)っ♨", "( ˇ෴ˇ )", "( ╹▽╹ )σ",
            "(•̀ᴗ•́)و ̑̑", "₍₍ (ง ˙ω˙)ว ⁾⁾", "ε=ε=ε=┌(;*´Д`)ﾉ",
            "( -_-)旦~", "( ˘ω˘ )☕", "(*ﾟ∀ﾟ)=3", "(ง ˙o˙)ว", "~(>_<~)",
            "(ノ°∀°)ノ⌒・*:.｡", "(*≧▽≦)ﾉｼ))", "(╹ᗜ╹╬)", "ε≡≡ﾍ(´∀`)ﾉ",
            "( ˃̶͈̀ロ˂̶͈́)੭ꠥ⁾⁾", "(っ˘̩╭╮˘̩)っ", "( ᐛ )σ", "₍ᐢ..ᐢ₎♡",
            "٩(ˊᗜˋ*)و", "(ノ≧∀≦)ノ ‥…━━━★",
            "(ง ᵕ̤ᴗᵕ̤)ว♡", "┗(^∀^)┛", "(ノ*°▽°*)", "( ˙³˙)(ᵕ̤ᴗᵕ̤)",
            "ε=ε=ε=(ﾉ≧∇≦)ﾉ", "（*´∇`*）", "(ノ・∀・)ノ ⌒ ●", "╰(*°▽°*)╯",
            "┏(＾0＾)┛", "(⊃｡•́‿•̀｡)⊃━☆ﾟ.*・", "┌|∵|┘♪", "(ﾉ*ΦωΦ)ﾉ",
            "( •̀∀•́ )ง", "ε≡≡ﾍ( ´Д`)ﾉ"
        )
        val ANIMAL = listOf(
            "(=^・ω・^=)", "ฅ^•ﻌ•^ฅ", "(=ↀωↀ=)", "ʕ•ᴥ•ʔ", "ʕ ᵔᴥᵔ ʔ",
            "(・⊝・)", "🐾(=^‥^=)", "ε(´סּ︵סּ`)з", "( ̄(工) ̄)", "ʕ·ᴥ·ʔ",
            "(◉Θ◉)", "⊂((・▽・))⊃", "(≡^∇^≡)", "(*≧∀≦*)", "U・ᴥ・U",
            "ʕ •̀ ω •́ ʔ", "(=^-ω-^=)", "▼・ᴥ・▼", "(^・ω・^ )", "ʕ≧ᴥ≦ʔ",
            "₍ᐢ..ᐢ₎", "ʕ •ᴥ• ʔゝ☆", "(=①ω①=)", "ʕ→ᴥ←ʔ", "ᐠ( ᐛ )ᐟ",
            "ฅ(⌯͒• ɪ •⌯͒)ฅ", "(=^・^=)", "ʕ·͡ᴥ·ʔ", "(≡ω≡.)", "˙ᵕ˙(ᐢ.ᐢ)",
            "ʕ •̀ o •́ ʔ", "(₌ↀωↀ₌)", "ʕ ·ᴥ· ʔ⚐", "ʕ ꈍᴥꈍʔ",
            "ᘛ⁐̤ᕐᐷ", "( ⓛ ω ⓛ *)", ">(ᵔᴥᵔ)<", "ʕ•͡ᴥ•ʔ",
            "ʕ⁎̯͡⁎ʔ༄", "ᕱ⑅ᕱ", "(=^-ω-^=)zzZ", "(≡ˑ̺ˑ≡)",
            "ʕ̡̢̡̡̞͙̙̤̩͉̞̲̎ᴥ̡̢̞̞̝̹̬ʔ̢̞̞͙̫̻̣", "(•ˋ _ ˊ•)🐟"
        )
        val FOOD = listOf(
            "( ˘▽˘)っ♨", "( -_-)旦~", "( ˘ω˘ )☕", "(っ˘ڡ˘ς)",
            "🍙(・ω・)", "🍜( ˙꒳​˙ )", "(ノ´ρ`)ノ🍕", "( ・ω・)🍡",
            "🎂(ˆ‿ˆ)", "🍰( ˘ ³˘)", "(˘ρ˘*)", "( º﹃º )",
            "(*´﹃`*)", "🍣(*≧ω≦)", "☕(ˊᗜˋ*)", "🧋(◕ᴗ◕✿)",
            "(´,,•ω•,,)☕", "🍩( ◜‿◝ )", "(*´Д`)🍔", "🍦(✿◠‿◠)",
            "🍿(ᵔᴥᵔ)", "(・ω・)つ🍫", "🥤(˘▽˘)", "( •̀ᴗ•́ )و🍳",
            "(ง •_•)ง🔥🥩", "🍻(＾▽＾)", "(*˘▽˘*)🧁", "(◕‿◕)🍓",
            "🫖(˶‾᷄ ⁻̫ ‾᷅˵)", "(*ˊᗜˋ*)🍪", "( ᐛ )🌮", "🍛(≧▽≦)",
            "( ˘ω˘ )🍵", "🥟(*´∀`*)", "(っ˘ω˘ς)🍲", "🥐(◠‿◠)"
        )
        val MUSIC = listOf(
            "♪(´ε` )", "♫(ˊᗜˋ*)", "ヾ(´〇`)ﾉ♪♪♪", "♬♩♪◖(● o ●)◗♪♩♬",
            "(ˆ⺫ˆ๑)♪", "┌|∵|┘♪", "♩♪♫ヾ(^∇^)", "( ˘ω˘ )♫",
            "♪ヽ(^^ヽ)♪", "(ﾉ≧∀≦)ﾉ♪", "ー♪♪(o*゜∇゜)o～♪♪", "♬(ノ゜∇゜)ノ♩",
            "⁽⁽◝( ˙ ꒳ ˙ )◜⁾⁾♪", "( ¯ᒡ̱¯ )🎵", "♫₊˚.° ꕤ", "♪(*´∀`*)♪",
            "♩♩(◕‿◕)♩♩", "🎶( ˶ˆ꒳ˆ˵ )", "♬♪♫ ヾ(*・。・)ﾉ ♬♪♫", "( •̀ᄇ• ́)ﻭ✧🎵",
            "♫♪♬ ヽ(^◇^*)ノ ♬♪♫", "₍₍ ◝(●˙꒳˙●)◜ ₎₎♪", "(˶‾᷄ ⁻̫ ‾᷅˵)🎸",
            "♫(◕ᴗ◕✿)♫", "🎤(≧▽≦)🎤", "♪♬~(˘▽˘~)", "(⸝⸝•ᴗ•⸝⸝ )🎹",
            "♩♬♩ ( ◜‿◝ ) ♩♬♩", "🥁(ง •_•)ง", "♪(^∇^*)", "🎷( ˙▿˙ )",
            "♫₊˚ (ˊᗜˋ*) ˚₊♫", "♬♩ ヾ(⌐■_■)ノ ♩♬"
        )
        val MAGIC = listOf(
            "(⊃｡•́‿•̀｡)⊃━☆ﾟ.*・", "ﾟ+｡:.ﾟ(◕‿◕)ﾟ.:｡+ﾟ", "✧*。(ˊᗜˋ*)✧*。",
            "(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧", "☆*:.｡.o(≧▽≦)o.｡.:*☆", "(ノ´ヮ`)ノ*: ・ﾟ✧",
            "(*ﾟ▽ﾟ)ﾉ✨", "(ᗒᗨᗕ)🪄", "( ˃̶̤́ ꒳ ˂̶̤̀ )✨", "ﾟ✧(≧▽≦)✧ﾟ",
            "(*≧∀≦)ﾉ⌒*:・゚✧", "(∩^o^)⊃━☆", "⊂(◉‿◉)つ✨", ".*・｡ﾟ(ˊᗜˋ*)ﾟ｡・*.",
            "(⁎˃ᆺ˂)⁎✧", "✦(◕‿◕)✦", "( ˙꒳​˙ )ﾉ⌒✩", "ﾟ+.ﾟ(´▽`人)ﾟ+.ﾟ",
            "₊·*◟(⌯͒ ꒪̱ ꒪̱⌯)◜‧*・", "(ﾉ*ФωФ)ﾉ⌒*:・゚✧", "。゚(TヮT)゚。✨",
            "(*ˊᗜˋ*)⌒✩⌒✩", "(◕ᴗ◕✿)⊃━☆", "(ᗒᗨᗕ)⊃━☆ﾟ.*",
            "☆ﾟ.*･｡ (ˆ‿ˆ✿) ☆ﾟ.*･｡", "✧(•̀ᴗ•́)و✧", "⊹⊱(◕‿◕)⊰⊹",
            "(*ˊᗜˋ*)✨🔮", "(◜◡◝)⊃━☆ﾟ.*・", "✩°｡⋆⸜(ˊ˗ˋ)", "✧˖°⌖(ˆ‿ˆ)⌖°˖✧",
            "(ꈍ ᴗ ꈍ✿)✩⡱", "⊹₊⟡⟢(◕ᴗ◕)⟣⟡₊⊹"
        )
        val LAZY = listOf(
            "(￣o￣) . z Z", "(=_=)", "(¦3ꇤ[▓▓]", "(-_-) zzZ",
            "(∪｡∪)｡｡｡zzZ", "(￣ρ￣)..zzZZ", "(´〜`)ゞ", "(-.-)Zzz・・・・",
            "(ᴗ˳ᴗ)", "( ˘ω˘ )zzZ", "(¬_¬\")", "(ーωー)", "(≖ᴗ≖✿)",
            "(~_~;)", "(=ω=;)", "(´-ι_-｀)", "(´～`ヾ)", "(。-ω-)zzz",
            "_(´ཀ`」 ∠)_", "(゜-゜)", "(-ω-、)", "(눈_눈)", "( ˘ ˘ )",
            "₍₍ (ᵕ̤ᴗᵕ̤) ₎₎zzZ", "(◞‸◟ )", "(ᵕ̤⌓ᵕ̤)", "( ˃̶᷇ ‧̫ ˂̶᷆ )",
            "(˶‾᷄ ⁻̫ ‾᷅˵)z", "(ᴗ_ᴗ。)", "(⇀_⇀)", "(o´～`o)ﾉ",
            "٩(ˊᗜˋ*)و...zzZ", "( ̄▽ ̄*)ゞ", "(¬ε¬)", "(ᵕ᷄≀ ̠ᵕ᷅)",
            "(-_-;)・・・", "( ˙-˙ )", "(.﹒︣ᴗ﹒︣.)", "(´ρ`)zz"
        )
        val SORRY = listOf(
            "m(_ _)m", "(´・ω・`)", "ごめん(ᗒᗣᗕ)՞", "(;´∀`)",
            "ᵍᵒᵐᵉᶰ(ᵕ̤ᴗᵕ̤)", "(人´∩`)", "(>人<;)", "(´；ω；`)",
            "m(._.)m", "(；人；)", "(>_<。)", "(;_;)/~~~",
            "(ᵕ̣̣̣̣̣̣﹏ᵕ̣̣̣̣̣̣)ᵍᵒᵐᵉᶰ", "( ´ ；ω；ʻ)", "(人≧ω≦)", "(˃̥̩̥̀ᗢ˂̥̩̥́)",
            "(⸝⸝•́ ᴗ •̀⸝⸝)ᵍᵒᵐᵉᶰ", "ᵍᵒᵐᵉᶰ(╥_╥)", "( ´•̥̥̥ω•̥̥̥` )ᵍᵒᵐᵉᶰ", "m(__ __)m",
            "(◞‸◟)ᵍᵒᵐᵉᶰ", "ₛₒᵣᵣᵧ(ᗒᗣᗕ)՞", "(；ω；)", "(ᗒᗣᗕ)ᵍᵒᵐᵉᶰ",
            "( ˃̣̣̥⌓˂̣̣̥ )🙏", "(´;ω;`)ᵍᵒᵐᵉᶰ", "(>_< )ᵍᵒᵐᵉᶰ", "(ᵕ̣̣̣̣̣̣﹏ᵕ̣̣̣̣̣̣)🙏",
            "ᔆᵒʳʳʸ(•᷄ɞ•᷅ )", "🙇(╥_╥)", "(ᗒᗩᗕ)ᵍᵒᵐᵉᶰ", "(人д`)",
            "(*_ _)人", "ᵍᵒᵐᵉᶰᶰᵃˢᵃⁱ(;ω;)", "🙏(;´Д`)", "(>人<)ᵍᵒᵐᵉᶰ"
        )
    }
}
