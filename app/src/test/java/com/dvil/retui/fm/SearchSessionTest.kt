package com.dvil.retui.fm

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSessionTest {
    @Test
    fun cancellationStopsTraversalAndReportsIt() {
        val root = createTempDir(prefix = "retui-search-")
        try {
            repeat(20) { index -> File(root, "file-$index.txt").writeText("x") }
            lateinit var session: SearchSession
            var seen = 0
            session = SearchSession(listOf(root), 500, true, { file ->
                seen++
                if (seen == 5) session.cancel()
                file.name.endsWith(".txt")
            })
            val result = session.execute()
            assertTrue(result.cancelled)
            assertTrue(result.scanned < 21)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun truncationLimitsResults() {
        val root = createTempDir(prefix = "retui-search-")
        try {
            repeat(5) { index -> File(root, "file-$index.txt").writeText("x") }
            val result = SearchSession(listOf(root), 2, true, { it.isFile }).execute()
            assertEquals(2, result.files.size)
            assertTrue(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }
}
