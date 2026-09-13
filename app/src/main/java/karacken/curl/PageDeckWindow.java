package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalized unique page sequence used by the unchanged PlayLikeCurl position model. */
final class PageDeckWindow<T> {
    private final List<PageImage<T>> pages;
    private final int currentIndex;

    private PageDeckWindow(List<PageImage<T>> pages, int currentIndex) {
        this.pages = Collections.unmodifiableList(pages);
        this.currentIndex = currentIndex;
    }

    static <T> PageDeckWindow<T> from(PageDeck<T> deck) {
        PageImage<T> current;
        if (deck instanceof PortraitPageDeck) {
            current = ((PortraitPageDeck<T>) deck).getCurrent();
        } else if (deck instanceof LandscapePageDeck) {
            current = ((LandscapePageDeck<T>) deck).getCurrentLeft();
        } else {
            throw new IllegalArgumentException("Unsupported page deck type");
        }

        Map<String, PageImage<T>> uniquePages = new LinkedHashMap<>();
        for (PageImage<T> page : deck.getPages()) {
            uniquePages.putIfAbsent(page.identityKey(), page);
        }
        List<PageImage<T>> normalized = new ArrayList<>(uniquePages.values());
        int normalizedCurrentIndex = -1;
        for (int index = 0; index < normalized.size(); index++) {
            if (normalized.get(index).identityKey().equals(current.identityKey())) {
                normalizedCurrentIndex = index;
                break;
            }
        }
        if (normalizedCurrentIndex < 0) {
            throw new IllegalArgumentException("Deck does not contain its current page");
        }
        return new PageDeckWindow<>(normalized, normalizedCurrentIndex);
    }

    List<PageImage<T>> getPages() {
        return pages;
    }

    int getCurrentIndex() {
        return currentIndex;
    }
}
