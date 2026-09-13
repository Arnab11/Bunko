package com.bunko.reader.reader.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size as CoilSize
import coil.transform.Transformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.InvertMode
import com.bunko.reader.EPaperMode
import com.bunko.reader.ReaderImageScaleType
import com.bunko.reader.BunkoLog
import com.bunko.reader.download.OfflinePage
import com.bunko.reader.download.decodeOfflinePage
import kotlin.math.max
import kotlin.math.min

internal object SmartFitContentScale : ContentScale {
    override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f || dstSize.width <= 0f || dstSize.height <= 0f) {
            return ScaleFactor(1f, 1f)
        }
        val srcAspect = srcSize.width / srcSize.height
        val dstAspect = dstSize.width / dstSize.height
        return if (srcAspect > dstAspect) {
            // Wider than viewport: fit screen maintaining aspect ratio
            ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
        } else {
            // Taller or standard aspect: fit width
            ContentScale.FillWidth.computeScaleFactor(srcSize, dstSize)
        }
    }
}

internal fun readerContentScale(scaleType: ReaderImageScaleType): ContentScale {
    return when (scaleType) {
        ReaderImageScaleType.FitScreen -> ContentScale.Fit
        ReaderImageScaleType.Stretch -> ContentScale.FillBounds
        ReaderImageScaleType.FitWidth -> ContentScale.FillWidth
        ReaderImageScaleType.FitHeight -> ContentScale.FillHeight
        ReaderImageScaleType.OriginalSize -> ContentScale.None
        ReaderImageScaleType.SmartFit -> SmartFitContentScale
    }
}

/**
 * Intelligent pure-Kotlin border cropping transformation for comics and manga.
 * Identifies whitespace or dark scan borders by sampling border pixels and
 * crops the bitmap safely without encroaching on page artwork.
 */
internal fun cropBorderFromBitmap(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 32 || height < 32) return bitmap

    fun isWhite(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r >= 238 && g >= 238 && b >= 238
    }

    fun isBlack(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r <= 20 && g <= 20 && b <= 20
    }

    val corners = intArrayOf(
        bitmap.getPixel(0, 0),
        bitmap.getPixel(width - 1, 0),
        bitmap.getPixel(0, height - 1),
        bitmap.getPixel(width - 1, height - 1)
    )

    val whiteCount = corners.count { isWhite(it) }
    val blackCount = corners.count { isBlack(it) }

    val isBorderColor: (Int) -> Boolean = when {
        whiteCount >= 2 -> ::isWhite
        blackCount >= 2 -> ::isBlack
        else -> return bitmap
    }

    val maxCropX = (width * 0.25f).toInt()
    val maxCropY = (height * 0.25f).toInt()
    val sampleStepX = max(1, width / 64)
    val sampleStepY = max(1, height / 64)

    fun isRowBorder(y: Int): Boolean {
        var nonBorderCount = 0
        var totalSamples = 0
        var x = 0
        while (x < width) {
            if (!isBorderColor(bitmap.getPixel(x, y))) {
                nonBorderCount++
                if (nonBorderCount > 2) return false
            }
            totalSamples++
            x += sampleStepX
        }
        return (nonBorderCount.toFloat() / totalSamples.coerceAtLeast(1)) <= 0.05f
    }

    fun isColBorder(x: Int): Boolean {
        var nonBorderCount = 0
        var totalSamples = 0
        var y = 0
        while (y < height) {
            if (!isBorderColor(bitmap.getPixel(x, y))) {
                nonBorderCount++
                if (nonBorderCount > 2) return false
            }
            totalSamples++
            y += sampleStepY
        }
        return (nonBorderCount.toFloat() / totalSamples.coerceAtLeast(1)) <= 0.05f
    }

    var top = 0
    while (top < maxCropY && isRowBorder(top)) {
        top++
    }

    var bottom = height - 1
    while (bottom > height - 1 - maxCropY && bottom > top && isRowBorder(bottom)) {
        bottom--
    }

    var left = 0
    while (left < maxCropX && isColBorder(left)) {
        left++
    }

    var right = width - 1
    while (right > width - 1 - maxCropX && right > left && isColBorder(right)) {
        right--
    }

    val cropW = right - left + 1
    val cropH = bottom - top + 1

    if ((top >= 4 || (height - 1 - bottom) >= 4 || left >= 4 || (width - 1 - right) >= 4) &&
        cropW >= width / 2 && cropH >= height / 2
    ) {
        return try {
            Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        } catch (t: Throwable) {
            BunkoLog.w("Failed to crop bitmap borders", t)
            bitmap
        }
    }

    return bitmap
}

