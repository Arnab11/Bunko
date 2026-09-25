package com.bunko.reader

import android.os.Bundle
import android.view.KeyEvent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bunko.reader.ui.theme.AppTheme
import com.bunko.reader.ui.theme.resolveBunkoColorScheme
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bunko.reader.settings.SettingsAdaptiveScreen
import com.bunko.reader.ui.theme.LocalToggleTheme
import com.bunko.reader.ui.theme.rememberThemeTransitionState
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.bunko.reader.ui.theme.BunkoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.bunko.reader.CollectionDto
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
import com.bunko.reader.update.UpdateSheet
import com.bunko.reader.update.UpdateState
import com.bunko.reader.update.rememberUpdateController
import com.bunko.reader.library.internal.HomeDestination
import com.bunko.reader.offline.LocalBookRepository
import com.bunko.reader.offline.OfflineStartupScreen

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    var volumeKeyHandler: ((Int) -> Boolean)? = null

    data class IncomingFile(val uri: Uri, val mimeType: String?)

    private val _incomingFile = MutableStateFlow<IncomingFile?>(null)
    val incomingFile: StateFlow<IncomingFile?> = _incomingFile.asStateFlow()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                if (uri != null) {
                    _incomingFile.value = IncomingFile(uri, intent.type)
                }
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                if (uri != null) {
                    _incomingFile.value = IncomingFile(uri, intent.type)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handler = volumeKeyHandler
            if (handler != null) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    handler(keyCode)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private data class EdgeToEdgeState(
        val isDarkMode: Boolean,
        val appTheme: AppTheme,
        val isAmoled: Boolean,
    )

    private var appliedEdgeToEdgeState: EdgeToEdgeState? = null

    private fun applyEdgeToEdge(
        isDarkMode: Boolean,
        appTheme: AppTheme = AppTheme.Default,
        isAmoled: Boolean = false,
    ) {
        val nextState = EdgeToEdgeState(isDarkMode, appTheme, isAmoled)
        if (appliedEdgeToEdgeState == nextState) return

        val lightScheme = resolveBunkoColorScheme(this, appTheme, isDarkMode = false, isAmoled = false)
        val darkScheme = resolveBunkoColorScheme(this, appTheme, isDarkMode = true, isAmoled = isAmoled)
        val synchronizedBarStyle = SystemBarStyle.auto(
            lightScrim = lightScheme.surfaceContainer.toArgb(),
            darkScrim = darkScheme.surfaceContainer.toArgb(),
        ) { isDarkMode }
        enableEdgeToEdge(
            statusBarStyle = synchronizedBarStyle,
            navigationBarStyle = synchronizedBarStyle,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        appliedEdgeToEdgeState = nextState
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(isDarkMode = true)
        handleIncomingIntent(intent)

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
            val appSettings by settingsStore.flow.collectAsState(initial = AppSettings())
            val themeTransitionState = rememberThemeTransitionState()
            val scope = rememberCoroutineScope()
            // Apply header-tap theme toggles instantly in memory so heavy tabs
            // (History / Want to Read) recompose with the new colors immediately
            // instead of waiting for the DataStore disk write + flow emission,
            // which previously made the reveal miss and the switch feel laggy.
            var immediateDark by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(appSettings.isDarkMode) {
                if (immediateDark == appSettings.isDarkMode) immediateDark = null
            }
            val effectiveDark = immediateDark ?: appSettings.isDarkMode

            LaunchedEffect(effectiveDark, appSettings.appTheme, appSettings.isAmoledMode) {
                if (themeTransitionState.isAnimating) {
                    snapshotFlow {
                        themeTransitionState.animationProgress.value to themeTransitionState.isAnimating
                    }.first { (progress, isAnimating) ->
                        !isAnimating || progress >= SYSTEM_BAR_THEME_SWITCH_PROGRESS
                    }
                }
                applyEdgeToEdge(
                    isDarkMode = effectiveDark,
                    appTheme = appSettings.appTheme,
                    isAmoled = appSettings.isAmoledMode,
                )
            }

            BunkoTheme(
                theme = appSettings.appTheme,
                isDarkMode = effectiveDark,
                isAmoledMode = appSettings.isAmoledMode,
                transitionState = themeTransitionState
            ) {
                // Single app-wide toggle: every header reads LocalToggleTheme,
                // so all pages share identical tap-title + reveal behavior.
                val toggleTheme: () -> Unit = {
                    val next = !effectiveDark
                    immediateDark = next
                    scope.launch { settingsStore.setDarkMode(next) }
                }
                CompositionLocalProvider(LocalToggleTheme provides toggleTheme) {
                    AppRoot(
                        sessionStore = sessionStore,
                        settingsStore = settingsStore,
                        loginDefaults = loginDefaults,
                        incomingFile = incomingFile,
                        onConsumeIncomingFile = { _incomingFile.value = null },
                        onToggleTheme = toggleTheme
                    )
                }
            }
        }
    }

    companion object {
        private const val SYSTEM_BAR_THEME_SWITCH_PROGRESS = 0.55f
    }
}

