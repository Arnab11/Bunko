package com.bunko.reader.reader

import com.bunko.reader.FileDimensionDto
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.isVertical
import com.bunko.reader.reader.internal.ReaderWebtoonDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWebtoonTest {

    @Test
    fun isWebtoonMetadataMatchesKeywords() {
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(listOf("Action", "Webtoon"), null))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(null, listOf("Manhwa", "Fantasy")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(listOf("long strip"), null))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(null, listOf("vertical scroll")))

        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(listOf("Action", "Manga"), listOf("Shounen")))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(null, null))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(emptyList(), emptyList()))
    }

    @Test
    fun isWebtoonDimensionsDetectsTallStrips() {
        // Typical manga page (e.g. 800 x 1200 -> aspect ratio 0.667)
        val mangaPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 1200),
            2 to FileDimensionDto(width = 800, height = 1200),
            3 to FileDimensionDto(width = 800, height = 1200)
        )
        assertFalse(ReaderWebtoonDetector.isWebtoonDimensions(mangaPages))

        // Webtoon slices with extreme vertical ratio (e.g. 800 x 2400 -> aspect ratio 0.333)
        val webtoonPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 2400),
            2 to FileDimensionDto(width = 800, height = 2000),
            3 to FileDimensionDto(width = 800, height = 2800)
        )
        assertTrue(ReaderWebtoonDetector.isWebtoonDimensions(webtoonPages))

        // Mixed webtoon strip with at least one extreme tall slice
        val mixedStrip = mapOf(
            1 to FileDimensionDto(width = 800, height = 1000),
            2 to FileDimensionDto(width = 800, height = 2500)
        )
        assertTrue(ReaderWebtoonDetector.isWebtoonDimensions(mixedStrip))

        // Null / empty dimensions
        assertFalse(ReaderWebtoonDetector.isWebtoonDimensions(null))
        assertFalse(ReaderWebtoonDetector.isWebtoonDimensions(emptyMap()))
    }

    @Test
    fun resolveEffectiveReadingDirectionWithAutoWebtoonMode() {
        val mangaPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 1200)
        )
        val webtoonPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 2400)
        )

        // Manga with autoWebtoonMode = true -> stays RightToLeft
        assertEquals(
            ReaderReadingDirection.RightToLeft,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.RightToLeft,
                autoWebtoonMode = true,
                pageDimensions = mangaPages,
                genres = listOf("Action", "Shounen")
            )
        )

        // Webtoon pages with autoWebtoonMode = true -> resolves to Webtoon
        assertEquals(
            ReaderReadingDirection.Webtoon,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.RightToLeft,
                autoWebtoonMode = true,
                pageDimensions = webtoonPages,
                genres = listOf("Action")
            )
        )

        // Webtoon metadata tag with autoWebtoonMode = true -> resolves to Webtoon
        assertEquals(
            ReaderReadingDirection.Webtoon,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.RightToLeft,
                autoWebtoonMode = true,
                pageDimensions = mangaPages,
                tags = listOf("Webtoon")
            )
        )

        // Webtoon content but autoWebtoonMode = false -> respects user preference
        assertEquals(
            ReaderReadingDirection.RightToLeft,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.RightToLeft,
                autoWebtoonMode = false,
                pageDimensions = webtoonPages,
                tags = listOf("Webtoon")
            )
        )

        // Explicit user preference is Webtoon -> stays Webtoon regardless of auto mode
        assertEquals(
            ReaderReadingDirection.Webtoon,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.Webtoon,
                autoWebtoonMode = false,
                pageDimensions = mangaPages
            )
        )

        // Explicit user preference is Vertical -> stays Vertical regardless of auto mode
        assertEquals(
            ReaderReadingDirection.Vertical,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.Vertical,
                autoWebtoonMode = true,
                pageDimensions = webtoonPages
            )
        )
    }

    @Test
    fun readerReadingDirectionIsVerticalHelper() {
        assertTrue(ReaderReadingDirection.Webtoon.isVertical)
        assertTrue(ReaderReadingDirection.Vertical.isVertical)
        assertFalse(ReaderReadingDirection.RightToLeft.isVertical)
        assertFalse(ReaderReadingDirection.LeftToRight.isVertical)

        val webtoonSettings = com.bunko.reader.ReaderSettings(readingDirection = ReaderReadingDirection.Webtoon)
        assertTrue(webtoonSettings.isVerticalReading)
        val verticalSettings = com.bunko.reader.ReaderSettings(readingDirection = ReaderReadingDirection.Vertical)
        assertTrue(verticalSettings.isVerticalReading)
        val ltrSettings = com.bunko.reader.ReaderSettings(readingDirection = ReaderReadingDirection.LeftToRight)
        assertFalse(ltrSettings.isVerticalReading)
    }

    @Test
    fun readerSettingsOverviewModeDefault() {
        val defaultSettings = com.bunko.reader.ReaderSettings()
        assertTrue(defaultSettings.overviewMode)

        val disabledSettings = com.bunko.reader.ReaderSettings(overviewMode = false)
        assertFalse(disabledSettings.overviewMode)
    }
}
