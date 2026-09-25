package com.bunko.reader.reader.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bunko.reader.EPaperMode
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.InvertMode
import com.bunko.reader.ReaderImageScaleType
import com.bunko.reader.download.OfflinePage
import com.bunko.reader.download.decodeOfflinePage
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunko.reader.EpubTextAlign
import karacken.curl.DeckRejectionReason
import karacken.curl.DeckReleaseReason
import karacken.curl.LandscapePageDeck
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PageImage
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import karacken.curl.PortraitPageDeck
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

// Provenance:
// Animation engine is the maintained Darkaxt/PlayLikeCurl fork
// (commit b186c6d11269d9f2fdc3316ec6393ee70a7c87db, production bitmap-deck API v1,
// GLSurfaceView PageSurfaceView + PortraitPageDeck/LandscapePageDeck of immutable
// ARGB_8888 bitmaps, vendored under karacken.curl).
// The legacy karackencurllib-1.0.aar requested for this task
// (karankalsi/PlayLikeCurl release 1.0, 2016, karacken.curl.PageSurfaceView with a
// String[] asset-name PageCurlAdapter) was downloaded to app/libs/ for reference,
// but its API only resolves textures from APK assets and cannot render Kavita/Coil
// network pages, spreads, or RTL — so the fork source above is what actually drives
// the "Play Curl" turn mode.
//
// Content fidelity: every deck bitmap is composited with the same rules as the
// Slide path's ReaderPageView (paper background, spine-glued half alignment,
// image scale types, border crop, Smart/Always invert, e-paper matrices, night
// overlay), so the reader layout and all dialog settings look identical and only
// the turn animation differs. The GL clear color is set to the paper color, so
// letterbox margins and spread seams never show black.

private const val PlayCurlLogTag = "PlayCurlReader"

/** Night overlay: same color and formula as RenderReaderPage. */
private const val PlayCurlNightColor: Int = 0xFFFF9E3D.toInt()

// Color matrices mirror readerColorFilter() in ReaderImagePipeline (Compose
// ColorMatrix and android.graphics.ColorMatrix share the 4x5 row-major layout).
private val PlayCurlNegativeMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)
private val PlayCurlEPaperBwMatrix = floatArrayOf(
    0.2381f, 0.8010f, 0.0809f, 0f, -10f,
    0.2381f, 0.8010f, 0.0809f, 0f, -10f,
    0.2296f, 0.7724f, 0.0780f, 0f, -12f,
    0f, 0f, 0f, 1f, 0f
)
private val PlayCurlEPaperBwInvertedMatrix = floatArrayOf(
    -0.2381f, -0.8010f, -0.0809f, 0f, 265f,
    -0.2381f, -0.8010f, -0.0809f, 0f, 265f,
    -0.2296f, -0.7724f, -0.0780f, 0f, 267f,
    0f, 0f, 0f, 1f, 0f
)
private val PlayCurlEPaperColorMatrix = floatArrayOf(
    0.626f, 0.394f, 0.040f, 0f, -5f,
    0.117f, 0.903f, 0.040f, 0f, -6f,
    0.113f, 0.380f, 0.528f, 0f, -12f,
    0f, 0f, 0f, 1f, 0f
)
private val PlayCurlEPaperColorInvertedMatrix = floatArrayOf(
    -0.626f, -0.394f, -0.040f, 0f, 260f,
    -0.117f, -0.903f, -0.040f, 0f, 261f,
    -0.113f, -0.380f, -0.528f, 0f, 267f,
    0f, 0f, 0f, 1f, 0f
)

/**
 * Mutable host shared between [PlayCurlPage] and the reader gesture pipeline.
 * ReaderScreen owns one via [rememberPlayCurlHostState] and calls
 * [requestTurn]/[beginDrag]/[moveDrag]/[endDrag]/[cancelDrag] from the same places
 * a curl state was driven, so tap zones, RTL, spread steps and chapter boundary
 * actions behave exactly as in Slide mode.
 *
 * RTL is handled by swapping deck positions (not by mirroring the SurfaceView,
 * which a SurfaceView cannot do reliably): for right-to-left the library's NEXT
 * side holds the reader-previous pages, so [requestTurn] inverts the direction.
 * Touch coordinates pass through raw; the library's drag-left-is-NEXT convention
 * then matches the swapped deck automatically.
 */
internal class PlayCurlHostState {
    var view: PageSurfaceView? = null
    var ready: Boolean by mutableStateOf(false)
    var rightToLeft: Boolean = false

    private var downTime: Long = 0L
    private var lastPointer: Offset = Offset.Zero
    private var downActive: Boolean = false

