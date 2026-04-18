package com.snowrealm.yukiboard.input.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateManagerTest {

    @Test
    fun emptyStateIsQuiet() {
        val mgr = CandidateManager()

        assertTrue(mgr.candidates.isEmpty())
        assertTrue(mgr.currentPage.isEmpty())
        assertFalse(mgr.hasNextPage)
        assertFalse(mgr.hasPrevPage)
        assertEquals(-1, mgr.focusedIndex)
        assertFalse(mgr.hasFocus())
    }

    @Test
    fun updatePopulatesFirstPageAndPagination() {
        val mgr = CandidateManager()
        val fifteen = (1..15).map { "c$it" }

        mgr.update(fifteen)

        assertEquals(fifteen, mgr.candidates)
        assertEquals(9, mgr.currentPage.size)
        assertEquals("c1", mgr.currentPage[0])
        assertEquals("c9", mgr.currentPage[8])
        assertTrue(mgr.hasNextPage)
        assertFalse(mgr.hasPrevPage)
    }

    @Test
    fun nextPageThenPrevPageReturnsToStart() {
        val mgr = CandidateManager()
        mgr.update((1..15).map { "c$it" })

        mgr.nextPage()
        assertEquals(6, mgr.currentPage.size)
        assertEquals("c10", mgr.currentPage[0])
        assertFalse(mgr.hasNextPage)
        assertTrue(mgr.hasPrevPage)

        mgr.prevPage()
        assertEquals("c1", mgr.currentPage[0])
        assertTrue(mgr.hasNextPage)
        assertFalse(mgr.hasPrevPage)
    }

    @Test
    fun focusNextCyclesOnCurrentPage() {
        val mgr = CandidateManager()
        mgr.update(listOf("a", "b", "c"))

        assertEquals("a", mgr.focusNext())
        assertTrue(mgr.hasFocus())
        assertEquals(0, mgr.focusedIndex)

        assertEquals("b", mgr.focusNext())
        assertEquals("c", mgr.focusNext())
        // After last, cycles back to first
        assertEquals("a", mgr.focusNext())
    }

    @Test
    fun confirmFocusedReturnsFocusedTextOrNull() {
        val mgr = CandidateManager()
        assertNull(mgr.confirmFocused())

        mgr.update(listOf("x", "y", "z"))
        mgr.focusNext()
        mgr.focusNext()
        assertEquals("y", mgr.confirmFocused())
    }

    @Test
    fun selectAtReturnsByAbsoluteIndexWithinCurrentPage() {
        val mgr = CandidateManager()
        mgr.update((1..15).map { "c$it" })

        assertEquals("c3", mgr.selectAt(2))

        mgr.nextPage()
        // Now pageIndex = 1, so selectAt(0) → absolute index 9 → "c10"
        assertEquals("c10", mgr.selectAt(0))
    }

    @Test
    fun clearResetsEverything() {
        val mgr = CandidateManager()
        mgr.update(listOf("a", "b", "c"))
        mgr.focusNext()

        mgr.clear()

        assertTrue(mgr.candidates.isEmpty())
        assertTrue(mgr.currentPage.isEmpty())
        assertEquals(-1, mgr.focusedIndex)
        assertEquals(0, mgr.currentCandidateCount)
        assertTrue(mgr.historyCandidates.isEmpty())
    }

    @Test
    fun archiveCurrentAsHistoryKeepsNonSelectedAsHistory() {
        val mgr = CandidateManager()
        mgr.update(listOf("apple", "ant", "air"))

        mgr.archiveCurrentAsHistory("apple")

        // Non-selected entries move to history
        assertTrue(mgr.historyCandidates.contains("ant"))
        assertTrue(mgr.historyCandidates.contains("air"))
        assertFalse(mgr.historyCandidates.contains("apple"))
    }

    @Test
    fun subsequentUpdateAutoArchivesPreviousCurrentIntoHistory() {
        val mgr = CandidateManager()
        mgr.update(listOf("alpha", "beta"))
        mgr.update(listOf("gamma", "delta"))

        // Previous current (alpha, beta) should now appear in history portion of candidates
        assertTrue(mgr.historyCandidates.containsAll(listOf("alpha", "beta")))
        // Current page leads with the new current candidates
        assertEquals("gamma", mgr.currentPage[0])
        assertEquals("delta", mgr.currentPage[1])
    }

    @Test
    fun pageSizeConstantIsNine() {
        assertEquals(9, CandidateManager.PAGE_SIZE)
    }
}
