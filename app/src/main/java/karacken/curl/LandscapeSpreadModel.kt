package karacken.curl

import kotlin.math.max
import kotlin.math.min

/**
 * Two-page spread adapter around PlayLikeCurl's unchanged gesture and settlement model.
 * Page indices are zero based, so the first visible spread is 0 | 1.
 */
internal class LandscapeSpreadModel(
    private val pageCount: Int,
    initialPageIndex: Int
) {
    init {
        require(pageCount > 0) { "A landscape spread requires at least one page" }
        require(initialPageIndex in 0 until pageCount) { "Initial page is outside the adapter" }
    }

    private val spreadCount: Int = (pageCount + 1) / 2
    val motionModel: PlayLikeCurlModel = PlayLikeCurlModel(spreadCount, initialPageIndex / 2)

    val currentLeftPageIndex: Int
        get() = pageIndexForSpread(motionModel.currentPosition, false)

    val currentRightPageIndex: Int
        get() = pageIndexForSpread(motionModel.currentPosition, true)

    val previousLeftPageIndex: Int
        get() = pageIndexForSpread(max(0, motionModel.currentPosition - 1), false)

    val previousRightPageIndex: Int
        get() = pageIndexForSpread(max(0, motionModel.currentPosition - 1), true)

    val nextLeftPageIndex: Int
        get() = pageIndexForSpread(min(spreadCount - 1, motionModel.currentPosition + 1), false)

    val nextRightPageIndex: Int
        get() = pageIndexForSpread(min(spreadCount - 1, motionModel.currentPosition + 1), true)

    val forwardTurningPageIndex: Int
        get() = currentRightPageIndex

    val forwardReversePageIndex: Int
        get() = nextLeftPageIndex

    val forwardUnderneathPageIndex: Int
        get() = nextRightPageIndex

    val backwardTurningPageIndex: Int
        get() = currentLeftPageIndex

    val backwardReversePageIndex: Int
        get() = previousRightPageIndex

    val backwardUnderneathPageIndex: Int
        get() = previousLeftPageIndex

    val currentPageIndex: Int
        get() = currentLeftPageIndex

    val transition: LandscapeSpreadTransition
        get() = LandscapeSpreadTransition.from(motionModel)

    fun jumpTo(pageIndex: Int) {
        require(pageIndex in 0 until pageCount) { "Page is outside the adapter" }
        motionModel.jumpTo(pageIndex / 2)
    }

    fun beginGesture(x: Float) {
        motionModel.beginGesture(x)
    }

    fun dragTo(x: Float, leafWidth: Float) {
        val endpointAdjustedWidth = leafWidth * PlayLikeCurlModel.GRID /
            (PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION)
        motionModel.dragTo(x, endpointAdjustedWidth)
    }

    fun release(): Settlement {
        return motionModel.release()
    }

    fun flingTowardNext(): Settlement {
        return motionModel.flingTowardNext()
    }

    fun flingTowardPrevious(): Settlement {
        return motionModel.flingTowardPrevious()
    }

    fun updateSettlement(valuePercent: Float) {
        motionModel.updateSettlement(valuePercent)
    }

    fun cancelGesture() {
        motionModel.cancelGesture()
    }

    fun completeSettlement(settlement: Settlement) {
        motionModel.completeSettlement(settlement)
    }

    private fun pageIndexForSpread(spreadIndex: Int, right: Boolean): Int {
        val rawIndex = spreadIndex * 2 + if (right) 1 else 0
        return min(pageCount - 1, rawIndex)
    }
}
