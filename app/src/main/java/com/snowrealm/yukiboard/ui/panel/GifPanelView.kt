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
 * GIF 搜尋面板 — 搜尋 + 網格顯示 GIF。
 *
 * MVP 架構：
 * - 搜尋框輸入關鍵字
 * - 預設顯示趨勢 GIF 分類標籤
 * - 實際 GIF 載入由外部 GifProvider 處理（Giphy API / Tenor API）
 * - 點擊 GIF 透過 InputConnection.commitContent() 插入
 */
class GifPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 搜尋回呼 — 外部接 Giphy/Tenor API */
    var onSearch: ((query: String, callback: (List<GifItem>) -> Unit) -> Unit)? = null

    /** 選擇 GIF 回呼 — 外部透過 InputConnection.commitContent 插入 */
    var onGifSelected: ((GifItem) -> Unit)? = null

    private var searchQuery = ""
    private var gifResults = listOf<GifItem>()
    private val tagRects = mutableListOf<Pair<RectF, String>>()
    private val gifRects = mutableListOf<Pair<RectF, Int>>()

    private val searchBarH = 36f * density
    private val tagBarH = 30f * density
    private val gifCellSize get() = (width / 3f).coerceAtLeast(80f * density)
    private val cornerR = 8f * density

    private val trendingTags = listOf(
        "開心", "悲傷", "謝謝", "OK", "LOL", "愛心",
        "貓", "狗", "跳舞", "拍手", "驚訝", "生氣"
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        tagRects.clear()
        gifRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 搜尋列
        drawSearchBar(canvas)

        if (gifResults.isEmpty()) {
            // 趨勢標籤
            drawTrendingTags(canvas)

            // 提示
            textPaint.color = theme.candidateTextColorInt
            textPaint.alpha = 100
            canvas.drawText(
                if (onSearch == null) "GIF 引擎未連接 — 請在設定中配置 API Key" else "點擊標籤或輸入關鍵字搜尋 GIF",
                width / 2f, height / 2f + 20 * density, textPaint
            )
            textPaint.alpha = 255
        } else {
            // GIF 網格（顯示標題佔位，實際圖片需 Glide/Coil 載入）
            drawGifGrid(canvas)
        }
    }

    private fun drawSearchBar(canvas: Canvas) {
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), searchBarH, bgPaint)

        // 搜尋框
        val searchRect = RectF(8 * density, 4 * density, width - 8 * density, searchBarH - 4 * density)
        cardPaint.color = theme.keyColorInt
        canvas.drawRoundRect(searchRect, cornerR, cornerR, cardPaint)

        searchPaint.color = if (searchQuery.isEmpty()) {
            theme.candidateTextColorInt.and(0x00FFFFFF).or(0x80000000.toInt())
        } else {
            theme.keyLabelColorInt
        }
        val displayText = searchQuery.ifEmpty { "🔍 搜尋 GIF..." }
        canvas.drawText(displayText, searchRect.left + 12 * density, searchRect.centerY() + searchPaint.textSize / 3f, searchPaint)
    }

    private fun drawTrendingTags(canvas: Canvas) {
        val startY = searchBarH + 8 * density
        val tagH = 28f * density
        val tagPad = 6f * density
        var x = 8f * density
        var y = startY

        trendingTags.forEach { tag ->
            val tagW = tagPaint.measureText(tag) + 24 * density
            if (x + tagW > width - 8 * density) {
                x = 8f * density
                y += tagH + tagPad
            }
            val rect = RectF(x, y, x + tagW, y + tagH)
            tagRects.add(rect to tag)

            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, tagH / 2, tagH / 2, cardPaint)

            tagPaint.color = theme.candidateTextColorInt
            canvas.drawText(tag, rect.centerX(), rect.centerY() + tagPaint.textSize / 3f, tagPaint)

            x += tagW + tagPad
        }
    }

    private fun drawGifGrid(canvas: Canvas) {
        val startY = searchBarH + 4 * density
        val cols = 3
        val cellW = width / cols.toFloat()
        val cellH = gifCellSize * 0.75f

        gifResults.forEachIndexed { i, gif ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = startY + row * cellH

            if (y > height) return@forEachIndexed

            val rect = RectF(x + 2 * density, y + 2 * density, x + cellW - 2 * density, y + cellH - 2 * density)
            gifRects.add(rect to i)

            // 佔位卡片（實際使用時用 Glide 載入縮圖）
            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 顯示標題
            textPaint.color = theme.candidateTextColorInt
            textPaint.textSize = 10f * density
            val label = gif.title.take(12)
            canvas.drawText(label, rect.centerX(), rect.centerY(), textPaint)
            textPaint.textSize = 13f * density

            // GIF 標記
            tagPaint.color = theme.candidateSelectedColorInt
            canvas.drawText("GIF", rect.centerX(), rect.centerY() + 14 * density, tagPaint)
        }
    }

    /** 外部呼叫：設定搜尋文字並執行搜尋 */
    fun search(query: String) {
        searchQuery = query
        onSearch?.invoke(query) { results ->
            gifResults = results
            post { invalidate() }
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // 標籤
            val tagHit = tagRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (tagHit != null) {
                search(tagHit.second)
                performClick()
                return true
            }

            // GIF 項目
            val gifHit = gifRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (gifHit != null && gifHit.second < gifResults.size) {
                onGifSelected?.invoke(gifResults[gifHit.second])
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

    /** GIF 項目資料 */
    data class GifItem(
        val id: String,
        val title: String,
        val previewUrl: String,
        val fullUrl: String,
        val contentUri: Uri? = null
    )
}
