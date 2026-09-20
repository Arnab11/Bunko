package karacken.curl

/** Structured renderer failure reported to the client instead of thrown from GL callbacks. */
class RenderFailure @JvmOverloads constructor(
    val generationId: Long,
    val isRecoverable: Boolean,
    val reason: RenderFailureReason,
    val message: String?,
    val cause: Throwable?,
    val requestedWidthPx: Int = 0,
    val requestedHeightPx: Int = 0,
    val maxTextureSize: Int = 0,
    val requiredBytes: Long = 0L,
    val gpuBudgetBytes: Long = 0L
)
