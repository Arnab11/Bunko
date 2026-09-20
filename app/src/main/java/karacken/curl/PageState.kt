package karacken.curl

internal data class PageState(
    val role: PageRole,
    val depth: Float,
    var curlPosition: Float,
    var pageIndex: Int
)
