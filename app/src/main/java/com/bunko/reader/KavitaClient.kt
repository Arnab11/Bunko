package com.bunko.reader

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import com.bunko.reader.cache.CoverImageCacheDirectory
import com.bunko.reader.cache.KavitaImageDiskCacheKeyInterceptor
import com.bunko.reader.cache.KavitaImageKeyer
import com.bunko.reader.cache.ReaderPageCacheDirectory
import com.bunko.reader.cache.imageCacheBudget
import com.bunko.reader.cache.imageCacheScope
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

private const val ApiCallTimeoutSeconds = 20L
private const val ApiConnectTimeoutSeconds = 10L

class KavitaClient(
    private val context: Context,
    private val store: KavitaSessionStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun buildApi(): Pair<KavitaApi, OkHttpClient> {
        val session = store.load()
        val rootUrl = normalizeBaseUrl(session.baseUrl)

        // Plain client (no auth) used to mint a fresh JWT from the saved apiKey. It runs
        // synchronously inside the 401 authenticator, so it needs its own timeouts or a
        // stalled auth server would hang every request that hit the re-auth path.
        val mintClient = OkHttpClient.Builder()
            .callTimeout(ApiCallTimeoutSeconds, TimeUnit.SECONDS)
            .connectTimeout(ApiConnectTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        // Inject auth dynamically so a freshly refreshed token is picked up by
        // later requests on the same client (no snapshot of the token here).
        val headerInterceptor = Interceptor { chain ->
            val current = runBlocking { store.load() }
            val req = chain.request().newBuilder()
                .apply {
                    if (current.jwt.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${current.jwt}")
                    } else if (current.apiKey.isNotBlank()) {
                        addHeader("x-api-key", current.apiKey)
                    }
                }
                .build()
            chain.proceed(req)
        }

        // On 401, transparently re-authenticate using the saved apiKey (Kavita's
        // JWT expires after ~10 days) so the user is not forced to log in again.
        val tokenAuthenticator = Authenticator { _, response ->
            val current = runBlocking { store.load() }
            if (current.apiKey.isBlank()) return@Authenticator null
            if (responseCount(response) >= 3) return@Authenticator null

            val failedAuth = response.request.header("Authorization")
            // Another request may have already refreshed the token; reuse it.
            if (current.jwt.isNotBlank() && failedAuth != "Bearer ${current.jwt}") {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer ${current.jwt}")
                    .build()
            }

            val newJwt = mintJwtFromApiKey(mintClient, rootUrl, current.apiKey)
                ?: return@Authenticator null
            runBlocking { store.save(current.copy(jwt = newJwt)) }
            response.request.newBuilder()
                .header("Authorization", "Bearer $newJwt")
                .build()
        }

        // Client used by Coil for cover/page images. Only a connect timeout: a large page
        // on a slow link may legitimately take a while to download, so it must NOT inherit
        // the API whole-call cap or reading images would fail mid-download.
        val imageOkHttp = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(ApiConnectTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        // JSON API calls add a whole-call cap so a slow or stalled server can never hang a
        // request indefinitely (e.g. the mark-read/progress calls when leaving a chapter).
        // Derived via newBuilder() so it shares the image client's connection pool/dispatcher.
        val apiOkHttp = imageOkHttp.newBuilder()
            .callTimeout(ApiCallTimeoutSeconds, TimeUnit.SECONDS)
            .build()

        val baseUrl = ("$rootUrl/")
            .toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Server URL must start with http:// or https://")

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(apiOkHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create<KavitaApi>() to imageOkHttp
    }

    /** Exchanges the saved apiKey for a fresh JWT via Kavita's Plugin/authenticate. */
    private fun mintJwtFromApiKey(client: OkHttpClient, rootUrl: String, apiKey: String): String? {
        return try {
            val url = "$rootUrl/api/Plugin/authenticate?apiKey=${Uri.encode(apiKey)}&pluginName=Bunko"
            val request = Request.Builder()
                .url(url)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return null
                json.decodeFromString(UserDto.serializer(), body).token?.takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            BunkoLog.w("Could not refresh Kavita JWT from API key.", t)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    suspend fun buildImageLoader(okHttp: OkHttpClient, session: KavitaSession): ImageLoader {
        val budget = imageCacheBudget(context)
        val cacheScope = imageCacheScope(session, store.activeProfile()?.id)
        return ImageLoader.Builder(context)
            .okHttpClient(okHttp)
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

    suspend fun buildReaderImageLoader(okHttp: OkHttpClient, session: KavitaSession): ImageLoader {
        val budget = imageCacheBudget(context)
        val cacheScope = imageCacheScope(session, store.activeProfile()?.id)
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

    fun pageImageUrl(baseUrl: String, apiKey: String, chapterId: Int, page: Int): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/Reader/image?chapterId=$chapterId${apiKeyQuery(apiKey)}&page=$page"
    }

    fun bookResourceUrl(baseUrl: String, apiKey: String, chapterId: Int, resourcePath: String): String {
        val root = normalizeBaseUrl(baseUrl)
        if (resourcePath.startsWith("http://", ignoreCase = true) || resourcePath.startsWith("https://", ignoreCase = true)) {
            return if (apiKey.isNotBlank() && !resourcePath.contains("apiKey=")) {
                val sep = if (resourcePath.contains("?")) "&" else "?"
                "$resourcePath${sep}apiKey=${Uri.encode(apiKey)}"
            } else {
                resourcePath
            }
        }
        val cleanPath = resourcePath.removePrefix("/")
        return if (cleanPath.startsWith("book-resources") || cleanPath.startsWith("api/Reader/book-resources")) {
            val url = if (cleanPath.startsWith("api/")) "$root/$cleanPath" else "$root/api/Reader/$cleanPath"
            if (apiKey.isNotBlank() && !url.contains("apiKey=")) {
                val sep = if (url.contains("?")) "&" else "?"
                "$url${sep}apiKey=${Uri.encode(apiKey)}"
            } else {
                url
            }
        } else {
            "$root/api/Reader/book-resources?chapterId=$chapterId&fileName=${Uri.encode(cleanPath)}${apiKeyQuery(apiKey)}"
        }
    }

    fun seriesCoverUrl(baseUrl: String, apiKey: String, seriesId: Int): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/Image/series-cover?seriesId=$seriesId${apiKeyQuery(apiKey)}"
    }

    fun chapterCoverUrl(baseUrl: String, apiKey: String, chapterId: Int): String {
        val root = normalizeBaseUrl(baseUrl)
        return "$root/api/Image/chapter-cover?chapterId=$chapterId${apiKeyQuery(apiKey)}"
    }

    fun loginUrl(baseUrl: String): String {
        return "${normalizeBaseUrl(baseUrl)}/api/Account/login"
    }

    private fun normalizeBaseUrl(baseUrl: String): String = normalizeKavitaBaseUrl(baseUrl)

    private fun apiKeyQuery(apiKey: String): String {
        // Coil image requests can be retried outside Retrofit's auth flow, so Kavita image endpoints keep apiKey in the URL.
        return apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=${Uri.encode(it)}" }.orEmpty()
    }
}

private val Ipv4Regex = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/**
 * Normalizes a user-entered Kavita server URL: trims trailing slashes, drops a
 * trailing /api or /login, and when no scheme is given, prepends one. An explicit
 * http(s):// is always respected. For scheme-less input the host is used to guess:
 * IP literals and localhost get http://, everything else https://. Shared by the
 * API client and the image-URL builders so covers and API calls use the same host.
 */
internal fun normalizeKavitaBaseUrl(baseUrl: String): String {
    var url = baseUrl.trim().trimEnd('/')
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        val host = url.substringBefore('/').substringBefore(':')
        val scheme = if (host.equals("localhost", ignoreCase = true) || Ipv4Regex.matches(host)) {
            "http://"
        } else {
            "https://"
        }
        url = scheme + url
    }
    if (url.endsWith("/api", ignoreCase = true)) {
        url = url.dropLast(4)
    }
    if (url.endsWith("/login", ignoreCase = true)) {
        url = url.dropLast(6)
    }
    return url.trimEnd('/')
}
