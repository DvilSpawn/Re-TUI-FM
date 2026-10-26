package com.dvil.retui.fm.terminal

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Native touch container with the skin's light/heavy rectangular frame treatment. */
class CommanderFrameDrawable(fill: Int, border: Int, private val width: Float, private val double: Boolean) : Drawable() {
    private val fillPaint = Paint().apply { color = fill }
    private val strokePaint = Paint().apply { color = border; style = Paint.Style.STROKE; strokeWidth = width }
    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, fillPaint)
        val rect = RectF(bounds)
        rect.inset(width / 2, width / 2)
        canvas.drawRect(rect, strokePaint)
        if (double) {
            rect.inset(width * 2, width * 2)
            canvas.drawRect(rect, strokePaint)
        }
    }
    override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha; strokePaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { fillPaint.colorFilter = filter; strokePaint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
