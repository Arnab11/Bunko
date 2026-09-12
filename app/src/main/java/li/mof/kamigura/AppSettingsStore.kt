package li.mof.kamigura

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("kamigura_settings")

enum class InvertMode {
    Off,
    Smart,
    Always
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

/** Colour of the margins around a page (and of the curl flap's back face). */
enum class PageBackground {
    Paper,
    Dark
}

internal const val DefaultReaderPrefetchTurns = 4
internal const val MaxReaderPrefetchTurns = 8

data class ReaderSettings(
    val readingDirection: ReaderReadingDirection = ReaderReadingDirection.RightToLeft,
    val invertMode: InvertMode = InvertMode.Off,
    val invertWhiteThreshold: Float = 0.5f,
    val prefetchTurns: Int = DefaultReaderPrefetchTurns,
    val pageTransitionAnimation: Boolean = true,
    val pageTurnMode: PageTurnMode = PageTurnMode.Curl,
    val pageBackground: PageBackground = PageBackground.Paper,
    val showPortraitPageBackContent: Boolean = true,
    val usePurePageBackgroundColors: Boolean = false,
    val showSpreadShiftButtons: Boolean = true,
    val epubFontSizeSp: Float = 18f,
    val epubFontFamily: String = "Serif"
) {
    val rightToLeft: Boolean
        get() = readingDirection == ReaderReadingDirection.RightToLeft
}

data class AppSettings(
    val reader: ReaderSettings = ReaderSettings()
)

class AppSettingsStore(private val context: Context) {
    private val KEY_RTL = booleanPreferencesKey("reader_rtl")
    private val KEY_READING_DIRECTION = stringPreferencesKey("reader_reading_direction")
    private val KEY_INVERT_MODE = stringPreferencesKey("reader_invert_mode")
    private val KEY_INVERT_WHITE_THRESHOLD = floatPreferencesKey("reader_invert_white_threshold")
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

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            reader = ReaderSettings(
                readingDirection = readerReadingDirection(
                    storedName = prefs[KEY_READING_DIRECTION],
                    legacyRightToLeft = prefs[KEY_RTL]
                ),
                invertMode = prefs[KEY_INVERT_MODE]
                    ?.let { runCatching { InvertMode.valueOf(it) }.getOrNull() }
                    ?: InvertMode.Off,
                invertWhiteThreshold = prefs[KEY_INVERT_WHITE_THRESHOLD] ?: 0.5f,
                prefetchTurns = (prefs[KEY_PREFETCH_TURNS]
                    ?: prefs[KEY_LEGACY_UNMETERED_PREFETCH_TURNS]
                    ?: DefaultReaderPrefetchTurns)
                    .coerceIn(0, MaxReaderPrefetchTurns),
                pageTransitionAnimation = prefs[KEY_PAGE_TRANSITION_ANIMATION] ?: true,
                pageTurnMode = prefs[KEY_PAGE_TURN_MODE]
                    ?.let { runCatching { PageTurnMode.valueOf(it) }.getOrNull() }
                    ?: PageTurnMode.Curl,
                pageBackground = prefs[KEY_PAGE_BACKGROUND]
                    ?.let { runCatching { PageBackground.valueOf(it) }.getOrNull() }
                    ?: PageBackground.Paper,
                showPortraitPageBackContent = prefs[KEY_SHOW_PORTRAIT_PAGE_BACK_CONTENT] ?: true,
                usePurePageBackgroundColors = prefs[KEY_USE_PURE_PAGE_BACKGROUND_COLORS] ?: false,
                showSpreadShiftButtons = prefs[KEY_SHOW_SPREAD_SHIFT_BUTTONS] ?: true,
                epubFontSizeSp = (prefs[KEY_EPUB_FONT_SIZE_SP] ?: 18f).coerceIn(12f, 36f),
                epubFontFamily = prefs[KEY_EPUB_FONT_FAMILY] ?: "Serif"
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

    suspend fun setInvertMode(value: InvertMode) {
        context.settingsDataStore.edit { it[KEY_INVERT_MODE] = value.name }
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
}

internal fun readerReadingDirection(
    storedName: String?,
    legacyRightToLeft: Boolean?
): ReaderReadingDirection {
    return storedName
        ?.let { runCatching { ReaderReadingDirection.valueOf(it) }.getOrNull() }
        ?: if (legacyRightToLeft != false) {
            ReaderReadingDirection.RightToLeft
        } else {
            ReaderReadingDirection.LeftToRight
        }
}
