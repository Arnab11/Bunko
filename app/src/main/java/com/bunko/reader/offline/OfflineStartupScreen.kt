package com.bunko.reader.offline

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaClient
import com.bunko.reader.KavitaServerProfile
import com.bunko.reader.KavitaSession
import com.bunko.reader.KavitaSessionStore
import com.bunko.reader.KomgaClient
import com.bunko.reader.KomgaServerProfile
import com.bunko.reader.KomgaSession
import com.bunko.reader.KomgaSessionStore
import com.bunko.reader.LoginDto
import com.bunko.reader.R
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.normalizeKomgaBaseUrl
import com.bunko.reader.settings.KavitaConnectDialog
import com.bunko.reader.settings.KomgaAuthMode
import com.bunko.reader.settings.KomgaConnectDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class StartupTarget {
    Offline,
    Kavita,
    Komga
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfflineStartupScreen(
    localRepository: LocalBookRepository,
    sessionStore: KavitaSessionStore,
    komgaSessionStore: KomgaSessionStore,
    offlineRepository: OfflineIssueRepository,
    onOpenOfflineLibrary: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onConnectKavita: suspend () -> Unit,
    onConnectKomga: suspend () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folderInfo by localRepository.folderFlow.collectAsState(initial = Pair(null, null))
    val books by localRepository.booksFlow.collectAsState(initial = emptyList())
    val isScanning by localRepository.isScanning.collectAsState()
    val activeMode by localRepository.activeModeFlow.collectAsState(initial = null)

    var savedSession by remember { mutableStateOf<KavitaSession?>(null) }
    var savedKomgaSession by remember { mutableStateOf<KomgaSession?>(null) }
    var kavitaProfiles by remember { mutableStateOf<List<KavitaServerProfile>>(emptyList()) }
    var komgaProfiles by remember { mutableStateOf<List<KomgaServerProfile>>(emptyList()) }
    var downloadedCount by remember { mutableStateOf(0) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // Dialog state for Kavita and Komga
    var isKavitaConnectOpen by remember { mutableStateOf(false) }
    var isKavitaConnecting by remember { mutableStateOf(false) }
    var kavitaConnectError by remember { mutableStateOf<String?>(null) }

    var isKomgaConnectOpen by remember { mutableStateOf(false) }
    var isKomgaConnecting by remember { mutableStateOf(false) }
    var komgaConnectError by remember { mutableStateOf<String?>(null) }

    suspend fun reloadServerState() {
        val session = sessionStore.load()
        savedSession = session
        savedKomgaSession = komgaSessionStore.load()
        kavitaProfiles = sessionStore.profiles()
        komgaProfiles = komgaSessionStore.profiles()
        if (session.baseUrl.isNotBlank()) {
            runCatching {
                downloadedCount = offlineRepository.observeDownloaded(session).first().size
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { reloadServerState() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        reloadServerState()
    }

    val hasFolder = !folderInfo.first.isNullOrBlank()
    val hasSavedKavitaServer = savedSession?.baseUrl?.isNotBlank() == true &&
        (savedSession?.jwt?.isNotBlank() == true || savedSession?.apiKey?.isNotBlank() == true)
    val hasSavedKomgaServer = savedKomgaSession?.baseUrl?.isNotBlank() == true &&
        (savedKomgaSession?.username?.isNotBlank() == true || savedKomgaSession?.apiKey?.isNotBlank() == true)
    val hasSavedServer = hasSavedKavitaServer || hasSavedKomgaServer
    val canContinue = hasFolder || hasSavedServer

    var userSelectedTarget by remember { mutableStateOf<StartupTarget?>(null) }
    val effectiveTarget = userSelectedTarget ?: when {
        activeMode == "komga" && hasSavedKomgaServer -> StartupTarget.Komga
        activeMode == "kavita" && hasSavedKavitaServer -> StartupTarget.Kavita
        hasFolder && !hasSavedServer -> StartupTarget.Offline
        hasSavedKavitaServer && !hasSavedKomgaServer && !hasFolder -> StartupTarget.Kavita
        hasSavedKomgaServer && !hasSavedKavitaServer && !hasFolder -> StartupTarget.Komga
        hasFolder && hasSavedServer -> if (activeMode == "offline") StartupTarget.Offline else if (hasSavedKavitaServer) StartupTarget.Kavita else StartupTarget.Komga
        hasSavedKavitaServer -> StartupTarget.Kavita
        hasSavedKomgaServer -> StartupTarget.Komga
        else -> StartupTarget.Offline
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
                localRepository.setDefaultFolder(uri, displayName)
                localRepository.setActiveMode("offline")
                userSelectedTarget = StartupTarget.Offline
            }
        }
    }

    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                localRepository.setStartupCompleted(true)
                localRepository.setActiveMode("offline")
                onOpenOfflineLibrary()
            }
        }
    }

    suspend fun attemptKavitaConnect() {
        isConnecting = true
        connectionError = null
        try {
            val session = savedSession ?: sessionStore.load()
            if (session.baseUrl.isBlank() || (session.jwt.isBlank() && session.apiKey.isBlank())) {
                isKavitaConnectOpen = true
                return
            }
            val client = KavitaClient(context, sessionStore)
            val (api, _) = client.buildApi()
            api.health()
            localRepository.setStartupCompleted(true)
            localRepository.setActiveMode("kavita")
            onConnectKavita()
        } catch (t: Throwable) {
            connectionError = t.message ?: "Connection failed"
            BunkoLog.w("Kavita server connection error", t)
        } finally {
            isConnecting = false
        }
    }

    suspend fun attemptKomgaConnect() {
        isConnecting = true
        connectionError = null
        try {
            val session = savedKomgaSession ?: komgaSessionStore.load()
            if (session.baseUrl.isBlank() || (session.username.isBlank() && session.apiKey.isBlank())) {
                isKomgaConnectOpen = true
                return
            }
            KomgaClient(context, komgaSessionStore).probeCredentials()
            localRepository.setStartupCompleted(true)
            localRepository.setActiveMode("komga")
            onConnectKomga()
        } catch (t: Throwable) {
            connectionError = t.message ?: "Connection failed"
            BunkoLog.w("Komga server connection error", t)
        } finally {
            isConnecting = false
        }
    }

    // Kavita Connect Flyout Dialog
    KavitaConnectDialog(
        isOpen = isKavitaConnectOpen,
        isLoading = isKavitaConnecting,
        errorMessage = kavitaConnectError,
        onDismiss = {
            if (!isKavitaConnecting) {
                isKavitaConnectOpen = false
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

                    var jwt = ""
                    if (password.isNotBlank()) {
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
                        val user = api.login(LoginDto(cleanUsername, password, cleanApiKey.ifBlank { null }))
                        jwt = user.token ?: throw IllegalStateException("No token returned")
                    }

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
                        profileId = null,
                        session = candidate,
                        name = serverName,
                        rememberAuth = true,
                        openByDefault = true
                    )
                    reloadServerState()
                    userSelectedTarget = StartupTarget.Kavita
                    localRepository.setActiveMode("kavita")
                    isKavitaConnectOpen = false
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

    // Komga Connect Flyout Dialog
    KomgaConnectDialog(
        isOpen = isKomgaConnectOpen,
        isLoading = isKomgaConnecting,
        errorMessage = komgaConnectError,
        onDismiss = {
            if (!isKomgaConnecting) {
                isKomgaConnectOpen = false
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
                    if (authMode == KomgaAuthMode.CREDENTIALS && username.isBlank()) throw IllegalArgumentException("Username is required")
                    if (authMode == KomgaAuthMode.CREDENTIALS && password.isBlank()) throw IllegalArgumentException("Password is required")
                    if (authMode == KomgaAuthMode.API_KEY && apiKey.isBlank()) throw IllegalArgumentException("API key is required")

                    val candidate = KomgaSession(
                        baseUrl = normalizedUrl,
                        username = username,
                        password = if (authMode == KomgaAuthMode.CREDENTIALS) password else "",
                        apiKey = if (authMode == KomgaAuthMode.API_KEY) apiKey else ""
                    )
                    komgaSessionStore.useTransient(candidate)
                    val probeClient = KomgaClient(context, komgaSessionStore)
                    probeClient.probeCredentials()
                    komgaSessionStore.saveProfile(
                        profileId = null,
                        session = candidate,
                        name = serverName,
                        rememberAuth = true,
                        openByDefault = true
                    )
                    reloadServerState()
                    userSelectedTarget = StartupTarget.Komga
                    localRepository.setActiveMode("komga")
                    isKomgaConnectOpen = false
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
        val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
        val startCutoutPadding = cutoutInsets.calculateStartPadding(layoutDirection)
        val endCutoutPadding = cutoutInsets.calculateEndPadding(layoutDirection)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = startCutoutPadding + 20.dp,
                        end = endCutoutPadding + 20.dp,
                        top = statusInsets.calculateTopPadding() + 20.dp,
                        bottom = 24.dp
                    )
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Bunko brand
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bunko",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "eBook, Manga & Comic Reader",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Section 1: LOCAL STORAGE
                Card(
                    onClick = {
                        userSelectedTarget = StartupTarget.Offline
                    },
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = if (canContinue && effectiveTarget == StartupTarget.Offline) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (canContinue && effectiveTarget == StartupTarget.Offline) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (canContinue && effectiveTarget == StartupTarget.Offline) 4.dp else 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Local Storage",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Read books stored on this device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (hasFolder) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Configured",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (hasFolder) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                            text = folderInfo.second ?: "Selected Folder",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${books.size} books & comics indexed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = { localRepository.rescan() },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        if (isScanning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Rescan folder",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { folderLauncher.launch(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Change Folder")
                                }

                                TextButton(
                                    onClick = {
                                        singleFileLauncher.launch(
                                            arrayOf(
                                                "application/epub+zip",
                                                "application/pdf",
                                                "application/x-cbz",
                                                "application/zip"
                                            )
                                        )
                                    }
                                ) {
                                    Text("Open File...")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { folderLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select Folder")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        singleFileLauncher.launch(
                                            arrayOf(
                                                "application/epub+zip",
                                                "application/pdf",
                                                "application/x-cbz",
                                                "application/zip"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open File")
                                }
                            }
                        }
                    }
                }

                // Section 2: MEDIA SERVERS (Kavita & Komga)
                Card(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = if (canContinue && (effectiveTarget == StartupTarget.Kavita || effectiveTarget == StartupTarget.Komga)) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (canContinue && (effectiveTarget == StartupTarget.Kavita || effectiveTarget == StartupTarget.Komga)) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (canContinue && (effectiveTarget == StartupTarget.Kavita || effectiveTarget == StartupTarget.Komga)) 4.dp else 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudSync,
                                contentDescription = "Media Servers",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Media Servers",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Stream & sync with self-hosted servers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Sub-card 1: Kavita Server Row
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasSavedKavitaServer && effectiveTarget == StartupTarget.Kavita) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = if (hasSavedKavitaServer && effectiveTarget == StartupTarget.Kavita) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasSavedKavitaServer) {
                                        userSelectedTarget = StartupTarget.Kavita
                                    } else {
                                        isKavitaConnectOpen = true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_kavita_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = kavitaProfiles.firstOrNull()?.name?.ifBlank { "Kavita" } ?: "Kavita",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hasSavedKavitaServer && effectiveTarget == StartupTarget.Kavita) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "Selected",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (hasSavedKavitaServer) {
                                            savedSession?.baseUrl.orEmpty()
                                        } else {
                                            "Connect Kavita server instance"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasSavedKavitaServer) {
                                    FilledTonalIconButton(
                                        onClick = { isKavitaConnectOpen = true },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Kavita server",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { isKavitaConnectOpen = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Connect", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        // Sub-card 2: Komga Server Row
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasSavedKomgaServer && effectiveTarget == StartupTarget.Komga) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = if (hasSavedKomgaServer && effectiveTarget == StartupTarget.Komga) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasSavedKomgaServer) {
                                        userSelectedTarget = StartupTarget.Komga
                                    } else {
                                        isKomgaConnectOpen = true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_komga_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = komgaProfiles.firstOrNull()?.name?.ifBlank { "Komga" } ?: "Komga",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hasSavedKomgaServer && effectiveTarget == StartupTarget.Komga) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "Selected",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (hasSavedKomgaServer) {
                                            savedKomgaSession?.baseUrl.orEmpty()
                                        } else {
                                            "Connect Komga server instance"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (hasSavedKomgaServer) {
                                    FilledTonalIconButton(
                                        onClick = { isKomgaConnectOpen = true },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Komga server",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { isKomgaConnectOpen = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Connect", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        if (downloadedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DownloadDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "$downloadedCount offline downloaded issues",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    TextButton(onClick = onOpenDownloaded) {
                                        Text(
                                            text = "View",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (connectionError != null) {
                            Text(
                                text = connectionError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // CONTINUE ACTION SECTION
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (!canContinue || isConnecting) return@Button
                            scope.launch {
                                when (effectiveTarget) {
                                    StartupTarget.Kavita -> attemptKavitaConnect()
                                    StartupTarget.Komga -> attemptKomgaConnect()
                                    StartupTarget.Offline -> {
                                        localRepository.setStartupCompleted(true)
                                        localRepository.setActiveMode("offline")
                                        onOpenOfflineLibrary()
                                    }
                                }
                            }
                        },
                        enabled = canContinue && !isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            val targetName = when (effectiveTarget) {
                                StartupTarget.Kavita -> "Kavita"
                                StartupTarget.Komga -> "Komga"
                                StartupTarget.Offline -> "Offline Library"
                            }
                            Text(
                                text = "Connecting to $targetName...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            val label = when {
                                !canContinue -> "Continue"
                                effectiveTarget == StartupTarget.Kavita -> "Continue with Kavita"
                                effectiveTarget == StartupTarget.Komga -> "Continue with Komga"
                                else -> "Continue with Local Storage"
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (!canContinue) {
                        Text(
                            text = "Select a local folder or connect a media server to continue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        Text(
                            text = "Tap any card above to select your preferred library source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
