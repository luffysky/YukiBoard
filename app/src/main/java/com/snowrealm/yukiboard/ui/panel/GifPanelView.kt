package com.snowrealm.yukiboard.ui.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.gif.GifImageCache
import com.snowrealm.yukiboard.theme.KeyboardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * GIF 搜尋面板 — 搜尋 + 網格顯示 GIF 預覽圖。
 *
 * 使用 GifImageCache 非同步載入預覽 Bitmap，
 * 載入完成後自動 invalidate 更新畫面。
 */
class GifPanelView(context: Context) : View(context), PanelInputTarget {

    private val density = context.resources.displayMetrics.density
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageCache = GifImageCache(context)

    // 已載入的預覽 bitmap 快取（url → bitmap）
    private val loadedBitmaps = mutableMapOf<String, Bitmap?>()
    // 正在載入中的 URL
    private val loadingUrls = mutableSetOf<String>()

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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 搜尋回呼 — 外部接 Giphy/Tenor API */
    var onSearch: ((query: String, callback: (List<GifItem>) -> Unit) -> Unit)? = null

    /** 選擇 GIF 回呼 — 外部透過 InputConnection.commitContent 插入 */
    var onGifSelected: ((GifItem) -> Unit)? = null

    /** 由 Service 提供的 Dialog 顯示回呼（IME 內部 Canvas View 無法用 EditText） */
    var onShowDialog: ((android.app.AlertDialog) -> Unit)? = null

    private var searchQuery = ""
    private var gifResults = listOf<GifItem>()
    private var isLoading = false
    private var isInputMode = false
    private val inputBuffer = StringBuilder()
    private var composingHint = ""  // CJK 組字中顯示
    private var searchBarRect = RectF()
    private val tagRects = mutableListOf<Pair<RectF, String>>()
    private val gifRects = mutableListOf<Pair<RectF, Int>>()

    private var scrollOffset = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val searchBarH = 36f * density
    private val cornerR = 8f * density

    private val trendingTags = listOf(
        "開心", "悲傷", "謝謝", "OK", "LOL", "愛心",
        "貓", "狗", "跳舞", "拍手", "驚訝", "生氣"
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        val h = (200 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        tagRects.clear()
        gifRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 搜尋列
        drawSearchBar(canvas)

        if (isLoading) {
            textPaint.color = theme.effectiveAccentColor()
            val dots = ".".repeat(((System.currentTimeMillis() / 500) % 4).toInt())
            canvas.drawText("搜尋中$dots", width / 2f, height / 2f, textPaint)
            postInvalidateDelayed(500)
            return
        }

        if (gifResults.isEmpty()) {
            drawTrendingTags(canvas)

            textPaint.color = theme.candidateTextColorInt
            textPaint.alpha = 100
            canvas.drawText(
                if (onSearch == null) "GIF 引擎未連接" else "點擊標籤或輸入關鍵字搜尋 GIF",
                width / 2f, height / 2f + 20 * density, textPaint
            )
            textPaint.alpha = 255
        } else {
            drawGifGrid(canvas)
        }
    }

    private fun drawSearchBar(canvas: Canvas) {
        bgPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), searchBarH, bgPaint)

