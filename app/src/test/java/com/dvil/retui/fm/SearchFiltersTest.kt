package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFiltersTest {
    @Test fun parsesSizeAndAgeFiltersWithoutConsumingSearchTerms() {
        val filters = parseSearchFilters(listOf("report", "size>1M", "older>7d", "pdf"), 1_000_000_000L)
        assertEquals(listOf("report", "pdf"), filters.terms)
        assertEquals(1_048_577L, filters.minBytes)
        assertNull(filters.maxBytes)
        assertEquals(395_200_000L, filters.modifiedBefore)
    }
}
