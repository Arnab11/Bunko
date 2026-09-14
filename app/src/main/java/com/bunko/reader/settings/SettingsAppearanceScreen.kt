package com.bunko.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunko.reader.ui.theme.AppTheme
import com.bunko.reader.ui.theme.LocalThemeTransitionState

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
                onThemeSelected = { theme, position ->
                    if (theme != currentTheme) {
                        themeTransition?.startTransition(position)
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
                var darkModeRowBounds by remember { mutableStateOf(Rect.Zero) }
                SwitchSettingRow(
                    title = "Dark Mode",
                    subtitle = "Use dark backgrounds across all screens",
                    checked = isDarkMode,
                    modifier = Modifier.onGloballyPositioned { darkModeRowBounds = it.boundsInRoot() },
                    onCheckedChange = { checked ->
                        themeTransition?.startTransition(darkModeRowBounds.center)
                        onDarkModeChanged(checked)
                    }
                )

                SettingsDivider()

                var amoledRowBounds by remember { mutableStateOf(Rect.Zero) }
                SwitchSettingRow(
                    title = "AMOLED Mode",
                    subtitle = "Pure black (#000000) surfaces for OLED displays",
                    checked = isAmoledMode,
                    enabled = isDarkMode,
                    modifier = Modifier.onGloballyPositioned { amoledRowBounds = it.boundsInRoot() },
                    onCheckedChange = { amoled ->
                        themeTransition?.startTransition(amoledRowBounds.center)
                        onAmoledModeChanged(amoled)
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
    onThemeSelected: (AppTheme, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val entries = remember { AppTheme.entries }
    val selectedIndex = remember(entries, currentTheme) {
        entries.indexOf(currentTheme).coerceAtLeast(0)
    }

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(entries, key = { _, theme -> theme.name }) { _, theme ->
            ThemeSwatchItem(
                theme = theme,
                isSelected = theme == currentTheme,
                isDark = isDarkMode,
                isAmoled = isAmoled,
                onClick = { position ->
                    onThemeSelected(theme, position)
                }
            )
        }
    }
}
