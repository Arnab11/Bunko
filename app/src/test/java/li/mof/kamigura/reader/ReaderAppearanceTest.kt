package li.mof.kamigura.reader

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
}
