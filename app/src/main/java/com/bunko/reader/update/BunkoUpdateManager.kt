package com.bunko.reader.update

import android.content.Context
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

// In-app updater ported from mpvRx (GitHub Releases check, APK download,
// ignore-version), adapted to Bunko: single stable channel, no flavors,
// no BuildConfig gates.

@Serializable
data class UpdateAsset(
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("size") val size: Long = 0L,
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
data class BunkoRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<UpdateAsset> = emptyList(),
    @SerialName("commit_count") val commitCount: Int? = null,
    @SerialName("commit_sha") val commitSha: String? = null,
    @SerialName("channel") val channel: String? = null
)

enum class UpdateChannel {
    STABLE,
    PREVIEW;

    companion object {
        fun fromStoredValue(value: String?): UpdateChannel? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

class BunkoUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(
        channel: UpdateChannel,
        forceShow: Boolean = false
    ): BunkoRelease? = withContext(Dispatchers.IO) {
        val release = try {
            when (channel) {
                UpdateChannel.STABLE -> getLatestRelease()
                UpdateChannel.PREVIEW -> getLatestPrerelease()
            }
        } catch (t: IOException) {
            BunkoLog.w("Could not check GitHub releases.", t)
            return@withContext null
        } catch (t: Exception) {
            BunkoLog.w("Could not parse GitHub release response.", t)
            return@withContext null
        } ?: return@withContext null
        if (selectBestApkAsset(release.assets) == null) return@withContext null
        val ignored = prefs.getString(ignoredVersionKey(channel), null)
        if (!forceShow && (ignored == release.tagName || ignored == release.tagName.removePrefix("v"))) {
            return@withContext null
        }
        val current = currentVersion()
        val newer = when (channel) {
            UpdateChannel.STABLE -> current != null && isVersionNewer(release.tagName, current)
            UpdateChannel.PREVIEW -> current != null && isPreviewReleaseNewer(release, current)
        }
        if (newer) release else null
    }

    private fun isPreviewReleaseNewer(release: BunkoRelease, current: String): Boolean {
        // Manifest model (mpvRx-style): the preview feed carries a commit count
        // compared against this build's GIT_COUNT.
        val remoteCount = release.commitCount ?: parsePreviewCount(release.tagName)
        if (remoteCount != null) {
            return remoteCount > com.bunko.reader.BuildConfig.GIT_COUNT
        }
        // Fallback for hand-cut prereleases without a count: tag comparison.
        return isPreviewNewer(release.tagName, current)
    }

    private fun parsePreviewCount(tagName: String): Int? =
        PreviewBuildRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun ignoreVersion(version: String, channel: UpdateChannel) {
        prefs.edit().putString(ignoredVersionKey(channel), version).apply()
    }

    private fun ignoredVersionKey(channel: UpdateChannel): String =
        "ignored_version_${channel.name.lowercase()}"

    fun downloadUpdate(release: BunkoRelease): Flow<Float> {
        val asset = selectBestApkAsset(release.assets)
            ?: throw IOException("No compatible APK asset found")
        val destination = File(appContext.externalCacheDir, asset.name)
        return downloadApk(asset.downloadUrl, destination)
    }

    fun getApkFile(release: BunkoRelease): File? {
        val asset = selectBestApkAsset(release.assets) ?: return null
        val file = File(appContext.externalCacheDir, asset.name)
        return if (file.exists()) file else null
    }

    fun clearCache() {
        appContext.externalCacheDir?.listFiles()?.forEach {
            if (it.name.endsWith(".apk")) it.delete()
        }
    }

    fun isAutoCheckEnabled(): Boolean = prefs.getBoolean(KeyAutoCheck, true)

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KeyAutoCheck, enabled).apply()
    }

    fun storedChannel(): UpdateChannel =
        UpdateChannel.fromStoredValue(prefs.getString(KeyChannel, null)) ?: UpdateChannel.STABLE

    fun storeChannel(channel: UpdateChannel) {
        prefs.edit().putString(KeyChannel, channel.name).apply()
    }

    fun currentVersion(): String? {
        return try {
            @Suppress("DEPRECATION")
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
        } catch (t: Exception) {
            BunkoLog.w("Could not read app version.", t)
            null
        }
    }

    private suspend fun getLatestRelease(): BunkoRelease = withContext(Dispatchers.IO) {
        val request = releaseRequest(LatestReleaseUrl)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val body = response.body?.string() ?: throw IOException("Empty response")
            json.decodeFromString<BunkoRelease>(body)
        }
    }

    private suspend fun getLatestPrerelease(): BunkoRelease? = withContext(Dispatchers.IO) {
        // Primary feed: the auto-built preview manifest published to Pages.
        fetchPreviewManifest()?.let { return@withContext it }
        // Fallback: the newest hand-cut GitHub prerelease.
        val request = releaseRequest(ReleasesListUrl)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val body = response.body?.string() ?: throw IOException("Empty response")
            json.decodeFromString<List<BunkoRelease>>(body)
                .firstOrNull { it.prerelease && it.tagName.isNotBlank() }
        }
    }

    private suspend fun fetchPreviewManifest(): BunkoRelease? = withContext(Dispatchers.IO) {
        val request = releaseRequest(PreviewManifestUrl)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                runCatching { json.decodeFromString<BunkoRelease>(body) }.getOrNull()
                    ?.takeIf { it.tagName.isNotBlank() }
            }
        } catch (t: IOException) {
            null
        }
    }

    private fun releaseRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")
            .header("Cache-Control", "no-cache")
            .build()
    }

    private fun selectBestApkAsset(assets: List<UpdateAsset>): UpdateAsset? {
        val deviceArch = primaryDeviceAbi()
        return selectBunkoApkAsset(assets, deviceArch)
    }

    private fun primaryDeviceAbi(): String {
        val primaryAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return "universal"
        return when (primaryAbi) {
            "arm64-v8a", "armeabi-v7a", "x86", "x86_64" -> primaryAbi
            else -> "universal"
        }
    }

    private fun downloadApk(url: String, destination: File): Flow<Float> = flow {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        val body = response.body ?: throw IOException("Empty response")
        val contentLength = body.contentLength()
        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    emit(
                        if (contentLength > 0) {
                            (totalBytesRead.toFloat() / contentLength.toFloat()) * 100f
                        } else {
                            -1f
                        }
                    )
                }
                output.flush()
            }
        }
        emit(100f)
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val PreferencesName = "bunko_update"
        const val KeyAutoCheck = "auto_check"
        const val KeyChannel = "update_channel"
        const val LatestReleaseUrl =
            "https://api.github.com/repos/Arnab11/Bunko/releases/latest"
        const val ReleasesListUrl =
            "https://api.github.com/repos/Arnab11/Bunko/releases?per_page=20"
        const val PreviewManifestUrl =
            "https://arnab11.github.io/Bunko/latest.json"

        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Picks the best release APK for [deviceArch] (mpvRx-style): an arch-specific
 * `Bunko-<abi>-<tag>.apk` first, then the universal build, then any APK.
 */
