package com.snowrealm.yukiboard.ui.panel

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.snowrealm.yukiboard.theme.KeyboardTheme
import com.snowrealm.yukiboard.ui.settings.YukiPreferences

/**
 * 剪貼簿面板 — 卡片式佈局，釘選區 + 歷史區，支援捲動。
 *
 * v2 增強功能：
 * - 搜尋功能（頂部搜尋框，即時篩選）
 * - 自動分類標籤（文字/連結/信箱/電話/圖片/檔案）
 * - 標籤篩選列（橫向捲動）
 * - 加密剪貼簿項目（AES-256-GCM，Android Keystore）
 * - 長按彈出選單（複製/加密/釘選/刪除）
 * - 敏感內容自動偵測（信用卡、身分證號）
 */
class ClipboardPanelView(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yukiboard_clipboard", Context.MODE_PRIVATE)
    private val yukiPrefs = YukiPreferences(appContext)
    private val clipManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val encryptHelper: ClipboardEncryptionHelper by lazy { ClipboardEncryptionHelper(appContext) }
    private val handler = Handler(Looper.getMainLooper())

    // ═══ Child views ═══
    private val searchBar: TextView
    private val tagRow: HorizontalScrollView
    private val canvasView: ClipboardCanvasView

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) {
            field = value
            canvasView.theme = value
            updateSearchTheme()
            updateTagRowTheme()
            canvasView.invalidate()
        }

    var onClipSelected: ((String) -> Unit)? = null

    /** 由 Service 提供的 Dialog 顯示回呼（IME 內部 View 無法直接使用 EditText） */
    var onShowDialog: ((android.app.AlertDialog) -> Unit)? = null

    private var clipHistory = mutableListOf<ClipItem>()
    private var filteredItems = listOf<ClipItem>()
    private var searchQuery = ""
    private var activeTagFilter: ClipContentTag? = null  // null = 全部
    private var showPinnedOnly = false

    // Temporarily revealed encrypted items (auto-hide after 30s)
    private val revealedItems = mutableSetOf<Int>()  // indices into clipHistory

    companion object {
        private const val MAX_HISTORY = 50
        private const val KEY_CLIP_PREFIX = "clip_"
        private const val KEY_PIN_PREFIX = "pin_"
        private const val KEY_TAG_PREFIX = "tag_"
        private const val KEY_ENCRYPTED_PREFIX = "enc_"
        private const val KEY_ENCRYPTED_DATA_PREFIX = "encdata_"
        private const val KEY_COUNT = "clip_count"
        private const val REVEAL_DURATION_MS = 30_000L
    }

    init {
        // Build layout: Search → TagRow → Canvas
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 1. Search bar (TextView — 點擊彈出搜尋 Dialog)
        searchBar = TextView(context).apply {
            text = "\uD83D\uDD0D 搜尋剪貼簿..."
            textSize = 13f
            setTextColor(0xFF808080.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2540.toInt())
                cornerRadius = 8f * density
                setStroke((1f * density).toInt(), 0xFF2A3A5A.toInt())
            }
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            }
        }
        searchBar.setOnClickListener { showSearchDialog() }
        root.addView(searchBar)

        // 2. Tag filter row
        tagRow = buildTagRow()
        root.addView(tagRow)

        // 3. Canvas view for actual items
        canvasView = ClipboardCanvasView(context)
        canvasView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        root.addView(canvasView)

        addView(root)
        loadHistory()
        applyFilters()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        val h = (220 * density).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
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
            val tag = ClipContentTag.detect(text)
            val item = ClipItem(text, pinned = false, tag = tag)
            clipHistory.add(0, item)

            // Auto-detect sensitive content → suggest encryption
            if (yukiPrefs.clipboardAutoDetectSensitive &&
                yukiPrefs.clipboardEncryptionEnabled &&
                ClipContentTag.containsSensitiveContent(text)
            ) {
                item.suggestEncrypt = true
            }

            if (clipHistory.size > MAX_HISTORY) {
                val removeIdx = clipHistory.indexOfLast { !it.pinned && !it.encrypted }
                if (removeIdx >= 0) clipHistory.removeAt(removeIdx)
            }
        }
        saveHistory()
        applyFilters()
    }

    private fun applyFilters() {
        filteredItems = clipHistory.filter { item ->
            // Tag filter
            val tagMatch = when {
                showPinnedOnly -> item.pinned
                activeTagFilter != null -> item.tag == activeTagFilter
                else -> true
            }
            // Search filter
            val searchMatch = if (searchQuery.isEmpty()) true
            else {
                if (item.encrypted && !revealedItems.contains(clipHistory.indexOf(item))) {
                    false  // Can't search encrypted content
                } else {
                    item.text.contains(searchQuery, ignoreCase = true)
                }
            }
            tagMatch && searchMatch
        }
        canvasView.setItems(filteredItems, clipHistory, revealedItems)
    }

    // ═══ Encryption ═══

    private fun encryptItem(index: Int) {
        if (index !in clipHistory.indices) return
        val item = clipHistory[index]
        if (item.encrypted) return
        try {
            val encData = encryptHelper.encrypt(item.text)
            item.encryptedData = encData
            item.encrypted = true
            revealedItems.remove(index)
            saveHistory()
            applyFilters()
        } catch (e: Exception) {
            android.util.Log.e("ClipPanel", "Encrypt failed", e)
        }
    }

    private fun decryptAndReveal(index: Int) {
        if (index !in clipHistory.indices) return
        val item = clipHistory[index]
        if (!item.encrypted || item.encryptedData == null) return
        // Decrypt text back for display
        val plain = item.encryptedData?.let { encryptHelper.decrypt(it) }
        if (plain != null) {
            item.text = plain  // Restore plaintext temporarily for display
            revealedItems.add(index)
            applyFilters()
            // Auto-mask after 30 seconds
            handler.postDelayed({
                revealedItems.remove(index)
                applyFilters()
            }, REVEAL_DURATION_MS)
        }
    }

    // ═══ Persistence ═══

    private fun loadHistory() {
        clipHistory.clear()
        val count = prefs.getInt(KEY_COUNT, 0)
        for (i in 0 until count) {
            val text = prefs.getString("$KEY_CLIP_PREFIX$i", null) ?: continue
            val pinned = prefs.getBoolean("$KEY_PIN_PREFIX$i", false)
            val tagName = prefs.getString("$KEY_TAG_PREFIX$i", null)
            val encrypted = prefs.getBoolean("$KEY_ENCRYPTED_PREFIX$i", false)
            val encData = prefs.getString("$KEY_ENCRYPTED_DATA_PREFIX$i", null)

            val tag = try {
                if (tagName != null) ClipContentTag.valueOf(tagName) else ClipContentTag.detect(text)
            } catch (_: Exception) {
                ClipContentTag.detect(text)
            }

            clipHistory.add(ClipItem(text, pinned, tag, encrypted, encData))
        }
    }

    private fun saveHistory() {
        val editor = prefs.edit()
        editor.putInt(KEY_COUNT, clipHistory.size)
        clipHistory.forEachIndexed { i, item ->
            editor.putString("$KEY_CLIP_PREFIX$i", item.text)
            editor.putBoolean("$KEY_PIN_PREFIX$i", item.pinned)
            editor.putString("$KEY_TAG_PREFIX$i", item.tag.name)
            editor.putBoolean("$KEY_ENCRYPTED_PREFIX$i", item.encrypted)
            editor.putString("$KEY_ENCRYPTED_DATA_PREFIX$i", item.encryptedData)
        }
        editor.apply()
    }

    // ═══ Tag row ═══

    private fun buildTagRow(): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
        }

        data class TagDef(val label: String, val tag: ClipContentTag?, val isPinFilter: Boolean = false)

        val tags = listOf(
            TagDef("\u5168\u90E8", null),       // 全部
            TagDef("\uD83D\uDCDD\u6587\u5B57", ClipContentTag.TEXT),      // 📝文字
            TagDef("\uD83D\uDD17\u9023\u7D50", ClipContentTag.LINK),      // 🔗連結
            TagDef("\uD83D\uDCE7\u4FE1\u7BB1", ClipContentTag.EMAIL),     // 📧信箱
            TagDef("\uD83D\uDCDE\u96FB\u8A71", ClipContentTag.PHONE),     // 📞電話
            TagDef("\uD83D\uDCCC\u91D8\u9078", null, isPinFilter = true), // 📌釘選
        )

        for (def in tags) {
            val chip = TextView(context).apply {
                text = def.label
                textSize = 11f
                setTextColor(0xFFB0B8CC.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF1E2D4A.toInt())
                    cornerRadius = 12f * density
                    setStroke((1f * density).toInt(), 0xFF2A3A5A.toInt())
                }
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((3 * density).toInt(), 0, (3 * density).toInt(), 0) }

                setOnClickListener {
                    if (def.isPinFilter) {
                        showPinnedOnly = !showPinnedOnly
                        activeTagFilter = null
                    } else {
                        showPinnedOnly = false
                        activeTagFilter = if (activeTagFilter == def.tag && def.tag != null) null else def.tag
                    }
                    updateTagRowHighlight(row)
                    applyFilters()
                }
                tag = def
            }
            row.addView(chip)
        }

        scroll.addView(row)
        return scroll
    }

    private fun updateTagRowHighlight(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as? TextView ?: continue
            @Suppress("UNCHECKED_CAST")
            val def = chip.tag as? Any ?: continue  // Can't easily get TagDef, use index
            val isActive = when (i) {
                0 -> activeTagFilter == null && !showPinnedOnly  // 全部
                5 -> showPinnedOnly  // 📌釘選
                else -> {
                    val tagValues = listOf(ClipContentTag.TEXT, ClipContentTag.LINK, ClipContentTag.EMAIL, ClipContentTag.PHONE)
                    !showPinnedOnly && i - 1 < tagValues.size && activeTagFilter == tagValues[i - 1]
                }
            }
            val bg = chip.background as? android.graphics.drawable.GradientDrawable
            if (isActive) {
                bg?.setColor(0xFF2A4A7A.toInt())
                bg?.setStroke((1f * density).toInt(), 0xFF4A8ADA.toInt())
                chip.setTextColor(0xFFFFFFFF.toInt())
            } else {
                bg?.setColor(0xFF1E2D4A.toInt())
                bg?.setStroke((1f * density).toInt(), 0xFF2A3A5A.toInt())
                chip.setTextColor(0xFFB0B8CC.toInt())
            }
        }
    }

    private fun updateSearchTheme() {
        val bg = searchBar.background as? android.graphics.drawable.GradientDrawable
        bg?.setColor(theme.effectivePanelBackground())
    }

    private fun updateTagRowTheme() {
        // Tag chips use dark theme colors which work with most themes
    }

    private fun showSearchDialog() {
        val input = EditText(context).apply {
            hint = "輸入關鍵字"
            setText(searchQuery)
            textSize = 15f
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
        }
        val dialog = android.app.AlertDialog.Builder(context, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("搜尋剪貼簿")
            .setView(input)
            .setPositiveButton("搜尋") { _, _ ->
                searchQuery = input.text.toString().trim()
                applyFilters()
                updateSearchBarDisplay()
            }
            .setNeutralButton("清除") { _, _ ->
                searchQuery = ""
                applyFilters()
                updateSearchBarDisplay()
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

    private fun updateSearchBarDisplay() {
        if (searchQuery.isEmpty()) {
            searchBar.text = "\uD83D\uDD0D 搜尋剪貼簿..."
            searchBar.setTextColor(0xFF808080.toInt())
        } else {
            searchBar.text = "\uD83D\uDD0D $searchQuery  \u2715"
            searchBar.setTextColor(0xFFE0E0E0.toInt())
        }
    }

    // ═══ Context menu (long press) ═══

    private fun showContextMenu(item: ClipItem, globalIndex: Int, anchorX: Int, anchorY: Int) {
        val menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2540.toInt())
                cornerRadius = 10f * density
                setStroke((1f * density).toInt(), 0xFF3A4A6A.toInt())
            }
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            elevation = 8f * density
        }

        val popup = PopupWindow(
            menuLayout,
            (160 * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))

        data class MenuItem(val label: String, val action: () -> Unit)

        val items = mutableListOf<MenuItem>()

        // Copy
        items.add(MenuItem("\u8907\u88FD") {  // 複製
            val plainText = if (item.encrypted && !revealedItems.contains(globalIndex)) {
                encryptHelper.decrypt(item.encryptedData ?: "") ?: item.text
            } else {
                item.text
            }
            val clip = android.content.ClipData.newPlainText("yukiboard", plainText)
            clipManager.setPrimaryClip(clip)
            popup.dismiss()
        })

        // Encrypt / Decrypt
        if (yukiPrefs.clipboardEncryptionEnabled) {
            if (!item.encrypted) {
                items.add(MenuItem("\uD83D\uDD12\u52A0\u5BC6") {  // 🔒加密
                    encryptItem(globalIndex)
                    popup.dismiss()
                })
            } else {
                items.add(MenuItem("\uD83D\uDD13\u89E3\u5BC6\u67E5\u770B") {  // 🔓解密查看
                    decryptAndReveal(globalIndex)
                    popup.dismiss()
                })
            }
        }

        // Pin / Unpin
        val pinLabel = if (item.pinned) "\u53D6\u6D88\u91D8\u9078" else "\uD83D\uDCCC\u91D8\u9078"  // 取消釘選 / 📌釘選
        items.add(MenuItem(pinLabel) {
            if (globalIndex in clipHistory.indices) {
                clipHistory[globalIndex].pinned = !clipHistory[globalIndex].pinned
                saveHistory()
                applyFilters()
            }
            popup.dismiss()
        })

        // Delete
        items.add(MenuItem("\uD83D\uDDD1\u522A\u9664") {  // 🗑刪除
            if (globalIndex in clipHistory.indices) {
                clipHistory.removeAt(globalIndex)
                saveHistory()
                applyFilters()
            }
            popup.dismiss()
        })

        for (menuItem in items) {
            val tv = TextView(context).apply {
                text = menuItem.label
                textSize = 13f
                setTextColor(0xFFE0E0E0.toInt())
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                setOnClickListener { menuItem.action() }
                background = android.graphics.drawable.StateListDrawable().apply {
                    val pressed = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF2A4A7A.toInt())
                        cornerRadius = 6f * density
                    }
                    val normal = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x00000000)
                        cornerRadius = 6f * density
                    }
                    addState(intArrayOf(android.R.attr.state_pressed), pressed)
                    addState(intArrayOf(), normal)
                }
            }
            menuLayout.addView(tv)
        }

        // Show sensitive warning for newly added items
        if (item.suggestEncrypt && !item.encrypted) {
            val warn = TextView(context).apply {
                text = "\u26A0\uFE0F \u5075\u6E2C\u5230\u654F\u611F\u5167\u5BB9"  // ⚠️ 偵測到敏感內容
                textSize = 10f
                setTextColor(0xFFFFAA00.toInt())
                setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            }
            menuLayout.addView(warn)
        }

        try {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, anchorX, anchorY)
        } catch (_: Exception) {
            // Fallback if window token not available
            popup.showAsDropDown(this, anchorX, anchorY - height)
        }
    }

    // ═══ Inner Canvas View ═══

    inner class ClipboardCanvasView(context: Context) : View(context) {

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

        private var displayItems = listOf<ClipItem>()
        private var allItems = mutableListOf<ClipItem>()
        private var revealed = setOf<Int>()

        // Touch areas
        private val itemRects = mutableListOf<Pair<RectF, Int>>()    // rect → global index
        private val pinRects = mutableListOf<Pair<RectF, Int>>()
        private val deleteRects = mutableListOf<Pair<RectF, Int>>()
        private val pinnedCardRects = mutableListOf<Pair<RectF, Int>>()
        private var clearAllRect = RectF()

        // Scroll
        private var scrollY = 0f
        private var lastTouchY = 0f
        private var touchDownY = 0f
        private var touchDownX = 0f
        private var isDragging = false

        // Pinned horizontal scroll
        private var pinnedScrollX = 0f
        private var lastPinnedTouchX = 0f
        private var isPinnedDragging = false
        private var touchInPinnedZone = false

        // Long press
        private var longPressRunnable: Runnable? = null
        private var longPressTriggered = false
        private val longPressTimeout = 500L

        // Sizes
        private val headerH = 36f * density
        private val pinnedAreaH = 56f * density
        private val itemH = 62f * density
        private val itemGap = 6f * density
        private val cardR = 10f * density
        private val padding = 10f * density

        fun setItems(filtered: List<ClipItem>, all: MutableList<ClipItem>, revealedSet: Set<Int>) {
            displayItems = filtered
            allItems = all
            revealed = revealedSet
            scrollY = 0f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            itemRects.clear()
            pinRects.clear()
            deleteRects.clear()
            pinnedCardRects.clear()

            // Background
            bgPaint.color = theme.effectivePanelBackground()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val pinnedItems = displayItems.filter { it.pinned }
            val unpinnedItems = displayItems.filter { !it.pinned }
            val hasPinned = pinnedItems.isNotEmpty()

            // Header
            drawHeader(canvas, displayItems.size)

            if (displayItems.isEmpty()) {
                drawEmptyState(canvas)
                return
            }

            // Pinned section
            var contentTop = headerH
            if (hasPinned) {
                drawPinnedSection(canvas, pinnedItems, contentTop)
                contentTop += pinnedAreaH + 4 * density
            }

            // History section
            drawHistorySection(canvas, unpinnedItems, contentTop)
        }

        private fun drawHeader(canvas: Canvas, totalCount: Int) {
            cardPaint.color = theme.candidateBarColorInt
            canvas.drawRect(0f, 0f, width.toFloat(), headerH, cardPaint)

            titlePaint.color = theme.keyLabelColorInt
            titlePaint.textSize = 14f * density
            titlePaint.isFakeBoldText = true
            canvas.drawText("\uD83D\uDCCB \u526A\u8CBC\u7C3F", padding + 2 * density, headerH * 0.68f, titlePaint)
            titlePaint.isFakeBoldText = false

            // Count badge
            if (totalCount > 0) {
                val countText = "$totalCount"
                badgePaint.color = theme.candidateSelectedColorInt
                val badgeX = padding + titlePaint.measureText("\uD83D\uDCCB \u526A\u8CBC\u7C3F") + 10 * density
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

            // Clear all button
            clearBtnPaint.color = 0xFFFF6B6B.toInt()
            clearBtnPaint.textSize = 12f * density
            val clearText = "\u6E05\u9664\u5168\u90E8"  // 清除全部
            val clearW = clearBtnPaint.measureText(clearText) + 16 * density
            clearAllRect = RectF(width - clearW - padding, 6 * density, width - padding, headerH - 6 * density)
            cardPaint.color = 0x20FF6B6B
            canvas.drawRoundRect(clearAllRect, 6 * density, 6 * density, cardPaint)
            canvas.drawText(clearText, clearAllRect.centerX(), clearAllRect.centerY() + clearBtnPaint.textSize / 3f, clearBtnPaint)
        }

        private fun drawEmptyState(canvas: Canvas) {
            val cy = height / 2f
            emptyPaint.textSize = 36f * density
            emptyPaint.color = theme.candidateTextColorInt
            emptyPaint.alpha = 60
            canvas.drawText("\uD83D\uDCCB", width / 2f, cy - 10 * density, emptyPaint)
            emptyPaint.textSize = 13f * density
            emptyPaint.alpha = 100
            val msg = if (searchQuery.isNotEmpty()) "\u627E\u4E0D\u5230\u7B26\u5408\u7684\u9805\u76EE"  // 找不到符合的項目
            else "\u8907\u88FD\u6587\u5B57\u5F8C\u6703\u986F\u793A\u5728\u9019\u88E1"  // 複製文字後會顯示在這裡
            canvas.drawText(msg, width / 2f, cy + 28 * density, emptyPaint)
            emptyPaint.alpha = 255
        }

        private fun drawPinnedSection(canvas: Canvas, pinnedItems: List<ClipItem>, top: Float) {
            previewPaint.color = theme.candidateTextColorInt
            previewPaint.alpha = 150
            previewPaint.textSize = 10f * density
            previewPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("\uD83D\uDCCC \u91D8\u9078", padding + 2 * density, top + 12 * density, previewPaint)
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

                // Card background + border
                cardPaint.color = (theme.candidateSelectedColorInt and 0x00FFFFFF) or 0x18000000
                canvas.drawRoundRect(rect, cardR, cardR, cardPaint)
                borderPaint.color = theme.candidateSelectedColorInt
                borderPaint.strokeWidth = 1.5f * density
                canvas.drawRoundRect(rect, cardR, cardR, borderPaint)

                // Text (or masked)
                textPaint.color = theme.keyLabelColorInt
                textPaint.textSize = 11f * density
                val maxW = cardW - 12 * density
                val displayText = if (item.encrypted && !revealed.contains(globalIdx)) {
                    "\uD83D\uDD12 \u25CF\u25CF\u25CF\u25CF\u25CF\u25CF"  // 🔒 ●●●●●●
                } else {
                    item.text.replace("\n", " ")
                }
                val display = ellipsize(displayText, textPaint, maxW)
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
                canvas.drawText("\u6C92\u6709\u672A\u91D8\u9078\u7684\u9805\u76EE", width / 2f, top + 30 * density, previewPaint)
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

                // Card background
                cardPaint.color = theme.keyColorInt
                canvas.drawRoundRect(rect, cardR, cardR, cardPaint)

                // Left color bar
                val barColor = if (i == 0) theme.candidateSelectedColorInt else (theme.candidateTextColorInt and 0x00FFFFFF) or 0x30000000
                cardPaint.color = barColor
                val barPath = Path()
                barPath.addRoundRect(
                    RectF(rect.left, rect.top, rect.left + 4 * density, rect.bottom),
                    floatArrayOf(cardR, cardR, 0f, 0f, 0f, 0f, cardR, cardR), Path.Direction.CW
                )
                canvas.drawPath(barPath, cardPaint)

                val isEncryptedHidden = item.encrypted && !revealed.contains(globalIdx)

                // Tag badge (top-left, inside card)
                val tagText = item.tag.icon
                previewPaint.textSize = 10f * density
                previewPaint.color = theme.candidateTextColorInt
                previewPaint.alpha = 140
                previewPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(tagText, rect.left + 12 * density, rect.top + 13 * density, previewPaint)
                previewPaint.alpha = 255

                // Lock icon for encrypted items
                if (item.encrypted) {
                    val lockIcon = if (isEncryptedHidden) "\uD83D\uDD12" else "\uD83D\uDD13"  // 🔒 / 🔓
                    previewPaint.textSize = 10f * density
                    canvas.drawText(lockIcon, rect.left + 24 * density, rect.top + 13 * density, previewPaint)
                }

                // Sensitive warning icon
                if (item.suggestEncrypt && !item.encrypted) {
                    previewPaint.textSize = 10f * density
                    previewPaint.color = 0xFFFFAA00.toInt()
                    canvas.drawText("\u26A0\uFE0F", rect.left + 24 * density, rect.top + 13 * density, previewPaint)
                    previewPaint.color = theme.candidateTextColorInt
                }

                // Main text
                textPaint.color = theme.keyLabelColorInt
                textPaint.textSize = 12.5f * density
                val textLeft = rect.left + 12 * density
                val textMaxW = rect.width() - 70 * density

                if (isEncryptedHidden) {
                    textPaint.color = theme.candidateTextColorInt
                    textPaint.alpha = 120
                    canvas.drawText("\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF\u25CF (\u9EDE\u64CA\u89E3\u5BC6)", textLeft, rect.centerY() + 4 * density, textPaint)
                    textPaint.alpha = 255
                } else {
                    val lines = wrapText(item.text.replace("\n", " \u21B5 "), textPaint, textMaxW, 2)
                    var ty = rect.top + 26 * density
                    for (line in lines) {
                        canvas.drawText(line, textLeft, ty, textPaint)
                        ty += textPaint.textSize * 1.3f
                    }
                }

                // Character count + tag label
                previewPaint.color = theme.candidateTextColorInt
                previewPaint.alpha = 80
                previewPaint.textSize = 9f * density
                previewPaint.textAlign = Paint.Align.LEFT
                val charCount = if (isEncryptedHidden) "\u5DF2\u52A0\u5BC6" else "${item.text.length} \u5B57"  // 已加密 / N 字
                canvas.drawText(charCount, textLeft, rect.bottom - 6 * density, previewPaint)
                previewPaint.alpha = 255

                // Pin button
                val btnSize = 28f * density
                val pinRect = RectF(
                    rect.right - 58 * density, rect.centerY() - btnSize / 2,
                    rect.right - 30 * density, rect.centerY() + btnSize / 2
                )
                pinRects.add(pinRect to globalIdx)
                iconPaint.textSize = 14f * density
                iconPaint.color = if (item.pinned) theme.candidateSelectedColorInt else theme.candidateTextColorInt
                canvas.drawText("\uD83D\uDCCC", pinRect.centerX(), pinRect.centerY() + 5 * density, iconPaint)

                // Delete button
                val delRect = RectF(
                    rect.right - 28 * density, rect.centerY() - btnSize / 2,
                    rect.right - 2 * density, rect.centerY() + btnSize / 2
                )
                deleteRects.add(delRect to globalIdx)
                iconPaint.color = 0xFFFF6B6B.toInt()
                iconPaint.textSize = 13f * density
                canvas.drawText("\u2715", delRect.centerX(), delRect.centerY() + 5 * density, iconPaint)
            }

            canvas.restore()

            // Bottom fade gradient
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

        // ═══ Touch handling ═══

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val pinnedItems = displayItems.filter { it.pinned }
            val hasPinned = pinnedItems.isNotEmpty()
            val pinnedZoneBottom = if (hasPinned) headerH + pinnedAreaH + 4 * density else headerH

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.y
                    touchDownY = event.y
                    touchDownX = event.x
                    lastPinnedTouchX = event.x
                    isDragging = false
                    isPinnedDragging = false
                    longPressTriggered = false
                    touchInPinnedZone = hasPinned && event.y < pinnedZoneBottom && event.y > headerH

                    // Schedule long press detection
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    val downX = event.x
                    val downY = event.y
                    longPressRunnable = Runnable {
                        if (!isDragging && !isPinnedDragging) {
                            longPressTriggered = true
                            handleLongPress(downX, downY)
                        }
                    }
                    longPressRunnable?.let { handler.postDelayed(it, longPressTimeout) }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (Math.abs(dx) > 6 * density || Math.abs(dy) > 6 * density) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }

                    if (touchInPinnedZone) {
                        val pdx = event.x - lastPinnedTouchX
                        if (Math.abs(pdx) > 6 * density) isPinnedDragging = true
                        if (isPinnedDragging) {
                            pinnedScrollX += pdx
                            val maxScroll = 0f
                            val minScroll = -(pinnedItems.size * (120f * density + 8 * density) - width + padding * 2).coerceAtLeast(0f)
                            pinnedScrollX = pinnedScrollX.coerceIn(minScroll, maxScroll)
                            lastPinnedTouchX = event.x
                            invalidate()
                        }
                    } else {
                        val pdy = event.y - lastTouchY
                        if (Math.abs(pdy) > 6 * density) isDragging = true
                        if (isDragging) {
                            scrollY -= pdy
                            val unpinnedCount = displayItems.count { !it.pinned }
                            val contentH = unpinnedCount * (itemH + itemGap)
                            val visibleH = height - pinnedZoneBottom
                            scrollY = scrollY.coerceIn(0f, (contentH - visibleH).coerceAtLeast(0f))
                            lastTouchY = event.y
                            invalidate()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (longPressTriggered || isPinnedDragging || isDragging) {
                        performClick()
                        return true
                    }

                    // Clear all
                    if (clearAllRect.contains(event.x, event.y)) {
                        clipHistory.removeAll { !it.pinned }
                        saveHistory()
                        scrollY = 0f
                        applyFilters()
                        performClick()
                        return true
                    }

                    // Delete
                    val delHit = deleteRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (delHit != null && delHit.second < clipHistory.size) {
                        clipHistory.removeAt(delHit.second)
                        saveHistory()
                        applyFilters()
                        performClick()
                        return true
                    }

                    // Pin toggle
                    val pinHit = pinRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (pinHit != null && pinHit.second < clipHistory.size) {
                        clipHistory[pinHit.second].pinned = !clipHistory[pinHit.second].pinned
                        saveHistory()
                        applyFilters()
                        performClick()
                        return true
                    }

                    // Pinned card tap
                    val pinnedHit = pinnedCardRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (pinnedHit != null && pinnedHit.second < clipHistory.size) {
                        val item = clipHistory[pinnedHit.second]
                        if (item.encrypted && !revealed.contains(pinnedHit.second)) {
                            decryptAndReveal(pinnedHit.second)
                        } else {
                            onClipSelected?.invoke(item.text)
                        }
                        performClick()
                        return true
                    }

                    // History card tap
                    val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
                    if (itemHit != null && itemHit.second < clipHistory.size) {
                        val item = clipHistory[itemHit.second]
                        if (item.encrypted && !revealed.contains(itemHit.second)) {
                            decryptAndReveal(itemHit.second)
                        } else {
                            onClipSelected?.invoke(item.text)
                        }
                        performClick()
                        return true
                    }
                }
            }
            return true
        }

        private fun handleLongPress(x: Float, y: Float) {
            // Find which item was long-pressed
            val hit = itemRects.firstOrNull { it.first.contains(x, y) }
                ?: pinnedCardRects.firstOrNull { it.first.contains(x, y) }
            if (hit != null && hit.second < clipHistory.size) {
                val item = clipHistory[hit.second]
                val screenPos = IntArray(2)
                getLocationOnScreen(screenPos)
                showContextMenu(
                    item, hit.second,
                    (screenPos[0] + x).toInt(),
                    (screenPos[1] + y).toInt()
                )
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        // ═══ Utilities ═══

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("\u2026") > maxWidth) end--
            return text.substring(0, end) + "\u2026"
        }

        private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
            val lines = mutableListOf<String>()
            var start = 0
            while (start < text.length && lines.size < maxLines) {
                val count = paint.breakText(text, start, text.length, true, maxWidth, null)
                if (count == 0) break
                val isLast = lines.size == maxLines - 1 && start + count < text.length
                if (isLast) {
                    var end = start + count
                    val suffix = "\u2026"
                    while (end > start && paint.measureText(text, start, end) + paint.measureText(suffix) > maxWidth) end--
                    lines.add(text.substring(start, end) + suffix)
                } else {
                    lines.add(text.substring(start, start + count))
                }
                start += count
            }
            return lines
        }
    }

    // ═══ Data class ═══

    data class ClipItem(
        var text: String,
        var pinned: Boolean,
        var tag: ClipContentTag = ClipContentTag.TEXT,
        var encrypted: Boolean = false,
        var encryptedData: String? = null,
        var suggestEncrypt: Boolean = false
    )
}
