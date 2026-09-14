package com.bunko.reader.reader

import com.bunko.reader.FileDimensionDto
import com.bunko.reader.reader.internal.readerPageLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLayoutTest {

    private fun wideAt(vararg pages: Int): Map<Int, FileDimensionDto> =
        pages.associateWith { page ->
            FileDimensionDto(width = 2000, height = 1400, pageNumber = page, isWide = true)
        }

    @Test
    fun previousStepIsFullSpreadInAPlainChain() {
        val layout = readerPageLayout(page = 6, pageCount = 30, portrait = false, pageDimensions = emptyMap())

        assertEquals(2, layout.previousStep)
    }

    @Test
    fun previousStepDoesNotSkipTheLonePageAfterAWidePage() {
        // Shifted chain: wide P4 alone, then [6,7]. Stepping back two would land on the
        // wide page and P5 would never be shown; step one instead.
        val layout = readerPageLayout(page = 6, pageCount = 30, portrait = false, pageDimensions = wideAt(3, 4))

        assertEquals(1, layout.previousStep)
    }

    @Test
    fun previousStepDoesNotSkipTheLonePageAfterTheCover() {
        // Shifted chain near the front: cover alone, then [2,3]. Stepping back two would
        // land on the cover and P1 would never be shown; step one instead.
        val layout = readerPageLayout(page = 2, pageCount = 30, portrait = false, pageDimensions = emptyMap())

        assertEquals(1, layout.previousStep)
    }

    @Test
    fun epubLandscapeSpreadStepsAreConsistent() {
        // Page 0 (spread [0, 1]) -> nextStep = 2, previousStep = 1 (boundary)
        val p0 = readerPageLayout(page = 0, pageCount = 30, portrait = false, pageDimensions = emptyMap(), isEpub = true)
        assertEquals(2, p0.nextStep)
        assertEquals(1, p0.previousStep)
        assertEquals(false, p0.singlePage)

        // Page 2 (spread [2, 3]) -> nextStep = 2, previousStep = 2
        val p2 = readerPageLayout(page = 2, pageCount = 30, portrait = false, pageDimensions = emptyMap(), isEpub = true)
        assertEquals(2, p2.nextStep)
        assertEquals(2, p2.previousStep)
        assertEquals(false, p2.singlePage)

        // Page 11 (spread [11, 12]) -> nextStep = 2, previousStep = 2
        val p11 = readerPageLayout(page = 11, pageCount = 30, portrait = false, pageDimensions = emptyMap(), isEpub = true)
        assertEquals(2, p11.nextStep)
        assertEquals(2, p11.previousStep)
        assertEquals(false, p11.singlePage)

        // Last page single spread (page 29 of 30) -> nextStep = 1, previousStep = 2
        val p29 = readerPageLayout(page = 29, pageCount = 30, portrait = false, pageDimensions = emptyMap(), isEpub = true)
        assertEquals(1, p29.nextStep)
        assertEquals(2, p29.previousStep)
        assertEquals(true, p29.singlePage)
    }
}
