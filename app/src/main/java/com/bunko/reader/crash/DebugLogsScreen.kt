package com.bunko.reader.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DebugLogRepository() }
    val listState = rememberLazyListState()

    var entries by remember { mutableStateOf<List<DebugLogEntry>>(emptyList()) }
    var rawCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedLevels by remember { mutableStateOf(DebugLogLevel.entries.toSet()) }
    var expandedEntryIds by remember { mutableStateOf(setOf<String>()) }

    suspend fun refreshLogs() {
        try {
            val snapshot = repository.loadSnapshot()
            entries = snapshot.entries
            rawCount = snapshot.rawLineCount
            errorMessage = null
        } catch (t: Throwable) {
            if (entries.isEmpty()) {
                errorMessage = t.message ?: "Failed to read logcat"
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(isPaused) {
        if (!isPaused) {
            while (isActive) {
                refreshLogs()
                delay(1500)
            }
        }
    }

    val filteredEntries = remember(entries, searchQuery, selectedLevels) {
        val query = searchQuery.trim().lowercase()
        entries.filter { entry ->
            entry.level in selectedLevels && (
                query.isEmpty() ||
                entry.message.lowercase().contains(query) ||
                entry.tag.lowercase().contains(query)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search logs...") },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                        )
                    } else {
                        Column {
                            Text("Debug Logs", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${filteredEntries.size} entries" + if (isPaused) " (Paused)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { isPaused = !isPaused }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val text = buildDebugLogText(filteredEntries, includeDeviceInfo = true)
                            shareDebugLogs(context, text)
                        }
                    }) {
                        Icon(Icons.Filled.Share, "Share Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Level Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allSelected = selectedLevels.size == DebugLogLevel.entries.size
                FilterChip(
                    selected = allSelected,
                    onClick = {
                        selectedLevels = if (allSelected) setOf(DebugLogLevel.Error, DebugLogLevel.Warn) else DebugLogLevel.entries.toSet()
                    },
                    label = { Text("ALL") }
                )
                DebugLogLevel.entries.forEach { level ->
                    val isSelected = level in selectedLevels
                    val chipColor = debugLogLevelColor(level)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedLevels = if (isSelected) {
                                if (selectedLevels.size > 1) selectedLevels - level else selectedLevels
                            } else {
                                selectedLevels + level
                            }
                        },
                        label = { Text(level.code) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.2f),
                            selectedLabelColor = chipColor
                        )
                    )
                }
            }

            when {
                loading && entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null && entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage ?: "Error reading logs", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            IconButton(onClick = { scope.launch { refreshLogs() } }) {
                                Icon(Icons.Filled.Refresh, "Retry")
                            }
                        }
                    }
                }
                filteredEntries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No logs matching '$searchQuery'" else "No log entries captured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = filteredEntries,
                            key = { it.id }
                        ) { entry ->
                            DebugLogEntryCard(
                                entry = entry,
                                expanded = entry.id in expandedEntryIds,
                                onToggleExpanded = {
                                    expandedEntryIds = if (entry.id in expandedEntryIds) {
                                        expandedEntryIds - entry.id
                                    } else {
                                        expandedEntryIds + entry.id
                                    }
                                },
                                onCopy = {
                                    copyLogEntry(context, entry)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DebugLogEntryCard(
    entry: DebugLogEntry,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopy: () -> Unit
) {
    val levelColor = debugLogLevelColor(entry.level)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = onToggleExpanded,
                onLongClick = onCopy
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DebugLogLevelBadge(entry.level)
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.tag.isNotBlank()) {
                    Text(
                        text = entry.tag,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (entry.level == DebugLogLevel.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )

            if (expanded && entry.pid != null) {
                Text(
                    text = "pid: ${entry.pid}" + (entry.tid?.let { " • tid: $it" } ?: "") + " • (long-press to copy)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun DebugLogLevelBadge(level: DebugLogLevel) {
    Surface(
        modifier = Modifier.size(22.dp),
        shape = RoundedCornerShape(6.dp),
        color = debugLogLevelColor(level)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = level.code,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun debugLogLevelColor(level: DebugLogLevel): Color = when (level) {
    DebugLogLevel.Verbose -> MaterialTheme.colorScheme.onSurfaceVariant
    DebugLogLevel.Debug -> Color(0xFF00ACC1)
    DebugLogLevel.Info -> MaterialTheme.colorScheme.primary
    DebugLogLevel.Warn -> Color(0xFFFFB300)
    DebugLogLevel.Error -> MaterialTheme.colorScheme.error
}

private fun formatDebugLogEntry(entry: DebugLogEntry): String = buildString {
    append(entry.timestamp)
    entry.pid?.let { pid ->
        append(' ')
        append(pid)
        entry.tid?.let { tid ->
            append('/')
            append(tid)
        }
    }
    append(' ')
    append(entry.level.code)
    append('/')
    append(entry.tag.ifBlank { "unknown" })
    append(": ")
    append(entry.message)
}

private fun copyLogEntry(context: Context, entry: DebugLogEntry) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("Bunko Log Entry", formatDebugLogEntry(entry)))
}

private fun buildDebugLogText(
    entries: List<DebugLogEntry>,
    includeDeviceInfo: Boolean
): String = buildString {
    if (includeDeviceInfo) {
        appendLine(CrashReportStore.collectDeviceInfo())
        appendLine()
        appendLine("=== Bunko Logcat Buffer ===")
    }
    entries.forEach { entry -> appendLine(formatDebugLogEntry(entry)) }
}.trimEnd()

private fun shareDebugLogs(context: Context, text: String) {
    if (text.isBlank()) return
    val byteCount = text.toByteArray(Charsets.UTF_8).size
    if (byteCount > 256 * 1024) {
        exportDebugLogs(context, text)
        return
    }

    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share Bunko debug logs"
        )
    )
}

private fun exportDebugLogs(
    context: Context,
    text: String
) {
    if (text.isBlank()) return

    val exportDirectory = File(context.cacheDir, "shared_logs").apply { mkdirs() }
    val file = File(exportDirectory, "bunko-debug-${System.currentTimeMillis()}.txt")
    file.writeText(text)

    exportDirectory
        .listFiles { candidate -> candidate.isFile && candidate.name.startsWith("bunko-debug-") }
        ?.sortedByDescending(File::lastModified)
        ?.drop(5)
        ?.forEach { candidate -> candidate.delete() }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                clipData = ClipData.newRawUri(file.name, uri)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Export Bunko debug logs"
        )
    )
}
