package com.bunko.reader.reader.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderLoadingScreen(
    preparingPdf: Boolean,
    error: String?,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF111412)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = error ?: if (preparingPdf) "Preparing PDF..." else "Opening chapter...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (error == null && preparingPdf) {
                Text(
                    text = "The first opening can take several minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFADB5B0),
                    textAlign = TextAlign.Center
                )
            }
            TextButton(onClick = onBack) { Text("Back to series") }
        }
    }
}
