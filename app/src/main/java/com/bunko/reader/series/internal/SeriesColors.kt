package com.bunko.reader.series.internal

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.bunko.reader.ChapterDto
import com.bunko.reader.SeriesDto

/** Internal to series, not for external use. */
internal fun SeriesDto.coverActionColor(): Color {
    return coverActionColor(primaryColor, secondaryColor, Color(0xFF6A5BB7))
}

/** Internal to series, not for external use. */
internal fun ChapterDto.coverActionColor(fallback: Color): Color {
    return coverActionColor(primaryColor, secondaryColor, fallback)
}

private fun coverActionColor(primaryColor: String?, secondaryColor: String?, fallback: Color): Color {
    val primary = primaryColor.parseKavitaRgb()
    val secondary = secondaryColor.parseKavitaRgb()
    val seed = when {
        primary == null -> secondary
        secondary == null -> primary
        primary.hsvSaturation() < 0.12f && secondary.hsvSaturation() > primary.hsvSaturation() + 0.12f -> secondary
        else -> primary
    } ?: return fallback

    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (seed shr 16) and 0xFF,
        (seed shr 8) and 0xFF,
        seed and 0xFF,
        hsv
    )
    // Kavita's picked cover colors can be too pale or too dark for buttons; clamp them into a readable accent range.
    hsv[1] = hsv[1].coerceIn(0.38f, 0.72f)
    hsv[2] = hsv[2].coerceIn(0.42f, 0.68f)
    return Color(AndroidColor.HSVToColor(hsv))
}

/** Internal to series, not for external use. */
internal fun Color.readableAccentOn(background: Color): Color {
    for (step in 0..10) {
        val candidate = lerp(this, Color.White, step / 10f)
        val lighter = maxOf(candidate.luminance(), background.luminance())
        val darker = minOf(candidate.luminance(), background.luminance())
        if ((lighter + 0.05f) / (darker + 0.05f) >= 4.5f) return candidate
    }
    return Color.White
}

private fun String?.parseKavitaRgb(): Int? {
    val raw = this
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 || it.length == 8 }
        ?: return null
    val rgb = if (raw.length == 8) raw.takeLast(6) else raw
    return rgb.toLongOrNull(16)?.toInt()
}

private fun Int.hsvSaturation(): Float {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (this shr 16) and 0xFF,
        (this shr 8) and 0xFF,
        this and 0xFF,
        hsv
    )
    return hsv[1]
}

