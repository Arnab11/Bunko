package com.bunko.reader.library.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.asPaddingValues
import com.bunko.reader.ui.browse.CoverProgressBadge
import com.bunko.reader.ui.browse.UnifiedPosterCard
import com.bunko.reader.ui.browse.toUnifiedMediaItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookReadingState
import com.bunko.reader.offline.LocalBookRepository
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.theme.BunkoBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBookDetailScreen(
    bookId: String,
    localRepository: LocalBookRepository,
    onBack: () -> Unit,
    onOpenReader: (bookId: String, startPage: Int) -> Unit,
    modifier: Modifier = Modifier,
    isDualPane: Boolean = false,
) {
    val books by localRepository.booksFlow.collectAsState(initial = emptyList())
    val book = books.firstOrNull { it.id == bookId }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (book == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Book Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Book not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LocalBookDetailContent(
        book = book,
        allBooks = books,
        localRepository = localRepository,
        onBack = onBack,
        onOpenReader = { targetBook, startPage -> onOpenReader(targetBook.id, startPage) },
        isDualPane = isDualPane,
        modifier = modifier
    )
}

/**
 * Reusable Book Details content for both full-screen and tablet dual-pane right pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBookDetailContent(
    book: LocalBook,
    allBooks: List<LocalBook>,
    localRepository: LocalBookRepository,
    onBack: () -> Unit,
    onOpenReader: (book: LocalBook, startPage: Int) -> Unit,
    modifier: Modifier = Modifier,
    isDualPane: Boolean = false,
    onSelectBook: ((LocalBook) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeBookId by remember(book.id) { mutableStateOf(book.id) }
    val currentBook = allBooks.firstOrNull { it.id == activeBookId } ?: book

    var showEditDialog by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var showEditCoverDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingReadingDate by remember { mutableStateOf<String?>(null) }
    var actionsExpanded by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val coverFile = saveCustomCover(context, currentBook.id, uri)
                    if (coverFile != null) {
                        localRepository.setBookCover(currentBook.id, coverFile.absolutePath)
                        snackbarHostState.showSnackbar("Cover updated")
                    }
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to update cover")
                }
            }
        }
    }

    fun openForReading() {
        val startPage = if (currentBook.isCompleted) 0 else currentBook.lastReadPage
        onOpenReader(currentBook, startPage)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 4.dp,
                            end = 4.dp,
                            top = statusInsets.calculateTopPadding() + 8.dp,
                            bottom = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = if (isDualPane) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isDualPane) "Close" else "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Book Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )

                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Cover") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    actionsExpanded = false
                                    showEditCoverDialog = true
                                }
                            )
                            if (currentBook.lastReadPage > 0 || currentBook.isCompleted) {
                                DropdownMenuItem(
                                    text = { Text("Reset Progress") },
                                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        scope.launch {
                                            localRepository.resetBookReadingStats(currentBook.id)
                                            snackbarHostState.showSnackbar("Reading stats reset")
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete Book", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    actionsExpanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero section: Cover on left, Title, Author, Series, Format on right
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 8.dp,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .width(128.dp)
                                    .aspectRatio(KavitaCoverAspectRatio)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showCoverPreview = true }
                            ) {
                                if (currentBook.hasCover) {
                                    AsyncImage(
                                        model = currentBook.coverPath,
                                        contentDescription = currentBook.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentBook.title.take(2).uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            AssistChip(
                                onClick = { showEditCoverDialog = true },
                                label = { Text("Edit Cover", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                border = null,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentBook.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (currentBook.seriesDisplay().isNotBlank()) {
                                Text(
                                    text = currentBook.seriesDisplay(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            // Status line: Format · Progress
                            val statusText = when (currentBook.readingState()) {
                                LocalBookReadingState.NOT_STARTED -> "Not Started"
                                LocalBookReadingState.READING -> "${(currentBook.progressFraction * 100).roundToInt()}% read"
                                LocalBookReadingState.FINISHED -> "Completed"
                            }
                            Text(
                                text = "${currentBook.format.displayName} · $statusText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            // Under pages & percentage read
                            if (currentBook.author.isNotBlank()) {
                                Text(
                                    text = "Author: ${currentBook.author}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }

                            if (currentBook.formattedSize.isNotBlank()) {
                                Text(
                                    text = "File size: ${currentBook.formattedSize}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    val primaryActionText = when (currentBook.readingState()) {
                        LocalBookReadingState.NOT_STARTED -> "Start Reading"
                        LocalBookReadingState.READING -> "Continue Reading"
                        LocalBookReadingState.FINISHED -> "Read Again"
                    }
                    BookQuickActions(
                        primaryActionText = primaryActionText,
                        onPrimaryAction = ::openForReading,
                        isWantToRead = currentBook.isWantToRead,
                        onToggleWantToRead = {
                            scope.launch {
                                val next = !currentBook.isWantToRead
                                localRepository.toggleBookWantToRead(currentBook.id, next)
                                snackbarHostState.showSnackbar(if (next) "Added to Want to Read" else "Removed from Want to Read")
                            }
                        },
                        isFinished = currentBook.isCompleted,
                        onToggleFinished = {
                            scope.launch {
                                val next = !currentBook.isCompleted
                                localRepository.markCompleted(currentBook.id, next)
                                snackbarHostState.showSnackbar(if (next) "Marked as finished" else "Marked as unread")
                            }
                        },
                        onEdit = { showEditDialog = true },
                    )

                    // Sibling Issues / Specials / Volumes (Placed above Rating Section)
                    OfflineIssuesSection(
                        currentBook = currentBook,
                        allBooks = allBooks,
                        onSelectBook = { targetBook ->
                            activeBookId = targetBook.id
                            onSelectBook?.invoke(targetBook)
                        }
                    )

                    // Interactive Rating
                    BookRatingRow(
                        rating = currentBook.rating,
                        onRatingChange = { newRating ->
                            scope.launch {
                                localRepository.updateBookRating(currentBook.id, newRating)
                            }
                        },
                        promptToRate = currentBook.isCompleted
                    )

                    // Tags
                    if (currentBook.tags().isNotEmpty()) {
                        BookTagsRow(
                            tags = currentBook.tags(),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Reading Stats Card
            item {
                ReadingStatsCard(
                    book = currentBook,
                    finished = currentBook.readingState() == LocalBookReadingState.FINISHED,
                    onEditStarted = { editingReadingDate = "started" },
                    onEditFinished = { editingReadingDate = "finished" },
                )
            }

            // About Book / Description Section
            item {
                BookDetailSection(
                    icon = Icons.Outlined.Description,
                    title = "About this book",
                    action = {
                        IconButton(onClick = { showEditDescriptionDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Description",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                ) {
                    if (currentBook.description.isNotBlank()) {
                        ScrollableDescription(text = currentBook.description)
                    } else {
                        Text(
                            text = "No description available. Tap the edit icon to add synopsis.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    val footer = buildList {
                        if (currentBook.lastModified > 0L) {
                            add("Added on ${currentBook.lastModified.formatDate()}")
                        }
                        if (currentBook.lastReadTime > 0L) {
                            add("Last opened ${currentBook.lastReadTime.formatDate()}")
                        }
                        if (currentBook.folderName.isNotBlank()) {
                            add("Folder: ${currentBook.folderName}")
                        }
                    }
                    Text(
                        text = footer.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // File & Technical Details Section
            item {
                BookDetailSection(
                    icon = Icons.Outlined.Info,
                    title = "File Information",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FileDetailRow(label = "Format", value = "${currentBook.format.displayName} (${currentBook.extension.uppercase()})")
                        if (currentBook.pageCount > 0) {
                            FileDetailRow(label = "Page Count", value = "${currentBook.pageCount} pages")
                        }
                        if (currentBook.formattedSize.isNotBlank()) {
                            FileDetailRow(label = "File Size", value = currentBook.formattedSize)
                        }
                        if (currentBook.folderName.isNotBlank()) {
                            FileDetailRow(label = "Library Folder", value = currentBook.folderName)
                        }
                        FileDetailRow(
                            label = "Comic / Manga Strip",
                            value = if (currentBook.format.isComic) "Archive Archive (${currentBook.format.displayName})" else if (currentBook.format.isReflowEbook) "Reflowable eBook" else "Document"
                        )
                    }
                }
            }

            // Bottom space for scrolling & FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Dialogs
    if (showCoverPreview) {
        CoverPreviewDialog(
            coverPath = currentBook.coverPath,
            title = currentBook.title,
            onDismiss = { showCoverPreview = false }
        )
    }

    if (showEditDialog) {
        EditMetadataDialog(
            book = currentBook,
            allBooks = allBooks,
            onDismiss = { showEditDialog = false },
            onSave = { title, author, series, volumeOrIssue, tagsCsv ->
                showEditDialog = false
                scope.launch {
                    localRepository.updateBookMetadata(currentBook.id, title, author, series, volumeOrIssue, currentBook.description, tagsCsv)
                    snackbarHostState.showSnackbar("Metadata saved")
                }
            }
        )
    }

    if (showEditDescriptionDialog) {
        EditDescriptionDialog(
            description = currentBook.description,
            onDismiss = { showEditDescriptionDialog = false },
            onSave = { newDesc ->
                showEditDescriptionDialog = false
                scope.launch {
                    localRepository.updateBookMetadata(currentBook.id, currentBook.title, currentBook.author, currentBook.seriesName, currentBook.volumeOrIssue, newDesc, currentBook.tagsCsv)
                    snackbarHostState.showSnackbar("Description updated")
                }
            }
        )
    }

    if (showEditCoverDialog) {
        EditCoverDialog(
            book = currentBook,
            onChangeCover = {
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                showEditCoverDialog = false
            },
            onRemoveCover = {
                showEditCoverDialog = false
                scope.launch {
                    localRepository.removeBookCover(currentBook.id)
                    snackbarHostState.showSnackbar("Cover removed")
                }
            },
            onDismiss = { showEditCoverDialog = false }
        )
    }

    if (showShareDialog) {
        val statusText = when (currentBook.readingState()) {
            LocalBookReadingState.NOT_STARTED -> "Not started"
            LocalBookReadingState.READING -> "${(currentBook.progressFraction * 100).roundToInt()}% read"
            LocalBookReadingState.FINISHED -> "Finished"
        }
        ShareCardDialog(
            bookTitle = currentBook.title,
            author = currentBook.author,
            seriesDisplay = currentBook.seriesDisplay(),
            readingPercent = currentBook.progressFraction,
            statusText = statusText,
            rating = currentBook.rating,
            tags = currentBook.tags(),
            coverPath = currentBook.coverPath,
            onDismiss = { showShareDialog = false }
        )
    }

    if (showDeleteDialog) {
        DeleteBookChoiceDialog(
            bookTitle = currentBook.title,
            onDismissRequest = { showDeleteDialog = false },
            onConfirmDelete = {
                showDeleteDialog = false
                scope.launch {
                    localRepository.deleteBook(currentBook.id)
                    onBack()
                }
            }
        )
    }

    editingReadingDate?.let { field ->
        val isStarted = field == "started"
        val currentDate = if (isStarted) {
            if (currentBook.startedReadingAt > 0L) currentBook.startedReadingAt else currentBook.lastReadTime
        } else {
            if (currentBook.finishedReadingAt > 0L) currentBook.finishedReadingAt else System.currentTimeMillis()
        }
        ReadingDatePickerDialog(
            title = if (isStarted) "Edit Started Date" else "Edit Finished Date",
            initialDate = currentDate,
            onConfirm = { pickedDate ->
                editingReadingDate = null
                scope.launch {
                    if (isStarted) {
                        localRepository.updateBookReadingDates(currentBook.id, startedAt = pickedDate, finishedAt = if (currentBook.finishedReadingAt > 0L) currentBook.finishedReadingAt else null)
                    } else {
                        localRepository.updateBookReadingDates(currentBook.id, startedAt = if (currentBook.startedReadingAt > 0L) currentBook.startedReadingAt else null, finishedAt = pickedDate)
                    }
                    snackbarHostState.showSnackbar("Reading date updated")
                }
            },
            onDismiss = { editingReadingDate = null }
        )
    }
}

@Composable
private fun FileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private suspend fun saveCustomCover(context: Context, bookId: String, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val coversDir = File(context.filesDir, "custom_covers").apply { mkdirs() }
        val targetFile = File(coversDir, "$bookId.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        if (targetFile.exists() && targetFile.length() > 0) targetFile else null
    } catch (t: Throwable) {
        null
    }
}

@Composable
fun OfflineIssuesSection(
    currentBook: LocalBook,
    allBooks: List<LocalBook>,
    onSelectBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val siblingBooks = remember(currentBook, allBooks) {
        findRelatedOfflineBooks(currentBook, allBooks)
    }

    if (siblingBooks.size <= 1) return

    val (specialBooks, regularBooks) = remember(siblingBooks) {
        siblingBooks.partition { isOfflineSpecialBook(it) }
    }

    var selectedTab by remember(regularBooks.isEmpty(), specialBooks.isEmpty()) {
        mutableStateOf(if (regularBooks.isNotEmpty()) 0 else 1)
    }

    val activeList = when {
        selectedTab == 0 && regularBooks.isNotEmpty() -> regularBooks
        selectedTab == 1 && specialBooks.isNotEmpty() -> specialBooks
        regularBooks.isNotEmpty() -> regularBooks
        else -> specialBooks
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (regularBooks.isNotEmpty() && specialBooks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = {
                        Text(
                            "Issues (${regularBooks.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = {
                        Text(
                            "Specials (${specialBooks.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        } else {
            val title = if (regularBooks.isNotEmpty()) "Issues (${regularBooks.size})" else "Specials (${specialBooks.size})"
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(activeList.distinctBy { it.id }, key = { it.id }) { item ->
                val isCurrent = item.id == currentBook.id
                val displayTitle = if (item.volumeOrIssue.isNotBlank()) "Issue #${item.volumeOrIssue}" else item.title
                val mediaItem = remember(item, displayTitle) {
                    item.toUnifiedMediaItem(displayTitle = displayTitle)
                }
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .then(
                            if (isCurrent) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            } else {
                                Modifier.padding(4.dp)
                            }
                        )
                ) {
                    UnifiedPosterCard(
                        item = mediaItem,
                        onClick = { onSelectBook(item) }
                    )
                }
            }
        }
    }
}

private fun findRelatedOfflineBooks(currentBook: LocalBook, allBooks: List<LocalBook>): List<LocalBook> {
    val distinctBooks = allBooks.distinctBy { it.id }.distinctBy { it.uriString.ifBlank { it.id } }

    // 1. Explicit seriesName match
    if (currentBook.seriesName.isNotBlank()) {
        val series = currentBook.seriesName.trim()
        val matches = distinctBooks.filter { 
            it.seriesName.isNotBlank() && it.seriesName.trim().equals(series, ignoreCase = true) 
        }
        if (matches.size > 1) {
            return matches.distinctBy { it.id }.sortedWith(
                compareBy<LocalBook> { it.volumeOrIssue.toIntOrNull() ?: extractTrailingNumber(it.title) ?: Int.MAX_VALUE }
                    .thenBy { it.title }
            )
        }
    }

    // 2. Similar title / series base match
    val baseTitle = extractSeriesBaseTitle(currentBook.title)
    if (baseTitle.isNotBlank() && baseTitle.length >= 3) {
        val matches = distinctBooks.filter { book ->
            val otherBase = extractSeriesBaseTitle(book.title)
            otherBase.isNotBlank() && otherBase.equals(baseTitle, ignoreCase = true)
        }
        if (matches.size > 1) {
            return matches.distinctBy { it.id }.sortedWith(
                compareBy<LocalBook> { it.volumeOrIssue.toIntOrNull() ?: extractTrailingNumber(it.title) ?: Int.MAX_VALUE }
                    .thenBy { it.title }
            )
        }
    }

    return emptyList()
}

private fun extractSeriesBaseTitle(title: String): String {
    var s = title.trim()
    s = s.substringBeforeLast('.')
    // Remove volume / chapter / issue prefixes and trailing numbers
    s = s.replace(Regex("(?i)\\s*[\\[\\(]?(?:vol(?:ume)?|v|ch(?:apter)?|issue|#)\\s*\\.?\\s*\\d+.*$"), "")
    s = s.replace(Regex("\\s*-\\s*\\d+\\s*$"), "")
    s = s.replace(Regex("\\s+\\d+\\s*$"), "")
    return s.trim()
}

private fun extractTrailingNumber(title: String): Int? {
    val match = Regex("(?:vol(?:ume)?|v|ch(?:apter)?|issue|#|-)?\\s*\\.?\\s*(\\d+)", RegexOption.IGNORE_CASE).findAll(title).lastOrNull()
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private fun isOfflineSpecialBook(book: LocalBook): Boolean {
    val vol = book.volumeOrIssue.trim()
    val title = book.title.trim()
    val specialRegex = Regex("(?i)\\b(special|extra|omake|one-?shot|bonus|side-?story|gaiden)\\b")

    if (vol.isNotBlank()) {
        if (specialRegex.containsMatchIn(vol)) return true
        if (vol.startsWith("SP", ignoreCase = true)) return true
        if (vol.startsWith("EX", ignoreCase = true)) return true
        if (vol.startsWith("EXTRA", ignoreCase = true)) return true
    }

    if (specialRegex.containsMatchIn(title)) {
        return true
    }

    return false
}

