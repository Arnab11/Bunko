package com.bunko.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunko.reader.ui.theme.AppTheme
import com.bunko.reader.ui.theme.LocalThemeTransitionState

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsAppearanceScreen(
    currentTheme: AppTheme,
    isDarkMode: Boolean,
    isAmoledMode: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeTransition = LocalThemeTransitionState.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Theme Palettes
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            ThemePicker(
                currentTheme = currentTheme,
                isDarkMode = isDarkMode,
                isAmoled = isAmoledMode,
                onThemeSelected = { theme ->
                    themeTransition?.startTransition(Offset.Zero)
                    scope.launch {
                        delay(50)
                        onThemeSelected(theme)
                    }
                }
            )
        }

        // Dark & AMOLED Mode Settings Card
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Display Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SettingsSectionCard {
                SwitchSettingRow(
                    title = "Dark Mode",
                    subtitle = "Use dark backgrounds across all screens",
                    checked = isDarkMode,
                    onCheckedChange = { checked ->
                        themeTransition?.startTransition(Offset.Zero)
                        scope.launch {
                            delay(50)
                            onDarkModeChanged(checked)
                        }
                    }
                )

                SettingsDivider()

                SwitchSettingRow(
                    title = "AMOLED Mode",
                    subtitle = "Pure black (#000000) surfaces for OLED displays",
                    checked = isAmoledMode,
                    enabled = isDarkMode,
                    onCheckedChange = { amoled ->
                        themeTransition?.startTransition(Offset.Zero)
                        scope.launch {
                            delay(50)
                            onAmoledModeChanged(amoled)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ThemePicker(
    currentTheme: AppTheme,
    isDarkMode: Boolean,
    isAmoled: Boolean,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val index = AppTheme.entries.indexOf(currentTheme)
        if (index >= 0) {
            listState.animateScrollToItem(maxOf(0, index - 1))
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(AppTheme.entries, key = { it.name }) { theme ->
            ThemeSwatchItem(
                theme = theme,
                isSelected = theme == currentTheme,
                isDark = isDarkMode,
                isAmoled = isAmoled,
                onClick = {
                    onThemeSelected(theme)
                }
            )
        }
    }
}
