package karacken.curl;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Production page-curl surface.
 *
 * <p>The client owns bitmap decoding and page identity. This view owns only interaction,
 * settlement, and GL resource lifecycle. Public mutations must run on the Android main thread.
 */
public class PageSurfaceView extends GLSurfaceView {
    private static final float FLING_THRESHOLD_PX_PER_SECOND = 200f;
    public static final long NO_GESTURE_ID = -1L;
    private static final PageSurfaceListener NO_OP_LISTENER = new PageSurfaceListener() {};

    private final PageDeckCoordinator<Bitmap> deckCoordinator = new PageDeckCoordinator<>();
    private final DeckLeaseRegistry leaseRegistry = new DeckLeaseRegistry();
    private final Set<Long> preparedGenerations = new LinkedHashSet<>();
    private final PageRenderer renderer;
    private final int touchSlop;

    private PageSurfaceListener pageSurfaceListener = NO_OP_LISTENER;
    private RenderCapabilities renderCapabilities;
    private ValueAnimator settlementAnimator;
    private SettlementContext activeSettlementContext;
    private VelocityTracker velocityTracker;
    private boolean settlementRunning;
    private boolean gestureAccepted;
    private boolean gestureMoved;
    private boolean surfaceVisible = true;
    private boolean attached;
    private boolean disposed;
    private long activeGestureId = NO_GESTURE_ID;
    private float gestureDownX;
    private float gestureDownY;
    private OnPageChangeListener onPageChangeListener;

    public PageSurfaceView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        renderer = new PageRenderer(new PageRenderer.Events() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                post(() -> handleCapabilitiesAvailable(capabilities));
            }

            @Override
            public void onDeckPrepared(long generationId) {
                post(() -> handleDeckPrepared(generationId));
            }

            @Override
            public void onDeckReleased(
                    long generationId,
                    DeckReleaseReason reason) {
                leaseRegistry.markReleaseRequested(generationId, reason);
                post(() -> handleDeckReleased(generationId, reason));
            }

