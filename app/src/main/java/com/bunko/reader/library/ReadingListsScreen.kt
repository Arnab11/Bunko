package com.bunko.reader.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.bunko.reader.CreateReadingListDto
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.ReadingListDto
import com.bunko.reader.ui.DarkLoadingState
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.BunkoPullToRefreshIndicator
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.theme.BunkoSurface

@Composable
internal fun ReadingListsScreen(
    sessionStore: KavitaSessionStore,
    onBack: () -> Unit,
    onOpenReadingList: (ReadingListDto) -> Unit,
    statusBarPadding: Boolean = true
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var apiError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        try {
            val (loadedApi, _) = KavitaClient(ctx, sessionStore).buildApi()
            api = loadedApi
            apiError = null
        } catch (t: Throwable) {
            BunkoLog.w("Could not create API for Reading Lists.", t)
            apiError = t.message ?: t.toString()
        }
    }

    ReadingListsPane(
        api = api,
        apiError = apiError,
        onBack = onBack,
        onOpenReadingList = onOpenReadingList,
        onRetryApi = { retryKey++ },
        statusBarPadding = statusBarPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReadingListsPane(
    api: KavitaApi?,
    apiError: String? = null,
    onBack: () -> Unit,
    onOpenReadingList: (ReadingListDto) -> Unit,
    onRetryApi: (() -> Unit)? = null,
    statusBarPadding: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var readingLists by remember { mutableStateOf<List<ReadingListDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    suspend fun loadReadingLists(initialLoad: Boolean) {
        val loadedApi = api ?: return
        if (initialLoad) loading = true else refreshing = true
        if (initialLoad) error = null
        try {
            readingLists = loadedApi.readingLists()
                .sortedBy { it.title ?: "Reading List ${it.id}" }
            error = null
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            BunkoLog.w("Could not load reading lists.", t)
            if (readingLists.isEmpty()) error = t.message ?: t.toString()
        } finally {
            if (initialLoad) loading = false else refreshing = false
        }
    }

    LaunchedEffect(api, apiError, retryKey) {
        if (apiError != null) {
            loading = false
            error = apiError
            return@LaunchedEffect
        }
        if (api == null) {
            loading = true
            error = null
            return@LaunchedEffect
        }
        loadReadingLists(initialLoad = true)
    }

    BrowsePageScaffold(
        title = "Reading Lists",
        onBack = onBack,
        statusBarPadding = statusBarPadding
    ) {
        when {
            loading -> DarkLoadingState()
            error != null -> DarkMessageState(
                title = "Could not load reading lists",
                body = error ?: "Unknown error",
                actionLabel = "Retry",
                onAction = {
                    if (apiError != null) onRetryApi?.invoke() else retryKey++
                }
            )
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    if (apiError != null) {
                        onRetryApi?.invoke()
                    } else {
                        scope.launch { loadReadingLists(initialLoad = false) }
                    }
                },
                state = pullRefreshState,
                indicator = { BunkoPullToRefreshIndicator(pullRefreshState, refreshing) }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        NewReadingListRow(onClick = {
                            createTitle = ""
                            createDialog = true
                        })
                    }
                    if (readingLists.isEmpty()) {
                        item {
                            DarkMessageState("Reading Lists", "No reading lists yet.")
                        }
                    }
                    items(readingLists, key = { it.id }) { readingList ->
                        ReadingListRow(
                            readingList = readingList,
                            onClick = { onOpenReadingList(readingList) }
                        )
                    }
                }
            }
        }
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!creating) createDialog = false
            },
            title = { Text("New Reading List") },
            text = {
                OutlinedTextField(
                    value = createTitle,
                    onValueChange = { createTitle = it },
                    label = { Text("Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !creating && createTitle.trim().isNotBlank(),
                    onClick = {
                        val title = createTitle.trim()
                        scope.launch {
                            creating = true
                            try {
                                val created = api?.createReadingList(CreateReadingListDto(title))
                                    ?: throw IllegalStateException("Server is not ready")
                                readingLists = (readingLists + created)
                                    .distinctBy { it.id }
                                    .sortedBy { it.title ?: "Reading List ${it.id}" }
                                createDialog = false
                            } catch (t: Throwable) {
                                BunkoLog.w("Could not create reading list.", t)
                                error = t.message ?: t.toString()
                            } finally {
                                creating = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !creating,
                    onClick = { createDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NewReadingListRow(
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = "New Reading List",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReadingListRow(
    readingList: ReadingListDto,
    onClick: () -> Unit
) {
    Surface(
        color = BunkoSurface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = readingList.title ?: "Reading List ${readingList.id}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = readingList.itemCount?.let { count ->
                        when (count) {
                            1 -> "1 series"
                            else -> "$count series"
                        }
                    } ?: "Reading List",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