        searchBarRect = RectF(8 * density, 4 * density, width - 8 * density, searchBarH - 4 * density)
        cardPaint.color = if (isInputMode)
            (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x22000000
        else theme.keyColorInt
        canvas.drawRoundRect(searchBarRect, cornerR, cornerR, cardPaint)

        // 聚焦邊框
        if (isInputMode) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = theme.candidateSelectedColorInt
            }
            canvas.drawRoundRect(searchBarRect, cornerR, cornerR, borderPaint)
        }

        val currentText = if (isInputMode) {
            if (composingHint.isNotEmpty()) inputBuffer.toString() + composingHint else inputBuffer.toString()
        } else searchQuery
        searchPaint.color = if (currentText.isEmpty()) {
            theme.candidateTextColorInt.and(0x00FFFFFF).or(0x80000000.toInt())
        } else {
            theme.keyLabelColorInt
        }
        val displayText = if (currentText.isEmpty()) {
            if (isInputMode) "輸入搜尋關鍵字..." else "\uD83D\uDD0D 點此搜尋 GIF..."
        } else currentText
        canvas.drawText(displayText, searchBarRect.left + 12 * density, searchBarRect.centerY() + searchPaint.textSize / 3f, searchPaint)

        // 清除按鈕
        if (currentText.isNotEmpty()) {
            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 14f * density
                color = (theme.candidateTextColorInt and 0x00FFFFFF) or 0x88000000.toInt()
            }
            canvas.drawText("✕", searchBarRect.right - 16 * density, searchBarRect.centerY() + 5 * density, clearPaint)
        }
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
        val cellH = cellW * 0.75f

        canvas.save()
        canvas.clipRect(0f, startY, width.toFloat(), height.toFloat())

        gifResults.forEachIndexed { i, gif ->
            val col = i % cols
            val row = i / cols
            val x = col * cellW
            val y = startY + row * cellH + scrollOffset

            if (y + cellH < startY || y > height) return@forEachIndexed

            val rect = RectF(x + 2 * density, y + 2 * density, x + cellW - 2 * density, y + cellH - 2 * density)
            gifRects.add(rect to i)

            // 背景卡片
            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 嘗試取得已載入的 bitmap
            val bitmap = loadedBitmaps[gif.previewUrl]
            if (bitmap != null && !bitmap.isRecycled) {
                // 繪製 bitmap，保持比例填滿
                drawBitmapFill(canvas, bitmap, rect)
            } else {
                // 顯示載入佔位
                textPaint.color = theme.candidateTextColorInt
                textPaint.alpha = 80
                textPaint.textSize = 10f * density
                val label = gif.title.take(12).ifEmpty { "GIF" }
                canvas.drawText(label, rect.centerX(), rect.centerY() - 4 * density, textPaint)
                textPaint.textSize = 20f * density
                canvas.drawText("\uD83C\uDFAC", rect.centerX(), rect.centerY() + 16 * density, textPaint)
                textPaint.textSize = 13f * density
                textPaint.alpha = 255

                // 發起非同步載入
                loadPreviewAsync(gif.previewUrl)
            }
        }

        canvas.restore()
    }

    /** 繪製 Bitmap 填滿 RectF（center crop） */
    private fun drawBitmapFill(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val dw = dst.width()
        val dh = dst.height()

        val scale = maxOf(dw / bw, dh / bh)
        val sw = dw / scale
        val sh = dh / scale
        val sx = (bw - sw) / 2f
        val sy = (bh - sh) / 2f

        val src = android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        val dstRect = android.graphics.Rect(dst.left.toInt(), dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt())

        canvas.save()
        canvas.clipRect(dst)
        // 圓角裁切
        val path = android.graphics.Path()
        path.addRoundRect(dst, cornerR, cornerR, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, src, dstRect, bitmapPaint)
        canvas.restore()
    }

    /** 非同步載入預覽圖 */
    private fun loadPreviewAsync(url: String) {
        if (url in loadedBitmaps || url in loadingUrls) return
        loadingUrls.add(url)
        scope.launch {
            val bmp = imageCache.getPreviewBitmap(url)
            loadedBitmaps[url] = bmp
            loadingUrls.remove(url)
            post { invalidate() }
        }
    }

    /** 外部呼叫：設定搜尋文字並執行搜尋 */
    fun search(query: String) {
        searchQuery = query
        isLoading = true
        gifResults = emptyList()
        scrollOffset = 0f
        invalidate()

        onSearch?.invoke(query) { results ->
            gifResults = results
            isLoading = false
            post { invalidate() }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gifResults.isNotEmpty()) {
                    val dy = event.y - lastTouchY
                    if (Math.abs(dy) > 5 * density) isDragging = true
                    if (isDragging) {
                        scrollOffset += dy
                        val cols = 3
                        val cellH = (width / cols.toFloat()) * 0.75f
                        val totalRows = (gifResults.size + cols - 1) / cols
                        val contentH = totalRows * cellH
                        val viewH = height - searchBarH - 4 * density
                        val minScroll = -(contentH - viewH).coerceAtLeast(0f)
                        scrollOffset = scrollOffset.coerceIn(minScroll, 0f)
                        lastTouchY = event.y
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 搜尋框點擊
                    if (searchBarRect.contains(event.x, event.y)) {
                        // 清除按鈕
                        val currentText = if (isInputMode) inputBuffer.toString() else searchQuery
                        if (currentText.isNotEmpty() && event.x > searchBarRect.right - 30 * density) {
                            inputBuffer.clear()
                            searchQuery = ""
                            gifResults = emptyList()
                            isInputMode = false
                            invalidate()
                            performClick()
                            return true
                        }
                        // 進入 canvas 輸入模式（鍵盤直接輸入到搜尋框）
                        if (!isInputMode) {
                            isInputMode = true
                            inputBuffer.clear()
                            if (searchQuery.isNotEmpty()) {
                                inputBuffer.append(searchQuery)
                            }
                            invalidate()
                        }
                        performClick()
                        return true
                    }

                    // 標籤
                    val tagHit = tagRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (tagHit != null) {
                        isInputMode = false
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
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    // ── 搜尋 Dialog ──

    private fun showSearchDialog() {
        val input = android.widget.EditText(context).apply {
            hint = "輸入 GIF 搜尋關鍵字"
            setText(searchQuery)
            textSize = 15f
            val dp = context.resources.displayMetrics.density
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
        }
        val dialog = android.app.AlertDialog.Builder(context, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("搜尋 GIF")
            .setView(input)
            .setPositiveButton("搜尋") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    isInputMode = false
                    search(q)
                }
            }
            .setNegativeButton("取消", null)
            .create()
        val cb = onShowDialog
        if (cb != null) {
            cb.invoke(dialog)
        } else {
            try {
                dialog.window?.let { w ->
                    w.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    w.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    )
                    w.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
                    val lp = w.attributes
                    lp.y = (48 * context.resources.displayMetrics.density).toInt()
                    w.attributes = lp
                }
                dialog.show()
            } catch (_: Exception) {}
        }
    }

    // ─────��───────── 鍵盤輸入 API ───────────────

    override val isInInputMode: Boolean get() = isInputMode

    override fun onCharInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        invalidate()
    }

    override fun onDeleteInput() {
        if (!isInputMode || inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        invalidate()
    }

    override fun onSpaceInput() {
        if (!isInputMode) return
        inputBuffer.append(' ')
        invalidate()
    }

    override fun onEnterInput() {
        if (!isInputMode) return
        if (inputBuffer.isNotEmpty()) {
            isInputMode = false
            search(inputBuffer.toString())
        }
    }

    /** 進入輸入模式（由 Service 在面板開啟時呼叫） */
    override fun enterInputMode() {
        if (!isInputMode) {
            isInputMode = true
            inputBuffer.clear()
            if (searchQuery.isNotEmpty()) inputBuffer.append(searchQuery)
            invalidate()
        }
    }

    override fun exitInputMode() {
        isInputMode = false
        invalidate()
    }

    /** 接收來自 InputRouter 的組字提交文字（中文等 CJK 輸入） */
    override fun onComposedTextInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        composingHint = ""
        invalidate()
    }

    /** 更新 CJK 組字中的提示文字（尚未提交） */
    override fun updateComposingHint(hint: String) {
        composingHint = hint
        invalidate()
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
