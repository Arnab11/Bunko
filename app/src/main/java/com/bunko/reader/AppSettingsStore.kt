package com.bunko.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("bunko_settings")

enum class InvertMode {
    Off,
    Smart,
    Always
}

enum class EPaperMode {
    Off,
    BlackAndWhite,
    Color
}

enum class PageTurnMode {
    Slide,
    Curl
}

enum class ReaderReadingDirection {
    RightToLeft,
    Vertical,
    LeftToRight
}

enum class PageLayoutMode {
    Auto,
    TwoPages,
    SinglePage
}

/** Colour of the margins around a page (and of the curl flap's back face). */
enum class PageBackground {
    Paper,
    Dark
}

enum class EpubTextAlign {
    Left,
    Center,
    Right,
    Justify
}

enum class ReaderNavigationMode {
    Default,
    LShaped,
    Kindlish,
    Edge,
    RightAndLeft,
    Disabled
}

enum class ReaderTappingInvertMode {
    None,
    Horizontal,
    Vertical,
    Both
}

enum class ReaderImageScaleType {
    FitScreen,
    Stretch,
    FitWidth,
    FitHeight,
    OriginalSize,
    SmartFit
}

internal const val DefaultReaderPrefetchTurns = 4
internal const val MaxReaderPrefetchTurns = 8

data class ReaderSettings(
    val readingDirection: ReaderReadingDirection = ReaderReadingDirection.LeftToRight,
    val pageLayoutMode: PageLayoutMode = PageLayoutMode.Auto,
    val invertMode: InvertMode = InvertMode.Off,
    val invertWhiteThreshold: Float = 0.5f,
    val prefetchTurns: Int = DefaultReaderPrefetchTurns,
    val pageTransitionAnimation: Boolean = true,
    val pageTurnMode: PageTurnMode = PageTurnMode.Slide,
    val pageBackground: PageBackground = PageBackground.Paper,
    val showPortraitPageBackContent: Boolean = true,
    val usePurePageBackgroundColors: Boolean = false,
    val showSpreadShiftButtons: Boolean = false,
    val epubFontSizeSp: Float = 18f,
    val epubFontFamily: String = "Serif",
    val epubTextAlign: EpubTextAlign = EpubTextAlign.Left,
    val ePaperMode: EPaperMode = EPaperMode.Off,
    val readerBrightness: Float = -1f,
    val nightModeEnabled: Boolean = false,
    val nightLightIntensity: Float = 0.45f,
    val navigationMode: ReaderNavigationMode = ReaderNavigationMode.Default,
    val tappingInvertMode: ReaderTappingInvertMode = ReaderTappingInvertMode.None,
    val imageScaleType: ReaderImageScaleType = ReaderImageScaleType.FitScreen,
    val cropBorders: Boolean = false
) {
    val rightToLeft: Boolean
        get() = readingDirection == ReaderReadingDirection.RightToLeft
}

data class AppSettings(
    val reader: ReaderSettings = ReaderSettings(),
    val appTheme: com.bunko.reader.ui.theme.AppTheme = com.bunko.reader.ui.theme.AppTheme.Default,
    val isDarkMode: Boolean = true,
    val isAmoledMode: Boolean = false,
)

