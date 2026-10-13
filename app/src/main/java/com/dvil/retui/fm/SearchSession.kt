package com.dvil.retui.fm

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal data class SearchProgress(
    val scanned: Int,
    val matched: Int
)

internal data class SearchResult(
    val files: List<File>,
    val scanned: Int,
    val truncated: Boolean,
    val inaccessibleRoots: Int,
    val cancelled: Boolean
)

internal class SearchSession(
    private val roots: List<File>,
    private val maxResults: Int,
    private val showHidden: Boolean,
    private val matches: (File) -> Boolean,
    val cancellation: AtomicBoolean = AtomicBoolean(false)
) {
    fun cancel() {
        cancellation.set(true)
    }

    fun execute(onProgress: (SearchProgress) -> Unit = {}): SearchResult {
        val output = ArrayList<File>()
        val seen = HashSet<String>()
        var scanned = 0
        var inaccessible = 0
        var truncated = false
        search@ for (root in roots) {
            for (file in root.walkTopDown()
                .onEnter { showHidden || it == root || !it.name.startsWith(".") }
                .onFail { _, _ -> inaccessible++ }) {
                if (cancellation.get()) break@search
                scanned++
                if (!showHidden && file.name.startsWith(".")) continue
                if (output.size >= maxResults) {
                    truncated = true
                    break@search
                }
                val path = runCatching { file.canonicalPath }.getOrNull() ?: file.absolutePath
                if (seen.add(path) && matches(file)) output += file
                if (scanned % 100 == 0) onProgress(SearchProgress(scanned, output.size))
            }
        }
        return SearchResult(output, scanned, truncated, inaccessible, cancellation.get())
    }
}
