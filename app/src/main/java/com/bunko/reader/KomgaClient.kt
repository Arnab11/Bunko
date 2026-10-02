package com.bunko.reader

import android.content.Context
import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bunko.reader.cache.CoverImageCacheDirectory
import com.bunko.reader.cache.KavitaImageDiskCacheKeyInterceptor
import com.bunko.reader.cache.KavitaImageKeyer
import com.bunko.reader.cache.ReaderPageCacheDirectory
import com.bunko.reader.cache.imageCacheBudget
import com.bunko.reader.cache.imageCacheScope
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private const val KomgaApiCallTimeoutSeconds = 20L
private const val KomgaApiConnectTimeoutSeconds = 10L

class KomgaClient(
    private val context: Context,
    private val store: KomgaSessionStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        // Komga treats explicit nulls as invalid for several DTOs (e.g. read
        // progress); omit null fields instead of sending them.
        explicitNulls = false
    }

    suspend fun buildApi(): Pair<KomgaApi, OkHttpClient> {
        val session = store.load()
        val rootUrl = normalizeBaseUrl(session.baseUrl)

        val cookieManager = java.net.CookieManager().apply {
            setCookiePolicy(java.net.CookiePolicy.ACCEPT_ALL)
        }

        val authInterceptor = Interceptor { chain ->
            val current = kotlinx.coroutines.runBlocking { store.load() }
            val builder = chain.request().newBuilder()
            if (current.apiKey.isNotBlank()) {
                builder.addHeader("X-API-Key", current.apiKey)
            } else if (current.username.isNotBlank()) {
                val credentials = "${current.username}:${current.password}"
                val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                builder.addHeader("Authorization", "Basic $encoded")
            }
            chain.proceed(builder.build())
        }

        val imageOkHttp = OkHttpClient.Builder()
            .cookieJar(KomgaCookieJar(cookieManager))
            .addInterceptor(authInterceptor)
            .connectTimeout(KomgaApiConnectTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        val apiOkHttp = imageOkHttp.newBuilder()
            .callTimeout(KomgaApiCallTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        val baseUrl = ("$rootUrl/")
            .toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Server URL must start with http:// or https://")

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .callFactory(apiOkHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create<KomgaApi>() to imageOkHttp
    }

    /** Verifies credentials by hitting a lightweight authenticated endpoint. */
    suspend fun probeCredentials(): KomgaUserDto {
        val (api, _) = buildApi()
        return api.currentUser()
    }

    suspend fun buildImageLoader(okHttp: OkHttpClient, session: KomgaSession): ImageLoader {
        val budget = imageCacheBudget(context)
        // Reuse the Kavita cache-keying infra but scope it to the Komga profile so
        // Komga covers never collide with Kavita covers for the same numeric id.
        val kavitaLikeSession = KavitaSession(
            baseUrl = "komga:${session.baseUrl}",
            username = session.username,
            apiKey = session.apiKey.ifBlank { "komga-basic" }
        )
        val cacheScope = imageCacheScope(kavitaLikeSession, store.activeProfile()?.id?.let { "komga-$it" })
        return ImageLoader.Builder(context)
            .okHttpClient(okHttp)
            .allowHardware(false)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(budget.coverMemoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(CoverImageCacheDirectory))
                    .maxSizeBytes(budget.coverDiskBytes)
                    .build()
            }
            .components {
                add(KavitaImageDiskCacheKeyInterceptor(cacheScope))
                add(KavitaImageKeyer(cacheScope))
                add(SvgDecoder.Factory())
            }
            .build()
    }

    suspend fun buildReaderImageLoader(okHttp: OkHttpClient, session: KomgaSession): ImageLoader {
        val budget = imageCacheBudget(context)
        val kavitaLikeSession = KavitaSession(
            baseUrl = "komga:${session.baseUrl}",
            username = session.username,
            apiKey = session.apiKey.ifBlank { "komga-basic" }
        )
        val cacheScope = imageCacheScope(kavitaLikeSession, store.activeProfile()?.id?.let { "komga-$it" })
        return ImageLoader.Builder(context)
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(budget.readerMemoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(ReaderPageCacheDirectory))
                    .maxSizeBytes(budget.readerDiskBytes)
                    .build()
            }
            .components {
                add(KavitaImageDiskCacheKeyInterceptor(cacheScope))
                add(KavitaImageKeyer(cacheScope))
            }
            .build()
    }

    fun seriesCoverUrl(baseUrl: String, seriesId: String): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/v1/series/$seriesId/thumbnail"
    }

    fun bookCoverUrl(baseUrl: String, bookId: String): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/v1/books/$bookId/thumbnail"
    }

    fun libraryCoverUrl(baseUrl: String, libraryId: String): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/v1/libraries/$libraryId/thumbnail"
    }

    fun bookPageUrl(baseUrl: String, bookId: String, pageNumber: Int): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/v1/books/$bookId/pages/$pageNumber"
    }

    fun bookResourceUrl(baseUrl: String, apiKey: String, chapterId: Int, resourcePath: String): String {
        val root = normalizeBaseUrl(baseUrl)
        val clean = resourcePath.substringBefore("#").trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        val path = clean.removePrefix("./").removePrefix("/")
        if (path.startsWith("api/")) {
            return "$root/$path"
        }
        val id = KomgaIdMapper.komgaBookId(chapterId) ?: return ""
        if (path.startsWith("resource/")) {
            return "$root/api/v1/books/$id/$path"
        }
        val decoded = Uri.decode(path)
        val encoded = decoded.split("/").joinToString("/") { Uri.encode(it) }
        return "$root/api/v1/books/$id/resource/$encoded"
    }

    fun authenticatedImageUrl(url: String, session: KomgaSession): String {
        // When using an API key, Coil cannot inject headers for every redirect, so
        // prefer the query form Komga also accepts? Komga only accepts the header,
        // so callers must use the authenticated OkHttp client (buildImageLoader).
        // Keep the URL unchanged; auth rides in headers/cookies.
        if (session.apiKey.isNotBlank()) return url
        return url
    }

    private fun normalizeBaseUrl(baseUrl: String): String = normalizeKomgaBaseUrl(baseUrl)
}

