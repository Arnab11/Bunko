package com.bunko.reader.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * App themes inspired by modern Material 3 and anime reader designs.
 * Each theme has light and dark color schemes with unique backgrounds,
 * plus AMOLED (pure black) mode.
 */
enum class AppTheme(
    val displayName: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val tertiaryLight: Color,
    val tertiaryDark: Color,
    val backgroundLight: Color,
    val backgroundDark: Color,
    val isDynamic: Boolean = false,
) {
    Default(
        displayName = "Bunko Teal",
        primaryLight = Color(0xFF154733),
        primaryDark = Color(0xFF97D8BE),
        secondaryLight = Color(0xFF1F352E),
        secondaryDark = Color(0xFFB4CCC2),
        tertiaryLight = Color(0xFF00353D),
        tertiaryDark = Color(0xFFA0CDD7),
        backgroundLight = Color(0xFFF2F5F4),
        backgroundDark = Color(0xFF161A18),
    ),
    Dynamic(
        displayName = "Material You",
        primaryLight = Color(0xFF6750A4),
        primaryDark = Color(0xFFD0BCFF),
        secondaryLight = Color(0xFF625B71),
        secondaryDark = Color(0xFFCCC2DC),
        tertiaryLight = Color(0xFF7D5260),
        tertiaryDark = Color(0xFFEFB8C8),
        backgroundLight = Color(0xFFFFFBFF),
        backgroundDark = Color(0xFF1C1B1F),
        isDynamic = true,
    ),
    Amethyst(
        displayName = "Amethyst",
        primaryLight = Color(0xFF794F81),
        primaryDark = Color(0xFFE8B5EF),
        secondaryLight = Color(0xFF6A596C),
        secondaryDark = Color(0xFFD6C0D6),
        tertiaryLight = Color(0xFF82524D),
        tertiaryDark = Color(0xFFF5B7B0),
        backgroundLight = Color(0xFFF7F5F8),
        backgroundDark = Color(0xFF161217),
    ),
    Aurora(
        displayName = "Aurora",
        primaryLight = Color(0xFF0B3FA0),
        primaryDark = Color(0xFF5B93FF),
        secondaryLight = Color(0xFF5C6B8C),
        secondaryDark = Color(0xFF9FAEC9),
        tertiaryLight = Color(0xFF3648A6),
        tertiaryDark = Color(0xFF97A8FF),
        backgroundLight = Color(0xFFF3F6FF),
        backgroundDark = Color(0xFF04070F),
    ),
    Catppuccin(
        displayName = "Catppuccin",
        primaryLight = Color(0xFF4C6B9A),
        primaryDark = Color(0xFF9BA8CF),
        secondaryLight = Color(0xFFB76B8F),
        secondaryDark = Color(0xFFD4A5B8),
        tertiaryLight = Color(0xFFB8763E),
        tertiaryDark = Color(0xFF8AB8A8),
        backgroundLight = Color(0xFFEFF1F5),
        backgroundDark = Color(0xFF1E1E2E),
    ),
    Cloudflare(
        displayName = "Cloudflare",
        primaryLight = Color(0xFFF6821F),
        primaryDark = Color(0xFFFFB77C),
        secondaryLight = Color(0xFF6B5E4C),
        secondaryDark = Color(0xFFD6C5AC),
        tertiaryLight = Color(0xFF855316),
        tertiaryDark = Color(0xFFFABD71),
        backgroundLight = Color(0xFFFFFBF7),
        backgroundDark = Color(0xFF1A1612),
    ),
    CottonCandy(
        displayName = "Cotton Candy",
        primaryLight = Color(0xFFE993C1),
        primaryDark = Color(0xFFFFB1D5),
        secondaryLight = Color(0xFF70A2C2),
        secondaryDark = Color(0xFF9ED0EF),
        tertiaryLight = Color(0xFF9C68AC),
        tertiaryDark = Color(0xFFDEB0E9),
        backgroundLight = Color(0xFFFFF8FA),
        backgroundDark = Color(0xFF1A1418),
    ),
    Doom(
        displayName = "Doom",
        primaryLight = Color(0xFFBB2929),
        primaryDark = Color(0xFFFF6B6B),
        secondaryLight = Color(0xFF6B5353),
        secondaryDark = Color(0xFFD6BABA),
        tertiaryLight = Color(0xFF8C4A4A),
        tertiaryDark = Color(0xFFFFB4AB),
        backgroundLight = Color(0xFFFFF8F7),
        backgroundDark = Color(0xFF1A1010),
    ),
    Lavender(
        displayName = "Lavender",
        primaryLight = Color(0xFF7C5AB8),
        primaryDark = Color(0xFFCFBCFF),
        secondaryLight = Color(0xFF635B70),
        secondaryDark = Color(0xFFCBC3DA),
        tertiaryLight = Color(0xFF7E525A),
        tertiaryDark = Color(0xFFF2B8C1),
        backgroundLight = Color(0xFFFCF8FF),
        backgroundDark = Color(0xFF16121A),
    ),
    Midnight(
        displayName = "Midnight",
        primaryLight = Color(0xFF0D47A1),
        primaryDark = Color(0xFF90CAF9),
        secondaryLight = Color(0xFF455A64),
        secondaryDark = Color(0xFFB0BEC5),
        tertiaryLight = Color(0xFF1565C0),
        tertiaryDark = Color(0xFF64B5F6),
        backgroundLight = Color(0xFFF5F9FF),
        backgroundDark = Color(0xFF0D1117),
    ),
    Nord(
        displayName = "Nord",
        primaryLight = Color(0xFF5E81AC),
        primaryDark = Color(0xFF88C0D0),
        secondaryLight = Color(0xFF4C566A),
        secondaryDark = Color(0xFFD8DEE9),
        tertiaryLight = Color(0xFFB48EAD),
        tertiaryDark = Color(0xFFD8A9C4),
        backgroundLight = Color(0xFFECEFF4),
        backgroundDark = Color(0xFF2E3440),
    ),
    RosePine(
        displayName = "Rosé Pine",
        primaryLight = Color(0xFF907AA9),
        primaryDark = Color(0xFFC4A7E7),
        secondaryLight = Color(0xFF56949F),
        secondaryDark = Color(0xFF9CCFD8),
        tertiaryLight = Color(0xFFD7827E),
        tertiaryDark = Color(0xFFEB6F92),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark = Color(0xFF191724),
    );

    fun getLightColorScheme(): ColorScheme {
        val primaryContainer = primaryLight.lighten(0.35f)
        val secondaryContainer = secondaryLight.lighten(0.35f)
        val tertiaryContainer = tertiaryLight.lighten(0.35f)
        val surfaceVariant = primaryLight.copy(alpha = 0.12f).compositeOver(Color(0xFFE7E0EC))
        val surfaceContainerLowest = Color.White
        val surfaceContainerLow = primaryLight.copy(alpha = 0.04f).compositeOver(backgroundLight)
        val surfaceContainer = primaryLight.copy(alpha = 0.06f).compositeOver(backgroundLight)
        val surfaceContainerHigh = primaryLight.copy(alpha = 0.09f).compositeOver(backgroundLight)
        val surfaceContainerHighest = primaryLight.copy(alpha = 0.12f).compositeOver(backgroundLight)
        val surfaceDim = backgroundLight.darken(0.08f)
        val surfaceBright = backgroundLight.lighten(0.04f)

        return lightColorScheme(
            primary = primaryLight,
            onPrimary = primaryLight.accessibleContentColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryContainer.accessibleContentColor(),
            secondary = secondaryLight,
            onSecondary = secondaryLight.accessibleContentColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = secondaryContainer.accessibleContentColor(),
            tertiary = tertiaryLight,
            onTertiary = tertiaryLight.accessibleContentColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = tertiaryContainer.accessibleContentColor(),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A),
            background = backgroundLight,
            onBackground = Color(0xFF1C1B1F),
            surface = backgroundLight,
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFF49454F),
            outline = secondaryLight.copy(alpha = 0.6f).compositeOver(Color(0xFF79747E)),
            outlineVariant = primaryLight.copy(alpha = 0.20f).compositeOver(Color(0xFFCAC4D0)),
            inverseSurface = backgroundDark,
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = primaryDark,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    fun getDarkColorScheme(): ColorScheme {
        val primaryContainer = primaryLight.darken(0.2f)
        val secondaryContainer = secondaryLight.darken(0.2f)
        val tertiaryContainer = tertiaryLight.darken(0.2f)
        val surfaceVariant = primaryDark.copy(alpha = 0.18f).compositeOver(Color(0xFF2A2A2A))
        val surfaceContainerLowest = backgroundDark.darken(0.2f)
        val surfaceContainerLow = primaryDark.copy(alpha = 0.08f).compositeOver(backgroundDark)
        val surfaceContainer = primaryDark.copy(alpha = 0.08f).compositeOver(backgroundDark)
        val surfaceContainerHigh = primaryDark.copy(alpha = 0.12f).compositeOver(backgroundDark)
        val surfaceContainerHighest = primaryDark.copy(alpha = 0.16f).compositeOver(backgroundDark)

        return darkColorScheme(
            primary = primaryDark,
            onPrimary = primaryDark.accessibleContentColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryContainer.accessibleContentColor(),
            secondary = secondaryDark,
            onSecondary = secondaryDark.accessibleContentColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = secondaryContainer.accessibleContentColor(),
            tertiary = tertiaryDark,
            onTertiary = tertiaryDark.accessibleContentColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = tertiaryContainer.accessibleContentColor(),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            background = backgroundDark,
            onBackground = Color(0xFFE6E1E5),
            surface = backgroundDark,
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = secondaryDark.copy(alpha = 0.5f).compositeOver(Color(0xFF938F99)),
            outlineVariant = primaryDark.copy(alpha = 0.22f).compositeOver(Color(0xFF49454F)),
            inverseSurface = backgroundLight,
            inverseOnSurface = Color(0xFF313033),
            inversePrimary = primaryLight,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }

    fun getAmoledColorScheme(): ColorScheme =
        getDarkColorScheme().copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = primaryDark.copy(alpha = 0.06f).compositeOver(Color(0xFF1A1A1A)),
            surfaceVariant = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF242424)),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = primaryDark.copy(alpha = 0.04f).compositeOver(Color(0xFF0A0A0A)),
            surfaceContainer = primaryDark.copy(alpha = 0.06f).compositeOver(Color(0xFF121212)),
            surfaceContainerHigh = primaryDark.copy(alpha = 0.08f).compositeOver(Color(0xFF1A1A1A)),
            surfaceContainerHighest = primaryDark.copy(alpha = 0.12f).compositeOver(Color(0xFF222222)),
            outline = secondaryDark.copy(alpha = 0.4f).compositeOver(Color(0xFF484848)),
            outlineVariant = Color(0xFF262626),
        )
}

