package com.bunko.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Bunko dark color scheme.
//
// Design principles:
// 1. Use Kavita's upstream primary hue (#4ac694, about 156 degrees) as the
//    shared brand direction, while letting Material 3 own the dark tone ramp.
// 2. Keep background and surface at the same value, then express elevation
//    through surfaceContainer roles.
// 3. Keep reading-progress colors outside ColorScheme because Material has no
//    native semantic role for reading state.

// Neutral surfaces: hue 156 degrees, low chroma.
private val SurfaceContainerLowest = Color(0xFF0F1211)
private val SurfaceDim = Color(0xFF161A18)
private val BackgroundSurface = Color(0xFF161A18)
private val SurfaceContainerLow = Color(0xFF1C201F)
private val SurfaceContainerColor = Color(0xFF242A27)
private val SurfaceContainerHigh = Color(0xFF2E3533)
private val SurfaceBright = Color(0xFF363D3A)
private val SurfaceContainerHighest = Color(0xFF373F3C)
private val SurfaceVariantColor = Color(0xFF444C49)

// Text and outline colors.
private val OnSurfaceColor = Color(0xFFE6E8E7)
private val OnSurfaceVariantColor = Color(0xFFC4CAC7)
private val OutlineColor = Color(0xFF99A39F)
private val OutlineVariantColor = Color(0xFF49504D)

// General teal accent. Series Details overrides its Continue button with the
// per-cover colorpick color, so this remains for general app accents.
private val Primary = Color(0xFF97D8BE)
private val OnPrimary = Color(0xFF154733)
private val PrimaryContainer = Color(0xFF267354)
private val OnPrimaryContainer = Color(0xFFD3EEE3)
private val InversePrimary = Color(0xFF36A176)

// Muted teal.
private val Secondary = Color(0xFFB4CCC2)
private val OnSecondary = Color(0xFF1F352E)
private val SecondaryContainer = Color(0xFF354B43)
private val OnSecondaryContainer = Color(0xFFD0E8DD)

// Cyan-leaning accent for tonal variation.
private val Tertiary = Color(0xFFA0CDD7)
private val OnTertiary = Color(0xFF00353D)
private val TertiaryContainer = Color(0xFF1E4D56)
private val OnTertiaryContainer = Color(0xFFBCEAF4)

// Material baseline dark error colors.
private val ErrorColor = Color(0xFFFFB4AB)
private val OnErrorColor = Color(0xFF690005)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)

// Inverse and scrim colors.
private val InverseSurface = Color(0xFFE3E8E5)
private val InverseOnSurface = Color(0xFF2C312E)
private val ScrimColor = Color(0xFF000000)

// App-level surfaces that intentionally preserve Bunko's current browsing
// UI colors while the Material color scheme remains the neutral baseline.
val BunkoBackground = Color(0xFF202222)
val BunkoSurface = Color(0xFF2C3030)
val BunkoChrome = Color(0xFF171818)

val BunkoDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundSurface,
    onBackground = OnSurfaceColor,
    surface = BackgroundSurface,
    onSurface = OnSurfaceColor,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    surfaceTint = Primary,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,
    scrim = ScrimColor,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainerColor,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

// Reading-progress semantic colors. These are fixed across screens and are not
// affected by per-cover colorpick.
val ReadingProgressInProgress = Color(0xFF1D9F6B)
val ReadingProgressRead = Color(0xFF166747)
val ReadingProgressTrack = Color(0x1FFFFFFF)
