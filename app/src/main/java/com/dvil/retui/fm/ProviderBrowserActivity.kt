package com.dvil.retui.fm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque

internal class ProviderBrowserActivity : Activity() {
    private lateinit var backend: SafStorageBackend
    private lateinit var current: Uri
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private val history = ArrayDeque<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootUri = intent.getParcelableExtra<Uri>(EXTRA_ROOT) ?: run { finish(); return }
        backend = SafStorageBackend(this, rootUri)
        current = intent.getParcelableExtra(EXTRA_DOCUMENT) ?: rootUri
        setContentView(buildUi())
        render()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(18, 18, 18, 18)
        root.setBackgroundColor(Color.rgb(14, 14, 16))

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        title = text("PROVIDER", 18, true)
        title.setTextColor(Color.rgb(230, 150, 160))
        bar.addView(title, LinearLayout.LayoutParams(0, 56, 1f))
        bar.addView(button("UP") { goUp() }, LinearLayout.LayoutParams(84, 56))
        bar.addView(button("NEW") { promptNewFolder() }, LinearLayout.LayoutParams(84, 56))
        root.addView(bar)

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun render() {
        title.text = "${backend.displayName}\n${current.lastPathSegment.orEmpty()}"
        list.removeAllViews()
        val entries = backend.list(current)
        if (entries.isEmpty()) {
            list.addView(text("Folder is empty or unavailable.", 15, false), LinearLayout.LayoutParams(-1, 60))
            return
        }
        entries.forEach { item ->
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(12, 4, 4, 4)
            row.setBackgroundColor(Color.rgb(25, 27, 27))
            val name = text(if (item.isDirectory) "[DIR] ${item.name}" else item.name, 15, item.isDirectory)
            name.setTextColor(if (item.isDirectory) Color.rgb(232, 190, 110) else Color.rgb(210, 140, 150))
            row.addView(name, LinearLayout.LayoutParams(0, 56, 1f))
            val detail = text(if (item.isDirectory) "folder" else humanSize(item.size), 12, false)
            detail.setTextColor(Color.GRAY)
            row.addView(detail, LinearLayout.LayoutParams(110, 56))
            row.setOnClickListener { if (item.isDirectory) openDirectory(item.uri) else openFile(item) }
            row.setOnLongClickListener { showItemMenu(item); true }
            list.addView(row, LinearLayout.LayoutParams(-1, 64).apply { topMargin = 6 })
        }
    }

    private fun openDirectory(uri: Uri) {
        history.addLast(current)
        current = uri
        render()
    }

    private fun goUp() {
        if (history.isNotEmpty()) current = history.removeLast()
        else if (current != backend.rootUri) current = backend.parent(current) ?: backend.rootUri
        else { finish(); return }
        render()
    }

    private fun promptNewFolder() {
        val input = EditText(this).apply { hint = "Folder name"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this)
            .setTitle("New provider folder")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && backend.createDirectory(current, name) == null) toast("Provider could not create that folder")
                render()
            }.show()
    }

    private fun showItemMenu(item: StorageItem) {
        val options = mutableListOf("Open" to { if (item.isDirectory) openDirectory(item.uri) else openFile(item) })
        if (backend.capabilities.rename) options += "Rename" to { promptRename(item) }
        if (backend.capabilities.delete) options += "Delete" to {
            if (!backend.delete(item.uri)) toast("Provider could not delete ${item.name}")
            render()
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun promptRename(item: StorageItem) {
        val input = EditText(this).apply { setText(item.name); setSingleLine(true) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                if (backend.rename(item.uri, input.text.toString().trim()) == null) toast("Provider could not rename that item")
                render()
            }.show()
    }

    private fun openFile(item: StorageItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = item.uri
            type = item.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure { toast("No Android app can open ${item.name}") }
    }

    private fun button(value: String, action: () -> Unit): TextView = text(value, 13, true).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(240, 190, 120))
        setBackgroundColor(Color.rgb(45, 35, 35))
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        typeface = android.graphics.Typeface.MONOSPACE
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

    companion object {
        const val EXTRA_ROOT = "provider_root"
        const val EXTRA_DOCUMENT = "provider_document"
    }
}
