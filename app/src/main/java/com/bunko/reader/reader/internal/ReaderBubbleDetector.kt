package com.bunko.reader.reader.internal

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import com.bunko.reader.BunkoLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a detected speech bubble or dialog text region in a comic/manga page.
 */
data class ComicSpeechBubble(
    val id: String,
    val pageIndex: Int,
    /** Coordinates in 0f..1f range relative to page image width/height */
    val normalizedRect: RectF,
    /** Raw text detected inside the bubble */
    val text: String,
    /** Pixel bounds of the expanded balloon on the source bitmap */
    val bubbleRect: Rect
)

object ReaderBubbleDetector {
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Cache up to 100 pages of detected bubbles in memory
    private val bubbleCache = LruCache<String, List<ComicSpeechBubble>>(100)

    private const val MAX_OCR_DIMENSION = 1280f

    /**
     * Detects speech bubbles asynchronously from a comic page Bitmap using ML Kit on-device vision.
     */
    suspend fun detectBubbles(
        cacheKey: String,
        pageIndex: Int,
        bitmap: Bitmap
    ): List<ComicSpeechBubble> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext emptyList()
        }

        bubbleCache.get(cacheKey)?.let { return@withContext it }

        val origW = bitmap.width
        val origH = bitmap.height

        // Downscale bitmap if larger than 1280px to ensure fast (~20ms) and battery-efficient OCR
        val scale = if (max(origW, origH) > MAX_OCR_DIMENSION) {
            MAX_OCR_DIMENSION / max(origW, origH).toFloat()
        } else {
            1.0f
        }

        val scaledBitmap = if (scale < 1.0f) {
            val targetW = (origW * scale).toInt().coerceAtLeast(1)
            val targetH = (origH * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val inputImage = try {
            InputImage.fromBitmap(scaledBitmap, 0)
        } catch (t: Throwable) {
            BunkoLog.w("Failed to create InputImage for Bubble Zoom", t)
            return@withContext emptyList()
        }

        val visionText: Text? = suspendCancellableCoroutine { cont ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { textResult ->
                    if (cont.isActive) cont.resume(textResult)
                }
                .addOnFailureListener { error ->
                    BunkoLog.w("ML Kit Bubble Detection failed", error)
                    if (cont.isActive) cont.resume(null)
                }
        }

        if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        if (visionText == null || visionText.textBlocks.isEmpty()) {
            val empty = emptyList<ComicSpeechBubble>()
            bubbleCache.put(cacheKey, empty)
            return@withContext empty
        }

        val rawBlocks = visionText.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            // Scale back bounding box to original bitmap space
            val invScale = 1.0f / scale
            val left = (box.left * invScale).toInt().coerceIn(0, origW)
            val top = (box.top * invScale).toInt().coerceIn(0, origH)
            val right = (box.right * invScale).toInt().coerceIn(0, origW)
            val bottom = (box.bottom * invScale).toInt().coerceIn(0, origH)
            val text = block.text.trim()
            if (text.isEmpty() || (right - left) < 12 || (bottom - top) < 12) {
                null
            } else {
                RawBlock(Rect(left, top, right, bottom), text)
            }
        }

        val mergedBlocks = mergeAdjacentBlocks(rawBlocks, origW, origH)

        val bubbles = mergedBlocks.mapIndexed { idx, mb ->
            val bubbleRect = detectBalloonBounds(bitmap, mb.rect, origW, origH)
            val normRect = RectF(
                bubbleRect.left.toFloat() / origW,
                bubbleRect.top.toFloat() / origH,
                bubbleRect.right.toFloat() / origW,
                bubbleRect.bottom.toFloat() / origH
            )

            ComicSpeechBubble(
                id = "${pageIndex}_$idx",
                pageIndex = pageIndex,
                normalizedRect = normRect,
                text = mb.text,
                bubbleRect = bubbleRect
            )
        }

        bubbleCache.put(cacheKey, bubbles)
        bubbles
    }

    /**
     * Hit tests a normalized tap coordinate (0..1, 0..1) against detected bubbles.
     */
    fun findTappedBubble(
        tapNormalized: Offset,
        bubbles: List<ComicSpeechBubble>
    ): ComicSpeechBubble? {
        if (bubbles.isEmpty()) return null

        val matches = bubbles.filter { bubble ->
            bubble.normalizedRect.contains(tapNormalized.x, tapNormalized.y)
        }

        if (matches.isEmpty()) return null
        if (matches.size == 1) return matches.first()

        // If multiple overlapping boxes hit, pick the one closest to tap center
        return matches.minByOrNull { bubble ->
            val cx = bubble.normalizedRect.centerX()
            val cy = bubble.normalizedRect.centerY()
            hypot(cx - tapNormalized.x, cy - tapNormalized.y)
        }
    }

    private data class RawBlock(val rect: Rect, val text: String)

    private fun mergeAdjacentBlocks(blocks: List<RawBlock>, maxW: Int, maxH: Int): List<RawBlock> {
        if (blocks.size <= 1) return blocks

        val merged = mutableListOf<RawBlock>()
        val used = BooleanArray(blocks.size)

        for (i in blocks.indices) {
            if (used[i]) continue
            var currentRect = Rect(blocks[i].rect)
            val currentText = StringBuilder(blocks[i].text)
            used[i] = true

            var changed = true
            while (changed) {
                changed = false
                for (j in blocks.indices) {
                    if (used[j]) continue
                    val other = blocks[j]
                    // If blocks are vertically adjacent (same column/speech bubble) or close
                    val verticalDistance = min(
                        Math.abs(currentRect.top - other.rect.bottom),
                        Math.abs(other.rect.top - currentRect.bottom)
                    )
                    val horizontalOverlap = min(currentRect.right, other.rect.right) - max(currentRect.left, other.rect.left)

                    val isClose = (verticalDistance < maxH * 0.04f && horizontalOverlap > 0) ||
                            (Rect.intersects(currentRect, other.rect))

                    if (isClose) {
                        currentRect.union(other.rect)
                        currentText.append("\n").append(other.text)
                        used[j] = true
                        changed = true
                    }
                }
            }
            merged.add(RawBlock(currentRect, currentText.toString()))
        }
        return merged
    }

    private fun detectBalloonBounds(
        bitmap: Bitmap,
        textRect: Rect,
        origW: Int,
        origH: Int
    ): Rect {
        // Base comfortable padding around text lines so text is NEVER cut off
        val basePadX = ((textRect.width() * 0.22f).toInt()).coerceIn(20, 80)
        val basePadY = ((textRect.height() * 0.24f).toInt()).coerceIn(20, 90)

        var left = (textRect.left - basePadX).coerceAtLeast(0)
        var top = (textRect.top - basePadY).coerceAtLeast(0)
        var right = (textRect.right + basePadX).coerceAtMost(origW)
        var bottom = (textRect.bottom + basePadY).coerceAtMost(origH)

        if (bitmap.isRecycled) return Rect(left, top, right, bottom)

        // Try scanning outward for the comic speech bubble's ink contour
        try {
            val cx = textRect.centerX().coerceIn(0, origW - 1)
            val cy = textRect.centerY().coerceIn(0, origH - 1)

            val maxScanX = min(origW / 6, 140)
            val maxScanY = min(origH / 6, 140)

            // Scan Left
            var scanLeft = textRect.left
            while (scanLeft > 4 && (textRect.left - scanLeft) < maxScanX) {
                val p = bitmap.getPixel(scanLeft, cy)
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                if (lum < 100) { // hit dark ink border
                    scanLeft -= 4 // enclose the border line
                    break
                }
                scanLeft -= 2
            }
            if (scanLeft < left) left = scanLeft.coerceAtLeast(0)

            // Scan Right
            var scanRight = textRect.right
            while (scanRight < origW - 5 && (scanRight - textRect.right) < maxScanX) {
                val p = bitmap.getPixel(scanRight, cy)
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                if (lum < 100) {
                    scanRight += 4
                    break
                }
                scanRight += 2
            }
            if (scanRight > right) right = scanRight.coerceAtMost(origW)

            // Scan Top
            var scanTop = textRect.top
            while (scanTop > 4 && (textRect.top - scanTop) < maxScanY) {
                val p = bitmap.getPixel(cx, scanTop)
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                if (lum < 100) {
                    scanTop -= 4
                    break
                }
                scanTop -= 2
            }
            if (scanTop < top) top = scanTop.coerceAtLeast(0)

            // Scan Bottom
            var scanBottom = textRect.bottom
            while (scanBottom < origH - 5 && (scanBottom - textRect.bottom) < maxScanY) {
                val p = bitmap.getPixel(cx, scanBottom)
                val lum = (((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)) / 3
                if (lum < 100) {
                    scanBottom += 4
                    break
                }
                scanBottom += 2
            }
            if (scanBottom > bottom) bottom = scanBottom.coerceAtMost(origH)
        } catch (_: Throwable) {
            // fallback to padded rect
        }

        return Rect(left, top, right, bottom)
    }

    fun clearCache() {
        bubbleCache.evictAll()
    }
}
