package com.bunko.reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun previewNewerThanOlderBase() {
        assertTrue(isPreviewNewer("v0.24-preview.1", "0.23"))
    }

    @Test
    fun previewNumberClimbsOnSameBase() {
        assertTrue(isPreviewNewer("v0.24-preview.2", "0.24-preview.1"))
        assertFalse(isPreviewNewer("v0.24-preview.1", "0.24-preview.2"))
        assertFalse(isPreviewNewer("v0.24-preview.1", "0.24-preview.1"))
    }

    @Test
    fun stableCountsAsPreviewZero() {
        assertTrue(isPreviewNewer("v0.24-preview.1", "0.24"))
        assertFalse(isPreviewNewer("v0.24-preview.1", "0.25"))
    }

    @Test
    fun malformedTagsNeverWin() {
        assertFalse(isPreviewNewer("not-a-version", "0.23"))
        assertFalse(isPreviewNewer("v0.24-preview.1", "not-a-version"))
    }

    private fun asset(name: String) = UpdateAsset(
        downloadUrl = "https://example.com/$name",
        name = name,
        size = 1L
    )

    @Test
    fun prefersDeviceArchApk() {
        val assets = listOf(
            asset("Bunko-universal-v0.24.apk"),
            asset("Bunko-arm64-v8a-v0.24.apk"),
            asset("Bunko-x86_64-v0.24.apk")
        )
        assertEquals(
            "Bunko-arm64-v8a-v0.24.apk",
            selectBunkoApkAsset(assets, "arm64-v8a")?.name
        )
        assertEquals(
            "Bunko-x86_64-v0.24.apk",
            selectBunkoApkAsset(assets, "x86_64")?.name
        )
    }

    @Test
    fun fallsBackToUniversalThenAnyApk() {
        val assets = listOf(
            asset("Bunko-x86_64-v0.24.apk"),
            asset("Bunko-universal-v0.24.apk")
        )
        assertEquals(
            "Bunko-universal-v0.24.apk",
            selectBunkoApkAsset(assets, "arm64-v8a")?.name
        )
        assertEquals(
            null,
            selectBunkoApkAsset(listOf(asset("notes.txt")), "arm64-v8a")
        )
    }
}
