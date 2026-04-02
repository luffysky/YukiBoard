package com.snowrealm.yukiboard.input.candidate

/**
 * 候選詞管理器。
 *
 * 職責：
 * - 持有目前的候選詞列表
 * - 管理分頁（每頁最多 [PAGE_SIZE] 個候選詞）
 * - 追蹤使用者選擇的候選詞（學習用，TODO：串接詞頻資料庫）
 */
class CandidateManager {

    private val _candidates = mutableListOf<String>()
    val candidates: List<String> get() = _candidates.toList()

    private var pageIndex = 0

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
    }

    fun nextPage() {
        if (hasNextPage) pageIndex++
    }

    fun prevPage() {
        if (hasPrevPage) pageIndex--
    }

    fun selectAt(index: Int): String? {
        val actual = pageIndex * PAGE_SIZE + index
        return _candidates.getOrNull(actual)
    }

    fun clear() {
        _candidates.clear()
        pageIndex = 0
    }

    companion object {
        const val PAGE_SIZE = 9
    }
}
