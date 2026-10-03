package com.bunko.reader.series.internal

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.bunko.reader.ChapterDto
import com.bunko.reader.VolumeDto
/** Internal to series, not for external use. */
internal fun ChapterDto.displayTitle(): String {
    title?.takeIf { it.isDisplayableChapterLabel() }?.let { return it }
    number.displayText()?.takeIf { it.isDisplayableChapterLabel() }?.let { return it }
    return id.toString()
}

/** Internal to series, not for external use. */
internal fun ChapterDto.cleanChapterDisplayTitle(seriesName: String? = null): String {
    val rawTitle = displayTitle().trim()
    val cleanSeries = seriesName?.trim().orEmpty()
    val num = number.displayText()?.trim()

    // If rawTitle is the group/series name or empty, format with chapter number if present
    if (cleanSeries.isNotBlank() && rawTitle.equals(cleanSeries, ignoreCase = true)) {
        return if (!num.isNullOrBlank() && num != "0" && num != "-100000") "Chapter $num" else ""
    }

    // If rawTitle starts with series name, strip it: "Demon Slayer - Chapter 19" -> "Chapter 19"
    if (cleanSeries.isNotBlank() && rawTitle.startsWith(cleanSeries, ignoreCase = true) && rawTitle.length > cleanSeries.length) {
        val stripped = rawTitle.substring(cleanSeries.length).trimStart(' ', '-', ':', '#', '_', '/').trim()
        if (stripped.isNotBlank()) {
            return stripped
        }
    }

    return rawTitle
}

/** Internal to series, not for external use. */
internal fun VolumeDto.displayName(): String? {
    name?.takeIf { it.isDisplayableVolumeLabel() }?.let {
        return if (it.toFloatOrNull() != null) "Volume $it" else it
    }
    val numberText = number.displayText()
    return numberText
        ?.takeIf { it.isDisplayableVolumeLabel() }
        ?.let { "Volume $it" }
}

/** Internal to series, not for external use. */
internal fun VolumeDto.displayShortName(): String? {
    return displayName()?.replaceFirst("Volume ", "Vol ")
}

/** Internal to series, not for external use. */
internal fun ChapterDto.releaseDateText(): String? {
    return releaseDate
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.startsWith("0001-01-01") }
        ?.substringBefore("T")
        ?.takeIf { it.isNotBlank() }
}

private fun String?.isDisplayableChapterLabel(): Boolean {
    return !isNullOrBlank() && this != "-100000"
}

private fun String?.isDisplayableVolumeLabel(): Boolean {
    return !isNullOrBlank() && this != "-100000" && this != "0"
}


private fun kotlinx.serialization.json.JsonElement?.displayText(): String? {
    return when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        null -> null
        else -> toString()
    }?.trim('"')
}
