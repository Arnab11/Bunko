package com.bunko.reader.reader.internal

import androidx.compose.ui.geometry.Offset

/** Internal to reader, not for external use. */
internal fun ReaderZoomPanState.lerpTo(
    target: ReaderZoomPanState,
    fraction: Float
): ReaderZoomPanState {
    val t = fraction.coerceIn(0f, 1f)
    return ReaderZoomPanState(
        userScale = userScale + (target.userScale - userScale) * t,
        offsetX = offsetX + (target.offsetX - offsetX) * t,
        offsetY = offsetY + (target.offsetY - offsetY) * t
    )
}

/** Internal to reader, not for external use. */
internal fun readerPanBoundsPx(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    zoomScale: Float
): ReaderPanBounds {
    val overflowScale = (zoomScale - 1f).coerceAtLeast(0f)
    return ReaderPanBounds(
        maxX = viewportWidthPx * overflowScale / 2f,
        maxY = viewportHeightPx * overflowScale / 2f
    )
}

/** Internal to reader, not for external use. */
internal fun ReaderZoomPanState.withTransform(
    zoomChange: Float,
    panChange: Offset,
    focalPoint: Offset,
    baseZoomScale: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float
): ReaderZoomPanState {
    val maxUserScale = (ReaderMaxZoomScale / baseZoomScale).coerceAtLeast(1f)
    val oldTotalScale = baseZoomScale * userScale
    val nextUserScale = (userScale * zoomChange).coerceIn(1f, maxUserScale)
    val nextTotalScale = baseZoomScale * nextUserScale
    val bounds = readerPanBoundsPx(viewportWidthPx, viewportHeightPx, nextTotalScale)
    val keepOffset = nextTotalScale > 1f + ReaderZoomEpsilon
    val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
    val focalFromCenter = focalPoint - center
    val previousFocalFromCenter = focalFromCenter - panChange
    val scaleChange = if (oldTotalScale > 0f) nextTotalScale / oldTotalScale else 1f
    val nextOffsetX = focalFromCenter.x - (previousFocalFromCenter.x - offsetX) * scaleChange
    val nextOffsetY = focalFromCenter.y - (previousFocalFromCenter.y - offsetY) * scaleChange
    return ReaderZoomPanState(
        userScale = nextUserScale,
        offsetX = if (keepOffset) nextOffsetX.coerceIn(-bounds.maxX, bounds.maxX) else 0f,
        offsetY = if (keepOffset) nextOffsetY.coerceIn(-bounds.maxY, bounds.maxY) else 0f
    )
}

/** Internal to reader, not for external use. */
internal fun ReaderZoomPanState.withDoubleTapZoom(
    tapPosition: Offset,
    baseZoomScale: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    initialState: ReaderZoomPanState
): ReaderZoomPanState {
    if (userScale > 1f + ReaderZoomEpsilon) return initialState

    val maxUserScale = (ReaderMaxZoomScale / baseZoomScale).coerceAtLeast(1f)
    val nextUserScale = ReaderDoubleTapUserZoomScale.coerceIn(1f, maxUserScale)
    if (nextUserScale <= 1f + ReaderZoomEpsilon) return initialState

    val nextTotalScale = baseZoomScale * nextUserScale
    val bounds = readerPanBoundsPx(viewportWidthPx, viewportHeightPx, nextTotalScale)
    val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
    val tapFromCenter = tapPosition - center
    return ReaderZoomPanState(
        userScale = nextUserScale,
        offsetX = (initialState.offsetX * nextUserScale + tapFromCenter.x * (1f - nextUserScale))
            .coerceIn(-bounds.maxX, bounds.maxX),
        offsetY = (initialState.offsetY * nextUserScale + tapFromCenter.y * (1f - nextUserScale))
            .coerceIn(-bounds.maxY, bounds.maxY)
    )
}
