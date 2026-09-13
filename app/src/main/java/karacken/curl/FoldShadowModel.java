package karacken.curl;

/** Pure fold-edge shadow projection shared by portrait and landscape rendering. */
final class FoldShadowModel {
    static final float MAX_OPACITY = 0.30f;
    static final float WIDTH = 0.07f;
    static final float SHADOW_DEPTH = PlayLikeCurlModel.RIGHT_DEPTH + 0.00025f;

    private FoldShadowModel() {
    }

    static State resolve(PageRole role, float curlPosition, boolean mirrored) {
        float progress = progress(role, curlPosition);
        float opacity = MAX_OPACITY * (float) Math.sin(Math.PI * progress);
        float edge = PlayLikeCurlGeometry.foldEdgeX(role, curlPosition);
        float edgeDepth = PlayLikeCurlGeometry.foldEdgeDepth(role, curlPosition);
        if (mirrored) edge = 1f - edge;
        edge = PlayLikeCurlGeometry.projectXOntoDepthPlane(
                edge,
                edgeDepth,
                SHADOW_DEPTH);
        return mirrored
                ? new State(edge - WIDTH, edge, edge, opacity, false)
                : new State(edge, edge + WIDTH, edge, opacity, true);
    }

    private static float progress(PageRole role, float curlPosition) {
        if (role == PageRole.RIGHT) return 0f;
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float value = role == PageRole.LEFT
                ? (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range
                : (PlayLikeCurlModel.GRID - curlPosition) / range;
        return Math.max(0f, Math.min(1f, value));
    }

    static final class State {
        private final float startX;
        private final float endX;
        private final float foldEdgeX;
        private final float opacity;
        private final boolean darkAtStart;

        State(float startX, float endX, float foldEdgeX, float opacity, boolean darkAtStart) {
            this.startX = startX;
            this.endX = endX;
            this.foldEdgeX = foldEdgeX;
            this.opacity = opacity;
            this.darkAtStart = darkAtStart;
        }

        float getStartX() {
            return startX;
        }

        float getEndX() {
            return endX;
        }

        float getFoldEdgeX() {
            return foldEdgeX;
        }

        float getOpacity() {
            return opacity;
        }

        boolean isDarkAtStart() {
            return darkAtStart;
        }
    }
}
