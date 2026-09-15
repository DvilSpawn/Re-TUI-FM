package com.dvil.retui.fm

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferEngineTest {
    @Test fun conflictsPartialFailuresAndCancellationAreExplicit() {
        val root = Files.createTempDirectory("fm-transfer").toFile()
        try {
            val source = File(root, "source").apply { mkdir() }
            File(source, "one.txt").writeText("one")
            File(source, "two.txt").writeText("two")
            val destination = File(root, "destination").apply { mkdir() }
            File(destination, "one.txt").writeText("old")
            val keepBoth = TransferEngine.transfer(source.listFiles()!!.toList(), destination, false, ConflictChoice.KEEP_BOTH, { false }) {}
            assertEquals(2, keepBoth.completed)
            assertEquals("old", File(destination, "one.txt").readText())
            assertEquals("one", File(destination, "one.txt.1").readText())

            val replaced = TransferEngine.transfer(listOf(File(source, "one.txt")), destination, false, ConflictChoice.REPLACE, { false }) {}
            assertEquals(1, replaced.completed)
            assertEquals("one", File(destination, "one.txt").readText())

            val skipped = TransferEngine.transfer(listOf(File(source, "one.txt")), destination, false, ConflictChoice.SKIP, { false }) {}
            assertEquals(1, skipped.skipped)
            File(source, "three.txt").writeText("three")
            val cancelled = TransferEngine.transfer(listOf(File(source, "three.txt")), destination, false, ConflictChoice.REPLACE, { true }) {}
            assertTrue(cancelled.cancelled)
            assertFalse(File(destination, "three.txt").exists())

            File(source, "large.bin").writeBytes(ByteArray(128 * 1024) { 7 })
            var cancellationChecks = 0
            val interrupted = TransferEngine.transfer(
                listOf(File(source, "large.bin")), destination, false, ConflictChoice.KEEP_BOTH,
                { cancellationChecks++ >= 5 }
            ) {}
            assertTrue(interrupted.cancelled)
            assertFalse(File(destination, "large.bin").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
