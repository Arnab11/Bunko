package karacken.curl;

import java.util.List;
import java.util.Objects;

/** Previous, current, and next portrait pages for one interaction window. */
public final class PortraitPageDeck<T> implements PageDeck<T> {
    private final long generationId;
    private final PageImage<T> previous;
    private final PageImage<T> current;
    private final PageImage<T> next;
    private final List<PageImage<T>> pages;

    public PortraitPageDeck(
            PageImage<T> previous,
            PageImage<T> current,
            PageImage<T> next) {
        this.previous = Objects.requireNonNull(previous, "previous");
        this.current = Objects.requireNonNull(current, "current");
        this.next = Objects.requireNonNull(next, "next");
        generationId = current.getGenerationId();
        requireGeneration(previous);
        requireGeneration(next);
        pages = List.of(previous, current, next);
    }

    @Override
    public long getGenerationId() {
        return generationId;
    }

    @Override
    public PageDeckMode getMode() {
        return PageDeckMode.PORTRAIT;
    }

    @Override
    public List<PageImage<T>> getPages() {
        return pages;
    }

    public PageImage<T> getPrevious() {
        return previous;
    }

    public PageImage<T> getCurrent() {
        return current;
    }

    public PageImage<T> getNext() {
        return next;
    }

    private void requireGeneration(PageImage<T> page) {
        if (page.getGenerationId() != generationId) {
            throw new IllegalArgumentException("All portrait pages must share one generation");
        }
    }
}
