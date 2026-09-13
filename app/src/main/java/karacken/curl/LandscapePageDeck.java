package karacken.curl;

import java.util.List;
import java.util.Objects;

/** Previous, current, and next two-leaf spreads for one landscape interaction window. */
public final class LandscapePageDeck<T> implements PageDeck<T> {
    private final long generationId;
    private final PageImage<T> previousLeft;
    private final PageImage<T> previousRight;
    private final PageImage<T> currentLeft;
    private final PageImage<T> currentRight;
    private final PageImage<T> nextLeft;
    private final PageImage<T> nextRight;
    private final List<PageImage<T>> pages;

    public LandscapePageDeck(
            PageImage<T> previousLeft,
            PageImage<T> previousRight,
            PageImage<T> currentLeft,
            PageImage<T> currentRight,
            PageImage<T> nextLeft,
            PageImage<T> nextRight) {
        this.previousLeft = Objects.requireNonNull(previousLeft, "previousLeft");
        this.previousRight = Objects.requireNonNull(previousRight, "previousRight");
        this.currentLeft = Objects.requireNonNull(currentLeft, "currentLeft");
        this.currentRight = Objects.requireNonNull(currentRight, "currentRight");
        this.nextLeft = Objects.requireNonNull(nextLeft, "nextLeft");
        this.nextRight = Objects.requireNonNull(nextRight, "nextRight");
        generationId = currentLeft.getGenerationId();
        requireGeneration(previousLeft);
        requireGeneration(previousRight);
        requireGeneration(currentRight);
        requireGeneration(nextLeft);
        requireGeneration(nextRight);
        pages = List.of(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight);
    }

    @Override
    public long getGenerationId() {
        return generationId;
    }

    @Override
    public PageDeckMode getMode() {
        return PageDeckMode.LANDSCAPE;
    }

    @Override
    public List<PageImage<T>> getPages() {
        return pages;
    }

    public PageImage<T> getPreviousLeft() {
        return previousLeft;
    }

    public PageImage<T> getPreviousRight() {
        return previousRight;
    }

    public PageImage<T> getCurrentLeft() {
        return currentLeft;
    }

    public PageImage<T> getCurrentRight() {
        return currentRight;
    }

    public PageImage<T> getNextLeft() {
        return nextLeft;
    }

    public PageImage<T> getNextRight() {
        return nextRight;
    }

    private void requireGeneration(PageImage<T> page) {
        if (page.getGenerationId() != generationId) {
            throw new IllegalArgumentException("All landscape leaves must share one generation");
        }
    }
}
