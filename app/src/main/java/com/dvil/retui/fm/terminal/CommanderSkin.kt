package com.dvil.retui.fm.terminal

/** Reads the upstream MC skin format. No Android dependencies, so every bundled skin is testable. */
data class CommanderStyle(val foreground: Int, val background: Int, val attributes: Set<String> = emptySet()) {
    val displayed: CommanderStyle get() = if ("reverse" in attributes || "inverse" in attributes)
        copy(foreground = background, background = foreground, attributes = attributes - setOf("reverse", "inverse")) else this
}

class CommanderSkin private constructor(val id: String, private val sections: Map<String, Map<String, String>>) {
    val description: String get() = sections["skin"]?.get("description") ?: id
    val colorMode: String get() = when {
        sections["skin"]?.get("truecolors") == "true" -> "True color"
        sections["skin"]?.get("256colors") == "true" -> "256 colors"
        else -> "16 colors"
    }
    val doubleFrame: Boolean get() = sections["lines"]?.get("dhoriz") == "═"

    fun style(section: String, key: String = "_default_"): CommanderStyle {
        val terminal = CommanderStyle(rgb(210, 210, 210), rgb(0, 0, 0))
        val core = resolve(sections["core"]?.get("_default_"), terminal)
        val base = if (section == "core") core else resolve(sections[section]?.get("_default_"), core)
        return if (key == "_default_") base else resolve(sections[section]?.get(key), base)
    }

    private fun alias(value: String, seen: Set<String> = emptySet()): String {
        val next = sections["aliases"]?.get(value) ?: return value
        require(value !in seen) { "Cyclic color alias in $id: $value" }
        return alias(next, seen + value)
    }

    private fun resolve(raw: String?, fallback: CommanderStyle): CommanderStyle {
        val parts = raw.orEmpty().split(';').map { alias(it.trim()) }
        val foreground = parts.getOrNull(0).orEmpty().let {
            if (it.isEmpty()) fallback.foreground else color(it, true)
        }
        val background = parts.getOrNull(1).orEmpty().let {
            if (it.isEmpty()) fallback.background else color(it, false)
        }
        val attr = parts.getOrNull(2).orEmpty()
        return CommanderStyle(foreground, background, if (attr.isEmpty()) fallback.attributes else attr.split('+').toSet())
    }

    private fun color(value: String, foreground: Boolean): Int {
        if (value == "default") return if (foreground) rgb(210, 210, 210) else rgb(0, 0, 0)
        if (value.startsWith('#')) {
            val hex = value.drop(1).let { if (it.length == 3) it.flatMap { digit -> listOf(digit, digit) }.joinToString("") else it }
            require(hex.length == 6) { "Invalid RGB color in $id: $value" }
            return 0xff000000.toInt() or hex.toInt(16)
        }
        val index = when {
            value in names -> names.indexOf(value)
            value.matches(Regex("color[0-9]{1,3}")) -> value.drop(5).toInt()
            value.matches(Regex("gray[0-9]{1,2}")) -> 232 + value.drop(4).toInt().also { require(it in 0..23) }
            value.matches(Regex("rgb[0-5]{3}")) -> 16 + (value[3] - '0') * 36 + (value[4] - '0') * 6 + (value[5] - '0')
            else -> error("Unknown color in $id: $value")
        }
        require(index in 0..255) { "Invalid palette index in $id: $value" }
        return when {
            index < 16 -> 0xff000000.toInt() or ansi[index]
            index < 232 -> {
                val n = index - 16
                fun cube(c: Int) = if (c == 0) 0 else 55 + c * 40
                rgb(cube(n / 36), cube(n / 6 % 6), cube(n % 6))
            }
            else -> (8 + (index - 232) * 10).let { rgb(it, it, it) }
        }
    }

    companion object {
        private val names = listOf("black", "red", "green", "brown", "blue", "magenta", "cyan", "lightgray",
            "gray", "brightred", "brightgreen", "yellow", "brightblue", "brightmagenta", "brightcyan", "white")
        private val ansi = intArrayOf(0x000000, 0xcd0000, 0x00cd00, 0xcdcd00, 0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5,
            0x7f7f7f, 0xff0000, 0x00ff00, 0xffff00, 0x5c5cff, 0xff00ff, 0x00ffff, 0xffffff)
        private fun rgb(r: Int, g: Int, b: Int) = 0xff000000.toInt() or (r shl 16) or (g shl 8) or b

        fun parse(id: String, text: String): CommanderSkin {
            val sections = linkedMapOf<String, MutableMap<String, String>>()
            var section = ""
            text.lineSequence().map(String::trim).forEach { line ->
                when {
                    line.isEmpty() || line.startsWith('#') -> Unit
                    line.startsWith('[') && line.endsWith(']') -> section = line.drop(1).dropLast(1)
                    '=' in line -> sections.getOrPut(section) { linkedMapOf() }[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
                }
            }
            require(sections["core"]?.containsKey("_default_") == true) { "Missing core palette in $id" }
            return CommanderSkin(id, sections)
        }

        /** All color roles, including unused MC widgets, for exhaustive catalog validation. */
        fun colorRoles(skin: CommanderSkin): List<CommanderStyle> = skin.sections.filterKeys {
            it !in setOf("skin", "lines", "aliases") && !it.startsWith("widget-")
        }.flatMap { (section, entries) -> entries.keys.filterNot { it.endsWith("-char") }.map { skin.style(section, it) } }
    }
}
