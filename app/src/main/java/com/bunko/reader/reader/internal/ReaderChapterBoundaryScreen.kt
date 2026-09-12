package com.bunko.reader.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunko.reader.reader.ReaderTurnDirection

internal data class ReaderChapterBoundary(
    val direction: ReaderTurnDirection,
    val current: ReaderChapterEntry,
    val neighbor: ReaderChapterEntry
)

/** Internal to reader, not for external use. */
@Composable
internal fun ReaderChapterBoundaryScreen(
    seriesName: String,
    boundary: ReaderChapterBoundary,
    switching: Boolean,
    hasError: Boolean,
    onContinue: () -> Unit,
    onBackToSeries: () -> Unit
) {
    val movingForward = boundary.direction == ReaderTurnDirection.Next
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (seriesName.isNotBlank()) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (movingForward) "Chapter complete" else "Start of chapter",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            ChapterBoundaryName(
                label = if (movingForward) "Finished" else "Current chapter",
                name = boundary.current.displayName
            )
            Text(
                text = if (movingForward) "Next" else "Previous",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = boundary.neighbor.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (hasError) {
                Text(
                    text = "Could not open chapter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = onContinue,
                enabled = !switching,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        switching -> "Opening..."
                        movingForward -> "Next chapter"
                        else -> "Previous chapter"
                    }
                )
            }
            TextButton(onClick = onBackToSeries, enabled = !switching) {
                Text("Back to series")
            }
        }
    }
}

@Composable
private fun ChapterBoundaryName(label: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
