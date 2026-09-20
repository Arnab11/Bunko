package karacken.curl

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Keeps each accepted bitmap lease bound to the listener that acquired it.
 *
 * The registry also makes terminal release idempotent when renderer and lifecycle cleanup
 * converge on the same generation.
 */
internal class DeckLeaseRegistry {
    internal class Lease(
        val generationId: Long,
        val listener: PageSurfaceListener,
        val releaseReason: DeckReleaseReason?
    )

    private val owners = LinkedHashMap<Long, PageSurfaceListener>()
    private val requestedReleaseReasons = LinkedHashMap<Long, DeckReleaseReason>()

    @Synchronized
    fun acquire(generationId: Long, listener: PageSurfaceListener): Boolean {
        if (owners.containsKey(generationId)) {
            return false
        }
        owners[generationId] = listener
        return true
    }

    @Synchronized
    fun listenerFor(generationId: Long, fallback: PageSurfaceListener?): PageSurfaceListener? {
        val owner = owners[generationId]
        return owner ?: fallback
    }

    @Synchronized
    fun ownerFor(generationId: Long): PageSurfaceListener? {
        return owners[generationId]
    }

    @Synchronized
    fun markReleaseRequested(generationId: Long, reason: DeckReleaseReason) {
        if (owners.containsKey(generationId)) {
            requestedReleaseReasons.putIfAbsent(generationId, reason)
        }
    }

    @Synchronized
    fun release(generationId: Long): Lease? {
        val listener = owners.remove(generationId) ?: return null
        val reason = requestedReleaseReasons.remove(generationId)
        return Lease(generationId, listener, reason)
    }

    @Synchronized
    fun releaseAll(fallbackReason: DeckReleaseReason): List<Lease> {
        val leases = ArrayList<Lease>(owners.size)
        for ((key, value) in owners) {
            val reason = requestedReleaseReasons[key] ?: fallbackReason
            leases.add(Lease(key, value, reason))
        }
        owners.clear()
        requestedReleaseReasons.clear()
        return Collections.unmodifiableList(leases)
    }

    @Synchronized
    fun hasOutstandingLeases(): Boolean {
        return owners.isNotEmpty()
    }
}
