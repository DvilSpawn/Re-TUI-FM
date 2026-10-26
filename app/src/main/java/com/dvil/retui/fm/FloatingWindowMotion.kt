package com.dvil.retui.fm

import kotlin.math.roundToInt

internal fun clampFloatingWindowPosition(start: Int, delta: Float, screen: Int, window: Int): Int =
    (start + delta.roundToInt()).coerceIn(0, (screen - window).coerceAtLeast(0))

internal data class FloatingRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right get() = left + width
    val bottom get() = top + height
}

internal data class FloatingFractions(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun isValid() = listOf(left, top, width, height).all { it.isFinite() } &&
        left >= 0 && top >= 0 && width > 0 && height > 0 && width <= 1 && height <= 1 &&
        left + width <= 1.001f && top + height <= 1.001f
    fun resolve(screenWidth: Int, screenHeight: Int, minWidth: Int, minHeight: Int): FloatingRect =
        clampFloatingRect(FloatingRect((left * screenWidth).roundToInt(), (top * screenHeight).roundToInt(),
            (width * screenWidth).roundToInt(), (height * screenHeight).roundToInt()), screenWidth, screenHeight, minWidth, minHeight)
}

internal fun FloatingRect.fractions(screenWidth: Int, screenHeight: Int) = FloatingFractions(
    left.toFloat() / screenWidth, top.toFloat() / screenHeight, width.toFloat() / screenWidth, height.toFloat() / screenHeight)

internal fun clampFloatingRect(rect: FloatingRect, screenWidth: Int, screenHeight: Int, minWidth: Int, minHeight: Int): FloatingRect {
    val w = rect.width.coerceIn(minWidth.coerceAtMost(screenWidth), screenWidth)
    val h = rect.height.coerceIn(minHeight.coerceAtMost(screenHeight), screenHeight)
    return FloatingRect(rect.left.coerceIn(0, screenWidth - w), rect.top.coerceIn(0, screenHeight - h), w, h)
}

internal fun resizeFloatingRect(rect: FloatingRect, corner: String, dx: Int, dy: Int,
    screenWidth: Int, screenHeight: Int, minWidth: Int, minHeight: Int): FloatingRect {
    val w = minWidth.coerceAtMost(screenWidth)
    val h = minHeight.coerceAtMost(screenHeight)
    val left = if (corner.endsWith("left")) (rect.left + dx).coerceIn(0, rect.right - w) else rect.left
    val right = if (corner.endsWith("right")) (rect.right + dx).coerceIn(rect.left + w, screenWidth) else rect.right
    val top = if (corner.startsWith("top")) (rect.top + dy).coerceIn(0, rect.bottom - h) else rect.top
    val bottom = if (corner.startsWith("bottom")) (rect.bottom + dy).coerceIn(rect.top + h, screenHeight) else rect.bottom
    return FloatingRect(left, top, right - left, bottom - top)
}

internal fun rememberFloatingGesture(preferred: FloatingRect, start: FloatingRect, displayed: FloatingRect,
    screenWidth: Int, screenHeight: Int, minWidth: Int, minHeight: Int): FloatingRect =
    clampFloatingRect(displayed.copy(top = preferred.top + displayed.top - start.top,
        height = preferred.height + displayed.height - start.height), screenWidth, screenHeight, minWidth, minHeight)
