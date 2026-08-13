package com.dvil.retui.fm

import kotlin.math.roundToInt

internal fun clampFloatingWindowPosition(start: Int, delta: Float, screen: Int, window: Int): Int =
    (start + delta.roundToInt()).coerceIn(0, (screen - window).coerceAtLeast(0))