    fun requestTurn(next: Boolean): Boolean {
        val surface = view ?: return false
        val effectiveNext = if (rightToLeft) !next else next
        return try {
            surface.turn(if (effectiveNext) PageChange.NEXT else PageChange.PREVIOUS)
        } catch (_: Exception) {
            false
        }
    }

    fun beginDrag(pointer: Offset) {
        val surface = view ?: return
        downTime = SystemClock.uptimeMillis()
        lastPointer = pointer
        downActive = true
        dispatch(surface, MotionEvent.ACTION_DOWN, pointer)
    }

    fun moveDrag(pointer: Offset) {
        if (!downActive) return
        val surface = view ?: return
        lastPointer = pointer
        dispatch(surface, MotionEvent.ACTION_MOVE, pointer)
    }

    fun endDrag() {
        if (!downActive) return
        downActive = false
        val surface = view ?: return
        dispatch(surface, MotionEvent.ACTION_UP, lastPointer)
    }

    fun cancelDrag() {
        if (!downActive) return
        downActive = false
        val surface = view ?: return
        try {
            surface.cancelGesture()
        } catch (_: Exception) {
        }
        dispatch(surface, MotionEvent.ACTION_CANCEL, lastPointer)
    }

    private fun dispatch(surface: PageSurfaceView, action: Int, pointer: Offset) {
        try {
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, pointer.x, pointer.y, 0
            )
            surface.onPageTouchEvent(event)
            event.recycle()
        } catch (_: Exception) {
        }
    }
}

@Composable
internal fun rememberPlayCurlHostState(): PlayCurlHostState = remember { PlayCurlHostState() }

internal enum class PlayCurlHalf {
    Left,
    Right
}

/**
 * Play Books-style curl surface. Renders the same book spreads as the Slide path
 * (same Coil models, paper, scale, crop, invert, e-paper and night settings)
 * through the PlayLikeCurl GLES2 renderer. Gestures stay owned by
 * [ReaderTapLayer]; this surface only renders and settles turns driven via
 * [PlayCurlHostState].
 */
