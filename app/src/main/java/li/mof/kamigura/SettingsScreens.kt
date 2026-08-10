package li.mof.kamigura

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
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
import li.mof.kamigura.ui.ValueBubbleSlider
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
                    text = "Kamigura v0.21",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderSettingsScreen(
    settingsStore: AppSettingsStore,
    onBack: () -> Unit
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
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SettingsTopAppBar(title = "Reader Settings", onBack = onBack)
        Column(
            Modifier
                .widthIn(max = SettingsFormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingRow(
                title = "Right-to-left",
                desc = "Fallback page-turn direction, used only when a series has no reading " +
                    "direction of its own on the Kavita server. Series with their own setting follow that.",
                checked = settings.reader.rightToLeft,
                onToggle = { v -> scope.launch { settingsStore.setRightToLeft(v) } }
            )

            Text("Page preloading", style = MaterialTheme.typography.titleMedium)
            Text(
                "One turn is one page turn. A two-page spread can preload two page images per turn.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            PrefetchTurnsSetting(
                turns = settings.reader.prefetchTurns,
                onTurnsChanged = { turns ->
                    scope.launch { settingsStore.setPrefetchTurns(turns) }
                }
            )

            SettingRow(
                title = "Page transition animation",
                desc = "Slides pages with a small amount of depth. Turn this off to use instant page changes.",
                checked = settings.reader.pageTransitionAnimation,
                onToggle = { enabled ->
                    scope.launch { settingsStore.setPageTransitionAnimation(enabled) }
                }
            )

            Text("Page turn", style = MaterialTheme.typography.titleMedium)
            Text(
                "Slide is the stable reader. Curl is experimental and applies to portrait single-page and landscape spread reading.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                val modes = PageTurnMode.entries
                modes.forEachIndexed { index, mode ->
                    ToggleButton(
                        checked = settings.reader.pageTurnMode == mode,
                        onCheckedChange = { scope.launch { settingsStore.setPageTurnMode(mode) } },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) { Text(mode.name) }
                }
            }

            SettingRow(
                title = "Page-back show-through",
                desc = "Shows a faint mirror of the page on the back of a curled portrait sheet.",
                checked = settings.reader.showPortraitPageBackContent,
                onToggle = { enabled ->
                    scope.launch { settingsStore.setShowPortraitPageBackContent(enabled) }
                }
            )

            Text("Page background", style = MaterialTheme.typography.titleMedium)
            Text(
                "Colour of the margins around a page, and of the back of a curling page. " +
                    "Invert always uses the dark one.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                val backgrounds = PageBackground.entries
                backgrounds.forEachIndexed { index, background ->
                    ToggleButton(
                        checked = settings.reader.pageBackground == background,
                        onCheckedChange = { scope.launch { settingsStore.setPageBackground(background) } },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            backgrounds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) { Text(background.name) }
                }
            }

            SettingRow(
                title = "Pure black / white margins",
                desc = "Uses #000000 for Dark and #FFFFFF for Paper instead of the softer colours.",
                checked = settings.reader.usePurePageBackgroundColors,
                onToggle = { enabled ->
                    scope.launch { settingsStore.setUsePurePageBackgroundColors(enabled) }
                }
            )

            SettingRow(
                title = "Spread shift buttons",
                desc = "Shows the +1 / -1 buttons in the reader menu (landscape) to correct a " +
                    "one-page spread misalignment. Edge long-press does the same either way.",
                checked = settings.reader.showSpreadShiftButtons,
                onToggle = { enabled ->
                    scope.launch { settingsStore.setShowSpreadShiftButtons(enabled) }
                }
            )

            Text("Invert (night)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Off shows pages as-is. Smart inverts text pages and skips illustrations. Always inverts every page.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                val modes = InvertMode.entries
                modes.forEachIndexed { index, mode ->
                    ToggleButton(
                        checked = settings.reader.invertMode == mode,
                        onCheckedChange = { scope.launch { settingsStore.setInvertMode(mode) } },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) { Text(mode.name) }
                }
            }
            if (settings.reader.invertMode == InvertMode.Smart) {
                val threshold = settings.reader.invertWhiteThreshold
                var thresholdDraft by remember(threshold) { mutableStateOf(threshold) }
                Text(
                    "Illustration threshold: pages at least ${(thresholdDraft * 100f).roundToInt()}% white are treated as text and inverted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
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

@Composable
private fun PrefetchTurnsSetting(
    turns: Int,
    onTurnsChanged: (Int) -> Unit
) {
    var draft by remember(turns) { mutableStateOf(turns.toFloat()) }
    val draftTurns = draft.roundToInt().coerceIn(0, MaxReaderPrefetchTurns)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Preload ahead: ${prefetchTurnsSummary(draftTurns)}",
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

private fun prefetchTurnsSummary(turns: Int): String {
    return if (turns == 0) {
        "Off"
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
