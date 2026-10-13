package com.dvil.retui.fm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    @Test fun parsesQuotedFindAndTransferCommands() {
        assertEquals(FmCommand.Find("quarterly report pdf"), CommandParser.parse("find \"quarterly report pdf\"").getOrThrow())
        assertEquals(
            FmCommand.Transfer(false, listOf("a.txt", "b.txt"), "Archive"),
            CommandParser.parse("copy a.txt b.txt Archive").getOrThrow()
        )
    }

    @Test fun rejectsShellSyntaxAndMalformedCommands() {
        assertTrue(CommandParser.parse("trash a; rm -rf /").isFailure)
        assertTrue(CommandParser.parse("rename only-one-name").isFailure)
        assertTrue(CommandParser.parse("find \"unclosed").isFailure)
    }
}
