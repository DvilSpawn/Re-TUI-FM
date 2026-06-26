package com.dvil.retui.fm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private enum class Panel { LEFT, RIGHT }

    private data class FileEntry(
        val file: File?,
        val label: String,
        val isDirectory: Boolean,
        val isParent: Boolean = false
    )

    private data class PaneState(
        var directory: File,
        val rows: ArrayList<FileEntry> = ArrayList(),
        var cursor: Int = 0,
        var topRow: Int = 0
    )

    private lateinit var leftPane: PaneState
    private lateinit var rightPane: PaneState
    private var activePanel = Panel.LEFT

    private var rootView: FrameLayout? = null
    private var mainContainer: LinearLayout? = null
    private var leftRowsView: LinearLayout? = null
    private var rightRowsView: LinearLayout? = null
    private var leftScrollView: ScrollView? = null
    private var rightScrollView: ScrollView? = null
    private var leftPathView: TextView? = null
    private var rightPathView: TextView? = null
    private var leftFooterView: TextView? = null
    private var rightFooterView: TextView? = null
    private var leftDiskView: TextView? = null
    private var rightDiskView: TextView? = null
    private var hintView: TextView? = null
    private var inputView: EditText? = null
    private var recentRowsView: LinearLayout? = null

    private var bgColor = Color.rgb(14, 14, 16)
    private var panelColor = Color.rgb(21, 23, 23)
    private var textColor = Color.rgb(205, 136, 146)
    private var borderColor = Color.rgb(126, 70, 78)
    private var inputBgColor = Color.TRANSPARENT
    private var inputTextColor = textColor
    private var outputPanelColor = panelColor
    private var outputTextColor = textColor
    private var outputBorderColor = borderColor
    private var fileTextColor = outputTextColor
    private var directoryTextColor = textColor
    private var selectionBgColor = Color.rgb(3, 169, 244)
    private var selectionTextColor = Color.WHITE
    private var modulePanelColor = Color.rgb(55, 28, 34)
    private var moduleTextColor = textColor
    private var moduleBorderColor = borderColor
    private var headerPanelColor = Color.rgb(26, 28, 28)
    private var headerTextColor = textColor
    private var moduleButtonBgColor = Color.rgb(111, 62, 70)
    private var moduleButtonTextColor = textColor
    private var moduleButtonBorderColor = borderColor
    private var headerTextSizeSp = 14
    private var outputTextSizeSp = 13
    private var inputFontSizeSp = 13
    private var moduleCornerRadiusDp = 0
    private var outputCornerRadiusDp = 0
    private var headerCornerRadiusDp = 0
    private var topMarginDp = 8
    private var displayMarginLeftPx = 0
    private var displayMarginTopPx = 0
    private var displayMarginRightPx = 0
    private var displayMarginBottomPx = 0
    private var systemInsetLeft = 0
    private var systemInsetTop = 0
    private var systemInsetRight = 0
    private var systemInsetBottom = 0
    private var terminalBackgroundImage: String? = null
    private var cyberdeckMode = false
    private var appTypeface: Typeface? = Typeface.MONOSPACE
    private var firstResume = true

    private val dateFormat = SimpleDateFormat("MMM d", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val topTabOverlapDp = 11

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        configureWindow()
        applyThemeExtras(intent)
        val start = resolveStartDirectory(intent)
        leftPane = PaneState(start)
        rightPane = PaneState(start)
        setContentView(buildUi())
        rootView?.requestFocus()
        ensureStorageAccess()
        reloadAll()
        handleIncomingCommand(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyThemeExtras(intent)
        val start = resolveStartDirectory(intent)
        activePane().directory = start
        reloadAll()
        handleIncomingCommand(intent)
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
        } else if (::leftPane.isInitialized) {
            reloadAll()
        }
    }

    override fun onBackPressed() {
        finish()
    }

    private fun configureWindow() {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.BLACK
        }
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        rootView = root
        root.isFocusableInTouchMode = true
        root.clipChildren = false
        root.clipToPadding = false
        root.setBackgroundColor(Color.TRANSPARENT)
        applyWallpaperBackground(root)
        installWindowInsetsHandler(root)

        val main = LinearLayout(this)
        mainContainer = main
        main.orientation = LinearLayout.VERTICAL
        main.clipChildren = false
        main.clipToPadding = false
        main.setPadding(dp(10), 0, dp(10), dp(10))
        main.background = panelDrawable(PanelRole.OUTER)
        val mainParams = FrameLayout.LayoutParams(-1, -1)
        applyMainMargins(mainParams)
        root.addView(main, mainParams)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.requestApplyInsets()
        }

        main.addView(buildTopBar(), LinearLayout.LayoutParams(-1, dp(18)))
        val paneParams = LinearLayout.LayoutParams(-1, 0, 1f)
        paneParams.topMargin = dp(4)
        main.addView(buildPane(Panel.LEFT), paneParams)
        main.addView(buildRecentDock(), LinearLayout.LayoutParams(-1, dp(130)))
        return root
    }

    private fun installWindowInsetsHandler(root: FrameLayout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) return
        root.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets = FmVisualInterop.safeInsets(insets)
            systemInsetLeft = safeInsets[0]
            systemInsetTop = safeInsets[1]
            systemInsetRight = safeInsets[2]
            systemInsetBottom = safeInsets[3]
            root.setPadding(systemInsetLeft, systemInsetTop, systemInsetRight, systemInsetBottom)
            updateMainMargins()
            insets
        }
    }

    private fun updateMainMargins() {
        val params = mainContainer?.layoutParams as? FrameLayout.LayoutParams ?: return
        applyMainMargins(params)
        mainContainer?.layoutParams = params
    }

    private fun applyMainMargins(params: FrameLayout.LayoutParams) {
        params.setMargins(
            dp(8) + displayMarginLeftPx,
            dp(topMarginDp) + displayMarginTopPx,
            dp(8) + displayMarginRightPx,
            dp(8) + displayMarginBottomPx
        )
    }

    private fun buildTopBar(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
        row.clipChildren = false
        row.clipToPadding = false
        row.setPadding(dp(28), 0, dp(18), 0)

        val title = label("RETUI FM", 16, true)
        title.gravity = Gravity.CENTER
        title.setTextColor(headerTextColor)
        title.setPadding(dp(4), 0, dp(4), 0)
        title.background = panelDrawable(PanelRole.HEADER)
        title.translationY = -dp(topTabOverlapDp).toFloat()
        row.addView(title, LinearLayout.LayoutParams(dp(124), dp(28)))

        val spacer = TextView(this)
        row.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        addTopButton(row, "⌂") { goHome() }
        addTopButton(row, "+") { promptMkdir() }
        addTopButton(row, "↻") { reloadAll() }
        return row
    }

    private fun buildPane(panel: Panel): View {
        val pane = LinearLayout(this)
        pane.orientation = LinearLayout.VERTICAL
        pane.setPadding(dp(8), dp(6), dp(8), dp(4))
        pane.background = panelDrawable(PanelRole.OUTPUT)
        pane.setOnClickListener { switchPanel(panel) }

        val path = label("/", outputTextSizeSp, true)
        path.gravity = Gravity.CENTER
        pane.addView(path, LinearLayout.LayoutParams(-1, dp(26)))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.addView(column("NAME", 1.75f, Gravity.START))
        header.addView(column("SIZE", 0.78f, Gravity.END))
        header.addView(column("MODIFY", 0.82f, Gravity.CENTER))
        header.addView(column("TIME", 0.72f, Gravity.CENTER))
        pane.addView(header, LinearLayout.LayoutParams(-1, dp(24)))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        scroll.addView(rows, FrameLayout.LayoutParams(-1, -2))
        pane.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = label("", max(10, outputTextSizeSp - 2), true)
        footer.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        pane.addView(footer, LinearLayout.LayoutParams(-1, dp(24)))
        val disk = label("", max(10, outputTextSizeSp - 2), true)
        disk.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        pane.addView(disk, LinearLayout.LayoutParams(-1, dp(22)))

        if (panel == Panel.LEFT) {
            leftPathView = path
            leftRowsView = rows
            leftScrollView = scroll
            leftFooterView = footer
            leftDiskView = disk
        } else {
            rightPathView = path
            rightRowsView = rows
            rightScrollView = scroll
            rightFooterView = footer
            rightDiskView = disk
        }
        return pane
    }

    private fun buildRecentDock(): View {
        val dock = LinearLayout(this)
        dock.orientation = LinearLayout.VERTICAL
        dock.setPadding(dp(10), dp(6), dp(10), dp(8))
        dock.background = panelDrawable(PanelRole.OUTPUT)

        hintView = label("Recent changes", max(10, inputFontSizeSp - 3), true)
        hintView!!.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        dock.addView(hintView, LinearLayout.LayoutParams(-1, dp(22)))

        recentRowsView = LinearLayout(this)
        recentRowsView!!.orientation = LinearLayout.VERTICAL
        dock.addView(recentRowsView, LinearLayout.LayoutParams(-1, 0, 1f))
        return dock
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleHardwareKey(event.keyCode, event)) return true
        return super.dispatchKeyEvent(event)
    }


    private fun handleHardwareKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (inputView?.hasFocus() == true && !inputView!!.text.toString().isEmpty()) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                openSelected()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursor(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                switchPanel(otherPanel())
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                openSelected()
                true
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                moveCursor(-visibleRowCount(activePanel))
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                moveCursor(visibleRowCount(activePanel))
                true
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                cursorHome()
                true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                cursorEnd()
                true
            }
            else -> false
        }
    }

    private fun submitInputOrOpenSelection() {
        val command = inputView?.text?.toString()?.trim() ?: ""
        if (command.isEmpty()) openSelected() else runCommand(command)
    }

    private fun runCommand(command: String) {
        inputView?.setText("")
        val parts = splitCommand(command)
        if (parts.isEmpty()) return
        when (parts[0].lowercase(Locale.US)) {
            "help" -> showOutput("HELP", helpText())
            "refresh", "ls" -> reloadAll()
            "pwd" -> showOutput("PWD", activePane().directory.absolutePath)
            "cd" -> changeDirectory(if (parts.size > 1) resolvePath(parts[1]) else homeDirectory())
            "preview", "peek", "view" -> resolveArg(parts, 1)?.let { previewFile(it) } ?: showOutput("PREVIEW", "preview: usage: preview [file]")
            "edit" -> resolveArg(parts, 1)?.let { editFile(it) } ?: showOutput("EDIT", "edit: usage: edit [text file]")
            "open" -> resolveArg(parts, 1)?.let { openFile(it) } ?: selectedFile()?.let { openFile(it) }
            "share" -> resolveArg(parts, 1)?.let { shareFile(it) } ?: selectedFile()?.let { shareFile(it) }
            "mkdir" -> runMkdir(parts)
            "rm", "delete" -> runDelete(parts)
            "cp" -> runCopy(parts)
            "mv", "renmov" -> runMove(parts)
            "find", "search" -> runFind(parts.drop(1).joinToString(" "))
            "exit", "quit", "close" -> finish()
            else -> showOutput("ERROR", "Command not found: $command")
        }
        refocusInput()
    }

    private fun handleIncomingCommand(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_COMMAND)?.trim()
        if (!command.isNullOrEmpty()) {
            runCommand(command)
        }
    }

    private fun reloadAll() {
        reloadPane(leftPane)
        reloadPane(rightPane)
        renderAll()
    }

    private fun reloadPane(pane: PaneState) {
        val dir = pane.directory
        pane.rows.clear()
        pane.rows.add(FileEntry(dir.parentFile, "/..", true, true))
        val files = dir.listFiles()?.toList().orEmpty()
        val sorted = files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
        for (file in sorted.take(MAX_ROWS)) {
            pane.rows.add(FileEntry(file, file.name, file.isDirectory))
        }
        if (pane.cursor >= pane.rows.size) pane.cursor = max(0, pane.rows.size - 1)
        if (pane.cursor < 0) pane.cursor = 0
        ensureCursorVisible(pane, panelFor(pane))
    }

    private fun renderAll() {
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateHint()
        updateRecentFiles()
    }

    private fun renderActivePane() {
        renderPane(activePanel)
        updateHint()
        updateRecentFiles()
    }

    private fun renderPane(panel: Panel) {
        val pane = pane(panel)
        val path = if (panel == Panel.LEFT) leftPathView else rightPathView
        val rowsView = if (panel == Panel.LEFT) leftRowsView else rightRowsView
        val scroll = if (panel == Panel.LEFT) leftScrollView else rightScrollView
        val footer = if (panel == Panel.LEFT) leftFooterView else rightFooterView
        val disk = if (panel == Panel.LEFT) leftDiskView else rightDiskView
        path?.text = abbreviatePath(pane.directory.absolutePath)
        rowsView?.removeAllViews()
        val visible = visibleRowCount(panel)
        ensureCursorVisible(pane, panel)
        val end = min(pane.rows.size, pane.topRow + visible)
        for (i in pane.topRow until end) {
            rowsView?.addView(rowView(pane.rows[i], panel, i), LinearLayout.LayoutParams(-1, dp(22)))
        }
        scroll?.scrollTo(0, 0)
        footer?.text = pane.rows.getOrNull(pane.cursor)?.label ?: pane.directory.name
        disk?.text = diskSummary(pane.directory)
    }

    private fun rowView(entry: FileEntry, panel: Panel, index: Int): View {
        val selected = panel == activePanel && index == pane(panel).cursor
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), 0, dp(2), 0)
        row.background = rowSelectionBackground(selected)
        row.setOnClickListener {
            activePanel = panel
            pane(panel).cursor = index
            ensureCursorVisible(pane(panel), panel)
            renderActivePane()
            openSelected()
        }
        row.setOnLongClickListener {
            activePanel = panel
            pane(panel).cursor = index
            renderActivePane()
            entry.file?.let { if (entry.isParent) changeDirectory(it) else showItemMenu(it) }
            true
        }

        row.addView(nameCell(entry, selected), LinearLayout.LayoutParams(0, -1, 1.75f))
        row.addView(cell(sizeText(entry.file), 0.78f, Gravity.END, false, rowTextColor(selected, false)))
        row.addView(cell(dateText(entry.file), 0.82f, Gravity.CENTER, false, rowTextColor(selected, false)))
        row.addView(cell(timeText(entry.file), 0.72f, Gravity.CENTER, false, rowTextColor(selected, false)))
        return row
    }

    private fun moveCursor(delta: Int) {
        val pane = activePane()
        if (pane.rows.isEmpty()) return
        val oldCursor = pane.cursor
        val oldTop = pane.topRow
        pane.cursor = clamp(pane.cursor + delta, 0, pane.rows.size - 1)
        ensureCursorVisible(pane, activePanel)
        if (pane.topRow == oldTop) {
            updateVisibleSelection(activePanel, oldCursor)
            updatePaneStatus(activePanel)
            updateHint()
        } else {
            renderActivePane()
        }
    }

    private fun cursorHome() {
        activePane().cursor = 0
        activePane().topRow = 0
        renderActivePane()
    }

    private fun cursorEnd() {
        val pane = activePane()
        pane.cursor = max(0, pane.rows.size - 1)
        ensureCursorVisible(pane, activePanel)
        renderActivePane()
    }

    private fun ensureCursorVisible(pane: PaneState, panel: Panel) {
        if (pane.rows.isEmpty()) {
            pane.topRow = 0
            return
        }
        val visible = visibleRowCount(panel)
        val maxTop = max(0, pane.rows.size - visible)
        pane.cursor = clamp(pane.cursor, 0, pane.rows.size - 1)
        pane.topRow = clamp(pane.topRow, 0, maxTop)
        if (pane.cursor < pane.topRow) pane.topRow = pane.cursor
        if (pane.cursor >= pane.topRow + visible) pane.topRow = pane.cursor - visible + 1
        pane.topRow = clamp(pane.topRow, 0, maxTop)
    }

    private fun visibleRowCount(panel: Panel): Int {
        val scroll = if (panel == Panel.LEFT) leftScrollView else rightScrollView
        val height = scroll?.height ?: 0
        val rowHeight = max(1, dp(22))
        return max(1, min(18, if (height > 0) height / rowHeight else 18))
    }

    private fun updateVisibleSelection(panel: Panel, oldCursor: Int) {
        val pane = pane(panel)
        val rowsView = if (panel == Panel.LEFT) leftRowsView else rightRowsView
        val oldVisible = oldCursor - pane.topRow
        val newVisible = pane.cursor - pane.topRow
        if (oldVisible in 0 until (rowsView?.childCount ?: 0)) {
            rowsView?.getChildAt(oldVisible)?.background = rowSelectionBackground(false)
        }
        if (newVisible in 0 until (rowsView?.childCount ?: 0)) {
            rowsView?.getChildAt(newVisible)?.background = rowSelectionBackground(panel == activePanel)
        }
    }

    private fun updatePaneStatus(panel: Panel) {
        val pane = pane(panel)
        val footer = if (panel == Panel.LEFT) leftFooterView else rightFooterView
        val disk = if (panel == Panel.LEFT) leftDiskView else rightDiskView
        footer?.text = pane.rows.getOrNull(pane.cursor)?.label ?: pane.directory.name
        disk?.text = diskSummary(pane.directory)
    }

    private fun openSelected() {
        val entry = activePane().rows.getOrNull(activePane().cursor) ?: return
        val file = entry.file ?: return
        if (entry.isDirectory) changeDirectory(file) else previewFile(file)
    }

    private fun changeDirectory(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            showOutput("CD", "Not a directory: ${dir.absolutePath}")
            return
        }
        if (!dir.canRead()) {
            showOutput("CD", "Cannot read: ${dir.absolutePath}")
            return
        }
        val pane = activePane()
        pane.directory = dir
        pane.cursor = 0
        pane.topRow = 0
        reloadPane(pane)
        renderActivePane()
    }

    private fun goHome() {
        changeDirectory(homeDirectory())
    }

    private fun goParent() {
        activePane().directory.parentFile?.let { changeDirectory(it) }
    }

    private fun switchPanel(panel: Panel) {
        activePanel = panel
        renderAll()
    }

    private fun previewFile(file: File) {
        if (!file.exists()) {
            showOutput("PREVIEW", "Not found: ${file.absolutePath}")
            return
        }
        if (file.isDirectory) {
            val children = file.listFiles()
            showOutput("PREVIEW", "${file.absolutePath}\n\n${children?.size ?: 0} children")
            return
        }
        if (!isLikelyText(file)) {
            showOutput("PREVIEW", "${file.absolutePath}\n\nNo text preview for this file. Use OPEN to hand it to Android.")
            return
        }
        try {
            FileInputStream(file).use { input ->
                val length = min(file.length(), PREVIEW_MAX_BYTES.toLong()).toInt()
                val buffer = ByteArray(length)
                val read = input.read(buffer)
                val text = if (read > 0) String(buffer, 0, read, StandardCharsets.UTF_8) else ""
                val suffix = if (file.length() > PREVIEW_MAX_BYTES) "\n\n... preview capped at $PREVIEW_MAX_BYTES bytes" else ""
                showOutput("PREVIEW", file.absolutePath + "\n\n" + text + suffix)
            }
        } catch (e: Exception) {
            showOutput("PREVIEW", "Could not read ${file.name}:\n${e.message}")
        }
    }

    private fun editFile(file: File) {
        if (!file.exists() || file.isDirectory || !isLikelyText(file)) {
            showOutput("EDIT", "edit: supported for text-like files only")
            return
        }
        val intent = Intent(this, FmEditorActivity::class.java)
        putThemeExtras(intent)
        intent.putExtra(EXTRA_PATH, file.absolutePath)
        startActivity(intent)
    }

    private fun openFile(file: File) {
        val uri = uriFor(file) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mimeFor(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, file.name))
        } catch (_: ActivityNotFoundException) {
            showOutput("OPEN", "No app can open: ${file.name}")
        }
    }

    private fun shareFile(file: File) {
        if (!file.isFile) {
            showOutput("SHARE", "Not a file: ${file.name}")
            return
        }
        val uri = uriFor(file) ?: return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = mimeFor(file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, file.name))
    }

    private fun runMkdir(parts: List<String>) {
        val name = parts.getOrNull(1)
        if (name.isNullOrBlank()) {
            showOutput("MKDIR", "mkdir: missing folder name")
            return
        }
        val dir = resolvePath(name)
        if (dir.exists()) {
            showOutput("MKDIR", "Already exists: ${dir.absolutePath}")
            return
        }
        if (dir.mkdirs()) {
            reloadAll()
            showOutput("MKDIR", "Created ${dir.absolutePath}")
        } else {
            showOutput("MKDIR", "Could not create ${dir.absolutePath}")
        }
    }

    private fun runDelete(parts: List<String>) {
        val file = resolveArg(parts, 1) ?: selectedFile()
        if (file == null || !file.exists()) {
            showOutput("DELETE", "rm: no target")
            return
        }
        confirmDelete(file)
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${file.name}?")
            .setMessage(file.absolutePath)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("TRASH") { _, _ ->
                val ok = moveToTrash(file)
                reloadAll()
                showOutput("TRASH", if (ok) "Moved to .retui-trash:\n${file.absolutePath}" else "Could not trash ${file.absolutePath}")
            }
            .show()
    }

    private fun showItemMenu(file: File) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add(if (file.isDirectory) "Open" else "Preview")
        actions.add { if (file.isDirectory) changeDirectory(file) else previewFile(file) }
        if (file.isFile && isLikelyText(file)) {
            labels.add("Edit")
            actions.add { editFile(file) }
        }
        if (file.isFile) {
            labels.add("Open with Android")
            actions.add { openFile(file) }
            labels.add("Share")
            actions.add { shareFile(file) }
        }
        if (file.isDirectory) {
            labels.add("New folder here")
            actions.add { promptMkdir(file) }
        }
        if (!file.name.startsWith("..")) {
            labels.add("Move to trash")
            actions.add { confirmDelete(file) }
        }
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun promptMkdir(baseDir: File = activePane().directory) {
        val input = EditText(this)
        input.setSingleLine(true)
        input.typeface = appTypeface
        input.setTextColor(inputTextColor)
        input.setHintTextColor(withAlpha(inputTextColor, 145))
        input.hint = "Folder name"
        AlertDialog.Builder(this)
            .setTitle("New folder")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val dir = File(baseDir, name)
                    if (dir.mkdirs()) {
                        reloadAll()
                        showOutput("MKDIR", "Created ${dir.absolutePath}")
                    } else {
                        showOutput("MKDIR", "Could not create ${dir.absolutePath}")
                    }
                }
            }
            .show()
    }

    private fun runCopy(parts: List<String>) {
        val src = resolveArg(parts, 1)
        val dst = parts.getOrNull(2)?.let { resolvePath(it) }
        if (src == null || dst == null) {
            showOutput("COPY", "cp: usage: cp [source] [destination]")
            return
        }
        try {
            val target = if (dst.isDirectory) File(dst, src.name) else dst
            copyRecursively(src, target)
            reloadAll()
            showOutput("COPY", "Copied ${src.absolutePath}\n-> ${target.absolutePath}")
        } catch (e: Exception) {
            showOutput("COPY", "Copy failed:\n${e.message}")
        }
    }

    private fun runMove(parts: List<String>) {
        val src = resolveArg(parts, 1)
        val dst = parts.getOrNull(2)?.let { resolvePath(it) }
        if (src == null || dst == null) {
            showOutput("MOVE", "mv: usage: mv [source] [destination]")
            return
        }
        val target = if (dst.isDirectory) File(dst, src.name) else dst
        if (src.renameTo(target)) {
            reloadAll()
            showOutput("MOVE", "Moved ${src.absolutePath}\n-> ${target.absolutePath}")
        } else {
            try {
                copyRecursively(src, target)
                val deleted = if (src.isDirectory) src.deleteRecursively() else src.delete()
                reloadAll()
                showOutput("MOVE", if (deleted) "Moved ${src.absolutePath}\n-> ${target.absolutePath}" else "Copied but could not remove source")
            } catch (e: Exception) {
                showOutput("MOVE", "Move failed:\n${e.message}")
            }
        }
    }

    private fun runFind(query: String) {
        if (query.isBlank()) {
            showOutput("FIND", "find: usage: find [pattern]")
            return
        }
        val out = StringBuilder()
        val pattern = query.lowercase(Locale.US)
        var count = 0
        activePane().directory.walkTopDown().maxDepth(8).forEach { file ->
            if (count >= 80) return@forEach
            if (file.name.lowercase(Locale.US).contains(pattern)) {
                out.append(file.absolutePath).append('\n')
                count++
            }
        }
        showOutput("FIND", if (out.isEmpty()) "No matches for $query" else out.toString())
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) throw IllegalStateException("Could not create ${dst.absolutePath}")
            src.listFiles()?.forEach { child -> copyRecursively(child, File(dst, child.name)) }
        } else {
            dst.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun moveToTrash(file: File): Boolean {
        val parent = file.parentFile ?: return false
        val trash = File(parent, TRASH_DIR_NAME)
        if (!trash.exists() && !trash.mkdirs()) return false
        var target = File(trash, file.name)
        var suffix = 1
        while (target.exists()) {
            target = File(trash, file.name + "." + suffix)
            suffix++
        }
        return file.renameTo(target)
    }

    private fun showOutput(title: String, message: CharSequence) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun helpText(): String {
        return "Commands:\n" +
            "cd [folder]\n" +
            "ls | refresh\n" +
            "pwd\n" +
            "find [pattern]\n" +
            "preview [file]\n" +
            "edit [text file]\n" +
            "open [file]\n" +
            "share [file]\n" +
            "mkdir [folder]\n" +
            "cp [source] [destination]\n" +
            "mv [source] [destination]\n" +
            "rm [file]\n" +
            "\nKeyboard:\n" +
            "arrows move/open, Enter opens selected, Tab/Left switches pane"
    }

    private fun updateHint() {
        hintView?.text = "Recent changes"
    }

    private fun updateRecentFiles() {
        val host = recentRowsView ?: return
        host.removeAllViews()
        val files = recentFiles(activePane().directory)
        if (files.isEmpty()) {
            host.addView(label("No recent files here", max(10, outputTextSizeSp - 2), false), LinearLayout.LayoutParams(-1, dp(22)))
            return
        }
        for (file in files) {
            host.addView(recentRow(file), LinearLayout.LayoutParams(-1, dp(22)))
        }
    }

    private fun recentRow(file: File): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), 0, dp(2), 0)
        row.setOnClickListener { previewFile(file) }
        row.setOnLongClickListener {
            showItemMenu(file)
            true
        }
        row.addView(nameCell(FileEntry(file, file.name, false), false), LinearLayout.LayoutParams(0, -1, 1.6f))
        row.addView(cell(dateText(file), 0.6f, Gravity.CENTER, false, fileTextColor))
        row.addView(cell(timeText(file), 0.45f, Gravity.END, false, fileTextColor))
        return row
    }

    private fun recentFiles(root: File): List<File> {
        val out = ArrayList<File>()
        var scanned = 0
        // ponytail: shallow scan; add MediaStore/indexing when users need global recents.
        fun scan(dir: File, depth: Int) {
            if (depth < 0 || scanned >= RECENT_SCAN_LIMIT) return
            val children = try {
                dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: return
            for (child in children) {
                if (scanned++ >= RECENT_SCAN_LIMIT || child.name == TRASH_DIR_NAME) return
                if (child.isFile) out.add(child) else if (child.isDirectory) scan(child, depth - 1)
            }
        }
        scan(root, 3)
        return out.sortedByDescending { it.lastModified() }.take(RECENT_FILE_LIMIT)
    }

    private fun selectedFile(): File? {
        val entry = activePane().rows.getOrNull(activePane().cursor) ?: return null
        return entry.file
    }

    private fun selectedCommandPath(): String {
        return quoteIfNeeded(selectedFile()?.absolutePath ?: "")
    }

    private fun resolveArg(parts: List<String>, index: Int): File? {
        return parts.getOrNull(index)?.let { resolvePath(it) }
    }

    private fun resolvePath(path: String): File {
        val clean = path.trim()
        val raw = if (clean.startsWith("~")) homeDirectory().absolutePath + clean.drop(1) else clean
        val file = File(raw)
        return if (file.isAbsolute) file.absoluteFile else File(activePane().directory, raw).absoluteFile
    }

    private fun splitCommand(command: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escape = false
        for (ch in command) {
            if (escape) {
                current.append(ch)
                escape = false
            } else if (ch == '\\') {
                escape = true
            } else if (quote != null) {
                if (ch == quote) quote = null else current.append(ch)
            } else if (ch == '\'' || ch == '"') {
                quote = ch
            } else if (ch.isWhitespace()) {
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.setLength(0)
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    private fun pane(panel: Panel): PaneState = if (panel == Panel.LEFT) leftPane else rightPane
    private fun activePane(): PaneState = pane(activePanel)
    private fun otherPanel(): Panel = if (activePanel == Panel.LEFT) Panel.RIGHT else Panel.LEFT
    private fun otherPane(): PaneState = pane(otherPanel())
    private fun panelFor(pane: PaneState): Panel = if (pane === leftPane) Panel.LEFT else Panel.RIGHT

    private fun addTopButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val view = label(text, 16, true)
        view.gravity = Gravity.CENTER
        view.setTextColor(moduleButtonTextColor)
        view.setPadding(dp(2), 0, dp(2), 0)
        view.background = panelDrawable(PanelRole.MODULE)
        view.setOnClickListener { action() }
        view.translationY = -dp(topTabOverlapDp).toFloat()
        val params = LinearLayout.LayoutParams(dp(32), dp(28))
        params.leftMargin = dp(8)
        parent.addView(view, params)
    }

    private fun addAction(parent: LinearLayout?, text: String, action: () -> Unit) {
        if (parent == null) return
        val view = label(text, max(9, inputFontSizeSp - 4), true)
        view.gravity = Gravity.CENTER
        view.setTextColor(moduleButtonTextColor)
        view.background = functionButtonBackground()
        view.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(2), dp(1), dp(2), dp(1))
        parent.addView(view, params)
    }

    private fun addKey(parent: LinearLayout?, text: String, action: () -> Unit) {
        if (parent == null) return
        val view = label(text, max(9, inputFontSizeSp - 4), true)
        view.gravity = Gravity.CENTER
        view.setTextColor(moduleButtonTextColor)
        view.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(2), dp(1), dp(2), dp(1))
        parent.addView(view, params)
    }

    private fun label(text: String?, sizeSp: Int, bold: Boolean): TextView {
        val view = TextView(this)
        view.text = text ?: ""
        view.setTextColor(outputTextColor)
        view.setTextSize(sizeSp.toFloat())
        view.typeface = appTypeface ?: Typeface.MONOSPACE
        if (bold) view.setTypeface(view.typeface, Typeface.BOLD)
        view.includeFontPadding = false
        view.isSingleLine = true
        return view
    }

    private fun column(text: String, weight: Float, gravity: Int): TextView {
        val view = label(text, max(10, outputTextSizeSp - 2), true)
        view.gravity = gravity or Gravity.CENTER_VERTICAL
        view.setPadding(dp(2), 0, dp(2), 0)
        view.setTextColor(moduleTextColor)
        view.background = ColorDrawable(withAlpha(modulePanelColor, 210))
        view.layoutParams = LinearLayout.LayoutParams(0, -1, weight)
        return view
    }

    private fun cell(text: String, weight: Float, gravity: Int, bold: Boolean, color: Int = outputTextColor): TextView {
        val view = label(text, max(10, outputTextSizeSp - 2), bold)
        view.setTextColor(color)
        view.gravity = gravity or Gravity.CENTER_VERTICAL
        view.setPadding(dp(2), 0, dp(2), 0)
        view.ellipsize = TextUtils.TruncateAt.END
        view.layoutParams = LinearLayout.LayoutParams(0, -1, weight)
        return view
    }

    private fun rowTextColor(selected: Boolean, directory: Boolean): Int {
        if (selected) return selectionTextColor
        return if (directory) directoryTextColor else fileTextColor
    }

    private fun sizeText(file: File?): String {
        if (file == null || file.isDirectory) return "UP--DIR"
        return humanSize(file.length())
    }

    private fun dateText(file: File?): String {
        if (file == null) return ""
        return dateFormat.format(file.lastModified()).uppercase(Locale.US)
    }

    private fun timeText(file: File?): String {
        if (file == null) return ""
        return timeFormat.format(file.lastModified())
    }

    private fun diskSummary(root: File): String {
        val total = root.totalSpace
        if (total <= 0) return ""
        val used = total - root.freeSpace
        val free = root.freeSpace
        return "FREE " + progressBar(free, total) + " " + humanSize(free) + " / " + humanSize(total)
    }

    private fun progressBar(value: Long, total: Long): String {
        val width = 10
        val filled = if (total <= 0) 0 else ((value.toDouble() / total) * width).toInt().coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }

    private fun nameCell(entry: FileEntry, selected: Boolean): View {
        val color = rowTextColor(selected, entry.isDirectory)
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.HORIZONTAL
        wrap.gravity = Gravity.CENTER_VERTICAL
        wrap.setPadding(dp(2), 0, dp(2), 0)

        val icon = ImageView(this)
        icon.setImageResource(fileIcon(entry.file, entry.isDirectory, entry.isParent))
        icon.setColorFilter(color)
        wrap.addView(icon, LinearLayout.LayoutParams(dp(15), dp(15)))

        val text = label(if (entry.isParent) ".." else entry.label.take(24), max(10, outputTextSizeSp - 2), entry.isDirectory)
        text.setTextColor(color)
        text.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        text.setPadding(dp(5), 0, 0, 0)
        text.ellipsize = TextUtils.TruncateAt.END
        wrap.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        return wrap
    }

    private fun fileIcon(file: File?, directory: Boolean, parent: Boolean = false): Int {
        if (parent) return R.drawable.ic_fm_parent
        if (directory) return R.drawable.ic_fm_folder
        val ext = file?.name?.lowercase(Locale.US)?.substringAfterLast('.', "") ?: ""
        return when (ext) {
            "xml", "html", "css", "js", "json", "kt", "java" -> R.drawable.ic_fm_code
            "txt", "md", "log", "yaml", "yml" -> R.drawable.ic_fm_text
            "png", "jpg", "jpeg", "gif", "webp" -> R.drawable.ic_fm_image
            "zip", "apk", "jar", "tar", "gz" -> R.drawable.ic_fm_archive
            "pdf", "doc", "docx", "xls", "xlsx" -> R.drawable.ic_fm_document
            else -> R.drawable.ic_fm_file
        }
    }

    private fun humanSize(size: Long): String {
        val units = arrayOf("B", "K", "M", "G", "T")
        var value = size.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) size.toString() else String.format(Locale.US, "%.1f%s", value, units[unit])
    }

    private fun abbreviatePath(path: String): String {
        return if (path.length <= 42) path else "..." + path.takeLast(39)
    }

    private fun isLikelyText(file: File): Boolean {
        val name = file.name.lowercase(Locale.US)
        val ext = name.substringAfterLast('.', "")
        return ext in setOf("txt", "md", "json", "csv", "xml", "html", "css", "js", "kt", "java", "log", "yaml", "yml", "ignore", "gitignore") ||
            file.name.startsWith(".") && file.length() <= PREVIEW_MAX_BYTES
    }

    private fun uriFor(file: File): Uri? {
        return try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            showOutput("FILE", "Could not share file URI:\n${e.message}")
            null
        }
    }

    private fun mimeFor(file: File): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US)) ?: "*/*"
    }

    private fun seed(value: String) {
        inputView?.setText(value)
        inputView?.setSelection(inputView?.text?.length ?: 0)
        refocusInput()
    }

    private fun refocusInput() {
        inputView?.postDelayed({
            inputView?.requestFocus()
            inputView?.setSelection(inputView?.text?.length ?: 0)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }, 40)
    }

    private fun homeDirectory(): File {
        val external = Environment.getExternalStorageDirectory()
        return if (external.exists()) external else File("/")
    }

    private fun resolveStartDirectory(intent: Intent?): File {
        val raw = intent?.getStringExtra(EXTRA_PATH)
        val target = if (raw.isNullOrBlank()) homeDirectory() else File(raw)
        return if (target.exists() && target.isDirectory) target.absoluteFile else homeDirectory()
    }

    private fun ensureStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7)
        }
    }

    private fun applyWallpaperBackground(root: View) {
        if (!terminalBackgroundImage.isNullOrBlank()) {
            val drawable = android.graphics.drawable.Drawable.createFromPath(terminalBackgroundImage)
            if (drawable != null) {
                root.background = drawable
                return
            }
        }
        root.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applyThemeExtras(intent: Intent?) {
        val prefs = themePrefs()
        applyStoredTheme(prefs)
        applyThemePayload(intent)
        if (hasThemePayload(intent)) saveThemePayload(prefs, intent)
        appTypeface = resolveTypeface(intent)
    }

    private fun putThemeExtras(intent: Intent) {
        intent.putExtra(EXTRA_THEME_BG, bgColor)
        intent.putExtra(EXTRA_TERMINAL_BG, panelColor)
        intent.putExtra(EXTRA_THEME_TEXT, textColor)
        intent.putExtra(EXTRA_THEME_BORDER, borderColor)
        intent.putExtra(EXTRA_MODULE_BG_COLOR, modulePanelColor)
        intent.putExtra(EXTRA_MODULE_TEXT_COLOR, moduleTextColor)
        intent.putExtra(EXTRA_MODULE_BORDER_COLOR, moduleBorderColor)
        intent.putExtra(EXTRA_MODULE_HEADER_BG_COLOR, headerPanelColor)
        intent.putExtra(EXTRA_MODULE_HEADER_TEXT_COLOR, headerTextColor)
        intent.putExtra(EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor)
        intent.putExtra(EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleButtonTextColor)
        intent.putExtra(EXTRA_MODULE_BUTTON_BORDER_COLOR, moduleButtonBorderColor)
        intent.putExtra(EXTRA_OUTPUT_BG_COLOR, outputPanelColor)
        intent.putExtra(EXTRA_OUTPUT_TEXT_COLOR, outputTextColor)
        intent.putExtra(EXTRA_OUTPUT_BORDER_COLOR, outputBorderColor)
        intent.putExtra(EXTRA_FM_PANEL_BG_COLOR, outputPanelColor)
        intent.putExtra(EXTRA_FM_BORDER_COLOR, outputBorderColor)
        intent.putExtra(EXTRA_FM_TEXT_COLOR, fileTextColor)
        intent.putExtra(EXTRA_FM_DIRECTORY_TEXT_COLOR, directoryTextColor)
        intent.putExtra(EXTRA_FM_SELECTION_BG_COLOR, selectionBgColor)
        intent.putExtra(EXTRA_FM_SELECTION_TEXT_COLOR, selectionTextColor)
        intent.putExtra(EXTRA_FM_HEADER_BG_COLOR, headerPanelColor)
        intent.putExtra(EXTRA_FM_HEADER_TEXT_COLOR, headerTextColor)
        intent.putExtra(EXTRA_FM_BUTTON_BG_COLOR, moduleButtonBgColor)
        intent.putExtra(EXTRA_FM_BUTTON_TEXT_COLOR, moduleButtonTextColor)
        intent.putExtra(EXTRA_FM_BUTTON_BORDER_COLOR, moduleButtonBorderColor)
        intent.putExtra(EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp)
        intent.putExtra(EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp)
        intent.putExtra(EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        intent.putExtra(EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        intent.putExtra(EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        intent.putExtra(EXTRA_TERMINAL_BG_IMAGE, terminalBackgroundImage)
        intent.putExtra(EXTRA_CYBERDECK_MODE, cyberdeckMode)
        intent.putExtra(EXTRA_FONT_PATH, getIntent()?.getStringExtra(EXTRA_FONT_PATH))
        intent.putExtra(EXTRA_FONT_NAME, getIntent()?.getStringExtra(EXTRA_FONT_NAME))
    }

    private fun intExtra(intent: Intent?, key: String, fallback: Int, vararg aliases: String): Int {
        val extras = intent?.extras ?: return fallback
        val keys = arrayOf(key, *aliases)
        for (candidate in keys) {
            val value = extras.get(candidate) ?: continue
            if (value is Number) return value.toInt()
            value.toString().toIntOrNull()?.let { return it }
        }
        return fallback
    }

    private fun booleanExtra(intent: Intent?, key: String, fallback: Boolean, vararg aliases: String): Boolean {
        val extras = intent?.extras ?: return fallback
        val keys = arrayOf(key, *aliases)
        for (candidate in keys) {
            val value = extras.get(candidate) ?: continue
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value.toString().equals("true", true) || value.toString() == "1"
            }
        }
        return fallback
    }

    private fun applyStoredTheme(prefs: SharedPreferences) {
        bgColor = prefs.getInt(EXTRA_THEME_BG, bgColor)
        panelColor = prefs.getInt(EXTRA_TERMINAL_BG, panelColor)
        textColor = prefs.getInt(EXTRA_THEME_TEXT, textColor)
        borderColor = prefs.getInt(EXTRA_THEME_BORDER, borderColor)
        modulePanelColor = prefs.getInt(EXTRA_MODULE_BG_COLOR, modulePanelColor)
        moduleTextColor = prefs.getInt(EXTRA_MODULE_TEXT_COLOR, moduleTextColor)
        moduleBorderColor = prefs.getInt(EXTRA_MODULE_BORDER_COLOR, moduleBorderColor)
        headerPanelColor = prefs.getInt(EXTRA_MODULE_HEADER_BG_COLOR, headerPanelColor)
        headerTextColor = prefs.getInt(EXTRA_MODULE_HEADER_TEXT_COLOR, headerTextColor)
        moduleButtonBgColor = prefs.getInt(EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor)
        moduleButtonTextColor = prefs.getInt(EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleButtonTextColor)
        moduleButtonBorderColor = prefs.getInt(EXTRA_MODULE_BUTTON_BORDER_COLOR, moduleButtonBorderColor)
        inputBgColor = prefs.getInt(EXTRA_INPUT_BG_COLOR, inputBgColor)
        inputTextColor = prefs.getInt(EXTRA_INPUT_TEXT_COLOR, inputTextColor)
        outputPanelColor = prefs.getInt(EXTRA_OUTPUT_BG_COLOR, outputPanelColor)
        outputTextColor = prefs.getInt(EXTRA_OUTPUT_TEXT_COLOR, outputTextColor)
        outputBorderColor = prefs.getInt(EXTRA_OUTPUT_BORDER_COLOR, outputBorderColor)
        fileTextColor = prefs.getInt(EXTRA_FM_TEXT_COLOR, fileTextColor)
        directoryTextColor = prefs.getInt(EXTRA_FM_DIRECTORY_TEXT_COLOR, directoryTextColor)
        selectionBgColor = prefs.getInt(EXTRA_FM_SELECTION_BG_COLOR, selectionBgColor)
        selectionTextColor = prefs.getInt(EXTRA_FM_SELECTION_TEXT_COLOR, selectionTextColor)
        headerTextSizeSp = prefs.getInt(EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp)
        outputTextSizeSp = prefs.getInt(EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp)
        inputFontSizeSp = prefs.getInt(EXTRA_INPUT_FONT_SIZE, inputFontSizeSp)
        moduleCornerRadiusDp = prefs.getInt(EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        outputCornerRadiusDp = prefs.getInt(EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        headerCornerRadiusDp = prefs.getInt(EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        topMarginDp = prefs.getInt(EXTRA_TOP_MARGIN, topMarginDp)
        terminalBackgroundImage = prefs.getString(EXTRA_TERMINAL_BG_IMAGE, terminalBackgroundImage)
        cyberdeckMode = prefs.getBoolean(EXTRA_CYBERDECK_MODE, cyberdeckMode)
        applyDisplayMarginsString(prefs.getString(EXTRA_DISPLAY_MARGIN_TOP_SECTION, null))
    }

    private fun applyThemePayload(intent: Intent?) {
        bgColor = FmVisualInterop.readColorExtra(intent, bgColor, EXTRA_THEME_BG)
        panelColor = FmVisualInterop.readColorExtra(intent, panelColor, EXTRA_TERMINAL_BG, "terminal_window_background_color")
        textColor = FmVisualInterop.readColorExtra(intent, textColor, EXTRA_THEME_TEXT)
        borderColor = FmVisualInterop.readColorExtra(intent, borderColor, EXTRA_THEME_BORDER)
        modulePanelColor = FmVisualInterop.readColorExtra(intent, modulePanelColor, EXTRA_MODULE_BG_COLOR, "terminal_window_background_color")
        moduleTextColor = FmVisualInterop.readColorExtra(intent, moduleTextColor, EXTRA_MODULE_TEXT_COLOR)
        moduleBorderColor = FmVisualInterop.readColorExtra(intent, moduleBorderColor, EXTRA_MODULE_BORDER_COLOR)
        headerPanelColor = FmVisualInterop.readColorExtra(intent, headerPanelColor, EXTRA_MODULE_HEADER_BG_COLOR)
        headerTextColor = FmVisualInterop.readColorExtra(intent, headerTextColor, EXTRA_MODULE_HEADER_TEXT_COLOR)
        moduleButtonBgColor = FmVisualInterop.readColorExtra(intent, moduleButtonBgColor, EXTRA_MODULE_BUTTON_BG_COLOR, "module_button_background_color")
        moduleButtonTextColor = FmVisualInterop.readColorExtra(intent, moduleButtonTextColor, EXTRA_MODULE_BUTTON_TEXT_COLOR)
        moduleButtonBorderColor = FmVisualInterop.readColorExtra(intent, moduleButtonBorderColor, EXTRA_MODULE_BUTTON_BORDER_COLOR)
        inputBgColor = FmVisualInterop.readColorExtra(intent, inputBgColor, EXTRA_INPUT_BG_COLOR, "input_background_color")
        inputTextColor = FmVisualInterop.readColorExtra(intent, inputTextColor, EXTRA_INPUT_TEXT_COLOR)
        outputPanelColor = FmVisualInterop.readColorExtra(intent, outputPanelColor, EXTRA_OUTPUT_BG_COLOR, "output_background_color")
        outputTextColor = FmVisualInterop.readColorExtra(intent, outputTextColor, EXTRA_OUTPUT_TEXT_COLOR)
        outputBorderColor = FmVisualInterop.readColorExtra(intent, outputBorderColor, EXTRA_OUTPUT_BORDER_COLOR)
        outputPanelColor = FmVisualInterop.readColorExtra(intent, outputPanelColor, EXTRA_FM_PANEL_BG_COLOR)
        outputBorderColor = FmVisualInterop.readColorExtra(intent, outputBorderColor, EXTRA_FM_BORDER_COLOR)
        fileTextColor = FmVisualInterop.readColorExtra(intent, fileTextColor, EXTRA_FM_TEXT_COLOR)
        directoryTextColor = FmVisualInterop.readColorExtra(intent, directoryTextColor, EXTRA_FM_DIRECTORY_TEXT_COLOR)
        selectionBgColor = FmVisualInterop.readColorExtra(intent, selectionBgColor, EXTRA_FM_SELECTION_BG_COLOR)
        selectionTextColor = FmVisualInterop.readColorExtra(intent, selectionTextColor, EXTRA_FM_SELECTION_TEXT_COLOR)
        headerPanelColor = FmVisualInterop.readColorExtra(intent, headerPanelColor, EXTRA_FM_HEADER_BG_COLOR)
        headerTextColor = FmVisualInterop.readColorExtra(intent, headerTextColor, EXTRA_FM_HEADER_TEXT_COLOR)
        moduleButtonBgColor = FmVisualInterop.readColorExtra(intent, moduleButtonBgColor, EXTRA_FM_BUTTON_BG_COLOR)
        moduleButtonTextColor = FmVisualInterop.readColorExtra(intent, moduleButtonTextColor, EXTRA_FM_BUTTON_TEXT_COLOR)
        moduleButtonBorderColor = FmVisualInterop.readColorExtra(intent, moduleButtonBorderColor, EXTRA_FM_BUTTON_BORDER_COLOR)
        headerTextSizeSp = intExtra(intent, EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp, "module_header_text_size")
        outputTextSizeSp = intExtra(intent, EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp, "module_body_text_size")
        inputFontSizeSp = intExtra(intent, EXTRA_INPUT_FONT_SIZE, inputFontSizeSp)
        moduleCornerRadiusDp = intExtra(intent, EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        outputCornerRadiusDp = intExtra(intent, EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        headerCornerRadiusDp = intExtra(intent, EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        topMarginDp = intExtra(intent, EXTRA_TOP_MARGIN, topMarginDp)
        applyLauncherDisplayMargins(intent)
        cyberdeckMode = booleanExtra(intent, EXTRA_CYBERDECK_MODE, cyberdeckMode, "enable_cyberdeck_mode")
        intent?.getStringExtra(EXTRA_TERMINAL_BG_IMAGE)?.let { terminalBackgroundImage = it }
    }

    private fun saveThemePayload(prefs: SharedPreferences, intent: Intent?) {
        val editor = prefs.edit()
        editor.putInt(EXTRA_THEME_BG, bgColor)
        editor.putInt(EXTRA_TERMINAL_BG, panelColor)
        editor.putInt(EXTRA_THEME_TEXT, textColor)
        editor.putInt(EXTRA_THEME_BORDER, borderColor)
        editor.putInt(EXTRA_MODULE_BG_COLOR, modulePanelColor)
        editor.putInt(EXTRA_MODULE_TEXT_COLOR, moduleTextColor)
        editor.putInt(EXTRA_MODULE_BORDER_COLOR, moduleBorderColor)
        editor.putInt(EXTRA_MODULE_HEADER_BG_COLOR, headerPanelColor)
        editor.putInt(EXTRA_MODULE_HEADER_TEXT_COLOR, headerTextColor)
        editor.putInt(EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor)
        editor.putInt(EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleButtonTextColor)
        editor.putInt(EXTRA_MODULE_BUTTON_BORDER_COLOR, moduleButtonBorderColor)
        editor.putInt(EXTRA_INPUT_BG_COLOR, inputBgColor)
        editor.putInt(EXTRA_INPUT_TEXT_COLOR, inputTextColor)
        editor.putInt(EXTRA_OUTPUT_BG_COLOR, outputPanelColor)
        editor.putInt(EXTRA_OUTPUT_TEXT_COLOR, outputTextColor)
        editor.putInt(EXTRA_OUTPUT_BORDER_COLOR, outputBorderColor)
        editor.putInt(EXTRA_FM_TEXT_COLOR, fileTextColor)
        editor.putInt(EXTRA_FM_DIRECTORY_TEXT_COLOR, directoryTextColor)
        editor.putInt(EXTRA_FM_SELECTION_BG_COLOR, selectionBgColor)
        editor.putInt(EXTRA_FM_SELECTION_TEXT_COLOR, selectionTextColor)
        editor.putInt(EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp)
        editor.putInt(EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp)
        editor.putInt(EXTRA_INPUT_FONT_SIZE, inputFontSizeSp)
        editor.putInt(EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        editor.putInt(EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        editor.putInt(EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        editor.putInt(EXTRA_TOP_MARGIN, topMarginDp)
        editor.putBoolean(EXTRA_CYBERDECK_MODE, cyberdeckMode)
        editor.putString(EXTRA_TERMINAL_BG_IMAGE, terminalBackgroundImage)
        editor.putString(EXTRA_FONT_PATH, intent?.getStringExtra(EXTRA_FONT_PATH))
        editor.putString(EXTRA_FONT_NAME, intent?.getStringExtra(EXTRA_FONT_NAME))
        displayMarginsString(intent)?.let { editor.putString(EXTRA_DISPLAY_MARGIN_TOP_SECTION, it) }
        editor.apply()
    }

    private fun hasThemePayload(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        for (key in THEME_PAYLOAD_KEYS) {
            if (extras.containsKey(key)) return true
        }
        return false
    }

    private fun themePrefs(): SharedPreferences {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private fun applyLauncherDisplayMargins(intent: Intent?) {
        applyDisplayMarginsString(displayMarginsString(intent))
    }

    private fun displayMarginsString(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        return extras.getString(EXTRA_DISPLAY_MARGIN_TOP_SECTION)
            ?: extras.getString(EXTRA_DISPLAY_MARGIN_MM)
    }

    private fun applyDisplayMarginsString(raw: String?) {
        if (raw == null) return
        val values = raw
            .split(',', ';', ' ')
            .mapNotNull { it.trim().takeIf { token -> token.isNotEmpty() }?.toFloatOrNull() }
        if (values.size < 4) return

        displayMarginLeftPx = mmToPx(values[0])
        displayMarginTopPx = mmToPx(values[1])
        displayMarginRightPx = mmToPx(values[2])
        displayMarginBottomPx = mmToPx(values[3])
    }

    private fun mmToPx(mm: Float): Int {
        val px = mm * resources.displayMetrics.xdpi / 25.4f
        return max(0, (px + 0.5f).toInt())
    }

    private fun resolveTypeface(intent: Intent?): Typeface {
        val path = intent?.getStringExtra(EXTRA_FONT_PATH)
        if (!path.isNullOrBlank()) {
            try {
                return Typeface.createFromFile(path)
            } catch (_: Exception) {
            }
        }
        val name = intent?.getStringExtra(EXTRA_FONT_NAME)
        if (!name.isNullOrBlank()) {
            if (name.equals("system", true)) return Typeface.DEFAULT
            if (name.equals("lucida_console", true)) {
                try {
                    return Typeface.createFromAsset(assets, "lucida_console.ttf")
                } catch (_: Exception) {
                }
            }
            return Typeface.create(name, Typeface.NORMAL)
        }
        return Typeface.MONOSPACE
    }

    private fun panelDrawable(role: PanelRole): Drawable {
        val fill = when (role) {
            PanelRole.OUTER -> withAlpha(outputPanelColor, 226)
            PanelRole.HEADER -> headerPanelColor
            PanelRole.INPUT -> inputBgColor
            PanelRole.MODULE -> modulePanelColor
            PanelRole.OUTPUT -> withAlpha(outputPanelColor, 238)
        }
        val stroke = when (role) {
            PanelRole.OUTER -> outputBorderColor
            PanelRole.HEADER -> borderColor
            PanelRole.INPUT -> outputBorderColor
            PanelRole.MODULE -> moduleBorderColor
            PanelRole.OUTPUT -> outputBorderColor
        }
        val radius = when (role) {
            PanelRole.OUTER -> outputCornerRadiusDp
            PanelRole.HEADER -> headerCornerRadiusDp
            PanelRole.OUTPUT -> outputCornerRadiusDp
            else -> moduleCornerRadiusDp
        }
        if (cyberdeckMode) {
            return CyberPanelDrawable(fill, stroke, max(1f, dpFloat(if (role == PanelRole.MODULE || role == PanelRole.INPUT) 1f else 1.25f)), role != PanelRole.MODULE && role != PanelRole.INPUT)
        }
        val drawable = GradientDrawable()
        drawable.setColor(fill)
        drawable.setStroke(max(1, dp(1)), stroke)
        drawable.cornerRadius = dp(radius).toFloat()
        return drawable
    }

    private fun buttonDrawable(): Drawable {
        if (cyberdeckMode) {
            return CyberPanelDrawable(withAlpha(moduleButtonBgColor, 205), moduleButtonBorderColor, max(1f, dpFloat(1f)), false)
        }
        val drawable = GradientDrawable()
        drawable.setColor(withAlpha(moduleButtonBgColor, 205))
        drawable.setStroke(max(1, dp(1)), moduleButtonBorderColor)
        drawable.cornerRadius = dp(moduleCornerRadiusDp).toFloat()
        return drawable
    }

    private fun functionButtonBackground(): Drawable {
        return ColorDrawable(withAlpha(moduleButtonBgColor, 210))
    }

    private fun rowSelectionBackground(selected: Boolean): Drawable {
        return ColorDrawable(if (selected) withAlpha(selectionBgColor, 230) else Color.TRANSPARENT)
    }

    private fun quoteIfNeeded(path: String): String {
        if (!path.any { it.isWhitespace() || it == '\'' || it == '"' }) return path
        return "\"" + path.replace("\"", "\\\"") + "\""
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        if (max < min) return min
        return kotlin.math.max(min, kotlin.math.min(max, value))
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(clamp(alpha, 0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun dpFloat(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private enum class PanelRole { OUTER, HEADER, OUTPUT, MODULE, INPUT }

    private class CyberPanelDrawable(
        fillColor: Int,
        borderColor: Int,
        strokeWidthPx: Float,
        private val notch: Boolean
    ) : Drawable() {
        private val strokeWidthPx = max(1f, strokeWidthPx)
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@CyberPanelDrawable.strokeWidthPx
            strokeJoin = Paint.Join.MITER
            color = borderColor
        }
        private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, this@CyberPanelDrawable.strokeWidthPx / 2f)
            color = withAlphaComponent(borderColor, 95)
        }
        private val path = Path()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val left = b.left.toFloat()
            val top = b.top.toFloat()
            val right = b.right.toFloat()
            val bottom = b.bottom.toFloat()
            val width = b.width().toFloat()
            val height = b.height().toFloat()
            val cornerCut = min(min(max(8f, height * 0.34f), width * 0.18f), max(20f, strokeWidthPx * 8f))
            val notchDepth = if (notch) min(min(max(8f, height * 0.22f), width * 0.16f), max(12f, strokeWidthPx * 6f)) else 0f
            val notchHalfHeight = min(max(1.5f, height * 0.04f), 4f)
            val notchCenter = top + height * 0.52f

            path.reset()
            path.moveTo(left, top)
            path.lineTo(right, top)
            path.lineTo(right, bottom - cornerCut)
            path.lineTo(right - cornerCut, bottom)
            path.lineTo(left, bottom)
            if (notch) {
                path.lineTo(left, notchCenter + notchHalfHeight)
                path.lineTo(left + notchDepth, notchCenter + notchHalfHeight)
                path.lineTo(left + notchDepth, notchCenter - notchHalfHeight)
                path.lineTo(left, notchCenter - notchHalfHeight)
            }
            path.close()

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
            drawDetails(canvas, left, top, right, bottom, width, height, cornerCut, notchDepth)
        }

        private fun drawDetails(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            width: Float,
            height: Float,
            cornerCut: Float,
            notchDepth: Float
        ) {
            if (width < 56f || height < 34f) return
            val inset = max(3f, strokeWidthPx * 2.2f)
            val topRailY = top + inset
            val bottomRailY = bottom - inset
            val leftRailStart = left + notchDepth + inset
            val leftRailEnd = min(right - cornerCut - inset, left + width * 0.42f)
            if (leftRailEnd > leftRailStart + 8f) canvas.drawLine(leftRailStart, topRailY, leftRailEnd, topRailY, detailPaint)
            val rightRailStart = max(left + width * 0.68f, right - cornerCut - width * 0.18f)
            val rightRailEnd = right - inset
            if (rightRailEnd > rightRailStart + 8f) canvas.drawLine(rightRailStart, topRailY, rightRailEnd, topRailY, detailPaint)
            if (height >= 34f) {
                val verticalX = right - inset
                canvas.drawLine(verticalX, top + height * 0.18f, verticalX, bottom - cornerCut - inset, detailPaint)
                canvas.drawLine(left + inset, bottomRailY, left + min(width * 0.22f, 76f), bottomRailY, detailPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
            detailPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            detailPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private fun withAlphaComponent(color: Int, alpha: Int): Int {
            return Color.argb(max(0, min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_THEME_BG = "theme_bg"
        const val EXTRA_TERMINAL_BG = "terminal_bg"
        const val EXTRA_THEME_TEXT = "theme_text"
        const val EXTRA_THEME_BORDER = "theme_border"
        const val EXTRA_MODULE_BG_COLOR = "module_bg_color"
        const val EXTRA_MODULE_TEXT_COLOR = "module_text_color"
        const val EXTRA_MODULE_BORDER_COLOR = "module_border_color"
        const val EXTRA_MODULE_HEADER_BG_COLOR = "module_header_bg_color"
        const val EXTRA_MODULE_HEADER_TEXT_COLOR = "module_header_text_color"
        const val EXTRA_MODULE_BUTTON_BG_COLOR = "module_button_bg_color"
        const val EXTRA_MODULE_BUTTON_TEXT_COLOR = "module_button_text_color"
        const val EXTRA_MODULE_BUTTON_BORDER_COLOR = "module_button_border_color"
        const val EXTRA_INPUT_BG_COLOR = "input_bg_color"
        const val EXTRA_INPUT_TEXT_COLOR = "input_text_color"
        const val EXTRA_OUTPUT_BG_COLOR = "output_bg_color"
        const val EXTRA_OUTPUT_TEXT_COLOR = "output_text_color"
        const val EXTRA_OUTPUT_BORDER_COLOR = "output_border_color"
        const val EXTRA_FM_PANEL_BG_COLOR = "fm_panel_background_color"
        const val EXTRA_FM_BORDER_COLOR = "fm_border_color"
        const val EXTRA_FM_TEXT_COLOR = "fm_text_color"
        const val EXTRA_FM_DIRECTORY_TEXT_COLOR = "fm_directory_text_color"
        const val EXTRA_FM_SELECTION_BG_COLOR = "fm_selection_background_color"
        const val EXTRA_FM_SELECTION_TEXT_COLOR = "fm_selection_text_color"
        const val EXTRA_FM_HEADER_BG_COLOR = "fm_header_background_color"
        const val EXTRA_FM_HEADER_TEXT_COLOR = "fm_header_text_color"
        const val EXTRA_FM_BUTTON_BG_COLOR = "fm_button_background_color"
        const val EXTRA_FM_BUTTON_TEXT_COLOR = "fm_button_text_color"
        const val EXTRA_FM_BUTTON_BORDER_COLOR = "fm_button_border_color"
        const val EXTRA_TOP_MARGIN = "top_margin"
        const val EXTRA_INPUT_FONT_SIZE = "input_font_size"
        const val EXTRA_DISPLAY_MARGIN_MM = "display_margin_mm"
        const val EXTRA_DISPLAY_MARGIN_TOP_SECTION = "display_margin_top_section"
        const val EXTRA_HEADER_TEXT_SIZE = "header_text_size"
        const val EXTRA_OUTPUT_TEXT_SIZE = "output_text_size"
        const val EXTRA_MODULE_CORNER_RADIUS = "module_corner_radius"
        const val EXTRA_OUTPUT_CORNER_RADIUS = "output_corner_radius"
        const val EXTRA_HEADER_CORNER_RADIUS = "header_corner_radius"
        const val EXTRA_FONT_PATH = "font_path"
        const val EXTRA_FONT_NAME = "font_name"
        const val EXTRA_TERMINAL_BG_IMAGE = "terminal_bg_image"
        const val EXTRA_CYBERDECK_MODE = "cyberdeck_mode"
        const val EXTRA_CRT_FILTER = "crt_filter"
        private const val PREFS_NAME = "retui_fm"
        private const val MAX_ROWS = 5000
        private const val PREVIEW_MAX_BYTES = 64 * 1024
        private const val RECENT_SCAN_LIMIT = 350
        private const val RECENT_FILE_LIMIT = 4
        private const val TRASH_DIR_NAME = ".retui-trash"
        private const val SPECIAL_KEY_PAGE_SWIPE_THRESHOLD_PX = 70f
        private val THEME_PAYLOAD_KEYS = arrayOf(
            EXTRA_THEME_BG,
            EXTRA_TERMINAL_BG,
            EXTRA_THEME_TEXT,
            EXTRA_THEME_BORDER,
            EXTRA_MODULE_BG_COLOR,
            EXTRA_MODULE_TEXT_COLOR,
            EXTRA_MODULE_BORDER_COLOR,
            EXTRA_MODULE_HEADER_BG_COLOR,
            EXTRA_MODULE_HEADER_TEXT_COLOR,
            EXTRA_MODULE_BUTTON_BG_COLOR,
            EXTRA_MODULE_BUTTON_TEXT_COLOR,
            EXTRA_MODULE_BUTTON_BORDER_COLOR,
            EXTRA_INPUT_BG_COLOR,
            EXTRA_INPUT_TEXT_COLOR,
            EXTRA_OUTPUT_BG_COLOR,
            EXTRA_OUTPUT_TEXT_COLOR,
            EXTRA_OUTPUT_BORDER_COLOR,
            EXTRA_FM_PANEL_BG_COLOR,
            EXTRA_FM_BORDER_COLOR,
            EXTRA_FM_TEXT_COLOR,
            EXTRA_FM_DIRECTORY_TEXT_COLOR,
            EXTRA_FM_SELECTION_BG_COLOR,
            EXTRA_FM_SELECTION_TEXT_COLOR,
            EXTRA_FM_HEADER_BG_COLOR,
            EXTRA_FM_HEADER_TEXT_COLOR,
            EXTRA_FM_BUTTON_BG_COLOR,
            EXTRA_FM_BUTTON_TEXT_COLOR,
            EXTRA_FM_BUTTON_BORDER_COLOR,
            EXTRA_TOP_MARGIN,
            EXTRA_INPUT_FONT_SIZE,
            EXTRA_DISPLAY_MARGIN_MM,
            EXTRA_DISPLAY_MARGIN_TOP_SECTION,
            EXTRA_HEADER_TEXT_SIZE,
            EXTRA_OUTPUT_TEXT_SIZE,
            EXTRA_MODULE_CORNER_RADIUS,
            EXTRA_OUTPUT_CORNER_RADIUS,
            EXTRA_HEADER_CORNER_RADIUS,
            EXTRA_FONT_PATH,
            EXTRA_FONT_NAME,
            EXTRA_TERMINAL_BG_IMAGE,
            EXTRA_CYBERDECK_MODE,
            "enable_cyberdeck_mode"
        )
    }
}
