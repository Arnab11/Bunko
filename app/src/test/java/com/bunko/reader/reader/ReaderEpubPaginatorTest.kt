package com.bunko.reader.reader

import androidx.compose.ui.unit.Density
import com.bunko.reader.reader.internal.EpubBlock
import com.bunko.reader.reader.internal.ReaderEpubPaginator
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
    fun testImageInsideParagraphAndFigureAndSvg() {
        val html = """
            <div>
                <p class="illustration"><img src="images/p_img.png" alt="P Image"/></p>
                <p>Text before <img src="images/inline.png"/> text after.</p>
                <figure>
                    <img src="images/fig.jpg" width="600" height="800" alt="Figure"/>
                    <figcaption>Figure caption text</figcaption>
                </figure>
                <svg viewBox="0 0 1200 1600">
                    <image xlink:href="images/svg_cover.jpg"/>
                </svg>
            </div>
        """.trimIndent()

        val blocks = ReaderEpubPaginator.parseHtmlToBlocks(
            html = html,
            chapterId = 1,
            baseUrl = "",
            apiKey = "",
            bookResourceUrlBuilder = { _, _, _, path -> path }
        )

        assertEquals(7, blocks.size)
        // 1. Paragraph image
        val img1 = blocks[0] as EpubBlock.ImageBlock
        assertEquals("images/p_img.png", img1.url)
        assertEquals("P Image", img1.alt)

        // 2. Text before
        val t1 = blocks[1] as EpubBlock.TextBlock
        assertEquals("Text before", t1.text.text.trim())

        // 3. Inline image
        val img2 = blocks[2] as EpubBlock.ImageBlock
        assertEquals("images/inline.png", img2.url)

        // 4. Text after
        val t2 = blocks[3] as EpubBlock.TextBlock
        assertEquals("text after.", t2.text.text.trim())

        // 5. Figure image with parsed dimensions
        val img3 = blocks[4] as EpubBlock.ImageBlock
        assertEquals("images/fig.jpg", img3.url)
        assertEquals(600, img3.intrinsicWidth)
        assertEquals(800, img3.intrinsicHeight)
        assertTrue(img3.aspectRatio > 1.3f)

        // 6. Figcaption text
        val cap = blocks[5] as EpubBlock.TextBlock
        assertEquals("Figure caption text", cap.text.text)

        // 7. Svg image with viewBox dimensions
        val img4 = blocks[6] as EpubBlock.ImageBlock
        assertEquals("images/svg_cover.jpg", img4.url)
        assertEquals(1200, img4.intrinsicWidth)
        assertEquals(1600, img4.intrinsicHeight)
        assertTrue(img4.aspectRatio > 1.3f)
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
        assertEquals("“Hello & welcome—enjoy!”", p.text.text)
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

    @Test
    fun testPaginateFullPageCoverSeparation() {
        val blocks = listOf(
            EpubBlock.TextBlock(androidx.compose.ui.text.AnnotatedString("Introduction")),
            EpubBlock.ImageBlock(
                url = "images/cover.jpg",
                intrinsicWidth = 1000,
                intrinsicHeight = 1500,
                aspectRatio = 1.5f
            ),
            EpubBlock.TextBlock(androidx.compose.ui.text.AnnotatedString("Chapter 1 content starts here."))
        )

        val density = Density(density = 2.5f)
        val subpages = ReaderEpubPaginator.paginateBlocks(
            spineIndex = 0,
            blocks = blocks,
            availableWidthPx = 1000,
            availableHeightPx = 1500,
            fontSizePx = 30f,
            density = density
        )

        // Full-page illustration must be placed on its own subpage
        assertEquals(3, subpages.size)
        assertEquals(1, subpages[0].blocks.size)
        assertTrue(subpages[0].blocks[0] is EpubBlock.TextBlock)

        assertEquals(1, subpages[1].blocks.size)
        assertTrue(subpages[1].blocks[0] is EpubBlock.ImageBlock)

        assertEquals(1, subpages[2].blocks.size)
        assertTrue(subpages[2].blocks[0] is EpubBlock.TextBlock)
    }
}
