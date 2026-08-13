package com.dvil.retui.fm

import java.io.File

internal object FilesNavigationContract {
    data class Entry(val name: String, val path: String, val isDirectory: Boolean)

    fun resolve(basePath: String, target: String): File {
        val requested = File(target)
        val resolved = if (requested.isAbsolute) requested else File(basePath, target)
        return runCatching { resolved.canonicalFile }.getOrElse { resolved.absoluteFile.normalize() }
    }

    fun resolveDirectory(basePath: String, target: String): Entry? {
        val resolved = resolve(basePath, target)
        return resolved.takeIf { it.isDirectory }?.let { Entry(it.name.ifEmpty { it.path }, it.path, true) }
    }

    fun launchTarget(path: String?, target: String?): File? {
        if (path.isNullOrBlank()) return null
        return if (target.isNullOrBlank()) resolve("/", path) else resolve(path, target)
    }

    fun startDirectory(path: String?, fallback: File): File {
        val requested = launchTarget(path, null) ?: return fallback
        return when {
            requested.isDirectory -> requested
            requested.isFile -> requested.parentFile ?: fallback
            else -> fallback
        }
    }

    fun entries(basePath: String, directoriesOnly: Boolean, prefix: String): List<Entry> {
        val base = runCatching { File(basePath).canonicalFile }.getOrNull()?.takeIf { it.isDirectory } ?: return emptyList()
        val matches = ArrayList<Entry>()
        if (directoriesOnly && "../".startsWith(prefix, ignoreCase = true)) {
            base.parentFile?.let { matches += Entry("../", it.canonicalPath, true) }
        }
        val children = runCatching { base.listFiles().orEmpty() }.getOrDefault(emptyArray())
        children.asSequence()
            .filter { if (directoriesOnly) it.isDirectory else it.isFile }
            .filter { it.name.startsWith(prefix, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .mapTo(matches) { Entry(it.name, resolve(base.path, it.name).path, it.isDirectory) }
        return matches
    }
}