fun resolveBunkoColorScheme(
    context: Context,
    theme: AppTheme,
    isDarkMode: Boolean,
    isAmoled: Boolean
): ColorScheme {
    if (theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicScheme = if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (isDarkMode && isAmoled) {
            dynamicScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF222222),
            )
        } else {
            dynamicScheme
        }
    }

    return if (isDarkMode) {
        if (isAmoled) theme.getAmoledColorScheme() else theme.getDarkColorScheme()
    } else {
        theme.getLightColorScheme()
    }
}

fun Color.accessibleContentColor(): Color {
    val blackContrast = contrastRatio(Color.Black)
    val whiteContrast = contrastRatio(Color.White)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

fun Color.contrastRatio(other: Color): Float {
    val relativeLuminance = luminance()
    val otherRelativeLuminance = other.luminance()
    val lighter = maxOf(relativeLuminance, otherRelativeLuminance)
    val darker = minOf(relativeLuminance, otherRelativeLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.darken(factor: Float): Color =
    Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha,
    )

private fun Color.lighten(factor: Float): Color =
    Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )

private fun Color.compositeOver(background: Color): Color {
    val bgAlpha = background.alpha
    val fgAlpha = alpha
    val a = fgAlpha + bgAlpha * (1f - fgAlpha)
    return if (a == 0f) {
        Color.Transparent
    } else {
        Color(
            red = (red * fgAlpha + background.red * bgAlpha * (1f - fgAlpha)) / a,
            green = (green * fgAlpha + background.green * bgAlpha * (1f - fgAlpha)) / a,
            blue = (blue * fgAlpha + background.blue * bgAlpha * (1f - fgAlpha)) / a,
            alpha = a,
        )
    }
}
