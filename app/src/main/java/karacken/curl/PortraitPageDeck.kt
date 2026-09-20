package karacken.curl

/** Previous, current, and next portrait pages for one interaction window. */
class PortraitPageDeck<T>(
    val previous: PageImage<T>,
    val current: PageImage<T>,
    val next: PageImage<T>
) : PageDeck<T> {
    override val generationId: Long = current.generationId
    override val mode: PageDeckMode = PageDeckMode.PORTRAIT
    override val pages: List<PageImage<T>> = listOf(previous, current, next)

    init {
        requireGeneration(previous)
        requireGeneration(next)
    }

    private fun requireGeneration(page: PageImage<T>) {
        require(page.generationId == generationId) {
            "All portrait pages must share one generation"
        }
    }
}
