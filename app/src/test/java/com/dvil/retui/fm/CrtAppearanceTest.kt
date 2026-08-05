package com.dvil.retui.fm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrtAppearanceTest {
    @Test fun canonicalFalseDisablesVignette() {
        assertFalse(CrtAppearance.resolveVignette(false, null, true))
    }

    @Test fun canonicalTakesPrecedenceOverAlias() {
        assertFalse(CrtAppearance.resolveVignette(false, true, true))
    }

    @Test fun aliasWorksWithoutCanonicalExtra() {
        assertFalse(CrtAppearance.resolveVignette(null, false, true))
    }

    @Test fun missingExtrasUseLocalSetting() {
        assertFalse(CrtAppearance.resolveVignette(null, null, false))
    }

    @Test fun missingLocalConfigurationDefaultsEnabled() {
        assertTrue(CrtAppearance.resolveVignette(null, null, null))
    }

    @Test fun disablingVignettePreservesOtherCrtLayers() {
        val layers = CrtAppearance.layers(crtEnabled = true, vignetteEnabled = false)
        assertTrue(layers.tint)
        assertTrue(layers.scanlines)
        assertTrue(layers.beams)
        assertTrue(layers.maskLines)
        assertFalse(layers.vignette)
    }
}
