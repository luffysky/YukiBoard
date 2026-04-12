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
 * - 點擊卡片 → inline 編輯、長按卡片 → 刪除確認
 * - 底部匯出/匯入按鈕
 * - 暗色主題：bg 0xFF0D1B2A, cards 0xFF1A2A3A, text 0xFFE8F4FF, accent 0xFF9ECFFF
 */
class UserDictionaryPanelView(context: Context) : View(context), PanelInputTarget {

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
    /** 由 Service 提供的 Dialog 顯示回呼（僅剩刪除確認用） */
    var onShowDialog: ((AlertDialog) -> Unit)? = null

    // ── 資料 ──
    private var allEntries = listOf<Triple<String, String, String>>() // word, bopomofo, category
    private var filteredEntries = listOf<Triple<String, String, String>>()
    private var searchQuery = ""
    private var selectedCategory = "全部"
    private val categoryTabs = mutableListOf("全部")
    /** 使用者自訂分類（非內建的） */
    private val customCategories = mutableListOf<String>()

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
    private var isInputMode = true  // 預設 true，像 LangLearnPanel 一樣直接接收輸入
    private val inputBuffer = StringBuilder()
    private var composingHint = ""

    // ── 新增/編輯模式（inline，不用 Dialog） ──
    private var isAddMode = false
    private var isEditMode = false
    private var editingIndex = -1       // 正在編輯的 filteredEntries index
    private var editingOldWord = ""     // 編輯前的原始 word（用於 updateManual）
    private var addField = 0  // 0=詞彙, 1=注音, 2=新增分類名稱
    private val addWordBuffer = StringBuilder()
    private val addBopoBuffer = StringBuilder()
    private val newCategoryBuffer = StringBuilder()
    private var addConfirmRect = RectF()
    private var addCancelRect = RectF()
    private var addFieldWordRect = RectF()
    private var addFieldBopoRect = RectF()
    private var addSelectedCategory = UserDictionary.DEFAULT_CATEGORY

    // ── 分類選擇器（新增/編輯模式用） ──
    private val catChipRects = mutableListOf<Pair<RectF, String>>()
    private var catScrollX = 0f
    private var catTouchDownX = 0f
    private var catTouchDownScrollX = 0f
    private var isCatDragging = false
    private var newCatBtnRect = RectF()
    private var isNewCatMode = false // 正在輸入新分類名稱
    private var newCatConfirmRect = RectF()
    private var newCatCancelRect = RectF()
    private var newCatFieldRect = RectF()

    // ── 尺寸 ──
    private val headerH = 42f * density
    private val tabBarH = 34f * density
    private val searchBarH = 36f * density
    private val catBarH = 32f * density
    private val itemH = 58f * density
    private val itemGap = 5f * density
    private val cardR = 10f * density
    private val padding = 10f * density
    private val bottomBarH = 38f * density

