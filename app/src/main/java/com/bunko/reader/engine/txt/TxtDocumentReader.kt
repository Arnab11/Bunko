package com.bunko.reader.engine.txt

import android.content.Context
import com.bunko.reader.engine.model.ReflowSpine
import com.bunko.reader.engine.model.TocItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ParsedTxt(
    val title: String,
    val spines: List<ReflowSpine>,
    val tableOfContents: List<TocItem>
)

object TxtDocumentReader {

    private val ChapterRegex = Regex("(?i)^\\s*(chapter\\s+\\d+|chapter\\s+[ivxlcdm]+|part\\s+\\d+|book\\s+\\d+|act\\s+\\d+|scene\\s+\\d+|#+\\s+.+)\\s*$", RegexOption.MULTILINE)

    suspend fun parseTxt(context: Context? = null, file: File): ParsedTxt = withContext(Dispatchers.IO) {
        val title = file.nameWithoutExtension.replace('_', ' ')
        val fullText = file.bufferedReader().use { it.readText() }

        val spines = mutableListOf<ReflowSpine>()
        val toc = mutableListOf<TocItem>()

        val matches = ChapterRegex.findAll(fullText).toList()
        if (matches.size > 1) {
            var lastIdx = 0
            var currentTitle = "Prologue"

            matches.forEachIndexed { i, match ->
                if (match.range.first > lastIdx) {
                    val sectionText = fullText.substring(lastIdx, match.range.first).trim()
                    if (sectionText.isNotBlank()) {
                        val spineIdx = spines.size
                        spines.add(
                            ReflowSpine(
                                id = "txt_section_$spineIdx",
                                spineIndex = spineIdx,
                                title = currentTitle,
                                rawHtml = textToHtml(currentTitle, sectionText)
                            )
                        )
                        toc.add(TocItem(currentTitle, spineIdx))
                    }
                }
                currentTitle = match.value.trim().removePrefix("#").trim()
                lastIdx = match.range.first
            }

            if (lastIdx < fullText.length) {
                val sectionText = fullText.substring(lastIdx).trim()
                if (sectionText.isNotBlank()) {
                    val spineIdx = spines.size
                    spines.add(
                        ReflowSpine(
                            id = "txt_section_$spineIdx",
                            spineIndex = spineIdx,
                            title = currentTitle,
                            rawHtml = textToHtml(currentTitle, sectionText)
                        )
                    )
                    toc.add(TocItem(currentTitle, spineIdx))
                }
            }
        }

        if (spines.isEmpty()) {
            // Split into 50k character blocks if very large
            val chunkSize = 50_000
            if (fullText.length > chunkSize) {
                val chunks = fullText.chunked(chunkSize)
                chunks.forEachIndexed { idx, chunk ->
                    val chunkTitle = "Part ${idx + 1}"
                    spines.add(
                        ReflowSpine(
                            id = "txt_chunk_$idx",
                            spineIndex = idx,
                            title = chunkTitle,
                            rawHtml = textToHtml(chunkTitle, chunk)
                        )
                    )
                    toc.add(TocItem(chunkTitle, idx))
                }
            } else {
                spines.add(
                    ReflowSpine(
                        id = "txt_chunk_0",
                        spineIndex = 0,
                        title = title,
                        rawHtml = textToHtml(title, fullText)
                    )
                )
                toc.add(TocItem(title, 0))
            }
        }

        ParsedTxt(
            title = title,
            spines = spines,
            tableOfContents = toc
        )
    }

    private fun textToHtml(title: String, text: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        
        val paragraphs = escaped.split(Regex("(\r?\n){2,}"))
            .map { it.trim().replace("\n", "<br/>") }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>$it</p>" }

        return "<html><head><title>$title</title></head><body><h2>$title</h2>$paragraphs</body></html>"
    }
}
