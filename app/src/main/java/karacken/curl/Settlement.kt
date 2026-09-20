package karacken.curl

internal class Settlement(
    val targetPercent: Int,
    val durationMillis: Long,
    val interpolator: SettlementInterpolator,
    val pageChange: PageChange
)
