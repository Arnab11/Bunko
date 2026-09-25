package com.bunko.reader.reader

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAppearanceTest {
    @Test
    fun portraitBackContentUsesFaintAlphaWhenEnabled() {
        assertEquals(0.05f, readerPortraitBackPageContentAlpha(showContent = true), 0f)
    }

    @Test
    fun portraitBackContentIsHiddenWhenDisabled() {
        assertEquals(0f, readerPortraitBackPageContentAlpha(showContent = false), 0f)
    }

    @Test
    fun pageBackgroundUsesSoftColorsByDefault() {
        assertEquals(Color(0xFFFBF0D9), readerPageBackgroundColor(darkPaper = false, usePureColors = false))
        assertEquals(Color(0xFF101010), readerPageBackgroundColor(darkPaper = true, usePureColors = false))
    }

    @Test
    fun pageBackgroundUsesPureColorsWhenEnabled() {
        assertEquals(Color.White, readerPageBackgroundColor(darkPaper = false, usePureColors = true))
        assertEquals(Color.Black, readerPageBackgroundColor(darkPaper = true, usePureColors = true))
    }

    @Test
    fun readerSettingsDefaultsTtsToDisabled() {
        val defaultSettings = com.bunko.reader.ReaderSettings()
        org.junit.Assert.assertFalse(defaultSettings.ttsEnabled)
    }
}
