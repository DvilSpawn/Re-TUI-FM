package com.dvil.retui.fm

import android.os.Build
import android.view.WindowInsets
import kotlin.math.max

object FmVisualInterop {
    @JvmStatic
    fun scaleColorAlpha(color: Int, maximumAlpha: Int): Int {
        val scaledAlpha = (color ushr 24) * maximumAlpha.coerceIn(0, 255) / 255
        return (color and 0x00ffffff) or (scaledAlpha shl 24)
    }

    @JvmStatic
    fun safeInsets(insets: WindowInsets?): IntArray {
        if (insets == null) return intArrayOf(0, 0, 0, 0)
        @Suppress("DEPRECATION")
        var left = max(0, insets.systemWindowInsetLeft)
        @Suppress("DEPRECATION")
        var top = max(0, insets.systemWindowInsetTop)
        @Suppress("DEPRECATION")
        var right = max(0, insets.systemWindowInsetRight)
        @Suppress("DEPRECATION")
        var bottom = max(0, insets.systemWindowInsetBottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets.displayCutout?.let { cutout ->
                left = max(left, cutout.safeInsetLeft)
                top = max(top, cutout.safeInsetTop)
                right = max(right, cutout.safeInsetRight)
                bottom = max(bottom, cutout.safeInsetBottom)
            }
        }
        return intArrayOf(left, top, right, bottom)
    }
}
