package com.dvil.retui.fm

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG contrast helpers for colours handed over by Launcher. Pure ARGB math so it runs in JVM tests.
 * Text is nudged toward white or black only as far as needed, so the Launcher hue survives.
 */
internal object Readability {
    const val BODY_TEXT = 7.0
    const val TEXT = 4.5
    const val NON_TEXT = 3.0

    private const val WHITE = 0xffffffff.toInt()
    private const val BLACK = 0xff000000.toInt()

    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = (color shr shift and 0xff) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun contrast(a: Int, b: Int): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    /** Returns [foreground] moved toward white or black until it reaches [minimum] against [background]. */
    fun ensureContrast(foreground: Int, background: Int, minimum: Double): Int {
        if (contrast(foreground, background) >= minimum) return foreground
        val target = if (contrast(WHITE, background) >= contrast(BLACK, background)) WHITE else BLACK
        return blendUntil(foreground, target) { contrast(it, background) >= minimum }
    }

    /** Returns [surface] pushed away from [base] until the two read as separate surfaces. */
    fun separate(surface: Int, base: Int, minimum: Double): Int {
        if (contrast(surface, base) >= minimum) return surface
        val target = if (luminance(base) < 0.18) WHITE else BLACK
        return blendUntil(surface, target) { contrast(it, base) >= minimum }
    }

    private fun blendUntil(color: Int, target: Int, accept: (Int) -> Boolean): Int {
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val mid = (low + high) / 2
            if (accept(blend(color, target, mid))) high = mid else low = mid
        }
        return blend(color, target, high)
    }

    private fun blend(color: Int, target: Int, amount: Double): Int {
        fun mix(shift: Int): Int {
            val from = color shr shift and 0xff
            val to = target shr shift and 0xff
            return (from + (to - from) * amount).toInt().coerceIn(0, 255) shl shift
        }
        // Keep the original alpha; only the visible colour moves.
        return (color and 0xff000000.toInt()) or mix(16) or mix(8) or mix(0)
    }
}
