package karacken.curl

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Authentic 3D page-curl deformation engine implementing Google Play Books'
 * dual-arc cubic Bézier spline model with exact arc-length parameterization.
 *
 * Key structural fixes:
 * - **Arc-Length Inversion:** Replaced the divergent bisection search with exact
 *   cumulative-chord arc-length reparameterization, eliminating edge clamping and distortion.
 * - **Single-Page Viewport Mapping:** Correctly maps the spine to X = 0.0 and outer edge
 *   to X = 1.0, preventing two-page spread squashing in portrait mode.
 * - **Inactive Previous Page Bounding:** Binds inactive left pages at X <= 0.0 so they
 *   never overlap the screen or occlude active forward turns.
 * - **Conical Tilt:** Seamlessly integrates dynamic touch Y tilt for realistic corner-lead peeling.
 */
internal object PlayLikeBezierCurl {
    /** Peak elevation of the curling page above the book plane in camera units. */
    const val MAX_CURL_HEIGHT: Float = 0.35f

    /** Maximum conical tilt angle factor across the vertical axis. */
    const val MAX_TILT: Float = 0.14f

    /** Default thumb-turn tilt (slight pull from lower half where reader thumb rests). */
    const val DEFAULT_THUMB_TILT: Float = 0.20f

    private val RAW_BEZIER = intArrayOf(
        1000, 0, 856, 0, 825, 0, 798, 0, 798, 0, 740, 0, 642, 0, 598, 0, 598, 0, 553, 0, 33, 0, 0, 0,
        800, 0, 528, 0, 350, 116, 201, 113, 201, 113, 167, 112, 134, 105, 102, 88, 102, 88, 67, 71, 33, 42, 0, 0,
        600, 71, 399, 125, 230, 290, 103, 290, 103, 290, 25, 290, -30, 242, -33, 152, -33, 152, -35, 110, -24, 59, 0, 0,
        400, 167, 220, 281, 124, 322, -6, 322, -6, 322, -97, 322, -150, 283, -150, 189, -150, 189, -147, 127, -106, 61, 0, 0,
        200, 301, 134, 336, -28, 382, -105, 382, -105, 382, -214, 382, -266, 318, -266, 221, -266, 221, -266, 118, -171, 0, 0, 0,
        0, 387, -41, 406, -100, 421, -160, 421, -160, 421, -244, 421, -354, 398, -354, 255, -354, 255, -354, 161, -281, 0, 0, 0,
        -200, 444, -281, 444, -347, 432, -392, 403, -392, 403, -434, 376, -457, 333, -457, 269, -457, 269, -457, 164, -328, 0, 0, 0,
        -400, 414, -448, 407, -485, 393, -512, 373, -512, 373, -547, 346, -565, 308, -565, 263, -565, 263, -565, 161, -426, 0, 0, 0,
        -600, 373, -628, 362, -646, 348, -661, 330, -661, 330, -678, 310, -688, 283, -688, 246, -688, 246, -688, 149, -537, 0, 0, 0,
        -800, 304, -797, 288, -795, 272, -792, 257, -792, 257, -789, 239, -784, 223, -778, 207, -778, 207, -724, 69, -546, 0, 0, 0,
        -1000, 0, -856, 0, -825, 0, -798, 0, -798, 0, -740, 0, -642, 0, -598, 0, -598, 0, -553, 0, -33, 0, 0, 0
    )

    private val KEYFRAMES = Array(11) { k ->
        val segs = Array(3) { s ->
            val pts = FloatArray(8)
            val sBase = k * 24 + s * 8
            for (i in 0 until 8) {
                pts[i] = RAW_BEZIER[sBase + i] * 0.001f
            }
            CubicBezier(pts)
        }
        SplineKeyframe(segs)
    }

    @Volatile
    var currentTouchTilt: Float = DEFAULT_THUMB_TILT
        private set

    /** Sets the active gesture touch tilt in [-1, 1] based on touch Y position. */
    fun setTouchTilt(tilt: Float) {
        currentTouchTilt = max(-1f, min(1f, tilt))
    }

    fun getTouchTilt(): Float = currentTouchTilt

    fun resetTouchTilt() {
        currentTouchTilt = DEFAULT_THUMB_TILT
    }

