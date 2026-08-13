package com.dvil.retui.fm

import android.app.Activity
import android.content.DialogInterface
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
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import com.dvil.retui.contract.RetuiVisualContract
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

class FmEditorActivity : Activity() {
    private var file: File? = null
    private var editor: EditText? = null
    private var originalText = ""
    private var bgColor = Color.rgb(38, 40, 40)
    private var panelColor = Color.rgb(48, 50, 50)
    private var textColor = Color.rgb(195, 139, 150)
    private var borderColor = Color.rgb(103, 64, 71)
    private var headerPanelColor = panelColor
    private var headerTextColor = textColor
    private var buttonBgColor = Color.rgb(103, 64, 83)
    private var buttonTextColor = textColor
    private var buttonBorderColor = borderColor
    private var outputPanelColor = panelColor
    private var outputTextColor = textColor
    private var outputBorderColor = borderColor
    private var moduleCornerRadiusDp = 0
    private var outputCornerRadiusDp = 0
    private var headerCornerRadiusDp = 0
    private var cyberdeckMode = false
    private var crtFilter = false
    private var crtVignette = true
    private var headerTextSizeSp = 14
    private var outputTextSizeSp = 13
    private var fontScaleOffsetSp = 0
    private var terminalBackgroundImage: String? = null
    private var appTypeface: Typeface? = Typeface.MONOSPACE
    private var launcherFrameRuntime: FilesFrameRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        launcherFrameRuntime = FilesFrameRuntime.load(this)
        applyThemeExtras()
        configureWindow()

        val path = intent?.getStringExtra(MainActivity.EXTRA_PATH)
        if (TextUtils.isEmpty(path)) {
            finish()
            return
        }
        file = File(path!!).absoluteFile
        if (file == null || !file!!.exists() || file!!.isDirectory) {
            Toast.makeText(this, "File is not editable.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (file!!.length() > EDITOR_TEXT_MAX_BYTES) {
            Toast.makeText(this, "Large file is read-only in FM.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        originalText = readFileText(file!!)
        setContentView(buildUi())
    }

    override fun onBackPressed() {
        attemptClose()
    }

    private fun buildUi(): View {
        val screen = FrameLayout(this)
        screen.setBackgroundColor(Color.TRANSPARENT)
        applyWallpaperBackground(screen)
        applyCrtForeground(screen)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(48), dp(18), dp(16))
        root.background = panelDrawable(PanelRole.OUTPUT, true)
        val rootParams = FrameLayout.LayoutParams(-1, -1)
        rootParams.setMargins(dp(28), dp(34), dp(28), dp(28))
        screen.addView(root, rootParams)

        val header = label("DOCUMENTS", headerTextSizeSp, true)
        header.gravity = Gravity.CENTER
        header.setPadding(dp(18), dp(2), dp(18), dp(2))
        header.setTextColor(headerTextColor)
        header.background = panelDrawable(PanelRole.HEADER, false)
        val headerParams = FrameLayout.LayoutParams(-2, -2)
        headerParams.leftMargin = dp(64)
        headerParams.topMargin = dp(24)
        screen.addView(header, headerParams)
        bindImeAwarePanelMargin(screen, root, rootParams, header, headerParams)
        bindPanelCutouts(root, header)

        val name = label(file!!.name.uppercase(), outputTextSizeSp + 1, true)
        name.setSingleLine(true)
        name.ellipsize = TextUtils.TruncateAt.MIDDLE
        name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        name.includeFontPadding = false
        name.setPadding(dp(12), 0, dp(12), 0)
        name.background = panelDrawable(PanelRole.MODULE, false)
        root.addView(name, LinearLayout.LayoutParams(-1, dp(40)))

        val meta = label("DOCUMENT EDITOR", max(10, outputTextSizeSp - 1), false)
        meta.alpha = 0.72f
        val metaParams = LinearLayout.LayoutParams(-1, -2)
        metaParams.setMargins(0, dp(12), 0, dp(8))
        root.addView(meta, metaParams)

        editor = EditText(this)
        editor!!.setSingleLine(false)
        editor!!.gravity = Gravity.TOP or Gravity.START
        editor!!.setHorizontallyScrolling(true)
        editor!!.setText(originalText)
        editor!!.setSelection(editor!!.text.length)
        editor!!.setTextColor(outputTextColor)
        editor!!.setHintTextColor(withAlpha(outputTextColor, 150))
        editor!!.setTextSize(scaledFontSp(outputTextSizeSp, fontScaleOffsetSp))
        editor!!.typeface = appTypeface
        editor!!.setPadding(dp(12), dp(10), dp(12), dp(10))
        editor!!.background = panelDrawable(PanelRole.OUTPUT, true)
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.setPadding(0, dp(10), 0, 0)
        val cancel = button("CANCEL", false)
        cancel.setOnClickListener { attemptClose() }
        bottom.addView(cancel)
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val save = button("SAVE", true)
        save.setOnClickListener { saveAndClose() }
        bottom.addView(save)
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(44)))

