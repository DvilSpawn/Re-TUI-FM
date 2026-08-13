package com.dvil.retui.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiVisualContractTest {
    @Test
    fun keysAreUniqueAndCoverCoreSurfaces() {
        val keys = RetuiVisualContract.KEYS
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(keys.contains(RetuiVisualContract.BG))
        assertTrue(keys.contains(RetuiVisualContract.TEXT))
        assertTrue(keys.contains(RetuiVisualContract.BORDER))
        assertTrue(keys.contains(RetuiVisualContract.OUTPUT_BG))
        assertTrue(keys.contains(RetuiVisualContract.SELECTION_BG))
        assertTrue(keys.contains(RetuiVisualContract.FONT_PATH))
        assertTrue(
            keys.toSet().containsAll(
                setOf(
                    RetuiVisualContract.FRAME_AVAILABLE,
                    RetuiVisualContract.FRAME_ASSET_ID,
                    RetuiVisualContract.FRAME_IMAGE_URI,
                    RetuiVisualContract.FRAME_SLICE_LEFT_PX,
                    RetuiVisualContract.FRAME_SLICE_TOP_PX,
                    RetuiVisualContract.FRAME_SLICE_RIGHT_PX,
                    RetuiVisualContract.FRAME_SLICE_BOTTOM_PX,
                    RetuiVisualContract.FRAME_BORDER_LEFT_DP,
                    RetuiVisualContract.FRAME_BORDER_TOP_DP,
                    RetuiVisualContract.FRAME_BORDER_RIGHT_DP,
                    RetuiVisualContract.FRAME_BORDER_BOTTOM_DP,
                    RetuiVisualContract.FRAME_MODE_TOP,
                    RetuiVisualContract.FRAME_MODE_RIGHT,
                    RetuiVisualContract.FRAME_MODE_BOTTOM,
                    RetuiVisualContract.FRAME_MODE_LEFT,
                    RetuiVisualContract.FRAME_MODE_CENTER,
                    RetuiVisualContract.FRAME_FILTERING
                )
            )
        )
    }
}
