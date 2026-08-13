package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FmFontScaleTest {
    @Test fun categoriesScaleEveryBaseSizeConsistently() {
        assertEquals("Standard", fontScaleLabel(0))
        assertTrue(scaledFontSp(13, -3) < scaledFontSp(13, 0))
        assertTrue(scaledFontSp(13, 4) > scaledFontSp(13, 0))
        assertEquals(8f, scaledFontSp(9, -3))
        assertTrue(isSupportedFontName("RETUI.OTF"))
    }
}
