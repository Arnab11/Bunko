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
    fun isWebtoonMetadataMatchesKeywordsWithoutFalsePositives() {
        // Legitimate webtoons
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(genres = listOf("Action", "Webtoon")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(tags = listOf("Manhwa", "Fantasy")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(genres = listOf("long strip")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(tags = listOf("vertical scroll")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(seriesName = "Solo Leveling"))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(publishers = listOf("KakaoPage")))
        assertTrue(ReaderWebtoonDetector.isWebtoonMetadata(summary = "This is a popular webtoon adapted from the web novel."))

        // False positive prevention
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(genres = listOf("Action", "Manga"), tags = listOf("Shounen"), seriesName = "One Piece"))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(tags = listOf("Full Color", "Colored", "Action")))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(summary = "Naruto searches for the ancient scroll of sealing."))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(seriesName = "Spider-Man"))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(genres = null, tags = null, seriesName = null))
        assertFalse(ReaderWebtoonDetector.isWebtoonMetadata(genres = emptyList(), tags = emptyList()))
    }

    @Test
    fun isWebtoonDimensionsDetectsTallStripsWithoutFalsePositives() {
        // Typical Japanese tankobon manga page (e.g. 800 x 1130 -> aspect ratio 0.708)
        val mangaPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 1130),
            2 to FileDimensionDto(width = 800, height = 1130),
            3 to FileDimensionDto(width = 800, height = 1130)
        )
        assertFalse(ReaderWebtoonDetector.isWebtoonDimensions(mangaPages))

        // Standard US Comic page (e.g. 1988 x 3056 -> aspect ratio 0.650)
        val usComicPages = (1..24).associateWith {
            FileDimensionDto(width = 1988, height = 3056)
        }
        assertFalse(ReaderWebtoonDetector.isWebtoonDimensions(usComicPages))

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
        val usComicPages = (1..24).associateWith {
            FileDimensionDto(width = 1988, height = 3056)
        }
        val webtoonPages = mapOf(
            1 to FileDimensionDto(width = 800, height = 2400)
        )

        // US Comic with autoWebtoonMode = true -> stays LeftToRight (not falsely converted to Webtoon)
        assertEquals(
            ReaderReadingDirection.LeftToRight,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.LeftToRight,
                autoWebtoonMode = true,
                pageDimensions = usComicPages,
                seriesName = "Batman"
            )
        )

        // Solo Leveling series name with autoWebtoonMode = true -> resolves to Webtoon
        assertEquals(
            ReaderReadingDirection.Webtoon,
            ReaderWebtoonDetector.resolveEffectiveReadingDirection(
                preferredDirection = ReaderReadingDirection.RightToLeft,
                autoWebtoonMode = true,
                pageDimensions = usComicPages,
                seriesName = "Solo Leveling"
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
                pageDimensions = usComicPages,
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
                pageDimensions = usComicPages
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
