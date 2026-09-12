package com.bunko.reader.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.bunko.reader.BunkoLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AvailableUpdate(
    val tagName: String,
    val releaseUrl: String
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val releaseUrl: String
)

internal class GitHubUpdateChecker(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE
    )

    suspend fun check(currentVersion: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedUpdate(currentVersion)
        val lastSuccessfulCheck = preferences.getLong(KeyLastSuccessfulCheck, 0L)
        if (now - lastSuccessfulCheck < CheckIntervalMillis) return@withContext cached

        val request = Request.Builder()
            .url(LatestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Bunko/$currentVersion")
            .apply {
                preferences.getString(KeyEtag, null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header("If-None-Match", it) }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> {
                        preferences.edit().putLong(KeyLastSuccessfulCheck, now).apply()
                        cached
                    }
                    response.isSuccessful -> {
                        val release = response.body?.string()?.let {
                            runCatching { json.decodeFromString<GitHubRelease>(it) }
                                .onFailure { error ->
                                    BunkoLog.w("Could not parse GitHub release response.", error)
                                }
                                .getOrNull()
                        }
                        if (release == null || !release.releaseUrl.startsWith(ReleaseUrlPrefix)) {
                            cached
                        } else {
                            preferences.edit()
                                .putLong(KeyLastSuccessfulCheck, now)
                                .putString(KeyEtag, response.header("ETag"))
                                .putString(KeyTagName, release.tagName)
                                .putString(KeyReleaseUrl, release.releaseUrl)
                                .apply()
                            release.toAvailableUpdate(currentVersion)
                        }
                    }
                    else -> {
                        cached
                    }
                }
            }
        } catch (t: IOException) {
            BunkoLog.w("Could not check GitHub releases.", t)
            cached
        }
    }

    private fun cachedUpdate(currentVersion: String): AvailableUpdate? {
        val tagName = preferences.getString(KeyTagName, null) ?: return null
        val releaseUrl = preferences.getString(KeyReleaseUrl, null)
            ?.takeIf { it.startsWith(ReleaseUrlPrefix) }
            ?: return null
        return if (isVersionNewer(tagName, currentVersion)) {
            AvailableUpdate(tagName, releaseUrl)
        } else {
            null
        }
    }

    private fun GitHubRelease.toAvailableUpdate(currentVersion: String): AvailableUpdate? {
        return if (isVersionNewer(tagName, currentVersion)) {
            AvailableUpdate(tagName, releaseUrl)
        } else {
            null
        }
    }

    private companion object {
        const val PreferencesName = "github_update_check"
        const val KeyLastSuccessfulCheck = "last_successful_check"
        const val KeyEtag = "etag"
        const val KeyTagName = "tag_name"
        const val KeyReleaseUrl = "release_url"
        const val LatestReleaseUrl =
            "https://api.github.com/repos/BunkoApp/Bunko/releases/latest"
        const val ReleaseUrlPrefix =
            "https://github.com/BunkoApp/Bunko/releases/"
        const val CheckIntervalMillis = 24L * 60L * 60L * 1000L

        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

internal fun isVersionNewer(candidate: String, current: String): Boolean {
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
