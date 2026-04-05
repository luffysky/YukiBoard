package com.snowrealm.yukiboard.ui.panel

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 剪貼簿面板 — 卡片式佈局，釘選區 + 歷史區，支援捲動。
 *
 * UI 設計：
 * - 頂部標題列（標題 + 清除全部按鈕）
 * - 釘選區：橫向滑動小卡片（藍色邊框高亮）
 * - 歷史區：直向捲動卡片列表，每張卡片顯示預覽文字 + 時間標籤
 * - 每張卡片右側：📌 釘選 / ✕ 刪除
 * - 空狀態：居中提示文字 + 剪貼簿圖示
 */
class ClipboardPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yukiboard_clipboard", Context.MODE_PRIVATE)
    private val clipManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onClipSelected: ((String) -> Unit)? = null

    private var clipHistory = mutableListOf<ClipItem>()

    // 觸控區域
    private val itemRects = mutableListOf<Pair<RectF, Int>>()
    private val pinRects = mutableListOf<Pair<RectF, Int>>()
    private val deleteRects = mutableListOf<Pair<RectF, Int>>()
    private val pinnedCardRects = mutableListOf<Pair<RectF, Int>>()
    private var clearAllRect = RectF()

    // 捲動
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var touchDownY = 0f
    private var isDragging = false

    // 釘選區橫向捲動
    private var pinnedScrollX = 0f
    private var lastPinnedTouchX = 0f
    private var isPinnedDragging = false
    private var touchInPinnedZone = false

    // 尺寸
    private val headerH = 36f * density
    private val pinnedAreaH = 56f * density
    private val itemH = 58f * density
    private val itemGap = 6f * density
    private val cardR = 10f * density
    private val padding = 10f * density

    companion object {
        private const val MAX_HISTORY = 30
        private const val KEY_CLIP_PREFIX = "clip_"
        private const val KEY_PIN_PREFIX = "pin_"
        private const val KEY_COUNT = "clip_count"
    }

    init {
        loadHistory()
        captureCurrentClip()
    }

    fun captureCurrentClip() {
        val clip = clipManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                addToHistory(text)
            }
        }
    }

    private fun addToHistory(text: String) {
        val existing = clipHistory.indexOfFirst { it.text == text }
        if (existing >= 0) {
            if (!clipHistory[existing].pinned) {
                val item = clipHistory.removeAt(existing)
                clipHistory.add(0, item)
            }
        } else {
            clipHistory.add(0, ClipItem(text, pinned = false))
            if (clipHistory.size > MAX_HISTORY) {
                val removeIdx = clipHistory.indexOfLast { !it.pinned }
                if (removeIdx >= 0) clipHistory.removeAt(removeIdx)
            }
        }
        saveHistory()
        invalidate()
    }

    private fun loadHistory() {
        clipHistory.clear()
        val count = prefs.getInt(KEY_COUNT, 0)
        for (i in 0 until count) {
            val text = prefs.getString("$KEY_CLIP_PREFIX$i", null) ?: continue
            val pinned = prefs.getBoolean("$KEY_PIN_PREFIX$i", false)
            clipHistory.add(ClipItem(text, pinned))
        }
    }

    private fun saveHistory() {
        val editor = prefs.edit()
        editor.putInt(KEY_COUNT, clipHistory.size)
        clipHistory.forEachIndexed { i, item ->
            editor.putString("$KEY_CLIP_PREFIX$i", item.text)
            editor.putBoolean("$KEY_PIN_PREFIX$i", item.pinned)
        }
        editor.apply()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (220 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        itemRects.clear()
        pinRects.clear()
        deleteRects.clear()
        pinnedCardRects.clear()

        // 背景
        bgPaint.color = theme.effectivePanelBackground()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val pinnedItems = clipHistory.filter { it.pinned }
        val unpinnedItems = clipHistory.filter { !it.pinned }
        val hasPinned = pinnedItems.isNotEmpty()

        // ═══ Header ═══
        drawHeader(canvas, unpinnedItems.size + pinnedItems.size)

        if (clipHistory.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        // ═══ 釘選區（橫向卡片） ═══
        var contentTop = headerH
        if (hasPinned) {
            drawPinnedSection(canvas, pinnedItems, contentTop)
            contentTop += pinnedAreaH + 4 * density
        }

        // ═══ 歷史區（直向捲動） ═══
        drawHistorySection(canvas, unpinnedItems, contentTop)
    }

    private fun drawHeader(canvas: Canvas, totalCount: Int) {
        // 標題列背景
        cardPaint.color = theme.candidateBarColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), headerH, cardPaint)

        // 📋 剪貼簿
        titlePaint.color = theme.keyLabelColorInt
        titlePaint.textSize = 14f * density
        titlePaint.isFakeBoldText = true
        canvas.drawText("📋 剪貼簿", padding + 2 * density, headerH * 0.68f, titlePaint)
        titlePaint.isFakeBoldText = false

        // 計數 badge
        if (totalCount > 0) {
            val countText = "$totalCount"
            badgePaint.color = theme.candidateSelectedColorInt
            val badgeX = padding + titlePaint.measureText("📋 剪貼簿") + 10 * density
            val badgeW = badgePaint.let {
                previewPaint.textSize = 10f * density
                previewPaint.measureText(countText) + 12 * density
            }
            val badgeRect = RectF(badgeX, 8 * density, badgeX + badgeW, headerH - 8 * density)
            canvas.drawRoundRect(badgeRect, 8 * density, 8 * density, badgePaint)
            previewPaint.color = 0xFFFFFFFF.toInt()
            previewPaint.textSize = 10f * density
            previewPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(countText, badgeRect.centerX(), badgeRect.centerY() + 4 * density, previewPaint)
            previewPaint.textAlign = Paint.Align.LEFT
        }

        // 清除全部按鈕
        clearBtnPaint.color = 0xFFFF6B6B.toInt()
        clearBtnPaint.textSize = 12f * density
        val clearText = "清除全部"
        val clearW = clearBtnPaint.measureText(clearText) + 16 * density
        clearAllRect = RectF(width - clearW - padding, 6 * density, width - padding, headerH - 6 * density)
        // 按鈕背景
        cardPaint.color = 0x20FF6B6B
        canvas.drawRoundRect(clearAllRect, 6 * density, 6 * density, cardPaint)
        canvas.drawText(clearText, clearAllRect.centerX(), clearAllRect.centerY() + clearBtnPaint.textSize / 3f, clearBtnPaint)
    }

    private fun drawEmptyState(canvas: Canvas) {
        val cy = height / 2f
        // 大圖示
        emptyPaint.textSize = 36f * density
        emptyPaint.color = theme.candidateTextColorInt
        emptyPaint.alpha = 60
        canvas.drawText("📋", width / 2f, cy - 10 * density, emptyPaint)
        // 提示文字
        emptyPaint.textSize = 13f * density
        emptyPaint.alpha = 100
        canvas.drawText("複製文字後會顯示在這裡", width / 2f, cy + 28 * density, emptyPaint)
        emptyPaint.alpha = 255
    }

    private fun drawPinnedSection(canvas: Canvas, pinnedItems: List<ClipItem>, top: Float) {
        // 區塊標籤
        previewPaint.color = theme.candidateTextColorInt
        previewPaint.alpha = 150
        previewPaint.textSize = 10f * density
        previewPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("📌 釘選", padding + 2 * density, top + 12 * density, previewPaint)
        previewPaint.alpha = 255

        val cardTop = top + 16 * density
        val cardH = pinnedAreaH - 20 * density
        val cardW = 120f * density
        val gap = 8f * density

        canvas.save()
        canvas.clipRect(padding, cardTop - 2 * density, width - padding, cardTop + cardH + 2 * density)

        pinnedItems.forEachIndexed { i, item ->
            val globalIdx = clipHistory.indexOf(item)
            val left = padding + i * (cardW + gap) + pinnedScrollX
            val right = left + cardW
            if (right < 0 || left > width) return@forEachIndexed

            val rect = RectF(left, cardTop, right, cardTop + cardH)
            pinnedCardRects.add(rect to globalIdx)

            // 卡片背景 + 邊框
            cardPaint.color = (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x18000000
            canvas.drawRoundRect(rect, cardR, cardR, cardPaint)
            borderPaint.color = theme.candidateSelectedColorInt
            borderPaint.strokeWidth = 1.5f * density
            canvas.drawRoundRect(rect, cardR, cardR, borderPaint)

            // 文字
            textPaint.color = theme.keyLabelColorInt
            textPaint.textSize = 11f * density
            val maxW = cardW - 12 * density
            val display = ellipsize(item.text.replace("\n", " "), textPaint, maxW)
            canvas.drawText(display, rect.left + 6 * density, rect.centerY() + 4 * density, textPaint)
        }

        canvas.restore()
    }

    private fun drawHistorySection(canvas: Canvas, items: List<ClipItem>, top: Float) {
        if (items.isEmpty()) {
            previewPaint.color = theme.candidateTextColorInt
            previewPaint.alpha = 80
            previewPaint.textSize = 12f * density
            previewPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("沒有未釘選的項目", width / 2f, top + 30 * density, previewPaint)
            previewPaint.textAlign = Paint.Align.LEFT
            previewPaint.alpha = 255
            return
        }

        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), height.toFloat())

        items.forEachIndexed { i, item ->
            val globalIdx = clipHistory.indexOf(item)
            val y = top + 4 * density + i * (itemH + itemGap) - scrollY

            if (y + itemH < top || y > height) return@forEachIndexed

            val rect = RectF(padding, y, width - padding, y + itemH)
            itemRects.add(rect to globalIdx)

            // 卡片背景
            cardPaint.color = theme.keyColorInt
            canvas.drawRoundRect(rect, cardR, cardR, cardPaint)

            // 左側色條（最新的用 accent color）
            val barColor = if (i == 0) theme.candidateSelectedColorInt else (theme.candidateTextColorInt and 0x00FFFFFF) or 0x30000000
            cardPaint.color = barColor
            val barPath = Path()
            barPath.addRoundRect(
                RectF(rect.left, rect.top, rect.left + 4 * density, rect.bottom),
                floatArrayOf(cardR, cardR, 0f, 0f, 0f, 0f, cardR, cardR), Path.Direction.CW
            )
            canvas.drawPath(barPath, cardPaint)

            // 主文字（兩行預覽）
            textPaint.color = theme.keyLabelColorInt
            textPaint.textSize = 12.5f * density
            val textLeft = rect.left + 12 * density
            val textMaxW = rect.width() - 70 * density
            val lines = wrapText(item.text.replace("\n", " ↵ "), textPaint, textMaxW, 2)

            var ty = rect.top + 16 * density
            for (line in lines) {
                canvas.drawText(line, textLeft, ty, textPaint)
                ty += textPaint.textSize * 1.3f
            }

            // 字數標籤
            previewPaint.color = theme.candidateTextColorInt
            previewPaint.alpha = 80
            previewPaint.textSize = 9f * density
            previewPaint.textAlign = Paint.Align.LEFT
            val charCount = "${item.text.length} 字"
            canvas.drawText(charCount, textLeft, rect.bottom - 6 * density, previewPaint)
            previewPaint.alpha = 255

            // 📌 釘選按鈕
            val btnSize = 28f * density
            val pinRect = RectF(
                rect.right - 58 * density, rect.centerY() - btnSize / 2,
                rect.right - 30 * density, rect.centerY() + btnSize / 2
            )
            pinRects.add(pinRect to globalIdx)
            iconPaint.textSize = 14f * density
            iconPaint.color = theme.candidateTextColorInt
            canvas.drawText("📌", pinRect.centerX(), pinRect.centerY() + 5 * density, iconPaint)

            // ✕ 刪除按鈕
            val delRect = RectF(
                rect.right - 28 * density, rect.centerY() - btnSize / 2,
                rect.right - 2 * density, rect.centerY() + btnSize / 2
            )
            deleteRects.add(delRect to globalIdx)
            iconPaint.color = 0xFFFF6B6B.toInt()
            iconPaint.textSize = 13f * density
            canvas.drawText("✕", delRect.centerX(), delRect.centerY() + 5 * density, iconPaint)
        }

        canvas.restore()

        // 底部漸層（表示可捲動）
        val totalH = items.size * (itemH + itemGap)
        val visibleH = height - top
        if (totalH > visibleH && scrollY < totalH - visibleH) {
            val fadeH = 20f * density
            val fadePaint = Paint()
            fadePaint.shader = LinearGradient(
                0f, height - fadeH, 0f, height.toFloat(),
                0x00000000, theme.effectivePanelBackground(),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, height - fadeH, width.toFloat(), height.toFloat(), fadePaint)
        }
    }

    // ═══════ 觸控 ═══════

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pinnedItems = clipHistory.filter { it.pinned }
        val hasPinned = pinnedItems.isNotEmpty()
        val pinnedZoneBottom = if (hasPinned) headerH + pinnedAreaH + 4 * density else headerH

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                touchDownY = event.y
                lastPinnedTouchX = event.x
                isDragging = false
                isPinnedDragging = false
                touchInPinnedZone = hasPinned && event.y < pinnedZoneBottom && event.y > headerH
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchInPinnedZone) {
                    val dx = event.x - lastPinnedTouchX
                    if (Math.abs(dx) > 6 * density) isPinnedDragging = true
                    if (isPinnedDragging) {
                        pinnedScrollX += dx
                        val maxScroll = 0f
                        val minScroll = -(pinnedItems.size * (120f * density + 8 * density) - width + padding * 2).coerceAtLeast(0f)
                        pinnedScrollX = pinnedScrollX.coerceIn(minScroll, maxScroll)
                        lastPinnedTouchX = event.x
                        invalidate()
                    }
                } else {
                    val dy = event.y - lastTouchY
                    if (Math.abs(dy) > 6 * density) isDragging = true
                    if (isDragging) {
                        scrollY -= dy
                        val unpinnedCount = clipHistory.count { !it.pinned }
                        val contentH = unpinnedCount * (itemH + itemGap)
                        val visibleH = height - pinnedZoneBottom
                        scrollY = scrollY.coerceIn(0f, (contentH - visibleH).coerceAtLeast(0f))
                        lastTouchY = event.y
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isPinnedDragging || isDragging) {
                    performClick()
                    return true
                }

                // 清除全部
                if (clearAllRect.contains(event.x, event.y)) {
                    clipHistory.removeAll { !it.pinned }
                    saveHistory()
                    scrollY = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // 刪除
                val delHit = deleteRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (delHit != null && delHit.second < clipHistory.size) {
                    clipHistory.removeAt(delHit.second)
                    saveHistory()
                    invalidate()
                    performClick()
                    return true
                }

                // 釘選
                val pinHit = pinRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (pinHit != null && pinHit.second < clipHistory.size) {
                    clipHistory[pinHit.second].pinned = !clipHistory[pinHit.second].pinned
                    saveHistory()
                    invalidate()
                    performClick()
                    return true
                }

                // 釘選區卡片點擊
                val pinnedHit = pinnedCardRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (pinnedHit != null && pinnedHit.second < clipHistory.size) {
                    onClipSelected?.invoke(clipHistory[pinnedHit.second].text)
                    performClick()
                    return true
                }

                // 歷史區卡片點擊
                val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (itemHit != null && itemHit.second < clipHistory.size) {
                    onClipSelected?.invoke(clipHistory[itemHit.second].text)
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

    // ═══════ 工具 ═══════

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length && lines.size < maxLines) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count == 0) break
            val isLast = lines.size == maxLines - 1 && start + count < text.length
            if (isLast) {
                // 最後一行加省略號
                var end = start + count
                val suffix = "…"
                while (end > start && paint.measureText(text, start, end) + paint.measureText(suffix) > maxWidth) end--
                lines.add(text.substring(start, end) + suffix)
            } else {
                lines.add(text.substring(start, start + count))
            }
            start += count
        }
        return lines
    }

    private data class ClipItem(val text: String, var pinned: Boolean)
}
