package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Test

class FmVisualInteropTest {
    @Test
    fun surfaceScalingPreservesSourceTransparency() {
        assertEquals(0x002e251a, FmVisualInterop.scaleColorAlpha(0x002e251a, 210))
        assertEquals(0x14292117, FmVisualInterop.scaleColorAlpha(0x19292117, 210))
        assertEquals(0xd2ffffff.toInt(), FmVisualInterop.scaleColorAlpha(0xffffffff.toInt(), 210))
    }
}
