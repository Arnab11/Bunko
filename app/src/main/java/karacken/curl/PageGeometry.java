package karacken.curl;

final class PageGeometry {
    private final PageRole role;
    private final float bitmapRatio;
    private final float[] positions;
    private final float[] textureCoordinates;
    private final short[] indices;

    PageGeometry(
            PageRole role,
            float bitmapRatio,
            float[] positions,
            float[] textureCoordinates,
            short[] indices) {
        this.role = role;
        this.bitmapRatio = bitmapRatio;
        this.positions = positions;
        this.textureCoordinates = textureCoordinates;
        this.indices = indices;
    }

    PageRole getRole() {
        return role;
    }

    float getBitmapRatio() {
        return bitmapRatio;
    }

    float[] getPositions() {
        return positions;
    }

    float[] getTextureCoordinates() {
        return textureCoordinates;
    }

    short[] getIndices() {
        return indices;
    }

    float positionY(int column, int row) {
        return positions[3 * (row * (PlayLikeCurlModel.GRID + 1) + column) + 1];
    }
}
