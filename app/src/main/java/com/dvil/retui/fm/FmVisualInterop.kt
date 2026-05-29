package com.dvil.retui.fm

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.WindowInsets
import kotlin.math.max

object FmVisualInterop {
    @JvmStatic
    fun readColorExtra(intent: Intent?, fallback: Int, vararg keys: String?): Int {
        val extras = intent?.extras ?: return fallback
        for (key in keys) {
            if (key.isNullOrEmpty() || !extras.containsKey(key)) continue
            @Suppress("DEPRECATION")
            val value = extras.get(key)
            parseColorValue(value)?.let { return it }
        }
        return fallback
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

    private fun parseColorValue(value: Any?): Int? {
        if (value == null) return null
        if (value is Number) return value.toInt()
        val raw = value.toString().trim()
        if (raw.isEmpty()) return null
        return try {
            when {
                raw.startsWith("#") -> Color.parseColor(raw)
                raw.startsWith("0x", ignoreCase = true) -> {
                    var parsed = raw.substring(2).toLong(16)
                    if (raw.length <= 8) parsed = parsed or 0xff000000L
                    parsed.toInt()
                }
                else -> raw.toInt()
            }
        } catch (_: Exception) {
            null
        }
    }
}
