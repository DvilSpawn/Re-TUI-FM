package com.dvil.retui.fm

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder

class LauncherMetadataProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        enforceLauncherCaller()
        val base = uri.getQueryParameter("path").orEmpty()
        val results = when (uri.pathSegments.firstOrNull()) {
            "resolve" -> listOfNotNull(
                FilesNavigationContract.resolveDirectory(base, uri.getQueryParameter("target").orEmpty())
            )
            "entries" -> FilesNavigationContract.entries(
                base,
                uri.getQueryParameter("kind") == "directory",
                uri.getQueryParameter("query").orEmpty()
            )
            else -> emptyList()
        }
        return MatrixCursor(COLUMNS).apply {
            results.forEach { addRow(arrayOf<Any?>(it.name, it.path, if (it.isDirectory) 1 else 0)) }
        }
    }

    private fun enforceLauncherCaller() {
        val context = requireNotNull(context)
        val caller = Binder.getCallingUid()
        if (caller == context.applicationInfo.uid) return
        val packages = context.packageManager.getPackagesForUid(caller).orEmpty()
        if (LAUNCHER_PACKAGE !in packages) throw SecurityException("Caller is not Re:T-UI Launcher")
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.dvil.retui.fm.launcher.entry"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")

    companion object {
        private const val LAUNCHER_PACKAGE = "com.dvil.tui_renewed"
        private val COLUMNS = arrayOf("name", "path", "is_directory")
    }
}
