package com.bunko.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.bunko.reader.cache.ImageCacheManager
import com.bunko.reader.cache.ImageCacheUsage
import com.bunko.reader.reader.ReaderSessionPreferenceCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSettingsScreen(onBack: () -> Unit) {
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
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SettingsTopAppBar(title = "Storage & cache", onBack = onBack)
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsFormContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentUsage = usage
                Text("Image cache", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cached images make recently read pages and covers reopen faster. " +
                        "Offline downloads are stored separately and are not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Reader direction and invert choices are cached per series for 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CacheUsageRow(
                    title = "Cover images",
                    usage = currentUsage?.coverBytes,
                    limit = currentUsage?.coverLimitBytes,
                    enabled = !clearing,
                    onClear = { clear { ImageCacheManager.clearCoverCache(ctx) } }
                )
                CacheUsageRow(
                    title = "Reader pages",
                    usage = currentUsage?.readerBytes,
                    limit = currentUsage?.readerLimitBytes,
                    enabled = !clearing,
                    onClear = { clear { ImageCacheManager.clearReaderCache(ctx) } }
                )
                Spacer(Modifier.padding(top = 4.dp))
                OutlinedButton(
                    onClick = {
                        clear {
                            ImageCacheManager.clearAll(ctx)
                            ReaderSessionPreferenceCache.clear(ctx.cacheDir)
                        }
                    },
                    enabled = !clearing
                ) {
                    Text(if (clearing) "Clearing..." else "Clear all caches")
                }
                Text(
                    "Android may clear these caches when storage is needed. You can also clear " +
                        "them from the system App info screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = ::refresh, enabled = !clearing) {
                    Text("Refresh usage")
                }
            }
        }
    }
}

@Composable
private fun CacheUsageRow(
    title: String,
    usage: Long?,
    limit: Long?,
    enabled: Boolean,
    onClear: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (usage == null || limit == null) "Calculating..."
                else "${formatCacheBytes(usage)} of up to ${formatCacheBytes(limit)}"
            )
        },
        trailingContent = {
            OutlinedButton(onClick = onClear, enabled = enabled) { Text("Clear") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
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
