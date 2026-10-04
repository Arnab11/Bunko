package com.bunko.reader.reader

import com.bunko.reader.reader.internal.PlayCurlHalf
import com.bunko.reader.reader.internal.playCurlHalfLogicalId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayCurlHalfIdTest {

    @Test
    fun halvesOfOneCenteredSingleHaveDistinctIds() {
        // A wide foldout renders as one centered sheet split at the spine. Both halves
        // share the source page index, so the side suffix is the only thing keeping
        // their GL textures apart.
        assertNotEquals(
            playCurlHalfLogicalId(5, PlayCurlHalf.Left),
            playCurlHalfLogicalId(5, PlayCurlHalf.Right)
        )
    }

    @Test
    fun halfIdsAreStable() {
        assertEquals(
            playCurlHalfLogicalId(5, PlayCurlHalf.Left),
            playCurlHalfLogicalId(5, PlayCurlHalf.Left)
        )
        assertEquals(
            playCurlHalfLogicalId(7, PlayCurlHalf.Right),
            playCurlHalfLogicalId(7, PlayCurlHalf.Right)
        )
    }

    @Test
    fun neighboringSpreadHalvesDoNotCollide() {
        val ids = setOf(
            playCurlHalfLogicalId(5, PlayCurlHalf.Left),
            playCurlHalfLogicalId(6, PlayCurlHalf.Right),
            playCurlHalfLogicalId(7, PlayCurlHalf.Left),
            playCurlHalfLogicalId(7, PlayCurlHalf.Right)
        )
        assertEquals(4, ids.size)
    }
}
