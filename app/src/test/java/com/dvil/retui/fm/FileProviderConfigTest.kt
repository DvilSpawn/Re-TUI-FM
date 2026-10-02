package com.dvil.retui.fm

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileProviderConfigTest {
    @Test
    fun storageProviderRootIsScopedToStorageDirectory() {
        val file = listOf(
            File("src/main/res/xml/file_paths.xml"),
            File("app/src/main/res/xml/file_paths.xml")
        ).first(File::exists)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val roots = document.getElementsByTagName("root-path")
        var storagePath: String? = null
        for (index in 0 until roots.length) {
            val node = roots.item(index)
            val name = node.attributes.getNamedItem("name")?.nodeValue
            if (name == "storage") storagePath = node.attributes.getNamedItem("path")?.nodeValue
        }

        assertEquals("storage/", storagePath)
        assertNull(document.documentElement.attributes.getNamedItem("path"))
    }
}
