package com.bunko.reader.reader.internal

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset

internal const val ReaderDoubleTapUserZoomScale = 2f
internal const val ReaderMaxZoomScale = 5f
internal const val ReaderZoomEpsilon = 0.01f
internal const val ReaderPrefetchConcurrency = 2

internal enum class ReaderDragMode {
    Pending,
    HorizontalTurn,
    VerticalClose,
    ZoomEdgeTurn
}

internal enum class ReaderTapZone {
    Left,
    Center,
    Right
}

internal data class ReaderPendingCenterTap(
    val position: Offset,
    val uptimeMillis: Long
)

internal data class ReaderSpreadPages(
    val leftPage: Int,
    val rightPage: Int
)

internal data class ReaderPageLayout(
    val singlePage: Boolean,
    val nextStep: Int,
    val previousStep: Int,
    val singleAlignment: Alignment
)

internal data class ReaderZoomPanState(
    val userScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

internal data class ReaderPanBounds(
    val maxX: Float,
    val maxY: Float
)

internal data class ReaderInvertCacheKey(
    val model: Any,
    val whiteThreshold: Float
)

internal data class ReaderPrefetchTarget(
    val model: String,
    val targetWidth: Int,
    val targetHeight: Int
)

internal data class ReaderPrefetchSize(
    val width: Int,
    val height: Int
)