class AppSettingsStore(private val context: Context) {
    private val KEY_APP_THEME = stringPreferencesKey("app_theme")
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
    private val KEY_AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    private val KEY_RTL = booleanPreferencesKey("reader_rtl")
    private val KEY_READING_DIRECTION = stringPreferencesKey("reader_reading_direction")
    private val KEY_PAGE_LAYOUT_MODE = stringPreferencesKey("reader_page_layout_mode")
    private val KEY_INVERT_MODE = stringPreferencesKey("reader_invert_mode")
    private val KEY_INVERT_WHITE_THRESHOLD = floatPreferencesKey("reader_invert_white_threshold")
    private val KEY_EPAPER_MODE = stringPreferencesKey("reader_epaper_mode")
    private val KEY_PREFETCH_TURNS = intPreferencesKey("reader_prefetch_turns")
    private val KEY_LEGACY_UNMETERED_PREFETCH_TURNS = intPreferencesKey("reader_unmetered_prefetch_turns")
    private val KEY_PAGE_TRANSITION_ANIMATION = booleanPreferencesKey("reader_page_transition_animation")
    private val KEY_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
    private val KEY_PAGE_BACKGROUND = stringPreferencesKey("reader_page_background")
    private val KEY_SHOW_PORTRAIT_PAGE_BACK_CONTENT =
        booleanPreferencesKey("reader_show_portrait_page_back_content")
    private val KEY_USE_PURE_PAGE_BACKGROUND_COLORS =
        booleanPreferencesKey("reader_use_pure_page_background_colors")
    private val KEY_SHOW_SPREAD_SHIFT_BUTTONS = booleanPreferencesKey("reader_show_spread_shift_buttons")
    private val KEY_EPUB_FONT_SIZE_SP = floatPreferencesKey("reader_epub_font_size_sp")
    private val KEY_EPUB_FONT_FAMILY = stringPreferencesKey("reader_epub_font_family")
    private val KEY_EPUB_TEXT_ALIGN = stringPreferencesKey("reader_epub_text_align")
    private val KEY_READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
    private val KEY_NIGHT_MODE_ENABLED = booleanPreferencesKey("reader_night_mode_enabled")
    private val KEY_NIGHT_LIGHT_INTENSITY = floatPreferencesKey("reader_night_light_intensity")
    private val KEY_READER_NAVIGATION_MODE = stringPreferencesKey("reader_navigation_mode")
    private val KEY_READER_TAPPING_INVERTED = stringPreferencesKey("reader_tapping_inverted")
    private val KEY_READER_IMAGE_SCALE_TYPE = stringPreferencesKey("reader_image_scale_type")
    private val KEY_READER_CROP_BORDERS = booleanPreferencesKey("reader_crop_borders")

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            appTheme = prefs[KEY_APP_THEME]?.let { runCatching { com.bunko.reader.ui.theme.AppTheme.valueOf(it) }.getOrNull() }
                ?: com.bunko.reader.ui.theme.AppTheme.Default,
            isDarkMode = prefs[KEY_DARK_MODE] ?: true,
            isAmoledMode = prefs[KEY_AMOLED_MODE] ?: false,
            reader = ReaderSettings(
                readingDirection = readerReadingDirection(
                    storedName = prefs[KEY_READING_DIRECTION],
                    legacyRightToLeft = prefs[KEY_RTL]
                ),
                pageLayoutMode = prefs[KEY_PAGE_LAYOUT_MODE]
                    ?.let { runCatching { PageLayoutMode.valueOf(it) }.getOrNull() }
                    ?: PageLayoutMode.Auto,
                invertMode = prefs[KEY_INVERT_MODE]
                    ?.let { runCatching { InvertMode.valueOf(it) }.getOrNull() }
                    ?: InvertMode.Off,
                invertWhiteThreshold = prefs[KEY_INVERT_WHITE_THRESHOLD] ?: 0.5f,
                ePaperMode = prefs[KEY_EPAPER_MODE]
                    ?.let { runCatching { EPaperMode.valueOf(it) }.getOrNull() }
                    ?: EPaperMode.Off,
                prefetchTurns = (prefs[KEY_PREFETCH_TURNS]
                    ?: prefs[KEY_LEGACY_UNMETERED_PREFETCH_TURNS]
                    ?: DefaultReaderPrefetchTurns)
                    .coerceIn(0, MaxReaderPrefetchTurns),
                pageTransitionAnimation = prefs[KEY_PAGE_TRANSITION_ANIMATION] ?: true,
                pageTurnMode = prefs[KEY_PAGE_TURN_MODE]
                    ?.let { runCatching { PageTurnMode.valueOf(it) }.getOrNull() }
                    ?: PageTurnMode.Slide,
                pageBackground = prefs[KEY_PAGE_BACKGROUND]
                    ?.let { runCatching { PageBackground.valueOf(it) }.getOrNull() }
                    ?: PageBackground.Paper,
                showPortraitPageBackContent = prefs[KEY_SHOW_PORTRAIT_PAGE_BACK_CONTENT] ?: true,
                usePurePageBackgroundColors = prefs[KEY_USE_PURE_PAGE_BACKGROUND_COLORS] ?: false,
                showSpreadShiftButtons = prefs[KEY_SHOW_SPREAD_SHIFT_BUTTONS] ?: false,
                epubFontSizeSp = (prefs[KEY_EPUB_FONT_SIZE_SP] ?: 18f).coerceIn(12f, 36f),
                epubFontFamily = prefs[KEY_EPUB_FONT_FAMILY] ?: "Serif",
                epubTextAlign = prefs[KEY_EPUB_TEXT_ALIGN]
                    ?.let { runCatching { EpubTextAlign.valueOf(it) }.getOrNull() }
                    ?: EpubTextAlign.Left,
                readerBrightness = prefs[KEY_READER_BRIGHTNESS] ?: -1f,
                nightModeEnabled = prefs[KEY_NIGHT_MODE_ENABLED] ?: false,
                nightLightIntensity = (prefs[KEY_NIGHT_LIGHT_INTENSITY] ?: 0.45f).coerceIn(0f, 1f),
                navigationMode = prefs[KEY_READER_NAVIGATION_MODE]
                    ?.let { runCatching { ReaderNavigationMode.valueOf(it) }.getOrNull() }
                    ?: ReaderNavigationMode.Default,
                tappingInvertMode = prefs[KEY_READER_TAPPING_INVERTED]
                    ?.let { runCatching { ReaderTappingInvertMode.valueOf(it) }.getOrNull() }
                    ?: ReaderTappingInvertMode.None,
                imageScaleType = prefs[KEY_READER_IMAGE_SCALE_TYPE]
                    ?.let { runCatching { ReaderImageScaleType.valueOf(it) }.getOrNull() }
                    ?: ReaderImageScaleType.FitScreen,
                cropBorders = prefs[KEY_READER_CROP_BORDERS] ?: false
            )
        )
    }

    suspend fun setRightToLeft(value: Boolean) {
        setReadingDirection(
            if (value) ReaderReadingDirection.RightToLeft else ReaderReadingDirection.LeftToRight
        )
    }

    suspend fun setReadingDirection(value: ReaderReadingDirection) {
        context.settingsDataStore.edit { it[KEY_READING_DIRECTION] = value.name }
    }

    suspend fun setPageLayoutMode(value: PageLayoutMode) {
        context.settingsDataStore.edit { it[KEY_PAGE_LAYOUT_MODE] = value.name }
    }

    suspend fun setInvertMode(value: InvertMode) {
        context.settingsDataStore.edit { it[KEY_INVERT_MODE] = value.name }
    }

    suspend fun setEPaperMode(value: EPaperMode) {
        context.settingsDataStore.edit { it[KEY_EPAPER_MODE] = value.name }
    }

    suspend fun setReaderBrightness(value: Float) {
        context.settingsDataStore.edit { it[KEY_READER_BRIGHTNESS] = value }
    }

    suspend fun setNightModeEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_NIGHT_MODE_ENABLED] = value }
    }

    suspend fun setNightLightIntensity(value: Float) {
        context.settingsDataStore.edit { it[KEY_NIGHT_LIGHT_INTENSITY] = value }
    }

    suspend fun setInvertWhiteThreshold(value: Float) {
        context.settingsDataStore.edit { it[KEY_INVERT_WHITE_THRESHOLD] = value }
    }

    suspend fun setPrefetchTurns(value: Int) {
        context.settingsDataStore.edit { it[KEY_PREFETCH_TURNS] = value.coerceIn(0, MaxReaderPrefetchTurns) }
    }

    suspend fun setPageTransitionAnimation(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_PAGE_TRANSITION_ANIMATION] = value }
    }

    suspend fun setPageTurnMode(value: PageTurnMode) {
        context.settingsDataStore.edit { it[KEY_PAGE_TURN_MODE] = value.name }
    }

    suspend fun setPageBackground(value: PageBackground) {
        context.settingsDataStore.edit { it[KEY_PAGE_BACKGROUND] = value.name }
    }

    suspend fun setShowPortraitPageBackContent(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_PORTRAIT_PAGE_BACK_CONTENT] = value }
    }

    suspend fun setUsePurePageBackgroundColors(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_USE_PURE_PAGE_BACKGROUND_COLORS] = value }
    }

    suspend fun setShowSpreadShiftButtons(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_SHOW_SPREAD_SHIFT_BUTTONS] = value }
    }

    suspend fun setEpubFontSizeSp(value: Float) {
        context.settingsDataStore.edit { it[KEY_EPUB_FONT_SIZE_SP] = value.coerceIn(12f, 36f) }
    }

    suspend fun setEpubFontFamily(value: String) {
        context.settingsDataStore.edit { it[KEY_EPUB_FONT_FAMILY] = value }
    }

    suspend fun setEpubTextAlign(value: EpubTextAlign) {
        context.settingsDataStore.edit { it[KEY_EPUB_TEXT_ALIGN] = value.name }
    }

    suspend fun setNavigationMode(value: ReaderNavigationMode) {
        context.settingsDataStore.edit { it[KEY_READER_NAVIGATION_MODE] = value.name }
    }

    suspend fun setTappingInvertMode(value: ReaderTappingInvertMode) {
        context.settingsDataStore.edit { it[KEY_READER_TAPPING_INVERTED] = value.name }
    }

    suspend fun setImageScaleType(value: ReaderImageScaleType) {
        context.settingsDataStore.edit { it[KEY_READER_IMAGE_SCALE_TYPE] = value.name }
    }

    suspend fun setCropBorders(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_READER_CROP_BORDERS] = value }
    }

    suspend fun setAppTheme(value: com.bunko.reader.ui.theme.AppTheme) {
        context.settingsDataStore.edit { it[KEY_APP_THEME] = value.name }
    }

    suspend fun setDarkMode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_DARK_MODE] = value }
    }

    suspend fun setAmoledMode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AMOLED_MODE] = value }
    }

    suspend fun toggleDarkMode() {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_DARK_MODE] ?: true
            prefs[KEY_DARK_MODE] = !current
        }
    }
}

internal fun readerReadingDirection(
    storedName: String?,
    legacyRightToLeft: Boolean?
): ReaderReadingDirection {
    return storedName
        ?.let { runCatching { ReaderReadingDirection.valueOf(it) }.getOrNull() }
        ?: if (legacyRightToLeft == true) {
            ReaderReadingDirection.RightToLeft
        } else {
            ReaderReadingDirection.LeftToRight
        }
}
