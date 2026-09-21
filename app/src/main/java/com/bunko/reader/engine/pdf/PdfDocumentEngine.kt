package com.bunko.reader.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object PdfDocumentEngine {

    private val rendererMutex = Mutex()

    private val maxCacheBytes = (Runtime.getRuntime().maxMemory() / 4).toInt().coerceIn(64 * 1024 * 1024, 256 * 1024 * 1024)
    private val pageBitmapCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            val bytes = value.allocationByteCount
            return if (bytes > 0) bytes else (value.rowBytes * value.height).coerceAtLeast(1)
        }
    }

    // Strong refs for bitmaps backing currently-mounted pages (reader viewport,
    // slide-transition layers, overview cards). A 10-18MB PDF bitmap is easily
    // LRU-evicted by neighbor prefetch decodes while still on screen; the next
    // cache lookup then misses and the page flashes its paper placeholder before
    // popping back in — on every slide. Pinning is refcounted because the reader,
    // the transition and the overview center card can share one instance, and it
    // is consulted by [getCachedBitmap] so a pinned page always resolves instantly.
    private val pinLock = Any()
    private val pinnedBitmaps = mutableMapOf<String, Pair<Bitmap, Int>>()

    private class ActiveSession(
        val filePath: String,
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer
    )

    private var activeSession: ActiveSession? = null

    private fun getOrCreateRenderer(file: File): PdfRenderer? {
        val current = activeSession
        if (current != null && current.filePath == file.absolutePath) {
            return current.renderer
        }
        closeActiveSession()
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
            val renderer = PdfRenderer(pfd)
            activeSession = ActiveSession(file.absolutePath, pfd, renderer)
            renderer
        } catch (t: Throwable) {
            BunkoLog.w("Failed to open PdfRenderer for ${file.name}", t)
            null
        }
    }

    fun closeActiveSession() {
        activeSession?.let { session ->
            try { session.renderer.close() } catch (_: Throwable) {}
            try { session.pfd.close() } catch (_: Throwable) {}
        }
        activeSession = null
    }

    private fun cacheKey(file: File, pageIndex: Int): String {
        return "${file.absolutePath}:$pageIndex"
    }

    fun getCachedBitmap(
        file: File,
        pageIndex: Int
    ): Bitmap? {
        val key = cacheKey(file, pageIndex)
        synchronized(pinLock) {
            val pinned = pinnedBitmaps[key]
            if (pinned != null && !pinned.first.isRecycled) return pinned.first
        }
        synchronized(pageBitmapCache) {
            val cached = pageBitmapCache.get(key)
            return if (cached != null && !cached.isRecycled) cached else null
        }
    }

    fun pinBitmap(file: File, pageIndex: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = cacheKey(file, pageIndex)
        synchronized(pinLock) {
            val current = pinnedBitmaps[key]
            pinnedBitmaps[key] = if (current != null && current.first === bitmap) {
                current.copy(second = current.second + 1)
            } else {
                // Stale pin for a previous instance (evicted + re-decoded): the old
                // bitmap is no longer displayed, replace the entry.
                Pair(bitmap, 1)
            }
        }
    }

    fun unpinBitmap(file: File, pageIndex: Int, bitmap: Bitmap) {
        val key = cacheKey(file, pageIndex)
        synchronized(pinLock) {
            val current = pinnedBitmaps[key] ?: return
            if (current.first !== bitmap) return
            if (current.second > 1) {
                pinnedBitmaps[key] = current.copy(second = current.second - 1)
            } else {
                pinnedBitmaps.remove(key)
            }
        }
    }

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        rendererMutex.withLock {
            try {
                val renderer = getOrCreateRenderer(file)
                renderer?.pageCount ?: 0
            } catch (t: Throwable) {
                BunkoLog.w("Failed to get PDF page count for ${file.name}", t)
                0
            }
        }
    }

    suspend fun getPageDimensions(file: File): Map<Int, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val dims = mutableMapOf<Int, Pair<Int, Int>>()
        rendererMutex.withLock {
            try {
                val renderer = getOrCreateRenderer(file) ?: return@withLock dims
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        dims[i] = Pair(page.width, page.height)
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to get PDF page dimensions for ${file.name}", t)
            }
        }
        dims
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = cacheKey(file, pageIndex)
        getCachedBitmap(file, pageIndex)?.let { return@withContext it }

        rendererMutex.withLock {
            getCachedBitmap(file, pageIndex)?.let { return@withLock it }

            try {
                val renderer = getOrCreateRenderer(file) ?: return@withLock null
                if (pageIndex !in 0 until renderer.pageCount) return@withLock null

                renderer.openPage(pageIndex).use { page ->
                    val rawW = page.width.coerceAtLeast(1)
                    val rawH = page.height.coerceAtLeast(1)

                    val scale = when {
                        targetWidth > 0 && targetHeight > 0 -> {
                            val scaleW = targetWidth.toFloat() / rawW
                            val scaleH = targetHeight.toFloat() / rawH
                            maxOf(scaleW, scaleH, 1.5f).coerceIn(1.5f, 3.0f)
                        }
                        targetWidth > 0 -> (targetWidth.toFloat() / rawW).coerceIn(1.5f, 3.0f)
                        targetHeight > 0 -> (targetHeight.toFloat() / rawH).coerceIn(1.5f, 3.0f)
                        else -> 1.5f // Default 1.5x for crisp readability
                    }

                    val outW = (rawW * scale).toInt().coerceAtLeast(1)
                    val outH = (rawH * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    synchronized(pageBitmapCache) {
                        pageBitmapCache.put(key, bitmap)
                    }
                    bitmap
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to render PDF page $pageIndex in ${file.name}", t)
                closeActiveSession()
                null
            }
        }
    }

    suspend fun extractCoverBitmap(
        file: File,
        maxWidth: Int = 400,
        maxHeight: Int = 600
    ): Bitmap? = withContext(Dispatchers.IO) {
        renderPageBitmap(file, 0, maxWidth, maxHeight)
    }
}
