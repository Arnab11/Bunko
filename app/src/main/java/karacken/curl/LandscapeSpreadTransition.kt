package karacken.curl

import kotlin.math.max
import kotlin.math.min

/**
 * Single-leaf page-turn transition for two-page landscape spreads.
 *
 * The turning leaf starts flat on the right half (forward) or left half (backward),
 * lifts into a curl that travels across the full spread, and settles flat on the
 * opposite half - exactly like a real book and Google Play Books. There is no
 * two-phase hand-off: one curl position drives the whole animation.
 */
internal class LandscapeSpreadTransition private constructor(
    val isForward: Boolean,
    val progress: Float,
    /** Curl position for the single turning leaf. */
    val leafCurlPosition: Float
) {
    companion object {
        fun from(motion: PlayLikeCurlModel): LandscapeSpreadTransition {
            val forward = motion.activePage != ActivePage.LEFT
            val curlPosition = if (forward) {
                motion.frontPage.curlPosition
            } else {
                motion.leftPage.curlPosition
            }
            val range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
            var progress = if (forward) {
                (PlayLikeCurlModel.GRID - curlPosition) / range
            } else {
                (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range
            }
            progress = clamp(progress)
            return LandscapeSpreadTransition(forward, progress, curlPosition)
        }

        private fun clamp(value: Float): Float {
            return max(0f, min(1f, value))
        }
    }
}
