package karacken.curl;

/**
 * Authentic 3D page-curl deformation engine implementing Google Play Books'
 * dual-arc cubic Bézier spline model with exact arc-length parameterization.
 *
 * <p>Key structural fixes:
 * <ul>
 *   <li><b>Arc-Length Inversion:</b> Replaced the divergent bisection search with exact
 *       cumulative-chord arc-length reparameterization, eliminating edge clamping and distortion.</li>
 *   <li><b>Single-Page Viewport Mapping:</b> Correctly maps the spine to $X = 0.0$ and outer edge
 *       to $X = 1.0$, preventing two-page spread squashing in portrait mode.</li>
 *   <li><b>Inactive Previous Page Bounding:</b> Binds inactive left pages at $X \le 0.0$ so they
 *       never overlap the screen or occlude active forward turns.</li>
 *   <li><b>Conical Tilt:</b> Seamlessly integrates dynamic touch $Y$ tilt for realistic corner-lead peeling.</li>
 * </ul>
 */
public final class PlayBooksBezierCurl {
    /** Peak elevation of the curling page above the book plane in camera units. */
    public static final float MAX_CURL_HEIGHT = 0.35f;

    /** Maximum conical tilt angle factor across the vertical axis. */
    public static final float MAX_TILT = 0.14f;

    /** Default thumb-turn tilt (slight pull from lower half where reader thumb rests). */
    public static final float DEFAULT_THUMB_TILT = 0.20f;

    private static final int[] RAW_BEZIER = {
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
    };

    private static final float ARC_SCALE = 0.84221236f;
    private static final SplineKeyframe[] KEYFRAMES = new SplineKeyframe[11];

    static {
        for (int k = 0; k < 11; k++) {
            CubicBezier[] segs = new CubicBezier[3];
            int base = k * 24;
            for (int s = 0; s < 3; s++) {
                float[] pts = new float[8];
                int sBase = base + s * 8;
                for (int i = 0; i < 8; i++) {
                    pts[i] = RAW_BEZIER[sBase + i] * 0.001f;
                }
                segs[s] = new CubicBezier(pts);
            }
            KEYFRAMES[k] = new SplineKeyframe(segs);
        }
    }

    private static volatile float currentTouchTilt = DEFAULT_THUMB_TILT;

    private PlayBooksBezierCurl() {}

    /**
     * Sets the active gesture touch tilt in [-1, 1] based on touch $Y$ position.
     */
    public static void setTouchTilt(float tilt) {
        currentTouchTilt = Math.max(-1f, Math.min(1f, tilt));
    }

    public static float getTouchTilt() {
        return currentTouchTilt;
    }

    public static void resetTouchTilt() {
        currentTouchTilt = DEFAULT_THUMB_TILT;
    }

    /**
     * Updates vertex positions for a page mesh using the Bézier spline model.
     */
    public static void update(PageGeometry page, float curlPosition, boolean active) {
        int grid = PlayLikeCurlModel.GRID;
        float bitmapRatio = page.getBitmapRatio();
        float heightCorrection = (bitmapRatio - 1f) / 2f;
        PageRole role = page.getRole();
        float[] positions = page.getPositions();

        if (!active) {
            // Left page when inactive sits folded away to the left (X <= 0)
            // so it never occludes the active page or viewport
            float baseDepth = role == PageRole.LEFT
                    ? PlayLikeCurlModel.LEFT_DEPTH
                    : (role == PageRole.FRONT ? PlayLikeCurlModel.FRONT_DEPTH : PlayLikeCurlModel.RIGHT_DEPTH);
            float startX = (role == PageRole.LEFT) ? -1f : 0f;

            for (int row = 0; row <= grid; row++) {
                float v = row / (float) grid;
                float y = v * bitmapRatio - heightCorrection;
                for (int col = 0; col <= grid; col++) {
                    int offset = 3 * (row * (grid + 1) + col);
                    positions[offset] = startX + col / (float) grid;
                    positions[offset + 1] = y;
                    positions[offset + 2] = baseDepth;
                }
            }
            return;
        }

        float progress = progressOf(role, curlPosition);
        float tilt = currentTouchTilt;
        boolean forward = (role == PageRole.FRONT);

        float[] point = new float[2];

        for (int row = 0; row <= grid; row++) {
            float v = row / (float) grid;
            float y = v * bitmapRatio - heightCorrection;
            float slantEnvelope = (float) Math.sin(Math.PI * progress);
            float effProgress = Math.max(0f, Math.min(1f, progress + tilt * MAX_TILT * (v - 0.5f) * slantEnvelope));

            for (int col = 0; col <= grid; col++) {
                float u = col / (float) grid;
                int offset = 3 * (row * (grid + 1) + col);

                if (role == PageRole.FRONT || role == PageRole.LEFT) {
                    evaluate(u, effProgress, forward, point);
                    positions[offset] = point[0];
                    positions[offset + 2] = point[1];
                } else {
                    positions[offset] = u;
                    positions[offset + 2] = PlayLikeCurlModel.RIGHT_DEPTH;
                }
                positions[offset + 1] = y;
            }
        }
    }