internal fun selectBunkoApkAsset(assets: List<UpdateAsset>, deviceArch: String): UpdateAsset? {
    val apkAssets = assets.filter {
        it.name.endsWith(".apk", ignoreCase = true) && it.downloadUrl.isNotBlank()
    }
    apkAssets.firstOrNull { it.name.hasAssetToken(deviceArch) }?.let { return it }
    apkAssets.firstOrNull { it.name.hasAssetToken("universal") }?.let { return it }
    return apkAssets.firstOrNull { asset ->
        KnownAbis.none { abi -> asset.name.hasAssetToken(abi) }
    } ?: apkAssets.firstOrNull()
}

private val KnownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

private fun String.hasAssetToken(token: String): Boolean =
    Regex("(?:^|-)${Regex.escape(token)}(?:-|\\.apk$)", RegexOption.IGNORE_CASE)
        .containsMatchIn(this)

fun isVersionNewer(candidate: String, current: String): Boolean {
    val candidateParts = candidate.versionParts() ?: return false
    val currentParts = current.versionParts() ?: return false
    val size = maxOf(candidateParts.size, currentParts.size)
    for (index in 0 until size) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun String.versionParts(): List<Int>? {
    val normalized = trim().removePrefix("v").removePrefix("V").substringBefore('-')
    if (normalized.isBlank()) return null
    return normalized.split('.').map { part ->
        val digits = part.takeWhile(Char::isDigit)
        if (digits.isBlank()) return null
        digits.toIntOrNull() ?: return null
    }
}

/**
 * Preview tags look like `v0.24-preview.1`. A preview is newer when its base
 * version is ahead, or the base matches and its preview number is higher
 * (an installed stable build counts as preview number 0).
 */
fun isPreviewNewer(candidate: String, current: String): Boolean {
    val candidateBase = candidate.versionParts() ?: return false
    val currentBase = current.versionParts() ?: return false
    val size = maxOf(candidateBase.size, currentBase.size)
    for (index in 0 until size) {
        val candidatePart = candidateBase.getOrElse(index) { 0 }
        val currentPart = currentBase.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return candidate.previewNumber() > current.previewNumber()
}

private val PreviewTagRegex = Regex("""preview\.(\d+)""", RegexOption.IGNORE_CASE)

/** Preview build number from a manifest tag (`preview-r123`) or hand-cut tag. */
internal fun BunkoRelease.previewBuildNumber(): Int? =
    commitCount ?: PreviewTagRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: PreviewBuildRegex.find(tagName)?.groupValues?.getOrNull(1)?.toIntOrNull()

private val PreviewBuildRegex = Regex("""(?:preview-)?r(\d+)""", RegexOption.IGNORE_CASE)

private fun String.previewNumber(): Int {
    val base = trim().removePrefix("v").removePrefix("V")
    if (!base.contains('-')) return 0
    return PreviewTagRegex.find(base)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}
