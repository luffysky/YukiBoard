package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 貼圖面板 — 顯示貼圖包 + 貼圖網格。
 *
 * MVP 架構：
 * - 貼圖包列（底部分類列）
 * - 貼圖網格（3~4 列）
 * - 點擊透過 InputConnection.commitContent() 插入
 * - 貼圖來源：本地 assets 或下載的貼圖包
 */
class StickerPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val packPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 選擇貼圖回呼 */
    var onStickerSelected: ((StickerItem) -> Unit)? = null

    private var selectedPackIndex = 0
    private val packRects = mutableListOf<Pair<RectF, Int>>()
    private val stickerRects = mutableListOf<Pair<RectF, Int>>()

    private val packBarH = 32f * density
    private val cornerR = 8f * density

    /** 貼圖包列表 — 可由外部設定（從 assets 或下載） */
    var stickerPacks: List<StickerPack> = listOf(
        StickerPack("yuki", "雪凜", "❄️", listOf(
            StickerItem("yuki_hello", "你好", "stickers/yuki/hello.webp"),
            StickerItem("yuki_thanks", "謝謝", "stickers/yuki/thanks.webp"),
            StickerItem("yuki_happy", "開心", "stickers/yuki/happy.webp"),
            StickerItem("yuki_sad", "難過", "stickers/yuki/sad.webp"),
            StickerItem("yuki_angry", "生氣", "stickers/yuki/angry.webp"),
            StickerItem("yuki_love", "愛心", "stickers/yuki/love.webp"),
            StickerItem("yuki_sleep", "睡覺", "stickers/yuki/sleep.webp"),
            StickerItem("yuki_cry", "哭哭", "stickers/yuki/cry.webp"),
            StickerItem("yuki_laugh", "大笑", "stickers/yuki/laugh.webp"),
            StickerItem("yuki_shock", "驚嚇", "stickers/yuki/shock.webp"),
            StickerItem("yuki_wink", "眨眼", "stickers/yuki/wink.webp"),
            StickerItem("yuki_cool", "酷", "stickers/yuki/cool.webp")
        )),
        StickerPack("snow_animals", "雪地動物", "🐻‍❄️", listOf(
            StickerItem("polar_hi", "嗨", "stickers/animals/polar_hi.webp"),
            StickerItem("polar_ok", "OK", "stickers/animals/polar_ok.webp"),
            StickerItem("penguin_dance", "跳舞", "stickers/animals/penguin_dance.webp"),
            StickerItem("penguin_wave", "掰掰", "stickers/animals/penguin_wave.webp"),
            StickerItem("fox_smile", "微笑", "stickers/animals/fox_smile.webp"),
            StickerItem("fox_sleep", "睡覺", "stickers/animals/fox_sleep.webp"),
            StickerItem("rabbit_hop", "蹦蹦跳", "stickers/animals/rabbit_hop.webp"),
            StickerItem("rabbit_eat", "吃東西", "stickers/animals/rabbit_eat.webp"),
            StickerItem("owl_think", "思考", "stickers/animals/owl_think.webp")
        ))
    )
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        packRects.clear()
        stickerRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (stickerPacks.isEmpty()) {
            emptyPaint.color = theme.candidateTextColorInt
            emptyPaint.alpha = 120
            canvas.drawText("沒有貼圖包 — 可在設定中下載", width / 2f, height / 2f, emptyPaint)
            return
        }

        // 貼圖包列（底部）
        drawPackBar(canvas)

        // 貼圖網格
        drawStickerGrid(canvas)
    }

    private fun drawPackBar(canvas: Canvas) {
        val barTop = height - packBarH
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, barTop, width.toFloat(), height.toFloat(), bgPaint)

        val packW = width.toFloat() / stickerPacks.size.coerceAtLeast(1)
        stickerPacks.forEachIndexed { i, pack ->
            val rect = RectF(i * packW, barTop, (i + 1) * packW, height.toFloat())
            packRects.add(rect to i)

            if (i == selectedPackIndex) {
                bgPaint.color = theme.candidateSelectedColorInt
                bgPaint.alpha = 50
                canvas.drawRect(rect, bgPaint)
                bgPaint.alpha = 255
            }

            packPaint.color = if (i == selectedPackIndex) theme.candidateSelectedColorInt else theme.candidateTextColorInt
            canvas.drawText(pack.icon, rect.centerX(), rect.centerY() + packPaint.textSize / 3f, packPaint)
        }
    }

    private fun drawStickerGrid(canvas: Canvas) {
        val pack = stickerPacks.getOrNull(selectedPackIndex) ?: return
        val stickers = pack.stickers

        val cols = 4
        val cellW = width / cols.toFloat()
        val cellH = cellW  // 正方形
        val gridBottom = height - packBarH

        stickers.forEachIndexed { i, sticker ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = row * cellH

            if (y + cellH > gridBottom) return@forEachIndexed

            val rect = RectF(x + 4 * density, y + 4 * density, x + cellW - 4 * density, y + cellH - 4 * density)
            stickerRects.add(rect to i)

            // 佔位卡片（實際使用時用 Glide/Coil 載入 WebP）
            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 顯示名稱佔位
            textPaint.color = theme.candidateTextColorInt
            textPaint.textSize = 10f * density
            canvas.drawText(sticker.name, rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
            textPaint.textSize = 13f * density
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // 貼圖包切換
            val packHit = packRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (packHit != null) {
                selectedPackIndex = packHit.second
                invalidate()
                performClick()
                return true
            }

            // 貼圖選擇
            val stickerHit = stickerRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (stickerHit != null) {
                val pack = stickerPacks.getOrNull(selectedPackIndex)
                val sticker = pack?.stickers?.getOrNull(stickerHit.second)
                if (sticker != null) {
                    onStickerSelected?.invoke(sticker)
                    performClick()
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

    /** 貼圖包 */
    data class StickerPack(
        val id: String,
        val name: String,
        val icon: String,
        val stickers: List<StickerItem>
    )

    /** 單一貼圖 */
    data class StickerItem(
        val id: String,
        val name: String,
        val assetPath: String,
        val contentUri: Uri? = null
    )
}
