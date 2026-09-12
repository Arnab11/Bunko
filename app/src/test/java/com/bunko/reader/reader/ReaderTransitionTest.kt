package com.bunko.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTransitionTest {
    @Test
    fun physicalDirectionMatchesRtlAndLtrReading() {
        assertEquals(1f, readerTurnPhysicalSign(true, ReaderTurnDirection.Next))
        assertEquals(-1f, readerTurnPhysicalSign(true, ReaderTurnDirection.Previous))
        assertEquals(-1f, readerTurnPhysicalSign(false, ReaderTurnDirection.Next))
        assertEquals(1f, readerTurnPhysicalSign(false, ReaderTurnDirection.Previous))
    }

    @Test
    fun dragDirectionMatchesExistingReaderSemantics() {
        assertEquals(ReaderTurnDirection.Next, readerTurnForDrag(100f, true))
        assertEquals(ReaderTurnDirection.Previous, readerTurnForDrag(-100f, true))
        assertEquals(ReaderTurnDirection.Next, readerTurnForDrag(-100f, false))
        assertEquals(ReaderTurnDirection.Previous, readerTurnForDrag(100f, false))
    }

    @Test
    fun progressAndCommitAreBounded() {
        assertEquals(0.5f, readerTurnProgress(500f, 1000f), 0.001f)
        assertEquals(1f, readerTurnProgress(1200f, 1000f), 0.001f)
        assertFalse(shouldCommitReaderTurn(0.119f))
        assertTrue(shouldCommitReaderTurn(0.12f))
    }

    @Test
    fun lockedProgressOnlyCountsDragInInitialDirection() {
        assertEquals(
            0.5f,
            readerLockedTurnProgress(
                dragX = 500f,
                rightToLeft = true,
                direction = ReaderTurnDirection.Next,
                visualDistancePx = 1000f
            ),
            0.001f
        )
        assertEquals(
            0f,
            readerLockedTurnProgress(
                dragX = -500f,
                rightToLeft = true,
                direction = ReaderTurnDirection.Next,
                visualDistancePx = 1000f
            ),
            0.001f
        )
        assertEquals(
            0.5f,
            readerLockedTurnProgress(
                dragX = -500f,
                rightToLeft = false,
                direction = ReaderTurnDirection.Next,
                visualDistancePx = 1000f
            ),
            0.001f
        )
    }

    @Test
    fun unlockedDragCanChooseDirectionEveryFrame() {
        val direction = readerTurnDirectionForDrag(
            dragX = -500f,
            rightToLeft = true,
            lockedDirection = ReaderTurnDirection.Next,
            directionLockEnabled = false
        )

        assertEquals(ReaderTurnDirection.Previous, direction)
        assertEquals(
            0.5f,
            readerTurnProgressForDrag(
                dragX = -500f,
                rightToLeft = true,
                direction = direction,
                visualDistancePx = 1000f,
                directionLockEnabled = false
            ),
            0.001f
        )
    }

    @Test
    fun lockedDragKeepsInitialDirectionAndCancelsReverseMotion() {
        val direction = readerTurnDirectionForDrag(
            dragX = -500f,
            rightToLeft = true,
            lockedDirection = ReaderTurnDirection.Next,
            directionLockEnabled = true
        )

        assertEquals(ReaderTurnDirection.Next, direction)
        assertEquals(
            0f,
            readerTurnProgressForDrag(
                dragX = -500f,
                rightToLeft = true,
                direction = direction,
                visualDistancePx = 1000f,
                directionLockEnabled = true
            ),
            0.001f
        )
    }

    @Test
    fun flingCommitFollowsPhysicalReadingDirection() {
        assertFalse(
            shouldCommitReaderTurn(
                progress = 0f,
                velocityX = 5_000f,
                direction = ReaderTurnDirection.Next,
                rightToLeft = true,
                minimumFlingVelocity = 500f
            )
        )
        assertFalse(
            shouldCommitReaderTurn(
                progress = 0.039f,
                velocityX = 5_000f,
                direction = ReaderTurnDirection.Next,
                rightToLeft = true,
                minimumFlingVelocity = 500f
            )
        )
        assertTrue(
            shouldCommitReaderTurn(
                progress = 0.05f,
                velocityX = 600f,
                direction = ReaderTurnDirection.Next,
                rightToLeft = true,
                minimumFlingVelocity = 500f
            )
        )
        assertFalse(
            shouldCommitReaderTurn(
                progress = 0.05f,
                velocityX = -600f,
                direction = ReaderTurnDirection.Next,
                rightToLeft = true,
                minimumFlingVelocity = 500f
            )
        )
        assertTrue(
            shouldCommitReaderTurn(
                progress = 0.05f,
                velocityX = -600f,
                direction = ReaderTurnDirection.Next,
                rightToLeft = false,
                minimumFlingVelocity = 500f
            )
        )
    }

    @Test
    fun turnTargetUsesLayoutStepInsteadOfHardcodedSpreadStep() {
        assertEquals(
            12,
            readerTurnTargetPage(
                currentPage = 10,
                pageCount = 30,
                direction = ReaderTurnDirection.Next,
                step = 2,
                completeWhenPastEnd = false
            )
        )
        assertEquals(
            11,
            readerTurnTargetPage(
                currentPage = 10,
                pageCount = 30,
                direction = ReaderTurnDirection.Next,
                step = 1,
                completeWhenPastEnd = false
            )
        )
        assertEquals(
            9,
            readerTurnTargetPage(
                currentPage = 10,
                pageCount = 30,
                direction = ReaderTurnDirection.Previous,
                step = 1,
                completeWhenPastEnd = false
            )
        )
    }

    @Test
    fun turnTargetHandlesReaderBoundaries() {
        assertEquals(
            14,
            readerTurnTargetPage(
                currentPage = 13,
                pageCount = 15,
                direction = ReaderTurnDirection.Next,
                step = 2,
                completeWhenPastEnd = false
            )
        )
        assertEquals(
            null,
            readerTurnTargetPage(
                currentPage = 13,
                pageCount = 15,
                direction = ReaderTurnDirection.Next,
                step = 2,
                completeWhenPastEnd = true
            )
        )
        assertEquals(
            null,
            readerTurnTargetPage(
                currentPage = 0,
                pageCount = 15,
                direction = ReaderTurnDirection.Previous,
                step = 2,
                completeWhenPastEnd = false
            )
        )
    }

    @Test
    fun spreadCurlBackFaceUsesIncomingNearPage() {
        assertEquals(
            12,
            readerSpreadCurlBackPageIndex(
                targetPage = 12,
                pageCount = 30,
                direction = ReaderTurnDirection.Next
            )
        )
        assertEquals(
            11,
            readerSpreadCurlBackPageIndex(
                targetPage = 10,
                pageCount = 30,
                direction = ReaderTurnDirection.Previous
            )
        )
        assertEquals(
            14,
            readerSpreadCurlBackPageIndex(
                targetPage = 14,
                pageCount = 15,
                direction = ReaderTurnDirection.Previous
            )
        )
    }

    @Test
    fun slideDistanceIsHalfOnlyForSpreadShifts() {
        // A spread shift (step 1 while a spread is shown) moves the strip by one page.
        assertEquals(0.5f, readerSlideDistanceFraction(step = 1, portrait = false, currentSinglePage = false))
        // Full-width sweeps: spread turns, portrait single steps, landscape singles.
        assertEquals(1f, readerSlideDistanceFraction(step = 2, portrait = false, currentSinglePage = false))
        assertEquals(1f, readerSlideDistanceFraction(step = 1, portrait = true, currentSinglePage = true))
        assertEquals(1f, readerSlideDistanceFraction(step = 1, portrait = false, currentSinglePage = true))
    }

    @Test
    fun shiftStripRolesFollowDirection() {
        assertEquals(
            Triple(7, 6, 5),
            readerShiftStripPages(outgoingPage = 5, targetPage = 6, direction = ReaderTurnDirection.Next)
        )
        assertEquals(
            Triple(5, 6, 7),
            readerShiftStripPages(outgoingPage = 6, targetPage = 5, direction = ReaderTurnDirection.Previous)
        )
    }

    @Test
    fun shiftStripGeometryGluesEdgesAndLandsOnTheSpine() {
        // physicalSign +1 (e.g. RTL forward): the staying page starts hugging the spine
        // from the left and travels right by exactly its own width.
        val g = readerShiftStripGeometry(
            physicalSign = 1f,
            halfViewportPx = 500f,
            stayWidthPx = 400f,
            enterWidthPx = 400f,
            exitWidthPx = 400f
        )
        assertEquals(400f, g.travelPx, 0.001f)
        assertEquals(100f, g.stayStartLeftPx, 0.001f)
        // Glued at start: enter's right edge (-300 + 400) meets stay's left edge (100).
        assertEquals(-300f, g.enterStartLeftPx, 0.001f)
        assertEquals(500f, g.exitStartLeftPx, 0.001f)

        val mirrored = readerShiftStripGeometry(
            physicalSign = -1f,
            halfViewportPx = 500f,
            stayWidthPx = 400f,
            enterWidthPx = 400f,
            exitWidthPx = 400f
        )
        assertEquals(-400f, mirrored.travelPx, 0.001f)
        assertEquals(500f, mirrored.stayStartLeftPx, 0.001f)
        assertEquals(900f, mirrored.enterStartLeftPx, 0.001f)
        assertEquals(100f, mirrored.exitStartLeftPx, 0.001f)
    }

    @Test
    fun spreadCurlBackFaceIsTheWidePageItselfInBothDirections() {
        assertEquals(
            12,
            readerSpreadCurlBackPageIndex(
                targetPage = 12,
                pageCount = 30,
                direction = ReaderTurnDirection.Next,
                targetIsWide = true
            )
        )
        assertEquals(
            10,
            readerSpreadCurlBackPageIndex(
                targetPage = 10,
                pageCount = 30,
                direction = ReaderTurnDirection.Previous,
                targetIsWide = true
            )
        )
    }
}