internal class CropBordersTransformation : Transformation {
    override val cacheKey: String = "bunko_crop_borders_v1"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        return cropBorderFromBitmap(input)
    }
}

private const val SmartInvertSampleSize = 64
private const val SmartInvertColorThreshold = 0.1f
private const val SmartInvertWhiteChannelMin = 217
private const val SmartInvertSaturationMin = 0.2f
private const val SmartInvertColorValueMin = 40

private sealed interface PageModelState {
    data object Loading : PageModelState
    data object Unavailable : PageModelState
    data class Ready(val model: Any) : PageModelState
}

private val NegativeColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

// Monochrome E-Paper: Rec.709 luminance with deep e-ink contrast curve
private val EPaperBwColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.2381f, 0.8010f, 0.0809f, 0f, -10f,
            0.2381f, 0.8010f, 0.0809f, 0f, -10f,
            0.2296f, 0.7724f, 0.0780f, 0f, -12f,
            0f,      0f,      0f,      1f, 0f
        )
    )
)

private val EPaperBwInvertedFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.2381f, -0.8010f, -0.0809f, 0f, 265f,
            -0.2381f, -0.8010f, -0.0809f, 0f, 265f,
            -0.2296f, -0.7724f, -0.0780f, 0f, 267f,
            0f,        0f,        0f,      1f, 0f
        )
    )
)

// Color E-Paper (Kaleido 3 style): ~50% saturation, subtle warm paper cast, enhanced linework contrast
private val EPaperColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.626f, 0.394f, 0.040f, 0f, -5f,
            0.117f, 0.903f, 0.040f, 0f, -6f,
            0.113f, 0.380f, 0.528f, 0f, -12f,
            0f,     0f,     0f,     1f, 0f
        )
    )
)

private val EPaperColorInvertedFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.626f, -0.394f, -0.040f, 0f, 260f,
            -0.117f, -0.903f, -0.040f, 0f, 261f,
            -0.113f, -0.380f, -0.528f, 0f, 267f,
            0f,       0f,      0f,     1f, 0f
        )
    )
)

internal fun readerColorFilter(
    ePaperMode: EPaperMode,
    invert: Boolean
): ColorFilter? = when {
    ePaperMode == EPaperMode.Off && !invert -> null
    ePaperMode == EPaperMode.Off && invert -> NegativeColorFilter
    ePaperMode == EPaperMode.BlackAndWhite && !invert -> EPaperBwColorFilter
    ePaperMode == EPaperMode.BlackAndWhite && invert -> EPaperBwInvertedFilter
    ePaperMode == EPaperMode.Color && !invert -> EPaperColorFilter
    ePaperMode == EPaperMode.Color && invert -> EPaperColorInvertedFilter
    else -> null
}

