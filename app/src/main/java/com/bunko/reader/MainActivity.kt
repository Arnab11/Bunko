package com.bunko.reader

import android.os.Bundle
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import com.bunko.reader.ui.theme.BunkoBackground
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.bunko.reader.ui.theme.BunkoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.bunko.reader.library.BookmarksScreen
import com.bunko.reader.library.CollectionsScreen
import com.bunko.reader.library.DownloadedScreen
import com.bunko.reader.library.LibraryScreen
import com.bunko.reader.library.HomeShelfKind
import com.bunko.reader.library.SearchSeriesScreen
import com.bunko.reader.library.SearchSeriesTarget
import com.bunko.reader.library.SeriesShelfScreen
import com.bunko.reader.download.OfflineIssueRepository
import com.bunko.reader.reader.ReaderScreen
import com.bunko.reader.series.ChapterPickScreen
import com.bunko.reader.series.SeriesScreen
import com.bunko.reader.update.AvailableUpdate
import com.bunko.reader.update.GitHubUpdateChecker
import com.bunko.reader.offline.LocalBookRepository
import com.bunko.reader.offline.OfflineStartupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionStore = KavitaSessionStore(this)
        val settingsStore = AppSettingsStore(this)
        val allowIntentLogin = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val loginDefaults = LoginDefaults(
            baseUrl = if (allowIntentLogin) intent.getStringExtra("serverUrl").orEmpty() else "",
            apiKey = if (allowIntentLogin) intent.getStringExtra("apiKey").orEmpty() else "",
            username = if (allowIntentLogin) intent.getStringExtra("username").orEmpty() else "",
            password = if (allowIntentLogin) intent.getStringExtra("password").orEmpty() else "",
            autoLogin = allowIntentLogin && intent.getBooleanExtra("autoLogin", false),
            debugLibraryId = if (allowIntentLogin) intent.getIntExtra("debugLibraryId", 0) else 0,
            debugSeriesId = if (allowIntentLogin) intent.getIntExtra("debugSeriesId", 0) else 0,
            debugSeriesName = if (allowIntentLogin) intent.getStringExtra("debugSeriesName").orEmpty() else ""
        )

        setContent {
            BunkoTheme {
                AppRoot(sessionStore, settingsStore, loginDefaults)
            }
        }
    }
}

