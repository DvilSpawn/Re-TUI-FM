package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadabilityTest {
    private val launcherText = 0xffe7e8e9.toInt()
    private val launcherOutput = 0xff2b2929.toInt()
    private val launcherButton = 0xff302d2d.toInt()
    private val launcherSelection = 0xff564434.toInt()

    @Test fun contrastMatchesWcagExtremes() {
        assertEquals(21.0, Readability.contrast(0xffffffff.toInt(), 0xff000000.toInt()), 0.01)
        assertEquals(1.0, Readability.contrast(launcherOutput, launcherOutput), 0.0001)
    }

    @Test fun readableColoursAreLeftAlone() {
        assertEquals(launcherText, Readability.ensureContrast(launcherText, launcherOutput, Readability.BODY_TEXT))
    }

    @Test fun lowContrastTextIsRaisedToTheMinimum() {
        val dimText = 0xff5a5555.toInt()
        val fixed = Readability.ensureContrast(dimText, launcherOutput, Readability.TEXT)
        assertTrue(Readability.contrast(fixed, launcherOutput) >= Readability.TEXT)
    }

    @Test fun darkTextOnLightBackgroundGetsDarker() {
        val paper = 0xfff2f2f2.toInt()
        val fixed = Readability.ensureContrast(0xffbbbbbb.toInt(), paper, Readability.TEXT)
        assertTrue(Readability.luminance(fixed) < Readability.luminance(0xffbbbbbb.toInt()))
        assertTrue(Readability.contrast(fixed, paper) >= Readability.TEXT)
    }

    @Test fun nearIdenticalSurfacesAreSeparated() {
        val button = Readability.separate(launcherButton, launcherOutput, 1.35)
        assertTrue(Readability.contrast(button, launcherOutput) >= 1.35)
        assertTrue(Readability.luminance(button) > Readability.luminance(launcherButton))
    }

    @Test fun alphaIsPreserved() {
        val translucent = 0x80303030.toInt()
        val fixed = Readability.ensureContrast(translucent, launcherOutput, Readability.TEXT)
        assertEquals(0x80, fixed ushr 24)
    }

    @Test fun selectionStaysDistinctAndItsTextReadable() {
        val selection = Readability.separate(launcherSelection, launcherOutput, 1.6)
        val text = Readability.ensureContrast(launcherText, selection, Readability.TEXT)
        assertTrue(Readability.contrast(selection, launcherOutput) >= 1.6)
        assertTrue(Readability.contrast(text, selection) >= Readability.TEXT)
    }
}