    /** Updates vertex positions for a page mesh using the Bézier spline model. */
    fun update(page: PageGeometry, curlPosition: Float, active: Boolean) {
        val grid = PlayLikeCurlModel.GRID
        val bitmapRatio = page.bitmapRatio
        val heightCorrection = (bitmapRatio - 1f) / 2f
        val role = page.role
        val positions = page.positions

        if (!active) {
            // Left page when inactive sits folded away to the left (X <= 0)
            // so it never occludes the active page or viewport
            val baseDepth = when (role) {
                PageRole.LEFT -> PlayLikeCurlModel.LEFT_DEPTH
                PageRole.FRONT -> PlayLikeCurlModel.FRONT_DEPTH
                PageRole.RIGHT -> PlayLikeCurlModel.RIGHT_DEPTH
            }
            val startX = if (role == PageRole.LEFT) -1f else 0f

            for (row in 0..grid) {
                val v = row / grid.toFloat()
                val y = v * bitmapRatio - heightCorrection
                for (col in 0..grid) {
                    val offset = 3 * (row * (grid + 1) + col)
                    positions[offset] = startX + col / grid.toFloat()
                    positions[offset + 1] = y
                    positions[offset + 2] = baseDepth
                }
            }
            return
        }

        val progress = progressOf(role, curlPosition)
        val tilt = currentTouchTilt
        val forward = (role == PageRole.FRONT)
        val point = FloatArray(2)

        for (row in 0..grid) {
            val v = row / grid.toFloat()
            val y = v * bitmapRatio - heightCorrection
            val slantEnvelope = sin(PI * progress).toFloat()
            val effProgress = max(0f, min(1f, progress + tilt * MAX_TILT * (v - 0.5f) * slantEnvelope))

            for (col in 0..grid) {
                val u = col / grid.toFloat()
                val offset = 3 * (row * (grid + 1) + col)

                if (role == PageRole.FRONT || role == PageRole.LEFT) {
                    evaluate(u, effProgress, forward, point)
                    positions[offset] = point[0]
                    positions[offset + 2] = point[1]
                } else {
                    positions[offset] = u
                    positions[offset + 2] = PlayLikeCurlModel.RIGHT_DEPTH
                }
                positions[offset + 1] = y
            }
        }
    }

    /**
     * Evaluates the 3D sheet position in single-page coordinates:
     * - Spine at X = 0.0, outer edge at X = 1.0.
     * - At progress 0.0: flat on right side [X = 0.0 to 1.0, Z = baseDepth].
     * - At progress 0.5: arched across spine in 3D (Z > 0).
     * - At progress 1.0: turned flat to left [X = 0.0 to -1.0, Z = baseDepth].
     */
    fun evaluate(u: Float, progress: Float, forward: Boolean, out: FloatArray) {
        val p = max(0f, min(1f, progress))
        val iMin = min(9, max(0, (10f * p).toInt()))
        val fMin = min(1f, max(0f, (p - iMin * 0.1f) * 10f))

        val x0 = KEYFRAMES[iMin].evalX(u)
        val y0 = KEYFRAMES[iMin].evalY(u)
        val x1 = KEYFRAMES[iMin + 1].evalX(u)
        val y1 = KEYFRAMES[iMin + 1].evalY(u)

        var xPlay = x0 * (1f - fMin) + x1 * fMin
        val yPlay = y0 * (1f - fMin) + y1 * fMin

        if (!forward) {
            xPlay = -xPlay
        }

        // In single-page mode, page occupies X in [0.0, 1.0] when flat on right
        out[0] = xPlay

        // Depth elevation
        val baseDepth = if (forward) PlayLikeCurlModel.FRONT_DEPTH else PlayLikeCurlModel.LEFT_DEPTH
        out[1] = baseDepth + yPlay * MAX_CURL_HEIGHT
    }

