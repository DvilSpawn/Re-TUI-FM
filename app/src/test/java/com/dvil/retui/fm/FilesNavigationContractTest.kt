package com.dvil.retui.fm

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesNavigationContractTest {
    @Test
    fun resolvesAndListsLauncherNavigationMetadata() {
        val root = Files.createTempDirectory("retui-files").toFile()
        val current = root.resolve("current").apply { mkdir() }
        val directory = current.resolve("Documents").apply { mkdir() }
        val file = current.resolve("draft.txt").apply { writeText("metadata must not expose this") }

        assertEquals(directory.canonicalPath, FilesNavigationContract.resolveDirectory(current.path, "Documents")?.path)
        assertEquals(root.canonicalPath, FilesNavigationContract.resolveDirectory(current.path, "../")?.path)

        val directories = FilesNavigationContract.entries(current.path, true, "")
        assertTrue(directories.any { it.name == "../" && it.isDirectory })
        assertTrue(directories.any { it.name == "Documents" && it.path == directory.canonicalPath })
        assertEquals(listOf("../"), FilesNavigationContract.entries(current.path, true, "..").map { it.name })

        val files = FilesNavigationContract.entries(current.path, false, "dra")
        assertEquals(listOf(file.canonicalPath), files.map { it.path })
        assertFalse(files.single().isDirectory)

        assertEquals(file.canonicalPath, FilesNavigationContract.launchTarget(current.path, "draft.txt")?.path)
        assertEquals(current.canonicalPath, FilesNavigationContract.startDirectory(file.path, root).path)
        assertEquals(root.path, FilesNavigationContract.startDirectory("missing", root).path)
    }
}
