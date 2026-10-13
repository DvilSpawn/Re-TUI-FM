package com.dvil.retui.fm

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.UUID

internal fun rememberTrashDirectory(context: Context, directory: File) {
    val prefs = context.getSharedPreferences("retui_fm", Context.MODE_PRIVATE)
    val paths = prefs.getString("trash_dirs", "").orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableSet()
    if (paths.add(directory.absolutePath)) {
        prefs.edit().putString("trash_dirs", paths.joinToString("\n")).apply()
    }
}

internal fun renameFile(source: File, name: String): File {
    require(name.isNotBlank() && name != "." && name != ".." &&
        name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Enter a single valid file name" }
    require(source.exists()) { "Item no longer exists" }
    val target = File(requireNotNull(source.parentFile) { "Cannot rename storage root" }, name)
    if (source.name == name) return source
    require(!target.exists()) { "An item with this name already exists" }
    Files.move(source.toPath(), target.toPath())
    return target
}

internal fun batchRename(sources: List<File>, names: List<String>): List<File> {
    require(sources.isNotEmpty() && sources.size == names.size) { "Select items to rename" }
    val parent = requireNotNull(sources.first().parentFile) { "Cannot rename storage root" }.canonicalFile
    val originals = sources.map { require(it.exists()) { "Item no longer exists: ${it.name}" }; it.canonicalFile }
    require(originals.all { it.parentFile == parent }) { "Batch rename requires one folder" }
    val targets = names.map { name -> File(parent, name).canonicalFile }
    require(targets.map(File::getName).distinct().size == targets.size) { "New names must be unique" }
    targets.forEach { target ->
        require(target.name.isNotBlank() && target.name != "." && target.name != ".." &&
            target.name.none { it == '/' || it == '\\' || it == '\u0000' }) { "Enter valid file names" }
        require(!target.exists() || target in originals) { "An item with this name already exists" }
    }
    val temporary = originals.map { source ->
        var temp: File
        do { temp = File(parent, ".retui-rename-${UUID.randomUUID()}") } while (temp.exists())
        Files.move(source.toPath(), temp.toPath())
        temp
    }
    val completed = ArrayList<Pair<File, File>>()
    return try {
        temporary.zip(targets).forEach { (temp, target) ->
            Files.move(temp.toPath(), target.toPath())
            completed += temp to target
        }
        targets
    } catch (failure: Exception) {
        completed.asReversed().forEach { (temp, target) ->
            target.renameTo(originals[temporary.indexOf(temp)])
        }
        temporary.forEachIndexed { index, temp -> if (temp.exists()) temp.renameTo(originals[index]) }
        throw failure
    }
}
