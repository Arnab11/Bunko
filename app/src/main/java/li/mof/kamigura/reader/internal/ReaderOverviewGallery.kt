package li.mof.kamigura.reader.internal

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Google Play Books style overview: every spread is the same size card, centered
 * with neighbours peeking on either side, scrolling smoothly 1:1 like a horizontal
 * PDF viewer via [HorizontalPager]. One card per readable spread position so a
 * swipe lands exactly on a page the reader can show.
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
    val initialIndex = readerOverviewIndexForCursor(cursors, currentCursor)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { cursors.size }
    )

    // When the reader page changes elsewhere (slider, tap zones, chapter switch),
    // jump the gallery straight there with no glide-through — like a PDF viewer.
    // While the user is actively dragging the gallery itself we leave it alone so
    // the finger keeps 1:1 control.
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
        // Play Books proportions: uniform cards, neighbours peeking. Portrait cards
        // are wider (less peek) and shorter (tall pages looked stretched); landscape
        // keeps the classic narrower card with more peek.
        val cardFraction = if (isPortrait) 0.82f else 0.62f
        val heightFraction = if (isPortrait) 0.80f else 0.90f
        val sidePadding = maxWidth * ((1f - cardFraction) / 2f)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 68.dp)
                .navigationBarsPadding()
                // Gap between the cards and the bottom slider bar.
                .padding(bottom = 92.dp),
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = 16.dp,
            reverseLayout = reverseLayout,
            beyondViewportPageCount = 2,
            key = { index -> cursors.getOrNull(index) ?: index },
            verticalAlignment = Alignment.CenterVertically
        ) { index ->
            val cursor = cursors.getOrNull(index) ?: return@HorizontalPager
            // All cards identical size — no focus scale/alpha — so the scroll feels
            // like a smooth horizontal PDF viewer, fully 1:1 with the finger.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .clickable {
                        if (index == pagerState.currentPage) {
                            onCenterTap()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                pageContent(cursor, Modifier.fillMaxSize())
            }
        }
    }
}
