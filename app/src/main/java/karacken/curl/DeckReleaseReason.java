package karacken.curl;

/** Why an accepted page-deck bitmap lease is no longer retained by the library. */
public enum DeckReleaseReason {
    REPLACED,
    EXPLICIT,
    FAILED,
    SESSION_DETACHED,
    DISPOSED
}
