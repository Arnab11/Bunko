package com.bunko.reader.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * App-wide dark/light toggle, provided once at the root so every header shares
 * the same tap-title behavior without prop-drilling through every screen.
 */
val LocalToggleTheme = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Modifier shared by all header titles: taps capture the title bounds, start the
 * circular theme-reveal from the title center, then invoke the toggle.
 * An explicit toggle wins; otherwise the ambient [LocalToggleTheme] is used.
 * Returns a plain [Modifier] when no toggle is available (title stays static).
 */
@Composable
fun themeToggleModifier(explicitToggle: (() -> Unit)? = null): Modifier {
    val transition = LocalThemeTransitionState.current
    val ambientToggle = LocalToggleTheme.current
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    val toggle = explicitToggle ?: ambientToggle ?: return Modifier
    return Modifier
        .onGloballyPositioned { coordinates ->
            titleBounds = coordinates.boundsInRoot()
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            val ongoing = transition
            if (ongoing?.isAnimating != true) {
                val clickPos = if (titleBounds != Rect.Zero) titleBounds.center else Offset.Zero
                ongoing?.startTransition(clickPos)
                toggle()
            }
        }
}
