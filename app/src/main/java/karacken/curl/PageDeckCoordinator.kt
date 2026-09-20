package karacken.curl

import java.util.ArrayList
import java.util.Collections

/**
 * Keeps deck replacement deterministic while a page turn is settling.
 *
 * The coordinator is deliberately independent from Android and OpenGL so the replacement
 * policy can be verified without renderer timing.
 */
internal class PageDeckCoordinator<T> {
    enum class Placement {
        ACTIVE,
        PENDING,
        UNCHANGED,
        REJECTED
    }

    internal class Release<T>(
        val deck: PageDeck<T>,
        val reason: DeckReleaseReason
    )

    internal class Promotion<T> private constructor(
        val activatedDeck: PageDeck<T>?,
        val release: Release<T>?
    ) {
        val releasedDeck: PageDeck<T>?
            get() = release?.deck

        val releaseReason: DeckReleaseReason?
            get() = release?.reason

        companion object {
            fun <T> none(): Promotion<T> {
                return Promotion(null, null)
            }

            fun <T> activated(
                deck: PageDeck<T>?,
                releasedDeck: PageDeck<T>?
            ): Promotion<T> {
                val release = if (releasedDeck == null) {
                    null
                } else {
                    Release(releasedDeck, DeckReleaseReason.REPLACED)
                }
                return Promotion(deck, release)
            }
        }
    }

    internal class Offer<T> private constructor(
        val placement: Placement,
        val rejectionReason: DeckRejectionReason?,
        val releases: List<Release<T>>
    ) {
        val releasedDeck: PageDeck<T>?
            get() = if (releases.isEmpty()) null else releases[0].deck

        val releaseReason: DeckReleaseReason?
            get() = if (releases.isEmpty()) null else releases[0].reason

        companion object {
            fun <T> active(releases: List<Release<T>>): Offer<T> {
                return Offer(
                    Placement.ACTIVE,
                    null,
                    Collections.unmodifiableList(ArrayList(releases))
                )
            }

            fun <T> pending(release: Release<T>?): Offer<T> {
                return Offer(
                    Placement.PENDING,
                    null,
                    if (release == null) emptyList() else listOf(release)
                )
            }

            fun <T> unchanged(): Offer<T> {
                return Offer(Placement.UNCHANGED, null, emptyList())
            }

            fun <T> rejected(reason: DeckRejectionReason): Offer<T> {
                return Offer(Placement.REJECTED, reason, emptyList())
            }
        }
    }

    private var activeDeck: PageDeck<T>? = null
    private var pendingDeck: PageDeck<T>? = null
    private var latestGeneration = Long.MIN_VALUE
    private var settling = false
    private var disposed = false

    @Synchronized
    fun offer(deck: PageDeck<T>): Offer<T> {
        if (disposed) {
            return Offer.rejected(DeckRejectionReason.DISPOSED)
        }
        val generationId = deck.generationId
        if (generationId < latestGeneration) {
            return Offer.rejected(DeckRejectionReason.STALE_GENERATION)
        }
        if (generationId == latestGeneration) {
            val retained = retainedDeck(generationId)
            if (retained != null && hasSameIdentity(retained, deck)) {
                return Offer.unchanged()
            }
            return Offer.rejected(DeckRejectionReason.CONFLICTING_GENERATION)
        }

        latestGeneration = generationId
        if (settling) {
            val replaced = pendingDeck
            pendingDeck = deck
            val release = if (replaced == null) {
                null
            } else {
                Release(replaced, DeckReleaseReason.REPLACED)
            }
            return Offer.pending(release)
        }

        val releases = ArrayList<Release<T>>(2)
        addRelease(releases, activeDeck, DeckReleaseReason.REPLACED)
        if (pendingDeck !== activeDeck) {
            addRelease(releases, pendingDeck, DeckReleaseReason.REPLACED)
        }
        activeDeck = deck
        pendingDeck = null
        return Offer.active(releases)
    }

    @Synchronized
    fun beginSettlement() {
        if (!disposed) {
            settling = true
        }
    }

    @Synchronized
    fun completeSettlement(): Promotion<T> {
        settling = false
        if (pendingDeck != null) {
            val released = activeDeck
            activeDeck = pendingDeck
            pendingDeck = null
            return Promotion.activated(activeDeck, released)
        }
        return Promotion.none()
    }

    @Synchronized
    fun cancelSettlement() {
        settling = false
    }

    @Synchronized
    fun activatePending(): Promotion<T> {
        if (disposed || settling || pendingDeck == null) {
            return Promotion.none()
        }
        val released = activeDeck
        activeDeck = pendingDeck
        pendingDeck = null
        return Promotion.activated(activeDeck, released)
    }

    @Synchronized
    fun getActiveDeck(): PageDeck<T>? {
        return activeDeck
    }

    @Synchronized
    fun getPendingDeck(): PageDeck<T>? {
        return pendingDeck
    }

    @Synchronized
    fun isSettling(): Boolean {
        return settling
    }

    @Synchronized
    fun release(generationId: Long): Release<T>? {
        if (activeDeck != null && activeDeck!!.generationId == generationId) {
            val released = activeDeck!!
            activeDeck = null
            return Release(released, DeckReleaseReason.EXPLICIT)
        }
        if (pendingDeck != null && pendingDeck!!.generationId == generationId) {
            val released = pendingDeck!!
            pendingDeck = null
            return Release(released, DeckReleaseReason.EXPLICIT)
        }
        return null
    }

    @Synchronized
    fun releasePending(reason: DeckReleaseReason): Release<T>? {
        if (pendingDeck == null) {
            return null
        }
        val released = pendingDeck!!
        pendingDeck = null
        return Release(released, reason)
    }

    @Synchronized
    fun dispose(): List<Release<T>> {
        if (disposed) {
            return emptyList()
        }
        disposed = true
        settling = false
        val releases = ArrayList<Release<T>>(2)
        addRelease(releases, activeDeck, DeckReleaseReason.DISPOSED)
        if (pendingDeck !== activeDeck) {
            addRelease(releases, pendingDeck, DeckReleaseReason.DISPOSED)
        }
        activeDeck = null
        pendingDeck = null
        return Collections.unmodifiableList(releases)
    }

    private fun retainedDeck(generationId: Long): PageDeck<T>? {
        if (pendingDeck != null && pendingDeck!!.generationId == generationId) {
            return pendingDeck
        }
        if (activeDeck != null && activeDeck!!.generationId == generationId) {
            return activeDeck
        }
        return null
    }

    private fun hasSameIdentity(first: PageDeck<T>, second: PageDeck<T>): Boolean {
        if (first.mode != second.mode) {
            return false
        }
        val firstPages = first.pages
        val secondPages = second.pages
        if (firstPages.size != secondPages.size) {
            return false
        }
        for (index in firstPages.indices) {
            val firstPage = firstPages[index]
            val secondPage = secondPages[index]
            if (firstPage.logicalPageId != secondPage.logicalPageId ||
                firstPage.ordinal != secondPage.ordinal ||
                firstPage.widthPx != secondPage.widthPx ||
                firstPage.heightPx != secondPage.heightPx ||
                firstPage.hasOverlay != secondPage.hasOverlay
            ) {
                return false
            }
        }
        return true
    }

    private fun addRelease(
        releases: MutableList<Release<T>>,
        deck: PageDeck<T>?,
        reason: DeckReleaseReason
    ) {
        if (deck != null) {
            releases.add(Release(deck, reason))
        }
    }
}
