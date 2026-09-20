package karacken.curl

/**
 * Event boundary between the renderer and the reader that owns page preparation.
 *
 * All callbacks are delivered on the Android main thread. Callbacks for one generation are
 * ordered. A generation accepted by the surface receives exactly one terminal
 * [onDeckReleased] callback.
 */
interface PageSurfaceListener {
    fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {}

    fun onFirstFrameRendered() {}

    fun onDeckPrepared(generationId: Long) {}

    fun onDeckRejected(generationId: Long, reason: DeckRejectionReason) {}

    fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {}

    fun onRenderFailure(failure: RenderFailure) {}

    fun onGestureRejected(
        generationId: Long,
        reason: GestureRejectionReason
    ) {}

    fun onGestureRejected(
        gestureId: Long,
        generationId: Long,
        reason: GestureRejectionReason
    ) {
        onGestureRejected(generationId, reason)
    }

    fun onGestureCancelled(
        gestureId: Long,
        generationId: Long
    ) {}

    fun onSettlementStarted(
        generationId: Long,
        sourceLogicalPageId: String,
        targetLogicalPageId: String,
        pageChange: PageChange
    ) {}

    fun onSettlementStarted(
        gestureId: Long,
        generationId: Long,
        sourceLogicalPageId: String,
        targetLogicalPageId: String,
        pageChange: PageChange
    ) {
        onSettlementStarted(
            generationId,
            sourceLogicalPageId,
            targetLogicalPageId,
            pageChange
        )
    }

    fun onSettlementCompleted(
        generationId: Long,
        currentLogicalPageId: String,
        currentOrdinal: Int,
        pageChange: PageChange
    ) {}

    fun onSettlementCompleted(
        gestureId: Long,
        generationId: Long,
        currentLogicalPageId: String,
        currentOrdinal: Int,
        pageChange: PageChange
    ) {
        onSettlementCompleted(
            generationId,
            currentLogicalPageId,
            currentOrdinal,
            pageChange
        )
    }

    fun onSettlementCancelled(
        generationId: Long,
        currentLogicalPageId: String
    ) {}

    fun onSettlementCancelled(
        gestureId: Long,
        generationId: Long,
        currentLogicalPageId: String
    ) {
        onSettlementCancelled(generationId, currentLogicalPageId)
    }
}
