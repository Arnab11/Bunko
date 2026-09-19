package com.bunko.reader.reader.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode

// Region model ported from Mihon (ViewerNavigation + ReaderNavigationOverlayView):
// normalized 0..1 rects painted in translucent zone colors with a centered label
// drawn as white text over a black stroke, exactly like Mihon's overlay.
internal enum class TapZoneKind(val color: Color, val label: String) {
    Menu(Color(0xCC95818D), "Menu"),
    Prev(Color(0xCCFF7733), "Prev"),
    Next(Color(0xCC84E296), "Next")
}

internal data class TapZoneRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val kind: TapZoneKind
)

/**
 * Normalized overlay regions for [navigationMode], mirroring both Bunko's
 * [resolveTapAction] hit-testing and Mihon's per-mode region layouts
 * (LNavigation, KindlishNavigation, EdgeNavigation). Empty for Disabled,
 * exactly like Mihon's DisabledNavigation which never shows the overlay.
 *
 * Inversion uses the same math as Mihon's RectF.invert. For Default and
 * RightAndLeft the side actions are direction-aware (MoveLeft/MoveRight), so
 * the Prev/Next sides are mirrored when [rightToLeft] is true.
 */
internal fun tapZoneRegions(
    navigationMode: ReaderNavigationMode,
    tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    rightToLeft: Boolean = false
): List<TapZoneRegion> {
    val leftKind: TapZoneKind
    val rightKind: TapZoneKind
    val base: List<TapZoneRegion> = when (navigationMode) {
        ReaderNavigationMode.Disabled -> return emptyList()
        ReaderNavigationMode.Default,
        ReaderNavigationMode.RightAndLeft -> {
            leftKind = if (rightToLeft) TapZoneKind.Next else TapZoneKind.Prev
            rightKind = if (rightToLeft) TapZoneKind.Prev else TapZoneKind.Next
            listOf(
                TapZoneRegion(0f, 0f, 0.33f, 1f, leftKind),
                TapZoneRegion(0.33f, 0f, 0.66f, 1f, TapZoneKind.Menu),
                TapZoneRegion(0.66f, 0f, 1f, 1f, rightKind)
            )
        }
        ReaderNavigationMode.LShaped -> listOf(
            TapZoneRegion(0f, 0f, 1f, 0.33f, TapZoneKind.Prev),
            TapZoneRegion(0f, 0.33f, 0.33f, 0.66f, TapZoneKind.Prev),
            TapZoneRegion(0.33f, 0.33f, 0.66f, 0.66f, TapZoneKind.Menu),
            TapZoneRegion(0.66f, 0.33f, 1f, 0.66f, TapZoneKind.Next),
            TapZoneRegion(0f, 0.66f, 1f, 1f, TapZoneKind.Next)
        )
        ReaderNavigationMode.Kindlish -> listOf(
            TapZoneRegion(0f, 0f, 1f, 0.33f, TapZoneKind.Menu),
            TapZoneRegion(0f, 0.33f, 0.33f, 1f, TapZoneKind.Prev),
            TapZoneRegion(0.33f, 0.33f, 1f, 1f, TapZoneKind.Next)
        )
        ReaderNavigationMode.Edge -> listOf(
            TapZoneRegion(0f, 0f, 0.33f, 1f, TapZoneKind.Next),
            TapZoneRegion(0.66f, 0f, 1f, 1f, TapZoneKind.Next),
            TapZoneRegion(0.33f, 0.66f, 0.66f, 1f, TapZoneKind.Prev),
            TapZoneRegion(0.33f, 0f, 0.66f, 0.66f, TapZoneKind.Menu)
        )
    }
    val horizontal = tappingInvertMode == ReaderTappingInvertMode.Horizontal ||
        tappingInvertMode == ReaderTappingInvertMode.Both
    val vertical = tappingInvertMode == ReaderTappingInvertMode.Vertical ||
        tappingInvertMode == ReaderTappingInvertMode.Both
    if (!horizontal && !vertical) return base
    return base.map { region ->
        val l = if (horizontal) 1f - region.right else region.left
        val r = if (horizontal) 1f - region.left else region.right
        val t = if (vertical) 1f - region.bottom else region.top
        val b = if (vertical) 1f - region.top else region.bottom
        region.copy(left = l, top = t, right = r, bottom = b)
    }
}

/**
 * Mihon-style tap-zone overlay: translucent zone fills with centered
 * white-on-black-stroke labels. Dismissed by any tap ([onDismiss]) — callers
 * auto-hide it after a beat, like Mihon's 1s fade.
 */
@Composable
internal fun ReaderTapZoneOverlay(
    navigationMode: ReaderNavigationMode,
    tappingInvertMode: ReaderTappingInvertMode,
    rightToLeft: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val regions = remember(navigationMode, tappingInvertMode, rightToLeft) {
        tapZoneRegions(navigationMode, tappingInvertMode, rightToLeft)
    }
    if (regions.isEmpty()) return
    val density = LocalDensity.current
    val textPx = remember(density) { with(density) { 20.sp.toPx() } }
    val fillPaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            textSize = textPx
        }
    }
    val borderPaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.BLACK
            textSize = textPx
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textPx * 0.125f
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val w = size.width
        val h = size.height
        regions.forEach { region ->
            drawRect(
                color = region.kind.color,
                topLeft = Offset(region.left * w, region.top * h),
                size = Size((region.right - region.left) * w, (region.bottom - region.top) * h)
            )
        }
        val native = drawContext.canvas.nativeCanvas
        regions.forEach { region ->
            val cx = (region.left + region.right) / 2f * w
            val cy = (region.top + region.bottom) / 2f * h + textPx * 0.35f
            native.drawText(region.kind.label, cx, cy, borderPaint)
            native.drawText(region.kind.label, cx, cy, fillPaint)
        }
    }
}