@Composable
fun AppRoot(
    sessionStore: KavitaSessionStore,
    settingsStore: AppSettingsStore,
    loginDefaults: LoginDefaults = LoginDefaults(),
    incomingFile: StateFlow<MainActivity.IncomingFile?> = MutableStateFlow(null),
    onConsumeIncomingFile: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val appSettings by settingsStore.flow.collectAsState(initial = AppSettings())

    var sessionRevision by remember { mutableIntStateOf(0) }
    val updateController = rememberUpdateController(ctx)
    val currentVersionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager
                .getPackageInfo(ctx.packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "0.23" }
    }
    val offlineRepository = remember(ctx) { OfflineIssueRepository(ctx) }
    val localRepository = remember(ctx) { LocalBookRepository(ctx) }
    var installedImageLoader by remember { mutableStateOf<ImageLoader?>(null) }

    fun installImageLoader(loader: ImageLoader?) {
        // Same software-bitmap requirement as the Kavita cover loader:
        // keeps View.drawToBitmap() (theme-reveal screenshot) working on
        // image-heavy screens.
        val next = loader ?: ImageLoader.Builder(ctx).allowHardware(false).build()
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

    val forceStartup = (ctx as? android.app.Activity)?.intent?.getBooleanExtra("force_startup", false) ?: false

    LaunchedEffect(forceStartup) {
        if (!forceStartup) {
            val session = sessionStore.load()
            if (session.baseUrl.isNotBlank() && (session.jwt.isNotBlank() || session.apiKey.isNotBlank())) {
                localRepository.setStartupCompleted(true)
            }
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

    val pendingIncomingFile by incomingFile.collectAsState()
    LaunchedEffect(pendingIncomingFile) {
        val incoming = pendingIncomingFile ?: return@LaunchedEffect
        val uri = incoming.uri
        BunkoLog.i("Processing incoming file URI: $uri")
        val book = localRepository.getOrCreateBookForUri(uri, incoming.mimeType)
        if (book != null) {
            BunkoLog.i("Successfully indexed incoming book: ${book.id} - ${book.title}")
            // Order matters: consuming first changes this effect's key and cancels it
            // at the next suspension point, which on cold start dropped the navigate
            // below (the app opened but the book didn't; retry worked). Navigate
            // synchronously first, then consume.
            localRepository.setStartupCompleted(true)
            val startPage = if (book.isCompleted) 0 else book.lastReadPage
            nav.navigate("local-reader/${book.id}?page=$startPage") {
                launchSingleTop = true
            }
            onConsumeIncomingFile()
        } else {
            BunkoLog.w("Could not index incoming book from URI: $uri")
            onConsumeIncomingFile()
            android.widget.Toast.makeText(
                ctx,
                "Couldn't open this file in Bunko",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val resolvedStart = remember {
        if (forceStartup || !startupCompleted!!) {
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
                route = "libraries?search={search}&tab={tab}",
                arguments = listOf(
                    navArgument("search") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStack ->
                val tabArg = backStack.arguments!!.getString("tab").orEmpty()
                val initialDestination = HomeDestination.entries.firstOrNull { it.name.equals(tabArg, ignoreCase = true) }
                LibraryScreen(
                    sessionStore = sessionStore,
                    sessionRevision = sessionRevision,
                    localRepository = localRepository,
                    initialIsOffline = (activeMode == "offline"),
                    initialSearchQuery = backStack.arguments!!.getString("search").orEmpty(),
                    initialDestination = initialDestination,
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenShelf = { shelfKind -> nav.navigate("shelf/${shelfKind.routeValue}") },
                    onOpenBookmarks = { nav.navigate("bookmarks") },
                    onOpenCollections = { nav.navigate("collections") },
                    onOpenDownloaded = { nav.navigate("downloaded") },
                    onOpenBookmark = { libraryId, seriesId, volumeId, chapterId, page ->
                        nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=false&page=$page")
                    },
                    onOpenCollection = { collection ->
                        nav.navigate(
                            "search-series/${SearchSeriesTarget.Collection.routeValue}/" +
                                "${collection.id}/${Uri.encode(collection.title)}"
                        )
                    },
                    onPickIssue = { libraryId, seriesId, volumeId, chapterId, incognito ->
                        nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=$incognito")
                    },
                    onOpenFilteredSeries = { target, id, label ->
                        nav.navigate("search-series/${target.routeValue}/$id/${Uri.encode(label)}")
                    },
                    onSelectLibrary = { lib -> nav.navigate("series/${lib.id}/${Uri.encode(lib.name)}") },
                    onSelectSeries = { series, tab ->
                        val libraryId = series.libraryId ?: 0
                        nav.navigate("chapters/$libraryId/${series.id}/${Uri.encode(series.name)}?fromTab=${tab.name}")
                    },
                    onOpenOfflineBook = { book ->
                        val startPage = if (book.isCompleted) 0 else book.lastReadPage
                        nav.navigate("local-reader/${book.id}?page=$startPage")
                    },
                    onRequireLogin = {
                        nav.navigate("login")
                    },
                    onToggleTheme = onToggleTheme,
                    navigationBarStyle = appSettings.navigationBarStyle
                )
            }

            composable("bookmarks") {
                BookmarksScreen(
                    sessionStore = sessionStore,
                    onBack = { nav.popBackStack() },
                    onOpenBookmark = { libraryId, seriesId, volumeId, chapterId, page ->
                        nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=false&page=$page")
                    },
                    onOpenLocalBookmark = { bookId, page ->
                        nav.navigate("local-reader/${Uri.encode(bookId)}?page=$page")
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
                        val tab = if (shelfKind == HomeShelfKind.OnDeck) "History" else "Home"
                        nav.navigate("chapters/$libraryId/${series.id}/${Uri.encode(series.name)}?fromTab=$tab")
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
                        nav.navigate("chapters/$libraryId/${series.id}/${Uri.encode(series.name)}?fromTab=Browse")
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
                        // Return to the existing hub instead of stacking a duplicate
                        // libraries entry; the query opens inline search there.
                        nav.navigate("libraries?search=${Uri.encode(query)}") {
                            popUpTo("libraries?search={search}&tab={tab}") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSelect = { s ->
                        val resolvedLib = s.libraryId?.takeIf { it > 0 } ?: libraryId
                        nav.navigate("chapters/$resolvedLib/${s.id}/${Uri.encode(s.name)}?fromTab=Libraries")
                    }
                )
            }

            composable(
                route = "chapters/{libraryId}/{seriesId}/{seriesName}?fromTab={fromTab}",
                arguments = listOf(
                    navArgument("libraryId") { type = NavType.IntType },
                    navArgument("seriesId") { type = NavType.IntType },
                    navArgument("seriesName") { type = NavType.StringType },
                    navArgument("fromTab") {
                        type = NavType.StringType
                        defaultValue = "Libraries"
                    }
                )
            ) { backStack ->
                val libraryId = backStack.arguments!!.getInt("libraryId")
                val seriesId = backStack.arguments!!.getInt("seriesId")
                val seriesName = backStack.arguments!!.getString("seriesName") ?: ""
                val fromTab = backStack.arguments!!.getString("fromTab").orEmpty()
                val currentDestination = HomeDestination.entries.firstOrNull { it.name.equals(fromTab, ignoreCase = true) }
                    ?: HomeDestination.Libraries
                ChapterPickScreen(
                    sessionStore = sessionStore,
                    libraryId = libraryId,
                    seriesId = seriesId,
                    seriesName = seriesName,
                    currentDestination = currentDestination,
                    onOpenSettings = { nav.navigate("settings") },
                    onOpenFilteredSeries = { target, id, label ->
                        nav.navigate("search-series/${target.routeValue}/$id/${Uri.encode(label)}")
                    },
                    onBack = { nav.popBackStack() },
                    onSelectDestination = { dest ->
                        if (dest == currentDestination) {
                            nav.popBackStack()
                        } else {
                            nav.navigate("libraries?tab=${dest.name}") {
                                popUpTo("libraries") { inclusive = true }
                            }
                        }
                    },
                    navigationBarStyle = appSettings.navigationBarStyle
                ) { chapterId, volumeId, incognito, initialPage ->
                    val pageParam = if (initialPage != null && initialPage >= 0) "&page=$initialPage" else ""
                    nav.navigate("reader/$libraryId/$seriesId/$volumeId/$chapterId?incognito=$incognito$pageParam")
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
                SettingsAdaptiveScreen(
                    settingsStore = settingsStore,
                    localRepository = localRepository,
                    sessionStore = sessionStore,
                    onConfigureServerDetails = { nav.navigate("settings/server") },
                    onBack = { nav.popBackStack() },
                    updateController = updateController
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

        // Global in-app updater sheet (mpvRx-style), above every destination.
        val updateState = updateController.state
        if (updateState is UpdateState.Available || updateState is UpdateState.ReadyToInstall) {
            val release = when (updateState) {
                is UpdateState.Available -> updateState.release
                is UpdateState.ReadyToInstall -> updateState.release
                else -> null
            }
            if (release != null) {
                UpdateSheet(
                    release = release,
                    isDownloading = updateController.isDownloading,
                    progress = updateController.downloadProgress,
                    isInstallReady = updateState is UpdateState.ReadyToInstall,
                    currentVersion = currentVersionName,
                    downloadError = updateController.downloadError,
                    onDismiss = { updateController.dismiss() },
                    onAction = {
                        if (updateState is UpdateState.ReadyToInstall) {
                            updateController.installUpdate(release)
                        } else {
                            updateController.downloadUpdate(release)
                        }
                    },
                    onIgnore = { updateController.ignoreVersion(release.tagName) }
                )
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

    val layoutDirection = LocalLayoutDirection.current
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val statusInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues()
    val startCutoutPadding = cutoutInsets.calculateStartPadding(layoutDirection)
    val endCutoutPadding = cutoutInsets.calculateEndPadding(layoutDirection)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = startCutoutPadding + 20.dp,
                    end = endCutoutPadding + 20.dp,
                    top = statusInsets.calculateTopPadding() + 20.dp,
                    bottom = 20.dp
                )
                .navigationBarsPadding(),
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
