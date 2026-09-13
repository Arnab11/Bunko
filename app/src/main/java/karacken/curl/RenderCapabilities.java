package karacken.curl;

/** Device and policy limits that must be known before a page deck is accepted. */
public final class RenderCapabilities {
    private static final int BYTES_PER_PIXEL = 4;

    private final int maxTextureSize;
    private final long gpuBudgetBytes;

    public RenderCapabilities(int maxTextureSize, long gpuBudgetBytes) {
        if (maxTextureSize <= 0) {
            throw new IllegalArgumentException("maxTextureSize must be positive");
        }
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        this.maxTextureSize = maxTextureSize;
        this.gpuBudgetBytes = gpuBudgetBytes;
    }

    public int getMaxTextureSize() {
        return maxTextureSize;
    }

    public long getGpuBudgetBytes() {
        return gpuBudgetBytes;
    }

    public int getBytesPerPixel() {
        return BYTES_PER_PIXEL;
    }
}
