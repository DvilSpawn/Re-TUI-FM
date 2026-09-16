package com.dvil.retui.fm

internal data class SearchFilters(
    val terms: List<String>,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null
)

internal fun parseSearchFilters(parts: List<String>, now: Long): SearchFilters {
    var minBytes: Long? = null
    var maxBytes: Long? = null
    var modifiedAfter: Long? = null
    var modifiedBefore: Long? = null
    val terms = parts.filter { part ->
        when {
            part.startsWith("size>", true) -> parseSize(part.drop(5))?.let { minBytes = if (it == Long.MAX_VALUE) it else it + 1; true } ?: false
            part.startsWith("size<", true) -> parseSize(part.drop(5))?.let { maxBytes = it - 1; true } ?: false
            part.startsWith("newer>", true) -> parseAge(part.drop(6))?.let { modifiedAfter = now - it; true } ?: false
            part.startsWith("older>", true) -> parseAge(part.drop(6))?.let { modifiedBefore = now - it; true } ?: false
            else -> false
        }.not()
    }
    return SearchFilters(terms, minBytes, maxBytes, modifiedAfter, modifiedBefore)
}

private fun parseSize(value: String): Long? {
    val match = Regex("^(\\d+(?:\\.\\d+)?)([KMG]?)B?$", RegexOption.IGNORE_CASE).matchEntire(value) ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) { "K" -> 1024L; "M" -> 1024L * 1024L; "G" -> 1024L * 1024L * 1024L; else -> 1L }
    return (match.groupValues[1].toDouble() * multiplier).toLong()
}

private fun parseAge(value: String): Long? {
    val match = Regex("^(\\d+)([DH])$", RegexOption.IGNORE_CASE).matchEntire(value) ?: return null
    val multiplier = if (match.groupValues[2].equals("d", true)) 24L * 60L * 60L * 1000L else 60L * 60L * 1000L
    return try {
        Math.multiplyExact(match.groupValues[1].toLong(), multiplier)
    } catch (_: ArithmeticException) {
        null
    }
}