    /**
     * Evaluates the 3D sheet position in single-page coordinates:
     * <ul>
     *   <li>Spine at $X = 0.0$, outer edge at $X = 1.0$.</li>
     *   <li>At progress 0.0: flat on right side [X = 0.0 to 1.0, Z = baseDepth].</li>
     *   <li>At progress 0.5: arched across spine in 3D (Z > 0).</li>
     *   <li>At progress 1.0: turned flat to left [X = 0.0 to -1.0, Z = baseDepth].</li>
     * </ul>
     */
    public static void evaluate(float u, float progress, boolean forward, float[] out) {
        float p = Math.max(0f, Math.min(1f, progress));
        int iMin = Math.min(9, Math.max(0, (int) (10f * p)));
        float fMin = Math.min(1f, Math.max(0f, (p - iMin * 0.1f) * 10f));

        float x0 = KEYFRAMES[iMin].evalX(u);
        float y0 = KEYFRAMES[iMin].evalY(u);
        float x1 = KEYFRAMES[iMin + 1].evalX(u);
        float y1 = KEYFRAMES[iMin + 1].evalY(u);

        float xPlay = x0 * (1f - fMin) + x1 * fMin;
        float yPlay = y0 * (1f - fMin) + y1 * fMin;

        if (!forward) {
            xPlay = -xPlay;
        }

        // In single-page mode, page occupies X in [0.0, 1.0] when flat on right
        out[0] = xPlay;

        // Depth elevation
        float baseDepth = forward ? PlayLikeCurlModel.FRONT_DEPTH : PlayLikeCurlModel.LEFT_DEPTH;
        out[1] = baseDepth + yPlay * MAX_CURL_HEIGHT;
    }

    /**
     * Updates vertex positions for a landscape two-page spread turning leaf.
     * <p>
     * The center spine is at X = 0.5.
     * Forward turn: leaf sweeps from the right page [X = 0.5 to 1.0] across spine (X = 0.5)
     * and lands flat on the left page [X = 0.0 to 0.5].
     * Backward turn: leaf sweeps from the left page [X = 0.0 to 0.5] across spine (X = 0.5)
     * and lands flat on the right page [X = 0.5 to 1.0].
     */
    public static void updateLandscape(PageGeometry page, float progress, boolean forward) {
        int grid = PlayLikeCurlModel.GRID;
        float bitmapRatio = page.getBitmapRatio();
        float pageHeight = 0.5f * bitmapRatio;
        float heightCorrection = (pageHeight - 1f) / 2f;
        float[] positions = page.getPositions();
        float p = Math.max(0f, Math.min(1f, progress));
        float tilt = currentTouchTilt;
        float[] point = new float[2];

        for (int row = 0; row <= grid; row++) {
            float v = row / (float) grid;
            float y = v * pageHeight - heightCorrection;
            float slantEnvelope = (float) Math.sin(Math.PI * p);
            float effProgress = Math.max(0f, Math.min(1f, p + tilt * MAX_TILT * (v - 0.5f) * slantEnvelope));

            for (int col = 0; col <= grid; col++) {
                float u = col / (float) grid;
                int offset = 3 * (row * (grid + 1) + col);

                evaluateLandscape(u, effProgress, forward, point);
                positions[offset] = point[0];
                positions[offset + 1] = y;
                positions[offset + 2] = point[1];
            }
        }
    }

