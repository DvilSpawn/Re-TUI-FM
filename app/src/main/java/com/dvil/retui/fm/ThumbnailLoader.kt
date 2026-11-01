package com.dvil.retui.fm

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import android.util.Size
import android.net.Uri
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.util.concurrent.Executors

internal object ThumbnailLoader {
    private const val MAX_CACHE_KB = 12 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val executor = Executors.newFixedThreadPool(2)

    fun load(context: Context, file: File, uri: Uri?, sizePx: Int, callback: (Bitmap?) -> Unit) {
        val key = "${uri ?: file.absolutePath}:$sizePx:${file.lastModified()}:${file.length()}"
        cache.get(key)?.let { callback(it); return }
        executor.execute {
            val bitmap = runCatching { decode(context, file, uri, sizePx) }.getOrNull()
            if (bitmap != null) cache.put(key, bitmap)
            android.os.Handler(context.mainLooper).post { callback(bitmap) }
        }
    }

    private fun decode(context: Context, file: File, uri: Uri?, sizePx: Int): Bitmap? {
        val extension = file.extension.lowercase()
        if (extension in IMAGE_EXTENSIONS) {
            return if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    // Scale by the longer side so the bitmap keeps the image's aspect ratio.
                    val scale = minOf(1f, sizePx.toFloat() / maxOf(info.size.width, info.size.height).coerceAtLeast(1))
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                }
            } else decodeSampled(file, sizePx)
        }
        if (extension in VIDEO_EXTENSIONS) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(sizePx, sizePx), null)
            } else {
                @Suppress("DEPRECATION") ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        }
        if (extension in AUDIO_EXTENSIONS) return audioArtwork(file)
        if (extension == "pdf") return pdfFirstPage(file, sizePx)
        return null
    }

    private fun decodeSampled(file: File, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun sampleSize(width: Int, height: Int, sizePx: Int): Int {
        var sample = 1
        while (width / sample > sizePx * 2 || height / sample > sizePx * 2) sample *= 2
        return sample
    }

    private fun audioArtwork(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } finally {
            retriever.release()
        }
    }

    private fun pdfFirstPage(file: File, sizePx: Int): Bitmap? {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = sizePx.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "bmp", "avif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "m4a", "ogg", "opus", "aac")
}
