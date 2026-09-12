package com.bunko.reader.library.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.offline.LocalBookFormat
import com.bunko.reader.ui.KavitaCoverAspectRatio
import com.bunko.reader.ui.browse.PosterGrid
import com.bunko.reader.ui.theme.ReadingProgressInProgress
import com.bunko.reader.ui.theme.ReadingProgressTrack
import java.io.File
import kotlin.math.absoluteValue

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
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        EmptyLibraryState(
            hasBooksOverall = false,
            onChooseFolder = onChangeFolder,
            onRescan = onRescan
        )
        return
    }

    val continueReading = remember(books) {
        books.filter { it.lastReadPage > 0 && !it.isCompleted }
            .sortedByDescending { it.lastReadPage }
    }
    val recentlyAdded = remember(books) {
        books.sortedByDescending { it.lastModified }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (continueReading.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Continue Reading",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${continueReading.size}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (isGridView) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueReading, key = { "cr_${it.id}" }) { book ->
                            Box(Modifier.width(135.dp)) {
                                LocalBookPosterCard(
                                    book = book,
                                    onClick = { onOpenBook(book) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            } else {
                items(continueReading, key = { "cr_${it.id}" }) { book ->
                    LocalBookListItem(
                        book = book,
                        onClick = { onOpenBook(book) }
                    )
                }
                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently Added",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See all (${books.size})",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onSeeAll)
                )
            }
        }
        if (isGridView) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentlyAdded.take(20), key = { "ra_${it.id}" }) { book ->
                        Box(Modifier.width(135.dp)) {
                            LocalBookPosterCard(
                                book = book,
                                onClick = { onOpenBook(book) }
                            )
                        }
                    }
                }
            }
        } else {
            items(recentlyAdded.take(20), key = { "ra_${it.id}" }) { book ->
                LocalBookListItem(
                    book = book,
                    onClick = { onOpenBook(book) }
                )
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
    val sortedBooks = remember(books, sort) {
        books.sortedWith { left, right ->
            when (sort) {
                LocalBookSort.Title -> left.title.lowercase().compareTo(right.title.lowercase())
                LocalBookSort.Recent -> right.lastReadPage.compareTo(left.lastReadPage)
                LocalBookSort.Size -> right.sizeBytes.compareTo(left.sizeBytes)
                LocalBookSort.Modified -> right.lastModified.compareTo(left.lastModified)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (books.isEmpty()) {
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
                LocalBookPosterCard(
                    book = book,
                    onClick = { onOpenBook(book) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(sortedBooks, key = { it.id }) { book ->
                    LocalBookListItem(
                        book = book,
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
    modifier: Modifier = Modifier
) {
    val searchResults = remember(books, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else books.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                    LocalBookPosterCard(book = book, onClick = { onOpenBook(book) })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(searchResults, key = { it.id }) { book ->
                        LocalBookListItem(book = book, onClick = { onOpenBook(book) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfflineLibrariesPane(
    folderName: String?,
    books: List<LocalBook>,
    isScanning: Boolean,
    isGridView: Boolean = true,
    onChangeFolder: () -> Unit,
    onRescan: () -> Unit,
    onOpenBook: (LocalBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val comicBooks = remember(books) { books.filter { it.format.isComic } }
    val epubBooks = remember(books) { books.filter { it.format.isEpub } }
    val pdfBooks = remember(books) { books.filter { it.format.isPdf } }

    var selectedFormatFilter by remember { mutableStateOf<LocalBookFormat?>(null) }
    val displayedBooks = remember(books, selectedFormatFilter) {
        if (selectedFormatFilter == null) emptyList()
        else books.filter { it.format == selectedFormatFilter }
    }

    if (selectedFormatFilter != null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "← Back to Folders",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { selectedFormatFilter = null }
                )
                Text(
                    text = "• ${selectedFormatFilter?.displayName} (${displayedBooks.size})",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isGridView) {
                PosterGrid(
                    items = displayedBooks,
                    key = { it.id }
                ) { book ->
                    LocalBookPosterCard(book = book, onClick = { onOpenBook(book) })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayedBooks, key = { it.id }) { book ->
                        LocalBookListItem(
                            book = book,
                            onClick = { onOpenBook(book) }
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = folderName ?: "Default Storage",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${books.size} total books found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onChangeFolder,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Change Folder")
                        }
                        Button(
                            onClick = onRescan,
                            enabled = !isScanning,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isScanning) "Scanning..." else "Rescan")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Format Libraries",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            FormatLibraryCard(
                title = "eBooks",
                subtitle = "EPUB publications",
                count = epubBooks.size,
                icon = Icons.Filled.AutoStories,
                onClick = { selectedFormatFilter = LocalBookFormat.EPUB }
            )
        }

        item {
            FormatLibraryCard(
                title = "Comics & Manga",
                subtitle = "CBZ and ZIP archives",
                count = comicBooks.size,
                icon = Icons.Filled.Folder,
                onClick = { selectedFormatFilter = LocalBookFormat.CBZ }
            )
        }

        item {
            FormatLibraryCard(
                title = "PDF Documents",
                subtitle = "Portable Document Format",
                count = pdfBooks.size,
                icon = Icons.Filled.PictureAsPdf,
                onClick = { selectedFormatFilter = LocalBookFormat.PDF }
            )
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
    val unreadBooks = remember(books) { books.filter { it.lastReadPage == 0 && !it.isCompleted } }

    Box(modifier = modifier.fillMaxSize()) {
        if (unreadBooks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (books.isEmpty()) "No books in library" else "All caught up! You have started all your books.",
                    color = Color(0xFFB9BDBD),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (isGridView) {
            PosterGrid(
                items = unreadBooks,
                key = { it.id }
            ) { book ->
                LocalBookPosterCard(
                    book = book,
                    onClick = { onOpenBook(book) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(unreadBooks, key = { it.id }) { book ->
                    LocalBookListItem(
                        book = book,
                        onClick = { onOpenBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatLibraryCard(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "$count",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
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
                .clip(RoundedCornerShape(12.dp))
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

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = book.format.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }

            if (book.lastReadPage > 0) {
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = ReadingProgressInProgress,
                    trackColor = ReadingProgressTrack
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 6.dp)
        ) {
            Text(
                text = book.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
                    "Choose a folder containing CBZ, ZIP, EPUB, or PDF files to get started."
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
