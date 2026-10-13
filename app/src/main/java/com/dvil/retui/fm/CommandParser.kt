package com.dvil.retui.fm

internal sealed interface FmCommand {
    data object Pwd : FmCommand
    data object ListDirectory : FmCommand
    data class ChangeDirectory(val path: String) : FmCommand
    data class Find(val query: String) : FmCommand
    data class Open(val path: String) : FmCommand
    data class Preview(val path: String) : FmCommand
    data class MakeDirectory(val name: String) : FmCommand
    data class Rename(val oldName: String, val newName: String) : FmCommand
    data class Transfer(val move: Boolean, val sources: List<String>, val destination: String) : FmCommand
    data class Trash(val paths: List<String>) : FmCommand
    data class Zip(val archive: String, val sources: List<String>) : FmCommand
    data class Extract(val archive: String, val destination: String?) : FmCommand
    data class Favorite(val action: String, val path: String?) : FmCommand
    data object Recent : FmCommand
}

internal object CommandParser {
    fun parse(input: String): Result<FmCommand> = runCatching {
        require(input.none { it == ';' || it == '|' || it == '`' } && !input.contains("$(") ) {
            "Shell syntax is not supported"
        }
        val parts = tokenize(input)
        require(parts.isNotEmpty()) { "Enter a command" }
        when (parts.first().lowercase()) {
            "pwd" -> requireSize(parts, 1) { FmCommand.Pwd }
            "ls" -> requireSize(parts, 1) { FmCommand.ListDirectory }
            "cd" -> FmCommand.ChangeDirectory(requireArg(parts, 1))
            "find" -> FmCommand.Find(requireArg(parts, 1, joinRest = true))
            "open" -> FmCommand.Open(requireArg(parts, 1))
            "preview" -> FmCommand.Preview(requireArg(parts, 1))
            "mkdir" -> requireSize(parts, 2) { FmCommand.MakeDirectory(requireArg(parts, 1)) }
            "rename" -> requireSize(parts, 3) { FmCommand.Rename(requireArg(parts, 1), requireArg(parts, 2)) }
            "copy", "move" -> {
                require(parts.size >= 3) { "Usage: ${parts.first()} <source...> <destination>" }
                FmCommand.Transfer(parts.first().equals("move", true), parts.subList(1, parts.lastIndex), parts.last())
            }
            "trash" -> requireSize(parts, 2) { FmCommand.Trash(parts.drop(1)) }
            "zip" -> requireSize(parts, 3) { FmCommand.Zip(requireArg(parts, 1), parts.drop(2)) }
            "extract" -> requireSize(parts, 2) { FmCommand.Extract(requireArg(parts, 1), parts.getOrNull(2)) }
            "fav", "favorite" -> {
                require(parts.size in 2..3) { "Usage: fav add|remove|go [path]" }
                FmCommand.Favorite(parts[1].lowercase(), parts.getOrNull(2))
            }
            "recent" -> requireSize(parts, 1) { FmCommand.Recent }
            else -> error("Unknown command: ${parts.first()}")
        }
    }

    fun tokenize(input: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escape = false
        for (ch in input.trim()) {
            when {
                escape -> { current.append(ch); escape = false }
                ch == '\\' -> escape = true
                quote != null && ch == quote -> quote = null
                quote != null -> current.append(ch)
                ch == '\'' || ch == '"' -> quote = ch
                ch.isWhitespace() -> if (current.isNotEmpty()) {
                    out += current.toString()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        if (escape) current.append('\\')
        require(quote == null) { "Unclosed quote" }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun requireArg(parts: List<String>, index: Int, joinRest: Boolean = false): String {
        require(parts.size > index) { "Missing argument" }
        return if (joinRest) parts.drop(index).joinToString(" ") else parts[index]
    }

    private fun <T> requireSize(parts: List<String>, size: Int, block: () -> T): T {
        require(parts.size == size) { "Unexpected arguments" }
        return block()
    }
}
