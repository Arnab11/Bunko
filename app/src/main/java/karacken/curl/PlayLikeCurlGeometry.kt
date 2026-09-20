package karacken.curl

import kotlin.math.sin

internal object PlayLikeCurlGeometry {
    const val CAMERA_DISTANCE: Float = 2f

    fun createPage(
        role: PageRole,
        bitmapWidth: Int,
        bitmapHeight: Int,
        orientation: PageOrientation
    ): PageGeometry {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Bitmap dimensions must be positive" }
        val bitmapRatio = bitmapRatio(bitmapWidth, bitmapHeight, orientation)
        val vertexCount = (PlayLikeCurlModel.GRID + 1) * (PlayLikeCurlModel.GRID + 1)
        val page = PageGeometry(
            role,
            bitmapRatio,
            FloatArray(vertexCount * 3),
            createTextureCoordinates(),
            createIndices()
        )
        update(
            page,
            if (role == PageRole.LEFT) {
                PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION
            } else {
                PlayLikeCurlModel.GRID.toFloat()
            },
            false
        )
        return page
    }

    fun update(page: PageGeometry, curlPosition: Float, active: Boolean) {
        val grid = PlayLikeCurlModel.GRID
        val heightCorrection = (page.bitmapRatio - 1f) / 2f
        val positions = page.positions
        for (row in 0..grid) {
            for (column in 0..grid) {
                val offset = 3 * (row * (grid + 1) + column)
                val normalizedX = column / grid.toFloat()
                when (page.role) {
                    PageRole.FRONT -> positions[offset] = frontX(column, curlPosition)
                    PageRole.LEFT -> positions[offset] = leftX(column, curlPosition)
                    else -> positions[offset] = normalizedX
                }
                positions[offset + 1] = row / grid.toFloat() * page.bitmapRatio - heightCorrection
                positions[offset + 2] = if (active) {
                    activeDepth(page.role, column, curlPosition)
                } else {
                    depth(page.role)
                }
            }
        }
    }

    fun projectionAspect(width: Int, height: Int): Float {
        return width / height.toFloat()
    }

    fun bitmapRatio(
        width: Int,
        height: Int,
        orientation: PageOrientation
    ): Float {
        return if (orientation == PageOrientation.PORTRAIT) {
            height / width.toFloat()
        } else {
            width / height.toFloat()
        }
    }

    fun foldEdgeX(role: PageRole, curlPosition: Float): Float {
        if (role == PageRole.FRONT) {
            return frontX(PlayLikeCurlModel.GRID, curlPosition)
        }
        if (role == PageRole.LEFT) {
            return leftX(PlayLikeCurlModel.GRID, curlPosition)
        }
        return 1f
    }

    fun foldEdgeDepth(role: PageRole, curlPosition: Float): Float {
        return activeDepth(role, PlayLikeCurlModel.GRID, curlPosition)
    }

    fun projectXOntoDepthPlane(
        sourceX: Float,
        sourceDepth: Float,
        targetDepth: Float
    ): Float {
        return 0.5f + (sourceX - 0.5f) * (CAMERA_DISTANCE - targetDepth) / (CAMERA_DISTANCE - sourceDepth)
    }

    private fun frontX(column: Int, curlPosition: Float): Float {
        val percentage = 1f - curlPosition / PlayLikeCurlModel.GRID
        val radius = resolvedRadius(percentage)
        val movement = if (percentage > 0.05f) percentage - 0.05f else 0f
        return column / PlayLikeCurlModel.GRID.toFloat() * (1f - radius) - movement
    }

    private fun leftX(column: Int, curlPosition: Float): Float {
        val percentage = (1f - curlPosition / PlayLikeCurlModel.GRID) * 0.75f
        val radius = resolvedRadius(percentage)
        return column / PlayLikeCurlModel.GRID.toFloat() * (1f - radius) - percentage
    }

    private fun activeDepth(role: PageRole, column: Int, curlPosition: Float): Float {
        if (role == PageRole.RIGHT) return PlayLikeCurlModel.RIGHT_DEPTH
        val rawPercentage = 1f - curlPosition / PlayLikeCurlModel.GRID
        val percentage = if (role == PageRole.LEFT) rawPercentage * 0.75f else rawPercentage
        val radius = resolvedRadius(percentage)
        val waveWidth = if (role == PageRole.LEFT) 0.50f else 0.60f
        val delta = PlayLikeCurlModel.GRID - curlPosition
        return (radius * sin(3.14f / (PlayLikeCurlModel.GRID * waveWidth) * (column - delta)) + radius * 1.1f).toFloat()
    }

    private fun resolvedRadius(percentage: Float): Float {
        return if (percentage < 0.20f) {
            PlayLikeCurlModel.RADIUS * percentage * 5f
        } else {
            PlayLikeCurlModel.RADIUS
        }
    }

    private fun depth(role: PageRole): Float {
        return when (role) {
            PageRole.LEFT -> PlayLikeCurlModel.LEFT_DEPTH
            PageRole.FRONT -> PlayLikeCurlModel.FRONT_DEPTH
            PageRole.RIGHT -> PlayLikeCurlModel.RIGHT_DEPTH
        }
    }

    private fun createTextureCoordinates(): FloatArray {
        val grid = PlayLikeCurlModel.GRID
        val coordinates = FloatArray((grid + 1) * (grid + 1) * 2)
        for (row in 0..grid) {
            for (column in 0..grid) {
                val offset = 2 * (row * (grid + 1) + column)
                coordinates[offset] = column / grid.toFloat()
                coordinates[offset + 1] = 1f - row / grid.toFloat()
            }
        }
        return coordinates
    }

    private fun createIndices(): ShortArray {
        val grid = PlayLikeCurlModel.GRID
        val indices = ShortArray(grid * grid * 6)
        for (row in 0 until grid) {
            for (column in 0 until grid) {
                val offset = 6 * (row * grid + column)
                indices[offset] = (row * (grid + 1) + column).toShort()
                indices[offset + 1] = (row * (grid + 1) + column + 1).toShort()
                indices[offset + 2] = ((row + 1) * (grid + 1) + column).toShort()
                indices[offset + 3] = (row * (grid + 1) + column + 1).toShort()
                indices[offset + 4] = ((row + 1) * (grid + 1) + column + 1).toShort()
                indices[offset + 5] = ((row + 1) * (grid + 1) + column).toShort()
            }
        }
        return indices
    }
}
