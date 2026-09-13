package karacken.curl;

/**
 * Two-page spread adapter around PlayLikeCurl's unchanged gesture and settlement model.
 * Page indices are zero based, so the first visible spread is 0 | 1.
 */
final class LandscapeSpreadModel {
    private final int pageCount;
    private final int spreadCount;
    private final PlayLikeCurlModel motionModel;

    LandscapeSpreadModel(int pageCount, int initialPageIndex) {
        if (pageCount <= 0) {
            throw new IllegalArgumentException("A landscape spread requires at least one page");
        }
        if (initialPageIndex < 0 || initialPageIndex >= pageCount) {
            throw new IllegalArgumentException("Initial page is outside the adapter");
        }
        this.pageCount = pageCount;
        spreadCount = (pageCount + 1) / 2;
        motionModel = new PlayLikeCurlModel(spreadCount, initialPageIndex / 2);
    }

    PlayLikeCurlModel getMotionModel() {
        return motionModel;
    }

    int getCurrentLeftPageIndex() {
        return pageIndexForSpread(motionModel.getCurrentPosition(), false);
    }

    int getCurrentRightPageIndex() {
        return pageIndexForSpread(motionModel.getCurrentPosition(), true);
    }

    int getPreviousLeftPageIndex() {
        return pageIndexForSpread(Math.max(0, motionModel.getCurrentPosition() - 1), false);
    }

    int getPreviousRightPageIndex() {
        return pageIndexForSpread(Math.max(0, motionModel.getCurrentPosition() - 1), true);
    }

    int getNextLeftPageIndex() {
        return pageIndexForSpread(
                Math.min(spreadCount - 1, motionModel.getCurrentPosition() + 1), false);
    }

    int getNextRightPageIndex() {
        return pageIndexForSpread(
                Math.min(spreadCount - 1, motionModel.getCurrentPosition() + 1), true);
    }

    int getForwardTurningPageIndex() {
        return getCurrentRightPageIndex();
    }

    int getForwardReversePageIndex() {
        return getNextLeftPageIndex();
    }

    int getForwardUnderneathPageIndex() {
        return getNextRightPageIndex();
    }

    int getBackwardTurningPageIndex() {
        return getCurrentLeftPageIndex();
    }

    int getBackwardReversePageIndex() {
        return getPreviousRightPageIndex();
    }

    int getBackwardUnderneathPageIndex() {
        return getPreviousLeftPageIndex();
    }

    int getCurrentPageIndex() {
        return getCurrentLeftPageIndex();
    }

    void jumpTo(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Page is outside the adapter");
        }
        motionModel.jumpTo(pageIndex / 2);
    }

    void beginGesture(float x) {
        motionModel.beginGesture(x);
    }

    void dragTo(float x, float leafWidth) {
        float endpointAdjustedWidth = leafWidth
                * PlayLikeCurlModel.GRID
                / (PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION);
        motionModel.dragTo(x, endpointAdjustedWidth);
    }

    LandscapeSpreadTransition getTransition() {
        return LandscapeSpreadTransition.from(motionModel);
    }

    Settlement release() {
        return motionModel.release();
    }

    Settlement flingTowardNext() {
        return motionModel.flingTowardNext();
    }

    Settlement flingTowardPrevious() {
        return motionModel.flingTowardPrevious();
    }

    void updateSettlement(float valuePercent) {
        motionModel.updateSettlement(valuePercent);
    }

    void cancelGesture() {
        motionModel.cancelGesture();
    }

    void completeSettlement(Settlement settlement) {
        motionModel.completeSettlement(settlement);
    }

    private int pageIndexForSpread(int spreadIndex, boolean right) {
        int rawIndex = spreadIndex * 2 + (right ? 1 : 0);
        return Math.min(pageCount - 1, rawIndex);
    }
}
