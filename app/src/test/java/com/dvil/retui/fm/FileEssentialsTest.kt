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
}
