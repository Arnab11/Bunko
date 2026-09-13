package karacken.curl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps each accepted bitmap lease bound to the listener that acquired it.
 *
 * <p>The registry also makes terminal release idempotent when renderer and lifecycle cleanup
 * converge on the same generation.
 */
final class DeckLeaseRegistry {
    static final class Lease {
        private final long generationId;
        private final PageSurfaceListener listener;
        private final DeckReleaseReason releaseReason;

        private Lease(
                long generationId,
                PageSurfaceListener listener,
                DeckReleaseReason releaseReason) {
            this.generationId = generationId;
            this.listener = listener;
            this.releaseReason = releaseReason;
        }

        long getGenerationId() {
            return generationId;
        }

        PageSurfaceListener getListener() {
            return listener;
        }

        DeckReleaseReason getReleaseReason() {
            return releaseReason;
        }
    }

    private final Map<Long, PageSurfaceListener> owners = new LinkedHashMap<>();
    private final Map<Long, DeckReleaseReason> requestedReleaseReasons =
            new LinkedHashMap<>();

    synchronized boolean acquire(long generationId, PageSurfaceListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (owners.containsKey(generationId)) {
            return false;
        }
        owners.put(generationId, listener);
        return true;
    }

    synchronized PageSurfaceListener listenerFor(
            long generationId,
            PageSurfaceListener fallback) {
        PageSurfaceListener owner = owners.get(generationId);
        return owner == null ? fallback : owner;
    }

    synchronized PageSurfaceListener ownerFor(long generationId) {
        return owners.get(generationId);
    }

    synchronized void markReleaseRequested(
            long generationId,
            DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (owners.containsKey(generationId)) {
            requestedReleaseReasons.putIfAbsent(generationId, reason);
        }
    }

    synchronized Lease release(long generationId) {
        PageSurfaceListener listener = owners.remove(generationId);
        DeckReleaseReason reason = requestedReleaseReasons.remove(generationId);
        return listener == null ? null : new Lease(generationId, listener, reason);
    }

    synchronized List<Lease> releaseAll(DeckReleaseReason fallbackReason) {
        Objects.requireNonNull(fallbackReason, "fallbackReason");
        List<Lease> leases = new ArrayList<>(owners.size());
        for (Map.Entry<Long, PageSurfaceListener> entry : owners.entrySet()) {
            DeckReleaseReason reason =
                    requestedReleaseReasons.getOrDefault(entry.getKey(), fallbackReason);
            leases.add(new Lease(entry.getKey(), entry.getValue(), reason));
        }
        owners.clear();
        requestedReleaseReasons.clear();
        return List.copyOf(leases);
    }

    synchronized boolean hasOutstandingLeases() {
        return !owners.isEmpty();
    }
}
