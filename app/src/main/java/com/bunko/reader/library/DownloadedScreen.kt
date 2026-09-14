package com.bunko.reader.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.bunko.reader.ChapterDto
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaApi
import com.bunko.reader.KavitaClient
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.MarkChapterReadDto
import com.bunko.reader.MarkVolumesReadDto
import com.bunko.reader.VolumeDto
import com.bunko.reader.download.OfflineIssueRecord
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.download.localCoverFile
import com.bunko.reader.series.IssueDetailSideSheet
import com.bunko.reader.series.chapterCoverUrl
import com.bunko.reader.series.internal.coverActionColor
import com.bunko.reader.ui.DarkMessageState
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.BrowsePageScaffold
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.theme.BunkoBackground

@Composable
private fun DownloadedGrid(
    records: List<OfflineIssueRecord>,
    session: KavitaSession,
    onSelect: (OfflineIssueRecord) -> Unit,
    onReadIncognito: (OfflineIssueRecord) -> Unit,
    onMarkRead: (OfflineIssueRecord) -> Unit,
    onMarkUnread: (OfflineIssueRecord) -> Unit,
    onDelete: (List<OfflineIssueRecord>) -> Unit,
    onBack: () -> Unit,
    statusBarPadding: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    val selectedIdSet = selectedIds.toSet()

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptyList()
    }

    fun toggleSelection(chapterId: Int) {
        selectedIds = if (chapterId in selectedIdSet) {
            selectedIds - chapterId
        } else {
            selectedIds + chapterId
        }
    }

    LaunchedEffect(records) {
        val availableIds = records.mapTo(mutableSetOf()) { it.chapterId }
        selectedIds = selectedIds.filter { it in availableIds }
        if (records.isEmpty()) selectionMode = false
    }

    BackHandler(enabled = selectionMode, onBack = ::exitSelectionMode)

    BrowsePageScaffold(
        title = if (selectionMode) "${selectedIds.size} selected" else "Downloaded",
        modifier = modifier,
        statusBarPadding = statusBarPadding,
        onBack = if (selectionMode) ::exitSelectionMode else onBack,
        navigationIcon = if (selectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = if (selectionMode) "Cancel selection" else "Back",
        actions = {
            if (selectionMode) {
                IconButton(
                    onClick = {
                        selectedIds = if (selectedIds.size == records.size) {
                            emptyList()
                        } else {
                            records.map { it.chapterId }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = if (selectedIds.size == records.size) {
                            "Clear selection"
                        } else {
                            "Select all"
                        },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        val selected = records.filter { it.chapterId in selectedIdSet }
                        exitSelectionMode()
                        onDelete(selected)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete selected downloads",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (records.isNotEmpty()) {
                IconButton(onClick = { selectionMode = true }) {
                    Icon(
                        imageVector = Icons.Filled.Checklist,
                        contentDescription = "Select items",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) {
        if (records.isEmpty()) {
            DarkMessageState(title = "Downloaded", body = "No issues downloaded yet.")
        } else {
            PosterGrid(items = records, key = { it.chapterId }) { record ->
                DownloadedIssueCard(
                    record = record,
                    session = session,
                    selectionMode = selectionMode,
                    selected = record.chapterId in selectedIdSet,
                    onClick = {
                        if (selectionMode) toggleSelection(record.chapterId) else onSelect(record)
                    },
                    onLongClick = {
                        if (!selectionMode) selectionMode = true
                        toggleSelection(record.chapterId)
                    },
                    onReadIncognito = { onReadIncognito(record) },
                    onMarkRead = { onMarkRead(record) },
                    onMarkUnread = { onMarkUnread(record) },
                    onDelete = { onDelete(listOf(record)) },
                    onSelectionChange = { toggleSelection(record.chapterId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadedIssueCard(
    record: OfflineIssueRecord,
    session: KavitaSession,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReadIncognito: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChange: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column {
                Box {
                    AsyncImage(
                        model = record.localCoverFile() ?: chapterCoverUrl(session, record.chapterId),
                        contentDescription = record.issueName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(KavitaCoverAspectRatio)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        contentScale = ContentScale.Crop
                    )
                    if (!selectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                        ) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Read") },
                                    leadingIcon = { Icon(Icons.Filled.AutoStories, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onClick()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Read Incognito") },
                                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onReadIncognito()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Mark as Read") },
                                    leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkRead()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Mark as Unread") },
                                    leadingIcon = { Icon(Icons.Filled.RemoveDone, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onMarkUnread()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Delete download", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(
                        text = record.issueName,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = record.seriesName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(KavitaCoverAspectRatio)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color.Black.copy(alpha = 0.12f)
                    )
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionChange() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.small)
                )
            }
        }
    }
}

@Composable
internal fun DownloadedScreen(
    sessionStore: KavitaSessionStore,
    onBack: () -> Unit,
    statusBarPadding: Boolean = true,
    navigationBarPadding: Boolean = true,
    onPickIssue: (
        libraryId: Int,
        seriesId: Int,
        volumeId: Int,
        chapterId: Int,
        incognito: Boolean
    ) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }

    var session by remember { mutableStateOf(KavitaSession()) }
    var api by remember { mutableStateOf<KavitaApi?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val downloadedFlow = remember(session.baseUrl, session.username, session.apiKey) {
        offlineRepository.observeDownloaded(session)
    }
    val downloaded by downloadedFlow.collectAsState(initial = emptyList())
    val visibleDownloads = downloaded.filterNot { it.chapterId in pendingDeleteIds }

    LaunchedEffect(Unit) {
        val loadedSession = sessionStore.load()
        session = loadedSession
        runCatching { offlineRepository.ensureLocalCovers(loadedSession) }
            .onFailure { BunkoLog.w("Could not ensure local covers on Downloaded.", it) }
        api = runCatching {
            KavitaClient(ctx, sessionStore).buildApi().first
        }.onFailure {
            BunkoLog.w("Could not create API for Downloaded.", it)
        }.getOrNull()
    }

    fun handleMarkRead(record: OfflineIssueRecord) {
        scope.launch {
            try {
                val currentApi = api
                if (currentApi != null) {
                    currentApi.markChapterRead(MarkChapterReadDto(record.seriesId, record.chapterId, false))
                } else {
                    offlineRepository.saveLocalProgress(
                        session = session,
                        chapterId = record.chapterId,
                        page = record.pageCount,
                        markRead = true
                    )
                }
                showMessage("Marked as read")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not mark downloaded chapter ${record.chapterId} as read.", t)
                showMessage("Could not mark issue as read")
            }
        }
    }

    fun handleMarkUnread(record: OfflineIssueRecord) {
        scope.launch {
            try {
                val currentApi = api
                if (currentApi != null) {
                    currentApi.markChaptersUnread(
                        MarkVolumesReadDto(record.seriesId, chapterIds = listOf(record.chapterId))
                    )
                } else {
                    offlineRepository.markLocalUnread(session, record.chapterId)
                }
                showMessage("Marked as unread")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                BunkoLog.w("Could not mark downloaded chapter ${record.chapterId} as unread.", t)
                showMessage("Could not mark issue as unread")
            }
        }
    }

    fun deleteDownloaded(records: List<OfflineIssueRecord>) {
        val deletingRecords = records.distinctBy { it.chapterId }
            .filterNot { it.chapterId in pendingDeleteIds }
        if (deletingRecords.isEmpty()) return
        val deletingIds = deletingRecords.mapTo(mutableSetOf()) { it.chapterId }
        pendingDeleteIds = pendingDeleteIds + deletingIds
        scope.launch {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = if (deletingRecords.size == 1) {
                        "Download deleted"
                    } else {
                        "${deletingRecords.size} downloads deleted"
                    },
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    pendingDeleteIds = pendingDeleteIds - deletingIds
                    return@launch
                }

                val failedIds = mutableSetOf<Int>()
                deletingRecords.forEach { record ->
                    try {
                        offlineRepository.remove(session, record.chapterId)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        failedIds += record.chapterId
                        BunkoLog.w("Could not delete downloaded chapter ${record.chapterId}.", t)
                    }
                }
                pendingDeleteIds = pendingDeleteIds - deletingIds
                if (failedIds.isNotEmpty()) {
                    snackbarHostState.showSnackbar(
                        if (failedIds.size == deletingRecords.size) {
                            "Could not delete downloads"
                        } else {
                            "Could not delete ${failedIds.size} downloads"
                        }
                    )
                }
            } catch (c: CancellationException) {
                pendingDeleteIds = pendingDeleteIds - deletingIds
                throw c
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .then(if (navigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
            .background(BunkoBackground)
    ) {
        DownloadedGrid(
            records = visibleDownloads,
            session = session,
            statusBarPadding = statusBarPadding,
            onSelect = { record ->
                onPickIssue(record.libraryId, record.seriesId, record.volumeId, record.chapterId, false)
            },
            onReadIncognito = { record ->
                onPickIssue(record.libraryId, record.seriesId, record.volumeId, record.chapterId, true)
            },
            onMarkRead = ::handleMarkRead,
            onMarkUnread = ::handleMarkUnread,
            onDelete = ::deleteDownloaded,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}


