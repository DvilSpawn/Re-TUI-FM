package com.dvil.retui.fm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentUris
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
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
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
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private enum class Panel { LEFT, RIGHT }
    private enum class Screen { HOME, TREE }

    private data class FileEntry(
        val file: File?,
        val label: String,
        val isDirectory: Boolean,
        val isParent: Boolean = false,
        val isSection: Boolean = false,
        val contentUri: Uri? = null
    )

    private data class FileCategory(
        val label: String,
        val glyph: String,
        val uri: Uri,
        val mimeTypes: Set<String> = emptySet()
    )

    private data class PaneState(
        var directory: File,
        val rows: ArrayList<FileEntry> = ArrayList(),
        var cursor: Int = 0,
        var summary: String = ""
    )

    private lateinit var leftPane: PaneState
    private lateinit var rightPane: PaneState
    private var activePanel = Panel.LEFT

    private var rootView: FrameLayout? = null
    private var mainContainer: LinearLayout? = null
    private var leftRowsView: LinearLayout? = null
    private var leftScrollView: ScrollView? = null
    private var rightGridView: GridView? = null
    private var rightGridAdapter: BaseAdapter? = null
    private var contentHost: FrameLayout? = null
    private var leftFooterView: TextView? = null
    private var rightFooterView: TextView? = null
    private var leftDiskView: TextView? = null
    private var rightDiskView: TextView? = null
    private var addressPathView: TextView? = null
    private var inputView: EditText? = null
    private var currentScreen = Screen.HOME
    private var rightVirtualTitle: String? = null
    private var categoryLoadVersion = 0
    private var homeCountVersion = 0
    private var findVersion = 0
    private val categoryCountCache = HashMap<String, Int>()
    private val categoryCountViews = HashMap<String, TextView>()

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
    private var crtFilter = false
    private var appFontPath: String? = null
    private var appFontName: String? = null
    private var appTypeface: Typeface? = Typeface.MONOSPACE
    private var iconTypeface: Typeface? = null
    private var firstResume = true

    private val topTabOverlapDp = 11
    private val filesUri = MediaStore.Files.getContentUri("external")
    private val fileCategories = listOf(
        FileCategory("Images", "", MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        FileCategory("Videos", "", MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
        FileCategory("Audio", "", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        FileCategory("Documents", "", filesUri, DOCUMENT_MIME_TYPES),
        FileCategory("APKs", "", filesUri, setOf("application/vnd.android.package-archive")),
        FileCategory("Archives", "", filesUri, ARCHIVE_MIME_TYPES)
    )

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
        if (shouldOpenTree(intent)) showTree(start) else showHome()
        handleIncomingCommand(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyThemeExtras(intent)
        val start = resolveStartDirectory(intent)
        leftPane = PaneState(start)
        rightPane = PaneState(start)
        setContentView(buildUi())
        if (shouldOpenTree(intent)) showTree(start) else showHome()
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
        if (currentScreen == Screen.HOME) {
            finish()
            return
        }
        if (rightPane.directory == homeDirectory() && leftPane.directory == homeDirectory()) {
            showHome()
            return
        }
        val parent = rightPane.directory.parentFile
        if (parent != null && rightPane.directory == leftPane.directory) {
            navigateMain(parent)
        } else if (parent != null) {
            showDirectoryContents(parent)
        } else {
            finish()
        }
    }

    private fun configureWindow() {
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        rootView = root
        root.isFocusableInTouchMode = true
        root.clipChildren = false
        root.clipToPadding = false
        root.setBackgroundColor(Color.TRANSPARENT)
        applyWallpaperBackground(root)
        applyCrtForeground(root)
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
        root.requestApplyInsets()

        main.addView(buildTopBar(), LinearLayout.LayoutParams(-1, dp(18)))
        main.addView(buildMenuBar(), LinearLayout.LayoutParams(-1, dp(30)))
        main.addView(buildAddressBar(), LinearLayout.LayoutParams(-1, dp(36)))
        contentHost = FrameLayout(this)
        val hostParams = LinearLayout.LayoutParams(-1, 0, 1f)
        hostParams.topMargin = dp(4)
        main.addView(contentHost, hostParams)
        return root
    }

    private fun buildTreePanes(): View {
        val panes = LinearLayout(this)
        panes.orientation = LinearLayout.HORIZONTAL
        val leftParams = LinearLayout.LayoutParams(0, -1, 0.38f)
        val rightParams = LinearLayout.LayoutParams(0, -1, 0.62f)
        rightParams.leftMargin = dp(4)
        panes.addView(buildPane(Panel.LEFT), leftParams)
        panes.addView(buildPane(Panel.RIGHT), rightParams)
        return panes
    }

    private fun installWindowInsetsHandler(root: FrameLayout) {
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

        return row
    }

    private fun buildMenuBar(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), 0, dp(8), 0)
        row.background = toolbarDrawable()
        addMenuButton(row, "File") { showFileMenu() }
        addMenuButton(row, "Edit") { showEditMenu() }
        addMenuButton(row, "View") { showViewMenu() }
        addMenuButton(row, "Go") { showGoMenu() }
        addMenuButton(row, "Places") { showPlacesMenu() }
        addMenuButton(row, "Help") { showHelpPopup() }
        return row
    }

    private fun buildAddressBar(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(3), dp(8), dp(3))
        row.background = toolbarDrawable()
        addToolbarIconButton(row, R.drawable.ic_fm_parent) { rightPane.directory.parentFile?.let { showDirectoryContents(it) } }
        addToolbarIconButton(row, R.drawable.ic_fm_home) { showHome() }
        addToolbarIconButton(row, R.drawable.ic_fm_folder_plus) { promptMkdir() }
        addToolbarIconButton(row, R.drawable.ic_fm_refresh) { reloadAll() }
        val address = LinearLayout(this)
        address.orientation = LinearLayout.HORIZONTAL
        address.gravity = Gravity.CENTER_VERTICAL
        address.setPadding(dp(8), 0, dp(8), 0)
        address.background = addressDrawable()
        val folder = ImageView(this)
        folder.setImageResource(R.drawable.ic_fm_folder)
        folder.setColorFilter(iconColor(false))
        address.addView(folder, LinearLayout.LayoutParams(dp(18), dp(18)))
        val path = label("/", outputTextSizeSp, true)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setPadding(dp(6), 0, 0, 0)
        path.setTextColor(outputTextColor)
        addressPathView = path
        address.addView(path, LinearLayout.LayoutParams(0, -1, 1f))
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.leftMargin = dp(6)
        row.addView(address, params)
        return row
    }

    private fun buildPane(panel: Panel): View {
        val pane = LinearLayout(this)
        pane.orientation = LinearLayout.VERTICAL
        pane.setPadding(dp(8), dp(6), dp(8), dp(4))
        pane.background = panelDrawable(PanelRole.OUTPUT)
        pane.setOnClickListener { switchPanel(panel) }

        if (panel == Panel.LEFT) {
            val header = LinearLayout(this)
            header.orientation = LinearLayout.HORIZONTAL
            header.addView(column("LOCATIONS", 1f, Gravity.START))
            pane.addView(header, LinearLayout.LayoutParams(-1, dp(24)))
            val scroll = ScrollView(this)
            scroll.isFillViewport = true
            scroll.isVerticalScrollBarEnabled = true
            val rows = LinearLayout(this)
            rows.orientation = LinearLayout.VERTICAL
            scroll.addView(rows, FrameLayout.LayoutParams(-1, -2))
            pane.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            leftRowsView = rows
            leftScrollView = scroll
        } else {
            val grid = GridView(this)
            grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
            grid.horizontalSpacing = dp(4)
            grid.verticalSpacing = dp(4)
            grid.clipToPadding = false
            grid.isVerticalScrollBarEnabled = true
            grid.cacheColorHint = Color.TRANSPARENT
            grid.setOnItemClickListener { _, _, position, _ ->
                activePanel = Panel.RIGHT
                rightPane.cursor = position
                clampCursor(rightPane)
                openSelected()
            }
            grid.setOnItemLongClickListener { _, _, position, _ ->
                activePanel = Panel.RIGHT
                rightPane.cursor = position
                renderActivePane()
                rightPane.rows.getOrNull(position)?.let { entry ->
                    entry.file?.let { if (entry.isParent) changeDirectory(it) else showItemMenu(it) }
                }
                true
            }
            pane.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
            rightGridView = grid
        }

        val footer = label("", max(10, outputTextSizeSp - 2), true)
        footer.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        pane.addView(footer, LinearLayout.LayoutParams(-1, dp(24)))
        val disk = label("", max(10, outputTextSizeSp - 2), true)
        disk.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        pane.addView(disk, LinearLayout.LayoutParams(-1, dp(22)))

        if (panel == Panel.LEFT) {
            leftFooterView = footer
            leftDiskView = disk
        } else {
            rightFooterView = footer
            rightDiskView = disk
        }
        return pane
    }

    private fun shouldOpenTree(intent: Intent?): Boolean {
        return !intent?.getStringExtra(EXTRA_COMMAND).isNullOrBlank() ||
            !intent?.getStringExtra(EXTRA_PATH).isNullOrBlank()
    }

    private fun showHome() {
        clearVirtualCategory()
        currentScreen = Screen.HOME
        homeCountVersion++
        addressPathView?.text = "HOME"
        leftRowsView = null
        leftScrollView = null
        rightGridView = null
        rightGridAdapter = null
        leftFooterView = null
        rightFooterView = null
        leftDiskView = null
        rightDiskView = null
        contentHost?.removeAllViews()
        contentHost?.addView(buildHomePage(), FrameLayout.LayoutParams(-1, -1))
    }

    private fun showTree(dir: File = rightPane.directory) {
        currentScreen = Screen.TREE
        homeCountVersion++
        contentHost?.removeAllViews()
        contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
        navigateMainInTree(dir)
    }

    private fun buildHomePage(): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, 0, 0, dp(8))
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        content.addView(buildCategorySection(), sectionParams())
        content.addView(buildStorageSection(), sectionParams())
        return scroll
    }

    private fun buildCategorySection(): View {
        val panel = homePanel("CATEGORIES")
        categoryCountViews.clear()
        var row: LinearLayout? = null
        for ((index, category) in fileCategories.withIndex()) {
            if (index % 3 == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                panel.addView(row, LinearLayout.LayoutParams(-1, dp(92)))
            }
            addCategoryTile(row, category, categoryCountCache[category.label])
        }
        loadCategoryCountsAsync()
        return panel
    }

    private fun buildStorageSection(): View {
        val panel = homePanel("STORAGE")
        addHomeRow(panel, "Phone storage", storageLine(homeDirectory()), "") { showTree(homeDirectory()) }
        addHomeRow(panel, "Recently deleted", "${trashFileCount()} items", "") { showRecentlyDeleted() }
        return panel
    }

    private fun homePanel(title: String): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(10), dp(8), dp(10), dp(10))
        panel.background = panelDrawable(PanelRole.OUTPUT)
        val heading = label(title, max(10, outputTextSizeSp - 1), true)
        heading.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        heading.setTextColor(moduleTextColor)
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(24)))
        return panel
    }

    private fun addCategoryTile(parent: LinearLayout?, category: FileCategory, count: Int?) {
        if (parent == null) return
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(dp(3), dp(5), dp(3), dp(4))
        tile.background = functionButtonBackground()
        tile.setOnClickListener { showCategoryFiles(category) }
        tile.addView(glyphView(category.glyph, iconColor(false), max(22, outputTextSizeSp + 8)), LinearLayout.LayoutParams(-1, dp(28)))
        val name = label(category.label, max(10, outputTextSizeSp - 2), true)
        name.gravity = Gravity.CENTER
        name.setTextColor(fileTextColor)
        tile.addView(name, LinearLayout.LayoutParams(-1, dp(22)))
        val value = label(count?.toString() ?: "...", max(9, outputTextSizeSp - 3), false)
        value.gravity = Gravity.CENTER
        value.setTextColor(withAlpha(outputTextColor, 185))
        categoryCountViews[category.label] = value
        tile.addView(value, LinearLayout.LayoutParams(-1, dp(18)))
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(3), dp(3), dp(3), dp(3))
        parent.addView(tile, params)
    }

    private fun addHomeRow(parent: LinearLayout, title: String, subtitle: String, glyph: String, action: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(6), 0, dp(6), 0)
        row.background = functionButtonBackground()
        row.setOnClickListener { action() }
        row.addView(glyphView(glyph, iconColor(false), max(16, outputTextSizeSp + 2)), LinearLayout.LayoutParams(dp(34), -1))
        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        val top = label(title, outputTextSizeSp, true)
        top.gravity = Gravity.START
        top.setTextColor(fileTextColor)
        val bottom = label(subtitle, max(9, outputTextSizeSp - 3), false)
        bottom.gravity = Gravity.START
        bottom.setTextColor(withAlpha(outputTextColor, 175))
        text.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        text.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))
        row.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        val arrow = label(">", outputTextSizeSp, true)
        arrow.gravity = Gravity.CENTER
        arrow.setTextColor(moduleButtonTextColor)
        row.addView(arrow, LinearLayout.LayoutParams(dp(20), -1))
        val params = LinearLayout.LayoutParams(-1, dp(54))
        params.topMargin = dp(6)
        parent.addView(row, params)
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(8)
        return params
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
                moveCursor(if (activePanel == Panel.RIGHT) -gridColumnCount() else -1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(if (activePanel == Panel.RIGHT) gridColumnCount() else 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (activePanel == Panel.RIGHT && rightPane.cursor % gridColumnCount() != 0) moveCursor(-1) else switchPanel(Panel.LEFT)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (activePanel == Panel.RIGHT) moveCursor(1) else switchPanel(Panel.RIGHT)
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
            "help" -> showHelpPopup()
            "refresh", "ls" -> reloadAll()
            "pwd" -> showOutput("PWD", contentPane().directory.absolutePath)
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
        clearVirtualCategory()
        if (currentScreen == Screen.HOME) {
            showHome()
            return
        }
        reloadPane(leftPane)
        reloadPane(rightPane)
        renderAll()
    }

    private fun reloadPane(pane: PaneState) {
        val dir = pane.directory
        pane.rows.clear()
        if (pane !== leftPane) {
            pane.rows.add(FileEntry(dir.parentFile, "/..", true, true))
        }
        val files = dir.listFiles()?.toList().orEmpty()
        val sorted = files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
        val visible = if (pane === leftPane) {
            val places = placeEntries()
            val placePaths = places.mapNotNull { it.file?.absolutePath }.toHashSet()
            pane.rows.add(FileEntry(null, "PLACES", false, isSection = true))
            pane.rows.addAll(places)
            if (dir.absolutePath !in placePaths) {
                pane.rows.add(FileEntry(dir, dir.name.ifBlank { dir.absolutePath }, true))
            }
            pane.rows.add(FileEntry(null, "FOLDERS", false, isSection = true))
            sorted.filter { it.isDirectory && it.absolutePath !in placePaths }
        } else {
            sorted
        }
        for (file in visible.take(MAX_ROWS)) {
            pane.rows.add(FileEntry(file, file.name, file.isDirectory))
        }
        pane.summary = summarizeRows(pane.rows)
        if (pane.cursor >= pane.rows.size) pane.cursor = max(0, pane.rows.size - 1)
        if (pane.cursor < 0) pane.cursor = 0
        clampCursor(pane)
    }

    private fun placeEntries(): List<FileEntry> {
        val home = homeDirectory()
        val candidates = listOf(
            "Home" to home,
            "Downloads" to File(home, Environment.DIRECTORY_DOWNLOADS),
            "Documents" to File(home, Environment.DIRECTORY_DOCUMENTS),
            "Pictures" to File(home, Environment.DIRECTORY_PICTURES),
            "Music" to File(home, Environment.DIRECTORY_MUSIC)
        )
        val seen = HashSet<String>()
        val entries = candidates.mapNotNull { (label, file) ->
            if (file.exists() && file.isDirectory && seen.add(file.absolutePath)) {
                FileEntry(file, label, true)
            } else {
                null
            }
        }.toMutableList()
        for (path in customPlacePaths()) {
            val file = File(path)
            if (file.exists() && file.isDirectory && seen.add(file.absolutePath)) {
                entries.add(FileEntry(file, file.name.ifBlank { file.absolutePath }, true))
            }
        }
        return entries
    }

    private fun categoryCounts(): Map<String, Int> {
        return fileCategories.associate { it.label to mediaStoreCount(it) }
    }

    private fun loadCategoryCountsAsync() {
        val loadVersion = homeCountVersion
        Thread {
            val counts = categoryCounts()
            runOnUiThread {
                if (currentScreen != Screen.HOME || loadVersion != homeCountVersion) return@runOnUiThread
                categoryCountCache.clear()
                categoryCountCache.putAll(counts)
                for ((label, count) in counts) {
                    categoryCountViews[label]?.text = count.toString()
                }
            }
        }.start()
    }

    private fun showCategoryFiles(category: FileCategory) {
        val loadVersion = ++categoryLoadVersion
        showCategoryRows(category, listOf(FileEntry(null, "Loading ${category.label.lowercase(Locale.US)}...", false)))
        Thread {
            loadCategoryRows(category, loadVersion)
        }.start()
    }

    private fun showCategoryRows(category: FileCategory, rows: List<FileEntry>) {
        if (currentScreen != Screen.TREE || leftRowsView == null || rightGridView == null) {
            currentScreen = Screen.TREE
            contentHost?.removeAllViews()
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            leftPane.directory = homeDirectory()
            leftPane.cursor = 0
            reloadPane(leftPane)
        } else {
            currentScreen = Screen.TREE
        }
        rightVirtualTitle = category.label.uppercase(Locale.US)
        rightPane.directory = homeDirectory()
        rightPane.rows.clear()
        rightPane.rows.addAll(rows)
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(rows, includeSize = false)
        activePanel = Panel.RIGHT
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        addressPathView?.text = rightVirtualTitle
    }

    private fun mediaStoreCount(category: FileCategory): Int {
        val (selection, args) = categorySelection(category)
        return try {
            contentResolver.query(
                category.uri,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun loadCategoryRows(category: FileCategory, loadVersion: Int) {
        val (selection, args) = categorySelection(category)
        val out = ArrayList<FileEntry>()
        var firstPageSent = false
        fun publish(rows: List<FileEntry>) {
            val snapshot = ArrayList(rows)
            runOnUiThread {
                if (loadVersion == categoryLoadVersion) showCategoryRows(category, snapshot)
            }
        }
        try {
            contentResolver.query(
                category.uri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA),
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < MAX_ROWS) {
                    val uri = ContentUris.withAppendedId(category.uri, cursor.getLong(0))
                    val path = cursor.getString(1) ?: continue
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        out.add(FileEntry(file, file.name, false, contentUri = uri))
                        if (!firstPageSent && out.size >= CATEGORY_FIRST_PAGE_ROWS) {
                            firstPageSent = true
                            publish(out)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            publish(listOf(FileEntry(null, "Could not load ${category.label.lowercase(Locale.US)}", false)))
            return
        }
        publish(
            if (out.isEmpty()) listOf(FileEntry(null, "No ${category.label.lowercase(Locale.US)} found", false))
            else out
        )
    }

    private fun categorySelection(category: FileCategory): Pair<String?, Array<String>?> {
        if (category.mimeTypes.isEmpty()) return null to null
        val placeholders = category.mimeTypes.joinToString(",") { "?" }
        return "${MediaStore.MediaColumns.MIME_TYPE} IN ($placeholders)" to category.mimeTypes.toTypedArray()
    }

    private fun storageLine(root: File): String {
        return diskSummary(root)
    }

    private fun trashDirs(): List<File> {
        val seen = HashSet<String>()
        val dirs = ArrayList<File>()
        fun addIfTrash(path: String) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory && seen.add(dir.absolutePath)) dirs.add(dir)
        }
        addIfTrash(File(homeDirectory(), TRASH_DIR_NAME).absolutePath)
        themePrefs().getString(PREF_TRASH_DIRS, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach(::addIfTrash)
        return dirs
    }

    private fun trashFileCount(): Int {
        return trashDirs().sumOf { it.listFiles()?.size ?: 0 }
    }

    private fun showRecentlyDeleted() {
        val dirs = trashDirs().filter { (it.listFiles()?.isNotEmpty() == true) }
        if (dirs.isEmpty()) {
            showOutput("RECENTLY DELETED", "Trash is empty")
        } else if (dirs.size == 1) {
            showTree(dirs.first())
        } else {
            showActionMenu(
                "Recently deleted",
                dirs.map { dir ->
                    val parent = dir.parentFile?.name ?: dir.absolutePath
                    parent to { showTree(dir) }
                }
            )
        }
    }

    private fun renderAll() {
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        addressPathView?.text = rightVirtualTitle ?: abbreviatePath(rightPane.directory.absolutePath)
    }

    private fun renderActivePane() {
        renderPane(activePanel)
    }

    private fun renderPane(panel: Panel) {
        val pane = pane(panel)
        val rowsView = if (panel == Panel.LEFT) leftRowsView else null
        val footer = if (panel == Panel.LEFT) leftFooterView else rightFooterView
        val disk = if (panel == Panel.LEFT) leftDiskView else rightDiskView
        clampCursor(pane)
        if (panel == Panel.RIGHT) {
            renderGrid()
        } else {
            rowsView?.removeAllViews()
            for (i in pane.rows.indices) {
                rowsView?.addView(rowView(pane.rows[i], panel, i), LinearLayout.LayoutParams(-1, dp(22)))
            }
        }
        keepSelectionVisible(panel)
        val selected = pane.rows.getOrNull(pane.cursor)?.takeUnless { it.isSection }?.label ?: pane.directory.name
        footer?.text = if (panel == Panel.RIGHT) "$selected  |  ${paneSummary(pane)}" else selected
        disk?.text = if (panel == Panel.LEFT) "${pane.rows.count { it.file?.isDirectory == true }} locations" else diskSummary(pane.directory)
    }

    private fun renderGrid() {
        val grid = rightGridView ?: return
        grid.numColumns = gridColumnCount()
        val adapter = rightGridAdapter ?: RightGridAdapter().also { rightGridAdapter = it }
        if (grid.adapter !== adapter) grid.adapter = adapter
        adapter.notifyDataSetChanged()
        grid.post { grid.setSelection(rightPane.cursor) }
    }

    private inner class RightGridAdapter : BaseAdapter() {
        override fun getCount(): Int = rightPane.rows.size
        override fun getItem(position: Int): Any = rightPane.rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val cell = (convertView as? LinearLayout) ?: newGridCell()
            bindGridCell(cell, rightPane.rows[position], position)
            cell.layoutParams = AbsListView.LayoutParams(-1, dp(RIGHT_GRID_CELL_HEIGHT_DP))
            return cell
        }
    }

    private class GridCellHolder(val icon: TextView, val label: TextView)

    private fun newGridCell(): LinearLayout {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.setPadding(dp(3), dp(4), dp(3), dp(3))

        val icon = glyphView("", iconColor(false), max(28, outputTextSizeSp + 13))
        val iconParams = LinearLayout.LayoutParams(-1, dp(36))
        iconParams.bottomMargin = dp(1)
        cell.addView(icon, iconParams)

        val text = label("", max(9, outputTextSizeSp - 3), false)
        text.setSingleLine(false)
        text.maxLines = 2
        text.gravity = Gravity.CENTER
        text.ellipsize = TextUtils.TruncateAt.END
        cell.addView(text, LinearLayout.LayoutParams(-1, 0, 1f))

        cell.tag = GridCellHolder(icon, text)
        return cell
    }

    private fun bindGridCell(cell: LinearLayout, entry: FileEntry, index: Int) {
        val selected = activePanel == Panel.RIGHT && index == rightPane.cursor
        val color = rowTextColor(selected, entry.isDirectory)
        val iconColor = iconColor(selected)
        cell.background = rowSelectionBackground(selected)
        val holder = cell.tag as GridCellHolder
        holder.icon.text = fileGlyph(entry, selected)
        holder.icon.setTextColor(iconColor)
        holder.icon.setTextSize(max(28, outputTextSizeSp + 13).toFloat())
        holder.icon.typeface = nerdTypeface()
        holder.label.text = if (entry.isParent) ".." else entry.label
        holder.label.setTextColor(color)
        holder.label.setTextSize(max(9, outputTextSizeSp - 3).toFloat())
        holder.label.setTypeface(appTypeface ?: Typeface.MONOSPACE, if (entry.isDirectory) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun rowView(entry: FileEntry, panel: Panel, index: Int): View {
        if (entry.isSection) return sectionRow(entry.label)
        val selected = rowSelected(entry, panel, index)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(2), 0, dp(2), 0)
        row.background = rowSelectionBackground(selected)
        row.setOnClickListener {
            activePanel = panel
            pane(panel).cursor = index
            clampCursor(pane(panel))
            openSelected()
        }
        row.setOnLongClickListener {
            activePanel = panel
            pane(panel).cursor = index
            renderActivePane()
            entry.file?.let { if (entry.isParent) changeDirectory(it) else showItemMenu(it) }
            true
        }

        row.addView(nameCell(entry, selected), LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun moveCursor(delta: Int) {
        val pane = activePane()
        if (pane.rows.isEmpty()) return
        val direction = if (delta < 0) -1 else 1
        var target = clamp(pane.cursor + delta, 0, pane.rows.size - 1)
        while (target in pane.rows.indices && pane.rows[target].isSection) target += direction
        if (target !in pane.rows.indices) {
            target = clamp(pane.cursor + delta, 0, pane.rows.size - 1)
            while (target in pane.rows.indices && pane.rows[target].isSection) target -= direction
        }
        if (target in pane.rows.indices) pane.cursor = target
        renderActivePane()
    }

    private fun cursorHome() {
        val pane = activePane()
        pane.cursor = pane.rows.indexOfFirst { !it.isSection }.takeIf { it >= 0 } ?: 0
        renderActivePane()
    }

    private fun cursorEnd() {
        val pane = activePane()
        pane.cursor = pane.rows.indexOfLast { !it.isSection }.takeIf { it >= 0 } ?: max(0, pane.rows.size - 1)
        renderActivePane()
    }

    private fun clampCursor(pane: PaneState) {
        if (pane.rows.isEmpty()) return
        pane.cursor = clamp(pane.cursor, 0, pane.rows.size - 1)
        if (!pane.rows[pane.cursor].isSection) return
        var after = pane.cursor + 1
        var before = pane.cursor - 1
        while (after < pane.rows.size || before >= 0) {
            if (after < pane.rows.size && !pane.rows[after].isSection) {
                pane.cursor = after
                return
            }
            if (before >= 0 && !pane.rows[before].isSection) {
                pane.cursor = before
                return
            }
            after++
            before--
        }
    }

    private fun visibleRowCount(panel: Panel): Int {
        val height = if (panel == Panel.RIGHT) rightGridView?.height ?: 0 else leftScrollView?.height ?: 0
        if (panel == Panel.RIGHT) {
            val columns = gridColumnCount()
            val rowHeight = max(1, dp(RIGHT_GRID_CELL_HEIGHT_DP))
            val rows = if (height > 0) height / rowHeight else 6
            return max(columns, rows * columns)
        }
        val rowHeight = max(1, dp(22))
        return max(1, if (height > 0) height / rowHeight else 18)
    }

    private fun keepSelectionVisible(panel: Panel) {
        val pane = pane(panel)
        if (panel == Panel.RIGHT) {
            rightGridView?.post { rightGridView?.setSelection(pane.cursor) }
            return
        }
        val scroll = leftScrollView
        val itemHeight = if (panel == Panel.RIGHT) dp(RIGHT_GRID_CELL_HEIGHT_DP) else dp(22)
        val itemTop = if (panel == Panel.RIGHT) (pane.cursor / gridColumnCount()) * itemHeight else pane.cursor * itemHeight
        scroll?.post {
            val height = scroll.height
            if (height <= 0) return@post
            val top = scroll.scrollY
            val bottom = top + height
            val target = when {
                itemTop < top -> itemTop
                itemTop + itemHeight > bottom -> itemTop + itemHeight - height
                else -> top
            }
            if (target != top) scroll.scrollTo(0, max(0, target))
        }
    }

    private fun gridColumnCount(): Int {
        val width = rightGridView?.width ?: 0
        val minCellWidth = max(1, dp(RIGHT_GRID_MIN_CELL_WIDTH_DP))
        return max(2, if (width > 0) width / minCellWidth else 3)
    }

    private fun openSelected() {
        val entry = activePane().rows.getOrNull(activePane().cursor) ?: return
        if (entry.isSection) return
        val file = entry.file ?: return
        if (activePanel == Panel.LEFT) {
            if (entry.isParent) navigateMain(file) else if (entry.isDirectory) showDirectoryContents(file)
        } else if (entry.isDirectory) {
            showDirectoryContents(file)
        } else {
            previewFile(file, entry.contentUri)
        }
    }

    private fun changeDirectory(dir: File) {
        navigateMain(dir)
    }

    private fun showDirectoryContents(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            showOutput("CD", "Not a directory: ${dir.absolutePath}")
            return
        }
        if (!dir.canRead()) {
            showOutput("CD", "Cannot read: ${dir.absolutePath}")
            return
        }
        if (currentScreen != Screen.TREE) {
            showTree(dir)
            return
        }
        clearVirtualCategory()
        rightPane.directory = dir
        rightPane.cursor = 0
        reloadPane(rightPane)
        renderAll()
    }

    private fun navigateMain(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            showOutput("CD", "Not a directory: ${dir.absolutePath}")
            return
        }
        if (!dir.canRead()) {
            showOutput("CD", "Cannot read: ${dir.absolutePath}")
            return
        }
        if (currentScreen != Screen.TREE) {
            showTree(dir)
            return
        }
        navigateMainInTree(dir)
    }

    private fun navigateMainInTree(dir: File) {
        clearVirtualCategory()
        leftPane.directory = dir
        leftPane.cursor = 0
        rightPane.directory = dir
        rightPane.cursor = 0
        reloadAll()
    }

    private fun goHome() {
        changeDirectory(homeDirectory())
    }

    private fun goParent() {
        rightPane.directory.parentFile?.let { showDirectoryContents(it) }
    }

    private fun switchPanel(panel: Panel) {
        activePanel = panel
        renderAll()
    }

    private fun clearVirtualCategory() {
        categoryLoadVersion++
        rightVirtualTitle = null
    }

    private fun previewFile(file: File, contentUri: Uri? = null) {
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
            openFile(file, contentUri)
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

    private fun openFile(file: File, contentUri: Uri? = null) {
        val mime = mimeFor(file)
        if (isMediaMime(mime)) {
            val mediaUri = contentUri ?: mediaStoreUriFor(file, mime)
            if (mediaUri != null) {
                startViewIntent(file, mediaUri, mime)
            } else {
                scanAndOpenMedia(file, mime)
            }
            return
        }
        val uri = uriFor(file) ?: return
        startViewIntent(file, uri, mime)
    }

    private fun scanAndOpenMedia(file: File, mime: String) {
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(mime)) { _, uri ->
            runOnUiThread {
                startViewIntent(file, uri ?: uriFor(file) ?: return@runOnUiThread, mime)
            }
        }
    }

    private fun startViewIntent(file: File, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mime)
        intent.clipData = ClipData.newUri(contentResolver, file.name, uri)
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
        labels.add(if (file.isDirectory || !isLikelyText(file)) "Open" else "Preview")
        actions.add { if (file.isDirectory) showDirectoryContents(file) else previewFile(file) }
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
            if (!isPlace(file)) {
                labels.add("Add to Places")
                actions.add { addPlace(file) }
            }
        }
        if (isInTrash(file)) {
            labels.add("Restore")
            actions.add { restoreFromTrash(file) }
        } else if (!file.name.startsWith("..")) {
            labels.add("Move to trash")
            actions.add { confirmDelete(file) }
        }
        showActionMenu(file.name, labels.zip(actions))
    }

    private fun customPlacePaths(): List<String> {
        return themePrefs().getString(PREF_CUSTOM_PLACES, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun isPlace(file: File): Boolean {
        return placeEntries().any { it.file?.absolutePath == file.absolutePath }
    }

    private fun addPlace(file: File) {
        if (!file.isDirectory || isPlace(file)) return
        val paths = customPlacePaths().toMutableList()
        paths.add(file.absolutePath)
        themePrefs().edit().putString(PREF_CUSTOM_PLACES, paths.joinToString("\n")).apply()
        reloadAll()
        showOutput("PLACES", "Added to Places:\n${file.absolutePath}")
    }

    private fun promptMkdir(baseDir: File = contentPane().directory) {
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
        val loadVersion = ++findVersion
        val root = contentPane().directory
        val pattern = query.lowercase(Locale.US)
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()
        Thread {
            val out = StringBuilder()
            var count = 0
            root.walkTopDown().maxDepth(8).forEach { file ->
                if (count >= 80) return@forEach
                if (file.name.lowercase(Locale.US).contains(pattern)) {
                    out.append(file.absolutePath).append('\n')
                    count++
                }
            }
            runOnUiThread {
                if (loadVersion == findVersion) {
                    showOutput("FIND", if (out.isEmpty()) "No matches for $query" else out.toString())
                }
            }
        }.start()
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
        val moved = file.renameTo(uniqueFile(File(trash, file.name)))
        if (moved) rememberTrashDir(trash)
        return moved
    }

    private fun rememberTrashDir(dir: File) {
        val paths = themePrefs().getString(PREF_TRASH_DIRS, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        if (paths.add(dir.absolutePath)) {
            themePrefs().edit().putString(PREF_TRASH_DIRS, paths.joinToString("\n")).apply()
        }
    }

    private fun isInTrash(file: File): Boolean {
        return file.parentFile?.name == TRASH_DIR_NAME
    }

    private fun restoreFromTrash(file: File) {
        val targetDir = file.parentFile?.parentFile ?: return
        val restored = file.renameTo(uniqueFile(File(targetDir, file.name)))
        reloadAll()
        showOutput("RESTORE", if (restored) "Restored ${file.name}" else "Could not restore ${file.name}")
    }

    private fun uniqueFile(base: File): File {
        var target = base
        var suffix = 1
        while (target.exists()) {
            target = File(base.parentFile, base.name + "." + suffix)
            suffix++
        }
        return target
    }

    private fun showOutput(title: String, message: CharSequence) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHelpPopup() {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(10), dp(12), dp(10))
        panel.background = panelDrawable(PanelRole.OUTPUT)

        val heading = label("HELP", max(14, outputTextSizeSp + 1), true)
        heading.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        heading.setTextColor(headerTextColor)
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(34)))

        val body = label(helpText(), max(10, outputTextSizeSp - 1), false)
        body.gravity = Gravity.START
        body.setSingleLine(false)
        body.setTextColor(outputTextColor)
        body.setPadding(dp(8), dp(6), dp(8), dp(6))

        val scroll = ScrollView(this)
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(430)))

        lateinit var dialog: AlertDialog
        val close = label("OK", outputTextSizeSp, true)
        close.gravity = Gravity.CENTER
        close.setTextColor(moduleButtonTextColor)
        close.background = panelDrawable(PanelRole.MODULE)
        close.setOnClickListener { dialog.dismiss() }
        val closeParams = LinearLayout.LayoutParams(dp(96), dp(38))
        closeParams.gravity = Gravity.END
        closeParams.topMargin = dp(8)
        panel.addView(close, closeParams)

        dialog = AlertDialog.Builder(this).create()
        dialog.setView(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun helpText(): String {
        return "Menus:\n" +
            "File: new folder, open/share selected, refresh, close\n" +
            "Edit: edit text, seed copy/move commands, trash\n" +
            "View: preview, path, pane focus, refresh\n" +
            "Go: up, home, storage root, set main dir\n" +
            "Places: Home, Downloads, Documents, Pictures, Music\n" +
            "\nCommands:\n" +
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
            "arrows move/open\n" +
            "Enter opens selected\n" +
            "Tab/Left switches pane"
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
        return if (file.isAbsolute) file.absoluteFile else File(contentPane().directory, raw).absoluteFile
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
    private fun contentPane(): PaneState = rightPane
    private fun panelFor(pane: PaneState): Panel = if (pane === leftPane) Panel.LEFT else Panel.RIGHT

    private fun addMenuButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val view = label(text, outputTextSizeSp, true)
        view.gravity = Gravity.CENTER_VERTICAL
        view.setTextColor(moduleButtonTextColor)
        view.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(-2, -1)
        params.rightMargin = dp(12)
        parent.addView(view, params)
    }

    private fun showFileMenu() {
        showActionMenu(
            "File",
            listOf(
                "New folder here" to { promptMkdir() },
                "Open selected with Android" to { withSelected("OPEN") { openFile(it) } },
                "Share selected file" to { withSelected("SHARE") { shareFile(it) } },
                "Refresh" to { reloadAll() },
                "Close FM" to { finish() }
            )
        )
    }

    private fun showEditMenu() {
        showActionMenu(
            "Edit",
            listOf(
                "Edit selected text" to { withSelected("EDIT") { editFile(it) } },
                "Copy selected..." to { withSelected("COPY") { seed("cp ${quoteIfNeeded(it.absolutePath)} ") } },
                "Move/rename selected..." to { withSelected("MOVE") { seed("mv ${quoteIfNeeded(it.absolutePath)} ") } },
                "Move selected to trash" to { withSelected("TRASH") { confirmDelete(it) } }
            )
        )
    }

    private fun showViewMenu() {
        showActionMenu(
            "View",
            listOf(
                "Preview selected" to { withSelected("PREVIEW") { previewFile(it) } },
                "Show right-pane path" to { showOutput("PWD", rightPane.directory.absolutePath) },
                "Focus left pane" to { switchPanel(Panel.LEFT) },
                "Focus right pane" to { switchPanel(Panel.RIGHT) },
                "Refresh" to { reloadAll() }
            )
        )
    }

    private fun showGoMenu() {
        showActionMenu(
            "Go",
            listOf(
                "Up one folder" to { goParent() },
                "Home" to { goHome() },
                "Storage root" to { navigateMain(homeDirectory()) },
                "Use right pane as main" to { navigateMain(rightPane.directory) }
            )
        )
    }

    private fun showPlacesMenu() {
        val items = placeEntries().mapNotNull { entry ->
            val file = entry.file ?: return@mapNotNull null
            entry.label to { showDirectoryContents(file) }
        }
        showActionMenu("Places", items)
    }

    private fun showActionMenu(title: String, items: List<Pair<String, () -> Unit>>) {
        if (items.isEmpty()) {
            showOutput(title, "No actions available")
            return
        }
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(10), dp(12), dp(10))
        panel.background = panelDrawable(PanelRole.OUTPUT)

        val heading = label(title, max(14, outputTextSizeSp + 1), true)
        heading.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        heading.setTextColor(headerTextColor)
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(34)))

        lateinit var dialog: AlertDialog
        for ((text, action) in items) {
            val row = label(text, outputTextSizeSp, true)
            row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            row.setPadding(dp(8), 0, dp(8), 0)
            row.setTextColor(moduleButtonTextColor)
            row.background = functionButtonBackground()
            row.setOnClickListener {
                dialog.dismiss()
                action()
            }
            val params = LinearLayout.LayoutParams(-1, dp(40))
            params.topMargin = dp(4)
            panel.addView(row, params)
        }

        dialog = AlertDialog.Builder(this).create()
        dialog.setView(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun withSelected(title: String, action: (File) -> Unit) {
        val file = selectedFile()
        if (file == null || !file.exists()) {
            showOutput(title, "No selected item")
            return
        }
        action(file)
    }

    private fun addToolbarIconButton(parent: LinearLayout, iconRes: Int, action: () -> Unit) {
        val view = ImageView(this)
        view.setImageResource(iconRes)
        view.setColorFilter(iconColor(false))
        view.scaleType = ImageView.ScaleType.CENTER
        view.setPadding(dp(7), dp(5), dp(7), dp(5))
        view.background = toolbarButtonDrawable()
        view.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(dp(36), -1)
        params.rightMargin = dp(4)
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

    private fun sectionRow(text: String): TextView {
        val view = label(text, max(9, outputTextSizeSp - 3), true)
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(5), 0, dp(2), 0)
        view.setTextColor(withAlpha(moduleTextColor, 175))
        view.background = ColorDrawable(withAlpha(moduleButtonBgColor, 80))
        return view
    }

    private fun rowTextColor(selected: Boolean, directory: Boolean): Int {
        if (selected) return selectionTextColor
        return if (directory) directoryTextColor else fileTextColor
    }

    private fun iconColor(selected: Boolean): Int {
        return if (selected) selectionTextColor else directoryTextColor
    }

    private fun rowSelected(entry: FileEntry, panel: Panel, index: Int): Boolean {
        if (panel == Panel.LEFT && activePanel != Panel.LEFT) {
            return entry.file?.absolutePath == rightPane.directory.absolutePath
        }
        return panel == activePanel && index == pane(panel).cursor
    }

    private fun diskSummary(root: File): String {
        val total = root.totalSpace
        if (total <= 0) return ""
        val free = root.freeSpace
        return "FREE " + progressBar(free, total) + " " + humanSize(free) + " / " + humanSize(total)
    }

    private fun paneSummary(pane: PaneState): String {
        if (pane.summary.isNotBlank()) return pane.summary
        return summarizeRows(pane.rows)
    }

    private fun summarizeRows(rows: List<FileEntry>, includeSize: Boolean = true): String {
        val entries = rows.filter { !it.isParent && !it.isSection && it.file != null }
        val dirs = entries.count { it.isDirectory }
        val files = entries.size - dirs
        if (!includeSize) return "$dirs dirs | $files files"
        val size = entries.filterNot { it.isDirectory }.sumOf { it.file?.length() ?: 0L }
        return "$dirs dirs | $files files: ${humanSize(size)}"
    }

    private fun progressBar(value: Long, total: Long): String {
        val width = 10
        val filled = if (total <= 0) 0 else ((value.toDouble() / total) * width).toInt().coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }

    private fun nameCell(entry: FileEntry, selected: Boolean): View {
        val color = rowTextColor(selected, entry.isDirectory)
        val iconColor = iconColor(selected)
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.HORIZONTAL
        wrap.gravity = Gravity.CENTER_VERTICAL
        wrap.setPadding(dp(2), 0, dp(2), 0)

        wrap.addView(glyphView(fileGlyph(entry, selected), iconColor, max(13, outputTextSizeSp - 1)), LinearLayout.LayoutParams(dp(18), -1))

        val text = label(if (entry.isParent) ".." else entry.label.take(24), max(10, outputTextSizeSp - 2), entry.isDirectory)
        text.setTextColor(color)
        text.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        text.setPadding(dp(3), 0, 0, 0)
        text.ellipsize = TextUtils.TruncateAt.END
        wrap.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        return wrap
    }

    private fun fileGlyph(entry: FileEntry, selected: Boolean): String {
        if (entry.isParent) return "↑"
        if (entry.isDirectory) return directoryGlyph(entry.file?.name ?: entry.label, selected)
        val ext = entry.file?.name?.lowercase(Locale.US)?.substringAfterLast('.', "") ?: ""
        return when (ext) {
            "md", "markdown" -> ""
            "txt", "log", "csv", "yaml", "yml", "toml", "ini", "conf", "ignore", "gitignore" -> ""
            "xml", "html", "css", "js", "json", "kt", "java", "go", "rs", "py", "rb", "lua", "c", "cpp", "h" -> ""
            "sh", "bash", "zsh", "fish" -> ""
            "png", "jpg", "jpeg", "gif", "webp", "heic", "svg" -> ""
            "mp3", "wav", "flac", "m4a", "ogg" -> ""
            "mp4", "mkv", "mov", "avi", "webm" -> ""
            "zip", "jar", "tar", "gz", "rar", "7z" -> ""
            "apk" -> ""
            "pdf" -> ""
            "doc", "docx" -> ""
            "xls", "xlsx" -> ""
            "ppt", "pptx" -> ""
            else -> ""
        }
    }

    private fun directoryGlyph(name: String, selected: Boolean): String {
        return when (name.lowercase(Locale.US)) {
            ".git" -> ""
            ".config", "config", "settings" -> ""
            "documents" -> ""
            "download", "downloads" -> ""
            "music" -> ""
            "pictures", "photos", "dcim" -> ""
            "movies", "videos" -> ""
            else -> if (selected) "" else ""
        }
    }

    private fun glyphView(text: String, color: Int, sizeSp: Int): TextView {
        val view = label(text, sizeSp, false)
        view.typeface = nerdTypeface()
        view.gravity = Gravity.CENTER
        view.setTextColor(color)
        return view
    }

    private fun nerdTypeface(): Typeface {
        iconTypeface?.let { return it }
        val loaded = try {
            Typeface.createFromAsset(assets, "symbols_nerd_font_mono.ttf")
        } catch (_: Exception) {
            appTypeface ?: Typeface.MONOSPACE
        }
        iconTypeface = loaded
        return loaded
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

    private fun mediaStoreUriFor(file: File, mime: String): Uri? {
        val collection = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        return try {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DATA}=?"
            val args = arrayOf(file.absolutePath)
            contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeFor(file: File): String {
        val ext = file.extension.lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US)) ?: "*/*"
    }

    private fun isMediaMime(mime: String): Boolean {
        return mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
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

    private fun applyCrtForeground(root: FrameLayout) {
        root.foreground = if (crtFilter) {
            CrtOverlayDrawable(this).apply { setAccentColor(outputTextColor) }
        } else {
            null
        }
    }

    private fun applyThemeExtras(intent: Intent?) {
        val prefs = themePrefs()
        val hasFontPayload = hasFontPayload(intent)
        applyStoredTheme(prefs)
        applyThemePayload(intent)
        if (!hasFontPayload && applyLauncherFontFallback()) {
            prefs.edit()
                .putString(EXTRA_FONT_PATH, appFontPath)
                .putString(EXTRA_FONT_NAME, appFontName)
                .apply()
        }
        if (hasThemePayload(intent)) saveThemePayload(prefs, intent)
        appTypeface = resolveTypeface()
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
        intent.putExtra(EXTRA_CRT_FILTER, crtFilter)
        appFontPath?.let { intent.putExtra(EXTRA_FONT_PATH, it) }
        appFontName?.let { intent.putExtra(EXTRA_FONT_NAME, it) }
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
        crtFilter = prefs.getBoolean(EXTRA_CRT_FILTER, crtFilter)
        appFontPath = prefs.getString(EXTRA_FONT_PATH, appFontPath)
        appFontName = prefs.getString(EXTRA_FONT_NAME, appFontName)
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
        crtFilter = booleanExtra(intent, EXTRA_CRT_FILTER, crtFilter, "enable_crt_filter")
        intent?.getStringExtra(EXTRA_TERMINAL_BG_IMAGE)?.let { terminalBackgroundImage = it }
        intent?.getStringExtra(EXTRA_FONT_PATH)?.takeIf { it.isNotBlank() }?.let {
            appFontPath = it
            appFontName = null
        }
        intent?.getStringExtra(EXTRA_FONT_NAME)?.takeIf { it.isNotBlank() }?.let {
            appFontName = it
            appFontPath = null
        }
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
        editor.putBoolean(EXTRA_CRT_FILTER, crtFilter)
        editor.putString(EXTRA_TERMINAL_BG_IMAGE, terminalBackgroundImage)
        editor.putString(EXTRA_FONT_PATH, appFontPath)
        editor.putString(EXTRA_FONT_NAME, appFontName)
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

    private fun hasFontPayload(intent: Intent?): Boolean {
        val extras = intent?.extras ?: return false
        return extras.containsKey(EXTRA_FONT_PATH) || extras.containsKey(EXTRA_FONT_NAME)
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

    private fun applyLauncherFontFallback(): Boolean {
        val config = launcherUiFiles().firstOrNull { it.exists() && it.isFile } ?: return false
        val xml = try {
            config.readText()
        } catch (_: Exception) {
            return false
        }
        val systemFont = xmlValue(xml, "system_font")?.equals("true", true) == true
        if (systemFont) {
            appFontPath = null
            appFontName = "system"
            return true
        }
        val fontName = xmlValue(xml, "font_file")?.trim().orEmpty()
        if (fontName.isEmpty()) return false
        val root = config.parentFile ?: return false
        val font = listOf(File(root, fontName), File(File(root, "fonts"), fontName))
            .firstOrNull { it.exists() && it.isFile && it.length() > 0 }
            ?: return false
        appFontPath = font.absolutePath
        appFontName = null
        return true
    }

    private fun launcherUiFiles(): List<File> {
        val sharedRoot = Environment.getExternalStorageDirectory()
        return listOf(
            File(sharedRoot, "Re-T-UI/ui.xml"),
            File(sharedRoot, "Android/data/com.dvil.tui_renewed/files/Re-T-UI/ui.xml")
        )
    }

    private fun xmlValue(xml: String, name: String): String? {
        return Regex("<$name\\s+value=\"([^\"]*)\"").find(xml)?.groupValues?.getOrNull(1)
    }

    private fun resolveTypeface(): Typeface {
        val path = appFontPath
        if (!path.isNullOrBlank()) {
            try {
                return Typeface.createFromFile(path)
            } catch (_: Exception) {
            }
        }
        val name = appFontName
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

    private fun toolbarDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(withAlpha(moduleButtonBgColor, 210))
        drawable.setStroke(max(1, dp(1)), moduleButtonBorderColor)
        drawable.cornerRadius = 0f
        return drawable
    }

    private fun addressDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(withAlpha(outputPanelColor, 230))
        drawable.setStroke(max(1, dp(1)), outputBorderColor)
        drawable.cornerRadius = 0f
        return drawable
    }

    private fun toolbarButtonDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(withAlpha(outputPanelColor, 150))
        drawable.setStroke(max(1, dp(1)), moduleButtonBorderColor)
        drawable.cornerRadius = 0f
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
        private const val PREF_CUSTOM_PLACES = "custom_places"
        private const val PREF_TRASH_DIRS = "trash_dirs"
        private const val MAX_ROWS = 5000
        private const val PREVIEW_MAX_BYTES = 64 * 1024
        private const val RIGHT_GRID_MIN_CELL_WIDTH_DP = 72
        private const val RIGHT_GRID_CELL_HEIGHT_DP = 82
        private const val CATEGORY_FIRST_PAGE_ROWS = 90
        private const val TRASH_DIR_NAME = ".retui-trash"
        private val DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "text/html",
            "text/xml",
            "application/xml",
            "application/json",
            "application/rtf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text"
        )
        private val ARCHIVE_MIME_TYPES = setOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-bzip2",
            "application/x-xz",
            "application/java-archive"
        )
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
            "enable_cyberdeck_mode",
            EXTRA_CRT_FILTER,
            "enable_crt_filter"
        )
    }
}
