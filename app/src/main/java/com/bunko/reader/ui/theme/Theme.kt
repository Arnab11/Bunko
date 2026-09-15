package com.bunko.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun BunkoTheme(
    theme: AppTheme = AppTheme.Default,
    isDarkMode: Boolean = true,
    isAmoledMode: Boolean = false,
    transitionState: ThemeTransitionState = rememberThemeTransitionState(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Memoize: dynamic schemes query the system wallpaper colors; recomputing
    // on every recomposition made theme flips sluggish on heavy grids.
    val colorScheme = remember(context, theme, isDarkMode, isAmoledMode) {
        resolveBunkoColorScheme(
            context = context,
            theme = theme,
            isDarkMode = isDarkMode,
            isAmoled = isAmoledMode
        )
    }
    CompositionLocalProvider(LocalThemeTransitionState provides transitionState) {
        ThemeTransitionOverlay(
            state = transitionState,
            themeKey = Triple(theme, isDarkMode, isAmoledMode),
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    }
}
