package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FloatingWindowMotionTest {
    @Test fun movementStaysOnScreen() {
        assertEquals(0, clampFloatingWindowPosition(20, -100f, 1000, 600))
        assertEquals(400, clampFloatingWindowPosition(20, 900f, 1000, 600))
        assertEquals(120, clampFloatingWindowPosition(20, 100f, 1000, 600))
    }

    @Test fun allCornersKeepOppositeCornerFixed() {
        val start = FloatingRect(100, 100, 400, 500)
        val cases = mapOf("top-left" to FloatingRect(120, 130, 380, 470),
            "top-right" to FloatingRect(100, 130, 420, 470),
            "bottom-left" to FloatingRect(120, 100, 380, 530),
            "bottom-right" to FloatingRect(100, 100, 420, 530))
        cases.forEach { (corner, expected) -> assertEquals(expected, resizeFloatingRect(start, corner, 20, 30, 1000, 1000, 320, 420)) }
    }

    @Test fun minimumAndScreenEdgesClampEveryCorner() {
        val start = FloatingRect(100, 100, 400, 500)
        assertEquals(FloatingRect(180, 180, 320, 420), resizeFloatingRect(start, "top-left", 900, 900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(100, 100, 320, 420), resizeFloatingRect(start, "bottom-right", -900, -900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(100, 180, 320, 420), resizeFloatingRect(start, "top-right", -900, 900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(180, 100, 320, 420), resizeFloatingRect(start, "bottom-left", 900, -900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(0, 0, 500, 600), resizeFloatingRect(start, "top-left", -900, -900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(100, 100, 900, 900), resizeFloatingRect(start, "bottom-right", 900, 900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(100, 0, 900, 600), resizeFloatingRect(start, "top-right", 900, -900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(0, 100, 500, 900), resizeFloatingRect(start, "bottom-left", -900, 900, 1000, 1000, 320, 420))
        assertEquals(FloatingRect(0, 0, 200, 300), clampFloatingRect(start, 200, 300, 320, 420))
    }

    @Test fun normalizedBoundsRestoreAcrossRotationAndKeyboardDoesNotChangePreference() {
        val saved = FloatingRect(100, 200, 400, 600).fractions(1000, 1200)
        assertEquals(FloatingRect(120, 167, 480, 500), saved.resolve(1200, 1000, 320, 420))
        val original = saved.resolve(1000, 1200, 320, 420)
        assertEquals(FloatingRect(100, 0, 400, 450), clampFloatingRect(original, 1000, 450, 320, 420))
        assertEquals(FloatingRect(100, 200, 400, 600), saved.resolve(1000, 1200, 320, 420))
    }

    @Test fun corruptPreferencesAreRejected() {
        listOf(FloatingFractions(Float.NaN, 0f, .9f, .8f), FloatingFractions(0f, Float.POSITIVE_INFINITY, .9f, .8f),
            FloatingFractions(-1f, 0f, .9f, .8f), FloatingFractions(0f, 0f, 0f, .8f),
            FloatingFractions(.5f, 0f, .9f, .8f)).forEach { assertFalse(it.isValid()) }
    }

    @Test fun widthGestureDuringKeyboardClampKeepsPreferredHeight() {
        val preferred = FloatingRect(100, 200, 400, 600)
        val keyboard = FloatingRect(100, 0, 400, 450)
        assertEquals(FloatingRect(100, 200, 380, 600), rememberFloatingGesture(preferred, keyboard,
            keyboard.copy(width = 380), 1000, 1200, 320, 420))
        assertEquals(FloatingRect(100, 220, 400, 580), rememberFloatingGesture(preferred, keyboard,
            keyboard.copy(top = 20, height = 430), 1000, 1200, 320, 420))
    }
}
