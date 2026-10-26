package com.dvil.retui.fm.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max

/** Keeps Android text, touch and accessibility semantics, drawing with Launcher's cell renderer. */
class TerminalLabelView(context: Context) : TextView(context) {
    private val grid = TerminalGridView(context)
    private var lastTypeface: Typeface? = null
    private var lastSize = -1f
    private var lastColor: Int? = null
    private var lastFrame: String? = null
    private var lastColumns = 0
    private var lastRows = 0

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout ?: return
        val availableWidth = width - compoundPaddingLeft - compoundPaddingRight
        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return
        // Long document output keeps Android's full text layout and scrolling.
        if (textLayout.lineCount > 120) { super.onDraw(canvas); return }
        if (lastTypeface != typeface || lastSize != textSize) {
            lastTypeface = typeface
            lastSize = textSize
            grid.setTerminalTypeface(typeface)
            grid.setTerminalTextSizePx(textSize)
        }
        if (lastColor != currentTextColor) {
            lastColor = currentTextColor
            grid.updateThemeColors(currentTextColor, Color.TRANSPARENT, currentTextColor)
        }
        val columns = (availableWidth / grid.characterWidth()).toInt().coerceIn(1, 240)
        val original = text.toString()
        val lines = (0 until textLayout.lineCount).map { line ->
            var value = original.substring(textLayout.getLineStart(line), textLayout.getLineEnd(line)).trimEnd('\n')
            val omitted = textLayout.getEllipsisCount(line)
            if (omitted > 0) {
                val start = textLayout.getEllipsisStart(line)
                value = value.take(start) + "…" + value.drop(start + omitted)
            }
            terminalDisplayText(value)
        }
        val plainFrame = lines.joinToString("\n")
        val frame = if (paintFlags and Paint.UNDERLINE_TEXT_FLAG != 0) "\u001b[4m$plainFrame\u001b[0m" else plainFrame
        if (frame != lastFrame || columns != lastColumns || lines.size != lastRows) {
            lastFrame = frame
            lastColumns = columns
            lastRows = lines.size
            grid.setFrame(frame, columns, lines.size.coerceAtLeast(1), null, null)
        }
        val blockHeight = lines.size * grid.lineHeight()
        val top = compoundPaddingTop + when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.BOTTOM -> max(0, availableHeight - blockHeight)
            Gravity.CENTER_VERTICAL -> max(0, (availableHeight - blockHeight) / 2)
            else -> 0
        }
        grid.layout(0, 0, ceil(columns * grid.characterWidth()).toInt(), blockHeight)
        canvas.save()
        canvas.clipRect(compoundPaddingLeft, compoundPaddingTop, width - compoundPaddingRight, height - compoundPaddingBottom)
        // Native layout provides alignment and wrapping; the renderer provides fixed terminal cells.
        lines.forEachIndexed { index, value ->
            val frameWidth = ceil(value.codePointCount(0, value.length) * grid.characterWidth()).toInt()
            val left = compoundPaddingLeft + when (Gravity.getAbsoluteGravity(gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK) {
                Gravity.RIGHT -> max(0, availableWidth - frameWidth)
                Gravity.CENTER_HORIZONTAL -> max(0, (availableWidth - frameWidth) / 2)
                else -> 0
            }
            canvas.save()
            canvas.translate(left.toFloat(), (top + index * grid.lineHeight()).toFloat())
            // Render the cached frame one line at a time to retain native horizontal alignment.
            canvas.clipRect(0, 0, availableWidth, grid.lineHeight())
            canvas.translate(0f, (-index * grid.lineHeight()).toFloat())
            grid.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }
}

internal fun terminalDisplayText(value: String): String = buildString {
    value.forEach { character ->
        append(when {
            character == '\u001b' -> '␛'
            character < ' ' || character in '\u007f'..'\u009f' -> ' '
            else -> character
        })
    }
}
