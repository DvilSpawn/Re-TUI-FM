package com.dvil.retui.fm

import java.io.File
import java.nio.file.Files

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
