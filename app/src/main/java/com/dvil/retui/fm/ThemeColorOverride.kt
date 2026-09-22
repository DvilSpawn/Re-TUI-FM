package com.dvil.retui.fm

internal fun resolveThemeColor(base: Int, overridesEnabled: Boolean, storedOverride: Int?): Int =
    if (overridesEnabled) storedOverride ?: base else base
