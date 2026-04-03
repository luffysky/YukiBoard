package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.sticker.RemoteStickerPack
import com.snowrealm.yukiboard.sticker.StickerRepository
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.*

/**
 * 貼圖面板 — 同步 Insight Engine 後台上架的貼圖。
 *
 * 使用方式與 Insight 私訊相同：
 * - 底部貼圖包列（封面縮圖切換）
 * - 上方貼圖網格（columnsPerRow 由後台設定）
 * - 點擊貼圖 → 插入 `[STICKER]:<url>` 到輸入框
 *
 * 圖片由 [StickerRepository.imageCache] 管理，
 * 首次載入時顯示佔位方塊，下載完成後自動重繪。
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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 點擊貼圖回呼，回傳 `[STICKER]:<url>` 格式字串 */
    var onStickerSelected: ((String) -> Unit)? = null

    /** 貼圖資料來源（由 IME Service 注入） */
    var repository: StickerRepository? = null
        set(value) {
            field = value
            remotePacks = value?.packs ?: emptyList()
            value?.onPacksUpdated = {
                remotePacks = value.packs
                post { invalidate() }
            }
            invalidate()
        }

    private var remotePacks: List<RemoteStickerPack> = emptyList()
    private var selectedPackIndex = 0
    private val packRects = mutableListOf<Pair<RectF, Int>>()
    private val stickerRects = mutableListOf<Pair<RectF, Int>>()

    private val packBarH = 36f * density
    private val cornerR = 8f * density
    private val padding = 4f * density

    // Bitmap 快取查詢用 scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loadedBitmaps = mutableMapOf<String, Bitmap?>()
    private val loadingUrls = mutableSetOf<String>()

    // 捲動支援
    private var scrollOffsetY = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        packRects.clear()
        stickerRects.clear()

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (remotePacks.isEmpty()) {
            emptyPaint.color = theme.candidateTextColorInt
            emptyPaint.alpha = 120
            canvas.drawText("正在同步貼圖...", width / 2f, height / 2f, emptyPaint)
            return
        }

        // 底部貼圖包列
        drawPackBar(canvas)

        // 貼圖網格
        drawStickerGrid(canvas)
    }

    private fun drawPackBar(canvas: Canvas) {
        val barTop = height - packBarH
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, barTop, width.toFloat(), height.toFloat(), bgPaint)

        val maxPackWidth = 56f * density
        val packW = (width.toFloat() / remotePacks.size.coerceAtLeast(1)).coerceAtMost(maxPackWidth)

        remotePacks.forEachIndexed { i, pack ->
            val left = i * packW
            val rect = RectF(left, barTop, left + packW, height.toFloat())
            packRects.add(rect to i)

            // 選中高亮
            if (i == selectedPackIndex) {
                bgPaint.color = theme.candidateSelectedColorInt
                bgPaint.alpha = 50
                canvas.drawRect(rect, bgPaint)
                bgPaint.alpha = 255
            }

            // 封面縮圖或名稱首字
            val coverBmp = if (pack.coverUrl.isNotEmpty()) getCachedBitmap(pack.coverUrl) else null
            if (coverBmp != null) {
                val iconSize = 24f * density
                val cx = rect.centerX() - iconSize / 2
                val cy = rect.centerY() - iconSize / 2
                val dst = RectF(cx, cy, cx + iconSize, cy + iconSize)
                canvas.drawBitmap(coverBmp, null, dst, bitmapPaint)
            } else {
                packPaint.color = if (i == selectedPackIndex)
                    theme.candidateSelectedColorInt else theme.candidateTextColorInt
                val label = pack.name.firstOrNull()?.toString() ?: "?"
                canvas.drawText(label, rect.centerX(), rect.centerY() + packPaint.textSize / 3f, packPaint)
            }
        }
    }

    private fun drawStickerGrid(canvas: Canvas) {
        val pack = remotePacks.getOrNull(selectedPackIndex) ?: return
        val stickers = pack.stickers
        val cols = pack.columnsPerRow.coerceIn(2, 8)
        val cellW = width / cols.toFloat()
        val cellH = cellW  // 正方形
        val gridTop = 0f
        val gridBottom = height - packBarH

        canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), gridBottom)

        stickers.forEachIndexed { i, sticker ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = gridTop + row * cellH + scrollOffsetY

            // 超出可視範圍就跳過
            if (y + cellH < gridTop || y > gridBottom) return@forEachIndexed

            val rect = RectF(
                x + padding, y + padding,
                x + cellW - padding, y + cellH - padding
            )
            stickerRects.add(rect to i)

            // 卡片底色
            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 貼圖圖片 or 本地佔位文字
            if (sticker.url.startsWith("local:")) {
                // 內建預設貼圖：顯示名稱文字
                val label = sticker.url.removePrefix("local:")
                textPaint.color = theme.candidateTextColorInt
                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                textPaint.textSize = 13f * density
                canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
                textPaint.textAlign = android.graphics.Paint.Align.LEFT
            } else {
                val bmp = getCachedBitmap(sticker.url)
                if (bmp != null) {
                    val imgPad = 4f * density
                    val dst = RectF(
                        rect.left + imgPad, rect.top + imgPad,
                        rect.right - imgPad, rect.bottom - imgPad
                    )
                    canvas.drawBitmap(bmp, null, dst, bitmapPaint)
                } else {
                    // 載入中佔位
                    loadingPaint.color = theme.candidateTextColorInt
                    loadingPaint.alpha = 60
                    canvas.drawText("...", rect.centerX(), rect.centerY() + 6f * density, loadingPaint)
                    loadingPaint.alpha = 255
                }
            }
        }

        canvas.restore()
    }

    /** 取得快取中的 Bitmap，若未載入則觸發非同步下載 */
    private fun getCachedBitmap(url: String): Bitmap? {
        if (url.isEmpty()) return null

        // 已在記憶體中
        loadedBitmaps[url]?.let { return it }

        // 已在載入中
        if (url in loadingUrls) return null

        // 觸發非同步載入
        loadingUrls.add(url)
        scope.launch {
            val bmp = repository?.getStickerBitmap(url)
            loadedBitmaps[url] = bmp
            loadingUrls.remove(url)
            post { invalidate() }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                if (Math.abs(dy) > 8f * density) isDragging = true
                if (isDragging && event.y < height - packBarH) {
                    scrollOffsetY += dy
                    clampScroll()
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 貼圖包切換
                    val packHit = packRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (packHit != null) {
                        selectedPackIndex = packHit.second
                        scrollOffsetY = 0f
                        invalidate()
                        performClick()
                        return true
                    }

                    // 貼圖選擇 → 遠端: [STICKER]:<url>（同 Insight 私訊格式）/ 本地: 文字
                    val stickerHit = stickerRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (stickerHit != null) {
                        val pack = remotePacks.getOrNull(selectedPackIndex)
                        val sticker = pack?.stickers?.getOrNull(stickerHit.second)
                        if (sticker != null) {
                            val text = if (sticker.url.startsWith("local:")) {
                                sticker.url.removePrefix("local:")
                            } else {
                                "[STICKER]:${sticker.url}"
                            }
                            onStickerSelected?.invoke(text)
                            performClick()
                        }
                        return true
                    }
                }
                return true
            }
        }
        return true
    }

    private fun clampScroll() {
        val pack = remotePacks.getOrNull(selectedPackIndex) ?: return
        val cols = pack.columnsPerRow.coerceIn(2, 8)
        val cellH = width / cols.toFloat()
        val rows = (pack.stickers.size + cols - 1) / cols
        val totalH = rows * cellH
        val visibleH = height - packBarH
        val minScroll = -(totalH - visibleH).coerceAtLeast(0f)
        scrollOffsetY = scrollOffsetY.coerceIn(minScroll, 0f)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun onDestroyView() {
        scope.cancel()
    }
}
