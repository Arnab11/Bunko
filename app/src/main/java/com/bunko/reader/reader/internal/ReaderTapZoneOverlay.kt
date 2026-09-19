package com.bunko.reader.reader.internal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bunko.reader.ReaderNavigationMode
import com.bunko.reader.ReaderTappingInvertMode

// Region model ported from Mihon's ViewerNavigation region layouts; the look is
// Bunko's own M3 theme (container roles, rounded zones, animated fade) so the
// preview reads as one family with the overview cards and dialog controls.
internal enum class TapZoneKind(val label: String) {
    Menu("Menu"),
    Prev("Prev"),
    Next("Next")
}

/** Tonal fill per zone, drawn from the app color scheme (light/dark aware). */
internal val TapZoneKind.container: Color
    @Composable
    get() = when (this) {
        TapZoneKind.Menu -> MaterialTheme.colorScheme.secondaryContainer
        TapZoneKind.Prev -> MaterialTheme.colorScheme.tertiaryContainer
        TapZoneKind.Next -> MaterialTheme.colorScheme.primaryContainer
    }

/** Label/border color matching the fill. */
internal val TapZoneKind.onContainer: Color
    @Composable
    get() = when (this) {
        TapZoneKind.Menu -> MaterialTheme.colorScheme.onSecondaryContainer
        TapZoneKind.Prev -> MaterialTheme.colorScheme.onTertiaryContainer
        TapZoneKind.Next -> MaterialTheme.colorScheme.onPrimaryContainer
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
 * Tap-zone preview: tonal zone tiles with M3 labels, faded with the same
 * easing as the overview/menu transitions. Dismissed by any tap ([onDismiss])
 * — callers auto-hide it after a beat.
 */
@Composable
internal fun ReaderTapZoneOverlay(
    visible: Boolean,
    navigationMode: ReaderNavigationMode,
    tappingInvertMode: ReaderTappingInvertMode,
    rightToLeft: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val regions = remember(navigationMode, tappingInvertMode, rightToLeft) {
        tapZoneRegions(navigationMode, tappingInvertMode, rightToLeft)
    }
    AnimatedVisibility(
        visible = visible && regions.isNotEmpty(),
        enter = fadeIn(tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            val fullWidth = maxWidth
            val fullHeight = maxHeight
            regions.forEach { region ->
                val fill = region.kind.container
                val onFill = region.kind.onContainer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(
                            x = fullWidth * region.left,
                            y = fullHeight * region.top
                        )
                        .size(
                            width = fullWidth * (region.right - region.left),
                            height = fullHeight * (region.bottom - region.top)
                        )
                        .background(fill.copy(alpha = 0.82f))
                        .border(1.dp, onFill.copy(alpha = 0.35f))
                        .padding(2.dp)
                ) {
                    Text(
                        text = region.kind.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onFill,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
