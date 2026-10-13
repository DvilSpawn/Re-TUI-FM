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
import java.util.UUID
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

    fun extractNamed(
        archive: File,
        selected: Set<String> = emptySet(),
        isCancelled: () -> Boolean = { false },
        progress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): File {
        val destination = destinationFor(archive)
        val staging = temporarySibling(destination)
        if (!staging.mkdir()) throw IOException("Could not create temporary extraction folder")
        val total = runCatching { entries(archive).size }.getOrElse { staging.deleteRecursively(); throw it }
        var completed = 0
        try {
            extractInto(archive, staging, selected, preserveArchivePaths = true, isCancelled = isCancelled) { current, _, _ ->
                completed++
                progress(current, completed, total)
            }
            check(!isCancelled()) { "Operation cancelled" }
            if (!staging.renameTo(destination)) throw IOException("Could not publish ${destination.name}")
            return destination
        } catch (failure: Exception) {
            staging.deleteRecursively()
            throw failure
        }
    }

    fun extractTo(
        archive: File,
        destination: File,
        selected: Set<String> = emptySet(),
        isCancelled: () -> Boolean = { false },
        progress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ): List<File> {
        require(destination.isDirectory) { "Not a folder: ${destination.absolutePath}" }
        val staging = File(destination, ".retui-extract-${UUID.randomUUID()}")
        if (!staging.mkdir()) throw IOException("Could not create temporary extraction folder")
        val staged = ArrayList<File>()
        val published = ArrayList<File>()
        val total = runCatching { entries(archive).size }.getOrElse { staging.deleteRecursively(); throw it }
        var completed = 0
        try {
            extractInto(archive, staging, selected, preserveArchivePaths = false, createdRoots = staged, isCancelled = isCancelled) { current, _, _ ->
                completed++
                progress(current, completed, total)
            }
            for (root in staged) {
                check(!isCancelled()) { "Operation cancelled" }
                val target = uniqueChild(destination, root.name, root.isDirectory)
                if (!root.renameTo(target)) throw IOException("Could not publish ${root.name}")
                published += target
            }
            staging.deleteRecursively()
            return published
        } catch (failure: Exception) {
            staging.deleteRecursively()
            published.forEach { it.deleteRecursively() }
            throw failure
        }
    }

    private fun extractInto(
        archive: File,
        destination: File,
        selected: Set<String>,
        preserveArchivePaths: Boolean,
        createdRoots: MutableList<File> = ArrayList(),
        isCancelled: () -> Boolean = { false },
        progress: (String, Int, Int) -> Unit = { _, _, _ -> }
    ) {
        val roots = selectionRoots(selected)
        val mappedRoots = HashMap<String, File>()
        val budget = ExtractionBudget()
        var extracted = 0
        scan(archive) { entry, copy ->
            check(!isCancelled()) { "Operation cancelled" }
            val selectedRoot = roots.firstOrNull { entry.path == it || entry.path.startsWith("$it/") }
            if (roots.isNotEmpty() && selectedRoot == null) return@scan
            if (++extracted > MAX_ENTRIES) throw IOException("Archive contains more than $MAX_ENTRIES entries")
            progress(entry.path, extracted, 0)

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
                    copy(BudgetOutputStream(output, budget, isCancelled))
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

    private fun temporarySibling(target: File): File =
        File(target.parentFile, ".${target.name}.retui-extract-${UUID.randomUUID()}")

    private class ExtractionBudget(var bytes: Long = 0)

    private class BudgetOutputStream(
        private val delegate: OutputStream,
        private val budget: ExtractionBudget,
        private val isCancelled: () -> Boolean
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
            if (isCancelled()) throw IOException("Operation cancelled")
            budget.bytes += bytes
            if (budget.bytes > MAX_EXTRACTED_BYTES) throw IOException("Archive expands beyond the 4 GB safety limit")
        }
    }
}
