package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherFrameTest {
    private val valid = LauncherFrameSpec(
        assetId = "opaque/id",
        sliceLeftPx = 8,
        sliceTopPx = 9,
        sliceRightPx = 10,
        sliceBottomPx = 11,
        borderLeftDp = 8f,
        borderTopDp = 9f,
        borderRightDp = 10f,
        borderBottomDp = 11f,
        modeTop = "stretch",
        modeRight = "tile",
        modeBottom = "stretch",
        modeLeft = "tile",
        modeCenter = "none",
        filtering = "nearest"
    )

    @Test
    fun payloadPolicyPreservesOrClearsExactlyAsRequested() {
        assertEquals(FramePayloadAction.IGNORE, framePayloadAction(false, true))
        assertEquals(FramePayloadAction.IGNORE, framePayloadAction(false, false))
        assertEquals(FramePayloadAction.PRESERVE, framePayloadAction(true, null))
        assertEquals(FramePayloadAction.CLEAR, framePayloadAction(true, false))
        assertEquals(FramePayloadAction.IMPORT, framePayloadAction(true, true))
    }

    @Test
    fun validMetadataAcceptsSupportedModesAndFiltering() {
        assertNull(valid.validationError(64, 64))
        assertNull(valid.copy(modeCenter = "stretch", filtering = "linear").validationError(64, 64))
        assertNull(valid.copy(modeCenter = "tile").validationError(64, 64))
    }

    @Test
    fun invalidMetadataIsRejected() {
        assertTrue(valid.copy(sliceLeftPx = 0).validationError(64, 64)!!.contains("slices"))
        assertTrue(valid.copy(sliceRightPx = 60).validationError(64, 64)!!.contains("overlap"))
        assertTrue(valid.copy(borderTopDp = Float.NaN).validationError(64, 64)!!.contains("borders"))
        assertTrue(valid.copy(borderBottomDp = 257f).validationError(64, 64)!!.contains("borders"))
        assertTrue(valid.copy(modeLeft = "repeat").validationError(64, 64)!!.contains("edge mode"))
        assertTrue(valid.copy(modeCenter = "fill").validationError(64, 64)!!.contains("center mode"))
        assertTrue(valid.copy(filtering = "smooth").validationError(64, 64)!!.contains("filtering"))
        assertTrue(valid.validationError(2049, 64)!!.contains("dimensions"))
    }

    @Test
    fun opposingBordersMeetWithoutOverlap() {
        assertEquals(1f, LauncherFrameMath.fitScale(100f, 80f, 30f, 20f, 40f, 20f), 0f)
        assertEquals(0.375f, LauncherFrameMath.fitScale(60f, 100f, 80f, 10f, 80f, 10f), 0f)
        assertTrue(LauncherFrameMath.boundaries(0, 60, 30f, 30f).contentEquals(floatArrayOf(0f, 30f, 30f, 60f)))
        assertTrue(LauncherFrameMath.boundaries(0, 5, 2f, 3f).contentEquals(floatArrayOf(0f, 2f, 2f, 5f)))
    }

    @Test
    fun frameTextUsesReadableBlackOrWhiteContrast() {
        assertEquals(0xff000000.toInt(), contrastTextColor(224, 214, 144))
        assertEquals(0xffffffff.toInt(), contrastTextColor(20, 24, 40))
    }
}
