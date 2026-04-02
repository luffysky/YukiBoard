package com.snowrealm.yukiboard.ui.panel

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 剪貼簿面板 — 顯示剪貼簿歷史記錄，點擊貼上。
 *
 * 使用 SharedPreferences 儲存最近 20 筆剪貼簿記錄。
 * Android ClipboardManager 只保留最新一筆，所以需自行監聽記錄。
 */
class ClipboardPanelView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yukiboard_clipboard", Context.MODE_PRIVATE)
    private val clipManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 13f * density
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    var onClipSelected: ((String) -> Unit)? = null

    private var clipHistory = mutableListOf<ClipItem>()
    private val itemRects = mutableListOf<Pair<RectF, Int>>()  // index in clipHistory
    private val pinRects = mutableListOf<Pair<RectF, Int>>()
    private val deleteRects = mutableListOf<Pair<RectF, Int>>()

    private val itemHeight = 44f * density
    private val headerHeight = 28f * density
    private val cornerR = 6f * density

    companion object {
        private const val MAX_HISTORY = 20
        private const val KEY_CLIP_PREFIX = "clip_"
        private const val KEY_PIN_PREFIX = "pin_"
        private const val KEY_COUNT = "clip_count"
    }

    init {
        loadHistory()
        captureCurrentClip()
    }

    /** 從目前系統剪貼簿擷取內容加入歷史 */
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
        // 避免重複
        val existing = clipHistory.indexOfFirst { it.text == text }
        if (existing >= 0) {
            // 移到最前面（除非已 pin）
            if (!clipHistory[existing].pinned) {
                val item = clipHistory.removeAt(existing)
                clipHistory.add(0, item)
            }
        } else {
            clipHistory.add(0, ClipItem(text, pinned = false))
            if (clipHistory.size > MAX_HISTORY) {
                // 刪除最舊的未 pin 項目
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
        setMeasuredDimension(w, (200 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        itemRects.clear()
        pinRects.clear()
        deleteRects.clear()

        bgPaint.color = theme.backgroundColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 標題
        titlePaint.color = theme.candidateTextColorInt
        canvas.drawText("剪貼簿歷史", 12f * density, headerHeight * 0.72f, titlePaint)

        if (clipHistory.isEmpty()) {
            emptyPaint.color = theme.candidateTextColorInt
            emptyPaint.alpha = 120
            canvas.drawText("空的 — 複製文字後會顯示在這裡", width / 2f, height / 2f, emptyPaint)
            return
        }

        val startY = headerHeight + 4 * density
        clipHistory.forEachIndexed { i, item ->
            val y = startY + i * (itemHeight + 4 * density)
            if (y > height) return@forEachIndexed

            val rect = RectF(8 * density, y, width - 8 * density, y + itemHeight)
            itemRects.add(rect to i)

            // 卡片背景
            cardPaint.color = if (item.pinned) {
                (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x20000000
            } else {
                theme.keyColorInt
            }
            canvas.drawRoundRect(rect, cornerR, cornerR, cardPaint)

            // 文字（截斷顯示）
            textPaint.color = theme.keyLabelColorInt
            val maxChars = ((rect.width() - 80 * density) / (textPaint.textSize * 0.55f)).toInt().coerceAtLeast(10)
            val displayText = if (item.text.length > maxChars) item.text.take(maxChars) + "…" else item.text
            canvas.drawText(displayText, rect.left + 8 * density, rect.centerY() + textPaint.textSize / 3f, textPaint)

            // Pin 按鈕
            val pinRect = RectF(rect.right - 64 * density, rect.top + 6 * density, rect.right - 34 * density, rect.bottom - 6 * density)
            pinRects.add(pinRect to i)
            pinPaint.color = if (item.pinned) theme.candidateSelectedColorInt else theme.candidateTextColorInt
            canvas.drawText(if (item.pinned) "📌" else "📍", pinRect.centerX(), pinRect.centerY() + pinPaint.textSize / 3f, pinPaint)

            // 刪除按鈕
            val delRect = RectF(rect.right - 30 * density, rect.top + 6 * density, rect.right - 4 * density, rect.bottom - 6 * density)
            deleteRects.add(delRect to i)
            deletePaint.color = 0xFFFF6B6B.toInt()
            canvas.drawText("✕", delRect.centerX(), delRect.centerY() + deletePaint.textSize / 3f, deletePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // 刪除按鈕
            val delHit = deleteRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (delHit != null) {
                clipHistory.removeAt(delHit.second)
                saveHistory()
                invalidate()
                performClick()
                return true
            }

            // Pin 按鈕
            val pinHit = pinRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (pinHit != null) {
                clipHistory[pinHit.second].pinned = !clipHistory[pinHit.second].pinned
                saveHistory()
                invalidate()
                performClick()
                return true
            }

            // 點擊項目 → 貼上
            val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
            if (itemHit != null) {
                onClipSelected?.invoke(clipHistory[itemHit.second].text)
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

    private data class ClipItem(val text: String, var pinned: Boolean)
}