private fun generateBwGrainBitmap(size: Int = 256): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = java.util.Random(1337L)

    // Seamless tileable value noise grids
    val macroDim = 16
    val macroGrid = FloatArray(macroDim * macroDim) { random.nextFloat() }

    val microDim = 64
    val microGrid = FloatArray(microDim * microDim) { random.nextFloat() }

    fun sampleGrid(grid: FloatArray, dim: Int, x: Int, y: Int): Float {
        val fx = (x * dim.toFloat()) / size
        val fy = (y * dim.toFloat()) / size
        val x0 = fx.toInt() % dim
        val y0 = fy.toInt() % dim
        val x1 = (x0 + 1) % dim
        val y1 = (y0 + 1) % dim
        val sx = (fx - x0.toFloat()).let { it * it * (3f - 2f * it) }
        val sy = (fy - y0.toFloat()).let { it * it * (3f - 2f * it) }
        val top = grid[y0 * dim + x0] * (1f - sx) + grid[y0 * dim + x1] * sx
        val bottom = grid[y1 * dim + x0] * (1f - sx) + grid[y1 * dim + x1] * sx
        return top * (1f - sy) + bottom * sy
    }

    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val macro = sampleGrid(macroGrid, macroDim, x, y)
            val micro = sampleGrid(microGrid, microDim, x, y)
            val fine = random.nextFloat()

            val noise = macro * 0.25f + micro * 0.35f + fine * 0.40f

            val pixel = when {
                // Microscopic e-ink pigment particles & paper tooth
                noise < 0.40f -> {
                    val strength = (0.40f - noise) / 0.40f
                    val alpha = (strength * 22f).toInt().coerceIn(0, 16)
                    (alpha shl 24) or 0x161616
                }
                // Subtle paper fiber highlights
                noise > 0.60f -> {
                    val strength = (noise - 0.60f) / 0.40f
                    val alpha = (strength * 18f).toInt().coerceIn(0, 13)
                    (alpha shl 24) or 0xFAF8F5
                }
                else -> 0
            }
            pixels[y * size + x] = pixel
        }
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap
}

private fun generateColorGrainBitmap(size: Int = 256): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = java.util.Random(4242L)

    val macroDim = 16
    val macroGrid = FloatArray(macroDim * macroDim) { random.nextFloat() }

    val microDim = 64
    val microGrid = FloatArray(microDim * microDim) { random.nextFloat() }

    fun sampleGrid(grid: FloatArray, dim: Int, x: Int, y: Int): Float {
        val fx = (x * dim.toFloat()) / size
        val fy = (y * dim.toFloat()) / size
        val x0 = fx.toInt() % dim
        val y0 = fy.toInt() % dim
        val x1 = (x0 + 1) % dim
        val y1 = (y0 + 1) % dim
        val sx = (fx - x0.toFloat()).let { it * it * (3f - 2f * it) }
        val sy = (fy - y0.toFloat()).let { it * it * (3f - 2f * it) }
        val top = grid[y0 * dim + x0] * (1f - sx) + grid[y0 * dim + x1] * sx
        val bottom = grid[y1 * dim + x0] * (1f - sx) + grid[y1 * dim + x1] * sx
        return top * (1f - sy) + bottom * sy
    }

    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val macro = sampleGrid(macroGrid, macroDim, x, y)
            val micro = sampleGrid(microGrid, microDim, x, y)
            val fine = random.nextFloat()

            val noise = macro * 0.25f + micro * 0.35f + fine * 0.40f

            // Color Filter Array (CFA) micro-mosaic simulation (Kaleido 3 diagonal subpixel grid)
            val cfaPhase = (x + y * 2) % 3
            val cfaColor = when (cfaPhase) {
                0 -> 0xB84232 // Faint terracotta/red
                1 -> 0x3A7A44 // Faint sage/green
                else -> 0x365E9D // Faint slate/blue
            }
            val cfaAlpha = if (fine > 0.45f) {
                ((fine - 0.45f) * 18f).toInt().coerceIn(0, 11)
            } else 0

            val pixel = when {
                cfaAlpha > 0 -> (cfaAlpha shl 24) or cfaColor
                noise < 0.38f -> {
                    val strength = (0.38f - noise) / 0.38f
                    val alpha = (strength * 20f).toInt().coerceIn(0, 14)
                    (alpha shl 24) or 0x1E1E1E
                }
                noise > 0.62f -> {
                    val strength = (noise - 0.62f) / 0.38f
                    val alpha = (strength * 16f).toInt().coerceIn(0, 11)
                    (alpha shl 24) or 0xFBF9F4
                }
                else -> 0
            }
            pixels[y * size + x] = pixel
        }
    }
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap
}

private val EPaperBwGrainBrush: Brush by lazy {
    val bitmap = generateBwGrainBitmap()
    val imageShader = ImageShader(
        image = bitmap.asImageBitmap(),
        tileModeX = TileMode.Repeated,
        tileModeY = TileMode.Repeated
    )
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader = imageShader
    }
}

