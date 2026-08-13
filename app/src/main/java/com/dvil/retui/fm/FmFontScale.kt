package com.dvil.retui.fm

internal val FONT_SCALE_CATEGORIES = listOf(
    -3 to "Extra small",
    -1 to "Small",
    0 to "Standard",
    2 to "Large",
    4 to "Extra large"
)

internal fun scaledFontSp(baseSp: Int, offsetSp: Int): Float =
    (baseSp + offsetSp.coerceIn(-3, 4)).coerceAtLeast(8).toFloat()

internal fun fontScaleLabel(offsetSp: Int): String =
    FONT_SCALE_CATEGORIES.firstOrNull { it.first == offsetSp }?.second ?: "Standard"

internal fun isSupportedFontName(name: String): Boolean =
    name.endsWith(".ttf", true) || name.endsWith(".otf", true)
