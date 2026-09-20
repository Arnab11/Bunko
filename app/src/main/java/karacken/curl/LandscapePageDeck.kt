package karacken.curl

/** Previous, current, and next two-leaf spreads for one landscape interaction window. */
class LandscapePageDeck<T>(
    val previousLeft: PageImage<T>,
    val previousRight: PageImage<T>,
    val currentLeft: PageImage<T>,
    val currentRight: PageImage<T>,
    val nextLeft: PageImage<T>,
    val nextRight: PageImage<T>
) : PageDeck<T> {
    override val generationId: Long = currentLeft.generationId
    override val mode: PageDeckMode = PageDeckMode.LANDSCAPE
    override val pages: List<PageImage<T>> = listOf(
        previousLeft,
        previousRight,
        currentLeft,
        currentRight,
        nextLeft,
        nextRight
    )

    init {
        requireGeneration(previousLeft)
        requireGeneration(previousRight)
        requireGeneration(currentRight)
        requireGeneration(nextLeft)
        requireGeneration(nextRight)
    }

    private fun requireGeneration(page: PageImage<T>) {
        require(page.generationId == generationId) {
            "All landscape leaves must share one generation"
        }
    }
}