@Composable
internal fun PlayCurlPage(
    page: Int,
    pageCount: Int,
    portrait: Boolean,
    rightToLeft: Boolean,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    paperColor: Color,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    pageDimensions: Map<Int, FileDimensionDto>,
    invertMode: InvertMode,
    whiteThreshold: Float,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    ePaperMode: EPaperMode,
    imageScaleType: ReaderImageScaleType,
    cropBorders: Boolean,
    nightModeEnabled: Boolean,
    nightLightIntensity: Float,
    host: PlayCurlHostState,
    onPageTurned: (Int) -> Unit,
    modifier: Modifier = Modifier,
    epubFontSizeSp: Float = 18f,
    epubFontFamily: String = "Serif",
    epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    epubContentPadding: PaddingValues? = null,
    density: Density? = null,
    isEpub: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val generationCounter = remember { AtomicLong(1L) }
    // Retained until onDeckReleased reports the generation (bitmap lease).
    val retainedBitmaps = remember { mutableMapOf<Long, List<Bitmap>>() }
    var capabilitiesAvailable by remember { mutableStateOf(false) }
    val paperArgb = paperColor.toArgb()
    val latestOnPageTurned = rememberUpdatedState(onPageTurned)
    val latestPageModel = rememberUpdatedState(pageModel)

    host.rightToLeft = rightToLeft

    var surfaceRef: PageSurfaceView? by remember { mutableStateOf(null) }

    AndroidView(
        factory = { context ->
            PageSurfaceView(context, paperArgb).apply {
                pageSurfaceListener = object : PageSurfaceListener {
                    override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
                        capabilitiesAvailable = true
                    }

                    override fun onFirstFrameRendered() {
                        host.ready = true
                    }

                    override fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {
                        Log.w(PlayCurlLogTag, "deck $generationId rejected: $reason")
                        retainedBitmaps.remove(generationId)?.forEach { recycleQuietly(it) }
                    }

                    override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
                        retainedBitmaps.remove(generationId)?.forEach { recycleQuietly(it) }
                    }

                    override fun onSettlementCompleted(
                        gestureId: Long,
                        generationId: Long,
                        currentLogicalPageId: String,
                        currentOrdinal: Int,
                        pageChange: PageChange
                    ) {
                        if (pageChange == PageChange.NONE) return
                        latestOnPageTurned.value(currentOrdinal.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                    }

                    override fun onRenderFailure(failure: RenderFailure) {
                        Log.e(PlayCurlLogTag, "render failure: ${failure.message} (${failure.reason})")
                    }
                }
                surfaceRef = this
                host.view = this
                attach()
                setVisible(true)
            }
        },
        update = { view ->
            host.view = view
            val w = viewportWidthPx.roundToIntCompat().coerceAtLeast(1)
            val h = viewportHeightPx.roundToIntCompat().coerceAtLeast(1)
            try {
                view.setViewport(w, h)
                view.setBackgroundColor(
                    (paperArgb shr 16) and 0xFF,
                    (paperArgb shr 8) and 0xFF,
                    paperArgb and 0xFF
                )
            } catch (_: Exception) {
            }
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                surfaceRef?.setVisible(false)
                surfaceRef?.detach()
                surfaceRef?.dispose()
            } catch (_: Exception) {
            }
            host.view = null
            host.ready = false
            retainedBitmaps.values.flatten().forEach { recycleQuietly(it) }
            retainedBitmaps.clear()
        }
    }

    LaunchedEffect(
        page, pageCount, portrait, rightToLeft,
        viewportWidthPx, viewportHeightPx, paperArgb,
        invertMode, whiteThreshold, ePaperMode, imageScaleType, cropBorders,
        nightModeEnabled, nightLightIntensity, pageDimensions,
        epubFontSizeSp, epubFontFamily, epubTextAlign, epubContentPadding,
        capabilitiesAvailable, isEpub
    ) {
        val surface = host.view ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        if (!capabilitiesAvailable) {
            host.ready = false
            return@LaunchedEffect
        }
        val viewW = viewportWidthPx.roundToIntCompat().coerceAtLeast(1)
        val viewH = viewportHeightPx.roundToIntCompat().coerceAtLeast(1)
        val anchor = page.coerceIn(0, pageCount - 1)
        val modelOf: (Int) -> Any? = { latestPageModel.value(it) }
        val generationId = generationCounter.getAndIncrement()
        scope.launch(Dispatchers.IO) {
            val deck = try {
                buildPlayCurlDeck(
                    context = appContext,
                    generationId = generationId,
                    anchor = anchor,
                    pageCount = pageCount,
                    portrait = portrait,
                    rightToLeft = rightToLeft,
                    viewW = viewW,
                    viewH = viewH,
                    paperArgb = paperArgb,
                    pageModel = modelOf,
                    imageLoader = imageLoader,
                    pageDimensions = pageDimensions,
                    invertMode = invertMode,
                    whiteThreshold = whiteThreshold,
                    invertDecisionCache = invertDecisionCache,
                    ePaperMode = ePaperMode,
                    imageScaleType = imageScaleType,
                    cropBorders = cropBorders,
                    nightModeEnabled = nightModeEnabled,
                    nightLightIntensity = nightLightIntensity,
                    epubFontSizeSp = epubFontSizeSp,
                    epubFontFamily = epubFontFamily,
                    epubTextAlign = epubTextAlign,
                    epubContentPadding = epubContentPadding,
                    density = density,
                    isEpub = isEpub
                )
            } catch (e: Exception) {
                Log.w(PlayCurlLogTag, "deck build failed: ${e.message}")
                null
            }
            if (deck == null) return@launch
            val bitmaps = deck.pages.map { it.content }
            withContext(Dispatchers.Main) {
                retainedBitmaps[generationId] = bitmaps
                try {
                    surface.submitDeck(deck)
                } catch (e: Exception) {
                    Log.w(PlayCurlLogTag, "submitDeck failed: ${e.message}")
                    retainedBitmaps.remove(generationId)
                }
            }
        }
    }
}

private data class PlayCurlStyle(
    val paperArgb: Int,
    val invertMode: InvertMode,
    val whiteThreshold: Float,
    val ePaperMode: EPaperMode,
    val imageScaleType: ReaderImageScaleType,
    val cropBorders: Boolean,
    val nightModeEnabled: Boolean,
    val nightLightIntensity: Float,
    val epubFontSizeSp: Float = 18f,
    val epubFontFamily: String = "Serif",
    val epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    val epubContentPadding: PaddingValues? = null,
    val density: Density? = null,
    val isEpub: Boolean = false
)

