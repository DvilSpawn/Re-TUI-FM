package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeColorOverrideTest {
    @Test fun localOverrideWinsOnlyWhileEnabled() {
        assertEquals(10, resolveThemeColor(10, false, 20))
        assertEquals(20, resolveThemeColor(10, true, 20))
        assertEquals(10, resolveThemeColor(10, true, null))
    }
}
