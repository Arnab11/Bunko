package li.mof.kamigura.reader

import androidx.compose.ui.unit.Density
import li.mof.kamigura.reader.internal.EpubBlock
import li.mof.kamigura.reader.internal.ReaderEpubPaginator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEpubPaginatorTest {

    @Test
    fun testParseHtmlToBlocks() {
        val sampleHtml = """
            <html>
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter One</h1>
                <p>This is a <b>bold</b> and <i>italic</i> sentence.</p>
                <hr/>
                <img src="images/cover.jpg" alt="Cover"/>
                <blockquote>A famous quote here.</blockquote>
            </body>
            </html>
        """.trimIndent()

        val blocks = ReaderEpubPaginator.parseHtmlToBlocks(
            html = sampleHtml,
            chapterId = 42,
            baseUrl = "http://localhost:5000",
            apiKey = "test-api-key",
            bookResourceUrlBuilder = { base, key, chId, path ->
                "$base/api/Reader/book-resources?chapterId=$chId&fileName=$path&apiKey=$key"
            }
        )

        assertTrue(blocks.size >= 4)
        val h1 = blocks[0] as EpubBlock.TextBlock
        assertTrue(h1.isHeading)
        assertEquals(1, h1.headingLevel)
        assertEquals("Chapter One", h1.text.text)

        val p = blocks[1] as EpubBlock.TextBlock
        assertEquals("This is a bold and italic sentence.", p.text.text)

        val hr = blocks[2]
        assertTrue(hr is EpubBlock.DividerBlock)

        val img = blocks[3] as EpubBlock.ImageBlock
        assertEquals("http://localhost:5000/api/Reader/book-resources?chapterId=42&fileName=images/cover.jpg&apiKey=test-api-key", img.url)
        assertEquals("Cover", img.alt)
    }

    @Test
    fun testHtmlEntityDecoding() {
        val html = "<p>&ldquo;Hello &amp; welcome&mdash;enjoy!&rdquo;</p>"
        val blocks = ReaderEpubPaginator.parseHtmlToBlocks(
            html = html,
            chapterId = 1,
            baseUrl = "http://localhost:5000",
            apiKey = "",
            bookResourceUrlBuilder = { base, _, chId, path -> "$base/$chId/$path" }
        )

        assertEquals(1, blocks.size)
        val p = blocks[0] as EpubBlock.TextBlock
        assertEquals("\"Hello & welcome—enjoy!\"", p.text.text)
    }

    @Test
    fun testPaginateLongTextIntoMultipleSubpages() {
        val longParagraph = (1..50).joinToString(" ") { "Sentence number $it." }
        val sampleHtml = "<p>$longParagraph</p>"

        val blocks = ReaderEpubPaginator.parseHtmlToBlocks(
            html = sampleHtml,
            chapterId = 1,
            baseUrl = "http://localhost:5000",
            apiKey = "",
            bookResourceUrlBuilder = { base, _, chId, path -> "$base/$chId/$path" }
        )

        val density = Density(density = 2.5f)
        // With a small available height of 300px, it must paginate into multiple discrete subpages
        val subpages = ReaderEpubPaginator.paginateBlocks(
            spineIndex = 0,
            blocks = blocks,
            availableWidthPx = 400,
            availableHeightPx = 300,
            fontSizePx = 40f,
            density = density
        )

        assertTrue("Expected multiple subpages for long text, got ${subpages.size}", subpages.size > 1)
        assertEquals(0, subpages.first().subpageIndex)
        assertEquals(subpages.size, subpages.first().totalSubpagesInSpine)
    }
}
