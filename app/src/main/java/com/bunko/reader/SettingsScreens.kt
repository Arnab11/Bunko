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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import com.bunko.reader.settings.CategoryRowGap
import com.bunko.reader.settings.ClickableSettingRow
import com.bunko.reader.settings.ExperimentalBadge
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
            title = {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            },
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
    var passwordVisible by remember { mutableStateOf(false) }
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
    var isTestingConnection by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

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
        passwordVisible = false
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
                    "Media Servers"
                } else {
                    if (selectedProfileId != null) "Edit Server" else "Add Server"
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (page == ServerSettingsPage.Servers) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Configured Servers",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        if (profiles.isEmpty()) {
                            SettingsSectionCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudQueue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "No servers configured",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Add your Kavita media server to stream and read books.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            SettingsSectionCard {
                                profiles.forEachIndexed { index, profile ->
                                    if (index > 0) {
                                        CategoryRowGap()
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectDefaultProfile(profile) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (profile.openByDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.Dns,
                                                    contentDescription = null,
                                                    tint = if (profile.openByDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = profile.name.ifBlank { "Kavita Server" },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (profile.openByDefault) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Active",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = profile.session.baseUrl.ifBlank { "No URL configured" },
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
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit server",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionCard {
                            ClickableSettingRow(
                                title = "Add New Server",
                                subtitle = "Connect to another Kavita server instance",
                                icon = Icons.Filled.Add,
                                onClick = {
                                    selectedProfileId = null
                                    page = ServerSettingsPage.ServerSelected
                                    baseUrl = ""
                                    username = ""
                                    password = ""
                                    passwordVisible = false
                                    apiKey = ""
                                    openByDefault = profiles.none { it.openByDefault }
                                    serverStatus = "Not configured"
                                    jwtStatus = "Logged out"
                                    authStatus = "Not saved"
                                    storageStatus = "No saved auth"
                                    status = null
                                }
                            )
                        }
                    }

                    Text(
                        text = "Bunko connects directly to your Kavita server using its REST API. Ensure your server URL includes the port and protocol (e.g. http:// or https://).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    // Server Details Page
                    if (status != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (status!!.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (status!!.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (status!!.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = status!!.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (status!!.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Section 1: Server Connection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Server Connection",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        SettingsSectionCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = { Text("Server URL") },
                                    placeholder = { Text("https://kavita.example.com") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Link, contentDescription = null)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            CategoryRowGap()
                            SwitchSettingRow(
                                title = "Default Server",
                                subtitle = "Automatically connect to this server when Bunko launches",
                                checked = openByDefault,
                                onCheckedChange = { openByDefault = it }
                            )
                        }
                    }

                    // Section 2: Authentication
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Authentication",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        SettingsSectionCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Person, contentDescription = null)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Lock, contentDescription = null)
                                    },
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
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Password is only used once to obtain a secure token from Kavita. It is never stored on disk.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = { Text("Auth Key (API Key)") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Key, contentDescription = null)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "Find your API key in Kavita Settings → 3rd Party Clients (x-api-key). Required for image loading.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            status = null
                                            isLoggingIn = true
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
                                                status = StatusMessage("Successfully logged in and saved", isError = false)
                                            } catch (t: HttpException) {
                                                val body = t.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
                                                status = StatusMessage("Login failed: HTTP ${t.code()}: ${body ?: t.message()}", isError = true)
                                            } catch (t: Throwable) {
                                                status = StatusMessage("Login failed: ${t.message ?: t.toString()}", isError = true)
                                            } finally {
                                                isLoggingIn = false
                                                restoreDefaultProfile()
                                                onActiveServerChanged()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    enabled = !isLoggingIn
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (isLoggingIn) "Authenticating..." else "Login & Authenticate")
                                }
                            }
                        }
                    }

                    // Section 3: Configuration & Diagnostics
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Connection & Status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        SettingsSectionCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Server Endpoint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(serverStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Saved Auth", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(authStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Session Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(jwtStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Keystore Storage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(storageStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                            CategoryRowGap()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            status = null
                                            isTestingConnection = true
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
                                                status = StatusMessage("Connection healthy (/api/Health OK)", isError = false)
                                            } catch (t: Throwable) {
                                                status = StatusMessage("Health check failed: ${t.message ?: t.toString()}", isError = true)
                                            } finally {
                                                isTestingConnection = false
                                                restoreDefaultProfile()
                                                onActiveServerChanged()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    enabled = !isTestingConnection
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (isTestingConnection) "Testing..." else "Test")
                                }

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
                                                status = StatusMessage("Configuration saved", isError = false)
                                            } catch (t: Throwable) {
                                                status = StatusMessage("Save failed: ${t.message ?: t.toString()}", isError = true)
                                            } finally {
                                                restoreDefaultProfile()
                                                onActiveServerChanged()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }

                    // Section 4: Danger Zone
                    if (selectedProfileId != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Danger Zone",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            SettingsSectionCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
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
                                                    status = StatusMessage("Saved credentials cleared", isError = false)
                                                } catch (t: Throwable) {
                                                    status = StatusMessage("Clear auth failed: ${t.message ?: t.toString()}", isError = true)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Text("Clear Auth")
                                    }

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
                                                status = StatusMessage("Server deleted", isError = false)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text("Forget")
                                    }
                                }
                            }
                        }
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
                            title = "Book",
                            subtitle = "Realistic 3D book page curl for portrait and landscape spreads",
                            selected = transitionsActive && currentTurnMode == PageTurnMode.PlayCurl,
                            trailingTitleContent = {
                                ExperimentalBadge()
                            },
                            onClick = {
                                scope.launch {
                                    settingsStore.setPageTransitionAnimation(true)
                                    settingsStore.setPageTurnMode(PageTurnMode.PlayCurl)
                                }
                            }
                        )
                        CategoryRowGap()
                        RadioSettingRow(
                            title = "Curl",
                            subtitle = "Interactive 3D page curl with corner crease and physics",
                            selected = transitionsActive && currentTurnMode == PageTurnMode.Curl,
                            trailingTitleContent = {
                                ExperimentalBadge()
                            },
                            onClick = {
                                scope.launch {
                                    settingsStore.setPageTransitionAnimation(true)
                                    settingsStore.setPageTurnMode(PageTurnMode.Curl)
                                }
                            }
                        )
                        if (transitionsActive && currentTurnMode == PageTurnMode.Curl) {
                            CategoryRowGap()
                            SwitchSettingRow(
                                title = "Page-Back Show-Through",
                                subtitle = "Shows a faint mirror of the page on the back of a curled portrait sheet",
                                checked = settings.reader.showPortraitPageBackContent,
                                onCheckedChange = { enabled ->
                                    scope.launch { settingsStore.setShowPortraitPageBackContent(enabled) }
                                }
                            )
                        }
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
