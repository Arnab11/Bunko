package karacken.curl

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pure fold-edge shadow projection shared by portrait and landscape rendering. */
internal object FoldShadowModel {
    const val MAX_OPACITY: Float = 0.30f
    const val WIDTH: Float = 0.07f
    const val SHADOW_DEPTH: Float = PlayLikeCurlModel.RIGHT_DEPTH + 0.00025f

    fun resolve(role: PageRole, curlPosition: Float, mirrored: Boolean): State {
        val progress = progress(role, curlPosition)
        val opacity = MAX_OPACITY * sin(PI * progress).toFloat()
        var edge = PlayLikeCurlGeometry.foldEdgeX(role, curlPosition)
        val edgeDepth = PlayLikeCurlGeometry.foldEdgeDepth(role, curlPosition)
        if (mirrored) edge = 1f - edge
        edge = PlayLikeCurlGeometry.projectXOntoDepthPlane(
            edge,
            edgeDepth,
            SHADOW_DEPTH
        )
        return if (mirrored) {
            State(edge - WIDTH, edge, edge, opacity, false)
        } else {
            State(edge, edge + WIDTH, edge, opacity, true)
        }
    }

    private fun progress(role: PageRole, curlPosition: Float): Float {
        if (role == PageRole.RIGHT) return 0f
        val range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
        val value = if (role == PageRole.LEFT) {
            (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range
        } else {
            (PlayLikeCurlModel.GRID - curlPosition) / range
        }
        return max(0f, min(1f, value))
    }

    internal class State(
        val startX: Float,
        val endX: Float,
        val foldEdgeX: Float,
        val opacity: Float,
        val isDarkAtStart: Boolean
    )
}
