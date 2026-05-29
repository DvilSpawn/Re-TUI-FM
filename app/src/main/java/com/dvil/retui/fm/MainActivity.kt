package com.dvil.retui.fm

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.DialogInterface
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.JsonReader
import android.util.JsonToken
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dvil.retui.fm.FmVisualInterop.safeInsets
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Char
import kotlin.CharSequence
import kotlin.Comparator
import kotlin.Exception
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import kotlin.Number
import kotlin.RuntimeException
import kotlin.Throws
import kotlin.also
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.contains
import kotlin.collections.dropLastWhile
import kotlin.collections.indices
import kotlin.collections.mutableListOf
import kotlin.collections.remove
import kotlin.collections.removeAll
import kotlin.collections.sort
import kotlin.collections.toTypedArray
import kotlin.floatArrayOf
import kotlin.intArrayOf
import kotlin.io.startsWith
import kotlin.io.use
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.plus
import kotlin.text.StringBuilder
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.format
import kotlin.text.indexOf
import kotlin.text.isEmpty
import kotlin.text.lastIndexOf
import kotlin.text.lowercase
import kotlin.text.replace
import kotlin.text.replaceFirst
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.toByteArray
import kotlin.text.toInt
import kotlin.text.toLong
import kotlin.text.toRegex
import kotlin.text.trim
import kotlin.text.uppercase
import kotlin.text.uppercaseChar
import kotlin.toString

class MainActivity : Activity() {
    private var bgColor = Color.rgb(38, 40, 40)
    private var panelColor = Color.rgb(48, 50, 50)
    private var textColor = Color.rgb(195, 139, 150)
    private var borderColor = Color.rgb(103, 64, 71)
    private var modulePanelColor = panelColor
    private var moduleTextColor = textColor
    private var moduleBorderColor = borderColor
    private var headerPanelColor = panelColor
    private var headerTextColor = textColor
    private var moduleButtonBgColor = Color.rgb(103, 64, 83)
    private var moduleButtonTextColor = textColor
    private var moduleButtonBorderColor = borderColor
    private var inputBgColor = Color.TRANSPARENT
    private var inputTextColor = textColor
    private var outputPanelColor = panelColor
    private var outputTextColor = textColor
    private var outputBorderColor = borderColor
    private var topMarginDp = 18
    private var inputFontSizeSp = 14
    private var headerTextSizeSp = 14
    private var outputTextSizeSp = 13
    private var outputHeaderTextSizeSp = 14
    private var moduleCornerRadiusDp = 0
    private var outputCornerRadiusDp = 0
    private var headerCornerRadiusDp = 0
    private var displayMarginsMm = intArrayOf(0, 0, 0, 0)
    private var displayBottomMarginsMm = intArrayOf(0, 0, 0, 0)
    private var landscapeDisplayMarginsMm = intArrayOf(0, 0, 0, 0)
    private var landscapeBottomDisplayMarginsMm = intArrayOf(0, 0, 0, 0)
    private var landscapeTopMarginDp = 4
    private var cyberdeckMode = false
    private var crtFilter = false
    private var terminalBackgroundImage: String? = null
    private var appTypeface: Typeface? = Typeface.MONOSPACE
    private var iconTypeface: Typeface? = Typeface.MONOSPACE

    private var currentDirectory: File? = null
    private var stage: FrameLayout? = null
    private var root: RelativeLayout? = null
    private var rootLayoutParams: FrameLayout.LayoutParams? = null
    private var contentFrame: LinearLayout? = null
    private var treePaneFrame: FrameLayout? = null
    private var treePane: LinearLayout? = null
    private var previewHeaderView: LinearLayout? = null
    private var titleView: TextView? = null
    private var closeView: TextView? = null
    private var bottomDock: LinearLayout? = null
    private var inputGroup: LinearLayout? = null
    private var toolsView: LinearLayout? = null
    private var pathView: TextView? = null
    private var previewTitleView: TextView? = null
    private var outputView: TextView? = null
    private var outputContainer: LinearLayout? = null
    private var fileRowsView: LinearLayout? = null
    private var treeBottomSpacer: View? = null
    private var previewImageView: ImageView? = null
    private var previewEditorView: EditText? = null
    private var previewActionsView: LinearLayout? = null
    private var treeScroll: ScrollView? = null
    private var alphaRailHost: FrameLayout? = null
    private val alphaRailScroll: ScrollView? = null
    private val alphaRail: LinearLayout? = null
    private var alphaRailView: SideRailView? = null
    private var pinnedRailHost: FrameLayout? = null
    private val pinnedRailScroll: ScrollView? = null
    private val pinnedRail: LinearLayout? = null
    private var pinnedRailView: SideRailView? = null
    private var commandHintView: TextView? = null
    private var inputPrefixView: TextView? = null
    private var inputView: EditText? = null
    private var suggestionsScroll: HorizontalScrollView? = null
    private var suggestionsGroup: LinearLayout? = null
    private var outputScroll: ScrollView? = null
    private val history = ArrayList<String?>()
    private val expandedPaths: MutableSet<String> = HashSet<String>()
    private var activeTreeOptions: TreeOptions? = TreeOptions.Companion.defaultListing()
    private var historyIndex = -1
    private var operationOverlay: OperationOverlay? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var preserveInputAfterCommand = false
    private var commandOutputVisible = false
    private var previewFile: File? = null
    private var editorFile: File? = null
    private var highlightedPreviewFile: File? = null
    private val toolButtons = ArrayList<TextView>()
    private val visibleSections = ArrayList<String>()
    private val visibleSectionRows = ArrayList<Int?>()
    private val selectedPaths = LinkedHashSet<String>()
    private val recentPaths = ArrayList<String>()
    private var commandHintExpanded = false
    private var selectedSection: String? = null
    private var systemInsetLeft = 0
    private var systemInsetTop = 0
    private var systemInsetRight = 0
    private var systemInsetBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        applyIntentTheme(getIntent())
        loadLayoutSettings(getIntent())
        currentDirectory = resolveStartDirectory(getIntent())
        buildUi()
        ensureStorageAccess()
        renderListing()
        handleIncomingCommand(getIntent())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentTheme(intent)
        loadLayoutSettings(intent)
        currentDirectory = resolveStartDirectory(intent)
        applyStagePadding()
        applyWindowMargins()
        styleUi()
        renderListing()
        handleIncomingCommand(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val pendingInput = if (inputView == null) "" else inputView!!.getText().toString()
        val pendingPreview = if (commandOutputVisible) previewFile else null
        buildUi()
        renderListing((if (activeTreeOptions == null) TreeOptions.Companion.defaultListing() else activeTreeOptions)!!)
        if (pendingInput.length > 0 && inputView != null) {
            inputView!!.setText(pendingInput)
            inputView!!.setSelection(inputView!!.getText().length)
        }
        if (pendingPreview != null && pendingPreview.exists()) {
            previewResolvedFile(pendingPreview)
        }
    }