    /**
     * Updates vertex positions for a landscape two-page spread turning leaf.
     *
     * The center spine is at X = 0.5.
     * Forward turn: leaf sweeps from the right page [X = 0.5 to 1.0] across spine (X = 0.5)
     * and lands flat on the left page [X = 0.0 to 0.5].
     * Backward turn: leaf sweeps from the left page [X = 0.0 to 0.5] across spine (X = 0.5)
     * and lands flat on the right page [X = 0.5 to 1.0].
     */
    fun updateLandscape(page: PageGeometry, progress: Float, forward: Boolean) {
        val grid = PlayLikeCurlModel.GRID
        val bitmapRatio = page.bitmapRatio
        val pageHeight = 0.5f * bitmapRatio
        val heightCorrection = (pageHeight - 1f) / 2f
        val positions = page.positions
        val p = max(0f, min(1f, progress))
        val tilt = currentTouchTilt
        val point = FloatArray(2)

        for (row in 0..grid) {
            val v = row / grid.toFloat()
            val y = v * pageHeight - heightCorrection
            val slantEnvelope = sin(PI * p).toFloat()
            val effProgress = max(0f, min(1f, p + tilt * MAX_TILT * (v - 0.5f) * slantEnvelope))

            for (col in 0..grid) {
                val u = col / grid.toFloat()
                val offset = 3 * (row * (grid + 1) + col)

                evaluateLandscape(u, effProgress, forward, point)
                positions[offset] = point[0]
                positions[offset + 1] = y
                positions[offset + 2] = point[1]
            }
        }
    }

    /**
     * Evaluates 3D sheet position in full-spread landscape coordinates [0.0, 1.0] where spine is at X = 0.5:
     * - Right page: [0.5, 1.0]
     * - Left page:  [0.0, 0.5]
     */
    fun evaluateLandscape(u: Float, progress: Float, forward: Boolean, out: FloatArray) {
        val p = max(0f, min(1f, progress))
        val iMin = min(9, max(0, (10f * p).toInt()))
        val fMin = min(1f, max(0f, (p - iMin * 0.1f) * 10f))

        val uKey = if (forward) u else (1.0f - u)

        val x0 = KEYFRAMES[iMin].evalX(uKey)
        val y0 = KEYFRAMES[iMin].evalY(uKey)
        val x1 = KEYFRAMES[iMin + 1].evalX(uKey)
        val y1 = KEYFRAMES[iMin + 1].evalY(uKey)

        val xPlay = x0 * (1f - fMin) + x1 * fMin
        val yPlay = y0 * (1f - fMin) + y1 * fMin

        if (forward) {
            out[0] = 0.5f + 0.5f * xPlay
        } else {
            out[0] = 0.5f - 0.5f * xPlay
        }
        out[1] = PlayLikeCurlModel.FRONT_DEPTH + yPlay * (MAX_CURL_HEIGHT * 0.75f)
    }

    /** Resolves the horizontal X position of the fold crest in landscape spread space [0, 1]. */
    fun foldEdgeXLandscape(progress: Float, forward: Boolean): Float {
        val pt = FloatArray(2)
        val uSample = if (forward) {
            max(0f, min(1f, 1f - progress * 0.5f))
        } else {
            max(0f, min(1f, progress * 0.5f))
        }
        evaluateLandscape(uSample, progress, forward, pt)
        return pt[0]
    }

    /** Resolves the depth Z of the fold apex in landscape spread space. */
    fun foldEdgeDepthLandscape(progress: Float, forward: Boolean): Float {
        val pt = FloatArray(2)
        evaluateLandscape(0.5f, progress, forward, pt)
        return max(0f, pt[1])
    }

    /** Resolves the horizontal X position of the fold crest for drop shadow positioning. */
    fun foldEdgeX(role: PageRole, curlPosition: Float): Float {
        val progress = progressOf(role, curlPosition)
        val forward = (role == PageRole.FRONT)
        val pt = FloatArray(2)
        val uSample = max(0f, min(1f, 1f - progress * 0.5f))
        evaluate(uSample, progress, forward, pt)
        return pt[0]
    }

    /** Resolves the depth Z of the fold apex for shadow projection. */
    fun foldEdgeDepth(role: PageRole, curlPosition: Float): Float {
        val progress = progressOf(role, curlPosition)
        val forward = (role == PageRole.FRONT)
        val pt = FloatArray(2)
        evaluate(0.5f, progress, forward, pt)
        return max(0f, pt[1])
    }

