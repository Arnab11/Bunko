package karacken.curl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Portrait Book-turn geometry invariants.
 *
 * The inactive previous (LEFT) page must park fully off-screen (X <= 0) so it
 * never peeks over the current page as a "border" containing previous content —
 * most visible in the tail of a right-to-left (NEXT) turn once the fold has
 * exited and only the parked page remains beside the new page.
 */
class PlayLikeCurlGeometryTest {

    @Test
    fun parkedInactiveLeftPageNeverOverlapsViewport() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.LEFT, 100, 150, PageOrientation.PORTRAIT
        )
        PlayLikeCurlGeometry.update(
            geometry, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, false
        )

        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions
        for (row in 0..grid) {
            for (col in 0..grid) {
                val offset = 3 * (row * (grid + 1) + col)
                assertTrue(
                    "parked left page must stay off-screen (x <= 0), got ${pos[offset]}",
                    pos[offset] <= 0f
                )
            }
        }
    }

    @Test
    fun flatFrontPageCoversFullViewport() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )
        PlayLikeCurlGeometry.update(geometry, PlayLikeCurlModel.GRID.toFloat(), false)

        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions
        for (row in 0..grid) {
            val leftOffset = 3 * (row * (grid + 1))
            val rightOffset = 3 * (row * (grid + 1) + grid)
            assertEquals(0f, pos[leftOffset], TOLERANCE)
            assertEquals(1f, pos[rightOffset], TOLERANCE)
        }
    }

    @Test
    fun leftPageLandsFullyOnScreenAtPreviousTurnEnd() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.LEFT, 100, 150, PageOrientation.PORTRAIT
        )
        PlayLikeCurlGeometry.update(geometry, PlayLikeCurlModel.GRID.toFloat(), true)

        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions
        val midRow = grid / 2
        val leftOffset = 3 * (midRow * (grid + 1))
        val rightOffset = 3 * (midRow * (grid + 1) + grid)
        assertEquals(0f, pos[leftOffset], TOLERANCE)
        assertEquals(1f, pos[rightOffset], TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
