package karacken.curl;

/**
 * Single-leaf page-turn transition for two-page landscape spreads.
 *
 * The turning leaf starts flat on the right half (forward) or left half (backward),
 * lifts into a curl that travels across the full spread, and settles flat on the
 * opposite half - exactly like a real book and Google Play Books. There is no
 * two-phase hand-off: one curl position drives the whole animation.
 */
final class LandscapeSpreadTransition {
    private final boolean forward;
    private final float progress;
    private final float leafCurlPosition;

    private LandscapeSpreadTransition(boolean forward, float progress, float leafCurlPosition) {
        this.forward = forward;
        this.progress = progress;
        this.leafCurlPosition = leafCurlPosition;
    }

    static LandscapeSpreadTransition from(PlayLikeCurlModel motion) {
        boolean forward = motion.getActivePage() != ActivePage.LEFT;
        float curlPosition = forward
                ? motion.getFrontPage().getCurlPosition()
                : motion.getLeftPage().getCurlPosition();
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float progress = forward
                ? (PlayLikeCurlModel.GRID - curlPosition) / range
                : (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range;
        progress = clamp(progress);
        return new LandscapeSpreadTransition(forward, progress, curlPosition);
    }

    boolean isForward() {
        return forward;
    }

    float getProgress() {
        return progress;
    }

    /** Curl position for the single turning leaf. */
    float getLeafCurlPosition() {
        return leafCurlPosition;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
