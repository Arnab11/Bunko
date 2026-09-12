package com.bunko.reader.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunko.reader.KavitaSession
import com.bunko.reader.normalizeKavitaBaseUrl

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DarkLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ContainedLoadingIndicator(
                containerColor = Color(0xFF24352F),
                indicatorColor = Color(0xFF86D39B)
            )
            Spacer(Modifier.height(12.dp))
            Text("Loading", color = Color(0xFFD1D5D5))
        }
    }
}

@Composable
internal fun DarkMessageState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color(0xFFB9BDBD), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

internal fun seriesCoverUrl(session: KavitaSession, seriesId: Int): String {
    val root = normalizeKavitaBaseUrl(session.baseUrl)
    val apiKey = session.apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=${Uri.encode(it)}" }.orEmpty()
    return "$root/api/Image/series-cover?seriesId=$seriesId$apiKey"
}

internal fun seriesInitial(name: String): String {
    return name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "S"
}
