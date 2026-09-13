package karacken.curl;

final class Settlement {
    private final int targetPercent;
    private final long durationMillis;
    private final SettlementInterpolator interpolator;
    private final PageChange pageChange;

    Settlement(
            int targetPercent,
            long durationMillis,
            SettlementInterpolator interpolator,
            PageChange pageChange) {
        this.targetPercent = targetPercent;
        this.durationMillis = durationMillis;
        this.interpolator = interpolator;
        this.pageChange = pageChange;
    }

    int getTargetPercent() {
        return targetPercent;
    }

    long getDurationMillis() {
        return durationMillis;
    }

    SettlementInterpolator getInterpolator() {
        return interpolator;
    }

    PageChange getPageChange() {
        return pageChange;
    }
}
