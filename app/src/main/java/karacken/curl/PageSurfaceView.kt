package karacken.curl

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import java.util.LinkedHashSet
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Production page-curl surface.
 *
 * The client owns bitmap decoding and page identity. This view owns only interaction,
 * settlement, and GL resource lifecycle. Public mutations must run on the Android main thread.
 */
open class PageSurfaceView @JvmOverloads constructor(
    context: Context,
    initialPaperColor: Int = 0xFFFFFFFF.toInt()
) : GLSurfaceView(context) {

    private val deckCoordinator = PageDeckCoordinator<Bitmap>()
    private val leaseRegistry = DeckLeaseRegistry()
    private val preparedGenerations = LinkedHashSet<Long>()
    private val renderer: PageRenderer
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    var pageSurfaceListener: PageSurfaceListener = NO_OP_LISTENER
        set(value) {
            requireMainThread()
            field = value
            val capabilities = renderCapabilities
            if (capabilities != null) {
                field.onCapabilitiesAvailable(capabilities)
            }
        }

    var renderCapabilities: RenderCapabilities? = null
        private set

    private var settlementAnimator: ValueAnimator? = null
    private var activeSettlementContext: SettlementContext? = null
    private var velocityTracker: VelocityTracker? = null
    private var settlementRunning = false
    private var gestureAccepted = false
    private var gestureMoved = false
    private var surfaceVisible = true
    private var attached = false
    private var disposed = false
    private var activeGestureId = NO_GESTURE_ID
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    var onPageChangeListener: OnPageChangeListener? = null

    init {
        renderer = PageRenderer(object : PageRenderer.Events {
            override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) {
                post { handleCapabilitiesAvailable(capabilities) }
            }

            override fun onFirstFrameRendered() {
                post { handleFirstFrameRendered() }
            }

            override fun onDeckPrepared(generationId: Long) {
                post { handleDeckPrepared(generationId) }
            }

            override fun onDeckReleased(generationId: Long, reason: DeckReleaseReason) {
                leaseRegistry.markReleaseRequested(generationId, reason)
                post { handleDeckReleased(generationId, reason) }
            }

            override fun onRenderFailure(failure: RenderFailure) {
                post { handleRenderFailure(failure) }
            }
        }, initialPaperColor)

        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
        isClickable = true
        isFocusable = true
    }

    /** Marks the surface active for interaction and resumes its GL thread. */
    fun attach() {
        requireMainThread()
        if (disposed || attached) return
        attached = true
        onResume()
        requestRender()
    }

    /** Cancels interaction and pauses frame production without releasing the active deck. */
    fun detach() {
        requireMainThread()
        if (!attached) return
        attached = false
        cancelGesture()
        queueDeckRelease(deckCoordinator.releasePending(DeckReleaseReason.SESSION_DETACHED))
        requestRender()
        onPause()
    }

    /**
     * Submits one complete interaction window.
     *
     * An idle submission becomes active after upload. During settlement, exactly one
     * replacement deck is retained and prepared. Acceptance starts a bitmap lease: every bitmap
     * in the deck must remain immutable and unrecycled until
     * [PageSurfaceListener.onDeckReleased] reports the generation.
     * Rejected decks are never acquired.
     */
    fun submitDeck(deck: PageDeck<Bitmap>) {
        requireMainThread()
        if (disposed) {
            pageSurfaceListener.onDeckRejected(deck.generationId, DeckRejectionReason.DISPOSED)
            return
        }
        if (!attached) {
            pageSurfaceListener.onDeckRejected(deck.generationId, DeckRejectionReason.SESSION_DETACHED)
            return
        }
        if (renderCapabilities == null) {
            pageSurfaceListener.onDeckRejected(deck.generationId, DeckRejectionReason.CAPABILITIES_UNAVAILABLE)
            return
        }
        val offer = deckCoordinator.offer(deck)
        if (offer.placement == PageDeckCoordinator.Placement.REJECTED) {
            pageSurfaceListener.onDeckRejected(deck.generationId, offer.rejectionReason!!)
            return
        }
        if (offer.placement == PageDeckCoordinator.Placement.UNCHANGED) {
            return
        }
        leaseRegistry.acquire(deck.generationId, pageSurfaceListener)
        val activateWhenPrepared = offer.placement == PageDeckCoordinator.Placement.ACTIVE
        for (release in offer.releases) {
            if (activateWhenPrepared && release.reason == DeckReleaseReason.REPLACED) {
                continue
            }
            queueDeckRelease(release)
        }
        preparedGenerations.remove(deck.generationId)
        queueEvent { renderer.prepareDeck(deck, activateWhenPrepared) }
        requestRender()
    }

    /** Supplies the current pixel viewport without changing page layout. */
    fun setViewport(widthPx: Int, heightPx: Int) {
        requireMainThread()
        require(widthPx > 0 && heightPx > 0) { "Viewport dimensions must be positive" }
        queueEvent { renderer.setViewport(widthPx, heightPx) }
        requestRender()
    }

    /** Sets the initial background clear color before GL surface initialization. */
    fun setInitialBackgroundColor(red: Int, green: Int, blue: Int) {
        renderer.setInitialBackgroundColor(red, green, blue)
    }

    /** Bunko: matches the GL clear color to the reader paper color (0-255 channels). */
    fun setBackgroundColor(red: Int, green: Int, blue: Int) {
        requireMainThread()
        queueEvent { renderer.setBackgroundColor(red, green, blue) }
        requestRender()
    }

    /** Changes the active-plus-pending GPU budget before any deck is acquired. */
    fun setGpuBudgetBytes(gpuBudgetBytes: Long) {
        requireMainThread()
        require(gpuBudgetBytes > 0) { "gpuBudgetBytes must be positive" }
        if (deckCoordinator.getActiveDeck() != null || deckCoordinator.getPendingDeck() != null) {
            throw IllegalStateException("GPU budget cannot change while a deck is retained")
        }
        queueEvent { renderer.setGpuBudgetBytes(gpuBudgetBytes) }
        requestRender()
    }

    /** Controls rendering and gesture eligibility without discarding the prepared deck. */
    fun setVisible(visible: Boolean) {
        requireMainThread()
        surfaceVisible = visible
        if (!visible) {
            cancelGesture()
        } else {
            requestRender()
        }
    }

    /** Cancels any partial gesture or settlement without navigating. */
    fun cancelGesture() {
        cancelGesture(activeGestureId)
    }

    /** Cancels the identified gesture or settlement without navigating. */
    fun cancelGesture(gestureId: Long) {
        requireMainThread()
        val cancelledSettlement = cancelSettlementAnimator()
        val cancelledGestureId = if (activeGestureId != NO_GESTURE_ID) activeGestureId else gestureId
        val generationId = activeGenerationId()
        val cancelledGesture = gestureAccepted
        val interaction = interactionModelOrNull()
        interaction?.cancelGesture()
        PlayLikeBezierCurl.resetTouchTilt()
        deckCoordinator.cancelSettlement()
        gestureAccepted = false
        gestureMoved = false
        recycleVelocityTracker()
        requestRender()
        notifySettlementCancelled(cancelledSettlement)
        if (cancelledSettlement == null && cancelledGesture) {
            notifyGestureCancelled(cancelledGestureId, generationId)
        }
        activeGestureId = NO_GESTURE_ID
    }

    /** Starts the same reference settlement used by a completed edge drag. */
    @JvmOverloads
    fun turn(pageChange: PageChange, gestureId: Long = NO_GESTURE_ID): Boolean {
        requireMainThread()
        if (settlementRunning) {
            settlementAnimator?.end()
        }
        activeGestureId = gestureId
        if (pageChange != PageChange.PREVIOUS && pageChange != PageChange.NEXT) {
            activeGestureId = NO_GESTURE_ID
            return false
        }
        if (!gestureReady()) {
            rejectGesture(gestureId)
            activeGestureId = NO_GESTURE_ID
            return false
        }
        val interaction = interactionModelOrNull()
        if (interaction == null) {
            activeGestureId = NO_GESTURE_ID
            return false
        }
        PlayLikeBezierCurl.setTouchTilt(PlayLikeBezierCurl.DEFAULT_THUMB_TILT)
        val settlement = interaction.turn(pageChange)
        if (settlement.pageChange == PageChange.NONE) {
            interaction.cancelGesture()
            requestRender()
            activeGestureId = NO_GESTURE_ID
            return false
        }
        settle(settlement)
        return true
    }

    /** Explicitly activates a prepared replacement after an idle generation change. */
    fun activatePendingDeck() {
        requireMainThread()
        val promotion = deckCoordinator.activatePending()
        val activated = promotion.activatedDeck ?: return
        markPromotionRelease(promotion)
        queueEvent { renderer.activateDeck(activated.generationId) }
        requestRender()
    }

    /** Releases a retained active or replacement deck and its GL textures. */
    fun releaseDeck(generationId: Long) {
        requireMainThread()
        preparedGenerations.remove(generationId)
        val release = deckCoordinator.release(generationId)
        queueDeckRelease(release)
        requestRender()
    }

    /** Idempotently releases renderer, gesture, and deck state. */
    fun dispose() {
        requireMainThread()
        if (disposed) return
        disposed = true
        val cancelledSettlement = cancelSettlementAnimator()
        recycleVelocityTracker()
        preparedGenerations.clear()
        deckCoordinator.dispose()
        if (attached) {
            attached = false
            preserveEGLContextOnPause = false
            queueEvent { renderer.dispose() }
            requestRender()
            onPause()
        }
        renderer.abandonClientState()
        notifySettlementCancelled(cancelledSettlement)
        releaseAllOutstandingLeases()
    }

    fun getCurrentPosition(): Int {
        val deck = deckCoordinator.getActiveDeck()
        if (deck is PortraitPageDeck) {
            return deck.current.ordinal
        }
        if (deck is LandscapePageDeck) {
            return deck.currentLeft.ordinal
        }
        return -1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handlePageTouchEvent(event, NO_GESTURE_ID)
    }

    private fun handlePageTouchEvent(event: MotionEvent, gestureId: Long): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            if (settlementRunning) {
                settlementAnimator?.end()
            }
            activeGestureId = gestureId
            gestureAccepted = gestureReady()
            gestureMoved = false
            recycleVelocityTracker()
            if (!gestureAccepted) {
                rejectGesture(activeGestureId)
                activeGestureId = NO_GESTURE_ID
                return true
            }
        } else if (!gestureAccepted) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureAccepted = false
                gestureMoved = false
                recycleVelocityTracker()
                activeGestureId = NO_GESTURE_ID
            }
            return true
        }

        val interaction = interactionModelOrNull()
        if (interaction == null) {
            gestureAccepted = false
            gestureMoved = false
            recycleVelocityTracker()
            notifyGestureCancelled(activeGestureId, activeGenerationId())
            activeGestureId = NO_GESTURE_ID
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                gestureDownX = event.x
                gestureDownY = event.y
                val viewHeightDown = if (height > 0) height.toFloat() else 1f
                PlayLikeBezierCurl.setTouchTilt(((event.y / viewHeightDown) - 0.5f) * 2f)
                obtainVelocityTracker().addMovement(event)
                interaction.beginGesture(gestureDownX)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                obtainVelocityTracker().addMovement(event)
                val deltaX = event.x - gestureDownX
                val deltaY = event.y - gestureDownY
                if (!gestureMoved && hypot(deltaX, deltaY) >= touchSlop) {
                    gestureMoved = true
                }
                if (gestureMoved) {
                    val viewHeightMove = if (height > 0) height.toFloat() else 1f
                    PlayLikeBezierCurl.setTouchTilt(((event.y / viewHeightMove) - 0.5f) * 2f)
                    dragInteraction(event.x)
                    requestRender()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                obtainVelocityTracker().addMovement(event)
                if (!gestureMoved) {
                    gestureAccepted = false
                    interaction.cancelGesture()
                    recycleVelocityTracker()
                    requestRender()
                    performClick()
                    notifyGestureCancelled(activeGestureId, activeGenerationId())
                    activeGestureId = NO_GESTURE_ID
                    return true
                }
                dragInteraction(event.x)
                val tracker = velocityTracker!!
                tracker.computeCurrentVelocity(1000)
                val velocityX = tracker.xVelocity
                recycleVelocityTracker()
                val settlement = if (abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND) {
                    if (velocityX < 0f) interaction.flingTowardNext() else interaction.flingTowardPrevious()
                } else {
                    interaction.release()
                }
                gestureAccepted = false
                settle(settlement)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureAccepted = false
                interaction.cancelGesture()
                gestureMoved = false
                recycleVelocityTracker()
                requestRender()
                notifyGestureCancelled(activeGestureId, activeGenerationId())
                activeGestureId = NO_GESTURE_ID
                return true
            }
            else -> return true
        }
    }

    fun onPageTouchEvent(event: MotionEvent): Boolean {
        return onTouchEvent(event)
    }

    fun onPageTouchEvent(event: MotionEvent, gestureId: Long): Boolean {
        return handlePageTouchEvent(event, gestureId)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        detach()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) {
            setViewport(width, height)
        }
    }

    private fun handleDeckPrepared(generationId: Long) {
        if (disposed) return
        val owner = leaseRegistry.ownerFor(generationId) ?: return
        if (!preparedGenerations.add(generationId)) return
        trimPreparedGenerations()
        owner.onDeckPrepared(generationId)
        requestRender()
    }

    private fun handleRenderFailure(failure: RenderFailure) {
        if (disposed) return
        val generationId = failure.generationId
        val owner = if (generationId < 0) pageSurfaceListener else leaseRegistry.ownerFor(generationId)
        owner?.onRenderFailure(failure)
    }

    private fun handleCapabilitiesAvailable(capabilities: RenderCapabilities) {
        if (disposed) return
        renderCapabilities = capabilities
        pageSurfaceListener.onCapabilitiesAvailable(capabilities)
    }

    private fun handleFirstFrameRendered() {
        if (disposed) return
        pageSurfaceListener.onFirstFrameRendered()
    }

    private fun handleDeckReleased(generationId: Long, reason: DeckReleaseReason) {
        preparedGenerations.remove(generationId)
        deckCoordinator.release(generationId)
        val lease = leaseRegistry.release(generationId)
        if (lease != null) {
            val effectiveReason = lease.releaseReason ?: reason
            lease.listener.onDeckReleased(generationId, effectiveReason)
        }
    }

    private fun trimPreparedGenerations() {
        while (preparedGenerations.size > 2) {
            val iterator = preparedGenerations.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private fun gestureReady(): Boolean {
        if (disposed || !attached || !surfaceVisible) {
            return false
        }
        val active = deckCoordinator.getActiveDeck()
        return active != null && preparedGenerations.contains(active.generationId)
    }

    private fun rejectGesture(gestureId: Long) {
        val active = deckCoordinator.getActiveDeck()
        val generationId = active?.generationId ?: -1L
        val reason = when {
            disposed -> GestureRejectionReason.DISPOSED
            !attached -> GestureRejectionReason.SESSION_DETACHED
            !surfaceVisible -> GestureRejectionReason.NOT_VISIBLE
            settlementRunning -> GestureRejectionReason.SETTLEMENT_RUNNING
            else -> GestureRejectionReason.DECK_NOT_PREPARED
        }
        listenerFor(generationId).onGestureRejected(gestureId, generationId, reason)
    }

    private fun dragInteraction(x: Float) {
        val gestureWidth = if (activeDeckIsLandscape()) {
            max(1f, width / 2f)
        } else {
            max(1f, width.toFloat())
        }
        val landscape = landscapeModelOrNull()
        if (landscape != null) {
            landscape.dragTo(x, gestureWidth)
        } else {
            interactionModelOrNull()?.dragTo(x, gestureWidth)
        }
    }

    private fun settle(settlement: Settlement) {
        if (settlementRunning) return
        val context = settlementContext(settlement.pageChange)
        activeGestureId = NO_GESTURE_ID
        activeSettlementContext = context
        deckCoordinator.beginSettlement()
        settlementRunning = true
        listenerFor(context.generationId).onSettlementStarted(
            context.gestureId,
            context.generationId,
            context.sourceLogicalPageId,
            context.targetLogicalPageId,
            settlement.pageChange
        )

        val startPercent = currentPagePercent()
        if (startPercent == settlement.targetPercent.toFloat()) {
            completeSettlement(settlement, context)
            return
        }
        settlementAnimator = ValueAnimator.ofFloat(startPercent, settlement.targetPercent.toFloat()).apply {
            duration = settlement.durationMillis
            interpolator = toAndroidInterpolator(settlement.interpolator)
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                val spread = landscapeModelOrNull()
                if (spread != null) {
                    spread.updateSettlement(value)
                } else {
                    interactionModelOrNull()?.updateSettlement(value)
                }
                requestRender()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        completeSettlement(settlement, context)
                    }
                }
            })
            start()
        }
    }

    private fun completeSettlement(settlement: Settlement, context: SettlementContext) {
        settlementRunning = false
        settlementAnimator = null
        activeSettlementContext = null
        gestureMoved = false
        PlayLikeBezierCurl.resetTouchTilt()

        val promotion = deckCoordinator.completeSettlement()
        val promoted = promotion.activatedDeck
        val finalSettlement = settlement
        if (promoted != null) {
            markPromotionRelease(promotion)
            queueEvent { renderer.activateDeck(promoted.generationId) }
        } else {
            val spread = renderer.landscapeSpreadModel
            if (spread != null) {
                spread.completeSettlement(finalSettlement)
            } else {
                val interaction = renderer.portraitModel
                interaction?.completeSettlement(finalSettlement)
            }
            if (finalSettlement.pageChange != PageChange.NONE) {
                renderer.commitTurn(finalSettlement.pageChange)
            }
        }
        requestRender()

        listenerFor(context.generationId).onSettlementCompleted(
            context.gestureId,
            context.generationId,
            context.targetLogicalPageId,
            context.targetOrdinal,
            settlement.pageChange
        )
        if (settlement.pageChange != PageChange.NONE) {
            onPageChangeListener?.onPageChanged(context.targetOrdinal)
        }
    }

    private fun cancelSettlementAnimator(): SettlementContext? {
        val cancelledContext = if (settlementRunning) activeSettlementContext else null
        if (settlementAnimator != null) {
            settlementAnimator!!.removeAllListeners()
            settlementAnimator!!.cancel()
            settlementAnimator = null
        }
        settlementRunning = false
        activeSettlementContext = null
        return cancelledContext
    }

    private fun notifySettlementCancelled(context: SettlementContext?) {
        if (context != null) {
            listenerFor(context.generationId).onSettlementCancelled(
                context.gestureId,
                context.generationId,
                context.sourceLogicalPageId
            )
        }
    }

    private fun listenerFor(generationId: Long): PageSurfaceListener {
        return leaseRegistry.listenerFor(generationId, pageSurfaceListener) ?: pageSurfaceListener
    }

    private fun activeGenerationId(): Long {
        return deckCoordinator.getActiveDeck()?.generationId ?: -1L
    }

    private fun notifyGestureCancelled(gestureId: Long, generationId: Long) {
        if (gestureId != NO_GESTURE_ID) {
            listenerFor(generationId).onGestureCancelled(gestureId, generationId)
        }
    }

    private fun releaseAllOutstandingLeases() {
        for (lease in leaseRegistry.releaseAll(DeckReleaseReason.DISPOSED)) {
            lease.listener.onDeckReleased(lease.generationId, lease.releaseReason ?: DeckReleaseReason.DISPOSED)
        }
    }

    private fun queueDeckRelease(release: PageDeckCoordinator.Release<Bitmap>?) {
        if (release == null) return
        val generationId = release.deck.generationId
        preparedGenerations.remove(generationId)
        leaseRegistry.markReleaseRequested(generationId, release.reason)
        queueEvent { renderer.releaseDeck(generationId, release.reason) }
    }

    private fun markPromotionRelease(promotion: PageDeckCoordinator.Promotion<Bitmap>) {
        val release = promotion.release
        if (release != null) {
            leaseRegistry.markReleaseRequested(release.deck.generationId, release.reason)
        }
    }

    private fun currentPagePercent(): Float {
        val interaction = interactionModelOrNull() ?: return 0f
        val activeState = when (interaction.activePage) {
            ActivePage.LEFT -> interaction.leftPage
            ActivePage.RIGHT -> interaction.rightPage
            ActivePage.CURRENT -> interaction.frontPage
        }
        return activeState.curlPosition / PlayLikeCurlModel.GRID * 100f
    }

    private fun settlementContext(pageChange: PageChange): SettlementContext {
        val deck = deckCoordinator.getActiveDeck()
        if (deck is PortraitPageDeck) {
            val source = deck.current
            val target = when (pageChange) {
                PageChange.PREVIOUS -> deck.previous
                PageChange.NEXT -> deck.next
                else -> source
            }
            return SettlementContext.from(activeGestureId, source, target)
        }
        val spread = deck as LandscapePageDeck
        val source = spread.currentLeft
        val target = when (pageChange) {
            PageChange.PREVIOUS -> spread.previousLeft
            PageChange.NEXT -> spread.nextLeft
            else -> source
        }
        return SettlementContext.from(activeGestureId, source, target)
    }

    private fun activeDeckIsLandscape(): Boolean {
        return deckCoordinator.getActiveDeck() is LandscapePageDeck
    }

    private fun interactionModelOrNull(): PlayLikeCurlModel? {
        val spread = landscapeModelOrNull()
        if (spread != null) {
            return spread.motionModel
        }
        return rendererPortraitModel()
    }

    private fun rendererPortraitModel(): PlayLikeCurlModel? {
        val deck = deckCoordinator.getActiveDeck()
        if (deck !is PortraitPageDeck) return null
        return renderer.portraitModel
    }

    private fun landscapeModelOrNull(): LandscapeSpreadModel? {
        val deck = deckCoordinator.getActiveDeck()
        if (deck !is LandscapePageDeck) return null
        return renderer.landscapeSpreadModel
    }

    private fun obtainVelocityTracker(): VelocityTracker {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        return velocityTracker!!
    }

    private fun recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker!!.recycle()
            velocityTracker = null
        }
    }

    fun interface OnPageChangeListener {
        fun onPageChanged(position: Int)
    }

    private class SettlementContext private constructor(
        val gestureId: Long,
        val generationId: Long,
        val sourceLogicalPageId: String,
        val targetLogicalPageId: String,
        val targetOrdinal: Int
    ) {
        companion object {
            fun from(
                gestureId: Long,
                source: PageImage<Bitmap>,
                target: PageImage<Bitmap>
            ): SettlementContext {
                return SettlementContext(
                    gestureId,
                    source.generationId,
                    source.logicalPageId,
                    target.logicalPageId,
                    target.ordinal
                )
            }
        }
    }

    companion object {
        private const val FLING_THRESHOLD_PX_PER_SECOND = 200f
        const val NO_GESTURE_ID: Long = -1L
        private val NO_OP_LISTENER = object : PageSurfaceListener {}

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "PageSurfaceView mutations must run on the main thread"
            }
        }

        private fun toAndroidInterpolator(interpolator: SettlementInterpolator): Interpolator {
            return if (interpolator == SettlementInterpolator.DECELERATE) {
                DecelerateInterpolator()
            } else {
                AccelerateDecelerateInterpolator()
            }
        }
    }
}
