package com.bunko.reader.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import coil.ImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.bunko.reader.EPaperMode
import com.bunko.reader.EpubTextAlign
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.InvertMode
import com.bunko.reader.ReaderImageScaleType
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode
import com.bunko.reader.reader.ReaderTurnDirection

internal sealed interface ReaderVerticalPosition {
    data class Page(val index: Int) : ReaderVerticalPosition
    data object PreviousBoundary : ReaderVerticalPosition
    data object NextBoundary : ReaderVerticalPosition
}

internal data class ReaderVerticalVisibleItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

internal fun readerVerticalPosition(
    visibleItems: List<ReaderVerticalVisibleItem>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    pageCount: Int
): ReaderVerticalPosition? {
    val currentItem = visibleItems.maxWithOrNull(
        compareBy<ReaderVerticalVisibleItem> { item ->
            val visibleStart = maxOf(item.offset, viewportStartOffset)
            val visibleEnd = minOf(item.offset + item.size, viewportEndOffset)
            (visibleEnd - visibleStart).coerceAtLeast(0)
        }.thenBy { item ->
            -kotlin.math.abs((item.offset + item.size / 2) - (viewportStartOffset + viewportEndOffset) / 2)
        }
    ) ?: return null
    return when (currentItem.index) {
        0 -> ReaderVerticalPosition.PreviousBoundary
        pageCount + 1 -> ReaderVerticalPosition.NextBoundary
        in 1..pageCount -> ReaderVerticalPosition.Page(currentItem.index - 1)
        else -> null
    }
}

internal fun readerVerticalPageAspectRatio(
    dimension: FileDimensionDto?,
    fallbackAspectRatio: Float
): Float {
    val width = dimension?.width ?: 0
    val height = dimension?.height ?: 0
    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        fallbackAspectRatio.coerceAtLeast(0.01f)
    }
}