            @Override
            public void onRenderFailure(RenderFailure failure) {
                post(() -> handleRenderFailure(failure));
            }
        });

        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);
        setClickable(true);
        setFocusable(true);
    }

    /** Marks the surface active for interaction and resumes its GL thread. */
    public void attach() {
        requireMainThread();
        if (disposed || attached) {
            return;
        }
        attached = true;
        onResume();
        requestRender();
    }

    /** Cancels interaction and pauses frame production without releasing the active deck. */
    public void detach() {
        requireMainThread();
        if (!attached) {
            return;
        }
        attached = false;
        cancelGesture();
        queueDeckRelease(deckCoordinator.releasePending(
                DeckReleaseReason.SESSION_DETACHED));
        requestRender();
        onPause();
    }

    /**
     * Submits one complete interaction window.
     *
     * <p>An idle submission becomes active after upload. During settlement, exactly one
     * replacement deck is retained and prepared. Acceptance starts a bitmap lease: every bitmap
     * in the deck must remain immutable and unrecycled until
     * {@link PageSurfaceListener#onDeckReleased(long, DeckReleaseReason)} reports the generation.
     * Rejected decks are never acquired.
     */
    public void submitDeck(PageDeck<Bitmap> deck) {
        requireMainThread();
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        if (disposed) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.DISPOSED);
            return;
        }
        if (!attached) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.SESSION_DETACHED);
            return;
        }
        if (renderCapabilities == null) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(),
                    DeckRejectionReason.CAPABILITIES_UNAVAILABLE);
            return;
        }
        PageDeckCoordinator.Offer<Bitmap> offer = deckCoordinator.offer(deck);
        if (offer.getPlacement() == PageDeckCoordinator.Placement.REJECTED) {
            pageSurfaceListener.onDeckRejected(
                    deck.getGenerationId(), offer.getRejectionReason());
            return;
        }
        if (offer.getPlacement() == PageDeckCoordinator.Placement.UNCHANGED) {
            return;
        }
        leaseRegistry.acquire(deck.getGenerationId(), pageSurfaceListener);
        for (PageDeckCoordinator.Release<Bitmap> release : offer.getReleases()) {
            queueDeckRelease(release);
        }
        boolean activateWhenPrepared =
                offer.getPlacement() == PageDeckCoordinator.Placement.ACTIVE;
        preparedGenerations.remove(deck.getGenerationId());
        queueEvent(() -> renderer.prepareDeck(deck, activateWhenPrepared));
        requestRender();
    }

    /** Supplies the current pixel viewport without changing page layout. */
    public void setViewport(int widthPx, int heightPx) {
        requireMainThread();
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        queueEvent(() -> renderer.setViewport(widthPx, heightPx));
        requestRender();
    }

    /**
     * Bunko: matches the GL clear color to the reader paper color so letterbox
     * margins and spread seams never show black. Channels are 0-255.
     */
    public void setBackgroundColor(int red, int green, int blue) {
        requireMainThread();
        queueEvent(() -> renderer.setBackgroundColor(red, green, blue));
        requestRender();
    }

    /**
     * Changes the active-plus-pending GPU budget before any deck is acquired.
     */
    public void setGpuBudgetBytes(long gpuBudgetBytes) {
        requireMainThread();
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        if (deckCoordinator.getActiveDeck() != null
                || deckCoordinator.getPendingDeck() != null) {
            throw new IllegalStateException(
                    "GPU budget cannot change while a deck is retained");
        }
        queueEvent(() -> renderer.setGpuBudgetBytes(gpuBudgetBytes));
        requestRender();
    }

    /** Controls rendering and gesture eligibility without discarding the prepared deck. */
    public void setVisible(boolean visible) {
        requireMainThread();
        surfaceVisible = visible;
        if (!visible) {
            cancelGesture();
        } else {
            requestRender();
        }
    }

    /** Cancels any partial gesture or settlement without navigating. */
    public void cancelGesture() {
        cancelGesture(activeGestureId);
    }

    /** Cancels the identified gesture or settlement without navigating. */
    public void cancelGesture(long gestureId) {
        requireMainThread();
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        long cancelledGestureId = activeGestureId != NO_GESTURE_ID
                ? activeGestureId
                : gestureId;
        long generationId = activeGenerationId();
        boolean cancelledGesture = gestureAccepted;
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction != null) {
            interaction.cancelGesture();
        }
        PlayBooksBezierCurl.resetTouchTilt();
        deckCoordinator.cancelSettlement();
        gestureAccepted = false;
        gestureMoved = false;
        recycleVelocityTracker();
        requestRender();
        notifySettlementCancelled(cancelledSettlement);
        if (cancelledSettlement == null && cancelledGesture) {
            notifyGestureCancelled(cancelledGestureId, generationId);
        }
        activeGestureId = NO_GESTURE_ID;
    }

    /**
     * Starts the same reference settlement used by a completed edge drag.
     *
     * <p>Returns false when the prepared deck cannot accept a turn or the requested direction is
     * outside the current deck boundary.
     */
    public boolean turn(PageChange pageChange) {
        return turn(pageChange, NO_GESTURE_ID);
    }

    public boolean turn(PageChange pageChange, long gestureId) {
        requireMainThread();
        activeGestureId = gestureId;
        if (pageChange != PageChange.PREVIOUS && pageChange != PageChange.NEXT) {
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        if (!gestureReady()) {
            rejectGesture(gestureId);
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        PlayBooksBezierCurl.setTouchTilt(PlayBooksBezierCurl.DEFAULT_THUMB_TILT);
        Settlement settlement = interaction.turn(pageChange);
        if (settlement.getPageChange() == PageChange.NONE) {
            interaction.cancelGesture();
            requestRender();
            activeGestureId = NO_GESTURE_ID;
            return false;
        }
        settle(settlement);
        return true;
    }

    /** Explicitly activates a prepared replacement after an idle generation change. */
    public void activatePendingDeck() {
        requireMainThread();
        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.activatePending();
        PageDeck<Bitmap> activated = promotion.getActivatedDeck();
        if (activated == null) {
            return;
        }
        markPromotionRelease(promotion);
        queueEvent(() -> renderer.activateDeck(activated.getGenerationId()));
        requestRender();
    }

    /** Releases a retained active or replacement deck and its GL textures. */
    public void releaseDeck(long generationId) {
        requireMainThread();
        preparedGenerations.remove(generationId);
        PageDeckCoordinator.Release<Bitmap> release =
                deckCoordinator.release(generationId);
        queueDeckRelease(release);
        requestRender();
    }

    /** Idempotently releases renderer, gesture, and deck state. */
    public void dispose() {
        requireMainThread();
        if (disposed) {
            return;
        }
        disposed = true;
        SettlementContext cancelledSettlement = cancelSettlementAnimator();
        recycleVelocityTracker();
        preparedGenerations.clear();
        deckCoordinator.dispose();
        if (attached) {
            attached = false;
            setPreserveEGLContextOnPause(false);
            queueEvent(renderer::dispose);
            requestRender();
            onPause();
        }
        renderer.abandonClientState();
        notifySettlementCancelled(cancelledSettlement);
        releaseAllOutstandingLeases();
    }

    /**
     * Installs the main-thread callback boundary for future decks and capability events.
     *
     * <p>An accepted generation remains bound to the listener that acquired its bitmap lease,
     * even if this listener is replaced before that generation is released.
     */
    public void setPageSurfaceListener(PageSurfaceListener listener) {
        requireMainThread();
        pageSurfaceListener = listener == null ? NO_OP_LISTENER : listener;
        if (renderCapabilities != null) {
            pageSurfaceListener.onCapabilitiesAvailable(renderCapabilities);
        }
    }

    public PageSurfaceListener getPageSurfaceListener() {
        return pageSurfaceListener;
    }

    public RenderCapabilities getRenderCapabilities() {
        return renderCapabilities;
    }

    public int getCurrentPosition() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (deck instanceof PortraitPageDeck) {
            return ((PortraitPageDeck<Bitmap>) deck).getCurrent().getOrdinal();
        }
        if (deck instanceof LandscapePageDeck) {
            return ((LandscapePageDeck<Bitmap>) deck).getCurrentLeft().getOrdinal();
        }
        return -1;
    }

    public OnPageChangeListener getOnPageChangeListener() {
        return onPageChangeListener;
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        requireMainThread();
        onPageChangeListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handlePageTouchEvent(event, NO_GESTURE_ID);
    }

    private boolean handlePageTouchEvent(MotionEvent event, long gestureId) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            activeGestureId = gestureId;
            gestureAccepted = gestureReady();
            gestureMoved = false;
            recycleVelocityTracker();
            if (!gestureAccepted) {
                rejectGesture(activeGestureId);
                activeGestureId = NO_GESTURE_ID;
                return true;
            }
        } else if (!gestureAccepted) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gestureAccepted = false;
                gestureMoved = false;
                recycleVelocityTracker();
                activeGestureId = NO_GESTURE_ID;
            }
            return true;
        }
        PlayLikeCurlModel interaction = interactionModelOrNull();
        if (interaction == null) {
            gestureAccepted = false;
            gestureMoved = false;
            recycleVelocityTracker();
            notifyGestureCancelled(activeGestureId, activeGenerationId());
            activeGestureId = NO_GESTURE_ID;
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                gestureDownX = event.getX();
                gestureDownY = event.getY();
                float viewHeightDown = getHeight() > 0 ? getHeight() : 1f;
                PlayBooksBezierCurl.setTouchTilt(((event.getY() / viewHeightDown) - 0.5f) * 2f);
                obtainVelocityTracker().addMovement(event);
                interaction.beginGesture(gestureDownX);
                return true;
            case MotionEvent.ACTION_MOVE:
                obtainVelocityTracker().addMovement(event);
                float deltaX = event.getX() - gestureDownX;
                float deltaY = event.getY() - gestureDownY;
                if (!gestureMoved
                        && Math.hypot(deltaX, deltaY) >= touchSlop) {
                    gestureMoved = true;
                }
                if (gestureMoved) {
                    float viewHeightMove = getHeight() > 0 ? getHeight() : 1f;
                    PlayBooksBezierCurl.setTouchTilt(((event.getY() / viewHeightMove) - 0.5f) * 2f);
                    dragInteraction(event.getX());
                    requestRender();
                }
                return true;
            case MotionEvent.ACTION_UP:
                obtainVelocityTracker().addMovement(event);
                if (!gestureMoved) {
                    gestureAccepted = false;
                    interaction.cancelGesture();
                    recycleVelocityTracker();
                    requestRender();
                    performClick();
                    notifyGestureCancelled(activeGestureId, activeGenerationId());
                    activeGestureId = NO_GESTURE_ID;
                    return true;
                }
                dragInteraction(event.getX());
                VelocityTracker tracker = velocityTracker;
                tracker.computeCurrentVelocity(1000);
                float velocityX = tracker.getXVelocity();
                recycleVelocityTracker();
                Settlement settlement;
                if (Math.abs(velocityX) >= FLING_THRESHOLD_PX_PER_SECOND) {
                    settlement = velocityX < 0f
                            ? interaction.flingTowardNext()
                            : interaction.flingTowardPrevious();
                } else {
                    settlement = interaction.release();
                }
                gestureAccepted = false;
                settle(settlement);
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureAccepted = false;
                interaction.cancelGesture();
                gestureMoved = false;
                recycleVelocityTracker();
                requestRender();
                notifyGestureCancelled(activeGestureId, activeGenerationId());
                activeGestureId = NO_GESTURE_ID;
                return true;
            default:
                return true;
        }
    }

    public boolean onPageTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    public boolean onPageTouchEvent(MotionEvent event, long gestureId) {
        return handlePageTouchEvent(event, gestureId);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) {
            setViewport(width, height);
        }
    }

    private void handleDeckPrepared(long generationId) {
        if (disposed) {
            return;
        }
        PageSurfaceListener owner = leaseRegistry.ownerFor(generationId);
        if (owner == null) {
            return;
        }
        if (!preparedGenerations.add(generationId)) {
            return;
        }
        trimPreparedGenerations();
        owner.onDeckPrepared(generationId);
        requestRender();
    }

    private void handleRenderFailure(RenderFailure failure) {
        if (disposed) {
            return;
        }
        long generationId = failure.getGenerationId();
        PageSurfaceListener owner = generationId < 0
                ? pageSurfaceListener
                : leaseRegistry.ownerFor(generationId);
        if (owner != null) {
            owner.onRenderFailure(failure);
        }
    }

    private void handleCapabilitiesAvailable(RenderCapabilities capabilities) {
        if (disposed) {
            return;
        }
        renderCapabilities = capabilities;
        pageSurfaceListener.onCapabilitiesAvailable(capabilities);
    }

    private void handleDeckReleased(
            long generationId,
            DeckReleaseReason reason) {
        preparedGenerations.remove(generationId);
        deckCoordinator.release(generationId);
        DeckLeaseRegistry.Lease lease = leaseRegistry.release(generationId);
        if (lease != null) {
            DeckReleaseReason effectiveReason =
                    lease.getReleaseReason() == null ? reason : lease.getReleaseReason();
            lease.getListener().onDeckReleased(generationId, effectiveReason);
        }
    }

    private void trimPreparedGenerations() {
        while (preparedGenerations.size() > 2) {
            Iterator<Long> iterator = preparedGenerations.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private boolean gestureReady() {
        if (disposed || !attached || !surfaceVisible || settlementRunning) {
            return false;
        }
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active != null && preparedGenerations.contains(active.getGenerationId());
    }

    private void rejectGesture(long gestureId) {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        long generationId = active == null ? -1L : active.getGenerationId();
        GestureRejectionReason reason;
        if (disposed) {
            reason = GestureRejectionReason.DISPOSED;
        } else if (!attached) {
            reason = GestureRejectionReason.SESSION_DETACHED;
        } else if (!surfaceVisible) {
            reason = GestureRejectionReason.NOT_VISIBLE;
        } else if (settlementRunning) {
            reason = GestureRejectionReason.SETTLEMENT_RUNNING;
        } else {
            reason = GestureRejectionReason.DECK_NOT_PREPARED;
        }
        listenerFor(generationId).onGestureRejected(gestureId, generationId, reason);
    }

    private void dragInteraction(float x) {
        float gestureWidth = activeDeckIsLandscape()
                ? Math.max(1f, getWidth() / 2f)
                : Math.max(1f, getWidth());
        if (landscapeModelOrNull() != null) {
            landscapeModelOrNull().dragTo(x, gestureWidth);
        } else {
            interactionModelOrNull().dragTo(x, gestureWidth);
        }
    }

    private void settle(Settlement settlement) {
        if (settlementRunning) {
            return;
        }
        SettlementContext context = settlementContext(settlement.getPageChange());
        activeGestureId = NO_GESTURE_ID;
        activeSettlementContext = context;
        deckCoordinator.beginSettlement();
        settlementRunning = true;
        listenerFor(context.generationId).onSettlementStarted(
                context.gestureId,
                context.generationId,
                context.sourceLogicalPageId,
                context.targetLogicalPageId,
                settlement.getPageChange());

        float startPercent = currentPagePercent();
        if (startPercent == settlement.getTargetPercent()) {
            completeSettlement(settlement, context);
            return;
        }
        settlementAnimator =
                ValueAnimator.ofFloat(startPercent, settlement.getTargetPercent());
        settlementAnimator.setDuration(settlement.getDurationMillis());
        settlementAnimator.setInterpolator(
                toAndroidInterpolator(settlement.getInterpolator()));
        settlementAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            LandscapeSpreadModel spread = landscapeModelOrNull();
            if (spread != null) {
                spread.updateSettlement(value);
            } else {
                interactionModelOrNull().updateSettlement(value);
            }
            requestRender();
        });
        settlementAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    completeSettlement(settlement, context);
                }
            }
        });
        settlementAnimator.start();
    }

    private void completeSettlement(Settlement settlement, SettlementContext context) {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            spread.completeSettlement(settlement);
        } else {
            interactionModelOrNull().completeSettlement(settlement);
        }
        settlementRunning = false;
        settlementAnimator = null;
        activeSettlementContext = null;
        gestureMoved = false;
        PlayBooksBezierCurl.resetTouchTilt();

        PageDeckCoordinator.Promotion<Bitmap> promotion =
                deckCoordinator.completeSettlement();
        PageDeck<Bitmap> promoted = promotion.getActivatedDeck();
        if (promoted != null) {
            markPromotionRelease(promotion);
            queueEvent(() -> renderer.activateDeck(promoted.getGenerationId()));
        } else if (settlement.getPageChange() != PageChange.NONE) {
            preparedGenerations.remove(context.generationId);
            queueEvent(() -> renderer.commitTurn(settlement.getPageChange()));
        }
        requestRender();

        listenerFor(context.generationId).onSettlementCompleted(
                context.gestureId,
                context.generationId,
                context.targetLogicalPageId,
                context.targetOrdinal,
                settlement.getPageChange());
        if (settlement.getPageChange() != PageChange.NONE
                && onPageChangeListener != null) {
            onPageChangeListener.onPageChanged(context.targetOrdinal);
        }
    }

    private SettlementContext cancelSettlementAnimator() {
        SettlementContext cancelledContext =
                settlementRunning ? activeSettlementContext : null;
        if (settlementAnimator != null) {
            settlementAnimator.removeAllListeners();
            settlementAnimator.cancel();
            settlementAnimator = null;
        }
        settlementRunning = false;
        activeSettlementContext = null;
        return cancelledContext;
    }

    private void notifySettlementCancelled(SettlementContext context) {
        if (context != null) {
            listenerFor(context.generationId).onSettlementCancelled(
                    context.gestureId,
                    context.generationId,
                    context.sourceLogicalPageId);
        }
    }

    private PageSurfaceListener listenerFor(long generationId) {
        return leaseRegistry.listenerFor(generationId, pageSurfaceListener);
    }

    private long activeGenerationId() {
        PageDeck<Bitmap> active = deckCoordinator.getActiveDeck();
        return active == null ? -1L : active.getGenerationId();
    }

    private void notifyGestureCancelled(long gestureId, long generationId) {
        if (gestureId != NO_GESTURE_ID) {
            listenerFor(generationId).onGestureCancelled(gestureId, generationId);
        }
    }

    private void releaseAllOutstandingLeases() {
        for (DeckLeaseRegistry.Lease lease :
                leaseRegistry.releaseAll(DeckReleaseReason.DISPOSED)) {
            lease.getListener().onDeckReleased(
                    lease.getGenerationId(),
                    lease.getReleaseReason());
        }
    }

    private void queueDeckRelease(PageDeckCoordinator.Release<Bitmap> release) {
        if (release == null) {
            return;
        }
        long generationId = release.getDeck().getGenerationId();
        preparedGenerations.remove(generationId);
        leaseRegistry.markReleaseRequested(generationId, release.getReason());
        queueEvent(() -> renderer.releaseDeck(generationId, release.getReason()));
    }

    private void markPromotionRelease(
            PageDeckCoordinator.Promotion<Bitmap> promotion) {
        PageDeckCoordinator.Release<Bitmap> release = promotion.getRelease();
        if (release != null) {
            leaseRegistry.markReleaseRequested(
                    release.getDeck().getGenerationId(),
                    release.getReason());
        }
    }

    private float currentPagePercent() {
        PlayLikeCurlModel interaction = interactionModelOrNull();
        PageState activeState;
        if (interaction.getActivePage() == ActivePage.LEFT) {
            activeState = interaction.getLeftPage();
        } else if (interaction.getActivePage() == ActivePage.RIGHT) {
            activeState = interaction.getRightPage();
        } else {
            activeState = interaction.getFrontPage();
        }
        return activeState.getCurlPosition() / PlayLikeCurlModel.GRID * 100f;
    }

    private SettlementContext settlementContext(PageChange pageChange) {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (deck instanceof PortraitPageDeck) {
            PortraitPageDeck<Bitmap> portrait = (PortraitPageDeck<Bitmap>) deck;
            PageImage<Bitmap> source = portrait.getCurrent();
            PageImage<Bitmap> target = pageChange == PageChange.PREVIOUS
                    ? portrait.getPrevious()
                    : pageChange == PageChange.NEXT
                            ? portrait.getNext()
                            : source;
            return SettlementContext.from(activeGestureId, source, target);
        }
        LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) deck;
        PageImage<Bitmap> source = spread.getCurrentLeft();
        PageImage<Bitmap> target = pageChange == PageChange.PREVIOUS
                ? spread.getPreviousLeft()
                : pageChange == PageChange.NEXT
                        ? spread.getNextLeft()
                        : source;
        return SettlementContext.from(activeGestureId, source, target);
    }

    private boolean activeDeckIsLandscape() {
        return deckCoordinator.getActiveDeck() instanceof LandscapePageDeck;
    }

    private PlayLikeCurlModel interactionModelOrNull() {
        LandscapeSpreadModel spread = landscapeModelOrNull();
        if (spread != null) {
            return spread.getMotionModel();
        }
        return rendererPortraitModel();
    }

    private PlayLikeCurlModel rendererPortraitModel() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof PortraitPageDeck)) {
            return null;
        }
        return renderer.getPortraitModel();
    }

    private LandscapeSpreadModel landscapeModelOrNull() {
        PageDeck<Bitmap> deck = deckCoordinator.getActiveDeck();
        if (!(deck instanceof LandscapePageDeck)) {
            return null;
        }
        return renderer.getLandscapeSpreadModel();
    }

    private VelocityTracker obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        return velocityTracker;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "PageSurfaceView mutations must run on the main thread");
        }
    }

    private static Interpolator toAndroidInterpolator(
            SettlementInterpolator interpolator) {
        return interpolator == SettlementInterpolator.DECELERATE
                ? new DecelerateInterpolator()
                : new AccelerateDecelerateInterpolator();
    }

    public interface OnPageChangeListener {
        void onPageChanged(int position);
    }

    private static final class SettlementContext {
        private final long gestureId;
        private final long generationId;
        private final String sourceLogicalPageId;
        private final String targetLogicalPageId;
        private final int targetOrdinal;

        private SettlementContext(
                long gestureId,
                long generationId,
                String sourceLogicalPageId,
                String targetLogicalPageId,
                int targetOrdinal) {
            this.gestureId = gestureId;
            this.generationId = generationId;
            this.sourceLogicalPageId = sourceLogicalPageId;
            this.targetLogicalPageId = targetLogicalPageId;
            this.targetOrdinal = targetOrdinal;
        }

        static SettlementContext from(
                long gestureId,
                PageImage<Bitmap> source,
                PageImage<Bitmap> target) {
            return new SettlementContext(
                    gestureId,
                    source.getGenerationId(),
                    source.getLogicalPageId(),
                    target.getLogicalPageId(),
                    target.getOrdinal());
        }
    }
}
