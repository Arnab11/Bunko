package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PlayLikeBezierCurlTest {
    private static final float TOLERANCE = 0.001f;

    @Before
    public void setUp() {
        PlayLikeBezierCurl.resetTouchTilt();
    }

    @After
    public void tearDown() {
        PlayLikeBezierCurl.resetTouchTilt();
    }

    @Test
    public void inactivePageRemainsFlat() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT);

        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID, false);

        int grid = PlayLikeCurlModel.GRID;
        float[] pos = geometry.getPositions();
        for (int row = 0; row <= grid; row++) {
            for (int col = 0; col <= grid; col++) {
                int offset = 3 * (row * (grid + 1) + col);
                assertEquals(col / (float) grid, pos[offset], TOLERANCE);
                assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[offset + 2], TOLERANCE);
            }
        }
    }

    @Test
    public void forwardCurlStartsFlatAndCompletesTurned() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT);

        // Progress = 0 (unturned)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID, true);
        int grid = PlayLikeCurlModel.GRID;
        float[] pos = geometry.getPositions();

        int midRow = grid / 2;
        int rightCol = grid;
        int rightOffset = 3 * (midRow * (grid + 1) + rightCol);
        assertEquals(1.0f, pos[rightOffset], TOLERANCE);
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[rightOffset + 2], TOLERANCE);

        // Progress = 1 (fully turned past spine to left)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, true);
        assertTrue(pos[rightOffset] <= 0f);
        assertEquals(PlayLikeCurlModel.LEFT_DEPTH, pos[rightOffset + 2], TOLERANCE);
    }

    @Test
    public void forwardCurlMidTurnPreservesSpineFlatnessAndLiftsFold() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT);

        float midPosition = (PlayLikeCurlModel.GRID + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f;
        PlayLikeBezierCurl.update(geometry, midPosition, true);

        int grid = PlayLikeCurlModel.GRID;
        float[] pos = geometry.getPositions();
        int midRow = grid / 2;

        // Spine edge (col = 0) must remain anchored flat at X = 0
        int spineOffset = 3 * (midRow * (grid + 1) + 0);
        assertEquals(0f, pos[spineOffset], TOLERANCE);

        // Fold crest must achieve visible 3D elevation
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int col = 0; col <= grid; col++) {
            int offset = 3 * (midRow * (grid + 1) + col);
            float z = pos[offset + 2];
            if (z > maxZ) {
                maxZ = z;
            }
        }

        assertTrue("Fold crest must lift in 3D above book plane, got: " + maxZ, maxZ > 0.05f);
        assertTrue("Max Z must not exceed peak elevation", maxZ <= PlayLikeBezierCurl.MAX_CURL_HEIGHT + 0.05f);
    }

    @Test
    public void backwardCurlStartsTurnedAndCompletesFlat() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.LEFT, 100, 150, PageOrientation.PORTRAIT);

        int grid = PlayLikeCurlModel.GRID;
        int midRow = grid / 2;
        int rightCol = grid;
        int offset = 3 * (midRow * (grid + 1) + rightCol);

        // Progress = 0 (on left side)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, true);
        float[] pos = geometry.getPositions();
        assertTrue(pos[offset] <= 0f);

        // Progress = 1 (turned back flat on right)
        PlayLikeBezierCurl.update(geometry, PlayLikeCurlModel.GRID, true);
        assertEquals(1.0f, pos[offset], TOLERANCE);
        assertEquals(PlayLikeCurlModel.LEFT_DEPTH, pos[offset + 2], TOLERANCE);
    }

    @Test
    public void touchTiltCreatesConicalCurl() {
        PageGeometry geometryBottomTilt = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT);
        PageGeometry geometryTopTilt = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 100, 150, PageOrientation.PORTRAIT);

        float midPos = (PlayLikeCurlModel.GRID + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f;
        int grid = PlayLikeCurlModel.GRID;

        // Bottom pull (thumb pull): bottom corner leads (smaller X)
        PlayLikeBezierCurl.setTouchTilt(0.8f);
        PlayLikeBezierCurl.update(geometryBottomTilt, midPos, true);

        // Top pull: top corner leads (smaller X)
        PlayLikeBezierCurl.setTouchTilt(-0.8f);
        PlayLikeBezierCurl.update(geometryTopTilt, midPos, true);

        float[] bottomTiltPos = geometryBottomTilt.getPositions();
        float[] topTiltPos = geometryTopTilt.getPositions();

        int topEdgeOffset = 3 * (0 * (grid + 1) + grid);
        int bottomEdgeOffset = 3 * (grid * (grid + 1) + grid);

        float bottomXWithBottomTilt = bottomTiltPos[bottomEdgeOffset];
        float topXWithBottomTilt = bottomTiltPos[topEdgeOffset];
        assertTrue("When pulling bottom corner, bottom X should lead top X", bottomXWithBottomTilt < topXWithBottomTilt);

        float bottomXWithTopTilt = topTiltPos[bottomEdgeOffset];
        float topXWithTopTilt = topTiltPos[topEdgeOffset];
        assertTrue("When pulling top corner, top X should lead bottom X", topXWithTopTilt < bottomXWithTopTilt);
    }

    @Test
    public void foldEdgeAndDepthProvideSmoothShadowData() {
        float startEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.GRID);
        float midEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.GRID / 2f);
        float endEdge = PlayLikeBezierCurl.foldEdgeX(PageRole.FRONT, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION);

        assertTrue("Fold edge starts on right", startEdge > 0.8f);
        assertTrue("Fold edge moves left during turn", midEdge < startEdge);
        assertTrue("Fold edge completes past spine", endEdge < midEdge);

        float midDepth = PlayLikeBezierCurl.foldEdgeDepth(PageRole.FRONT, PlayLikeCurlModel.GRID / 2f);
        assertTrue("Mid-turn fold depth should have positive elevation", midDepth > 0.05f);
    }

    @Test
    public void landscapeSpreadForwardStartsRightLandsLeft() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 200, 150, PageOrientation.LANDSCAPE);

        // Progress = 0.0 (resting on right page [0.5, 1.0])
        PlayLikeBezierCurl.updateLandscape(geometry, 0.0f, true);
        float[] pos = geometry.getPositions();
        int grid = PlayLikeCurlModel.GRID;
        int midRow = grid / 2;

        int spineOffset = 3 * (midRow * (grid + 1) + 0);
        int outerOffset = 3 * (midRow * (grid + 1) + grid);
        assertEquals(0.5f, pos[spineOffset], TOLERANCE);
        assertEquals(1.0f, pos[outerOffset], TOLERANCE);
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[outerOffset + 2], TOLERANCE);

        // Progress = 0.5 (arching across spine in 3D)
        PlayLikeBezierCurl.updateLandscape(geometry, 0.5f, true);
        assertEquals(0.5f, pos[spineOffset], TOLERANCE); // spine stays at 0.5
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int col = 0; col <= grid; col++) {
            float z = pos[3 * (midRow * (grid + 1) + col) + 2];
            if (z > maxZ) maxZ = z;
        }
        assertTrue("Crest lifts above table in 3D", maxZ > 0.05f);

        // Progress = 1.0 (lands flat on left page [0.0, 0.5])
        PlayLikeBezierCurl.updateLandscape(geometry, 1.0f, true);
        assertEquals(0.5f, pos[spineOffset], TOLERANCE); // spine at 0.5
        assertEquals(0.0f, pos[outerOffset], TOLERANCE); // outer edge at 0.0
        assertEquals(PlayLikeCurlModel.FRONT_DEPTH, pos[outerOffset + 2], TOLERANCE);
    }

    @Test
    public void landscapeSpreadBackwardStartsLeftLandsRight() {
        PageGeometry geometry = PlayLikeCurlGeometry.createPage(
                PageRole.LEFT, 200, 150, PageOrientation.LANDSCAPE);

        int grid = PlayLikeCurlModel.GRID;
        int midRow = grid / 2;
        int outerOffset = 3 * (midRow * (grid + 1) + 0);
        int spineOffset = 3 * (midRow * (grid + 1) + grid);

        // Progress = 0.0 (resting on left page [0.0, 0.5])
        PlayLikeBezierCurl.updateLandscape(geometry, 0.0f, false);
        float[] pos = geometry.getPositions();
        assertEquals(0.5f, pos[spineOffset], TOLERANCE);
        assertEquals(0.0f, pos[outerOffset], TOLERANCE);

        // Progress = 1.0 (lands flat on right page [0.5, 1.0])
        PlayLikeBezierCurl.updateLandscape(geometry, 1.0f, false);
        assertEquals(0.5f, pos[spineOffset], TOLERANCE);
        assertEquals(1.0f, pos[outerOffset], TOLERANCE);
    }

    @Test
    public void landscapeFoldEdgeShadowCoordinates() {
        float startEdge = PlayLikeBezierCurl.foldEdgeXLandscape(0f, true);
        float midEdge = PlayLikeBezierCurl.foldEdgeXLandscape(0.5f, true);
        float endEdge = PlayLikeBezierCurl.foldEdgeXLandscape(1.0f, true);

        assertTrue("Starts on right page", startEdge > 0.8f);
        assertTrue("Sweeps across spine", midEdge < startEdge && midEdge > 0.2f);
        assertTrue("Lands on left page", endEdge < midEdge);

        float midDepth = PlayLikeBezierCurl.foldEdgeDepthLandscape(0.5f, true);
        assertTrue("Mid depth is positive", midDepth > 0.05f);
    }
}
