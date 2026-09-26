package com.bunko.reader.library.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.bunko.reader.offline.LocalBook
import com.bunko.reader.ui.KavitaCoverAspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date

/**
 * Preview dialog for full-size book cover.
 */
@Composable
fun CoverPreviewDialog(
    coverPath: String,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(KavitaCoverAspectRatio)
                ) {
                    if (coverPath.isNotBlank()) {
                        AsyncImage(
                            model = coverPath,
                            contentDescription = title,
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
                                text = title.take(2).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Dialog for editing book metadata (Title, Author, Series, Volume, Tags).
 */
@Composable
fun EditMetadataDialog(
    book: LocalBook,
    allBooks: List<LocalBook>,
    onDismiss: () -> Unit,
    onSave: (title: String, author: String, series: String, volumeOrIssue: String, tagsCsv: String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var seriesName by remember(book.id) { mutableStateOf(book.seriesName) }
    var volumeOrIssue by remember(book.id) { mutableStateOf(book.volumeOrIssue) }
    var tagsCsv by remember(book.id) { mutableStateOf(book.tagsCsv) }

    val authorSuggestions = remember(allBooks) {
        allBooks.mapNotNull { it.author.trim().takeIf { a -> a.isNotEmpty() } }.distinct().sorted()
    }
    val seriesSuggestions = remember(allBooks) {
        allBooks.mapNotNull { it.seriesName.trim().takeIf { s -> s.isNotEmpty() } }.distinct().sorted()
    }
    val tagSuggestions = remember(allBooks) {
        allBooks.flatMap { it.tags() }.distinct().sorted()
    }

    val canSave = title.isNotBlank()
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Edit Metadata",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                    isError = !canSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )

                MetadataSuggestionField(
                    value = author,
                    onValueChange = { author = it },
                    suggestions = authorSuggestions,
                    label = "Author",
                    leadingIcon = Icons.Outlined.EditNote,
                    textFieldColors = textFieldColors,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetadataSuggestionField(
                            value = seriesName,
                            onValueChange = { seriesName = it },
                            suggestions = seriesSuggestions,
                            label = "Series Name",
                            leadingIcon = Icons.Outlined.Category,
                            textFieldColors = textFieldColors,
                        )

                        OutlinedTextField(
                            value = volumeOrIssue,
                            onValueChange = { volumeOrIssue = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Volume / Issue #") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = textFieldColors,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        )
                    }
                }

                TagSuggestionField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    suggestions = tagSuggestions,
                    textFieldColors = textFieldColors,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(title, author, seriesName, volumeOrIssue, tagsCsv) },
                        enabled = canSave,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing the full description of a book.
 */
@Composable
fun EditDescriptionDialog(
    description: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(description) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Edit Description",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp),
                    label = { Text("Synopsis / Summary") },
                    minLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(text) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text("Save")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for editing or replacing the book cover image.
 */
@Composable
fun EditCoverDialog(
    book: LocalBook,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Edit Book Cover",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(KavitaCoverAspectRatio)
                ) {
                    if (book.hasCover) {
                        AsyncImage(
                            model = book.coverPath,
                            contentDescription = book.title,
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
                                text = book.title.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onRemoveCover,
                        enabled = book.hasCover,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove")
                    }
                    Button(
                        onClick = onChangeCover,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change")
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * Dialog for editing Kavita series cover (Upload custom cover or Reset to server default).
 */
@Composable
fun KavitaEditCoverDialog(
    seriesName: String,
    coverUrl: String,
    onChangeCover: () -> Unit,
    onResetCover: () -> Unit,
    onDismiss: () -> Unit,
    isProcessing: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Edit Series Cover",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(KavitaCoverAspectRatio)
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = seriesName,
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
                                text = seriesName.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onResetCover,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset")
                    }
                    Button(
                        onClick = onChangeCover,
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isProcessing) "Uploading…" else "Change")
                    }
                }

                Button(
                    onClick = onDismiss,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

/**
 * Social Share Card Dialog (matching Vayana style).
 */
@Composable
fun ShareCardDialog(
    bookTitle: String,
    author: String,
    seriesDisplay: String,
    readingPercent: Float,
    statusText: String,
    rating: Float,
    tags: List<String>,
    coverPath: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLayer = rememberGraphicsLayer()
    var isCapturing by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(true) }

    fun shareImage() {
        isCapturing = true
        scope.launch {
            try {
                val captured = exportLayer.toImageBitmap().asAndroidBitmap()
                val bitmap = if (captured.config == Bitmap.Config.HARDWARE) {
                    captured.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    captured
                }
                context.shareCardBitmap(bitmap, "Share $bookTitle")
                onDismiss()
            } finally {
                isCapturing = false
            }
        }
    }

    fun shareText() {
        val shareText = buildString {
            append("📖 ").append(bookTitle)
            if (author.isNotBlank()) append(" by ").append(author)
            if (seriesDisplay.isNotBlank()) append(" (").append(seriesDisplay).append(")")
            append("\nStatus: ").append(statusText)
            if (rating > 0f) append(" · Rating: ").append(rating).append("★")
            append("\nRead with Bunko")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Book"))
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Share Book",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                )

                // The capture card container
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .drawWithContent {
                            val exportSize = 1080
                            val exportScale = exportSize / size.width
                            exportLayer.record(size = IntSize(exportSize, exportSize)) {
                                scale(exportScale, pivot = Offset.Zero) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            drawContent()
                        },
                ) {
                    BookShareCardPreview(
                        title = bookTitle,
                        author = author,
                        seriesDisplay = seriesDisplay,
                        readingPercent = readingPercent,
                        statusText = statusText,
                        rating = rating,
                        tags = tags,
                        coverPath = coverPath,
                        isDark = isDarkTheme
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { isDarkTheme = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (!isDarkTheme) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Light", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { isDarkTheme = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isDarkTheme) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Dark", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = ::shareText,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShortText,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Text")
                    }
                    Button(
                        onClick = ::shareImage,
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Card")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookShareCardPreview(
    title: String,
    author: String,
    seriesDisplay: String,
    readingPercent: Float,
    statusText: String,
    rating: Float,
    tags: List<String>,
    coverPath: String,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF141218) else Color(0xFFFBF8FF)
    val cardSurface = if (isDark) Color(0xFF211F26) else Color(0xFFEFEBF3)
    val onCard = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B20)
    val onCardVariant = if (isDark) Color(0xFFCAC4D0) else Color(0xFF49454F)
    val primaryColor = if (isDark) Color(0xFFD0BCFF) else Color(0xFF6750A4)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        color = cardBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardSurface,
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(KavitaCoverAspectRatio)
                ) {
                    if (coverPath.isNotBlank()) {
                        AsyncImage(
                            model = coverPath,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = onCardVariant,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onCard,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (author.isNotBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = onCardVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (seriesDisplay.isNotBlank()) {
                        Text(
                            text = seriesDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = onCardVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Progress", style = MaterialTheme.typography.labelSmall, color = onCardVariant)
                        Text(
                            text = "${(readingPercent * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onCard
                        )
                    }
                    if (rating > 0f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rating", style = MaterialTheme.typography.labelSmall, color = onCardVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$rating",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onCard
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = StarGoldColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bunko Reader",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(
                    text = System.currentTimeMillis().formatDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onCardVariant
                )
            }
        }
    }
}

private suspend fun Context.shareCardBitmap(bitmap: Bitmap, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "bunko_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        FileProvider.getUriForFile(this@shareCardBitmap, "$packageName.provider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, chooserTitle))
}

@Composable
fun DeleteBookChoiceDialog(
    bookTitle: String,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Delete Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Are you sure you want to remove \"$bookTitle\" from your library?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    FilledTonalButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onConfirmDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

/**
 * Material date picker dialog for setting started / finished reading dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDatePickerDialog(
    title: String,
    initialDate: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (initialDate > 0L) initialDate else System.currentTimeMillis()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onConfirm(it) }
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
            }
        )
    }
}

@Composable
private fun MetadataSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    leadingIcon: ImageVector,
    textFieldColors: androidx.compose.material3.TextFieldColors,
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = remember(value, suggestions) {
        if (value.isBlank()) emptyList() else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(5)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    expanded = it.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                leadingIcon = {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange(""); expanded = false }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear",
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            DropdownMenu(
                expanded = expanded && matches.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    textFieldColors: androidx.compose.material3.TextFieldColors,
) {
    val lastTag = value.substringAfterLast(',').trim()
    val matches = remember(lastTag, suggestions) {
        if (lastTag.isBlank()) emptyList() else suggestions.filter { it.contains(lastTag, ignoreCase = true) && !it.equals(lastTag, ignoreCase = true) }.take(6)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tags (comma-separated)") },
            supportingText = { Text("e.g. Manga, Shonen, Action") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )

        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                matches.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            val before = value.substringBeforeLast(',', missingDelimiterValue = "")
                            val newValue = if (before.isNotBlank()) "$before, $suggestion, " else "$suggestion, "
                            onValueChange(newValue)
                        },
                        label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}