private val EPaperColorGrainBrush: Brush by lazy {
    val bitmap = generateColorGrainBitmap()
    val imageShader = ImageShader(
        image = bitmap.asImageBitmap(),
        tileModeX = TileMode.Repeated,
        tileModeY = TileMode.Repeated
    )
    object : ShaderBrush() {
        override fun createShader(size: Size): Shader = imageShader
    }
}

internal fun Modifier.ePaperGrain(ePaperMode: EPaperMode): Modifier =
    if (ePaperMode == EPaperMode.Off) this
    else this.drawWithContent {
        drawContent()
        val brush = if (ePaperMode == EPaperMode.Color) EPaperColorGrainBrush else EPaperBwGrainBrush
        drawRect(brush = brush)
    }

/** Internal to reader, not for external use. */
@Composable
internal fun ReaderPageView(
    cursor: Int,
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    rightToLeft: Boolean,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    invertMode: InvertMode,
    whiteThreshold: Float,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    pageBackground: Color = Color(0xFF111111),
    singlePageAlignmentOverride: Alignment? = null,
    ePaperMode: EPaperMode = EPaperMode.Off,
    imageScaleType: ReaderImageScaleType = ReaderImageScaleType.FitScreen,
    cropBorders: Boolean = false,
    modifier: Modifier = Modifier
) {
    val layout = readerPageLayout(
        page = cursor,
        pageCount = pageCount,
        portrait = portrait,
        pageDimensions = pageDimensions
    )
    val spread = spreadPagesFor(cursor, rightToLeft)
    Row(modifier.background(pageBackground)) {
        if (layout.singlePage) {
            key(cursor) {
                PageImage(
                    model = pageModel(cursor),
                    imageLoader = imageLoader,
                    label = "Page $cursor",
                    alignment = singlePageAlignmentOverride ?: layout.singleAlignment,
                    invertMode = invertMode,
                    whiteThreshold = whiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = pageBackground,
                    ePaperMode = ePaperMode,
                    imageScaleType = imageScaleType,
                    cropBorders = cropBorders
                )
            }
        } else {
            key(spread.leftPage) {
                PageImage(
                    model = pageModel(spread.leftPage),
                    imageLoader = imageLoader,
                    label = "Left ${spread.leftPage}",
                    alignment = Alignment.CenterEnd,
                    invertMode = invertMode,
                    whiteThreshold = whiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = pageBackground,
                    ePaperMode = ePaperMode,
                    imageScaleType = imageScaleType,
                    cropBorders = cropBorders
                )
            }
            key(spread.rightPage) {
                PageImage(
                    model = pageModel(spread.rightPage),
                    imageLoader = imageLoader,
                    label = "Right ${spread.rightPage}",
                    alignment = Alignment.CenterStart,
                    invertMode = invertMode,
                    whiteThreshold = whiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    pageBackground = pageBackground,
                    ePaperMode = ePaperMode,
                    imageScaleType = imageScaleType,
                    cropBorders = cropBorders
                )
            }
        }
    }
}

