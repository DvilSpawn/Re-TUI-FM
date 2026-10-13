package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class NavigationStateTest {
    @Test fun keepsRecentDirectoriesNewestFirstAndExcludesTrash() {
        val root = Files.createTempDirectory("fm-nav").toFile()
        try {
            val one = root.resolve("one").apply { mkdir() }
            val two = root.resolve("two").apply { mkdir() }
            val trash = root.resolve(".retui-trash").apply { mkdir() }
            val result = NavigationStateStore.validPaths(
                listOf(one.path, two.path, one.path, trash.path),
                isTrash = { it.name == ".retui-trash" }
            )
            assertEquals(listOf(one.canonicalPath, two.canonicalPath), result)
        } finally { root.deleteRecursively() }
    }

    @Test fun capsRecentDirectoriesAtTwelve() {
        val root = Files.createTempDirectory("fm-nav-cap").toFile()
        try {
            val dirs = (0 until 14).map { root.resolve("d$it").apply { mkdir() } }
            val result = NavigationStateStore.validPaths(dirs.reversed().map { it.path })
            assertEquals(12, result.size)
            assertTrue(result.none { it.endsWith("d0") || it.endsWith("d1") })
        } finally { root.deleteRecursively() }
    }
}