@Composable
fun AppRoot(
    sessionStore: KavitaSessionStore,
    settingsStore: AppSettingsStore,
    loginDefaults: LoginDefaults = LoginDefaults()
) {
    val nav = rememberNavController()
    val ctx = LocalContext.current

    var sessionRevision by remember { mutableIntStateOf(0) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var updateNoticeShown by rememberSaveable { mutableStateOf(false) }
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }
    val localRepository = remember(ctx) { LocalBookRepository(ctx) }
    var installedImageLoader by remember { mutableStateOf<ImageLoader?>(null) }

    fun installImageLoader(loader: ImageLoader?) {
        val next = loader ?: ImageLoader(ctx)
        val previous = installedImageLoader
        if (previous !== next) {
            // A Coil DiskCache directory must have a single active owner.
            previous?.shutdown()
            Coil.setImageLoader(next)
            installedImageLoader = next
        }
    }

    DisposableEffect(Unit) {
        onDispose { installedImageLoader?.shutdown() }
    }

    LaunchedEffect(Unit) {
        installImageLoader(null)
        @Suppress("DEPRECATION")
        val currentVersion = ctx.packageManager
            .getPackageInfo(ctx.packageName, 0)
            .versionName
            .orEmpty()
        availableUpdate = GitHubUpdateChecker(ctx).check(currentVersion)
    }

    LaunchedEffect(sessionRevision) {
        runCatching {
            val session = sessionStore.load()
            if (session.baseUrl.isNotBlank() && (session.jwt.isNotBlank() || session.apiKey.isNotBlank())) {
                val (api, _) = KavitaClient(ctx, sessionStore).buildApi()
                offlineRepository.syncPending(session, api)
            }
        }.onFailure {
            BunkoLog.w("Could not sync pending offline progress during app startup.", it)
        }
    }

    suspend fun refreshActiveServer() {
        sessionRevision += 1
        val nextImageLoader = try {
            val session = sessionStore.load()
            if (session.baseUrl.isBlank() || (session.jwt.isBlank() && session.apiKey.isBlank())) {
                null
            } else {
                val client = KavitaClient(ctx, sessionStore)
                val (_, okHttp) = client.buildApi()
                client.buildImageLoader(okHttp, session)
            }
        } catch (t: Throwable) {
            BunkoLog.w("Could not refresh active server image loader.", t)
            null
        }
        installImageLoader(nextImageLoader)
    }

    val scope = rememberCoroutineScope()
    val startupCompleted by localRepository.startupCompletedFlow.collectAsState(initial = null)
    val activeMode by localRepository.activeModeFlow.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val session = sessionStore.load()
        if (session.baseUrl.isNotBlank() && (session.jwt.isNotBlank() || session.apiKey.isNotBlank())) {
            localRepository.setStartupCompleted(true)
        }
    }

    if (startupCompleted == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BunkoBackground)
        )
        return
    }

    val resolvedStart = remember {
        if (!startupCompleted!!) {
            "startup"
        } else {
            "libraries"
        }
    }

    NavHost(navController = nav, startDestination = resolvedStart) {
        composable("startup") {
            OfflineStartupScreen(
                localRepository = localRepository,
                sessionStore = sessionStore,
                offlineRepository = offlineRepository,
                onOpenOfflineLibrary = {
                    scope.launch { localRepository.setActiveMode("offline") }
                    nav.navigate("libraries") {
                        popUpTo("startup") { inclusive = true }
                    }
                },
                onOpenDownloaded = { nav.navigate("downloaded") },
                onConnectKavita = {
                    scope.launch { localRepository.setActiveMode("kavita") }
                    val client = KavitaClient(ctx, sessionStore)
                    val session = sessionStore.load()
                    val (_, okHttp) = client.buildApi()
                    installImageLoader(client.buildImageLoader(okHttp, session))
                    sessionRevision += 1
                    val destination = if (
                        loginDefaults.debugLibraryId > 0 && loginDefaults.debugSeriesId > 0
                    ) {
                        "chapters/${loginDefaults.debugLibraryId}/${loginDefaults.debugSeriesId}/" +
                            Uri.encode(loginDefaults.debugSeriesName)
                    } else {
                        "libraries"
                    }
                    nav.navigate(destination) {
                        popUpTo("startup") { inclusive = true }
                    }
                },
                onOpenServerSettings = { nav.navigate("settings/server") }
            )
        }

        composable(
            route = "local-reader/{bookId}?page={page}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStack ->
            val bookId = backStack.arguments!!.getString("bookId").orEmpty()
            val initialPage = backStack.arguments!!.getInt("page").takeIf { it >= 0 }
            ReaderScreen(
                sessionStore = sessionStore,
                settingsStore = settingsStore,
                localBookId = bookId,
                localRepository = localRepository,
                initialPage = initialPage,
                onBack = { nav.popBackStack() }
            )
        }
            composable("login") {
                LoginScreen(
                    sessionStore = sessionStore,
                    defaults = loginDefaults,
                    onLoggedIn = {
                        val client = KavitaClient(ctx, sessionStore)
                        val session = sessionStore.load()
                        val (_, okHttp) = client.buildApi()
                        installImageLoader(client.buildImageLoader(okHttp, session))
                        sessionRevision += 1
                        val destination = if (
                            loginDefaults.debugLibraryId > 0 && loginDefaults.debugSeriesId > 0
                        ) {
                            "chapters/${loginDefaults.debugLibraryId}/${loginDefaults.debugSeriesId}/" +
                                Uri.encode(loginDefaults.debugSeriesName)
                        } else {
                            "libraries"
                        }
                        nav.navigate(destination) {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    hasOfflineDownloads = {
                        val saved = sessionStore.loadDefault()
                        saved.baseUrl.isNotBlank() &&
                            offlineRepository.observeDownloaded(saved).first().isNotEmpty()
                    },
                    onOpenOffline = {
                        val saved = sessionStore.loadDefault()
                        sessionStore.useTransient(saved)
                        installImageLoader(null)
                        sessionRevision += 1
                        nav.navigate("libraries") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onOpenServerSettings = { nav.navigate("settings/server") }
                )
            }

            composable(
                route = "libraries?search={search}",
                arguments = listOf(
                    navArgument("search") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStack ->
                LibraryScreen(
                    sessionStore = sessionStore,
                    sessionRevision = sessionRevision,
                    localRepository = localRepository,
                    initialIsOffline = (activeMode == "offline"),
                    initialSearchQuery = backStack.arguments!!.getString("search").orEmpty(),
                    availableUpdate = availableUpdate?.takeUnless { updateNoticeShown },
                    onOpenUpdate = { releaseUrl ->
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                        }.onFailure {
                            BunkoLog.w("Could not open release URL.", it)
                        }
                    },
                    onUpdateNoticeShown = { updateNoticeShown = true },
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenShelf = { shelfKind -> nav.navigate("shelf/${shelfKind.routeValue}") },
                    onOpenBookmarks = { nav.navigate("bookmarks") },
                    onOpenCollections = { nav.navigate("collections") },
                    onOpenDownloaded = { nav.navigate("downloaded") },
                    onOpenFilteredSeries = { target, id, label ->
                        nav.navigate("search-series/${target.routeValue}/$id/${Uri.encode(label)}")
                    },
                    onSelectLibrary = { lib -> nav.navigate("series/${lib.id}/${lib.name}") },
                    onSelectSeries = { series ->
                        val libraryId = series.libraryId ?: 0
                        nav.navigate("chapters/$libraryId/${series.id}/${series.name}")
                    },
                    onOpenOfflineBook = { book ->
                        nav.navigate("local-reader/${book.id}?page=${book.lastReadPage}")
                    },
                    onRequireLogin = {
                        nav.navigate("login")
                    }
                )
            }

            composable("bookmarks") {
                BookmarksScreen(
                    sessionStore = sessionStore,
                    onBack = { nav.popBackStack() },
                    onOpenBookmark = { libraryId, seriesId, volumeId, chapterId, page ->
                        nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=false&page=$page")
                    }
                )
            }

            composable("collections") {
                CollectionsScreen(
                    sessionStore = sessionStore,
                    onBack = { nav.popBackStack() },
                    onOpenCollection = { collection ->
                        nav.navigate(
                            "search-series/${SearchSeriesTarget.Collection.routeValue}/" +
                                "${collection.id}/${Uri.encode(collection.title)}"
                        )
                    }
                )
            }

            composable("downloaded") {
                DownloadedScreen(
                    sessionStore = sessionStore,
                    onBack = { nav.popBackStack() },
                    onPickIssue = { libraryId, seriesId, volumeId, chapterId, incognito ->
                        nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=$incognito")
                    }
                )
            }

            composable(
                route = "shelf/{shelfKind}",
                arguments = listOf(
                    navArgument("shelfKind") { type = NavType.StringType }
                )
            ) { backStack ->
                val shelfKind = HomeShelfKind.fromRouteValue(
                    backStack.arguments!!.getString("shelfKind")
                ) ?: HomeShelfKind.OnDeck
                SeriesShelfScreen(
                    sessionStore = sessionStore,
                    shelfKind = shelfKind,
                    onBack = { nav.popBackStack() },
                    onSelectSeries = { series ->
                        val libraryId = series.libraryId ?: 0
                        nav.navigate("chapters/$libraryId/${series.id}/${series.name}")
                    }
                )
            }

            composable(
                route = "search-series/{target}/{targetId}/{label}",
                arguments = listOf(
                    navArgument("target") { type = NavType.StringType },
                    navArgument("targetId") { type = NavType.IntType },
                    navArgument("label") { type = NavType.StringType }
                )
            ) { backStack ->
                val target = SearchSeriesTarget.fromRouteValue(
                    backStack.arguments!!.getString("target")
                ) ?: SearchSeriesTarget.Genre
                val targetId = backStack.arguments!!.getInt("targetId")
                val label = backStack.arguments!!.getString("label") ?: ""
                SearchSeriesScreen(
                    sessionStore = sessionStore,
                    target = target,
                    targetId = targetId,
                    label = label,
                    onBack = { nav.popBackStack() },
                    onSelectSeries = { series ->
                        val libraryId = series.libraryId ?: 0
                        nav.navigate("chapters/$libraryId/${series.id}/${series.name}")
                    }
                )
            }

            composable(
                route = "series/{libraryId}/{libraryName}",
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.IntType },
                    navArgument("libraryName") { type = NavType.StringType }
                )
            ) { backStack ->
                val libraryId = backStack.arguments!!.getInt("libraryId")
                val libraryName = backStack.arguments!!.getString("libraryName") ?: ""
                SeriesScreen(
                    sessionStore = sessionStore,
                    libraryId = libraryId,
                    libraryName = libraryName,
                    onBack = { nav.popBackStack() },
                    onSearchHome = { query ->
                        nav.navigate("libraries?search=${Uri.encode(query)}")
                    },
                    onSelect = { s ->
                        nav.navigate("chapters/$libraryId/${s.id}/${s.name}")
                    }
                )
            }

            composable(
                route = "chapters/{libraryId}/{seriesId}/{seriesName}",
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.IntType },
                    navArgument("seriesId") { type = NavType.IntType },
                    navArgument("seriesName") { type = NavType.StringType }
                )
            ) { backStack ->
                val libraryId = backStack.arguments!!.getInt("libraryId")
                val seriesId = backStack.arguments!!.getInt("seriesId")
                val seriesName = backStack.arguments!!.getString("seriesName") ?: ""
                ChapterPickScreen(
                    sessionStore,
                    libraryId,
                    seriesId,
                    seriesName,
                    onOpenFilteredSeries = { target, id, label ->
                        nav.navigate("search-series/${target.routeValue}/$id/${Uri.encode(label)}")
                    }
                ) { chapterId, volumeId, incognito ->
                    nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=$incognito")
                }
            }

            composable(
                route = "reader/{libraryId}/{seriesId}/{volumeId}/{chapterId}?incognito={incognito}&page={page}",
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.IntType },
                    navArgument("seriesId") { type = NavType.IntType },
                    navArgument("volumeId") { type = NavType.IntType },
                    navArgument("chapterId") { type = NavType.IntType },
                    navArgument("incognito") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("page") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStack ->
                val libraryId = backStack.arguments!!.getInt("libraryId")
                val seriesId = backStack.arguments!!.getInt("seriesId")
                val volumeId = backStack.arguments!!.getInt("volumeId")
                val chapterId = backStack.arguments!!.getInt("chapterId")
                val incognito = backStack.arguments!!.getBoolean("incognito")
                val initialPage = backStack.arguments!!.getInt("page").takeIf { it >= 0 }
                ReaderScreen(
                    sessionStore,
                    settingsStore,
                    libraryId,
                    seriesId,
                    volumeId,
                    chapterId,
                    incognito = incognito,
                    initialPage = initialPage,
                    onBack = { nav.popBackStack() }
                )
            }

            composable("settings") {
                SettingsHubScreen(
                    onServer = { nav.navigate("settings/server") },
                    onReader = { nav.navigate("settings/reader") },
                    onStorage = { nav.navigate("settings/cache") },
                    onBack = { nav.popBackStack() }
                )
            }

            composable("settings/server") {
                ServerSettingsScreen(
                    sessionStore = sessionStore,
                    onActiveServerChanged = { refreshActiveServer() },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("settings/reader") {
                ReaderSettingsScreen(settingsStore = settingsStore, onBack = { nav.popBackStack() })
            }
            composable("settings/cache") {
                CacheSettingsScreen(onBack = { nav.popBackStack() })
            }
        }
}