@Composable
private fun RowScope.PageImage(
    model: Any?,
    imageLoader: ImageLoader,
    label: String,
    alignment: Alignment,
    contentScale: ContentScale = ContentScale.Fit,
    imageScaleType: ReaderImageScaleType = ReaderImageScaleType.FitScreen,
    cropBorders: Boolean = false,
    invertMode: InvertMode = InvertMode.Off,
    whiteThreshold: Float = 0.5f,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    pageBackground: Color = Color(0xFF111111),
    ePaperMode: EPaperMode = EPaperMode.Off
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(pageBackground),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = with(density) { maxWidth.toPx().toInt() }
        val targetHeight = with(density) { maxHeight.toPx().toInt() }
        val pageModelState by produceState<PageModelState>(
            initialValue = when (model) {
                null -> PageModelState.Unavailable
                is OfflinePage -> PageModelState.Loading
                else -> PageModelState.Ready(model)
            },
            model,
            targetWidth,
            targetHeight,
            cropBorders
        ) {
            value = when (model) {
                null -> PageModelState.Unavailable
                is OfflinePage -> try {
                    val decoded = decodeOfflinePage(model, targetWidth, targetHeight)
                    val processed = if (cropBorders && decoded is Bitmap) {
                        cropBorderFromBitmap(decoded)
                    } else decoded
                    processed?.let(PageModelState::Ready) ?: PageModelState.Unavailable
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    BunkoLog.w("Reader offline page decode failed.", t)
                    PageModelState.Unavailable
                }
                else -> PageModelState.Ready(model)
            }
        }
        val resolvedModel = (pageModelState as? PageModelState.Ready)?.model
        val cacheKey = resolvedModel?.let { ReaderInvertCacheKey(it, whiteThreshold) }
        val cachedInvertDecision = cacheKey?.let(invertDecisionCache::get)
        var loadedDrawable by remember(resolvedModel, imageLoader) {
            mutableStateOf<Drawable?>(null)
        }
        val shouldInvert by produceState<Boolean?>(
            initialValue = when (invertMode) {
                InvertMode.Off -> false
                InvertMode.Always -> true
                InvertMode.Smart -> cachedInvertDecision
            },
            resolvedModel,
            invertMode,
            whiteThreshold,
            loadedDrawable,
            cachedInvertDecision
        ) {
            value = when (invertMode) {
                InvertMode.Off -> false
                InvertMode.Always -> true
                InvertMode.Smart -> {
                    val loadedModel = resolvedModel
                    val drawable = loadedDrawable
                    if (loadedModel == null || drawable == null) {
                        null
                    } else {
                        val key = ReaderInvertCacheKey(loadedModel, whiteThreshold)
                        cachedInvertDecision ?: try {
                            try {
                                analyzeShouldInvert(drawable, whiteThreshold)
                            } catch (t: Throwable) {
                                BunkoLog.w(
                                    "Reader Smart Invert drawable analysis failed; retrying from cache.",
                                    t
                                )
                                analyzeShouldInvert(
                                    ctx,
                                    imageLoader,
                                    loadedModel,
                                    whiteThreshold
                                )
                            }.also { invertDecisionCache[key] = it }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            BunkoLog.w("Reader Smart Invert analysis failed.", t)
                            null
                        }
                    }
                }
            }
        }

        when (pageModelState) {
            PageModelState.Loading -> ReaderPageLoadingPlaceholder()
            PageModelState.Unavailable -> ReaderPageUnavailablePlaceholder()
            is PageModelState.Ready -> {
                val imageRequest = remember(resolvedModel, cropBorders, ctx) {
                    if (resolvedModel == null) null
                    else if (resolvedModel is Bitmap || resolvedModel is Drawable) {
                        resolvedModel
                    } else {
                        ImageRequest.Builder(ctx)
                            .data(resolvedModel)
                            .apply {
                                if (cropBorders) {
                                    transformations(CropBordersTransformation())
                                }
                            }
                            .build()
                    }
                }
                SubcomposeAsyncImage(
                    model = imageRequest,
                    imageLoader = imageLoader,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .ePaperGrain(ePaperMode),
                    alignment = alignment,
                    contentScale = if (contentScale != ContentScale.Fit) contentScale else readerContentScale(imageScaleType),
                    colorFilter = readerColorFilter(ePaperMode, shouldInvert == true),
                    onSuccess = { state ->
                        loadedDrawable = state.result.drawable
                    },
                    loading = {
                        PagePlaceholderContainer {
                            ReaderPageLoadingPlaceholder()
                        }
                    },
                    error = {
                        PagePlaceholderContainer {
                            ReaderPageUnavailablePlaceholder()
                        }
                    },
                    success = {
                        if (invertMode == InvertMode.Smart && shouldInvert == null) {
                            PagePlaceholderContainer {
                                ReaderPageLoadingPlaceholder()
                            }
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PagePlaceholderContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// Loading is transient and usually pre-warmed, so it stays a whisper. Unavailable persists,
// and a scanned book can legitimately contain a blank page, so it gets a mark that cannot be
// mistaken for one — still static and grey, never an animated indicator.
@Composable
private fun ReaderPageLoadingPlaceholder() {
    Text("...", color = Color.Gray)
}

@Composable
private fun ReaderPageUnavailablePlaceholder() {
    Icon(
        imageVector = Icons.Outlined.BrokenImage,
        contentDescription = "Page unavailable",
        tint = Color.Gray,
        modifier = Modifier.size(36.dp)
    )
}

/** Internal to reader, not for external use. */
internal suspend fun prefetchReaderPages(
    context: Context,
    imageLoader: ImageLoader,
    targets: List<ReaderPrefetchTarget>
) = coroutineScope {
    val semaphore = Semaphore(ReaderPrefetchConcurrency)
    targets.map { target ->
        launch {
            semaphore.withPermit {
                // Prefetch targets are already bounded to the reader's RAM working set.
                // Previously displayed pages remain available through Coil's disk LRU.
                val request = ImageRequest.Builder(context)
                    .data(target.model)
                    .size(target.targetWidth, target.targetHeight)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
                try {
                    imageLoader.execute(request)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    BunkoLog.w("Reader prefetch failed.", t)
                    // Prefetch is opportunistic; the visible page reports its own error.
                }
            }
        }
    }.forEach { it.join() }
}

/** Internal to reader, not for external use. */
internal suspend fun preAnalyzeReaderPages(
    context: Context,
    imageLoader: ImageLoader,
    models: List<String>,
    whiteThreshold: Float,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
) = coroutineScope {
    val semaphore = Semaphore(ReaderPrefetchConcurrency)
    models.map { model ->
        launch {
            semaphore.withPermit {
                val key = ReaderInvertCacheKey(model, whiteThreshold)
                if (key !in invertDecisionCache) {
                    try {
                        // Pre-analyzing Smart Invert keeps page-turn transitions from flashing unfiltered pages.
                        invertDecisionCache[key] = analyzeShouldInvert(
                            context,
                            imageLoader,
                            model,
                            whiteThreshold
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        BunkoLog.w("Reader Smart Invert pre-analysis failed.", t)
                        // Visible rendering retries the analysis if pre-analysis fails.
                    }
                }
            }
        }
    }.forEach { it.join() }
}

/**
 * Loads a small downscaled copy of [model] and decides whether the page looks like
 * a text page (mostly white, low color) that should be inverted for night reading.
 * Illustration / color pages return false so they are shown normally.
 */
private suspend fun analyzeShouldInvert(
    ctx: Context,
    imageLoader: coil.ImageLoader,
    model: Any,
    whiteThreshold: Float
): Boolean {
    val request = ImageRequest.Builder(ctx)
        .data(model)
        .size(SmartInvertSampleSize)
        .allowHardware(false)
        .build()
    val result = imageLoader.execute(request)
    if (result !is SuccessResult) {
        error("Smart Invert sample image could not be loaded.")
    }
    return analyzeShouldInvert(result.drawable, whiteThreshold)
}

private fun analyzeShouldInvert(drawable: Drawable, whiteThreshold: Float): Boolean {
    val bitmap = drawable
        .toBitmap(SmartInvertSampleSize, SmartInvertSampleSize)
        .copy(Bitmap.Config.ARGB_8888, false)
        ?: error("Smart Invert sample image could not be copied.")

    val width = bitmap.width
    val height = bitmap.height
    val total = width * height
    if (total <= 0) return false

    val pixels = IntArray(total)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var whiteCount = 0
    var colorCount = 0
    for (pixel in pixels) {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        if (mn >= SmartInvertWhiteChannelMin) whiteCount++
        val saturation = if (mx == 0) 0f else (mx - mn).toFloat() / mx
        if (mx >= SmartInvertColorValueMin && saturation >= SmartInvertSaturationMin) colorCount++
    }

    val whiteRatio = whiteCount.toFloat() / total
    val colorRatio = colorCount.toFloat() / total
    return whiteRatio >= whiteThreshold && colorRatio <= SmartInvertColorThreshold
}

