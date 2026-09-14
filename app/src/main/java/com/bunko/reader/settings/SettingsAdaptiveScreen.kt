package com.bunko.reader.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunko.reader.AppSettings
import com.bunko.reader.AppSettingsStore
import com.bunko.reader.CacheSettingsScreen
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.ReaderSettingsScreen
import com.bunko.reader.offline.LocalBookRepository
import kotlinx.coroutines.launch

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APPEARANCE(
        title = "Appearance",
        subtitle = "Theme palettes, dark mode & AMOLED",
        icon = Icons.Filled.Palette
    ),
    SOURCES(
        title = "Library Sources",
        subtitle = "Offline folder & Kavita media servers",
        icon = Icons.Filled.Storage
    ),
    READER(
        title = "Reader",
        subtitle = "Reading direction, curls & layout",
        icon = Icons.AutoMirrored.Filled.MenuBook
    ),
    CACHE(
        title = "Storage & Cache",
        subtitle = "Cover cache & temporary data",
        icon = Icons.Filled.CleaningServices
    ),
    ABOUT(
        title = "About",
        subtitle = "Version & GitHub repository",
        icon = Icons.Filled.Info
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdaptiveScreen(
    settingsStore: AppSettingsStore,
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    onConfigureServerDetails: () -> Unit,
    onBack: () -> Unit,
    initialCategory: SettingsCategory? = null,
    onActiveModeChanged: suspend (String) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val appSettings by settingsStore.flow.collectAsState(initial = AppSettings())

    if (isTablet) {
        var selectedCategory by remember {
            mutableStateOf(initialCategory ?: SettingsCategory.APPEARANCE)
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Left Pane: Master Categories (38% width)
                Column(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Settings",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        SettingsSectionCard {
                            SettingsCategory.entries.forEachIndexed { index, cat ->
                                CategoryRow(
                                    icon = cat.icon,
                                    title = cat.title,
                                    subtitle = cat.subtitle,
                                    isSelected = selectedCategory == cat,
                                    isFirst = index == 0,
                                    isLast = index == SettingsCategory.entries.lastIndex,
                                    onClick = { selectedCategory = cat }
                                )
                                if (index < SettingsCategory.entries.lastIndex) {
                                    CategoryRowGap()
                                }
                            }
                        }
                    }
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // Right Pane: Detail Content (62% width)
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = selectedCategory.title,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        SettingsCategoryContent(
                            category = selectedCategory,
                            settingsStore = settingsStore,
                            appSettings = appSettings,
                            localRepository = localRepository,
                            sessionStore = sessionStore,
                            onConfigureServerDetails = onConfigureServerDetails,
                            onActiveModeChanged = onActiveModeChanged
                        )
                    }
                }
            }
        }
    } else {
        // Phone Layout (single-pane drill-down)
        var activeCategory by remember { mutableStateOf(initialCategory) }

        BackHandler(enabled = activeCategory != null) {
            activeCategory = null
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = activeCategory?.title ?: "Settings",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (activeCategory != null) {
                                activeCategory = null
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (activeCategory == null) {
                    // Master List
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        SettingsSectionCard {
                            SettingsCategory.entries.forEachIndexed { index, cat ->
                                CategoryRow(
                                    icon = cat.icon,
                                    title = cat.title,
                                    subtitle = cat.subtitle,
                                    isFirst = index == 0,
                                    isLast = index == SettingsCategory.entries.lastIndex,
                                    onClick = { activeCategory = cat }
                                )
                                if (index < SettingsCategory.entries.lastIndex) {
                                    CategoryRowGap()
                                }
                            }
                        }
                    }
                } else {
                    // Detail Screen
                    SettingsCategoryContent(
                        category = activeCategory!!,
                        settingsStore = settingsStore,
                        appSettings = appSettings,
                        localRepository = localRepository,
                        sessionStore = sessionStore,
                        onConfigureServerDetails = onConfigureServerDetails,
                        onActiveModeChanged = onActiveModeChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    settingsStore: AppSettingsStore,
    appSettings: AppSettings,
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    onConfigureServerDetails: () -> Unit,
    onActiveModeChanged: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    when (category) {
        SettingsCategory.APPEARANCE -> {
            SettingsAppearanceScreen(
                currentTheme = appSettings.appTheme,
                isDarkMode = appSettings.isDarkMode,
                isAmoledMode = appSettings.isAmoledMode,
                onThemeSelected = { theme ->
                    scope.launch { settingsStore.setAppTheme(theme) }
                },
                onDarkModeChanged = { dark ->
                    scope.launch { settingsStore.setDarkMode(dark) }
                },
                onAmoledModeChanged = { amoled ->
                    scope.launch { settingsStore.setAmoledMode(amoled) }
                }
            )
        }
        SettingsCategory.SOURCES -> {
            SettingsSourcesScreen(
                localRepository = localRepository,
                sessionStore = sessionStore,
                onConfigureServerDetails = onConfigureServerDetails,
                onActiveModeChanged = onActiveModeChanged
            )
        }
        SettingsCategory.READER -> {
            ReaderSettingsScreen(
                settingsStore = settingsStore,
                onBack = {},
                showTopBar = false
            )
        }
        SettingsCategory.CACHE -> {
            CacheSettingsScreen(
                onBack = {},
                showTopBar = false
            )
        }
        SettingsCategory.ABOUT -> {
            AboutScreen()
        }
    }
}
