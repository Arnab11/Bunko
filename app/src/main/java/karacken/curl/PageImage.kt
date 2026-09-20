package karacken.curl

/**
 * Immutable client page identity paired with already prepared image content.
 *
 * For the Android production API, [content] is an immutable, opaque ARGB_8888 bitmap.
 * The client retains ownership but must not modify or recycle the bitmap after submitting its deck.
 * The client may release or recycle it only after [PageSurfaceListener.onDeckReleased] reports that generation.
 */
class PageImage<T> @JvmOverloads constructor(
    val generationId: Long,
    val logicalPageId: String,
    val ordinal: Int,
    val widthPx: Int,
    val heightPx: Int,
    val content: T,
    val overlayContent: T? = null
) {
    init {
        require(logicalPageId.isNotBlank()) { "logicalPageId must not be blank" }
        require(ordinal >= 0) { "ordinal must not be negative" }
        require(widthPx > 0 && heightPx > 0) { "page dimensions must be positive" }
        requireNotNull(content) { "content must not be null" }
    }

    val hasOverlay: Boolean
        get() = overlayContent != null

    internal fun identityKey(): String {
        return "$generationId\u0000$logicalPageId\u0000$ordinal"
    }

    internal fun overlayIdentityKey(): String {
        return "${identityKey()}\u0000overlay"
    }
}
