package karacken.curl

enum class RenderFailureReason {
    SHADER,
    BITMAP,
    TEXTURE_TOO_LARGE,
    GPU_BUDGET_EXCEEDED,
    TEXTURE_UPLOAD,
    CONTEXT,
    DISPOSED
}
