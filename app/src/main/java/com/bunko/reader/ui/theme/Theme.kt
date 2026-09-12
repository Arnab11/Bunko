package com.bunko.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val colorScheme = resolveBunkoColorScheme(
        context = context,
        theme = theme,
        isDarkMode = isDarkMode,
        isAmoled = isAmoledMode
    )
    CompositionLocalProvider(LocalThemeTransitionState provides transitionState) {
        ThemeTransitionOverlay(state = transitionState) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
    }
}
