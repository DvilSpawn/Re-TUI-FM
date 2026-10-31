package com.dvil.retui.fm

import com.dvil.retui.fm.terminal.TerminalLabelView
import com.dvil.retui.fm.terminal.CommanderSkin
import com.dvil.retui.fm.terminal.CommanderStyle
import com.dvil.retui.fm.terminal.CommanderFrameDrawable
import android.Manifest
import com.dvil.retui.contract.RetuiVisualContract
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.BitmapFactory
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
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class MainActivity : Activity() {
    private val commanderSkins by lazy {
        assets.list("mc-skins").orEmpty().filter { it.endsWith(".ini") }.sorted().map { filename ->
            assets.open("mc-skins/$filename").bufferedReader().use { CommanderSkin.parse(filename.removeSuffix(".ini"), it.readText()) }
        }
    }
    private var commanderSkin: CommanderSkin? = null
    private var usingLauncherPalette = false
    private enum class Panel { LEFT, RIGHT }
    private enum class Screen { HOME, TREE }
    private enum class SortMode { NAME_ASC, NAME_DESC, MODIFIED_NEWEST, MODIFIED_OLDEST, SIZE_LARGEST, SIZE_SMALLEST, TYPE }

    private data class ThemeColorBinding(
        val label: String,
        val key: String,
        val get: () -> Int,
        val set: (Int) -> Unit
    )

    private data class FileEntry(
        val file: File?,
        val label: String,
        val isDirectory: Boolean,
        val isParent: Boolean = false,
        val isSection: Boolean = false,
        val contentUri: Uri? = null,
        val isTrashPlace: Boolean = false,
        val archivePath: String? = null,
        val size: Long = 0,
        val providerItem: StorageItem? = null,
        val providerRootUri: Uri? = null,
        val modified: Long = 0
    )

    private data class PendingArchiveExtraction(val archive: File, val selected: Set<String>)

    private data class FileCategory(
        val label: String,
        val glyph: String,
        val uri: Uri,
        val mimeTypes: Set<String> = emptySet()
    )

    private data class SearchRequest(
        val nameTerm: String?,
        val typeTerm: String?,
        val minBytes: Long? = null,
        val maxBytes: Long? = null,
        val modifiedAfter: Long? = null,
        val modifiedBefore: Long? = null
    )

    private data class PaneState(
        var directory: File,
        val rows: ArrayList<FileEntry> = ArrayList(),
        var cursor: Int = 0,
        var summary: String = ""
    )

    private data class NavigationSnapshot(
        val screen: Screen,
        val left: PaneState,
        val right: PaneState,
        val panel: Panel,
        val virtualTitle: String?,
        val category: FileCategory?,
        val archive: File?,
        val archiveEntries: List<ArchiveExtractor.Entry>,
        val archiveDirectory: String
    )

    private lateinit var leftPane: PaneState
    private lateinit var rightPane: PaneState
    private var activePanel = Panel.LEFT

    private var rootView: FrameLayout? = null
    private var mainContainer: LinearLayout? = null
    private var leftRowsView: LinearLayout? = null
    private var leftScrollView: ScrollView? = null
    private var rightListView: ListView? = null
    private var rightListAdapter: BaseAdapter? = null
    private var rightNameSortView: TextView? = null
    private var rightModifiedSortView: TextView? = null
    private var rightWorkspace: FrameLayout? = null
    @Volatile private var paneDialog: PaneDialog? = null
    private var appearanceReturnForm: PaneDialog? = null
    private var tappedLeftEntry: String? = null
    private var pendingPaneRelayout = false
    private var leftTitleView: TextView? = null
    private var rightTitleView: TextView? = null
    private var breadcrumbRow: LinearLayout? = null
    private var menuBarView: View? = null
    private val folderCountCache = HashMap<String, Int>()
    /** Touch users never see the keyboard cursor in compact mode; it appears once a key is pressed. */
    private var cursorFromKeys = false
    private var actionBarScreen: Screen? = null
    private var quickPreview: View? = null
    private var pendingListPosition: Pair<Int, Int>? = null
    private var setupOverlay: View? = null
    private var workspaceBorder: FrameLayout? = null
    private var setupStep = 0
    private var setupFirstRun = false
    private var functionBar: LinearLayout? = null
    private var commandLineView: TextView? = null
    private var showHidden = false
    private var contentHost: FrameLayout? = null
    private var leftFooterView: TextView? = null
    private var rightFooterView: TextView? = null
    private var leftDiskView: TextView? = null
    private var rightDiskView: TextView? = null
        private var selectionBar: LinearLayout? = null
    private var selectionScrollView: HorizontalScrollView? = null
    private var selectionSurface: LinearLayout? = null
    private val selectedPaths = LinkedHashSet<String>()
    private val pendingCopyPaths = ArrayList<String>()
    private var pendingApkPath: String? = null
    private val pendingMovePaths = ArrayList<String>()
    private var shareSelectionMode = false
    private var showRightCursorHighlight = true
    private var rightSortMode = SortMode.NAME_ASC
    private val navigationHistory = ArrayDeque<NavigationSnapshot>()
    private var suppressNavigationHistory = false
    private var backCallback: android.window.OnBackInvokedCallback? = null
    private var currentScreen = Screen.HOME
    private var rightVirtualTitle: String? = null
    private var activeArchive: File? = null
    private var activeProvider: SafStorageBackend? = null
    private var activeProviderUri: Uri? = null
    private var archiveEntries: List<ArchiveExtractor.Entry> = emptyList()
    private var archiveDirectory = ""
    private var archiveLoadVersion = 0
    private var pendingArchiveExtraction: PendingArchiveExtraction? = null
    private var activeOperationId: String? = null
    private var activeTransferStatus: TextView? = null
    private var activeOperationLabel = "OPERATION"
    private var operationReceiverRegistered = false
    private var activeSearchCancellation: AtomicBoolean? = null
    private var searchScanned = 0
    private var activeCategory: FileCategory? = null
    private var categoryLoadVersion = 0
    private var homeCountVersion = 0
    private var findVersion = 0
    private val categoryCountCache = HashMap<String, Int>()
    private val categoryCountViews = HashMap<String, TextView>()

    private val operationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val event = intent ?: return
            val eventId = event.getStringExtra(OperationService.EXTRA_ID) ?: return
            val state = event.getStringExtra(OperationService.EXTRA_STATE).orEmpty()
            val completed = event.getIntExtra(OperationService.EXTRA_COMPLETED, 0)
            val total = event.getIntExtra(OperationService.EXTRA_TOTAL, 0)
            val completedBytes = event.getLongExtra(OperationService.EXTRA_COMPLETED_BYTES, 0L)
            val totalBytes = event.getLongExtra(OperationService.EXTRA_TOTAL_BYTES, 0L)
            val message = event.getStringExtra(OperationService.EXTRA_MESSAGE).orEmpty()
            if (state == OperationState.RUNNING.name && activeOperationId == null) {
                activeOperationId = eventId
                activeOperationLabel = operationLabel(event.getStringExtra(OperationService.EXTRA_KIND).orEmpty())
                showOperationProgressDialog(
                    activeOperationLabel,
                    operationProgressText(completed, total, message, completedBytes, totalBytes),
                    eventId
                )
            }
            if (eventId != activeOperationId) return
            if (state == OperationState.RUNNING.name) {
                activeTransferStatus?.text = operationProgressText(completed, total, message, completedBytes, totalBytes)
                return
            }
            activeOperationId = null
            activeTransferStatus = null
            paneDialog?.dismiss()
            selectedPaths.clear()
            pendingCopyPaths.clear()
            pendingMovePaths.clear()
            val showTrashAfter = activeOperationLabel in setOf("RESTORE", "DELETE") && rightVirtualTitle == "TRASH"
            if (showTrashAfter) showTrashContents(recordHistory = false) else reloadAll()
            updateSelectionBar()
            val title = activeOperationLabel
            activeOperationLabel = "OPERATION"
            showOutput(title, when (state) {
                OperationState.COMPLETED.name -> "$completed of $total completed${if (message.isBlank()) "" else "\n$message"}"
                OperationState.CANCELLED.name -> "Operation cancelled after $completed of $total"
                else -> message.ifBlank { "Operation failed" }
            })
        }
    }

    private var bgColor = Color.rgb(14, 14, 16)
    private var panelColor = Color.rgb(55, 55, 55)
    private var textColor = Color.rgb(200, 196, 151)
    private var borderColor = Color.rgb(170, 170, 170)
    private var inputBgColor = Color.TRANSPARENT
    private var inputTextColor = textColor
    private var outputPanelColor = panelColor
    private var outputTextColor = textColor
    private var outputBorderColor = borderColor
    private var fileTextColor = Color.rgb(210, 210, 210)
    private var directoryTextColor = textColor
    private var selectionBgColor = Color.rgb(0, 96, 96)
    private var selectionTextColor = Color.WHITE
    private var modulePanelColor = Color.rgb(55, 55, 55)
    private var moduleTextColor = textColor
    private var moduleBorderColor = borderColor
    private var headerPanelColor = Color.rgb(72, 72, 72)
    private var headerTextColor = textColor
    private var moduleButtonBgColor = Color.rgb(72, 72, 72)
    private var moduleButtonTextColor = textColor
    private var moduleButtonBorderColor = borderColor
    private var headerTextSizeSp = 14
    private var outputTextSizeSp = 13
    private var inputFontSizeSp = 13
    private var fontScaleOffsetSp = 0
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
    private var crtVignette = true
    private var appFontPath: String? = null
    private var appFontName: String? = null
    private var appTypeface: Typeface? = Typeface.MONOSPACE
    private var iconTypeface: Typeface? = null
    private var firstResume = true
    private var floatingWindow = false
    private var floatingFullscreen = false
    private var fullscreenControl: FrameLayout? = null
    private var fullscreenIcon: TextView? = null
    private var floatingPanel: FloatingFilesPanel? = null
    private var floatingHost: FrameLayout? = null
    private var floatingBounds: FloatingFractions? = null
    private var keyboardInsetExtra = 0
    private var legacyFloatingHeight = 0
    private var legacyFloatingWidth = 0
    private var workspaceFrame: FrameLayout? = null
    private var launcherPanelColor = modulePanelColor
    private var launcherPanelBorderColor = moduleBorderColor
    private var launcherHeaderColor = headerPanelColor
    private var launcherHeaderTextColor = headerTextColor
    private var launcherHeaderTextSizeSp = headerTextSizeSp
    private var dashedBorders = false
    private var dashLengthDp = 6
    private var dashGapDp = 4
    private var borderStrokeDp = 1.5f
    private var showModifiedColumn = true
    private var launcherFrameRuntime: FilesFrameRuntime? = null

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
        floatingWindow = this is FloatingFilesActivity
        setTheme(if (floatingWindow) R.style.AppTheme_Floating else R.style.AppTheme)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        LauncherFrameStore(this).process(intent)
        launcherFrameRuntime = FilesFrameRuntime.load(this)
        floatingFullscreen = themePrefs().getBoolean("floating.fullscreen", false)
        configureWindow()
        applyThemeExtras(intent)
        if (floatingWindow) {
            val prefs = themePrefs()
            fun fraction(key: String, fallback: Float) = if (savedInstanceState?.containsKey("floating.$key") == true)
                savedInstanceState.getFloat("floating.$key") else prefs.getFloat("floating.$key", fallback)
            floatingBounds = FloatingFractions(fraction("left", -1f), fraction("top", -1f), fraction("width", -1f), fraction("height", -1f)).takeIf { it.isValid() }
        }
        configureIncomingMode(intent)
        val start = resolveStartDirectory(intent)
        leftPane = PaneState(start)
        rightPane = PaneState(start)
        rightSortMode = savedSortMode()
        showHidden = themePrefs().getBoolean("show_hidden", false)
        setContentView(buildUi())
        registerOperationReceiver()
        restoreActiveOperation()
        rootView?.requestFocus()
        runTrashCleanup()
        displayIncomingIntent(intent, start)
        val walkthroughComplete = themePrefs().getBoolean(PREF_WALKTHROUGH_COMPLETE, false)
        val incomingRequest = shouldOpenTree(intent) || (intent?.action != null && intent?.action != Intent.ACTION_MAIN)
        val showFirstRunTour = shouldShowSetup(
            completed = walkthroughComplete,
            floatingWindow = floatingWindow,
            restoringState = savedInstanceState != null,
            incomingRequest = incomingRequest,
            storageGranted = hasStorageAccess()
        )
        if (showFirstRunTour) showSetup(0, firstRun = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = android.window.OnBackInvokedCallback { handleBackNavigation() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        paneDialog?.dismiss()
        appearanceReturnForm = null
        LauncherFrameStore(this).process(intent)
        launcherFrameRuntime = FilesFrameRuntime.load(this)
        selectedPaths.clear()
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        activeArchive = null
        archiveEntries = emptyList()
        archiveDirectory = ""
        pendingArchiveExtraction = null
        shareSelectionMode = false
        applyThemeExtras(intent)
        configureIncomingMode(intent)
        val start = resolveStartDirectory(intent)
        leftPane = PaneState(start)
        rightPane = PaneState(start)
        setContentView(buildUi())
        displayIncomingIntent(intent, start)
        // Explain and grant inside the FM window rather than jumping to Android settings.
        if (!hasStorageAccess()) showSetup(0, firstRun = true)
    }

    override fun onResume() {
        super.onResume()
        pendingApkPath?.let { path ->
            pendingApkPath = null
            if (packageManager.canRequestPackageInstalls()) {
                openFile(File(path))
                return
            }
            showOutput("INSTALL", "Allow Re:T-UI Files to install unknown apps, then open the APK again.")
        }
        if (setupOverlay != null && setupStep == 0) showSetup(0, setupFirstRun)
        if (firstResume) {
            firstResume = false
        } else if (::leftPane.isInitialized) {
            // Returning from another app (Open with, settings) refreshes in place instead of jumping to the top.
            val category = activeCategory
            when {
                currentScreen == Screen.HOME -> showHome(recordHistory = false)
                category != null -> {
                    pendingListPosition = currentListPosition()
                    val loadVersion = ++categoryLoadVersion
                    Thread { loadCategoryRows(category, loadVersion) }.start()
                }
                // Search results, Trash, providers and archives keep their rows; re-running them would lose context.
                activeArchive != null || rightVirtualTitle != null -> refreshSelectionHighlights()
                else -> {
                    pendingListPosition = currentListPosition()
                    reloadAll()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FONT_IMPORT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let(::importSelectedFont)
        }
        if (requestCode == PROVIDER_ROOT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                ProviderRoots.add(this, uri, data.flags)
                reloadAll()
                showOutput("PROVIDER", "Added provider folder:\n$uri")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayoutMode()
        if (currentScreen == Screen.TREE) {
            if (paneDialog == null) reflowTreePanes() else pendingPaneRelayout = true
        }
        floatingPanel?.post { floatingPanel?.fit() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        floatingBounds?.let { bounds ->
            outState.putFloat("floating.left", bounds.left)
            outState.putFloat("floating.top", bounds.top)
            outState.putFloat("floating.width", bounds.width)
            outState.putFloat("floating.height", bounds.height)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        activeSearchCancellation?.set(true)
        if (operationReceiverRegistered) {
            unregisterReceiver(operationReceiver)
            operationReceiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        activeSearchCancellation?.let {
            it.set(true)
            return
        }
        activeOperationId?.let {
            cancelOperation(it)
            return
        }
        if (setupOverlay != null) {
            if (setupStep == 1) showSetup(0, setupFirstRun) else finishSetup()
            return
        }
        if (quickPreview != null) return dismissQuickPreview()
        paneDialog?.let { it.dismiss(); restoreAppearanceForm(); return }
        if (selectedPaths.isNotEmpty() || shareSelectionMode) {
            closeShareSelection()
            return
        }
        if ((pendingCopyPaths.isNotEmpty() || pendingMovePaths.isNotEmpty()) && currentScreen == Screen.TREE && activeArchive == null && activeProvider == null) {
            goParent()
            return
        }
        if (navigationHistory.isNotEmpty()) {
            restoreNavigation(navigationHistory.removeLast())
            return
        }
        if (currentScreen != Screen.HOME) {
            showHome(recordHistory = false)
            return
        }
        finish()
    }

    private fun configureWindow() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }

    private fun registerOperationReceiver() {
        ContextCompat.registerReceiver(
            this,
            operationReceiver,
            IntentFilter(OperationService.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        operationReceiverRegistered = true
    }

    private fun restoreActiveOperation() {
        OperationStore.active(this)?.let { record ->
            activeOperationId = record.id
            activeOperationLabel = operationLabel(record.kind, record.move)
            showOperationProgressDialog(
                activeOperationLabel,
                operationProgressText(record.completed, record.sourceCount, record.current, record.completedBytes, record.totalBytes),
                record.id
            )
        }
        wakeOperationQueue()
    }

    private fun showOperationProgressDialog(title: String, statusText: String, id: String) {
        val panel = dialogPanel("$title in progress")
        val status = label(statusText, outputTextSizeSp, false)
        status.setSingleLine(false)
        status.contentDescription = "$title progress: $statusText"
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { cancelOperation(id) }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        activeTransferStatus = status
        showDialogPanel(panel)
    }

    private fun operationLabel(kind: String, move: Boolean = false): String = when (kind) {
        OperationService.KIND_ZIP -> "ZIP"
        OperationService.KIND_EXTRACT -> "EXTRACT"
        OperationService.KIND_TRASH -> "TRASH"
        OperationService.KIND_RESTORE -> "RESTORE"
        OperationService.KIND_DELETE -> "DELETE"
        else -> if (move) "MOVE" else "COPY"
    }

    private fun wakeOperationQueue() {
        if (OperationStore.nextQueued(this) == null) return
        val intent = Intent(this, OperationService::class.java).setAction(OperationService.ACTION_WAKE)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, intent)
            else startService(intent)
        }
    }

    private fun cancelOperation(id: String) {
        startService(Intent(this, OperationService::class.java).apply {
            action = OperationService.ACTION_CANCEL
            putExtra(OperationService.EXTRA_ID, id)
        })
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        rootView = root
        quickPreview = null
        setupOverlay = null
        root.isFocusableInTouchMode = true
        root.clipChildren = false
        root.clipToPadding = false
        root.setBackgroundColor(Color.TRANSPARENT)
        installWindowInsetsHandler(root)

        val main = LinearLayout(this)
        mainContainer = main
        main.orientation = LinearLayout.VERTICAL
        main.clipChildren = false
        main.clipToPadding = false
        main.setPadding(dp(2), 0, dp(2), dp(2))
        main.setBackgroundColor(outputPanelColor or 0xff000000.toInt())
        applyCrtForeground(main)
        val border = FrameLayout(this).apply {
            background = workspaceFrameDrawable()
            val edges = launcherFrameRuntime?.borderInsets ?: intArrayOf(0, 0, 0, 0)
            // Leave the shared frame visible without painting over MC controls.
            setPadding(max(dp(if (cyberdeckMode) 8 else 6), edges[0]), max(dp(18), edges[1]),
                max(dp(6), edges[2]), max(dp(6), edges[3]))
            addView(main, FrameLayout.LayoutParams(-1, -1))
        }
        workspaceBorder = border
        val header = buildWorkspaceHeader(main)
        val frame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(border, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(10); bottomMargin = dp(2) })
            addView(header, FrameLayout.LayoutParams(-1, dp(48)).apply { leftMargin = dp(12); rightMargin = dp(4); topMargin = -dp(14) })
        }
        workspaceFrame = frame
        val mainParams = FrameLayout.LayoutParams(-1, -1)
        applyMainMargins(mainParams)
        val menu = buildMenuBar()
        if (floatingWindow) {
            val host = FrameLayout(this)
            floatingHost = host
            root.addView(host, mainParams)
            floatingPanel = FloatingFilesPanel(this, host, frame, listOf(header, menu), { floatingBounds }, { bounds ->
                floatingBounds = bounds
                themePrefs().edit().putFloat("floating.left", bounds.left).putFloat("floating.top", bounds.top)
                    .putFloat("floating.width", bounds.width).putFloat("floating.height", bounds.height).apply()
            }, { floatingAvailableHeight() }, { floatingFullscreen }) { _, glyph ->
                label(glyph, outputTextSizeSp, false).apply {
                    gravity = Gravity.CENTER
                    setTextColor(launcherPanelBorderColor)
                    alpha = .22f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }
        } else root.addView(frame, mainParams)
        root.requestApplyInsets()

        menuBarView = menu
        main.addView(menu, LinearLayout.LayoutParams(-1, dp(32)))
        val workspace = FrameLayout(this)
        contentHost = FrameLayout(this)
        workspace.addView(contentHost, FrameLayout.LayoutParams(-1, -1))
        val selectionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buildSelectionBar(), FrameLayout.LayoutParams(-2, -1))
        }
        selectionScrollView = selectionScroll
        val selection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(headerPanelColor)
            addView(selectionScroll, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val close = label("X", outputTextSizeSp, true).apply {
            gravity = Gravity.CENTER
            setTextColor(moduleButtonTextColor)
            contentDescription = "Clear selection or cancel pending action"
            setOnClickListener {
                when {
                    activeSearchCancellation != null -> activeSearchCancellation?.set(true)
                    activeArchive != null -> clearArchiveSelection()
                    pendingArchiveExtraction != null -> cancelArchiveExtraction()
                    shareSelectionMode -> closeShareSelection()
                    else -> clearSelection()
                }
            }
        }
        selection.addView(close, LinearLayout.LayoutParams(dp(48), -1))
        selectionSurface = selection
        selection.visibility = selectionBar?.visibility ?: View.GONE
        workspace.addView(selection, FrameLayout.LayoutParams(-1, dp(32), Gravity.TOP))
        val workspaceParams = LinearLayout.LayoutParams(-1, 0, 1f)
        workspaceParams.topMargin = dp(1)
        main.addView(workspace, workspaceParams)
        main.addView(buildCommandLine(), LinearLayout.LayoutParams(-1, dp(32)))
        main.addView(buildFunctionBar(), LinearLayout.LayoutParams(-1, dp(64)))
        applyLayoutMode()
        return root
    }

    private fun treePanesMissing(): Boolean = rightListView == null || !compactLayout() && leftRowsView == null

    /** Phones get one full-width list with large targets; wide screens keep the two MC panels. */
    private fun compactLayout(): Boolean = resources.configuration.screenWidthDp < 600

    private fun applyLayoutMode() {
        val compact = compactLayout()
        menuBarView?.visibility = if (compact) View.GONE else View.VISIBLE
        selectionSurface?.let { surface ->
            // In compact mode selection actions sit above the command line, replacing the action bar.
            surface.layoutParams = FrameLayout.LayoutParams(-1, dp(if (compact) 48 else 32), if (compact) Gravity.BOTTOM else Gravity.TOP)
        }
        updateFunctionBar()
    }

    private fun buildSelectionBar(): LinearLayout {
        val row = LinearLayout(this)
        selectionBar = row
        row.id = R.id.fm_selection_bar
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(6), 0, 0, 0)
        row.background = toolbarDrawable()
        row.visibility = if (shareSelectionMode || activeArchive != null || pendingArchiveExtraction != null || selectedPaths.isNotEmpty() || pendingCopyPaths.isNotEmpty() || pendingMovePaths.isNotEmpty()) View.VISIBLE else View.GONE
        updateSelectionBar()
        return row
    }

    private fun updateSelectionBar() {
        val row = selectionBar ?: return
        updateFunctionBar()
        row.removeAllViews()
        val hasWork = activeSearchCancellation != null || shareSelectionMode || activeArchive != null || pendingArchiveExtraction != null || selectedPaths.isNotEmpty() || pendingCopyPaths.isNotEmpty() || pendingMovePaths.isNotEmpty()
        row.visibility = if (hasWork && currentScreen == Screen.TREE) View.VISIBLE else View.GONE
        selectionSurface?.visibility = row.visibility
        if (compactLayout()) {
            functionBar?.visibility = if (row.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            rightListView?.setPadding(0, 0, 0, if (row.visibility == View.VISIBLE) dp(48) else 0)
        }
        if (!hasWork) {
            selectionScrollView?.scrollTo(0, 0)
            return
        }
        activeSearchCancellation?.let {
            val count = label("Searching… $searchScanned scanned", max(10, outputTextSizeSp - 2), true)
            count.gravity = Gravity.CENTER_VERTICAL
            count.setTextColor(moduleButtonTextColor)
            row.addView(count, LinearLayout.LayoutParams(dp(selectionCountWidthDp()), -1))
            addSelectionAction(row, "CANCEL") { it.set(true) }
            return
        }
        activeArchive?.let { archive ->
            // The path bar already names the archive; the bar only needs what will be extracted.
            val count = label(if (selectedPaths.isEmpty()) "Whole archive" else "${selectedPaths.size} selected", max(10, outputTextSizeSp - 2), true)
            count.gravity = Gravity.CENTER_VERTICAL
            count.setTextColor(moduleButtonTextColor)
            row.addView(count, LinearLayout.LayoutParams(dp(selectionCountWidthDp() + 16), -1))
            addSelectionAction(row, "EXTRACT") { showExtractChoices(archive) }
            return
        }
        pendingArchiveExtraction?.let { request ->
            val text = if (request.selected.isEmpty()) request.archive.name else "${request.selected.size} ready to extract"
            val count = label(text, max(10, outputTextSizeSp - 2), true)
            count.gravity = Gravity.CENTER_VERTICAL
            count.setTextColor(moduleButtonTextColor)
            row.addView(count, LinearLayout.LayoutParams(dp(selectionCountWidthDp()), -1))
            addSelectionAction(row, "EXTRACT HERE") { confirmArchiveExtractHere() }
            return
        }
        val countText = when {
            shareSelectionMode && selectedPaths.isEmpty() -> "Select files to share"
            selectedPaths.isNotEmpty() -> "${selectedPaths.size} selected"
            pendingMovePaths.isNotEmpty() -> "${pendingMovePaths.size} ready to move"
            else -> "${pendingCopyPaths.size} ready to copy"
        }
        val count = label(countText, max(10, outputTextSizeSp - 2), true)
        count.gravity = Gravity.CENTER_VERTICAL
        count.setTextColor(moduleButtonTextColor)
        row.addView(count, LinearLayout.LayoutParams(dp(selectionCountWidthDp()), -1))
        if (shareSelectionMode) {
            if (selectedPaths.isNotEmpty()) addSelectionAction(row, "SHARE") { shareSelectedFiles() }
            return
        }
        if (selectedPaths.isNotEmpty()) {
            val selected = selectedFiles()
            if (selected.isNotEmpty() && selected.all(::isInTrash)) {
                addSelectionAction(row, "RESTORE") { confirmRestoreSelected() }
                addSelectionAction(row, "DELETE") { confirmDeleteSelectedPermanently() }
            } else {
                addSelectionAction(row, "COPY") { prepareCopy() }
                addSelectionAction(row, "MOVE") { prepareMove() }
                addSelectionAction(row, "TRASH") { confirmBulkTrash() }
                addSelectionAction(row, "SHARE") { shareSelectedFiles() }
                addSelectionAction(row, "ZIP") { promptZipSelected() }
            }
        } else if (pendingMovePaths.isNotEmpty()) {
            addSelectionAction(row, "MOVE HERE") { confirmMoveHere() }
        } else {
            addSelectionAction(row, "PASTE") { confirmPaste() }
        }
    }

    private fun selectionCountWidthDp(): Int = if (compactLayout()) 84 else 120

    private fun addSelectionAction(parent: LinearLayout, text: String, action: () -> Unit) {
        val button = label(text, max(9, outputTextSizeSp - 3), true)
        button.gravity = Gravity.CENTER
        button.setTextColor(moduleButtonTextColor)
        button.background = functionButtonBackground()
        button.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(-2, -1)
        params.leftMargin = dp(4)
        params.width = dp(
            when (text) {
                "EXTRACT ALL", "EXTRACT" -> 72
                "EXTRACT HERE" -> 76
                "EXTRACT TO" -> 68
                "MOVE HERE" -> 74
                "SHARE", "TRASH" -> 56
                "CANCEL" -> 64
                else -> 48
            }
        )
        parent.addView(button, params)
    }

    private fun buildTreePanes(): View {
        tappedLeftEntry = null
        val panes = LinearLayout(this)
        panes.orientation = LinearLayout.HORIZONTAL
        if (compactLayout()) {
            // The left folder list duplicates the right one on narrow screens; leave it unbuilt.
            leftTitleView = null
            leftRowsView = null
            leftScrollView = null
            leftFooterView = null
            leftDiskView = null
            activePanel = Panel.RIGHT
            rightWorkspace = FrameLayout(this).also { host ->
                host.addView(buildPane(Panel.RIGHT), FrameLayout.LayoutParams(-1, -1))
                panes.addView(host, LinearLayout.LayoutParams(-1, -1))
            }
            return panes
        }
        breadcrumbRow = null
        val leftParams = LinearLayout.LayoutParams(0, -1, 0.38f)
        val rightParams = LinearLayout.LayoutParams(0, -1, 0.62f)
        rightParams.leftMargin = dp(4)
        panes.addView(buildPane(Panel.LEFT), leftParams)
        rightWorkspace = FrameLayout(this).also { host ->
            host.addView(buildPane(Panel.RIGHT), FrameLayout.LayoutParams(-1, -1))
            panes.addView(host, rightParams)
        }
        return panes
    }

    private fun reflowTreePanes() {
        pendingPaneRelayout = false
        val leftScroll = leftScrollView?.scrollY ?: 0
        val first = rightListView?.firstVisiblePosition ?: 0
        val offset = rightListView?.getChildAt(0)?.top ?: 0
        contentHost?.removeAllViews()
        contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
        renderAll()
        updateSelectionBar()
        leftScrollView?.post { leftScrollView?.scrollTo(0, leftScroll) }
        rightListView?.post { rightListView?.setSelectionFromTop(first, offset) }
    }

    private fun installWindowInsetsHandler(root: FrameLayout) {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safe = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                intArrayOf(safe.left, safe.top, safe.right, safe.bottom)
            } else FmVisualInterop.safeInsets(insets).apply {
                @Suppress("DEPRECATION")
                val cutoutBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout?.safeInsetBottom ?: 0 else 0
                this[3] = min(this[3], max(insets.stableInsetBottom.coerceAtLeast(0), cutoutBottom))
            }
            systemInsetLeft = safeInsets[0]
            systemInsetTop = safeInsets[1]
            systemInsetRight = safeInsets[2]
            systemInsetBottom = safeInsets[3]
            keyboardInsetExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                (ime - bars).coerceAtLeast(0)
            } else {
                @Suppress("DEPRECATION")
                (insets.systemWindowInsetBottom - systemInsetBottom).coerceAtLeast(0)
            }
            root.setPadding(systemInsetLeft, 0, systemInsetRight, systemInsetBottom + keyboardInsetExtra)
            updateMainMargins()
            floatingPanel?.post { floatingPanel?.fit() }
            insets
        }
    }

    private fun updateMainMargins() {
        val target = if (floatingWindow) floatingHost else workspaceFrame
        val params = target?.layoutParams as? FrameLayout.LayoutParams ?: return
        val previous = listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
        applyMainMargins(params)
        if (previous != listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)) {
            target.layoutParams = params
            floatingPanel?.post { floatingPanel?.fit() }
        }
    }

    private fun applyMainMargins(params: FrameLayout.LayoutParams) {
        params.setMargins(
            dp(8) + displayMarginLeftPx,
            // Floating chrome already reserves its header/gutter; avoid the legacy panel offset.
            systemInsetTop + (if (floatingWindow) 0 else dp(topMarginDp)) + displayMarginTopPx,
            dp(8) + displayMarginRightPx,
            dp(8) + displayMarginBottomPx
        )
    }

    private fun floatingAvailableHeight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val host = floatingHost ?: return 0
            if (keyboardInsetExtra == 0) {
                legacyFloatingHeight = host.height
                legacyFloatingWidth = host.width
            }
            return if (legacyFloatingWidth == host.width && legacyFloatingHeight > 0) legacyFloatingHeight
                else host.height + keyboardInsetExtra
        }
        return windowManager.currentWindowMetrics.bounds.height() - systemInsetTop - systemInsetBottom -
            displayMarginTopPx - dp(8) - displayMarginBottomPx
    }

    private fun buildWorkspaceHeader(content: View): View = object : FrameLayout(this) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val bounds = android.graphics.Rect()
            if (event.actionMasked == MotionEvent.ACTION_DOWN && content.getGlobalVisibleRect(bounds) &&
                bounds.contains(event.rawX.toInt(), event.rawY.toInt())) return false
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        fun tab(text: String, description: String, action: (() -> Unit)? = null): TextView =
            label(text, launcherHeaderTextSizeSp, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                setTextColor(launcherHeaderTextColor)
                contentDescription = description
                val fill = GradientDrawable().apply {
                    setColor(launcherHeaderColor)
                    cornerRadius = dp(headerCornerRadiusDp).toFloat()
                }
                background = launcherFrameRuntime?.drawable(fill) ?: fill
                action?.let { setOnClickListener { it() } }
            }
        addView(tab("RE:TUI FM", "RE:TUI FM window").apply { ellipsize = TextUtils.TruncateAt.END },
            FrameLayout.LayoutParams(-2, dp(20), Gravity.LEFT).apply { topMargin = dp(14); rightMargin = dp(if (floatingWindow) 144 else 96) })
        fun control(text: String, description: String, rightMargin: Int, action: () -> Unit): FrameLayout {
            val target = FrameLayout(this@MainActivity).apply {
                contentDescription = description
                isFocusable = true
                setOnClickListener { action() }
                val icon = tab(text, description).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                if (rightMargin == 96) fullscreenIcon = icon
                addView(icon,
                    FrameLayout.LayoutParams(-1, dp(20)).apply { topMargin = dp(14) })
            }
            addView(target, FrameLayout.LayoutParams(dp(48), -1, Gravity.RIGHT).apply { this.rightMargin = dp(rightMargin) })
            return target
        }
        control("×", "Close RE:TUI FM", 48) { finish() }
        control("?", "RE:TUI FM help", 0) { showHelpPopup() }
        if (floatingWindow) {
            fullscreenControl = control("□", "Fullscreen RE:TUI FM", 96) {
                floatingFullscreen = !floatingFullscreen
                themePrefs().edit().putBoolean("floating.fullscreen", floatingFullscreen).apply()
                updateFullscreenControl()
                configureWindow()
                rootView?.requestApplyInsets()
                floatingPanel?.fit()
            }
            updateFullscreenControl()
        }
    }

    private fun updateFullscreenControl() {
        fullscreenControl?.contentDescription = if (floatingFullscreen) "Restore floating RE:TUI FM" else "Fullscreen RE:TUI FM"
        fullscreenIcon?.text = if (floatingFullscreen) "▣" else "□"
    }

    private fun buildMenuBar(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundColor(headerPanelColor)
        addMenuButton(row, "Places") { showPlacesMenu() }
        addMenuButton(row, "File") { showFileMenu() }
        addMenuButton(row, "Command") { showCommandMenu() }
        addMenuButton(row, "Options") { showOptionsMenu() }
        addMenuButton(row, "Sort") { showSortMenu() }
        return row
    }

    private fun buildCommandLine(): View {
        val command = label("", max(11, outputTextSizeSp), false)
        command.id = R.id.fm_command_button
        command.gravity = Gravity.CENTER_VERTICAL
        command.setPadding(dp(8), 0, dp(8), 0)
        command.setTextColor(inputTextColor)
        command.setBackgroundColor(outputPanelColor)
        commanderSkin?.style("core", "commandline")?.displayed?.let { styleCommanderText(command, it, background = true) }
        command.contentDescription = "Open file command palette"
        command.setOnClickListener { promptCommand() }
        commandLineView = command
        return command
    }

    private fun buildFunctionBar(): View {
        val bar = LinearLayout(this)
        bar.id = R.id.fm_function_bar
        bar.orientation = LinearLayout.VERTICAL
        functionBar = bar
        updateFunctionBar()
        return bar
    }

    private fun updateFunctionBar() {
        val bar = functionBar ?: return
        bar.removeAllViews()
        if (compactLayout()) {
            buildActionBar(bar)
            return
        }
        bar.visibility = View.VISIBLE
        bar.layoutParams?.let { it.height = dp(64); bar.layoutParams = it }
        val names = listOf("Help", "Menu", "View", "Edit",
            if (pendingCopyPaths.isNotEmpty()) "Paste" else "Copy",
            if (pendingMovePaths.isNotEmpty()) "Move here" else "Move",
            "Mkdir", "Trash", "Menus", "Quit")
        for (group in names.indices.chunked(5)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (index in group) {
                val button = label("${index + 1} ${names[index]}", outputTextSizeSp, false)
                button.id = View.generateViewId()
                button.gravity = Gravity.CENTER
                button.setSingleLine(false)
                button.maxLines = 2
                button.setTextColor(moduleButtonTextColor)
                button.setBackgroundColor(moduleButtonBgColor)
                commanderSkin?.style("buttonbar", "button")?.displayed?.let { styleCommanderText(button, it, background = true) }
                button.contentDescription = "F${index + 1} ${names[index]}"
                button.setOnClickListener { runFunctionAction(index + 1) }
                val params = LinearLayout.LayoutParams(0, -1, 1f)
                params.setMargins(dp(1), dp(1), dp(1), dp(1))
                row.addView(button, params)
            }
            bar.addView(row, LinearLayout.LayoutParams(-1, dp(32)))
        }
    }

    private fun buildActionBar(bar: LinearLayout) {
        actionBarScreen = currentScreen
        bar.layoutParams?.let { it.height = dp(40); bar.layoutParams = it }
        bar.setBackgroundColor(outputPanelColor or 0xff000000.toInt())
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val lastFolder = NavigationStateStore.last(themePrefs()) ?: homeDirectory()
        val actions = if (currentScreen == Screen.HOME) listOf(
            Triple("Browse", "Browse ${lastFolder.name.ifBlank { "storage" }}") { navigateMain(lastFolder) },
            Triple("Places", "Places and bookmarks") { showPlacesMenu() },
            Triple("Search", "Search all files") { promptSearch(null, "Search all storage") },
            Triple("More", "More menus") { showMoreMenu() }
        ) else listOf(
            Triple("Places", "Places and bookmarks") { showPlacesMenu() },
            Triple("Search", "Search this folder") { promptSearch(contentPane().directory, "Search current folder") },
            Triple("Sort", "Sort order") { showSortMenu() },
            Triple("New", "New folder or file") {
                showActionMenu("New", listOf(
                    "Folder" to { promptMkdir() },
                    "Text file" to { promptNewTextFile() }
                ))
            },
            Triple("More", "More menus") { showMoreMenu() }
        )
        for ((text, description, action) in actions) {
            val button = label(text, outputTextSizeSp, true)
            button.id = View.generateViewId()
            button.gravity = Gravity.CENTER
            button.setTextColor(moduleButtonTextColor)
            button.setBackgroundColor(moduleButtonBgColor)
            commanderSkin?.style("buttonbar", "button")?.displayed?.let { styleCommanderText(button, it, background = true) }
            // Compact chips sized to their label; the full bar height stays tappable.
            val fill = (button.background as? ColorDrawable)?.color ?: moduleButtonBgColor
            button.background = android.graphics.drawable.InsetDrawable(ColorDrawable(fill), 0, dp(6), 0, dp(6))
            button.setPadding(dp(12), 0, dp(12), 0)
            button.contentDescription = description
            button.setOnClickListener { action() }
            if (row.childCount > 0) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            row.addView(button, LinearLayout.LayoutParams(-2, -1))
        }
        // Every chip takes the widest label's width so the bar reads as one even set.
        val chips = (0 until row.childCount).map(row::getChildAt).filterIsInstance<TextView>()
        val chipWidth = chips.maxOfOrNull { it.paint.measureText(it.text.toString()).toInt() + it.paddingLeft + it.paddingRight } ?: 0
        chips.forEach { it.layoutParams = LinearLayout.LayoutParams(chipWidth, -1) }
        row.setPadding(dp(6), 0, dp(6), 0)
        row.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            // Narrow floating windows: share the width equally instead of pushing the last chip off-screen.
            val needed = chips.size * chipWidth + view.paddingLeft + view.paddingRight
            if (right - left in 1 until needed && chips.first().layoutParams.width != 0) {
                view.post {
                    chips.forEach { chip ->
                        chip.setPadding(dp(2), 0, dp(2), 0)
                        chip.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
                    }
                }
            }
        }
        bar.addView(row, LinearLayout.LayoutParams(-1, -1))
    }

    private fun showMoreMenu() {
        showActionMenu("Menus", listOf(
            "File" to { showFileMenu() },
            "Selection" to { showEditMenu() },
            "View" to { showViewMenu() },
            "Command" to { showCommandMenu() },
            "Options" to { showOptionsMenu() },
            "Help" to { showHelpPopup() },
            "Close RE:TUI FM" to { finish() }
        ))
    }

    private fun runFunctionAction(number: Int) {
        when (number) {
            1 -> showHelpPopup()
            2 -> showEditMenu()
            3 -> openSelected()
            4 -> withSelected("EDIT") { editFile(it) }
            5 -> if (pendingCopyPaths.isNotEmpty()) confirmPaste() else actOnMarkedOrCurrent(::prepareCopy)
            6 -> if (pendingMovePaths.isNotEmpty()) confirmMoveHere() else actOnMarkedOrCurrent(::prepareMove)
            7 -> promptMkdir()
            8 -> actOnMarkedOrCurrent {
                if (selectedFiles().all(::isInTrash)) confirmDeleteSelectedPermanently() else confirmBulkTrash()
            }
            9 -> showActionMenu("Menus", listOf(
                "Places" to { showPlacesMenu() }, "File" to { showFileMenu() },
                "Command" to { showCommandMenu() }, "Options" to { showOptionsMenu() },
                "Sort" to { showSortMenu() }
            ))
            10 -> finish()
        }
    }

    private fun actOnMarkedOrCurrent(action: () -> Unit) {
        if (activeArchive != null) {
            showActionMenu("Archive", listOf("Extract" to { extractArchiveNamed() }, "Extract to" to { prepareArchiveExtractTo() }))
            return
        }
        if (selectedPaths.isEmpty()) {
            val entry = activePane().rows.getOrNull(activePane().cursor)
            entry?.providerItem?.let { showProviderItemMenu(it, entry.providerRootUri); return }
            val file = entry?.file
            if (entry == null || entry.isParent || entry.isSection || entry.isTrashPlace || file == null || !file.exists()) {
                showOutput("SELECT", "Select a file or folder first.")
                return
            }
            toggleSelection(file)
        }
        action()
    }

    private fun buildPane(panel: Panel): View {
        val pane = LinearLayout(this)
        pane.orientation = LinearLayout.VERTICAL
        val framePadding = if (commanderSkin?.doubleFrame == true) 4 else 3
        pane.setPadding(dp(framePadding), dp(framePadding), dp(framePadding), dp(framePadding))
        pane.background = CommanderFrameDrawable(outputPanelColor, outputBorderColor, dpFloat(1f), commanderSkin?.doubleFrame == true)
        pane.setOnClickListener { switchPanel(panel) }
        val title = label("", outputTextSizeSp, false)
        title.gravity = Gravity.CENTER_VERTICAL
        title.ellipsize = TextUtils.TruncateAt.START
        title.setPadding(dp(4), 0, dp(4), 0)
        title.contentDescription = if (panel == Panel.LEFT) "Folder browser menu" else "Path and breadcrumbs"
        title.setOnClickListener { if (panel == Panel.LEFT) showFolderBrowserMenu() else showBreadcrumbMenu() }
        val compact = panel == Panel.RIGHT && compactLayout()
        if (compact) {
            pane.addView(buildBreadcrumbBar(), LinearLayout.LayoutParams(-1, dp(48)))
            rightTitleView = null
            rightNameSortView = null
            rightModifiedSortView = null
        } else {
            pane.addView(title, LinearLayout.LayoutParams(-1, dp(32)))
            if (panel == Panel.LEFT) leftTitleView = title else rightTitleView = title
        }

        if (panel == Panel.LEFT) {
            val header = LinearLayout(this)
            header.orientation = LinearLayout.HORIZONTAL
            header.addView(column("Name", 1f, Gravity.START))
            pane.addView(header, LinearLayout.LayoutParams(-1, dp(20)))
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
            if (!compact) pane.addView(buildRightSortBar(), LinearLayout.LayoutParams(-1, dp(20)))
            val grid = ListView(this)
            grid.id = R.id.fm_file_list
            grid.divider = null
            grid.dividerHeight = 0
            grid.clipToPadding = false
            grid.isVerticalScrollBarEnabled = true
            grid.cacheColorHint = Color.TRANSPARENT
            grid.setOnItemClickListener { _, _, position, _ ->
                tappedLeftEntry = null
                activePanel = Panel.RIGHT
                rightPane.cursor = position
                clampCursor(rightPane)
                val entry = rightPane.rows.getOrNull(position)
                if (shareSelectionMode || selectedPaths.isNotEmpty()) {
                    entry?.let(::toggleSelection)
                } else {
                    showRightCursorHighlight = true
                    openSelected()
                }
            }
            grid.setOnItemLongClickListener { _, _, position, _ ->
                tappedLeftEntry = null
                activePanel = Panel.RIGHT
                rightPane.cursor = position
                showRightCursorHighlight = false
                rightPane.rows.getOrNull(position)?.let { entry ->
                    if (entry.providerItem != null) showProviderItemMenu(entry.providerItem, entry.providerRootUri)
                    else if (!entry.isParent) toggleSelection(entry)
                }
                true
            }
            pane.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
            rightListView = grid
            grid.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val visible = right - left >= dp(240)
                rightModifiedSortView?.visibility = if (visible) View.VISIBLE else View.GONE
                if (visible != showModifiedColumn) {
                    showModifiedColumn = visible
                    val first = grid.firstVisiblePosition
                    val offset = grid.getChildAt(0)?.top ?: 0
                    rightListAdapter?.notifyDataSetChanged()
                    grid.post { grid.setSelectionFromTop(first, offset) }
                }
            }
        }

        val footer = label("", max(10, outputTextSizeSp - 2), true)
        footer.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        footer.setPadding(dp(4), 0, dp(4), 0)
        footer.ellipsize = TextUtils.TruncateAt.END
        footer.background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(max(1, dp(1)), outputBorderColor) }
        // Compact rows already show full names, so the selected-name footer is redundant there.
        if (!compact) pane.addView(footer, LinearLayout.LayoutParams(-1, dp(20)))
        val disk = label("", max(10, outputTextSizeSp - 2), true)
        disk.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        pane.addView(disk, LinearLayout.LayoutParams(-1, dp(20)))

        if (panel == Panel.LEFT) {
            leftFooterView = footer
            leftDiskView = disk
        } else {
            rightFooterView = footer
            rightDiskView = disk
        }
        return pane
    }

    private fun breadcrumbTextColor(): Int =
        commanderSkin?.style("core", "header")?.displayed?.foreground ?: headerTextColor

    private fun buildBreadcrumbBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(headerPanelColor)
        commanderSkin?.style("core", "header")?.displayed?.let { style -> bar.setBackgroundColor(style.background) }
        val up = label("<", outputTextSizeSp + 2, true)
        up.gravity = Gravity.CENTER
        up.setTextColor(breadcrumbTextColor())
        up.contentDescription = "Up one folder"
        up.setOnClickListener { goParent() }
        bar.addView(up, LinearLayout.LayoutParams(dp(48), -1))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val crumbs = LinearLayout(this)
        crumbs.orientation = LinearLayout.HORIZONTAL
        crumbs.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(crumbs, FrameLayout.LayoutParams(-2, -1))
        bar.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        breadcrumbRow = crumbs
        return bar
    }

    private fun renderBreadcrumbs() {
        val crumbs = breadcrumbRow ?: return
        crumbs.removeAllViews()
        fun crumb(text: String, current: Boolean, action: (() -> Unit)?) {
            val view = label(text, outputTextSizeSp, current)
            view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(6), 0, dp(6), 0)
            view.setTextColor(if (current) breadcrumbTextColor() else withAlpha(breadcrumbTextColor(), 200))
            action?.let { view.setOnClickListener { it() } }
            crumbs.addView(view, LinearLayout.LayoutParams(-2, -1))
        }
        fun separator() = crumbs.addView(label("/", outputTextSizeSp, false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(withAlpha(breadcrumbTextColor(), 150))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-2, -1))
        val virtual = rightVirtualTitle
        crumb("Home", false) { showHome() }
        separator()
        val archive = activeArchive
        if (currentScreen == Screen.TREE && archive != null) {
            folderCrumbs(archive.parentFile ?: homeDirectory(), ::crumb, ::separator, currentIsLink = true)
            separator()
            val inside = archiveDirectory.split('/').filter { it.isNotEmpty() }
            crumb(archive.name, inside.isEmpty(), if (inside.isEmpty()) null else ({ showArchiveDirectory("") }))
            inside.forEachIndexed { index, name ->
                separator()
                val path = inside.take(index + 1).joinToString("/")
                val current = index == inside.lastIndex
                crumb(name, current, if (current) null else ({ showArchiveDirectory(path) }))
            }
            (crumbs.parent as? HorizontalScrollView)?.let { scroll -> scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) } }
            return
        }
        if (currentScreen != Screen.TREE || virtual != null || activeProvider != null) {
            crumb(virtual ?: activeArchive?.name ?: activeProvider?.displayName ?: "Home", true, null)
            return
        }
        folderCrumbs(rightPane.directory, ::crumb, ::separator, currentIsLink = false)
        (crumbs.parent as? HorizontalScrollView)?.let { scroll -> scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) } }
    }

    private fun folderCrumbs(
        dir: File,
        crumb: (String, Boolean, (() -> Unit)?) -> Unit,
        separator: () -> Unit,
        currentIsLink: Boolean
    ) {
        val home = homeDirectory()
        val chain = ArrayList<File>()
        var cursor: File? = dir
        while (cursor != null) {
            chain += cursor
            if (cursor == home) break
            cursor = cursor.parentFile
        }
        chain.asReversed().forEachIndexed { index, folder ->
            if (index > 0) separator()
            val name = if (folder == home) "~" else folder.name.ifBlank { folder.absolutePath }
            val current = folder == dir && !currentIsLink
            crumb(name, current, if (current) null else ({ showDirectoryContents(folder) }))
        }
    }

    private fun buildRightSortBar(): View {
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        val name = column("Name", 1f, Gravity.START)
        name.contentDescription = "Sort by name"
        name.setOnClickListener {
            rightSortMode = if (rightSortMode == SortMode.NAME_ASC) SortMode.NAME_DESC else SortMode.NAME_ASC
            sortRightPane()
        }
        bar.addView(name)
        rightNameSortView = name
        val size = column("Size", 0f, Gravity.END)
        size.layoutParams = LinearLayout.LayoutParams(dp(54), -1)
        size.contentDescription = "Sort order"
        size.setOnClickListener { showSortMenu() }
        bar.addView(size)
        run {
            val date = column("Modified", 0f, Gravity.CENTER)
            date.layoutParams = LinearLayout.LayoutParams(dp(84), -1)
            date.contentDescription = "Sort by modified date"
            date.setOnClickListener {
                rightSortMode = if (rightSortMode == SortMode.MODIFIED_NEWEST) SortMode.MODIFIED_OLDEST else SortMode.MODIFIED_NEWEST
                sortRightPane()
            }
            bar.addView(date)
            rightModifiedSortView = date
            date.visibility = if (showModifiedColumn) View.VISIBLE else View.GONE
        }
        updateSortButtons()
        return bar
    }

    private fun showSortMenu() {
        showActionMenu("Sort order", listOf(
            "Name A–Z" to { rightSortMode = SortMode.NAME_ASC; sortRightPane() },
            "Name Z–A" to { rightSortMode = SortMode.NAME_DESC; sortRightPane() },
            "Newest first" to { rightSortMode = SortMode.MODIFIED_NEWEST; sortRightPane() },
            "Oldest first" to { rightSortMode = SortMode.MODIFIED_OLDEST; sortRightPane() },
            "Largest first" to { rightSortMode = SortMode.SIZE_LARGEST; sortRightPane() },
            "Smallest first" to { rightSortMode = SortMode.SIZE_SMALLEST; sortRightPane() },
            "File type" to { rightSortMode = SortMode.TYPE; sortRightPane() }
        ))
    }

    private fun updateSortButtons() {
        rightNameSortView?.apply {
            alpha = if (rightSortMode == SortMode.NAME_ASC || rightSortMode == SortMode.NAME_DESC) 1f else 0.5f
            text = if (rightSortMode == SortMode.NAME_DESC) "Name ↓" else "Name ↑"
            contentDescription = if (rightSortMode == SortMode.NAME_DESC) "Name descending" else "Name ascending"
        }
        rightModifiedSortView?.apply {
            alpha = if (rightSortMode == SortMode.MODIFIED_NEWEST || rightSortMode == SortMode.MODIFIED_OLDEST) 1f else 0.5f
            text = "Modified"
            contentDescription = if (rightSortMode == SortMode.MODIFIED_OLDEST) "Oldest modified first" else "Recently modified first"
        }
    }

    private fun sortRightPane() {
        themePrefs().edit().putString(PREF_RIGHT_SORT_MODE, rightSortMode.name).apply()
        val sorted = sortedEntries(rightPane.rows)
        rightPane.rows.clear()
        rightPane.rows.addAll(sorted)
        rightPane.cursor = 0
        updateSortButtons()
        renderPane(Panel.RIGHT)
    }

    private fun savedSortMode(): SortMode =
        SortMode.values().firstOrNull { it.name == themePrefs().getString(PREF_RIGHT_SORT_MODE, null) }
            ?: SortMode.NAME_ASC

    private fun copyPane(pane: PaneState) = PaneState(pane.directory, ArrayList(pane.rows), pane.cursor, pane.summary)

    private fun captureNavigation() = NavigationSnapshot(
        currentScreen, copyPane(leftPane), copyPane(rightPane), activePanel, rightVirtualTitle,
        activeCategory, activeArchive, archiveEntries, archiveDirectory
    )

    private fun rememberNavigation() {
        if (suppressNavigationHistory) return
        if (navigationHistory.size == 32) navigationHistory.removeFirst()
        navigationHistory.addLast(captureNavigation())
    }

    private fun restoreNavigation(snapshot: NavigationSnapshot) {
        findVersion++
        categoryLoadVersion++
        archiveLoadVersion++
        selectedPaths.clear()
        suppressNavigationHistory = true
        try {
            if (snapshot.screen == Screen.HOME) {
                showHome(recordHistory = false)
                return
            }
            currentScreen = Screen.TREE
            leftPane = copyPane(snapshot.left)
            rightPane = copyPane(snapshot.right)
            activePanel = snapshot.panel
            rightVirtualTitle = snapshot.virtualTitle
            activeCategory = snapshot.category
            activeArchive = snapshot.archive
            archiveEntries = snapshot.archiveEntries
            archiveDirectory = snapshot.archiveDirectory
            contentHost?.removeAllViews()
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            renderAll()
            updateSelectionBar()
        } finally {
            suppressNavigationHistory = false
        }
    }

    private fun displayIncomingIntent(intent: Intent?, start: File) {
        navigationHistory.clear()
        currentScreen = Screen.HOME
        rightVirtualTitle = null
        activeCategory = null
        val tree = shouldOpenTree(intent)
        if (tree) navigationHistory.addLast(captureNavigation())
        suppressNavigationHistory = true
        try {
            if (!tree && compactLayout()) {
                // Like stock file managers, a plain launch starts on categories, recents and storage.
                showHome(recordHistory = false)
                return
            }
            showTree(start, recordHistory = false)
            handleIncomingRequest(intent)
        } finally {
            suppressNavigationHistory = false
        }
    }

    private fun shouldOpenTree(intent: Intent?): Boolean {
        return !intent?.getStringExtra(EXTRA_ACTION).isNullOrBlank() ||
            !intent?.getStringExtra(EXTRA_PATH).isNullOrBlank() ||
            searchRequestExtra(intent) != null
    }

    private fun showHome(recordHistory: Boolean = true) {
        if (recordHistory && currentScreen != Screen.HOME) rememberNavigation()
        findVersion++
        paneDialog = null
        selectedPaths.clear()
        clearVirtualCategory()
        currentScreen = Screen.HOME
        updateSelectionBar()
        homeCountVersion++
        updateAddressBar()
        leftRowsView = null
        leftScrollView = null
        rightListView = null
        rightListAdapter = null
        leftFooterView = null
        rightFooterView = null
        leftDiskView = null
        rightDiskView = null
        contentHost?.removeAllViews()
        contentHost?.addView(buildHomePage(), FrameLayout.LayoutParams(-1, -1))
    }

    private fun showTree(dir: File = rightPane.directory, recordHistory: Boolean = true) {
        if (recordHistory && currentScreen != Screen.TREE) rememberNavigation()
        currentScreen = Screen.TREE
        activePanel = Panel.RIGHT
        updateSelectionBar()
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
        if (!hasStorageAccess()) {
            val access = homePanel("STORAGE ACCESS")
            addHomeRow(access, "Not granted", "Tap to grant access", "\uF023") { ensureStorageAccess(userInitiated = true) }
            content.addView(access, sectionParams())
        }
        content.addView(buildHomeSearchRow(), LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(4), dp(4), dp(4), dp(2)) })
        content.addView(buildCategorySection(), sectionParams())
        buildRecentSection()?.let { content.addView(it, sectionParams()) }
        content.addView(buildStorageSection(), sectionParams())
        return scroll
    }

    private fun buildHomeSearchRow(): View {
        val search = label("$ find  Search all files…", outputTextSizeSp, false)
        search.gravity = Gravity.CENTER_VERTICAL
        search.setPadding(dp(12), 0, dp(12), 0)
        search.setTextColor(withAlpha(inputTextColor, 200))
        search.background = panelDrawable(PanelRole.OUTPUT)
        search.contentDescription = "Search all files"
        search.setOnClickListener { promptSearch(null, "Search all storage") }
        return search
    }

    private fun buildRecentSection(): View? {
        val recent = NavigationStateStore.recent(themePrefs()).take(4)
        if (recent.isEmpty()) return null
        val panel = homePanel("RECENT FOLDERS")
        for (folder in recent) {
            val name = if (folder == homeDirectory()) "Phone storage" else folder.name.ifBlank { folder.absolutePath }
            addHomeRow(panel, name, abbreviatePath(folder.absolutePath), "\uF07B") { navigateMain(folder) }
        }
        return panel
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
        for (root in storageRoots()) {
            addHomeRow(panel, storageRootLabel(root), storageLine(root), "") { navigateMain(root) }
        }
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
        tile.addView(glyphView(category.glyph, moduleButtonTextColor, max(22, outputTextSizeSp + 8)), LinearLayout.LayoutParams(-1, dp(28)))
        val name = label(category.label, max(10, outputTextSizeSp - 2), true)
        name.gravity = Gravity.CENTER
        name.setTextColor(moduleButtonTextColor)
        tile.addView(name, LinearLayout.LayoutParams(-1, dp(22)))
        val value = label(count?.toString() ?: "...", max(9, outputTextSizeSp - 3), false)
        value.gravity = Gravity.CENTER
        value.setTextColor(withAlpha(moduleButtonTextColor, 185))
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
        row.addView(glyphView(glyph, moduleButtonTextColor, max(16, outputTextSizeSp + 2)), LinearLayout.LayoutParams(dp(34), -1))
        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        text.gravity = Gravity.CENTER_VERTICAL
        val top = label(title, outputTextSizeSp, true)
        top.gravity = Gravity.START or Gravity.BOTTOM
        top.setTextColor(moduleButtonTextColor)
        val bottom = label(subtitle, max(9, outputTextSizeSp - 3), false)
        bottom.gravity = Gravity.START or Gravity.TOP
        bottom.setTextColor(withAlpha(moduleButtonTextColor, 175))
        text.addView(top, LinearLayout.LayoutParams(-1, dp(24)))
        text.addView(bottom, LinearLayout.LayoutParams(-1, dp(18)))
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) cursorFromKeys = false
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) cursorFromKeys = true
        if (paneDialog == null && handleHardwareKey(event.keyCode, event)) return true
        return super.dispatchKeyEvent(event)
    }


    private fun handleHardwareKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F10) {
            runFunctionAction(keyCode - KeyEvent.KEYCODE_F1 + 1)
            return true
        }
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
                switchPanel(Panel.LEFT)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                switchPanel(Panel.RIGHT)
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                switchPanel(if (activePanel == Panel.LEFT) Panel.RIGHT else Panel.LEFT)
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
            KeyEvent.KEYCODE_SLASH -> {
                promptCommand()
                true
            }
            else -> false
        }
    }

    private fun handleIncomingRequest(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_ACTION)?.trim()?.lowercase(Locale.US)
        when (action) {
            ACTION_OPEN -> incomingTarget(intent)?.let {
                if (it.isDirectory) navigateMain(it) else if (it.exists()) openFile(it) else showOutput("OPEN", "Not found: ${it.path}")
            }
            ACTION_LIST -> Unit
            ACTION_SHARE -> intent?.getStringExtra(EXTRA_TARGET)?.takeIf { it.isNotBlank() }?.let {
                val target = incomingTarget(intent)
                if (target != null && target.exists()) shareFile(target) else showOutput("SHARE", "Not found: $it")
            }
            ACTION_SEARCH -> searchRequestExtra(intent)?.let { runFind(it, intentSearchRoot(intent)) }
            else -> searchRequestExtra(intent)?.let { runFind(it, intentSearchRoot(intent)) }
        }
    }

    private fun configureIncomingMode(intent: Intent?) {
        shareSelectionMode = intent?.getStringExtra(EXTRA_ACTION)?.equals(ACTION_SHARE, true) == true &&
            intent.getStringExtra(EXTRA_TARGET).isNullOrBlank()
    }

    private fun incomingTarget(intent: Intent?): File? = FilesNavigationContract.launchTarget(
        intent?.getStringExtra(EXTRA_PATH),
        intent?.getStringExtra(EXTRA_TARGET)
    )

    private fun intentSearchRoot(intent: Intent?): File? =
        intent?.getStringExtra(EXTRA_PATH)?.takeIf { it.isNotBlank() }?.let(::File)

    private fun searchExtra(intent: Intent?): String? {
        return stringExtra(intent, EXTRA_SEARCH, "query", "q", "search_query")
    }

    private fun searchRequestExtra(intent: Intent?): SearchRequest? {
        val name = stringExtra(intent, EXTRA_SEARCH_NAME, "file_name", "filename")
        val type = stringExtra(intent, EXTRA_SEARCH_TYPE, "search_file_type", "file_type", "extension", "ext")
        if (!name.isNullOrBlank() || !type.isNullOrBlank()) {
            return SearchRequest(cleanSearchTerm(name), cleanSearchTerm(type))
        }
        return searchExtra(intent)?.takeIf { it.isNotBlank() }?.let { searchRequestFromText(it) }
    }

    private fun reloadAll() {
        activeArchive?.let {
            openArchive(it, archiveDirectory, recordHistory = false)
            return
        }
        clearVirtualCategory()
        if (currentScreen == Screen.HOME) {
            showHome(recordHistory = false)
            return
        }
        reloadPane(leftPane)
        reloadPane(rightPane)
        renderAll()
    }

    private fun reloadPane(pane: PaneState) {
        folderCountCache.clear()
        val dir = pane.directory
        pane.rows.clear()
        // A storage root's parent (e.g. /storage/emulated) is unreadable on Android; Home is the way up.
        if (!isStorageRoot(dir)) pane.rows.add(FileEntry(dir.parentFile, "/..", true, true))
        val children = dir.listFiles()
        val files = children?.filter { showHidden || !it.name.startsWith(".") }.orEmpty()
        val sorted = if (pane === rightPane) sortedFiles(files) else files.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.US) }
        )
        val visible = if (pane === leftPane) sorted.filter { it.isDirectory } else sorted
        for (file in visible.take(MAX_ROWS)) {
            pane.rows.add(FileEntry(file, file.name, file.isDirectory))
        }
        if (visible.size > MAX_ROWS) pane.rows.add(FileEntry(null, "Showing first $MAX_ROWS items. Refine this folder or search.", false))
        if (children == null) pane.rows.add(FileEntry(null, "Folder unavailable. Open File > Storage access.", false))
        pane.summary = summarizeRows(pane.rows)
        if (pane.cursor >= pane.rows.size) pane.cursor = max(0, pane.rows.size - 1)
        if (pane.cursor < 0) pane.cursor = 0
        clampCursor(pane)
    }

    private fun placeEntries(includeRecent: Boolean = true): List<FileEntry> {
        val home = homeDirectory()
        val candidates = listOf(
            "Home" to home,
            "Downloads" to File(home, Environment.DIRECTORY_DOWNLOADS),
            "Documents" to File(home, Environment.DIRECTORY_DOCUMENTS),
            "Pictures" to File(home, Environment.DIRECTORY_PICTURES),
            "Music" to File(home, Environment.DIRECTORY_MUSIC)
        ) + storageRoots().drop(1).map { storageRootLabel(it) to it }
        val seen = HashSet<String>()
        val entries = candidates.mapNotNull { (label, file) ->
            if (file.exists() && file.isDirectory && seen.add(file.absolutePath)) {
                FileEntry(file, label, true)
            } else {
                null
            }
        }.toMutableList()
        entries.add(FileEntry(null, "Trash", true, isTrashPlace = true))
        for (path in customPlacePaths()) {
            val file = File(path)
            if (file.exists() && file.isDirectory && seen.add(file.absolutePath)) {
                entries.add(FileEntry(file, file.name.ifBlank { file.absolutePath }, true))
            }
        }
        val recent = NavigationStateStore.recent(themePrefs())
            .filter { seen.add(it.absolutePath) }
        if (includeRecent && recent.isNotEmpty()) {
            entries.add(FileEntry(null, "RECENT", false, isSection = true))
            entries.addAll(recent.map { FileEntry(it, "Recent: ${it.name.ifBlank { it.absolutePath }}", true) })
        }
        val providers = ProviderRoots.list(this).mapNotNull { uri ->
            val backend = runCatching { SafStorageBackend(this, uri) }.getOrNull() ?: return@mapNotNull null
            FileEntry(
                file = null,
                label = "Provider: ${backend.displayName}",
                isDirectory = true,
                contentUri = uri,
                providerItem = StorageItem(uri, backend.displayName, true),
                providerRootUri = uri
            )
        }
        if (providers.isNotEmpty()) {
            entries.add(FileEntry(null, "PROVIDERS", false, isSection = true))
            entries.addAll(providers)
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

    private fun showCategoryFiles(category: FileCategory, recordHistory: Boolean = true) {
        if (recordHistory) rememberNavigation()
        activeCategory = category
        val loadVersion = ++categoryLoadVersion
        showCategoryRows(category, listOf(FileEntry(null, "Loading ${category.label.lowercase(Locale.US)}...", false)))
        Thread {
            loadCategoryRows(category, loadVersion)
        }.start()
    }

    private fun showCategoryRows(category: FileCategory, rows: List<FileEntry>) {
        if (currentScreen != Screen.TREE || treePanesMissing()) {
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
        rightPane.rows.addAll(sortedEntries(rows))
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(rows, includeSize = false)
        activePanel = Panel.RIGHT
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
    }

    private fun sortedEntries(rows: List<FileEntry>): List<FileEntry> {
        val fixed = rows.filter { it.file == null || it.isParent }
        val sortable = rows.filterNot { it.file == null || it.isParent }
        val files = sortedFiles(sortable.mapNotNull { it.file })
        val byPath = sortable.mapNotNull { entry -> entry.file?.absolutePath?.let { it to entry } }.toMap()
        return fixed + files.mapNotNull { byPath[it.absolutePath] }
    }

    private fun sortedFiles(files: List<File>): List<File> {
        return files.sortedWith { a, b ->
            val directoryOrder = compareValues(!a.isDirectory, !b.isDirectory)
            if (directoryOrder != 0) directoryOrder else when (rightSortMode) {
                SortMode.NAME_ASC -> a.name.compareTo(b.name, ignoreCase = true)
                SortMode.NAME_DESC -> b.name.compareTo(a.name, ignoreCase = true)
                SortMode.MODIFIED_NEWEST -> compareValues(b.lastModified(), a.lastModified()).takeIf { it != 0 }
                    ?: a.name.compareTo(b.name, ignoreCase = true)
                SortMode.SIZE_LARGEST -> compareValues(b.length(), a.length()).takeIf { it != 0 } ?: a.name.compareTo(b.name, true)
                SortMode.SIZE_SMALLEST -> compareValues(a.length(), b.length()).takeIf { it != 0 } ?: a.name.compareTo(b.name, true)
                SortMode.TYPE -> a.extension.compareTo(b.extension, true).takeIf { it != 0 } ?: a.name.compareTo(b.name, true)
                SortMode.MODIFIED_OLDEST -> compareValues(a.lastModified(), b.lastModified()).takeIf { it != 0 }
                    ?: a.name.compareTo(b.name, ignoreCase = true)
            }
        }
    }

    private fun mediaStoreCount(category: FileCategory): Int {
        val (selection, args) = categorySelection(category)
        return try {
            contentResolver.query(
                category.uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                selection,
                args,
                null
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext()) {
                    val file = cursor.getString(0)?.let(::File) ?: continue
                    if (file.exists() && !isUnderTrash(file)) count++
                }
                count
            } ?: 0
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
                    if (file.exists() && file.isFile && !isUnderTrash(file)) {
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
        showTrashContents()
    }

    private fun showTrashContents(recordHistory: Boolean = true) {
        if (recordHistory) rememberNavigation()
        categoryLoadVersion++
        if (currentScreen != Screen.TREE || treePanesMissing()) {
            currentScreen = Screen.TREE
            contentHost?.removeAllViews()
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            leftPane.directory = homeDirectory()
            leftPane.cursor = 0
            reloadPane(leftPane)
        }
        val items = trashDirs().flatMap { it.listFiles()?.toList().orEmpty() }
            .distinctBy(File::getAbsolutePath)
            .map { FileEntry(it, it.name, it.isDirectory) }
        rightVirtualTitle = "TRASH"
        activeCategory = null
        rightPane.directory = homeDirectory()
        rightPane.rows.clear()
        rightPane.rows.addAll(sortedEntries(if (items.isEmpty()) listOf(FileEntry(null, "Trash is empty", false)) else items))
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(items)
        activePanel = Panel.RIGHT
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
    }

    private fun renderAll() {
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
    }

    private fun updateAddressBar() {
        leftTitleView?.apply {
            text = "← ${abbreviatePath(leftPane.directory.absolutePath)}"
            setTextColor(if (activePanel == Panel.LEFT) selectionTextColor else outputTextColor)
            setBackgroundColor(if (activePanel == Panel.LEFT) selectionBgColor else headerPanelColor)
            commanderSkin?.style("core", if (activePanel == Panel.LEFT) "reverse" else "frame")?.displayed?.let { styleCommanderText(this, it, background = true) }
        }
        rightTitleView?.apply {
            text = "${rightVirtualTitle ?: abbreviatePath(rightPane.directory.absolutePath)} →"
            setTextColor(if (activePanel == Panel.RIGHT) selectionTextColor else outputTextColor)
            setBackgroundColor(if (activePanel == Panel.RIGHT) selectionBgColor else headerPanelColor)
            commanderSkin?.style("core", if (activePanel == Panel.RIGHT) "reverse" else "frame")?.displayed?.let { styleCommanderText(this, it, background = true) }
        }
        renderBreadcrumbs()
        if (compactLayout() && actionBarScreen != currentScreen) updateFunctionBar()
        commandLineView?.text = "$ " + if (currentScreen == Screen.HOME) "Command…" else "${abbreviatePath(rightPane.directory.absolutePath)} › Command…"
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
        var restoredPosition = false
        if (panel == Panel.RIGHT) {
            restoredPosition = renderFileList()
        } else {
            val previousScroll = leftScrollView?.scrollY ?: 0
            rowsView?.removeAllViews()
            for (i in pane.rows.indices) {
                rowsView?.addView(rowView(pane.rows[i], panel, i), LinearLayout.LayoutParams(-1, dp(if (pane.rows[i].isSection) 20 else FILE_ROW_HEIGHT_DP)))
            }
            leftScrollView?.post { leftScrollView?.scrollTo(0, previousScroll) }
        }
        if (panel == Panel.RIGHT && !restoredPosition) keepSelectionVisible(panel)
        val selected = pane.rows.getOrNull(pane.cursor)?.takeUnless { it.isSection }?.label ?: pane.directory.name
        footer?.text = selected
        footer?.contentDescription = "$selected. ${paneSummary(pane)}"
        disk?.text = if (panel == Panel.LEFT) "${pane.rows.count { it.isDirectory && !it.isParent }} dirs" else diskSummary(pane.directory)
    }

    /** Returns true when a saved scroll position was restored instead of scrolling to the cursor. */
    private fun renderFileList(): Boolean {
        val grid = rightListView ?: return false
        val adapter = rightListAdapter ?: RightListAdapter().also { rightListAdapter = it }
        if (grid.adapter !== adapter) grid.adapter = adapter
        adapter.notifyDataSetChanged()
        val restore = pendingListPosition
        pendingListPosition = null
        grid.post { if (restore != null) grid.setSelectionFromTop(restore.first, restore.second) else grid.setSelection(rightPane.cursor) }
        return restore != null
    }

    private fun currentListPosition(): Pair<Int, Int>? =
        rightListView?.let { list -> list.firstVisiblePosition to (list.getChildAt(0)?.top ?: 0) }

    private inner class RightListAdapter : BaseAdapter() {
        override fun getCount(): Int = rightPane.rows.size
        override fun getItem(position: Int): Any = rightPane.rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            if (compactLayout()) {
                val cell = (convertView?.takeIf { it.tag is CompactRowHolder } as? LinearLayout) ?: newCompactRow()
                bindCompactRow(cell, rightPane.rows[position], position)
                return cell
            }
            val cell = (convertView?.takeIf { it.tag is FileRowHolder } as? LinearLayout) ?: newFileRow()
            bindFileRow(cell, rightPane.rows[position], position)
            cell.layoutParams = AbsListView.LayoutParams(-1, dp(FILE_ROW_HEIGHT_DP))
            return cell
        }
    }

    private class FileRowHolder(val prefix: TextView, val label: TextView, val size: TextView, val date: TextView, val dateDivider: View)

    private class CompactRowHolder(val type: TextView, val label: TextView, val meta: TextView)

    private fun newCompactRow(): LinearLayout {
        val cell = LinearLayout(this)
        cell.id = R.id.fm_file_row
        cell.orientation = LinearLayout.HORIZONTAL
        cell.gravity = Gravity.CENTER_VERTICAL
        cell.minimumHeight = dp(56)
        cell.setPadding(dp(4), dp(6), dp(8), dp(6))
        val type = label("", max(10, outputTextSizeSp - 1), true)
        type.gravity = Gravity.CENTER
        cell.addView(type, LinearLayout.LayoutParams(dp(52), -2))
        val text = LinearLayout(this)
        text.orientation = LinearLayout.VERTICAL
        val name = label("", outputTextSizeSp, false)
        name.setSingleLine(false)
        name.maxLines = 2
        name.ellipsize = TextUtils.TruncateAt.END
        text.addView(name, LinearLayout.LayoutParams(-1, -2))
        val meta = label("", max(10, outputTextSizeSp - 2), false)
        meta.ellipsize = TextUtils.TruncateAt.END
        text.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        cell.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        cell.layoutParams = AbsListView.LayoutParams(-1, -2)
        cell.tag = CompactRowHolder(type, name, meta)
        return cell
    }

    private fun bindCompactRow(cell: LinearLayout, entry: FileEntry, index: Int) {
        val marked = (entry.archivePath ?: entry.file?.absolutePath) in selectedPaths
        val cursor = pendingCopyPaths.isEmpty() && pendingMovePaths.isEmpty() &&
            cursorFromKeys && showRightCursorHighlight && activePanel == Panel.RIGHT && index == rightPane.cursor
        val selected = marked || selectedPaths.isEmpty() && cursor
        val color = rowTextColor(selected, entry.isDirectory)
        cell.background = rowSelectionBackground(selected)
        val holder = cell.tag as CompactRowHolder
        holder.type.text = if (marked) "[ * ]" else fileTypeTag(entry)
        holder.label.text = if (entry.isParent) ".. (up one folder)" else entry.label
        holder.label.setTypeface(appTypeface ?: Typeface.MONOSPACE, if (entry.isDirectory) Typeface.BOLD else Typeface.NORMAL)
        val modified = entry.file?.lastModified() ?: entry.providerItem?.modified ?: entry.modified
        val date = if (modified > 0) java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(java.util.Date(modified)) else ""
        val size = when {
            entry.isParent || entry.isSection -> ""
            entry.isDirectory && entry.file != null -> folderItemCount(entry.file)
            entry.isDirectory -> "Folder"
            entry.file != null -> humanSize(entry.file.length())
            entry.providerItem != null || entry.archivePath != null -> humanSize(entry.size)
            else -> ""
        }
        holder.meta.text = listOf(size, date).filter { it.isNotEmpty() }.joinToString(" · ")
        holder.meta.visibility = if (holder.meta.text.isEmpty()) View.GONE else View.VISIBLE
        listOf(holder.type, holder.label).forEach { it.setTextColor(color) }
        holder.meta.setTextColor(if (selected) selectionTextColor else withAlpha(color, 170))
        commanderFileStyle(entry, marked, cursor)?.let { style ->
            cell.setBackgroundColor(style.background)
            listOf(holder.type, holder.label).forEach { styleCommanderText(it, style) }
            holder.meta.setTextColor(withAlpha(holder.label.currentTextColor, 170))
        }
        cell.contentDescription = when {
            entry.isParent -> "Parent folder"
            entry.isDirectory -> "Folder ${entry.label}, ${holder.meta.text}"
            else -> "File ${entry.label}, ${holder.meta.text}"
        } + if (marked) ", marked" else ""
    }

    private fun fileTypeTag(entry: FileEntry): String {
        if (entry.isParent) return "[ ..]"
        if (entry.isDirectory) return "[DIR]"
        if (entry.file == null && entry.providerItem == null && entry.archivePath == null) return ""
        val ext = entry.label.substringAfterLast('.', "").lowercase(Locale.US)
        val tag = when {
            ext in ARCHIVE_EXTENSIONS -> "ZIP"
            ext in IMAGE_EXTENSIONS -> "IMG"
            ext in VIDEO_EXTENSIONS -> "VID"
            ext in AUDIO_EXTENSIONS -> "AUD"
            ext == "pdf" -> "PDF"
            ext == "apk" -> "APK"
            ext in DOCUMENT_EXTENSIONS -> "DOC"
            ext.isNotEmpty() && ext.length <= 3 -> ext.uppercase(Locale.US)
            else -> "---"
        }
        return "[${tag.padEnd(3)}]"
    }

    private fun folderItemCount(dir: File): String {
        val count = folderCountCache.getOrPut(dir.absolutePath) {
            dir.list()?.count { showHidden || !it.startsWith(".") } ?: -1
        }
        return when (count) {
            -1 -> "Folder"
            1 -> "1 item"
            else -> "$count items"
        }
    }

    private fun newFileRow(): LinearLayout {
        val cell = LinearLayout(this)
        cell.id = R.id.fm_file_row
        cell.orientation = LinearLayout.HORIZONTAL
        cell.gravity = Gravity.CENTER_VERTICAL
        val prefix = label("", outputTextSizeSp, true)
        prefix.gravity = Gravity.CENTER_VERTICAL
        cell.addView(prefix, LinearLayout.LayoutParams(dp(12), -1))
        val text = label("", outputTextSizeSp, false)
        text.gravity = Gravity.CENTER_VERTICAL
        text.ellipsize = TextUtils.TruncateAt.MIDDLE
        text.setPadding(dp(2), 0, dp(2), 0)
        cell.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        fun divider() = View(this).apply { setBackgroundColor(withAlpha(outputBorderColor, 110)) }
        val nameDivider = divider()
        cell.addView(nameDivider, LinearLayout.LayoutParams(max(1, dp(1)), -1))
        val size = label("", outputTextSizeSp, false)
        size.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        size.setPadding(0, 0, dp(4), 0)
        cell.addView(size, LinearLayout.LayoutParams(dp(54), -1))
        val dateDivider = divider()
        cell.addView(dateDivider, LinearLayout.LayoutParams(max(1, dp(1)), -1))
        val date = label("", outputTextSizeSp, false)
        date.gravity = Gravity.CENTER
        cell.addView(date, LinearLayout.LayoutParams(dp(84), -1))
        cell.tag = FileRowHolder(prefix, text, size, date, dateDivider)
        return cell
    }

    private fun bindFileRow(cell: LinearLayout, entry: FileEntry, index: Int) {
        val marked = (entry.archivePath ?: entry.file?.absolutePath) in selectedPaths
        val cursor = pendingCopyPaths.isEmpty() && pendingMovePaths.isEmpty() &&
            showRightCursorHighlight && activePanel == Panel.RIGHT && index == rightPane.cursor
        val selected = marked || selectedPaths.isEmpty() && cursor
        val color = rowTextColor(selected, entry.isDirectory)
        cell.background = rowSelectionBackground(selected)
        val holder = cell.tag as FileRowHolder
        holder.date.visibility = if (showModifiedColumn) View.VISIBLE else View.GONE
        holder.dateDivider.visibility = holder.date.visibility
        val prefix = if (marked) "*" else if (entry.isDirectory) "/" else " "
        holder.prefix.text = prefix
        holder.prefix.setTextColor(color)
        holder.label.text = if (entry.isParent) ".." else entry.label
        holder.label.isSelected = selected
        holder.label.setTextColor(color)
        holder.label.setTypeface(appTypeface ?: Typeface.MONOSPACE, if (entry.isDirectory) Typeface.BOLD else Typeface.NORMAL)
        holder.size.text = when {
            entry.isParent -> "UP-DIR"
            entry.isDirectory -> "SUB-DIR"
            entry.file != null -> humanSize(entry.file.length())
            entry.providerItem != null || entry.archivePath != null -> humanSize(entry.size)
            else -> ""
        }
        holder.size.setTextColor(color)
        val modified = entry.file?.lastModified() ?: entry.providerItem?.modified ?: 0L
        holder.date.text = if (modified > 0) java.text.SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(java.util.Date(modified)) else ""
        holder.date.setTextColor(if (selected) selectionTextColor else withAlpha(color, 180))
        commanderFileStyle(entry, marked, cursor)?.let { style ->
            cell.setBackgroundColor(style.background)
            listOf(holder.prefix, holder.label, holder.size, holder.date).forEach { styleCommanderText(it, style) }
        }
        cell.contentDescription = when {
            entry.isParent -> "Parent folder"
            entry.isDirectory -> "Folder ${entry.label}"
            else -> "File ${entry.label}"
        } + if (marked) ", marked" else ""
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
            val key = entry.providerItem?.uri?.toString() ?: entry.file?.absolutePath ?: entry.label
            val open = panel != Panel.LEFT || (activePanel == Panel.LEFT && tappedLeftEntry == key)
            activePanel = panel
            pane(panel).cursor = index
            clampCursor(pane(panel))
            if (panel == Panel.LEFT) {
                tappedLeftEntry = key
                renderPane(Panel.LEFT)
                refreshSelectionHighlights()
                updateAddressBar()
                if (open) openSelected()
            } else if (shareSelectionMode || selectedPaths.isNotEmpty()) toggleSelection(entry) else openSelected()
        }
        row.setOnLongClickListener {
            tappedLeftEntry = null
            activePanel = panel
            pane(panel).cursor = index
            entry.file?.let {
                if (panel == Panel.RIGHT && !entry.isParent) toggleSelection(entry)
                else if (!entry.isParent) showItemMenu(it)
            }
            entry.providerItem?.let { item -> showProviderItemMenu(item, entry.providerRootUri) }
            true
        }

        row.addView(nameCell(entry, selected), LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun moveCursor(delta: Int) {
        tappedLeftEntry = null
        val pane = activePane()
        if (pane.rows.isEmpty()) return
        if (activePanel == Panel.RIGHT) showRightCursorHighlight = true
        val direction = if (delta < 0) -1 else 1
        var target = clamp(pane.cursor + delta, 0, pane.rows.size - 1)
        while (target in pane.rows.indices && pane.rows[target].isSection) target += direction
        if (target !in pane.rows.indices) {
            target = clamp(pane.cursor + delta, 0, pane.rows.size - 1)
            while (target in pane.rows.indices && pane.rows[target].isSection) target -= direction
        }
        if (target in pane.rows.indices) pane.cursor = target
        renderActivePane()
        keepSelectionVisible(activePanel)
    }

    private fun cursorHome() {
        val pane = activePane()
        if (activePanel == Panel.RIGHT) showRightCursorHighlight = true
        pane.cursor = pane.rows.indexOfFirst { !it.isSection }.takeIf { it >= 0 } ?: 0
        tappedLeftEntry = null
        renderActivePane()
        keepSelectionVisible(activePanel)
    }

    private fun cursorEnd() {
        val pane = activePane()
        if (activePanel == Panel.RIGHT) showRightCursorHighlight = true
        pane.cursor = pane.rows.indexOfLast { !it.isSection }.takeIf { it >= 0 } ?: max(0, pane.rows.size - 1)
        tappedLeftEntry = null
        renderActivePane()
        keepSelectionVisible(activePanel)
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
        val height = if (panel == Panel.RIGHT) rightListView?.height ?: 0 else leftScrollView?.height ?: 0
        if (panel == Panel.RIGHT) {
            val rowHeight = max(1, dp(FILE_ROW_HEIGHT_DP))
            return max(1, if (height > 0) height / rowHeight else 6)
        }
        val rowHeight = max(1, dp(FILE_ROW_HEIGHT_DP))
        return max(1, if (height > 0) height / rowHeight else 18)
    }

    private fun keepSelectionVisible(panel: Panel) {
        val pane = pane(panel)
        if (panel == Panel.RIGHT) {
            rightListView?.post { rightListView?.setSelection(pane.cursor) }
            return
        }
        val scroll = leftScrollView
        val item = leftRowsView?.getChildAt(pane.cursor)
        val itemHeight = item?.height ?: dp(FILE_ROW_HEIGHT_DP)
        val itemTop = item?.top ?: pane.cursor * itemHeight
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

    private fun openSelected() {
        val entry = activePane().rows.getOrNull(activePane().cursor) ?: return
        if (entry.isSection) return
        if (entry.isTrashPlace) return showTrashContents()
        entry.providerItem?.let { item ->
            if (item.isDirectory) showProviderDirectory(item.uri, entry.providerRootUri ?: activeProvider?.rootUri)
            else previewProviderItem(item)
            return
        }
        if (activeArchive != null && activePanel == Panel.RIGHT) {
            val path = entry.archivePath ?: return
            if (entry.isDirectory) showArchiveDirectory(path)
            else activeArchive?.let { previewArchiveEntry(it, path, entry.size) }
            return
        }
        val file = entry.file ?: return
        if (activePanel == Panel.LEFT) {
            if (entry.isDirectory) showDirectoryContents(file)
        } else if (entry.isDirectory) {
            showDirectoryContents(file)
        } else {
            previewFile(file, entry.contentUri)
        }
    }

    private fun showProviderDirectory(uri: Uri, rootUri: Uri? = null, recordHistory: Boolean = true) {
        val providerRoot = rootUri ?: activeProvider?.rootUri ?: ProviderRoots.list(this).firstOrNull { it.toString() == uri.toString() }
        if (providerRoot == null) {
            showOutput("PROVIDER", "Provider root is no longer available.")
            return
        }
        val backend = if (activeProvider?.rootUri == providerRoot) activeProvider!! else SafStorageBackend(this, providerRoot)
        if (currentScreen != Screen.TREE || treePanesMissing()) {
            currentScreen = Screen.TREE
            contentHost?.removeAllViews()
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            leftPane.directory = homeDirectory()
            leftPane.cursor = 0
            reloadPane(leftPane)
        } else if (recordHistory && activeProviderUri != null && activeProviderUri != uri) {
            rememberNavigation()
        }
        paneDialog?.dismiss()
        selectedPaths.clear()
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        activeArchive = null
        activeCategory = null
        activeProvider = backend
        activeProviderUri = uri
        rightVirtualTitle = "PROVIDER: ${backend.displayName}"
        rightPane.directory = homeDirectory()
        rightPane.rows.clear()
        backend.parent(uri)?.let { parent ->
            rightPane.rows += FileEntry(
                file = null,
                label = "..",
                isDirectory = true,
                isParent = true,
                contentUri = parent,
                providerItem = StorageItem(parent, "..", true),
                providerRootUri = providerRoot
            )
        }
        rightPane.rows += backend.list(uri).take(MAX_ROWS).map { item ->
            FileEntry(
                file = null,
                label = item.name,
                isDirectory = item.isDirectory,
                contentUri = item.uri,
                size = item.size,
                providerItem = item,
                providerRootUri = providerRoot
            )
        }
        if (rightPane.rows.isEmpty()) rightPane.rows += FileEntry(null, "Provider folder is empty or unavailable", false)
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(rightPane.rows)
        activePanel = Panel.RIGHT
        showRightCursorHighlight = true
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
        updateSelectionBar()
    }

    private fun previewProviderItem(item: StorageItem) {
        val backend = activeProvider ?: return showOutput("PROVIDER", "No provider is active.")
        if (item.isDirectory) return showProviderDirectory(item.uri)
        val mime = item.mimeType.ifBlank { mimeFor(File(item.name)) }
        if (mime.startsWith("text/") || item.name.substringAfterLast('.', "").lowercase(Locale.US) in DOCUMENT_EXTENSIONS) {
            val text = runCatching {
                backend.openInput(item.uri)?.use { input ->
                    val buffer = ByteArray(PREVIEW_MAX_BYTES)
                    val read = input.read(buffer).coerceAtLeast(0)
                    String(buffer, 0, read, StandardCharsets.UTF_8)
                }
            }.getOrNull()
            if (text != null) return showOutput("PREVIEW", "${item.name}\n\n$text")
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, mime).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, item.name)) }
            .onFailure { showOutput("OPEN", "No Android app can open ${item.name}") }
    }

    private fun changeDirectory(dir: File) {
        navigateMain(dir)
    }

    private fun showDirectoryContents(dir: File, recordHistory: Boolean = true) {
        paneDialog?.dismiss()
        if (!hasStorageAccess()) {
            showStorageProblem(dir)
            return
        }
        if (!dir.exists() || !dir.isDirectory) {
            showOutput("CD", "Not a directory: ${dir.absolutePath}")
            return
        }
        if (!dir.canRead()) {
            showStorageProblem(dir)
            return
        }
        showRightCursorHighlight = true
        selectedPaths.clear()
        updateSelectionBar()
        if (currentScreen != Screen.TREE) {
            showTree(dir, recordHistory)
            return
        }
        if (recordHistory && (rightVirtualTitle != null || rightPane.directory.canonicalPath != dir.canonicalPath)) rememberNavigation()
        clearVirtualCategory()
        rightPane.directory = dir
        rightPane.cursor = 0
        reloadPane(rightPane)
        renderAll()
        rememberNormalDirectory(dir)
    }

    private fun navigateMain(dir: File, recordHistory: Boolean = true) {
        paneDialog?.dismiss()
        if (!hasStorageAccess()) {
            showStorageProblem(dir)
            return
        }
        if (!dir.exists() || !dir.isDirectory) {
            showOutput("CD", "Not a directory: ${dir.absolutePath}")
            return
        }
        if (!dir.canRead()) {
            showStorageProblem(dir)
            return
        }
        showRightCursorHighlight = true
        selectedPaths.clear()
        updateSelectionBar()
        if (currentScreen != Screen.TREE) {
            showTree(dir, recordHistory)
            return
        }
        if (recordHistory && (rightVirtualTitle != null || leftPane.directory.canonicalPath != dir.canonicalPath || rightPane.directory.canonicalPath != dir.canonicalPath)) rememberNavigation()
        navigateMainInTree(dir)
    }

    private fun navigateMainInTree(dir: File) {
        clearVirtualCategory()
        leftPane.directory = dir
        leftPane.cursor = 0
        rightPane.directory = dir
        rightPane.cursor = 0
        reloadAll()
        rememberNormalDirectory(dir)
    }

    private fun rememberNormalDirectory(dir: File) {
        if (activeArchive == null && activeCategory == null && rightVirtualTitle == null && !isUnderTrash(dir)) {
            NavigationStateStore.remember(themePrefs(), dir)
        }
    }

    private fun goHome() {
        changeDirectory(homeDirectory())
    }

    private fun goParent() {
        if (activeProvider != null) {
            activeProvider?.parent(activeProviderUri ?: return)?.let { showProviderDirectory(it, activeProvider?.rootUri) }
        } else if (activeArchive != null) {
            if (archiveDirectory.isNotEmpty()) showArchiveDirectory(ArchiveExtractor.parent(archiveDirectory)) else closeArchive()
        } else if (isStorageRoot(rightPane.directory)) {
            showHome()
        } else {
            rightPane.directory.parentFile?.let { showDirectoryContents(it) }
        }
    }

    private fun switchPanel(panel: Panel) {
        tappedLeftEntry = null
        activePanel = panel
        if (panel == Panel.RIGHT) showRightCursorHighlight = true
        renderAll()
    }

    private fun clearVirtualCategory() {
        categoryLoadVersion++
        archiveLoadVersion++
        rightVirtualTitle = null
        activeCategory = null
        activeArchive = null
        activeProvider = null
        activeProviderUri = null
        archiveEntries = emptyList()
        archiveDirectory = ""
    }

    private fun openArchive(archive: File, directory: String = "", recordHistory: Boolean = true) {
        val loadVersion = ++archiveLoadVersion
        Toast.makeText(this, "Opening ${archive.name}", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { ArchiveExtractor.entries(archive) }
            runOnUiThread {
                if (loadVersion != archiveLoadVersion) return@runOnUiThread
                result.fold(
                    onSuccess = { entries ->
                        if (recordHistory) rememberNavigation()
                        if (currentScreen != Screen.TREE) showTree(archive.parentFile ?: homeDirectory(), recordHistory = false)
                        activeArchive = archive
                        archiveEntries = entries
                        archiveDirectory = directory
                        pendingArchiveExtraction = null
                        selectedPaths.clear()
                        pendingCopyPaths.clear()
                        pendingMovePaths.clear()
                        showArchiveDirectory(directory, recordHistory = false)
                    },
                    onFailure = { showOutput("ARCHIVE", "Could not open ${archive.name}:\n${it.message}") }
                )
            }
        }.start()
    }

    private fun showArchiveDirectory(directory: String, recordHistory: Boolean = true) {
        val archive = activeArchive ?: return
        if (recordHistory && archiveDirectory != directory) rememberNavigation()
        archiveDirectory = directory
        val children = ArchiveExtractor.children(archiveEntries, directory)
        rightVirtualTitle = archive.name + "!/" + directory
        activeCategory = null
        rightPane.directory = archive.parentFile ?: homeDirectory()
        rightPane.rows.clear()
        if (directory.isNotEmpty()) {
            rightPane.rows += FileEntry(null, "..", true, isParent = true, archivePath = ArchiveExtractor.parent(directory))
        }
        rightPane.rows += children.take(MAX_ROWS).map {
            FileEntry(null, it.path.substringAfterLast('/'), it.isDirectory, archivePath = it.path, size = it.size, modified = it.modified)
        }
        if (rightPane.rows.isEmpty()) rightPane.rows += FileEntry(null, "Archive is empty", false)
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(rightPane.rows)
        activePanel = Panel.RIGHT
        showRightCursorHighlight = true
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
        updateSelectionBar()
    }

    private fun closeArchive(recordHistory: Boolean = true) {
        val parent = activeArchive?.parentFile ?: homeDirectory()
        if (recordHistory) rememberNavigation()
        selectedPaths.clear()
        clearVirtualCategory()
        showDirectoryContents(parent, recordHistory = false)
    }

    private fun clearArchiveSelection() {
        selectedPaths.clear()
        showRightCursorHighlight = false
        updateSelectionBar()
        refreshSelectionHighlights()
    }

    /** Unpacks one entry into the app cache and hands it to the normal preview flow. */
    private fun previewArchiveEntry(archive: File, path: String, size: Long) {
        if (size > ARCHIVE_PREVIEW_MAX_BYTES) {
            showOutput("ARCHIVE", "$path\n\n${humanSize(size)}\n\nToo large to preview here. Use EXTRACT first.")
            return
        }
        val loadVersion = archiveLoadVersion
        Thread {
            val result = runCatching {
                val dir = File(cacheDir, "archive-preview")
                dir.deleteRecursively()
                if (!dir.mkdirs()) throw java.io.IOException("Could not create preview folder")
                ArchiveExtractor.extractTo(archive, dir, setOf(path)).single().also { copy ->
                    archiveEntries.firstOrNull { it.path == path }?.modified?.takeIf { it > 0 }?.let(copy::setLastModified)
                }
            }
            runOnUiThread {
                if (loadVersion != archiveLoadVersion || activeArchive != archive) return@runOnUiThread
                result.fold(
                    onSuccess = { previewFile(it) },
                    onFailure = { showOutput("ARCHIVE", "Could not open $path:\n${it.message}") }
                )
            }
        }.start()
    }

    private fun showExtractChoices(archive: File) {
        val folder = ArchiveExtractor.destinationFor(archive).name
        showActionMenu("Extract", listOf(
            "Into new folder \"$folder\"" to { extractArchiveNamed() },
            "Here, without a folder" to { extractArchiveHere() },
            "Choose folder…" to { prepareArchiveExtractTo() }
        ))
    }

    private fun extractArchiveNamed() {
        val archive = activeArchive ?: return
        runArchiveExtraction(archive, selectedPaths.toSet(), null, named = true)
    }

    private fun extractArchiveHere() {
        val archive = activeArchive ?: return
        runArchiveExtraction(archive, selectedPaths.toSet(), archive.parentFile ?: homeDirectory(), named = false)
    }

    private fun prepareArchiveExtractTo() {
        val archive = activeArchive ?: return
        pendingArchiveExtraction = PendingArchiveExtraction(archive, selectedPaths.toSet())
        selectedPaths.clear()
        clearVirtualCategory()
        showDirectoryContents(archive.parentFile ?: homeDirectory())
        updateSelectionBar()
        Toast.makeText(this, "Navigate to a folder and tap EXTRACT HERE", Toast.LENGTH_SHORT).show()
    }

    private fun confirmArchiveExtractHere() {
        val request = pendingArchiveExtraction ?: return
        runArchiveExtraction(request.archive, request.selected, rightPane.directory, named = false)
    }

    private fun cancelArchiveExtraction() {
        pendingArchiveExtraction = null
        updateSelectionBar()
    }

    private fun runArchiveExtraction(archive: File, selected: Set<String>, destination: File?, named: Boolean) {
        val target = destination ?: archive.parentFile ?: homeDirectory()
        startArchiveOperation(OperationService.KIND_EXTRACT, listOf(archive), target, named, selected)
        pendingArchiveExtraction = null
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
        if (ArchiveExtractor.supports(file)) {
            openArchive(file)
            return
        }
        if (file.extension.lowercase(Locale.US) in QUICK_PREVIEW_EXTENSIONS) {
            showQuickPreview(file, contentUri)
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
                // Files unpacked from an archive live in the cache and cannot be edited in place.
                val editable = !file.absolutePath.startsWith(cacheDir.absolutePath)
                showTextPreview(file, text, file.length() > PREVIEW_MAX_BYTES, editable)
            }
        } catch (e: Exception) {
            showOutput("PREVIEW", "Could not read ${file.name}:\n${e.message}")
        }
    }

    /** Floating terminal window over the current screen, shared by image/media and text previews. */
    private fun showPreviewWindow(
        title: String,
        stage: View,
        meta: String,
        hint: String,
        buttons: List<Pair<String, () -> Unit>>,
        fitStage: Boolean = false,
        onHintClick: ((TextView) -> Unit)? = null
    ): View? {
        dismissQuickPreview()
        val root = rootView ?: return null
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(0x99000000.toInt())
        scrim.isClickable = true
        scrim.setOnClickListener { dismissQuickPreview() }

        val window = LinearLayout(this)
        window.orientation = LinearLayout.VERTICAL
        window.isClickable = true
        window.setPadding(dp(4), dp(4), dp(4), dp(4))
        window.background = CommanderFrameDrawable(outputPanelColor or 0xff000000.toInt(), outputBorderColor, dpFloat(1f), commanderSkin?.doubleFrame == true)

        val titleBar = LinearLayout(this)
        titleBar.orientation = LinearLayout.HORIZONTAL
        titleBar.setBackgroundColor(headerPanelColor)
        val titleView = label(title, outputTextSizeSp, true)
        titleView.gravity = Gravity.CENTER_VERTICAL
        titleView.ellipsize = TextUtils.TruncateAt.MIDDLE
        titleView.setPadding(dp(8), 0, dp(4), 0)
        titleView.setTextColor(headerTextColor)
        commanderSkin?.style("dialog", "dtitle")?.displayed?.let { styleCommanderText(titleView, it) }
        titleBar.addView(titleView, LinearLayout.LayoutParams(0, -1, 1f))
        val close = label("X", outputTextSizeSp, true)
        close.gravity = Gravity.CENTER
        close.setTextColor(titleView.currentTextColor)
        close.contentDescription = "Close preview"
        close.setOnClickListener { dismissQuickPreview() }
        titleBar.addView(close, LinearLayout.LayoutParams(dp(48), -1))
        window.addView(titleBar, LinearLayout.LayoutParams(-1, dp(40)))

        val screen = resources.displayMetrics
        val width = min(screen.widthPixels - dp(32), dp(460))
        var stageHeight = (screen.heightPixels * 0.45f).toInt()
        if (fitStage) {
            // Short text shrinks the window instead of leaving a tall empty area.
            stage.measure(View.MeasureSpec.makeMeasureSpec(width - dp(8), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            stageHeight = stageHeight.coerceAtMost(max(dp(96), stage.measuredHeight))
        }
        window.addView(stage, LinearLayout.LayoutParams(-1, stageHeight).apply { topMargin = dp(4) })

        val metaView = label(meta, max(10, outputTextSizeSp - 1), false)
        metaView.setPadding(dp(8), 0, dp(8), 0)
        metaView.gravity = Gravity.CENTER_VERTICAL
        metaView.setTextColor(outputTextColor)
        window.addView(metaView, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(4) })
        val hintView = label(hint, max(10, outputTextSizeSp - 2), false)
        hintView.setPadding(dp(8), 0, dp(8), 0)
        hintView.gravity = Gravity.CENTER_VERTICAL
        hintView.setTextColor(withAlpha(outputTextColor, 150))
        onHintClick?.let { action -> hintView.setOnClickListener { action(hintView) } }
        window.addView(hintView, LinearLayout.LayoutParams(-1, dp(22)))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        // Same light button style as the action bar; dialog buttons assume a light dialog panel behind them.
        for ((text, action) in buttons) {
            val view = label(text, outputTextSizeSp, true)
            view.gravity = Gravity.CENTER
            view.setTextColor(moduleButtonTextColor)
            view.background = functionButtonBackground()
            view.setOnClickListener { action() }
            row.addView(view, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        window.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        scrim.addView(window, FrameLayout.LayoutParams(width, -2, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        quickPreview = scrim
        return scrim
    }

    private fun previewDetails(file: File, vararg extra: String): String {
        val details = mutableListOf(fileTypeTag(FileEntry(file, file.name, false)).trim('[', ']', ' '))
        details += extra
        details += humanSize(file.length())
        details += java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(java.util.Date(file.lastModified()))
        return details.joinToString(" · ")
    }

    /** Image, video, audio and PDF preview; tapping the picture hands off to Android's app picker. */
    private fun showQuickPreview(file: File, contentUri: Uri?) {
        val screen = resources.displayMetrics
        val stage = FrameLayout(this)
        stage.setBackgroundColor(0xff000000.toInt())
        stage.contentDescription = "Open ${file.name} with another app"
        stage.setOnClickListener { dismissQuickPreview(); openFile(file, contentUri) }
        val loading = label("loading…", outputTextSizeSp, false)
        loading.gravity = Gravity.CENTER
        loading.setTextColor(withAlpha(outputTextColor, 170))
        stage.addView(loading, FrameLayout.LayoutParams(-1, -1))
        val image = ImageView(this)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        stage.addView(image, FrameLayout.LayoutParams(-1, -1))

        var dimensions = emptyArray<String>()
        if (file.extension.lowercase(Locale.US) in IMAGE_EXTENSIONS) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth > 0) dimensions = arrayOf("${bounds.outWidth}x${bounds.outHeight}")
        }
        val scrim = showPreviewWindow(file.name, stage, previewDetails(file, *dimensions), "$ tap the preview to open with…", listOf(
            "OPEN WITH" to { dismissQuickPreview(); openFile(file, contentUri) },
            "SHARE" to { dismissQuickPreview(); shareFile(file) },
            "CLOSE" to { dismissQuickPreview() }
        )) ?: return

        ThumbnailLoader.load(this, file, contentUri, max(screen.widthPixels, screen.heightPixels / 2)) { bitmap ->
            if (quickPreview !== scrim) return@load
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                loading.visibility = View.GONE
            } else {
                loading.text = "no preview for ${file.extension.lowercase(Locale.US)}\ntap to open with…"
                loading.setSingleLine(false)
                loading.maxLines = 2
            }
        }
    }

    /** Text preview styled like `cat -n`: dim line numbers and light key/tag/string colouring. */
    private fun showTextPreview(file: File, text: String, truncated: Boolean, editable: Boolean) {
        val lines = text.removeSuffix("\n").split('\n')
        val markdown = file.extension.equals("md", ignoreCase = true) || file.extension.equals("markdown", ignoreCase = true)
        val body = android.text.SpannableStringBuilder()
        val gutter = lines.size.toString().length
        val numberColor = withAlpha(outputTextColor, 100)
        lines.forEachIndexed { index, line ->
            val numberStart = body.length
            body.append((index + 1).toString().padStart(gutter)).append("  ")
            body.setSpan(android.text.style.ForegroundColorSpan(numberColor), numberStart, body.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val lineStart = body.length
            body.append(line.trimEnd('\r'))
            highlightLine(body, lineStart, line.trimEnd('\r'), markdown)
            if (index < lines.lastIndex) body.append('\n')
        }
        if (truncated) body.append("\n\n… preview capped at ${humanSize(PREVIEW_MAX_BYTES.toLong())}")

        val content = TextView(this)
        content.text = body
        content.setHorizontallyScrolling(false)
        content.typeface = appTypeface ?: Typeface.MONOSPACE
        content.setTextSize(scaledFontSp(max(10, outputTextSizeSp - 1), fontScaleOffsetSp))
        content.setTextColor(outputTextColor)
        content.setLineSpacing(dpFloat(3f), 1f)
        content.setTextIsSelectable(true)
        content.setPadding(dp(8), dp(8), dp(8), dp(8))
        val stage = ScrollView(this)
        stage.setBackgroundColor(0xff000000.toInt())
        stage.isFillViewport = true
        val holder = FrameLayout(this)
        stage.addView(holder, FrameLayout.LayoutParams(-1, -2))
        val sideways = HorizontalScrollView(this)
        // ASCII art breaks when wrapped, so symbol-heavy text scrolls sideways instead.
        var wrap = !looksLikeTextArt(text)
        fun applyWrap() {
            (content.parent as? android.view.ViewGroup)?.removeView(content)
            holder.removeAllViews()
            sideways.removeAllViews()
            content.setHorizontallyScrolling(!wrap)
            if (wrap) {
                holder.addView(content, FrameLayout.LayoutParams(-1, -2))
            } else {
                sideways.addView(content, FrameLayout.LayoutParams(-2, -2))
                holder.addView(sideways, FrameLayout.LayoutParams(-1, -2))
            }
        }
        applyWrap()
        fun hintText() = "$ cat -n ${file.name}  · wrap ${if (wrap) "on" else "off"}"

        val lineCount = if (lines.size == 1) "1 line" else "${lines.size} lines"
        val buttons = mutableListOf<Pair<String, () -> Unit>>()
        if (editable) buttons += "EDIT" to { dismissQuickPreview(); editFile(file) }
        buttons += "OPEN WITH" to { dismissQuickPreview(); openFile(file) }
        buttons += "SHARE" to { dismissQuickPreview(); shareFile(file) }
        buttons += "CLOSE" to { dismissQuickPreview() }
        showPreviewWindow(file.name, stage, previewDetails(file, lineCount), hintText(), buttons, fitStage = true) { hint ->
            wrap = !wrap
            applyWrap()
            hint.text = hintText()
        }
    }

    private fun looksLikeTextArt(text: String): Boolean {
        val visible = text.filterNot(Char::isWhitespace)
        if (visible.length < 40) return false
        return visible.count(Char::isLetter) < visible.length / 2
    }

    private fun highlightLine(body: android.text.SpannableStringBuilder, start: Int, line: String, markdown: Boolean) {
        fun color(from: Int, to: Int, value: Int) {
            if (to > from) body.setSpan(android.text.style.ForegroundColorSpan(value), start + from, start + to, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val keyColor = moduleTextColor
        val stringColor = directoryTextColor
        val trimmed = line.trimStart()
        if (markdown) {
            if (trimmed.startsWith("#")) color(0, line.length, keyColor)
            return
        }
        if (trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith(";") || trimmed.startsWith("<!--")) {
            color(0, line.length, withAlpha(outputTextColor, 110))
            return
        }
        PREVIEW_TAG.findAll(line).forEach { color(it.range.first, it.range.last + 1, keyColor) }
        PREVIEW_STRING.findAll(line).forEach { color(it.range.first, it.range.last + 1, stringColor) }
        PREVIEW_JSON_KEY.find(line)?.let { color(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, keyColor) }
            ?: PREVIEW_KEY.find(line)?.let { color(it.groups[1]!!.range.first, it.groups[1]!!.range.last + 1, keyColor) }
    }

    private fun dismissQuickPreview() {
        val preview = quickPreview ?: return
        quickPreview = null
        (preview.parent as? android.view.ViewGroup)?.removeView(preview)
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
        if (mime == "application/vnd.android.package-archive" && !packageManager.canRequestPackageInstalls()) {
            pendingApkPath = file.absolutePath
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
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

    private fun toggleSelection(file: File) {
        if (!file.exists() || file.name == TRASH_DIR_NAME) return
        if (shareSelectionMode && !file.isFile) return
        showRightCursorHighlight = false
        if (selectedPaths.isEmpty()) {
            pendingCopyPaths.clear()
            pendingMovePaths.clear()
        }
        if (!selectedPaths.add(file.absolutePath)) selectedPaths.remove(file.absolutePath)
        updateSelectionBar()
        refreshSelectionHighlights()
    }

    private fun toggleSelection(entry: FileEntry) {
        if (entry.isParent || entry.isSection) return
        val archivePath = entry.archivePath
        if (activeArchive != null && archivePath != null) {
            showRightCursorHighlight = false
            if (!selectedPaths.add(archivePath)) selectedPaths.remove(archivePath)
            updateSelectionBar()
            refreshSelectionHighlights()
            return
        }
        entry.file?.let(::toggleSelection)
    }

    private fun refreshSelectionHighlights() {
        val grid = rightListView ?: return
        val first = grid.firstVisiblePosition
        repeat(grid.childCount) { offset ->
            val position = first + offset
            val cell = grid.getChildAt(offset) as? LinearLayout ?: return@repeat
            val entry = rightPane.rows.getOrNull(position) ?: return@repeat
            if (cell.tag is CompactRowHolder) bindCompactRow(cell, entry, position) else bindFileRow(cell, entry, position)
        }
    }

    private fun selectedFiles(): List<File> = selectedPaths.map(::File).filter(File::exists)

    private fun confirmRestoreSelected() {
        val files = selectedFiles().filter(::isInTrash)
        if (files.isEmpty()) return clearSelection()
        if (files.any { restoreTarget(it)?.exists() == true }) {
            promptRestoreConflict(files)
            return
        }
        restoreSelected(files, ConflictChoice.KEEP_BOTH)
    }

    private fun promptRestoreConflict(files: List<File>) {
        val panel = dialogPanel("Restore conflict")
        val body = label("One or more original names already exist. Choose how to restore, or choose another destination.", max(10, outputTextSizeSp - 1), false)
        body.setSingleLine(false)
        panel.addView(body, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        for ((label, choice) in listOf("KEEP BOTH" to ConflictChoice.KEEP_BOTH, "REPLACE" to ConflictChoice.REPLACE, "SKIP" to ConflictChoice.SKIP)) {
            addDialogButton(buttons, label) {
                dialog.dismiss()
                restoreSelected(files, choice)
            }
        }
        addDialogButton(buttons, "OTHER FOLDER") {
            dialog.dismiss()
            showRestoreDestinationMenu(files)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun restoreSelected(files: List<File>, choice: ConflictChoice) {
        restoreSelected(files, choice, null)
    }

    private fun restoreSelected(files: List<File>, choice: ConflictChoice, destination: File?) {
        startFileOperation(OperationService.KIND_RESTORE, files, choice, destination)
    }

    private fun showRestoreDestinationMenu(files: List<File>) {
        val destinations = LinkedHashMap<String, File>()
        destinations["Current folder"] = contentPane().directory
        destinations["Home"] = homeDirectory()
        placeEntries().mapNotNull { it.file?.takeIf { file -> file.isDirectory && !it.isTrashPlace } }
            .forEach { destinations.putIfAbsent(it.name.ifBlank { it.absolutePath }, it) }
        NavigationStateStore.recent(themePrefs()).forEach { destinations.putIfAbsent("Recent: ${it.name}", it) }
        showActionMenu("Restore destination", destinations.map { (name, destination) ->
            name to { promptRestoreDestinationChoice(files, destination) }
        })
    }

    private fun promptRestoreDestinationChoice(files: List<File>, destination: File) {
        val panel = dialogPanel("Restore into ${destination.name.ifBlank { destination.absolutePath }}")
        val body = label(destination.absolutePath, max(10, outputTextSizeSp - 2), false)
        body.setSingleLine(false)
        panel.addView(body, LinearLayout.LayoutParams(-1, dp(44)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        for ((label, choice) in listOf("KEEP BOTH" to ConflictChoice.KEEP_BOTH, "REPLACE" to ConflictChoice.REPLACE, "SKIP" to ConflictChoice.SKIP)) {
            addDialogButton(buttons, label) {
                dialog.dismiss()
                restoreSelected(files, choice, destination)
            }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun clearSelection() {
        showRightCursorHighlight = false
        selectedPaths.clear()
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        updateSelectionBar()
        refreshSelectionHighlights()
    }

    private fun closeShareSelection() {
        shareSelectionMode = false
        clearSelection()
    }

    private fun prepareCopy() {
        val files = selectedFiles()
        if (files.isEmpty()) return clearSelection()
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        showRightCursorHighlight = false
        pendingCopyPaths.addAll(files.map { it.absolutePath })
        selectedPaths.clear()
        updateSelectionBar()
        refreshSelectionHighlights()
        Toast.makeText(this, "Navigate to a folder and tap PASTE", Toast.LENGTH_SHORT).show()
    }

    private fun confirmPaste() {
        val files = pendingCopyPaths.map(::File).filter(File::exists)
        if (files.isEmpty()) return clearSelection()
        val destination = rightPane.directory
        val panel = dialogPanel("Paste ${files.size} items here?")
        val path = label(destination.absolutePath, max(10, outputTextSizeSp - 2), false)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setSingleLine(false)
        path.setTextColor(withAlpha(outputTextColor, 190))
        path.setPadding(dp(8), dp(8), dp(8), dp(8))
        panel.addView(path, LinearLayout.LayoutParams(-1, -2))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addTransferButtons(buttons, dialog = { dialog }, files, destination, false)
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun prepareMove() {
        val files = selectedFiles()
        if (files.isEmpty()) return clearSelection()
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        showRightCursorHighlight = false
        pendingMovePaths.addAll(files.map { it.absolutePath })
        selectedPaths.clear()
        updateSelectionBar()
        refreshSelectionHighlights()
        Toast.makeText(this, "Navigate to a folder and tap MOVE HERE", Toast.LENGTH_SHORT).show()
    }

    private fun confirmMoveHere() {
        val files = pendingMovePaths.map(::File).filter(File::exists)
        if (files.isEmpty()) return clearSelection()
        val destination = rightPane.directory
        val panel = dialogPanel("Move ${files.size} items here?")
        val path = label(destination.absolutePath, max(10, outputTextSizeSp - 2), false)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setSingleLine(false)
        path.setTextColor(withAlpha(outputTextColor, 190))
        path.setPadding(dp(8), dp(8), dp(8), dp(8))
        panel.addView(path, LinearLayout.LayoutParams(-1, -2))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addTransferButtons(buttons, dialog = { dialog }, files, destination, true)
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun addTransferButtons(
        buttons: LinearLayout,
        dialog: () -> PaneDialog,
        files: List<File>,
        destination: File,
        move: Boolean
    ) {
        addDialogButton(buttons, "CANCEL") { dialog().dismiss() }
        addDialogButton(buttons, if (move) "MOVE" else "COPY") {
            dialog().dismiss()
            if (files.any { File(destination, it.name).exists() }) {
                promptTransferConflictChoice(files, destination, move)
            } else {
                runBulkTransfer(files, destination, move, ConflictChoice.KEEP_BOTH)
            }
        }
    }

    private fun promptTransferConflictChoice(files: List<File>, destination: File, move: Boolean) {
        val panel = dialogPanel("If an item already exists")
        val detail = label("Choose how to handle collisions in ${destination.absolutePath}", outputTextSizeSp, false)
        detail.setSingleLine(false)
        panel.addView(detail)
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        for ((label, choice) in listOf("KEEP BOTH" to ConflictChoice.KEEP_BOTH, "REPLACE" to ConflictChoice.REPLACE, "SKIP" to ConflictChoice.SKIP)) {
            addDialogButton(buttons, label) {
                dialog.dismiss()
                runBulkTransfer(files, destination, move, choice)
            }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun runBulkTransfer(files: List<File>, destination: File, move: Boolean, choice: ConflictChoice) {
        val operationId = UUID.randomUUID().toString()
        val showInline = activeOperationId == null
        if (showInline) {
            activeOperationId = operationId
            activeOperationLabel = if (move) "MOVE" else "COPY"
            showOperationProgressDialog(activeOperationLabel, "Preparing…", operationId)
        } else {
            showOutput("JOB CENTER", "Queued ${if (move) "move" else "copy"} for ${files.size} items.")
        }
        val intent = Intent(this, OperationService::class.java).apply {
            putExtra(OperationService.EXTRA_ID, operationId)
            putStringArrayListExtra(OperationService.EXTRA_SOURCES, ArrayList(files.map(File::getAbsolutePath)))
            putExtra(OperationService.EXTRA_DESTINATION, destination.absolutePath)
            putExtra(OperationService.EXTRA_MOVE, move)
            putExtra(OperationService.EXTRA_CHOICE, choice.name)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (error: Exception) {
            if (showInline) {
                activeOperationId = null
                activeTransferStatus = null
                paneDialog?.dismiss()
            }
            showOutput(if (move) "MOVE" else "COPY", "Could not start operation: ${error.message}")
        }
    }

    private fun startArchiveOperation(
        kind: String,
        sources: List<File>,
        destination: File,
        named: Boolean,
        selected: Set<String>
    ) {
        val operationId = UUID.randomUUID().toString()
        val showInline = activeOperationId == null
        if (showInline) activeOperationLabel = if (kind == OperationService.KIND_ZIP) "ZIP" else "EXTRACT"
        val title = if (kind == OperationService.KIND_ZIP) "Creating ZIP" else "Extracting archive"
        if (showInline) {
            activeOperationId = operationId
            showOperationProgressDialog(activeOperationLabel, "Preparing…", operationId)
        } else {
            showOutput("JOB CENTER", "Queued $title.")
        }
        val intent = Intent(this, OperationService::class.java).apply {
            putExtra(OperationService.EXTRA_ID, operationId)
            putStringArrayListExtra(OperationService.EXTRA_SOURCES, ArrayList(sources.map(File::getAbsolutePath)))
            putExtra(OperationService.EXTRA_DESTINATION, destination.absolutePath)
            putExtra(OperationService.EXTRA_KIND, kind)
            putExtra(OperationService.EXTRA_NAMED, named)
            putStringArrayListExtra(OperationService.EXTRA_SELECTED, ArrayList(selected))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (error: Exception) {
            if (showInline) {
                activeOperationId = null
                activeTransferStatus = null
                activeOperationLabel = "OPERATION"
                paneDialog?.dismiss()
            }
            showOutput(if (kind == OperationService.KIND_ZIP) "ZIP" else "EXTRACT", "Could not start operation: ${error.message}")
        }
    }

    private fun startFileOperation(kind: String, sources: List<File>, choice: ConflictChoice, destination: File? = null) {
        if (sources.isEmpty()) return showOutput(kind.uppercase(Locale.US), "Nothing to process")
        val operationId = UUID.randomUUID().toString()
        val showInline = activeOperationId == null
        if (showInline) activeOperationLabel = when (kind) {
                OperationService.KIND_TRASH -> "TRASH"
                OperationService.KIND_RESTORE -> "RESTORE"
                else -> "DELETE"
            }
        val title = when (kind) {
            OperationService.KIND_TRASH -> "Moving to trash"
            OperationService.KIND_RESTORE -> "Restoring items"
            else -> "Deleting items"
        }
        if (showInline) {
            activeOperationId = operationId
            showOperationProgressDialog(activeOperationLabel, "Preparing…", operationId)
        } else {
            showOutput("JOB CENTER", "Queued $title.")
        }
        val intent = Intent(this, OperationService::class.java).apply {
            putExtra(OperationService.EXTRA_ID, operationId)
            putStringArrayListExtra(OperationService.EXTRA_SOURCES, ArrayList(sources.map(File::getAbsolutePath)))
            putExtra(OperationService.EXTRA_DESTINATION, (destination ?: homeDirectory()).absolutePath)
            putExtra(OperationService.EXTRA_KIND, kind)
            putExtra(OperationService.EXTRA_CHOICE, choice.name)
            putExtra(OperationService.EXTRA_RESTORE_ORIGINAL, kind == OperationService.KIND_RESTORE && destination == null)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (error: Exception) {
            val operationLabel = activeOperationLabel
            if (showInline) {
                activeOperationId = null
                activeTransferStatus = null
                activeOperationLabel = "OPERATION"
                paneDialog?.dismiss()
            }
            showOutput(operationLabel, "Could not start operation: ${error.message}")
        }
    }

    private fun confirmBulkTrash() {
        val files = selectedFiles()
        if (files.isEmpty()) return clearSelection()
        val panel = dialogPanel("Move ${files.size} items to trash?")
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "TRASH") {
            dialog.dismiss()
            startFileOperation(OperationService.KIND_TRASH, files, ConflictChoice.KEEP_BOTH)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun shareSelectedFiles() {
        val files = selectedFiles().filter(File::isFile)
        val uris = ArrayList<Uri>()
        files.forEach { uriFor(it)?.let(uris::add) }
        if (uris.isEmpty()) {
            showOutput("SHARE", "Select one or more files")
            return
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.type = "*/*"
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share ${uris.size} files"))
        if (shareSelectionMode) closeShareSelection()
    }

    private fun promptZipSelected() {
        val files = selectedFiles()
        if (files.isEmpty()) return clearSelection()
        val panel = dialogPanel("Create ZIP from ${files.size} items")
        val input = EditText(this)
        input.setSingleLine(true)
        input.typeface = appTypeface
        input.setText("archive.zip")
        input.selectAll()
        input.setTextColor(inputTextColor)
        input.setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
        input.background = addressDrawable()
        input.setPadding(dp(8), 0, dp(8), 0)
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CREATE") {
            val rawName = input.text.toString().trim()
            if (rawName.isEmpty()) {
                input.error = "Enter an archive name"
                return@addDialogButton
            }
            val name = if (rawName.endsWith(".zip", true)) rawName else "$rawName.zip"
            val target = File(rightPane.directory, name)
            if (target.exists()) {
                input.error = "Archive already exists"
                return@addDialogButton
            }
            dialog.dismiss()
            createZip(files, target)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun createZip(files: List<File>, target: File) {
        startArchiveOperation(OperationService.KIND_ZIP, files, target, named = false, selected = emptySet())
    }

    private fun confirmDelete(file: File) {
        if (isInTrash(file)) return confirmDeletePermanently(file)
        val panel = dialogPanel("Delete ${file.name}?")
        val path = label(file.absolutePath, max(10, outputTextSizeSp - 2), false)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setSingleLine(false)
        path.setTextColor(withAlpha(outputTextColor, 190))
        path.setPadding(dp(8), dp(8), dp(8), dp(8))
        panel.addView(path, LinearLayout.LayoutParams(-1, -2))

        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "TRASH") {
            dialog.dismiss()
            val ok = moveToTrash(file)
            reloadAll()
            showOutput("TRASH", if (ok) "Moved to .retui-trash:\n${file.absolutePath}" else "Could not trash ${file.absolutePath}")
        }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(10)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel)
    }

    private fun confirmDeletePermanently(file: File) {
        val panel = dialogPanel("Delete ${file.name} permanently?")
        val warning = label("This cannot be undone.\n${file.absolutePath}", max(10, outputTextSizeSp - 2), false)
        warning.setSingleLine(false)
        warning.setTextColor(outputTextColor)
        warning.setPadding(dp(8), dp(8), dp(8), dp(8))
        panel.addView(warning, LinearLayout.LayoutParams(-1, -2))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "DELETE") {
            dialog.dismiss()
            val deleted = deletePermanently(file)
            if (rightVirtualTitle == "TRASH") showTrashContents() else reloadAll()
            showOutput("DELETE", if (deleted) "Permanently deleted ${file.name}" else "Could not delete ${file.name}")
        }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(10)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel)
    }

    private fun confirmDeleteSelectedPermanently() {
        val files = selectedFiles().filter(::isInTrash)
        if (files.isEmpty()) return clearSelection()
        val panel = dialogPanel("Delete ${files.size} items permanently?")
        val warning = label("This cannot be undone.", max(10, outputTextSizeSp - 2), false)
        warning.gravity = Gravity.CENTER
        panel.addView(warning, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "DELETE") {
            dialog.dismiss()
            val deleted = files.count(::deletePermanently)
            clearSelection()
            showTrashContents()
            showOutput("DELETE", "$deleted of ${files.size} items permanently deleted")
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun deletePermanently(file: File): Boolean {
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
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
            labels.add("Search this folder")
            actions.add { promptSearch(file, "Search ${file.name.ifBlank { "folder" }}") }
            labels.add("New folder here")
            actions.add { promptMkdir(file) }
            if (!isPlace(file)) {
                labels.add("Add to Places")
                actions.add { addPlace(file) }
            }
        }
        labels.add("Properties")
        actions.add { showProperties(file) }
        file.parentFile?.takeIf { it.isDirectory }?.let { parent ->
            labels.add("Reveal parent folder")
            actions.add { showDirectoryContents(parent) }
        }
        if (!isUnderTrash(file) && file.parentFile != null) {
            labels.add("Rename")
            actions.add { promptRename(file) }
        }
        if (isInTrash(file)) {
            labels.add("Restore")
            actions.add { restoreFromTrash(file) }
            labels.add("Delete permanently")
            actions.add { confirmDeletePermanently(file) }
        } else if (!file.name.startsWith("..")) {
            labels.add("Move to trash")
            actions.add { confirmDelete(file) }
        }
        showActionMenu(file.name, labels.zip(actions))
    }

    private fun showProviderItemMenu(item: StorageItem, rootUri: Uri?) {
        val backend = runCatching { SafStorageBackend(this, rootUri ?: activeProvider?.rootUri ?: return) }.getOrNull() ?: return
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (item.uri == backend.rootUri) {
            options += "Open" to { showProviderDirectory(item.uri, backend.rootUri) }
            options += "Remove provider" to {
                ProviderRoots.remove(this, backend.rootUri)
                clearVirtualCategory()
                reloadAll()
            }
        } else {
            val itemCapabilities = backend.capabilitiesFor(item.uri)
            options += (if (item.isDirectory) "Open" else "Preview") to {
                if (item.isDirectory) showProviderDirectory(item.uri, backend.rootUri) else previewProviderItem(item)
            }
            if (itemCapabilities.rename) options += "Rename" to { promptProviderRename(backend, item) }
            if (itemCapabilities.delete) options += "Delete" to {
                confirmProviderMutation("Delete ${item.name}?", "This removes the provider item.") {
                    if (!backend.delete(item.uri)) showOutput("PROVIDER", "Could not delete ${item.name}")
                    else activeProviderUri?.let { showProviderDirectory(it, backend.rootUri, recordHistory = false) }
                }
            }
            activeProviderUri?.let { destination ->
                val destinationCapabilities = backend.capabilitiesFor(destination)
                if (!item.isDirectory && itemCapabilities.copy && destinationCapabilities.create) options += "Copy here" to {
                    confirmProviderMutation("Copy ${item.name}?", "Copy into the current provider folder.") {
                        val targetName = uniqueProviderName(backend, destination, item.name)
                        if (backend.copy(item.uri, destination, targetName) == null) showOutput("PROVIDER", "Could not copy ${item.name}")
                        else showProviderDirectory(destination, backend.rootUri, recordHistory = false)
                    }
                }
                if (itemCapabilities.move && destinationCapabilities.create) options += "Move here" to {
                    confirmProviderMutation("Move ${item.name}?", "Move into the current provider folder.") {
                        val targetName = uniqueProviderName(backend, destination, item.name)
                        if (backend.move(item.uri, destination, targetName) == null) showOutput("PROVIDER", "Could not move ${item.name}")
                        else showProviderDirectory(destination, backend.rootUri, recordHistory = false)
                    }
                }
            }
        }
        showActionMenu(item.name, options)
    }

    private fun promptProviderRename(backend: SafStorageBackend, item: StorageItem) {
        val panel = dialogPanel("Rename provider item")
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(item.name)
            setSelectAllOnFocus(true)
            setTextColor(inputTextColor)
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            background = addressDrawable()
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "RENAME") {
            val name = input.text.toString().trim()
            if (name.isBlank()) {
                input.error = "Enter a name"
                return@addDialogButton
            }
            dialog.dismiss()
            if (backend.rename(item.uri, name) == null) showOutput("PROVIDER", "Could not rename ${item.name}")
            else activeProviderUri?.let { showProviderDirectory(it, backend.rootUri, recordHistory = false) }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun confirmProviderMutation(title: String, detail: String, action: () -> Unit) {
        val panel = dialogPanel(title)
        val body = label(detail, max(10, outputTextSizeSp - 2), false)
        body.setSingleLine(false)
        panel.addView(body, LinearLayout.LayoutParams(-1, dp(52)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CONFIRM") { dialog.dismiss(); action() }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun uniqueProviderName(backend: SafStorageBackend, destination: Uri, name: String): String {
        val existing = backend.list(destination).map { it.name }.toHashSet()
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = if (dot == null) name else name.substring(0, dot)
        val suffix = if (dot == null) "" else name.substring(dot)
        var index = 1
        while (true) {
            val candidate = "$stem ($index)$suffix"
            if (existing.add(candidate)) return candidate
            index++
        }
    }

    private fun selectVisible(invert: Boolean) {
        val paths = rightPane.rows.filter { !it.isParent && !it.isSection && it.file?.name != TRASH_DIR_NAME && (!shareSelectionMode || !it.isDirectory) }
            .mapNotNull { it.archivePath ?: it.file?.absolutePath }
        pendingCopyPaths.clear()
        pendingMovePaths.clear()
        val selected = if (invert) paths.filterNot { it in selectedPaths } else paths
        selectedPaths.clear()
        selectedPaths.addAll(selected)
        updateSelectionBar()
        refreshSelectionHighlights()
    }

    private fun promptRename(file: File) {
        if (isUnderTrash(file)) return showOutput("RENAME", "Restore this item before renaming it.")
        val panel = dialogPanel("Rename")
        val input = EditText(this).apply {
            setSingleLine(true)
            typeface = appTypeface
            setText(file.name)
            selectAll()
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val errorMessage = label("", outputTextSizeSp, false).apply { setSingleLine(false) }
        panel.addView(errorMessage)
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "RENAME") {
            try {
                val target = renameFile(file, input.text.toString())
                if (selectedPaths.remove(file.absolutePath)) selectedPaths.add(target.absolutePath)
                dialog.dismiss()
                reloadAll()
            } catch (e: Exception) {
                errorMessage.text = e.message ?: "Could not rename item"
            }
        }
        panel.addView(buttons)
        dialog = showDialogPanel(panel, input)
    }

    private fun showProperties(file: File) {
        val panel = dialogPanel("Properties")
        val body = label("Calculating…", outputTextSizeSp, false).apply { setSingleLine(false); setTextIsSelectable(true) }
        panel.addView(body, LinearLayout.LayoutParams(-1, -2))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "BACK") { dialog.dismiss() }
        panel.addView(buttons)
        dialog = showDialogPanel(panel)
        val details = "Name: ${file.name}\nPath: ${file.absolutePath}\nType: ${if (file.isDirectory) "Folder" else mimeFor(file)}\nModified: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.lastModified()))}"
        body.text = "$details\nCalculating size…"
        Thread {
            val result = runCatching {
                var bytes = 0L
                var count = 0L
                java.nio.file.Files.walk(file.toPath()).use { paths ->
                    val iterator = paths.iterator()
                    while (iterator.hasNext() && paneDialog === dialog) {
                        val path = iterator.next()
                        if (path != file.toPath()) count++
                        if (java.nio.file.Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) bytes += java.nio.file.Files.size(path)
                    }
                }
                "$details\nSize: ${humanSize(bytes)} ($bytes bytes)" + if (file.isDirectory) "\nContained items: $count" else ""
            }
            runOnUiThread {
                if (paneDialog === dialog) body.text = result.getOrElse { "$details\nSize unavailable: ${it.message}" }
            }
        }.start()
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

    private fun removePlace(path: String) {
        val paths = customPlacePaths().filterNot { it == path }
        themePrefs().edit().putString(PREF_CUSTOM_PLACES, paths.joinToString("\n")).apply()
        reloadAll()
        showOutput("PLACES", "Removed bookmark:\n$path")
    }

    private fun promptMkdir(baseDir: File = contentPane().directory) {
        activeProvider?.let { backend ->
            activeProviderUri?.let { promptProviderMkdir(backend, it) }
            return
        }
        val panel = dialogPanel("New folder")
        val path = label(baseDir.absolutePath, max(10, outputTextSizeSp - 2), false)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setSingleLine(true)
        path.ellipsize = TextUtils.TruncateAt.START
        path.setTextColor(withAlpha(outputTextColor, 190))
        panel.addView(path, LinearLayout.LayoutParams(-1, dp(24)))

        val input = EditText(this)
        input.setSingleLine(true)
        input.typeface = appTypeface
        input.setTextColor(inputTextColor)
        input.setHintTextColor(withAlpha(inputTextColor, 145))
        input.setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
        input.setPadding(dp(8), 0, dp(8), 0)
        input.background = addressDrawable()
        input.hint = "Folder name"
        val inputParams = LinearLayout.LayoutParams(-1, dp(48))
        inputParams.topMargin = dp(8)
        panel.addView(input, inputParams)

        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CREATE") {
            val name = input.text.toString().trim()
            dialog.dismiss()
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
        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(10)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel, input)
    }

    private fun promptProviderMkdir(backend: SafStorageBackend, parent: Uri) {
        if (!backend.capabilitiesFor(parent).create) {
            showOutput("PROVIDER", "This provider does not allow creating folders here.")
            return
        }
        val panel = dialogPanel("New provider folder")
        val input = EditText(this).apply {
            setSingleLine(true)
            setTextColor(inputTextColor)
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            background = addressDrawable()
            hint = "Folder name"
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CREATE") {
            val name = input.text.toString().trim()
            if (name.isBlank()) {
                input.error = "Enter a name"
                return@addDialogButton
            }
            dialog.dismiss()
            if (backend.createDirectory(parent, name) == null) showOutput("PROVIDER", "Could not create $name")
            else showProviderDirectory(parent, backend.rootUri, recordHistory = false)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun promptProviderNewTextFile(backend: SafStorageBackend, parent: Uri) {
        if (!backend.capabilitiesFor(parent).create) {
            showOutput("PROVIDER", "This provider does not allow creating files here.")
            return
        }
        val panel = dialogPanel("New provider text file")
        val input = EditText(this).apply {
            setSingleLine(true)
            setTextColor(inputTextColor)
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            background = addressDrawable()
            hint = "notes.txt"
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CREATE") {
            val name = input.text.toString().trim()
            if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) {
                input.error = "Enter one valid file name"
                return@addDialogButton
            }
            dialog.dismiss()
            if (backend.createFile(parent, name, "text/plain") == null) showOutput("PROVIDER", "Could not create $name")
            else showProviderDirectory(parent, backend.rootUri, recordHistory = false)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun promptNewTextFile(baseDir: File = contentPane().directory) {
        activeProvider?.let { backend ->
            activeProviderUri?.let { promptProviderNewTextFile(backend, it) }
            return
        }
        val panel = dialogPanel("New text file")
        val input = EditText(this).apply {
            setSingleLine(true)
            typeface = appTypeface
            setTextColor(inputTextColor)
            setHintTextColor(withAlpha(inputTextColor, 145))
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            setPadding(dp(8), 0, dp(8), 0)
            background = addressDrawable()
            hint = "notes.txt"
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "CREATE") {
            val name = input.text.toString().trim()
            if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) {
                input.error = "Enter one valid file name"
                return@addDialogButton
            }
            val target = File(baseDir, name)
            if (target.exists()) {
                input.error = "That file already exists"
                return@addDialogButton
            }
            val intent = Intent(this, FmEditorActivity::class.java)
            putThemeExtras(intent)
            intent.putExtra(EXTRA_PATH, target.absolutePath)
            intent.putExtra(EXTRA_CREATE, true)
            dialog.dismiss()
            startActivity(intent)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun duplicateSelected() {
        val files = selectedFiles()
        if (files.isEmpty()) return showOutput("DUPLICATE", "Select one or more items")
        runBulkTransfer(files, contentPane().directory, move = false, choice = ConflictChoice.KEEP_BOTH)
    }

    private fun promptBatchRename() {
        val files = selectedFiles()
        if (files.isEmpty()) return showOutput("RENAME", "Select one or more items")
        val panel = dialogPanel("Batch rename")
        val help = label("Use {n} for the item number. Extensions are preserved.", max(9, outputTextSizeSp - 2), false)
        help.setSingleLine(false)
        panel.addView(help, LinearLayout.LayoutParams(-1, dp(34)))
        val input = EditText(this).apply {
            setSingleLine(true)
            typeface = appTypeface
            setTextColor(inputTextColor)
            setHintTextColor(withAlpha(inputTextColor, 145))
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            setText("file-{n}")
            selectAll()
            setPadding(dp(8), 0, dp(8), 0)
            background = addressDrawable()
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "RENAME") {
            val pattern = input.text.toString().trim()
            val names = files.mapIndexed { index, file ->
                val dot = file.name.lastIndexOf('.').takeIf { it > 0 }
                val extension = if (dot == null) "" else file.name.substring(dot)
                val base = if (pattern.contains("{n}")) pattern.replace("{n}", (index + 1).toString())
                else "$pattern-${index + 1}"
                "$base$extension"
            }
            runCatching { batchRename(files, names) }
                .onSuccess {
                    dialog.dismiss()
                    clearSelection()
                    reloadAll()
                    showOutput("RENAME", "Renamed ${files.size} items")
                }
                .onFailure { input.error = it.message ?: "Could not rename items" }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun confirmEmptyTrash() {
        if (trashFileCount() == 0) return showOutput("TRASH", "Trash is empty")
        val panel = dialogPanel("Empty trash permanently?")
        val body = label("This cannot be undone.", outputTextSizeSp, false)
        panel.addView(body, LinearLayout.LayoutParams(-1, dp(48)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "DELETE") {
            dialog.dismiss()
            val items = trashDirs().flatMap { it.listFiles()?.toList().orEmpty() }
            startFileOperation(OperationService.KIND_DELETE, items, ConflictChoice.KEEP_BOTH)
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun promptSearch(root: File? = null, title: String = "Search") {
        val panel = dialogPanel(title)

        var selectedRoot = root
        val path = label(selectedRoot?.absolutePath ?: "All storage", max(10, outputTextSizeSp - 2), true)
        path.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        path.setSingleLine(true)
        path.ellipsize = TextUtils.TruncateAt.START
        path.setTextColor(withAlpha(outputTextColor, 190))
        path.contentDescription = "Search root. Tap to choose current folder, all storage, a place, or recent folder."
        path.id = R.id.fm_search_root
        panel.addView(path, LinearLayout.LayoutParams(-1, dp(24)))

        val input = EditText(this)
        input.id = R.id.fm_search_query
        input.setSingleLine(true)
        input.typeface = appTypeface
        input.setTextColor(inputTextColor)
        input.setHintTextColor(withAlpha(inputTextColor, 145))
        input.setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
        input.setPadding(dp(8), 0, dp(8), 0)
        input.background = addressDrawable()
        input.hint = "Name, type; size>10M newer>7d"
        val inputParams = LinearLayout.LayoutParams(-1, dp(48))
        inputParams.topMargin = dp(8)
        panel.addView(input, inputParams)

        val chipScroll = android.widget.HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        for ((caption, token) in listOf(
            "NAME" to null,
            "TYPE: IMAGE" to "image",
            "TYPE: PDF" to "pdf",
            "SIZE > 10M" to "size>10M",
            "NEWER 7D" to "newer>7d"
        )) {
            val chip = label(caption, max(9, outputTextSizeSp - 3), true)
            chip.gravity = Gravity.CENTER
            chip.setTextColor(moduleButtonTextColor)
            chip.background = functionButtonBackground()
            chip.setPadding(dp(10), 0, dp(10), 0)
            chip.contentDescription = "Search filter $caption"
            chip.setOnClickListener {
                if (token == null) input.requestFocus()
                else input.append(if (input.text.isNullOrBlank()) token else " $token")
            }
            val chipParams = LinearLayout.LayoutParams(-2, dp(36))
            chipParams.rightMargin = dp(6)
            chips.addView(chip, chipParams)
        }
        chipScroll.addView(chips, LinearLayout.LayoutParams(-2, dp(36)))
        panel.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(40)))

        lateinit var dialog: PaneDialog
        path.setOnClickListener {
            dialog.dismiss()
            showSearchRootMenu { chosen -> promptSearch(chosen, title) }
        }
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "HISTORY") {
            dialog.dismiss()
            showSearchHistory(root)
        }
        addDialogButton(buttons, "SEARCH") {
            val query = input.text.toString().trim()
            dialog.dismiss()
            if (query.isNotEmpty()) runFind(searchRequestFromText(query), selectedRoot)
        }

        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(10)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel, input)
    }

    private fun showSearchRootMenu(onChosen: (File?) -> Unit) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "All storage" to { onChosen(null) }
        items += "Current folder" to { onChosen(contentPane().directory) }
        for (entry in placeEntries()) {
            val file = entry.file ?: continue
            if (entry.isTrashPlace || !file.isDirectory) continue
            items += entry.label to { onChosen(file) }
        }
        NavigationStateStore.recent(themePrefs()).forEach { file ->
            items += "Recent: ${file.name.ifBlank { file.absolutePath }}" to { onChosen(file) }
        }
        showActionMenu("Search root", items)
    }

    private fun rememberSearchQuery(query: String) {
        val values = themePrefs().getString(PREF_SEARCH_HISTORY, "").orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it == query }
            .toMutableList()
        values.add(0, query)
        themePrefs().edit().putString(PREF_SEARCH_HISTORY, values.take(10).joinToString("\n")).apply()
    }

    private fun showSearchHistory(root: File?) {
        val items = themePrefs().getString(PREF_SEARCH_HISTORY, "").orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { query -> query to { runFind(searchRequestFromText(query), root) } }
            .toList()
        showActionMenu("Search history", items)
    }

    private fun promptCommand() {
        val panel = dialogPanel("COMMAND")
        val input = EditText(this).apply {
            setSingleLine(true)
            typeface = appTypeface
            setTextColor(inputTextColor)
            setHintTextColor(withAlpha(inputTextColor, 145))
            setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
            setPadding(dp(8), 0, dp(8), 0)
            background = addressDrawable()
            hint = "find, cd, open, copy, move, trash..."
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val help = label("Commands are local and guarded; shell syntax is disabled.", max(9, outputTextSizeSp - 3), false)
        help.setSingleLine(false)
        help.setTextColor(withAlpha(outputTextColor, 175))
        panel.addView(help, LinearLayout.LayoutParams(-1, dp(34)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "RUN") {
            val command = input.text.toString().trim()
            val result = CommandParser.parse(command)
            dialog.dismiss()
            result.fold(::executeCommand) { showOutput("COMMAND", it.message ?: "Invalid command") }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel, input)
    }

    private fun executeCommand(command: FmCommand) {
        when (command) {
            FmCommand.Pwd -> showOutput("PWD", contentPane().directory.absolutePath)
            FmCommand.ListDirectory -> showOutput("LS", contentPane().rows.filter { it.file != null }.joinToString("\n") { it.label })
            is FmCommand.ChangeDirectory -> resolveCommandDirectory(command.path)?.let(::showDirectoryContents)
            is FmCommand.Find -> runFind(searchRequestFromText(command.query), contentPane().directory)
            is FmCommand.Open -> resolveCommandPath(command.path)?.let { if (it.isDirectory) showDirectoryContents(it) else openFile(it) }
            is FmCommand.Preview -> resolveCommandPath(command.path)?.let { previewFile(it) }
            is FmCommand.MakeDirectory -> createDirectoryFromCommand(command.name)
            is FmCommand.Rename -> renameFromCommand(command.oldName, command.newName)
            is FmCommand.Transfer -> transferFromCommand(command)
            is FmCommand.Trash -> trashFromCommand(command.paths)
            is FmCommand.Zip -> zipFromCommand(command)
            is FmCommand.Extract -> extractFromCommand(command)
            is FmCommand.Favorite -> favoriteFromCommand(command)
            FmCommand.Recent -> showRecentMenu()
        }
    }

    private fun resolveCommandPath(raw: String): File? {
        val path = raw.trim()
        if (path.isEmpty()) return null
        val file = if (path.startsWith(File.separator)) File(path) else File(contentPane().directory, path)
        return file.takeIf { it.exists() } ?: run {
            showOutput("COMMAND", "Not found: $path")
            null
        }
    }

    private fun resolveCommandDirectory(raw: String): File? {
        val clean = raw.trim()
        val known = placeEntries().firstOrNull { it.file?.name.equals(clean, true) || it.label.equals(clean, true) }?.file
        val target = when {
            clean.equals("recent", true) -> NavigationStateStore.recent(themePrefs()).firstOrNull()
            clean.equals("home", true) -> homeDirectory()
            clean == ".." -> contentPane().directory.parentFile
            else -> known ?: resolveCommandPath(clean)
        }
        return target?.takeIf { it.isDirectory && it.canRead() } ?: run {
            showOutput("CD", "Not a readable directory: $clean")
            null
        }
    }

    private fun createDirectoryFromCommand(name: String) {
        val target = File(contentPane().directory, name)
        if (target.exists() || !target.mkdirs()) showOutput("MKDIR", "Could not create ${target.absolutePath}")
        else { reloadAll(); showOutput("MKDIR", "Created ${target.absolutePath}") }
    }

    private fun renameFromCommand(oldName: String, newName: String) {
        val source = resolveCommandPath(oldName) ?: return
        val panel = dialogPanel("Rename ${source.name}?")
        val detail = label("New name: $newName\n${source.absolutePath}", max(10, outputTextSizeSp - 2), false)
        detail.setSingleLine(false)
        panel.addView(detail, LinearLayout.LayoutParams(-1, dp(64)))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss() }
        addDialogButton(buttons, "RENAME") {
            val result = runCatching { renameFile(source, newName) }
            if (result.isSuccess) {
                dialog.dismiss()
                reloadAll()
                showOutput("RENAME", "Renamed ${source.name} to $newName")
            } else {
                showOutput("RENAME", result.exceptionOrNull()?.message ?: "Could not rename ${source.name}")
            }
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun transferFromCommand(command: FmCommand.Transfer) {
        val files = command.sources.mapNotNull(::resolveCommandPath)
        val destination = resolveCommandDirectory(command.destination) ?: return
        if (files.isEmpty()) return
        confirmCommandTransfer(files, destination, command.move)
    }

    private fun confirmCommandTransfer(files: List<File>, destination: File, move: Boolean) {
        val panel = dialogPanel(if (move) "Move ${files.size} items?" else "Copy ${files.size} items?")
        val path = label(destination.absolutePath, max(10, outputTextSizeSp - 2), false)
        path.setSingleLine(false)
        panel.addView(path, LinearLayout.LayoutParams(-1, -2))
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addTransferButtons(buttons, { dialog }, files, destination, move)
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun trashFromCommand(paths: List<String>) {
        val files = paths.mapNotNull(::resolveCommandPath)
        if (files.isEmpty()) return
        selectedPaths.clear()
        selectedPaths.addAll(files.map(File::getAbsolutePath))
        updateSelectionBar()
        confirmBulkTrash()
    }

    private fun zipFromCommand(command: FmCommand.Zip) {
        val files = command.sources.mapNotNull(::resolveCommandPath)
        val archivePath = if (command.archive.startsWith(File.separator)) File(command.archive) else File(contentPane().directory, command.archive)
        val target = archivePath.let { if (it.extension.equals("zip", true)) it else File(it.parentFile, "${it.name}.zip") }
        if (files.isEmpty() || target.exists()) showOutput("ZIP", "Invalid sources or archive already exists")
        else createZip(files, target)
    }

    private fun extractFromCommand(command: FmCommand.Extract) {
        val archive = resolveCommandPath(command.archive) ?: return
        if (!ArchiveExtractor.supports(archive)) return showOutput("EXTRACT", "Unsupported archive: ${archive.name}")
        val destination = command.destination?.let(::resolveCommandDirectory) ?: archive.parentFile
        runArchiveExtraction(archive, emptySet(), destination, named = false)
    }

    private fun favoriteFromCommand(command: FmCommand.Favorite) {
        when (command.action) {
            "add" -> (command.path?.let(::resolveCommandDirectory) ?: contentPane().directory).let(::addPlace)
            "remove" -> command.path?.let(::resolveCommandDirectory)?.let { removePlace(it.absolutePath) }
            "go" -> (command.path ?: "recent").let(::resolveCommandDirectory)?.let(::showDirectoryContents)
            else -> showOutput("FAV", "Usage: fav add|remove|go [path]")
        }
    }

    private fun showRecentMenu() {
        val items = NavigationStateStore.recent(themePrefs()).map { file ->
            file.name.ifBlank { file.absolutePath } to { showDirectoryContents(file) }
        }
        showActionMenu("Recent", items)
    }

    private fun runFind(request: SearchRequest, root: File? = contentPane().directory, recordHistory: Boolean = true) {
        val label = searchLabel(request)
        if (label.isBlank()) {
            showOutput("FIND", "find: [name] [type] [size>10M] [newer>7d]")
            return
        }
        val scope = FilesNavigationContract.searchScope(root, storageRoots())
        val displayRoot = root ?: scope.roots.firstOrNull() ?: homeDirectory()
        if (scope.roots.isEmpty()) {
            if (recordHistory) rememberNavigation()
            showFindRows(label, displayRoot, listOf(FileEntry(null, "No readable storage found. Open File > Storage access.", false)))
            return
        }
        val loadVersion = ++findVersion
        activeSearchCancellation?.set(true)
        val cancellation = AtomicBoolean(false)
        activeSearchCancellation = cancellation
        searchScanned = 0
        if (recordHistory) rememberNavigation()
        showFindRows(label, displayRoot, listOf(FileEntry(null, "Searching for $label...", false)))
        if (recordHistory) rememberSearchQuery(label)
        updateSelectionBar()
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()
        Thread {
            val session = SearchSession(scope.roots, MAX_ROWS, showHidden, { file -> matchesSearch(file, request) }, cancellation)
            val result = session.execute { progress ->
                runOnUiThread {
                    if (activeSearchCancellation === cancellation) {
                        searchScanned = progress.scanned
                        updateSelectionBar()
                    }
                }
            }
            runOnUiThread {
                if (activeSearchCancellation === cancellation) {
                    activeSearchCancellation = null
                    updateSelectionBar()
                }
                if (loadVersion == findVersion) {
                    val rows = if (result.files.isEmpty()) arrayListOf(FileEntry(null, "No matches for $label", false))
                    else ArrayList(result.files.map { file -> FileEntry(file, file.name, file.isDirectory) })
                    if (result.truncated) rows.add(FileEntry(null, "Showing first $MAX_ROWS matches. Refine the search.", false))
                    // Android always blocks folders like Android/data, so only mention it when nothing was found.
                    if (scope.skippedRoots > 0) rows.add(FileEntry(null, "${scope.skippedRoots} storage location(s) could not be searched.", false))
                    if (result.files.isEmpty() && result.inaccessibleRoots > 0) rows.add(FileEntry(null, "Folders Android protects, like Android/data, were not searched.", false))
                    if (result.cancelled) rows.add(FileEntry(null, "Search cancelled after ${result.scanned} items.", false))
                    showFindRows(
                        label,
                        displayRoot,
                        rows
                    )
                }
            }
        }.start()
    }

    private fun searchRequestFromText(text: String): SearchRequest {
        return searchRequestFromParts(splitCommand(text))
    }

    private fun searchRequestFromParts(parts: List<String>): SearchRequest {
        val filters = parseSearchFilters(parts, System.currentTimeMillis())
        if (filters.terms.isEmpty()) return SearchRequest(null, null, filters.minBytes, filters.maxBytes, filters.modifiedAfter, filters.modifiedBefore)
        val last = filters.terms.last()
        return if (filters.terms.size >= 2 && isSearchTypeToken(last)) {
            SearchRequest(cleanSearchTerm(filters.terms.dropLast(1).joinToString(" ")), cleanSearchTerm(last), filters.minBytes, filters.maxBytes, filters.modifiedAfter, filters.modifiedBefore)
        } else {
            SearchRequest(cleanSearchTerm(filters.terms.joinToString(" ")), null, filters.minBytes, filters.maxBytes, filters.modifiedAfter, filters.modifiedBefore)
        }
    }

    private fun cleanSearchTerm(value: String?): String? {
        return value
            ?.trim()
            ?.replace("\\*", "*")
            ?.replace("\\?", "?")
            ?.takeIf { it.isNotEmpty() && it != "*" }
    }

    private fun searchLabel(request: SearchRequest): String {
        val name = request.nameTerm ?: "*"
        val type = request.typeTerm
        val terms = if (type.isNullOrBlank()) request.nameTerm.orEmpty() else "$name $type"
        return listOfNotNull(
            terms.takeIf { it.isNotBlank() },
            request.minBytes?.let { "size>${humanSize(it - 1)}" },
            request.maxBytes?.let { "size<${humanSize(it + 1)}" },
            request.modifiedAfter?.let { "newer" },
            request.modifiedBefore?.let { "older" }
        ).joinToString(" ")
    }

    private fun matchesSearch(file: File, request: SearchRequest): Boolean {
        val size = file.length()
        val modified = file.lastModified()
        return matchesName(file.name, request.nameTerm) && matchesType(file, request.typeTerm)
            && (request.minBytes == null || size >= request.minBytes)
            && (request.maxBytes == null || size <= request.maxBytes)
            && (request.modifiedAfter == null || modified > request.modifiedAfter)
            && (request.modifiedBefore == null || modified < request.modifiedBefore)
    }

    private fun matchesName(name: String, term: String?): Boolean {
        val clean = term?.lowercase(Locale.US)?.trim().orEmpty()
        if (clean.isEmpty()) return true
        val value = name.lowercase(Locale.US)
        if (!clean.contains('*') && !clean.contains('?')) return value.contains(clean)
        return globRegex(clean).matches(value)
    }

    private fun globRegex(pattern: String): Regex {
        val out = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> out.append(".*")
                '?' -> out.append('.')
                else -> out.append(Regex.escape(ch.toString()))
            }
        }
        out.append('$')
        return Regex(out.toString())
    }

    private fun matchesType(file: File, term: String?): Boolean {
        val clean = term?.lowercase(Locale.US)?.trim().orEmpty()
        if (clean.isEmpty() || clean == "*") return true
        val ext = file.extension.lowercase(Locale.US)
        if (clean in DIRECTORY_TYPE_TOKENS) return file.isDirectory
        if (file.isDirectory) return false
        if (clean.startsWith(".")) return ext == clean.drop(1)
        val mime = mimeFor(file)
        return when (clean) {
            "image", "images", "img", "photo", "photos" -> mime.startsWith("image/") || ext in IMAGE_EXTENSIONS
            "video", "videos", "movie", "movies" -> mime.startsWith("video/") || ext in VIDEO_EXTENSIONS
            "audio", "music", "sound" -> mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS
            "doc", "docs", "document", "documents", "text" -> mime in DOCUMENT_MIME_TYPES || ext in DOCUMENT_EXTENSIONS
            "apk", "apks", "android" -> ext == "apk" || mime == "application/vnd.android.package-archive"
            "archive", "archives" -> mime in ARCHIVE_MIME_TYPES || ext in ARCHIVE_EXTENSIONS
            else -> ext == clean
        }
    }

    private fun isSearchTypeToken(token: String): Boolean {
        val clean = token.lowercase(Locale.US).trim()
        return clean.startsWith(".") ||
            clean in SEARCH_TYPE_TOKENS ||
            clean in IMAGE_EXTENSIONS ||
            clean in VIDEO_EXTENSIONS ||
            clean in AUDIO_EXTENSIONS ||
            clean in DOCUMENT_EXTENSIONS ||
            clean in ARCHIVE_EXTENSIONS ||
            clean == "apk"
    }

    private fun showFindRows(query: String, root: File, rows: List<FileEntry>) {
        if (currentScreen != Screen.TREE || treePanesMissing()) {
            currentScreen = Screen.TREE
            contentHost?.removeAllViews()
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            leftPane.directory = root
            leftPane.cursor = 0
            reloadPane(leftPane)
        }
        rightVirtualTitle = "SEARCH: $query"
        rightPane.directory = root
        rightPane.rows.clear()
        rightPane.rows.addAll(rows)
        rightPane.cursor = 0
        rightPane.summary = summarizeRows(rows)
        activePanel = Panel.RIGHT
        renderPane(Panel.LEFT)
        renderPane(Panel.RIGHT)
        updateAddressBar()
    }

    private fun moveToTrash(file: File): Boolean {
        val parent = file.parentFile ?: return false
        val trash = File(parent, TRASH_DIR_NAME)
        if (!trash.exists() && !trash.mkdirs()) return false
        val target = uniqueFile(File(trash, file.name))
        val moved = file.renameTo(target)
        if (moved) {
            target.setLastModified(System.currentTimeMillis())
            rememberTrashDir(trash)
        }
        return moved
    }

    private fun rememberTrashDir(dir: File) {
        rememberTrashDirectory(this, dir)
    }

    private fun isInTrash(file: File): Boolean {
        return file.parentFile?.name == TRASH_DIR_NAME
    }

    private fun isUnderTrash(file: File): Boolean {
        var parent = file.parentFile
        while (parent != null) {
            if (parent.name == TRASH_DIR_NAME) return true
            parent = parent.parentFile
        }
        return false
    }

    private fun runTrashCleanup(force: Boolean = false, report: Boolean = false) {
        val prefs = themePrefs()
        val now = System.currentTimeMillis()
        Thread {
            if (!prefs.getBoolean(PREF_TRASH_RETENTION_INITIALIZED, false)) {
                trashDirs().forEach { root ->
                    root.walkTopDown().filter(File::isFile).forEach { it.setLastModified(now) }
                }
                prefs.edit()
                    .putBoolean(PREF_TRASH_RETENTION_INITIALIZED, true)
                    .putLong(PREF_LAST_TRASH_CLEANUP, now)
                    .apply()
                if (report) runOnUiThread { showOutput("TRASH CLEANUP", "Existing Trash items will age from today.") }
                return@Thread
            }

            val days = prefs.getInt(PREF_TRASH_RETENTION_DAYS, DEFAULT_TRASH_RETENTION_DAYS)
            if (days <= 0) return@Thread
            val lastCleanup = prefs.getLong(PREF_LAST_TRASH_CLEANUP, 0L)
            if (!force && now - lastCleanup < DAY_MILLIS) return@Thread

            val cutoff = now - days * DAY_MILLIS
            var deleted = 0
            trashDirs().forEach { root ->
                root.walkBottomUp().forEach { entry ->
                    when {
                        entry == root -> Unit
                        entry.isFile && entry.lastModified() <= cutoff -> if (entry.delete()) deleted++
                        entry.isDirectory && entry.listFiles()?.isEmpty() == true -> entry.delete()
                    }
                }
            }
            prefs.edit().putLong(PREF_LAST_TRASH_CLEANUP, now).apply()
            if (report) runOnUiThread {
                reloadAll()
                showOutput("TRASH CLEANUP", "$deleted expired item${if (deleted == 1) "" else "s"} deleted.")
            }
        }.start()
    }

    private fun restoreFromTrash(file: File) {
        if (restoreTarget(file)?.exists() == true) {
            promptRestoreConflict(listOf(file))
            return
        }
        startFileOperation(OperationService.KIND_RESTORE, listOf(file), ConflictChoice.KEEP_BOTH)
    }

    private fun restoreTarget(file: File): File? {
        var targetDir = file.parentFile ?: return null
        while (targetDir.name == TRASH_DIR_NAME) targetDir = targetDir.parentFile ?: return null
        return File(targetDir, file.name)
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

    private fun dialogPanel(title: String): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(12), dp(10), dp(12), dp(10))
        panel.background = panelDrawable(PanelRole.OUTPUT)

        val heading = label(title, max(14, outputTextSizeSp + 1), true)
        heading.tag = "commander-title"
        heading.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        heading.setTextColor(headerTextColor)
        panel.addView(heading, LinearLayout.LayoutParams(-1, dp(34)))
        return panel
    }

    private inner class PaneDialog(var host: FrameLayout, val view: View, val returnHome: Boolean) {
        fun dismiss() {
            if (paneDialog !== this) return
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
            host.removeView(view)
            host.getChildAt(0)?.visibility = View.VISIBLE
            paneDialog = null
            if (returnHome) showHome(recordHistory = false)
            else if (pendingPaneRelayout) reflowTreePanes()
            else updateSelectionBar()
        }
    }

    private fun showDialogPanel(panel: View, focus: View? = null): PaneDialog {
        paneDialog?.dismiss()
        val returnHome = currentScreen == Screen.HOME
        if (returnHome) showTree(rightPane.directory, recordHistory = false)
        val host = requireNotNull(rightWorkspace)
        val background = styleDialogPanel(panel)
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(background)
        scroll.addView(panel, FrameLayout.LayoutParams(-1, -2))
        host.getChildAt(0)?.visibility = View.GONE
        selectionBar?.visibility = View.GONE
        selectionSurface?.visibility = View.GONE
        host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val dialog = PaneDialog(host, scroll, returnHome)
        paneDialog = dialog
        if (focus != null) focus.post {
            focus.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(focus, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        return dialog
    }

    private fun styleDialogPanel(panel: View): Int {
        val section = (panel.tag as? String)?.takeIf { it == "menu" || it == "popupmenu" } ?: "dialog"
        val dialogStyle = commanderSkin?.style(section)?.displayed
        val background = (dialogStyle?.background ?: outputPanelColor) or 0xff000000.toInt()
        val foreground = (dialogStyle?.foreground ?: outputTextColor) or 0xff000000.toInt()
        val readable = if (androidx.core.graphics.ColorUtils.calculateContrast(foreground, background) >= 4.5) foreground
            else if (androidx.core.graphics.ColorUtils.calculateLuminance(background) > 0.179) Color.BLACK else Color.WHITE
        fun style(view: View) {
            if (view is TextView) {
                view.setTextColor(readable)
                view.setHintTextColor(readable)
                if (view.isClickable || view is EditText) view.setBackgroundColor(background)
                commanderSkin?.let { skin ->
                    val role = when {
                        view is EditText -> skin.style("core", "input")
                        view.tag == "commander-title" -> skin.style(section, when (section) {
                            "menu" -> "menuhot"
                            "popupmenu" -> "menutitle"
                            else -> "dtitle"
                        })
                        section != "dialog" -> skin.style(section)
                        view.isClickable -> skin.style("dialog", "dfocus")
                        else -> skin.style("dialog")
                    }.displayed
                    styleCommanderText(view, role, background = true)
                }
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        style(panel)
        panel.setBackgroundColor(background)
        return background
    }

    private fun dialogButtonRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        return row
    }

    private fun addDialogButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val button = label(text, outputTextSizeSp, true)
        button.id = R.id.fm_dialog_button
        button.contentDescription = text.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
        button.gravity = Gravity.CENTER
        button.setTextColor(moduleButtonTextColor)
        button.background = panelDrawable(PanelRole.MODULE, interactive = true)
        button.setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(0, dp(48), 1f)
        params.leftMargin = dp(2)
        parent.addView(button, params)
    }

    private fun showOutput(title: String, message: CharSequence) {
        val panel = dialogPanel(title)
        val body = label(message.toString(), max(10, outputTextSizeSp - 1), false)
        body.gravity = Gravity.START
        body.setSingleLine(false)
        body.setTextColor(outputTextColor)
        body.setPadding(dp(8), dp(6), dp(8), dp(6))

        val scroll = ScrollView(this)
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(260)))

        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "OK") { dialog.dismiss() }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(8)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel)
    }

    private fun showHelpPopup() {
        val panel = dialogPanel("HELP")

        val body = label(helpText(), max(10, outputTextSizeSp - 1), false)
        body.gravity = Gravity.START
        body.setSingleLine(false)
        body.setTextColor(outputTextColor)
        body.setPadding(dp(8), dp(6), dp(8), dp(6))

        val scroll = ScrollView(this)
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(430)))

        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "TOUR") {
            dialog.dismiss()
            showSetup(1)
        }
        addDialogButton(buttons, "OK") { dialog.dismiss() }
        val buttonParams = LinearLayout.LayoutParams(-1, dp(48))
        buttonParams.topMargin = dp(8)
        panel.addView(buttons, buttonParams)
        dialog = showDialogPanel(panel)
    }

    private fun helpText(): String {
        val browsing = if (compactLayout()) {
            "Home shows search, categories, recent folders and storage. Browse returns to your last folder.\n" +
                "Tap a folder to open it. Tap any part of the path bar to jump back; < goes up one folder.\n" +
                "Tap a file to preview it; tap the preview or OPEN WITH to choose an app.\n" +
                "Long-press to select, then tap more items. COPY, MOVE, TRASH, SHARE and ZIP appear at the bottom.\n" +
                "Bottom bar: Places, Search, Sort, New and More (File, Selection, View, Command, Options, Help)."
        } else {
            "The left pane lists folders: tap once to select, again to open it on the right.\n" +
                "Tap right-pane items to open or preview them; long-press to mark them for batch actions.\n" +
                "Menus: Places, File, Command, Options and Sort. F1-F10 mirror the function bar."
        }
        return "Browsing:\n$browsing\n" +
            "\nPreviews:\n" +
            "Images, video, audio and PDFs open in a floating window. Text shows line numbers; tap the $ line to toggle wrap.\n" +
            "Files inside archives can be previewed without extracting; EXTRACT asks where to put them.\n" +
            "\nSearch:\n" +
            "Search by name or type: pdf, image, audio, archive, apk or folder.\n" +
            "Add size>10M or size<500K for size limits, newer>7d or older>12h for dates.\n" +
            "Android keeps folders like Android/data off-limits, so they are never searched.\n" +
            "\nCommand line:\n" +
            "Tap the $ line for file commands. From Re:T-UI Launcher: files, files -open <dir>, files -search <name>.\n" +
            "\nAppearance:\n" +
            "FM uses your Launcher colors by default, adjusted where needed so text stays readable.\n" +
            "Options > Appearance switches to Re:TUI gray or one of the MC skins, and sets font and font scale.\n" +
            "\nKeyboard:\n" +
            "Up/Down move, Page Up/Down and Home/End jump, Enter opens, / opens the command line, F1-F10 run the function keys" +
                (if (compactLayout()) "." else ", Left/Right or Tab switch pane.") + "\n" +
            "\nPrivacy:\n" +
            privacySummary()
    }

    /** Full-screen first-run setup in the terminal style: storage access, then a quick start. */
    private fun showSetup(step: Int, firstRun: Boolean = false) {
        val root = workspaceBorder ?: return
        setupOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        setupStep = step
        setupFirstRun = firstRun
        val page = FrameLayout(this)
        page.isClickable = true
        page.setBackgroundColor(outputPanelColor or 0xff000000.toInt())
        page.setPadding(dp(16), dp(16), dp(16), dp(8))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val dim = withAlpha(outputTextColor, 140)
        fun line(text: String, color: Int, bold: Boolean = false, top: Int = 14, sizeOffset: Int = 0) {
            // Plain TextView in the app font: the terminal cell renderer ignores line spacing on wrapped paragraphs.
            val view = TextView(this)
            view.text = text
            view.typeface = Typeface.create(appTypeface ?: Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
            view.setTextSize(scaledFontSp(outputTextSizeSp + 1 + sizeOffset, fontScaleOffsetSp))
            view.setTextColor(color)
            view.setLineSpacing(dpFloat(5f), 1f)
            content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        line("// step ${step + 1} of 2", dim, top = 0)
        if (step == 0) {
            val granted = hasStorageAccess()
            line("$ storage access", moduleTextColor, bold = true, sizeOffset = 2)
            line("// required · FM can't list your files without it", dim, top = 8)
            line("FM reads and manages files on your phone and SD card: browsing, previews, copy, move, ZIP and Trash. " +
                "It doesn't upload anything, and Android still keeps folders like Android/data off-limits.", outputTextColor, top = 18)
            line("You can change this any time in Android settings or from More › File › Storage access.", outputTextColor)
            val status = LinearLayout(this)
            status.orientation = LinearLayout.HORIZONTAL
            status.addView(label("access: ", outputTextSizeSp, false).apply { setTextColor(dim) })
            status.addView(label(if (granted) "granted" else "not granted", outputTextSizeSp, true).apply {
                setTextColor(if (granted) directoryTextColor else moduleTextColor)
            })
            content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        } else {
            line("$ quick start", moduleTextColor, bold = true, sizeOffset = 2)
            line("// tap a file to preview it · tap the preview to open with…", dim, top = 18)
            line("// long-press to select · then copy, move, zip, share or trash", dim)
            line("// tap any part of the path bar to jump back", dim)
            line("// home shows categories, recent folders and storage", dim)
            line("// $ command line for power use · type `help`", dim)
            line("// from Re:T-UI Launcher: files · files -open <dir> · files -search <name>", dim)
        }
        val scroll = ScrollView(this)
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(56) })

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        fun action(text: String, primary: Boolean, onClick: () -> Unit) {
            val view = label("[$text]", outputTextSizeSp, primary)
            view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(4), 0, dp(12), 0)
            view.setTextColor(if (primary) moduleTextColor else outputTextColor)
            view.setOnClickListener { onClick() }
            actions.addView(view, LinearLayout.LayoutParams(-2, dp(48)))
        }
        fun spacer() = actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        if (step == 0) {
            if (!hasStorageAccess()) action("grant access", true) { ensureStorageAccess() }
            spacer()
            if (hasStorageAccess()) action("next", true) { showSetup(1, firstRun) } else action("skip", false) { showSetup(1, firstRun) }
        } else {
            action("back", false) { showSetup(0, firstRun) }
            spacer()
            action("done", true) { finishSetup() }
        }
        page.addView(actions, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setupOverlay = page
    }

    private fun finishSetup() {
        themePrefs().edit().putBoolean(PREF_WALKTHROUGH_COMPLETE, true).apply()
        setupOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        setupOverlay = null
        if (!::leftPane.isInitialized) return
        if (hasStorageAccess()) {
            // Re-run the original request (plain launch, files -open, files -search) now that files are readable.
            paneDialog?.dismiss()
            displayIncomingIntent(intent, resolveStartDirectory(intent))
        } else if (currentScreen == Screen.HOME) showHome(recordHistory = false)
    }

    private fun privacySummary(): String =
        "Files stay on your device unless you choose to share or open them with another Android app. " +
            "Re:T-UI Files has no accounts, ads, analytics, cloud upload, or automatic file transmission."

    private fun currentVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun selectedFile(): File? {
        val entry = activePane().rows.getOrNull(activePane().cursor) ?: return null
        return entry.file
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

    private fun addMenuButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val view = label(text, outputTextSizeSp, true)
        view.id = when (text.lowercase(Locale.US)) {
            "left" -> R.id.fm_menu_left
            "right" -> R.id.fm_menu_right
            "command" -> R.id.fm_menu_command
            "options" -> R.id.fm_menu_options
            "file" -> R.id.fm_menu_file
            "edit" -> R.id.fm_menu_edit
            "view" -> R.id.fm_menu_view
            "go" -> R.id.fm_menu_go
            "places" -> R.id.fm_menu_places
            "help" -> R.id.fm_menu_help
            else -> View.generateViewId()
        }
        view.gravity = Gravity.CENTER
        view.setTextColor(moduleButtonTextColor)
        view.contentDescription = "$text menu"
        commanderSkin?.style("menu", "menuinactive")?.displayed?.let { styleCommanderText(view, it, background = true) }
        view.setOnClickListener { action() }
        parent.addView(view, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun showFileMenu() {
        showActionMenu(
            "File",
            listOf(
                "View  F3" to { runFunctionAction(3) },
                "Properties" to { withSelected("PROPERTIES") { showProperties(it) } },
                "Edit  F4" to { runFunctionAction(4) },
                "Copy  F5" to { runFunctionAction(5) },
                "Rename/Move  F6" to { runFunctionAction(6) },
                "Trash  F8" to { runFunctionAction(8) },
                "Selection and more" to { showEditMenu() },
                "Storage access" to { ensureStorageAccess(userInitiated = true) },
                "Add provider folder" to { pickProviderRoot() },
                "Job center" to { showOperationCenter() },
                "New folder here" to { promptMkdir() },
                "New text file here" to { promptNewTextFile() },
                "Open selected with Android" to { withSelected("OPEN") { openFile(it) } },
                "Share selected file" to { withSelected("SHARE") { shareFile(it) } },
                "Empty trash" to { confirmEmptyTrash() },
                "Refresh" to { reloadAll() },
                "Settings" to { appearanceReturnForm = null; showSettingsMenu() },
                "Close FM" to { finish() }
            )
        )
    }

    private fun showOperationCenter() {
        val records = OperationStore.all(this)
        val panel = dialogPanel("JOB CENTER")
        val intro = label(
            if (records.isEmpty()) "No file operations yet." else "One operation runs at a time; queued work stays here if FM is closed.",
            max(9, outputTextSizeSp - 2),
            false
        )
        intro.setSingleLine(false)
        intro.setTextColor(withAlpha(outputTextColor, 185))
        panel.addView(intro, LinearLayout.LayoutParams(-1, dp(42)))
        for (record in records.take(20)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(4), dp(4), dp(4))
            row.background = functionButtonBackground()
            row.contentDescription = "${operationLabel(record.kind, record.move)} ${record.state.name.lowercase(Locale.US)}"
            val body = label(operationSummary(record), max(10, outputTextSizeSp - 2), true)
            body.setSingleLine(false)
            body.setTextColor(fileTextColor)
            row.addView(body, LinearLayout.LayoutParams(0, -1, 1f))
            if (record.state == OperationState.RUNNING || record.state == OperationState.QUEUED) {
                val cancel = label("CANCEL", max(9, outputTextSizeSp - 3), true)
                cancel.gravity = Gravity.CENTER
                cancel.setTextColor(moduleButtonTextColor)
                cancel.background = functionButtonBackground()
                cancel.contentDescription = "Cancel ${operationLabel(record.kind, record.move)}"
                cancel.setOnClickListener { cancelOperation(record.id); showOperationCenter() }
                row.addView(cancel, LinearLayout.LayoutParams(dp(70), dp(44)))
            }
            val params = LinearLayout.LayoutParams(-1, dp(58))
            params.topMargin = dp(4)
            panel.addView(row, params)
        }
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CLEAR HISTORY") {
            OperationStore.clearFinished(this)
            dialog.dismiss()
            showOperationCenter()
        }
        addDialogButton(buttons, "BACK") { dialog.dismiss() }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
    }

    private fun operationSummary(record: OperationRecord): String {
        val action = operationLabel(record.kind, record.move)
        val progress = "${record.completed}/${record.sourceCount}"
        val bytes = if (record.totalBytes > 0L) "  ${humanSize(record.completedBytes)}/${humanSize(record.totalBytes)}" else ""
        val detail = record.error ?: record.current.takeIf(String::isNotBlank) ?: record.destination
        return "$action  ${record.state.name}\n$progress$bytes  ${detail.take(72)}"
    }

    private fun operationProgressText(completed: Int, total: Int, message: String, completedBytes: Long, totalBytes: Long): String {
        val bytes = if (totalBytes > 0L) "\n${humanSize(completedBytes)} / ${humanSize(totalBytes)}" else ""
        return "$completed/$total complete$bytes${if (message.isBlank()) "" else "\n$message"}"
    }

    private fun showFolderBrowserMenu() {
        showActionMenu("Folder browser", listOf(
            "Open selected folder on right" to { activePanel = Panel.LEFT; openSelected() },
            "Browse selected folder" to { selectedLeftDirectory()?.let(::browseLeftDirectory) },
            "Parent folder" to { leftPane.directory.parentFile?.let(::browseLeftDirectory) },
            "Places" to { showPlacesMenu() },
            "Recent folders" to { showRecentMenu() },
            "Refresh" to { reloadPane(leftPane); renderPane(Panel.LEFT) }
        ))
    }

    private fun selectedLeftDirectory(): File? = leftPane.rows.getOrNull(leftPane.cursor)?.file?.takeIf { it.isDirectory }

    private fun browseLeftDirectory(dir: File) {
        if (!dir.canRead()) return showStorageProblem(dir)
        tappedLeftEntry = null
        leftPane.directory = dir
        leftPane.cursor = 0
        reloadPane(leftPane)
        renderPane(Panel.LEFT)
        leftScrollView?.post { leftScrollView?.scrollTo(0, 0) }
        updateAddressBar()
    }

    private fun showCommandMenu() {
        showActionMenu("Command", listOf(
            "Folder browser" to { showFolderBrowserMenu() },
            "Parent folder" to { goParent() },
            "Path and breadcrumbs" to { showBreadcrumbMenu() },
            "Find file" to { promptSearch(contentPane().directory, "Find file") },
            "Search device" to { promptSearch(null, "Search device") },
            "Places" to { showPlacesMenu() },
            "Recent folders" to { showRecentMenu() },
            "Background jobs" to { showOperationCenter() },
            "File commands" to { promptCommand() },
            "Categories and storage" to { showHome() }
        ))
    }

    private fun showOptionsMenu() {
        fun hasInput(view: View): Boolean = view is EditText ||
            (view is android.view.ViewGroup && (0 until view.childCount).any { hasInput(view.getChildAt(it)) })
        paneDialog?.takeIf { hasInput(it.view) }?.let { form ->
            appearanceReturnForm = form
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(form.view.windowToken, 0)
            form.host.removeView(form.view)
            paneDialog = null
        }
        showActionMenu("Options", listOf(
            "Appearance" to { showAppearanceMenu() },
            "Panel options" to { appearanceReturnForm = null; showViewMenu() },
            "Settings" to { appearanceReturnForm = null; showSettingsMenu() },
            "Help" to { appearanceReturnForm = null; showHelpPopup() }
        ))
    }

    private fun showSettingsMenu() {
        val days = themePrefs().getInt(PREF_TRASH_RETENTION_DAYS, DEFAULT_TRASH_RETENTION_DAYS)
        val value = if (days <= 0) "Never" else "$days days"
        showActionMenu(
            "Settings",
            listOf(
                "Trash cleanup: $value" to { showTrashRetentionMenu() },
                "Appearance" to { showAppearanceMenu() },
                "Rate on Google Play" to { openPlayListing() },
                "Send feedback" to { openExternal(Uri.parse(FEEDBACK_URL), null, "Feedback") },
                "Privacy" to { showOutput("PRIVACY", "${privacySummary()}\n\nPolicy:\n$PRIVACY_URL") }
            )
        )
    }

    private fun showAppearanceMenu() {
        val prefs = themePrefs()
        val overridden = prefs.getBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)
        val launcherAvailable = launcherThemeAvailable()
        val source = when {
            overridden -> "Custom colors"
            commanderSkin != null -> commanderSkin!!.id
            usingLauncherPalette -> "Launcher colors"
            else -> "Re:TUI gray"
        }
        val items = mutableListOf<Pair<String, () -> Unit>>()
        items += "Theme source: $source" to {
            showOutput("THEME SOURCE", "Using $source colors.")
        }
        items += "MC skins (${commanderSkins.size})" to { showCommanderSkinPicker() }
        items += "MC skin credits & license" to {
            val license = assets.open("mc-skins/LICENSE.txt").bufferedReader().use { it.readText() }
            showOutput("MC SKIN CREDITS", "Midnight Commander skin collection\nhttps://github.com/MidnightCommander/mc\n\nOriginal author credits are preserved in the bundled skin files.\n\n$license")
        }
        if (launcherAvailable) items += "Use Launcher colors" to { applyThemeChoice(null) }
        items += "Use Re:TUI gray colors" to { applyThemeChoice(RETUI_GRAY_CHOICE) }
        themeColorBindings().forEach { binding ->
            items += "${binding.label}: ${colorHex(binding.get())}" to { showThemeColorEditor(binding) }
        }
        val acceptFrames = LauncherFrameStore(this).isAccepted()
        items += "Accept frames from Launcher: ${if (acceptFrames) "On" else "Off"}" to {
            toggleLauncherFrames(acceptFrames)
        }
        items += "Font: ${currentFontModeLabel()}" to { showFontMenu() }
        items += "Font scale: ${fontScaleLabel(fontScaleOffsetSp)}" to { showFontScaleMenu() }
        showActionMenu("Appearance", items)
    }

    private fun showCommanderSkinPicker() {
        val launcherAvailable = launcherThemeAvailable()
        val choiceIds = (if (launcherAvailable) listOf<String?>(null) else emptyList()) + RETUI_GRAY_CHOICE + commanderSkins.map { it.id }
        val skins = choiceIds.map { id -> commanderSkins.firstOrNull { it.id == id } }
        fun choiceLabel(position: Int) = when (choiceIds[position]) {
            null -> "Launcher colors"
            RETUI_GRAY_CHOICE -> "Re:TUI gray"
            else -> skins[position]?.id ?: "?"
        }
        val current = themePrefs().getString(PREF_COMMANDER_SKIN, null)
        var chosen = choiceIds.indexOf(if (current == null && !launcherAvailable) RETUI_GRAY_CHOICE else current).coerceAtLeast(0)
        val panel = dialogPanel("MC skins")
        val name = label("", outputTextSizeSp, true)
        name.setSingleLine(false)
        panel.addView(name, LinearLayout.LayoutParams(-1, dp(48)))
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = "Skin preview"
        }
        val samples = listOf("Places File Command", "/storage  →", "Name       Size", "/Download  SUB-DIR", "*notes.txt     2K", " readme.md     1K", "1 Help  5 Copy  8 Trash")
            .map { text -> label(text, 9, false).also { preview.addView(it, LinearLayout.LayoutParams(-1, dp(20))) } }
        panel.addView(preview, LinearLayout.LayoutParams(-1, dp(150)))
        val list = ListView(this).apply {
            divider = null
            contentDescription = "MC skin collection"
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = skins.size
            override fun getItem(position: Int) = skins[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val row = (convertView as? TextView) ?: label("", outputTextSizeSp, false).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), 0, dp(4), 0)
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = AbsListView.LayoutParams(-1, dp(40))
                }
                row.text = (if (position == chosen) "● " else "  ") + choiceLabel(position)
                row.contentDescription = skins[position]?.let { "${it.id}, ${it.description}" } ?: choiceLabel(position)
                row.setTextColor(if (position == chosen) selectionTextColor else outputTextColor)
                row.setBackgroundColor(if (position == chosen) selectionBgColor else outputPanelColor)
                return row
            }
        }
        list.adapter = adapter
        panel.addView(list, LinearLayout.LayoutParams(-1, dp(240)))
        fun renderPreview() {
            val skin = skins[chosen]
            val launcher = choiceIds[chosen] == null
            name.text = skin?.let { "${it.id}\n${it.colorMode}" }
                ?: if (launcher) "Launcher colors\nAdjusted for readability" else "Re:TUI gray\nCurrent compact palette"
            val prefs = themePrefs()
            fun stored(key: String, fallback: Int) = if (prefs.contains(key)) prefs.getInt(key, fallback) else fallback
            val launcherBase = stored(RetuiVisualContract.OUTPUT_BG, Color.rgb(55, 55, 55))
            val launcherSelection = Readability.separate(stored(RetuiVisualContract.SELECTION_BG, Color.rgb(0, 96, 96)), launcherBase, 1.6)
            val core = skin?.style("core")?.displayed ?: if (launcher) CommanderStyle(
                Readability.ensureContrast(stored(RetuiVisualContract.OUTPUT_TEXT, Color.rgb(210, 210, 210)), launcherBase, Readability.BODY_TEXT), launcherBase
            ) else CommanderStyle(Color.rgb(210, 210, 210), Color.rgb(55, 55, 55))
            val selected = skin?.style("core", "selected")?.displayed ?: if (launcher) CommanderStyle(
                Readability.ensureContrast(stored(RetuiVisualContract.SELECTION_TEXT, Color.WHITE), launcherSelection, Readability.TEXT), launcherSelection
            ) else CommanderStyle(Color.WHITE, Color.rgb(0, 96, 96))
            val directory = skin?.style("filehighlight", "directory")?.displayed ?: core.copy(foreground = if (launcher)
                Readability.ensureContrast(stored(RetuiVisualContract.DIRECTORY_TEXT, core.foreground), launcherBase, Readability.TEXT) else Color.rgb(200, 196, 151))
            val styles = listOf(skin?.style("menu", "menuinactive")?.displayed ?: core,
                skin?.style("core", "reverse")?.displayed ?: selected,
                skin?.style("core", "header")?.displayed ?: directory, directory,
                skin?.style("core", "marked")?.displayed ?: directory, selected,
                skin?.style("buttonbar", "button")?.displayed ?: core)
            samples.zip(styles).forEach { (view, style) -> styleCommanderText(view, style, background = true) }
            val border = skin?.style("core", "frame")?.displayed?.foreground ?: Color.LTGRAY
            preview.background = CommanderFrameDrawable(core.background, border, dpFloat(1f), skin?.doubleFrame == true)
            adapter.notifyDataSetChanged()
        }
        list.setOnItemClickListener { _, _, position, _ -> chosen = position; renderPreview() }
        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss(); restoreAppearanceForm() }
        addDialogButton(buttons, "APPLY") { dialog.dismiss(); applyThemeChoice(choiceIds[chosen]) }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        dialog = showDialogPanel(panel)
        renderPreview()
        list.setSelection(chosen)
    }

    /** null = Launcher colours (the default), [RETUI_GRAY_CHOICE], or an MC skin id. */
    private fun applyThemeChoice(choice: String?) {
        val prefs = themePrefs()
        val editor = prefs.edit().putBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)
        if (choice == null) editor.remove(PREF_COMMANDER_SKIN) else editor.putString(PREF_COMMANDER_SKIN, choice)
        themeColorBindings().forEach { editor.remove(PREF_THEME_COLOR_PREFIX + it.key) }
        editor.apply()
        refreshThemeUi()
    }

    private fun refreshThemeUi() {
        val leftScroll = leftScrollView?.scrollY ?: 0
        val first = rightListView?.firstVisiblePosition ?: 0
        val offset = rightListView?.getChildAt(0)?.top ?: 0
        applyThemeExtras(null)
        rightListAdapter = null
        setContentView(buildUi())
        if (currentScreen == Screen.HOME) showHome(recordHistory = false)
        else {
            contentHost?.addView(buildTreePanes(), FrameLayout.LayoutParams(-1, -1))
            renderAll()
            updateSelectionBar()
            leftScrollView?.post { leftScrollView?.scrollTo(0, leftScroll) }
            rightListView?.post { rightListView?.setSelectionFromTop(first, offset) }
        }
        restoreAppearanceForm()
    }

    private fun restoreAppearanceForm() {
        val form = appearanceReturnForm ?: return
        appearanceReturnForm = null
        paneDialog?.dismiss()
        val host = rightWorkspace ?: return
        (form.view.parent as? android.view.ViewGroup)?.removeView(form.view)
        form.host = host
        val scroll = form.view as ScrollView
        scroll.setBackgroundColor(styleDialogPanel(scroll.getChildAt(0)))
        host.getChildAt(0)?.visibility = View.GONE
        selectionBar?.visibility = View.GONE
        selectionSurface?.visibility = View.GONE
        host.addView(form.view, FrameLayout.LayoutParams(-1, -1))
        paneDialog = form
    }

    private fun styleCommanderText(view: TextView, style: CommanderStyle, background: Boolean = false) {
        view.setTextColor(style.foreground)
        if (background) view.setBackgroundColor(style.background)
        val fontStyle = (if ("bold" in style.attributes) Typeface.BOLD else 0) or
            (if ("italic" in style.attributes) Typeface.ITALIC else 0)
        view.setTypeface(appTypeface ?: Typeface.MONOSPACE, fontStyle)
        view.paintFlags = if ("underline" in style.attributes) view.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            else view.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
    }

    private fun showThemeColorEditor(binding: ThemeColorBinding) {
        var color = binding.get()
        val panel = dialogPanel(binding.label)
        val swatch = View(this)
        val swatchParams = LinearLayout.LayoutParams(-1, dp(52))
        swatchParams.setMargins(0, dp(8), 0, dp(8))
        panel.addView(swatch, swatchParams)

        val input = EditText(this)
        input.setSingleLine(true)
        input.setSelectAllOnFocus(true)
        input.typeface = appTypeface
        input.setTextColor(inputTextColor)
        input.setTextSize(scaledFontSp(inputFontSizeSp, fontScaleOffsetSp))
        input.background = addressDrawable()
        input.setPadding(dp(8), 0, dp(8), 0)
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(44)))

        fun render(next: Int) {
            color = next
            input.setText(colorHex(color))
            input.setSelection(input.text.length)
            swatch.background = GradientDrawable().apply {
                setColor(color)
                setStroke(dp(1), outputBorderColor)
            }
        }

        listOf('A', 'R', 'G', 'B').forEach { channel ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val value = label("$channel ${colorChannel(color, channel)}", max(9, outputTextSizeSp - 2), true)
            value.gravity = Gravity.CENTER_VERTICAL
            row.addView(value, LinearLayout.LayoutParams(dp(54), dp(38)))
            val slider = SeekBar(this)
            slider.max = 255
            slider.progress = colorChannel(color, channel)
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    render(replaceColorChannel(color, channel, progress))
                    value.text = "$channel ${colorChannel(color, channel)}"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            row.addView(slider, LinearLayout.LayoutParams(0, dp(38), 1f))
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(38)))
        }

        lateinit var dialog: PaneDialog
        val buttons = dialogButtonRow()
        addDialogButton(buttons, "CANCEL") { dialog.dismiss(); restoreAppearanceForm() }
        addDialogButton(buttons, "APPLY") {
            val parsed = RetuiVisualContract.parseColor(input.text.toString())
            if (parsed == null) {
                input.error = "Use #AARRGGBB or #RRGGBB"
                return@addDialogButton
            }
            binding.set(parsed)
            saveThemeColorOverride()
            dialog.dismiss()
            refreshThemeUi()
        }
        panel.addView(buttons, LinearLayout.LayoutParams(-1, dp(48)))
        render(color)
        dialog = showDialogPanel(panel, input)
    }

    private fun colorChannel(color: Int, channel: Char): Int = when (channel) {
        'A' -> Color.alpha(color)
        'R' -> Color.red(color)
        'G' -> Color.green(color)
        else -> Color.blue(color)
    }

    private fun replaceColorChannel(color: Int, channel: Char, value: Int): Int = Color.argb(
        if (channel == 'A') value else Color.alpha(color),
        if (channel == 'R') value else Color.red(color),
        if (channel == 'G') value else Color.green(color),
        if (channel == 'B') value else Color.blue(color)
    )

    private fun colorHex(color: Int): String = String.format(Locale.US, "#%08X", color)

    private fun openPlayListing() {
        openExternal(Uri.parse(PLAY_MARKET_URL), Uri.parse(PLAY_HTTPS_URL), "Google Play")
    }

    private fun openExternal(primary: Uri, fallback: Uri?, label: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, primary))
        } catch (_: ActivityNotFoundException) {
            if (fallback != null) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, fallback))
                } catch (_: ActivityNotFoundException) {
                    showOutput(label, "No app is available to open this link.\n\n$fallback")
                }
            } else {
                showOutput(label, "No app is available to open this link.\n\n$primary")
            }
        }
    }

    private fun toggleLauncherFrames(current: Boolean) {
        if (LauncherFrameStore(this).setAccepted(!current)) {
            launcherFrameRuntime = FilesFrameRuntime.load(this)
            refreshThemeUi()
        }
        else Toast.makeText(this, "Could not save frame setting.", Toast.LENGTH_SHORT).show()
    }

    private fun showFontMenu() {
        showActionMenu(
            "Font",
            listOf(
                "Import custom .ttf or .otf" to { launchFontImportPicker() },
                "Follow Launcher" to { setFontOverride(FONT_MODE_LAUNCHER) },
                "Use system font" to { setFontOverride(FONT_MODE_SYSTEM) },
                "Use Re:T-UI monospace" to { setFontOverride(FONT_MODE_DEFAULT) }
            )
        )
    }

    private fun showFontScaleMenu() {
        showActionMenu(
            "Font scale",
            FONT_SCALE_CATEGORIES.map { (offset, label) ->
                label to {
                    themePrefs().edit().putInt(PREF_FONT_SCALE_OFFSET, offset).apply()
                    recreate()
                }
            }
        )
    }

    private fun launchFontImportPicker() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, FONT_MIME_TYPES)
        }
        try {
            startActivityForResult(picker, FONT_IMPORT_REQUEST)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Font picker is unavailable on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importSelectedFont(uri: Uri) {
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
                }
        } catch (_: Exception) {
            null
        }
            ?: uri.lastPathSegment
            ?: "retui-font.ttf"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!isSupportedFontName(safeName)) {
            Toast.makeText(this, "Choose a .ttf or .otf font file.", Toast.LENGTH_LONG).show()
            return
        }
        val fontDir = File(filesDir, "fonts")
        val target = uniqueFontTarget(fontDir, safeName)
        try {
            check(fontDir.exists() || fontDir.mkdirs()) { "Unable to create the font folder." }
            contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Unable to read the selected font." }
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_FONT_BYTES) { "Font file is too large." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(target.length() in 1..MAX_FONT_BYTES) { "Font file is empty or too large." }
            Typeface.createFromFile(target)
            themePrefs().edit()
                .putString(PREF_FONT_MODE, FONT_MODE_CUSTOM)
                .putString(PREF_CUSTOM_FONT_PATH, target.absolutePath)
                .apply()
            recreate()
        } catch (error: Exception) {
            target.delete()
            Toast.makeText(this, "Font import failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun uniqueFontTarget(directory: File, name: String): File {
        val extension = "." + name.substringAfterLast('.', "ttf")
        val stem = name.removeSuffix(extension)
        var target = File(directory, name)
        var suffix = 2
        while (target.exists()) target = File(directory, "$stem-$suffix${extension.lowercase(Locale.US)}").also { suffix++ }
        return target
    }

    private fun setFontOverride(mode: String?) {
        themePrefs().edit().apply {
            if (mode == null) remove(PREF_FONT_MODE) else putString(PREF_FONT_MODE, mode)
        }.apply()
        recreate()
    }

    private fun currentFontModeLabel(): String = when (themePrefs().getString(PREF_FONT_MODE, null)) {
        FONT_MODE_CUSTOM -> File(themePrefs().getString(PREF_CUSTOM_FONT_PATH, "").orEmpty()).name.ifBlank { "Custom" }
        FONT_MODE_SYSTEM -> "System"
        FONT_MODE_LAUNCHER -> "Follow Launcher"
        else -> "Re:T-UI monospace"
    }

    private fun showTrashRetentionMenu() {
        val options = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Never")
        showActionMenu(
            "Trash cleanup",
            options.map { (days, label) ->
                label to {
                    themePrefs().edit()
                        .putInt(PREF_TRASH_RETENTION_DAYS, days)
                        .putLong(PREF_LAST_TRASH_CLEANUP, 0L)
                        .apply()
                    if (days > 0) runTrashCleanup(force = true, report = true)
                    else showOutput("TRASH CLEANUP", "Automatic cleanup disabled.")
                }
            }
        )
    }

    private fun showEditMenu() {
        showActionMenu(
            "Selection and actions",
            listOf(
                "Rename selected" to { withSelected("RENAME") { promptRename(it) } },
                "Batch rename selected" to { promptBatchRename() },
                "Duplicate selected" to { duplicateSelected() },
                "Properties" to { withSelected("PROPERTIES") { showProperties(it) } },
                "Select all" to { selectVisible(false) },
                "Invert selection" to { selectVisible(true) },
                "Clear selection" to { clearSelection() },
                "Share marked files" to { shareSelectedFiles() },
                "ZIP marked files" to { promptZipSelected() },
                "Edit selected text" to { withSelected("EDIT") { editFile(it) } },
                "Move selected to trash" to { withSelected("TRASH") { confirmDelete(it) } }
            )
        )
    }

    private fun showViewMenu() {
        showActionMenu(
            "View",
            listOf(
                "Hidden files: ${if (showHidden) "shown" else "hidden"}" to {
                    showHidden = !showHidden
                    themePrefs().edit().putBoolean("show_hidden", showHidden).apply()
                    selectedPaths.clear()
                    reloadAll()
                },
                "Preview selected" to { withSelected("PREVIEW") { previewFile(it) } },
                "Search current folder" to { promptSearch(contentPane().directory, "Search current folder") },
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
        val items = placeEntries(includeRecent = false).mapNotNull { entry ->
            if (entry.isTrashPlace) return@mapNotNull entry.label to { showTrashContents() }
            val file = entry.file ?: return@mapNotNull null
            entry.label to { showDirectoryContents(file) }
        }.toMutableList()
        items.add(0, "Folder browser" to { showFolderBrowserMenu() })
        items.add(1, "Recent folders" to { showRecentMenu() })
        if (customPlacePaths().isNotEmpty()) {
            items += "Manage bookmarks" to {
                showActionMenu("Remove bookmark", customPlacePaths().map { path ->
                    File(path).name.ifBlank { path } to { removePlace(path) }
                })
            }
        }
        ProviderRoots.list(this).forEach { uri ->
            val backend = SafStorageBackend(this, uri)
            items += "Provider: ${backend.displayName}" to { openProviderRoot(uri) }
        }
        showActionMenu("Places", items)
    }

    private fun pickProviderRoot() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, PROVIDER_ROOT_REQUEST) }
            .onFailure { showOutput("PROVIDER", "Android could not open the provider folder picker.") }
    }

    private fun openProviderRoot(uri: Uri) {
        showProviderDirectory(uri, uri)
    }

    private fun showBreadcrumbMenu() {
        if (currentScreen != Screen.TREE || activeArchive != null || rightVirtualTitle != null) return
        val current = rightPane.directory
        val chain = ArrayList<File>()
        var cursor: File? = current
        while (cursor != null) {
            chain += cursor
            cursor = cursor.parentFile
        }
        showActionMenu(
            "Breadcrumbs",
            chain.asReversed().map { folder ->
                (folder.name.ifBlank { folder.absolutePath }) to { showDirectoryContents(folder) }
            }
        )
    }

    private fun showActionMenu(title: String, items: List<Pair<String, () -> Unit>>) {
        if (items.isEmpty()) {
            showOutput(title, "No actions available")
            return
        }
        val panel = dialogPanel(title)
        panel.tag = if (title in setOf("Places", "File", "Command", "Options", "Sort order", "Menus")) "menu" else "popupmenu"

        lateinit var dialog: PaneDialog
        for ((text, action) in items) {
            val row = label(text, max(11, outputTextSizeSp - 1), true)
            row.setSingleLine(false)
            row.maxLines = 2
            row.ellipsize = TextUtils.TruncateAt.END
            row.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            row.setPadding(dp(8), 0, dp(8), 0)
            row.setTextColor(moduleButtonTextColor)
            row.background = functionButtonBackground()
            row.setOnClickListener {
                dialog.dismiss()
                action()
            }
            val params = LinearLayout.LayoutParams(-1, dp(48))
            params.topMargin = dp(4)
            panel.addView(row, params)
        }

        val close = dialogButtonRow()
        addDialogButton(close, "BACK") {
            dialog.dismiss()
            if (title == "Options" || title == "Appearance") restoreAppearanceForm()
        }
        panel.addView(close)
        dialog = showDialogPanel(panel)
    }

    private fun withSelected(title: String, action: (File) -> Unit) {
        val file = selectedFile()
        if (file == null || !file.exists()) {
            showOutput(title, "No selected item")
            return
        }
        action(file)
    }

    private fun label(text: String?, sizeSp: Int, bold: Boolean): TextView {
        val view = TerminalLabelView(this)
        view.text = text ?: ""
        view.setTextColor(outputTextColor)
        view.setTextSize(scaledFontSp(sizeSp, fontScaleOffsetSp))
        view.typeface = appTypeface ?: Typeface.MONOSPACE
        if (bold) view.setTypeface(view.typeface, Typeface.BOLD)
        view.includeFontPadding = false
        view.isSingleLine = true
        // The terminal renderer uses the visible width, not TextView's scrolling single-line canvas.
        view.setHorizontallyScrolling(false)
        return view
    }

    private fun column(text: String, weight: Float, gravity: Int): TextView {
        val view = label(text, outputTextSizeSp, true)
        view.gravity = gravity or Gravity.CENTER_VERTICAL
        view.setPadding(dp(2), 0, dp(2), 0)
        view.setTextColor(moduleTextColor)
        view.background = ColorDrawable(FmVisualInterop.scaleColorAlpha(modulePanelColor, 210))
        commanderSkin?.style("core", "header")?.displayed?.let { styleCommanderText(view, it, background = true) }
        view.layoutParams = LinearLayout.LayoutParams(0, -1, weight)
        return view
    }

    private fun sectionRow(text: String): TextView {
        val view = label(text, max(9, outputTextSizeSp - 3), true)
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(5), 0, dp(2), 0)
        view.setTextColor(withAlpha(moduleTextColor, 175))
        view.background = ColorDrawable(FmVisualInterop.scaleColorAlpha(moduleButtonBgColor, 80))
        return view
    }

    private fun rowTextColor(selected: Boolean, directory: Boolean): Int {
        if (selected) return selectionTextColor
        return if (directory) directoryTextColor else fileTextColor
    }

    private fun commanderFileStyle(entry: FileEntry, marked: Boolean, cursor: Boolean): CommanderStyle? {
        val skin = commanderSkin ?: return null
        if (marked) return skin.style("core", if (cursor) "markselect" else "marked").displayed
        if (cursor) return skin.style("core", "selected").displayed
        val ext = entry.label.substringAfterLast('.', "").lowercase(Locale.US)
        val role = when {
            entry.isDirectory -> "directory"
            ext in ARCHIVE_EXTENSIONS -> "archive"
            ext in IMAGE_EXTENSIONS -> "graph"
            ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS -> "media"
            ext in setOf("c", "h", "cpp", "hpp", "kt", "java", "py", "js", "ts", "sh", "lua", "rs", "go", "xml", "json", "yaml", "yml") -> "source"
            ext in setOf("db", "sqlite", "sql") -> "database"
            ext in setOf("tmp", "bak", "old") -> "temp"
            ext in DOCUMENT_EXTENSIONS -> "doc"
            entry.file?.canExecute() == true -> "executable"
            else -> "_default_"
        }
        return skin.style("filehighlight", role).displayed
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
        val entries = rows.filter { !it.isParent && !it.isSection && (it.file != null || it.archivePath != null) }
        val dirs = entries.count { it.isDirectory }
        val files = entries.size - dirs
        if (!includeSize) return "$dirs dirs | $files files"
        val size = entries.filterNot { it.isDirectory }.sumOf { it.file?.length() ?: it.size }
        return "$dirs dirs | $files files: ${humanSize(size)}"
    }

    private fun progressBar(value: Long, total: Long): String {
        val width = 10
        val filled = if (total <= 0) 0 else ((value.toDouble() / total) * width).toInt().coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }

    private fun nameCell(entry: FileEntry, selected: Boolean): View {
        val color = rowTextColor(selected, entry.isDirectory)
        val wrap = LinearLayout(this)
        wrap.id = R.id.fm_file_row
        wrap.contentDescription = when {
            entry.isParent -> "Parent folder"
            entry.isDirectory -> "Folder ${entry.label}"
            else -> "File ${entry.label}"
        }
        wrap.orientation = LinearLayout.HORIZONTAL
        wrap.gravity = Gravity.CENTER_VERTICAL
        wrap.setPadding(dp(2), 0, dp(2), 0)

        val prefix = label("/", outputTextSizeSp, true)
        prefix.gravity = Gravity.CENTER_VERTICAL
        prefix.setTextColor(color)
        wrap.addView(prefix, LinearLayout.LayoutParams(dp(12), -1))
        val text = label(if (entry.isParent) ".." else entry.label, outputTextSizeSp, entry.isDirectory)
        text.setTextColor(color)
        text.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        text.setPadding(dp(3), 0, 0, 0)
        text.ellipsize = TextUtils.TruncateAt.END
        wrap.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
        commanderFileStyle(entry, false, selected)?.let { style ->
            wrap.setBackgroundColor(style.background)
            styleCommanderText(prefix, style)
            styleCommanderText(text, style)
        }
        return wrap
    }

    private fun glyphView(text: String, color: Int, sizeSp: Int): TextView {
        // Icons skip the terminal cell renderer, which would squeeze square glyphs into a narrow text cell.
        val view = TextView(this)
        view.text = text
        view.setTextSize(scaledFontSp(sizeSp, fontScaleOffsetSp))
        view.includeFontPadding = false
        view.isSingleLine = true
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
        return if (unit == 0) "$size B" else String.format(Locale.US, "%.1f%s", value, units[unit])
    }

    private fun abbreviatePath(path: String): String {
        return if (path.length <= 42) path else "..." + path.takeLast(39)
    }

    private fun isLikelyText(file: File): Boolean {
        val name = file.name.lowercase(Locale.US)
        val ext = name.substringAfterLast('.', "")
        return ext in setOf("txt", "md", "json", "csv", "xml", "html", "css", "js", "kt", "java", "log", "yaml", "yml", "ignore", "gitignore",
            "ini", "properties", "conf", "cfg", "toml", "env", "sh", "py", "ts", "gradle", "kts", "c", "h", "cpp", "rs", "go", "lua", "sql", "srt", "tsv") ||
            file.name.startsWith(".") && file.length() <= PREVIEW_MAX_BYTES
    }

    private fun uriFor(file: File): Uri? {
        val allowedRoots = storageRoots() + listOf(filesDir, cacheDir)
        if (!FilesNavigationContract.isWithin(file, allowedRoots)) {
            showOutput("FILE", "Could not share file URI:\nOutside configured storage roots: ${file.absolutePath}")
            return null
        }
        return try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file.canonicalFile)
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

    private fun homeDirectory(): File {
        val external = Environment.getExternalStorageDirectory()
        return if (external.exists()) external else File("/")
    }

    private fun isStorageRoot(dir: File): Boolean =
        dir.absolutePath == homeDirectory().absolutePath || storageRoots().any { it.absolutePath == dir.absolutePath }

    private fun storageRoots(): List<File> {
        val home = homeDirectory()
        val roots = linkedSetOf(home.absolutePath)
        for (appDirectory in getExternalFilesDirs(null)) {
            var root = appDirectory
            repeat(4) { root = root?.parentFile }
            root?.takeIf { it.exists() && it.isDirectory }?.let { roots += it.absolutePath }
        }
        return roots.map(::File)
    }

    private fun storageRootLabel(root: File): String {
        return if (root.absolutePath == homeDirectory().absolutePath) "Phone storage" else "Removable storage (${root.name})"
    }

    private fun resolveStartDirectory(intent: Intent?): File {
        val explicit = intent?.getStringExtra(EXTRA_PATH)
        val incoming = shouldOpenTree(intent) || (intent?.action != null && intent.action != Intent.ACTION_MAIN)
        if (incoming || !explicit.isNullOrBlank()) {
            return FilesNavigationContract.startDirectory(explicit, homeDirectory())
        }
        return NavigationStateStore.last(themePrefs()) ?: homeDirectory()
    }

    private fun hasStorageAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        legacyStoragePermissions(Build.VERSION.SDK_INT).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showStorageProblem(dir: File) {
        if (hasStorageAccess()) {
            showOutput("Folder unavailable", "Cannot read ${dir.absolutePath}. Android may restrict this folder, or its storage may be disconnected.")
        } else {
            showActionMenu("Storage permission required", listOf(
                "Grant storage access" to { ensureStorageAccess(userInitiated = true) }
            ))
        }
    }

    private fun ensureStorageAccess(userInitiated: Boolean = false) {
        if (hasStorageAccess()) {
            if (userInitiated) showOutput("Storage access", "Storage access is granted. Android still restricts protected folders.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        val missing = legacyStoragePermissions(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val previouslyRequested = themePrefs().getBoolean("legacy_storage_requested", false)
        if (previouslyRequested && missing.any { !shouldShowRequestPermissionRationale(it) }) {
            if (userInitiated) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        themePrefs().edit().putBoolean("legacy_storage_requested", true).apply()
        requestPermissions(missing.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7) return
        if (hasStorageAccess()) {
            paneDialog?.dismiss()
            if (currentScreen == Screen.HOME) showHome(recordHistory = false)
            else activeCategory?.let { showCategoryFiles(it, recordHistory = false) } ?: reloadAll()
        } else {
            showStorageProblem(rightPane.directory)
        }
    }

    private fun applyCrtForeground(root: View) {
        root.foreground = if (crtFilter) {
            CrtOverlayDrawable(this, crtVignette).apply { setAccentColor(outputTextColor) }
        } else {
            null
        }
    }

    private fun applyThemeExtras(intent: Intent?) {
        val prefs = themePrefs()
        val shouldUseLauncherFontFallback = shouldUseLauncherFontFallback(intent)
        applyStoredTheme(prefs)
        crtVignette = CrtAppearance.resolveVignette(
            RetuiVisualContract.booleanOrNull(intent, RetuiVisualContract.CRT_VIGNETTE),
            null,
            localCrtVignette()
        )
        applyThemePayload(intent)
        if (shouldUseLauncherFontFallback && applyLauncherFontFallback()) {
            prefs.edit()
                .putString(RetuiVisualContract.FONT_PATH, appFontPath)
                .putString(RetuiVisualContract.FONT_NAME, appFontName)
                .apply()
        }
        if (RetuiVisualContract.hasVisualPayload(intent)) saveThemePayload(prefs, intent)
        applyStoredThemeColorOverride(prefs)
        applyLocalFontOverride(prefs)
        appTypeface = resolveTypeface()
        launcherPanelColor = modulePanelColor
        launcherPanelBorderColor = moduleBorderColor
        launcherHeaderColor = headerPanelColor
        launcherHeaderTextColor = headerTextColor
        launcherHeaderTextSizeSp = headerTextSizeSp
        launcherFrameRuntime?.textColor?.takeUnless {
            prefs.getBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)
        }?.let { color ->
            textColor = color
            inputTextColor = color
            outputTextColor = color
            fileTextColor = color
            directoryTextColor = color
            selectionTextColor = color
            moduleTextColor = color
            headerTextColor = color
            moduleButtonTextColor = color
        }
        applyCommanderPalette()
    }

    private fun applyCommanderPalette() {
        outputTextSizeSp = 11
        headerTextSizeSp = 11
        inputFontSizeSp = 11
        commanderSkin = null
        usingLauncherPalette = false
        if (themePrefs().getBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)) return
        val choice = themePrefs().getString(PREF_COMMANDER_SKIN, null)
        if (choice == null && launcherThemeAvailable()) {
            // Default: the Launcher's own colours, adjusted only where they would hurt readability.
            usingLauncherPalette = true
            applyReadabilityPass()
            return
        }
        commanderSkin = commanderSkins.firstOrNull { it.id == choice }
        commanderSkin?.let { skin ->
            val core = skin.style("core").displayed
            val menu = skin.style("menu", "menuinactive").displayed
            val button = skin.style("buttonbar", "button").displayed
            val input = skin.style("core", "input").displayed
            val selection = skin.style("core", "selected").displayed
            outputPanelColor = core.background
            outputTextColor = core.foreground
            fileTextColor = core.foreground
            directoryTextColor = skin.style("filehighlight", "directory").displayed.foreground
            outputBorderColor = skin.style("core", "frame").displayed.foreground
            headerPanelColor = menu.background
            headerTextColor = menu.foreground
            modulePanelColor = core.background
            moduleTextColor = skin.style("core", "header").displayed.foreground
            moduleButtonBgColor = button.background
            moduleButtonTextColor = button.foreground
            moduleButtonBorderColor = outputBorderColor
            inputBgColor = input.background
            inputTextColor = input.foreground
            selectionBgColor = selection.background
            selectionTextColor = selection.foreground
            return
        }
        outputPanelColor = Color.rgb(55, 55, 55)
        outputTextColor = Color.rgb(210, 210, 210)
        fileTextColor = outputTextColor
        directoryTextColor = Color.rgb(200, 196, 151)
        outputBorderColor = Color.rgb(170, 170, 170)
        headerPanelColor = Color.rgb(72, 72, 72)
        headerTextColor = directoryTextColor
        modulePanelColor = headerPanelColor
        moduleTextColor = directoryTextColor
        moduleButtonBgColor = headerPanelColor
        moduleButtonTextColor = outputTextColor
        moduleButtonBorderColor = outputBorderColor
        inputTextColor = outputTextColor
        selectionBgColor = Color.rgb(0, 96, 96)
        selectionTextColor = Color.WHITE
        applyReadabilityPass()
    }

    private fun launcherThemeAvailable(): Boolean {
        val prefs = themePrefs()
        return prefs.getBoolean(PREF_LAUNCHER_THEME_AVAILABLE, false) || prefs.contains(RetuiVisualContract.BG) ||
            prefs.contains(RetuiVisualContract.OUTPUT_BG)
    }

    /**
     * Launcher palettes often use several near-identical dark surfaces and one text colour. Surfaces are
     * separated first so buttons, headers and the selection stay visible, then every text colour is checked
     * against the surface it is actually drawn on. Saved Launcher values are never rewritten.
     */
    private fun applyReadabilityPass() {
        val base = outputPanelColor
        moduleButtonBgColor = Readability.separate(moduleButtonBgColor, base, 1.35)
        headerPanelColor = Readability.separate(headerPanelColor, base, 1.25)
        selectionBgColor = Readability.separate(selectionBgColor, base, 1.6)
        outputBorderColor = Readability.ensureContrast(outputBorderColor, base, Readability.NON_TEXT)
        outputTextColor = Readability.ensureContrast(outputTextColor, base, Readability.BODY_TEXT)
        fileTextColor = Readability.ensureContrast(fileTextColor, base, Readability.BODY_TEXT)
        directoryTextColor = Readability.ensureContrast(directoryTextColor, base, Readability.TEXT)
        moduleTextColor = Readability.ensureContrast(moduleTextColor, base, Readability.TEXT)
        inputTextColor = Readability.ensureContrast(inputTextColor, base, Readability.TEXT)
        // Header text appears on header bars and as dialog titles on the output panel.
        headerTextColor = Readability.ensureContrast(Readability.ensureContrast(headerTextColor, headerPanelColor, Readability.TEXT), base, Readability.TEXT)
        moduleButtonTextColor = Readability.ensureContrast(moduleButtonTextColor, moduleButtonBgColor, Readability.TEXT)
        selectionTextColor = Readability.ensureContrast(selectionTextColor, selectionBgColor, Readability.TEXT)
        launcherHeaderTextColor = Readability.ensureContrast(launcherHeaderTextColor, launcherHeaderColor, Readability.TEXT)
    }

    private fun themeColorBindings(): List<ThemeColorBinding> = listOf(
        ThemeColorBinding("App background", RetuiVisualContract.BG, { bgColor }) { bgColor = it },
        ThemeColorBinding("Window background", RetuiVisualContract.TERMINAL_BG, { panelColor }) { panelColor = it },
        ThemeColorBinding("Shared text", RetuiVisualContract.TEXT, { textColor }) {
            textColor = it
            fileTextColor = it
        },
        ThemeColorBinding("Shared border", RetuiVisualContract.BORDER, { borderColor }) { borderColor = it },
        ThemeColorBinding("Panel background", RetuiVisualContract.PANEL_BG, { modulePanelColor }) { modulePanelColor = it },
        ThemeColorBinding("Panel text", RetuiVisualContract.PANEL_TEXT, { moduleTextColor }) { moduleTextColor = it },
        ThemeColorBinding("Panel border", RetuiVisualContract.PANEL_BORDER, { moduleBorderColor }) { moduleBorderColor = it },
        ThemeColorBinding("Header background", RetuiVisualContract.HEADER_BG, { headerPanelColor }) { headerPanelColor = it },
        ThemeColorBinding("Header text", RetuiVisualContract.HEADER_TEXT, { headerTextColor }) { headerTextColor = it },
        ThemeColorBinding("Button background", RetuiVisualContract.BUTTON_BG, { moduleButtonBgColor }) { moduleButtonBgColor = it },
        ThemeColorBinding("Button text", RetuiVisualContract.BUTTON_TEXT, { moduleButtonTextColor }) { moduleButtonTextColor = it },
        ThemeColorBinding("Button border", RetuiVisualContract.BUTTON_BORDER, { moduleButtonBorderColor }) { moduleButtonBorderColor = it },
        ThemeColorBinding("Input background", RetuiVisualContract.INPUT_BG, { inputBgColor }) { inputBgColor = it },
        ThemeColorBinding("Input text", RetuiVisualContract.INPUT_TEXT, { inputTextColor }) { inputTextColor = it },
        ThemeColorBinding("Output background", RetuiVisualContract.OUTPUT_BG, { outputPanelColor }) { outputPanelColor = it },
        ThemeColorBinding("Output text", RetuiVisualContract.OUTPUT_TEXT, { outputTextColor }) { outputTextColor = it },
        ThemeColorBinding("Output border", RetuiVisualContract.OUTPUT_BORDER, { outputBorderColor }) { outputBorderColor = it },
        ThemeColorBinding("Directory text", RetuiVisualContract.DIRECTORY_TEXT, { directoryTextColor }) { directoryTextColor = it },
        ThemeColorBinding("Selection background", RetuiVisualContract.SELECTION_BG, { selectionBgColor }) { selectionBgColor = it },
        ThemeColorBinding("Selection text", RetuiVisualContract.SELECTION_TEXT, { selectionTextColor }) { selectionTextColor = it }
    )

    private fun applyStoredThemeColorOverride(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)
        themeColorBindings().forEach { binding ->
            val key = PREF_THEME_COLOR_PREFIX + binding.key
            val stored = if (prefs.contains(key)) prefs.getInt(key, binding.get()) else null
            binding.set(resolveThemeColor(binding.get(), enabled, stored))
        }
    }

    private fun saveThemeColorOverride() {
        val editor = themePrefs().edit().putBoolean(PREF_THEME_COLORS_OVERRIDDEN, true)
        themeColorBindings().forEach { binding ->
            editor.putInt(PREF_THEME_COLOR_PREFIX + binding.key, binding.get())
        }
        editor.apply()
    }

    private fun clearThemeColorOverride() {
        val editor = themePrefs().edit().putBoolean(PREF_THEME_COLORS_OVERRIDDEN, false)
        themeColorBindings().forEach { binding -> editor.remove(PREF_THEME_COLOR_PREFIX + binding.key) }
        editor.apply()
        recreate()
    }

    private fun putThemeExtras(intent: Intent) {
        intent.putExtra(RetuiVisualContract.BG, bgColor)
        intent.putExtra(RetuiVisualContract.TERMINAL_BG, panelColor)
        intent.putExtra(RetuiVisualContract.TEXT, textColor)
        intent.putExtra(RetuiVisualContract.BORDER, borderColor)
        intent.putExtra(RetuiVisualContract.PANEL_BG, modulePanelColor)
        intent.putExtra(RetuiVisualContract.PANEL_TEXT, moduleTextColor)
        intent.putExtra(RetuiVisualContract.PANEL_BORDER, moduleBorderColor)
        intent.putExtra(RetuiVisualContract.HEADER_BG, headerPanelColor)
        intent.putExtra(RetuiVisualContract.HEADER_TEXT, headerTextColor)
        intent.putExtra(RetuiVisualContract.BUTTON_BG, moduleButtonBgColor)
        intent.putExtra(RetuiVisualContract.BUTTON_TEXT, moduleButtonTextColor)
        intent.putExtra(RetuiVisualContract.BUTTON_BORDER, moduleButtonBorderColor)
        intent.putExtra(RetuiVisualContract.INPUT_BG, inputBgColor)
        intent.putExtra(RetuiVisualContract.INPUT_TEXT, inputTextColor)
        intent.putExtra(RetuiVisualContract.OUTPUT_BG, outputPanelColor)
        intent.putExtra(RetuiVisualContract.OUTPUT_TEXT, outputTextColor)
        intent.putExtra(RetuiVisualContract.OUTPUT_BORDER, outputBorderColor)
        intent.putExtra(RetuiVisualContract.DIRECTORY_TEXT, directoryTextColor)
        intent.putExtra(RetuiVisualContract.SELECTION_BG, selectionBgColor)
        intent.putExtra(RetuiVisualContract.SELECTION_TEXT, selectionTextColor)
        // file list text maps to TEXT when not overridden; keep distinct via TEXT already set
        intent.putExtra(RetuiVisualContract.HEADER_TEXT_SIZE, headerTextSizeSp)
        intent.putExtra(RetuiVisualContract.BODY_TEXT_SIZE, outputTextSizeSp)
        intent.putExtra(RetuiVisualContract.INPUT_FONT_SIZE, inputFontSizeSp)
        intent.putExtra(EXTRA_FONT_SCALE_OFFSET, fontScaleOffsetSp)
        intent.putExtra(RetuiVisualContract.MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        intent.putExtra(RetuiVisualContract.OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        intent.putExtra(RetuiVisualContract.HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        intent.putExtra(RetuiVisualContract.TOP_MARGIN, topMarginDp)
        intent.putExtra(RetuiVisualContract.TERMINAL_BG_IMAGE, terminalBackgroundImage)
        intent.putExtra(RetuiVisualContract.CYBERDECK_MODE, cyberdeckMode)
        intent.putExtra(RetuiVisualContract.CRT_FILTER, crtFilter)
        intent.putExtra(RetuiVisualContract.CRT_VIGNETTE, crtVignette)
        appFontPath?.let { intent.putExtra(RetuiVisualContract.FONT_PATH, it) }
        appFontName?.let { intent.putExtra(RetuiVisualContract.FONT_NAME, it) }
    }

    private fun applyStoredTheme(prefs: SharedPreferences) {
        bgColor = prefsInt(prefs, RetuiVisualContract.BG, bgColor, "theme_bg")
        panelColor = prefsInt(prefs, RetuiVisualContract.TERMINAL_BG, panelColor, "terminal_bg")
        textColor = prefsInt(prefs, RetuiVisualContract.TEXT, textColor, "theme_text")
        borderColor = prefsInt(prefs, RetuiVisualContract.BORDER, borderColor, "theme_border")
        modulePanelColor = prefsInt(prefs, RetuiVisualContract.PANEL_BG, modulePanelColor, "module_bg_color")
        moduleTextColor = prefsInt(prefs, RetuiVisualContract.PANEL_TEXT, moduleTextColor, "module_text_color")
        moduleBorderColor = prefsInt(prefs, RetuiVisualContract.PANEL_BORDER, moduleBorderColor, "module_border_color")
        headerPanelColor = prefsInt(prefs, RetuiVisualContract.HEADER_BG, headerPanelColor, "module_header_bg_color", "fm_header_background_color")
        headerTextColor = prefsInt(prefs, RetuiVisualContract.HEADER_TEXT, headerTextColor, "module_header_text_color", "fm_header_text_color")
        moduleButtonBgColor = prefsInt(prefs, RetuiVisualContract.BUTTON_BG, moduleButtonBgColor, "module_button_bg_color", "fm_button_background_color")
        moduleButtonTextColor = prefsInt(prefs, RetuiVisualContract.BUTTON_TEXT, moduleButtonTextColor, "module_button_text_color", "fm_button_text_color")
        moduleButtonBorderColor = prefsInt(prefs, RetuiVisualContract.BUTTON_BORDER, moduleButtonBorderColor, "module_button_border_color", "fm_button_border_color")
        inputBgColor = prefsInt(prefs, RetuiVisualContract.INPUT_BG, inputBgColor, "input_bg_color")
        inputTextColor = prefsInt(prefs, RetuiVisualContract.INPUT_TEXT, inputTextColor, "input_text_color")
        outputPanelColor = prefsInt(prefs, RetuiVisualContract.OUTPUT_BG, outputPanelColor, "output_bg_color", "fm_panel_background_color")
        outputTextColor = prefsInt(prefs, RetuiVisualContract.OUTPUT_TEXT, outputTextColor, "output_text_color")
        outputBorderColor = prefsInt(prefs, RetuiVisualContract.OUTPUT_BORDER, outputBorderColor, "output_border_color", "fm_border_color")
        fileTextColor = prefsInt(prefs, RetuiVisualContract.TEXT, fileTextColor, "fm_text_color", "theme_text")
        directoryTextColor = prefsInt(prefs, RetuiVisualContract.DIRECTORY_TEXT, directoryTextColor, "fm_directory_text_color")
        selectionBgColor = prefsInt(prefs, RetuiVisualContract.SELECTION_BG, selectionBgColor, "fm_selection_background_color")
        selectionTextColor = prefsInt(prefs, RetuiVisualContract.SELECTION_TEXT, selectionTextColor, "fm_selection_text_color")
        headerTextSizeSp = prefsInt(prefs, RetuiVisualContract.HEADER_TEXT_SIZE, headerTextSizeSp, "header_text_size", "module_header_text_size")
        outputTextSizeSp = prefsInt(prefs, RetuiVisualContract.BODY_TEXT_SIZE, outputTextSizeSp, "output_text_size", "module_body_text_size")
        inputFontSizeSp = prefsInt(prefs, RetuiVisualContract.INPUT_FONT_SIZE, inputFontSizeSp, "input_font_size")
        fontScaleOffsetSp = prefs.getInt(PREF_FONT_SCALE_OFFSET, 0).coerceIn(-3, 4)
        moduleCornerRadiusDp = prefsInt(prefs, RetuiVisualContract.MODULE_CORNER_RADIUS, moduleCornerRadiusDp, "module_corner_radius")
        outputCornerRadiusDp = prefsInt(prefs, RetuiVisualContract.OUTPUT_CORNER_RADIUS, outputCornerRadiusDp, "output_corner_radius")
        headerCornerRadiusDp = prefsInt(prefs, RetuiVisualContract.HEADER_CORNER_RADIUS, headerCornerRadiusDp, "header_corner_radius")
        topMarginDp = prefsInt(prefs, RetuiVisualContract.TOP_MARGIN, topMarginDp, "top_margin")
        terminalBackgroundImage = prefs.getString(RetuiVisualContract.TERMINAL_BG_IMAGE, null)
            ?: prefs.getString("terminal_bg_image", terminalBackgroundImage)
        cyberdeckMode = prefsBool(prefs, RetuiVisualContract.CYBERDECK_MODE, cyberdeckMode, "cyberdeck_mode", "enable_cyberdeck_mode")
        dashedBorders = prefsBool(prefs, RetuiVisualContract.DASHED_BORDERS, dashedBorders, "enable_dashed_border")
        dashLengthDp = prefsInt(prefs, RetuiVisualContract.DASHED_BORDER_DASH_LENGTH, dashLengthDp)
        dashGapDp = prefsInt(prefs, RetuiVisualContract.DASHED_BORDER_GAP_LENGTH, dashGapDp)
        borderStrokeDp = prefs.getFloat(RetuiVisualContract.DASHED_BORDER_STROKE_WIDTH_DP, borderStrokeDp)
        crtFilter = prefsBool(prefs, RetuiVisualContract.CRT_FILTER, crtFilter, "crt_filter", "enable_crt_filter")
        appFontPath = prefs.getString(RetuiVisualContract.FONT_PATH, null) ?: prefs.getString("font_path", appFontPath)
        appFontName = prefs.getString(RetuiVisualContract.FONT_NAME, null) ?: prefs.getString("font_name", appFontName)
        applyDisplayMarginsString(
            prefs.getString(RetuiVisualContract.DISPLAY_MARGIN_TOP, null)
                ?: prefs.getString("display_margin_top_section", null)
                ?: prefs.getString("display_margin_mm", null)
        )
    }

    private fun prefsInt(prefs: SharedPreferences, key: String, fallback: Int, vararg legacy: String): Int {
        if (prefs.contains(key)) return prefs.getInt(key, fallback)
        for (candidate in legacy) {
            if (prefs.contains(candidate)) return prefs.getInt(candidate, fallback)
        }
        return fallback
    }

    private fun prefsBool(prefs: SharedPreferences, key: String, fallback: Boolean, vararg legacy: String): Boolean {
        if (prefs.contains(key)) return prefs.getBoolean(key, fallback)
        for (candidate in legacy) {
            if (prefs.contains(candidate)) return prefs.getBoolean(candidate, fallback)
        }
        return fallback
    }

    private fun applyThemePayload(intent: Intent?) {
        bgColor = RetuiVisualContract.color(intent, bgColor, RetuiVisualContract.BG)
        panelColor = RetuiVisualContract.color(intent, panelColor, RetuiVisualContract.TERMINAL_BG)
        textColor = RetuiVisualContract.color(intent, textColor, RetuiVisualContract.TEXT)
        borderColor = RetuiVisualContract.color(intent, borderColor, RetuiVisualContract.BORDER)
        modulePanelColor = RetuiVisualContract.color(intent, modulePanelColor, RetuiVisualContract.PANEL_BG)
        moduleTextColor = RetuiVisualContract.color(intent, moduleTextColor, RetuiVisualContract.PANEL_TEXT)
        moduleBorderColor = RetuiVisualContract.color(intent, moduleBorderColor, RetuiVisualContract.PANEL_BORDER)
        headerPanelColor = RetuiVisualContract.color(intent, headerPanelColor, RetuiVisualContract.HEADER_BG)
        headerTextColor = RetuiVisualContract.color(intent, headerTextColor, RetuiVisualContract.HEADER_TEXT)
        moduleButtonBgColor = RetuiVisualContract.color(intent, moduleButtonBgColor, RetuiVisualContract.BUTTON_BG)
        moduleButtonTextColor = RetuiVisualContract.color(intent, moduleButtonTextColor, RetuiVisualContract.BUTTON_TEXT)
        moduleButtonBorderColor = RetuiVisualContract.color(intent, moduleButtonBorderColor, RetuiVisualContract.BUTTON_BORDER)
        inputBgColor = RetuiVisualContract.color(intent, inputBgColor, RetuiVisualContract.INPUT_BG)
        inputTextColor = RetuiVisualContract.color(intent, inputTextColor, RetuiVisualContract.INPUT_TEXT)
        outputPanelColor = RetuiVisualContract.color(intent, outputPanelColor, RetuiVisualContract.OUTPUT_BG)
        outputTextColor = RetuiVisualContract.color(intent, outputTextColor, RetuiVisualContract.OUTPUT_TEXT)
        outputBorderColor = RetuiVisualContract.color(intent, outputBorderColor, RetuiVisualContract.OUTPUT_BORDER)
        fileTextColor = RetuiVisualContract.color(intent, fileTextColor, RetuiVisualContract.TEXT)
        directoryTextColor = RetuiVisualContract.color(intent, directoryTextColor, RetuiVisualContract.DIRECTORY_TEXT)
        selectionBgColor = RetuiVisualContract.color(intent, selectionBgColor, RetuiVisualContract.SELECTION_BG)
        selectionTextColor = RetuiVisualContract.color(intent, selectionTextColor, RetuiVisualContract.SELECTION_TEXT)
        headerTextSizeSp = RetuiVisualContract.int(intent, headerTextSizeSp, RetuiVisualContract.HEADER_TEXT_SIZE)
        outputTextSizeSp = RetuiVisualContract.int(intent, outputTextSizeSp, RetuiVisualContract.BODY_TEXT_SIZE)
        inputFontSizeSp = RetuiVisualContract.int(intent, inputFontSizeSp, RetuiVisualContract.INPUT_FONT_SIZE)
        moduleCornerRadiusDp = RetuiVisualContract.int(intent, moduleCornerRadiusDp, RetuiVisualContract.MODULE_CORNER_RADIUS)
        outputCornerRadiusDp = RetuiVisualContract.int(intent, outputCornerRadiusDp, RetuiVisualContract.OUTPUT_CORNER_RADIUS)
        headerCornerRadiusDp = RetuiVisualContract.int(intent, headerCornerRadiusDp, RetuiVisualContract.HEADER_CORNER_RADIUS)
        topMarginDp = RetuiVisualContract.int(intent, topMarginDp, RetuiVisualContract.TOP_MARGIN)
        applyLauncherDisplayMargins(intent)
        cyberdeckMode = RetuiVisualContract.boolean(intent, cyberdeckMode, RetuiVisualContract.CYBERDECK_MODE)
        dashedBorders = RetuiVisualContract.boolean(intent, dashedBorders, RetuiVisualContract.DASHED_BORDERS)
        dashLengthDp = RetuiVisualContract.int(intent, dashLengthDp, RetuiVisualContract.DASHED_BORDER_DASH_LENGTH)
        dashGapDp = RetuiVisualContract.int(intent, dashGapDp, RetuiVisualContract.DASHED_BORDER_GAP_LENGTH)
        borderStrokeDp = RetuiVisualContract.float(intent, borderStrokeDp, RetuiVisualContract.DASHED_BORDER_STROKE_WIDTH_DP)
        crtFilter = RetuiVisualContract.boolean(intent, crtFilter, RetuiVisualContract.CRT_FILTER)
        RetuiVisualContract.string(intent, RetuiVisualContract.TERMINAL_BG_IMAGE)?.let { terminalBackgroundImage = it }
        var appliedFont = false
        val fontFileExtra = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_FILE)
        RetuiVisualContract.string(intent, RetuiVisualContract.FONT_PATH)?.let {
            val resolved = fontPathFromPayload(it, fontFileExtra)
            if (!resolved.isNullOrBlank()) {
                appFontPath = resolved
                appFontName = null
                appliedFont = true
            }
        }
        if (!appliedFont) {
            fontFileExtra
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveLauncherFontFile(it) }
                ?.let {
                    appFontPath = importFontPath(it.absolutePath) ?: it.absolutePath
                    appFontName = null
                    appliedFont = true
                }
        }
        if (!appliedFont) RetuiVisualContract.string(intent, RetuiVisualContract.FONT_NAME)?.let {
            appFontName = it
            appFontPath = null
        }
    }

    private fun saveThemePayload(prefs: SharedPreferences, intent: Intent?) {
        val editor = prefs.edit()
        editor.putInt(RetuiVisualContract.BG, bgColor)
        editor.putInt(RetuiVisualContract.TERMINAL_BG, panelColor)
        editor.putInt(RetuiVisualContract.TEXT, textColor)
        editor.putInt(RetuiVisualContract.BORDER, borderColor)
        editor.putInt(RetuiVisualContract.PANEL_BG, modulePanelColor)
        editor.putInt(RetuiVisualContract.PANEL_TEXT, moduleTextColor)
        editor.putInt(RetuiVisualContract.PANEL_BORDER, moduleBorderColor)
        editor.putInt(RetuiVisualContract.HEADER_BG, headerPanelColor)
        editor.putInt(RetuiVisualContract.HEADER_TEXT, headerTextColor)
        editor.putInt(RetuiVisualContract.BUTTON_BG, moduleButtonBgColor)
        editor.putInt(RetuiVisualContract.BUTTON_TEXT, moduleButtonTextColor)
        editor.putInt(RetuiVisualContract.BUTTON_BORDER, moduleButtonBorderColor)
        editor.putInt(RetuiVisualContract.INPUT_BG, inputBgColor)
        editor.putInt(RetuiVisualContract.INPUT_TEXT, inputTextColor)
        editor.putInt(RetuiVisualContract.OUTPUT_BG, outputPanelColor)
        editor.putInt(RetuiVisualContract.OUTPUT_TEXT, outputTextColor)
        editor.putInt(RetuiVisualContract.OUTPUT_BORDER, outputBorderColor)
        editor.putInt(RetuiVisualContract.DIRECTORY_TEXT, directoryTextColor)
        editor.putInt(RetuiVisualContract.SELECTION_BG, selectionBgColor)
        editor.putInt(RetuiVisualContract.SELECTION_TEXT, selectionTextColor)
        editor.putInt(RetuiVisualContract.HEADER_TEXT_SIZE, headerTextSizeSp)
        editor.putInt(RetuiVisualContract.BODY_TEXT_SIZE, outputTextSizeSp)
        editor.putInt(RetuiVisualContract.INPUT_FONT_SIZE, inputFontSizeSp)
        editor.putInt(RetuiVisualContract.MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        editor.putInt(RetuiVisualContract.OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        editor.putInt(RetuiVisualContract.HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        editor.putInt(RetuiVisualContract.TOP_MARGIN, topMarginDp)
        editor.putBoolean(RetuiVisualContract.CYBERDECK_MODE, cyberdeckMode)
        editor.putBoolean(RetuiVisualContract.DASHED_BORDERS, dashedBorders)
        editor.putInt(RetuiVisualContract.DASHED_BORDER_DASH_LENGTH, dashLengthDp)
        editor.putInt(RetuiVisualContract.DASHED_BORDER_GAP_LENGTH, dashGapDp)
        editor.putFloat(RetuiVisualContract.DASHED_BORDER_STROKE_WIDTH_DP, borderStrokeDp)
        editor.putBoolean(RetuiVisualContract.CRT_FILTER, crtFilter)
        editor.putString(RetuiVisualContract.TERMINAL_BG_IMAGE, terminalBackgroundImage)
        editor.putString(RetuiVisualContract.FONT_PATH, appFontPath)
        editor.putString(RetuiVisualContract.FONT_NAME, appFontName)
        editor.putBoolean(PREF_LAUNCHER_THEME_AVAILABLE, true)
        displayMarginsString(intent)?.let { editor.putString(RetuiVisualContract.DISPLAY_MARGIN_TOP, it) }
        editor.apply()
    }

    private fun shouldUseLauncherFontFallback(intent: Intent?): Boolean {
        if (intent?.extras == null) return true
        val path = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_PATH)
        val fontFile = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_FILE)
        val name = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_NAME)
        if (!path.isNullOrEmpty() && fontPathUsableForPayload(path, fontFile)) return false
        if (!fontFile.isNullOrEmpty() && resolveLauncherFontFile(fontFile) != null) return false
        if (!path.isNullOrEmpty()) return true
        if (name.isNullOrEmpty()) return true
        return name.equals("lucida_console", true)
    }

    private fun stringExtra(intent: Intent?, key: String, vararg aliases: String): String? {
        val extras = intent?.extras ?: return null
        val keys = arrayOf(key, *aliases)
        for (candidate in keys) {
            val value = extras.get(candidate)?.toString()?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun themePrefs(): SharedPreferences {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private fun applyLauncherDisplayMargins(intent: Intent?) {
        applyDisplayMarginsString(displayMarginsString(intent))
    }

    private fun displayMarginsString(intent: Intent?): String? {
        return RetuiVisualContract.string(intent, RetuiVisualContract.DISPLAY_MARGIN_TOP)
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
        return px.roundToInt()
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
        val font = resolveLauncherFontFile(fontName, config.parentFile)
            ?: return false
        appFontPath = importFontPath(font.absolutePath) ?: font.absolutePath
        appFontName = null
        return true
    }

    private fun resolveLauncherFontFile(name: String, preferredRoot: File? = null): File? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null

        val direct = File(cleanName)
        if (direct.isAbsolute && fontFileUsable(direct)) return direct

        val roots = ArrayList<File>()
        preferredRoot?.let { roots.add(it) }
        launcherUiFiles().mapNotNull { it.parentFile }.forEach { root ->
            if (roots.none { it.absolutePath == root.absolutePath }) roots.add(root)
        }

        for (root in roots) {
            val candidates = arrayOf(File(root, cleanName), File(File(root, "fonts"), cleanName))
            for (candidate in candidates) {
                if (fontFileUsable(candidate)) return candidate
            }
        }
        return null
    }

    private fun fontPathFromPayload(path: String, fontFile: String?): String? {
        if (!fontPathUsableForPayload(path, fontFile)) return null
        return importFontPath(path) ?: path
    }

    private fun fontPathUsableForPayload(path: String, fontFile: String?): Boolean {
        if (!fontPathMatchesFile(path, fontFile)) return false
        return fontPathUsable(path)
    }

    private fun fontPathMatchesFile(path: String, fontFile: String?): Boolean {
        val cleanFontFile = fontFile?.trim().orEmpty()
        if (cleanFontFile.isEmpty()) return true
        return File(path).name.equals(File(cleanFontFile).name, ignoreCase = true)
    }

    private fun importFontPath(path: String): String? {
        return try {
            val source = File(path)
            if (!fontFileUsable(source)) return null
            val fontDir = File(filesDir, "fonts")
            if (!fontDir.exists() && !fontDir.mkdirs()) return null
            val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "retui-font" }
            val target = File(fontDir, safeName)
            if (source.absolutePath != target.absolutePath) {
                source.copyTo(target, overwrite = true)
                target.setLastModified(source.lastModified())
            }
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun fontPathUsable(path: String): Boolean {
        return fontFileUsable(File(path))
    }

    private fun fontFileUsable(file: File): Boolean {
        return try {
            file.exists() && file.isFile && file.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun launcherUiFiles(): List<File> {
        val sharedRoot = Environment.getExternalStorageDirectory()
        return listOf(
            File(sharedRoot, "Re-T-UI/ui.xml"),
            File(sharedRoot, "Android/data/com.dvil.tui_renewed/files/Re-T-UI/ui.xml")
        )
    }

    private fun localCrtVignette(): Boolean? {
        val config = launcherUiFiles().firstOrNull { it.exists() && it.isFile } ?: return null
        val xml = try {
            config.readText()
        } catch (_: Exception) {
            return null
        }
        val value = xmlValue(xml, RetuiVisualContract.CRT_VIGNETTE)
            ?: xmlValue(xml, "enable_crt_vignette")
            ?: xmlValue(xml, "crt_vignette")
            ?: return null
        return value.equals("true", true) || value == "1"
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

    private fun applyLocalFontOverride(prefs: SharedPreferences) {
        when (prefs.getString(PREF_FONT_MODE, null)) {
            FONT_MODE_CUSTOM -> {
                appFontPath = prefs.getString(PREF_CUSTOM_FONT_PATH, null)?.takeIf(::fontPathUsable)
                appFontName = null
            }
            FONT_MODE_SYSTEM -> {
                appFontPath = null
                appFontName = "system"
            }
            null, FONT_MODE_DEFAULT -> {
                appFontPath = null
                appFontName = null
            }
        }
    }

    private fun workspaceFrameDrawable(): Drawable {
        val framed = launcherFrameRuntime?.active == true
        val fill = if (framed) Color.TRANSPARENT else launcherPanelColor
        val fallback = if (cyberdeckMode && !framed) {
            CyberPanelDrawable(fill, launcherPanelBorderColor, max(1f, dpFloat(1.5f)), true)
        } else GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(outputCornerRadiusDp).toFloat()
            val stroke = if (framed) 0 else max(1, dpFloat(borderStrokeDp.takeIf { it.isFinite() }?.coerceIn(.5f, 8f) ?: 1.5f).roundToInt())
            if (dashedBorders && dashLengthDp > 0 && dashGapDp > 0) {
                setStroke(stroke, launcherPanelBorderColor, dp(dashLengthDp.coerceAtMost(128)).toFloat(), dp(dashGapDp.coerceAtMost(128)).toFloat())
            } else setStroke(stroke, launcherPanelBorderColor)
        }
        return launcherFrameRuntime?.drawable(fallback) ?: fallback
    }

    private fun panelDrawable(role: PanelRole, interactive: Boolean = false): Drawable {
        val fill = when (role) {
            PanelRole.OUTER -> FmVisualInterop.scaleColorAlpha(outputPanelColor, 226)
            PanelRole.HEADER -> headerPanelColor
            PanelRole.INPUT -> inputBgColor
            PanelRole.MODULE -> modulePanelColor
            PanelRole.OUTPUT -> FmVisualInterop.scaleColorAlpha(outputPanelColor, 238)
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
        val fallback = if (cyberdeckMode) {
            CyberPanelDrawable(fill, stroke, max(1f, dpFloat(if (role == PanelRole.MODULE || role == PanelRole.INPUT) 1f else 1.25f)), role != PanelRole.MODULE && role != PanelRole.INPUT)
        } else {
            GradientDrawable().apply {
                setColor(fill)
                setStroke(max(1, dp(1)), stroke)
                cornerRadius = dp(radius).toFloat()
            }
        }
        if (role == PanelRole.OUTER) return fallback
        return launcherFrameRuntime?.drawable(fallback, interactive) ?: fallback
    }

    private fun buttonDrawable(): Drawable {
        val fallback = if (cyberdeckMode) {
            CyberPanelDrawable(FmVisualInterop.scaleColorAlpha(moduleButtonBgColor, 205), moduleButtonBorderColor, max(1f, dpFloat(1f)), false)
        } else {
            GradientDrawable().apply {
                setColor(FmVisualInterop.scaleColorAlpha(moduleButtonBgColor, 205))
                setStroke(max(1, dp(1)), moduleButtonBorderColor)
                cornerRadius = dp(moduleCornerRadiusDp).toFloat()
            }
        }
        return launcherFrameRuntime?.drawable(fallback, interactive = true) ?: fallback
    }

    private fun toolbarDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(FmVisualInterop.scaleColorAlpha(moduleButtonBgColor, 210))
        drawable.setStroke(max(1, dp(1)), moduleButtonBorderColor)
        drawable.cornerRadius = 0f
        return launcherFrameRuntime?.drawable(drawable) ?: drawable
    }

    private fun addressDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(FmVisualInterop.scaleColorAlpha(outputPanelColor, 230))
        drawable.setStroke(max(1, dp(1)), outputBorderColor)
        drawable.cornerRadius = 0f
        return launcherFrameRuntime?.drawable(drawable, interactive = true) ?: drawable
    }

    private fun toolbarButtonDrawable(): Drawable {
        val drawable = GradientDrawable()
        drawable.setColor(FmVisualInterop.scaleColorAlpha(outputPanelColor, 150))
        drawable.setStroke(max(1, dp(1)), moduleButtonBorderColor)
        drawable.cornerRadius = 0f
        return launcherFrameRuntime?.drawable(drawable, interactive = true) ?: drawable
    }

    private fun functionButtonBackground(): Drawable {
        val fallback = ColorDrawable(FmVisualInterop.scaleColorAlpha(moduleButtonBgColor, 210))
        return launcherFrameRuntime?.drawable(fallback, interactive = true) ?: fallback
    }

    private fun rowSelectionBackground(selected: Boolean): Drawable {
        if (!selected) return ColorDrawable(Color.TRANSPARENT)
        val fallback = ColorDrawable(FmVisualInterop.scaleColorAlpha(selectionBgColor, 230))
        return launcherFrameRuntime?.drawable(fallback) ?: fallback
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
        const val EXTRA_ACTION = "action"
        const val EXTRA_TARGET = "target"
        const val EXTRA_SEARCH = "search"
        const val EXTRA_SEARCH_NAME = "search_name"
        const val EXTRA_SEARCH_TYPE = "search_type"
        const val EXTRA_CREATE = "create"
        const val EXTRA_FONT_SCALE_OFFSET = "fm_font_scale_offset"

        const val ACTION_SEARCH = "search"
        const val ACTION_OPEN = "open"
        const val ACTION_LIST = "ls"
        const val ACTION_SHARE = "share"
        private const val PREFS_NAME = "retui_fm"
        private const val PREF_CUSTOM_PLACES = "custom_places"
        private const val PREF_TRASH_DIRS = "trash_dirs"
        private const val PREF_TRASH_RETENTION_DAYS = "trash_retention_days"
        private const val PREF_TRASH_RETENTION_INITIALIZED = "trash_retention_initialized"
        private const val PREF_LAST_TRASH_CLEANUP = "last_trash_cleanup"
        private const val PREF_WALKTHROUGH_COMPLETE = "walkthrough_complete"
        private const val PREF_RIGHT_SORT_MODE = "right_sort_mode"
        private const val PREF_SEARCH_HISTORY = "search_history"
        private const val PREF_FONT_SCALE_OFFSET = "font_scale_offset"
        private const val PREF_FONT_MODE = "font_override_mode"
        private const val FONT_MODE_LAUNCHER = "launcher"
        private const val PREF_CUSTOM_FONT_PATH = "custom_font_path"
        private const val PREF_THEME_COLORS_OVERRIDDEN = "theme.colorsOverridden"
        private const val PREF_COMMANDER_SKIN = "theme.commanderSkin"
        private const val RETUI_GRAY_CHOICE = "retui-gray"
        private const val PREF_THEME_COLOR_PREFIX = "theme.override."
        private const val PREF_LAUNCHER_THEME_AVAILABLE = "theme.launcher.available"
        private const val FONT_MODE_CUSTOM = "custom"
        private const val FONT_MODE_SYSTEM = "system"
        private const val FONT_MODE_DEFAULT = "default"
        private const val FONT_IMPORT_REQUEST = 205
        private const val PROVIDER_ROOT_REQUEST = 206
        private const val MAX_FONT_BYTES = 32L * 1024L * 1024L
        private val FONT_MIME_TYPES = arrayOf(
            "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf",
            "application/vnd.ms-opentype", "application/font-sfnt", "application/octet-stream"
        )
        private const val DEFAULT_TRASH_RETENTION_DAYS = 30
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_ROWS = 5000
        private const val PREVIEW_MAX_BYTES = 64 * 1024
        private const val PLAY_MARKET_URL = "market://details?id=com.dvil.retui.fm"
        private const val PLAY_HTTPS_URL = "https://play.google.com/store/apps/details?id=com.dvil.retui.fm"
        private const val FEEDBACK_URL = "https://github.com/DvilSpawn/Re-TUI-FM/issues/new"
        private const val PRIVACY_URL = "https://github.com/DvilSpawn/Re-TUI-FM/blob/master/docs/privacy.html"
        private const val FILE_ROW_HEIGHT_DP = 32
        private const val CATEGORY_FIRST_PAGE_ROWS = 90
        private const val TRASH_DIR_NAME = ".retui-trash"
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "svg", "bmp", "avif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "ogg", "opus", "aac")
        private val DOCUMENT_EXTENSIONS = setOf("txt", "md", "markdown", "pdf", "csv", "html", "xml", "json", "rtf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt")
        private val PREVIEW_TAG = Regex("</?[A-Za-z_][\\w:.-]*|/?>")
        private val PREVIEW_STRING = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        private val PREVIEW_JSON_KEY = Regex("^\\s*(\"[^\"]+\")\\s*:")
        private val PREVIEW_KEY = Regex("^\\s*([A-Za-z_][\\w.\\-]*)\\s*[=:]")
        private const val ARCHIVE_PREVIEW_MAX_BYTES = 64L * 1024 * 1024
        private val QUICK_PREVIEW_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "bmp", "avif") +
            setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "mp3", "wav", "flac", "m4a", "ogg", "opus", "aac", "pdf")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
        private val DIRECTORY_TYPE_TOKENS = setOf("dir", "dirs", "directory", "directories", "folder", "folders")
        private val SEARCH_TYPE_TOKENS = setOf(
            "image", "images", "img", "photo", "photos",
            "video", "videos", "movie", "movies",
            "audio", "music", "sound",
            "doc", "docs", "document", "documents", "text",
            "apk", "apks", "android",
            "archive", "archives"
        ) + DIRECTORY_TYPE_TOKENS
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
    }
}
