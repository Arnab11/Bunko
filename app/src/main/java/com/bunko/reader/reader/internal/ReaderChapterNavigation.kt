package com.bunko.reader.reader.internal

import com.bunko.reader.ChapterDto
import com.bunko.reader.VolumeDto
import com.bunko.reader.series.internal.displayName
import com.bunko.reader.series.internal.displayTitle

/** A non-special chapter in the same order as the series screen. */
internal data class ReaderChapterEntry(
    val chapterId: Int,
    val volumeId: Int,
    val volumeName: String?,
    val chapterName: String
) {
    val displayName: String
        get() = listOfNotNull(volumeName, chapterName).joinToString(" / ")
}

internal data class ReaderChapterNeighbors(
    val previous: ReaderChapterEntry?,
    val next: ReaderChapterEntry?
)

/** Includes specials so a directly opened special can still be named in the reader menu. */
internal fun readerChapterEntry(
    volumes: List<VolumeDto>,
    chapterId: Int
): ReaderChapterEntry? = volumes.firstNotNullOfOrNull { volume ->
    volume.chapters.firstOrNull { it.id == chapterId }?.let { volume.readerEntry(it) }
}

/** Internal to reader, not for external use. */
internal fun readerChapterSequence(volumes: List<VolumeDto>): List<ReaderChapterEntry> =
    volumes.flatMap { volume ->
        volume.chapters
            .asSequence()
            .filterNot { it.isSpecial }
            .map(volume::readerEntry)
            .toList()
    }

/** Internal to reader, not for external use. */
internal fun readerChapterNeighbors(
    chapters: List<ReaderChapterEntry>,
    chapterId: Int
): ReaderChapterNeighbors {
    val index = chapters.indexOfFirst { it.chapterId == chapterId }
    if (index < 0) return ReaderChapterNeighbors(previous = null, next = null)
    return ReaderChapterNeighbors(
        previous = chapters.getOrNull(index - 1),
        next = chapters.getOrNull(index + 1)
    )
}

private fun VolumeDto.readerEntry(chapter: ChapterDto): ReaderChapterEntry =
    ReaderChapterEntry(
        chapterId = chapter.id,
        volumeId = chapter.volumeId ?: id,
        volumeName = displayName(),
        chapterName = chapter.displayTitle()
    )
