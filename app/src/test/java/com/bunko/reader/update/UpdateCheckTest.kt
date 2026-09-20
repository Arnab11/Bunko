package com.bunko.reader.update

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
}