    private fun progressOf(role: PageRole, curlPosition: Float): Float {
        if (role == PageRole.RIGHT) return 0f
        val range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
        val p = if (role == PageRole.LEFT) {
            (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range
        } else {
            (PlayLikeCurlModel.GRID - curlPosition) / range
        }
        return max(0f, min(1f, p))
    }

    private class CubicBezier(private val b: FloatArray) {
        fun evaluate(t: Float, out: FloatArray) {
            out[0] = evalX(t)
            out[1] = evalY(t)
        }

        fun evalX(t: Float): Float {
            val f2 = 1.0f - t
            val f3 = f2 * f2 * f2
            val f4 = b[6] * f3
            val f5 = 3.0f * t * f2
            val f6 = f2 * f5
            val f7 = b[4] * f6
            val f8 = f5 * t
            val f9 = b[2] * f8
            val f10 = t * t * t
            val f11 = b[0] * f10
            return f4 + f7 + f9 + f11
        }

        fun evalY(t: Float): Float {
            val f2 = 1.0f - t
            val f3 = f2 * f2 * f2
            val f5 = 3.0f * t * f2
            val f6 = f2 * f5
            val f8 = f5 * t
            val f10 = t * t * t
            val f12 = f3 * b[7]
            val f13 = f6 * b[5]
            return f12 + f13 + f8 * b[3] + f10 * b[1]
        }
    }

    private class SplineKeyframe(rawSegs: Array<CubicBezier>) {
        private val tableX = FloatArray(101)
        private val tableY = FloatArray(101)

        init {
            val segs = arrayOf(rawSegs[2], rawSegs[1], rawSegs[0])
            val samplesPerSeg = 100
            val totalSamples = samplesPerSeg * 3
            val sampleX = FloatArray(totalSamples + 1)
            val sampleY = FloatArray(totalSamples + 1)
            val cumDist = FloatArray(totalSamples + 1)

            val pt = FloatArray(2)
            var idx = 0

            for (s in 0 until 3) {
                val startI = if (s == 0) 0 else 1
                for (i in startI..samplesPerSeg) {
                    val t = i / samplesPerSeg.toFloat()
                    segs[s].evaluate(t, pt)
                    sampleX[idx] = pt[0]
                    sampleY[idx] = pt[1]
                    if (idx == 0) {
                        cumDist[0] = 0f
                    } else {
                        val dx = sampleX[idx] - sampleX[idx - 1]
                        val dy = sampleY[idx] - sampleY[idx - 1]
                        cumDist[idx] = cumDist[idx - 1] + hypot(dx, dy)
                    }
                    idx++
                }
            }

            val n = idx
            var totalLen = cumDist[n - 1]
            if (totalLen <= 0f) totalLen = 1f

            tableX[0] = sampleX[0]
            tableY[0] = sampleY[0]
            var sampleIdx = 0

            for (k in 1 until 100) {
                val targetDist = (k / 100f) * totalLen
                while (sampleIdx < n - 2 && cumDist[sampleIdx + 1] < targetDist) {
                    sampleIdx++
                }
                val segDist = cumDist[sampleIdx + 1] - cumDist[sampleIdx]
                val frac = if (segDist > 1e-6f) (targetDist - cumDist[sampleIdx]) / segDist else 0f
                tableX[k] = sampleX[sampleIdx] + frac * (sampleX[sampleIdx + 1] - sampleX[sampleIdx])
                tableY[k] = sampleY[sampleIdx] + frac * (sampleY[sampleIdx + 1] - sampleY[sampleIdx])
            }

            tableX[100] = sampleX[n - 1]
            tableY[100] = sampleY[n - 1]
        }

        fun evalX(u: Float): Float {
            val clamped = max(0f, min(1f, u))
            val fIdx = clamped * 100f
            val i = min(99, fIdx.toInt())
            val frac = fIdx - i
            return tableX[i] * (1f - frac) + tableX[i + 1] * frac
        }

        fun evalY(u: Float): Float {
            val clamped = max(0f, min(1f, u))
            val fIdx = clamped * 100f
            val i = min(99, fIdx.toInt())
            val frac = fIdx - i
            return tableY[i] * (1f - frac) + tableY[i + 1] * frac
        }
    }
}
