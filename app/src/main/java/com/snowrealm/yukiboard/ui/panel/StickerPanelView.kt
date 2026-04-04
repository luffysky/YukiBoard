package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.sticker.RemoteStickerPack
import com.snowrealm.yukiboard.sticker.StickerPackDownloadManager
import com.snowrealm.yukiboard.sticker.StickerPackDownloadManager.DownloadState
import com.snowrealm.yukiboard.sticker.StickerRepository
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.*

/**
 * 貼圖面板 — 同步 Insight Engine 後台上架的貼圖。
 *
 * 含下載管理：header 列顯示包名 + 下載按鈕/進度。
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
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        textAlign = Paint.Align.LEFT
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onStickerSelected: ((String) -> Unit)? = null

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

    /** 下載管理器（由 IMEService 注入） */
    var downloadManager: StickerPackDownloadManager? = null
        set(value) {
            field = value
            value?.onProgressUpdated = { progress ->
                downloadProgressMap[progress.packId] = progress
                post { invalidate() }
            }
            value?.onAllCompleted = { post { invalidate() } }
        }

    private var remotePacks: List<RemoteStickerPack> = emptyList()
    private var selectedPackIndex = 0
    private val packRects = mutableListOf<Pair<RectF, Int>>()
    private val stickerRects = mutableListOf<Pair<RectF, Int>>()
    private var downloadBtnRect: RectF? = null
    private var downloadAllBtnRect: RectF? = null

    private val downloadProgressMap = mutableMapOf<String, StickerPackDownloadManager.DownloadProgress>()

    private val headerH = 28f * density
    private val packBarH = 36f * density
    private val cornerR = 8f * density
    private val padding = 4f * density

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val loadedBitmaps = mutableMapOf<String, Bitmap?>()
    private val loadingUrls = mutableSetOf<String>()

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
        downloadBtnRect = null
        downloadAllBtnRect = null

        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (remotePacks.isEmpty()) {
            emptyPaint.color = theme.candidateTextColorInt
            emptyPaint.alpha = 120
            canvas.drawText("正在同步貼圖...", width / 2f, height / 2f, emptyPaint)
            return
        }

        // Header: 包名 + 下載按鈕
        drawHeader(canvas)

        // 貼圖網格
        drawStickerGrid(canvas)

        // 底部貼圖包列
        drawPackBar(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), headerH, bgPaint)

        val pack = remotePacks.getOrNull(selectedPackIndex)
        val packName = pack?.name ?: ""
        val packId = pack?.id ?: ""

        // 包名
        headerPaint.color = theme.candidateTextColorInt
        canvas.drawText(packName, 10f * density, headerH / 2f + headerPaint.textSize / 3f, headerPaint)

        val progress = downloadProgressMap[packId]
        val hasRemoteStickers = pack?.stickers?.any { !it.url.startsWith("local:") } == true

        if (hasRemoteStickers) {
            when (progress?.state) {
                DownloadState.DOWNLOADING -> {
                    // 進度條
                    val barLeft = width - 130f * density
                    val barRight = width - 10f * density
                    val barTop = 8f * density
                    val barBottom = headerH - 8f * density
                    val barRect = RectF(barLeft, barTop, barRight, barBottom)

                    // 背景
                    progressPaint.color = theme.keyColorInt
                    canvas.drawRoundRect(barRect, 4f * density, 4f * density, progressPaint)

                    // 進度
                    val pct = progress.progressPercent / 100f
                    val fillRect = RectF(barLeft, barTop, barLeft + barRect.width() * pct, barBottom)
                    progressPaint.color = theme.effectiveAccentColor()
                    progressPaint.alpha = 180
                    canvas.drawRoundRect(fillRect, 4f * density, 4f * density, progressPaint)
                    progressPaint.alpha = 255

                    // 百分比文字
                    btnPaint.color = theme.keyLabelColorInt
                    canvas.drawText("${progress.progressPercent}%", barRect.centerX(), barRect.centerY() + btnPaint.textSize / 3f, btnPaint)
                }
                DownloadState.COMPLETED -> {
                    btnPaint.color = theme.effectiveAccentColor()
                    canvas.drawText("已下載", width - 40f * density, headerH / 2f + btnPaint.textSize / 3f, btnPaint)
                }
                DownloadState.FAILED -> {
                    // 重試按鈕
                    drawDownloadBtn(canvas, "重試", width - 60f * density)
                }
                else -> {
                    // 下載按鈕 + 全部下載
                    drawDownloadBtn(canvas, "下載", width - 60f * density)
                    drawDownloadAllBtn(canvas, width - 120f * density)
                }
            }
        }
    }

    private fun drawDownloadBtn(canvas: Canvas, label: String, x: Float) {
        val btnW = 44f * density
        val btnH = 18f * density
        val rect = RectF(x, (headerH - btnH) / 2f, x + btnW, (headerH + btnH) / 2f)
        downloadBtnRect = rect

        cardPaint.color = theme.effectiveAccentColor()
        cardPaint.alpha = 40
        canvas.drawRoundRect(rect, 9f * density, 9f * density, cardPaint)
        cardPaint.alpha = 255

        // 邊框
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.effectiveAccentColor()
        cardPaint.alpha = 100
        canvas.drawRoundRect(rect, 9f * density, 9f * density, cardPaint)
        cardPaint.style = Paint.Style.FILL
        cardPaint.alpha = 255

        btnPaint.color = theme.effectiveAccentColor()
        canvas.drawText(label, rect.centerX(), rect.centerY() + btnPaint.textSize / 3f, btnPaint)
    }

    private fun drawDownloadAllBtn(canvas: Canvas, x: Float) {
        val btnW = 52f * density
        val btnH = 18f * density
        val rect = RectF(x, (headerH - btnH) / 2f, x + btnW, (headerH + btnH) / 2f)
        downloadAllBtnRect = rect

        cardPaint.color = theme.keyColorInt
        canvas.drawRoundRect(rect, 9f * density, 9f * density, cardPaint)

        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1f * density
        cardPaint.color = theme.candidateTextColorInt
        cardPaint.alpha = 60
        canvas.drawRoundRect(rect, 9f * density, 9f * density, cardPaint)
        cardPaint.style = Paint.Style.FILL
        cardPaint.alpha = 255

        btnPaint.color = theme.candidateTextColorInt
        canvas.drawText("全部下載", rect.centerX(), rect.centerY() + btnPaint.textSize / 3f, btnPaint)
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

            if (i == selectedPackIndex) {
                bgPaint.color = theme.candidateSelectedColorInt
                bgPaint.alpha = 50
                canvas.drawRect(rect, bgPaint)
                bgPaint.alpha = 255
            }

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

            // 小進度點
            val prog = downloadProgressMap[pack.id]
            if (prog != null) {
                val dotR = 3f * density
                val dotX = rect.right - 6f * density
                val dotY = barTop + 6f * density
                progressPaint.color = when (prog.state) {
                    DownloadState.COMPLETED -> 0xFF4CAF50.toInt()
                    DownloadState.DOWNLOADING -> theme.effectiveAccentColor()
                    DownloadState.FAILED -> 0xFFFF5252.toInt()
                    else -> 0x00000000
                }
                if (prog.state != DownloadState.IDLE) {
                    canvas.drawCircle(dotX, dotY, dotR, progressPaint)
                }
            }
        }
    }

    private fun drawStickerGrid(canvas: Canvas) {
        val pack = remotePacks.getOrNull(selectedPackIndex) ?: return
        val stickers = pack.stickers
        val cols = pack.columnsPerRow.coerceIn(2, 8)
        val cellW = width / cols.toFloat()
        val cellH = cellW
        val gridTop = headerH
        val gridBottom = height - packBarH

        canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), gridBottom)

        stickers.forEachIndexed { i, sticker ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = gridTop + row * cellH + scrollOffsetY

            if (y + cellH < gridTop || y > gridBottom) return@forEachIndexed

            val rect = RectF(
                x + padding, y + padding,
                x + cellW - padding, y + cellH - padding
            )
            stickerRects.add(rect to i)

            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            if (sticker.url.startsWith("local:")) {
                val label = sticker.url.removePrefix("local:")
                textPaint.color = theme.candidateTextColorInt
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = 13f * density
                canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
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
                    loadingPaint.color = theme.candidateTextColorInt
                    loadingPaint.alpha = 60
                    canvas.drawText("...", rect.centerX(), rect.centerY() + 6f * density, loadingPaint)
                    loadingPaint.alpha = 255
                }
            }
        }

        canvas.restore()
    }

    private fun getCachedBitmap(url: String): Bitmap? {
        if (url.isEmpty()) return null
        loadedBitmaps[url]?.let { return it }
        if (url in loadingUrls) return null
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
                if (isDragging && event.y > headerH && event.y < height - packBarH) {
                    scrollOffsetY += dy
                    clampScroll()
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 下載當前包
                    downloadBtnRect?.let { rect ->
                        if (rect.contains(event.x, event.y)) {
                            val pack = remotePacks.getOrNull(selectedPackIndex)
                            if (pack != null) downloadManager?.downloadPack(pack)
                            performClick()
                            return true
                        }
                    }

                    // 全部下載
                    downloadAllBtnRect?.let { rect ->
                        if (rect.contains(event.x, event.y)) {
                            downloadManager?.downloadAllPacks()
                            performClick()
                            return true
                        }
                    }

                    // 貼圖包切換
                    val packHit = packRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (packHit != null) {
                        selectedPackIndex = packHit.second
                        scrollOffsetY = 0f
                        invalidate()
                        performClick()
                        return true
                    }

                    // 貼圖選擇
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
        val visibleH = height - packBarH - headerH
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