private suspend fun buildPlayCurlDeck(
    context: Context,
    generationId: Long,
    anchor: Int,
    pageCount: Int,
    portrait: Boolean,
    rightToLeft: Boolean,
    viewW: Int,
    viewH: Int,
    paperArgb: Int,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    pageDimensions: Map<Int, FileDimensionDto>,
    invertMode: InvertMode,
    whiteThreshold: Float,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    ePaperMode: EPaperMode,
    imageScaleType: ReaderImageScaleType,
    cropBorders: Boolean,
    nightModeEnabled: Boolean,
    nightLightIntensity: Float,
    epubFontSizeSp: Float = 18f,
    epubFontFamily: String = "Serif",
    epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    epubContentPadding: PaddingValues? = null,
    density: Density? = null,
    isEpub: Boolean = false
): PageDeck<Bitmap> {
    val style = PlayCurlStyle(
        paperArgb = paperArgb,
        invertMode = invertMode,
        whiteThreshold = whiteThreshold,
        ePaperMode = ePaperMode,
        imageScaleType = imageScaleType,
        cropBorders = cropBorders,
        nightModeEnabled = nightModeEnabled,
        nightLightIntensity = nightLightIntensity,
        epubFontSizeSp = epubFontSizeSp,
        epubFontFamily = epubFontFamily,
        epubTextAlign = epubTextAlign,
        epubContentPadding = epubContentPadding,
        density = density,
        isEpub = isEpub
    )
    // Warm the shared Smart-invert cache for every page the deck needs, exactly
    // like the Slide path's prefetch does, so invert decisions match.
    if (invertMode == InvertMode.Smart) {
        val needed = if (portrait) {
            listOf(anchor - 1, anchor, anchor + 1)
        } else {
            listOf(anchor - 2, anchor - 1, anchor, anchor + 1, anchor + 2, anchor + 3)
        }.map { it.coerceIn(0, pageCount - 1) }.distinct()
        val stringModels = needed.mapNotNull {
            try {
                pageModel(it) as? String
            } catch (_: Exception) {
                null
            }
        }.distinct()
        if (stringModels.isNotEmpty()) {
            try {
                preAnalyzeReaderPages(context, imageLoader, stringModels, whiteThreshold, invertDecisionCache)
            } catch (_: Exception) {
            }
        }
    }
    if (portrait) {
        // RTL mirrors deck roles so the library's fixed drag-left-is-NEXT
        // convention keeps matching the reader's turn directions.
        val prevIndex = if (rightToLeft) anchor + 1 else anchor - 1
        val nextIndex = if (rightToLeft) anchor - 1 else anchor + 1
        return PortraitPageDeck(
            playCurlSingle(generationId, context, prevIndex, pageCount, viewW, viewH, style, pageModel, imageLoader, invertDecisionCache),
            playCurlSingle(generationId, context, anchor, pageCount, viewW, viewH, style, pageModel, imageLoader, invertDecisionCache),
            playCurlSingle(generationId, context, nextIndex, pageCount, viewW, viewH, style, pageModel, imageLoader, invertDecisionCache)
        )
    }
    val leftW = (viewW / 2).coerceAtLeast(1)
    val rightW = (viewW - leftW).coerceAtLeast(1)
    val curLayout = readerPageLayout(anchor, pageCount, portrait = false, pageDimensions = pageDimensions, isEpub = isEpub)
    val prevAnchor = (anchor - curLayout.previousStep).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val nextAnchor = (anchor + curLayout.nextStep).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    // Render the three neighboring spreads full-width, then split each into
    // halves. Splitting one spread image guarantees the halves join seamlessly.
    val prevSpread = playCurlSpread(generationId, context, prevAnchor, pageCount, rightToLeft, viewW, viewH, style, pageModel, imageLoader, pageDimensions, invertDecisionCache)
    val currentSpread = playCurlSpread(generationId, context, anchor, pageCount, rightToLeft, viewW, viewH, style, pageModel, imageLoader, pageDimensions, invertDecisionCache)
    val nextSpread = playCurlSpread(generationId, context, nextAnchor, pageCount, rightToLeft, viewW, viewH, style, pageModel, imageLoader, pageDimensions, invertDecisionCache)
    val (prevL, prevR) = prevSpread.splitHalves(generationId, pageCount, leftW, rightW)
    val (curL, curR) = currentSpread.splitHalves(generationId, pageCount, leftW, rightW)
    val (nextL, nextR) = nextSpread.splitHalves(generationId, pageCount, leftW, rightW)
    return if (rightToLeft) {
        // Swapped roles: the library's NEXT side holds the reader-previous
        // spread and vice versa (see PlayCurlHostState).
        LandscapePageDeck(nextR, nextL, curR, curL, prevR, prevL)
    } else {
        LandscapePageDeck(prevL, prevR, curL, curR, nextL, nextR)
    }
}

/** A rendered full-viewport sheet plus the logical index of each half. */
private data class PlayCurlSheet(
    val bitmap: Bitmap,
    val leftIndex: Int,
    val rightIndex: Int,
    val single: Boolean
) {
    fun splitHalves(
        generationId: Long,
        pageCount: Int,
        leftW: Int,
        rightW: Int
    ): Pair<PageImage<Bitmap>, PageImage<Bitmap>> {
        val leftBitmap = Bitmap.createBitmap(bitmap, 0, 0, leftW.coerceAtMost(bitmap.width), bitmap.height)
        val rightBitmap = Bitmap.createBitmap(
            bitmap, leftW.coerceAtMost(bitmap.width - 1), 0,
            rightW.coerceAtMost(bitmap.width - leftW.coerceAtMost(bitmap.width)), bitmap.height
        )
        leftBitmap.setHasAlpha(false)
        rightBitmap.setHasAlpha(false)
        val boundedLeft = leftIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val boundedRight = rightIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return PageImage(
            generationId, "page-$boundedLeft", boundedLeft,
            leftBitmap.width, leftBitmap.height, leftBitmap
        ) to PageImage(
            generationId, "page-$boundedRight", boundedRight,
            rightBitmap.width, rightBitmap.height, rightBitmap
        )
    }
}

