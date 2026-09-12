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
}
