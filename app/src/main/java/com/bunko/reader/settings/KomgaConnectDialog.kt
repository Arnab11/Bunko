package com.bunko.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bunko.reader.KomgaServerProfile
import com.bunko.reader.R

enum class KomgaAuthMode {
    CREDENTIALS,
    API_KEY
}

/**
 * mpvRx-style connect flyout for Komga (mirrors AddNavidromeServerDialog in mpvRx:
 * bottom-sheet flyout with server URL, display name, and auth-mode toggle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomgaConnectDialog(
    isOpen: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    initialServer: KomgaServerProfile? = null,
    onDismiss: () -> Unit,
    onConnect: (serverUrl: String, serverName: String, authMode: KomgaAuthMode, username: String, password: String, apiKey: String) -> Unit
) {
    if (!isOpen) return

    var serverUrl by remember(initialServer) { mutableStateOf(initialServer?.session?.baseUrl ?: "") }
    var serverName by remember(initialServer) { mutableStateOf(initialServer?.name ?: "") }
    var authMode by remember(initialServer) {
        mutableStateOf(
            if (!initialServer?.session?.apiKey.isNullOrBlank()) KomgaAuthMode.API_KEY
            else KomgaAuthMode.CREDENTIALS
        )
    }
    var username by remember(initialServer) { mutableStateOf(initialServer?.session?.username ?: "") }
    var password by remember(initialServer) { mutableStateOf("") }
    var apiKey by remember(initialServer) { mutableStateOf(initialServer?.session?.apiKey ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val canConnect = serverUrl.isNotBlank() && when (authMode) {
        KomgaAuthMode.CREDENTIALS -> username.isNotBlank() && (password.isNotBlank() || initialServer != null)
        KomgaAuthMode.API_KEY -> apiKey.isNotBlank()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header (mpvRx SharedAddServerDialog style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_komga_logo),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (initialServer == null) "Connect to Komga" else "Edit Komga Server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enter your Komga server address",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("komga.example.com:25600 or 192.168.1.100:25600") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Display name (optional)") },
                placeholder = { Text("Komga") },
                leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            // Auth mode toggle (mirrors Navidrome credentials/token switch)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = authMode == KomgaAuthMode.CREDENTIALS,
                    onClick = { authMode = KomgaAuthMode.CREDENTIALS },
                    label = { Text("Username & password") },
                    enabled = !isLoading
                )
                FilterChip(
                    selected = authMode == KomgaAuthMode.API_KEY,
                    onClick = { authMode = KomgaAuthMode.API_KEY },
                    label = { Text("API key") },
                    enabled = !isLoading
                )
            }

            if (authMode == KomgaAuthMode.CREDENTIALS) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / email") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (initialServer == null) "Password" else "Password (leave blank to keep)") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Komga uses HTTP Basic auth. The password is stored encrypted on this device and sent only to your server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Create an API key in Komga → User settings. Sent as X-API-Key header.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional label)") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    onConnect(
                        serverUrl.trim(),
                        serverName.trim().ifBlank { "Komga" },
                        authMode,
                        username.trim(),
                        password,
                        apiKey.trim()
                    )
                },
                enabled = canConnect && !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isLoading) "Connecting..." else "Connect")
            }
        }
    }
}