private suspend fun playCurlSingle(
    generationId: Long,
    context: Context,
    index: Int,
    pageCount: Int,
    viewW: Int,
    viewH: Int,
    style: PlayCurlStyle,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
): PageImage<Bitmap> {
    val bounded = index.coerceIn(0, pageCount - 1)
    val bitmap = renderPlayCurlSheet(
        context = context,
        width = viewW,
        height = viewH,
        leftIndex = bounded,
        rightIndex = null,
        style = style,
        pageModel = pageModel,
        imageLoader = imageLoader,
        invertDecisionCache = invertDecisionCache
    )
    return PageImage(generationId, "page-$bounded", bounded, bitmap.width, bitmap.height, bitmap)
}

private suspend fun playCurlSpread(
    generationId: Long,
    context: Context,
    anchor: Int,
    pageCount: Int,
    rightToLeft: Boolean,
    viewW: Int,
    viewH: Int,
    style: PlayCurlStyle,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    pageDimensions: Map<Int, FileDimensionDto>,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
): PlayCurlSheet {
    val boundedAnchor = anchor.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val layout = readerPageLayout(
        page = boundedAnchor,
        pageCount = pageCount,
        portrait = false,
        pageDimensions = pageDimensions,
        isEpub = style.isEpub
    )
    val (leftIndex, rightIndex) = if (layout.singlePage) {
        boundedAnchor to boundedAnchor
    } else {
        val spread = spreadPagesFor(boundedAnchor, rightToLeft)
        spread.leftPage to spread.rightPage
    }
    val bitmap = renderPlayCurlSheet(
        context = context,
        width = viewW,
        height = viewH,
        leftIndex = leftIndex,
        rightIndex = if (layout.singlePage) null else rightIndex,
        style = style,
        pageModel = pageModel,
        imageLoader = imageLoader,
        invertDecisionCache = invertDecisionCache
    )
    return PlayCurlSheet(bitmap, leftIndex, rightIndex, layout.singlePage)
}

/**
 * Renders one full-viewport sheet exactly like the Slide path's ReaderPageView:
 * paper background, one centered page ([rightIndex] == null) or a spread pair
 * with the left page glued to the spine ([PlayCurlHalf.Left]) and the right
 * page glued from the spine ([PlayCurlHalf.Right]).
 */
