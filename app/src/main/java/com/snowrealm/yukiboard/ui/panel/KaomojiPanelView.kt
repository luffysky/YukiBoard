package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 顔文字選擇面板 — 分類顯示日式顔文字。
 */
class KaomojiPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        KaomojiCat("動物", ANIMAL)
    )

    private var selectedCatIndex = 0
    private val catRects = mutableListOf<Pair<RectF, Int>>()
    private val itemRects = mutableListOf<Pair<RectF, String>>()

    private val catBarHeight = 30f * density
    private val itemHeight = 36f * density

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

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 分類列
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
            canvas.drawText(cat.name, rect.centerX(), catBarHeight * 0.72f, catPaint)
        }

        // 顔文字列表（2 列並排）
        val items = categories[selectedCatIndex].items
        val colWidth = width / 2f
        val startY = catBarHeight + 4 * density

        items.forEachIndexed { i, kaomoji ->
            val col = i % 2
            val row = i / 2
            val x = col * colWidth
            val y = startY + row * itemHeight

            if (y + itemHeight < catBarHeight || y > height) return@forEachIndexed

            val rect = RectF(x + 4 * density, y, x + colWidth - 4 * density, y + itemHeight)
            itemRects.add(rect to kaomoji)

            textPaint.color = theme.keyLabelColorInt
            canvas.drawText(kaomoji, rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val catHit = catRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (catHit != null) {
                selectedCatIndex = catHit.second
                invalidate()
                performClick()
                return true
            }

            val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (itemHit != null) {
                onKaomojiSelected?.invoke(itemHit.second)
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

    private data class KaomojiCat(val name: String, val items: List<String>)

    companion object {
        val HAPPY = listOf(
            "(◕‿◕)", "(｡◕‿◕｡)", "(◠‿◠)", "(✿◠‿◠)", "(◕ᴗ◕✿)",
            "\\(^▽^)/", "(＾▽＾)", "(*^▽^*)", "(≧▽≦)", "(＾◇＾)",
            "٩(◕‿◕｡)۶", "ヽ(>∀<☆)ノ", "☆*:.｡.o(≧▽≦)o.｡.:*☆",
            "(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧", "(*≧ω≦)", "(っ˘ω˘ς)", "(◕‿◕)♡"
        )
        val SAD = listOf(
            "(╥_╥)", "(T_T)", "(；_；)", "(ಥ_ಥ)", "(〒︿〒)",
            "(´;ω;`)", "( ´•̥̥̥ω•̥̥̥` )", "(ノ_<。)", "(╯︵╰,)", "(┬┬﹏┬┬)",
            "( ; ω ; )", "(இ﹏இ)", "(｡•́︿•̀｡)", "(T▽T)", "·°(°˃̣̣̥᷄⌓˂̣̣̥᷅ )°·"
        )
        val ANGRY = listOf(
            "(╬ Ò﹏Ó)", "(ノಠ益ಠ)ノ彡┻━┻", "(｀Д´)", "(>_<)", "(◣_◢)",
            "ヽ(`Д´)ノ", "（＃`Д´）", "(╯°□°)╯︵ ┻━┻", "（≧▼≦；）", "（￣^￣）",
            "( •̀ω•́ )σ", "(¬_¬ )", "( ` ω ´ )", "(눈_눈)", "ಠ_ಠ"
        )
        val LOVE = listOf(
            "(◕‿◕)♡", "(♥ω♥*)", "(●♡∀♡)", "(„ಡωಡ„)", "(♡˙︶˙♡)",
            "(灬♥ω♥灬)", "( ˘ ³˘)♥", "(◍•ᴗ•◍)❤", "♡(◡‿◡)", "(๑♡⌓♡๑)",
            "(*♡∀♡)", "σ(≧ε≦σ) ♡", "(ღ˘⌣˘ღ)", "(´,,•ω•,,)♡", "♡＼(￣▽￣)／♡"
        )
        val SURPRISE = listOf(
            "(⊙_⊙)", "Σ(°△°|||)", "(°o°)", "(O_O)", "Σ(ﾟДﾟ)",
            "(ﾟロﾟ)", "( ꒪Д꒪)", "(⊙﹏⊙)", "(*ﾟﾛﾟ)", "(゜o゜;",
            "∑(O_O；)", "w(°ｏ°)w", "(⊙△⊙✿)", "(°△°|||)", "Σ(°Д°;)"
        )
        val CUTE = listOf(
            "(◕ᴗ◕✿)", "(｡♥‿♥｡)", "(◠‿◠✿)", "(⁎⁍̴̛ᴗ⁍̴̛⁎)", "(ㅅ´ ˘ `)",
            "(・ω・)", "(。・ω・。)", "( ˘•ω•˘ )", "(✧ω✧)", "(●ᴗ●)",
            "(♡ᵕ̤ᴗᵕ̤)", "꒰ ꒡⌓꒡꒱", "(ᵔᴥᵔ)", "（´・ω・`）", "(˶ᵔ ᵕ ᵔ˶)"
        )
        val GREETING = listOf(
            "(^-^)ノ", "(^_^)/", "ヾ(^∇^)", "(*・ω・)ﾉ", "(✿◠‿◠)ノ",
            "ヽ(・∀・)ﾉ", "(=^・ω・^)ノ", "~(˘▾˘~)", "(づ￣ ³￣)づ", "(つ≧▽≦)つ",
            "ヾ(≧▽≦*)o", "(*^‿^*)/", "(ﾉ´ヮ`)ﾉ*: ・ﾟ✧", "⊂(•‿•⊂ )", "(.❛ ᴗ ❛.)"
        )
        val ACTION = listOf(
            "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "ᕦ(ò_óˇ)ᕤ", "(ง •_•)ง",
            "(ᗒᗩᗕ)", "┌( ˵ ❛ ^ ❛ )ᓄ", "✧*。٩(ˊᗜˋ*)و✧*。", "ᕙ(⇀‸↼‶)ᕗ",
            "(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧", "( ˘▽˘)っ♨", "( ˇ෴ˇ )", "( ╹▽╹ )σ",
            "(•̀ᴗ•́)و ̑̑", "₍₍ (ง ˙ω˙)ว ⁾⁾", "ε=ε=ε=┌(;*´Д`)ﾉ"
        )
        val ANIMAL = listOf(
            "(=^・ω・^=)", "ฅ^•ﻌ•^ฅ", "(=ↀωↀ=)", "ʕ•ᴥ•ʔ", "ʕ ᵔᴥᵔ ʔ",
            "(・⊝・)", "🐾(=^‥^=)", "ε(´סּ︵סּ`)з", "( ̄(工) ̄)", "ʕ·ᴥ·ʔ",
            "(◉Θ◉)", "⊂((・▽・))⊃", "(≡^∇^≡)", "(*≧∀≦*)", "U・ᴥ・U"
        )
    }
}
