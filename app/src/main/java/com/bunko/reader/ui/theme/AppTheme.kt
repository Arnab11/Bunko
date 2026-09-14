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
 * App themes inspired by mpvRx and modern Material 3 designs.
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
        displayName = "Default",
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
    TokyoNight(
        displayName = "Tokyo Night",
        primaryLight = Color(0xFF3D5A80),
        primaryDark = Color(0xFF7D9BC1),
        secondaryLight = Color(0xFF6B5B95),
        secondaryDark = Color(0xFFA89DC9),
        tertiaryLight = Color(0xFF4A6B5C),
        tertiaryDark = Color(0xFF8AB4A3),
        backgroundLight = Color(0xFFF0F1F5),
        backgroundDark = Color(0xFF1A1B26),
    ),
    Gruvbox(
        displayName = "Gruvbox",
        primaryLight = Color(0xFF9D5B3F),
        primaryDark = Color(0xFFD89B6A),
        secondaryLight = Color(0xFF7A7556),
        secondaryDark = Color(0xFFB0AE8A),
        tertiaryLight = Color(0xFF4A7B7C),
        tertiaryDark = Color(0xFF8AAFA8),
        backgroundLight = Color(0xFFFBF1C7),
        backgroundDark = Color(0xFF282828),
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
    EReader(
        displayName = "E-Reader",
        primaryLight = Color(0xFF000000),
        primaryDark = Color(0xFFFFFFFF),
        secondaryLight = Color(0xFF2B2B2B),
        secondaryDark = Color(0xFFD4D4D4),
        tertiaryLight = Color(0xFF4B4B4B),
        tertiaryDark = Color(0xFFA8A8A8),
        backgroundLight = Color(0xFFFFFFFF),
        backgroundDark = Color(0xFF000000),
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
        secondaryLight = Color(0xFFB4637A),
        secondaryDark = Color(0xFFEBBCBA),
        tertiaryLight = Color(0xFF7A9A8A),
        tertiaryDark = Color(0xFF9CCFD8),
        backgroundLight = Color(0xFFFAF4ED),
        backgroundDark = Color(0xFF232136),
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
    );

    fun getLightColorScheme(): ColorScheme {
        if (this == EReader) {
            return lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color.Black,
                secondary = Color(0xFF2E2E2E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFEBEBEB),
                onSecondaryContainer = Color.Black,
                tertiary = Color(0xFF4A4A4A),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE5E5E5),
                onTertiaryContainer = Color.Black,
                error = Color(0xFF600000),
                onError = Color.White,
                errorContainer = Color(0xFFE0E0E0),
                onErrorContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFEEEEEE),
                onSurfaceVariant = Color.Black,
                outline = Color(0xFF444444),
                outlineVariant = Color(0xFF888888),
                inverseSurface = Color.Black,
                inverseOnSurface = Color.White,
                inversePrimary = Color.White,
                surfaceDim = Color(0xFFF0F0F0),
                surfaceBright = Color.White,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF8F8F8),
                surfaceContainer = Color(0xFFF0F0F0),
                surfaceContainerHigh = Color(0xFFE8E8E8),
                surfaceContainerHighest = Color(0xFFE0E0E0),
            )
        }

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
            inversePrimary = primaryDark.withMinimumContrastAgainst(backgroundDark),
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
        if (this == EReader) {
            return darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                primaryContainer = Color(0xFF2E2E2E),
                onPrimaryContainer = Color.White,
                secondary = Color(0xFFE0E0E0),
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF242424),
                onSecondaryContainer = Color.White,
                tertiary = Color(0xFFCCCCCC),
                onTertiary = Color.Black,
                tertiaryContainer = Color(0xFF1E1E1E),
                onTertiaryContainer = Color.White,
                error = Color.White,
                onError = Color.Black,
                errorContainer = Color(0xFF330000),
                onErrorContainer = Color.White,
                background = Color.Black,
                onBackground = Color.White,
                surface = Color.Black,
                onSurface = Color.White,
                surfaceVariant = Color(0xFF1C1C1C),
                onSurfaceVariant = Color.White,
                outline = Color(0xFFCCCCCC),
                outlineVariant = Color(0xFF777777),
                inverseSurface = Color.White,
                inverseOnSurface = Color.Black,
                inversePrimary = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF141414),
                surfaceContainerHigh = Color(0xFF1E1E1E),
                surfaceContainerHighest = Color(0xFF282828),
            )
        }

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

private const val MinimumTextContrast = 4.5f

fun Color.accessibleContentColor(): Color {
    val blackContrast = contrastRatio(Color.Black)
    val whiteContrast = contrastRatio(Color.White)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

fun Color.withMinimumContrastAgainst(background: Color): Color =
    withMinimumContrastAgainst(listOf(background))

fun Color.withMinimumContrastAgainst(backgrounds: List<Color>): Color {
    if (backgrounds.minOf { background -> contrastRatio(background) } >= MinimumTextContrast) return this

    val blackContrast = backgrounds.minOf { background -> Color.Black.contrastRatio(background) }
    val whiteContrast = backgrounds.minOf { background -> Color.White.contrastRatio(background) }
    val target = if (blackContrast >= whiteContrast) Color.Black else Color.White
    var insufficientFraction = 0f
    var sufficientFraction = 1f
    repeat(12) {
        val fraction = (insufficientFraction + sufficientFraction) / 2f
        val candidate = blendToward(target, fraction)
        if (backgrounds.minOf { background -> candidate.contrastRatio(background) } >= MinimumTextContrast) {
            sufficientFraction = fraction
        } else {
            insufficientFraction = fraction
        }
    }
    return blendToward(target, sufficientFraction)
}

fun Color.contrastRatio(other: Color): Float {
    val relativeLuminance = luminance()
    val otherRelativeLuminance = other.luminance()
    val lighter = maxOf(relativeLuminance, otherRelativeLuminance)
    val darker = minOf(relativeLuminance, otherRelativeLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.blendToward(target: Color, fraction: Float): Color =
    Color(
        red = red + (target.red - red) * fraction,
        green = green + (target.green - green) * fraction,
        blue = blue + (target.blue - blue) * fraction,
        alpha = alpha + (target.alpha - alpha) * fraction,
    )

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

