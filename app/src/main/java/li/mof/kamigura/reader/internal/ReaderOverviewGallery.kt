package li.mof.kamigura.reader.internal

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import li.mof.kamigura.FileDimensionDto

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
 * Google Play Books style overview: spreads laid out horizontally in a continuous
 * 1:1 scrollable carousel, matching Play Books proportions: wide spread cards (80-82% width)
 * with peeking neighbors, and tapping the center spread restores the reading view.
 */
@Composable
internal fun ReaderOverviewGallery(
    cursors: List<Int>,
    currentCursor: Int,
    reverseLayout: Boolean,
    onSelect: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxHeight > maxWidth
        // Google Play Books proportions matching screenshots:
        // Wide 2-page spread card in landscape (~80% width) with adjacent spreads peeking (~10%)
        val cardFraction = if (isPortrait) 0.82f else 0.80f
        val heightFraction = if (isPortrait) 0.80f else 0.96f
        val sidePadding = maxWidth * ((1f - cardFraction) / 2f)

        // Background tap layer behind the pager to dismiss overview when tapping the margins
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onCenterTap() })
                }
        )

        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = PagerSnapDistance.atMost(10)
            ),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 56.dp)
                .navigationBarsPadding()
                .padding(bottom = 76.dp),
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = 20.dp,
            reverseLayout = reverseLayout,
            beyondViewportPageCount = 2,
            key = { index -> cursors.getOrNull(index) ?: index },
            verticalAlignment = Alignment.CenterVertically
        ) { index ->
            val cursor = cursors.getOrNull(index) ?: return@HorizontalPager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(pagerState.currentPage, index) {
                        detectTapGestures(
                            onTap = {
                                if (index == pagerState.currentPage) {
                                    onCenterTap()
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                pageContent(cursor, Modifier.fillMaxSize())
            }
        }
    }
}