private val KomgaIpv4Regex = Regex("""\d{1,3}(\.\d{1,3}){3}""")

internal fun normalizeKomgaBaseUrl(baseUrl: String): String {
    var url = baseUrl.trim().trimEnd('/')
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        val host = url.substringBefore('/').substringBefore(':')
        val scheme = if (host.equals("localhost", ignoreCase = true) || KomgaIpv4Regex.matches(host)) {
            "http://"
        } else {
            "https://"
        }
        url = scheme + url
    }
    return url.trimEnd('/')
}

/** Appends Komga auth to a Coil image request URL when API-key auth is in use. */
internal fun KomgaSession.authHeaders(): Map<String, String> {
    return if (apiKey.isNotBlank()) {
        mapOf("X-API-Key" to apiKey)
    } else if (username.isNotBlank()) {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        mapOf("Authorization" to "Basic $encoded")
    } else {
        emptyMap()
    }
}

/** Builds an OkHttp request with Komga auth headers applied (for manual fetches). */
internal fun komgaAuthenticatedUrl(baseUrl: String, path: String, session: KomgaSession): String {
    val root = normalizeKomgaBaseUrl(baseUrl)
    val cleanPath = path.removePrefix("/")
    val url = if (cleanPath.startsWith("api/")) "$root/$cleanPath" else "$root/api/v1/$cleanPath"
    if (session.apiKey.isNotBlank() && !url.contains("apiKey=")) {
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}apiKey=${Uri.encode(session.apiKey)}"
    }
    return url
}

/** Minimal CookieJar backed by java.net.CookieManager (persists KOMGA-SESSION). */
private class KomgaCookieJar(
    private val manager: java.net.CookieManager
) : okhttp3.CookieJar {
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (cookies.isNotEmpty()) {
            val uri = url.toUri()
            val headers = mapOf("Set-Cookie" to cookies.map { it.toString() })
            runCatching { manager.put(uri, headers) }
        }
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val uri = url.toUri()
        val headers = runCatching { manager.get(uri, emptyMap()) }.getOrNull() ?: return emptyList()
        val cookieHeaders = headers.entries
            .filter { (k, _) -> k.equals("Cookie", ignoreCase = true) }
            .flatMap { it.value }
        if (cookieHeaders.isEmpty()) return emptyList()
        return cookieHeaders.flatMap { header ->
            runCatching { okhttp3.Cookie.parseAll(url, okhttp3.Headers.headersOf("Cookie", header)) }.getOrDefault(emptyList())
        }
    }
}
