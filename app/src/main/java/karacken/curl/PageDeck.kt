package karacken.curl

/** Exact set of physical pages required for one bidirectional interaction window. */
interface PageDeck<T> {
    val generationId: Long
    val mode: PageDeckMode
    val pages: List<PageImage<T>>
}