    fun reloadEntries() {
        allEntries = userDictionary?.getAllManualWithCategory() ?: emptyList()
        categoryTabs.clear()
        categoryTabs.add("全部")
        val allCats = userDictionary?.getAllCategories() ?: UserDictionary.BUILT_IN_CATEGORIES
        for (c in allCats) if (c != "全部" && c !in categoryTabs) categoryTabs.add(c)
        // 追蹤自訂分類
        customCategories.clear()
        customCategories.addAll(allCats.filter { it !in UserDictionary.BUILT_IN_CATEGORIES })
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
        catChipRects.clear()

        // 背景
        bgPaint.color = BG_COLOR
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawHeader(canvas)

        var contentTop = headerH
        if (isAddMode || isEditMode) {
            contentTop += drawAddEditBar(canvas, contentTop)
            if (isNewCatMode) {
                contentTop += drawNewCategoryBar(canvas, contentTop)
            } else {
                contentTop += drawCategorySelector(canvas, contentTop)
            }
        } else if (isSearchMode) {
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
    // Category tabs (horizontal scroll) — 列表篩選用
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

            // Card background — 編輯中的卡片高亮
            val isEditing = isEditMode && editingIndex == i
            cardPaint.color = if (isEditing) 0xFF253D55.toInt() else CARD_COLOR
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
    // Add/Edit mode bar（詞彙 + 注音輸入欄）
    // =========================================================================
    private fun drawAddEditBar(canvas: Canvas, top: Float): Float {
        val barH = searchBarH * 2.2f
        // 背景
        cardPaint.color = 0xFF1A3050.toInt()
        canvas.drawRoundRect(padding, top + 2, width - padding, top + barH - 2, cardR, cardR, cardPaint)

        val fieldH = (barH - 8 * density) / 2f
        val left = padding + 8 * density
        val right = width - padding - 70 * density

        // 標題
        val modeLabel = if (isEditMode) "✏️ 編輯" else "➕ 新增"
        titlePaint.textSize = 10f * density
        titlePaint.color = ACCENT_COLOR
        // 不佔空間，畫在背景框右上角
        canvas.drawText(modeLabel, right + 6 * density, top + 12 * density, titlePaint)

        // 詞彙欄位
        val wordTop = top + 4 * density
        addFieldWordRect.set(left, wordTop, right, wordTop + fieldH - 2 * density)
        cardPaint.color = if (addField == 0) 0xFF253D55.toInt() else 0xFF1E3245.toInt()
        canvas.drawRoundRect(addFieldWordRect, 6 * density, 6 * density, cardPaint)
        if (addField == 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * density; color = ACCENT_COLOR
            }
            canvas.drawRoundRect(addFieldWordRect, 6 * density, 6 * density, borderPaint)
        }
        val wordDisplay = if (addField == 0) addWordBuffer.toString() + composingHint + "▏" else addWordBuffer.toString()
        titlePaint.textSize = 13f * density
        titlePaint.color = if (addWordBuffer.isEmpty() && addField != 0) DIM_TEXT else TEXT_COLOR
        val wordText = if (wordDisplay.isEmpty()) "詞彙（直接打字）" else wordDisplay
        if (wordDisplay.isEmpty()) titlePaint.color = DIM_TEXT
        canvas.drawText(wordText, left + 8 * density, addFieldWordRect.centerY() + 5 * density, titlePaint)

        // 注音欄位
        val bopoTop = wordTop + fieldH
        addFieldBopoRect.set(left, bopoTop, right, bopoTop + fieldH - 2 * density)
        cardPaint.color = if (addField == 1) 0xFF253D55.toInt() else 0xFF1E3245.toInt()
        canvas.drawRoundRect(addFieldBopoRect, 6 * density, 6 * density, cardPaint)
        if (addField == 1) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * density; color = ACCENT_COLOR
            }
            canvas.drawRoundRect(addFieldBopoRect, 6 * density, 6 * density, borderPaint)
        }
        val bopoDisplay = if (addField == 1) addBopoBuffer.toString() + composingHint + "▏" else addBopoBuffer.toString()
        titlePaint.color = if (bopoDisplay.isEmpty()) DIM_TEXT else TEXT_COLOR
        val bopoText = if (bopoDisplay.isEmpty()) "注音（直接打字）" else bopoDisplay
        canvas.drawText(bopoText, left + 8 * density, addFieldBopoRect.centerY() + 5 * density, titlePaint)

        // 確認按鈕
        val btnW = 28 * density
        val btnRight = width - padding - 6 * density
        addConfirmRect.set(btnRight - btnW, wordTop, btnRight, wordTop + fieldH - 2 * density)
        cardPaint.color = 0xFF2A6B4A.toInt()
        canvas.drawRoundRect(addConfirmRect, 6 * density, 6 * density, cardPaint)
        btnPaint.textSize = 16f * density
        btnPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("✓", addConfirmRect.centerX(), addConfirmRect.centerY() + 6 * density, btnPaint)

