package com.snowrealm.yukiboard.ui.panel

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.snowrealm.yukiboard.input.dictionary.UserDictionary
import com.snowrealm.yukiboard.theme.KeyboardTheme

/**
 * 使用者詞庫管理面板 — CRUD + 分類標籤 + 搜尋 + 匯出匯入。
 *
 * UI 設計：
 * - 頂部標題列（搜尋框 + 新增按鈕）
 * - 分類標籤列（水平捲動：全部 / 一般 / 人名 / 地名 / 專有名詞 / 工作用語 / 網路用語 / 自訂）
 * - 直向捲動卡片列表，每張卡片：word (large) + bopomofo (small) + category chip
 * - 點擊卡片編輯、長按卡片刪除
 * - 底部匯出/匯入按鈕
 * - 暗色主題：bg 0xFF0D1B2A, cards 0xFF1A2A3A, text 0xFFE8F4FF, accent 0xFF9ECFFF
 */
class UserDictionaryPanelView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    // ── Paints ──
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tabTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ── 暗色主題常數 ──
    private val BG_COLOR = 0xFF0D1B2A.toInt()
    private val CARD_COLOR = 0xFF1A2A3A.toInt()
    private val TEXT_COLOR = 0xFFE8F4FF.toInt()
    private val ACCENT_COLOR = 0xFF9ECFFF.toInt()
    private val DIM_TEXT = 0xFF8EAABF.toInt()
    private val DELETE_COLOR = 0xFFFF6B6B.toInt()
    private val TAB_ACTIVE_BG = 0xFF2A4A6A.toInt()
    private val TAB_INACTIVE_BG = 0xFF152535.toInt()

    // ── 分類標籤顏色 ──
    private val CATEGORY_COLORS = mapOf(
        "一般" to 0xFF5B8DBE.toInt(),
        "人名" to 0xFFE8A87C.toInt(),
        "地名" to 0xFF85D89A.toInt(),
        "專有名詞" to 0xFFC78DFF.toInt(),
        "工作用語" to 0xFFFFD166.toInt(),
        "網路用語" to 0xFFFF6B9D.toInt(),
        "自訂" to 0xFF9ECFFF.toInt()
    )

    var theme: KeyboardTheme = KeyboardTheme.default()
        set(value) { field = value; invalidate() }

    /** 外部提供 UserDictionary 實例 */
    var userDictionary: UserDictionary? = null
        set(value) {
            field = value
            reloadEntries()
        }

    /** 匯出回呼 */
    var onExportRequest: ((String) -> Unit)? = null
    /** 匯入回呼 */
    var onImportRequest: (() -> Unit)? = null
    /** 由 Service 提供的 Dialog 顯示回呼（IME 內部 View 無法直接彈 Dialog） */
    var onShowDialog: ((AlertDialog) -> Unit)? = null

    // ── 資料 ──
    private var allEntries = listOf<Triple<String, String, String>>() // word, bopomofo, category
    private var filteredEntries = listOf<Triple<String, String, String>>()
    private var searchQuery = ""
    private var selectedCategory = "全部"
    private val categoryTabs = mutableListOf("全部")

    // ── 觸控區域 ──
    private val itemRects = mutableListOf<Pair<RectF, Int>>()
    private val deleteRects = mutableListOf<Pair<RectF, Int>>()
    private var addBtnRect = RectF()
    private var searchBtnRect = RectF()
    private var clearSearchRect = RectF()
    private var exportBtnRect = RectF()
    private var importBtnRect = RectF()
    private val tabRects = mutableListOf<Pair<RectF, String>>()

    // ── 捲動 ──
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var touchDownY = 0f
    private var touchDownX = 0f
    private var isDragging = false
    private var touchDownTime = 0L

    // ── 標籤列水平捲動 ──
    private var tabScrollX = 0f
    private var tabTouchDownX = 0f
    private var tabTouchDownScrollX = 0f
    private var isTabDragging = false

    // ── 搜尋模式 ──
    private var isSearchMode = false
    // ── canvas 輸入模式（鍵盤直接輸入到搜尋框） ──
    private var isInputMode = false
    private val inputBuffer = StringBuilder()
    private var composingHint = ""

    // ── 尺寸 ──
    private val headerH = 42f * density
    private val tabBarH = 34f * density
    private val searchBarH = 36f * density
    private val itemH = 58f * density
    private val itemGap = 5f * density
    private val cardR = 10f * density
    private val padding = 10f * density
    private val bottomBarH = 38f * density

    fun reloadEntries() {
        allEntries = userDictionary?.getAllManualWithCategory() ?: emptyList()
        categoryTabs.clear()
        categoryTabs.add("全部")
        categoryTabs.addAll(userDictionary?.getAllCategories() ?: UserDictionary.BUILT_IN_CATEGORIES)
        categoryTabs.remove("全部") // remove duplicate if present from getAllCategories
        categoryTabs.add(0, "全部")
        applyFilter()
        scrollY = 0f
        invalidate()
    }

    fun importEntries(data: String): Int {
        val count = userDictionary?.importFromString(data) ?: 0
        if (count > 0) reloadEntries()
        return count
    }

    private fun applyFilter() {
        var entries = if (selectedCategory == "全部") allEntries
        else allEntries.filter { it.third == selectedCategory }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            entries = entries.filter { (word, bopo, cat) ->
                word.lowercase().contains(q) || bopo.contains(q) || cat.contains(q)
            }
        }
        filteredEntries = entries
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).let {
            if (it > 0) it else resources.displayMetrics.widthPixels
        }
        setMeasuredDimension(w, (240 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        itemRects.clear()
        deleteRects.clear()
        tabRects.clear()

        // 背景
        bgPaint.color = BG_COLOR
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawHeader(canvas)

        var contentTop = headerH
        if (isSearchMode) {
            drawSearchBar(canvas, contentTop)
            contentTop += searchBarH
        }

        drawCategoryTabs(canvas, contentTop)
        contentTop += tabBarH

        val listBottom = height - bottomBarH
        if (filteredEntries.isEmpty()) {
            drawEmptyState(canvas, contentTop, listBottom)
        } else {
            drawEntryList(canvas, contentTop, listBottom)
        }

        drawBottomBar(canvas)
    }

    // =========================================================================
    // Header: 搜尋 + 計數 + 新增
    // =========================================================================
    private fun drawHeader(canvas: Canvas) {
        cardPaint.color = 0xFF112233.toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), headerH, cardPaint)

        // 📖 詞庫 + 計數
        titlePaint.color = TEXT_COLOR
        titlePaint.textSize = 14f * density
        titlePaint.isFakeBoldText = true
        canvas.drawText("📖 使用者詞庫", padding + 2 * density, headerH * 0.64f, titlePaint)
        titlePaint.isFakeBoldText = false

        val total = allEntries.size
        if (total > 0) {
            val countText = "$total"
            badgePaint.color = ACCENT_COLOR
            val badgeX = padding + titlePaint.measureText("📖 使用者詞庫") + 10 * density
            labelPaint.textSize = 10f * density
            val badgeW = labelPaint.measureText(countText) + 12 * density
            val badgeRect = RectF(badgeX, 9 * density, badgeX + badgeW, headerH - 9 * density)
            canvas.drawRoundRect(badgeRect, 8 * density, 8 * density, badgePaint)
            labelPaint.color = BG_COLOR
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(countText, badgeRect.centerX(), badgeRect.centerY() + 4 * density, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
        }

        // 右側按鈕
        val btnSize = 28f * density
        val btnY = (headerH - btnSize) / 2f
        var btnRight = width - padding

        // ➕ 新增按鈕
        addBtnRect = RectF(btnRight - btnSize, btnY, btnRight, btnY + btnSize)
        drawHeaderButton(canvas, addBtnRect, "➕", ACCENT_COLOR)
        btnRight -= btnSize + 4 * density

        // 🔍 搜尋按鈕
        searchBtnRect = RectF(btnRight - btnSize, btnY, btnRight, btnY + btnSize)
        val searchColor = if (isSearchMode) ACCENT_COLOR else DIM_TEXT
        drawHeaderButton(canvas, searchBtnRect, "🔍", searchColor)
    }

    private fun drawHeaderButton(canvas: Canvas, rect: RectF, icon: String, color: Int) {
        cardPaint.color = (color and 0x00FFFFFF) or 0x20000000
        canvas.drawRoundRect(rect, 6 * density, 6 * density, cardPaint)
        iconPaint.textSize = 13f * density
        iconPaint.color = color
        canvas.drawText(icon, rect.centerX(), rect.centerY() + 5 * density, iconPaint)
    }

    // =========================================================================
    // Search bar
    // =========================================================================
    private fun drawSearchBar(canvas: Canvas, top: Float) {
        val barRect = RectF(padding, top + 4 * density, width - padding, top + searchBarH - 4 * density)
        cardPaint.color = if (isInputMode) (ACCENT_COLOR and 0x00FFFFFF) or 0x22000000 else CARD_COLOR
        canvas.drawRoundRect(barRect, cardR, cardR, cardPaint)

        // 聚焦邊框
        if (isInputMode) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = ACCENT_COLOR
            }
            canvas.drawRoundRect(barRect, cardR, cardR, borderPaint)
        }

        iconPaint.textSize = 12f * density
        iconPaint.color = DIM_TEXT
        canvas.drawText("🔍", barRect.left + 16 * density, barRect.centerY() + 4 * density, iconPaint)

        textPaint.textSize = 12f * density
        textPaint.textAlign = Paint.Align.LEFT

        val displayText = if (isInputMode) {
            if (composingHint.isNotEmpty()) inputBuffer.toString() + composingHint else inputBuffer.toString()
        } else searchQuery

        if (displayText.isNotEmpty()) {
            textPaint.color = TEXT_COLOR
            canvas.drawText(displayText, barRect.left + 30 * density, barRect.centerY() + 4 * density, textPaint)

            val clearSize = 22f * density
            clearSearchRect = RectF(
                barRect.right - clearSize - 4 * density, barRect.centerY() - clearSize / 2f,
                barRect.right - 4 * density, barRect.centerY() + clearSize / 2f
            )
            iconPaint.color = DELETE_COLOR
            iconPaint.textSize = 11f * density
            canvas.drawText("✕", clearSearchRect.centerX(), clearSearchRect.centerY() + 4 * density, iconPaint)
        } else {
            textPaint.color = DIM_TEXT
            canvas.drawText(if (isInputMode) "輸入搜尋關鍵字..." else "搜尋詞彙或注音...", barRect.left + 30 * density, barRect.centerY() + 4 * density, textPaint)
            clearSearchRect = RectF()
        }
    }

    // =========================================================================
    // Category tabs (horizontal scroll)
    // =========================================================================
    private fun drawCategoryTabs(canvas: Canvas, top: Float) {
        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), top + tabBarH)

        val tabH = 26f * density
        val tabY = top + (tabBarH - tabH) / 2f
        val tabPadH = 12f * density
        val tabGap = 6f * density
        val tabRadius = tabH / 2f

        tabTextPaint.textSize = 11f * density

        var x = padding - tabScrollX

        for (cat in categoryTabs) {
            val isActive = cat == selectedCategory
            val textW = tabTextPaint.measureText(cat)
            val tabW = textW + tabPadH * 2
            val rect = RectF(x, tabY, x + tabW, tabY + tabH)
            tabRects.add(rect to cat)

            // 背景
            tabPaint.color = if (isActive) TAB_ACTIVE_BG else TAB_INACTIVE_BG
            canvas.drawRoundRect(rect, tabRadius, tabRadius, tabPaint)

            if (isActive) {
                // 底部指示線
                tabPaint.color = ACCENT_COLOR
                canvas.drawRoundRect(
                    rect.left + tabW * 0.2f, rect.bottom - 2.5f * density,
                    rect.right - tabW * 0.2f, rect.bottom,
                    1.5f * density, 1.5f * density, tabPaint
                )
            }

            tabTextPaint.color = if (isActive) ACCENT_COLOR else DIM_TEXT
            canvas.drawText(cat, rect.centerX(), rect.centerY() + 4 * density, tabTextPaint)

            x += tabW + tabGap
        }

        canvas.restore()
    }

    // =========================================================================
    // Entry list
    // =========================================================================
    private fun drawEntryList(canvas: Canvas, top: Float, bottom: Float) {
        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), bottom)

        filteredEntries.forEachIndexed { i, (word, bopo, cat) ->
            val y = top + 4 * density + i * (itemH + itemGap) - scrollY

            if (y + itemH < top || y > bottom) return@forEachIndexed

            val rect = RectF(padding, y, width - padding, y + itemH)
            itemRects.add(rect to i)

            // Card background
            cardPaint.color = CARD_COLOR
            canvas.drawRoundRect(rect, cardR, cardR, cardPaint)

            // Left color bar based on category
            val barColor = CATEGORY_COLORS[cat] ?: ACCENT_COLOR
            cardPaint.color = barColor
            val barPath = Path()
            barPath.addRoundRect(
                RectF(rect.left, rect.top, rect.left + 4 * density, rect.bottom),
                floatArrayOf(cardR, cardR, 0f, 0f, 0f, 0f, cardR, cardR), Path.Direction.CW
            )
            canvas.drawPath(barPath, cardPaint)

            val textLeft = rect.left + 14 * density
            val maxTextW = rect.width() - 90 * density

            // Word (large)
            textPaint.color = TEXT_COLOR
            textPaint.textSize = 15f * density
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.isFakeBoldText = true
            canvas.drawText(ellipsize(word, textPaint, maxTextW), textLeft, rect.top + 22 * density, textPaint)
            textPaint.isFakeBoldText = false

            // Bopomofo (small)
            labelPaint.color = DIM_TEXT
            labelPaint.textSize = 11f * density
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(ellipsize(bopo, labelPaint, maxTextW * 0.6f), textLeft, rect.bottom - 10 * density, labelPaint)

            // Category chip
            val chipColor = CATEGORY_COLORS[cat] ?: ACCENT_COLOR
            chipTextPaint.textSize = 9f * density
            val chipTextW = chipTextPaint.measureText(cat)
            val chipW = chipTextW + 12 * density
            val chipH = 16f * density
            val chipX = textLeft + labelPaint.measureText(ellipsize(bopo, labelPaint, maxTextW * 0.6f)) + 8 * density
            val chipY = rect.bottom - 10 * density - chipH * 0.65f
            val chipRect = RectF(chipX, chipY, chipX + chipW, chipY + chipH)

            // Only draw chip if it fits
            if (chipRect.right < rect.right - 36 * density) {
                chipPaint.color = (chipColor and 0x00FFFFFF) or 0x30000000
                canvas.drawRoundRect(chipRect, chipH / 2f, chipH / 2f, chipPaint)
                chipTextPaint.color = chipColor
                canvas.drawText(cat, chipRect.centerX(), chipRect.centerY() + 3.5f * density, chipTextPaint)
            }

            // Delete ✕ button
            val delSize = 26f * density
            val delRect = RectF(
                rect.right - delSize - 6 * density, rect.centerY() - delSize / 2f,
                rect.right - 6 * density, rect.centerY() + delSize / 2f
            )
            deleteRects.add(delRect to i)
            iconPaint.color = DELETE_COLOR
            iconPaint.alpha = 160
            iconPaint.textSize = 12f * density
            canvas.drawText("✕", delRect.centerX(), delRect.centerY() + 4 * density, iconPaint)
            iconPaint.alpha = 255
        }

        canvas.restore()

        // Bottom fade
        val totalH = filteredEntries.size * (itemH + itemGap)
        val visibleH = bottom - top
        if (totalH > visibleH && scrollY < totalH - visibleH) {
            val fadeH = 20f * density
            val fadePaint = Paint()
            fadePaint.shader = LinearGradient(
                0f, bottom - fadeH, 0f, bottom,
                0x00000000, BG_COLOR,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, bottom - fadeH, width.toFloat(), bottom, fadePaint)
        }
    }

    private fun drawEmptyState(canvas: Canvas, top: Float, bottom: Float) {
        val cy = top + (bottom - top) / 2f
        emptyPaint.textSize = 36f * density
        emptyPaint.color = DIM_TEXT
        emptyPaint.alpha = 60
        canvas.drawText("📖", width / 2f, cy - 10 * density, emptyPaint)
        emptyPaint.textSize = 13f * density
        emptyPaint.alpha = 100
        val hint = when {
            searchQuery.isNotEmpty() -> "找不到符合「$searchQuery」的詞彙"
            selectedCategory != "全部" -> "「$selectedCategory」分類中尚無詞彙"
            else -> "尚無自訂詞彙，點右上 ➕ 新增"
        }
        canvas.drawText(hint, width / 2f, cy + 28 * density, emptyPaint)
        emptyPaint.alpha = 255
    }

    // =========================================================================
    // Bottom bar: 匯出 / 匯入
    // =========================================================================
    private fun drawBottomBar(canvas: Canvas) {
        val top = height - bottomBarH
        cardPaint.color = 0xFF112233.toInt()
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), cardPaint)

        val btnW = 80f * density
        val btnH = 28f * density
        val btnY = top + (bottomBarH - btnH) / 2f

        // 匯出按鈕
        val exportX = width / 2f - btnW - 8 * density
        exportBtnRect = RectF(exportX, btnY, exportX + btnW, btnY + btnH)
        cardPaint.color = TAB_ACTIVE_BG
        canvas.drawRoundRect(exportBtnRect, btnH / 2f, btnH / 2f, cardPaint)
        btnPaint.textSize = 12f * density
        btnPaint.color = TEXT_COLOR
        canvas.drawText("📤 匯出", exportBtnRect.centerX(), exportBtnRect.centerY() + 4.5f * density, btnPaint)

        // 匯入按鈕
        val importX = width / 2f + 8 * density
        importBtnRect = RectF(importX, btnY, importX + btnW, btnY + btnH)
        cardPaint.color = TAB_ACTIVE_BG
        canvas.drawRoundRect(importBtnRect, btnH / 2f, btnH / 2f, cardPaint)
        btnPaint.color = TEXT_COLOR
        canvas.drawText("📥 匯入", importBtnRect.centerX(), importBtnRect.centerY() + 4.5f * density, btnPaint)
    }

    // =========================================================================
    // Touch handling
    // =========================================================================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                touchDownY = event.y
                touchDownX = event.x
                touchDownTime = System.currentTimeMillis()
                isDragging = false

                // Check if touch is in tab bar area for horizontal scrolling
                val tabTop = headerH + (if (isSearchMode) searchBarH else 0f)
                if (event.y in tabTop..(tabTop + tabBarH)) {
                    tabTouchDownX = event.x
                    tabTouchDownScrollX = tabScrollX
                    isTabDragging = false
                }

                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                val dx = event.x - touchDownX

                // Tab bar horizontal drag
                val tabTop = headerH + (if (isSearchMode) searchBarH else 0f)
                if (touchDownY in tabTop..(tabTop + tabBarH) && Math.abs(dx) > 8 * density) {
                    isTabDragging = true
                    tabScrollX = (tabTouchDownScrollX - (event.x - tabTouchDownX)).coerceAtLeast(0f)
                    invalidate()
                    return true
                }

                if (!isDragging && Math.abs(event.y - touchDownY) > 8 * density) {
                    isDragging = true
                }
                if (isDragging) {
                    val contentTop = headerH + tabBarH + (if (isSearchMode) searchBarH else 0f)
                    scrollY -= dy
                    val contentH = filteredEntries.size * (itemH + itemGap)
                    val visibleH = (height - bottomBarH) - contentTop
                    scrollY = scrollY.coerceIn(0f, (contentH - visibleH).coerceAtLeast(0f))
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging || isTabDragging) {
                    isTabDragging = false
                    performClick()
                    return true
                }

                val elapsed = System.currentTimeMillis() - touchDownTime

                // Header buttons
                if (addBtnRect.contains(event.x, event.y)) {
                    showAddDialog()
                    performClick()
                    return true
                }
                if (searchBtnRect.contains(event.x, event.y)) {
                    if (isSearchMode) {
                        isSearchMode = false
                        isInputMode = false
                        inputBuffer.clear()
                        composingHint = ""
                        searchQuery = ""
                        applyFilter()
                    } else {
                        isSearchMode = true
                        isInputMode = true
                        inputBuffer.clear()
                        if (searchQuery.isNotEmpty()) inputBuffer.append(searchQuery)
                    }
                    scrollY = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // Bottom bar buttons
                if (exportBtnRect.contains(event.x, event.y)) {
                    handleExport()
                    performClick()
                    return true
                }
                if (importBtnRect.contains(event.x, event.y)) {
                    onImportRequest?.invoke()
                    performClick()
                    return true
                }

                // Search bar
                if (isSearchMode && clearSearchRect.contains(event.x, event.y)) {
                    inputBuffer.clear()
                    composingHint = ""
                    searchQuery = ""
                    applyFilter()
                    invalidate()
                    performClick()
                    return true
                }
                if (isSearchMode) {
                    val searchTop = headerH
                    val barRect = RectF(padding, searchTop, width - padding, searchTop + searchBarH)
                    if (barRect.contains(event.x, event.y)) {
                        // 進入 inputMode
                        if (!isInputMode) {
                            isInputMode = true
                            inputBuffer.clear()
                            if (searchQuery.isNotEmpty()) inputBuffer.append(searchQuery)
                            invalidate()
                        }
                        performClick()
                        return true
                    }
                }

                // Category tabs
                val tabHit = tabRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (tabHit != null) {
                    selectedCategory = tabHit.second
                    applyFilter()
                    scrollY = 0f
                    invalidate()
                    performClick()
                    return true
                }

                // Delete button
                val delHit = deleteRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (delHit != null && delHit.second < filteredEntries.size) {
                    showDeleteConfirmation(delHit.second)
                    performClick()
                    return true
                }

                // Item tap → edit; long press → delete
                val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (itemHit != null && itemHit.second < filteredEntries.size) {
                    if (elapsed > 500) {
                        showDeleteConfirmation(itemHit.second)
                    } else {
                        showEditDialog(itemHit.second)
                    }
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

    // =========================================================================
    // Dialogs
    // =========================================================================

    private fun showAddDialog() {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(), (16 * density).toInt(),
                (20 * density).toInt(), (8 * density).toInt()
            )
        }
        val wordInput = EditText(ctx).apply {
            hint = "詞彙（例：迷客夏）"
            textSize = 15f
        }
        val bopoInput = EditText(ctx).apply {
            hint = "注音（例：ㄇㄧˊ ㄎㄜˋ ㄒㄧㄚˋ）"
            textSize = 15f
        }

        val categorySpinner = Spinner(ctx)
        val categories = userDictionary?.getAllCategories() ?: UserDictionary.BUILT_IN_CATEGORIES
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = adapter

        val catLabel = TextView(ctx).apply {
            text = "分類："
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        layout.addView(wordInput)
        layout.addView(bopoInput)
        layout.addView(catLabel)
        layout.addView(categorySpinner)

        showImeDialog(
            AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("新增詞彙")
                .setView(layout)
                .setPositiveButton("新增") { _, _ ->
                    val word = wordInput.text.toString().trim()
                    val bopo = bopoInput.text.toString().trim()
                    val cat = categorySpinner.selectedItem?.toString() ?: UserDictionary.DEFAULT_CATEGORY
                    if (word.isNotBlank() && bopo.isNotBlank()) {
                        userDictionary?.addManual(word, bopo, cat)
                        reloadEntries()
                        Toast.makeText(ctx, "已新增「$word」→ $cat", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "詞彙和注音都不可為空", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create()
        )
    }

    private fun showEditDialog(index: Int) {
        if (index !in filteredEntries.indices) return
        val (oldWord, oldBopo, oldCat) = filteredEntries[index]
        val ctx = context ?: return

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(), (16 * density).toInt(),
                (20 * density).toInt(), (8 * density).toInt()
            )
        }
        val wordInput = EditText(ctx).apply {
            hint = "詞彙"
            setText(oldWord)
            textSize = 15f
        }
        val bopoInput = EditText(ctx).apply {
            hint = "注音"
            setText(oldBopo)
            textSize = 15f
        }

        val categorySpinner = Spinner(ctx)
        val categories = userDictionary?.getAllCategories() ?: UserDictionary.BUILT_IN_CATEGORIES
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = adapter
        val catIndex = categories.indexOf(oldCat)
        if (catIndex >= 0) categorySpinner.setSelection(catIndex)

        val catLabel = TextView(ctx).apply {
            text = "分類："
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        layout.addView(wordInput)
        layout.addView(bopoInput)
        layout.addView(catLabel)
        layout.addView(categorySpinner)

        showImeDialog(
            AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("編輯詞彙")
                .setView(layout)
                .setPositiveButton("儲存") { _, _ ->
                    val newWord = wordInput.text.toString().trim()
                    val newBopo = bopoInput.text.toString().trim()
                    val newCat = categorySpinner.selectedItem?.toString() ?: UserDictionary.DEFAULT_CATEGORY
                    if (newWord.isNotBlank() && newBopo.isNotBlank()) {
                        userDictionary?.updateManual(oldWord, newWord, newBopo, newCat)
                        reloadEntries()
                        Toast.makeText(ctx, "已更新「$newWord」", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create()
        )
    }

    private fun showDeleteConfirmation(index: Int) {
        if (index !in filteredEntries.indices) return
        val (word, _, _) = filteredEntries[index]
        val ctx = context ?: return

        showImeDialog(
            AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("刪除詞彙")
                .setMessage("確定要刪除「$word」嗎？")
                .setPositiveButton("刪除") { _, _ ->
                    userDictionary?.removeManual(word)
                    reloadEntries()
                    Toast.makeText(ctx, "已刪除「$word」", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .create()
        )
    }

    private fun showSearchDialog() {
        val ctx = context ?: return
        val input = EditText(ctx).apply {
            hint = "輸入關鍵字"
            setText(searchQuery)
            textSize = 15f
            setPadding(
                (20 * density).toInt(), (12 * density).toInt(),
                (20 * density).toInt(), (12 * density).toInt()
            )
        }

        showImeDialog(
            AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("搜尋詞庫")
                .setView(input)
                .setPositiveButton("搜尋") { _, _ ->
                    searchQuery = input.text.toString().trim()
                    applyFilter()
                    scrollY = 0f
                    invalidate()
                }
                .setNeutralButton("清除") { _, _ ->
                    searchQuery = ""
                    applyFilter()
                    scrollY = 0f
                    invalidate()
                }
                .setNegativeButton("取消", null)
                .create()
        )
    }

    private fun handleExport() {
        val dict = userDictionary ?: return
        val data = dict.exportToString()
        if (data.isBlank()) {
            Toast.makeText(context, "詞庫是空的，無可匯出", Toast.LENGTH_SHORT).show()
            return
        }
        onExportRequest?.invoke(data)
            ?: Toast.makeText(context, "匯出功能未連接", Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private fun showImeDialog(dialog: AlertDialog) {
        val cb = onShowDialog
        if (cb != null) {
            cb.invoke(dialog)
        } else {
            // fallback: try TYPE_APPLICATION_OVERLAY (needs SYSTEM_ALERT_WINDOW)
            try {
                dialog.window?.let { w ->
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    w.setType(type)
                    w.setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    )
                    w.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL)
                    val lp = w.attributes
                    lp.y = (48 * resources.displayMetrics.density).toInt()
                    w.attributes = lp
                }
                dialog.show()
            } catch (e: Exception) {
                android.util.Log.e("UserDictPanel", "Cannot show dialog: ${e.message}")
            }
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("...") > maxWidth) end--
        return text.substring(0, end) + "..."
    }

    // =========================================================================
    // 鍵盤輸入 API（canvas inputMode，與 GIF/翻譯面板同介面）
    // =========================================================================

    val isInInputMode: Boolean get() = isInputMode

    fun onCharInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    fun onDeleteInput() {
        if (!isInputMode || inputBuffer.isEmpty()) return
        inputBuffer.deleteCharAt(inputBuffer.lastIndex)
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    fun onSpaceInput() {
        if (!isInputMode) return
        inputBuffer.append(' ')
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    fun onEnterInput() {
        if (!isInputMode) return
        isInputMode = false
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    fun exitInputMode() {
        isInputMode = false
        composingHint = ""
        invalidate()
    }

    fun onComposedTextInput(text: String) {
        if (!isInputMode) return
        inputBuffer.append(text)
        composingHint = ""
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    fun updateComposingHint(hint: String) {
        composingHint = hint
        invalidate()
    }
}
