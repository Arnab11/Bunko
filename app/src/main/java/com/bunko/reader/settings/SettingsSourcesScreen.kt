package com.bunko.reader.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bunko.reader.KavitaServerProfile
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.R
import com.bunko.reader.offline.LocalBookRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsSourcesScreen(
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    onConfigureServerDetails: () -> Unit,
    onActiveModeChanged: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeMode by localRepository.activeModeFlow.collectAsState(initial = "offline")
    val folders by localRepository.foldersFlow.collectAsState(initial = emptyList())
    val books by localRepository.booksFlow.collectAsState(initial = emptyList())
    val isScanning by localRepository.isScanning.collectAsState()

    var kavitaProfiles by remember { mutableStateOf<List<KavitaServerProfile>>(emptyList()) }
    var activeProfile by remember { mutableStateOf<KavitaServerProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<KavitaServerProfile?>(null) }

    suspend fun reloadProfiles() {
        kavitaProfiles = sessionStore.profiles()
        activeProfile = sessionStore.activeProfile()
    }

    LaunchedEffect(Unit) {
        reloadProfiles()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
            val displayName = DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/')
                .ifBlank { "eBooks & Comics" }
            scope.launch {
                localRepository.addFolder(uri, displayName)
            }
        }
    }

    if (profileToDelete != null) {
        val target = profileToDelete!!
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Delete Server Profile") },
            text = { Text("Are you sure you want to remove \"${target.name.ifBlank { target.session.baseUrl }}\"? You will need to re-enter credentials to connect again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        profileToDelete = null
                        scope.launch {
                            sessionStore.deleteProfile(id)
                            reloadProfiles()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Active Library Mode Selector Card (mpvRx style)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ACTIVE LIBRARY SOURCE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            SettingsSectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isOffline = activeMode != "kavita"

                    Button(
                        onClick = {
                            scope.launch {
                                localRepository.setActiveMode("offline")
                                onActiveModeChanged("offline")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = if (isOffline) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Local Storage", fontWeight = if (isOffline) FontWeight.Bold else FontWeight.Normal)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                localRepository.setActiveMode("kavita")
                                onActiveModeChanged("kavita")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = if (!isOffline) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kavita_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (!isOffline) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Kavita Server", fontWeight = if (!isOffline) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        // 2. Local Storage Folders Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOCAL STORAGE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (folders.isNotEmpty()) {
                    Text(
                        text = "${folders.size} ${if (folders.size == 1) "folder" else "folders"} • ${books.size} ${if (books.size == 1) "book" else "books"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Indexed Folders",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Directories scanned for EPUB, MOBI, Comics (CBZ/CBR), and PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (folders.isNotEmpty()) {
                            val infiniteTransition = rememberInfiniteTransition(label = "rescan_rot")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "rescan_angle"
                            )

                            FilledTonalIconButton(
                                onClick = { localRepository.rescan() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Rescan all folders",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = if (isScanning) Modifier.rotate(rotation) else Modifier
                                )
                            }
                        }
                    }

                    if (folders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            folders.forEach { folder ->
                                val folderBooksCount = books.count {
                                    it.folderUriString == folder.uriString || (folders.size == 1 && it.folderUriString.isBlank())
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folder.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$folderBooksCount books indexed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                scope.launch { localRepository.removeFolder(folder.uriString) }
                                            },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Remove folder",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Folder", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Library Folder")
                        }
                    }
                }
            }
        }

        // 3. Kavita Remote Media Servers Section (mpvRx Media Server style)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KAVITA SERVERS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (kavitaProfiles.isNotEmpty()) {
                    Text(
                        text = "${kavitaProfiles.size} ${if (kavitaProfiles.size == 1) "server" else "servers"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (kavitaProfiles.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Kavita Server Connected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Connect to your self-hosted Kavita server to stream and sync manga & comics",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            kavitaProfiles.forEach { profile ->
                                val isCurrentlyActive = activeMode == "kavita" && (activeProfile?.id == profile.id || (activeProfile == null && profile.openByDefault))

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isCurrentlyActive) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                                    },
                                    border = if (isCurrentlyActive) {
                                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isCurrentlyActive) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_kavita_logo),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = profile.name.ifBlank { "Kavita Server" },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (isCurrentlyActive) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                                        ) {
                                                            Text(
                                                                text = "ACTIVE",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = profile.session.baseUrl.ifBlank { "Configured" } +
                                                        if (profile.session.username.isNotBlank()) " • ${profile.session.username}" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            FilledTonalIconButton(
                                                onClick = onConfigureServerDetails,
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Edit server",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            FilledTonalIconButton(
                                                onClick = { profileToDelete = profile },
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Delete server",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        if (!isCurrentlyActive) {
                                            FilledTonalButton(
                                                onClick = {
                                                    scope.launch {
                                                        sessionStore.selectProfile(profile.id)
                                                        sessionStore.setDefaultProfile(profile.id)
                                                        localRepository.setActiveMode("kavita")
                                                        onActiveModeChanged("kavita")
                                                        reloadProfiles()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Set as Active Server")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onConfigureServerDetails,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (kavitaProfiles.isEmpty()) "Connect Kavita Server" else "Add Another Server")
                    }
                }
            }
        }
    }
}