    /**
     * Evaluates 3D sheet position in full-spread landscape coordinates [0.0, 1.0] where spine is at X = 0.5:
     * - Right page: [0.5, 1.0]
     * - Left page:  [0.0, 0.5]
     */
    public static void evaluateLandscape(float u, float progress, boolean forward, float[] out) {
        float p = Math.max(0f, Math.min(1f, progress));
        int iMin = Math.min(9, Math.max(0, (int) (10f * p)));
        float fMin = Math.min(1f, Math.max(0f, (p - iMin * 0.1f) * 10f));

        // For forward turn (right page peeling left): col 0 is spine (u=0), col grid is outer edge (u=1).
        // For backward turn (left page peeling right): col 0 is outer edge (uKey=1), col grid is spine (uKey=0).
        float uKey = forward ? u : (1.0f - u);

        float x0 = KEYFRAMES[iMin].evalX(uKey);
        float y0 = KEYFRAMES[iMin].evalY(uKey);
        float x1 = KEYFRAMES[iMin + 1].evalX(uKey);
        float y1 = KEYFRAMES[iMin + 1].evalY(uKey);

        float xPlay = x0 * (1f - fMin) + x1 * fMin;
        float yPlay = y0 * (1f - fMin) + y1 * fMin;

        // In 2-page spread: spine is at X = 0.5.
        // Forward: starts at 0.5 + 0.5 * 1.0 = 1.0, lands at 0.5 + 0.5 * (-1.0) = 0.0.
        // Backward: starts at 0.5 - 0.5 * 1.0 = 0.0, lands at 0.5 - 0.5 * (-1.0) = 1.0.
        if (forward) {
            out[0] = 0.5f + 0.5f * xPlay;
        } else {
            out[0] = 0.5f - 0.5f * xPlay;
        }
        out[1] = PlayLikeCurlModel.FRONT_DEPTH + yPlay * (MAX_CURL_HEIGHT * 0.75f);
    }

    /**
     * Resolves the horizontal X position of the fold crest in landscape spread space [0, 1].
     */
    public static float foldEdgeXLandscape(float progress, boolean forward) {
        float[] pt = new float[2];
        float uSample = forward
                ? Math.max(0f, Math.min(1f, 1f - progress * 0.5f))
                : Math.max(0f, Math.min(1f, progress * 0.5f));
        evaluateLandscape(uSample, progress, forward, pt);
        return pt[0];
    }

    /**
     * Resolves the depth Z of the fold apex in landscape spread space.
     */
    public static float foldEdgeDepthLandscape(float progress, boolean forward) {
        float[] pt = new float[2];
        evaluateLandscape(0.5f, progress, forward, pt);
        return Math.max(0f, pt[1]);
    }

    /**
     * Resolves the horizontal X position of the fold crest for drop shadow positioning.
     */
    public static float foldEdgeX(PageRole role, float curlPosition) {
        float progress = progressOf(role, curlPosition);
        boolean forward = (role == PageRole.FRONT);
        float[] pt = new float[2];
        float uSample = Math.max(0f, Math.min(1f, 1f - progress * 0.5f));
        evaluate(uSample, progress, forward, pt);
        return pt[0];
    }

    /**
     * Resolves the depth Z of the fold apex for shadow projection.
     */
    public static float foldEdgeDepth(PageRole role, float curlPosition) {
        float progress = progressOf(role, curlPosition);
        boolean forward = (role == PageRole.FRONT);
        float[] pt = new float[2];
        evaluate(0.5f, progress, forward, pt);
        return Math.max(0f, pt[1]);
    }

