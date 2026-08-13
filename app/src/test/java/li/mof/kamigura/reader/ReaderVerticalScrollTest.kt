package li.mof.kamigura.reader

import li.mof.kamigura.FileDimensionDto
import li.mof.kamigura.reader.internal.ReaderVerticalPosition
import li.mof.kamigura.reader.internal.ReaderVerticalVisibleItem
import li.mof.kamigura.reader.internal.readerVerticalPageAspectRatio
import li.mof.kamigura.reader.internal.readerVerticalPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderVerticalScrollTest {
    @Test
    fun currentPageIsTheOneWithTheLargestVisibleArea() {
        assertEquals(
            ReaderVerticalPosition.Page(4),
            readerVerticalPosition(
                visibleItems = listOf(
                    ReaderVerticalVisibleItem(index = 4, offset = -700, size = 900),
                    ReaderVerticalVisibleItem(index = 5, offset = 200, size = 900)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 1_000,
                pageCount = 20
            )
        )
    }

    @Test
    fun boundaryItemsAreDistinguishedFromPages() {
        assertEquals(
            ReaderVerticalPosition.PreviousBoundary,
            readerVerticalPosition(
                visibleItems = listOf(ReaderVerticalVisibleItem(0, 0, 1_000)),
                viewportStartOffset = 0,
                viewportEndOffset = 1_000,
                pageCount = 10
            )
        )
        assertEquals(
            ReaderVerticalPosition.NextBoundary,
            readerVerticalPosition(
                visibleItems = listOf(ReaderVerticalVisibleItem(11, 0, 1_000)),
                viewportStartOffset = 0,
                viewportEndOffset = 1_000,
                pageCount = 10
            )
        )
    }

    @Test
    fun firstReadablePageIsOffsetPastThePreviousBoundary() {
        assertEquals(
            ReaderVerticalPosition.Page(0),
            readerVerticalPosition(
                visibleItems = listOf(ReaderVerticalVisibleItem(1, 0, 1_000)),
                viewportStartOffset = 0,
                viewportEndOffset = 1_000,
                pageCount = 10
            )
        )
    }

    @Test
    fun pageAspectRatioUsesDimensionsAndFallsBackWhenMissing() {
        assertEquals(
            0.5f,
            readerVerticalPageAspectRatio(
                dimension = FileDimensionDto(width = 1_000, height = 2_000),
                fallbackAspectRatio = 0.75f
            ),
            0f
        )
        assertEquals(
            0.75f,
            readerVerticalPageAspectRatio(dimension = null, fallbackAspectRatio = 0.75f),
            0f
        )
    }
}
