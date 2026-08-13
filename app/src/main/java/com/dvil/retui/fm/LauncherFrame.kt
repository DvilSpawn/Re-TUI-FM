package com.dvil.retui.fm

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.Log
import com.dvil.retui.contract.RetuiVisualContract
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class LauncherFrameSpec(
    val assetId: String,
    val sliceLeftPx: Int,
    val sliceTopPx: Int,
    val sliceRightPx: Int,
    val sliceBottomPx: Int,
    val borderLeftDp: Float,
    val borderTopDp: Float,
    val borderRightDp: Float,
    val borderBottomDp: Float,
    val modeTop: String,
    val modeRight: String,
    val modeBottom: String,
    val modeLeft: String,
    val modeCenter: String,
    val filtering: String
) {
    fun validationError(width: Int, height: Int): String? {
        if (assetId.isBlank()) return "frame_asset_id is empty"
        if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return "PNG dimensions are out of range"
        if (sliceLeftPx <= 0 || sliceTopPx <= 0 || sliceRightPx <= 0 || sliceBottomPx <= 0) return "frame slices must be positive"
        if (sliceLeftPx + sliceRightPx >= width) return "horizontal frame slices overlap"
        if (sliceTopPx + sliceBottomPx >= height) return "vertical frame slices overlap"
        if (listOf(borderLeftDp, borderTopDp, borderRightDp, borderBottomDp).any { !it.isFinite() || it !in 0f..256f }) {
            return "frame borders must be finite values from 0 through 256 dp"
        }
        if (listOf(modeTop, modeRight, modeBottom, modeLeft).any { it != STRETCH && it != TILE }) {
            return "frame edge mode must be stretch or tile"
        }
        if (modeCenter != STRETCH && modeCenter != TILE && modeCenter != NONE) return "frame center mode is invalid"
        if (filtering != NEAREST && filtering != LINEAR) return "frame filtering is invalid"
        return null
    }

    companion object {
        const val STRETCH = "stretch"
        const val TILE = "tile"
        const val NONE = "none"
        const val NEAREST = "nearest"
        const val LINEAR = "linear"
        const val MAX_DIMENSION = 2048
    }
}

internal enum class FramePayloadAction { IGNORE, PRESERVE, CLEAR, IMPORT }

internal fun framePayloadAction(accepted: Boolean, available: Boolean?): FramePayloadAction = when {
    !accepted -> FramePayloadAction.IGNORE
    available == null -> FramePayloadAction.PRESERVE
    !available -> FramePayloadAction.CLEAR
    else -> FramePayloadAction.IMPORT
}

internal object LauncherFrameMath {
    fun fitScale(width: Float, height: Float, left: Float, top: Float, right: Float, bottom: Float): Float = minOf(
        1f,
        (width / (left + right)).coerceAtLeast(0f),
        (height / (top + bottom)).coerceAtLeast(0f)
    )

    fun boundaries(start: Int, end: Int, leading: Float, trailing: Float) = floatArrayOf(
        start.toFloat(),
        (start + leading).roundToInt().toFloat(),
        (end - trailing).roundToInt().toFloat(),
        end.toFloat()
    )
}

