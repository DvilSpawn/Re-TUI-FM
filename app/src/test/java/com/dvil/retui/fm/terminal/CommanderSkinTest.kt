package com.dvil.retui.fm.terminal

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CommanderSkinTest {
    @Test fun everyBundledSkinResolvesEveryColorRole() {
        val files = File("src/main/assets/mc-skins").listFiles()!!.filter { it.extension == "ini" }
        assertEquals(41, files.size)
        files.forEach { file ->
            val skin = CommanderSkin.parse(file.nameWithoutExtension, file.readText())
            assertTrue(file.name, skin.description.isNotBlank())
            assertTrue(file.name, CommanderSkin.colorRoles(skin).size > 60)
            CommanderSkin.colorRoles(skin).forEach {
                assertEquals(file.name, 255, it.foreground ushr 24)
                assertEquals(file.name, 255, it.background ushr 24)
            }
        }
    }

    @Test fun aliasesAndPartialValuesInheritEachComponent() {
        val skin = CommanderSkin.parse("sample", """
            [aliases]
            main = next
            next = #123
            accent = rgb141
            [core]
            _default_ = main;color234;italic
            selected = ;accent
            [dialog]
            _default_ = gray22
            dfocus = ;;none
        """.trimIndent())
        assertEquals(0xff112233.toInt(), skin.style("core", "selected").foreground)
        assertEquals(0xff5fd75f.toInt(), skin.style("core", "selected").background)
        assertEquals(setOf("italic"), skin.style("core", "selected").attributes)
        assertEquals(0xffe4e4e4.toInt(), skin.style("dialog", "dfocus").foreground)
        assertEquals(0xff1c1c1c.toInt(), skin.style("dialog", "dfocus").background)
        assertEquals(setOf("none"), skin.style("dialog", "dfocus").attributes)
        assertEquals(skin.style("core"), skin.style("missing", "missing"))
    }

    @Test fun defaultColorsAndReverseAreOpaqueAndDeterministic() {
        val skin = CommanderSkin.parse("sample", "[core]\n_default_ = white;black\nselected = default;default;reverse")
        val selected = skin.style("core", "selected").displayed
        assertEquals(0xff000000.toInt(), selected.foreground)
        assertEquals(0xffd2d2d2.toInt(), selected.background)
    }

    @Test(expected = IllegalArgumentException::class) fun aliasCyclesAreRejected() {
        CommanderSkin.parse("sample", "[aliases]\na=b\nb=a\n[core]\n_default_=a;black").style("core")
    }
}