data class LoginDefaults(
    val baseUrl: String = "",
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val autoLogin: Boolean = false,
    val debugLibraryId: Int = 0,
    val debugSeriesId: Int = 0,
    val debugSeriesName: String = ""
)

@Composable
fun LoginScreen(
    sessionStore: KavitaSessionStore,
    defaults: LoginDefaults = LoginDefaults(),
    onLoggedIn: suspend () -> Unit,
    hasOfflineDownloads: suspend () -> Boolean,
    onOpenOffline: suspend () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var offlineAvailable by remember { mutableStateOf(false) }

    suspend fun connectSaved(showMissingAuthError: Boolean = true) {
        busy = true
        error = null
        try {
            val saved = sessionStore.loadDefault()
            if (saved.baseUrl.isBlank() || (saved.jwt.isBlank() && saved.apiKey.isBlank())) {
                if (!showMissingAuthError) return
                error = "No default server auth. Add a server or mark one as default."
                onOpenServerSettings()
                return
            }
            val client = KavitaClient(ctx, sessionStore)
            val (api, _) = client.buildApi()
            api.health()
            onLoggedIn()
        } catch (t: Throwable) {
            error = "Connect failed: ${t.message ?: t.toString()}"
        } finally {
            busy = false
        }
    }

    suspend fun attemptDebugLogin() {
        busy = true
        error = null
        try {
            val cleanBaseUrl = defaults.baseUrl.trim()
            val cleanUsername = defaults.username.trim()
            val cleanApiKey = defaults.apiKey.trim()
            if (cleanBaseUrl.isBlank()) throw IllegalArgumentException("Server URL is required")
            if (cleanApiKey.isBlank()) throw IllegalArgumentException("Auth Key is required for image loading")
            sessionStore.useTransient(
                KavitaSession(baseUrl = cleanBaseUrl, username = cleanUsername, apiKey = "", jwt = "")
            )
            val client = KavitaClient(ctx, sessionStore)
            val (api, _) = client.buildApi()
            val user = api.login(LoginDto(cleanUsername, defaults.password, cleanApiKey.ifBlank { null }))
            val jwt = user.token ?: throw IllegalStateException("No token returned")
            sessionStore.useTransient(
                KavitaSession(baseUrl = cleanBaseUrl, username = cleanUsername, apiKey = cleanApiKey, jwt = "")
            )
            val (apiKeyApi, _) = client.buildApi()
            apiKeyApi.userLibraries()
            sessionStore.save(
                KavitaSession(baseUrl = cleanBaseUrl, username = cleanUsername, apiKey = cleanApiKey, jwt = jwt)
            )
            onLoggedIn()
        } catch (t: Throwable) {
            error = "Debug login failed: ${t.message ?: t.toString()}"
        } finally {
            busy = false
        }
    }

    LaunchedEffect(defaults.autoLogin) {
        offlineAvailable = runCatching { hasOfflineDownloads() }
            .onFailure { BunkoLog.w("Could not check offline downloads on login screen.", it) }
            .getOrDefault(false)
        if (defaults.autoLogin && !busy) {
            attemptDebugLogin()
        } else if (!busy) {
            connectSaved(showMissingAuthError = false)
        }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Bunko", style = MaterialTheme.typography.headlineSmall)

            if (error != null) Text(error!!, color = Color.Red)

            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            connectSaved()
                        }
                    }
                ) { Text(if (busy) "Connecting..." else "Connect") }

                FilledTonalButton(
                    onClick = onOpenServerSettings,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Servers") }

                if (offlineAvailable) {
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { onOpenOffline() } }
                    ) { Text("Offline Downloads") }
                }
            }
        }
    }
}
