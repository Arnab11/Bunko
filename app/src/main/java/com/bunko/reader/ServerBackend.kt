package com.bunko.reader

import android.content.Context
import coil.ImageLoader
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Which server family backs the current library UI. Read by the top-level
 * cover-URL helpers so existing call sites keep working for both servers.
 */
object ActiveServerRuntime {
    @Volatile var mode: String = "kavita"
    @Volatile var komgaBaseUrl: String = ""
}

/** Cover/page URL builders for the active server (ids are Bunko Int ids). */
class ServerImageUrls(
    val seriesCoverUrl: (seriesId: Int) -> String,
    val chapterCoverUrl: (chapterId: Int) -> String,
    val libraryCoverUrl: (libraryId: Int) -> String,
    val pageImageUrl: (chapterId: Int, page: Int, extractPdf: Boolean) -> String,
    val bookResourceUrl: (chapterId: Int, resourcePath: String) -> String
)

/**
 * Everything a screen needs to talk to the active server, Kavita or Komga.
 * In Komga mode [api] is a [KomgaKavitaAdapter], so all existing Kavita-shaped
 * screen logic works unchanged.
 */
class ServerBackend(
    val mode: String,
    val api: KavitaApi,
    val okHttp: OkHttpClient,
    val imageUrls: ServerImageUrls,
    val isConfigured: Boolean,
    val serverLabel: String,
    private val readerLoaderFactory: (suspend () -> ImageLoader?)? = null
) {
    val isKomga: Boolean get() = mode == "komga"

    /** Builds the reader image pipeline on demand (caller owns shutdown). */
    suspend fun readerImageLoader(): ImageLoader? {
        return readerLoaderFactory?.invoke()
    }
}

/**
 * Resolves the active library source ("offline" behaves like Kavita with an
 * unconfigured backend) and publishes it to [ActiveServerRuntime] so the
 * shared cover helpers stay correct.
 */
suspend fun Context.serverBackend(
    kavitaStore: KavitaSessionStore,
    komgaStore: KomgaSessionStore,
    activeMode: String? = null
): ServerBackend {
    val appContext = applicationContext
    KomgaIdMapper.init(appContext)
    val mode = activeMode ?: runCatching {
        com.bunko.reader.offline.LocalBookRepository(appContext).activeModeFlow.first()
    }.getOrDefault("kavita")

    if (mode == "komga") {
        val session = komgaStore.load()
        val baseUrl = normalizeKomgaBaseUrl(session.baseUrl)
        ActiveServerRuntime.mode = "komga"
        ActiveServerRuntime.komgaBaseUrl = baseUrl
        val configured = baseUrl.isNotBlank() &&
            (session.username.isNotBlank() || session.apiKey.isNotBlank())
        if (!configured) {
            return ServerBackend(
                mode = "komga",
                api = KomgaKavitaAdapter(UnconfiguredKomgaApi, emptyOkHttp(), baseUrl),
                okHttp = emptyOkHttp(),
                imageUrls = komgaImageUrls(baseUrl),
                isConfigured = false,
                serverLabel = "Komga"
            )
        }
        val client = KomgaClient(appContext, komgaStore)
        val (api, okHttp) = client.buildApi()
        val adapter = KomgaKavitaAdapter(api, okHttp, baseUrl)
        return ServerBackend(
            mode = "komga",
            api = adapter,
            okHttp = okHttp,
            imageUrls = komgaImageUrls(baseUrl),
            isConfigured = true,
            serverLabel = komgaStore.activeProfile()?.name?.ifBlank { "Komga" } ?: "Komga",
            readerLoaderFactory = {
                runCatching { client.buildReaderImageLoader(okHttp, session) }.getOrNull()
            }
        )
    }

    ActiveServerRuntime.mode = "kavita"
    ActiveServerRuntime.komgaBaseUrl = ""
    val session = kavitaStore.load()
    val configured = session.baseUrl.isNotBlank() &&
        (session.jwt.isNotBlank() || session.apiKey.isNotBlank())
    if (!configured) {
        return ServerBackend(
            mode = "kavita",
            api = UnconfiguredKavitaApi,
            okHttp = emptyOkHttp(),
            imageUrls = kavitaImageUrls(KavitaSession()),
            isConfigured = false,
            serverLabel = "Kavita"
        )
    }
    val client = KavitaClient(appContext, kavitaStore)
    val (api, okHttp) = client.buildApi()
    return ServerBackend(
        mode = "kavita",
        api = api,
        okHttp = okHttp,
        imageUrls = kavitaImageUrls(session),
        isConfigured = true,
        serverLabel = kavitaStore.activeProfile()?.name?.ifBlank { "Kavita" } ?: "Kavita",
        readerLoaderFactory = {
            runCatching { client.buildReaderImageLoader(okHttp, session) }.getOrNull()
        }
    )
}

