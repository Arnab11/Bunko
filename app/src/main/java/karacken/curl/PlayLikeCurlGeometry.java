package karacken.curl;

final class PlayLikeCurlGeometry {
    static final float CAMERA_DISTANCE = 2f;

    private PlayLikeCurlGeometry() {
    }

    static PageGeometry createPage(
            PageRole role,
            int bitmapWidth,
            int bitmapHeight,
            PageOrientation orientation) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            throw new IllegalArgumentException("Bitmap dimensions must be positive");
        }
        float bitmapRatio = bitmapRatio(bitmapWidth, bitmapHeight, orientation);
        int vertexCount = (PlayLikeCurlModel.GRID + 1) * (PlayLikeCurlModel.GRID + 1);
        PageGeometry page = new PageGeometry(
                role,
                bitmapRatio,
                new float[vertexCount * 3],
                createTextureCoordinates(),
                createIndices());
        update(
                page,
                role == PageRole.LEFT
                        ? PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
                        : PlayLikeCurlModel.GRID,
                false);
        return page;
    }

    static void update(PageGeometry page, float curlPosition, boolean active) {
        int grid = PlayLikeCurlModel.GRID;
        float heightCorrection = (page.getBitmapRatio() - 1f) / 2f;
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 3 * (row * (grid + 1) + column);
                float normalizedX = column / (float) grid;
                if (page.getRole() == PageRole.FRONT) {
                    page.getPositions()[offset] = frontX(column, curlPosition);
                } else if (page.getRole() == PageRole.LEFT) {
                    page.getPositions()[offset] = leftX(column, curlPosition);
                } else {
                    page.getPositions()[offset] = normalizedX;
                }
                page.getPositions()[offset + 1] =
                        row / (float) grid * page.getBitmapRatio() - heightCorrection;
                page.getPositions()[offset + 2] = active
                        ? activeDepth(page.getRole(), column, curlPosition)
                        : depth(page.getRole());
            }
        }
    }

    static float projectionAspect(int width, int height) {
        return width / (float) height;
    }

    static float bitmapRatio(
            int width,
            int height,
            PageOrientation orientation) {
        return orientation == PageOrientation.PORTRAIT
                ? height / (float) width
                : width / (float) height;
    }

    static float foldEdgeX(PageRole role, float curlPosition) {
        if (role == PageRole.FRONT) {
            return frontX(PlayLikeCurlModel.GRID, curlPosition);
        }
        if (role == PageRole.LEFT) {
            return leftX(PlayLikeCurlModel.GRID, curlPosition);
        }
        return 1f;
    }

    static float foldEdgeDepth(PageRole role, float curlPosition) {
        return activeDepth(role, PlayLikeCurlModel.GRID, curlPosition);
    }

    static float projectXOntoDepthPlane(
            float sourceX,
            float sourceDepth,
            float targetDepth) {
        return 0.5f
                + (sourceX - 0.5f)
                * (CAMERA_DISTANCE - targetDepth)
                / (CAMERA_DISTANCE - sourceDepth);
    }

    private static float frontX(int column, float curlPosition) {
        float percentage = 1f - curlPosition / PlayLikeCurlModel.GRID;
        float radius = resolvedRadius(percentage);
        float movement = percentage > 0.05f ? percentage - 0.05f : 0f;
        return column / (float) PlayLikeCurlModel.GRID * (1f - radius) - movement;
    }

    private static float leftX(int column, float curlPosition) {
        float percentage = (1f - curlPosition / PlayLikeCurlModel.GRID) * 0.75f;
        float radius = resolvedRadius(percentage);
        return column / (float) PlayLikeCurlModel.GRID * (1f - radius) - percentage;
    }

    private static float activeDepth(PageRole role, int column, float curlPosition) {
        if (role == PageRole.RIGHT) return PlayLikeCurlModel.RIGHT_DEPTH;
        float rawPercentage = 1f - curlPosition / PlayLikeCurlModel.GRID;
        float percentage = role == PageRole.LEFT ? rawPercentage * 0.75f : rawPercentage;
        float radius = resolvedRadius(percentage);
        float waveWidth = role == PageRole.LEFT ? 0.50f : 0.60f;
        float delta = PlayLikeCurlModel.GRID - curlPosition;
        return (float) (
                radius * Math.sin(
                        3.14f / (PlayLikeCurlModel.GRID * waveWidth) * (column - delta))
                        + radius * 1.1f);
    }

    private static float resolvedRadius(float percentage) {
        return percentage < 0.20f
                ? PlayLikeCurlModel.RADIUS * percentage * 5f
                : PlayLikeCurlModel.RADIUS;
    }

    private static float depth(PageRole role) {
        if (role == PageRole.LEFT) return PlayLikeCurlModel.LEFT_DEPTH;
        if (role == PageRole.FRONT) return PlayLikeCurlModel.FRONT_DEPTH;
        return PlayLikeCurlModel.RIGHT_DEPTH;
    }

    private static float[] createTextureCoordinates() {
        int grid = PlayLikeCurlModel.GRID;
        float[] coordinates = new float[(grid + 1) * (grid + 1) * 2];
        for (int row = 0; row <= grid; row++) {
            for (int column = 0; column <= grid; column++) {
                int offset = 2 * (row * (grid + 1) + column);
                coordinates[offset] = column / (float) grid;
                coordinates[offset + 1] = 1f - row / (float) grid;
            }
        }
        return coordinates;
    }

    private static short[] createIndices() {
        int grid = PlayLikeCurlModel.GRID;
        short[] indices = new short[grid * grid * 6];
        for (int row = 0; row < grid; row++) {
            for (int column = 0; column < grid; column++) {
                int offset = 6 * (row * grid + column);
                indices[offset] = (short) (row * (grid + 1) + column);
                indices[offset + 1] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 2] = (short) ((row + 1) * (grid + 1) + column);
                indices[offset + 3] = (short) (row * (grid + 1) + column + 1);
                indices[offset + 4] = (short) ((row + 1) * (grid + 1) + column + 1);
                indices[offset + 5] = (short) ((row + 1) * (grid + 1) + column);
            }
        }
        return indices;
    }
}
