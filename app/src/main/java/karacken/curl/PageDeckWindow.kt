package karacken.curl

import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/** Normalized unique page sequence used by the unchanged PlayLikeCurl position model. */
internal class PageDeckWindow<T> private constructor(
    val pages: List<PageImage<T>>,
    val currentIndex: Int
) {
    companion object {
        fun <T> from(deck: PageDeck<T>): PageDeckWindow<T> {
            val current: PageImage<T> = when (deck) {
                is PortraitPageDeck<T> -> deck.current
                is LandscapePageDeck<T> -> deck.currentLeft
                else -> throw IllegalArgumentException("Unsupported page deck type")
            }

            val uniquePages = LinkedHashMap<String, PageImage<T>>()
            for (page in deck.pages) {
                uniquePages.putIfAbsent(page.identityKey(), page)
            }
            val normalized = ArrayList(uniquePages.values)
            var normalizedCurrentIndex = -1
            for (index in normalized.indices) {
                if (normalized[index].identityKey() == current.identityKey()) {
                    normalizedCurrentIndex = index
                    break
                }
            }
            if (normalizedCurrentIndex < 0) {
                throw IllegalArgumentException("Deck does not contain its current page")
            }
            return PageDeckWindow(Collections.unmodifiableList(normalized), normalizedCurrentIndex)
        }
    }
}
