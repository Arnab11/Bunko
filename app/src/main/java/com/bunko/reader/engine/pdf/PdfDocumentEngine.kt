package com.bunko.reader.engine.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object PdfDocumentEngine {

    private val rendererMutex = Mutex()

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to get PDF page count for ${file.name}", t)
            0
        }
    }

    suspend fun getPageDimensions(file: File): Map<Int, Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val dims = mutableMapOf<Int, Pair<Int, Int>>()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            dims[i] = Pair(page.width, page.height)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Failed to get PDF page dimensions for ${file.name}", t)
        }
        dims
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        rendererMutex.withLock {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null

                        renderer.openPage(pageIndex).use { page ->
                            val rawW = page.width.coerceAtLeast(1)
                            val rawH = page.height.coerceAtLeast(1)

                            val scale = when {
                                targetWidth > 0 && targetHeight > 0 -> {
                                    val scaleW = targetWidth.toFloat() / rawW
                                    val scaleH = targetHeight.toFloat() / rawH
                                    minOf(scaleW, scaleH).coerceIn(1.0f, 3.0f)
                                }
                                targetWidth > 0 -> (targetWidth.toFloat() / rawW).coerceIn(1.0f, 3.0f)
                                targetHeight > 0 -> (targetHeight.toFloat() / rawH).coerceIn(1.0f, 3.0f)
                                else -> 1.5f // Default 1.5x for crisp readability
                            }

                            val outW = (rawW * scale).toInt().coerceAtLeast(1)
                            val outH = (rawH * scale).toInt().coerceAtLeast(1)

                            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)

                            val matrix = android.graphics.Matrix().apply {
                                setScale(scale, scale)
                            }

                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            } catch (t: Throwable) {
                BunkoLog.w("Failed to render PDF page $pageIndex in ${file.name}", t)
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
