package com.bunko.reader.reader.internal

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.regex.Pattern

object ReaderEpubPaginator {

    private val TagPattern = Pattern.compile("(?s)<(/?[a-zA-Z0-9]+)([^>]*)>")
    private val EntityPattern = Pattern.compile("&(#?[a-zA-Z0-9]+);")

    fun parseHtmlToBlocks(
        html: String,
        chapterId: Int,
        baseUrl: String,
        apiKey: String,
        bookResourceUrlBuilder: (String, String, Int, String) -> String
    ): List<EpubBlock> {
        val blocks = mutableListOf<EpubBlock>()
        val bodyContent = extractBodyContent(html)
        val matcher = TagPattern.matcher(bodyContent)

        var lastIdx = 0
        var currentTag = ""
        var currentAttrs = ""
        val textBuilder = StringBuilder()
        val styles = mutableListOf<StyleSpan>()
        val activeStyles = mutableListOf<ActiveStyle>()

        fun flushText(isHeading: Boolean = false, headingLevel: Int = 0, isQuote: Boolean = false) {
            val raw = textBuilder.toString()
            textBuilder.setLength(0)
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                styles.clear()
                return
            }

            // Adjust styles relative to the trimmed start
            val leadingWs = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val length = trimmed.length
            val annotatedBuilder = AnnotatedString.Builder(trimmed)

            for (s in styles) {
                val start = (s.start - leadingWs).coerceIn(0, length)
                val end = (s.end - leadingWs).coerceIn(start, length)
                if (end > start) {
                    annotatedBuilder.addStyle(s.style, start, end)
                }
            }
            styles.clear()

            blocks.add(
                EpubBlock.TextBlock(
                    text = annotatedBuilder.toAnnotatedString(),
                    isHeading = isHeading,
                    headingLevel = headingLevel,
                    isQuote = isQuote
                )
            )
        }