        // 取消按鈕
        addCancelRect.set(btnRight - btnW, bopoTop, btnRight, bopoTop + fieldH - 2 * density)
        cardPaint.color = 0xFF6B2A2A.toInt()
        canvas.drawRoundRect(addCancelRect, 6 * density, 6 * density, cardPaint)
        canvas.drawText("✕", addCancelRect.centerX(), addCancelRect.centerY() + 6 * density, btnPaint)

        return barH
    }

    // =========================================================================
    // Category selector（新增/編輯時選擇分類）
    // =========================================================================
    private fun drawCategorySelector(canvas: Canvas, top: Float): Float {
        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), top + catBarH)

        val chipH = 24f * density
        val chipY = top + (catBarH - chipH) / 2f
        val chipPadH = 10f * density
        val chipGap = 5f * density
        val chipRadius = chipH / 2f

        chipTextPaint.textSize = 10f * density

        var x = padding - catScrollX

        val allCats = (UserDictionary.BUILT_IN_CATEGORIES + customCategories).distinct()
        for (cat in allCats) {
            val isActive = cat == addSelectedCategory
            val textW = chipTextPaint.measureText(cat)
            val chipW = textW + chipPadH * 2
            val rect = RectF(x, chipY, x + chipW, chipY + chipH)
            catChipRects.add(rect to cat)

            val chipColor = CATEGORY_COLORS[cat] ?: ACCENT_COLOR
            chipPaint.color = if (isActive) chipColor else (chipColor and 0x00FFFFFF) or 0x25000000
            canvas.drawRoundRect(rect, chipRadius, chipRadius, chipPaint)

            if (isActive) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = chipColor
                }
                canvas.drawRoundRect(rect, chipRadius, chipRadius, borderPaint)
            }

            chipTextPaint.color = if (isActive) 0xFFFFFFFF.toInt() else chipColor
            canvas.drawText(cat, rect.centerX(), rect.centerY() + 3.5f * density, chipTextPaint)

            x += chipW + chipGap
        }

        // ＋新增分類 按鈕
        val addCatText = "＋新增"
        val addCatTextW = chipTextPaint.measureText(addCatText)
        val addCatW = addCatTextW + chipPadH * 2
        newCatBtnRect = RectF(x, chipY, x + addCatW, chipY + chipH)
        chipPaint.color = (ACCENT_COLOR and 0x00FFFFFF) or 0x18000000
        canvas.drawRoundRect(newCatBtnRect, chipRadius, chipRadius, chipPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f * density; color = ACCENT_COLOR; alpha = 100
        }
        canvas.drawRoundRect(newCatBtnRect, chipRadius, chipRadius, borderPaint)
        chipTextPaint.color = ACCENT_COLOR
        canvas.drawText(addCatText, newCatBtnRect.centerX(), newCatBtnRect.centerY() + 3.5f * density, chipTextPaint)

        canvas.restore()
        return catBarH
    }

    // =========================================================================
    // New category inline input
    // =========================================================================
    private fun drawNewCategoryBar(canvas: Canvas, top: Float): Float {
        val barH = 32f * density
        val left = padding + 8 * density
        val fieldRight = width - padding - 70 * density

        // 輸入框
        newCatFieldRect = RectF(left, top + 3 * density, fieldRight, top + barH - 3 * density)
        cardPaint.color = if (addField == 2) 0xFF253D55.toInt() else 0xFF1E3245.toInt()
        canvas.drawRoundRect(newCatFieldRect, 6 * density, 6 * density, cardPaint)
        if (addField == 2) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1f * density; color = ACCENT_COLOR
            }
            canvas.drawRoundRect(newCatFieldRect, 6 * density, 6 * density, bp)
        }

        val display = if (addField == 2) newCategoryBuffer.toString() + composingHint + "▏" else newCategoryBuffer.toString()
        titlePaint.textSize = 12f * density
        titlePaint.color = if (display.isEmpty()) DIM_TEXT else TEXT_COLOR
        val text = if (display.isEmpty()) "新分類名稱..." else display
        canvas.drawText(text, left + 8 * density, newCatFieldRect.centerY() + 4 * density, titlePaint)

        // 確認
        val btnW = 28 * density
        val btnRight = width - padding - 6 * density
        newCatConfirmRect = RectF(btnRight - btnW, top + 3 * density, btnRight, top + barH - 3 * density)
        cardPaint.color = 0xFF2A6B4A.toInt()
        canvas.drawRoundRect(newCatConfirmRect, 6 * density, 6 * density, cardPaint)
        btnPaint.textSize = 14f * density
        btnPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText("✓", newCatConfirmRect.centerX(), newCatConfirmRect.centerY() + 5 * density, btnPaint)

        // 取消
        newCatCancelRect = RectF(btnRight - btnW * 2 - 4 * density, top + 3 * density, btnRight - btnW - 4 * density, top + barH - 3 * density)
        cardPaint.color = 0xFF6B2A2A.toInt()
        canvas.drawRoundRect(newCatCancelRect, 6 * density, 6 * density, cardPaint)
        canvas.drawText("✕", newCatCancelRect.centerX(), newCatCancelRect.centerY() + 5 * density, btnPaint)

        return barH
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
                val addEditH = if (isAddMode || isEditMode) searchBarH * 2.2f + (if (isNewCatMode) 32f * density else catBarH) else 0f
                val searchH = if (isSearchMode && !isAddMode && !isEditMode) searchBarH else 0f
                val tabTop = headerH + addEditH + searchH
                if (event.y in tabTop..(tabTop + tabBarH)) {
                    tabTouchDownX = event.x
                    tabTouchDownScrollX = tabScrollX
                    isTabDragging = false
                }

                // Category selector horizontal scroll
                if ((isAddMode || isEditMode) && !isNewCatMode) {
                    val catTop = headerH + searchBarH * 2.2f
                    if (event.y in catTop..(catTop + catBarH)) {
                        catTouchDownX = event.x
                        catTouchDownScrollX = catScrollX
                        isCatDragging = false
                    }
                }

                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastTouchY
                val dx = event.x - touchDownX

                // Tab bar horizontal drag
                val addEditH = if (isAddMode || isEditMode) searchBarH * 2.2f + (if (isNewCatMode) 32f * density else catBarH) else 0f
                val searchH = if (isSearchMode && !isAddMode && !isEditMode) searchBarH else 0f
                val tabTop = headerH + addEditH + searchH
                if (touchDownY in tabTop..(tabTop + tabBarH) && Math.abs(dx) > 8 * density) {
                    isTabDragging = true
                    tabScrollX = (tabTouchDownScrollX - (event.x - tabTouchDownX)).coerceAtLeast(0f)
                    invalidate()
                    return true
                }

                // Category selector horizontal drag
                if ((isAddMode || isEditMode) && !isNewCatMode) {
                    val catTop = headerH + searchBarH * 2.2f
                    if (touchDownY in catTop..(catTop + catBarH) && Math.abs(dx) > 8 * density) {
                        isCatDragging = true
                        catScrollX = (catTouchDownScrollX - (event.x - catTouchDownX)).coerceAtLeast(0f)
                        invalidate()
                        return true
                    }
                }

                if (!isDragging && Math.abs(event.y - touchDownY) > 8 * density) {
                    isDragging = true
                }
                if (isDragging) {
                    val contentTop = headerH + tabBarH + addEditH + searchH
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
                if (isDragging || isTabDragging || isCatDragging) {
                    isTabDragging = false
                    isCatDragging = false
                    performClick()
                    return true
                }

                val elapsed = System.currentTimeMillis() - touchDownTime

                // New category mode buttons
                if (isNewCatMode) {
                    if (newCatConfirmRect.contains(event.x, event.y)) {
                        commitNewCategory()
                        performClick()
                        return true
                    }
                    if (newCatCancelRect.contains(event.x, event.y)) {
                        isNewCatMode = false
                        addField = 0
                        newCategoryBuffer.clear()
                        composingHint = ""
                        invalidate()
                        performClick()
                        return true
                    }
                    if (newCatFieldRect.contains(event.x, event.y)) {
                        addField = 2
                        composingHint = ""
                        invalidate()
                        performClick()
                        return true
                    }
                }

                // Add/Edit mode buttons
                if (isAddMode || isEditMode) {
                    if (addConfirmRect.contains(event.x, event.y)) {
                        commitAddEditEntry()
                        performClick()
                        return true
                    }
                    if (addCancelRect.contains(event.x, event.y)) {
                        cancelAddEditMode()
                        performClick()
                        return true
                    }
                    if (addFieldWordRect.contains(event.x, event.y)) {
                        addField = 0
                        composingHint = ""
                        invalidate()
                        performClick()
                        return true
                    }
                    if (addFieldBopoRect.contains(event.x, event.y)) {
                        addField = 1
                        composingHint = ""
                        invalidate()
                        performClick()
                        return true
                    }

                    // Category chip selection
                    if (!isNewCatMode) {
                        val catHit = catChipRects.firstOrNull { it.first.contains(event.x, event.y) }
                        if (catHit != null) {
                            addSelectedCategory = catHit.second
                            invalidate()
                            performClick()
                            return true
                        }
                        if (newCatBtnRect.contains(event.x, event.y)) {
                            isNewCatMode = true
                            addField = 2
                            newCategoryBuffer.clear()
                            composingHint = ""
                            invalidate()
                            performClick()
                            return true
                        }
                    }
                }

                // Header buttons
                if (addBtnRect.contains(event.x, event.y)) {
                    enterAddMode()
                    performClick()
                    return true
                }
                if (searchBtnRect.contains(event.x, event.y)) {
                    if (isSearchMode) {
                        isSearchMode = false
                        inputBuffer.clear()
                        composingHint = ""
                        searchQuery = ""
                        applyFilter()
                    } else {
                        cancelAddEditMode()
                        isSearchMode = true
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

                // Item tap → inline edit; long press → delete
                val itemHit = itemRects.firstOrNull { it.first.contains(event.x, event.y) }
                if (itemHit != null && itemHit.second < filteredEntries.size) {
                    if (elapsed > 500) {
                        showDeleteConfirmation(itemHit.second)
                    } else {
                        enterEditMode(itemHit.second)
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
    // Add / Edit mode management
    // =========================================================================

    private fun enterAddMode() {
        isAddMode = true
        isEditMode = false
        editingIndex = -1
        addField = 0
        addWordBuffer.clear()
        addBopoBuffer.clear()
        addSelectedCategory = UserDictionary.DEFAULT_CATEGORY
        composingHint = ""
        isSearchMode = false
        isNewCatMode = false
        catScrollX = 0f
        scrollY = 0f
        invalidate()
    }

    private fun enterEditMode(index: Int) {
        if (index !in filteredEntries.indices) return
        val (word, bopo, cat) = filteredEntries[index]
        isEditMode = true
        isAddMode = false
        editingIndex = index
        editingOldWord = word
        addField = 0
        addWordBuffer.clear().append(word)
        addBopoBuffer.clear().append(bopo)
        addSelectedCategory = cat
        composingHint = ""
        isSearchMode = false
        isNewCatMode = false
        catScrollX = 0f
        invalidate()
    }

    private fun commitAddEditEntry() {
        val word = addWordBuffer.toString().trim()
        val bopo = addBopoBuffer.toString().trim()
        if (word.isNotBlank() && bopo.isNotBlank()) {
            if (isEditMode) {
                userDictionary?.updateManual(editingOldWord, word, bopo, addSelectedCategory)
                Toast.makeText(context, "已更新「$word」", Toast.LENGTH_SHORT).show()
            } else {
                userDictionary?.addManual(word, bopo, addSelectedCategory)
                Toast.makeText(context, "已新增「$word」", Toast.LENGTH_SHORT).show()
            }
            reloadEntries()
        } else {
            Toast.makeText(context, "詞彙和注音都不可為空", Toast.LENGTH_SHORT).show()
        }
        cancelAddEditMode()
    }

    private fun cancelAddEditMode() {
        isAddMode = false
        isEditMode = false
        editingIndex = -1
        addField = 0
        addWordBuffer.clear()
        addBopoBuffer.clear()
        newCategoryBuffer.clear()
        composingHint = ""
        isNewCatMode = false
        invalidate()
    }

    private fun commitNewCategory() {
        val name = newCategoryBuffer.toString().trim()
        if (name.isNotBlank() && name !in UserDictionary.BUILT_IN_CATEGORIES && name !in customCategories) {
            customCategories.add(name)
            if (name !in categoryTabs) categoryTabs.add(name)
            addSelectedCategory = name
            Toast.makeText(context, "已新增分類「$name」", Toast.LENGTH_SHORT).show()
        } else if (name.isBlank()) {
            Toast.makeText(context, "分類名稱不可為空", Toast.LENGTH_SHORT).show()
        } else {
            // 分類已存在，直接選擇
            addSelectedCategory = name
        }
        isNewCatMode = false
        addField = 0
        newCategoryBuffer.clear()
        composingHint = ""
        invalidate()
    }

    // =========================================================================
    // Delete confirmation（僅此處保留 Dialog）
    // =========================================================================

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

    override val isInInputMode: Boolean get() = isInputMode

    private fun activeBuffer(): StringBuilder = when {
        isNewCatMode && addField == 2 -> newCategoryBuffer
        (isAddMode || isEditMode) && addField == 0 -> addWordBuffer
        (isAddMode || isEditMode) && addField == 1 -> addBopoBuffer
        else -> inputBuffer
    }

    override fun onCharInput(text: String) {
        if (!isInputMode) return
        activeBuffer().append(text)
        if (!isAddMode && !isEditMode && !isNewCatMode) { searchQuery = inputBuffer.toString(); applyFilter() }
        invalidate()
    }

    override fun onDeleteInput() {
        if (!isInputMode) return
        val buf = activeBuffer()
        if (buf.isEmpty()) return
        buf.deleteCharAt(buf.lastIndex)
        if (!isAddMode && !isEditMode && !isNewCatMode) { searchQuery = inputBuffer.toString(); applyFilter() }
        invalidate()
    }

    override fun onSpaceInput() {
        if (!isInputMode) return
        activeBuffer().append(' ')
        if (!isAddMode && !isEditMode && !isNewCatMode) { searchQuery = inputBuffer.toString(); applyFilter() }
        invalidate()
    }

    override fun onEnterInput() {
        if (!isInputMode) return
        if (isNewCatMode && addField == 2) {
            commitNewCategory()
            return
        }
        if (isAddMode || isEditMode) {
            when (addField) {
                0 -> {
                    // 詞彙欄 Enter → 切到注音欄
                    addField = 1
                    composingHint = ""
                }
                1 -> {
                    // 注音欄 Enter → 確認新增/編輯
                    commitAddEditEntry()
                }
            }
            invalidate()
            return
        }
        // 搜尋模式 Enter → 確認搜尋
        searchQuery = inputBuffer.toString()
        applyFilter()
        invalidate()
    }

    override fun enterInputMode() {
        isInputMode = true
        inputBuffer.clear()
        composingHint = ""
        invalidate()
    }

    override fun exitInputMode() {
        isInputMode = false
        composingHint = ""
        invalidate()
    }

    override fun onComposedTextInput(text: String) {
        if (!isInputMode) return
        activeBuffer().append(text)
        composingHint = ""
        if (!isAddMode && !isEditMode && !isNewCatMode) { searchQuery = inputBuffer.toString(); applyFilter() }
        invalidate()
    }

    override fun updateComposingHint(hint: String) {
        composingHint = hint
        invalidate()
    }
}
