package com.bunko.reader.reader.internal

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.regex.Pattern

object ReaderEpubPaginator {

    private val EntityPattern = Pattern.compile("&(#?[a-zA-Z0-9]+);")

    private fun extractImageDimensions(url: String, element: org.jsoup.nodes.Element?): Triple<Int, Int, Float> {
        var width = 0
        var height = 0

        // 1. Try decoding local file bounds (fast, header only)
        val cleanPath = url.removePrefix("file://")
        val file = File(cleanPath)
        if (file.exists() && file.isFile && file.length() > 0L) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                width = opts.outWidth
                height = opts.outHeight
            }
        }

        // 2. Check HTML/SVG tag attributes if file bounds couldn't be decoded
        if ((width <= 0 || height <= 0) && element != null) {
            val widthAttr = element.attr("width").ifEmpty {
                element.parent()?.takeIf { it.tagName().equals("svg", ignoreCase = true) }?.attr("width") ?: ""
            }
            val heightAttr = element.attr("height").ifEmpty {
                element.parent()?.takeIf { it.tagName().equals("svg", ignoreCase = true) }?.attr("height") ?: ""
            }

            val parsedW = widthAttr.filter { it.isDigit() }.toIntOrNull() ?: 0
            val parsedH = heightAttr.filter { it.isDigit() }.toIntOrNull() ?: 0
            if (parsedW > 0) width = parsedW
            if (parsedH > 0) height = parsedH

            if (width <= 0 || height <= 0) {
                val viewBox = element.attr("viewBox").ifEmpty {
                    element.parent()?.takeIf { it.tagName().equals("svg", ignoreCase = true) }?.attr("viewBox") ?: ""
                }
                if (viewBox.isNotBlank()) {
                    val parts = viewBox.trim().split(Regex("[\\s,]+")).mapNotNull { it.toIntOrNull() }
                    if (parts.size >= 4 && parts[2] > 0 && parts[3] > 0) {
                        width = parts[2]
                        height = parts[3]
                    }
                }
            }
        }

        val aspectRatio = if (width > 0 && height > 0) height.toFloat() / width.toFloat() else 0f
        return Triple(width, height, aspectRatio)
    }

    private fun trimAnnotatedString(annotated: AnnotatedString): AnnotatedString {
        val text = annotated.text
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start == -1) return AnnotatedString("")
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        return annotated.subSequence(start, end)
    }

    fun parseHtmlToBlocks(
        html: String,
        chapterId: Int,
        baseUrl: String,
        apiKey: String,
        bookResourceUrlBuilder: (String, String, Int, String) -> String
    ): List<EpubBlock> {
        val blocks = mutableListOf<EpubBlock>()
        if (html.isBlank()) return blocks

        try {
            val doc = org.jsoup.Jsoup.parse(html)
            val body = doc.body() ?: return blocks

            fun resolveImageUrl(src: String): String {
                return if (src.startsWith("/") || src.startsWith("http://") || src.startsWith("https://") || src.startsWith("file://") || src.startsWith("data:")) {
                    src
                } else {
                    bookResourceUrlBuilder(baseUrl, apiKey, chapterId, src)
                }
            }

            fun createImageBlock(el: org.jsoup.nodes.Element): EpubBlock.ImageBlock? {
                val isSvgImage = el.tagName().equals("image", ignoreCase = true)
                val rawSrc = if (isSvgImage) {
                    el.attr("xlink:href").ifEmpty { el.attr("href") }
                } else {
                    el.attr("src")
                }
                if (rawSrc.isBlank()) return null
                val fullUrl = resolveImageUrl(rawSrc)
                val alt = el.attr("alt").ifEmpty { null }
                val (w, h, ar) = extractImageDimensions(fullUrl, el)
                return EpubBlock.ImageBlock(
                    url = fullUrl,
                    alt = alt,
                    intrinsicWidth = w,
                    intrinsicHeight = h,
                    aspectRatio = ar
                )
            }

            fun processInlineNode(node: org.jsoup.nodes.Node, currentStyles: List<SpanStyle>, builder: AnnotatedString.Builder) {
                when (node) {
                    is org.jsoup.nodes.TextNode -> {
                        val text = decodeHtmlEntities(node.text())
                        if (text.isNotEmpty()) {
                            val start = builder.length
                            builder.append(text)
                            currentStyles.forEach { style ->
                                builder.addStyle(style, start, start + text.length)
                            }
                        }
                    }
                    is org.jsoup.nodes.Element -> {
                        val tag = node.tagName().lowercase()
                        val newStyles = currentStyles.toMutableList()
                        when (tag) {
                            "b", "strong" -> newStyles.add(SpanStyle(fontWeight = FontWeight.Bold))
                            "i", "em" -> newStyles.add(SpanStyle(fontStyle = FontStyle.Italic))
                            "u" -> newStyles.add(SpanStyle(textDecoration = TextDecoration.Underline))
                            "s", "strike", "del" -> newStyles.add(SpanStyle(textDecoration = TextDecoration.LineThrough))
                            "code", "pre" -> newStyles.add(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                            "sup" -> newStyles.add(SpanStyle(baselineShift = BaselineShift.Superscript))
                            "sub" -> newStyles.add(SpanStyle(baselineShift = BaselineShift.Subscript))
                            "ruby" -> {
                                val rb = node.select("rb, text()").firstOrNull()?.toString() ?: node.ownText()
                                val rt = node.select("rt").firstOrNull()?.text() ?: ""
                                val rubyFormatted = if (rt.isNotEmpty()) "$rb ($rt)" else rb
                                val start = builder.length
                                builder.append(rubyFormatted)
                                currentStyles.forEach { style ->
                                    builder.addStyle(style, start, start + rubyFormatted.length)
                                }
                                return
                            }
                        }

                        if (tag == "br") {
                            builder.append("\n")
                        } else if (tag != "img" && tag != "image" && tag != "svg") {
                            node.childNodes().forEach { child ->
                                processInlineNode(child, newStyles, builder)
                            }
                        }
                    }
                }
            }

            fun processContainer(
                container: org.jsoup.nodes.Element,
                isHeading: Boolean = false,
                headingLevel: Int = 0,
                isQuote: Boolean = false,
                initialStyles: List<SpanStyle> = emptyList()
            ) {
                var runningBuilder = AnnotatedString.Builder()

                fun flushRunningText(trimEdges: Boolean = false) {
                    var str = runningBuilder.toAnnotatedString()
                    if (trimEdges) {
                        str = trimAnnotatedString(str)
                    }
                    if (str.text.isNotBlank()) {
                        blocks.add(
                            EpubBlock.TextBlock(
                                text = str,
                                isHeading = isHeading,
                                headingLevel = headingLevel,
                                isQuote = isQuote
                            )
                        )
                    }
                    runningBuilder = AnnotatedString.Builder()
                }

                container.childNodes().forEach { child ->
                    when (child) {
                        is org.jsoup.nodes.TextNode -> {
                            val txt = child.text()
                            if (txt.isNotBlank() || (runningBuilder.length > 0 && txt.isNotEmpty())) {
                                processInlineNode(child, initialStyles, runningBuilder)
                            }
                        }
                        is org.jsoup.nodes.Element -> {
                            val tag = child.tagName().lowercase()
                            when (tag) {
                                "img", "image" -> {
                                    flushRunningText()
                                    createImageBlock(child)?.let { blocks.add(it) }
                                }
                                "svg" -> {
                                    flushRunningText()
                                    val imageChild = child.select("image").firstOrNull()
                                    if (imageChild != null) {
                                        createImageBlock(imageChild)?.let { blocks.add(it) }
                                    }
                                }
                                "hr" -> {
                                    flushRunningText(trimEdges = true)
                                    blocks.add(EpubBlock.DividerBlock)
                                }
                                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                    flushRunningText(trimEdges = true)
                                    val lvl = tag.substring(1).toIntOrNull() ?: 1
                                    val builder = AnnotatedString.Builder()
                                    processInlineNode(child, listOf(SpanStyle(fontWeight = FontWeight.Bold)), builder)
                                    val trimmed = trimAnnotatedString(builder.toAnnotatedString())
                                    if (trimmed.text.isNotBlank()) {
                                        blocks.add(EpubBlock.TextBlock(text = trimmed, isHeading = true, headingLevel = lvl))
                                    }
                                }
                                "blockquote" -> {
                                    flushRunningText(trimEdges = true)
                                    val hasSpecial = child.select("img, image, svg, hr").isNotEmpty()
                                    if (hasSpecial) {
                                        processContainer(child, isQuote = true, initialStyles = listOf(SpanStyle(fontStyle = FontStyle.Italic)))
                                    } else {
                                        val builder = AnnotatedString.Builder()
                                        processInlineNode(child, listOf(SpanStyle(fontStyle = FontStyle.Italic)), builder)
                                        val trimmed = trimAnnotatedString(builder.toAnnotatedString())
                                        if (trimmed.text.isNotBlank()) {
                                            blocks.add(EpubBlock.TextBlock(text = trimmed, isQuote = true))
                                        }
                                    }
                                }
                                "ul", "ol" -> {
                                    flushRunningText(trimEdges = true)
                                    child.select("> li").forEachIndexed { idx, li ->
                                        val bullet = if (tag == "ol") "${idx + 1}. " else "• "
                                        val liBuilder = AnnotatedString.Builder(bullet)
                                        processInlineNode(li, emptyList(), liBuilder)
                                        val liStr = trimAnnotatedString(liBuilder.toAnnotatedString())
                                        if (liStr.text.isNotBlank()) {
                                            blocks.add(EpubBlock.TextBlock(text = liStr))
                                        }
                                        li.select("img, image").forEach { liImg ->
                                            createImageBlock(liImg)?.let { blocks.add(it) }
                                        }
                                    }
                                }
                                "figure" -> {
                                    flushRunningText(trimEdges = true)
                                    val figImg = child.select("img, image, svg image").firstOrNull()
                                    if (figImg != null) {
                                        createImageBlock(figImg)?.let { blocks.add(it) }
                                    }
                                    val caption = child.select("figcaption").firstOrNull()
                                    if (caption != null) {
                                        val capBuilder = AnnotatedString.Builder()
                                        processInlineNode(caption, listOf(SpanStyle(fontStyle = FontStyle.Italic)), capBuilder)
                                        val capStr = trimAnnotatedString(capBuilder.toAnnotatedString())
                                        if (capStr.text.isNotBlank()) {
                                            blocks.add(EpubBlock.TextBlock(text = capStr, isQuote = true))
                                        }
                                    }
                                }
                                "p", "div", "section", "article", "main", "center" -> {
                                    val hasSpecial = child.select("img, image, svg, hr, h1, h2, h3, h4, h5, h6, blockquote, figure, ul, ol").isNotEmpty()
                                    if (hasSpecial) {
                                        flushRunningText(trimEdges = true)
                                        processContainer(child, isHeading, headingLevel, isQuote, initialStyles)
                                    } else {
                                        flushRunningText(trimEdges = true)
                                        val pBuilder = AnnotatedString.Builder()
                                        processInlineNode(child, initialStyles, pBuilder)
                                        val pStr = trimAnnotatedString(pBuilder.toAnnotatedString())
                                        if (pStr.text.isNotBlank()) {
                                            blocks.add(
                                                EpubBlock.TextBlock(
                                                    text = pStr,
                                                    isHeading = isHeading,
                                                    headingLevel = headingLevel,
                                                    isQuote = isQuote
                                                )
                                            )
                                        }
                                    }
                                }
                                "a", "span" -> {
                                    val hasSpecial = child.select("img, image, svg").isNotEmpty()
                                    if (hasSpecial) {
                                        flushRunningText()
                                        processContainer(child, isHeading, headingLevel, isQuote, initialStyles)
                                    } else {
                                        processInlineNode(child, initialStyles, runningBuilder)
                                    }
                                }
                                else -> {
                                    processInlineNode(child, initialStyles, runningBuilder)
                                }
                            }
                        }
                    }
                }

                flushRunningText(trimEdges = true)
            }

            processContainer(body)

        } catch (t: Throwable) {
            com.bunko.reader.BunkoLog.w("Failed to parse HTML to blocks", t)
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
        val minImageHeightPx = with(density) { 120.dp.roundToPx() }
        val maxImageHeightPx = (availableHeightPx - with(density) { 20.dp.roundToPx() }).coerceAtLeast(minImageHeightPx)
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
                    val isStandaloneOrFullPage = run {
                        if (blocks.size == 1) true
                        else if (block.aspectRatio >= 1.15f && (availableWidthPx * block.aspectRatio) >= (availableHeightPx * 0.7f)) true
                        else false
                    }

                    val computedImageHeightPx = when {
                        isStandaloneOrFullPage -> availableHeightPx
                        block.aspectRatio > 0f -> {
                            val scaled = (availableWidthPx * block.aspectRatio).toInt()
                            scaled.coerceIn(minImageHeightPx, maxImageHeightPx)
                        }
                        block.intrinsicHeight > 0 && block.intrinsicWidth > 0 -> {
                            val scaled = (block.intrinsicHeight.toFloat() / block.intrinsicWidth.toFloat() * availableWidthPx).toInt()
                            scaled.coerceIn(minImageHeightPx, maxImageHeightPx)
                        }
                        else -> {
                            (availableHeightPx * 0.5f).toInt().coerceIn(minImageHeightPx, maxImageHeightPx)
                        }
                    }

                    if (isStandaloneOrFullPage) {
                        if (currentSubpageBlocks.isNotEmpty()) {
                            startNewPage()
                        }
                        currentSubpageBlocks.add(block)
                        startNewPage()
                    } else {
                        val requiredHeight = computedImageHeightPx + blockSpacingPx
                        if (remainingHeightPx < requiredHeight && currentSubpageBlocks.isNotEmpty()) {
                            startNewPage()
                        }
                        currentSubpageBlocks.add(block)
                        remainingHeightPx -= requiredHeight
                    }
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
                        val effectiveRemaining = if (currentSubpageBlocks.isEmpty()) remainingHeightPx else remainingHeightPx - bottomSafetyBufferPx
                        if (totalHeight + extraSpacing <= effectiveRemaining) {
                            currentSubpageBlocks.add(
                                block.copy(text = remainingAnnotated)
                            )
                            remainingHeightPx -= (totalHeight + extraSpacing)
                            break
                        }

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
                            if (currentSubpageBlocks.isNotEmpty()) {
                                startNewPage()
                            } else {
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
}
