package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWindowMotionTest {
    @Test fun movementStaysOnScreen() {
        assertEquals(0, clampFloatingWindowPosition(20, -100f, 1000, 600))
        assertEquals(400, clampFloatingWindowPosition(20, 900f, 1000, 600))
        assertEquals(120, clampFloatingWindowPosition(20, 100f, 1000, 600))
    }
}