@Composable
internal fun ReaderVerticalScroll(
    pageCount: Int,
    pageDimensions: Map<Int, FileDimensionDto>,
    pageModel: (Int) -> Any?,
    imageLoader: ImageLoader,
    invertMode: InvertMode,
    ePaperMode: EPaperMode = EPaperMode.Off,
    whiteThreshold: Float,
    invertDecisionCache: MutableMap<ReaderInvertCacheKey, Boolean>,
    pageBackground: Color,
    viewportHeight: Dp,
    fallbackPageAspectRatio: Float,
    listState: LazyListState,
    previousBoundary: ReaderChapterBoundary?,
    nextBoundary: ReaderChapterBoundary?,
    seriesName: String,
    chapterSwitching: Boolean,
    boundaryHasError: Boolean,
    onCurrentPageChanged: (Int) -> Unit,
    onBoundaryReached: (ReaderTurnDirection) -> Unit,
    onContinueBoundary: (ReaderTurnDirection) -> Unit,
    onBackToSeries: () -> Unit,
    onMenuToggle: () -> Unit,
    epubSubpages: List<EpubSubpage> = emptyList(),
    epubFontSizeSp: Float = 18f,
    epubFontFamily: String = "Serif",
    epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    nightModeEnabled: Boolean = false,
    nightLightIntensity: Float = 0f,
    isWebtoon: Boolean = false,
    sidePaddingPercent: Int = 0,
    navigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    imageScaleType: ReaderImageScaleType = ReaderImageScaleType.FitWidth,
    cropBorders: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val viewportHeightPx = with(density) { viewportHeight.toPx() }
    val scrollDistance = viewportHeightPx * 0.75f
    val sidePaddingFraction = (sidePaddingPercent.coerceIn(0, 25) / 100f) / 2f
    val horizontalSidePadding = configuration.screenWidthDp.dp * sidePaddingFraction

    LaunchedEffect(listState, pageCount) {
        snapshotFlow { listState.layoutInfo }
            .map { layout ->
                readerVerticalPosition(
                    visibleItems = layout.visibleItemsInfo.map { item ->
                        ReaderVerticalVisibleItem(item.index, item.offset, item.size)
                    },
                    viewportStartOffset = layout.viewportStartOffset,
                    viewportEndOffset = layout.viewportEndOffset,
                    pageCount = pageCount
                )
            }
            .distinctUntilChanged()
            .collect { position ->
                when (position) {
                    is ReaderVerticalPosition.Page -> onCurrentPageChanged(position.index)
                    ReaderVerticalPosition.PreviousBoundary -> {
                        onBoundaryReached(ReaderTurnDirection.Previous)
                    }
                    ReaderVerticalPosition.NextBoundary -> {
                        onBoundaryReached(ReaderTurnDirection.Next)
                    }
                    null -> Unit
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalSidePadding)
    ) {
        item(key = "previous-boundary") {
            VerticalBoundaryPage(
                viewportHeight = viewportHeight,
                boundary = previousBoundary,
                seriesName = seriesName,
                switching = chapterSwitching,
                hasError = boundaryHasError,
                onContinue = { onContinueBoundary(ReaderTurnDirection.Previous) },
                onBackToSeries = onBackToSeries
            )
        }
        items(
            items = (0 until pageCount).toList(),
            key = { page -> "page-$page" }
        ) { page ->
            val isEpub = epubSubpages.isNotEmpty()
            val itemModifier = if (isEpub) {
                Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
            } else {
                val aspectRatio = readerVerticalPageAspectRatio(
                    dimension = pageDimensions[page],
                    fallbackAspectRatio = fallbackPageAspectRatio
                )
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
            }

            Box(
                itemModifier
                    .pointerInput(onMenuToggle, navigationMode, tappingInvertMode, scrollDistance) {
                        detectTapGestures { position ->
                            val action = resolveTapAction(position, size, navigationMode, tappingInvertMode)
                            when (action) {
                                ReaderTapAction.Menu -> onMenuToggle()
                                ReaderTapAction.MoveRight, ReaderTapAction.Next -> {
                                    coroutineScope.launch {
                                        listState.animateScrollBy(scrollDistance)
                                    }
                                }
                                ReaderTapAction.MoveLeft, ReaderTapAction.Prev -> {
                                    coroutineScope.launch {
                                        listState.animateScrollBy(-scrollDistance)
                                    }
                                }
                            }
                        }
                    }
            ) {
                if (isEpub && page in epubSubpages.indices) {
                    ReaderEpubPageView(
                        subpage = epubSubpages[page],
                        fontSizeSp = epubFontSizeSp,
                        epubFontFamily = epubFontFamily,
                        epubTextAlign = epubTextAlign,
                        pageBackground = pageBackground,
                        invertMode = invertMode,
                        ePaperMode = ePaperMode,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ReaderPageView(
                        cursor = page,
                        pageCount = pageCount,
                        portrait = true,
                        pageDimensions = pageDimensions,
                        rightToLeft = false,
                        pageModel = pageModel,
                        imageLoader = imageLoader,
                        invertMode = invertMode,
                        whiteThreshold = whiteThreshold,
                        invertDecisionCache = invertDecisionCache,
                        pageBackground = pageBackground,
                        ePaperMode = ePaperMode,
                        imageScaleType = imageScaleType,
                        cropBorders = cropBorders,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (nightModeEnabled && nightLightIntensity > 0f) {
                    val amberAlpha = (nightLightIntensity * 0.38f).coerceIn(0f, 0.45f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFF9E3D).copy(alpha = amberAlpha))
                    )
                }
            }
        }
        item(key = "next-boundary") {
            VerticalBoundaryPage(
                viewportHeight = viewportHeight,
                boundary = nextBoundary,
                seriesName = seriesName,
                switching = chapterSwitching,
                hasError = boundaryHasError,
                onContinue = { onContinueBoundary(ReaderTurnDirection.Next) },
                onBackToSeries = onBackToSeries
            )
        }
    }
}

@Composable
private fun VerticalBoundaryPage(
    viewportHeight: Dp,
    boundary: ReaderChapterBoundary?,
    seriesName: String,
    switching: Boolean,
    hasError: Boolean,
    onContinue: () -> Unit,
    onBackToSeries: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(viewportHeight)) {
        boundary?.let {
            ReaderChapterBoundaryScreen(
                seriesName = seriesName,
                boundary = it,
                switching = switching,
                hasError = hasError,
                onContinue = onContinue,
                onBackToSeries = onBackToSeries
            )
        }
    }
}
