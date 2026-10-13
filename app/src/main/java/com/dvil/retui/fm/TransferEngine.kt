package com.dvil.retui.fm

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

internal enum class ConflictChoice { KEEP_BOTH, REPLACE, SKIP }

internal data class TransferProgress(
    val current: String,
    val completed: Int,
    val total: Int,
    val completedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

internal data class TransferReport(
    val completed: Int,
    val skipped: Int,
    val failures: List<String>,
    val cancelled: Boolean,
    val completedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

internal object TransferEngine {
    fun transfer(
        sources: List<File>,
        destination: File,
        move: Boolean,
        conflictChoice: ConflictChoice,
        cancelled: () -> Boolean,
        progress: (TransferProgress) -> Unit
    ): TransferReport {
        var completed = 0
        var skipped = 0
        val failures = ArrayList<String>()
        val totalBytes = sources.sumOf(::sizeOf)
        require(destination.exists() || destination.mkdirs()) { "Could not create destination ${destination.absolutePath}" }
        require(destination.isDirectory && destination.canWrite()) { "Destination is not writable: ${destination.absolutePath}" }
        if (totalBytes > 0L && destination.freeSpace in 1 until totalBytes) {
            throw IllegalStateException("Not enough free space for the transfer")
        }
        var completedBytes = 0L
        for ((index, source) in sources.withIndex()) {
            if (cancelled()) break
            progress(TransferProgress(source.name, completed, sources.size, completedBytes, totalBytes))
            try {
                require(source.exists()) { "${source.name}: no longer exists" }
                val sourcePath = source.canonicalPath
                val destinationPath = destination.canonicalPath
                require(!move || source.parentFile?.canonicalPath != destinationPath) { "${source.name}: already in this folder" }
                require(!source.isDirectory || !destinationPath.startsWith("$sourcePath${File.separator}")) { "${source.name}: cannot place inside itself" }
                var target = File(destination, source.name)
                var replacementBackup: File? = null
                if (target.exists()) {
                    when (conflictChoice) {
                        ConflictChoice.KEEP_BOTH -> target = uniqueFile(target)
                        ConflictChoice.REPLACE -> {
                            replacementBackup = uniqueFile(File(destination, ".${target.name}.retui-replace"))
                            require(target.renameTo(replacementBackup)) { "${source.name}: could not prepare replacement" }
                        }
                        ConflictChoice.SKIP -> {
                            skipped++
                            continue
                        }
                    }
                }
                val temporary = temporarySibling(target)
                try {
                    copyRecursively(source, temporary, cancelled)
                    if (!temporary.renameTo(target)) {
                        throw IllegalStateException("${source.name}: could not publish destination")
                    }
                    if (move && !(if (source.isDirectory) source.deleteRecursively() else source.delete())) {
                        throw IllegalStateException("${source.name}: copied, but the source could not be removed")
                    }
                    if (replacementBackup != null && !replacementBackup.deleteRecursively()) {
                        throw IllegalStateException("${source.name}: could not finalize replacement")
                    }
                } catch (failure: Exception) {
                    temporary.deleteRecursively()
                    target.deleteRecursively()
                    replacementBackup?.renameTo(File(destination, source.name))
                    throw failure
                }
                completedBytes += sizeOf(source)
                completed++
                progress(TransferProgress(source.name, completed, sources.size, completedBytes, totalBytes))
            } catch (failure: TransferCancelled) {
                break
            } catch (failure: Exception) {
                failures += (failure.message ?: source.name)
            }
        }
        return TransferReport(completed, skipped, failures, cancelled(), completedBytes, totalBytes)
    }

    private fun copyRecursively(source: File, target: File, cancelled: () -> Boolean) {
        if (cancelled()) throw TransferCancelled()
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IllegalStateException("Could not create ${target.absolutePath}")
            source.listFiles()?.forEach { copyRecursively(it, File(target, it.name), cancelled) }
            return
        }
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (cancelled()) throw TransferCancelled()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun uniqueFile(base: File): File {
        var target = base
        var suffix = 1
        while (target.exists()) target = File(base.parentFile, "${base.name}.$suffix").also { suffix++ }
        return target
    }

    private fun temporarySibling(target: File): File {
        return File(target.parentFile, ".${target.name}.retui-tmp-${UUID.randomUUID()}")
    }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length().coerceAtLeast(0L)
        return file.listFiles().orEmpty().sumOf(::sizeOf)
    }

    private class TransferCancelled : Exception()
}