private suspend fun renderPlayCurlSheet(
    context: Context,
    width: Int,
    height: Int,
    leftIndex: Int,
    rightIndex: Int?,
    style: PlayCurlStyle,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
): Bitmap {
    val out = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(style.paperArgb)
    if (rightIndex == null) {
        drawPlayCurlPage(
            canvas = canvas,
            box = Rect(0, 0, out.width, out.height),
            index = leftIndex,
            glue = PlayCurlHalf.Left,
            centeredSingle = true,
            context = context,
            style = style,
            pageModel = pageModel,
            imageLoader = imageLoader,
            invertDecisionCache = invertDecisionCache
        )
    } else {
        val halfW = out.width / 2
        drawPlayCurlPage(
            canvas = canvas,
            box = Rect(0, 0, halfW, out.height),
            index = leftIndex,
            glue = PlayCurlHalf.Left,
            centeredSingle = false,
            context = context,
            style = style,
            pageModel = pageModel,
            imageLoader = imageLoader,
            invertDecisionCache = invertDecisionCache
        )
        drawPlayCurlPage(
            canvas = canvas,
            box = Rect(halfW, 0, out.width, out.height),
            index = rightIndex,
            glue = PlayCurlHalf.Right,
            centeredSingle = false,
            context = context,
            style = style,
            pageModel = pageModel,
            imageLoader = imageLoader,
            invertDecisionCache = invertDecisionCache
        )
    }
    if (style.nightModeEnabled && style.nightLightIntensity > 0f) {
        val amberAlpha = (style.nightLightIntensity * 0.38f).coerceIn(0f, 0.45f)
        val paint = Paint().apply {
            color = PlayCurlNightColor
            alpha = (amberAlpha * 255f).toInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
    }
    out.setHasAlpha(false)
    return out
}

/** Loads, crops, inverts and scales one page, then draws it spine-glued into [box]. */
private suspend fun drawPlayCurlPage(
    canvas: Canvas,
    box: Rect,
    index: Int,
    glue: PlayCurlHalf,
    centeredSingle: Boolean,
    context: Context,
    style: PlayCurlStyle,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
) {
    if (box.width() <= 0 || box.height() <= 0) return
    val model = try {
        pageModel(index)
    } catch (_: Exception) {
        null
    } ?: return

    if (model is EpubSubpage) {
        val src = renderEpubSubpageToBitmap(context, model, box.width(), box.height(), style, imageLoader, glue)
            ?: return
        try {
            if (src.width <= 0 || src.height <= 0 || src.isRecycled) return
            canvas.save()
            canvas.clipRect(box)
            canvas.drawBitmap(src, null, box, null)
            canvas.restore()
        } finally {
            recycleQuietly(src)
        }
        return
    }

    var src = loadPlayCurlSource(context, model, imageLoader, box.width(), box.height(), style.cropBorders)
        ?: return
    try {
        if (src.width <= 0 || src.height <= 0 || src.isRecycled) return
        val invert = playCurlShouldInvert(model, src, context, style, invertDecisionCache)
        val paint = playCurlImagePaint(style.ePaperMode, invert)
        val dst = playCurlDestRect(
            srcW = src.width,
            srcH = src.height,
            box = box,
            glue = glue,
            centeredSingle = centeredSingle,
            scaleType = style.imageScaleType
        )
        canvas.save()
        canvas.clipRect(box)
        canvas.drawBitmap(src, null, dst, paint)
        canvas.restore()
    } finally {
        recycleQuietly(src)
    }
}

private suspend fun renderEpubSubpageToBitmap(
    context: Context,
    subpage: EpubSubpage,
    width: Int,
    height: Int,
    style: PlayCurlStyle,
    imageLoader: ImageLoader,
    glue: PlayCurlHalf? = null
): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(style.paperArgb)

        val r = (style.paperArgb shr 16) and 0xFF
        val g = (style.paperArgb shr 8) and 0xFF
        val b = style.paperArgb and 0xFF
        val isDark = (r * 0.299 + g * 0.587 + b * 0.114) < 128
        val isSepia = !isDark && (style.paperArgb != 0xFFFFFFFF.toInt())
        val textColor = when {
            isDark -> 0xFFEDEDED.toInt()
            isSepia -> 0xFF423224.toInt()
            else -> 0xFF141414.toInt()
        }
        val dividerColor = when {
            isDark -> 0xFF333333.toInt()
            isSepia -> 0xFFD6C8B4.toInt()
            else -> 0xFFDCD7CC.toInt()
        }
        val quoteBarColor = when {
            isDark -> 0xFF666666.toInt()
            isSepia -> 0xFF8D7B68.toInt()
            else -> 0xFF9E988D.toInt()
        }

        val activeTypeface = when (style.epubFontFamily.lowercase()) {
            "sans", "sansserif", "sans-serif" -> Typeface.SANS_SERIF
            "mono", "monospace" -> Typeface.MONOSPACE
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.SERIF
        }

        val density = style.density ?: Density(context)
        val fontSizePx = with(density) { style.epubFontSizeSp.sp.toPx() }

        val basePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = fontSizePx
            color = textColor
            typeface = activeTypeface
        }
        val quotePaint = TextPaint(basePaint).apply {
            typeface = Typeface.create(activeTypeface, Typeface.ITALIC)
        }
        val headingPaints = (1..6).associateWith { level ->
            val mult = when (level) {
                1 -> 1.45f
                2 -> 1.3f
                3 -> 1.2f
                else -> 1.1f
            }
            TextPaint(basePaint).apply {
                textSize = fontSizePx * mult
                typeface = Typeface.create(activeTypeface, Typeface.BOLD)
            }
        }

        val slAlign = when (style.epubTextAlign) {
            EpubTextAlign.Left -> Layout.Alignment.ALIGN_NORMAL
            EpubTextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            EpubTextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
            EpubTextAlign.Justify -> Layout.Alignment.ALIGN_NORMAL
        }

        val defaultPad = style.epubContentPadding ?: PaddingValues(20.dp, 48.dp, 20.dp, 50.dp)
        val startPad = defaultPad.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val endPad = defaultPad.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
        val outerMargin = with(density) {
            maxOf(startPad, endPad).coerceAtLeast(20.dp).roundToPx()
        }
        val innerMargin = with(density) { 20.dp.roundToPx() }
        val (startPadPx, endPadPx) = when (glue) {
            PlayCurlHalf.Left -> outerMargin to innerMargin
            PlayCurlHalf.Right -> innerMargin to outerMargin
            null -> with(density) {
                startPad.coerceAtLeast(20.dp).roundToPx() to endPad.coerceAtLeast(20.dp).roundToPx()
            }
        }
        val topPadPx = with(density) { defaultPad.calculateTopPadding().roundToPx() }
        val contentWidthPx = (width - startPadPx - endPadPx).coerceAtLeast(10)

        val blockSpacingPx = with(density) { 10.dp.roundToPx() }
        val dividerPaint = Paint().apply {
            color = dividerColor
            strokeWidth = with(density) { 1.dp.toPx() }
        }
        val quoteBarPaint = Paint().apply {
            color = quoteBarColor
            this.style = Paint.Style.FILL
        }

        var currentY = topPadPx.toFloat()

        for (block in subpage.blocks) {
            when (block) {
                is EpubBlock.DividerBlock -> {
                    val lineY = currentY + with(density) { 4.dp.toPx() }
                    canvas.drawLine(startPadPx.toFloat(), lineY, (startPadPx + contentWidthPx).toFloat(), lineY, dividerPaint)
                    currentY += with(density) { 9.dp.toPx() } + blockSpacingPx
                }
                is EpubBlock.ImageBlock -> {
                    val req = ImageRequest.Builder(context)
                        .data(block.url)
                        .size(contentWidthPx, (height * 0.55f).toInt())
                        .allowHardware(false)
                        .build()
                    val res = imageLoader.execute(req)
                    if (res is SuccessResult) {
                        val img = res.drawable.toBitmapCompat()
                        if (img != null && !img.isRecycled) {
                            val scale = minOf(contentWidthPx.toFloat() / img.width.toFloat(), (height * 0.55f) / img.height.toFloat(), 1f)
                            val dstW = img.width * scale
                            val dstH = img.height * scale
                            val dstLeft = startPadPx + (contentWidthPx - dstW) / 2f
                            val dstRect = RectF(dstLeft, currentY, dstLeft + dstW, currentY + dstH)
                            canvas.drawBitmap(img, null, dstRect, null)
                            currentY += dstH + blockSpacingPx
                            recycleQuietly(img)
                        }
                    }
                }
                is EpubBlock.TextBlock -> {
                    val paint = when {
                        block.isHeading -> headingPaints[block.headingLevel] ?: basePaint
                        block.isQuote -> quotePaint
                        else -> basePaint
                    }
                    val text = block.text.text
                    if (text.isNotEmpty()) {
                        val sl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidthPx)
                                .setAlignment(slAlign)
                                .setLineSpacing(0f, 1.45f)
                                .setIncludePad(true)
                                .build()
                        } else {
                            @Suppress("DEPRECATION")
                            StaticLayout(text, paint, contentWidthPx, slAlign, 1.45f, 0f, true)
                        }

                        if (block.isHeading) {
                            currentY += with(density) { 4.dp.toPx() }
                        }

                        if (block.isQuote) {
                            val barWidth = with(density) { 3.dp.toPx() }
                            val quoteBarRect = RectF(
                                startPadPx.toFloat(),
                                currentY,
                                startPadPx + barWidth,
                                currentY + sl.height.toFloat()
                            )
                            canvas.drawRect(quoteBarRect, quoteBarPaint)
                            canvas.save()
                            canvas.translate(startPadPx + barWidth + with(density) { 10.dp.toPx() }, currentY)
                            sl.draw(canvas)
                            canvas.restore()
                        } else {
                            canvas.save()
                            canvas.translate(startPadPx.toFloat(), currentY)
                            sl.draw(canvas)
                            canvas.restore()
                        }

                        currentY += sl.height.toFloat()
                        if (block.isHeading) {
                            currentY += with(density) { 4.dp.toPx() }
                        }
                        currentY += blockSpacingPx
                    }
                }
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

