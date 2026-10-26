package com.dvil.retui.fm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

internal data class StorageCapabilities(
    val create: Boolean = false,
    val rename: Boolean = false,
    val copy: Boolean = false,
    val move: Boolean = false,
    val delete: Boolean = false
)

internal data class StorageItem(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modified: Long = 0L,
    val mimeType: String = "*/*",
    val flags: Long = 0L
)

internal interface StorageBackend {
    val id: String
    val displayName: String
    val capabilities: StorageCapabilities
    fun list(parent: Uri): List<StorageItem>
    fun createDirectory(parent: Uri, name: String): Uri?
    fun createFile(parent: Uri, name: String, mimeType: String): Uri?
    fun rename(item: Uri, name: String): Uri?
    fun delete(item: Uri): Boolean
    fun openInput(item: Uri): InputStream?
    fun openOutput(item: Uri, append: Boolean = false): OutputStream?
    fun copy(source: Uri, destination: Uri, name: String): Uri?
    fun move(source: Uri, destination: Uri, name: String): Uri? = rename(source, name)
}

internal class LocalFileBackend : StorageBackend {
    override val id = "local"
    override val displayName = "Phone storage"
    override val capabilities = StorageCapabilities(true, true, true, true, true)
    override fun list(parent: Uri): List<StorageItem> = emptyList()
    override fun createDirectory(parent: Uri, name: String): Uri? = null
    override fun createFile(parent: Uri, name: String, mimeType: String): Uri? = null
    override fun rename(item: Uri, name: String): Uri? = null
    override fun delete(item: Uri): Boolean = false
    override fun openInput(item: Uri): InputStream? = null
    override fun openOutput(item: Uri, append: Boolean): OutputStream? = null
    override fun copy(source: Uri, destination: Uri, name: String): Uri? = null
}

internal class SafStorageBackend(
    private val context: Context,
    val rootUri: Uri
) : StorageBackend {
    override val id: String = rootUri.toString()
    // A persisted tree grants access; providers query the document inside that tree.
    private val rootDocumentUri = runCatching {
        DocumentsContract.buildDocumentUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri))
    }.getOrDefault(rootUri)
    private fun documentUri(uri: Uri): Uri = if (uri == rootUri) rootDocumentUri else uri

    override val displayName: String = runCatching {
        context.contentResolver.query(rootDocumentUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Provider storage"
    override val capabilities: StorageCapabilities = capabilitiesFor(rootDocumentUri)

    internal fun capabilitiesFor(document: Uri): StorageCapabilities = runCatching {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_FLAGS)
        context.contentResolver.query(documentUri(document), projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) capabilitiesFor(cursor.getLong(0)) else StorageCapabilities()
        } ?: StorageCapabilities()
    }.getOrDefault(StorageCapabilities())

    override fun list(parent: Uri): List<StorageItem> {
        return runCatching {
            val documentId = DocumentsContract.getDocumentId(documentUri(parent))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_FLAGS
                ),
                null,
                null,
                "${DocumentsContract.Document.COLUMN_MIME_TYPE} ASC, ${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val out = ArrayList<StorageItem>()
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    out += StorageItem(
                        uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, id),
                        name = cursor.getString(1).orEmpty().ifBlank { "Unnamed" },
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = cursor.getLong(3).coerceAtLeast(0L),
                        modified = cursor.getLong(4).coerceAtLeast(0L),
                        mimeType = mime.ifBlank { "*/*" },
                        flags = cursor.getLong(5)
                    )
                }
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    override fun createDirectory(parent: Uri, name: String): Uri? = DocumentsContract.createDocument(
        context.contentResolver,
        documentUri(parent),
        DocumentsContract.Document.MIME_TYPE_DIR,
        name
    )

    override fun createFile(parent: Uri, name: String, mimeType: String): Uri? = DocumentsContract.createDocument(
        context.contentResolver,
        documentUri(parent),
        mimeType,
        name
    )

    override fun rename(item: Uri, name: String): Uri? = DocumentsContract.renameDocument(context.contentResolver, item, name)

    override fun delete(item: Uri): Boolean = DocumentsContract.deleteDocument(context.contentResolver, item)

    override fun openInput(item: Uri): InputStream? = runCatching { context.contentResolver.openInputStream(item) }.getOrNull()

    override fun openOutput(item: Uri, append: Boolean): OutputStream? = runCatching {
        context.contentResolver.openOutputStream(item, if (append) "wa" else "wt")
    }.getOrNull()

    override fun copy(source: Uri, destination: Uri, name: String): Uri? {
        val mime = context.contentResolver.getType(source) ?: "application/octet-stream"
        val target = createFile(destination, name, mime) ?: return null
        return runCatching {
            val input = openInput(source) ?: error("Provider could not open source")
            val output = openOutput(target) ?: error("Provider could not open destination")
            input.use { sourceStream -> output.use { targetStream -> sourceStream.copyTo(targetStream) } }
            target
        }.getOrElse {
            delete(target)
            null
        }
    }

    override fun move(source: Uri, destination: Uri, name: String): Uri? {
        if (DocumentsContract.getTreeDocumentId(rootUri) == DocumentsContract.getTreeDocumentId(destination)) {
            return rename(source, name)
        }
        return copy(source, destination, name)?.also { delete(source) }
    }

    fun parent(item: Uri): Uri? {
        if (item == rootUri || item == rootDocumentUri) return null
        return runCatching {
            val id = DocumentsContract.getDocumentId(item)
            val parentId = id.substringBeforeLast('/', "")
            if (parentId.isBlank()) rootUri else DocumentsContract.buildDocumentUriUsingTree(rootUri, parentId)
        }.getOrNull()
    }

    private fun capabilitiesFor(flags: Long): StorageCapabilities = StorageCapabilities(
        create = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() != 0L,
        rename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong() != 0L,
        copy = flags and DocumentsContract.Document.FLAG_SUPPORTS_COPY.toLong() != 0L,
        move = flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE.toLong() != 0L,
        delete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L
    )
}

internal object ProviderRoots {
    private const val PREFS = "retui_fm"
    private const val KEY = "saf_roots"

    fun list(context: Context): List<Uri> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet()).orEmpty().mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }

    fun add(context: Context, uri: Uri, flags: Int) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) }
        val roots = list(context).map(Uri::toString).toMutableSet()
        roots.add(uri.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, roots).apply()
    }

    fun remove(context: Context, uri: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val roots = list(context).map(Uri::toString).filterNot { it == uri.toString() }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, roots).apply()
    }
}
