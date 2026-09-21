package com.bunko.reader

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import com.bunko.reader.crash.DebugLogsActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunko.reader.cache.ImageCacheManager
import com.bunko.reader.cache.ImageCacheUsage
import com.bunko.reader.reader.ReaderSessionPreferenceCache
import com.bunko.reader.settings.CategoryRowGap
import com.bunko.reader.settings.ClickableSettingRow
import com.bunko.reader.settings.SettingsSectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSettingsScreen(
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<ImageCacheUsage?>(null) }
    var clearing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch { usage = ImageCacheManager.usage(ctx) }
    }

    LaunchedEffect(Unit) { usage = ImageCacheManager.usage(ctx) }

    fun clear(action: suspend () -> Unit) {
        scope.launch {
            clearing = true
            try {
                action()
            } finally {
                usage = ImageCacheManager.usage(ctx)
                clearing = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (showTopBar) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
        ) {
            if (showTopBar) {
                SettingsTopAppBar(title = "Storage & Cache", onBack = onBack)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val currentUsage = usage

                // Cache Usage Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Image & Data Cache",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        ClickableSettingRow(
                            icon = Icons.Filled.Image,
                            title = "Cover Images",
                            subtitle = if (currentUsage == null) "Calculating..." else "${formatCacheBytes(currentUsage.coverBytes)} of ${formatCacheBytes(currentUsage.coverLimitBytes)}",
                            trailingText = if (clearing) "..." else "Clear",
                            onClick = { if (!clearing) clear { ImageCacheManager.clearCoverCache(ctx) } }
                        )
                        CategoryRowGap()
                        ClickableSettingRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "Reader Pages",
                            subtitle = if (currentUsage == null) "Calculating..." else "${formatCacheBytes(currentUsage.readerBytes)} of ${formatCacheBytes(currentUsage.readerLimitBytes)}",
                            trailingText = if (clearing) "..." else "Clear",
                            onClick = { if (!clearing) clear { ImageCacheManager.clearReaderCache(ctx) } }
                        )
                    }
                }

                // Cache Management Actions Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Maintenance",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        ClickableSettingRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = "Clear All Caches",
                            subtitle = "Frees up all cached images and series reading preferences",
                            trailingText = if (clearing) "Clearing..." else "Clear All",
                            onClick = {
                                if (!clearing) {
                                    clear {
                                        ImageCacheManager.clearAll(ctx)
                                        ReaderSessionPreferenceCache.clear(ctx.cacheDir)
                                    }
                                }
                            }
                        )
                        CategoryRowGap()
                        ClickableSettingRow(
                            icon = Icons.Filled.Refresh,
                            title = "Refresh Usage",
                            subtitle = "Recalculate cache sizes currently stored on device",
                            trailingText = "Refresh",
                            onClick = ::refresh
                        )
                    }
                }

                // Diagnostics & Logs Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Diagnostics & Logs",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        ClickableSettingRow(
                            icon = Icons.Filled.BugReport,
                            title = "App Logs",
                            subtitle = "View, filter, and export real-time debug logs",
                            trailingText = "View",
                            onClick = {
                                ctx.startActivity(Intent(ctx, DebugLogsActivity::class.java))
                            }
                        )
                    }
                }

                Text(
                    text = "Cached images make recently opened covers and pages reopen instantaneously. Offline downloads are stored separately and are never deleted when clearing cache.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private fun formatCacheBytes(bytes: Long): String {
    val mib = 1024L * 1024L
    val gib = 1024L * mib
    return when {
        bytes >= gib -> "%.1f GB".format(bytes.toDouble() / gib)
        bytes >= mib -> "%.0f MB".format(bytes.toDouble() / mib)
        else -> "${(bytes / 1024L).coerceAtLeast(0L)} KB"
    }
}