/** Same source pipeline as the Slide path: offline decode or Coil, then border crop. */
private suspend fun loadPlayCurlSource(
    context: Context,
    model: Any,
    imageLoader: ImageLoader,
    targetW: Int,
    targetH: Int,
    cropBorders: Boolean
): Bitmap? {
    return try {
        val decoded: Bitmap? = when (model) {
            is OfflinePage -> decodeOfflinePage(model, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
            // Copy: the caller's bitmap must never be recycled by this pipeline.
            is Bitmap -> try {
                model.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Exception) {
                null
            }
            is android.graphics.drawable.Drawable -> model.toBitmapCompat()
            is String -> {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .size(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result !is SuccessResult) return null
                result.drawable.toBitmapCompat()
            }
            else -> {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .size(targetW.coerceAtLeast(1), targetH.coerceAtLeast(1))
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result !is SuccessResult) return null
                result.drawable.toBitmapCompat()
            }
        }
        if (decoded == null || decoded.isRecycled) return null
        if (cropBorders) {
            val cropped = cropBorderFromBitmap(decoded)
            if (cropped !== decoded) recycleQuietly(decoded)
            cropped.takeUnless { it.isRecycled }
        } else {
            decoded
        }
    } catch (_: Exception) {
        null
    }
}

private fun android.graphics.drawable.Drawable.toBitmapCompat(): Bitmap? {
    return try {
        val existing = (this as? BitmapDrawable)?.bitmap
        if (existing != null && !existing.isRecycled) {
            existing.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            val w = intrinsicWidth.coerceAtLeast(1)
            val h = intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            setBounds(0, 0, w, h)
            draw(canvas)
            bmp
        }
    } catch (_: Exception) {
        null
    }
}

