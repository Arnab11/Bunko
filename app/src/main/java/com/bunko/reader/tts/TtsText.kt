package com.bunko.reader.tts

import com.bunko.reader.reader.internal.EpubBlock
import com.bunko.reader.reader.internal.EpubSubpage

/**
 * A speakable paragraph extracted from a single [EpubBlock.TextBlock].
 *
 * @param blockIndex index of the block inside [EpubSubpage.blocks] (not filtered).
 */
data class TtsParagraph(
    val blockIndex: Int,
    val text: String
)

/** Word/char highlight inside one text block. Offsets are in that block's plain text. */
data class TtsHighlight(
    val blockIndex: Int,
    val startInBlock: Int,
    val endInBlock: Int
)

private const val PARAGRAPH_SEPARATOR = "\n\n"

/**
 * Extracts speakable paragraphs from a subpage, preserving the block index so
 * spoken offsets can be mapped back for highlight rendering.
 */
fun EpubSubpage.ttsParagraphs(): List<TtsParagraph> {
    val out = mutableListOf<TtsParagraph>()
    blocks.forEachIndexed { index, block ->
        if (block is EpubBlock.TextBlock) {
            // Use the exact rendered string (already trimmed by the paginator) so
            // highlight offsets from onRangeStart map 1:1 back to the block.
            val plain = block.text.text
            if (plain.isNotBlank()) out.add(TtsParagraph(blockIndex = index, text = plain))
        }
    }
    return out
}

/**
 * Combined speakable text plus offset map back to blocks.
 *
 * [combined] is what gets chunked and sent to TTS. [blockRanges] maps each
 * paragraph to its [IntRange] inside [combined] (separator gaps belong to nobody).
 */
data class TtsSession(
    val combined: String,
    val paragraphs: List<TtsParagraph>,
    val blockRanges: List<Pair<Int, IntRange>>
) {
    val isEmpty: Boolean get() = combined.isBlank()

    /** Maps a global offset in [combined] to a [TtsHighlight], or null in separators. */
    fun highlightFor(globalStart: Int, globalEnd: Int): TtsHighlight? {
        for ((blockIndex, range) in blockRanges) {
            if (globalStart in range) {
                val localStart = (globalStart - range.first).coerceAtLeast(0)
                val para = paragraphs.firstOrNull { it.blockIndex == blockIndex } ?: return null
                val localEnd = (globalEnd - range.first).coerceIn(localStart, para.text.length)
                if (localEnd <= localStart) return null
                return TtsHighlight(blockIndex, localStart, localEnd)
            }
        }
        return null
    }
}

fun buildTtsSession(paragraphs: List<TtsParagraph>): TtsSession {
    if (paragraphs.isEmpty()) return TtsSession("", emptyList(), emptyList())
    val sb = StringBuilder()
    val ranges = mutableListOf<Pair<Int, IntRange>>()
    paragraphs.forEachIndexed { i, p ->
        if (i > 0) sb.append(PARAGRAPH_SEPARATOR)
        val start = sb.length
        sb.append(p.text)
        ranges.add(p.blockIndex to (start until sb.length))
    }
    return TtsSession(sb.toString(), paragraphs, ranges)
}

/**
 * Splits long text into TTS-safe chunks, preferring paragraph then sentence boundaries.
 * Keeps exact offsets (no trimming inside) so onRangeStart mapping stays correct.
 */
fun chunkForTts(text: String, maxChunk: Int = 3500): List<Pair<Int, String>> {
    if (text.length <= maxChunk) return listOf(0 to text)
    val out = mutableListOf<Pair<Int, String>>()
    var offset = 0
    while (offset < text.length) {
        val end = (offset + maxChunk).coerceAtMost(text.length)
        if (end >= text.length) {
            out.add(offset to text.substring(offset))
            break
        }
        // Prefer a paragraph break, then sentence end, then space.
        val window = text.substring(offset, end)
        val paraBreak = window.lastIndexOf("\n\n")
        val sentenceBreak = listOf(". ", "! ", "? ", "。", "। ").maxOfOrNull { window.lastIndexOf(it) } ?: -1
        val spaceBreak = window.lastIndexOf(' ')
        val cut = when {
            paraBreak > maxChunk / 2 -> paraBreak + 2
            sentenceBreak > maxChunk / 2 -> sentenceBreak + 1
            spaceBreak > maxChunk / 2 -> spaceBreak + 1
            else -> maxChunk
        }
        out.add(offset to text.substring(offset, offset + cut))
        offset += cut
    }
    return out
}
