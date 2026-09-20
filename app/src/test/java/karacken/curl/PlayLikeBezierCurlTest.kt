package karacken.curl

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayLikeBezierCurlTest {

    @Before
    fun setUp() {
        PlayLikeBezierCurl.resetTouchTilt()
    }

    @After
    fun tearDown() {
        PlayLikeBezierCurl.resetTouchTilt()
    }

    @Test
    fun inactivePageRemainsFlat() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )

        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID.toFloat(), false)

        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions
        for (row in 0..grid) {
            for (col in 0..grid) {
                val offset = 3 * (row * (grid + 1) + col)
                assertEquals(col / grid.toFloat(), pos[offset], TOLERANCE)
                assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[offset + 2], TOLERANCE)
            }
        }
    }

    @Test
    fun forwardCurlStartsFlatAndCompletesTurned() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )

        // Progress = 0 (unturned)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID.toFloat(), true)
        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions

        val midRow = grid / 2
        val rightCol = grid
        val rightOffset = 3 * (midRow * (grid + 1) + rightCol)
        assertEquals(1.0f, pos[rightOffset], TOLERANCE)
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[rightOffset + 2], TOLERANCE)

        // Progress = 1 (fully turned past spine to left)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, true)
        assertTrue(pos[rightOffset] <= 0f)
        assertEquals(PlayLikeCurlModel.LEFT_DEPTH, pos[rightOffset + 2], TOLERANCE)
    }

    @Test
    fun forwardCurlMidTurnPreservesSpineFlatnessAndLiftsFold() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )

        val midPosition = (PlayLikeCurlModel.GRID + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f
        PlayLikeBezierCurl.update(geometry, midPosition, true)

        val grid = PlayLikeCurlModel.GRID
        val pos = geometry.positions
        val midRow = grid / 2

        // Spine edge (col = 0) must remain anchored flat at X = 0
        val spineOffset = 3 * (midRow * (grid + 1) + 0)
        assertEquals(0f, pos[spineOffset], TOLERANCE)

        // Fold crest must achieve visible 3D elevation
        var maxZ = Float.NEGATIVE_INFINITY
        for (col in 0..grid) {
            val offset = 3 * (midRow * (grid + 1) + col)
            val z = pos[offset + 2]
            if (z > maxZ) {
                maxZ = z
            }
        }

        assertTrue("Fold crest must lift in 3D above book plane, got: $maxZ", maxZ > 0.05f)
        assertTrue("Max Z must not exceed peak elevation", maxZ <= PlayLikeBezierCurl.MAX_CURL_HEIGHT + 0.05f)
    }

    @Test
    fun backwardCurlStartsTurnedAndCompletesFlat() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.LEFT, 100, 150, PageOrientation.PORTRAIT
        )

        val grid = PlayLikeCurlModel.GRID
        val midRow = grid / 2
        val rightCol = grid
        val offset = 3 * (midRow * (grid + 1) + rightCol)

        // Progress = 0 (on left side)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, true)
        var pos = geometry.positions
        assertTrue(pos[offset] <= 0f)

        // Progress = 1 (turned back flat on right)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID.toFloat(), true)
        pos = geometry.positions
        assertEquals(1.0f, pos[offset], TOLERANCE)
        assertEquals(PlayLikeCurlModel.LEFT_DEPTH, pos[offset + 2], TOLERANCE)
    }

    @Test
    fun touchTiltCreatesConicalCurl() {
        val geometryBottomTilt = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )
        val geometryTopTilt = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT
        )

        val midPos = (PlayLikeCurlModel.GRID + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f
        val grid = PlayLikeCurlModel.GRID

        // Bottom pull (thumb pull): bottom corner leads (smaller X)
        PlayLikeBezierCurl.setTouchTilt(0.8f)
        PlayLikeBezierCurl.update(geometryBottomTilt, midPos, true)

        // Top pull: top corner leads (smaller X)
        PlayLikeBezierCurl.setTouchTilt(-0.8f)
        PlayLikeBezierCurl.update(geometryTopTilt, midPos, true)

        val bottomTiltPos = geometryBottomTilt.positions
        val topTiltPos = geometryTopTilt.positions

        val topEdgeOffset = 3 * (0 * (grid + 1) + grid)
        val bottomEdgeOffset = 3 * (grid * (grid + 1) + grid)

        val bottomXWithBottomTilt = bottomTiltPos[bottomEdgeOffset]
        val topXWithBottomTilt = bottomTiltPos[topEdgeOffset]
        assertTrue("When pulling bottom corner, bottom X should lead top X", bottomXWithBottomTilt < topXWithBottomTilt)

        val bottomXWithTopTilt = topTiltPos[bottomEdgeOffset]
        val topXWithTopTilt = topTiltPos[topEdgeOffset]
        assertTrue("When pulling top corner, top X should lead bottom X", topXWithTopTilt < bottomXWithTopTilt)
    }

    @Test
    fun foldEdgeAndDepthProvideSmoothShadowData() {
        val startEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.GRID.toFloat())
        val midEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.GRID / 2f)
        val endEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION)

        assertTrue("Fold edge starts on right", startEdge > 0.8f)
        assertTrue("Fold edge moves left during turn", midEdge < startEdge)
        assertTrue("Fold edge completes past spine", endEdge < midEdge)

        val midDepth = PlayLikeBezierCurl.foldEdgeDepth(PageRole.FRONT, PlayLikeCurlModel.GRID / 2f)
        assertTrue("Mid-turn fold depth should have positive elevation", midDepth > 0.05f)
    }

    @Test
    fun landscapeSpreadForwardStartsRightLandsLeft() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.FRONT, 200, 150, PageOrientation.LANDSCAPE
        )

        // Progress = 0.0 (resting on right page [0.5, 1.0])
        PlayLikeBezierCurl.updateLandscape(geometry, 0.0f, true)
        val pos = geometry.positions
        val grid = PlayLikeCurlModel.GRID
        val midRow = grid / 2

        val spineOffset = 3 * (midRow * (grid + 1) + 0)
        val outerOffset = 3 * (midRow * (grid + 1) + grid)
        assertEquals(0.5f, pos[spineOffset], TOLERANCE)
        assertEquals(1.0f, pos[outerOffset], TOLERANCE)
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[outerOffset + 2], TOLERANCE)

        // Progress = 0.5 (arching across spine in 3D)
        PlayLikeBezierCurl.updateLandscape(geometry, 0.5f, true)
        assertEquals(0.5f, pos[spineOffset], TOLERANCE) // spine stays at 0.5
        var maxZ = Float.NEGATIVE_INFINITY
        for (col in 0..grid) {
            val z = pos[3 * (midRow * (grid + 1) + col) + 2]
            if (z > maxZ) maxZ = z
        }
        assertTrue("Crest lifts above table in 3D", maxZ > 0.05f)

        // Progress = 1.0 (lands flat on left page [0.0, 0.5])
        PlayLikeBezierCurl.updateLandscape(geometry, 1.0f, true)
        assertEquals(0.5f, pos[spineOffset], TOLERANCE) // spine at 0.5
        assertEquals(0.0f, pos[outerOffset], TOLERANCE) // outer edge at 0.0
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[outerOffset + 2], TOLERANCE)
    }

    @Test
    fun landscapeSpreadBackwardStartsLeftLandsRight() {
        val geometry = PlayLikeCurlGeometry.createPage(
            PageRole.LEFT, 200, 150, PageOrientation.LANDSCAPE
        )

        val grid = PlayLikeCurlModel.GRID
        val midRow = grid / 2
        val outerOffset = 3 * (midRow * (grid + 1) + 0)
        val spineOffset = 3 * (midRow * (grid + 1) + grid)

        // Progress = 0.0 (resting on left page [0.0, 0.5])
        PlayLikeBezierCurl.updateLandscape(geometry, 0.0f, false)
        val pos = geometry.positions
        assertEquals(0.5f, pos[spineOffset], TOLERANCE)
        assertEquals(0.0f, pos[outerOffset], TOLERANCE)

        // Progress = 1.0 (lands flat on right page [0.5, 1.0])
        PlayLikeBezierCurl.updateLandscape(geometry, 1.0f, false)
        assertEquals(0.5f, pos[spineOffset], TOLERANCE)
        assertEquals(1.0f, pos[outerOffset], TOLERANCE)
    }

    @Test
    fun landscapeFoldEdgeShadowCoordinates() {
        val startEdge = PlayLikeBezierCurl.foldEdgeXLandscape(0f, true)
        val midEdge = PlayLikeBezierCurl.foldEdgeXLandscape(0.5f, true)
        val endEdge = PlayLikeBezierCurl.foldEdgeXLandscape(1.0f, true)

        assertTrue("Starts on right page", startEdge > 0.8f)
        assertTrue("Sweeps across spine", midEdge < startEdge && midEdge > 0.2f)
        assertTrue("Lands on left page", endEdge < midEdge)

        val midDepth = PlayLikeBezierCurl.foldEdgeDepthLandscape(0.5f, true)
        assertTrue("Mid depth is positive", midDepth > 0.05f)
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