    override fun onBackPressed() {
        if (commandOutputVisible) {
            renderListing((if (activeTreeOptions == null) TreeOptions.Companion.defaultListing() else activeTreeOptions)!!)
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (currentDirectory != null && outputView != null) {
            renderListing()
        }
    }

    private fun buildUi() {
        resetViewReferencesForRebuild()
        val landscape = this.isLandscapeLayout
        stage = FrameLayout(this)
        applyStagePadding()
        stage!!.setClipChildren(false)
        stage!!.setClipToPadding(false)
        applyWallpaperBackground()
        setContentView(stage)
        installWindowInsetsHandler()

        root = RelativeLayout(this)
        if (landscape) {
            root!!.setPadding(0, 0, 0, 0)
        } else {
            root!!.setPadding(dp(14), dp(30), dp(14), dp(14))
        }
        root!!.setClipChildren(false)
        root!!.setClipToPadding(false)
        rootLayoutParams = FrameLayout.LayoutParams(-1, -1)
        rootLayoutParams!!.setMargins(0, dp(activeTopMarginDp()), 0, dp(if (landscape) 0 else 2))
        stage!!.addView(root, rootLayoutParams)

        bottomDock = LinearLayout(this)
        bottomDock!!.setId(View.generateViewId())
        bottomDock!!.setOrientation(LinearLayout.VERTICAL)
        bottomDock!!.setClipChildren(true)
        bottomDock!!.setClipToPadding(true)
        if (!landscape) {
            val dockParams = RelativeLayout.LayoutParams(-1, -2)
            dockParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            root!!.addView(bottomDock, dockParams)
        }

        contentFrame = LinearLayout(this)
        contentFrame!!.setId(View.generateViewId())
        contentFrame!!.setOrientation(LinearLayout.VERTICAL)
        contentFrame!!.setPadding(0, 0, 0, 0)
        contentFrame!!.setClipChildren(!landscape)
        contentFrame!!.setClipToPadding(!landscape)
        val contentParams = RelativeLayout.LayoutParams(-1, -1)
        if (!landscape) contentParams.addRule(RelativeLayout.ABOVE, bottomDock!!.getId())
        root!!.addView(contentFrame, contentParams)

        if (!landscape) {
            titleView = label("FILES", headerTextSizeSp, true)
            titleView!!.setGravity(Gravity.CENTER)
            titleView!!.setMinWidth(dp(160))
            titleView!!.setPadding(dp(12), dp(2), dp(12), dp(2))
            val titleParams = FrameLayout.LayoutParams(-2, -2)
            titleParams.leftMargin = dp(44)
            titleParams.topMargin = dp(8)
            stage!!.addView(titleView, titleParams)
        }

        closeView = label("X", 15, true)
        closeView!!.setGravity(Gravity.CENTER)
        closeView!!.setOnClickListener(View.OnClickListener { v: View? -> closePreviewOrFinish() })
        val closeParams = FrameLayout.LayoutParams(dp(48), dp(36), Gravity.TOP or Gravity.END)
        closeParams.topMargin = dp(8)
        stage!!.addView(closeView, closeParams)

        buildMainContent()
        buildSideRails()
        buildCommandDock()

        styleUi()
        installKeyboardInsetWatcher()
    }

    private fun resetViewReferencesForRebuild() {
        toolButtons.clear()
        treePane = null
        treePaneFrame = null
        previewHeaderView = null
        previewTitleView = null
        outputView = null
        outputContainer = null
        fileRowsView = null
        treeBottomSpacer = null
        previewImageView = null
        previewEditorView = null
        previewActionsView = null
        treeScroll = null
        outputScroll = null
        commandHintView = null
        editorFile = null
    }

    private fun buildCommandDock() {
        if (bottomDock == null) return
        bottomDock!!.setPadding(dp(8), dp(if (this.isLandscapeLayout) 6 else 8), dp(8), dp(8))

        commandHintView = label("", max(10, inputFontSizeSp - 2), false)
        commandHintView!!.setSingleLine(false)
        commandHintView!!.setMaxLines(commandHintCollapsedLines())
        commandHintView!!.setEllipsize(TextUtils.TruncateAt.END)
        commandHintView!!.setGravity(Gravity.CENTER_VERTICAL or Gravity.START)
        commandHintView!!.setIncludeFontPadding(true)
        commandHintView!!.setPadding(dp(8), dp(2), dp(8), dp(2))
        commandHintView!!.setClickable(true)
        commandHintView!!.setOnClickListener(View.OnClickListener { v: View? -> toggleCommandHintExpanded() })

        inputGroup = LinearLayout(this)
        inputGroup!!.setGravity(Gravity.CENTER_VERTICAL)
        inputGroup!!.setPadding(dp(8), 0, dp(8), 0)
        inputPrefixView = label("$ ", inputFontSizeSp, true)
        inputPrefixView!!.setGravity(Gravity.CENTER)
        inputPrefixView!!.setIncludeFontPadding(false)
        inputGroup!!.addView(inputPrefixView, LinearLayout.LayoutParams(-2, -1))

        inputView = EditText(this)
        inputView!!.setSingleLine(true)
        inputView!!.setTextSize(inputFontSizeSp.toFloat())
        inputView!!.setTypeface(appTypeface)
        inputView!!.setGravity(Gravity.CENTER_VERTICAL)
        inputView!!.setIncludeFontPadding(false)
        inputView!!.setPadding(0, 0, 0, 0)
        inputView!!.setBackgroundColor(Color.TRANSPARENT)
        inputView!!.setImeOptions(EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN)
        inputView!!.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            val enter =
                event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                runInput(inputView!!.getText().toString())
                return@OnEditorActionListener true
            }
            false
        })
        inputView!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateSuggestions(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        inputGroup!!.addView(inputView, LinearLayout.LayoutParams(0, -1, 1f))

        suggestionsScroll = HorizontalScrollView(this)
        suggestionsScroll!!.setHorizontalScrollBarEnabled(false)
        suggestionsScroll!!.setVisibility(View.GONE)
        suggestionsGroup = LinearLayout(this)
        suggestionsGroup!!.setOrientation(LinearLayout.HORIZONTAL)
        suggestionsScroll!!.addView(suggestionsGroup, FrameLayout.LayoutParams(-2, dp(34)))

        toolsView = LinearLayout(this)
        toolsView!!.setGravity(Gravity.CENTER)
        if (this.isLandscapeLayout) {
            addTool(
                toolsView!!,
                "Refresh",
                ICON_REFRESH,
                View.OnClickListener { v: View? -> seed("refresh") })
            addTool(toolsView!!, "Up", ICON_UP, View.OnClickListener { v: View? -> seed("cd ..") })
            addTool(
                toolsView!!,
                "Open",
                ICON_OPEN,
                View.OnClickListener { v: View? -> openPreviewOrSeed() })
            addTool(
                toolsView!!,
                "Share",
                ICON_SHARE,
                View.OnClickListener { v: View? -> sharePreviewOrSeed() })
            addTool(
                toolsView!!,
                "Permission",
                ICON_SETTINGS,
                View.OnClickListener { v: View? -> seed("permission") })
        } else {
            addTool(
                toolsView!!,
                "Refresh",
                ICON_REFRESH,
                View.OnClickListener { v: View? -> seed("refresh") })
            addTool(toolsView!!, "Up", ICON_UP, View.OnClickListener { v: View? -> seed("cd ..") })
            addTool(
                toolsView!!,
                "Open",
                ICON_OPEN,
                View.OnClickListener { v: View? -> openPreviewOrSeed() })
            addTool(
                toolsView!!,
                "Share",
                ICON_SHARE,
                View.OnClickListener { v: View? -> sharePreviewOrSeed() })
            addTool(
                toolsView!!,
                "Permission",
                ICON_SETTINGS,
                View.OnClickListener { v: View? -> seed("permission") })
        }

        val hintParams = LinearLayout.LayoutParams(-1, -2)
        hintParams.topMargin = dp(if (this.isLandscapeLayout) 4 else 8)
        bottomDock!!.addView(commandHintView, hintParams)

        val inputParams = LinearLayout.LayoutParams(-1, dp(if (this.isLandscapeLayout) 34 else 38))
        inputParams.topMargin = dp(2)
        bottomDock!!.addView(inputGroup, inputParams)

        val toolsParams = LinearLayout.LayoutParams(-1, dp(28))
        toolsParams.topMargin = dp(4)
        bottomDock!!.addView(suggestionsScroll, LinearLayout.LayoutParams(-1, dp(34)))
        bottomDock!!.addView(toolsView, toolsParams)
    }

    private fun buildMainContent() {
        if (this.isLandscapeLayout) {
            buildLandscapeContent()
        } else {
            buildPortraitContent()
        }
    }

    private fun buildPortraitContent() {
        contentFrame!!.setOrientation(LinearLayout.VERTICAL)
        pathView = label("", outputHeaderTextSizeSp, true)
        pathView!!.setSingleLine(true)
        pathView!!.setPadding(dp(34), 0, dp(34), 0)
        contentFrame!!.addView(pathView, LinearLayout.LayoutParams(-1, -2))

        outputScroll = ScrollView(this)
        outputScroll!!.setId(View.generateViewId())
        outputScroll!!.setFillViewport(true)
        treeScroll = outputScroll
        outputContainer = LinearLayout(this)
        outputContainer!!.setOrientation(LinearLayout.VERTICAL)
        outputContainer!!.setPadding(dp(34), 0, dp(34), 0)
        addOutputSurfaceViews(outputContainer!!, true)
        outputScroll!!.addView(outputContainer, FrameLayout.LayoutParams(-1, -2))
        contentFrame!!.addView(outputScroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun buildLandscapeContent() {
        contentFrame!!.setOrientation(LinearLayout.VERTICAL)
        contentFrame!!.setPadding(0, 0, 0, 0)

        treePane = LinearLayout(this)
        treePane!!.setOrientation(LinearLayout.VERTICAL)
        treePane!!.setPadding(dp(10), dp(18), dp(10), dp(8))

        titleView = label("FILES", headerTextSizeSp, true)
        titleView!!.setGravity(Gravity.CENTER)
        titleView!!.setMinWidth(dp(160))
        titleView!!.setPadding(dp(12), dp(2), dp(12), dp(2))

        pathView = label("", outputHeaderTextSizeSp, true)
        pathView!!.setSingleLine(true)
        pathView!!.setEllipsize(TextUtils.TruncateAt.MIDDLE)
        pathView!!.setPadding(dp(34), dp(2), dp(12), dp(2))
        treePane!!.addView(pathView, LinearLayout.LayoutParams(-1, -2))

        treeScroll = ScrollView(this)
        treeScroll!!.setId(View.generateViewId())
        treeScroll!!.setFillViewport(true)
        outputScroll = treeScroll
        val treeContainer = LinearLayout(this)
        treeContainer.setOrientation(LinearLayout.VERTICAL)
        treeContainer.setPadding(dp(34), 0, dp(12), 0)
        outputContainer = treeContainer
        addOutputSurfaceViews(outputContainer!!, true)
        treeScroll!!.addView(outputContainer, FrameLayout.LayoutParams(-1, -2))
        treePane!!.addView(treeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        treePane!!.addView(bottomDock, LinearLayout.LayoutParams(-1, -2))

        previewActionsView = LinearLayout(this)
        previewActionsView!!.setGravity(Gravity.CENTER_VERTICAL)
        previewActionsView!!.setVisibility(View.GONE)
        addPreviewAction("SAVE", View.OnClickListener { v: View? -> savePreviewEditor() })
        val actionsParams = LinearLayout.LayoutParams(-1, dp(30))
        actionsParams.topMargin = dp(4)
        bottomDock!!.addView(previewActionsView, actionsParams)

        treePaneFrame = buildLandscapePaneFrame(treePane, titleView)
        contentFrame!!.addView(treePaneFrame, LinearLayout.LayoutParams(-1, -1))
    }

    private fun buildLandscapePaneFrame(content: View?, title: View?): FrameLayout {
        val frame = FrameLayout(this)
        frame.setClipChildren(false)
        frame.setClipToPadding(false)
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))

        val titleParams = FrameLayout.LayoutParams(-2, -2)
        titleParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        titleParams.topMargin = -dp(9)
        frame.addView(title, titleParams)
        return frame
    }

    private fun addOutputSurfaceViews(parent: LinearLayout, includeRows: Boolean) {
        outputView = label("", outputTextSizeSp, false)
        outputView!!.setTextIsSelectable(false)
        outputView!!.setClickable(true)
        outputView!!.setLinksClickable(true)
        outputView!!.setHighlightColor(Color.TRANSPARENT)
        outputView!!.setMovementMethod(LinkMovementMethod.getInstance())
        parent.addView(outputView, LinearLayout.LayoutParams(-1, -2))

        previewImageView = ImageView(this)
        previewImageView!!.setAdjustViewBounds(true)
        previewImageView!!.setScaleType(ImageView.ScaleType.FIT_CENTER)
        previewImageView!!.setVisibility(View.GONE)
        val previewParams = LinearLayout.LayoutParams(-1, -2)
        previewParams.topMargin = dp(10)
        parent.addView(previewImageView, previewParams)

        previewEditorView = EditText(this)
        previewEditorView!!.setSingleLine(false)
        previewEditorView!!.setMinLines(12)
        previewEditorView!!.setGravity(Gravity.TOP or Gravity.START)
        previewEditorView!!.setHorizontallyScrolling(true)
        previewEditorView!!.setBackgroundColor(Color.TRANSPARENT)
        previewEditorView!!.setPadding(0, dp(8), 0, dp(8))
        previewEditorView!!.setVisibility(View.GONE)
        parent.addView(previewEditorView, LinearLayout.LayoutParams(-1, -2))

        if (includeRows) {
            fileRowsView = LinearLayout(this)
            fileRowsView!!.setOrientation(LinearLayout.VERTICAL)
            parent.addView(fileRowsView, LinearLayout.LayoutParams(-1, -2))
            treeBottomSpacer = View(this)
            parent.addView(treeBottomSpacer, LinearLayout.LayoutParams(-1, 0))
        }
    }

    private fun addPreviewAction(label: String?, listener: View.OnClickListener?) {
        if (previewActionsView == null) return
        val view = label(label, 12, true)
        view.setGravity(Gravity.CENTER)
        view.setPadding(dp(10), 0, dp(10), 0)
        styleChip(view, true)
        view.setOnClickListener(listener)
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(2), 0, dp(2), 0)
        previewActionsView!!.addView(view, params)
    }

    private fun buildSideRails() {
        if (this.isLandscapeLayout) return
        alphaRailHost = FrameLayout(this)
        alphaRailHost!!.setClipChildren(true)
        alphaRailHost!!.setClipToPadding(true)
        alphaRailView = SideRailView(this)
        alphaRailHost!!.addView(alphaRailView, FrameLayout.LayoutParams(-1, -1))
        stage!!.addView(alphaRailHost, FrameLayout.LayoutParams(dp(34), dp(80)))

        pinnedRailHost = FrameLayout(this)
        pinnedRailHost!!.setClipChildren(true)
        pinnedRailHost!!.setClipToPadding(true)
        pinnedRailView = SideRailView(this)
        pinnedRailHost!!.addView(pinnedRailView, FrameLayout.LayoutParams(-1, -1))
        stage!!.addView(pinnedRailHost, FrameLayout.LayoutParams(dp(38), dp(80)))
        installRailBoundsWatcher()
    }

    private fun installRailBoundsWatcher() {
        if (root == null || activeTreeScroll() == null || bottomDock == null) return
        root!!.getViewTreeObserver()
            .addOnGlobalLayoutListener(OnGlobalLayoutListener { this.updateRailBounds() })
        root!!.post(Runnable { this.updateRailBounds() })
    }

    private fun updateRailBounds() {
        val treeAnchorScroll = activeTreeScroll()
        if (alphaRailHost == null || pinnedRailHost == null || treeAnchorScroll == null || bottomDock == null || root == null || stage == null) return
        val stageLoc = IntArray(2)
        val anchorLoc = IntArray(2)
        val outputLoc = IntArray(2)
        val bottomLoc = IntArray(2)
        stage!!.getLocationInWindow(stageLoc)
        val sideAnchor: View = (if (treePane != null) treePane else root)!!
        sideAnchor.getLocationInWindow(anchorLoc)
        treeAnchorScroll.getLocationInWindow(outputLoc)
        val bottomAnchor: View = (if (inputGroup != null) inputGroup else bottomDock)!!
        bottomAnchor.getLocationInWindow(bottomLoc)
        val contentLeft = stage!!.getPaddingLeft()
        val contentTop = stage!!.getPaddingTop()
        val contentWidth = stage!!.getWidth() - stage!!.getPaddingLeft() - stage!!.getPaddingRight()
        val rootLeft = anchorLoc[0] - stageLoc[0] - contentLeft
        val rootRight = rootLeft + sideAnchor.getWidth()
        val top = outputLoc[1] - stageLoc[1] - contentTop + dp(42)
        val bottom = bottomLoc[1] - stageLoc[1] - contentTop - dp(8)
        val height = max(dp(80), bottom - top)
        val alphaWidth = dp(34)
        val pinnedWidth = dp(38)
        val minLeft = -contentLeft
        val maxLeft = contentWidth + stage!!.getPaddingRight() - pinnedWidth
        val alphaLeft = clamp(rootLeft - alphaWidth / 2, minLeft, maxLeft)
        val pinnedLeft = clamp(rootRight - pinnedWidth / 2, minLeft, maxLeft)
        applyRailBounds(alphaRailHost!!, alphaLeft, top, alphaWidth, height)
        applyRailBounds(pinnedRailHost!!, pinnedLeft, top, pinnedWidth, height)
    }

    private fun activeTreeScroll(): ScrollView? {
        return if (treeScroll != null) treeScroll else outputScroll
    }

    private fun applyRailBounds(rail: View, left: Int, top: Int, width: Int, height: Int) {
        val params = rail.getLayoutParams() as FrameLayout.LayoutParams?
        if (params == null) return
        if (params.leftMargin != left || params.topMargin != top || params.width != width || params.height != height) {
            params.leftMargin = left
            params.topMargin = top
            params.width = width
            params.height = height
            rail.setLayoutParams(params)
        }
    }

    private fun styleUi() {
        if (root == null) return
        applyWallpaperBackground()
        applyCrtOverlay()
        applyWindowMargins()
        if (this.isLandscapeLayout) {
            root!!.setBackgroundColor(Color.TRANSPARENT)
            if (treePaneFrame != null) stylePanel(treePaneFrame!!, PanelRole.MODULE)
        } else {
            stylePanel(root!!, false)
        }
        if (contentFrame != null) {
            contentFrame!!.setBackgroundColor(Color.TRANSPARENT)
        }
        if (bottomDock != null) {
            stylePanel(
                bottomDock!!,
                if (this.isLandscapeLayout) PanelRole.OUTPUT else PanelRole.MODULE
            )
        }
        if (titleView != null) {
            titleView!!.setTextColor(headerTextColor)
            titleView!!.setTextSize(headerTextSizeSp.toFloat())
            stylePanel(titleView!!, PanelRole.HEADER)
        }
        if (closeView != null) {
            closeView!!.setTextColor(headerTextColor)
            stylePanel(closeView!!, PanelRole.HEADER)
        }
        bindPanelCutouts(root, titleView, closeView)
        bindPanelCutouts(treePaneFrame, titleView, closeView)
        if (pathView != null) {
            pathView!!.setTextColor(outputTextColor)
            pathView!!.setTextSize(outputHeaderTextSizeSp.toFloat())
        }
        if (outputView != null) {
            outputView!!.setTextColor(outputTextColor)
            outputView!!.setTextSize(outputTextSizeSp.toFloat())
            outputView!!.setTypeface(appTypeface)
        }
        if (previewTitleView != null) {
            previewTitleView!!.setTextColor(headerTextColor)
            previewTitleView!!.setTextSize(headerTextSizeSp.toFloat())
            previewTitleView!!.setTypeface(appTypeface, Typeface.BOLD)
            stylePanel(previewTitleView!!, PanelRole.HEADER)
        }
        if (previewEditorView != null) {
            previewEditorView!!.setTextColor(outputTextColor)
            previewEditorView!!.setHintTextColor(
                Color.argb(
                    150,
                    Color.red(outputTextColor),
                    Color.green(outputTextColor),
                    Color.blue(outputTextColor)
                )
            )
            previewEditorView!!.setTextSize(outputTextSizeSp.toFloat())
            previewEditorView!!.setTypeface(appTypeface)
        }
        if (inputView != null) {
            inputView!!.setTextColor(inputTextColor)
            inputView!!.setHintTextColor(
                Color.argb(
                    150,
                    Color.red(inputTextColor),
                    Color.green(inputTextColor),
                    Color.blue(inputTextColor)
                )
            )
            inputView!!.setTypeface(appTypeface)
            inputView!!.setTextSize(inputFontSizeSp.toFloat())
            inputView!!.setGravity(Gravity.CENTER_VERTICAL)
            inputView!!.setIncludeFontPadding(false)
            stylePanel(inputGroup!!, PanelRole.INPUT)
        }
        if (commandHintView != null) {
            commandHintView!!.setTextColor(withAlpha(inputTextColor, 190))
            commandHintView!!.setTypeface(appTypeface)
            commandHintView!!.setTextSize(max(10, inputFontSizeSp - 2).toFloat())
            commandHintView!!.setIncludeFontPadding(true)
            commandHintView!!.setBackgroundColor(Color.TRANSPARENT)
            updateCommandHint(if (inputView == null) "" else inputView!!.getText().toString())
        }
        if (inputPrefixView != null) {
            inputPrefixView!!.setTextColor(inputTextColor)
            inputPrefixView!!.setTypeface(appTypeface, Typeface.BOLD)
            inputPrefixView!!.setTextSize(inputFontSizeSp.toFloat())
            inputPrefixView!!.setGravity(Gravity.CENTER)
            inputPrefixView!!.setIncludeFontPadding(false)
        }
        if (toolsView != null) {
            toolsView!!.setBackgroundColor(Color.TRANSPARENT)
            for (button in toolButtons) styleToolButton(button)
        }
        if (previewActionsView != null) {
            for (i in 0..<previewActionsView!!.getChildCount()) {
                val child = previewActionsView!!.getChildAt(i)
                if (child is TextView) styleChip(child, true)
            }
        }
        if (suggestionsGroup != null) {
            for (i in 0..<suggestionsGroup!!.getChildCount()) {
                val child = suggestionsGroup!!.getChildAt(i)
                if (child is TextView) {
                    val tag = child.getTag()
                    styleSuggestionChip(child, tag !is Boolean || tag)
                }
            }
        }
        if (alphaRailView != null) alphaRailView!!.invalidate()
        if (pinnedRailView != null) pinnedRailView!!.invalidate()
    }

    private fun installKeyboardInsetWatcher() {
        if (stage == null || rootLayoutParams == null) return
        stage!!.getViewTreeObserver().addOnGlobalLayoutListener(OnGlobalLayoutListener {
            val visible = Rect()
            stage!!.getWindowVisibleDisplayFrame(visible)
            val screenHeight = stage!!.getRootView().getHeight()
            val keyboardHeight = max(0, screenHeight - visible.bottom)
            val newBottom = if (keyboardHeight > dp(120)) keyboardHeight + dp(8) else dp(2)
            if (rootLayoutParams!!.bottomMargin != newBottom) {
                rootLayoutParams!!.bottomMargin = newBottom
                root!!.setLayoutParams(rootLayoutParams)
            }
        })
    }

    private fun runInput(raw: String?) {
        val command = if (raw == null) "" else raw.trim { it <= ' ' }
        if (command.length == 0) return
        history.add(command)
        historyIndex = history.size
        preserveInputAfterCommand = false
        execute(command)
        if (!preserveInputAfterCommand) inputView!!.setText("")
        refocusInput()
    }

    private fun closePreviewOrFinish() {
        if (commandOutputVisible) {
            renderListing((if (activeTreeOptions == null) TreeOptions.Companion.defaultListing() else activeTreeOptions)!!)
            return
        }
        finish()
    }

    private fun execute(command: String) {
        val lower = command.lowercase()
        if ("help" == lower) {
            print("Commands:\ncd [folder]\ncd ..\nls\npwd\nfind [path] -name [pattern] [-x|-a] [--type image|video|audio|doc|dir|file] [--size +100M]\nsearch [pattern]\nfilter [pattern]\ntree [-a -d -f -h -s -D -F -i -r --dirsfirst --ignore-case --noreport -L n -P pattern -I pattern --sort name|size|mtime]\npreview [file]        read-only .md .txt .log .json .csv previews are bounded\nedit [text file]      text editor; large files stay read-only\nopen [file]\nshare [file]\nmkdir [folder]\ncp [-r] [source] [destination]\nmv [source] [destination]\nrm [-r] [file]        guarded; moves to .retui-trash by default\nrm --permanent [file] guarded; files only, no directory trees\ntrash [file]\nrestore [label|all]\nzip -r [archive.zip] [folder]\nsel add|rm|list|clear|trash|share|zip|cp|mv\nrecent\nback\nfav here\nfav add [label] [path]\nfav go [label]\nfav rm [label]\nfav rename [old] [new]\nfav list\npermission\nrefresh\nexit")
        } else if ("exit" == lower || "close" == lower) {
            finish()
        } else if ("permission" == lower || "permissions" == lower || "permit" == lower) {
            openStorageAccessSettings()
        } else if ("tree" == lower || lower.startsWith("tree ")) {
            renderTree(command)
        } else if ("find" == lower || lower.startsWith("find ") || lower.startsWith("search ")) {
            runFind(command)
        } else if ("filter" == lower || lower.startsWith("filter ")) {
            runFilter(command)
        } else if ("fav" == lower || lower.startsWith("fav ")) {
            runFavoriteCommand(splitArgs(command))
        } else if ("sel" == lower || lower.startsWith("sel ") || "mark" == lower || lower.startsWith(
                "mark "
            ) || "unmark" == lower || lower.startsWith("unmark ")
        ) {
            runSelectionCommand(splitArgs(command))
        } else if ("trash" == lower || lower.startsWith("trash ")) {
            runTrashCommand(splitArgs(command))
        } else if ("restore" == lower || lower.startsWith("restore ")) {
            runRestoreCommand(splitArgs(command))
        } else if ("preview" == lower || lower.startsWith("preview ") || lower.startsWith("peek ")) {
            runPreview(command)
        } else if ("edit" == lower || lower.startsWith("edit ")) {
            runEdit(command)
        } else if ("recent" == lower || lower.startsWith("recent ") || "back" == lower) {
            runRecentCommand(splitArgs(command))
        } else if ("ls" == lower || lower.startsWith("ls ") || "refresh" == lower) {
            renderListing()
        } else if ("pwd" == lower) {
            print(currentDirectory!!.getAbsolutePath())
        } else if (lower == "cd" || lower.startsWith("cd ")) {
            changeDirectory(if (command.length > 2) command.substring(2).trim { it <= ' ' } else "")
        } else if ("open" == lower) {
            openPreviewOrSeed()
        } else if (lower.startsWith("open ")) {
            openFile(command.substring(5).trim { it <= ' ' })
        } else if (lower.startsWith("share ")) {
            shareFile(command.substring(6).trim { it <= ' ' })
        } else if (lower.startsWith("mkdir ")) {
            runShellFileCommand(command)
        } else if (lower.startsWith("rm ") || lower.startsWith("cp ") || lower.startsWith("mv ") || lower.startsWith(
                "zip "
            )
        ) {
            runShellFileCommand(command)
        } else {
            preserveInputAfterCommand = true
            showTerminalPopup("error", "Command not found: " + command + "\nType help.")
        }
    }

    private fun changeDirectory(target: String?) {
        val dir = resolve(target)
        if (dir == null || !dir.exists()) {
            preserveInputAfterCommand = true
            showTerminalPopup("cd", "Not found: " + target)
            return
        }
        if (!dir.isDirectory()) {
            preserveInputAfterCommand = true
            showTerminalPopup("cd", "Not a directory: " + target)
            return
        }
        if (!canListDirectory(dir)) {
            preserveInputAfterCommand = true
            showTerminalPopup("cd", "Cannot read: " + dir.getAbsolutePath())
            return
        }
        addRecentDirectory(currentDirectory)
        currentDirectory = dir
        expandedPaths.clear()
        renderListing()
    }

    private fun renderListing(options: TreeOptions = TreeOptions.Companion.defaultListing()) {
        activeTreeOptions = options
        if (pathView != null) pathView!!.setText(currentDirectory!!.getAbsolutePath())
        if (!hasStorageAccess()) {
            showTerminalPopup(
                "permission",
                "Storage access required.\nType: permission\nOr grant All files access in Android settings."
            )
            return
        }
        var children = currentDirectory!!.listFiles()
        if (children == null) {
            val fallback = sharedStorageRoot()
            if (fallback != null && (pathKey(fallback) != pathKey(currentDirectory!!)) && canListDirectory(
                    fallback
                )
            ) {
                currentDirectory = fallback
                expandedPaths.clear()
                if (pathView != null) pathView!!.setText(currentDirectory!!.getAbsolutePath())
                children = currentDirectory!!.listFiles()
            }
        }
        if (children == null) {
            if (fileRowsView != null) {
                fileRowsView!!.removeAllViews()
                fileRowsView!!.setVisibility(View.GONE)
            }
            showTerminalPopup("error", "Cannot read: " + currentDirectory!!.getAbsolutePath())
            return
        }
        val report = StringBuilder()
        val rows: ArrayList<TreeRow> = ArrayList<TreeRow>()
        val remaining = intArrayOf(TREE_MAX_ITEMS)
        val stats = TreeStats()
        appendTreeRows(rows, currentDirectory!!, "", 0, options, remaining, stats)
        if (remaining[0] <= 0) {
            report.append("...\n")
            report.append("Output capped. Use cd to narrow the surface.")
        }
        if (!options.noReport) {
            if (report.length > 0) report.append("\n\n")
            report.append(stats.directories)
                .append(if (stats.directories == 1) " directory" else " directories")
            if (!options.dirsOnly) {
                report.append(", ").append(stats.files)
                    .append(if (stats.files == 1) " file" else " files")
            }
        }
        renderFileRows(rows, report.toString())
        updateSuggestions(if (inputView == null) "" else inputView!!.getText().toString())
    }

    private fun renderTree(command: String) {
        val options = parseTreeOptions(command)
        if (options.error != null) {
            preserveInputAfterCommand = true
            showTerminalPopup("tree", options.error)
            return
        }
        renderListing(options)
    }

    private fun runFind(command: String) {
        val args = splitArgs(command)
        if (args.isEmpty()) return
        val searchAlias = "search".equals(args.get(0), ignoreCase = true)
        var rootDir = currentDirectory
        var pattern: String? = null
        var allFiles = false
        var limit = 400
        val filter = SearchFilter()

        var i = 1
        while (i < args.size) {
            val arg = args.get(i)
            if ("-a" == arg || "-x" == arg) {
                allFiles = true
            } else if ("-name" == arg || "--name" == arg) {
                if (i + 1 >= args.size) {
                    preserveInputAfterCommand = true
                    showTerminalPopup("find", "find: -name requires a pattern")
                    return
                }
                pattern = args.get(++i)
            } else if ("-max" == arg || "--limit" == arg) {
                if (i + 1 >= args.size) {
                    preserveInputAfterCommand = true
                    showTerminalPopup("find", "find: " + arg + " requires a number")
                    return
                }
                val parsed = parsePositiveInt(args.get(++i), "find: invalid limit")
                if (parsed <= 0) {
                    preserveInputAfterCommand = true
                    showTerminalPopup("find", "find: invalid limit")
                    return
                }
                limit = parsed
            } else if ("--type" == arg) {
                if (i + 1 >= args.size) {
                    preserveInputAfterCommand = true
                    showTerminalPopup(
                        "find",
                        "find: --type requires image, video, audio, doc, dir, or file"
                    )
                    return
                }
                filter.type = args.get(++i).lowercase()
            } else if ("--size" == arg) {
                if (i + 1 >= args.size || !filter.setSize(args.get(++i))) {
                    preserveInputAfterCommand = true
                    showTerminalPopup("find", "find: --size expects +100M, -10M, or 500K")
                    return
                }
            } else if (pattern == null && (searchAlias || arg.contains("*") || arg.contains("?"))) {
                pattern = arg
            } else if (pattern == null && args.size == 2) {
                pattern = arg
            } else {
                val maybeRoot = resolve(arg)
                if (maybeRoot != null && maybeRoot.exists() && maybeRoot.isDirectory()) {
                    rootDir = maybeRoot
                } else if (pattern == null) {
                    pattern = arg
                } else {
                    preserveInputAfterCommand = true
                    showTerminalPopup("find", "find: unsupported argument: " + arg)
                    return
                }
            }
            i++
        }

        if (pattern == null || pattern.length == 0) {
            preserveInputAfterCommand = true
            showTerminalPopup("find", "find: usage: find [path] -name [pattern]\nsearch [pattern]")
            return
        }
        val compiled = compileFindPattern(pattern)
        val rows: ArrayList<TreeRow> = ArrayList<TreeRow>()
        val remaining = intArrayOf(limit)
        findRows(rootDir, compiled, allFiles, filter, rows, remaining)
        var report = rows.size.toString() + (if (rows.size == 1) " match" else " matches")
        if (remaining[0] <= 0) report += "\nOutput capped. Use a narrower pattern."
        renderFileRows(rows, report)
    }

    private fun compileFindPattern(pattern: String): Pattern {
        if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
            return compileGlob(pattern, true)
        }
        return Pattern.compile(".*" + Pattern.quote(pattern.lowercase()) + ".*")
    }

    private fun runFilter(command: String) {
        val args = splitArgs(command)
        if (args.size < 2) {
            preserveInputAfterCommand = true
            showTerminalPopup("filter", "filter: usage: filter [pattern]")
            return
        }
        val pattern = compileFindPattern(args.get(1))
        val children = currentDirectory!!.listFiles()
        if (children == null) {
            showTerminalPopup("filter", "Cannot read: " + currentDirectory!!.getAbsolutePath())
            return
        }
        val rows: ArrayList<TreeRow> = ArrayList<TreeRow>()
        children.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        for (child in children) {
            val name = child.getName()
            if (name.startsWith(".")) continue
            if (pattern.matcher(name.lowercase()).matches()) {
                rows.add(
                    TreeRow(
                        "",
                        iconFor(child, false),
                        child.getName(),
                        child,
                        child.isDirectory(),
                        false,
                        hasChildren(child)
                    )
                )
            }
        }
        renderFileRows(
            rows,
            rows.size.toString() + (if (rows.size == 1) " visible match" else " visible matches")
        )
    }

    private fun findRows(
        directory: File?,
        pattern: Pattern,
        allFiles: Boolean,
        filter: SearchFilter,
        rows: MutableList<TreeRow>,
        remaining: IntArray
    ) {
        if (directory == null || remaining[0] <= 0) return
        val children = directory.listFiles()
        if (children == null) return
        children.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        for (child in children) {
            if (remaining[0] <= 0) return
            val name = child.getName()
            if (!allFiles && name.startsWith(".")) continue
            if (pattern.matcher(name.lowercase()).matches() && filter.matches(child)) {
                val display = if (pathKey(child).startsWith(pathKey(currentDirectory!!)))
                    pathKey(child).substring(pathKey(currentDirectory!!).length)
                        .replaceFirst("^/".toRegex(), "")
                else
                    child.getAbsolutePath()
                rows.add(
                    TreeRow(
                        "|-- ",
                        iconFor(child, false),
                        display,
                        child,
                        child.isDirectory(),
                        false,
                        hasChildren(child)
                    )
                )
                remaining[0]--
            }
            if (child.isDirectory()) findRows(child, pattern, allFiles, filter, rows, remaining)
        }
    }

    private fun appendTree(
        out: SpannableStringBuilder,
        directory: File,
        prefix: kotlin.String?,
        depth: Int,
        options: TreeOptions,
        remaining: IntArray,
        stats: TreeStats
    ) {
        val children = directory.listFiles()
        if (children == null) {
            if (out.length == 0) out.append("[..]")
            return
        }
        val visible = filterChildren(children, options)
        visible.sortWith(Comparator { left: File?, right: File? ->
            compareFilesForTree(
                left!!,
                right!!,
                options
            )
        })
        if (depth == 0) {
            val parentStart = out.length
            out.append("[..]")
            out.setSpan(
                FileClickSpan(directory.getParentFile(), true),
                parentStart,
                out.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        var index = 0
        while (index < visible.size && remaining[0] > 0) {
            val child = visible.get(index)
            val last = index == visible.size - 1
            out.append('\n')
            appendTreeLine(out, child, prefix, last, options)
            remaining[0]--
            if (child.isDirectory()) {
                stats.directories++
            } else {
                stats.files++
            }
            val manuallyExpanded = child.isDirectory() && expandedPaths.contains(pathKey(child))
            if (child.isDirectory() && (depth < options.maxDepth || manuallyExpanded) && remaining[0] > 0) {
                appendTree(
                    out,
                    child,
                    prefix + branchPadding(last, options),
                    depth + 1,
                    options,
                    remaining,
                    stats
                )
            }
            index++
        }
    }

    private fun appendTreeRows(
        rows: MutableList<TreeRow>,
        directory: File,
        prefix: kotlin.String,
        depth: Int,
        options: TreeOptions,
        remaining: IntArray,
        stats: TreeStats
    ) {
        val children = directory.listFiles()
        if (depth == 0) {
            rows.add(
                TreeRow(
                    "",
                    "",
                    "[..]",
                    directory.getParentFile(),
                    true,
                    false,
                    directory.getParentFile() != null
                )
            )
        }
        if (children == null) return
        val visible = filterChildren(children, options)
        visible.sortWith(Comparator { left: File?, right: File? ->
            compareFilesForTree(
                left!!,
                right!!,
                options
            )
        })
        var index = 0
        while (index < visible.size && remaining[0] > 0) {
            val child = visible.get(index)
            val last = index == visible.size - 1
            val manuallyExpanded = child.isDirectory() && expandedPaths.contains(pathKey(child))
            val expanded = child.isDirectory() && (depth < options.maxDepth || manuallyExpanded)
            val rowPrefix = treePrefixText(prefix, last, options)
            val icon = iconFor(child, expanded)
            rows.add(
                TreeRow(
                    rowPrefix,
                    icon,
                    treeNameText(child, options),
                    child,
                    child.isDirectory(),
                    expanded,
                    hasChildren(child)
                )
            )
            remaining[0]--
            if (child.isDirectory()) {
                stats.directories++
            } else {
                stats.files++
            }
            if (expanded && remaining[0] > 0) {
                appendTreeRows(
                    rows,
                    child,
                    prefix + branchPadding(last, options),
                    depth + 1,
                    options,
                    remaining,
                    stats
                )
            }
            index++
        }
    }

    private fun filterChildren(children: Array<File>, options: TreeOptions): MutableList<File> {
        val visible = ArrayList<File>()
        for (child in children) {
            if (!options.allFiles && child.getName().startsWith(".")) continue
            if (options.dirsOnly && !child.isDirectory()) continue
            if (options.includePattern != null && !matchesPattern(
                    child,
                    options.includePattern!!,
                    options.ignoreCase,
                    options.matchDirs
                )
            ) continue
            if (options.excludePattern != null && matchesPattern(
                    child,
                    options.excludePattern!!,
                    options.ignoreCase,
                    true
                )
            ) continue
            visible.add(child)
        }
        return visible
    }

    private fun matchesPattern(
        file: File,
        pattern: Pattern,
        ignoreCase: Boolean,
        includeDirectories: Boolean
    ): Boolean {
        if (file.isDirectory() && !includeDirectories) return false
        val value = file.getName()
        return pattern.matcher(if (ignoreCase) value.lowercase() else value).matches()
    }

    private fun appendTreeLine(
        out: SpannableStringBuilder,
        file: File,
        prefix: kotlin.String?,
        last: Boolean,
        options: TreeOptions
    ) {
        val lineStart = out.length
        if (!options.fullPath) {
            out.append(prefix)
            if (!options.noIndentLines) out.append(if (last) "`-- " else "|-- ")
        }
        if (options.humanSize || options.byteSize) {
            out.append('[').append(
                if (options.humanSize) humanSize(file.length()) else file.length().toString()
            ).append("] ")
        }
        if (options.date) {
            out.append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified()))
                .append(' ')
        }
        if (!options.fullPath && file.isDirectory()) out.append("[D] ")
        out.append(if (options.fullPath) file.getAbsolutePath() else file.getName())
        if (options.typeSuffix) out.append(typeSuffix(file))
        out.setSpan(
            FileClickSpan(file, file.isDirectory()),
            lineStart,
            out.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun treeLineText(
        file: File,
        prefix: kotlin.String?,
        last: Boolean,
        options: TreeOptions
    ): kotlin.String {
        val out = StringBuilder()
        if (!options.fullPath) {
            out.append(prefix)
            if (!options.noIndentLines) out.append(if (last) "`-- " else "|-- ")
        }
        if (options.humanSize || options.byteSize) {
            out.append('[').append(
                if (options.humanSize) humanSize(file.length()) else file.length().toString()
            ).append("] ")
        }
        if (options.date) {
            out.append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified()))
                .append(' ')
        }
        if (!options.fullPath && file.isDirectory()) out.append("[D] ")
        out.append(if (options.fullPath) file.getAbsolutePath() else file.getName())
        if (options.typeSuffix) out.append(typeSuffix(file))
        return out.toString()
    }

    private fun treePrefixText(
        prefix: kotlin.String,
        last: Boolean,
        options: TreeOptions
    ): kotlin.String {
        if (options.fullPath) return ""
        val out = StringBuilder(prefix)
        if (!options.noIndentLines) out.append(if (last) "`-- " else "|-- ")
        return out.toString()
    }

    private fun treeNameText(file: File, options: TreeOptions): kotlin.String {
        val out = StringBuilder()
        if (options.humanSize || options.byteSize) {
            out.append('[').append(
                if (options.humanSize) humanSize(file.length()) else file.length().toString()
            ).append("] ")
        }
        if (options.date) {
            out.append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified()))
                .append(' ')
        }
        out.append(if (options.fullPath) file.getAbsolutePath() else file.getName())
        if (options.typeSuffix) out.append(typeSuffix(file))
        return out.toString()
    }

    private fun iconFor(file: File?, expanded: Boolean): kotlin.String {
        if (file == null) return ""
        if (file.isDirectory()) return if (expanded) ICON_FOLDER_OPEN else ICON_FOLDER
        val ext = extension(file.getName())
        if (isOneOf(
                ext,
                "jpg",
                "jpeg",
                "png",
                "gif",
                "webp",
                "bmp",
                "heic",
                "heif",
                "svg"
            )
        ) return ICON_IMAGE
        if (isOneOf(
                ext,
                "pdf",
                "doc",
                "docx",
                "txt",
                "rtf",
                "odt",
                "xls",
                "xlsx",
                "ppt",
                "pptx",
                "md"
            )
        ) return ICON_DOCUMENT
        if (isOneOf(
                ext,
                "json",
                "xml",
                "html",
                "css",
                "js",
                "ts",
                "java",
                "kt",
                "kts",
                "lua",
                "sh",
                "py",
                "rb",
                "go",
                "rs",
                "c",
                "cpp",
                "h",
                "gradle",
                "yml",
                "yaml"
            )
        ) return ICON_CODE
        return ICON_FILE
    }

    private fun extension(name: kotlin.String?): kotlin.String {
        val index = if (name == null) -1 else name.lastIndexOf('.')
        if (index < 0 || index == name!!.length - 1) return ""
        return name.substring(index + 1).lowercase()
    }

    private fun isOneOf(value: kotlin.String?, vararg options: kotlin.String): Boolean {
        for (option in options) {
            if (option == value) return true
        }
        return false
    }

    private fun isType(file: File, type: kotlin.String?): Boolean {
        if (TextUtils.isEmpty(type)) return true
        if ("dir" == type || "directory" == type) return file.isDirectory()
        if ("file" == type) return file.isFile()
        val ext = extension(file.getName())
        if ("image" == type || "img" == type) return isOneOf(
            ext,
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "bmp",
            "heic",
            "heif",
            "svg"
        )
        if ("video" == type) return isOneOf(ext, "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        if ("audio" == type) return isOneOf(ext, "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
        if ("doc" == type || "document" == type) return isOneOf(
            ext,
            "pdf",
            "doc",
            "docx",
            "txt",
            "rtf",
            "odt",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "md"
        )
        if ("archive" == type) return isOneOf(ext, "zip", "tar", "gz", "tgz", "7z", "rar")
        return false
    }

    private fun hasChildren(directory: File?): Boolean {
        if (directory == null || !directory.isDirectory()) return false
        val children = directory.listFiles()
        return children != null && children.size > 0
    }

    private fun branchPadding(last: Boolean, options: TreeOptions): kotlin.String {
        if (options.noIndentLines) return "    "
        return if (last) "    " else "|   "
    }

    private fun compareFilesForTree(left: File, right: File, options: TreeOptions): Int {
        if (options.dirsFirst && left.isDirectory() != right.isDirectory()) {
            return if (left.isDirectory()) -1 else 1
        }
        val result: Int
        if ("size" == options.sort) {
            result = left.length().compareTo(right.length())
        } else if ("mtime" == options.sort) {
            result = left.lastModified().compareTo(right.lastModified())
        } else {
            result = String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        return if (options.reverse) -result else result
    }

    private fun parseTreeOptions(command: kotlin.String): TreeOptions {
        val options = TreeOptions.Companion.expanded()
        val args = splitArgs(command)
        var i = 1
        while (i < args.size) {
            val arg: kotlin.String? = args.get(i)
            if ("--" == arg) break
            if ("-a" == arg) options.allFiles = true
            else if ("-d" == arg) options.dirsOnly = true
            else if ("-f" == arg) options.fullPath = true
            else if ("-h" == arg) options.humanSize = true
            else if ("-s" == arg) options.byteSize = true
            else if ("-D" == arg) options.date = true
            else if ("-F" == arg) options.typeSuffix = true
            else if ("-i" == arg) options.noIndentLines = true
            else if ("-r" == arg) options.reverse = true
            else if ("--dirsfirst" == arg) options.dirsFirst = true
            else if ("--ignore-case" == arg) options.ignoreCase = true
            else if ("--matchdirs" == arg) options.matchDirs = true
            else if ("--noreport" == arg) options.noReport = true
            else if ("-L" == arg) {
                if (i + 1 >= args.size) return TreeOptions.Companion.error("tree: -L requires a level")
                options.maxDepth = parsePositiveInt(args.get(++i), "tree: invalid level")
                if (options.maxDepth < 0) return TreeOptions.Companion.error("tree: invalid level")
            } else if ("-P" == arg) {
                if (i + 1 >= args.size) return TreeOptions.Companion.error("tree: -P requires a pattern")
                options.includePattern = compileGlob(args.get(++i), options.ignoreCase)
            } else if ("-I" == arg) {
                if (i + 1 >= args.size) return TreeOptions.Companion.error("tree: -I requires a pattern")
                options.excludePattern = compileGlob(args.get(++i), options.ignoreCase)
            } else if ("--sort" == arg) {
                if (i + 1 >= args.size) return TreeOptions.Companion.error("tree: --sort requires name, size, or mtime")
                val sort = args.get(++i).lowercase()
                if (("name" != sort) && ("size" != sort) && ("mtime" != sort)) {
                    return TreeOptions.Companion.error("tree: unsupported sort: " + sort)
                }
                options.sort = sort
            } else {
                return TreeOptions.Companion.error("tree: unsupported option: " + arg)
            }
            i++
        }
        return options
    }

    private fun parsePositiveInt(raw: kotlin.String, error: kotlin.String?): Int {
        try {
            val value = raw.toInt()
            return if (value >= 0) value else -1
        } catch (e: Exception) {
            return -1
        }
    }

    private fun parseSizeSpec(raw: kotlin.String?): kotlin.Long {
        if (TextUtils.isEmpty(raw)) return -1
        var value = raw!!.trim { it <= ' ' }.uppercase()
        var multiplier: kotlin.Long = 1
        val last = value.get(value.length - 1)
        if (last == 'K' || last == 'M' || last == 'G') {
            value = value.substring(0, value.length - 1)
            if (last == 'K') multiplier = 1024L
            else if (last == 'M') multiplier = 1024L * 1024L
            else multiplier = 1024L * 1024L * 1024L
        }
        try {
            return value.toLong() * multiplier
        } catch (e: Exception) {
            return -1
        }
    }

    private fun compileGlob(glob: kotlin.String, ignoreCase: Boolean): Pattern {
        val value = if (ignoreCase) glob.lowercase() else glob
        val regex = StringBuilder()
        for (i in 0..<value.length) {
            val c = value.get(i)
            if (c == '*') regex.append(".*")
            else if (c == '?') regex.append('.')
            else regex.append(Pattern.quote(c.toString()))
        }
        return Pattern.compile(regex.toString())
    }

    private fun splitArgs(raw: kotlin.String): MutableList<kotlin.String> {
        val args = ArrayList<kotlin.String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        for (i in 0..<raw.length) {
            val c = raw.get(i)
            if (escaped) {
                current.append(c)
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                quoted = !quoted
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length > 0) {
                    args.add(current.toString())
                    current.setLength(0)
                }
            } else {
                current.append(c)
            }
        }
        if (escaped) current.append('\\')
        if (current.length > 0) args.add(current.toString())
        return args
    }

    private fun humanSize(bytes: kotlin.Long): kotlin.String {
        if (bytes < 1024) return bytes.toString() + "B"
        val units = arrayOf<kotlin.String?>("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value = value / 1024.0
            unit++
        } while (value >= 1024 && unit < units.size - 1)
        return kotlin.String.format(Locale.US, "%.1f%s", value, units[unit])
    }

    private fun typeSuffix(file: File): kotlin.String {
        if (file.isDirectory()) return "/"
        if (file.canExecute()) return "*"
        return ""
    }

    private fun openFile(target: kotlin.String?) {
        val file = resolve(target)
        if (file == null || !file.exists()) {
            preserveInputAfterCommand = true
            showTerminalPopup("open", "Not found: " + target)
            return
        }
        if (file.isDirectory()) {
            changeDirectory(target)
            return
        }
        openFile(file)
    }

    private fun openFile(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uriFor(file), mimeFor(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
            print("Opening: " + file.getName())
        } catch (e: Exception) {
            showTerminalPopup("open", "No app can open: " + file.getName())
        }
    }

    private fun openPreviewOrSeed() {
        if (commandOutputVisible && previewFile != null && previewFile!!.exists()) {
            seed((if (previewFile!!.isDirectory()) "cd " else "open ") + commandPath(previewFile))
        } else {
            seed("open ")
        }
    }

    private fun sharePreviewOrSeed() {
        if (commandOutputVisible && previewFile != null && previewFile!!.exists() && previewFile!!.isFile()) {
            seed("share " + commandPath(previewFile))
        } else {
            seed("share ")
        }
    }

    private fun toggleDirectory(directory: File?) {
        if (directory == null || !directory.exists()) return
        if (!directory.isDirectory()) {
            openFile(directory)
            return
        }
        val key = pathKey(directory)
        if (expandedPaths.contains(key)) {
            collapseDirectory(key)
        } else {
            expandedPaths.add(key)
        }
        renderListing((if (activeTreeOptions == null) TreeOptions.Companion.defaultListing() else activeTreeOptions)!!)
    }

    private fun collapseDirectory(key: kotlin.String?) {
        val removals = ArrayList<kotlin.String?>()
        for (path in expandedPaths) {
            if (path == key || path.startsWith(key + File.separator)) {
                removals.add(path)
            }
        }
        expandedPaths.removeAll(removals)
    }

    private fun shareFile(target: kotlin.String?) {
        val file = resolve(target)
        if (file == null || !file.exists() || file.isDirectory()) {
            preserveInputAfterCommand = true
            showTerminalPopup("share", "Not a file: " + target)
            return
        }
        val uri = uriFor(file)
        val intent = Intent(Intent.ACTION_SEND)
        intent.setType(mimeFor(file))
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share with"))
        print("Sharing: " + file.getName())
    }

    private fun shareFile(file: File?) {
        if (file == null) return
        shareFile(file.getAbsolutePath())
    }

    private fun runPreview(command: kotlin.String) {
        val args = splitArgs(command)
        if (args.size < 2) {
            preserveInputAfterCommand = true
            showTerminalPopup("preview", "preview: usage: preview [file]")
            return
        }
        val file = resolve(args.get(1))
        if (file == null || !file.exists()) {
            preserveInputAfterCommand = true
            showTerminalPopup("preview", "Not found: " + args.get(1))
            return
        }
        previewResolvedFile(file)
    }

    private fun runEdit(command: kotlin.String) {
        val args = splitArgs(command)
        if (args.size < 2) {
            preserveInputAfterCommand = true
            showTerminalPopup("edit", "edit: usage: edit [text file]")
            return
        }
        val file = resolve(args.get(1))
        if (file == null || !file.exists() || file.isDirectory()) {
            preserveInputAfterCommand = true
            showTerminalPopup("edit", "Not a text file: " + args.get(1))
            return
        }
        if (!isLikelyText(file)) {
            preserveInputAfterCommand = true
            showTerminalPopup("edit", "edit: supported for text-like files only")
            return
        }
        showTextEditor(file)
    }

    private fun previewResolvedFile(file: File?) {
        if (file == null || !file.exists()) return
        previewFile = file
        highlightedPreviewFile = file
        refreshTreeSelection()
        val ext = extension(file.getName())
        if (file.isDirectory()) {
            previewDirectory(file)
        } else if (isType(file, "image")) {
            previewImage(file)
        } else if ("zip" == ext) {
            previewZip(file)
        } else if ("json" == ext) {
            previewJson(file)
        } else if ("csv" == ext) {
            previewCsv(file)
        } else if ("md" == ext) {
            previewMarkdown(file)
        } else if (isOneOf(ext, "txt", "log")) {
            previewPlainText(file, "content")
        } else if (isLikelyText(file)) {
            previewText(file)
        } else {
            previewUnsupported(file)
        }
    }

    private fun previewDirectory(directory: File) {
        val children = directory.listFiles()
        if (children == null) {
            printPreview("Cannot read: " + directory.getAbsolutePath())
            return
        }
        var dirs = 0
        var files = 0
        var bytes: kotlin.Long = 0
        for (child in children) {
            if (child.isDirectory()) dirs++
            else {
                files++
                bytes += max(0, child.length())
            }
        }
        printPreview(
            previewHeader(directory) + "\n\nsummary:\nchildren: " + dirs + " dirs, " + files + " files\nfile bytes: " + humanSize(
                bytes
            )
        )
    }

    private fun previewZip(file: File?) {
        val out = StringBuilder(previewHeader(file)).append("\n\narchive:")
        var count = 0
        try {
            ZipInputStream(FileInputStream(file)).use { zip ->
                var entry: ZipEntry?
                while ((zip.getNextEntry()
                        .also { entry = it }) != null && count < PREVIEW_TEXT_LIMIT
                ) {
                    out.append('\n').append(entry!!.getName())
                    count++
                }
            }
        } catch (e: Exception) {
            out.append("\n").append(e.message)
        }
        printPreview(out.toString())
    }

    private fun previewText(file: File) {
        previewPlainText(file, "content")
    }

    private fun previewPlainText(file: File, sectionLabel: kotlin.String?) {
        val out = StringBuilder(previewHeader(file))
        appendPreviewScope(out, file)
        out.append("\n\n").append(sectionLabel).append(":\n")
        try {
            val text = readBoundedText(file, PREVIEW_MAX_BYTES)
            appendNumberedLines(out, text.text, PREVIEW_TEXT_LIMIT)
            appendTruncationNotice(out, text, PREVIEW_TEXT_LIMIT)
        } catch (e: Exception) {
            out.append(e.message)
        }
        printPreview(out)
    }

    private fun previewMarkdown(file: File) {
        val out = StringBuilder(previewHeader(file))
        appendPreviewScope(out, file)
        try {
            val text = readBoundedText(file, PREVIEW_MAX_BYTES)
            out.append("\n\nmarkdown outline:")
            appendMarkdownOutline(out, text.text)
            out.append("\n\nrendered text:\n")
            appendMarkdownLines(out, text.text)
            appendTruncationNotice(out, text, PREVIEW_TEXT_LIMIT)
        } catch (e: Exception) {
            out.append("\n\nmarkdown:\n").append(e.message)
        }
        printPreview(out)
    }

    private fun previewJson(file: File) {
        val out = StringBuilder(previewHeader(file))
        out.append("\n\njson:")
        try {
            JsonReader(
                InputStreamReader(
                    FileInputStream(file),
                    StandardCharsets.UTF_8
                )
            ).use { reader ->
                reader.setLenient(true)
                val state = JsonPreviewState()
                appendJsonValue(reader, out, state, 0, null)
                if (state.limited) out.append("\n... preview stopped after ")
                    .append(PREVIEW_JSON_NODES).append(" nodes")
            }
        } catch (limit: PreviewLimitReached) {
            out.append("\n... preview stopped after ").append(PREVIEW_JSON_NODES).append(" nodes")
        } catch (e: Exception) {
            out.append("\nparse error: ").append(e.message).append("\n\nraw excerpt:\n")
            try {
                val text = readBoundedText(file, PREVIEW_MAX_BYTES)
                appendNumberedLines(out, text.text, PREVIEW_TEXT_LIMIT)
                appendTruncationNotice(out, text, PREVIEW_TEXT_LIMIT)
            } catch (readError: Exception) {
                out.append(readError.message)
            }
        }
        printPreview(out)
    }

    private fun previewCsv(file: File) {
        val out = StringBuilder(previewHeader(file))
        appendPreviewScope(out, file)
        try {
            val text = readBoundedText(file, PREVIEW_MAX_BYTES)
            val csv = parseCsvPreview(text.text)
            out.append("\n\ncsv table:")
            out.append("\nrows shown: ").append(csv.rows.size)
            out.append("\ncolumns shown: ").append(csv.columnCount)
            if (csv.truncatedColumns) out.append(" (wide rows capped)")
            out.append("\n\n")
            appendCsvTable(out, csv)
            if (csv.truncatedRows || text.truncated) {
                out.append("...\npreview capped at ")
                    .append(PREVIEW_CSV_ROWS)
                    .append(" rows and ")
                    .append(humanSize(PREVIEW_MAX_BYTES.toLong()))
                    .append(".\n")
            }
        } catch (e: Exception) {
            out.append("\n\ncsv:\n").append(e.message)
        }
        printPreview(out)
    }

    private fun previewUnsupported(file: File?) {
        val out = StringBuilder(previewHeader(file))
        out.append("\n\npreview:\nNo read-only preview for this format inside FM.")
        out.append("\nSupported text previews: .md, .txt, .log, .json, .csv.")
        out.append("\nUse open/share for platform handlers.")
        printPreview(out)
    }

    @Throws(Exception::class)
    private fun readBoundedText(file: File, maxBytes: Int): BoundedText {
        val limit = min(max(0, file.length()), maxBytes.toLong()).toInt()
        val buffer = ByteArray(limit)
        var total = 0
        FileInputStream(file).use { `in` ->
            while (total < buffer.size) {
                val read = `in`.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
        }
        val truncated = file.length() > total
        return BoundedText(String(buffer, 0, total, StandardCharsets.UTF_8), total, truncated)
    }

    private fun appendPreviewScope(out: StringBuilder, file: File) {
        out.append("\npreview: read-only, capped at ")
            .append(humanSize(PREVIEW_MAX_BYTES.toLong()))
            .append(" / ")
            .append(PREVIEW_TEXT_LIMIT)
            .append(" lines")
        if (file.length() > PREVIEW_MAX_BYTES) out.append(" (large file)")
    }

    private fun appendNumberedLines(out: StringBuilder, text: kotlin.String?, maxLines: Int) {
        val lines = previewLines(text)
        val count = min(lines.size, maxLines)
        for (i in 0..<count) {
            out.append(kotlin.String.format(Locale.US, "%3d ", i + 1)).append(lines[i]).append('\n')
        }
    }

    private fun appendTruncationNotice(out: StringBuilder, text: BoundedText, maxLines: Int) {
        val lineCount = previewLines(text.text).size
        if (lineCount > maxLines || text.truncated) {
            out.append("...\npreview capped at ")
                .append(maxLines)
                .append(" lines and ")
                .append(humanSize(PREVIEW_MAX_BYTES.toLong()))
                .append(".\n")
        }
    }

    private fun previewLines(text: kotlin.String?): Array<kotlin.String> {
        val clean = if (text == null) "" else text.replace("\r\n", "\n").replace('\r', '\n')
        return clean.split("\n".toRegex()).toTypedArray()
    }

    private fun appendMarkdownOutline(out: StringBuilder, text: kotlin.String?) {
        val lines = previewLines(text)
        var count = 0
        for (line in lines) {
            val trimmed = line.trim { it <= ' ' }
            if (!trimmed.startsWith("#")) continue
            var level = 0
            while (level < trimmed.length && trimmed.get(level) == '#') level++
            if (level == 0 || level > 6 || level >= trimmed.length || trimmed.get(level) != ' ') continue
            out.append('\n')
            for (i in 1..<level) out.append("  ")
            out.append("- ").append(trimmed.substring(level).trim { it <= ' ' })
            count++
            if (count >= 16) {
                out.append("\n  ...")
                break
            }
        }
        if (count == 0) out.append("\n(no headings in preview window)")
    }

    private fun appendMarkdownLines(out: StringBuilder, text: kotlin.String?) {
        val lines = previewLines(text)
        val count = min(lines.size, PREVIEW_TEXT_LIMIT)
        var codeBlock = false
        for (i in 0..<count) {
            val line = lines[i]
            val trimmed = line.trim { it <= ' ' }
            if (trimmed.startsWith("```")) {
                codeBlock = !codeBlock
                out.append(kotlin.String.format(Locale.US, "%3d ", i + 1)).append(trimmed)
                    .append('\n')
            } else if (!codeBlock && trimmed.startsWith("#")) {
                out.append(kotlin.String.format(Locale.US, "%3d ", i + 1))
                    .append(trimmed.replaceFirst("^#{1,6}\\s*".toRegex(), "")).append('\n')
            } else {
                out.append(kotlin.String.format(Locale.US, "%3d ", i + 1)).append(line).append('\n')
            }
        }
    }

    @Throws(Exception::class)
    private fun appendJsonValue(
        reader: JsonReader,
        out: StringBuilder,
        state: JsonPreviewState,
        depth: Int,
        label: kotlin.String?
    ) {
        if (state.nodes++ >= PREVIEW_JSON_NODES || depth > PREVIEW_JSON_DEPTH) {
            state.limited = true
            throw PreviewLimitReached()
        }
        val token = reader.peek()
        appendJsonIndent(out, depth)
        if (label != null) out.append(label).append(": ")
        when (token) {
            JsonToken.BEGIN_OBJECT -> {
                out.append("{\n")
                reader.beginObject()
                while (reader.hasNext()) appendJsonValue(
                    reader,
                    out,
                    state,
                    depth + 1,
                    reader.nextName()
                )
                reader.endObject()
                appendJsonIndent(out, depth)
                out.append("}\n")
            }

            JsonToken.BEGIN_ARRAY -> {
                out.append("[\n")
                reader.beginArray()
                var index = 0
                while (reader.hasNext()) appendJsonValue(
                    reader,
                    out,
                    state,
                    depth + 1,
                    "[" + index++ + "]"
                )
                reader.endArray()
                appendJsonIndent(out, depth)
                out.append("]\n")
            }

            JsonToken.STRING -> out.append('"').append(trimPreviewValue(reader.nextString(), 160))
                .append('"').append('\n')

            JsonToken.NUMBER -> out.append(reader.nextString()).append('\n')
            JsonToken.BOOLEAN -> out.append(reader.nextBoolean()).append('\n')
            JsonToken.NULL -> {
                reader.nextNull()
                out.append("null\n")
            }

            else -> {
                reader.skipValue()
                out.append("(skipped)\n")
            }
        }
    }

    private fun appendJsonIndent(out: StringBuilder, depth: Int) {
        for (i in 0..<depth) out.append("  ")
    }

    private fun trimPreviewValue(value: kotlin.String?, max: Int): kotlin.String {
        if (value == null) return ""
        val clean = value.replace('\n', ' ').replace('\r', ' ')
        return if (clean.length <= max) clean else clean.substring(0, max) + "..."
    }

    private fun parseCsvPreview(text: kotlin.String): CsvPreview {
        val preview = CsvPreview()
        var row = ArrayList<kotlin.String?>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text.get(i)
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text.get(i + 1) == '"') {
                        appendCsvCellChar(cell, '"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    appendCsvCellChar(cell, c)
                }
            } else if (c == '"' && cell.length == 0) {
                quoted = true
            } else if (c == ',') {
                addCsvCell(preview, row, cell)
            } else if (c == '\n') {
                addCsvCell(preview, row, cell)
                addCsvRow(preview, row)
                if (preview.rows.size >= PREVIEW_CSV_ROWS) {
                    preview.truncatedRows = i + 1 < text.length
                    return preview
                }
                row = ArrayList<kotlin.String?>()
            } else if (c != '\r') {
                appendCsvCellChar(cell, c)
            }
            i++
        }
        if (cell.length > 0 || !row.isEmpty()) {
            addCsvCell(preview, row, cell)
            addCsvRow(preview, row)
        }
        return preview
    }

    private fun appendCsvCellChar(cell: StringBuilder, c: Char) {
        if (cell.length < PREVIEW_CELL_CHARS) cell.append(c)
    }

    private fun addCsvCell(
        preview: CsvPreview,
        row: ArrayList<kotlin.String?>,
        cell: StringBuilder
    ) {
        if (row.size < PREVIEW_MAX_COLUMNS) {
            row.add(trimPreviewValue(cell.toString(), PREVIEW_CELL_CHARS))
        } else {
            preview.truncatedColumns = true
        }
        cell.setLength(0)
    }

    private fun addCsvRow(preview: CsvPreview, row: ArrayList<kotlin.String?>) {
        preview.columnCount = max(preview.columnCount, row.size)
        if (preview.rows.size < PREVIEW_CSV_ROWS) preview.rows.add(row)
        else preview.truncatedRows = true
    }

    private fun appendCsvTable(out: StringBuilder, csv: CsvPreview) {
        if (csv.rows.isEmpty()) {
            out.append("(empty)\n")
            return
        }
        val widths = IntArray(max(1, csv.columnCount))
        for (row in csv.rows) {
            var i = 0
            while (i < widths.size && i < row.size) {
                widths[i] = min(24, max(widths[i], row.get(i)!!.length))
                i++
            }
        }
        for (rowIndex in csv.rows.indices) {
            val row = csv.rows.get(rowIndex)
            out.append(kotlin.String.format(Locale.US, "%3d ", rowIndex + 1))
            for (i in widths.indices) {
                if (i > 0) out.append(" | ")
                val value = if (i < row.size) row.get(i) else ""
                out.append(padRight(trimPreviewValue(value, widths[i]), widths[i]))
            }
            out.append('\n')
        }
    }

    private fun padRight(value: kotlin.String?, width: Int): kotlin.String {
        val clean = if (value == null) "" else value
        val out = StringBuilder(clean)
        while (out.length < width) out.append(' ')
        return out.toString()
    }

    private fun showTextEditor(file: File?) {
        if (file == null || previewEditorView == null) {
            previewTextAsOutput(file)
            return
        }
        var text = ""
        val truncated = file.length() > EDITOR_TEXT_MAX_BYTES
        try {
            FileInputStream(file).use { `in` ->
                val buffer = ByteArray(min(file.length(), EDITOR_TEXT_MAX_BYTES.toLong()).toInt())
                val read = `in`.read(buffer)
                if (read > 0) text = String(buffer, 0, read, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            print(previewHeader(file) + "\n\nerror:\n" + e.message)
            return
        }
        commandOutputVisible = true
        previewFile = file
        editorFile = if (truncated) null else file
        highlightedPreviewFile = file
        setRailsVisible(true)
        setPreviewTitle("EDITOR")
        if (outputView != null) {
            outputView!!.setVisibility(View.VISIBLE)
            outputView!!.setText(
                previewHeader(file) + (if (truncated) "\n\neditor:\nLarge file preview is read-only and capped at " + humanSize(
                    EDITOR_TEXT_MAX_BYTES.toLong()
                ) + "." else "\n\neditor:\nEditing")
            )
        }
        if (fileRowsView != null) {
            fileRowsView!!.removeAllViews()
            fileRowsView!!.setVisibility(View.GONE)
        }
        if (previewImageView != null) {
            previewImageView!!.setImageDrawable(null)
            previewImageView!!.setVisibility(View.GONE)
        }
        previewEditorView!!.setVisibility(View.VISIBLE)
        previewEditorView!!.setEnabled(!truncated)
        previewEditorView!!.setText(text)
        previewEditorView!!.setSelection(0)
        if (previewActionsView != null) previewActionsView!!.setVisibility(if (truncated) View.GONE else View.VISIBLE)
        if (outputScroll != null) outputScroll!!.post(Runnable { outputScroll!!.fullScroll(View.FOCUS_UP) })
    }

    private fun previewTextAsOutput(file: File?) {
        if (file == null) return
        previewPlainText(file, "content")
    }

    private fun previewImage(file: File) {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        val info = StringBuilder(previewHeader(file))
        if (width > 0 && height > 0) info.append("\n\nimage:\ndimensions: ").append(width)
            .append(" x ").append(height)

        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize(width, height, 1600)
        val bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options)
        if (bitmap == null) {
            print(
                info.append("\n\nCould not decode image preview.\nUse: open ")
                    .append(quoteIfNeeded(file.getName()))
            )
            return
        }
        showImagePreview(info.toString(), bitmap)
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2
        return max(1, sample)
    }

    private fun showImagePreview(text: kotlin.String?, bitmap: Bitmap?) {
        commandOutputVisible = true
        setRailsVisible(this.isLandscapeLayout)
        editorFile = null
        setPreviewTitle("PREVIEW")
        if (outputView != null) {
            outputView!!.setVisibility(View.VISIBLE)
            outputView!!.setText(if (text == null) "" else text)
        }
        if (fileRowsView != null) {
            fileRowsView!!.removeAllViews()
            fileRowsView!!.setVisibility(View.GONE)
        }
        hidePreviewEditor()
        if (previewImageView != null) {
            previewImageView!!.setImageBitmap(bitmap)
            previewImageView!!.setVisibility(View.VISIBLE)
            previewImageView!!.setMaxHeight(
                max(
                    dp(160),
                    if (outputScroll == null) dp(420) else outputScroll!!.getHeight() - dp(120)
                )
            )
        }
        if (outputScroll != null) outputScroll!!.post(Runnable { outputScroll!!.fullScroll(View.FOCUS_UP) })
    }

    private fun fileInfo(file: File): kotlin.String {
        val out = StringBuilder()
        out.append(if (file.isDirectory()) "directory: " else "file: ").append(file.getName())
        out.append('\n').append(file.getAbsolutePath())
        if (!file.isDirectory()) out.append("\nsize: ").append(humanSize(file.length()))
            .append(" (").append(file.length()).append(" bytes)")
        out.append("\nmodified: ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified()))
        out.append("\ntype: ").append(if (file.isDirectory()) "directory" else mimeFor(file))
        return out.toString()
    }

    private fun previewHeader(file: File?): kotlin.String {
        if (file == null) return ""
        val out = StringBuilder()
        out.append(pathKey(file))
        out.append("\n\nmeta:\n")
        out.append("name: ")
            .append(if (file.getName().length == 0) pathKey(file) else file.getName())
        if (!file.isDirectory()) out.append("\nsize: ").append(humanSize(file.length()))
            .append(" (").append(file.length()).append(" bytes)")
        out.append("\nmodified: ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(file.lastModified()))
        out.append("\ntype: ").append(if (file.isDirectory()) "directory" else mimeFor(file))
        return out.toString()
    }

    private fun isLikelyText(file: File): Boolean {
        val ext = extension(file.getName())
        return isOneOf(
            ext,
            "txt",
            "md",
            "json",
            "xml",
            "html",
            "css",
            "js",
            "ts",
            "java",
            "kt",
            "kts",
            "lua",
            "sh",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "gradle",
            "yml",
            "yaml",
            "log",
            "csv"
        )
    }

    private fun makeDirectory(name: kotlin.String?) {
        val dir = resolve(name)
        if (dir == null) {
            showTerminalPopup("mkdir", "Could not create: " + name)
            return
        }
        if (dir.exists()) {
            showTerminalPopup("mkdir", "mkdir: already exists: " + dir.getAbsolutePath())
            return
        }
        if (dir.mkdirs()) {
            renderListing()
            showTerminalPopup("mkdir", "created directory: " + dir.getAbsolutePath())
        } else {
            showTerminalPopup("mkdir", "Could not create: " + name)
        }
    }

    private fun remove(target: kotlin.String?) {
        runShellFileCommand("rm " + target)
    }

    private fun runShellFileCommand(command: kotlin.String) {
        val args = splitArgs(command)
        if (args.isEmpty()) return
        val verb = args.get(0).lowercase()
        if ("mkdir" == verb) {
            runMkdir(args)
        } else if ("rm" == verb) {
            runRemove(args)
        } else if ("cp" == verb) {
            runCopyMove(args, false)
        } else if ("mv" == verb) {
            runCopyMove(args, true)
        } else if ("zip" == verb) {
            runZip(args)
        }
    }

    private fun runFavoriteCommand(args: MutableList<kotlin.String>?) {
        if (args == null || args.size < 2 || "list".equals(args.get(1), ignoreCase = true)) {
            showFavoritesInTerminal()
            return
        }
        val op = args.get(1).lowercase()
        if ("here" == op) {
            addFavoriteFromArgs(args)
        } else if ("add" == op) {
            addFavoriteFromArgs(args)
        } else if ("go" == op || "cd" == op || "open" == op) {
            if (args.size < 3) {
                preserveInputAfterCommand = true
                print("fav " + op + " requires a label\n\n" + favoriteUsageText())
                return
            }
            goFavorite(args.get(2))
        } else if ("rm" == op || "remove" == op || "del" == op) {
            if (args.size < 3) {
                preserveInputAfterCommand = true
                print("fav rm requires a label\n\n" + favoriteUsageText())
                return
            }
            removeFavorite(args.get(2))
        } else if ("rename" == op) {
            if (args.size < 4) {
                preserveInputAfterCommand = true
                print("fav rename requires old and new labels\n\n" + favoriteUsageText())
                return
            }
            renameFavorite(args.get(2), args.get(3))
        } else {
            preserveInputAfterCommand = true
            val match = findFavorite(op)
            if (match != null) {
                goFavorite(op)
            } else {
                print(favoriteUsageText())
            }
        }
    }

    private fun addFavoriteFromArgs(args: MutableList<kotlin.String>) {
        var target = currentDirectory
        var label: kotlin.String? = null
        if (args.size == 3) {
            val candidate = resolve(args.get(2))
            if (candidate != null && candidate.exists() && candidate.isDirectory()) {
                target = candidate
            } else {
                label = args.get(2)
            }
        } else if (args.size >= 4) {
            label = args.get(2)
            target = resolve(args.get(3))
        }
        if (target == null || !target.exists() || !target.isDirectory()) {
            preserveInputAfterCommand = true
            print("favorite path is not a directory\n\n" + favoriteUsageText())
            return
        }
        val favorites = loadFavorites()
        var cleanLabel = normalizeFavoriteLabel(
            if (label == null) autoFavoriteLabel(
                target,
                favorites
            ) else label
        )
        if (cleanLabel.length == 0) cleanLabel = autoFavoriteLabel(target, favorites)
        upsertFavorite(favorites, FavoritePath(cleanLabel, pathKey(target)))
        saveFavorites(favorites)
        buildPinnedRail()
        showFavoritesInTerminal("favorite added: " + cleanLabel + "\n" + pathKey(target))
    }

    private fun removeFavorite(label: kotlin.String?) {
        val key = normalizeFavoriteLabel(label)
        val favorites = loadFavorites()
        var removed = false
        for (i in favorites.indices.reversed()) {
            if (favorites.get(i).label.equals(key, ignoreCase = true)) {
                favorites.removeAt(i)
                removed = true
            }
        }
        if (!removed) {
            preserveInputAfterCommand = true
            print("favorite not found: " + label + "\n\n" + favoriteListText())
            return
        }
        saveFavorites(favorites)
        buildPinnedRail()
        showFavoritesInTerminal("favorite removed: " + key)
    }

    private fun renameFavorite(oldLabel: kotlin.String?, newLabel: kotlin.String?) {
        val oldKey = normalizeFavoriteLabel(oldLabel)
        val newKey = normalizeFavoriteLabel(newLabel)
        if (newKey.length == 0) {
            preserveInputAfterCommand = true
            print("new label is empty\n\n" + favoriteUsageText())
            return
        }
        val favorites = loadFavorites()
        for (favorite in favorites) {
            if (favorite.label.equals(oldKey, ignoreCase = true)) {
                favorite.label = newKey
                saveFavorites(favorites)
                buildPinnedRail()
                showFavoritesInTerminal("favorite renamed: " + oldKey + " -> " + newKey)
                return
            }
        }
        preserveInputAfterCommand = true
        print("favorite not found: " + oldLabel + "\n\n" + favoriteListText())
    }

    private fun goFavorite(label: kotlin.String?) {
        val favorite = findFavorite(label)
        if (favorite == null) {
            preserveInputAfterCommand = true
            print("favorite not found: " + label + "\n\n" + favoriteListText())
            return
        }
        changeDirectory(favorite.path)
    }

    private fun findFavorite(label: kotlin.String?): FavoritePath? {
        val key = normalizeFavoriteLabel(label)
        for (favorite in loadFavorites()) {
            if (favorite.label.equals(key, ignoreCase = true)) return favorite
        }
        return null
    }

    private fun runZip(args: MutableList<kotlin.String>) {
        var recursive = false
        val operands = ArrayList<kotlin.String>()
        for (i in 1..<args.size) {
            val arg: kotlin.String? = args.get(i)
            if ("-r" == arg || "-R" == arg || "--recursive" == arg) recursive = true
            else operands.add(arg!!)
        }
        if (operands.size < 2) {
            preserveInputAfterCommand = true
            showTerminalPopup("zip", "zip: usage: zip -r archive_name.zip directory_name")
            return
        }
        val archive = resolve(operands.removeAt(0))
        if (archive == null) {
            preserveInputAfterCommand = true
            showTerminalPopup("zip", "zip: invalid archive path")
            return
        }
        if (archive.exists() && archive.isDirectory()) {
            preserveInputAfterCommand = true
            showTerminalPopup(
                "zip",
                "zip: archive path is a directory:\n" + archive.getAbsolutePath() + "\n\nUse: zip -r archive_name.zip directory_name"
            )
            return
        }
        val sources = expandSourceArgs(operands)
        if (sources.isEmpty()) {
            preserveInputAfterCommand = true
            showTerminalPopup("zip", "zip: no matches")
            return
        }
        for (source in sources) {
            if (source.file!!.isDirectory() && !recursive && !source.copyContents) {
                preserveInputAfterCommand = true
                showTerminalPopup(
                    "zip",
                    "zip: directory requires -r:\n" + source.file.getAbsolutePath()
                )
                return
            }
        }
        val plan = buildZipPlan(archive, sources, recursive)
        if (plan.error != null) {
            preserveInputAfterCommand = true
            showTerminalPopup("zip", plan.error)
            return
        }
        val start = Runnable { startZipOperation(plan) }
        if (archive.exists()) {
            confirmTerminal(
                "overwrite existing archive?\n" + archive.getAbsolutePath(),
                "zip",
                start
            )
        } else {
            start.run()
        }
    }

    private fun runMkdir(args: MutableList<kotlin.String>) {
        var parents = false
        val paths = ArrayList<kotlin.String?>()
        for (i in 1..<args.size) {
            val arg: kotlin.String? = args.get(i)
            if ("-p" == arg) parents = true
            else paths.add(arg)
        }
        if (paths.isEmpty()) {
            showTerminalPopup("mkdir", "mkdir: missing operand")
            return
        }
        val result = OperationResult()
        for (path in paths) {
            val dir = resolve(path)
            if (dir == null) {
                result.failures.add(path + ": invalid path")
                continue
            }
            if (dir.exists()) {
                if (dir.isDirectory() && parents) result.skipped++
                else result.failures.add(dir.getAbsolutePath() + ": already exists")
                continue
            }
            if (dir.mkdirs()) result.created++
            else result.failures.add(dir.getAbsolutePath() + ": could not create")
        }
        renderListing()
        showTerminalPopup(
            "mkdir",
            result.summary("created directories", "deleted files", "copied files")
        )
    }

    private fun runRemove(args: MutableList<kotlin.String>) {
        var recursive = false
        var permanent = false
        val patterns = ArrayList<kotlin.String>()
        for (i in 1..<args.size) {
            val arg: kotlin.String? = args.get(i)
            if ("-r" == arg || "-R" == arg || "--recursive" == arg) recursive = true
            else if ("--permanent" == arg || "--delete" == arg) permanent = true
            else patterns.add(arg!!)
        }
        if (patterns.isEmpty()) {
            showTerminalPopup("rm", "rm: missing operand")
            return
        }
        val targets = expandTargetArgs(patterns, true)
        if (targets.isEmpty()) {
            showTerminalPopup("rm", "rm: no matches")
            return
        }
        for (target in targets) {
            if (target.isDirectory() && !recursive) {
                showTerminalPopup(
                    "rm",
                    "rm: cannot remove directory without -r:\n" + target.getAbsolutePath()
                )
                return
            }
        }
        val blocked = firstBlockedActionTarget(targets, "remove")
        if (blocked != null) {
            showTerminalPopup("rm", blocked)
            return
        }
        if (!permanent) {
            confirmTerminal(
                trashConfirmationMessage("rm", targets),
                "rm",
                Runnable { performTrashTargets(targets, "rm") })
            return
        }
        for (target in targets) {
            if (target.isDirectory()) {
                showTerminalPopup(
                    "rm",
                    "rm --permanent refuses directories.\nUse rm -r to move a folder to .retui-trash, then trash empty when you are sure."
                )
                return
            }
        }
        val plan = buildDeletePlan(targets)
        if (plan.items.isEmpty()) {
            showTerminalPopup("rm", "rm: nothing to delete")
            return
        }
        confirmTerminal(
            permanentDeleteConfirmationMessage(targets, plan),
            "rm",
            Runnable { startDeleteOperation(plan) })
    }

    private fun runCopyMove(args: MutableList<kotlin.String>, move: Boolean) {
        var recursive = move
        val operands = ArrayList<kotlin.String>()
        for (i in 1..<args.size) {
            val arg: kotlin.String? = args.get(i)
            if ("-r" == arg || "-R" == arg || "--recursive" == arg) recursive = true
            else operands.add(arg!!)
        }
        if (operands.size < 2) {
            showTerminalPopup(
                if (move) "mv" else "cp",
                (if (move) "mv" else "cp") + ": missing operand"
            )
            return
        }
        val destArg = operands.removeAt(operands.size - 1)
        val destination = resolve(destArg)
        val destAsDirectory = destArg.endsWith("/") || destArg.endsWith(File.separator)
        val sources = expandSourceArgs(operands)
        if (sources.isEmpty()) {
            showTerminalPopup(if (move) "mv" else "cp", (if (move) "mv" else "cp") + ": no matches")
            return
        }
        if (sources.size > 1 && (destination == null || (destination.exists() && !destination.isDirectory()))) {
            showTerminalPopup(
                if (move) "mv" else "cp",
                (if (move) "mv" else "cp") + ": destination must be directory for multiple sources"
            )
            return
        }
        for (source in sources) {
            if (source.file!!.isDirectory() && !recursive && !source.copyContents) {
                showTerminalPopup(
                    "cp",
                    "cp: omitting directory:\n" + source.file.getAbsolutePath()
                )
                return
            }
        }
        val invalidTarget = firstInvalidCopyMoveTarget(sources, destination, move)
        if (invalidTarget != null) {
            showTerminalPopup(if (move) "mv" else "cp", invalidTarget)
            return
        }
        val plan = buildCopyPlan(sources, destination, recursive, move, destAsDirectory)
        if (plan.error != null) {
            showTerminalPopup(if (move) "mv" else "cp", plan.error)
            return
        }
        val start = Runnable { startCopyMoveOperation(plan) }
        if (move || plan.overwriteCount > 0 || containsDirectorySource(sources)) {
            confirmTerminal(
                copyMoveConfirmationMessage(
                    if (move) "mv" else "cp",
                    sources,
                    destination!!,
                    plan
                ), if (move) "mv" else "cp", start
            )
        } else {
            start.run()
        }
    }

    private fun containsDirectorySource(sources: MutableList<SourceSpec>?): Boolean {
        if (sources == null) return false
        for (source in sources) {
            if (source != null && source.file != null && source.file.isDirectory()) return true
        }
        return false
    }

    private fun firstInvalidCopyMoveTarget(
        sources: MutableList<SourceSpec>?,
        destination: File?,
        move: Boolean
    ): kotlin.String? {
        if (sources == null || destination == null) return null
        for (source in sources) {
            if (source == null || source.file == null) continue
            if (samePath(source.file, destination)) {
                return (if (move) "mv" else "cp") + ": source and destination are the same:\n" + pathKey(
                    source.file
                )
            }
            if (move && source.file.isDirectory() && isPathWithin(destination, source.file)) {
                return ("mv: destination is inside the source directory:\nsource: "
                        + pathKey(source.file)
                        + "\ndestination: "
                        + pathKey(destination))
            }
        }
        return null
    }

    private fun copyMoveConfirmationMessage(
        command: kotlin.String?,
        sources: MutableList<SourceSpec>?,
        destination: File,
        plan: CopyPlan
    ): kotlin.String {
        val verb = if ("mv" == command) "Move" else "Copy"
        val out = StringBuilder()
        out.append(verb).append(" with confirmation")
        out.append("\n\ndestination:\n").append(pathKey(destination))
        out.append("\n\nsources:")
        appendSourceList(out, sources, 8)
        out.append("\n\nplanned:")
        out.append("\nfiles: ").append(plan.items.size)
        out.append("\ndirectories: ").append(plan.directories.size)
        out.append("\nbytes: ").append(humanSize(plan.totalBytes))
        if (plan.overwriteCount > 0) out.append("\nwill overwrite: ").append(plan.overwriteCount)
            .append(" existing file(s)")
        if (plan.move) out.append("\nsource files are removed after the copy completes successfully.")
        return out.toString()
    }

    private fun trashConfirmationMessage(
        command: kotlin.String?,
        targets: MutableList<File>?
    ): kotlin.String {
        val out = StringBuilder()
        out.append("Move to FM trash/quarantine?")
        out.append("\n\nquarantine:\n").append(pathKey(trashDirectory()))
        out.append("\n\nsources:")
        appendFileList(out, targets, 10)
        if ("rm" == command) {
            out.append("\n\nrm is guarded here: this will not permanently delete the source.")
        }
        out.append("\nRestore later with: restore [label|all]")
        return out.toString()
    }

    private fun permanentDeleteConfirmationMessage(
        targets: MutableList<File>?,
        plan: DeletePlan
    ): kotlin.String {
        val out = StringBuilder()
        out.append("Permanently delete file(s)?")
        out.append("\n\nThis bypasses .retui-trash and cannot be restored by FM.")
        out.append("\n\nsources:")
        appendFileList(out, targets, 10)
        out.append("\n\nplanned files: ").append(plan.items.size)
        return out.toString()
    }

    private fun appendSourceList(
        out: StringBuilder,
        sources: MutableList<SourceSpec>?,
        limit: Int
    ) {
        if (sources == null || sources.isEmpty()) {
            out.append("\n(none)")
            return
        }
        var count = 0
        for (source in sources) {
            if (source == null || source.file == null) continue
            if (count >= limit) break
            out.append('\n').append(pathKey(source.file))
            if (source.copyContents) out.append(File.separator).append(".")
            count++
        }
        if (sources.size > count) out.append("\n... +").append(sources.size - count).append(" more")
    }

    private fun appendFileList(out: StringBuilder, files: MutableList<File>?, limit: Int) {
        if (files == null || files.isEmpty()) {
            out.append("\n(none)")
            return
        }
        var count = 0
        for (file in files) {
            if (file == null) continue
            if (count >= limit) break
            out.append('\n').append(pathKey(file))
            count++
        }
        if (files.size > count) out.append("\n... +").append(files.size - count).append(" more")
    }

    private fun firstBlockedActionTarget(
        targets: MutableList<File>?,
        action: kotlin.String?
    ): kotlin.String? {
        if (targets == null) return null
        val root = sharedStorageRoot()
        val trash = trashDirectory()
        for (target in targets) {
            if (target == null) continue
            val targetPath = pathKey(target)
            if (File.separator == targetPath) {
                return "Refusing to " + action + " filesystem root."
            }
            if (root != null && samePath(target, root)) {
                return "Refusing to " + action + " shared storage root:\n" + pathKey(root)
            }
            if (samePath(target, trash) || isPathWithin(trash, target)) {
                return "Refusing to " + action + " the FM trash/quarantine store:\n" + pathKey(trash)
            }
            if (target.isDirectory() && currentDirectory != null && isPathWithin(
                    currentDirectory,
                    target
                )
            ) {
                return "Refusing to " + action + " the current directory or one of its parents:\n" + targetPath
            }
        }
        return null
    }

    private fun samePath(a: File?, b: File?): Boolean {
        return a != null && b != null && pathKey(a) == pathKey(b)
    }

    private fun isPathWithin(child: File?, ancestor: File?): Boolean {
        if (child == null || ancestor == null) return false
        val childPath = pathKey(child)
        val ancestorPath = pathKey(ancestor)
        if (childPath == ancestorPath) return true
        val prefix =
            if (ancestorPath.endsWith(File.separator)) ancestorPath else ancestorPath + File.separator
        return childPath.startsWith(prefix)
    }

    private fun runSelectionCommand(args: MutableList<kotlin.String>?) {
        if (args == null || args.isEmpty()) return
        val verb = args.get(0).lowercase()
        val op =
            if ("sel" == verb) (if (args.size > 1) args.get(1).lowercase() else "list") else verb
        val start = if ("sel" == verb) 2 else 1
        if ("add" == op || "mark" == op) {
            addSelection(args.subList(start, args.size))
        } else if ("rm" == op || "remove" == op || "unmark" == op) {
            removeSelection(args.subList(start, args.size))
        } else if ("list" == op || "ls" == op) {
            showSelection()
        } else if ("clear" == op) {
            selectedPaths.clear()
            showSelectionMessage("selection cleared")
        } else if ("trash" == op) {
            val files = selectedFiles()
            if (files.isEmpty()) showSelectionMessage("selection is empty")
            else trashTargets(files)
        } else if ("zip" == op) {
            if (args.size <= start) {
                showTerminalPopup("sel", "sel zip requires an archive path")
                return
            }
            val sources: ArrayList<SourceSpec> = ArrayList<SourceSpec>()
            for (file in selectedFiles()) sources.add(SourceSpec(file, false))
            if (sources.isEmpty()) {
                showSelectionMessage("selection is empty")
                return
            }
            val archive = resolve(args.get(start))
            if (archive == null) {
                showTerminalPopup("sel", "sel zip: invalid archive path")
                return
            }
            val plan = buildZipPlan(archive, sources, true)
            val run = Runnable { startZipOperation(plan) }
            if (archive.exists()) confirmTerminal(
                "overwrite existing archive?\n" + archive.getAbsolutePath(),
                "zip",
                run
            )
            else run.run()
        } else if ("cp" == op || "mv" == op) {
            if (args.size <= start) {
                showTerminalPopup("sel", "sel " + op + " requires a destination")
                return
            }
            val command = ArrayList<kotlin.String>()
            command.add(op)
            if ("cp" == op) command.add("-r")
            for (file in selectedFiles()) command.add(file.getAbsolutePath())
            command.add(args.get(start))
            runCopyMove(command, "mv" == op)
        } else if ("share" == op) {
            val files = selectedFiles()
            if (files.size != 1) showTerminalPopup(
                "sel",
                "sel share currently needs exactly one selected file"
            )
            else shareFile(files.get(0))
        } else {
            print("sel commands:\nsel add [pattern]\nsel rm [pattern]\nsel list\nsel clear\nsel trash\nsel zip [archive.zip]\nsel cp [destination]\nsel mv [destination]\nsel share")
        }
    }

    private fun addSelection(patterns: MutableList<kotlin.String>?) {
        if (patterns == null || patterns.isEmpty()) {
            showTerminalPopup("sel", "sel add requires one or more files or globs")
            return
        }
        for (file in expandTargetArgs(patterns, true)) selectedPaths.add(pathKey(file))
        showSelection()
    }

    private fun removeSelection(patterns: MutableList<kotlin.String>?) {
        if (patterns == null || patterns.isEmpty()) {
            showTerminalPopup("sel", "sel rm requires one or more files or globs")
            return
        }
        for (file in expandTargetArgs(patterns, true)) selectedPaths.remove(pathKey(file))
        showSelection()
    }

    private fun selectedFiles(): ArrayList<File> {
        val files = ArrayList<File>()
        val stale = ArrayList<kotlin.String?>()
        for (path in selectedPaths) {
            val file = File(path)
            if (file.exists()) files.add(file)
            else stale.add(path)
        }
        selectedPaths.removeAll(stale)
        return files
    }

    private fun showSelection() {
        val out = StringBuilder("selection:")
        val files = selectedFiles()
        if (files.isEmpty()) out.append("\nempty")
        for (file in files) out.append('\n').append(file.getAbsolutePath())
        print(out.toString())
    }

    private fun showSelectionMessage(message: kotlin.String?) {
        print(message + "\nselected: " + selectedPaths.size)
    }

    private fun runTrashCommand(args: MutableList<kotlin.String>?) {
        if (args == null || args.size < 2 || "list".equals(args.get(1), ignoreCase = true)) {
            showTrashList()
            return
        }
        val op = args.get(1).lowercase()
        if ("empty" == op) {
            confirmTerminal(
                "permanently delete all trashed files?",
                "trash",
                Runnable { this.emptyTrash() })
            return
        }
        trashTargets(expandTargetArgs(args.subList(1, args.size), true))
    }

    private fun runRestoreCommand(args: MutableList<kotlin.String>?) {
        if (args == null || args.size < 2) {
            showTerminalPopup("restore", "restore: usage: restore [label|all]")
            return
        }
        restoreTrash(args.get(1))
    }

    private fun trashTargets(targets: ArrayList<File>?) {
        if (targets == null || targets.isEmpty()) {
            showTerminalPopup("trash", "trash: no matches")
            return
        }
        val blocked = firstBlockedActionTarget(targets, "trash")
        if (blocked != null) {
            showTerminalPopup("trash", blocked)
            return
        }
        confirmTerminal(
            trashConfirmationMessage("trash", targets),
            "trash",
            Runnable { performTrashTargets(targets, "trash") })
    }

    private fun performTrashTargets(targets: ArrayList<File>, title: kotlin.String?) {
        val trashDir = trashDirectory()
        if (!trashDir.exists() && !trashDir.mkdirs()) {
            showTerminalPopup(title, "trash: could not create " + trashDir.getAbsolutePath())
            return
        }
        val index = loadTrashIndex()
        val result = OperationResult()
        for (target in targets) {
            if (target == null || !target.exists()) continue
            val original = pathKey(target)
            val trashed = uniqueTrashFile(target)
            if (target.renameTo(trashed)) {
                index.add(
                    TrashEntry(
                        trashed.getName(),
                        original,
                        pathKey(trashed),
                        System.currentTimeMillis()
                    )
                )
                result.deleted++
                selectedPaths.remove(original)
            } else {
                result.failed++
                result.failures.add(original + ": could not move to trash")
            }
        }
        saveTrashIndex(index)
        renderListing()
        showTerminalPopup(title, result.summary("copied files", "trashed files", "copied files"))
    }

    private fun restoreTrash(label: kotlin.String?) {
        val entries = loadTrashIndex()
        if (entries.isEmpty()) {
            showTerminalPopup("restore", "trash is empty")
            return
        }
        val all = "all".equals(label, ignoreCase = true)
        val result = OperationResult()
        val remaining: ArrayList<TrashEntry> = ArrayList<TrashEntry>()
        for (entry in entries) {
            val match = all || entry.label.equals(label, ignoreCase = true)
            if (!match) {
                remaining.add(entry)
                continue
            }
            val trashed = File(entry.trashedPath)
            val original = File(entry.originalPath)
            val parent = original.getParentFile()
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                result.failed++
                result.failures.add(entry.label + ": could not create " + parent.getAbsolutePath())
                remaining.add(entry)
            } else if (original.exists()) {
                result.failed++
                result.failures.add(entry.label + ": original path already exists")
                remaining.add(entry)
            } else if (trashed.renameTo(original)) {
                result.copied++
            } else {
                result.failed++
                result.failures.add(entry.label + ": could not restore")
                remaining.add(entry)
            }
        }
        saveTrashIndex(remaining)
        renderListing()
        showTerminalPopup(
            "restore",
            result.summary("restored files", "deleted files", "copied files")
        )
    }

    private fun showTrashList() {
        val entries = loadTrashIndex()
        val out = StringBuilder("trash:")
        if (entries.isEmpty()) out.append("\nempty")
        for (entry in entries) {
            out.append('\n').append(entry.label).append(" -> ").append(entry.originalPath)
        }
        out.append("\n\nrestore [label|all]\ntrash empty")
        print(out.toString())
    }

    private fun emptyTrash() {
        val plan = buildDeletePlan(mutableListOf(trashDirectory()))
        startDeleteOperation(plan)
        saveTrashIndex(ArrayList<TrashEntry>())
    }

    private fun trashDirectory(): File {
        return File(sharedStorageRoot(), TRASH_DIR_NAME)
    }

    private fun uniqueTrashFile(source: File): File {
        val base = source.getName()
        val dir = trashDirectory()
        var candidate = File(dir, System.currentTimeMillis().toString() + "-" + base)
        var i = 2
        while (candidate.exists()) candidate =
            File(dir, System.currentTimeMillis().toString() + "-" + i++ + "-" + base)
        return candidate
    }

    private fun loadTrashIndex(): ArrayList<TrashEntry> {
        val entries: ArrayList<TrashEntry> = ArrayList<TrashEntry>()
        val raw: kotlin.String = getSharedPreferences(
            MainActivity.Companion.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        ).getString(MainActivity.Companion.PREF_TRASH_INDEX, "")!!
        if (TextUtils.isEmpty(raw)) return entries
        for (line in raw.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val parts = line.split("\t".toRegex(), limit = 4).toTypedArray()
            if (parts.size < 4) continue
            try {
                val trashed = File(parts[2])
                if (trashed.exists()) entries.add(
                    TrashEntry(
                        parts[0],
                        parts[1],
                        parts[2],
                        parts[3].toLong()
                    )
                )
            } catch (ignored: Exception) {
            }
        }
        return entries
    }

    private fun saveTrashIndex(entries: MutableList<TrashEntry>?) {
        val out = StringBuilder()
        if (entries != null) {
            for (entry in entries) {
                out.append(entry.label).append('\t').append(entry.originalPath).append('\t')
                    .append(entry.trashedPath).append('\t').append(entry.time).append('\n')
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_TRASH_INDEX, out.toString()).apply()
    }

    private fun expandTargetArgs(
        patterns: MutableList<kotlin.String>,
        includeDotHidden: Boolean
    ): ArrayList<File> {
        val seen = LinkedHashSet<kotlin.String?>()
        val out = ArrayList<File>()
        for (pattern in patterns) {
            val expanded = expandGlob(pattern, includeDotHidden)
            for (file in expanded) {
                val key = pathKey(file)
                if (seen.add(key)) out.add(file)
            }
        }
        return out
    }

    private fun expandSourceArgs(patterns: MutableList<kotlin.String>): ArrayList<SourceSpec> {
        val out: ArrayList<SourceSpec> = ArrayList<SourceSpec>()
        val seen = LinkedHashSet<kotlin.String?>()
        for (pattern in patterns) {
            if (pattern.endsWith("/.") || "." == pattern) {
                val dir = resolve(
                    if (pattern.endsWith("/.")) pattern.substring(
                        0,
                        pattern.length - 2
                    ) else pattern
                )
                if (dir != null && dir.exists()) out.add(SourceSpec(dir, true))
                continue
            }
            val expanded = expandGlob(pattern, false)
            for (file in expanded) {
                val key = pathKey(file)
                if (seen.add(key)) out.add(SourceSpec(file, false))
            }
        }
        return out
    }

    private fun expandGlob(raw: kotlin.String, includeDotHidden: Boolean): MutableList<File> {
        if (raw.indexOf('*') < 0) {
            val file = resolve(raw)
            return if (file != null && file.exists()) mutableListOf(file) else mutableListOf()
        }
        val slash = raw.lastIndexOf(File.separatorChar)
        val dirPart = if (slash >= 0) raw.substring(0, slash) else ""
        val nameGlob = if (slash >= 0) raw.substring(slash + 1) else raw
        val dir = resolve(if (dirPart.length == 0) "." else dirPart)
        val children = if (dir == null) null else dir.listFiles()
        if (children == null) return mutableListOf()
        val pattern = compileGlob(nameGlob, false)
        val dotPattern = nameGlob.startsWith(".")
        val matches = ArrayList<File>()
        for (child in children) {
            val name = child.getName()
            if ("." == name || ".." == name) continue
            if (name.startsWith(".") && !dotPattern && !includeDotHidden) continue
            if (pattern.matcher(name).matches()) matches.add(child)
        }
        matches.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        return matches
    }

    private fun buildCopyPlan(
        sources: MutableList<SourceSpec>,
        destination: File?,
        recursive: Boolean,
        move: Boolean,
        destAsDirectory: Boolean
    ): CopyPlan {
        val plan = CopyPlan(move)
        if (destination == null) {
            plan.error = (if (move) "mv" else "cp") + ": invalid destination"
            return plan
        }
        val multiple = sources.size > 1
        for (source in sources) {
            if (move) plan.moveSources.add(source.file!!)
            if (source.file!!.isDirectory()) {
                if (!recursive && !source.copyContents) {
                    plan.error = "cp: omitting directory: " + source.file.getAbsolutePath()
                    return plan
                }
                val baseDest = destinationForSource(source, destination, multiple)
                if (source.copyContents) {
                    addDirectoryContentsToPlan(plan, source.file, baseDest)
                } else if (destination.exists() && destination.isDirectory()) {
                    addDirectoryContentsToPlan(
                        plan,
                        source.file,
                        File(destination, source.file.getName())
                    )
                } else {
                    addDirectoryContentsToPlan(plan, source.file, destination)
                }
            } else {
                if (destAsDirectory) plan.directories.add(destination)
                val target = if (multiple || destination.isDirectory() || destAsDirectory) File(
                    destination,
                    source.file.getName()
                ) else destination
                addFileToPlan(plan, source.file, target)
            }
        }
        for (item in plan.items) {
            plan.totalBytes += max(0, item.source.length())
            if (item.destination.exists()) plan.overwriteCount++
        }
        return plan
    }

    private fun destinationForSource(
        source: SourceSpec,
        destination: File,
        multiple: Boolean
    ): File? {
        if (source.copyContents) return destination
        if (multiple || destination.isDirectory()) return File(destination, source.file!!.getName())
        return destination
    }

    private fun addDirectoryContentsToPlan(plan: CopyPlan, sourceDir: File, destDir: File?) {
        plan.directories.add(destDir!!)
        val children = sourceDir.listFiles()
        if (children == null) return
        children.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        for (child in children) {
            val dest = File(destDir, child.getName())
            if (child.isDirectory()) addDirectoryContentsToPlan(plan, child, dest)
            else addFileToPlan(plan, child, dest)
        }
    }

    private fun addFileToPlan(plan: CopyPlan, source: File, destination: File) {
        plan.items.add(CopyItem(source, destination))
    }

    private fun buildDeletePlan(targets: MutableList<File>): DeletePlan {
        val plan = DeletePlan()
        val seen = LinkedHashSet<kotlin.String?>()
        for (target in targets) {
            addDeleteTarget(plan, target, seen)
        }
        plan.totalItems = plan.items.size
        return plan
    }

    private fun addDeleteTarget(plan: DeletePlan, target: File?, seen: MutableSet<kotlin.String?>) {
        if (target == null || !target.exists()) return
        if (target.isDirectory()) {
            val children = target.listFiles()
            if (children != null) {
                children.sortWith { left, right ->
                    String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
                }
                for (child in children) addDeleteTarget(plan, child, seen)
            }
        }
        val key = pathKey(target)
        if (seen.add(key)) plan.items.add(target)
    }

    private fun startCopyMoveOperation(plan: CopyPlan) {
        val cancelled = AtomicBoolean(false)
        showOperationOverlay(if (plan.move) "mv" else "cp", cancelled)
        Thread(Runnable {
            val result = OperationResult()
            var copiedBytes: kotlin.Long = 0
            val writeBlock = firstBlockedWriteTarget(plan)
            if (writeBlock != null) {
                result.failed = plan.items.size
                result.failures.add(writeBlock + ": destination is not writable (EPERM). Android blocked file creation there.")
                finishOperationOverlay()
                val finalResult = result
                mainHandler.post(Runnable {
                    showTerminalPopup(
                        if (plan.move) "mv" else "cp",
                        finalResult.summary(
                            if (plan.move) "moved files" else "copied files",
                            "deleted files",
                            "copied files"
                        )
                    )
                })
                return@Runnable
            }
            for (dir in plan.directories) {
                if (cancelled.get()) break
                if (dir.exists()) result.skipped++
                else if (dir.mkdirs()) result.created++
                else {
                    result.failed++
                    result.failures.add(dir.getAbsolutePath() + ": could not create directory")
                }
            }
            for (i in plan.items.indices) {
                if (cancelled.get()) break
                val item = plan.items.get(i)
                updateOperationOverlay(
                    item.source.getAbsolutePath(),
                    i,
                    plan.items.size,
                    copiedBytes,
                    plan.totalBytes
                )
                try {
                    copiedBytes += copyFileChunked(item.source, item.destination, cancelled)
                    if (cancelled.get()) {
                        result.cancelled = true
                        break
                    }
                    result.copied++
                } catch (e: Exception) {
                    result.failed++
                    result.failures.add(item.source.getAbsolutePath() + " -> " + item.destination.getAbsolutePath() + ": " + e.message)
                }
            }
            if (plan.move && !result.cancelled && result.failed == 0 && result.copied == plan.items.size) {
                val deletePlan = buildDeletePlan(plan.moveSources)
                for (i in deletePlan.items.indices) {
                    if (cancelled.get()) break
                    val file = deletePlan.items.get(i)
                    updateOperationOverlay(
                        file.getAbsolutePath(),
                        i,
                        deletePlan.items.size,
                        copiedBytes,
                        plan.totalBytes
                    )
                    if (file.delete()) result.deleted++
                    else {
                        result.failed++
                        result.failures.add(file.getAbsolutePath() + ": could not delete source")
                    }
                }
            }
            if (cancelled.get()) result.cancelled = true
            finishOperationOverlay()
            val finalResult = result
            mainHandler.post(Runnable {
                renderListing()
                showTerminalPopup(
                    if (plan.move) "mv" else "cp",
                    finalResult.summary(
                        if (plan.move) "moved files" else "copied files",
                        "deleted files",
                        "copied files"
                    )
                )
            })
        }, "retui-file-op").start()
    }

    private fun firstBlockedWriteTarget(plan: CopyPlan): kotlin.String? {
        val seen = LinkedHashSet<kotlin.String?>()
        for (item in plan.items) {
            val parent = item.destination.getParentFile()
            if (parent == null) continue
            val key = pathKey(parent)
            if (!seen.add(key)) continue
            try {
                if (!parent.exists() && !parent.mkdirs()) return parent.getAbsolutePath()
                val probe = File(parent, ".retui-write-test")
                FileOutputStream(probe, false).use { out ->
                    out.write(1)
                }
                probe.delete()
            } catch (e: Exception) {
                return parent.getAbsolutePath()
            }
        }
        return null
    }

    private fun buildZipPlan(
        archive: File,
        sources: MutableList<SourceSpec>,
        recursive: Boolean
    ): ZipPlan {
        val plan = ZipPlan(archive)
        for (source in sources) {
            if (source.file!!.isDirectory()) {
                if (!recursive && !source.copyContents) {
                    plan.error = "zip: directory requires -r: " + source.file.getAbsolutePath()
                    return plan
                }
                val base = if (source.copyContents) "" else source.file.getName() + "/"
                addZipDirectory(plan, source.file, base)
            } else {
                addZipFile(plan, source.file, source.file.getName())
            }
        }
        return plan
    }

    private fun addZipDirectory(plan: ZipPlan, directory: File, baseName: kotlin.String) {
        if (baseName.length > 0) plan.entries.add(ZipItem(directory, baseName, true))
        val children = directory.listFiles()
        if (children == null) return
        children.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        for (child in children) {
            if (pathKey(child) == pathKey(plan.archive)) continue
            val entry = baseName + child.getName()
            if (child.isDirectory()) addZipDirectory(plan, child, entry + "/")
            else addZipFile(plan, child, entry)
        }
    }

    private fun addZipFile(plan: ZipPlan, file: File, entryName: kotlin.String?) {
        if (pathKey(file) == pathKey(plan.archive)) return
        plan.entries.add(ZipItem(file, entryName, false))
        plan.totalBytes += max(0, file.length())
    }

    private fun startZipOperation(plan: ZipPlan) {
        val cancelled = AtomicBoolean(false)
        showOperationOverlay("zip", cancelled)
        Thread(Runnable {
            var zipped = 0
            var failed = 0
            val failures = ArrayList<kotlin.String?>()
            var zippedBytes: kotlin.Long = 0
            val parent = plan.archive.getParentFile()
            val temp =
                if (parent == null) File(plan.archive.getAbsolutePath() + ".retui-zipping") else File(
                    parent,
                    plan.archive.getName() + ".retui-zipping"
                )
            try {
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw Exception("could not create archive directory")
                }
                ZipOutputStream(FileOutputStream(temp)).use { zip ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    for (i in plan.entries.indices) {
                        if (cancelled.get()) break
                        val item = plan.entries.get(i)
                        updateOperationOverlay(
                            item.source.getAbsolutePath(),
                            i,
                            plan.entries.size,
                            zippedBytes,
                            plan.totalBytes
                        )
                        try {
                            val entry = ZipEntry(item.entryName)
                            entry.setTime(item.source.lastModified())
                            zip.putNextEntry(entry)
                            if (!item.directory) {
                                FileInputStream(item.source).use { `in` ->
                                    var read: Int
                                    while ((`in`.read(buffer).also { read = it }) >= 0) {
                                        if (cancelled.get()) break
                                        zip.write(buffer, 0, read)
                                        zippedBytes += read.toLong()
                                    }
                                }
                            }
                            zip.closeEntry()
                            if (!cancelled.get() && !item.directory) zipped++
                        } catch (e: Exception) {
                            failed++
                            failures.add(item.source.getAbsolutePath() + ": " + e.message)
                        }
                    }
                }
                if (cancelled.get()) {
                    temp.delete()
                } else {
                    if (plan.archive.exists() && !plan.archive.delete()) throw Exception("could not overwrite archive")
                    if (!temp.renameTo(plan.archive)) throw Exception("could not finish archive")
                }
            } catch (e: Exception) {
                failed++
                failures.add(plan.archive.getAbsolutePath() + ": " + e.message)
                temp.delete()
            }
            finishOperationOverlay()
            val finalZipped = zipped
            val finalFailed = failed
            val finalCancelled = cancelled.get()
            val finalFailures = failures
            mainHandler.post(Runnable {
                renderListing()
                val out = StringBuilder()
                out.append("zipped files: ").append(finalZipped).append('\n')
                out.append("failed files: ").append(finalFailed)
                if (finalCancelled) out.append("\ncancelled")
                if (!finalFailures.isEmpty()) {
                    out.append("\nfailed paths:")
                    for (failure in finalFailures) out.append('\n').append(failure)
                }
                showTerminalPopup("zip", out.toString())
            })
        }, "retui-zip-op").start()
    }

    @Throws(Exception::class)
    private fun copyFileChunked(
        source: File,
        destination: File,
        cancelled: AtomicBoolean
    ): kotlin.Long {
        val parent = destination.getParentFile()
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw Exception("could not create destination directory")
        }
        try {
            return copyFileViaTemp(source, destination, cancelled)
        } catch (e: Exception) {
            if (isTempFileBlocked(e)) {
                return copyFileDirect(source, destination, cancelled)
            }
            throw e
        }
    }

    @Throws(Exception::class)
    private fun copyFileViaTemp(
        source: File,
        destination: File,
        cancelled: AtomicBoolean
    ): kotlin.Long {
        val temp = File(destination.getParentFile(), destination.getName() + ".retui-copying")
        var written: kotlin.Long = 0
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        FileInputStream(source).use { `in` ->
            FileOutputStream(temp).use { out ->
                var read: Int
                while ((`in`.read(buffer).also { read = it }) >= 0) {
                    if (cancelled.get()) {
                        out.close()
                        temp.delete()
                        return written
                    }
                    out.write(buffer, 0, read)
                    written += read.toLong()
                }
            }
        }
        if (destination.exists() && !destination.delete()) {
            temp.delete()
            throw Exception("could not overwrite destination")
        }
        if (!temp.renameTo(destination)) {
            temp.delete()
            throw Exception("could not finish copy")
        }
        destination.setLastModified(source.lastModified())
        return written
    }

    @Throws(Exception::class)
    private fun copyFileDirect(
        source: File,
        destination: File,
        cancelled: AtomicBoolean
    ): kotlin.Long {
        var written: kotlin.Long = 0
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        FileInputStream(source).use { `in` ->
            FileOutputStream(destination, false).use { out ->
                var read: Int
                while ((`in`.read(buffer).also { read = it }) >= 0) {
                    if (cancelled.get()) {
                        out.close()
                        destination.delete()
                        return written
                    }
                    out.write(buffer, 0, read)
                    written += read.toLong()
                }
            }
        }
        destination.setLastModified(source.lastModified())
        return written
    }

    private fun isTempFileBlocked(e: Exception): Boolean {
        val message = e.message
        return message != null && (message.contains("EPERM") || message.contains("EACCES"))
    }

    private fun startDeleteOperation(plan: DeletePlan) {
        val cancelled = AtomicBoolean(false)
        showOperationOverlay("rm", cancelled)
        Thread(Runnable {
            val result = OperationResult()
            for (i in plan.items.indices) {
                if (cancelled.get()) break
                val item = plan.items.get(i)
                updateOperationOverlay(
                    item.getAbsolutePath(),
                    i,
                    plan.items.size,
                    i.toLong(),
                    plan.items.size.toLong()
                )
                if (item.delete()) result.deleted++
                else {
                    result.failed++
                    result.failures.add(item.getAbsolutePath() + ": could not delete")
                }
            }
            if (cancelled.get()) result.cancelled = true
            finishOperationOverlay()
            val finalResult = result
            mainHandler.post(Runnable {
                renderListing()
                showTerminalPopup(
                    "rm",
                    finalResult.summary("copied files", "deleted files", "copied files")
                )
            })
        }, "retui-delete-op").start()
    }

    private fun confirmTerminal(
        message: kotlin.String?,
        title: kotlin.String?,
        onConfirm: Runnable
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "YES",
                DialogInterface.OnClickListener { d: DialogInterface?, which: Int -> onConfirm.run() })
            .setNegativeButton(
                "NO",
                DialogInterface.OnClickListener { d: DialogInterface?, which: Int ->
                    showTerminalPopup(
                        title,
                        title + ": skipped"
                    )
                })
            .create()
        dialog.setOnShowListener(OnShowListener { d: DialogInterface? ->
            dialog.getWindow()!!.setBackgroundDrawable(panelDrawable(true))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(textColor)
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(textColor)
            val titleView = dialog.findViewById<TextView?>(
                getResources().getIdentifier(
                    "alertTitle",
                    "id",
                    "android"
                )
            )
            if (titleView != null) titleView.setTextColor(textColor)
            val messageView = dialog.findViewById<TextView?>(android.R.id.message)
            if (messageView != null) {
                messageView.setTextColor(textColor)
                messageView.setTypeface(appTypeface)
            }
        })
        dialog.show()
    }

    private fun showTerminalPopup(title: kotlin.String?, message: CharSequence?) {
        val body = label(if (message == null) "" else message.toString(), outputTextSizeSp, false)
        body.setPadding(dp(14), dp(10), dp(14), dp(10))
        body.setTextIsSelectable(true)
        body.setSingleLine(false)
        body.setTypeface(appTypeface)
        body.setTextColor(textColor)

        val scroll = ScrollView(this)
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        val maxHeight = max(dp(180), getResources().getDisplayMetrics().heightPixels / 3)
        scroll.setLayoutParams(LinearLayout.LayoutParams(-1, maxHeight))

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(
                "OK",
                DialogInterface.OnClickListener { d: DialogInterface?, which: Int -> refocusInput() })
            .create()
        dialog.setOnShowListener(OnShowListener { d: DialogInterface? ->
            val window = dialog.getWindow()
            if (window != null) window.setBackgroundDrawable(panelDrawable(PanelRole.OUTPUT))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(textColor)
            val dialogTitle = dialog.findViewById<TextView?>(
                getResources().getIdentifier(
                    "alertTitle",
                    "id",
                    "android"
                )
            )
            if (dialogTitle != null) {
                dialogTitle.setTextColor(textColor)
                dialogTitle.setTypeface(appTypeface, Typeface.BOLD)
                dialogTitle.setTextSize(headerTextSizeSp.toFloat())
            }
        })
        dialog.show()
    }

    private fun showOperationOverlay(title: kotlin.String?, cancelled: AtomicBoolean) {
        mainHandler.post(Runnable {
            if (operationOverlay != null) stage!!.removeView(operationOverlay!!.container)
            operationOverlay = OperationOverlay(title, cancelled)
            stage!!.addView(operationOverlay!!.container, operationOverlay!!.params)
        })
    }

    private fun updateOperationOverlay(
        current: kotlin.String?,
        done: Int,
        total: Int,
        bytesDone: kotlin.Long,
        bytesTotal: kotlin.Long
    ) {
        mainHandler.post(Runnable {
            if (operationOverlay != null) operationOverlay!!.update(
                current,
                done,
                total,
                bytesDone,
                bytesTotal
            )
        })
    }

    private fun finishOperationOverlay() {
        mainHandler.post(Runnable {
            if (operationOverlay != null) {
                stage!!.removeView(operationOverlay!!.container)
                operationOverlay = null
            }
        })
    }

    private fun updateSuggestions(input: kotlin.String?) {
        updateCommandHint(input)
        if (suggestionsGroup == null) return
        suggestionsGroup!!.removeAllViews()
        val raw = if (input == null) "" else input
        val trimmed = raw.trim { it <= ' ' }
        val lower = raw.lowercase()
        if (trimmed.length == 0) {
            if (!hasStorageAccess()) {
                setSuggestionsVisible(true)
                addSuggestion("permission", "permission", true)
                return
            }
            setSuggestionsVisible(true)
            if (commandOutputVisible && previewFile != null && previewFile!!.exists()) {
                suggestPreviewActions(previewFile)
            } else {
                suggestDefaultActions()
            }
            return
        }
        setSuggestionsVisible(true)

        var prefix = ""
        var dirsOnly = false
        var filesOnly = false
        var commandPrefix = if (raw.contains(" ")) raw.substring(0, raw.indexOf(' ') + 1) else ""
        if (!raw.contains(" ")) {
            suggestCommands(trimmed)
            return
        }
        if (lower.startsWith("cd ")) {
            prefix = raw.substring(3).trim { it <= ' ' }
            dirsOnly = true
        } else if (lower.startsWith("open ")) {
            prefix = raw.substring(5).trim { it <= ' ' }
            filesOnly = true
        } else if (suggestCompletedPathActions(raw)) {
            return
        } else if (lower.startsWith("preview ") || lower.startsWith("peek ") || lower.startsWith("edit ") || lower.startsWith(
                "filter "
            )
        ) {
            val target = shellSuggestionTarget(raw)
            prefix = target.prefix
            commandPrefix = target.commandPrefix
        } else if (lower.startsWith("rm ")) {
            suggestRemoveFlags(raw)
            val target = shellSuggestionTarget(raw)
            prefix = target.prefix
            commandPrefix = target.commandPrefix
            filesOnly = isPermanentRemoveCommand(raw)
        } else if (lower.startsWith("share ") || lower.startsWith("trash ")) {
            val target = shellSuggestionTarget(raw)
            prefix = target.prefix
            commandPrefix = target.commandPrefix
            if (lower.startsWith("trash ")) {
                if (suggestTrashCommands(raw)) return
            }
        } else if (lower.startsWith("cp ") || lower.startsWith("mv ") || lower.startsWith("mkdir ") || lower.startsWith(
                "zip "
            )
        ) {
            val target = shellSuggestionTarget(raw)
            prefix = target.prefix
            commandPrefix = target.commandPrefix
        } else if (lower.startsWith("restore ")) {
            suggestTrashLabels(raw.substring(8).trim { it <= ' ' }, "restore ")
            return
        } else if (lower.startsWith("recent ")) {
            suggestRecentPaths(raw.substring(7).trim { it <= ' ' })
            return
        } else if (lower.startsWith("sel ") || lower.startsWith("mark ") || lower.startsWith("unmark ")) {
            if (suggestSelection(raw)) return
            val target = shellSuggestionTarget(raw)
            prefix = target.prefix
            commandPrefix = target.commandPrefix
        } else if (lower.startsWith("find ") || lower.startsWith("search ")) {
            suggestFind(raw)
            return
        } else if (lower.startsWith("tree ")) {
            suggestTree(raw)
            return
        }
        suggestChildren(prefix, dirsOnly, filesOnly, commandPrefix)
    }

    private fun updateCommandHint(input: kotlin.String?) {
        if (commandHintView == null) return
        if (commandHintExpanded) {
            commandHintView!!.setMaxLines(Int.Companion.MAX_VALUE)
            commandHintView!!.setEllipsize(null)
        } else {
            commandHintView!!.setMaxLines(commandHintCollapsedLines())
            commandHintView!!.setEllipsize(TextUtils.TruncateAt.END)
        }
        commandHintView!!.setText(commandHintText(input))
    }

    private fun toggleCommandHintExpanded() {
        commandHintExpanded = !commandHintExpanded
        updateCommandHint(if (inputView == null) "" else inputView!!.getText().toString())
        refocusInput()
    }

    private fun commandHintCollapsedLines(): Int {
        return 2
    }

    private fun commandHintText(input: kotlin.String?): kotlin.String {
        val hint = "# hint " + (if (commandHintExpanded) "[-] " else "[+] ") + commandHintFor(input)
        if (!commandHintExpanded) {
            return hint
        }
        return (hint
                + "\n" + commandHelpFor(input))
    }

    private fun commandHintFor(input: kotlin.String?): kotlin.String {
        val raw = if (input == null) "" else input
        val trimmed = raw.trim { it <= ' ' }
        if (trimmed.length == 0) {
            if (commandOutputVisible && previewFile != null && previewFile!!.exists()) {
                return "preview mode; suggestions are actions for " + previewFile!!.getName() + "; X closes preview"
            }
            return "type a file command; suggestions complete commands, flags, and paths"
        }
        val args = splitArgs(raw)
        if (args.isEmpty()) return "type a file command; suggestions complete the next token"
        val command = args.get(0).lowercase()
        val last = args.get(args.size - 1).lowercase()
        val endedWithSpace = raw.endsWith(" ")

        if (args.size == 1 && !endedWithSpace) {
            if ("find".startsWith(command)) return "find searches below this folder; add -name, --type, --size, or -x"
            if ("tree".startsWith(command)) return "tree redraws the file tree; flags control depth, hidden files, and sorting"
            if ("cd".startsWith(command)) return "cd changes the current folder"
            if ("preview".startsWith(command) || "peek".startsWith(command)) return "preview shows bounded read-only file content inside FM"
            if ("edit".startsWith(command)) return "edit opens a text file in the terminal editor"
            if ("open".startsWith(command)) return "open sends a file to Android's matching app"
            if ("share".startsWith(command)) return "share opens Android share for a file"
            if ("sel".startsWith(command)) return "sel works with marked files: add, rm, list, clear, share, trash, zip"
            return "suggestions are matching commands; choose one to see its params"
        }

        if ("find" == command || "search" == command) return findHint(args, endedWithSpace, last)
        if ("tree" == command) return treeHint(args, endedWithSpace, last)
        if ("cd" == command) return "cd moves into the selected folder; use cd .. to go up"
        if ("preview" == command || "peek" == command) return "preview supports .md .txt .log .json .csv with capped read-only output"
        if ("edit" == command) return "edit opens a text-like file here; save stays inside FM"
        if ("open" == command) return "open launches the selected file with Android apps"
        if ("share" == command) return "share sends the selected file through Android share"
        if ("rm" == command) return removeHint(args, endedWithSpace, last)
        if ("trash" == command) return "trash moves files into .retui-trash; use restore to bring them back"
        if ("restore" == command) return "restore brings back a trashed item by label, or restore all"
        if ("cp" == command) return "cp copies source to destination; overwrites and folder copies ask first"
        if ("mv" == command) return "mv renames or moves after showing source and destination"
        if ("mkdir" == command) return "mkdir creates a folder in the current directory"
        if ("zip" == command) return "zip -r archive.zip folder creates an archive"
        if ("sel" == command || "mark" == command || "unmark" == command) return selectionHint(
            args,
            endedWithSpace,
            last
        )
        if ("filter" == command) return "filter narrows the current directory by name without recursive search"
        return "press enter to run; suggestions complete the next token"
    }

    private fun commandHelpFor(input: kotlin.String?): kotlin.String {
        val args = splitArgs(if (input == null) "" else input)
        val command = if (args.isEmpty()) "" else args.get(0).lowercase()
        if ("preview" == command || "peek" == command) {
            return ("preview:\n"
                    + "- supported: .md .txt .log .json .csv\n"
                    + "- output is read-only and capped for large files\n"
                    + "- use edit only when you intentionally want the text editor")
        }
        if ("rm" == command || "trash" == command || "restore" == command) {
            return ("safe remove:\n"
                    + "- rm [file] moves to .retui-trash after confirmation\n"
                    + "- rm -r [folder] moves folders to trash after confirmation\n"
                    + "- rm --permanent [file] is files-only and cannot be restored by FM\n"
                    + "- restore [label|all] brings items back from trash")
        }
        if ("cp" == command || "mv" == command) {
            return ("copy and move:\n"
                    + "- source and destination are shown before guarded operations\n"
                    + "- overwrites and folder copies ask for confirmation\n"
                    + "- mv refuses moving a folder inside itself")
        }
        if ("sel" == command || "mark" == command || "unmark" == command) {
            return ("selection:\n"
                    + "- sel add [path] marks files for batch work\n"
                    + "- sel list shows marks; sel clear removes marks\n"
                    + "- sel trash, sel zip, sel cp, and sel mv act on marked files")
        }
        if ("find" == command || "search" == command) {
            return ("find:\n"
                    + "- find [path] -name [pattern] searches recursively\n"
                    + "- add --type image|video|audio|doc|dir|file|archive\n"
                    + "- add --size +100M or -10M to filter by size")
        }
        if ("tree" == command) {
            return ("tree:\n"
                    + "- tree -L 2 limits depth\n"
                    + "- tree -a includes hidden files\n"
                    + "- tree --sort name|size|mtime changes order")
        }
        return ("common:\n"
                + "- preview file | peek file | edit file\n"
                + "- cp [-r] src dst | mv src dst\n"
                + "- rm [-r] target | trash target\n"
                + "- restore label|all\n"
                + "- sel add/list/clear/trash/zip/cp/mv")
    }

    private fun findHint(
        args: MutableList<kotlin.String>,
        endedWithSpace: Boolean,
        last: kotlin.String
    ): kotlin.String {
        if ("-x" == last || "-a" == last) return "find " + last + " includes hidden dotfiles and hidden folders"
        if ("-name" == last || "--name" == last) return "find " + last + " expects text or a glob like *.xml"
        if ("--type" == last) return "find --type filters by image, video, audio, doc, dir, file, or archive"
        if ("--size" == last) return "find --size accepts +100M, -10M, or 500K"
        if ("--limit" == last || "-max" == last) return "find " + last + " caps the number of results"
        if (args.contains("--type") && endedWithSpace) return "choose a file type; suggestions show valid values"
        if (args.contains("--size") && endedWithSpace) return "enter a size filter such as +10M or -500K"
        if (args.contains("-name") && endedWithSpace) return "enter the filename text or glob to match"
        if (endedWithSpace) return "add a pattern, path, or flag; -x includes hidden files"
        return "find searches recursively from here; default results skip hidden files"
    }

    private fun treeHint(
        args: MutableList<kotlin.String>?,
        endedWithSpace: Boolean,
        last: kotlin.String?
    ): kotlin.String {
        if ("-a" == last) return "tree -a includes hidden dotfiles and hidden folders"
        if ("-d" == last) return "tree -d shows directories only"
        if ("-L" == last) return "tree -L expects a depth number"
        if ("--dirsfirst" == last) return "tree --dirsfirst groups folders before files"
        if ("--sort" == last) return "tree --sort expects name, size, or mtime"
        if (endedWithSpace) return "add flags like -L 2, -a, -d, --dirsfirst, or --sort size"
        return "tree redraws this file surface with terminal-style options"
    }

    private fun selectionHint(
        args: MutableList<kotlin.String>,
        endedWithSpace: Boolean,
        last: kotlin.String?
    ): kotlin.String {
        if (args.size <= 1 || endedWithSpace) return "sel add marks files; sel list shows marks; sel share/trash/zip acts on them"
        if ("add" == last) return "sel add marks a path for batch actions"
        if ("rm" == last) return "sel rm removes a path from the marked set"
        if ("list" == last) return "sel list prints all marked files"
        if ("clear" == last) return "sel clear removes all marks"
        if ("share" == last) return "sel share sends marked files to Android share"
        if ("trash" == last) return "sel trash moves marked files to trash"
        if ("zip" == last) return "sel zip archive.zip compresses marked files"
        return "sel commands keep batch file work terminal-driven"
    }

    private fun removeHint(
        args: MutableList<kotlin.String>?,
        endedWithSpace: Boolean,
        last: kotlin.String?
    ): kotlin.String {
        if ("--permanent" == last || "--delete" == last) return "permanent rm is files-only and still requires confirmation"
        if ("-r" == last) return "rm -r moves folders to .retui-trash after confirmation"
        if (isPermanentRemoveArgs(args)) return "rm --permanent refuses directories; suggestions show files only"
        if (isRecursiveRemoveArgs(args)) return "rm -r is guarded and moves folders to .retui-trash"
        if (endedWithSpace) return "choose a target; add -r for folders or --permanent for files-only delete"
        return "rm is guarded: default remove means move to .retui-trash after confirmation"
    }

    private fun setSuggestionsVisible(visible: Boolean) {
        if (suggestionsScroll != null) {
            suggestionsScroll!!.setVisibility(if (visible) View.VISIBLE else View.GONE)
        }
    }

    private fun suggestDefaultActions() {
        addSuggestion("tree", "tree ", false)
        addSuggestion("find", "find ", false)
        addSuggestion("preview", "preview ", false)
        addSuggestion("edit", "edit ", false)
        addSuggestion("trash", "trash ", false)
        addSuggestion("restore", "restore ", false)
        addSuggestion("cd ..", "cd ..", false)
        addSuggestion("recent", "recent", false)
        addSuggestion("cp", "cp ", false)
        addSuggestion("mv", "mv ", false)
        addSuggestion("sel", "sel ", false)
        addSuggestion("fav", "fav ", false)
    }

    private fun suggestCommands(prefix: kotlin.String) {
        val commands = arrayOf(
            "cd",
            "ls",
            "pwd",
            "find",
            "search",
            "filter",
            "tree",
            "preview",
            "peek",
            "edit",
            "open",
            "share",
            "mkdir",
            "cp",
            "mv",
            "rm",
            "trash",
            "restore",
            "zip",
            "sel",
            "mark",
            "unmark",
            "recent",
            "back",
            "fav",
            "permission",
            "refresh",
            "help"
        )
        val lower = prefix.lowercase()
        var added = 0
        for (command in commands) {
            if (added >= 24) return
            if (command!!.startsWith(lower)) {
                val execute =
                    "ls" == command || "pwd" == command || "recent" == command || "back" == command || "fav" == command || "permission" == command || "refresh" == command || "help" == command
                addSuggestion(command, if (execute) command else command + " ", execute)
                added++
            }
        }
    }

    private fun suggestChildren(
        prefix: kotlin.String,
        dirsOnly: Boolean,
        filesOnly: Boolean,
        commandPrefix: kotlin.String
    ) {
        val children = currentDirectory!!.listFiles()
        if (children == null) return
        children.sortWith { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName())
        }
        var added = 0
        for (child in children) {
            if (added >= 24) return
            if (dirsOnly && !child.isDirectory()) continue
            if (filesOnly && !child.isFile()) continue
            val name = child.getName()
            if (prefix.length > 0 && !name.lowercase().contains(prefix.lowercase())) continue
            val execute = !(commandPrefix.endsWith("open ") || commandPrefix.endsWith("share ")
                    || commandPrefix.endsWith("preview ") || commandPrefix.endsWith("peek ") || commandPrefix.endsWith(
                "edit "
            )
                    || commandPrefix.startsWith("cp ") || commandPrefix.startsWith("mv ")
                    || commandPrefix.startsWith("rm ") || commandPrefix.startsWith("mkdir ")
                    || commandPrefix.startsWith("trash ") || commandPrefix.startsWith("sel ")
                    || commandPrefix.startsWith("mark ") || commandPrefix.startsWith("unmark ")
                    || commandPrefix.startsWith("zip "))
            addSuggestion(name, commandPrefix + quoteIfNeeded(name), execute, false)
            added++
        }
    }

    private fun suggestTrashCommands(input: kotlin.String): Boolean {
        val args = splitArgs(input)
        if (args.size == 2 && !input.endsWith(" ")) {
            val prefix = args.get(1).lowercase()
            if ("list".startsWith(prefix)) addSuggestion("list", "trash list", true)
            if ("empty".startsWith(prefix)) addSuggestion("empty", "trash empty", true)
        }
        return false
    }

    private fun suggestTrashLabels(prefix: kotlin.String, commandPrefix: kotlin.String?) {
        val lower = prefix.lowercase()
        addSuggestion("all", commandPrefix + "all", true)
        for (entry in loadTrashIndex()) {
            if (entry.label.lowercase().contains(lower)) {
                addSuggestion(entry.label, commandPrefix + entry.label, true, false)
            }
        }
    }

    private fun suggestRemoveFlags(input: kotlin.String) {
        val args = splitArgs(input)
        val endedWithSpace = input.endsWith(" ")
        val last = if (args.size > 1) args.get(args.size - 1).lowercase() else ""
        if (!endedWithSpace && !last.startsWith("-")) return
        if (isPermanentRemoveArgs(args)) return
        val base = if (endedWithSpace) input else input.substring(0, input.length - last.length)
        val prefix = if (endedWithSpace) "" else last
        for (flag in arrayOf<kotlin.String>("-r", "--permanent")) {
            if (prefix.length == 0 || flag.startsWith(prefix)) {
                addSuggestion(flag, base + flag + " ", false)
            }
        }
    }

    private fun isPermanentRemoveCommand(input: kotlin.String): Boolean {
        return isPermanentRemoveArgs(splitArgs(input))
    }

    private fun isPermanentRemoveArgs(args: MutableList<kotlin.String>?): Boolean {
        if (args == null) return false
        for (arg in args) {
            if ("--permanent".equals(arg, ignoreCase = true) || "--delete".equals(
                    arg,
                    ignoreCase = true
                )
            ) return true
        }
        return false
    }

    private fun isRecursiveRemoveArgs(args: MutableList<kotlin.String>?): Boolean {
        if (args == null) return false
        for (arg in args) {
            if ("-r".equals(arg, ignoreCase = true) || "-R" == arg) return true
        }
        return false
    }

    private fun previewSuggestionLabel(file: File?): kotlin.String {
        if (file == null) return "preview"
        val ext = extension(file.getName())
        if (isOneOf(ext, "md", "txt", "log", "json", "csv")) return "preview " + ext
        if ("zip" == ext) return "preview zip"
        if (isType(file, "image")) return "preview image"
        return "preview"
    }

    private fun suggestRecentPaths(prefix: kotlin.String) {
        val lower = prefix.lowercase()
        for (path in recentPaths) {
            if (path.lowercase().contains(lower)) {
                addSuggestion(File(path).getName(), "recent " + quoteIfNeeded(path), true, false)
            }
        }
    }

    private fun suggestCompletedPathActions(input: kotlin.String?): Boolean {
        if (TextUtils.isEmpty(input) || input!!.endsWith(" ")) return false
        val args = splitArgs(input)
        if (args.size < 2) return false
        val command = args.get(0).lowercase()
        if (!isPathActionCommand(command)) return false
        val file = resolve(args.get(args.size - 1))
        if (file == null || !file.exists()) return false
        suggestPathActions(file)
        return true
    }

    private fun isPathActionCommand(command: kotlin.String?): Boolean {
        return "preview" == command || "peek" == command || "edit" == command
                || "open" == command || "share" == command || "trash" == command
                || "rm" == command || "cd" == command
    }

    private fun suggestPathActions(file: File?) {
        if (file == null) return
        val path = commandPath(file)
        if (file.isDirectory()) {
            addSuggestion("cd", "cd " + path, false)
            addSuggestion("tree -L 2", "tree -L 2", false)
            addSuggestion("find here", "find " + path + " ", false)
            addSuggestion("trash", "trash " + path, false)
            addSuggestion("rm -r -> trash", "rm -r " + path, false)
            addSuggestion("sel add", "sel add " + path, false)
            return
        }
        addSuggestion(previewSuggestionLabel(file), "preview " + path, false)
        if (isLikelyText(file)) addSuggestion("edit", "edit " + path, false)
        addSuggestion("open", "open " + path, false)
        addSuggestion("share", "share " + path, false)
        addSuggestion("trash", "trash " + path, false)
        addSuggestion("rm -> trash", "rm " + path, false)
        addSuggestion("sel add", "sel add " + path, false)
    }

    private fun suggestPreviewActions(file: File?) {
        if (file == null) return
        val path = commandPath(file)
        if (file.isDirectory()) {
            addSuggestion("cd", "cd " + path, false)
            addSuggestion("find here", "find " + path + " ", false)
            addSuggestion("tree -L 2", "tree -L 2", false)
            addSuggestion("trash", "trash " + path, false)
            addSuggestion("rm -r -> trash", "rm -r " + path, false)
            addSuggestion("sel add", "sel add " + path, false)
            return
        }
        if (isLikelyText(file)) addSuggestion("edit", "edit " + path, false)
        addSuggestion("open", "open " + path, false)
        addSuggestion("share", "share " + path, false)
        addSuggestion("trash", "trash " + path, false)
        addSuggestion("rm -> trash", "rm " + path, false)
        addSuggestion("sel add", "sel add " + path, false)
    }

    private fun suggestSelection(input: kotlin.String): Boolean {
        val args = splitArgs(input)
        val endedWithSpace = input.endsWith(" ")
        val command = if (args.isEmpty()) "sel" else args.get(0).lowercase()
        if ("sel" != command) return false
        if (args.size == 1 && endedWithSpace) {
            suggestSelectionOps("")
            return true
        }
        if (args.size == 2 && !endedWithSpace) {
            suggestSelectionOps(args.get(1))
            return true
        }
        val op = if (args.size > 1) args.get(1).lowercase() else ""
        if ("list" == op || "clear" == op || "trash" == op || "share" == op) return true
        if ("zip" == op) {
            addSuggestion("archive.zip", "sel zip archive.zip", false, false)
            return true
        }
        return false
    }

    private fun suggestSelectionOps(prefix: kotlin.String) {
        val lower = prefix.lowercase()
        val ops = arrayOf(
            "add",
            "rm",
            "list",
            "clear",
            "trash",
            "share",
            "zip",
            "cp",
            "mv"
        )
        for (op in ops) {
            if (op!!.startsWith(lower)) {
                val execute = "list" == op || "clear" == op || "trash" == op || "share" == op
                addSuggestion(op, if (execute) "sel " + op else "sel " + op + " ", execute)
            }
        }
    }

    private fun suggestFind(input: kotlin.String) {
        val args = splitArgs(input)
        val last = if (args.isEmpty()) "" else args.get(args.size - 1).lowercase()
        val endedWithSpace = input.endsWith(" ")
        val lower = input.lowercase()
        if (lower.endsWith("--type ")) {
            for (type in arrayOf<kotlin.String>(
                "image",
                "video",
                "audio",
                "doc",
                "dir",
                "file",
                "archive"
            )) {
                addSuggestion(type, input + type, false)
            }
            return
        }
        if (lower.endsWith("--size ")) {
            for (size in arrayOf<kotlin.String>("+10M", "+100M", "-500K", "-10M")) {
                addSuggestion(size, input + size, false)
            }
            return
        }
        if (lower.endsWith("--limit ") || lower.endsWith("-max ")) {
            for (limit in arrayOf<kotlin.String>("50", "100", "400", "1000")) {
                addSuggestion(limit, input + limit, false)
            }
            return
        }
        if (lower.endsWith("-name ") || lower.endsWith("--name ")) {
            for (pattern in arrayOf<kotlin.String>("*.xml", "*.txt", "*.jpg", "*.pdf", ".*")) {
                addSuggestion(pattern, input + pattern, false, false)
            }
            return
        }
        if (endedWithSpace || last.startsWith("-")) {
            val base =
                if (input.endsWith(" ")) input else input.substring(0, input.length - last.length)
            val flags = arrayOf("-name", "--type", "--size", "--limit", "-x", "-a")
            for (flag in flags) {
                if (endedWithSpace || flag!!.startsWith(last)) addSuggestion(
                    flag!!,
                    base + flag + " ",
                    false
                )
            }
        } else {
            val target = shellSuggestionTarget(input)
            suggestChildren(target.prefix, false, false, target.commandPrefix)
        }
    }

    private fun suggestTree(input: kotlin.String) {
        val args = splitArgs(input)
        val last = if (args.isEmpty()) "" else args.get(args.size - 1)
        val lower = input.lowercase()
        val endedWithSpace = input.endsWith(" ")
        if (lower.endsWith("-l ")) {
            for (depth in arrayOf<kotlin.String>("1", "2", "3", "4")) addSuggestion(
                depth,
                input + depth,
                false
            )
            return
        }
        if (lower.endsWith("--sort ")) {
            for (sort in arrayOf<kotlin.String>("name", "size", "mtime")) addSuggestion(
                sort,
                input + sort,
                false
            )
            return
        }
        if (input.endsWith("-P ") || input.endsWith("-I ")) {
            for (pattern in arrayOf<kotlin.String>("*.xml", "*.txt", "*.jpg", ".*")) {
                addSuggestion(pattern, input + pattern, false, false)
            }
            return
        }
        if (endedWithSpace || last.startsWith("-")) {
            val base =
                if (input.endsWith(" ")) input else input.substring(0, input.length - last.length)
            val prefix = if (endedWithSpace) "" else last
            val flags = arrayOf(
                "-L",
                "-a",
                "-d",
                "-f",
                "-h",
                "-s",
                "-D",
                "-F",
                "-i",
                "-r",
                "--dirsfirst",
                "--ignore-case",
                "--noreport",
                "-P",
                "-I",
                "--sort"
            )
            for (flag in flags) {
                if (prefix.length == 0 || flag!!.lowercase().startsWith(prefix.lowercase())) {
                    addSuggestion(flag!!, base + flag + " ", false)
                }
            }
        }
    }

    private fun lastUnquotedSpace(value: kotlin.String): Int {
        var quoted = false
        var escaped = false
        for (i in value.length - 1 downTo 0) {
            val c = value.get(i)
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                quoted = !quoted
            } else if (Character.isWhitespace(c) && !quoted) {
                return i
            }
        }
        return -1
    }

    private fun shellSuggestionTarget(input: kotlin.String): SuggestionTarget {
        val split = lastUnquotedSpace(input)
        val prefix = if (split >= 0) input.substring(split + 1).trim { it <= ' ' } else ""
        val commandPrefix = if (split >= 0) input.substring(0, split + 1) else input + " "
        if (prefix.startsWith("-")) {
            return SuggestionTarget("", if (input.endsWith(" ")) input else input + " ")
        }
        return SuggestionTarget(prefix, commandPrefix)
    }

    private fun addSuggestion(
        label: kotlin.String,
        value: kotlin.String?,
        execute: Boolean,
        commandChip: Boolean = true
    ) {
        val chip = label(label.uppercase(), 12, true)
        chip.setGravity(Gravity.CENTER)
        chip.setPadding(dp(12), 0, dp(12), 0)
        chip.setTag(commandChip)
        styleSuggestionChip(chip, commandChip)
        chip.setOnClickListener(View.OnClickListener { v: View? ->
            inputView!!.setText(value)
            inputView!!.setSelection(inputView!!.getText().length)
            if (execute) runInput(value)
        })
        val lp = LinearLayout.LayoutParams(-2, dp(28))
        lp.setMargins(0, dp(3), dp(8), dp(3))
        suggestionsGroup!!.addView(chip, lp)
    }

    private fun resolve(raw: kotlin.String?): File? {
        val value = unquote(if (raw == null) "" else raw.trim { it <= ' ' })
        if (value.length == 0) return currentDirectory
        val file =
            if (value.startsWith(File.separator)) File(value) else File(currentDirectory, value)
        try {
            return file.getCanonicalFile()
        } catch (e: Exception) {
            return file.getAbsoluteFile()
        }
    }

    private fun canListDirectory(directory: File?): Boolean {
        return directory != null && directory.exists() && directory.isDirectory() && directory.listFiles() != null
    }

    private fun addRecentDirectory(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) return
        val key = pathKey(directory)
        recentPaths.remove(key)
        recentPaths.add(0, key)
        while (recentPaths.size > RECENT_LIMIT) recentPaths.removeAt(recentPaths.size - 1)
    }

    private fun runRecentCommand(args: MutableList<kotlin.String>?) {
        val op = if (args == null || args.isEmpty()) "recent" else args.get(0).lowercase()
        if ("back" == op) {
            if (recentPaths.isEmpty()) {
                showTerminalPopup("recent", "No previous directory")
                return
            }
            changeDirectory(recentPaths.get(0))
            return
        }
        if (args != null && args.size > 1) {
            val needle = args.get(1).lowercase()
            for (path in recentPaths) {
                if (path.lowercase().contains(needle)) {
                    changeDirectory(path)
                    return
                }
            }
            showTerminalPopup("recent", "No recent match: " + args.get(1))
            return
        }
        val out = SpannableStringBuilder("recent:")
        if (recentPaths.isEmpty()) out.append("\nempty")
        for (path in recentPaths) appendRecentLine(out, path)
        out.append("\n\nrecent [text]\nback")
        print(out)
    }

    private fun appendRecentLine(out: SpannableStringBuilder, path: kotlin.String) {
        out.append('\n')
        val start = out.length
        out.append(path)
        out.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                seed("cd " + quoteIfNeeded(path))
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.setColor(outputTextColor)
                ds.setUnderlineText(true)
            }
        }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun resolveStartDirectory(intent: Intent?): File? {
        var raw = if (intent == null) null else intent.getStringExtra(EXTRA_PATH)
        if (TextUtils.isEmpty(raw)) {
            raw = getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            ).getString(PREF_LAUNCH_PREFIX + EXTRA_PATH, null)
        }
        val fallback = sharedStorageRoot()
        var start = if (raw == null || raw.length == 0) fallback else File(raw)
        if ("/storage/emulated" == start!!.getAbsolutePath()) {
            start = fallback
        }
        return if (canListDirectory(start)) start else fallback
    }

    private fun handleIncomingCommand(intent: Intent?) {
        val command = if (intent == null) null else intent.getStringExtra(EXTRA_COMMAND)
        if (!TextUtils.isEmpty(command)) execute(command!!)
    }

    private fun hasStorageAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureStorageAccess() {
        if (hasStorageAccess()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openStorageAccessSettings()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<kotlin.String>(Manifest.permission.READ_EXTERNAL_STORAGE),
                7
            )
        }
        Toast.makeText(this, "Grant storage access for Re:T-UI Files.", Toast.LENGTH_LONG).show()
    }

    private fun openStorageAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(Uri.parse("package:" + getPackageName()))
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<kotlin.String>(Manifest.permission.READ_EXTERNAL_STORAGE),
                7
            )
        }
    }

    private fun sharedStorageRoot(): File? {
        val root = Environment.getExternalStorageDirectory()
        if (root != null && "/storage/emulated" == root.getAbsolutePath()) {
            val userRoot = File(root, "0")
            if (userRoot.exists()) return userRoot
        }
        val direct = File("/storage/emulated/0")
        return if (direct.exists()) direct else root
    }

    private fun uriFor(file: File): Uri? {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file)
    }

    private fun mimeFor(file: File): kotlin.String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(file.getName())
        val type = if (ext == null) null else MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext.lowercase())
        return if (type == null) "*/*" else type
    }

    private fun print(text: kotlin.String?) {
        print(text as CharSequence?)
    }

    private fun print(text: CharSequence?) {
        commandOutputVisible = true
        setRailsVisible(this.isLandscapeLayout)
        editorFile = null
        setPreviewTitle("OUTPUT")
        if (outputView != null) outputView!!.setVisibility(View.VISIBLE)
        if (fileRowsView != null) {
            fileRowsView!!.removeAllViews()
            fileRowsView!!.setVisibility(View.GONE)
        }
        if (previewImageView != null) {
            previewImageView!!.setImageDrawable(null)
            previewImageView!!.setVisibility(View.GONE)
        }
        hidePreviewEditor()
        outputView!!.setText(if (text == null) "" else text, TextView.BufferType.SPANNABLE)
        outputView!!.setMovementMethod(LinkMovementMethod.getInstance())
        if (outputScroll != null) outputScroll!!.post(Runnable { outputScroll!!.fullScroll(View.FOCUS_UP) })
    }

    private fun printPreview(text: CharSequence?) {
        print(text)
        setPreviewTitle("PREVIEW")
    }

    private fun hidePreviewEditor() {
        if (previewEditorView != null) {
            previewEditorView!!.setText("")
            previewEditorView!!.setVisibility(View.GONE)
            previewEditorView!!.setEnabled(true)
        }
        if (previewActionsView != null) previewActionsView!!.setVisibility(View.GONE)
        editorFile = null
    }

    private fun setPreviewTitle(title: kotlin.String?) {
        if (previewTitleView != null && title != null) {
            previewTitleView!!.setText(title)
        }
    }

    private fun savePreviewEditor() {
        if (editorFile == null || previewEditorView == null) {
            Toast.makeText(this, "No editable file selected.", Toast.LENGTH_SHORT).show()
            return
        }
        val savedFile = editorFile
        try {
            FileOutputStream(editorFile, false).use { out ->
                out.write(
                    previewEditorView!!.getText().toString().toByteArray(StandardCharsets.UTF_8)
                )
                out.flush()
                if (outputView != null) outputView!!.setText(fileInfo(savedFile!!) + "\n\nsaved")
                Toast.makeText(this, "Saved " + savedFile!!.getName(), Toast.LENGTH_SHORT).show()
                renderListing((if (activeTreeOptions == null) TreeOptions.Companion.defaultListing() else activeTreeOptions)!!)
                previewResolvedFile(savedFile)
                updateSuggestions(if (inputView == null) "" else inputView!!.getText().toString())
            }
        } catch (e: Exception) {
            showTerminalPopup("save", "Could not save " + savedFile!!.getName() + ":\n" + e.message)
        }
    }

    private fun renderFileRows(rows: MutableList<TreeRow>, report: kotlin.String?) {
        commandOutputVisible = false
        previewFile = null
        editorFile = null
        highlightedPreviewFile = null
        setRailsVisible(true)
        setPreviewTitle(if (this.isLandscapeLayout) "PREVIEW" else null)
        if (outputView != null) {
            outputView!!.setVisibility(if (TextUtils.isEmpty(report)) View.GONE else View.VISIBLE)
            outputView!!.setText(if (report == null) "" else report)
        }
        if (previewImageView != null) {
            previewImageView!!.setImageDrawable(null)
            previewImageView!!.setVisibility(View.GONE)
        }
        hidePreviewEditor()
        if (fileRowsView == null) return
        fileRowsView!!.removeAllViews()
        fileRowsView!!.setVisibility(View.VISIBLE)
        setTreeBottomSpacerHeight(0)
        for (row in rows) {
            fileRowsView!!.addView(fileRowView(row), LinearLayout.LayoutParams(-1, -2))
        }
        buildAlphaRail(rows)
        buildPinnedRail()
        val scroll = activeTreeScroll()
        if (scroll != null) scroll.post(Runnable { scroll.fullScroll(View.FOCUS_UP) })
    }

    private fun setRailsVisible(visible: Boolean) {
        if (alphaRailHost != null) alphaRailHost!!.setVisibility(if (visible) View.VISIBLE else View.GONE)
        if (pinnedRailHost != null) pinnedRailHost!!.setVisibility(if (visible) View.VISIBLE else View.GONE)
    }

    private fun buildAlphaRail(rows: MutableList<TreeRow>?) {
        if (alphaRailView == null) return
        visibleSections.clear()
        visibleSectionRows.clear()
        val items = ArrayList<RailItem>()
        if (rows == null) {
            alphaRailView!!.setItems(items)
            return
        }
        for (i in rows.indices) {
            val row: TreeRow? = rows.get(i)
            if (row == null || row.file == null || row.file == currentDirectory!!.getParentFile()) continue
            val section = sectionForFile(row.file)
            if (!visibleSections.contains(section)) {
                visibleSections.add(section)
                visibleSectionRows.add(i)
            }
        }
        selectedSection = if (visibleSections.isEmpty()) null else visibleSections.get(0)
        for (i in visibleSections.indices) {
            val section = visibleSections.get(i)
            val rowIndex = visibleSectionRows.get(i)!!
            items.add(
                RailItem(
                    section,
                    section == selectedSection,
                    9.5f,
                    Runnable { jumpToRow(rowIndex, section) },
                    null
                )
            )
        }
        alphaRailView!!.setItems(items, true)
    }

    private fun buildPinnedRail() {
        if (pinnedRailView == null) return
        val items = ArrayList<RailItem>()
        addHomeTab(items)
        addFavoritePickerTab(items)
        pinnedRailView!!.setItems(items, false)
    }

    private fun addHomeTab(items: ArrayList<RailItem>?) {
        val home = sharedStorageRoot()
        if (items == null || home == null || !home.exists() || !home.isDirectory()) return
        items.add(
            RailItem(
                ICON_HOME,
                pathKey(home) == pathKey(currentDirectory!!),
                12f,
                Runnable { changeDirectory(home.getAbsolutePath()) },
                null,
                true
            )
        )
    }

    private fun addFavoritePickerTab(items: ArrayList<RailItem>?) {
        if (items == null) return
        val favorites = loadValidFavorites()
        items.add(
            RailItem(
                ICON_STAR,
                isFavoriteDirectory(currentDirectory, favorites),
                12f,
                Runnable { this.showFavoritesInTerminal() },
                Runnable { seed("fav here") },
                true
            )
        )
    }

    private fun buildPinnedRailLegacy() {
        if (pinnedRail == null) return
        pinnedRail.removeAllViews()
        addHomeTabLegacy()
        addFavoritePickerTabLegacy()
    }

    private fun addHomeTabLegacy() {
        val home = sharedStorageRoot()
        if (home == null || !home.exists() || !home.isDirectory()) return
        val tab = sideTab(ICON_HOME, 12f)
        tab.setTypeface(iconTypeface, Typeface.NORMAL)
        styleRailTab(tab, pathKey(home) == pathKey(currentDirectory!!))
        tab.setOnClickListener(View.OnClickListener { v: View? -> changeDirectory(home.getAbsolutePath()) })
        pinnedRail!!.addView(tab)
    }

    private fun addFavoritePickerTabLegacy() {
        val tab = sideTab(ICON_STAR, 12f)
        tab.setTypeface(iconTypeface, Typeface.NORMAL)
        styleRailTab(tab, false)
        tab.setOnClickListener(View.OnClickListener { v: View? -> showFavoritesInTerminal() })
        tab.setOnLongClickListener(OnLongClickListener { v: View? ->
            seed("fav here")
            true
        })
        pinnedRail!!.addView(tab)
    }

    private fun loadValidFavorites(): ArrayList<FavoritePath> {
        val valid: ArrayList<FavoritePath> = ArrayList<FavoritePath>()
        for (favorite in loadFavorites()) {
            val dir = File(favorite.path)
            if (dir.exists() && dir.isDirectory()) valid.add(favorite)
        }
        return valid
    }

    private fun isFavoriteDirectory(
        directory: File?,
        favorites: MutableList<FavoritePath>?
    ): Boolean {
        if (directory == null || favorites == null) return false
        val key = pathKey(directory)
        for (favorite in favorites) {
            if (key == pathKey(File(favorite.path))) return true
        }
        return false
    }

    private fun sideTab(label: kotlin.String?, sp: Float): TextView {
        val tab = label(label, Math.round(sp), true)
        tab.setGravity(Gravity.CENTER)
        tab.setSingleLine(true)
        tab.setEllipsize(TextUtils.TruncateAt.END)
        tab.setPadding(dp(2), 0, dp(2), 0)
        val lp = LinearLayout.LayoutParams(-1, dp(34))
        lp.bottomMargin = dp(4)
        tab.setLayoutParams(lp)
        return tab
    }

    private fun styleRailTab(tab: TextView, selected: Boolean) {
        tab.setBackground(
            buttonDrawable(
                if (selected) moduleButtonTextColor else modulePanelColor,
                moduleButtonBorderColor,
                2
            )
        )
        tab.setTextColor(if (selected) modulePanelColor else moduleButtonTextColor)
    }

    private fun jumpToRow(rowIndex: Int, section: kotlin.String?) {
        var rowIndex = rowIndex
        val scroll = activeTreeScroll()
        if (fileRowsView == null || scroll == null || rowIndex < 0 || rowIndex >= fileRowsView!!.getChildCount()) return
        selectedSection = section
        buildAlphaRailFromCurrentRows()
        val renderedIndex = firstRenderedRowIndexForSection(section)
        if (renderedIndex >= 0) rowIndex = renderedIndex
        val targetRowIndex = rowIndex
        val child = fileRowsView!!.getChildAt(rowIndex)
        val spacerHeight = max(0, scroll.getHeight() - child.getHeight() - dp(24))
        setTreeBottomSpacerHeight(spacerHeight)
        scroll.postDelayed(Runnable {
            val targetChild = fileRowsView!!.getChildAt(targetRowIndex)
            if (targetChild == null) return@Runnable
            val target = fileRowsView!!.getTop() + targetChild.getTop()
            scroll.scrollTo(0, max(0, target))
            scroll.postDelayed(Runnable { alignRenderedRowToTop(scroll, targetRowIndex) }, 32L)
        }, 32L)
    }

    private fun alignRenderedRowToTop(scroll: ScrollView?, rowIndex: Int) {
        if (scroll == null || fileRowsView == null || rowIndex < 0 || rowIndex >= fileRowsView!!.getChildCount()) return
        val child = fileRowsView!!.getChildAt(rowIndex)
        if (child == null) return
        val childLoc = IntArray(2)
        val scrollLoc = IntArray(2)
        child.getLocationInWindow(childLoc)
        scroll.getLocationInWindow(scrollLoc)
        val delta = childLoc[1] - scrollLoc[1]
        if (abs(delta) > dp(1)) scroll.scrollBy(0, delta)
    }

    private fun firstRenderedRowIndexForSection(section: kotlin.String?): Int {
        if (fileRowsView == null || TextUtils.isEmpty(section)) return -1
        for (i in 0..<fileRowsView!!.getChildCount()) {
            val child = fileRowsView!!.getChildAt(i)
            val tag = if (child == null) null else child.getTag()
            if (tag == null) continue
            val file = File(tag.toString())
            if (section == sectionForFile(file)) return i
        }
        return -1
    }

    private fun setTreeBottomSpacerHeight(height: Int) {
        if (treeBottomSpacer == null) return
        val params = treeBottomSpacer!!.getLayoutParams()
        if (params == null) {
            treeBottomSpacer!!.setLayoutParams(LinearLayout.LayoutParams(-1, max(0, height)))
            return
        }
        val nextHeight = max(0, height)
        if (params.height != nextHeight) {
            params.height = nextHeight
            treeBottomSpacer!!.setLayoutParams(params)
            if (outputContainer != null) outputContainer!!.requestLayout()
        }
    }

    private fun buildAlphaRailFromCurrentRows() {
        if (alphaRailView == null) return
        val items = ArrayList<RailItem>()
        for (i in visibleSections.indices) {
            val section = visibleSections.get(i)
            val rowIndex = visibleSectionRows.get(i)!!
            items.add(
                RailItem(
                    section,
                    section == selectedSection,
                    9.5f,
                    Runnable { jumpToRow(rowIndex, section) },
                    null
                )
            )
        }
        alphaRailView!!.setItems(items, false)
    }

    private fun sectionForFile(file: File?): kotlin.String {
        val name = if (file == null) "" else file.getName()
        if (name.length == 0) return "#"
        val first = name.get(0).uppercaseChar()
        if (first < 'A' || first > 'Z') return "#"
        return first.toString()
    }

    private fun loadFavorites(): ArrayList<FavoritePath> {
        val favorites: ArrayList<FavoritePath> = ArrayList<FavoritePath>()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw: kotlin.String = prefs.getString(PREF_FAVORITES, "")!!
        if (!TextUtils.isEmpty(raw)) {
            val lines = raw.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab >= line.length - 1) continue
                val label = normalizeFavoriteLabel(line.substring(0, tab))
                val path = line.substring(tab + 1)
                if (label.length > 0 && path.length > 0) favorites.add(FavoritePath(label, path))
            }
        }
        if (!prefs.getBoolean(PREF_DEFAULT_FAVORITES_IMPORTED, false)) {
            addDefaultFavorites(favorites)
            prefs.edit()
                .putString(PREF_FAVORITES, serializeFavorites(favorites))
                .putBoolean(PREF_DEFAULT_FAVORITES_IMPORTED, true)
                .apply()
        }
        return favorites
    }

    private fun saveFavorites(favorites: MutableList<FavoritePath>?) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(PREF_FAVORITES, serializeFavorites(favorites)).apply()
    }

    private fun serializeFavorites(favorites: MutableList<FavoritePath>?): kotlin.String {
        val out = StringBuilder()
        if (favorites != null) {
            for (favorite in favorites) {
                if (favorite == null || TextUtils.isEmpty(favorite.label) || TextUtils.isEmpty(
                        favorite.path
                    )
                ) continue
                out.append(normalizeFavoriteLabel(favorite.label)).append('\t')
                    .append(favorite.path).append('\n')
            }
        }
        return out.toString()
    }

    private fun addDefaultFavorites(favorites: ArrayList<FavoritePath>?) {
        val root = sharedStorageRoot()
        addDefaultFavorite(favorites, "DL", File(root, "Download"))
        addDefaultFavorite(favorites, "RET", File(root, "Re-T-UI"))
        addDefaultFavorite(favorites, "R:T", File(root, "Re:T-UI"))
        addDefaultFavorite(favorites, "AND", File(root, "Android"))
    }

    private fun addDefaultFavorite(
        favorites: ArrayList<FavoritePath>?,
        label: kotlin.String?,
        directory: File?
    ) {
        if (favorites == null || directory == null || !directory.exists() || !directory.isDirectory()) return
        val key = normalizeFavoriteLabel(label)
        if (key.length == 0 || !isFavoriteLabelFree(key, favorites)) return
        favorites.add(FavoritePath(key, pathKey(directory)))
    }

    private fun upsertFavorite(favorites: ArrayList<FavoritePath>, incoming: FavoritePath) {
        for (favorite in favorites) {
            if (favorite.label.equals(incoming.label, ignoreCase = true)) {
                favorite.path = incoming.path
                return
            }
        }
        favorites.add(incoming)
    }

    private fun favoriteListText(): kotlin.String {
        val favorites = loadFavorites()
        if (favorites.isEmpty()) return "No favorites.\nUse: fav here"
        val out = StringBuilder("favorites:")
        for (favorite in favorites) {
            out.append('\n').append(favorite.label).append(" -> ").append(favorite.path)
        }
        out.append("\n\n").append(favoriteUsageText())
        return out.toString()
    }

    private fun favoriteUsageText(): kotlin.String {
        return ("fav commands:\n"
                + "fav here                 add current folder\n"
                + "fav add [label] [path]   add or update favorite\n"
                + "fav go [label]           jump to favorite\n"
                + "fav rm [label]           remove favorite\n"
                + "fav rename [old] [new]   rename favorite\n"
                + "fav list                 show favorites")
    }

    private fun showFavoritesInTerminal(status: kotlin.String? = null) {
        val favorites = loadFavorites()
        val out = SpannableStringBuilder()
        if (!TextUtils.isEmpty(status)) {
            out.append(status).append("\n\n")
        }
        out.append("favorites:\n")
        if (favorites.isEmpty()) {
            out.append("No favorites yet.\n")
        } else {
            for (favorite in favorites) appendFavoriteLine(out, favorite)
        }
        out.append("\n").append(favoriteUsageText())
        print(out)
    }

    private fun appendFavoriteLine(out: SpannableStringBuilder, favorite: FavoritePath) {
        val start = out.length
        out.append(favorite.label).append(" -> ").append(favorite.path)
        out.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                seed("cd " + quoteIfNeeded(favorite.path))
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.setColor(outputTextColor)
                ds.setUnderlineText(true)
            }
        }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.append("\n")
    }

    private fun normalizeFavoriteLabel(raw: kotlin.String?): kotlin.String {
        if (raw == null) return ""
        val upper = raw.trim { it <= ' ' }.uppercase()
        val out = StringBuilder()
        var i = 0
        while (i < upper.length && out.length < 4) {
            val c = upper.get(i)
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == ':' || c == '_') out.append(
                c
            )
            i++
        }
        return out.toString()
    }

    private fun autoFavoriteLabel(
        directory: File?,
        existing: MutableList<FavoritePath>?
    ): kotlin.String {
        var base = normalizeFavoriteLabel(if (directory == null) "FAV" else directory.getName())
        if (base.length == 0) base = "FAV"
        if (isFavoriteLabelFree(base, existing)) return base
        val stem = if (base.length > 3) base.substring(0, 3) else base
        for (i in 2..9) {
            val candidate = normalizeFavoriteLabel(stem + i)
            if (isFavoriteLabelFree(candidate, existing)) return candidate
        }
        return normalizeFavoriteLabel(stem + "X")
    }

    private fun isFavoriteLabelFree(
        label: kotlin.String?,
        existing: MutableList<FavoritePath>?
    ): Boolean {
        if (existing == null) return true
        for (favorite in existing) {
            if (favorite.label.equals(label, ignoreCase = true)) return false
        }
        return true
    }

    private fun fileRowView(row: TreeRow): View {
        val line = LinearLayout(this)
        line.setOrientation(LinearLayout.HORIZONTAL)
        line.setGravity(Gravity.CENTER_VERTICAL)
        line.setPadding(0, dp(1), 0, dp(1))
        line.setTag(if (row.file == null) "" else pathKey(row.file))
        line.setOnClickListener(View.OnClickListener { v: View? -> handleRowClick(row) })
        styleFileRowSelection(line)

        val size = if (row.directory) directoryRowSizeSp() else fileRowSizeSp()
        val prefix = label(row.prefix, size, false)
        prefix.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        prefix.setIncludeFontPadding(false)
        line.addView(prefix, LinearLayout.LayoutParams(-2, -2))

        if (!TextUtils.isEmpty(row.icon)) {
            val icon = label(row.icon, size + 1, false)
            icon.setTypeface(iconTypeface, Typeface.NORMAL)
            icon.setGravity(Gravity.CENTER)
            icon.setIncludeFontPadding(false)
            val iconParams = LinearLayout.LayoutParams(dp(20), -2)
            iconParams.rightMargin = dp(4)
            line.addView(icon, iconParams)
        }

        val name = label(row.name, size, row.directory)
        name.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        name.setSingleLine(false)
        name.setIncludeFontPadding(false)
        line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        return line
    }

    private fun refreshTreeSelection() {
        if (fileRowsView == null) return
        for (i in 0..<fileRowsView!!.getChildCount()) {
            val child = fileRowsView!!.getChildAt(i)
            styleFileRowSelection(child)
        }
    }

    private fun styleFileRowSelection(rowView: View?) {
        if (rowView == null) return
        val tag = rowView.getTag()
        val selected =
            if (highlightedPreviewFile == null) null else pathKey(highlightedPreviewFile!!)
        val active = selected != null && selected == tag.toString()
        if (!active) {
            rowView.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        rowView.setBackground(
            buttonDrawable(
                moduleButtonBgColor,
                moduleButtonBorderColor,
                max(2, moduleCornerRadiusDp)
            )
        )
    }

    private fun fileRowSizeSp(): Int {
        return clamp(inputFontSizeSp + 1, 13, 20)
    }

    private fun directoryRowSizeSp(): Int {
        return clamp(inputFontSizeSp + 2, 14, 22)
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return max(min, min(max, value))
    }

    private fun handleRowClick(row: TreeRow?) {
        if (row == null || row.file == null) return
        highlightedPreviewFile = row.file
        refreshTreeSelection()
        if (row.directory) {
            if (row.file == currentDirectory!!.getParentFile()) {
                seed("cd ..")
            } else {
                seed("cd " + commandPath(row.file))
            }
        } else {
            seed("preview " + commandPath(row.file))
        }
    }

    private fun seed(value: kotlin.String?) {
        inputView!!.setText(value)
        inputView!!.setSelection(inputView!!.getText().length)
        inputView!!.requestFocus()
    }

    private fun refocusInput() {
        if (inputView == null) return
        inputView!!.postDelayed(Runnable {
            inputView!!.requestFocus()
            inputView!!.setSelection(inputView!!.getText().length)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
            if (imm != null) imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }, 40)
    }

    private fun addTool(
        parent: LinearLayout,
        label: kotlin.String?,
        icon: kotlin.String?,
        listener: View.OnClickListener?
    ) {
        val view = label(icon, 16, false)
        view.setGravity(Gravity.CENTER)
        view.setSingleLine(true)
        view.setIncludeFontPadding(false)
        view.setTypeface(iconTypeface, Typeface.NORMAL)
        view.setContentDescription(label)
        view.setPadding(0, 0, 0, 0)
        styleToolButton(view)
        view.setOnClickListener(listener)
        toolButtons.add(view)
        val params = LinearLayout.LayoutParams(0, -1, 1f)
        params.setMargins(dp(3), 0, dp(3), 0)
        parent.addView(view, params)
    }

    private fun label(text: kotlin.String?, sp: Int, bold: Boolean): TextView {
        val view = TextView(this)
        view.setText(text)
        view.setTextColor(textColor)
        view.setTextSize(sp.toFloat())
        view.setTypeface(appTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        view.setIncludeFontPadding(true)
        return view
    }

    private fun stylePanel(view: View, translucent: Boolean = false) {
        view.setBackground(panelDrawable(if (translucent) PanelRole.OUTPUT else PanelRole.MODULE))
    }

    private fun stylePanel(view: View, role: PanelRole?) {
        view.setBackground(panelDrawable(role))
    }

    private fun panelDrawable(translucent: Boolean): Drawable {
        return panelDrawable(if (translucent) PanelRole.OUTPUT else PanelRole.MODULE)
    }

    private fun panelDrawable(role: PanelRole?): Drawable {
        var baseFill = modulePanelColor
        var stroke = moduleBorderColor
        if (role == PanelRole.OUTPUT) {
            baseFill = outputPanelColor
            stroke = outputBorderColor
        } else if (role == PanelRole.HEADER) {
            baseFill = headerPanelColor
            stroke = moduleBorderColor
        } else if (role == PanelRole.INPUT) {
            baseFill = inputBgColor
            stroke = moduleBorderColor
        }
        val fill = if (role == PanelRole.OUTPUT)
            Color.argb(250, Color.red(baseFill), Color.green(baseFill), Color.blue(baseFill))
        else
            baseFill
        if (cyberdeckMode) {
            return CyberPanelDrawable(
                fill,
                stroke,
                max(1f, dpFloat(if (role == PanelRole.MODULE) 1.5f else 1.2f)),
                true
            )
        }
        val bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        bg.setColor(fill)
        bg.setStroke(dp(1), stroke)
        var radius = moduleCornerRadiusDp
        if (role == PanelRole.OUTPUT) radius = outputCornerRadiusDp
        else if (role == PanelRole.HEADER) radius = headerCornerRadiusDp
        bg.setCornerRadius(dp(radius).toFloat())
        return bg
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            clamp(alpha, 0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun blendColor(from: Int, to: Int, amount: Float): Int {
        val clamped = max(0f, min(1f, amount))
        val r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * clamped)
        val g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * clamped)
        val b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped)
        return Color.rgb(r, g, b)
    }

    private fun luminance(color: Int): Float {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
    }

    private fun derivedToolButtonColor(): Int {
        val base = modulePanelColor
        if (luminance(base) > 0.78f) return blendColor(base, Color.BLACK, 0.10f)
        return blendColor(base, Color.WHITE, 0.18f)
    }

    private fun buttonDrawable(fill: Int, stroke: Int, radiusDp: Int): Drawable {
        if (cyberdeckMode) {
            return CyberPanelDrawable(fill, stroke, max(1f, dpFloat(1f)), false)
        }
        val bg = GradientDrawable()
        bg.setShape(GradientDrawable.RECTANGLE)
        bg.setColor(fill)
        bg.setStroke(dp(1), stroke)
        bg.setCornerRadius(dp(radiusDp).toFloat())
        return bg
    }

    private fun styleToolButton(view: TextView) {
        if (cyberdeckMode) {
            view.setBackground(
                CyberIconFrameDrawable(
                    withAlpha(moduleButtonBorderColor, 230),
                    max(1f, dpFloat(1.2f)),
                    dpFloat(9f),
                    dpFloat(5f)
                )
            )
        } else {
            view.setBackground(
                buttonDrawable(
                    derivedToolButtonColor(),
                    moduleButtonBorderColor,
                    moduleCornerRadiusDp
                )
            )
        }
        view.setTextColor(moduleButtonTextColor)
        view.setTypeface(iconTypeface, Typeface.NORMAL)
        view.setTextSize(16f)
        view.setGravity(Gravity.CENTER)
        view.setIncludeFontPadding(false)
    }

    private fun styleChip(view: TextView, commandChip: Boolean) {
        view.setBackground(
            buttonDrawable(
                if (commandChip) moduleButtonBgColor else modulePanelColor,
                moduleButtonBorderColor,
                moduleCornerRadiusDp
            )
        )
        view.setTextColor(moduleButtonTextColor)
    }

    private fun styleSuggestionChip(view: TextView, commandChip: Boolean) {
        if (cyberdeckMode) {
            view.setBackground(
                CyberPanelDrawable(
                    if (commandChip) moduleButtonBgColor else modulePanelColor,
                    moduleButtonBorderColor,
                    max(1f, dpFloat(1.2f)),
                    true
                )
            )
        } else {
            view.setBackground(
                buttonDrawable(
                    if (commandChip) moduleButtonBgColor else modulePanelColor,
                    moduleButtonBorderColor,
                    moduleCornerRadiusDp
                )
            )
        }
        view.setTextColor(moduleButtonTextColor)
    }

    private fun applyIntentTheme(intent: Intent?) {
        if (intent == null) return
        val hasStyleExtras = hasStyleExtras(intent)
        if (!hasStyleExtras) {
            applyPersistedLaunchTheme()
            appTypeface = resolveTypefaceFromPrefs()
            iconTypeface = resolveIconTypeface()
            return
        }
        bgColor = readColorExtra(
            intent,
            bgColor,
            EXTRA_THEME_BG,
            "background_color",
            "theme_background_color"
        )
        panelColor = readColorExtra(
            intent,
            panelColor,
            EXTRA_TERMINAL_BG,
            "terminal_window_background_color",
            "terminal_window_bg",
            "terminal_background_color"
        )
        textColor = readColorExtra(
            intent,
            textColor,
            EXTRA_THEME_TEXT,
            "output_text_color",
            "theme_text_color"
        )
        borderColor = readColorExtra(
            intent,
            borderColor,
            EXTRA_THEME_BORDER,
            "terminal_border_color",
            "theme_border_color"
        )
        modulePanelColor = readColorExtra(
            intent,
            panelColor,
            EXTRA_MODULE_BG_COLOR,
            "module_background_color",
            EXTRA_TERMINAL_BG,
            "terminal_window_background_color"
        )
        moduleTextColor = readColorExtra(
            intent,
            textColor,
            EXTRA_MODULE_TEXT_COLOR,
            "module_name_text_color",
            "notification_widget_text_color"
        )
        moduleBorderColor = readColorExtra(
            intent,
            borderColor,
            EXTRA_MODULE_BORDER_COLOR,
            "terminal_border_color",
            "module_border"
        )
        headerPanelColor = readColorExtra(
            intent,
            modulePanelColor,
            EXTRA_MODULE_HEADER_BG_COLOR,
            "terminal_header_background_color",
            "terminal_header_tab_background_color"
        )
        headerTextColor = readColorExtra(
            intent,
            moduleTextColor,
            EXTRA_MODULE_HEADER_TEXT_COLOR,
            "terminal_header_text_color",
            "module_header_text"
        )
        moduleButtonBgColor = readColorExtra(
            intent,
            moduleButtonBgColor,
            EXTRA_MODULE_BUTTON_BG_COLOR,
            "module_button_background_color"
        )
        moduleButtonTextColor = readColorExtra(
            intent,
            moduleTextColor,
            EXTRA_MODULE_BUTTON_TEXT_COLOR,
            "module_button_text"
        )
        moduleButtonBorderColor = readColorExtra(
            intent,
            moduleBorderColor,
            EXTRA_MODULE_BUTTON_BORDER_COLOR,
            "module_button_border",
            "terminal_border_color"
        )
        inputBgColor =
            readColorExtra(intent, inputBgColor, EXTRA_INPUT_BG_COLOR, "input_background_color")
        inputTextColor = readColorExtra(intent, textColor, EXTRA_INPUT_TEXT_COLOR, "input_text")
        outputPanelColor = readColorExtra(
            intent,
            panelColor,
            EXTRA_OUTPUT_BG_COLOR,
            "output_background_color",
            "output_bg"
        )
        outputTextColor = readColorExtra(intent, textColor, EXTRA_OUTPUT_TEXT_COLOR, "output_text")
        outputBorderColor = readColorExtra(
            intent,
            moduleBorderColor,
            EXTRA_OUTPUT_BORDER_COLOR,
            "terminal_border_color",
            "output_border"
        )
        textColor = moduleTextColor
        borderColor = moduleBorderColor
        topMarginDp = readIntExtra(intent, topMarginDp, EXTRA_TOP_MARGIN)
        inputFontSizeSp = readIntExtra(intent, inputFontSizeSp, EXTRA_INPUT_FONT_SIZE)
        headerTextSizeSp = readIntExtra(
            intent,
            headerTextSizeSp,
            EXTRA_HEADER_TEXT_SIZE,
            EXTRA_MODULE_HEADER_TEXT_SIZE,
            "header_font_size"
        )
        outputHeaderTextSizeSp = readIntExtra(
            intent,
            outputHeaderTextSizeSp,
            EXTRA_OUTPUT_HEADER_TEXT_SIZE,
            "output_header_text_size_sp"
        )
        outputTextSizeSp = readIntExtra(
            intent,
            outputTextSizeSp,
            EXTRA_OUTPUT_TEXT_SIZE,
            "module_output_text_size",
            "output_font_size"
        )
        moduleCornerRadiusDp = readIntExtra(
            intent,
            moduleCornerRadiusDp,
            EXTRA_MODULE_CORNER_RADIUS,
            "module_radius",
            "corner_radius",
            "corner_radius_dp"
        )
        outputCornerRadiusDp = readIntExtra(
            intent,
            outputCornerRadiusDp,
            EXTRA_OUTPUT_CORNER_RADIUS,
            "output_radius",
            "output_corner_radius_dp",
            EXTRA_MODULE_CORNER_RADIUS
        )
        headerCornerRadiusDp = readIntExtra(
            intent,
            headerCornerRadiusDp,
            EXTRA_HEADER_CORNER_RADIUS,
            "header_radius",
            "header_corner_radius_dp",
            EXTRA_MODULE_CORNER_RADIUS
        )
        terminalBackgroundImage = readStringExtra(
            intent,
            EXTRA_TERMINAL_BG_IMAGE,
            "terminal_bg_path",
            "terminal_background",
            "terminal_background_image",
            "wallpaper_path"
        )
        cyberdeckMode = readBooleanExtra(
            intent,
            cyberdeckMode,
            EXTRA_CYBERDECK_MODE,
            "cyberdeck_mode",
            "cyberdeck",
            "enable_cyberdeck"
        )
        crtFilter =
            readBooleanExtra(intent, crtFilter, EXTRA_CRT_FILTER, "crt_filter", "crt", "enable_crt")
        val topMargins =
            readStringExtra(intent, EXTRA_DISPLAY_MARGIN_TOP_SECTION, EXTRA_DISPLAY_MARGIN_MM)
        val bottomMargins =
            readStringExtra(intent, EXTRA_DISPLAY_MARGIN_BOTTOM_SECTION, EXTRA_DISPLAY_MARGIN_MM)
        if (topMargins != null) displayMarginsMm = parseDisplayMargins(topMargins)
        if (bottomMargins != null) displayBottomMarginsMm = parseDisplayMargins(bottomMargins)
        appTypeface = resolveTypeface(intent)
        iconTypeface = resolveIconTypeface()
        savePersistedLaunchTheme(intent)
    }

    private fun hasStyleExtras(intent: Intent?): Boolean {
        if (intent == null) return false
        val keys = arrayOf(
            EXTRA_THEME_BG,
            EXTRA_THEME_TEXT,
            EXTRA_THEME_BORDER,
            EXTRA_TERMINAL_BG,
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
            EXTRA_TOP_MARGIN,
            EXTRA_INPUT_FONT_SIZE,
            EXTRA_DISPLAY_MARGIN_MM,
            EXTRA_DISPLAY_MARGIN_TOP_SECTION,
            EXTRA_DISPLAY_MARGIN_BOTTOM_SECTION,
            EXTRA_FONT_PATH,
            EXTRA_FONT_NAME,
            EXTRA_MODULE_CORNER_RADIUS,
            EXTRA_OUTPUT_CORNER_RADIUS,
            EXTRA_HEADER_CORNER_RADIUS,
            EXTRA_HEADER_TEXT_SIZE,
            EXTRA_OUTPUT_TEXT_SIZE,
            EXTRA_MODULE_HEADER_TEXT_SIZE,
            EXTRA_OUTPUT_HEADER_TEXT_SIZE,
            EXTRA_TERMINAL_BG_IMAGE,
            EXTRA_CYBERDECK_MODE,
            EXTRA_CRT_FILTER,
            "header_font_size",
            "output_header_text_size_sp",
            "module_output_text_size",
            "output_font_size",
            "module_radius",
            "corner_radius",
            "corner_radius_dp",
            "output_radius",
            "output_corner_radius_dp",
            "header_radius",
            "header_corner_radius_dp",
            "terminal_bg_path",
            "terminal_background",
            "terminal_background_image",
            "wallpaper_path",
            "terminal_window_background_color",
            "terminal_window_bg",
            "terminal_background_color",
            "terminal_border_color",
            "terminal_header_background_color",
            "terminal_header_tab_background_color",
            "module_background_color",
            "module_button_background_color",
            "input_background_color",
            "output_background_color",
            "display_margin_top_section",
            "display_margin_bottom_section",
            "cyberdeck_mode",
            "cyberdeck",
            "enable_cyberdeck",
            "crt_filter",
            "crt",
            "enable_crt"
        )
        for (key in keys) {
            if (key != null && intent.hasExtra(key)) return true
        }
        return false
    }

    private fun applyPersistedLaunchTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        bgColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_BG, bgColor)
        panelColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_TERMINAL_BG, panelColor)
        textColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_TEXT, textColor)
        borderColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_BORDER, borderColor)
        modulePanelColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BG_COLOR, panelColor)
        moduleTextColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_TEXT_COLOR, textColor)
        moduleBorderColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BORDER_COLOR, borderColor)
        headerPanelColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_HEADER_BG_COLOR, modulePanelColor)
        headerTextColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_HEADER_TEXT_COLOR, moduleTextColor)
        moduleButtonBgColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor)
        moduleButtonTextColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleTextColor)
        moduleButtonBorderColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_BORDER_COLOR, moduleBorderColor)
        inputBgColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_BG_COLOR, inputBgColor)
        inputTextColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_TEXT_COLOR, textColor)
        outputPanelColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_BG_COLOR, panelColor)
        outputTextColor = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_TEXT_COLOR, textColor)
        outputBorderColor =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_BORDER_COLOR, moduleBorderColor)
        textColor = moduleTextColor
        borderColor = moduleBorderColor
        topMarginDp = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_TOP_MARGIN, topMarginDp)
        inputFontSizeSp = prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_FONT_SIZE, inputFontSizeSp)
        headerTextSizeSp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp)
        outputHeaderTextSizeSp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_HEADER_TEXT_SIZE, outputHeaderTextSizeSp)
        outputTextSizeSp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp)
        moduleCornerRadiusDp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        outputCornerRadiusDp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        headerCornerRadiusDp =
            prefs.getInt(PREF_LAUNCH_PREFIX + EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        terminalBackgroundImage =
            prefs.getString(PREF_LAUNCH_PREFIX + EXTRA_TERMINAL_BG_IMAGE, terminalBackgroundImage)
        cyberdeckMode = prefs.getBoolean(PREF_LAUNCH_PREFIX + EXTRA_CYBERDECK_MODE, cyberdeckMode)
        crtFilter = prefs.getBoolean(PREF_LAUNCH_PREFIX + EXTRA_CRT_FILTER, crtFilter)
        displayMarginsMm =
            parseDisplayMargins(prefs.getString(PREF_LAUNCH_PREFIX + EXTRA_DISPLAY_MARGIN_MM, null))
        displayBottomMarginsMm = parseDisplayMargins(
            prefs.getString(
                PREF_LAUNCH_PREFIX + EXTRA_DISPLAY_MARGIN_BOTTOM_SECTION,
                null
            )
        )
    }

    private fun savePersistedLaunchTheme(intent: Intent?) {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_BG, bgColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_TERMINAL_BG, panelColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_TEXT, textColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_THEME_BORDER, borderColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BG_COLOR, modulePanelColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_TEXT_COLOR, moduleTextColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BORDER_COLOR, moduleBorderColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_HEADER_BG_COLOR, headerPanelColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_HEADER_TEXT_COLOR, headerTextColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_BG_COLOR, moduleButtonBgColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_TEXT_COLOR, moduleButtonTextColor)
        editor.putInt(
            PREF_LAUNCH_PREFIX + EXTRA_MODULE_BUTTON_BORDER_COLOR,
            moduleButtonBorderColor
        )
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_BG_COLOR, inputBgColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_TEXT_COLOR, inputTextColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_BG_COLOR, outputPanelColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_TEXT_COLOR, outputTextColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_BORDER_COLOR, outputBorderColor)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_TOP_MARGIN, topMarginDp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_INPUT_FONT_SIZE, inputFontSizeSp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_HEADER_TEXT_SIZE, headerTextSizeSp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_HEADER_TEXT_SIZE, outputHeaderTextSizeSp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_TEXT_SIZE, outputTextSizeSp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_MODULE_CORNER_RADIUS, moduleCornerRadiusDp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_OUTPUT_CORNER_RADIUS, outputCornerRadiusDp)
        editor.putInt(PREF_LAUNCH_PREFIX + EXTRA_HEADER_CORNER_RADIUS, headerCornerRadiusDp)
        editor.putString(
            PREF_LAUNCH_PREFIX + EXTRA_DISPLAY_MARGIN_MM,
            displayMarginsMm[0].toString() + "," + displayMarginsMm[1] + "," + displayMarginsMm[2] + "," + displayMarginsMm[3]
        )
        editor.putString(
            PREF_LAUNCH_PREFIX + EXTRA_DISPLAY_MARGIN_BOTTOM_SECTION,
            displayBottomMarginsMm[0].toString() + "," + displayBottomMarginsMm[1] + "," + displayBottomMarginsMm[2] + "," + displayBottomMarginsMm[3]
        )
        editor.putBoolean(PREF_LAUNCH_PREFIX + EXTRA_CYBERDECK_MODE, cyberdeckMode)
        editor.putBoolean(PREF_LAUNCH_PREFIX + EXTRA_CRT_FILTER, crtFilter)
        persistStringExtra(editor, intent, EXTRA_PATH)
        persistStringExtra(editor, intent, EXTRA_FONT_PATH)
        persistStringExtra(editor, intent, EXTRA_FONT_NAME)
        if (!TextUtils.isEmpty(terminalBackgroundImage)) editor.putString(
            PREF_LAUNCH_PREFIX + EXTRA_TERMINAL_BG_IMAGE,
            terminalBackgroundImage
        )
        editor.apply()
    }

    private fun persistStringExtra(
        editor: SharedPreferences.Editor,
        intent: Intent?,
        key: kotlin.String?
    ) {
        val value = if (intent == null) null else intent.getStringExtra(key)
        if (!TextUtils.isEmpty(value)) editor.putString(PREF_LAUNCH_PREFIX + key, value)
    }

    private fun readIntExtra(intent: Intent?, fallback: Int, vararg keys: kotlin.String?): Int {
        if (intent == null || keys == null) return fallback
        for (key in keys) {
            if (key == null || !intent.hasExtra(key)) continue
            val value = if (intent.getExtras() == null) null else intent.getExtras()!!.get(key)
            if (value is Number) return value.toInt()
            try {
                return value.toString().trim { it <= ' ' }.toInt()
            } catch (ignored: Exception) {
                return fallback
            }
        }
        return fallback
    }

    private fun readColorExtra(intent: Intent?, fallback: Int, vararg keys: kotlin.String?): Int {
        return FmVisualInterop.readColorExtra(intent, fallback, *keys)
    }

    private fun readBooleanExtra(
        intent: Intent?,
        fallback: Boolean,
        vararg keys: kotlin.String?
    ): Boolean {
        if (intent == null || keys == null) return fallback
        val extras = intent.getExtras()
        if (extras == null) return fallback
        for (key in keys) {
            if (key == null || !extras.containsKey(key)) continue
            val value = extras.get(key)
            if (value is Boolean) return value
            if (value is Number) return value.toInt() != 0
            if (value != null) {
                val raw = value.toString().trim { it <= ' ' }.lowercase()
                if ("1" == raw || "true" == raw || "yes" == raw || "on" == raw) return true
                if ("0" == raw || "false" == raw || "no" == raw || "off" == raw) return false
            }
        }
        return fallback
    }

    private fun readStringExtra(intent: Intent?, vararg keys: kotlin.String?): kotlin.String? {
        if (intent == null || keys == null) return null
        for (key in keys) {
            val value = if (key == null) null else intent.getStringExtra(key)
            if (!TextUtils.isEmpty(value)) return value
        }
        return null
    }

    private fun resolveTypeface(intent: Intent?): Typeface? {
        val path = if (intent == null) null else intent.getStringExtra(EXTRA_FONT_PATH)
        val name = if (intent == null) null else intent.getStringExtra(EXTRA_FONT_NAME)
        return resolveTypeface(path, name)
    }

    private fun resolveTypefaceFromPrefs(): Typeface? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return resolveTypeface(
            prefs.getString(PREF_LAUNCH_PREFIX + EXTRA_FONT_PATH, null),
            prefs.getString(PREF_LAUNCH_PREFIX + EXTRA_FONT_NAME, null)
        )
    }

    private fun resolveTypeface(path: kotlin.String?, name: kotlin.String?): Typeface? {
        if (!TextUtils.isEmpty(path)) {
            try {
                val font = File(path)
                if (font.exists() && font.length() > 0) {
                    return Typeface.createFromFile(font)
                }
            } catch (ignored: Exception) {
            }
        }
        if ("system".equals(name, ignoreCase = true)) {
            return Typeface.DEFAULT
        }
        try {
            return Typeface.createFromAsset(getAssets(), "lucida_console.ttf")
        } catch (ignored: Exception) {
            return Typeface.MONOSPACE
        }
    }

    private fun resolveIconTypeface(): Typeface? {
        try {
            return Typeface.createFromAsset(getAssets(), "symbols_nerd_font_mono.ttf")
        } catch (ignored: Exception) {
            return appTypeface
        }
    }

    private fun loadLayoutSettings(intent: Intent?) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        landscapeDisplayMarginsMm = parseDisplayMargins(
            prefs.getString(
                PREF_LANDSCAPE_DISPLAY_MARGIN_MM, null
            )
        )
        landscapeBottomDisplayMarginsMm = landscapeDisplayMarginsMm
        landscapeTopMarginDp = prefs.getInt(PREF_LANDSCAPE_TOP_MARGIN, landscapeTopMarginDp)

        val marginExtra = firstExtra(
            intent,
            EXTRA_LANDSCAPE_DISPLAY_MARGIN_MM,
            "landscape_margin_mm",
            "landscape_margins_mm"
        )
        if (marginExtra != null) {
            val value = marginExtra.toString()
            landscapeDisplayMarginsMm = parseDisplayMargins(value)
            landscapeBottomDisplayMarginsMm = landscapeDisplayMarginsMm
            prefs.edit().putString(PREF_LANDSCAPE_DISPLAY_MARGIN_MM, value).apply()
        }

        val topExtra = firstExtra(intent, EXTRA_LANDSCAPE_TOP_MARGIN, "landscape_top_margin_dp")
        val top = parseIntValue(topExtra, -1)
        if (top >= 0) {
            landscapeTopMarginDp = top
            prefs.edit().putInt(PREF_LANDSCAPE_TOP_MARGIN, landscapeTopMarginDp).apply()
        }
    }

    private fun firstExtra(intent: Intent?, vararg keys: kotlin.String?): Any? {
        val extras = if (intent == null) null else intent.getExtras()
        if (extras == null || keys == null) return null
        for (key in keys) {
            if (key != null && extras.containsKey(key)) return extras.get(key)
        }
        return null
    }

    private fun parseIntValue(value: Any?, fallback: Int): Int {
        if (value == null) return fallback
        if (value is Number) return value.toInt()
        try {
            return value.toString().trim { it <= ' ' }.toInt()
        } catch (ignored: Exception) {
            return fallback
        }
    }

    private val isLandscapeLayout: Boolean
        get() = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun activeTopMarginDp(): Int {
        val requested = if (this.isLandscapeLayout) landscapeTopMarginDp else topMarginDp
        // The title and close tabs live 8dp from the overlay top, while the
        // window border must stay lower so the tabs overlap the border instead
        // of drifting into the pane when the launcher's top margin is zero.
        return if (this.isLandscapeLayout) requested else max(18, requested)
    }

    private fun activeDisplayMarginsMm(): IntArray {
        return if (this.isLandscapeLayout) landscapeDisplayMarginsMm else displayMarginsMm
    }

    private fun activeBottomDisplayMarginsMm(): IntArray {
        return if (this.isLandscapeLayout) landscapeBottomDisplayMarginsMm else displayBottomMarginsMm
    }

    private fun applyWindowMargins() {
        if (rootLayoutParams == null || root == null) return
        val top = dp(max(0, activeTopMarginDp()))
        if (rootLayoutParams!!.topMargin != top) {
            rootLayoutParams!!.topMargin = top
            root!!.setLayoutParams(rootLayoutParams)
        }
    }

    private fun applyStagePadding() {
        if (stage == null) return
        val topMargins = activeDisplayMarginsMm()
        val bottomMargins = activeBottomDisplayMarginsMm()
        val horizontalBase = if (this.isLandscapeLayout) 8 else 22
        // Portrait top spacing is supplied by the launcher's top_margin extra.
        val topBase = if (this.isLandscapeLayout) 8 else 0
        val bottomBase = if (this.isLandscapeLayout) 6 else 4
        stage!!.setPadding(
            systemInsetLeft + dp(horizontalBase) + max(
                mmToPx(topMargins[0]),
                mmToPx(bottomMargins[0])
            ),
            systemInsetTop + dp(topBase) + mmToPx(topMargins[1]),
            systemInsetRight + dp(horizontalBase) + max(
                mmToPx(topMargins[2]),
                mmToPx(bottomMargins[2])
            ),
            systemInsetBottom + dp(bottomBase) + mmToPx(bottomMargins[3])
        )
    }

    private fun parseDisplayMargins(raw: kotlin.String?): IntArray {
        val margins = intArrayOf(0, 0, 0, 0)
        if (TextUtils.isEmpty(raw)) return margins
        val parts = raw!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var i = 0
        while (i < margins.size && i < parts.size) {
            try {
                margins[i] = max(0, parts[i].trim { it <= ' ' }.toInt())
            } catch (ignored: Exception) {
                margins[i] = 0
            }
            i++
        }
        return margins
    }

    private fun configureWindow() {
        val window = getWindow()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT)
            window.setNavigationBarColor(Color.BLACK)
        }
    }

    private fun installWindowInsetsHandler() {
        if (stage == null) return
        stage!!.setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener { view: View?, insets: WindowInsets? ->
            applySystemInsets(insets)
            insets!!
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            stage!!.requestApplyInsets()
        }
    }

    private fun applySystemInsets(insets: WindowInsets?) {
        val safe = safeInsets(insets)
        val left = safe[0]
        val top = safe[1]
        val right = safe[2]
        val bottom = safe[3]
        if (left == systemInsetLeft && top == systemInsetTop && right == systemInsetRight && bottom == systemInsetBottom) {
            return
        }
        systemInsetLeft = left
        systemInsetTop = top
        systemInsetRight = right
        systemInsetBottom = bottom
        applyStagePadding()
        applyWindowMargins()
    }

    private fun applyWallpaperBackground() {
        if (stage == null) return
        if (!TextUtils.isEmpty(terminalBackgroundImage)) {
            try {
                val provided = Drawable.createFromPath(terminalBackgroundImage)
                if (provided != null) {
                    getWindow().setBackgroundDrawable(ColorDrawable(bgColor))
                    stage!!.setBackground(provided)
                    return
                }
            } catch (ignored: Exception) {
            }
        }
        getWindow().setBackgroundDrawable(ColorDrawable(bgColor))
        stage!!.setBackgroundColor(bgColor)
    }

    private fun applyCrtOverlay() {
        if (stage == null) return
        if (crtFilter) {
            stage!!.setForeground(CrtOverlayDrawable(this, outputTextColor))
        } else {
            stage!!.setForeground(null)
        }
    }

    private fun bindPanelCutouts(panel: View?, vararg cutoutViews: View?) {
        if (!cyberdeckMode || panel == null || cutoutViews == null) return
        val background = panel.getBackground()
        if (background !is CyberPanelDrawable) return
        val drawable: CyberPanelDrawable = background
        val updater = Runnable {
            if (panel.getWidth() <= 0 || panel.getHeight() <= 0) return@Runnable
            val panelLoc = IntArray(2)
            panel.getLocationOnScreen(panelLoc)
            val top = ArrayList<RectF>()
            val bottom = ArrayList<RectF>()
            for (view in cutoutViews) {
                if (view == null || view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    continue
                }
                val viewLoc = IntArray(2)
                view.getLocationOnScreen(viewLoc)
                val left = (viewLoc[0] - panelLoc[0] - dp(3)).toFloat()
                val right = (viewLoc[0] - panelLoc[0] + view.getWidth() + dp(3)).toFloat()
                val cutout = RectF(
                    max(0f, left),
                    0f,
                    min(panel.getWidth().toFloat(), right),
                    0f
                )
                if (cutout.right <= cutout.left) continue
                val centerY = viewLoc[1] - panelLoc[1] + view.getHeight() / 2f
                if (centerY <= panel.getHeight() / 2f) top.add(cutout)
                else bottom.add(cutout)
            }
            drawable.setCutouts(top, bottom)
        }
        panel.post(updater)
        for (view in cutoutViews) {
            if (view != null) view.post(updater)
        }
    }

    private fun quoteIfNeeded(value: kotlin.String): kotlin.String {
        return if (value.contains(" ")) "\"" + value.replace("\"", "\\\"") + "\"" else value
    }

    private fun commandPath(file: File?): kotlin.String? {
        if (file == null) return ""
        if (file == currentDirectory!!.getParentFile()) return ".."
        val current = pathKey(currentDirectory!!)
        val target = pathKey(file)
        val prefix = if (current.endsWith(File.separator)) current else current + File.separator
        if (target.startsWith(prefix)) {
            return quoteIfNeeded(target.substring(prefix.length))
        }
        return quoteIfNeeded(target)
    }

    private fun unquote(value: kotlin.String): kotlin.String {
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1).replace("\\\"", "\"")
        }
        return value
    }

    private fun pathKey(file: File): kotlin.String {
        try {
            return file.getCanonicalPath()
        } catch (e: Exception) {
            return file.getAbsolutePath()
        }
    }

    private fun dp(value: Int): Int {
        return (value * getResources().getDisplayMetrics().density + 0.5f).toInt()
    }

    private fun dpFloat(value: Float): Float {
        return value * getResources().getDisplayMetrics().density
    }

    private fun sp(value: Float): Float {
        return value * getResources().getDisplayMetrics().scaledDensity
    }

    private fun mmToPx(mm: Int): Int {
        return (mm * getResources().getDisplayMetrics().xdpi / 25.4f + 0.5f).toInt()
    }

    private class TreeOptions {
        var allFiles: Boolean = false
        var dirsOnly: Boolean = false
        var fullPath: Boolean = false
        var humanSize: Boolean = false
        var byteSize: Boolean = false
        var date: Boolean = false
        var typeSuffix: Boolean = false
        var noIndentLines: Boolean = false
        var reverse: Boolean = false
        var dirsFirst: Boolean = true
        var ignoreCase: Boolean = false
        var matchDirs: Boolean = false
        var noReport: Boolean = false
        var maxDepth: Int = 0
        var sort: kotlin.String = "name"
        var error: kotlin.String? = null
        var includePattern: Pattern? = null
        var excludePattern: Pattern? = null

        companion object {
            fun defaultListing(): TreeOptions {
                val options = TreeOptions()
                options.maxDepth = LIST_TREE_DEPTH
                options.noReport = true
                return options
            }

            fun expanded(): TreeOptions {
                val options = TreeOptions()
                options.maxDepth = EXPANDED_TREE_DEPTH
                return options
            }

            fun error(message: kotlin.String?): TreeOptions {
                val options = TreeOptions()
                options.error = message
                return options
            }
        }
    }

    private class TreeStats {
        var directories: Int = 0
        var files: Int = 0
    }

    private class BoundedText(val text: kotlin.String, val bytesRead: Int, val truncated: Boolean)

    private class JsonPreviewState {
        var nodes: Int = 0
        var limited: Boolean = false
    }

    private class PreviewLimitReached : RuntimeException()

    private class CsvPreview {
        val rows: ArrayList<ArrayList<kotlin.String?>> = ArrayList<ArrayList<kotlin.String?>>()
        var columnCount: Int = 0
        var truncatedRows: Boolean = false
        var truncatedColumns: Boolean = false
    }

    private inner class SearchFilter {
        var type: kotlin.String? = null
        var minSize: kotlin.Long? = null
        var maxSize: kotlin.Long? = null

        fun setSize(raw: kotlin.String?): Boolean {
            if (TextUtils.isEmpty(raw)) return false
            val value = raw!!.trim { it <= ' ' }
            val min = value.startsWith("+")
            val max = value.startsWith("-")
            val parsed = parseSizeSpec(if (min || max) value.substring(1) else value)
            if (parsed < 0) return false
            if (min) minSize = parsed
            else if (max) maxSize = parsed
            else {
                minSize = parsed
                maxSize = parsed
            }
            return true
        }

        fun matches(file: File?): Boolean {
            if (file == null) return false
            if (!isType(file, type)) return false
            val size = if (file.isDirectory()) 0 else file.length()
            if (minSize != null && size < minSize!!) return false
            if (maxSize != null && size > maxSize!!) return false
            return true
        }
    }

    private class TreeRow(
        val prefix: kotlin.String?,
        val icon: kotlin.String?,
        val name: kotlin.String?,
        val file: File?,
        val directory: Boolean,
        val expanded: Boolean,
        val hasChildren: Boolean
    )

    private enum class PanelRole {
        MODULE,
        OUTPUT,
        HEADER,
        INPUT
    }

    private class SuggestionTarget(val prefix: kotlin.String, val commandPrefix: kotlin.String)

    private class FavoritePath(var label: kotlin.String, var path: kotlin.String)

    private class TrashEntry(
        val label: kotlin.String,
        val originalPath: kotlin.String,
        val trashedPath: kotlin.String,
        val time: kotlin.Long
    )

    private class SourceSpec(val file: File?, val copyContents: Boolean)

    private class CopyItem(val source: File, val destination: File)

    private class CopyPlan(val move: Boolean) {
        val directories: ArrayList<File> = ArrayList<File>()
        val items: ArrayList<CopyItem> = ArrayList<CopyItem>()
        val moveSources: ArrayList<File> = ArrayList<File>()
        var totalBytes: kotlin.Long = 0
        var overwriteCount: Int = 0
        var error: kotlin.String? = null
    }

    private class DeletePlan {
        val items: ArrayList<File> = ArrayList<File>()
        var totalItems: Int = 0
    }

    private class ZipPlan(val archive: File) {
        val entries: ArrayList<ZipItem> = ArrayList<ZipItem>()
        var totalBytes: kotlin.Long = 0
        var error: kotlin.String? = null
    }

    private class ZipItem(val source: File, val entryName: kotlin.String?, val directory: Boolean)

    private class OperationResult {
        var copied: Int = 0
        var deleted: Int = 0
        var created: Int = 0
        var skipped: Int = 0
        var failed: Int = 0
        var cancelled: Boolean = false
        val failures: ArrayList<kotlin.String?> = ArrayList<kotlin.String?>()

        fun summary(
            primaryLabel: kotlin.String,
            deleteLabel: kotlin.String?,
            copyLabel: kotlin.String?
        ): kotlin.String {
            val out = StringBuilder()
            if (created > 0) out.append("created ").append(created).append(" directories\n")
            if (copied > 0 || primaryLabel.startsWith("copied") || primaryLabel.startsWith("moved")) {
                out.append(primaryLabel).append(": ").append(copied).append('\n')
            } else {
                out.append(copyLabel).append(": ").append(copied).append('\n')
            }
            out.append(deleteLabel).append(": ").append(deleted).append('\n')
            out.append("skipped files: ").append(skipped).append('\n')
            out.append("failed files: ").append(failed)
            if (cancelled) out.append("\ncancelled")
            if (!failures.isEmpty()) {
                out.append("\nfailed paths:")
                for (failure in failures) out.append('\n').append(failure)
            }
            return out.toString()
        }
    }

    private class RailItem @JvmOverloads constructor(
        val label: kotlin.String,
        val selected: Boolean,
        val textSizeSp: Float,
        val click: Runnable?,
        val longClick: Runnable?,
        val icon: Boolean = false
    )

    private inner class SideRailView(context: MainActivity?) : View(context) {
        private val items: ArrayList<RailItem> = ArrayList<RailItem>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val railPath = Path()
        private var scrollOffset = 0f
        private var downY = 0f
        private var startScroll = 0f
        private var dragging = false
        private var longPressed = false
        private var pendingLongPress: Runnable? = null

        init {
            setWillNotDraw(false)
            setClickable(true)
        }

        fun setItems(next: MutableList<RailItem>?) {
            setItems(next, false)
        }

        fun setItems(next: MutableList<RailItem>?, resetScroll: Boolean) {
            items.clear()
            if (next != null) items.addAll(next)
            if (resetScroll) scrollOffset = 0f
            scrollOffset = clampScroll(scrollOffset)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.clipRect(0, 0, getWidth(), getHeight())
            var y = -scrollOffset
            for (item in items) {
                rect.set(0f, y, getWidth().toFloat(), y + railItemHeight())
                if (rect.bottom >= 0 && rect.top <= getHeight()) drawItem(canvas, item, rect)
                y += railItemStride()
            }
            canvas.restore()
        }

        fun drawItem(canvas: Canvas, item: RailItem, bounds: RectF) {
            val radius = dp(2).toFloat()
            if (cyberdeckMode) {
                val cut = min(min(bounds.width(), bounds.height()) * 0.28f, dpFloat(8f))
                railPath.reset()
                railPath.moveTo(bounds.left, bounds.top)
                railPath.lineTo(bounds.right, bounds.top)
                railPath.lineTo(bounds.right, bounds.bottom - cut)
                railPath.lineTo(bounds.right - cut, bounds.bottom)
                railPath.lineTo(bounds.left, bounds.bottom)
                railPath.close()

                paint.setPathEffect(null)
                paint.setStyle(Paint.Style.FILL)
                paint.setColor(if (item.selected) moduleButtonTextColor else modulePanelColor)
                canvas.drawPath(railPath, paint)
                paint.setStyle(Paint.Style.STROKE)
                paint.setStrokeWidth(dpFloat(1f))
                paint.setColor(moduleButtonBorderColor)
                canvas.drawPath(railPath, paint)
                paint.setStrokeWidth(max(1f, dpFloat(0.5f)))
                paint.setColor(withAlpha(moduleButtonBorderColor, 95))
                val y = bounds.top + dpFloat(4f)
                canvas.drawLine(
                    bounds.left + dpFloat(5f),
                    y,
                    bounds.left + bounds.width() * 0.55f,
                    y,
                    paint
                )
            } else {
                paint.setPathEffect(null)
                paint.setStyle(Paint.Style.FILL)
                paint.setColor(if (item.selected) moduleButtonTextColor else modulePanelColor)
                canvas.drawRoundRect(bounds, radius, radius, paint)
                paint.setStyle(Paint.Style.STROKE)
                paint.setStrokeWidth(dp(1).toFloat())
                paint.setColor(moduleButtonBorderColor)
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }

            paint.setStyle(Paint.Style.FILL)
            paint.setStrokeWidth(0f)
            paint.setPathEffect(null)
            paint.setTypeface(if (item.icon) iconTypeface else appTypeface)
            paint.setTextSize(fitTextSize(item.label, item.textSizeSp, bounds.width() - dp(6)))
            paint.setColor(if (item.selected) modulePanelColor else moduleButtonTextColor)
            paint.setTextAlign(Paint.Align.CENTER)
            val fm = paint.getFontMetrics()
            val textY = bounds.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(item.label, bounds.centerX(), textY, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (items.isEmpty()) return true
            when (event.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.getY()
                    startScroll = scrollOffset
                    dragging = false
                    longPressed = false
                    pendingLongPress = Runnable {
                        if (!dragging) {
                            val item = itemAt(downY)
                            if (item != null && item.longClick != null) {
                                longPressed = true
                                item.longClick.run()
                            }
                        }
                    }
                    mainHandler.postDelayed(pendingLongPress!!, 450)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.getY() - downY
                    if (abs(dy) > dp(4)) {
                        dragging = true
                        if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress!!)
                    }
                    scrollOffset = clampScroll(startScroll - dy)
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress!!)
                    pendingLongPress = null
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (pendingLongPress != null) mainHandler.removeCallbacks(pendingLongPress!!)
                    pendingLongPress = null
                    if (!dragging && !longPressed) {
                        val item = itemAt(event.getY())
                        if (item != null && item.click != null) {
                            performClick()
                            item.click.run()
                        }
                    }
                    return true
                }

                else -> return true
            }
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        fun itemAt(y: Float): RailItem? {
            val index = ((y + scrollOffset) / railItemStride()).toInt()
            if (index < 0 || index >= items.size) return null
            val itemTop = index * railItemStride() - scrollOffset
            if (y > itemTop + railItemHeight()) return null
            return items.get(index)
        }

        fun clampScroll(value: Float): Float {
            val max = max(0f, items.size * railItemStride() - dp(4) - getHeight())
            return max(0f, min(value, max))
        }

        fun railItemHeight(): Float {
            return dp(34).toFloat()
        }

        fun railItemStride(): Float {
            return dp(38).toFloat()
        }

        fun fitTextSize(label: kotlin.String?, desiredSp: Float, maxWidth: Float): Float {
            var size = sp(desiredSp)
            paint.setTextSize(size)
            while (size > sp(6f) && paint.measureText(label) > maxWidth) {
                size -= sp(0.5f)
                paint.setTextSize(size)
            }
            return size
        }
    }

    private class CyberPanelDrawable(
        private val fillColor: Int,
        private val borderColor: Int,
        strokeWidthPx: Float,
        private val notch: Boolean
    ) : Drawable() {
        private val strokeWidthPx: Float
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val topCutouts = ArrayList<RectF>()
        private val bottomCutouts = ArrayList<RectF>()

        init {
            this.strokeWidthPx = max(1f, strokeWidthPx)

            fillPaint.setStyle(Paint.Style.FILL)
            fillPaint.setColor(fillColor)

            strokePaint.setStyle(Paint.Style.STROKE)
            strokePaint.setStrokeWidth(this.strokeWidthPx)
            strokePaint.setStrokeJoin(Paint.Join.MITER)
            strokePaint.setColor(borderColor)

            detailPaint.setStyle(Paint.Style.STROKE)
            detailPaint.setStrokeWidth(max(1f, this.strokeWidthPx / 2f))
            detailPaint.setColor(withAlphaComponent(borderColor, 95))

            cutoutPaint.setStyle(Paint.Style.FILL)
            cutoutPaint.setColor(fillColor)
        }

        fun setCutouts(top: MutableList<RectF>?, bottom: MutableList<RectF>?) {
            topCutouts.clear()
            bottomCutouts.clear()
            if (top != null) topCutouts.addAll(top)
            if (bottom != null) bottomCutouts.addAll(bottom)
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = getBounds()
            if (b.isEmpty()) return

            val left = b.left.toFloat()
            val top = b.top.toFloat()
            val right = b.right.toFloat()
            val bottom = b.bottom.toFloat()
            val width = b.width().toFloat()
            val height = b.height().toFloat()
            val cornerCut =
                min(min(max(8f, height * 0.34f), width * 0.18f), max(20f, strokeWidthPx * 8f))
            val notchDepth = if (notch) min(
                min(max(8f, height * 0.22f), width * 0.16f),
                max(12f, strokeWidthPx * 6f)
            ) else
                0f
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
            drawCutouts(canvas, b)
        }

        fun drawDetails(
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
            if (width < 56f || height < 44f) return
            val inset = max(3f, strokeWidthPx * 2.2f)
            val topRailY = top + inset
            val bottomRailY = bottom - inset
            val leftRailStart = left + notchDepth + inset
            val leftRailEnd = min(right - cornerCut - inset, left + width * 0.42f)
            if (leftRailEnd > leftRailStart + 8f) {
                canvas.drawLine(leftRailStart, topRailY, leftRailEnd, topRailY, detailPaint)
            }
            val rightRailStart = max(left + width * 0.68f, right - cornerCut - width * 0.18f)
            val rightRailEnd = right - inset
            if (rightRailEnd > rightRailStart + 8f) {
                canvas.drawLine(rightRailStart, topRailY, rightRailEnd, topRailY, detailPaint)
            }
            if (height >= 34f) {
                val verticalX = right - inset
                canvas.drawLine(
                    verticalX,
                    top + height * 0.18f,
                    verticalX,
                    bottom - cornerCut - inset,
                    detailPaint
                )
                canvas.drawLine(
                    left + inset,
                    bottomRailY,
                    left + min(width * 0.22f, 76f),
                    bottomRailY,
                    detailPaint
                )
            }
        }

        fun drawCutouts(canvas: Canvas, bounds: Rect) {
            if (topCutouts.isEmpty() && bottomCutouts.isEmpty()) return
            val cutoutHeight = max(strokeWidthPx * 4f, 10f)
            for (cutout in topCutouts) {
                canvas.drawRect(
                    bounds.left + cutout.left,
                    bounds.top.toFloat(),
                    bounds.left + cutout.right,
                    bounds.top + cutoutHeight,
                    cutoutPaint
                )
            }
            for (cutout in bottomCutouts) {
                canvas.drawRect(
                    bounds.left + cutout.left,
                    bounds.bottom - cutoutHeight,
                    bounds.left + cutout.right,
                    bounds.bottom.toFloat(),
                    cutoutPaint
                )
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.setAlpha(alpha)
            strokePaint.setAlpha(alpha)
            detailPaint.setAlpha(alpha)
            cutoutPaint.setAlpha(alpha)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.setColorFilter(colorFilter)
            strokePaint.setColorFilter(colorFilter)
            detailPaint.setColorFilter(colorFilter)
            cutoutPaint.setColorFilter(colorFilter)
            invalidateSelf()
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }

        companion object {
            private fun withAlphaComponent(color: Int, alpha: Int): Int {
                return Color.argb(
                    max(0, min(255, alpha)),
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
            }
        }
    }

    private class CyberIconFrameDrawable(
        frameColor: Int,
        strokePx: Float,
        cornerLengthPx: Float,
        insetPx: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cornerLengthPx: Float
        private val insetPx: Float

        init {
            paint.setStyle(Paint.Style.STROKE)
            paint.setColor(frameColor)
            paint.setStrokeWidth(strokePx)
            paint.setStrokeCap(Paint.Cap.SQUARE)
            paint.setStrokeJoin(Paint.Join.MITER)
            this.cornerLengthPx = cornerLengthPx
            this.insetPx = insetPx
        }

        override fun draw(canvas: Canvas) {
            val b = getBounds()
            if (b.isEmpty()) return
            val left = b.left + insetPx
            val top = b.top + insetPx
            val right = b.right - insetPx
            val bottom = b.bottom - insetPx
            val length = min(cornerLengthPx, min((right - left) * 0.34f, (bottom - top) * 0.34f))
            if (length <= 0f) return

            canvas.drawLine(left, top, left + length, top, paint)
            canvas.drawLine(left, top, left, top + length, paint)
            canvas.drawLine(right, top, right - length, top, paint)
            canvas.drawLine(right, top, right, top + length, paint)
            canvas.drawLine(left, bottom, left + length, bottom, paint)
            canvas.drawLine(left, bottom, left, bottom - length, paint)
            canvas.drawLine(right, bottom, right - length, bottom, paint)
            canvas.drawLine(right, bottom, right, bottom - length, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.setAlpha(alpha)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.setColorFilter(colorFilter)
            invalidateSelf()
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }

    private class CrtOverlayDrawable(activity: MainActivity, accentColor: Int) : Drawable() {
        private val scanlineStepPx: Float
        private val scanlineHeightPx: Float
        private val beamHeightPx: Float
        private val maskStepPx: Float
        private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scanlinePaint = Paint()
        private val beamPaint = Paint()
        private val maskPaint = Paint()
        private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            val density = activity.getResources().getDisplayMetrics().density
            scanlineStepPx = max(3f, density * 3f)
            scanlineHeightPx = max(1f, density)
            beamHeightPx = max(1f, density * 0.5f)
            maskStepPx = max(4f, density * 4f)

            tintPaint.setStyle(Paint.Style.FILL)
            tintPaint.setColor(
                Color.argb(
                    10,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                )
            )

            scanlinePaint.setStyle(Paint.Style.FILL)
            scanlinePaint.setColor(Color.argb(44, 0, 0, 0))

            beamPaint.setStyle(Paint.Style.FILL)
            beamPaint.setColor(Color.argb(10, 255, 255, 255))

            maskPaint.setStyle(Paint.Style.STROKE)
            maskPaint.setStrokeWidth(1f)
            maskPaint.setColor(Color.argb(18, 0, 0, 0))

            vignettePaint.setStyle(Paint.Style.FILL)
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds == null || bounds.isEmpty()) {
                vignettePaint.setShader(null)
                return
            }
            vignettePaint.setShader(
                RadialGradient(
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    max(bounds.width(), bounds.height()) * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(116, 0, 0, 0)),
                    floatArrayOf(0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
        }

        override fun draw(canvas: Canvas) {
            val b = getBounds()
            if (b.isEmpty()) return
            canvas.drawRect(b, tintPaint)
            var y = b.top.toFloat()
            while (y < b.bottom) {
                canvas.drawRect(
                    b.left.toFloat(),
                    y,
                    b.right.toFloat(),
                    y + scanlineHeightPx,
                    scanlinePaint
                )
                canvas.drawRect(
                    b.left.toFloat(),
                    y + scanlineHeightPx,
                    b.right.toFloat(),
                    y + scanlineHeightPx + beamHeightPx,
                    beamPaint
                )
                y += scanlineStepPx
            }
            var x = b.left.toFloat()
            while (x < b.right) {
                canvas.drawLine(x, b.top.toFloat(), x, b.bottom.toFloat(), maskPaint)
                x += maskStepPx
            }
            canvas.drawRect(b, vignettePaint)
        }

        override fun setAlpha(alpha: Int) {
            tintPaint.setAlpha(alpha)
            scanlinePaint.setAlpha(alpha)
            beamPaint.setAlpha(alpha)
            maskPaint.setAlpha(alpha)
            vignettePaint.setAlpha(alpha)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            tintPaint.setColorFilter(colorFilter)
            scanlinePaint.setColorFilter(colorFilter)
            beamPaint.setColorFilter(colorFilter)
            maskPaint.setColorFilter(colorFilter)
            vignettePaint.setColorFilter(colorFilter)
            invalidateSelf()
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }

    private inner class OperationOverlay(name: kotlin.String?, cancelled: AtomicBoolean) {
        val container: LinearLayout
        val params: FrameLayout.LayoutParams
        val title: TextView
        val bar: TextView
        val current: TextView
        val counts: TextView
        val cancel: TextView

        init {
            container = LinearLayout(this@MainActivity)
            container.setOrientation(LinearLayout.VERTICAL)
            container.setPadding(dp(12), dp(10), dp(12), dp(10))
            stylePanel(container, true)

            title = label(name + " running", headerTextSizeSp, true)
            title.setGravity(Gravity.CENTER)
            container.addView(title, LinearLayout.LayoutParams(-1, -2))

            bar = label("[--------------------]", outputTextSizeSp, false)
            bar.setGravity(Gravity.CENTER)
            container.addView(bar, LinearLayout.LayoutParams(-1, -2))

            current = label("", max(10, outputTextSizeSp - 1), false)
            current.setSingleLine(true)
            current.setEllipsize(TextUtils.TruncateAt.MIDDLE)
            container.addView(current, LinearLayout.LayoutParams(-1, -2))

            counts = label("", max(10, outputTextSizeSp - 1), false)
            counts.setGravity(Gravity.CENTER)
            container.addView(counts, LinearLayout.LayoutParams(-1, -2))

            cancel = label("CANCEL", 12, true)
            cancel.setGravity(Gravity.CENTER)
            cancel.setPadding(0, dp(4), 0, dp(4))
            styleChip(cancel, true)
            cancel.setOnClickListener(View.OnClickListener { v: View? ->
                cancelled.set(true)
                title.setText(name + " cancelling")
            })
            val cancelParams = LinearLayout.LayoutParams(-1, dp(30))
            cancelParams.topMargin = dp(8)
            container.addView(cancel, cancelParams)

            params = FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
            params.leftMargin = dp(24)
            params.rightMargin = dp(24)
            update("", 0, 0, 0, 0)
        }

        fun update(
            path: kotlin.String?,
            done: Int,
            total: Int,
            bytesDone: kotlin.Long,
            bytesTotal: kotlin.Long
        ) {
            current.setText(if (path == null) "" else path)
            val width = 20
            val filled =
                if (total > 0) min(width, max(0, ((done * 1L * width) / total).toInt())) else 0
            val line = StringBuilder("[")
            for (i in 0..<width) line.append(if (i < filled) '#' else '-')
            line.append(']')
            bar.setText(line.toString())
            val itemText = if (total > 0) done.toString() + "/" + total else "?"
            val byteText =
                if (bytesTotal > 0) humanSize(bytesDone) + "/" + humanSize(bytesTotal) else ""
            counts.setText(if (byteText.length > 0) itemText + "  " + byteText else itemText)
        }
    }

    private inner class FileClickSpan(private val file: File?, private val directory: Boolean) :
        ClickableSpan() {
        override fun onClick(widget: View) {
            if (file == null) return
            if (directory) {
                if (file == currentDirectory!!.getParentFile()) {
                    seed("cd ..")
                } else {
                    seed("cd " + commandPath(file))
                }
            } else {
                seed("preview " + commandPath(file))
            }
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.setColor(textColor)
            ds.setUnderlineText(false)
            ds.setFakeBoldText(directory)
        }
    }

    companion object {
        const val ACTION_OPEN_CONSOLE: kotlin.String = "com.dvil.retui.fm.OPEN_CONSOLE"
        const val EXTRA_PATH: kotlin.String = "path"
        const val EXTRA_COMMAND: kotlin.String = "command"
        const val EXTRA_THEME_BG: kotlin.String = "theme_bg"
        const val EXTRA_THEME_TEXT: kotlin.String = "theme_text"
        const val EXTRA_THEME_BORDER: kotlin.String = "theme_border"
        const val EXTRA_TERMINAL_BG: kotlin.String = "terminal_bg"
        const val EXTRA_MODULE_BG_COLOR: kotlin.String = "module_bg_color"
        const val EXTRA_MODULE_TEXT_COLOR: kotlin.String = "module_text_color"
        const val EXTRA_MODULE_BORDER_COLOR: kotlin.String = "module_border_color"
        const val EXTRA_MODULE_HEADER_BG_COLOR: kotlin.String = "module_header_bg_color"
        const val EXTRA_MODULE_HEADER_TEXT_COLOR: kotlin.String = "module_header_text_color"
        const val EXTRA_MODULE_BUTTON_BG_COLOR: kotlin.String = "module_button_bg_color"
        const val EXTRA_MODULE_BUTTON_TEXT_COLOR: kotlin.String = "module_button_text_color"
        const val EXTRA_MODULE_BUTTON_BORDER_COLOR: kotlin.String = "module_button_border_color"
        const val EXTRA_INPUT_BG_COLOR: kotlin.String = "input_bg_color"
        const val EXTRA_INPUT_TEXT_COLOR: kotlin.String = "input_text_color"
        const val EXTRA_OUTPUT_BG_COLOR: kotlin.String = "output_bg_color"
        const val EXTRA_OUTPUT_TEXT_COLOR: kotlin.String = "output_text_color"
        const val EXTRA_OUTPUT_BORDER_COLOR: kotlin.String = "output_border_color"
        const val EXTRA_TOP_MARGIN: kotlin.String = "top_margin"
        const val EXTRA_INPUT_FONT_SIZE: kotlin.String = "input_font_size"
        const val EXTRA_DISPLAY_MARGIN_MM: kotlin.String = "display_margin_mm"
        const val EXTRA_DISPLAY_MARGIN_TOP_SECTION: kotlin.String = "display_margin_top_section"
        const val EXTRA_DISPLAY_MARGIN_BOTTOM_SECTION: kotlin.String =
            "display_margin_bottom_section"
        const val EXTRA_FONT_PATH: kotlin.String = "font_path"
        const val EXTRA_FONT_NAME: kotlin.String = "font_name"
        const val EXTRA_MODULE_CORNER_RADIUS: kotlin.String = "module_corner_radius"
        const val EXTRA_OUTPUT_CORNER_RADIUS: kotlin.String = "output_corner_radius"
        const val EXTRA_HEADER_CORNER_RADIUS: kotlin.String = "header_corner_radius"
        const val EXTRA_HEADER_TEXT_SIZE: kotlin.String = "header_text_size"
        const val EXTRA_OUTPUT_TEXT_SIZE: kotlin.String = "output_text_size"
        const val EXTRA_MODULE_HEADER_TEXT_SIZE: kotlin.String = "module_header_text_size"
        const val EXTRA_OUTPUT_HEADER_TEXT_SIZE: kotlin.String = "output_header_text_size"
        const val EXTRA_TERMINAL_BG_IMAGE: kotlin.String = "terminal_bg_image"
        const val EXTRA_CYBERDECK_MODE: kotlin.String = "enable_cyberdeck_mode"
        const val EXTRA_CRT_FILTER: kotlin.String = "enable_crt_filter"
        const val EXTRA_LANDSCAPE_DISPLAY_MARGIN_MM: kotlin.String = "landscape_display_margin_mm"
        const val EXTRA_LANDSCAPE_TOP_MARGIN: kotlin.String = "landscape_top_margin"

        private const val LIST_TREE_DEPTH = 0
        private const val EXPANDED_TREE_DEPTH = 2
        private const val TREE_MAX_ITEMS = 320
        private const val ICON_FOLDER = "\uea83"
        private const val ICON_FOLDER_OPEN = "\ueaf7"
        private const val ICON_IMAGE = "\uf1c5"
        private const val ICON_DOCUMENT = "\udb82\uddee"
        private const val ICON_CODE = "\udb81\uddc0"
        private const val ICON_FILE = "\uf15b"
        private const val ICON_HOME = "\uf015"
        private const val ICON_STAR = "\uf005"
        private const val ICON_REFRESH = "\uf021"
        private const val ICON_UP = "\uf062"
        private const val ICON_OPEN = "\uf07c"
        private const val ICON_SHARE = "\uf064"
        private const val ICON_SETTINGS = "\uf013"
        private val COPY_BUFFER_SIZE = 64 * 1024
        private const val PREFS_NAME = "retui_fm"
        private const val PREF_FAVORITES = "favorites"
        private const val PREF_DEFAULT_FAVORITES_IMPORTED = "default_favorites_imported"
        private const val PREF_TRASH_INDEX = "trash_index"
        private const val PREF_LAUNCH_PREFIX = "launch_"
        private const val PREF_LANDSCAPE_DISPLAY_MARGIN_MM = "landscape_display_margin_mm"
        private const val PREF_LANDSCAPE_TOP_MARGIN = "landscape_top_margin"
        private const val TRASH_DIR_NAME = ".retui-trash"
        private const val RECENT_LIMIT = 24
        private const val PREVIEW_TEXT_LIMIT = 80
        private val PREVIEW_MAX_BYTES = 64 * 1024
        private const val PREVIEW_MAX_COLUMNS = 8
        private const val PREVIEW_CSV_ROWS = 28
        private const val PREVIEW_CELL_CHARS = 96
        private const val PREVIEW_JSON_NODES = 160
        private const val PREVIEW_JSON_DEPTH = 6
        private val EDITOR_TEXT_MAX_BYTES = 256 * 1024
    }
}