        return screen
    }

    private fun configureWindow() {
        val window = window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
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
        window.navigationBarColor = Color.BLACK
    }

    private fun applyWallpaperBackground(screen: FrameLayout) {
        if (!TextUtils.isEmpty(terminalBackgroundImage)) {
            try {
                val provided = Drawable.createFromPath(terminalBackgroundImage)
                if (provided != null) {
                    screen.background = provided
                    return
                }
            } catch (_: Exception) {
            }
        }
        screen.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applyCrtForeground(screen: FrameLayout) {
        screen.foreground = if (crtFilter) {
            CrtOverlayDrawable(this, crtVignette).apply { setAccentColor(outputTextColor) }
        } else {
            null
        }
    }

    private fun saveAndClose() {
        val target = file ?: return
        try {
            FileOutputStream(target, false).use { out ->
                out.write(editor!!.text.toString().toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            Toast.makeText(this, "Saved " + target.name, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun attemptClose() {
        val changed = editor != null && editor!!.text.toString() != originalText
        if (!changed) {
            finish()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("Unsaved file changes will be lost.")
            .setNegativeButton("KEEP EDITING", null)
            .setPositiveButton(
                "DISCARD",
                DialogInterface.OnClickListener { _: DialogInterface?, _: Int -> finish() }
            )
            .show()
    }

    private fun readFileText(file: File): String {
        FileInputStream(file).use { input ->
            val length = min(file.length(), EDITOR_TEXT_MAX_BYTES).toInt()
            val buffer = ByteArray(length)
            val read = input.read(buffer)
            return if (read > 0) String(buffer, 0, read, StandardCharsets.UTF_8) else ""
        }
    }

    private fun label(text: String, sizeSp: Int, bold: Boolean): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(outputTextColor)
        view.setTextSize(scaledFontSp(sizeSp, fontScaleOffsetSp))
        view.typeface = appTypeface ?: Typeface.MONOSPACE
        if (bold) view.setTypeface(view.typeface, Typeface.BOLD)
        view.includeFontPadding = true
        return view
    }

    private fun button(text: String, primary: Boolean): TextView {
        val view = label(text, 12, true)
        view.gravity = Gravity.CENTER
        view.setPadding(dp(18), 0, dp(18), 0)
        view.setTextColor(buttonTextColor)
        view.background = buttonDrawable(if (primary) buttonBgColor else panelColor, buttonBorderColor)
        val params = LinearLayout.LayoutParams(-2, -1)
        params.setMargins(dp(6), 0, 0, 0)
        view.layoutParams = params
        return view
    }

    private fun bindImeAwarePanelMargin(
        screen: View,
        panel: View,
        params: FrameLayout.LayoutParams,
        header: View,
        headerParams: FrameLayout.LayoutParams
    ) {
        val baseLeft = params.leftMargin
        val baseTop = params.topMargin
        val baseRight = params.rightMargin
        val baseBottom = params.bottomMargin
        val baseHeaderLeft = headerParams.leftMargin
        val baseHeaderTop = headerParams.topMargin
        ViewCompat.setOnApplyWindowInsetsListener(screen) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            params.setMargins(
                baseLeft + safe.left,
                baseTop + safe.top,
                baseRight + safe.right,
                baseBottom + max(safe.bottom, ime)
            )
            panel.layoutParams = params
            headerParams.leftMargin = baseHeaderLeft + safe.left
            headerParams.topMargin = baseHeaderTop + safe.top
            header.layoutParams = headerParams
            insets
        }
        screen.post { ViewCompat.requestApplyInsets(screen) }
    }

    private fun panelDrawable(role: PanelRole, translucent: Boolean): Drawable {
        val fill = when (role) {
            PanelRole.HEADER -> headerPanelColor
            PanelRole.OUTPUT -> if (translucent) withAlpha(outputPanelColor, 250) else outputPanelColor
            PanelRole.MODULE -> panelColor
        }
        val stroke = when (role) {
            PanelRole.HEADER -> borderColor
            PanelRole.OUTPUT -> outputBorderColor
            PanelRole.MODULE -> outputBorderColor
        }
        val fallback = if (cyberdeckMode) {
            CyberPanelDrawable(fill, stroke, max(1f, dpFloat(if (role == PanelRole.MODULE) 1.5f else 1.2f)), true)
        } else {
            roundedDrawable(fill, stroke, roleRadius(role))
        }
        return launcherFrameRuntime?.drawable(fallback) ?: fallback
    }

    private fun buttonDrawable(fill: Int, stroke: Int): Drawable {
        val fallback = if (cyberdeckMode) {
            CyberPanelDrawable(fill, stroke, max(1f, dpFloat(1f)), false)
        } else {
            roundedDrawable(fill, stroke, moduleCornerRadiusDp)
        }
        return launcherFrameRuntime?.drawable(fallback, interactive = true) ?: fallback
    }

    private fun roundedDrawable(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(fill)
        drawable.setStroke(max(1, dp(1)), stroke)
        drawable.cornerRadius = dp(radiusDp).toFloat()
        return drawable
    }

    private fun roleRadius(role: PanelRole): Int {
        return when (role) {
            PanelRole.HEADER -> headerCornerRadiusDp
            PanelRole.OUTPUT -> outputCornerRadiusDp
            PanelRole.MODULE -> moduleCornerRadiusDp
        }
    }

    private fun bindPanelCutouts(panel: View, vararg cutoutViews: View) {
        if (!cyberdeckMode) return
        val drawable = panel.background as? CyberPanelDrawable ?: return
        val updater = Runnable {
            if (panel.width <= 0 || panel.height <= 0) return@Runnable
            val panelLoc = IntArray(2)
            panel.getLocationOnScreen(panelLoc)
            val top = ArrayList<RectF>()
            val bottom = ArrayList<RectF>()
            for (view in cutoutViews) {
                if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) continue
                val viewLoc = IntArray(2)
                view.getLocationOnScreen(viewLoc)
                val left = (viewLoc[0] - panelLoc[0] - dp(3)).toFloat()
                val right = (viewLoc[0] - panelLoc[0] + view.width + dp(3)).toFloat()
                val cutout = RectF(max(0f, left), 0f, min(panel.width.toFloat(), right), 0f)
                if (cutout.right <= cutout.left) continue
                val centerY = viewLoc[1] - panelLoc[1] + view.height / 2f
                if (centerY <= panel.height / 2f) top.add(cutout) else bottom.add(cutout)
            }
            drawable.setCutouts(top, bottom)
        }
        panel.post(updater)
        for (view in cutoutViews) view.post(updater)
    }

    private fun applyThemeExtras() {
        val intent = intent
        bgColor = RetuiVisualContract.color(intent, bgColor, RetuiVisualContract.BG)
        panelColor = RetuiVisualContract.color(intent, panelColor, RetuiVisualContract.TERMINAL_BG)
        textColor = RetuiVisualContract.color(intent, textColor, RetuiVisualContract.TEXT)
        borderColor = RetuiVisualContract.color(intent, borderColor, RetuiVisualContract.BORDER)
        headerPanelColor = RetuiVisualContract.color(intent, panelColor, RetuiVisualContract.HEADER_BG)
        headerTextColor = RetuiVisualContract.color(intent, textColor, RetuiVisualContract.HEADER_TEXT)
        buttonBgColor = RetuiVisualContract.color(intent, buttonBgColor, RetuiVisualContract.BUTTON_BG)
        buttonTextColor = RetuiVisualContract.color(intent, textColor, RetuiVisualContract.BUTTON_TEXT)
        buttonBorderColor = RetuiVisualContract.color(intent, borderColor, RetuiVisualContract.BUTTON_BORDER)
        outputPanelColor = RetuiVisualContract.color(intent, panelColor, RetuiVisualContract.OUTPUT_BG)
        outputTextColor = RetuiVisualContract.color(intent, textColor, RetuiVisualContract.OUTPUT_TEXT)
        outputBorderColor = RetuiVisualContract.color(intent, borderColor, RetuiVisualContract.OUTPUT_BORDER)
        headerTextSizeSp = RetuiVisualContract.int(intent, headerTextSizeSp, RetuiVisualContract.HEADER_TEXT_SIZE)
        outputTextSizeSp = RetuiVisualContract.int(intent, outputTextSizeSp, RetuiVisualContract.BODY_TEXT_SIZE)
        fontScaleOffsetSp = intent?.getIntExtra(MainActivity.EXTRA_FONT_SCALE_OFFSET, 0)?.coerceIn(-3, 4) ?: 0
        moduleCornerRadiusDp = RetuiVisualContract.int(intent, moduleCornerRadiusDp, RetuiVisualContract.MODULE_CORNER_RADIUS)
        outputCornerRadiusDp = RetuiVisualContract.int(intent, outputCornerRadiusDp, RetuiVisualContract.OUTPUT_CORNER_RADIUS)
        headerCornerRadiusDp = RetuiVisualContract.int(intent, headerCornerRadiusDp, RetuiVisualContract.HEADER_CORNER_RADIUS)
        cyberdeckMode = RetuiVisualContract.boolean(intent, cyberdeckMode, RetuiVisualContract.CYBERDECK_MODE)
        crtFilter = RetuiVisualContract.boolean(intent, crtFilter, RetuiVisualContract.CRT_FILTER)
        crtVignette = RetuiVisualContract.boolean(intent, crtVignette, RetuiVisualContract.CRT_VIGNETTE)
        terminalBackgroundImage = RetuiVisualContract.string(intent, RetuiVisualContract.TERMINAL_BG_IMAGE)
        appTypeface = resolveTypeface()
        launcherFrameRuntime?.textColor?.let { color ->
            textColor = color
            headerTextColor = color
            buttonTextColor = color
            outputTextColor = color
        }
    }

    private fun resolveTypeface(): Typeface? {
        val path = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_PATH)
        if (!TextUtils.isEmpty(path)) {
            try {
                return Typeface.createFromFile(path)
            } catch (_: Exception) {
            }
        }
        val name = RetuiVisualContract.string(intent, RetuiVisualContract.FONT_NAME)
        if (!TextUtils.isEmpty(name)) {
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

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun dpFloat(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private enum class PanelRole {
        MODULE,
        OUTPUT,
        HEADER
    }

    private class CyberPanelDrawable(
        private val fillColor: Int,
        private val borderColor: Int,
        strokeWidthPx: Float,
        private val notch: Boolean
    ) : Drawable() {
        private val strokeWidthPx: Float = max(1f, strokeWidthPx)
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
        private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        private val path = Path()
        private val topCutouts = ArrayList<RectF>()
        private val bottomCutouts = ArrayList<RectF>()

        fun setCutouts(top: List<RectF>, bottom: List<RectF>) {
            topCutouts.clear()
            topCutouts.addAll(top)
            bottomCutouts.clear()
            bottomCutouts.addAll(bottom)
            invalidateSelf()
        }

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
            drawCutouts(canvas, b)
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
                canvas.drawLine(verticalX, top + height * 0.18f, verticalX, bottom - cornerCut - inset, detailPaint)
                canvas.drawLine(left + inset, bottomRailY, left + min(width * 0.22f, 76f), bottomRailY, detailPaint)
            }
        }

        private fun drawCutouts(canvas: Canvas, bounds: Rect) {
            if (topCutouts.isEmpty() && bottomCutouts.isEmpty()) return
            val cutoutHeight = max(strokeWidthPx * 4f, 10f)
            for (cutout in topCutouts) {
                canvas.drawRect(bounds.left + cutout.left, bounds.top.toFloat(), bounds.left + cutout.right, bounds.top + cutoutHeight, cutoutPaint)
            }
            for (cutout in bottomCutouts) {
                canvas.drawRect(bounds.left + cutout.left, bounds.bottom - cutoutHeight, bounds.left + cutout.right, bounds.bottom.toFloat(), cutoutPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            strokePaint.alpha = alpha
            detailPaint.alpha = alpha
            cutoutPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            detailPaint.colorFilter = colorFilter
            cutoutPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private fun withAlphaComponent(color: Int, alpha: Int): Int {
            return Color.argb(max(0, min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    companion object {
        private const val EDITOR_TEXT_MAX_BYTES = 256 * 1024L
    }
}
