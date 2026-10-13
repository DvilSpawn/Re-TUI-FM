package com.dvil.retui.fm

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class FileEssentialsTest {
    @Test fun renamePreservesDataAndRejectsUnsafeNamesAndCollisions() {
        val root = Files.createTempDirectory("fm-rename").toFile()
        try {
            val source = File(root, "notes.txt").apply { writeText("keep me") }
            File(root, "existing.txt").writeText("existing")
            for (name in listOf("", " ", ".", "..", "../escape", "a/b", "a\\b", "a\u0000b", "existing.txt")) {
                assertTrue(name, runCatching { renameFile(source, name) }.isFailure)
                assertEquals("keep me", source.readText())
            }
            assertEquals(source, renameFile(source, source.name))
            val renamed = renameFile(source, "नोट्स 01.txt")
            assertEquals("keep me", renamed.readText())
            assertFalse(source.exists())
            val directory = File(root, "folder").apply { mkdir() }
            File(directory, "child").writeText("child")
            assertEquals("child", File(renameFile(directory, "new folder"), "child").readText())
            assertEquals("existing", File(root, "existing.txt").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun batchRenameValidatesBeforeApplyingAndPreservesContents() {
        val root = Files.createTempDirectory("fm-batch-rename").toFile()
        try {
            val first = File(root, "one.txt").apply { writeText("1") }
            val second = File(root, "two.txt").apply { writeText("2") }
            val renamed = batchRename(listOf(first, second), listOf("alpha.txt", "beta.txt"))
            assertEquals("1", renamed[0].readText())
            assertEquals("2", renamed[1].readText())
            assertTrue(runCatching { batchRename(renamed, listOf("same.txt", "same.txt")) }.isFailure)
            assertEquals("1", File(root, "alpha.txt").readText())
            assertEquals("2", File(root, "beta.txt").readText())
        } finally { root.deleteRecursively() }
    }
}
