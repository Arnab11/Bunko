package com.bunko.reader.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.bunko.reader.FileDimensionDto
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode

/** Internal to reader, not for external use. */
internal fun readerOverviewCursors(
    pageCount: Int,
    portrait: Boolean,
    pageDimensions: Map<Int, FileDimensionDto>,
    isEpub: Boolean = false
): List<Int> {
    if (pageCount <= 0) return emptyList()
    val cursors = mutableListOf<Int>()
    var cursor = 0
    var guard = 0
    while (cursor < pageCount && guard < pageCount + 10) {
        cursors.add(cursor)
        val layout = readerPageLayout(cursor, pageCount, portrait, pageDimensions, isEpub)
        cursor += layout.nextStep.coerceAtLeast(1)
        guard++
    }
    return cursors
}

/** Internal to reader, not for external use. */
internal fun readerOverviewIndexForCursor(cursors: List<Int>, cursor: Int): Int {
    if (cursors.isEmpty()) return 0
    cursors.indexOf(cursor).takeIf { it >= 0 }?.let { return it }
    var best = 0
    cursors.forEachIndexed { index, value ->
        if (value <= cursor) best = index
    }
    return best
}

/**
 * Measures the child at full reader dimensions [fullWidth] x [fullHeight] so that text wrapping,
 * line count, headings, and formatting are 100% identical to normal reading mode, then scales
 * the layout uniformly by [scale] without overflow or rendering artifacts.
 */
