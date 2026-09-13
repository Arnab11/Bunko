package com.bunko.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.text.font.FontWeight
import com.bunko.reader.settings.CategoryRowGap
import com.bunko.reader.settings.ClickableSettingRow
import com.bunko.reader.settings.RadioSettingRow
import com.bunko.reader.settings.SettingsSectionCard
import com.bunko.reader.settings.SwitchSettingRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bunko.reader.ui.ValueBubbleSlider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import retrofit2.HttpException

private enum class ServerSettingsPage {
    Servers,
    ServerSelected
}

private data class StatusMessage(val text: String, val isError: Boolean)

private val StatusSuccessColor = Color(0xFF81C784)
private val SettingsHubContentMaxWidth = 560.dp
internal val SettingsFormContentMaxWidth = 720.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsTopAppBar(title: String, onBack: () -> Unit) {
    MaterialTheme(motionScheme = MotionScheme.expressive()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onServer: () -> Unit,
    onReader: () -> Unit,
    onStorage: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SettingsTopAppBar(title = "Settings", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = SettingsHubContentMaxWidth)
                        .fillMaxWidth()
                ) {
                    SettingsNavRow(
                        icon = Icons.Filled.Dns,
                        title = "Server (Kavita)",
                        onClick = onServer
                    )
                    SettingsNavRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Reader",
                        onClick = onReader
                    )
                    SettingsNavRow(
                        icon = Icons.Filled.Storage,
                        title = "Storage & cache",
                        onClick = onStorage
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Bunko v0.22",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    sessionStore: KavitaSessionStore,
    onActiveServerChanged: suspend () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf<List<KavitaServerProfile>>(emptyList()) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(ServerSettingsPage.Servers) }
    var openByDefault by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("(unknown)") }
    var jwtStatus by remember { mutableStateOf("(unknown)") }
    var authStatus by remember { mutableStateOf("(unknown)") }
    var storageStatus by remember { mutableStateOf("(unknown)") }
    var status by remember { mutableStateOf<StatusMessage?>(null) }

    suspend fun refreshSessionState(
        selectProfileId: String? = selectedProfileId,
        clearPassword: Boolean = true
    ) {
        val loadedProfiles = sessionStore.profiles()
        profiles = loadedProfiles
        val selected = loadedProfiles.firstOrNull { it.id == selectProfileId }
        selectedProfileId = selected?.id
        page = if (selected != null) ServerSettingsPage.ServerSelected else ServerSettingsPage.Servers

        val s = selected?.session ?: KavitaSession()
        val state = sessionStore.storageState(selected?.id)
        baseUrl = s.baseUrl
        username = s.username
        apiKey = s.apiKey
        if (clearPassword) password = ""
        openByDefault = selected?.openByDefault ?: false
        serverStatus = if (s.baseUrl.isBlank()) "Not configured" else s.baseUrl
        jwtStatus = if (s.jwt.isBlank()) "Logged out" else "Logged in"
        authStatus = if (state.hasSavedAuth) "Saved" else "Not saved"
        storageStatus = when {
            !state.hasSavedAuth -> "No saved auth"
            state.secretsEncrypted -> "Encrypted"
            state.hasLegacyPlaintextSecrets -> "Legacy plaintext"
            else -> "(unknown)"
        }
    }

    suspend fun restoreDefaultProfile() {
        val loadedProfiles = sessionStore.profiles()
        profiles = loadedProfiles
        val defaultProfile = loadedProfiles.firstOrNull { it.openByDefault }
        sessionStore.selectProfile(defaultProfile?.id)
    }

    fun selectDefaultProfile(profile: KavitaServerProfile) {
        scope.launch {
            sessionStore.setOpenByDefault(profile.id, true)
            sessionStore.selectProfile(profile.id)
            profiles = sessionStore.profiles()
            selectedProfileId = profile.id
            onActiveServerChanged()
        }
    }

    LaunchedEffect(Unit) {
        val loadedProfiles = sessionStore.profiles()
        val defaultProfile = loadedProfiles.firstOrNull { it.openByDefault }
            ?: loadedProfiles.firstOrNull()?.also { sessionStore.setOpenByDefault(it.id, true) }
        profiles = sessionStore.profiles()
        selectedProfileId = defaultProfile?.id
        sessionStore.selectProfile(defaultProfile?.id)
        page = ServerSettingsPage.Servers
    }

    BackHandler(enabled = page == ServerSettingsPage.ServerSelected) {
        page = ServerSettingsPage.Servers
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SettingsTopAppBar(
            title = if (page == ServerSettingsPage.Servers) {
                "Servers"
            } else {
                "Server Details"
            },
            onBack = {
                if (page == ServerSettingsPage.ServerSelected) {
                    page = ServerSettingsPage.Servers
                } else {
                    onBack()
                }
            }
        )
        Column(
            Modifier
                .widthIn(max = SettingsFormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page == ServerSettingsPage.Servers) {
                Text("Servers", style = MaterialTheme.typography.titleMedium)
                if (profiles.isEmpty()) {
                    Text("No saved servers", color = Color.Gray)
                } else {
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = profile.openByDefault,
                                onClick = { selectDefaultProfile(profile) }
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectDefaultProfile(profile) }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    profile.session.baseUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        status = null
                                        refreshSessionState(profile.id)
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}")
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedProfileId = null
                            page = ServerSettingsPage.ServerSelected
                            baseUrl = ""
                            username = ""
                            password = ""
                            apiKey = ""
                            openByDefault = profiles.none { it.openByDefault }
                            serverStatus = "Not configured"
                            jwtStatus = "Logged out"
                            authStatus = "Not saved"
                            storageStatus = "No saved auth"
                            status = null
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
                    )
                    Text(
                        "New server",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp)
                    )
                }
                Text("Choose the default server or edit its connection details.", color = Color.Gray)
            } else {
                Text("Connection", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Server URL") }, singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                status = null
                                try {
                                    val prev = profiles.firstOrNull { it.id == selectedProfileId }?.session
                                        ?: KavitaSession()
                                    val saved = sessionStore.saveProfile(
                                        selectedProfileId,
                                        prev.copy(baseUrl = baseUrl, username = username, apiKey = apiKey),
                                        rememberAuth = true,
                                        openByDefault = openByDefault
                                    )
                                    refreshSessionState(saved.id, clearPassword = false)
                                    status = StatusMessage("Saved", isError = false)
                                } catch (t: Throwable) {
                                    status = StatusMessage("Save failed: ${t.message ?: t.toString()}", isError = true)
                                } finally {
                                    restoreDefaultProfile()
                                    onActiveServerChanged()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }

                    Button(
                        onClick = {
                            scope.launch {
                                status = null
                                try {
                                    val prev = profiles.firstOrNull { it.id == selectedProfileId }?.session
                                        ?: KavitaSession()
                                    val saved = sessionStore.saveProfile(
                                        selectedProfileId,
                                        prev.copy(baseUrl = baseUrl, username = username, apiKey = apiKey),
                                        rememberAuth = true,
                                        openByDefault = openByDefault
                                    )
                                    refreshSessionState(saved.id, clearPassword = false)
                                    val client = KavitaClient(ctx, sessionStore)
                                    val (api, _) = client.buildApi()
                                    api.health()
                                    status = StatusMessage("OK: /api/Health", isError = false)
                                } catch (t: Throwable) {
                                    status = StatusMessage("Health failed: ${t.message ?: t.toString()}", isError = true)
                                } finally {
                                    restoreDefaultProfile()
                                    onActiveServerChanged()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Test") }
                }

                Text("Auth Details", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(
                    "Password is only used for the first login to fetch a token. It is never saved — after that, the stored token / Auth Key is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Auth Key (x-api-key)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Button(
                    onClick = {
                        scope.launch {
                            status = null
                            try {
                                val cleanBaseUrl = baseUrl.trim()
                                val cleanUsername = username.trim()
                                val cleanApiKey = apiKey.trim()
                                if (cleanBaseUrl.isBlank()) {
                                    throw IllegalArgumentException("Server URL is required")
                                }
                                if (cleanUsername.isBlank() && cleanApiKey.isBlank()) {
                                    throw IllegalArgumentException("Username or Auth Key is required")
                                }
                                if (cleanApiKey.isBlank()) {
                                    throw IllegalArgumentException("Auth Key is required for image loading")
                                }

                                sessionStore.useTransient(
                                    KavitaSession(
                                        baseUrl = cleanBaseUrl,
                                        username = cleanUsername,
                                        apiKey = "",
                                        jwt = ""
                                    )
                                )
                                val client = KavitaClient(ctx, sessionStore)
                                val (api, _) = client.buildApi()
                                val user = api.login(LoginDto(cleanUsername, password, cleanApiKey.ifBlank { null }))
                                val jwt = user.token ?: throw IllegalStateException("No token returned")
                                sessionStore.useTransient(
                                    KavitaSession(
                                        baseUrl = cleanBaseUrl,
                                        username = cleanUsername,
                                        apiKey = cleanApiKey,
                                        jwt = ""
                                    )
                                )
                                val (apiKeyApi, _) = client.buildApi()
                                apiKeyApi.userLibraries()
                                val saved = sessionStore.saveProfile(
                                    selectedProfileId,
                                    KavitaSession(
                                        baseUrl = cleanBaseUrl,
                                        username = cleanUsername,
                                        apiKey = cleanApiKey,
                                        jwt = jwt
                                    ),
                                    rememberAuth = true,
                                    openByDefault = openByDefault
                                )
                                refreshSessionState(saved.id, clearPassword = false)
                                status = StatusMessage("Logged in and saved", isError = false)
                            } catch (t: HttpException) {
                                val body = t.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                                status = StatusMessage("Login failed: HTTP ${t.code()}: ${body ?: t.message()}", isError = true)
                            } catch (t: Throwable) {
                                status = StatusMessage("Login failed: ${t.message ?: t.toString()}", isError = true)
                            } finally {
                                restoreDefaultProfile()
                                onActiveServerChanged()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Login & Save Auth") }

                Text("Storage", style = MaterialTheme.typography.titleMedium)
                Text("Server: $serverStatus", color = Color.Gray)
                Text("Saved auth: $authStatus", color = Color.Gray)
                Text("Auth status: $jwtStatus", color = Color.Gray)
                Text("Secret storage: $storageStatus", color = Color.Gray)
                status?.let { Text(it.text, color = if (it.isError) Color.Red else StatusSuccessColor) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                status = null
                                try {
                                    sessionStore.clearCredentials(selectedProfileId)
                                    username = ""
                                    password = ""
                                    apiKey = ""
                                    refreshSessionState(selectedProfileId)
                                    restoreDefaultProfile()
                                    onActiveServerChanged()
                                    status = StatusMessage("Saved auth cleared", isError = false)
                                } catch (t: Throwable) {
                                    status = StatusMessage("Clear auth failed: ${t.message ?: t.toString()}", isError = true)
                                }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear Auth") }

                    Button(
                        onClick = {
                            scope.launch {
                                val forgottenId = selectedProfileId
                                val wasDefault = profiles.firstOrNull { it.id == forgottenId }?.openByDefault == true
                                sessionStore.deleteProfile(forgottenId)
                                val remainingProfiles = sessionStore.profiles()
                                if (wasDefault && remainingProfiles.none { it.openByDefault }) {
                                    remainingProfiles.firstOrNull()?.let {
                                        sessionStore.setOpenByDefault(it.id, true)
                                    }
                                }
                                baseUrl = ""
                                username = ""
                                password = ""
                                apiKey = ""
                                selectedProfileId = null
                                page = ServerSettingsPage.Servers
                                restoreDefaultProfile()
                                selectedProfileId = profiles.firstOrNull { it.openByDefault }?.id
                                onActiveServerChanged()
                                status = StatusMessage("Server settings cleared", isError = false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("Forget Server") }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(
    settingsStore: AppSettingsStore,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.flow.collectAsState(initial = AppSettings())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (showTopBar) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
        ) {
            if (showTopBar) {
                SettingsTopAppBar(title = "Reader Settings", onBack = onBack)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Reading Direction
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Reading Direction",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        RadioSettingRow(
                            title = "Left to Right (LTR)",
                            subtitle = "Standard reading mode for western comics and novels",
                            selected = settings.reader.readingDirection == ReaderReadingDirection.LeftToRight,
                            onClick = { scope.launch { settingsStore.setReadingDirection(ReaderReadingDirection.LeftToRight) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Vertical",
                            subtitle = "Continuous vertical scrolling for webtoons and comics",
                            selected = settings.reader.readingDirection == ReaderReadingDirection.Vertical,
                            onClick = { scope.launch { settingsStore.setReadingDirection(ReaderReadingDirection.Vertical) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Webtoon",
                            subtitle = "Continuous vertical strip with tap-to-scroll (75% height) and side margins",
                            selected = settings.reader.readingDirection == ReaderReadingDirection.Webtoon,
                            onClick = { scope.launch { settingsStore.setReadingDirection(ReaderReadingDirection.Webtoon) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Right to Left (RTL)",
                            subtitle = "Standard reading mode for manga and Japanese publications",
                            selected = settings.reader.readingDirection == ReaderReadingDirection.RightToLeft,
                            onClick = { scope.launch { settingsStore.setReadingDirection(ReaderReadingDirection.RightToLeft) } }
                        )
                        CategoryRowGap()
                        SwitchSettingRow(
                            title = "Auto Webtoon Mode",
                            subtitle = "Automatically switch to Webtoon mode for webtoons and manhwa based on page dimensions and tags",
                            checked = settings.reader.autoWebtoonMode,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setAutoWebtoonMode(enabled) }
                            }
                        )
                        if (settings.reader.readingDirection == ReaderReadingDirection.Webtoon || settings.reader.autoWebtoonMode) {
                            CategoryRowGap()
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Webtoon Side Padding: ${settings.reader.webtoonSidePadding}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Adds side margins to webtoon strips on wider screens and tablets",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(0, 5, 10, 15, 20, 25).forEach { paddingVal ->
                                        val isSelected = settings.reader.webtoonSidePadding == paddingVal
                                        Surface(
                                            onClick = { scope.launch { settingsStore.setWebtoonSidePadding(paddingVal) } },
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "$paddingVal%",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Page Preloading
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Page Preloading",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        Box(modifier = Modifier.padding(16.dp)) {
                            PrefetchTurnsSetting(
                                turns = settings.reader.prefetchTurns,
                                vertical = settings.reader.readingDirection == ReaderReadingDirection.Vertical,
                                onTurnsChanged = { turns ->
                                    scope.launch { settingsStore.setPrefetchTurns(turns) }
                                }
                            )
                        }
                    }
                }

                // Section 3: Page Turns & Animation
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Page Turns & Navigation",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        val transitionsActive = settings.reader.pageTransitionAnimation
                        val currentTurnMode = settings.reader.pageTurnMode

                        RadioSettingRow(
                            title = "Off",
                            subtitle = "Instant page changes with no transition",
                            selected = !transitionsActive,
                            onClick = {
                                scope.launch { settingsStore.setPageTransitionAnimation(false) }
                            }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Slide",
                            subtitle = "Fast, stable horizontal slide animation",
                            selected = transitionsActive && currentTurnMode == PageTurnMode.Slide,
                            onClick = {
                                scope.launch {
                                    settingsStore.setPageTransitionAnimation(true)
                                    settingsStore.setPageTurnMode(PageTurnMode.Slide)
                                }
                            }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Play Curl",
                            subtitle = "Play Books-style soft page fold for portrait and landscape spreads",
                            selected = transitionsActive && currentTurnMode == PageTurnMode.PlayCurl,
                            onClick = {
                                scope.launch {
                                    settingsStore.setPageTransitionAnimation(true)
                                    settingsStore.setPageTurnMode(PageTurnMode.PlayCurl)
                                }
                            }
                        )
                        CategoryRowGap()
                        SwitchSettingRow(
                            title = "Spread Shift Buttons",
                            subtitle = "Shows +/- 1 shift buttons in reader menu to correct two-page spread alignments",
                            checked = settings.reader.showSpreadShiftButtons,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setShowSpreadShiftButtons(enabled) }
                            }
                        )
                        CategoryRowGap()
                        SwitchSettingRow(
                            title = "Overview Mode",
                            subtitle = "Shows a thumbnail gallery of pages when opening the reader menu or pinching. When disabled, reader controls overlay directly on top of the page.",
                            checked = settings.reader.overviewMode,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setOverviewMode(enabled) }
                            }
                        )
                    }
                }

                // Section 3b: Tap Zones & Navigation
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tap Zones",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        val currentNav = settings.reader.navigationMode
                        RadioSettingRow(
                            title = "Default",
                            subtitle = "Left column back, center menu, right column forward",
                            selected = currentNav == ReaderNavigationMode.Default,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.Default) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "L-shaped",
                            subtitle = "Top & left edges back, center menu, right & bottom edges forward",
                            selected = currentNav == ReaderNavigationMode.LShaped,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.LShaped) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Kindle-ish",
                            subtitle = "Top third menu, narrow left strip back, remaining forward",
                            selected = currentNav == ReaderNavigationMode.Kindlish,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.Kindlish) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Edge",
                            subtitle = "Left & right edges forward, center bottom back, center top menu",
                            selected = currentNav == ReaderNavigationMode.Edge,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.Edge) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Right & Left",
                            subtitle = "Left half back, center menu, right half forward",
                            selected = currentNav == ReaderNavigationMode.RightAndLeft,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.RightAndLeft) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Disabled",
                            subtitle = "Turn pages using swipe gestures only; tapping opens menu",
                            selected = currentNav == ReaderNavigationMode.Disabled,
                            onClick = { scope.launch { settingsStore.setNavigationMode(ReaderNavigationMode.Disabled) } }
                        )
                    }
                }

                if (settings.reader.navigationMode != ReaderNavigationMode.Disabled) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Invert Tap Zone",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        SettingsSectionCard {
                            val currentInvert = settings.reader.tappingInvertMode
                            RadioSettingRow(
                                title = "None",
                                subtitle = "Default tapping orientation",
                                selected = currentInvert == ReaderTappingInvertMode.None,
                                onClick = { scope.launch { settingsStore.setTappingInvertMode(ReaderTappingInvertMode.None) } }
                            )
                            CategoryRowGap()
                            RadioSettingRow(
                                title = "Horizontal",
                                subtitle = "Invert left and right tap zones",
                                selected = currentInvert == ReaderTappingInvertMode.Horizontal,
                                onClick = { scope.launch { settingsStore.setTappingInvertMode(ReaderTappingInvertMode.Horizontal) } }
                            )
                            CategoryRowGap()
                            RadioSettingRow(
                                title = "Vertical",
                                subtitle = "Invert top and bottom tap zones",
                                selected = currentInvert == ReaderTappingInvertMode.Vertical,
                                onClick = { scope.launch { settingsStore.setTappingInvertMode(ReaderTappingInvertMode.Vertical) } }
                            )
                            CategoryRowGap()
                            RadioSettingRow(
                                title = "Both",
                                subtitle = "Invert both horizontal and vertical tap zones",
                                selected = currentInvert == ReaderTappingInvertMode.Both,
                                onClick = { scope.launch { settingsStore.setTappingInvertMode(ReaderTappingInvertMode.Both) } }
                            )
                        }
                    }
                }

                // Section 3c: Scale Type & Borders
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Image Scale & Borders",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        val currentScale = settings.reader.imageScaleType
                        RadioSettingRow(
                            title = "Fit screen",
                            subtitle = "Scale image to fit screen preserving aspect ratio",
                            selected = currentScale == ReaderImageScaleType.FitScreen,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.FitScreen) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Stretch",
                            subtitle = "Stretch image to fill entire screen",
                            selected = currentScale == ReaderImageScaleType.Stretch,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.Stretch) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Fit width",
                            subtitle = "Scale image width to fit screen width",
                            selected = currentScale == ReaderImageScaleType.FitWidth,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.FitWidth) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Fit height",
                            subtitle = "Scale image height to fit screen height",
                            selected = currentScale == ReaderImageScaleType.FitHeight,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.FitHeight) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Original size",
                            subtitle = "Display image at 1:1 original pixel scale",
                            selected = currentScale == ReaderImageScaleType.OriginalSize,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.OriginalSize) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Smart fit",
                            subtitle = "Automatically fits width or screen based on image aspect ratio",
                            selected = currentScale == ReaderImageScaleType.SmartFit,
                            onClick = { scope.launch { settingsStore.setImageScaleType(ReaderImageScaleType.SmartFit) } }
                        )
                        CategoryRowGap()
                        SwitchSettingRow(
                            title = "Crop Borders",
                            subtitle = "Automatically detects and trims whitespace or dark scan borders",
                            checked = settings.reader.cropBorders,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setCropBorders(enabled) }
                            }
                        )
                    }
                }

                // Section 4: Page Margins & Background
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Page Margins & Background",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        RadioSettingRow(
                            title = "Dark Margins",
                            subtitle = "Comfortable soft dark margins around pages",
                            selected = settings.reader.pageBackground == PageBackground.Dark,
                            onClick = { scope.launch { settingsStore.setPageBackground(PageBackground.Dark) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Paper Margins",
                            subtitle = "Warm off-white paper color for margins",
                            selected = settings.reader.pageBackground == PageBackground.Paper,
                            onClick = { scope.launch { settingsStore.setPageBackground(PageBackground.Paper) } }
                        )
                        CategoryRowGap()
                        SwitchSettingRow(
                            title = "Pure AMOLED Margins",
                            subtitle = "Uses pure #000000 black for Dark and pure #FFFFFF for Paper",
                            checked = settings.reader.usePurePageBackgroundColors,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setUsePurePageBackgroundColors(enabled) }
                            }
                        )
                    }
                }

                // Section 5: Night Mode / Invert
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Night Invert",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    SettingsSectionCard {
                        RadioSettingRow(
                            title = "Off",
                            subtitle = "Original page colors preserved as authored",
                            selected = settings.reader.invertMode == InvertMode.Off,
                            onClick = { scope.launch { settingsStore.setInvertMode(InvertMode.Off) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Smart Invert",
                            subtitle = "Inverts text pages for night reading while preserving illustrations",
                            selected = settings.reader.invertMode == InvertMode.Smart,
                            onClick = { scope.launch { settingsStore.setInvertMode(InvertMode.Smart) } }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Always Invert",
                            subtitle = "Inverts all pages unconditionally",
                            selected = settings.reader.invertMode == InvertMode.Always,
                            onClick = { scope.launch { settingsStore.setInvertMode(InvertMode.Always) } }
                        )

                        if (settings.reader.invertMode == InvertMode.Smart) {
                            CategoryRowGap()
                            val threshold = settings.reader.invertWhiteThreshold
                            var thresholdDraft by remember(threshold) { mutableStateOf(threshold) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Illustration Threshold",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pages at least ${(thresholdDraft * 100f).roundToInt()}% white are treated as text and inverted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ValueBubbleSlider(
                                    value = thresholdDraft,
                                    onValueChange = { thresholdDraft = it },
                                    onValueChangeFinished = {
                                        scope.launch { settingsStore.setInvertWhiteThreshold(thresholdDraft) }
                                    },
                                    valueRange = 0.2f..0.9f,
                                    valueLabel = { value -> "${(value * 100f).roundToInt()}%" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefetchTurnsSetting(
    turns: Int,
    vertical: Boolean,
    onTurnsChanged: (Int) -> Unit
) {
    var draft by remember(turns) { mutableStateOf(turns.toFloat()) }
    val draftTurns = draft.roundToInt().coerceIn(0, MaxReaderPrefetchTurns)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Preload ahead: ${prefetchTurnsSummary(draftTurns, vertical)}",
            style = MaterialTheme.typography.bodyMedium
        )
        ValueBubbleSlider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onTurnsChanged(draftTurns) },
            valueRange = 0f..MaxReaderPrefetchTurns.toFloat(),
            steps = MaxReaderPrefetchTurns - 1,
            valueLabel = { value ->
                value.roundToInt().let { if (it == 0) "Off" else it.toString() }
            }
        )
    }
}

private fun prefetchTurnsSummary(turns: Int, vertical: Boolean): String {
    return if (turns == 0) {
        "Off"
    } else if (vertical) {
        "$turns pages"
    } else {
        "$turns turns (up to ${turns * 2} pages)"
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingRow(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
