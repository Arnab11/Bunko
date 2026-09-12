package com.bunko.reader.cache

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import coil.intercept.Interceptor
import coil.key.Keyer
import coil.request.ImageResult
import coil.request.Options
import com.bunko.reader.KavitaSession
import com.bunko.reader.normalizeKavitaBaseUrl
import java.io.File
import java.security.MessageDigest

internal const val CoverImageCacheDirectory = "cover_image_cache"
internal const val ReaderPageCacheDirectory = "reader_page_cache"

private const val MiB = 1024L * 1024L
private const val GiB = 1024L * MiB
private const val StorageReserveBytes = 4L * GiB
private const val MaximumImageCacheBytes = 9L * GiB

internal data class ImageCacheBudget(
    val coverMemoryPercent: Double,
    val readerMemoryPercent: Double,
    val coverDiskBytes: Long,
    val readerDiskBytes: Long
)

internal fun imageCacheBudget(context: Context): ImageCacheBudget {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryClassMb = activityManager?.memoryClass ?: 256
    val lowRam = activityManager?.isLowRamDevice ?: false
    val usableBytes = runCatching { StatFs(context.cacheDir.absolutePath).availableBytes }
        .getOrDefault(0L)
    return imageCacheBudget(memoryClassMb, lowRam, usableBytes)
}

internal fun imageCacheBudget(
    memoryClassMb: Int,
    lowRam: Boolean,
    usableStorageBytes: Long
): ImageCacheBudget {
    val coverMemoryPercent: Double
    val readerMemoryPercent: Double
    when {
        lowRam || memoryClassMb < 256 -> {
            coverMemoryPercent = 0.08
            readerMemoryPercent = 0.20
        }
        memoryClassMb >= 512 -> {
            coverMemoryPercent = 0.12
            readerMemoryPercent = 0.40
        }
        else -> {
            coverMemoryPercent = 0.10
            readerMemoryPercent = 0.30
        }
    }

    // Cache is disposable, but reserve storage for downloads and other app data first.
    // Below the reserve, use only a small fraction instead of enforcing a minimum that
    // would make a nearly-full device worse.
    val cachePool = if (usableStorageBytes <= StorageReserveBytes) {
        (usableStorageBytes / 100L).coerceIn(16L * MiB, 96L * MiB)
    } else {
        ((usableStorageBytes - StorageReserveBytes) / 20L)
            .coerceAtMost(MaximumImageCacheBytes)
    }
    val coverDiskBytes = when {
        cachePool < 128L * MiB -> cachePool / 4L
        cachePool < 512L * MiB -> cachePool / 4L
        else -> (cachePool / 9L).coerceIn(256L * MiB, 1L * GiB)
    }
    val readerDiskBytes = cachePool - coverDiskBytes

    return ImageCacheBudget(
        coverMemoryPercent = coverMemoryPercent,
        readerMemoryPercent = readerMemoryPercent,
        coverDiskBytes = coverDiskBytes,
        readerDiskBytes = readerDiskBytes
    )
}

internal fun imageCacheScope(session: KavitaSession, profileId: String? = null): String {
    val identity = profileId?.trim()?.takeIf { it.isNotBlank() } ?: session.username.trim().lowercase().ifBlank {
        sha256(session.apiKey).take(16)
    }
    return sha256("${normalizeKavitaBaseUrl(session.baseUrl).lowercase()}|$identity").take(24)
}

internal fun stableKavitaImageCacheKey(scope: String, model: Any): String? {
    val uri = runCatching { Uri.parse(model.toString()) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    if (!uri.path.orEmpty().startsWith("/api/", ignoreCase = true)) return null

    val canonical = uri.buildUpon().clearQuery().apply {
        uri.queryParameterNames
            .filterNot { it.equals("apiKey", ignoreCase = true) }
            .sorted()
            .forEach { name ->
                uri.getQueryParameters(name).forEach { value -> appendQueryParameter(name, value) }
            }
    }.build().toString()
    return "bunko:$scope:$canonical"
}

internal class KavitaImageKeyer(
    private val scope: String
) : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? =
        stableKavitaImageCacheKey(scope, data)
}

internal class KavitaImageDiskCacheKeyInterceptor(
    private val scope: String
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val stableKey = stableKavitaImageCacheKey(scope, chain.request.data)
            ?: return chain.proceed(chain.request)
        return chain.proceed(
            chain.request.newBuilder()
                .diskCacheKey(stableKey)
                .build()
        )
    }
}

internal fun cacheDirectorySize(directory: File): Long {
    if (!directory.exists()) return 0L
    return directory.walkTopDown()
        .filter(File::isFile)
        .sumOf { file -> runCatching { file.length() }.getOrDefault(0L) }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
