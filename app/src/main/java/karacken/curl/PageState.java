package karacken.curl;

final class PageState {
    private final PageRole role;
    private final float depth;
    private float curlPosition;
    private int pageIndex;

    PageState(PageRole role, float depth, float curlPosition, int pageIndex) {
        this.role = role;
        this.depth = depth;
        this.curlPosition = curlPosition;
        this.pageIndex = pageIndex;
    }

    PageRole getRole() {
        return role;
    }

    float getDepth() {
        return depth;
    }

    float getCurlPosition() {
        return curlPosition;
    }

    void setCurlPosition(float curlPosition) {
        this.curlPosition = curlPosition;
    }

    int getPageIndex() {
        return pageIndex;
    }

    void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }
}
