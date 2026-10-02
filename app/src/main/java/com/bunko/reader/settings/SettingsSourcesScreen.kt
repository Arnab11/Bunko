package com.bunko.reader.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunko.reader.KavitaClient
import com.bunko.reader.KavitaServerProfile
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.LoginDto
import com.bunko.reader.KomgaClient
import com.bunko.reader.KomgaServerProfile
import com.bunko.reader.KomgaSession
import com.bunko.reader.KomgaSessionStore
import com.bunko.reader.R
import com.bunko.reader.normalizeKomgaBaseUrl
import com.bunko.reader.offline.LocalBookRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsSourcesScreen(
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    komgaSessionStore: KomgaSessionStore,
    onConfigureServerDetails: () -> Unit = {},
    onActiveModeChanged: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeMode by localRepository.activeModeFlow.collectAsState(initial = "offline")
    val folders by localRepository.foldersFlow.collectAsState(initial = emptyList())
    val books by localRepository.booksFlow.collectAsState(initial = emptyList())

    var kavitaProfiles by remember { mutableStateOf<List<KavitaServerProfile>>(emptyList()) }
    var activeProfile by remember { mutableStateOf<KavitaServerProfile?>(null) }
    var profileToDelete by remember { mutableStateOf<KavitaServerProfile?>(null) }
    var kavitaProfileToEdit by remember { mutableStateOf<KavitaServerProfile?>(null) }
    var isKavitaConnectOpen by remember { mutableStateOf(false) }
    var isKavitaConnecting by remember { mutableStateOf(false) }
    var kavitaConnectError by remember { mutableStateOf<String?>(null) }

    var komgaProfiles by remember { mutableStateOf<List<KomgaServerProfile>>(emptyList()) }
    var activeKomgaProfile by remember { mutableStateOf<KomgaServerProfile?>(null) }
    var komgaProfileToDelete by remember { mutableStateOf<KomgaServerProfile?>(null) }
    var komgaProfileToEdit by remember { mutableStateOf<KomgaServerProfile?>(null) }
    var isKomgaConnectOpen by remember { mutableStateOf(false) }
    var isKomgaConnecting by remember { mutableStateOf(false) }
    var komgaConnectError by remember { mutableStateOf<String?>(null) }

    suspend fun reloadProfiles() {
        kavitaProfiles = sessionStore.profiles()
        activeProfile = sessionStore.activeProfile()
        komgaProfiles = komgaSessionStore.profiles()
        activeKomgaProfile = komgaSessionStore.activeProfile()
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

    if (komgaProfileToDelete != null) {
        val target = komgaProfileToDelete!!
        AlertDialog(
            onDismissRequest = { komgaProfileToDelete = null },
            title = { Text("Delete Komga Server") },
            text = { Text("Are you sure you want to remove \"${target.name.ifBlank { target.session.baseUrl }}\"? You will need to re-enter credentials to connect again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        komgaProfileToDelete = null
                        scope.launch {
                            komgaSessionStore.deleteProfile(id)
                            reloadProfiles()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { komgaProfileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    KomgaConnectDialog(
        isOpen = isKomgaConnectOpen,
        isLoading = isKomgaConnecting,
        errorMessage = komgaConnectError,
        initialServer = komgaProfileToEdit,
        onDismiss = {
            if (!isKomgaConnecting) {
                isKomgaConnectOpen = false
                komgaProfileToEdit = null
                komgaConnectError = null
            }
        },
        onConnect = { serverUrl, serverName, authMode, username, password, apiKey ->
            scope.launch {
                isKomgaConnecting = true
                komgaConnectError = null
                try {
                    val normalizedUrl = normalizeKomgaBaseUrl(serverUrl)
                    if (normalizedUrl.isBlank()) throw IllegalArgumentException("Server URL is required")
                    val resolvedPassword = if (authMode == KomgaAuthMode.CREDENTIALS && password.isBlank() && komgaProfileToEdit != null) {
                        komgaProfileToEdit!!.session.password
                    } else password
                    if (authMode == KomgaAuthMode.CREDENTIALS && username.isBlank()) {
                        throw IllegalArgumentException("Username is required")
                    }
                    if (authMode == KomgaAuthMode.CREDENTIALS && resolvedPassword.isBlank()) {
                        throw IllegalArgumentException("Password is required")
                    }
                    if (authMode == KomgaAuthMode.API_KEY && apiKey.isBlank()) {
                        throw IllegalArgumentException("API key is required")
                    }
                    val candidate = KomgaSession(
                        baseUrl = normalizedUrl,
                        username = username,
                        password = if (authMode == KomgaAuthMode.CREDENTIALS) resolvedPassword else "",
                        apiKey = if (authMode == KomgaAuthMode.API_KEY) apiKey else ""
                    )
                    komgaSessionStore.useTransient(candidate)
                    val probeClient = KomgaClient(context, komgaSessionStore)
                    probeClient.probeCredentials()
                    komgaSessionStore.saveProfile(
                        komgaProfileToEdit?.id,
                        candidate,
                        name = serverName,
                        rememberAuth = true,
                        openByDefault = komgaProfiles.none { it.openByDefault }
                    )
                    reloadProfiles()
                    localRepository.setActiveMode("komga")
                    onActiveModeChanged("komga")
                    isKomgaConnectOpen = false
                    komgaProfileToEdit = null
                } catch (t: retrofit2.HttpException) {
                    val body = t.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    komgaConnectError = "Connection failed: HTTP ${t.code()}: ${body ?: t.message()}"
                } catch (t: Throwable) {
                    komgaConnectError = "Connection failed: ${t.message ?: t.toString()}"
                } finally {
                    isKomgaConnecting = false
                }
            }
        }
    )

    // Kavita connect flyout (mpvRx-style bottom sheet)
    KavitaConnectDialog(
        isOpen = isKavitaConnectOpen,
        isLoading = isKavitaConnecting,
        errorMessage = kavitaConnectError,
        initialServer = kavitaProfileToEdit,
        onDismiss = {
            if (!isKavitaConnecting) {
                isKavitaConnectOpen = false
                kavitaProfileToEdit = null
                kavitaConnectError = null
            }
        },
        onConnect = { serverUrl, serverName, username, password, apiKey ->
            scope.launch {
                isKavitaConnecting = true
                kavitaConnectError = null
                try {
                    val cleanBaseUrl = serverUrl.trim()
                    val cleanUsername = username.trim()
                    val cleanApiKey = apiKey.trim()
                    if (cleanBaseUrl.isBlank()) throw IllegalArgumentException("Server URL is required")
                    if (cleanApiKey.isBlank()) throw IllegalArgumentException("Auth Key (API Key) is required")

                    val resolvedPassword = if (password.isBlank() && kavitaProfileToEdit != null) {
                        ""
                    } else password

                    var jwt = ""
                    if (resolvedPassword.isNotBlank()) {
                        sessionStore.useTransient(
                            KavitaSession(
                                baseUrl = cleanBaseUrl,
                                username = cleanUsername,
                                apiKey = "",
                                jwt = ""
                            )
                        )
                        val client = KavitaClient(context, sessionStore)
                        val (api, _) = client.buildApi()
                        val user = api.login(LoginDto(cleanUsername, resolvedPassword, cleanApiKey.ifBlank { null }))
                        jwt = user.token ?: throw IllegalStateException("No token returned")
                    } else if (kavitaProfileToEdit != null) {
                        jwt = kavitaProfileToEdit!!.session.jwt
                    }

                    // Probe libraries using API key
                    sessionStore.useTransient(
                        KavitaSession(
                            baseUrl = cleanBaseUrl,
                            username = cleanUsername,
                            apiKey = cleanApiKey,
                            jwt = jwt
                        )
                    )
                    val client = KavitaClient(context, sessionStore)
                    val (apiKeyApi, _) = client.buildApi()
                    apiKeyApi.userLibraries()

                    val candidate = KavitaSession(
                        baseUrl = cleanBaseUrl,
                        username = cleanUsername,
                        apiKey = cleanApiKey,
                        jwt = jwt
                    )
                    sessionStore.saveProfile(
                        kavitaProfileToEdit?.id,
                        candidate,
                        name = serverName,
                        rememberAuth = true,
                        openByDefault = kavitaProfiles.none { it.openByDefault }
                    )
                    reloadProfiles()
                    localRepository.setActiveMode("kavita")
                    onActiveModeChanged("kavita")
                    isKavitaConnectOpen = false
                    kavitaProfileToEdit = null
                } catch (t: retrofit2.HttpException) {
                    val body = t.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    kavitaConnectError = "Connection failed: HTTP ${t.code()}: ${body ?: t.message()}"
                } catch (t: Throwable) {
                    kavitaConnectError = "Connection failed: ${t.message ?: t.toString()}"
                } finally {
                    isKavitaConnecting = false
                }
            }
        }
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. Jellyfin / Kavita Section (mpvRx Media Servers style)
        MediaServerSection(
            title = "Kavita",
            profilesCount = kavitaProfiles.size
        ) {
            if (kavitaProfiles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        kavitaProfiles.forEachIndexed { index, profile ->
                            val isCurrentlyActive = activeMode == "kavita" && (activeProfile?.id == profile.id || (activeProfile == null && profile.openByDefault))
                            var menuExpanded by remember { mutableStateOf(false) }

                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            sessionStore.selectProfile(profile.id)
                                            sessionStore.setDefaultProfile(profile.id)
                                            localRepository.setActiveMode("kavita")
                                            onActiveModeChanged("kavita")
                                            reloadProfiles()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_kavita_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = profile.name.ifBlank { "Kavita" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrentlyActive) {
                                            ActivePillBadge()
                                        }
                                    }
                                    val subtitle = buildString {
                                        if (profile.session.username.isNotBlank()) {
                                            append(profile.session.username)
                                            append(" • ")
                                        }
                                        append(profile.session.baseUrl.ifBlank { "Configured" })
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (!isCurrentlyActive) {
                                            DropdownMenuItem(
                                                text = { Text("Set as Active Server") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    scope.launch {
                                                        sessionStore.selectProfile(profile.id)
                                                        sessionStore.setDefaultProfile(profile.id)
                                                        localRepository.setActiveMode("kavita")
                                                        onActiveModeChanged("kavita")
                                                        reloadProfiles()
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Edit Server") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                kavitaProfileToEdit = profile
                                                kavitaConnectError = null
                                                isKavitaConnectOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Server", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                profileToDelete = profile
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        AddAnotherServerRow(
                            title = "Add another server",
                            subtitle = "Connect an additional Kavita server",
                            onClick = {
                                kavitaProfileToEdit = null
                                kavitaConnectError = null
                                isKavitaConnectOpen = true
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            kavitaProfileToEdit = null
                            kavitaConnectError = null
                            isKavitaConnectOpen = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kavita_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Kavita",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add and connect to a Kavita server",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Seerr / Komga Section (mpvRx Media Servers style)
        MediaServerSection(
            title = "Komga",
            profilesCount = komgaProfiles.size
        ) {
            if (komgaProfiles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        komgaProfiles.forEachIndexed { index, profile ->
                            val isCurrentlyActive = activeMode == "komga" && (activeKomgaProfile?.id == profile.id || (activeKomgaProfile == null && profile.openByDefault))
                            var menuExpanded by remember { mutableStateOf(false) }

                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            komgaSessionStore.selectProfile(profile.id)
                                            komgaSessionStore.setDefaultProfile(profile.id)
                                            localRepository.setActiveMode("komga")
                                            onActiveModeChanged("komga")
                                            reloadProfiles()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_komga_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = profile.name.ifBlank { "Komga" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrentlyActive) {
                                            ActivePillBadge()
                                        }
                                    }
                                    val subtitle = buildString {
                                        if (profile.session.username.isNotBlank()) {
                                            append(profile.session.username)
                                            append(" • ")
                                        }
                                        append(profile.session.baseUrl.ifBlank { "Configured" })
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (!isCurrentlyActive) {
                                            DropdownMenuItem(
                                                text = { Text("Set as Active Server") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    scope.launch {
                                                        komgaSessionStore.selectProfile(profile.id)
                                                        komgaSessionStore.setDefaultProfile(profile.id)
                                                        localRepository.setActiveMode("komga")
                                                        onActiveModeChanged("komga")
                                                        reloadProfiles()
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Edit Server") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                komgaProfileToEdit = profile
                                                komgaConnectError = null
                                                isKomgaConnectOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Server", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                komgaProfileToDelete = profile
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        AddAnotherServerRow(
                            title = "Add another server",
                            subtitle = "Connect an additional Komga server",
                            onClick = {
                                komgaProfileToEdit = null
                                komgaConnectError = null
                                isKomgaConnectOpen = true
                            }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            komgaProfileToEdit = null
                            komgaConnectError = null
                            isKomgaConnectOpen = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_komga_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Komga",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add and connect to a Komga server",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Local Storage Section (mpvRx Media Servers style)
        MediaServerSection(
            title = "Local Storage",
            profilesCount = folders.size
        ) {
            if (folders.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        folders.forEachIndexed { index, folder ->
                            val folderBooksCount = books.count {
                                it.folderUriString == folder.uriString || (folders.size == 1 && it.folderUriString.isBlank())
                            }
                            val isCurrentlyActive = activeMode == "offline"
                            var menuExpanded by remember { mutableStateOf(false) }

                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            localRepository.setActiveMode("offline")
                                            onActiveModeChanged("offline")
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrentlyActive) {
                                            ActivePillBadge()
                                        }
                                    }
                                    Text(
                                        text = "$folderBooksCount books indexed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (!isCurrentlyActive) {
                                            DropdownMenuItem(
                                                text = { Text("Set as Active Source") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    menuExpanded = false
                                                    scope.launch {
                                                        localRepository.setActiveMode("offline")
                                                        onActiveModeChanged("offline")
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Rescan Folder") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                localRepository.rescan()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove Folder", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                scope.launch { localRepository.removeFolder(folder.uriString) }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        AddAnotherServerRow(
                            title = "Add another folder",
                            subtitle = "Connect an additional local folder",
                            onClick = { folderLauncher.launch(null) }
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderLauncher.launch(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect Local Storage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add and index a folder from device storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * mpvRx-style Section with bold Title and 28dp x 3dp rounded accent underline.
 */
@Composable
private fun MediaServerSection(
    title: String,
    profilesCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.padding(start = 2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        content()
    }
}

/**
 * mpvRx "+ Add another server" row within the server card.
 */
@Composable
private fun AddAnotherServerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * mpvRx "Active" pill badge next to server name.
 */
@Composable
private fun ActivePillBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}
