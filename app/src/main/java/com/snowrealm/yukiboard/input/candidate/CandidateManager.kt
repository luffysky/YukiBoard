package com.snowrealm.yukiboard.input.candidate

/**
 * 候選詞管理器。
 *
 * 職責：
 * - 持有目前的候選詞列表
 * - 管理分頁（每頁最多 [PAGE_SIZE] 個候選詞）
 * - 追蹤使用者選擇的候選詞
 * - 支援「預測」標記（灰色字，區分智慧預測 vs 一般候選）
 * - 候選字歷史累積：選字後前一段候選保存為歷史，使用者可回頭選
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

    // ─── 候選字歷史累積 ───
    /** 歷史候選字（前面詞段選過但未選的候選字） */
    private val _historyCandidates = mutableListOf<String>()
    val historyCandidates: List<String> get() = _historyCandidates.toList()

    /** 目前段的候選字數量（用於區分 current vs history） */
    var currentCandidateCount: Int = 0
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
        // 自動歸檔：如果有舊的當前段候選字，且新候選字跟舊的不同，
        // 把舊候選存入歷史（使用者持續打字不選字時，前段候選不會消失）
        if (currentCandidateCount > 0) {
            // 取出上一輪的「當前段」候選（在列表頭部）
            val oldCurrent = _candidates.take(currentCandidateCount)
            val isDifferent = newCandidates.isEmpty() || oldCurrent != newCandidates.take(oldCurrent.size)
            if (isDifferent) {
                for (c in oldCurrent) {
                    if (c !in _historyCandidates && c !in newCandidates) {
                        _historyCandidates.add(c)
                    }
                }
                while (_historyCandidates.size > 30) {
                    _historyCandidates.removeAt(0)
                }
            }
        }

        _candidates.clear()
        // 當前段候選字排最前面（使用者正在打的內容優先）
        _candidates.addAll(newCandidates)
        // 歷史候選字排後面（前段打過的詞，供回選）
        for (h in _historyCandidates) {
            if (h !in newCandidates) _candidates.add(h)
        }
        currentCandidateCount = newCandidates.size
        pageIndex = 0
        focusedIndex = -1
    }

    /**
     * 選字時呼叫：把當前段的候選字存入歷史（排除已選的），
     * 供後續顯示為淺色歷史候選字。
     */
    fun archiveCurrentAsHistory(selectedText: String) {
        // 把目前段的非選中候選字存到歷史（保留最近 20 個）
        val currentOnly = _candidates.take(currentCandidateCount)
        for (c in currentOnly) {
            if (c != selectedText && c !in _historyCandidates) {
                _historyCandidates.add(c)
            }
        }
        // 限制歷史大小
        while (_historyCandidates.size > 20) {
            _historyCandidates.removeAt(0)
        }
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
        _historyCandidates.clear()
        currentCandidateCount = 0
        pageIndex = 0
        focusedIndex = -1
    }

    /** 只清歷史，不清當前候選 */
    fun clearHistory() {
        _historyCandidates.clear()
    }

    companion object {
        const val PAGE_SIZE = 9
    }
}
