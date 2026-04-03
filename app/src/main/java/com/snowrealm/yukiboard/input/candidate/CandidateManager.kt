package com.snowrealm.yukiboard.input.candidate

/**
 * 候選詞管理器。
 *
 * 職責：
 * - 持有目前的候選詞列表
 * - 管理分頁（每頁最多 [PAGE_SIZE] 個候選詞）
 * - 追蹤使用者選擇的候選詞
 * - 支援「預測」標記（灰色字，區分智慧預測 vs 一般候選）
 *
 * Tab 鍵導航：
 * - 按 Tab = 選中下一個候選
 * - 按空白 = 確認當前選中的候選
 * - 直接點選 = 確認該候選
 */
class CandidateManager {

    private val _candidates = mutableListOf<String>()
    val candidates: List<String> get() = _candidates.toList()

    private var pageIndex = 0

    /** 目前 Tab 聚焦的候選索引，-1 = 無聚焦 */
    var focusedIndex: Int = -1
        private set

    val currentPage: List<String>
        get() {
            val from = pageIndex * PAGE_SIZE
            val to   = minOf(from + PAGE_SIZE, _candidates.size)
            return if (from >= _candidates.size) emptyList() else _candidates.subList(from, to)
        }

    val hasNextPage: Boolean get() = (pageIndex + 1) * PAGE_SIZE < _candidates.size
    val hasPrevPage: Boolean get() = pageIndex > 0

    fun update(newCandidates: List<String>) {
        _candidates.clear()
        _candidates.addAll(newCandidates)
        pageIndex = 0
        focusedIndex = -1
    }

    fun nextPage() {
        if (hasNextPage) { pageIndex++; focusedIndex = -1 }
    }

    fun prevPage() {
        if (hasPrevPage) { pageIndex--; focusedIndex = -1 }
    }

    /** Tab 鍵：聚焦下一個候選 */
    fun focusNext(): String? {
        val page = currentPage
        if (page.isEmpty()) return null
        focusedIndex = if (focusedIndex < page.size - 1) focusedIndex + 1 else 0
        return page.getOrNull(focusedIndex)
    }

    /** 確認目前聚焦的候選（空白鍵觸發） */
    fun confirmFocused(): String? {
        if (focusedIndex < 0) return null
        return selectAt(focusedIndex)
    }

    /** 是否有聚焦候選 */
    fun hasFocus(): Boolean = focusedIndex >= 0

    fun selectAt(index: Int): String? {
        val actual = pageIndex * PAGE_SIZE + index
        return _candidates.getOrNull(actual)
    }

    fun clear() {
        _candidates.clear()
        pageIndex = 0
        focusedIndex = -1
    }

    companion object {
        const val PAGE_SIZE = 9
    }
}
