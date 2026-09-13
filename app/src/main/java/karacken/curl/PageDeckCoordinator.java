package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Keeps deck replacement deterministic while a page turn is settling.
 *
 * <p>The coordinator is deliberately independent from Android and OpenGL so the replacement
 * policy can be verified without renderer timing.
 */
final class PageDeckCoordinator<T> {
    enum Placement {
        ACTIVE,
        PENDING,
        UNCHANGED,
        REJECTED
    }

    static final class Release<T> {
        private final PageDeck<T> deck;
        private final DeckReleaseReason reason;

        Release(PageDeck<T> deck, DeckReleaseReason reason) {
            this.deck = Objects.requireNonNull(deck, "deck");
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        PageDeck<T> getDeck() {
            return deck;
        }

        DeckReleaseReason getReason() {
            return reason;
        }
    }

    static final class Promotion<T> {
        private final PageDeck<T> activatedDeck;
        private final Release<T> release;

        private Promotion(PageDeck<T> activatedDeck, Release<T> release) {
            this.activatedDeck = activatedDeck;
            this.release = release;
        }

        static <T> Promotion<T> none() {
            return new Promotion<>(null, null);
        }

        static <T> Promotion<T> activated(
                PageDeck<T> deck,
                PageDeck<T> releasedDeck) {
            Release<T> release = releasedDeck == null
                    ? null
                    : new Release<>(releasedDeck, DeckReleaseReason.REPLACED);
            return new Promotion<>(deck, release);
        }

        PageDeck<T> getActivatedDeck() {
            return activatedDeck;
        }

        PageDeck<T> getReleasedDeck() {
            return release == null ? null : release.getDeck();
        }

        DeckReleaseReason getReleaseReason() {
            return release == null ? null : release.getReason();
        }

        Release<T> getRelease() {
            return release;
        }
    }

    static final class Offer<T> {
        private final Placement placement;
        private final DeckRejectionReason rejectionReason;
        private final List<Release<T>> releases;

        private Offer(
                Placement placement,
                DeckRejectionReason rejectionReason,
                List<Release<T>> releases) {
            this.placement = placement;
            this.rejectionReason = rejectionReason;
            this.releases = releases;
        }

        static <T> Offer<T> active(List<Release<T>> releases) {
            return new Offer<>(
                    Placement.ACTIVE,
                    null,
                    List.copyOf(releases));
        }

        static <T> Offer<T> pending(Release<T> release) {
            return new Offer<>(
                    Placement.PENDING,
                    null,
                    release == null ? List.of() : List.of(release));
        }

        static <T> Offer<T> unchanged() {
            return new Offer<>(Placement.UNCHANGED, null, List.of());
        }

        static <T> Offer<T> rejected(DeckRejectionReason reason) {
            return new Offer<>(Placement.REJECTED, reason, List.of());
        }

        Placement getPlacement() {
            return placement;
        }

        DeckRejectionReason getRejectionReason() {
            return rejectionReason;
        }

        List<Release<T>> getReleases() {
            return releases;
        }

        PageDeck<T> getReleasedDeck() {
            return releases.isEmpty() ? null : releases.get(0).getDeck();
        }

        DeckReleaseReason getReleaseReason() {
            return releases.isEmpty() ? null : releases.get(0).getReason();
        }
    }

    private PageDeck<T> activeDeck;
    private PageDeck<T> pendingDeck;
    private long latestGeneration = Long.MIN_VALUE;
    private boolean settling;
    private boolean disposed;

    synchronized Offer<T> offer(PageDeck<T> deck) {
        if (disposed) {
            return Offer.rejected(DeckRejectionReason.DISPOSED);
        }
        long generationId = deck.getGenerationId();
        if (generationId < latestGeneration) {
            return Offer.rejected(DeckRejectionReason.STALE_GENERATION);
        }
        if (generationId == latestGeneration) {
            PageDeck<T> retained = retainedDeck(generationId);
            if (retained != null && hasSameIdentity(retained, deck)) {
                return Offer.unchanged();
            }
            return Offer.rejected(DeckRejectionReason.CONFLICTING_GENERATION);
        }

        latestGeneration = generationId;
        if (settling) {
            PageDeck<T> replaced = pendingDeck;
            pendingDeck = deck;
            Release<T> release = replaced == null
                    ? null
                    : new Release<>(replaced, DeckReleaseReason.REPLACED);
            return Offer.pending(release);
        }

        List<Release<T>> releases = new ArrayList<>(2);
        addRelease(releases, activeDeck, DeckReleaseReason.REPLACED);
        if (pendingDeck != activeDeck) {
            addRelease(releases, pendingDeck, DeckReleaseReason.REPLACED);
        }
        activeDeck = deck;
        pendingDeck = null;
        return Offer.active(releases);
    }