private fun playCurlShouldInvert(
    model: Any,
    bitmap: Bitmap,
    context: Context,
    style: PlayCurlStyle,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>
): Boolean = when (style.invertMode) {
    InvertMode.Off -> false
    InvertMode.Always -> true
    InvertMode.Smart -> {
        val key = ReaderInvertCacheKey(model, style.whiteThreshold)
        invertDecisionCache[key] ?: try {
            analyzeShouldInvert(BitmapDrawable(context.resources, bitmap), style.whiteThreshold)
                .also { invertDecisionCache[key] = it }
        } catch (_: Exception) {
            false
        }
    }
}

private fun playCurlImagePaint(ePaperMode: EPaperMode, invert: Boolean): Paint? {
    val matrix = when {
        ePaperMode == EPaperMode.Off && !invert -> return null
        ePaperMode == EPaperMode.Off -> PlayCurlNegativeMatrix
        ePaperMode == EPaperMode.BlackAndWhite && !invert -> PlayCurlEPaperBwMatrix
        ePaperMode == EPaperMode.BlackAndWhite -> PlayCurlEPaperBwInvertedMatrix
        ePaperMode == EPaperMode.Color && !invert -> PlayCurlEPaperColorMatrix
        else -> PlayCurlEPaperColorInvertedMatrix
    }
    return Paint().apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
    }
}

/**
 * Destination rect mirroring ContentScale semantics used by the Slide path
 * (readerContentScale): Fit centers, FillBounds stretches, FillWidth/FillHeight
 * fill one axis and center-crop the overflow, None keeps original size,
 * SmartFit picks Fit for wide sources and FillWidth otherwise. Spread halves
 * glue to the spine (left page right-aligned, right page left-aligned) exactly
 * like ReaderPageView's CenterEnd/CenterStart.
 */
private fun playCurlDestRect(
    srcW: Int,
    srcH: Int,
    box: Rect,
    glue: PlayCurlHalf,
    centeredSingle: Boolean,
    scaleType: ReaderImageScaleType
): RectF {
    val bw = box.width().toFloat()
    val bh = box.height().toFloat()
    if (srcW <= 0 || srcH <= 0 || bw <= 0f || bh <= 0f) {
        return RectF(box)
    }
    val effectiveScale = when (scaleType) {
        ReaderImageScaleType.FitScreen -> ScaleKind.Fit
        ReaderImageScaleType.Stretch -> ScaleKind.FillBounds
        ReaderImageScaleType.FitWidth -> ScaleKind.FillWidth
        ReaderImageScaleType.FitHeight -> ScaleKind.FillHeight
        ReaderImageScaleType.OriginalSize -> ScaleKind.None
        ReaderImageScaleType.SmartFit ->
            if (srcW / srcH.toFloat() > bw / bh) ScaleKind.Fit else ScaleKind.FillWidth
    }
    val (dw, dh) = when (effectiveScale) {
        ScaleKind.Fit -> {
            val s = minOf(bw / srcW, bh / srcH)
            srcW * s to srcH * s
        }
        ScaleKind.FillBounds -> bw to bh
        ScaleKind.FillWidth -> {
            val s = bw / srcW
            bw to srcH * s
        }
        ScaleKind.FillHeight -> {
            val s = bh / srcH
            srcW * s to bh
        }
        ScaleKind.None -> srcW.toFloat() to srcH.toFloat()
    }
    val left = when {
        centeredSingle -> box.left + (bw - dw) / 2f
        glue == PlayCurlHalf.Left -> box.right - dw
        else -> box.left.toFloat()
    }
    // Vertical overflow (FillWidth/None on tall boxes) stays centered and clips,
    // matching the Slide path's Center alignment on that axis.
    val top = box.top + (bh - dh) / 2f
    return RectF(left, top, left + dw, top + dh)
}

private enum class ScaleKind {
    Fit,
    FillBounds,
    FillWidth,
    FillHeight,
    None
}

private fun recycleQuietly(bitmap: Bitmap) {
    try {
        if (!bitmap.isRecycled) bitmap.recycle()
    } catch (_: Exception) {
    }
}

private fun Float.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
