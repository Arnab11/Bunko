package karacken.curl

internal class PageGeometry(
    val role: PageRole,
    val bitmapRatio: Float,
    val positions: FloatArray,
    val textureCoordinates: FloatArray,
    val indices: ShortArray
) {
    fun positionY(column: Int, row: Int): Float {
        return positions[3 * (row * (PlayLikeCurlModel.GRID + 1) + column) + 1]
    }
}
