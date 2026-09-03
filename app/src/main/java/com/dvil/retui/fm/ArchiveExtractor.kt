package com.dvil.retui.fm

import com.github.junrar.Archive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipFile

internal object ArchiveExtractor {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_EXTRACTED_BYTES = 4L * 1024 * 1024 * 1024
    private val suffixes = listOf(
        ".tar.bz2", ".tar.gz", ".tar.xz", ".tbz2", ".tgz", ".txz",
        ".zip", ".jar", ".rar", ".7z", ".tar"
    )

    data class Entry(val path: String, val isDirectory: Boolean, val size: Long = 0)

    fun supports(file: File): Boolean = file.isFile && suffix(file) != null

    fun entries(archive: File): List<Entry> {
        val entries = ArrayList<Entry>()
        scan(archive) { entry, _ ->
            if (entries.size >= MAX_ENTRIES) throw IOException("Archive contains more than $MAX_ENTRIES entries")
            entries += entry
        }
        return entries
    }

    fun children(entries: List<Entry>, directory: String): List<Entry> {
        val prefix = directory.takeIf(String::isNotEmpty)?.plus('/') ?: ""
        val children = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            if (!entry.path.startsWith(prefix)) continue
            val relative = entry.path.removePrefix(prefix)
            if (relative.isEmpty()) continue
            val name = relative.substringBefore('/')
            val path = prefix + name
            val direct = relative == name
            val child = Entry(path, !direct || entry.isDirectory, if (direct) entry.size else 0)
            val existing = children[path]
            if (existing == null || child.isDirectory && !existing.isDirectory) children[path] = child
        }
        return children.values.sortedWith(compareBy<Entry> { !it.isDirectory }.thenBy { it.path.lowercase(Locale.US) })
    }

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun destinationFor(archive: File, parent: File = archive.parentFile ?: error("Archive has no parent folder")): File {
        val archiveSuffix = suffix(archive) ?: error("Unsupported archive: ${archive.name}")
        val stem = archive.name.dropLast(archiveSuffix.length).ifBlank { "archive" }
        return uniqueChild(parent, stem, true)
    }

    fun extractNamed(archive: File, selected: Set<String> = emptySet()): File {
        val destination = reserveNamedDestination(archive)
        try {
            extractInto(archive, destination, selected, preserveArchivePaths = true)
            return destination
        } catch (failure: Exception) {
            destination.deleteRecursively()
            throw failure
        }
    }

    fun extractTo(archive: File, destination: File, selected: Set<String> = emptySet()): List<File> {
        require(destination.isDirectory) { "Not a folder: ${destination.absolutePath}" }
        val created = ArrayList<File>()
        try {
            extractInto(archive, destination, selected, preserveArchivePaths = false, createdRoots = created)
            return created
        } catch (failure: Exception) {
            created.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
            throw failure
        }
    }

    private fun extractInto(
        archive: File,
        destination: File,
        selected: Set<String>,
        preserveArchivePaths: Boolean,
        createdRoots: MutableList<File> = ArrayList()
    ) {
        val roots = selectionRoots(selected)
        val mappedRoots = HashMap<String, File>()
        val budget = ExtractionBudget()
        var extracted = 0
        scan(archive) { entry, copy ->
            val selectedRoot = roots.firstOrNull { entry.path == it || entry.path.startsWith("$it/") }
            if (roots.isNotEmpty() && selectedRoot == null) return@scan
            if (++extracted > MAX_ENTRIES) throw IOException("Archive contains more than $MAX_ENTRIES entries")

            val outputPath = if (preserveArchivePaths) {
                entry.path
            } else {
                val root = selectedRoot ?: entry.path.substringBefore('/')
                root.substringAfterLast('/') + entry.path.removePrefix(root)
            }
            val target = if (preserveArchivePaths) {
                safeTarget(destination, outputPath)
            } else {
                val topName = outputPath.substringBefore('/')
                val top = mappedRoots.getOrPut(topName) {
                    val isDirectory = outputPath.contains('/') || entry.isDirectory
                    uniqueChild(destination, topName, isDirectory).also(createdRoots::add)
                }
                outputPath.substringAfter('/', "").takeIf(String::isNotEmpty)?.let { safeTarget(top, it) } ?: top
            }

            if (entry.isDirectory) {
                if (!target.isDirectory && !target.mkdirs()) throw IOException("Could not create ${target.name}")
            } else {
                val parent = target.parentFile ?: throw IOException("Invalid archive path: ${entry.path}")
                if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Could not create ${parent.name}")
                FileOutputStream(target).use { output ->
                    copy(BudgetOutputStream(output, budget))
                }
            }
        }
    }

    private fun scan(archive: File, action: (Entry, (OutputStream) -> Unit) -> Unit) {
        when (suffix(archive)) {
            ".zip", ".jar" -> ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val item = entries.nextElement()
                    val entry = entry(item.name, item.isDirectory, item.size)
                    action(entry) { output -> zip.getInputStream(item).use { it.copyTo(output) } }
                }
            }
            ".7z" -> SevenZFile.builder().setFile(archive).get().use { sevenZip ->
                while (true) {
                    val item = sevenZip.nextEntry ?: break
                    val entry = entry(item.name ?: throw IOException("7z archive contains an unnamed entry"), item.isDirectory, item.size)
                    action(entry) { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = sevenZip.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            ".rar" -> Archive(archive).use { rar ->
                if (rar.isEncrypted) throw IOException("Password-protected RAR archives are not supported")
                for (item in rar) {
                    if (item.isEncrypted) throw IOException("Password-protected RAR archives are not supported")
                    val entry = entry(item.fileName, item.isDirectory, item.unpSize)
                    action(entry) { output -> rar.extractFile(item, output) }
                }
            }
            ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz" ->
                tarInput(archive).use { tar ->
                    while (true) {
                        val item = tar.nextTarEntry ?: break
                        val unsupported = item.isSymbolicLink || item.isLink || !item.isDirectory && !item.isFile
                        if (!tar.canReadEntryData(item)) throw IOException("Unsupported TAR entry: ${item.name}")
                        val entry = entry(item.name, item.isDirectory, item.size)
                        action(entry) { output ->
                            if (unsupported) throw IOException("Archive links and special TAR entries are not supported")
                            tar.copyTo(output)
                        }
                    }
                }
            else -> throw IOException("Unsupported archive: ${archive.name}")
        }
    }

    private fun entry(rawPath: String, directory: Boolean, size: Long): Entry {
        val absolute = rawPath.startsWith('/') || rawPath.length >= 2 && rawPath[1] == ':'
        val parts = rawPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (absolute || parts.isEmpty() || parts.any { it == ".." }) throw IOException("Unsafe archive path: $rawPath")
        return Entry(parts.joinToString("/"), directory, size.coerceAtLeast(0))
    }

    private fun tarInput(archive: File): TarArchiveInputStream {
        val input = BufferedInputStream(FileInputStream(archive))
        val decompressed = when (suffix(archive)) {
            ".tar.gz", ".tgz" -> GzipCompressorInputStream(input, true)
            ".tar.bz2", ".tbz2" -> BZip2CompressorInputStream(input, true)
            ".tar.xz", ".txz" -> XZCompressorInputStream(input, true)
            else -> input
        }
        return TarArchiveInputStream(decompressed)
    }

    private fun reserveNamedDestination(archive: File): File {
        while (true) {
            val destination = destinationFor(archive)
            if (destination.mkdir()) return destination
            if (!destination.exists()) throw IOException("Could not create ${destination.name}")
        }
    }

    private fun safeTarget(destination: File, entryName: String): File {
        val root = destination.canonicalFile
        val target = File(root, entryName).canonicalFile
        if (target == root || !target.path.startsWith(root.path + File.separator)) {
            throw IOException("Unsafe archive path: $entryName")
        }
        return target
    }

    private fun selectionRoots(selected: Set<String>): List<String> = selected
        .map { it.trim('/') }
        .filter(String::isNotEmpty)
        .distinct()
        .sortedBy(String::length)
        .filter { candidate -> selected.none { other -> candidate != other && candidate.startsWith("${other.trimEnd('/')}/") } }

    private fun uniqueChild(parent: File, name: String, directory: Boolean): File {
        var target = File(parent, name)
        var number = 2
        val dot = if (directory) -1 else name.lastIndexOf('.').takeIf { it > 0 } ?: -1
        val stem = if (dot < 0) name else name.substring(0, dot)
        val extension = if (dot < 0) "" else name.substring(dot)
        while (target.exists()) target = File(parent, "$stem (${number++})$extension")
        return target
    }

    private fun suffix(file: File): String? {
        val lower = file.name.lowercase(Locale.US)
        return suffixes.firstOrNull(lower::endsWith)
    }

    private class ExtractionBudget(var bytes: Long = 0)

    private class BudgetOutputStream(
        private val delegate: OutputStream,
        private val budget: ExtractionBudget
    ) : OutputStream() {
        override fun write(value: Int) {
            add(1)
            delegate.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            add(length)
            delegate.write(buffer, offset, length)
        }

        private fun add(bytes: Int) {
            budget.bytes += bytes
            if (budget.bytes > MAX_EXTRACTED_BYTES) throw IOException("Archive expands beyond the 4 GB safety limit")
        }
    }
}
