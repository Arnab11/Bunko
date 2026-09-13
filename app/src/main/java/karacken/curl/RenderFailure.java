package karacken.curl;

/** Structured renderer failure reported to the client instead of thrown from GL callbacks. */
public final class RenderFailure {
    private final long generationId;
    private final boolean recoverable;
    private final RenderFailureReason reason;
    private final String message;
    private final Throwable cause;
    private final int requestedWidthPx;
    private final int requestedHeightPx;
    private final int maxTextureSize;
    private final long requiredBytes;
    private final long gpuBudgetBytes;

    public RenderFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause) {
        this(
                generationId,
                recoverable,
                reason,
                message,
                cause,
                0,
                0,
                0,
                0L,
                0L);
    }

    public RenderFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause,
            int requestedWidthPx,
            int requestedHeightPx,
            int maxTextureSize,
            long requiredBytes,
            long gpuBudgetBytes) {
        this.generationId = generationId;
        this.recoverable = recoverable;
        this.reason = reason;
        this.message = message;
        this.cause = cause;
        this.requestedWidthPx = requestedWidthPx;
        this.requestedHeightPx = requestedHeightPx;
        this.maxTextureSize = maxTextureSize;
        this.requiredBytes = requiredBytes;
        this.gpuBudgetBytes = gpuBudgetBytes;
    }

    public long getGenerationId() {
        return generationId;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public RenderFailureReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getCause() {
        return cause;
    }

    public int getRequestedWidthPx() {
        return requestedWidthPx;
    }

    public int getRequestedHeightPx() {
        return requestedHeightPx;
    }

    public int getMaxTextureSize() {
        return maxTextureSize;
    }

    public long getRequiredBytes() {
        return requiredBytes;
    }

    public long getGpuBudgetBytes() {
        return gpuBudgetBytes;
    }
}