        while (matcher.find()) {
            val textBefore = bodyContent.substring(lastIdx, matcher.start())
            if (textBefore.isNotEmpty()) {
                val decoded = decodeHtmlEntities(textBefore)
                val startPos = textBuilder.length
                textBuilder.append(decoded)
                val endPos = textBuilder.length
                for (active in activeStyles) {
                    styles.add(StyleSpan(active.style, startPos, endPos))
                }
            }

            val rawTagName = matcher.group(1) ?: ""
            val tagName = rawTagName.lowercase()
            val attrs = matcher.group(2).orEmpty()
            val isClosing = tagName.startsWith("/")
            val cleanTagName = tagName.removePrefix("/")

            when (cleanTagName) {
                "p", "div", "section", "article" -> {
                    if (isClosing || textBuilder.isNotEmpty()) {
                        flushText()
                    }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = cleanTagName.substring(1).toIntOrNull() ?: 1
                    if (isClosing) {
                        flushText(isHeading = true, headingLevel = level)
                    } else if (textBuilder.isNotEmpty()) {
                        flushText()
                    }
                }
                "blockquote" -> {
                    if (isClosing) {
                        flushText(isQuote = true)
                    } else if (textBuilder.isNotEmpty()) {
                        flushText()
                    }
                }
                "hr" -> {
                    flushText()
                    blocks.add(EpubBlock.DividerBlock)
                }
                "br" -> {
                    textBuilder.append("\n")
                }
                "b", "strong" -> {
                    if (isClosing) {
                        removeActiveStyle(activeStyles, FontWeight.Bold)
                    } else {
                        activeStyles.add(ActiveStyle(SpanStyle(fontWeight = FontWeight.Bold), FontWeight.Bold))
                    }
                }
                "i", "em" -> {
                    if (isClosing) {
                        removeActiveStyle(activeStyles, FontStyle.Italic)
                    } else {
                        activeStyles.add(ActiveStyle(SpanStyle(fontStyle = FontStyle.Italic), FontStyle.Italic))
                    }
                }
                "u" -> {
                    if (isClosing) {
                        removeActiveStyle(activeStyles, TextDecoration.Underline)
                    } else {
                        activeStyles.add(ActiveStyle(SpanStyle(textDecoration = TextDecoration.Underline), TextDecoration.Underline))
                    }
                }
                "s", "strike", "del" -> {
                    if (isClosing) {
                        removeActiveStyle(activeStyles, TextDecoration.LineThrough)
                    } else {
                        activeStyles.add(ActiveStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), TextDecoration.LineThrough))
                    }
                }
                "img", "image" -> {
                    flushText()
                    val src = extractAttribute(attrs, "src")
                        ?: extractAttribute(attrs, "xlink:href")
                        ?: extractAttribute(attrs, "href")
                    if (!src.isNullOrBlank()) {
                        val fullUrl = bookResourceUrlBuilder(baseUrl, apiKey, chapterId, src)
                        val alt = extractAttribute(attrs, "alt")
                        blocks.add(EpubBlock.ImageBlock(url = fullUrl, alt = alt))
                    }
                }
            }

            lastIdx = matcher.end()
        }

        val remainingText = bodyContent.substring(lastIdx)
        if (remainingText.isNotEmpty()) {
            val decoded = decodeHtmlEntities(remainingText)
            textBuilder.append(decoded)
        }
        if (textBuilder.isNotEmpty()) {
            flushText()
        }

        return blocks
    }

    fun paginateBlocks(
        spineIndex: Int,
        blocks: List<EpubBlock>,
        availableWidthPx: Int,
        availableHeightPx: Int,
        fontSizePx: Float,
        lineHeightMultiplier: Float = 1.45f,
        fontFamily: String = "Serif",
        density: Density
    ): List<EpubSubpage> {
        if (blocks.isEmpty() || availableWidthPx <= 0 || availableHeightPx <= 0) {
            return listOf(
                EpubSubpage(
                    spineIndex = spineIndex,
                    subpageIndex = 0,
                    totalSubpagesInSpine = 1,
                    blocks = emptyList(),
                    progressRatio = 0f
                )
            )
        }

        val subpages = mutableListOf<MutableList<EpubBlock>>()
        var currentSubpageBlocks = mutableListOf<EpubBlock>()
        var remainingHeightPx = availableHeightPx

        val activeTypeface = when (fontFamily.lowercase()) {
            "sans", "sansserif", "sans-serif" -> Typeface.SANS_SERIF
            "mono", "monospace" -> Typeface.MONOSPACE
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.SERIF
        }

        val basePaint = runCatching {
            TextPaint().apply {
                isAntiAlias = true
                textSize = fontSizePx
                typeface = activeTypeface
            }
        }.getOrNull()

        val quotePaint = if (basePaint != null) {
            runCatching {
                TextPaint(basePaint).apply {
                    textSize = fontSizePx
                    typeface = Typeface.create(activeTypeface, Typeface.ITALIC)
                }
            }.getOrNull()
        } else null

        val headingPaints = if (basePaint != null) {
            (1..6).associateWith { level ->
                val headingMultiplier = when (level) {
                    1 -> 1.45f
                    2 -> 1.3f
                    3 -> 1.2f
                    else -> 1.1f
                }
                runCatching {
                    TextPaint(basePaint).apply {
                        textSize = fontSizePx * headingMultiplier
                        typeface = Typeface.create(activeTypeface, Typeface.BOLD)
                    }
                }.getOrNull()
            }
        } else emptyMap()

        val blockSpacingPx = with(density) { 10.dp.roundToPx() }
        val headingSpacingPx = with(density) { 18.dp.roundToPx() }
        val dividerHeightPx = with(density) { 24.dp.roundToPx() }
        val minImageHeightPx = with(density) { 160.dp.roundToPx() }
        val defaultImageHeightPx = (availableHeightPx * 0.55f).toInt().coerceAtLeast(minImageHeightPx)
        // Safety buffer to prevent rounding and subpixel wrapping discrepancies from causing the last line to overflow
        val bottomSafetyBufferPx = (fontSizePx * 0.35f).toInt().coerceAtLeast(6)

        fun startNewPage() {
            if (currentSubpageBlocks.isNotEmpty()) {
                subpages.add(currentSubpageBlocks)
                currentSubpageBlocks = mutableListOf()
            }
            remainingHeightPx = availableHeightPx
        }

        for (block in blocks) {
            when (block) {
                is EpubBlock.DividerBlock -> {
                    if (remainingHeightPx < dividerHeightPx && currentSubpageBlocks.isNotEmpty()) {
                        startNewPage()
                    }
                    currentSubpageBlocks.add(block)
                    remainingHeightPx -= dividerHeightPx
                }

                is EpubBlock.ImageBlock -> {
                    val requiredHeight = defaultImageHeightPx + blockSpacingPx
                    if (remainingHeightPx < requiredHeight && currentSubpageBlocks.isNotEmpty()) {
                        startNewPage()
                    }
                    currentSubpageBlocks.add(block)
                    remainingHeightPx -= requiredHeight
                }

                is EpubBlock.TextBlock -> {
                    val effectiveFontSize = if (block.isHeading) {
                        val headingMultiplier = when (block.headingLevel) {
                            1 -> 1.45f
                            2 -> 1.3f
                            3 -> 1.2f
                            else -> 1.1f
                        }
                        fontSizePx * headingMultiplier
                    } else {
                        fontSizePx
                    }
                    val paint = when {
                        block.isHeading -> headingPaints[block.headingLevel] ?: basePaint
                        block.isQuote -> quotePaint ?: basePaint
                        else -> basePaint
                    }

                    val extraSpacing = if (block.isHeading) headingSpacingPx else blockSpacingPx
                    var remainingAnnotated = block.text

                    while (remainingAnnotated.text.isNotEmpty()) {
                        val textStr = remainingAnnotated.text
                        val layout = measureText(textStr, paint, availableWidthPx, effectiveFontSize, lineHeightMultiplier)
                        val totalLines = layout.lineCount

                        if (totalLines == 0) break

                        val totalHeight = layout.height

                        // If the whole text block fits on the current page with safety margin
                        val effectiveRemaining = if (currentSubpageBlocks.isEmpty()) remainingHeightPx else remainingHeightPx - bottomSafetyBufferPx
                        if (totalHeight + extraSpacing <= effectiveRemaining) {
                            currentSubpageBlocks.add(
                                block.copy(text = remainingAnnotated)
                            )
                            remainingHeightPx -= (totalHeight + extraSpacing)
                            break
                        }

                        // Text doesn't fit entirely; find how many lines fit on the current page
                        var fittingLine = -1
                        for (line in 0 until totalLines) {
                            val lineBottom = layout.getLineBottom(line)
                            if (lineBottom + extraSpacing <= (remainingHeightPx - bottomSafetyBufferPx)) {
                                fittingLine = line
                            } else {
                                break
                            }
                        }

                        if (fittingLine >= 0) {
                            val splitIndex = layout.getLineEnd(fittingLine).coerceIn(0, textStr.length)
                            if (splitIndex > 0) {
                                val fittingChunk = remainingAnnotated.subSequence(0, splitIndex)
                                currentSubpageBlocks.add(
                                    block.copy(text = fittingChunk)
                                )
                                var nextStart = splitIndex
                                while (nextStart < textStr.length && textStr[nextStart].isWhitespace() && textStr[nextStart] != '\n') {
                                    nextStart++
                                }
                                remainingAnnotated = if (nextStart < textStr.length) {
                                    remainingAnnotated.subSequence(nextStart, textStr.length)
                                } else {
                                    AnnotatedString("")
                                }
                            }
                            startNewPage()
                        } else {
                            // If not even a single line fits and there's already content on the page, start a new page
                            if (currentSubpageBlocks.isNotEmpty()) {
                                startNewPage()
                            } else {
                                // First item on an empty page doesn't fit completely; take at least 1 line
                                val splitIndex = layout.getLineEnd(0).coerceIn(0, textStr.length)
                                val fittingChunk = remainingAnnotated.subSequence(0, splitIndex)
                                currentSubpageBlocks.add(
                                    block.copy(text = fittingChunk)
                                )
                                var nextStart = splitIndex
                                while (nextStart < textStr.length && textStr[nextStart].isWhitespace() && textStr[nextStart] != '\n') {
                                    nextStart++
                                }
                                remainingAnnotated = if (nextStart < textStr.length) {
                                    remainingAnnotated.subSequence(nextStart, textStr.length)
                                } else {
                                    AnnotatedString("")
                                }
                                startNewPage()
                            }
                        }
                    }
                }
            }
        }

        if (currentSubpageBlocks.isNotEmpty()) {
            subpages.add(currentSubpageBlocks)
        }

        if (subpages.isEmpty()) {
            subpages.add(mutableListOf())
        }

        val totalSubpages = subpages.size
        return subpages.mapIndexed { index, pageBlocks ->
            EpubSubpage(
                spineIndex = spineIndex,
                subpageIndex = index,
                totalSubpagesInSpine = totalSubpages,
                blocks = pageBlocks,
                progressRatio = if (totalSubpages > 1) index.toFloat() / (totalSubpages - 1).toFloat() else 0f
            )
        }
    }

    private interface TextLayoutResult {
        val lineCount: Int
        val height: Int
        fun getLineBottom(line: Int): Int
        fun getLineEnd(line: Int): Int
        fun getLineStart(line: Int): Int
    }

    private fun measureText(
        text: CharSequence,
        paint: TextPaint?,
        width: Int,
        fontSizePx: Float,
        lineSpacingMultiplier: Float
    ): TextLayoutResult {
        if (paint != null) {
            val result = runCatching {
                val sl = buildStaticLayout(text, paint, width, lineSpacingMultiplier)
                object : TextLayoutResult {
                    override val lineCount: Int get() = sl.lineCount
                    override val height: Int get() = sl.height
                    override fun getLineBottom(line: Int): Int = sl.getLineBottom(line)
                    override fun getLineEnd(line: Int): Int = sl.getLineEnd(line)
                    override fun getLineStart(line: Int): Int = sl.getLineStart(line)
                }
            }.getOrNull()
            if (result != null) return result
        }
        val avgCharWidth = (fontSizePx * 0.55f).coerceAtLeast(1f)
        val charsPerLine = (width / avgCharWidth).toInt().coerceAtLeast(10)
        val lineHeight = (fontSizePx * lineSpacingMultiplier).toInt().coerceAtLeast(12)
        val length = text.length
        val numLines = ((length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
        return object : TextLayoutResult {
            override val lineCount: Int get() = numLines
            override val height: Int get() = numLines * lineHeight
            override fun getLineBottom(line: Int): Int = (line + 1) * lineHeight
            override fun getLineEnd(line: Int): Int = ((line + 1) * charsPerLine).coerceAtMost(length)
            override fun getLineStart(line: Int): Int = (line * charsPerLine).coerceAtMost(length)
        }
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        lineSpacingMultiplier: Float
    ): StaticLayout {
        val safeWidth = width.coerceAtLeast(10)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                safeWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                0f,
                true
            )
        }
    }

    private fun extractBodyContent(html: String): String {
        val lower = html.lowercase()
        val bodyStart = lower.indexOf("<body")
        val bodyEnd = lower.lastIndexOf("</body>")
        val extracted = if (bodyStart != -1 && bodyEnd != -1 && bodyEnd > bodyStart) {
            val startClose = html.indexOf('>', bodyStart)
            if (startClose != -1) html.substring(startClose + 1, bodyEnd) else html
        } else {
            html
        }
        // Remove style and script tags
        return extracted
            .replace(Regex("(?s)<style.*?</style>"), "")
            .replace(Regex("(?s)<script.*?</script>"), "")
    }

    private fun extractAttribute(attrs: String, attrName: String): String? {
        val pattern = Pattern.compile("""(?i)\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        val matcher = pattern.matcher(attrs)
        if (matcher.find()) {
            return matcher.group(1) ?: matcher.group(2) ?: matcher.group(3)
        }
        return null
    }

    private fun decodeHtmlEntities(input: String): String {
        val matcher = EntityPattern.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val entity = matcher.group(1).orEmpty()
            val rawMatch = matcher.group(0).orEmpty()
            val replacement = when (entity.lowercase()) {
                "nbsp" -> " "
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "mdash" -> "—"
                "ndash" -> "–"
                "hellip" -> "…"
                "ldquo", "rdquo" -> "\""
                "lsquo", "rsquo" -> "'"
                else -> {
                    if (entity.startsWith("#x", ignoreCase = true)) {
                        entity.substring(2).toIntOrNull(16)?.let { Character.toChars(it).joinToString("") } ?: rawMatch
                    } else if (entity.startsWith("#")) {
                        entity.substring(1).toIntOrNull()?.let { Character.toChars(it).joinToString("") } ?: rawMatch
                    } else {
                        rawMatch
                    }
                }
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun removeActiveStyle(list: MutableList<ActiveStyle>, key: Any) {
        val idx = list.indexOfLast { it.key == key }
        if (idx != -1) list.removeAt(idx)
    }

    private data class StyleSpan(
        val style: SpanStyle,
        val start: Int,
        val end: Int
    )

    private data class ActiveStyle(
        val style: SpanStyle,
        val key: Any
    )
}
