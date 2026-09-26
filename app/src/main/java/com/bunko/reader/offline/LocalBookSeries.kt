package com.bunko.reader.offline

/**
 * Shared series/issue grouping logic for offline (local) books.
 *
 * Local books have no server-side volumes/chapters, so siblings are derived
 * from file metadata: explicit [LocalBook.seriesName] first, then filename
 * heuristics (common base title + volume/issue numbers). Used by both the
 * library stacks and the book detail "Issues" section so they always agree.
 */

/** Strips trailing volume/chapter/issue markers: "One Piece Vol 12" -> "One Piece". */
fun extractSeriesBaseTitle(title: String): String {
    val cleaned = title
        .replace(Regex("""\s*[\(\[](?:vol(?:ume)?|ch(?:apter)?|issue|ep(?:isode)?|v|c|#)\s*\d+[\)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[\s_\-]+(?:vol(?:ume)?|ch(?:apter)?|issue|ep(?:isode)?|v|c|#)\s*\.?\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[\s_\-]+#?\d+\s*$"""), "")
        .trim()
    return if (cleaned.isBlank()) title.trim() else cleaned
}

/** Last bare/trailing number in a title: "One Piece 12" -> 12. Null when absent. */
fun extractTrailingNumber(title: String): Int? {
    val match = Regex("(?:vol(?:ume)?|v|ch(?:apter)?|issue|#|-)?\\s*\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .findAll(title).lastOrNull()
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

/**
 * Parses a volume/issue number from a cleaned filename title.
 * Returns "" when the title carries no number evidence.
 *
 * Handles: "One Piece 12", "One Piece #12", "Berserk v3", "Naruto ch. 5",
 * "Bleach [Vol 2]", "Saga (Issue 7)". Guards against years ("Book 2024")
 * and dotted abbreviations by requiring the number to be at the end
 * (allowing a trailing bracket/paren).
 */
fun parseVolumeOrIssue(title: String): String {
    val s = title.trim()
    if (s.isEmpty()) return ""
    val m = Regex(
        """(?i)(?:\b(?:vol(?:ume)?|v|ch(?:apter)?|issue|ep(?:isode)?|#)\s*\.?\s*(\d{1,4})|[\s_\-_\[]#?(\d{1,4}))\s*[\]\)]?\s*$"""
    ).find(s) ?: return ""
    val num = (m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
        ?: m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        ?: return "").toIntOrNull() ?: return ""
    // Reject year-like values so "History 2024" doesn't become issue #2024.
    if (num in 1900..2100) return ""
    return num.toString()
}

/** Sort key: parsed number first, then title. */
fun offlineIssueSortKey(book: LocalBook): Int =
    book.volumeOrIssue.toIntOrNull()
        ?: extractTrailingNumber(book.title)
        ?: Int.MAX_VALUE

/**
 * Two catalog entries pointing at the same underlying file (e.g. a scanned
 * folder copy plus an opened external-file entry). Matches on id/uri, or on
 * identical title + format + size as a fallback.
 */
fun isSameLocalFile(a: LocalBook, b: LocalBook): Boolean {
    if (a.id == b.id) return true
    if (a.uriString.isNotBlank() && a.uriString == b.uriString) return true
    return a.title.equals(b.title, ignoreCase = true) &&
        a.extension.equals(b.extension, ignoreCase = true) &&
        a.sizeBytes > 0 && a.sizeBytes == b.sizeBytes
}

/** Catalog with same-file duplicates collapsed (keeps first occurrence). */
fun distinctLocalFiles(books: List<LocalBook>): List<LocalBook> {
    val out = ArrayList<LocalBook>(books.size)
    for (book in books) {
        if (out.none { isSameLocalFile(it, book) }) out.add(book)
    }
    return out
}

/**
 * Sibling issues of [currentBook] within [allBooks], **excluding** the current
 * book itself. Empty when the book is a standalone single.
 *
 * Match priority: explicit seriesName -> shared base title. Base-title groups
 * additionally require number evidence or differing titles, so two copies of
 * one file (or unrelated books mangled to one base) never form a phantom
 * "Issues" row.
 */
fun findSiblingOfflineBooks(currentBook: LocalBook, allBooks: List<LocalBook>): List<LocalBook> {
    val distinct = distinctLocalFiles(allBooks).filter { it.id != currentBook.id }

    // 1. Explicit series name (set via Edit Metadata).
    if (currentBook.seriesName.isNotBlank()) {
        val series = currentBook.seriesName.trim()
        val matches = distinct.filter {
            it.seriesName.isNotBlank() && it.seriesName.trim().equals(series, ignoreCase = true)
        }
        if (matches.isNotEmpty()) {
            return matches.sortedWith(compareBy<LocalBook>({ offlineIssueSortKey(it) }, { it.title }))
        }
    }

    // 2. Shared base title from filenames.
    val baseTitle = extractSeriesBaseTitle(currentBook.title)
    if (baseTitle.isNotBlank() && baseTitle.length >= 3) {
        val matches = distinct.filter { book ->
            val otherBase = extractSeriesBaseTitle(book.title)
            otherBase.isNotBlank() && otherBase.equals(baseTitle, ignoreCase = true)
        }
        if (matches.isNotEmpty()) {
            val numbered = matches.count {
                it.volumeOrIssue.toIntOrNull() != null || extractTrailingNumber(it.title) != null
            }
            val titlesDiffer = matches.any { !it.title.equals(currentBook.title, ignoreCase = true) }
            // Without numbers and with identical titles this is the same book
            // (or an indistinguishable copy) — not a series.
            if (numbered > 0 || titlesDiffer) {
                return matches.sortedWith(compareBy<LocalBook>({ offlineIssueSortKey(it) }, { it.title }))
            }
        }
    }

    return emptyList()
}
