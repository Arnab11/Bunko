package karacken.curl

/** Device and policy limits that must be known before a page deck is accepted. */
class RenderCapabilities(
    val maxTextureSize: Int,
    val gpuBudgetBytes: Long
) {
    init {
        require(maxTextureSize > 0) { "maxTextureSize must be positive" }
        require(gpuBudgetBytes > 0) { "gpuBudgetBytes must be positive" }
    }

    val bytesPerPixel: Int
        get() = BYTES_PER_PIXEL

    companion object {
        private const val BYTES_PER_PIXEL = 4
    }
}