private fun emptyOkHttp(): OkHttpClient = OkHttpClient()

internal fun kavitaImageUrls(session: KavitaSession): ServerImageUrls {
    // Mirrors KavitaClient's builders (apiKey stays in the URL for Coil retries).
    fun apiKeyQuery(): String {
        return session.apiKey.takeIf { it.isNotBlank() }
            ?.let { "&apiKey=${android.net.Uri.encode(it)}" }.orEmpty()
    }
    fun root(): String = normalizeKavitaBaseUrl(session.baseUrl)
    return ServerImageUrls(
        seriesCoverUrl = { seriesId -> "${root()}/api/Image/series-cover?seriesId=$seriesId${apiKeyQuery()}" },
        chapterCoverUrl = { chapterId -> "${root()}/api/Image/chapter-cover?chapterId=$chapterId${apiKeyQuery()}" },
        libraryCoverUrl = { libraryId -> "${root()}/api/Image/library-cover?libraryId=$libraryId${apiKeyQuery()}" },
        pageImageUrl = { chapterId, page, extractPdf ->
            withPdfExtraction(
                "${root()}/api/Reader/image?chapterId=$chapterId${apiKeyQuery()}&page=$page",
                extractPdf
            )
        },
        bookResourceUrl = { chapterId, resourcePath ->
            KavitaResourceUrls.bookResourceUrl(root(), session.apiKey, chapterId, resourcePath)
        }
    )
}

internal fun komgaImageUrls(baseUrl: String): ServerImageUrls {
    val root = normalizeKomgaBaseUrl(baseUrl)
    fun seriesKomgaId(seriesId: Int): String? = KomgaIdMapper.komgaSeriesId(seriesId)
    fun bookKomgaId(chapterId: Int): String? = KomgaIdMapper.komgaBookId(chapterId)
    return ServerImageUrls(
        seriesCoverUrl = { seriesId ->
            val id = seriesKomgaId(seriesId)
            if (id != null) "$root/api/v1/series/$id/thumbnail" else ""
        },
        chapterCoverUrl = { chapterId ->
            val id = bookKomgaId(chapterId)
            if (id != null) "$root/api/v1/books/$id/thumbnail" else ""
        },
        libraryCoverUrl = { libraryId ->
            val id = KomgaIdMapper.komgaLibraryId(libraryId)
            if (id != null) "$root/api/v1/libraries/$id/thumbnail" else ""
        },
        // Kavita pages are 0-based; Komga pages are 1-based.
        pageImageUrl = { chapterId, page, _ ->
            val id = bookKomgaId(chapterId)
            if (id != null) "$root/api/v1/books/$id/pages/${page + 1}" else ""
        },
        bookResourceUrl = { chapterId, resourcePath ->
            if (resourcePath.startsWith("http://", ignoreCase = true) ||
                resourcePath.startsWith("https://", ignoreCase = true)
            ) {
                resourcePath
            } else {
                val id = bookKomgaId(chapterId)
                if (id != null && resourcePath.isNotBlank()) {
                    "$root/api/v1/books/$id/resource/${resourcePath.removePrefix("/")}"
                } else ""
            }
        }
    )
}

/** Kavita book-resource URL logic shared with KavitaClient (Coil-safe form). */
private object KavitaResourceUrls {
    fun bookResourceUrl(baseUrl: String, apiKey: String, chapterId: Int, resourcePath: String): String {
        val root = normalizeKavitaBaseUrl(baseUrl)
        if (resourcePath.startsWith("http://", ignoreCase = true) || resourcePath.startsWith("https://", ignoreCase = true)) {
            return if (apiKey.isNotBlank() && !resourcePath.contains("apiKey=")) {
                val sep = if (resourcePath.contains("?")) "&" else "?"
                "$resourcePath${sep}apiKey=${android.net.Uri.encode(apiKey)}"
            } else {
                resourcePath
            }
        }
        val cleanPath = resourcePath.removePrefix("/")
        return if (cleanPath.startsWith("book-resources") || cleanPath.startsWith("api/Reader/book-resources")) {
            val url = if (cleanPath.startsWith("api/")) "$root/$cleanPath" else "$root/api/Reader/$cleanPath"
            if (apiKey.isNotBlank() && !url.contains("apiKey=")) {
                val sep = if (url.contains("?")) "&" else "?"
                "$url${sep}apiKey=${android.net.Uri.encode(apiKey)}"
            } else {
                url
            }
        } else {
            val key = apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=${android.net.Uri.encode(it)}" }.orEmpty()
            "$root/api/Reader/book-resources?chapterId=$chapterId&fileName=${android.net.Uri.encode(cleanPath)}$key"
        }
    }
}
