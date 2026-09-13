package com.bunko.reader

import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.download.compareNaturalFileNames
import com.bunko.reader.cache.imageCacheBudget
import com.bunko.reader.cache.imageCacheScope
import com.bunko.reader.cache.stableKavitaImageCacheKey
import com.bunko.reader.reader.readerPrefetchMemoryPlan
import com.bunko.reader.reader.readerPrefetchPageIndices
import com.bunko.reader.reader.ReaderSessionPreferenceCache
import com.bunko.reader.reader.ReaderSessionPreferences
import com.bunko.reader.reader.ReaderSessionPreferenceTtlMillis
import com.bunko.reader.reader.readerSessionPreferenceKey
import com.bunko.reader.update.isVersionNewer
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun releaseVersionComparisonHandlesVPrefixAndMissingPatch() {
        assertTrue(isVersionNewer("v0.13", "0.12.1"))
        assertTrue(isVersionNewer("0.12.1", "0.12"))
        assertFalse(isVersionNewer("v0.12", "0.12.0"))
        assertFalse(isVersionNewer("not-a-version", "0.12"))
    }

    @Test
    fun portraitPrefetchesOnePagePerTurn() {
        assertEquals(
            listOf(11, 12, 13, 14),
            readerPrefetchPageIndices(
                page = 10,
                pageCount = 30,
                portrait = true,
                pageDimensions = emptyMap(),
                turns = 4
            )
        )
    }

    @Test
    fun landscapePrefetchesFourFutureSpreads() {
        assertEquals(
            (1..8).toList(),
            readerPrefetchPageIndices(
                page = 0,
                pageCount = 30,
                portrait = false,
                pageDimensions = emptyMap(),
                turns = 4
            )
        )
    }

    @Test
    fun zeroTurnsDisablesPrefetch() {
        assertEquals(
            emptyList<Int>(),
            readerPrefetchPageIndices(
                page = 10,
                pageCount = 30,
                portrait = false,
                pageDimensions = emptyMap(),
                turns = 0
            )
        )
    }

    @Test
    fun prefetchClampsAtChapterEnd() {
        assertEquals(
            listOf(29),
            readerPrefetchPageIndices(
                page = 28,
                pageCount = 30,
                portrait = true,
                pageDimensions = emptyMap(),
                turns = 4
            )
        )
    }

    @Test
    fun naturalPageOrderMatchesReaderOrder() {
        val names = listOf(
            "chapter/page10.jpg",
            "chapter/page2.jpg",
            "chapter/page001.jpg",
            "chapter/page20.jpg"
        )

        assertEquals(
            listOf(
                "chapter/page001.jpg",
                "chapter/page2.jpg",
                "chapter/page10.jpg",
                "chapter/page20.jpg"
            ),
            names.sortedWith(::compareNaturalFileNames)
        )
    }

    @Test
    fun sessionKeySeparatesUsersOnTheSameServer() {
        val first = OfflineIssueRepository.sessionKey(
            KavitaSession(baseUrl = "https://example.test", username = "reader-a")
        )
        val second = OfflineIssueRepository.sessionKey(
            KavitaSession(baseUrl = "https://example.test/", username = "reader-b")
        )

        assertNotEquals(first, second)
    }

    @Test
    fun sessionKeyNormalizesEquivalentServerUrls() {
        val first = OfflineIssueRepository.sessionKey(
            KavitaSession(baseUrl = "https://example.test/api", username = "Reader")
        )
        val second = OfflineIssueRepository.sessionKey(
            KavitaSession(baseUrl = "https://example.test/", username = "reader")
        )

        assertEquals(first, second)
    }

    @Test
    fun offlineStorageCheckKeepsReserveAfterDownload() {
        val mib = 1024L * 1024L

        assertEquals(
            true,
            OfflineIssueRepository.hasEnoughOfflineStorage(
                expectedBytes = 181L * mib,
                availableBytes = 910L * mib
            )
        )
        assertEquals(
            false,
            OfflineIssueRepository.hasEnoughOfflineStorage(
                expectedBytes = 950L * mib,
                availableBytes = 910L * mib
            )
        )
    }

    @Test
    fun imageCacheKeyIgnoresApiKeyRotationForTheSameProfile() {
        val session = KavitaSession(baseUrl = "https://example.test", username = "reader")
        val scope = imageCacheScope(session)
        val before = stableKavitaImageCacheKey(
            scope,
            "https://example.test/api/Reader/image?chapterId=42&apiKey=old&page=7"
        )
        val after = stableKavitaImageCacheKey(
            scope,
            "https://example.test/api/Reader/image?chapterId=42&apiKey=new&page=7"
        )

        assertEquals(before, after)
    }

    @Test
    fun imageCacheScopeSeparatesUsersOnTheSameServer() {
        val first = imageCacheScope(KavitaSession(baseUrl = "https://example.test", username = "reader-a"))
        val second = imageCacheScope(KavitaSession(baseUrl = "https://example.test", username = "reader-b"))

        assertNotEquals(first, second)
    }

    @Test
    fun largeHeapDevicesReceiveAReaderFocusedCacheBudget() {
        val gib = 1024L * 1024L * 1024L
        val budget = imageCacheBudget(
            memoryClassMb = 512,
            lowRam = false,
            usableStorageBytes = 512L * gib
        )

        assertEquals(0.40, budget.readerMemoryPercent, 0.0)
        assertEquals(8L * gib, budget.readerDiskBytes)
        assertEquals(1L * gib, budget.coverDiskBytes)
    }

    @Test
    fun lowFreeStorageDoesNotAllocateMoreThanOnePercentToImageCache() {
        val mib = 1024L * 1024L
        val budget = imageCacheBudget(
            memoryClassMb = 512,
            lowRam = false,
            usableStorageBytes = 2_000L * mib
        )

        assertEquals(20L * mib, budget.coverDiskBytes + budget.readerDiskBytes)
    }

    @Test
    fun prefetchStopsAtTheReaderMemoryWorkingSetBoundary() {
        assertEquals(
            listOf(true, true, false, false),
            readerPrefetchMemoryPlan(
                estimatedBytes = listOf(30L, 40L, 50L, 1L),
                memoryCacheMaxBytes = 100L
            )
        )
    }

    @Test
    fun imageCacheScopeUsesStableProfileIdForApiKeyOnlyProfiles() {
        val before = imageCacheScope(
            KavitaSession(baseUrl = "https://example.test", apiKey = "old"),
            profileId = "profile-1"
        )
        val after = imageCacheScope(
            KavitaSession(baseUrl = "https://example.test", apiKey = "new"),
            profileId = "profile-1"
        )

        assertEquals(before, after)
    }

    @Test
    fun readerSessionPreferencesExpireAfterThirtyDays() {
        ReaderSessionPreferenceCache.clearMemoryForTest()
        val preferences = ReaderSessionPreferences(
            rightToLeft = false,
            invertMode = InvertMode.Always
        )
        ReaderSessionPreferenceCache.put("book", preferences, nowMillis = 1_000L)

        assertEquals(
            preferences,
            ReaderSessionPreferenceCache.get(
                "book",
                nowMillis = 1_000L + ReaderSessionPreferenceTtlMillis - 1L
            )
        )
        assertEquals(
            null,
            ReaderSessionPreferenceCache.get(
                "book",
                nowMillis = 1_000L + ReaderSessionPreferenceTtlMillis * 2L
            )
        )
    }

    @Test
    fun readerSessionPreferencesSurviveAnInMemoryCacheReset() = runBlocking {
        val directory = Files.createTempDirectory("reader-preferences").toFile()
        val preferences = ReaderSessionPreferences(
            rightToLeft = true,
            invertMode = InvertMode.Smart
        )
        try {
            ReaderSessionPreferenceCache.clear(directory)
            ReaderSessionPreferenceCache.put("book", preferences, nowMillis = 10_000L)
            ReaderSessionPreferenceCache.persist(directory)
            ReaderSessionPreferenceCache.clearMemoryForTest()

            ReaderSessionPreferenceCache.load(directory, nowMillis = 11_000L)

            assertEquals(
                preferences,
                ReaderSessionPreferenceCache.get("book", nowMillis = 11_000L)
            )
        } finally {
            ReaderSessionPreferenceCache.clear(directory)
            ReaderSessionPreferenceCache.clearMemoryForTest()
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacyReaderSessionDirectionSurvivesDiskMigration() = runBlocking {
        val directory = Files.createTempDirectory("legacy-reader-preferences").toFile()
        try {
            ReaderSessionPreferenceCache.clear(directory)
            directory.resolve("reader_session_preferences.json").writeText(
                """[{"key":"book","rightToLeft":false,"invertMode":"Off","touchedAtMillis":10000}]"""
            )
            ReaderSessionPreferenceCache.clearMemoryForTest()

            ReaderSessionPreferenceCache.load(directory, nowMillis = 11_000L)

            assertEquals(
                ReaderReadingDirection.LeftToRight,
                ReaderSessionPreferenceCache.get("book", nowMillis = 11_000L)?.readingDirection
            )
        } finally {
            ReaderSessionPreferenceCache.clear(directory)
            ReaderSessionPreferenceCache.clearMemoryForTest()
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacyReaderDirectionSettingsMigrateWithoutChangingTheChoice() {
        assertEquals(
            ReaderReadingDirection.RightToLeft,
            readerReadingDirection(storedName = null, legacyRightToLeft = true)
        )
        assertEquals(
            ReaderReadingDirection.LeftToRight,
            readerReadingDirection(storedName = null, legacyRightToLeft = false)
        )
        assertEquals(
            ReaderReadingDirection.LeftToRight,
            readerReadingDirection(storedName = null, legacyRightToLeft = null)
        )
        assertEquals(
            ReaderReadingDirection.Vertical,
            readerReadingDirection(
                storedName = ReaderReadingDirection.Vertical.name,
                legacyRightToLeft = true
            )
        )
    }

    @Test
    fun readerSessionPreferencesAreScopedPerProfileAndSeries() {
        val session = KavitaSession(baseUrl = "https://example.test", username = "reader")

        assertNotEquals(
            readerSessionPreferenceKey(session, "profile-a", seriesId = 10),
            readerSessionPreferenceKey(session, "profile-a", seriesId = 11)
        )
        assertNotEquals(
            readerSessionPreferenceKey(session, "profile-a", seriesId = 10),
            readerSessionPreferenceKey(session, "profile-b", seriesId = 10)
        )
    }
}
