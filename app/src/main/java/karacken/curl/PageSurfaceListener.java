package karacken.curl;

/**
 * Event boundary between the renderer and the reader that owns page preparation.
 *
 * <p>All callbacks are delivered on the Android main thread. Callbacks for one generation are
 * ordered. A generation accepted by the surface receives exactly one terminal
 * {@link #onDeckReleased(long, DeckReleaseReason)} callback.
 */
public interface PageSurfaceListener {
    default void onCapabilitiesAvailable(RenderCapabilities capabilities) {}

    default void onDeckPrepared(long generationId) {}

    default void onDeckRejected(long generationId, DeckRejectionReason reason) {}

    default void onDeckReleased(long generationId, DeckReleaseReason reason) {}

    default void onRenderFailure(RenderFailure failure) {}

    default void onGestureRejected(
            long generationId,
            GestureRejectionReason reason) {}

    default void onGestureRejected(
            long gestureId,
            long generationId,
            GestureRejectionReason reason) {
        onGestureRejected(generationId, reason);
    }

    default void onGestureCancelled(
            long gestureId,
            long generationId) {}

    default void onSettlementStarted(
            long generationId,
            String sourceLogicalPageId,
            String targetLogicalPageId,
            PageChange pageChange) {}

    default void onSettlementStarted(
            long gestureId,
            long generationId,
            String sourceLogicalPageId,
            String targetLogicalPageId,
            PageChange pageChange) {
        onSettlementStarted(
                generationId,
                sourceLogicalPageId,
                targetLogicalPageId,
                pageChange);
    }

    default void onSettlementCompleted(
            long generationId,
            String currentLogicalPageId,
            int currentOrdinal,
            PageChange pageChange) {}

    default void onSettlementCompleted(
            long gestureId,
            long generationId,
            String currentLogicalPageId,
            int currentOrdinal,
            PageChange pageChange) {
        onSettlementCompleted(
                generationId,
                currentLogicalPageId,
                currentOrdinal,
                pageChange);
    }

    default void onSettlementCancelled(
            long generationId,
            String currentLogicalPageId) {}

    default void onSettlementCancelled(
            long gestureId,
            long generationId,
            String currentLogicalPageId) {
        onSettlementCancelled(generationId, currentLogicalPageId);
    }
}
