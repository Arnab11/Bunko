package com.bunko.reader.reader

import com.bunko.reader.ChapterDto
import com.bunko.reader.VolumeDto
import com.bunko.reader.reader.internal.readerChapterNeighbors
import com.bunko.reader.reader.internal.readerChapterEntry
import com.bunko.reader.reader.internal.readerChapterSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChapterNavigationTest {

    @Test
    fun sequenceMatchesVolumeThenChapterOrderAndExcludesSpecials() {
        val volumes = listOf(
            VolumeDto(
                id = 10,
                name = "1",
                chapters = listOf(
                    ChapterDto(id = 101, title = "One"),
                    ChapterDto(id = 199, title = "Bonus", isSpecial = true)
                )
            ),
            VolumeDto(
                id = 20,
                name = "2",
                chapters = listOf(
                    ChapterDto(id = 201, title = "Two", volumeId = 21),
                    ChapterDto(id = 202, title = "Three")
                )
            )
        )

        val sequence = readerChapterSequence(volumes)

        assertEquals(listOf(101, 201, 202), sequence.map { it.chapterId })
        assertEquals(listOf(10, 21, 20), sequence.map { it.volumeId })
        assertEquals("Volume 1 / One", sequence.first().displayName)
    }

    @Test
    fun neighborsAreNullAtSequenceEnds() {
        val sequence = readerChapterSequence(
            listOf(
                VolumeDto(
                    id = 10,
                    chapters = listOf(
                        ChapterDto(id = 1, title = "One"),
                        ChapterDto(id = 2, title = "Two"),
                        ChapterDto(id = 3, title = "Three")
                    )
                )
            )
        )

        assertNull(readerChapterNeighbors(sequence, 1).previous)
        assertEquals(2, readerChapterNeighbors(sequence, 1).next?.chapterId)
        assertEquals(2, readerChapterNeighbors(sequence, 3).previous?.chapterId)
        assertNull(readerChapterNeighbors(sequence, 3).next)
    }

    @Test
    fun specialOpenedDirectlyHasNoSequenceNeighbors() {
        val sequence = readerChapterSequence(
            listOf(
                VolumeDto(
                    id = 10,
                    chapters = listOf(
                        ChapterDto(id = 1, title = "One"),
                        ChapterDto(id = 2, title = "Bonus", isSpecial = true),
                        ChapterDto(id = 3, title = "Three")
                    )
                )
            )
        )

        val neighbors = readerChapterNeighbors(sequence, chapterId = 2)

        assertNull(neighbors.previous)
        assertNull(neighbors.next)
    }

    @Test
    fun specialCanStillBeNamedOutsideTheSequence() {
        val volumes = listOf(
            VolumeDto(
                id = 10,
                name = "Extras",
                chapters = listOf(ChapterDto(id = 2, title = "Bonus", isSpecial = true))
            )
        )

        val entry = readerChapterEntry(volumes, chapterId = 2)

        assertEquals("Extras / Bonus", entry?.displayName)
    }
}
