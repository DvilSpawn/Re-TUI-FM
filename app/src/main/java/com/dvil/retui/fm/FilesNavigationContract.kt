package com.dvil.retui.fm

import java.io.File

internal object FilesNavigationContract {
    data class Entry(val name: String, val path: String, val isDirectory: Boolean)
    data class SearchScope(val roots: List<File>, val skippedRoots: Int)

    fun searchScope(explicitRoot: File?, discoveredRoots: List<File>): SearchScope {
        val roots = LinkedHashMap<String, File>()
        var skipped = 0
        for (candidate in explicitRoot?.let(::listOf) ?: discoveredRoots) {
            val root = runCatching { candidate.canonicalFile }.getOrNull()
            if (root == null || !root.isDirectory || !root.canRead()) {
                skipped++
            } else {
                roots.putIfAbsent(root.path, root)
            }
        }
        return SearchScope(roots.values.toList(), skipped)
    }

    fun isWithin(file: File, roots: List<File>): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return roots.any { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            path == rootPath || path.startsWith(rootPath.trimEnd(File.separatorChar) + File.separator)
        }
    }

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
