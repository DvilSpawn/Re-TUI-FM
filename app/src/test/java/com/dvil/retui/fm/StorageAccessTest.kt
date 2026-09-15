package com.dvil.retui.fm

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StorageAccessTest {
    @Test fun legacyPermissionsAndManifestCoverAndroidEightThroughTenOnly() {
        val expected = listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
        for (sdk in 26..29) assertEquals(expected, legacyStoragePermissions(sdk))
        for (sdk in 30..36) assertTrue(legacyStoragePermissions(sdk).isEmpty())
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val android = "http://schemas.android.com/apk/res/android"
        val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        assertEquals("true", app.getAttributeNS(android, "requestLegacyExternalStorage"))
        val permissions = doc.getElementsByTagName("uses-permission")
        for (name in expected) {
            val node = (0 until permissions.length).map { permissions.item(it) as org.w3c.dom.Element }
                .single { it.getAttributeNS(android, "name") == name }
            assertEquals("29", node.getAttributeNS(android, "maxSdkVersion"))
        }
    }
}
