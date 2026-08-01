package li.mof.kamigura.reader.internal

import android.content.Context
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import li.mof.kamigura.FileDimensionDto
import li.mof.kamigura.InvertMode
import li.mof.kamigura.KamiguraLog
import li.mof.kamigura.download.OfflinePage
import li.mof.kamigura.download.decodeOfflinePage
import kotlin.math.max
import kotlin.math.min

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
    modifier: Modifier = Modifier
) {
    val layout = readerPageLayout(
        page = cursor,
        pageCount = pageCount,
        portrait = portrait,
        pageDimensions = pageDimensions
    )
    val spread = spreadPagesFor(cursor, rightToLeft)
    Row(modifier) {
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
                    pageBackground = pageBackground
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
                    pageBackground = pageBackground
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
                    pageBackground = pageBackground
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
    invertMode: InvertMode = InvertMode.Off,
    whiteThreshold: Float = 0.5f,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    pageBackground: Color = Color(0xFF111111)
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(pageBackground)
            .clipToBounds(),
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
            targetHeight
        ) {
            value = when (model) {
                null -> PageModelState.Unavailable
                is OfflinePage -> try {
                    decodeOfflinePage(model, targetWidth, targetHeight)
                        ?.let(PageModelState::Ready)
                        ?: PageModelState.Unavailable
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    KamiguraLog.w("Reader offline page decode failed.", t)
                    PageModelState.Unavailable
                }
                else -> PageModelState.Ready(model)
            }
        }
        val resolvedModel = (pageModelState as? PageModelState.Ready)?.model
        val cacheKey = resolvedModel?.let { ReaderInvertCacheKey(it, whiteThreshold) }
        val shouldInvert by produceState<Boolean?>(
            initialValue = when (invertMode) {
                InvertMode.Off -> false
                InvertMode.Always -> true
                InvertMode.Smart -> cacheKey?.let(invertDecisionCache::get)
            },
            resolvedModel,
            invertMode,
            whiteThreshold,
            imageLoader
        ) {
            value = when (invertMode) {
                InvertMode.Off -> false
                InvertMode.Always -> true
                InvertMode.Smart -> {
                    val loadedModel = resolvedModel
                    if (loadedModel == null) {
                        null
                    } else {
                        val key = ReaderInvertCacheKey(loadedModel, whiteThreshold)
                        invertDecisionCache[key] ?: try {
                            analyzeShouldInvert(
                                ctx,
                                imageLoader,
                                loadedModel,
                                whiteThreshold
                            ).also { invertDecisionCache[key] = it }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            KamiguraLog.w("Reader Smart Invert analysis failed.", t)
                            false
                        }
                    }
                }
            }
        }

        when (pageModelState) {
            PageModelState.Loading -> ReaderPageLoadingPlaceholder()
            PageModelState.Unavailable -> ReaderPageUnavailablePlaceholder()
            is PageModelState.Ready -> {
                if (shouldInvert == null) {
                    ReaderPageLoadingPlaceholder()
                } else {
                    SubcomposeAsyncImage(
                        model = resolvedModel,
                        imageLoader = imageLoader,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        alignment = alignment,
                        contentScale = contentScale,
                        colorFilter = if (shouldInvert == true) NegativeColorFilter else null,
                        loading = {
                            PagePlaceholderContainer {
                                ReaderPageLoadingPlaceholder()
                            }
                        },
                        error = {
                            PagePlaceholderContainer {
                                ReaderPageUnavailablePlaceholder()
                            }
                        }
                    )
                }
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
                    KamiguraLog.w("Reader prefetch failed.", t)
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
                        KamiguraLog.w("Reader Smart Invert pre-analysis failed.", t)
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
    val bitmap = (imageLoader.execute(request) as? SuccessResult)
        ?.drawable
        ?.toBitmap()
        ?: return false

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

