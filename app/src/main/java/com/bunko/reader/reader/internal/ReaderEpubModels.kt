package com.bunko.reader.reader.internal

import androidx.compose.ui.text.AnnotatedString

sealed interface EpubBlock {
    data class TextBlock(
        val text: AnnotatedString,
        val isHeading: Boolean = false,
        val headingLevel: Int = 0,
        val isQuote: Boolean = false,
        val isPreformatted: Boolean = false
    ) : EpubBlock

    data class ImageBlock(
        val url: String,
        val alt: String? = null
    ) : EpubBlock

    object DividerBlock : EpubBlock
}

data class EpubSubpage(
    val spineIndex: Int,
    val subpageIndex: Int,
    val totalSubpagesInSpine: Int,
    val blocks: List<EpubBlock>,
    val progressRatio: Float = 0f
)

data class EpubChapterData(
    val chapterId: Int,
    val spineCount: Int,
    val subpages: List<EpubSubpage>
)