    private static float progressOf(PageRole role, float curlPosition) {
        if (role == PageRole.RIGHT) return 0f;
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float p = role == PageRole.LEFT
                ? (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range
                : (PlayLikeCurlModel.GRID - curlPosition) / range;
        return Math.max(0f, Math.min(1f, p));
    }

    private static final class CubicBezier {
        private final float[] b;

        CubicBezier(float[] pts) {
            this.b = pts;
        }

        void evaluate(float t, float[] out) {
            out[0] = evalX(t);
            out[1] = evalY(t);
        }

        float evalX(float t) {
            float f2 = 1.0f - t;
            float f3 = f2 * f2 * f2;
            float f4 = b[6] * f3;
            float f5 = 3.0f * t * f2;
            float f6 = f2 * f5;
            float f7 = b[4] * f6;
            float f8 = f5 * t;
            float f9 = b[2] * f8;
            float f10 = t * t * t;
            float f11 = b[0] * f10;
            return f4 + f7 + f9 + f11;
        }

        float evalY(float t) {
            float f2 = 1.0f - t;
            float f3 = f2 * f2 * f2;
            float f5 = 3.0f * t * f2;
            float f6 = f2 * f5;
            float f8 = f5 * t;
            float f10 = t * t * t;
            float f12 = f3 * b[7];
            float f13 = f6 * b[5];
            return f12 + f13 + f8 * b[3] + f10 * b[1];
        }
    }

    private static final class SplineKeyframe {
        private final float[] tableX = new float[101];
        private final float[] tableY = new float[101];

        SplineKeyframe(CubicBezier[] rawSegs) {
            // Order from spine (0.0) out to edge (1.0): rawSegs[2], rawSegs[1], rawSegs[0]
            CubicBezier[] segs = new CubicBezier[] {rawSegs[2], rawSegs[1], rawSegs[0]};

            // Densely sample the 3 connected segments
            int samplesPerSeg = 100;
            int totalSamples = samplesPerSeg * 3;
            float[] sampleX = new float[totalSamples + 1];
            float[] sampleY = new float[totalSamples + 1];
            float[] cumDist = new float[totalSamples + 1];

            float[] pt = new float[2];
            int idx = 0;

            for (int s = 0; s < 3; s++) {
                int startI = (s == 0) ? 0 : 1;
                for (int i = startI; i <= samplesPerSeg; i++) {
                    float t = i / (float) samplesPerSeg;
                    segs[s].evaluate(t, pt);
                    sampleX[idx] = pt[0];
                    sampleY[idx] = pt[1];
                    if (idx == 0) {
                        cumDist[0] = 0f;
                    } else {
                        float dx = sampleX[idx] - sampleX[idx - 1];
                        float dy = sampleY[idx] - sampleY[idx - 1];
                        cumDist[idx] = cumDist[idx - 1] + (float) Math.hypot(dx, dy);
                    }
                    idx++;
                }
            }

            int n = idx;
            float totalLen = cumDist[n - 1];
            if (totalLen <= 0f) totalLen = 1f;

            // Generate 101 equidistant arc-length samples
            tableX[0] = sampleX[0];
            tableY[0] = sampleY[0];
            int sampleIdx = 0;

            for (int k = 1; k < 100; k++) {
                float targetDist = (k / 100f) * totalLen;
                while (sampleIdx < n - 2 && cumDist[sampleIdx + 1] < targetDist) {
                    sampleIdx++;
                }
                float segDist = cumDist[sampleIdx + 1] - cumDist[sampleIdx];
                float frac = (segDist > 1e-6f) ? (targetDist - cumDist[sampleIdx]) / segDist : 0f;
                tableX[k] = sampleX[sampleIdx] + frac * (sampleX[sampleIdx + 1] - sampleX[sampleIdx]);
                tableY[k] = sampleY[sampleIdx] + frac * (sampleY[sampleIdx + 1] - sampleY[sampleIdx]);
            }

            tableX[100] = sampleX[n - 1];
            tableY[100] = sampleY[n - 1];
        }

        float evalX(float u) {
            float clamped = Math.max(0f, Math.min(1f, u));
            float fIdx = clamped * 100f;
            int i = Math.min(99, (int) fIdx);
            float frac = fIdx - i;
            return tableX[i] * (1f - frac) + tableX[i + 1] * frac;
        }

        float evalY(float u) {
            float clamped = Math.max(0f, Math.min(1f, u));
            float fIdx = clamped * 100f;
            int i = Math.min(99, (int) fIdx);
            float frac = fIdx - i;
            return tableY[i] * (1f - frac) + tableY[i + 1] * frac;
        }
    }
}
