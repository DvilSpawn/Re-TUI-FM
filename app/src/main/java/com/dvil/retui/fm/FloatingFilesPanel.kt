package com.dvil.retui.fm

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wallpaper-hosted touch panel; content owns clicks even underneath a corner target. */
internal class FloatingFilesPanel(context: Context, private val host: FrameLayout, private val content: View,
    private val dragBars: List<View>, private val readBounds: () -> FloatingFractions?,
    private val saveBounds: (FloatingFractions) -> Unit, private val availableHeight: () -> Int,
    private val fullscreen: () -> Boolean,
    grip: (String, String) -> TextView) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var start = FloatingRect(0, 0, 1, 1)
    private var preferred = start
    private var startPreferred = start
    private var moving = false
    private var menuDown = false
    private var displayed = start
    private val grips = ArrayList<View>()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private val baseHeight get() = availableHeight().coerceAtLeast(host.height)

    init {
        clipChildren = true
        addView(content, LayoutParams(-1, -1).apply { setMargins(dp(24), dp(24), dp(24), dp(24)) })
        listOf("top-left" to "◤", "top-right" to "◥", "bottom-left" to "◣", "bottom-right" to "◢").forEach { (corner, glyph) ->
            val control = object : FrameLayout(context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && contentHit(content, event.rawX.toInt(), event.rawY.toInt())) return false
                    return super.dispatchTouchEvent(event)
                }
            }
            val text = grip(corner, glyph)
            control.contentDescription = "Resize $corner corner"
            control.isFocusable = true
            control.addView(text, LayoutParams(-1, -1))
            control.setOnClickListener { step(corner, 32) }
            control.setOnLongClickListener { step(corner, -32); true }
            var dragged = false
            control.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { begin(event); dragged = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX; val dy = event.rawY - downY
                        if (abs(dx) + abs(dy) > slop) dragged = true
                        if (dragged) show(resizeFloatingRect(start, corner, dx.roundToInt(), dy.roundToInt(), host.width, host.height, dp(320), dp(420)))
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - downX; val dy = event.rawY - downY
                        if (dragged || abs(dx) + abs(dy) > slop) {
                            show(resizeFloatingRect(start, corner, dx.roundToInt(), dy.roundToInt(), host.width, host.height, dp(320), dp(420)))
                            remember()
                        }
                        else if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()) target.performLongClick()
                        else target.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { if (dragged) remember(); true }
                    else -> false
                }
            }
            val gravity = (if (corner.endsWith("left")) Gravity.LEFT else Gravity.RIGHT) or
                (if (corner.startsWith("top")) Gravity.TOP else Gravity.BOTTOM)
            addView(control, LayoutParams(dp(48), dp(48), gravity))
            grips.add(control)
        }
        host.addView(this)
        host.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) fit()
        }
    }

    fun fit() {
        if (host.width <= 0 || host.height <= 0) return
        val base = readBounds()?.takeIf { it.isValid() }?.resolve(host.width, baseHeight, dp(320), dp(420)) ?: run {
            val w = minOf((host.width * .92f).roundToInt(), dp(720))
            val h = minOf((baseHeight * .82f).roundToInt(), dp(760))
            clampFloatingRect(FloatingRect((host.width - w) / 2, (baseHeight - h) / 2, w, h), host.width, baseHeight, dp(320), dp(420))
        }
        preferred = base
        val expanded = fullscreen()
        grips.forEach { it.visibility = if (expanded) View.GONE else View.VISIBLE }
        val margins = if (expanded) intArrayOf(0, dp(14), 0, 0) else IntArray(4) { dp(24) }
        val params = content.layoutParams as LayoutParams
        if (listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin) != margins.toList()) {
            params.setMargins(margins[0], margins[1], margins[2], margins[3])
            content.layoutParams = params
        }
        show(if (expanded) FloatingRect(0, 0, host.width, host.height)
            else clampFloatingRect(base, host.width, host.height, dp(320), dp(420)))
    }

    private fun begin(event: MotionEvent) {
        downX = event.rawX; downY = event.rawY; start = displayed; startPreferred = preferred
    }
    private fun show(rect: FloatingRect) {
        displayed = rect
        val previous = layoutParams as? LayoutParams
        if (previous?.width == rect.width && previous.height == rect.height &&
            previous.leftMargin == rect.left && previous.topMargin == rect.top) return
        layoutParams = LayoutParams(rect.width, rect.height, Gravity.TOP or Gravity.LEFT).apply { leftMargin = rect.left; topMargin = rect.top }
    }
    private fun remember() {
        if (host.width <= 0 || baseHeight <= 0) return
        // A width gesture while IME-clamped must not save the keyboard's temporary height.
        preferred = rememberFloatingGesture(startPreferred, start, displayed, host.width, baseHeight, dp(320), dp(420))
        saveBounds(preferred.fractions(host.width, baseHeight))
    }
    private fun step(corner: String, amount: Int) {
        start = displayed; startPreferred = preferred
        show(resizeFloatingRect(displayed, corner, dp(amount) * if (corner.endsWith("left")) -1 else 1,
            dp(amount) * if (corner.startsWith("top")) -1 else 1, host.width, host.height, dp(320), dp(420)))
        remember()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (fullscreen()) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            begin(event); moving = false
            menuDown = dragBars.any { bar ->
                val bounds = Rect(); bar.getGlobalVisibleRect(bounds)
                bounds.contains(event.rawX.toInt(), event.rawY.toInt())
            }
        }
        if (event.actionMasked in listOf(MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP) && menuDown &&
            abs(event.rawX - downX) + abs(event.rawY - downY) > slop) moving = true
        return moving
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!moving) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> show(start.copy(
                left = clampFloatingWindowPosition(start.left, event.rawX - downX, host.width, start.width),
                top = clampFloatingWindowPosition(start.top, event.rawY - downY, host.height, start.height)))
            MotionEvent.ACTION_UP -> {
                show(start.copy(left = clampFloatingWindowPosition(start.left, event.rawX - downX, host.width, start.width),
                    top = clampFloatingWindowPosition(start.top, event.rawY - downY, host.height, start.height)))
                remember(); moving = false; menuDown = false
            }
            MotionEvent.ACTION_CANCEL -> { remember(); moving = false; menuDown = false }
        }
        return true
    }

    private fun contentHit(view: View, x: Int, y: Int): Boolean {
        if (!view.isShown || !view.isEnabled) return false
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return false
        if (view.isClickable || view.isLongClickable || view is android.widget.EditText) return true
        return view is ViewGroup && (0 until view.childCount).any { contentHit(view.getChildAt(it), x, y) }
    }
}
