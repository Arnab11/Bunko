package karacken.curl;

import java.util.List;

/** Exact set of physical pages required for one bidirectional interaction window. */
public interface PageDeck<T> {
    long getGenerationId();

    PageDeckMode getMode();

    List<PageImage<T>> getPages();
}
