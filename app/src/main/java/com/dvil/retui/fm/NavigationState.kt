package com.dvil.retui.fm

import android.content.SharedPreferences
import java.io.File

internal object NavigationStateStore {
    const val MAX_RECENT = 12
    const val RECENT_KEY = "recent_directories"
    const val LAST_DIRECTORY_KEY = "last_directory"

    fun remember(prefs: SharedPreferences, directory: File) {
        val path = runCatching { directory.canonicalPath }.getOrNull() ?: return
        val paths = prefs.getString(RECENT_KEY, "").orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it == path }
            .toMutableList()
        paths.add(0, path)
        prefs.edit()
            .putString(RECENT_KEY, paths.take(MAX_RECENT).joinToString("\n"))
            .putString(LAST_DIRECTORY_KEY, path)
            .apply()
    }

    fun recent(prefs: SharedPreferences): List<File> {
        val paths = validPaths(prefs.getString(RECENT_KEY, "").orEmpty().lineSequence().toList())
        prefs.edit().putString(RECENT_KEY, paths.joinToString("\n")).apply()
        return paths.map(::File)
    }

    fun last(prefs: SharedPreferences): File? {
        val path = validPaths(listOf(prefs.getString(LAST_DIRECTORY_KEY, null).orEmpty())).firstOrNull()
        if (path == null) prefs.edit().remove(LAST_DIRECTORY_KEY).apply()
        return path?.let(::File)
    }

    fun validPaths(
        paths: Iterable<String>,
        isValid: (File) -> Boolean = { it.exists() && it.isDirectory && it.canRead() },
        isTrash: (File) -> Boolean = { file -> file.path.split(File.separatorChar).any { it == ".retui-trash" } }
    ): List<String> = paths.mapNotNull { raw ->
        val file = File(raw.trim())
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return@mapNotNull null
        if (path.isNotEmpty() && isValid(File(path)) && !isTrash(File(path))) path else null
    }.distinct().take(MAX_RECENT)
}
