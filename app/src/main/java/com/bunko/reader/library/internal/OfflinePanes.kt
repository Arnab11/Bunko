package com.bunko.reader.library.internal

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookFormat
import com.bunko.reader.offline.LocalFolder
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.CoverProgressBadge
import com.bunko.reader.ui.browse.PosterGrid
import java.io.File
import kotlin.math.absoluteValue

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.TextButton
import com.bunko.reader.ui.browse.UnifiedPosterCard
import com.bunko.reader.ui.browse.UnifiedListItem
import com.bunko.reader.ui.browse.toUnifiedMediaItem

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

enum class LocalBookSort(val label: String) {
    Title("Title"),
    Recent("Recently Read"),
    Size("File Size"),
    Modified("Date Modified")
}

@Composable
internal fun OfflineHomePane(
    books: List<LocalBook>,
    isGridView: Boolean = true,
    onOpenBook: (LocalBook) -> Unit,
    onSeeAll: () -> Unit,
    onOpenContinueReading: () -> Unit = onSeeAll,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryBooks = remember(books) {
        books.filter { !it.isExternalFile }
    }

    if (books.isEmpty()) {
        EmptyLibraryState(
            hasBooksOverall = false,
            onChooseFolder = onChangeFolder,
            onRescan = onRescan
        )
        return
    }

    val continueReading = remember(books) {
        books.filter { (it.lastReadPage > 0 || it.lastReadTime > 0L) && !it.isCompleted }
            .sortedWith { a, b ->
                val timeA = if (a.lastReadTime > 0L) a.lastReadTime else a.lastModified
                val timeB = if (b.lastReadTime > 0L) b.lastReadTime else b.lastModified
                timeB.compareTo(timeA)
            }
    }
    val recentlyAdded = remember(libraryBooks) {
        libraryBooks.sortedByDescending { it.lastModified }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (continueReading.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenContinueReading() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Continue Reading",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${continueReading.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "See all",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Continue Reading",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isGridView) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val minCardWidth = 130.dp
                            val columns = maxOf(2, (maxWidth / minCardWidth).toInt())
                            val maxItems = columns * 2
                            val previewItems = continueReading.take(maxItems)
                            val chunked = previewItems.chunked(columns)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                chunked.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { book ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                UnifiedPosterCard(
                                                    item = book.toUnifiedMediaItem(),
                                                    onClick = { onOpenBook(book) }
                                                )
                                            }
                                        }
                                        repeat(columns - rowItems.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            continueReading.take(5).forEach { book ->
                                UnifiedListItem(
                                    item = book.toUnifiedMediaItem(),
                                    onClick = { onOpenBook(book) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeeAll() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recently Added",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${libraryBooks.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "See all",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Recently Added",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (isGridView) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val minCardWidth = 130.dp
                        val columns = maxOf(2, (maxWidth / minCardWidth).toInt())
                        val maxItems = columns * 2
                        val previewItems = recentlyAdded.take(maxItems)
                        val chunked = previewItems.chunked(columns)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            chunked.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { book ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            UnifiedPosterCard(
                                                item = book.toUnifiedMediaItem(),
                                                onClick = { onOpenBook(book) }
                                            )
                                        }
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        recentlyAdded.take(5).forEach { book ->
                            UnifiedListItem(
                                item = book.toUnifiedMediaItem(),
                                onClick = { onOpenBook(book) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfflineHistoryPane(
    books: List<LocalBook>,
    sort: LocalBookSort = LocalBookSort.Recent,
    isGridView: Boolean = true,
    onOpenBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryHistory = remember(books, sort) {
        val libraryOnly = books.filter { !it.isExternalFile && (it.lastReadPage > 0 || it.lastReadTime > 0L) }
        val inProgress = libraryOnly.filter { !it.isCompleted }
        val targetList = if (inProgress.isNotEmpty()) inProgress else libraryOnly
        when (sort) {
            LocalBookSort.Title -> targetList.sortedBy { it.title.lowercase() }
            LocalBookSort.Recent -> targetList.sortedWith { a, b ->
                val timeA = if (a.lastReadTime > 0L) a.lastReadTime else a.lastModified
                val timeB = if (b.lastReadTime > 0L) b.lastReadTime else b.lastModified
                timeB.compareTo(timeA)
            }
            LocalBookSort.Modified -> targetList.sortedByDescending { it.lastModified }
            LocalBookSort.Size -> targetList.sortedByDescending { it.sizeBytes }
        }
    }

    val openedFiles = remember(books, sort) {
        val externalList = books.filter { it.isExternalFile && (it.lastReadPage > 0 || it.lastReadTime > 0L) }
        when (sort) {
            LocalBookSort.Title -> externalList.sortedBy { it.title.lowercase() }
            LocalBookSort.Recent -> externalList.sortedWith { a, b ->
                val timeA = if (a.lastReadTime > 0L) a.lastReadTime else a.lastModified
                val timeB = if (b.lastReadTime > 0L) b.lastReadTime else b.lastModified
                timeB.compareTo(timeA)
            }
            LocalBookSort.Modified -> externalList.sortedByDescending { it.lastModified }
            LocalBookSort.Size -> externalList.sortedByDescending { it.sizeBytes }
        }
    }

    if (libraryHistory.isEmpty() && openedFiles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Reading History",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Books you start reading or open from files will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (openedFiles.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Opened from External Source",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${openedFiles.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isGridView) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val minCardWidth = 130.dp
                                val columns = maxOf(2, (maxWidth / minCardWidth).toInt())
                                val chunked = openedFiles.chunked(columns)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    chunked.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { book ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    UnifiedPosterCard(
                                                        item = book.toUnifiedMediaItem(),
                                                        onClick = { onOpenBook(book) }
                                                    )
                                                }
                                            }
                                            repeat(columns - rowItems.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                openedFiles.forEach { book ->
                                    UnifiedListItem(
                                        item = book.toUnifiedMediaItem(),
                                        onClick = { onOpenBook(book) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (libraryHistory.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Library History",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${libraryHistory.size}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (isGridView) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val minCardWidth = 130.dp
                                val columns = maxOf(2, (maxWidth / minCardWidth).toInt())
                                val chunked = libraryHistory.chunked(columns)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    chunked.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { book ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    UnifiedPosterCard(
                                                        item = book.toUnifiedMediaItem(),
                                                        onClick = { onOpenBook(book) }
                                                    )
                                                }
                                            }
                                            repeat(columns - rowItems.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                libraryHistory.forEach { book ->
                                    UnifiedListItem(
                                        item = book.toUnifiedMediaItem(),
                                        onClick = { onOpenBook(book) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfflineBrowsePane(
    books: List<LocalBook>,
    sort: LocalBookSort,
    isGridView: Boolean,
    onOpenBook: (LocalBook) -> Unit,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryBooks = remember(books) { books.filter { !it.isExternalFile } }
    val sortedBooks = remember(libraryBooks, sort) {
        libraryBooks.sortedWith { left, right ->
            when (sort) {
                LocalBookSort.Title -> left.title.lowercase().compareTo(right.title.lowercase())
                LocalBookSort.Recent -> {
                    val timeA = if (left.lastReadTime > 0L) left.lastReadTime else if (left.lastReadPage > 0) left.lastModified else 0L
                    val timeB = if (right.lastReadTime > 0L) right.lastReadTime else if (right.lastReadPage > 0) right.lastModified else 0L
                    timeB.compareTo(timeA)
                }
                LocalBookSort.Size -> right.sizeBytes.compareTo(left.sizeBytes)
                LocalBookSort.Modified -> right.lastModified.compareTo(left.lastModified)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (libraryBooks.isEmpty()) {
            EmptyLibraryState(
                hasBooksOverall = false,
                onChooseFolder = onChangeFolder,
                onRescan = onRescan
            )
        } else if (isGridView) {
            PosterGrid(
                items = sortedBooks,
                key = { it.id }
            ) { book ->
                UnifiedPosterCard(
                    item = book.toUnifiedMediaItem(),
                    onClick = { onOpenBook(book) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedBooks, key = { it.id }) { book ->
                    UnifiedListItem(
                        item = book.toUnifiedMediaItem(),
                        onClick = { onOpenBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun OfflineSearchPane(
    books: List<LocalBook>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isGridView: Boolean,
    onOpenBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier,
    // Inline mode: the query field lives in the top bar.
    showSearchField: Boolean = true
) {
    val searchResults = remember(books, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else books.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showSearchField) {
            OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search books & comics...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
            )
        }

        if (searchQuery.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Type to search your offline library",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (searchResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No books found matching \"$searchQuery\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Text(
                text = "${searchResults.size} results",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (isGridView) {
                PosterGrid(
                    items = searchResults,
                    key = { it.id }
                ) { book ->
                    UnifiedPosterCard(
                        item = book.toUnifiedMediaItem(),
                        onClick = { onOpenBook(book) }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(searchResults, key = { it.id }) { book ->
                        UnifiedListItem(
                            item = book.toUnifiedMediaItem(),
                            onClick = { onOpenBook(book) }
                        )
                    }
                }
            }
        }
    }
}

internal data class LocalBookStack(
    val key: String,
    val title: String,
    val books: List<LocalBook>
) {
    val isStack: Boolean get() = books.size > 1
    val primaryBook: LocalBook get() = books.first()
    val totalCount: Int get() = books.size
}

internal fun groupBooksIntoStacks(books: List<LocalBook>): List<LocalBookStack> {
    val groups = LinkedHashMap<String, MutableList<LocalBook>>()
    for (book in books) {
        val seriesKey = if (book.seriesName.isNotBlank()) {
            book.seriesName.trim().lowercase()
        } else {
            extractSeriesBaseTitle(book.title).lowercase()
        }
        groups.getOrPut(seriesKey) { mutableListOf() }.add(book)
    }

    return groups.map { (key, groupedBooks) ->
        val displayTitle = if (groupedBooks.first().seriesName.isNotBlank()) {
            groupedBooks.first().seriesName
        } else {
            extractSeriesBaseTitle(groupedBooks.first().title)
        }
        LocalBookStack(
            key = key,
            title = displayTitle,
            books = groupedBooks
        )
    }
}

internal fun extractSeriesBaseTitle(title: String): String {
    var cleaned = title
        .replace(Regex("""\s*[\(\[](?:vol(?:ume)?|ch(?:apter)?|issue|ep(?:isode)?|v|c|#)\s*\d+[\)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[\s_\-]+(?:vol(?:ume)?|ch(?:apter)?|issue|ep(?:isode)?|v|c|#)\s*\.?\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[\s_\-]+#?\d+\s*$"""), "")
        .trim()
    return if (cleaned.isBlank()) title else cleaned
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineLibrariesPane(
    folderName: String?,
    folders: List<LocalFolder> = emptyList(),
    books: List<LocalBook>,
    isScanning: Boolean,
    isGridView: Boolean = true,
    onChangeFolder: () -> Unit,
    onAddFolder: () -> Unit = onChangeFolder,
    onRescan: () -> Unit,
    onOpenBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryBooks = remember(books) { books.filter { !it.isExternalFile } }
    val bookStacks = remember(libraryBooks) { groupBooksIntoStacks(libraryBooks) }
    var selectedStackForSheet by remember { mutableStateOf<LocalBookStack?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (libraryBooks.isEmpty()) {
            EmptyLibraryState(
                hasBooksOverall = false,
                onChooseFolder = onChangeFolder,
                onRescan = onRescan
            )
        } else if (isGridView) {
            PosterGrid(
                items = bookStacks,
                key = { it.key }
            ) { stack ->
                val primaryBook = stack.primaryBook
                val mediaItem = primaryBook.toUnifiedMediaItem(
                    isStack = stack.isStack,
                    itemCount = stack.totalCount,
                    displayTitle = stack.title
                )
                UnifiedPosterCard(
                    item = mediaItem,
                    onClick = {
                        if (stack.isStack) {
                            selectedStackForSheet = stack
                        } else {
                            onOpenBook(primaryBook)
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bookStacks, key = { it.key }) { stack ->
                    val primaryBook = stack.primaryBook
                    val mediaItem = primaryBook.toUnifiedMediaItem(
                        isStack = stack.isStack,
                        itemCount = stack.totalCount,
                        displayTitle = stack.title
                    )
                    UnifiedListItem(
                        item = mediaItem,
                        onClick = {
                            if (stack.isStack) {
                                selectedStackForSheet = stack
                            } else {
                                onOpenBook(primaryBook)
                            }
                        }
                    )
                }
            }
        }

        if (selectedStackForSheet != null) {
            val currentStack = selectedStackForSheet!!
            ModalBottomSheet(
                onDismissRequest = { selectedStackForSheet = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = currentStack.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "${currentStack.totalCount} issues available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentStack.books, key = { it.id }) { book ->
                            UnifiedListItem(
                                item = book.toUnifiedMediaItem(),
                                onClick = {
                                    selectedStackForSheet = null
                                    onOpenBook(book)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfflineWantToReadPane(
    books: List<LocalBook>,
    isGridView: Boolean,
    onOpenBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryBooks = remember(books) { books.filter { !it.isExternalFile } }
    val unreadBooks = remember(libraryBooks) { libraryBooks.filter { it.lastReadPage == 0 && !it.isCompleted } }

    Box(modifier = modifier.fillMaxSize()) {
        if (unreadBooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (libraryBooks.isEmpty()) "No books in library" else "All caught up! You have started all your books.",
                    color = Color(0xFFB9BDBD),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (isGridView) {
            PosterGrid(
                items = unreadBooks,
                key = { it.id }
            ) { book ->
                UnifiedPosterCard(
                    item = book.toUnifiedMediaItem(),
                    onClick = { onOpenBook(book) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(unreadBooks, key = { it.id }) { book ->
                    UnifiedListItem(
                        item = book.toUnifiedMediaItem(),
                        onClick = { onOpenBook(book) }
                    )
                }
            }
        }
    }
}



@Composable
fun LocalBookPosterCard(
    book: LocalBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(KavitaCoverAspectRatio)
                .clip(RectangleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (book.hasCover) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DynamicCoverPlaceholder(book = book)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = book.format.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                if (book.isWebtoonBook) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "WEBTOON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (book.lastReadPage > 0 || book.isCompleted) {
                CoverProgressBadge(
                    progress = book.progressFraction,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = book.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book.lastReadPage > 0) {
                    Text(
                        text = if (book.isCompleted) "Completed" else "p. ${book.lastReadPage + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Text(
                    text = book.formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LocalBookListItem(
    book: LocalBook,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 114.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (book.hasCover) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DynamicCoverPlaceholder(book = book, isSmall = false)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = book.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = book.format.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (book.isWebtoonBook) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "WEBTOON",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = book.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (book.lastReadPage > 0) {
                        Text(
                            text = if (book.isCompleted) "• Completed" else "• Page ${book.lastReadPage + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicCoverPlaceholder(
    book: LocalBook,
    isSmall: Boolean = false
) {
    val hash = book.title.hashCode().absoluteValue
    val gradientColors = remember(hash) {
        val palettes = listOf(
            listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
            listOf(Color(0xFF0F2027), Color(0xFF203A43)),
            listOf(Color(0xFF3A1C71), Color(0xFFD76D77)),
            listOf(Color(0xFF134E5E), Color(0xFF71B280)),
            listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)),
            listOf(Color(0xFF3E5151), Color(0xFFDECBA4)),
            listOf(Color(0xFF200122), Color(0xFF6f0000)),
            listOf(Color(0xFF0575E6), Color(0xFF021B79))
        )
        palettes[hash % palettes.size]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(if (isSmall) 4.dp else 12.dp)
        ) {
            val icon = when (book.format) {
                LocalBookFormat.CBZ, LocalBookFormat.ZIP -> Icons.Filled.Folder
                LocalBookFormat.EPUB -> Icons.Filled.AutoStories
                LocalBookFormat.PDF -> Icons.Filled.PictureAsPdf
                else -> Icons.Filled.AutoStories
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(if (isSmall) 22.dp else 36.dp)
            )

            if (!isSmall) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = book.title,
                    color = Color.White.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryState(
    hasBooksOverall: Boolean,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = if (hasBooksOverall) "No matching books" else "No books found",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (hasBooksOverall) {
                    "Try adjusting your search query."
                } else {
                    "Choose a folder containing EPUB, MOBI, Comics, or PDF files to get started."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onChooseFolder,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose Folder")
                }

                if (hasBooksOverall) {
                    Button(
                        onClick = onRescan,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rescan")
                    }
                }
            }
        }
    }
}