private fun Modifier.scaledLayout(
    scale: Float,
    fullWidth: Dp,
    fullHeight: Dp
): Modifier = this.layout { measurable, _ ->
    val placeable = measurable.measure(
        Constraints.fixed(
            fullWidth.roundToPx(),
            fullHeight.roundToPx()
        )
    )
    layout(
        (fullWidth * scale).roundToPx(),
        (fullHeight * scale).roundToPx()
    ) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * Google Play Books style overview: spreads laid out horizontally in a continuous
 * 1:1 scrollable carousel, matching Play Books proportions: wide spread cards (80-82% width)
 * with peeking neighbors, and tapping the center spread restores the reading view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderOverviewGallery(
    cursors: List<Int>,
    currentCursor: Int,
    reverseLayout: Boolean,
    // Passed as State so per-frame zoom values are read in draw phase only —
    // the cards below never recompose during the animation.
    progressState: State<Float>,
    onSelect: (Int) -> Unit,
    onCenterTap: () -> Unit,
    // Pinch-to-dismiss callbacks: 2-finger gestures are intercepted at
    // PointerEventPass.Initial before HorizontalPager's horizontal scroll,
    // allowing the reader to dismiss the overview via pinch-out.
    onTransform: ((zoomChange: Float, panChange: Offset, focalPoint: Offset) -> Unit)? = null,
    onTransformEnd: ((velocityScale: Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Mihon-style tap-zone preview, drawn only over the current card so it
    // tracks the overview page instead of covering the whole screen.
    tapZoneOverlayVisible: Boolean = false,
    tapZoneNavigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    tapZoneTappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    tapZoneRightToLeft: Boolean = false,
    onTapZoneOverlayDismiss: () -> Unit = {},
    pageContent: @Composable (cursor: Int, modifier: Modifier) -> Unit
) {
    if (cursors.isEmpty()) return
    val scope = rememberCoroutineScope()
    val initialIndex = remember(cursors) {
        readerOverviewIndexForCursor(cursors, currentCursor)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { cursors.size }
    )

    // When the reader page changes externally (slider, chapter switch),
    // jump the gallery straight there with no glide-through.
    LaunchedEffect(currentCursor, cursors) {
        val targetIndex = readerOverviewIndexForCursor(cursors, currentCursor)
        if (targetIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            if (targetIndex in 0 until cursors.size) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }
    LaunchedEffect(cursors.size) {
        if (pagerState.currentPage >= cursors.size) {
            pagerState.scrollToPage((cursors.size - 1).coerceAtLeast(0))
        }
    }

    // A settled gallery page commits back to the reader.
    LaunchedEffect(pagerState, cursors) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (settledPage, scrolling) ->
                if (!scrolling && settledPage in cursors.indices) {
                    val cursor = cursors[settledPage]
                    if (cursor != currentCursor) onSelect(cursor)
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // ── Pinch interceptor ────────────────────────────────────────────────────
            // Runs at PointerEventPass.Initial so we see 2-finger events BEFORE
            // HorizontalPager's horizontal-scroll handler (which runs at Main).
            // When 2+ fingers are down we compute the zoom/pan transform, invoke
            // the callbacks, and consume the changes so the pager never sees them.
            .pointerInput(onTransform, onTransformEnd) {
                if (onTransform == null) return@pointerInput
                awaitEachGesture {
                    var lastCentroid: Offset? = null
                    var lastSpan = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            val cursor = cursors.getOrNull(pagerState.currentPage)
                            if (cursor != null && cursor != currentCursor) onSelect(cursor)
                            onTransformEnd?.invoke(if (lastSpan > 0f) lastSpan else 1f)
                            break
                        }
                        if (pressed.size < 2) {
                            // Single finger — let pager handle it; reset pinch state.
                            lastCentroid = null
                            lastSpan = 0f
                            continue
                        }
                        // 2+ fingers: compute centroid and span.
                        var cx = 0f; var cy = 0f
                        pressed.forEach { cx += it.position.x; cy += it.position.y }
                        val centroid = Offset(cx / pressed.size, cy / pressed.size)
                        val span = pressed
                            .map { (it.position - centroid).getDistance() }
                            .average().toFloat()
                        val prev = lastCentroid
                        if (prev != null && lastSpan > 0f && span > 0f) {
                            onTransform.invoke(
                                span / lastSpan,
                                centroid - prev,
                                centroid
                            )
                            // Consume so HorizontalPager doesn't treat it as a scroll.
                            pressed.forEach { it.consume() }
                        }
                        lastCentroid = centroid
                        lastSpan = span
                    }
                }
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isPortrait = screenHeight > screenWidth

        // Background tap layer behind the pager to dismiss overview when tapping the margins
        val overviewBackground = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = progressState.value
                }
                .background(overviewBackground)
                .pointerInput(pagerState.currentPage, cursors, currentCursor) {
                    detectTapGestures(onTap = {
                        val cursor = cursors.getOrNull(pagerState.currentPage)
                        if (cursor != null && cursor != currentCursor) onSelect(cursor)
                        onCenterTap()
                    })
                }
        )

        val context = LocalContext.current
        val density = LocalDensity.current
        val resStatusBarHeight = remember(context) {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
        val stableStatusBarHeight = maxOf(
            WindowInsets.statusBarsIgnoringVisibility
                .union(WindowInsets.displayCutout)
                .asPaddingValues()
                .calculateTopPadding(),
            with(density) { resStatusBarHeight.toDp() }
        )
        val galleryTopInset = stableStatusBarHeight

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = galleryTopInset + 56.dp)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        ) {
            val pagerHeight = maxHeight
            val maxCardWidth = screenWidth * (if (isPortrait) 0.78f else 0.74f)
            val maxCardHeight = pagerHeight * 0.88f
            val scale = minOf(maxCardWidth / screenWidth, maxCardHeight / screenHeight)
            val cardWidth = screenWidth * scale
            val cardHeight = screenHeight * scale
            val sidePadding = (screenWidth - cardWidth) / 2f

            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val topInsetPx = with(density) { (galleryTopInset + 56.dp).toPx() }
            val bottomInsetPx = with(density) { (88.dp + navBarBottom).toPx() }
            val centerShiftYPx = (topInsetPx - bottomInsetPx) / 2f
            val sideSlidePx = with(density) { 140.dp.toPx() }

            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(10)
                ),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 20.dp,
                reverseLayout = reverseLayout,
                beyondViewportPageCount = 1,
                key = { index -> cursors.getOrNull(index) ?: index },
                verticalAlignment = Alignment.CenterVertically
            ) { index ->
                val cursor = cursors.getOrNull(index) ?: return@HorizontalPager
                val isCenter = index == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .zIndex(if (isCenter) 2f else 1f)
                        .graphicsLayer {
                            val progress = progressState.value
                            if (isCenter) {
                                val targetScale = 1f + (1f - progress) * (1f / scale - 1f)
                                scaleX = targetScale
                                scaleY = targetScale
                                translationY = -(1f - progress) * centerShiftYPx
                            } else {
                                alpha = progress
                                val slideDir = if (reverseLayout) {
                                    if (index < pagerState.currentPage) 1f else -1f
                                } else {
                                    if (index < pagerState.currentPage) -1f else 1f
                                }
                                translationX = slideDir * (1f - progress) * sideSlidePx
                            }
                            // Elevation + corners ride the same draw-phase value
                            // instead of the shadow/clip modifiers (composition).
                            shadowElevation = 8f * progress * density.density
                            shape = RoundedCornerShape(4.dp * progress)
                            clip = true
                        }
                        .width(cardWidth)
                        .height(cardHeight)
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = {
                                    if (index == pagerState.currentPage) {
                                        val c = cursors.getOrNull(index)
                                        if (c != null && c != currentCursor) onSelect(c)
                                        onCenterTap()
                                    } else {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.scaledLayout(
                            scale = scale,
                            fullWidth = screenWidth,
                            fullHeight = screenHeight
                        )
                    ) {
                        pageContent(cursor, Modifier.fillMaxSize())
                    }
                    // Clipped to the card (rounded corners included); a tap here
                    // dismisses the preview instead of driving the gallery.
                    if (isCenter) {
                        ReaderTapZoneOverlay(
                            visible = tapZoneOverlayVisible,
                            navigationMode = tapZoneNavigationMode,
                            tappingInvertMode = tapZoneTappingInvertMode,
                            rightToLeft = tapZoneRightToLeft,
                            onDismiss = onTapZoneOverlayDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}