    synchronized void beginSettlement() {
        if (!disposed) {
            settling = true;
        }
    }

    synchronized Promotion<T> completeSettlement() {
        settling = false;
        if (pendingDeck != null) {
            PageDeck<T> released = activeDeck;
            activeDeck = pendingDeck;
            pendingDeck = null;
            return Promotion.activated(activeDeck, released);
        }
        return Promotion.none();
    }

    synchronized void cancelSettlement() {
        settling = false;
    }

    synchronized Promotion<T> activatePending() {
        if (disposed || settling || pendingDeck == null) {
            return Promotion.none();
        }
        PageDeck<T> released = activeDeck;
        activeDeck = pendingDeck;
        pendingDeck = null;
        return Promotion.activated(activeDeck, released);
    }

    synchronized PageDeck<T> getActiveDeck() {
        return activeDeck;
    }

    synchronized PageDeck<T> getPendingDeck() {
        return pendingDeck;
    }

    synchronized boolean isSettling() {
        return settling;
    }

    synchronized Release<T> release(long generationId) {
        if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
            PageDeck<T> released = activeDeck;
            activeDeck = null;
            return new Release<>(released, DeckReleaseReason.EXPLICIT);
        }
        if (pendingDeck != null && pendingDeck.getGenerationId() == generationId) {
            PageDeck<T> released = pendingDeck;
            pendingDeck = null;
            return new Release<>(released, DeckReleaseReason.EXPLICIT);
        }
        return null;
    }

    synchronized Release<T> releasePending(DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (pendingDeck == null) {
            return null;
        }
        PageDeck<T> released = pendingDeck;
        pendingDeck = null;
        return new Release<>(released, reason);
    }

    synchronized List<Release<T>> dispose() {
        if (disposed) {
            return Collections.emptyList();
        }
        disposed = true;
        settling = false;
        List<Release<T>> releases = new ArrayList<>(2);
        addRelease(releases, activeDeck, DeckReleaseReason.DISPOSED);
        if (pendingDeck != activeDeck) {
            addRelease(releases, pendingDeck, DeckReleaseReason.DISPOSED);
        }
        activeDeck = null;
        pendingDeck = null;
        return List.copyOf(releases);
    }

    private PageDeck<T> retainedDeck(long generationId) {
        if (pendingDeck != null && pendingDeck.getGenerationId() == generationId) {
            return pendingDeck;
        }
        if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
            return activeDeck;
        }
        return null;
    }

    private static <T> boolean hasSameIdentity(
            PageDeck<T> first,
            PageDeck<T> second) {
        if (first.getMode() != second.getMode()) {
            return false;
        }
        List<PageImage<T>> firstPages = first.getPages();
        List<PageImage<T>> secondPages = second.getPages();
        if (firstPages.size() != secondPages.size()) {
            return false;
        }
        for (int index = 0; index < firstPages.size(); index++) {
            PageImage<T> firstPage = firstPages.get(index);
            PageImage<T> secondPage = secondPages.get(index);
            if (!firstPage.getLogicalPageId().equals(secondPage.getLogicalPageId())
                    || firstPage.getOrdinal() != secondPage.getOrdinal()
                    || firstPage.getWidthPx() != secondPage.getWidthPx()
                    || firstPage.getHeightPx() != secondPage.getHeightPx()
                    || firstPage.hasOverlay() != secondPage.hasOverlay()) {
                return false;
            }
        }
        return true;
    }

    private static <T> void addRelease(
            List<Release<T>> releases,
            PageDeck<T> deck,
            DeckReleaseReason reason) {
        if (deck != null) {
            releases.add(new Release<>(deck, reason));
        }
    }
}
