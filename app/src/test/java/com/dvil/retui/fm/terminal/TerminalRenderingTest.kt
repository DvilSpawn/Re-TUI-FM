package com.dvil.retui.fm.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalRenderingTest {
    @Test fun decBordersUseTerminalLineDrawing() {
        assertEquals("┌──┐", "lqqk".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals("│  │", "x  x".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals("└──┘", "mqqj".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals('q', decLineDrawingGlyph('q', false))
    }

    @Test fun fileNamesCannotInjectTerminalStyles() {
        assertEquals("␛[31mreport.txt", terminalDisplayText("\u001b[31mreport.txt"))
        assertEquals("a b c", terminalDisplayText("a\u000eb\u009bc"))
        assertEquals("Résumé-文件-😀.txt", terminalDisplayText("Résumé-文件-😀.txt"))
    }
}
