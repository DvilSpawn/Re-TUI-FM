package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {
    @Test
    fun extractionUsesArchiveNameAndNumberedSibling() {
        val root = Files.createTempDirectory("archive-name").toFile()
        try {
            val archive = File(root, "backup.tar.gz").apply { writeBytes(byteArrayOf()) }
            assertEquals("backup", ArchiveExtractor.destinationFor(archive).name)
            File(root, "backup").mkdir()
            assertEquals("backup (2)", ArchiveExtractor.destinationFor(archive).name)
            File(root, "backup (2)").mkdir()
            assertEquals("backup (3)", ArchiveExtractor.destinationFor(archive).name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun zipExtractionRejectsPathsOutsideDestination() {
        val root = Files.createTempDirectory("archive-slip").toFile()
        try {
            val archive = File(root, "unsafe.zip")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry("../escaped.txt"))
                zip.write("nope".toByteArray())
                zip.closeEntry()
            }

            assertThrows(Exception::class.java) { ArchiveExtractor.extractNamed(archive) }
            assertFalse(File(root, "escaped.txt").exists())
            assertFalse(File(root, "unsafe").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun zipExtractionCreatesSiblingFolder() {
        val root = Files.createTempDirectory("archive-extract").toFile()
        try {
            val archive = File(root, "photos.zip")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry("trip/photo.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }

            val destination = ArchiveExtractor.extractNamed(archive)
            assertEquals("photos", destination.name)
            assertEquals("hello", File(destination, "trip/photo.txt").readText())
            assertTrue(archive.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archiveEntriesCanBeNavigatedWithoutExtraction() {
        val entries = listOf(
            ArchiveExtractor.Entry("docs/readme.txt", false, 5),
            ArchiveExtractor.Entry("docs/images/logo.png", false, 10),
            ArchiveExtractor.Entry("root.txt", false, 2)
        )

        assertEquals(listOf("docs", "root.txt"), ArchiveExtractor.children(entries, "").map { it.path })
        assertEquals(listOf("docs/images", "docs/readme.txt"), ArchiveExtractor.children(entries, "docs").map { it.path })
        assertEquals("docs", ArchiveExtractor.parent("docs/images"))
    }

    @Test
    fun selectedArchiveFileExtractsToChosenFolder() {
        val root = Files.createTempDirectory("archive-selected").toFile()
        try {
            val archive = File(root, "files.zip")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                zip.putNextEntry(ZipEntry("docs/one.txt"))
                zip.write("one".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("docs/two.txt"))
                zip.write("two".toByteArray())
                zip.closeEntry()
            }
            val destination = File(root, "chosen").apply { mkdir() }

            ArchiveExtractor.extractTo(archive, destination, setOf("docs/two.txt"))

            assertEquals("two", File(destination, "two.txt").readText())
            assertFalse(File(destination, "one.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
