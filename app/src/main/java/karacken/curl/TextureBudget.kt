package karacken.curl

import java.util.LinkedHashMap

/** Pure active-plus-pending texture sizing used before any GLES allocation. */
internal object TextureBudget {
    private const val BYTES_PER_PIXEL = 4L

    internal class Result(
        val failureReason: RenderFailureReason?,
        val requiredBytes: Long,
        val gpuBudgetBytes: Long,
        val requestedWidthPx: Int,
        val requestedHeightPx: Int,
        val maxTextureSize: Int
    )

    fun evaluate(
        activeDeck: PageDeck<*>?,
        pendingDeck: PageDeck<*>?,
        maxTextureSize: Int,
        gpuBudgetBytes: Long
    ): Result {
        require(maxTextureSize > 0) { "maxTextureSize must be positive" }
        require(gpuBudgetBytes > 0) { "gpuBudgetBytes must be positive" }

        val uniquePages = LinkedHashMap<String, PageImage<*>>()
        collect(activeDeck, uniquePages)
        collect(pendingDeck, uniquePages)

        var requiredBytes = 0L
        for (page in uniquePages.values) {
            val widthPx = page.widthPx
            val heightPx = page.heightPx
            if (widthPx > maxTextureSize || heightPx > maxTextureSize) {
                return Result(
                    RenderFailureReason.TEXTURE_TOO_LARGE,
                    requiredBytes,
                    gpuBudgetBytes,
                    widthPx,
                    heightPx,
                    maxTextureSize
                )
            }
            requiredBytes = Math.addExact(
                requiredBytes,
                Math.multiplyExact(
                    Math.multiplyExact(widthPx.toLong(), heightPx.toLong()),
                    BYTES_PER_PIXEL
                )
            )
        }

        val failureReason = if (requiredBytes > gpuBudgetBytes) {
            RenderFailureReason.GPU_BUDGET_EXCEEDED
        } else {
            null
        }

        return Result(
            failureReason,
            requiredBytes,
            gpuBudgetBytes,
            0,
            0,
            maxTextureSize
        )
    }

    private fun collect(
        deck: PageDeck<*>?,
        uniquePages: MutableMap<String, PageImage<*>>
    ) {
        if (deck == null) return
        for (page in deck.pages) {
            uniquePages.putIfAbsent(page.identityKey(), page)
            if (page.hasOverlay) {
                uniquePages.putIfAbsent(page.overlayIdentityKey(), page)
            }
        }
    }
}
