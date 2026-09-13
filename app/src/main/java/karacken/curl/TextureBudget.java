package karacken.curl;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure active-plus-pending texture sizing used before any GLES allocation. */
final class TextureBudget {
    private static final long BYTES_PER_PIXEL = 4L;

    static final class Result {
        private final RenderFailureReason failureReason;
        private final long requiredBytes;
        private final long gpuBudgetBytes;
        private final int requestedWidthPx;
        private final int requestedHeightPx;
        private final int maxTextureSize;

        private Result(
                RenderFailureReason failureReason,
                long requiredBytes,
                long gpuBudgetBytes,
                int requestedWidthPx,
                int requestedHeightPx,
                int maxTextureSize) {
            this.failureReason = failureReason;
            this.requiredBytes = requiredBytes;
            this.gpuBudgetBytes = gpuBudgetBytes;
            this.requestedWidthPx = requestedWidthPx;
            this.requestedHeightPx = requestedHeightPx;
            this.maxTextureSize = maxTextureSize;
        }

        RenderFailureReason getFailureReason() {
            return failureReason;
        }

        long getRequiredBytes() {
            return requiredBytes;
        }

        long getGpuBudgetBytes() {
            return gpuBudgetBytes;
        }

        int getRequestedWidthPx() {
            return requestedWidthPx;
        }

        int getRequestedHeightPx() {
            return requestedHeightPx;
        }

        int getMaxTextureSize() {
            return maxTextureSize;
        }
    }

    private TextureBudget() {}

    static Result evaluate(
            PageDeck<?> activeDeck,
            PageDeck<?> pendingDeck,
            int maxTextureSize,
            long gpuBudgetBytes) {
        if (maxTextureSize <= 0) {
            throw new IllegalArgumentException("maxTextureSize must be positive");
        }
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }

        Map<String, PageImage<?>> uniquePages = new LinkedHashMap<>();
        collect(activeDeck, uniquePages);
        collect(pendingDeck, uniquePages);

        long requiredBytes = 0L;
        for (PageImage<?> page : uniquePages.values()) {
            int widthPx = page.getWidthPx();
            int heightPx = page.getHeightPx();
            if (widthPx > maxTextureSize || heightPx > maxTextureSize) {
                return new Result(
                        RenderFailureReason.TEXTURE_TOO_LARGE,
                        requiredBytes,
                        gpuBudgetBytes,
                        widthPx,
                        heightPx,
                        maxTextureSize);
            }
            requiredBytes = Math.addExact(
                    requiredBytes,
                    Math.multiplyExact(
                            Math.multiplyExact((long) widthPx, (long) heightPx),
                            BYTES_PER_PIXEL));
        }

        RenderFailureReason failureReason =
                requiredBytes > gpuBudgetBytes
                        ? RenderFailureReason.GPU_BUDGET_EXCEEDED
                        : null;
        return new Result(
                failureReason,
                requiredBytes,
                gpuBudgetBytes,
                0,
                0,
                maxTextureSize);
    }

    private static void collect(
            PageDeck<?> deck,
            Map<String, PageImage<?>> uniquePages) {
        if (deck == null) {
            return;
        }
        for (PageImage<?> page : deck.getPages()) {
            uniquePages.putIfAbsent(page.identityKey(), page);
            if (page.hasOverlay()) {
                uniquePages.putIfAbsent(page.overlayIdentityKey(), page);
            }
        }
    }
}
