package com.bunko.reader.update

import android.content.Context
import com.bunko.reader.BunkoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray

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
        return downloadApkParallel(asset.downloadUrl, destination, asset.name, asset.size)
    }

    fun getApkFile(release: BunkoRelease): File? {
        val asset = selectBestApkAsset(release.assets) ?: return null
        val destination = File(appContext.externalCacheDir, asset.name)
        val hasParts = (0 until 8).any {
            File(appContext.externalCacheDir, "${asset.name}.part$it").exists()
        } || File(appContext.externalCacheDir, "${asset.name}.part").exists()
        return if (destination.exists() && destination.length() > 0L && !hasParts) destination else null
    }

    fun getExistingProgress(release: BunkoRelease): Float {
        val asset = selectBestApkAsset(release.assets) ?: return 0f
        val destination = File(appContext.externalCacheDir, asset.name)
        if (destination.exists() && destination.length() > 0L) return 100f
        if (asset.size <= 0L) return 0f
        var totalPartBytes = 0L
        val singlePart = File(appContext.externalCacheDir, "${asset.name}.part")
        if (singlePart.exists()) totalPartBytes += singlePart.length()
        for (i in 0 until 8) {
            val p = File(appContext.externalCacheDir, "${asset.name}.part$i")
            if (p.exists()) totalPartBytes += p.length()
        }
        if (totalPartBytes > 0L) {
            return ((totalPartBytes.toFloat() / asset.size.toFloat()) * 100f).coerceIn(0f, 99f)
        }
        return 0f
    }

    fun clearCache() {
        appContext.externalCacheDir?.listFiles()?.forEach {
            if (it.name.endsWith(".apk") || it.name.contains(".part")) {
                it.delete()
            }
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
        apiClient.newCall(request).execute().use { response ->
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
        apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val body = response.body?.string() ?: throw IOException("Empty response")
            json.decodeFromString<List<BunkoRelease>>(body)
                .firstOrNull { it.prerelease && it.tagName.isNotBlank() }
        }
    }

    private suspend fun fetchPreviewManifest(): BunkoRelease? = withContext(Dispatchers.IO) {
        val request = releaseRequest(PreviewManifestUrl)
        try {
            apiClient.newCall(request).execute().use { response ->
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

    private fun downloadApkParallel(
        url: String,
        destination: File,
        assetName: String,
        expectedTotalBytes: Long
    ): Flow<Float> = channelFlow {
        var totalBytes = expectedTotalBytes

        // If totalBytes is unknown, fetch headers to discover content length
        if (totalBytes <= 0L) {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")
                .build()
            try {
                downloadClient.newCall(headReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        totalBytes = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    }
                }
            } catch (_: Exception) {
            }
        }

        val numChunks = if (totalBytes >= MinParallelBytes) ParallelChunkCount else 1
        val singlePartFile = File(appContext.externalCacheDir, "$assetName.part")

        if (numChunks == 1) {
            downloadSingleStream(url, destination, singlePartFile, totalBytes) { p -> send(p) }
            send(100f)
            return@channelFlow
        }

        // Multi-chunk parallel download
        val chunkSize = totalBytes / numChunks
        val partFiles = (0 until numChunks).map { i ->
            File(appContext.externalCacheDir, "$assetName.part$i")
        }

        // Check if all chunks are already completely downloaded
        val allCompleted = partFiles.mapIndexed { i, file ->
            val startByte = i * chunkSize
            val endByte = if (i == numChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
            val targetSize = endByte - startByte + 1
            file.exists() && file.length() >= targetSize
        }.all { it }

        if (allCompleted) {
            mergeChunkFiles(partFiles, destination)
            send(100f)
            return@channelFlow
        }

        val writtenPerChunk = AtomicLongArray(numChunks)
        for (i in 0 until numChunks) {
            writtenPerChunk.set(i, if (partFiles[i].exists()) partFiles[i].length() else 0L)
        }

        val lastEmittedPercent = java.util.concurrent.atomic.AtomicInteger(-1)

        fun checkAndSendProgress() {
            var sum = 0L
            for (i in 0 until numChunks) {
                sum += writtenPerChunk.get(i)
            }
            if (totalBytes > 0L) {
                val percent = ((sum.toDouble() / totalBytes.toDouble()) * 100.0)
                    .toFloat()
                    .coerceIn(0f, 99.9f)
                val pInt = percent.toInt()
                val prev = lastEmittedPercent.get()
                if (pInt != prev && lastEmittedPercent.compareAndSet(prev, pInt)) {
                    trySend(percent)
                }
            }
        }

        // Send initial progress if resuming
        checkAndSendProgress()

        coroutineScope {
            val jobs = (0 until numChunks).map { i ->
                val startByte = i * chunkSize
                val endByte = if (i == numChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                val targetSize = endByte - startByte + 1
                val partFile = partFiles[i]

                async(Dispatchers.IO) {
                    var existingLen = if (partFile.exists()) partFile.length() else 0L
                    if (existingLen >= targetSize) {
                        writtenPerChunk.set(i, targetSize)
                        return@async
                    }

                    val reqStart = startByte + existingLen
                    val req = Request.Builder()
                        .url(url)
                        .header("Range", "bytes=$reqStart-$endByte")
                        .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")
                        .build()

                    var response = downloadClient.newCall(req).execute()

                    if (response.code == 416) {
                        response.close()
                        partFile.delete()
                        existingLen = 0L
                        writtenPerChunk.set(i, 0L)
                        val retryReq = Request.Builder()
                            .url(url)
                            .header("Range", "bytes=$startByte-$endByte")
                            .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")
                            .build()
                        response = downloadClient.newCall(retryReq).execute()
                    }

                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        throw IOException("HTTP error $code for chunk $i")
                    }

                    val body = response.body ?: throw IOException("Empty body for chunk $i")
                    val isPartial = response.code == 206
                    val appendMode = isPartial && existingLen > 0L
                    if (!appendMode) {
                        existingLen = 0L
                        if (partFile.exists()) partFile.delete()
                    }

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var currentWritten = existingLen
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                currentWritten += read
                                writtenPerChunk.set(i, currentWritten)
                                checkAndSendProgress()
                            }
                            output.flush()
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        // All chunks finished: merge them
        mergeChunkFiles(partFiles, destination)
        send(100f)
    }.flowOn(Dispatchers.IO)

    private fun mergeChunkFiles(partFiles: List<File>, destination: File) {
        if (destination.exists()) destination.delete()
        val tempDest = File(destination.parentFile, "${destination.name}.merge_tmp")
        if (tempDest.exists()) tempDest.delete()

        FileOutputStream(tempDest).use { out ->
            for (file in partFiles) {
                file.inputStream().use { it.copyTo(out) }
            }
            out.flush()
        }
        if (!tempDest.renameTo(destination)) {
            tempDest.copyTo(destination, overwrite = true)
            tempDest.delete()
        }
        partFiles.forEach { it.delete() }
    }

    private suspend fun downloadSingleStream(
        url: String,
        destination: File,
        tempFile: File,
        expectedTotalBytes: Long,
        onProgress: suspend (Float) -> Unit
    ) {
        var existingLength = if (tempFile.exists()) tempFile.length() else 0L

        if (expectedTotalBytes > 0L && existingLength >= expectedTotalBytes) {
            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }
            return
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")

        if (existingLength > 0L) {
            requestBuilder.header("Range", "bytes=$existingLength-")
        }

        var response = downloadClient.newCall(requestBuilder.build()).execute()

        if (response.code == 416) {
            response.close()
            tempFile.delete()
            existingLength = 0L
            val freshRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "Bunko/${currentVersion().orEmpty()}")
                .build()
            response = downloadClient.newCall(freshRequest).execute()
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Unexpected download code: $code")
        }

        val body = response.body ?: throw IOException("Empty download response")
        val isPartial = response.code == 206
        val appendMode = isPartial && existingLength > 0L
        if (!appendMode) {
            existingLength = 0L
            if (tempFile.exists()) tempFile.delete()
        }

        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0L) {
            if (appendMode) existingLength + contentLength else contentLength
        } else {
            expectedTotalBytes
        }

        body.byteStream().use { input ->
            FileOutputStream(tempFile, appendMode).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesWritten = existingLength
                var lastEmittedPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                    if (totalBytes > 0L) {
                        val percent = ((totalBytesWritten.toDouble() / totalBytes.toDouble()) * 100.0)
                            .toFloat()
                            .coerceIn(0f, 99.9f)
                        val pInt = percent.toInt()
                        if (pInt != lastEmittedPercent) {
                            lastEmittedPercent = pInt
                            onProgress(percent)
                        }
                    } else {
                        onProgress(-1f)
                    }
                }
                output.flush()
            }
        }

        if (destination.exists()) destination.delete()
        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }
    }

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

        const val ParallelChunkCount = 4
        const val MinParallelBytes = 2 * 1024 * 1024L // 2MB

        val json = Json { ignoreUnknownKeys = true }
        val apiClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val downloadClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 8
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
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
