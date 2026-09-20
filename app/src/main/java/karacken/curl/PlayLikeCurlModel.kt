package karacken.curl

import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** State-machine transcription of the original PlayLikeCurl renderer and surface view. */
class PlayLikeCurlModel(
    private val pageCount: Int,
    initialPosition: Int
) {
    init {
        require(pageCount > 0) { "PlayLikeCurl requires at least one page" }
        require(initialPosition in 0 until pageCount) { "Initial page is outside the adapter" }
    }

    internal val leftPage = PageState(PageRole.LEFT, LEFT_DEPTH, RIGHT_ENDPOINT_POSITION, 0)
    internal val frontPage = PageState(PageRole.FRONT, FRONT_DEPTH, GRID.toFloat(), 0)
    internal val rightPage = PageState(PageRole.RIGHT, RIGHT_DEPTH, GRID.toFloat(), 0)
    internal val drawOrder: List<PageState> = Collections.unmodifiableList(
        listOf(leftPage, frontPage, rightPage)
    )

    internal var activePage: ActivePage = ActivePage.CURRENT
        private set

    var currentPosition: Int = initialPosition
        private set

    private var gestureStartX: Float = 0f
    private var gestureStartCurlPosition: Float = GRID.toFloat()

    init {
        resetPages()
        updatePageIdentities()
    }

    fun jumpTo(position: Int) {
        require(position in 0 until pageCount) { "Page is outside the adapter" }
        currentPosition = position
        resetPages()
        updatePageIdentities()
    }

    fun beginGesture(x: Float) {
        gestureStartX = x
        activePage = ActivePage.CURRENT
        gestureStartCurlPosition = activePageState().curlPosition
    }

    fun dragTo(x: Float, width: Float) {
        if (width <= 0f) return
        val delta = x - gestureStartX
        val movedFraction = delta / width
        if (delta > 0f) {
            if (gestureStartCurlPosition >= GRID && canSwipePrevious()) {
                activePage = ActivePage.LEFT
                gestureStartCurlPosition = activePageState().curlPosition
            }
            val value = gestureStartCurlPosition + movedFraction * GRID
            if (value <= GRID) activePageState().curlPosition = value
        } else if (delta < 0f) {
            val value = (1f - abs(movedFraction)) * GRID - (GRID - gestureStartCurlPosition)
            if (canSwipeNext()) activePageState().curlPosition = value
        }
    }

    internal fun release(): Settlement {
        if (activePage == ActivePage.LEFT) {
            if (canSwipePrevious() && activePageState().curlPosition >= RELEASE_COMMIT_POSITION) {
                return settlement(
                    LEFT_ENDPOINT_PERCENT,
                    PageChange.PREVIOUS,
                    SettlementInterpolator.DECELERATE
                )
            }
            return settlement(
                RIGHT_ENDPOINT_PERCENT,
                PageChange.NONE,
                SettlementInterpolator.ACCELERATE_DECELERATE
            )
        }
        if (canSwipeNext() && activePageState().curlPosition <= RELEASE_COMMIT_POSITION) {
            return settlement(
                RIGHT_ENDPOINT_PERCENT,
                PageChange.NEXT,
                SettlementInterpolator.DECELERATE
            )
        }
        return settlement(
            LEFT_ENDPOINT_PERCENT,
            PageChange.NONE,
            SettlementInterpolator.ACCELERATE_DECELERATE
        )
    }

    internal fun flingTowardNext(): Settlement {
        if (!canSwipeNext()) return release()
        return settlement(
            RIGHT_ENDPOINT_PERCENT,
            PageChange.NEXT,
            SettlementInterpolator.DECELERATE
        )
    }

    internal fun flingTowardPrevious(): Settlement {
        if (!canSwipePrevious()) return release()
        return settlement(
            LEFT_ENDPOINT_PERCENT,
            PageChange.PREVIOUS,
            SettlementInterpolator.DECELERATE
        )
    }

    internal fun turn(pageChange: PageChange): Settlement {
        if (pageChange == PageChange.PREVIOUS) {
            if (!canSwipePrevious()) return release()
            activePage = ActivePage.LEFT
            leftPage.curlPosition = RIGHT_ENDPOINT_POSITION
            return flingTowardPrevious()
        }
        if (pageChange == PageChange.NEXT) {
            if (!canSwipeNext()) return release()
            activePage = ActivePage.CURRENT
            frontPage.curlPosition = GRID.toFloat()
            return flingTowardNext()
        }
        return release()
    }

    internal fun updateSettlement(valuePercent: Float) {
        activePageState().curlPosition = GRID * valuePercent / 100f
    }

    internal fun cancelGesture() {
        resetPages()
    }

    internal fun completeSettlement(settlement: Settlement) {
        resetPages()
        if (settlement.pageChange == PageChange.PREVIOUS) {
            currentPosition = max(0, currentPosition - 1)
        } else if (settlement.pageChange == PageChange.NEXT) {
            currentPosition = min(pageCount - 1, currentPosition + 1)
        }
        updatePageIdentities()
    }

    private fun settlement(
        targetPercent: Int,
        pageChange: PageChange,
        interpolator: SettlementInterpolator
    ): Settlement {
        return Settlement(
            targetPercent,
            SETTLEMENT_DURATION_MILLIS,
            interpolator,
            pageChange
        )
    }

    private fun resetPages() {
        leftPage.curlPosition = RIGHT_ENDPOINT_POSITION
        rightPage.curlPosition = GRID.toFloat()
        frontPage.curlPosition = GRID.toFloat()
        activePage = ActivePage.CURRENT
    }

    private fun updatePageIdentities() {
        leftPage.pageIndex = max(0, currentPosition - 1)
        frontPage.pageIndex = currentPosition
        rightPage.pageIndex = min(pageCount - 1, currentPosition + 1)
    }

    private fun activePageState(): PageState {
        return when (activePage) {
            ActivePage.LEFT -> leftPage
            ActivePage.RIGHT -> rightPage
            ActivePage.CURRENT -> frontPage
        }
    }

    private fun canSwipePrevious(): Boolean {
        return currentPosition > 0
    }

    private fun canSwipeNext(): Boolean {
        return currentPosition < pageCount - 1
    }

    companion object {
        const val GRID: Int = 25
        const val RADIUS: Float = 0.18f
        const val LEFT_ENDPOINT_PERCENT: Int = 100
        const val RIGHT_ENDPOINT_PERCENT: Int = -5
        const val SETTLEMENT_DURATION_MILLIS: Long = 220L
        const val LEFT_DEPTH: Float = -0.001f
        const val FRONT_DEPTH: Float = -0.002f
        const val RIGHT_DEPTH: Float = -0.003f
        const val RIGHT_ENDPOINT_POSITION: Float = GRID * (RIGHT_ENDPOINT_PERCENT / 100f)
        private const val RELEASE_COMMIT_POSITION: Float =
            (GRID + RIGHT_ENDPOINT_POSITION) / 2f
    }
}