internal class LauncherFrameStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val frameDir = File(context.filesDir, FRAME_DIR)

    fun isAccepted(): Boolean = prefs.getBoolean(PREF_ACCEPT, true)

    fun setAccepted(accepted: Boolean): Boolean = prefs.edit().putBoolean(PREF_ACCEPT, accepted).commit()

    fun process(intent: Intent?) {
        val extras = intent?.extras
        val hasAvailability = extras?.containsKey(RetuiVisualContract.FRAME_AVAILABLE) == true
        val available = if (hasAvailability) {
            @Suppress("DEPRECATION")
            (extras?.get(RetuiVisualContract.FRAME_AVAILABLE) as? Boolean)
                ?: return logFailure("frame_available must be a Boolean")
        } else {
            null
        }
        when (framePayloadAction(isAccepted(), available)) {
            FramePayloadAction.IGNORE, FramePayloadAction.PRESERVE -> return
            FramePayloadAction.CLEAR -> {
                if (!prefs.edit().remove(PREF_ACTIVE_ID).commit()) logFailure("could not clear the active Launcher frame")
                return
            }
            FramePayloadAction.IMPORT -> Unit
        }

        try {
            val payload = parsePayload(extras!!)
            frameDir.mkdirs()
            val target = File(frameDir, cacheName(payload.spec.assetId))
            val cached = target.isFile
            if (!cached || validatePng(target, payload.spec) != null) importPng(payload.imageUri, target, payload.spec)
            validatePng(target, payload.spec)?.let { error(it) }
            if (!writeMetadata(payload.spec)) error("could not persist Launcher frame metadata")
        } catch (error: Exception) {
            logFailure(error.message ?: "invalid Launcher frame", error)
        }
    }

    fun loadActive(): LauncherFrameAsset? {
        if (!isAccepted()) return null
        val activeId = prefs.getString(PREF_ACTIVE_ID, null) ?: return null
        return try {
            val spec = readMetadata(activeId)
            val file = File(frameDir, cacheName(activeId))
            validatePng(file, spec)?.let { error(it) }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("cached Launcher frame cannot be decoded")
            LauncherFrameAsset(bitmap, spec, context.resources.displayMetrics.density)
        } catch (error: Exception) {
            logFailure(error.message ?: "cached Launcher frame is invalid", error)
            null
        }
    }

    private fun parsePayload(extras: android.os.Bundle): FramePayload {
        fun raw(key: String): Any = @Suppress("DEPRECATION") (extras.get(key) ?: error("missing $key"))
        fun string(key: String): String = (raw(key) as? String)?.takeIf { it.isNotBlank() } ?: error("invalid $key")
        fun int(key: String): Int {
            val number = raw(key) as? Number ?: error("invalid $key")
            val value = number.toLong()
            if (number.toDouble() != value.toDouble() || value !in Int.MIN_VALUE..Int.MAX_VALUE) error("invalid $key")
            return value.toInt()
        }
        fun float(key: String): Float = (raw(key) as? Number)?.toFloat() ?: error("invalid $key")

        val spec = LauncherFrameSpec(
            assetId = string(RetuiVisualContract.FRAME_ASSET_ID),
            sliceLeftPx = int(RetuiVisualContract.FRAME_SLICE_LEFT_PX),
            sliceTopPx = int(RetuiVisualContract.FRAME_SLICE_TOP_PX),
            sliceRightPx = int(RetuiVisualContract.FRAME_SLICE_RIGHT_PX),
            sliceBottomPx = int(RetuiVisualContract.FRAME_SLICE_BOTTOM_PX),
            borderLeftDp = float(RetuiVisualContract.FRAME_BORDER_LEFT_DP),
            borderTopDp = float(RetuiVisualContract.FRAME_BORDER_TOP_DP),
            borderRightDp = float(RetuiVisualContract.FRAME_BORDER_RIGHT_DP),
            borderBottomDp = float(RetuiVisualContract.FRAME_BORDER_BOTTOM_DP),
            modeTop = string(RetuiVisualContract.FRAME_MODE_TOP),
            modeRight = string(RetuiVisualContract.FRAME_MODE_RIGHT),
            modeBottom = string(RetuiVisualContract.FRAME_MODE_BOTTOM),
            modeLeft = string(RetuiVisualContract.FRAME_MODE_LEFT),
            modeCenter = string(RetuiVisualContract.FRAME_MODE_CENTER),
            filtering = string(RetuiVisualContract.FRAME_FILTERING)
        )
        val uri = Uri.parse(string(RetuiVisualContract.FRAME_IMAGE_URI))
        return FramePayload(spec, uri)
    }

    private fun importPng(uri: Uri, target: File, spec: LauncherFrameSpec) {
        val temp = File.createTempFile("incoming-", ".png", frameDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("frame_image_uri is unreadable")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PNG_BYTES) error("Launcher frame PNG exceeds 4 MiB")
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            validatePng(temp, spec)?.let { error(it) }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun validatePng(file: File, spec: LauncherFrameSpec): String? {
        if (!file.isFile || file.length() !in PNG_SIGNATURE.size.toLong()..MAX_PNG_BYTES) return "Launcher frame PNG is missing or too large"
        FileInputStream(file).use { input ->
            val signature = ByteArray(PNG_SIGNATURE.size)
            if (input.read(signature) != signature.size || !signature.contentEquals(PNG_SIGNATURE)) return "Launcher frame has an invalid PNG signature"
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        spec.validationError(options.outWidth, options.outHeight)?.let { return it }
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return "Launcher frame PNG cannot be decoded"
        decoded.recycle()
        return null
    }

    private fun writeMetadata(spec: LauncherFrameSpec): Boolean = prefs.edit()
        .putString(PREF_CACHED_ID, spec.assetId)
        .putString(PREF_ACTIVE_ID, spec.assetId)
        .putInt(RetuiVisualContract.FRAME_SLICE_LEFT_PX, spec.sliceLeftPx)
        .putInt(RetuiVisualContract.FRAME_SLICE_TOP_PX, spec.sliceTopPx)
        .putInt(RetuiVisualContract.FRAME_SLICE_RIGHT_PX, spec.sliceRightPx)
        .putInt(RetuiVisualContract.FRAME_SLICE_BOTTOM_PX, spec.sliceBottomPx)
        .putFloat(RetuiVisualContract.FRAME_BORDER_LEFT_DP, spec.borderLeftDp)
        .putFloat(RetuiVisualContract.FRAME_BORDER_TOP_DP, spec.borderTopDp)
        .putFloat(RetuiVisualContract.FRAME_BORDER_RIGHT_DP, spec.borderRightDp)
        .putFloat(RetuiVisualContract.FRAME_BORDER_BOTTOM_DP, spec.borderBottomDp)
        .putString(RetuiVisualContract.FRAME_MODE_TOP, spec.modeTop)
        .putString(RetuiVisualContract.FRAME_MODE_RIGHT, spec.modeRight)
        .putString(RetuiVisualContract.FRAME_MODE_BOTTOM, spec.modeBottom)
        .putString(RetuiVisualContract.FRAME_MODE_LEFT, spec.modeLeft)
        .putString(RetuiVisualContract.FRAME_MODE_CENTER, spec.modeCenter)
        .putString(RetuiVisualContract.FRAME_FILTERING, spec.filtering)
        .commit()

    private fun readMetadata(assetId: String) = LauncherFrameSpec(
        assetId = assetId,
        sliceLeftPx = prefs.getInt(RetuiVisualContract.FRAME_SLICE_LEFT_PX, 0),
        sliceTopPx = prefs.getInt(RetuiVisualContract.FRAME_SLICE_TOP_PX, 0),
        sliceRightPx = prefs.getInt(RetuiVisualContract.FRAME_SLICE_RIGHT_PX, 0),
        sliceBottomPx = prefs.getInt(RetuiVisualContract.FRAME_SLICE_BOTTOM_PX, 0),
        borderLeftDp = prefs.getFloat(RetuiVisualContract.FRAME_BORDER_LEFT_DP, Float.NaN),
        borderTopDp = prefs.getFloat(RetuiVisualContract.FRAME_BORDER_TOP_DP, Float.NaN),
        borderRightDp = prefs.getFloat(RetuiVisualContract.FRAME_BORDER_RIGHT_DP, Float.NaN),
        borderBottomDp = prefs.getFloat(RetuiVisualContract.FRAME_BORDER_BOTTOM_DP, Float.NaN),
        modeTop = prefs.getString(RetuiVisualContract.FRAME_MODE_TOP, null) ?: "",
        modeRight = prefs.getString(RetuiVisualContract.FRAME_MODE_RIGHT, null) ?: "",
        modeBottom = prefs.getString(RetuiVisualContract.FRAME_MODE_BOTTOM, null) ?: "",
        modeLeft = prefs.getString(RetuiVisualContract.FRAME_MODE_LEFT, null) ?: "",
        modeCenter = prefs.getString(RetuiVisualContract.FRAME_MODE_CENTER, null) ?: "",
        filtering = prefs.getString(RetuiVisualContract.FRAME_FILTERING, null) ?: ""
    )

    private fun cacheName(assetId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(assetId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".png"
    }

    private fun logFailure(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    private data class FramePayload(val spec: LauncherFrameSpec, val imageUri: Uri)

    companion object {
        private const val TAG = "LauncherFrame"
        private const val PREFS_NAME = "retui_fm"
        private const val PREF_ACCEPT = "accept_launcher_frames"
        private const val PREF_ACTIVE_ID = "launcher_frame_active_id"
        private const val PREF_CACHED_ID = "launcher_frame_cached_id"
        private const val FRAME_DIR = "launcher_frames"
        private const val MAX_PNG_BYTES = 4L * 1024L * 1024L
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}

internal class FilesFrameRuntime private constructor(private val asset: LauncherFrameAsset?) {
    val textColor: Int? = asset?.textColor

    fun drawable(fallback: Drawable, interactive: Boolean = false): Drawable = asset?.drawable()?.let {
        val layered = LayerDrawable(arrayOf(fallback, it))
        if (interactive) StatefulFrameDrawable(layered) else layered
    } ?: fallback

    companion object {
        fun load(context: Context) = FilesFrameRuntime(LauncherFrameStore(context).loadActive())
    }
}

private class StatefulFrameDrawable(private val frame: Drawable) : Drawable() {
    private val feedback = Paint().apply { color = 0x24ffffff }
    private var showFeedback = false

    override fun draw(canvas: Canvas) {
        frame.draw(canvas)
        if (showFeedback) canvas.drawRect(bounds, feedback)
    }

    override fun onBoundsChange(bounds: Rect) {
        frame.bounds = bounds
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val visible = state.contains(android.R.attr.state_pressed) || state.contains(android.R.attr.state_focused)
        if (visible == showFeedback) return false
        showFeedback = visible
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        frame.alpha = alpha
        feedback.alpha = (alpha * 36 / 255)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        frame.colorFilter = colorFilter
        feedback.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal class LauncherFrameAsset(bitmap: Bitmap, private val spec: LauncherFrameSpec, private val density: Float) {
    private val parts = FrameParts(bitmap, spec)
    val textColor = frameTextColor(parts.center)

    fun drawable(): Drawable = LauncherFrameDrawable(parts, spec, density)
}

private fun frameTextColor(bitmap: Bitmap): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    var weight = 0L
    val stepX = maxOf(1, bitmap.width / 32)
    val stepY = maxOf(1, bitmap.height / 32)
    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel).toLong()
            red += Color.red(pixel) * alpha
            green += Color.green(pixel) * alpha
            blue += Color.blue(pixel) * alpha
            weight += alpha
        }
    }
    if (weight == 0L) return 0xffffffff.toInt()
    return contrastTextColor((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
}

internal fun contrastTextColor(red: Int, green: Int, blue: Int): Int {
    fun linear(value: Int): Double {
        val channel = value.coerceIn(0, 255) / 255.0
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    return if (luminance > 0.179) 0xff000000.toInt() else 0xffffffff.toInt()
}

private class FrameParts(bitmap: Bitmap, spec: LauncherFrameSpec) {
    val topLeft = Bitmap.createBitmap(bitmap, 0, 0, spec.sliceLeftPx, spec.sliceTopPx)
    val top = Bitmap.createBitmap(bitmap, spec.sliceLeftPx, 0, bitmap.width - spec.sliceLeftPx - spec.sliceRightPx, spec.sliceTopPx)
    val topRight = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRightPx, 0, spec.sliceRightPx, spec.sliceTopPx)
    val left = Bitmap.createBitmap(bitmap, 0, spec.sliceTopPx, spec.sliceLeftPx, bitmap.height - spec.sliceTopPx - spec.sliceBottomPx)
    val center = Bitmap.createBitmap(bitmap, spec.sliceLeftPx, spec.sliceTopPx, bitmap.width - spec.sliceLeftPx - spec.sliceRightPx, bitmap.height - spec.sliceTopPx - spec.sliceBottomPx)
    val right = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRightPx, spec.sliceTopPx, spec.sliceRightPx, bitmap.height - spec.sliceTopPx - spec.sliceBottomPx)
    val bottomLeft = Bitmap.createBitmap(bitmap, 0, bitmap.height - spec.sliceBottomPx, spec.sliceLeftPx, spec.sliceBottomPx)
    val bottom = Bitmap.createBitmap(bitmap, spec.sliceLeftPx, bitmap.height - spec.sliceBottomPx, bitmap.width - spec.sliceLeftPx - spec.sliceRightPx, spec.sliceBottomPx)
    val bottomRight = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRightPx, bitmap.height - spec.sliceBottomPx, spec.sliceRightPx, spec.sliceBottomPx)
}

private class LauncherFrameDrawable(
    private val parts: FrameParts,
    private val spec: LauncherFrameSpec,
    private val density: Float
) : Drawable() {
    private val paint = Paint().apply { isFilterBitmap = spec.filtering == LauncherFrameSpec.LINEAR }
    private val shaderPaint = Paint().apply { isFilterBitmap = spec.filtering == LauncherFrameSpec.LINEAR }
    private val shaderMatrix = Matrix()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val scale = LauncherFrameMath.fitScale(
            b.width().toFloat(),
            b.height().toFloat(),
            spec.borderLeftDp * density,
            spec.borderTopDp * density,
            spec.borderRightDp * density,
            spec.borderBottomDp * density
        )
        val x = LauncherFrameMath.boundaries(
            b.left, b.right, spec.borderLeftDp * density * scale, spec.borderRightDp * density * scale
        )
        val y = LauncherFrameMath.boundaries(
            b.top, b.bottom, spec.borderTopDp * density * scale, spec.borderBottomDp * density * scale
        )

        stretch(canvas, parts.topLeft, RectF(x[0], y[0], x[1], y[1]))
        edge(canvas, parts.top, RectF(x[1], y[0], x[2], y[1]), spec.modeTop, Axis.HORIZONTAL, scale)
        stretch(canvas, parts.topRight, RectF(x[2], y[0], x[3], y[1]))
        edge(canvas, parts.left, RectF(x[0], y[1], x[1], y[2]), spec.modeLeft, Axis.VERTICAL, scale)
        if (spec.modeCenter != LauncherFrameSpec.NONE) edge(canvas, parts.center, RectF(x[1], y[1], x[2], y[2]), spec.modeCenter, Axis.BOTH, scale)
        edge(canvas, parts.right, RectF(x[2], y[1], x[3], y[2]), spec.modeRight, Axis.VERTICAL, scale)
        stretch(canvas, parts.bottomLeft, RectF(x[0], y[2], x[1], y[3]))
        edge(canvas, parts.bottom, RectF(x[1], y[2], x[2], y[3]), spec.modeBottom, Axis.HORIZONTAL, scale)
        stretch(canvas, parts.bottomRight, RectF(x[2], y[2], x[3], y[3]))
    }

    private fun stretch(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        if (destination.width() > 0f && destination.height() > 0f) canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun edge(canvas: Canvas, bitmap: Bitmap, destination: RectF, mode: String, axis: Axis, frameScale: Float) {
        if (destination.width() <= 0f || destination.height() <= 0f) return
        if (mode == LauncherFrameSpec.STRETCH) {
            stretch(canvas, bitmap, destination)
            return
        }
        val scale = when (axis) {
            Axis.HORIZONTAL -> destination.height() / bitmap.height
            Axis.VERTICAL -> destination.width() / bitmap.width
            Axis.BOTH -> tileScale() * frameScale
        }.coerceAtLeast(0.01f)
        val scaleX = if (axis == Axis.VERTICAL) destination.width() / bitmap.width else scale
        val scaleY = if (axis == Axis.HORIZONTAL) destination.height() / bitmap.height else scale
        val shader = BitmapShader(
            bitmap,
            if (axis == Axis.VERTICAL) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT,
            if (axis == Axis.HORIZONTAL) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT
        )
        shaderMatrix.reset()
        shaderMatrix.setScale(scaleX, scaleY)
        shaderMatrix.postTranslate(destination.left.toFloat(), destination.top.toFloat())
        shader.setLocalMatrix(shaderMatrix)
        shaderPaint.shader = shader
        canvas.drawRect(destination, shaderPaint)
        shaderPaint.shader = null
    }

    private fun tileScale(): Float = listOf(
        spec.borderLeftDp * density / spec.sliceLeftPx,
        spec.borderTopDp * density / spec.sliceTopPx,
        spec.borderRightDp * density / spec.sliceRightPx,
        spec.borderBottomDp * density / spec.sliceBottomPx
    ).firstOrNull { it > 0f } ?: 1f

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        shaderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        shaderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private enum class Axis { HORIZONTAL, VERTICAL, BOTH }
}
