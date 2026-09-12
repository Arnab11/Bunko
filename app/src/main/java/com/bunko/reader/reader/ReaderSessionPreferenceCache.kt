package com.bunko.reader.reader

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.bunko.reader.InvertMode
import com.bunko.reader.BunkoLog
import com.bunko.reader.KavitaSession
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.cache.imageCacheScope

internal const val ReaderSessionPreferenceTtlMillis = 30L * 24L * 60L * 60L * 1_000L
private const val ReaderSessionPreferenceCacheFileName = "reader_session_preferences.json"

internal data class ReaderSessionPreferences(
    val readingDirection: ReaderReadingDirection,
    val invertMode: InvertMode
) {
    constructor(rightToLeft: Boolean, invertMode: InvertMode) : this(
        readingDirection = if (rightToLeft) {
            ReaderReadingDirection.RightToLeft
        } else {
            ReaderReadingDirection.LeftToRight
        },
        invertMode = invertMode
    )

    val rightToLeft: Boolean
        get() = readingDirection == ReaderReadingDirection.RightToLeft
}

internal fun readerSessionPreferenceKey(
    session: KavitaSession,
    profileId: String?,
    seriesId: Int
): String = "${imageCacheScope(session, profileId)}:series:$seriesId"

internal object ReaderSessionPreferenceCache {
    private const val MaxEntries = 256

    private data class Entry(
        val preferences: ReaderSessionPreferences,
        val touchedAtMillis: Long
    )

    @Serializable
    private data class StoredEntry(
        val key: String,
        val readingDirection: String? = null,
        val rightToLeft: Boolean? = null,
        val invertMode: String,
        val touchedAtMillis: Long
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val diskMutex = Mutex()
    private var loadedDirectoryPath: String? = null

    suspend fun load(directory: File, nowMillis: Long) = diskMutex.withLock {
        val directoryPath = directory.absolutePath
        val alreadyLoaded = synchronized(this) {
            if (loadedDirectoryPath == directoryPath) {
                removeExpired(nowMillis)
                true
            } else {
                false
            }
        }
        if (alreadyLoaded) return@withLock
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                val file = directory.resolve(ReaderSessionPreferenceCacheFileName)
                if (file.exists()) json.decodeFromString<List<StoredEntry>>(file.readText()) else emptyList()
            }.onFailure {
                BunkoLog.w("Could not read Reader session preference cache.", it)
            }.getOrDefault(emptyList())
        }
        synchronized(this) {
            if (loadedDirectoryPath != directoryPath) {
                entries.clear()
                stored.forEach { storedEntry ->
                    val mode = runCatching { InvertMode.valueOf(storedEntry.invertMode) }.getOrNull()
                        ?: return@forEach
                    val direction = storedEntry.readingDirection
                        ?.let { runCatching { ReaderReadingDirection.valueOf(it) }.getOrNull() }
                        ?: if (storedEntry.rightToLeft != false) {
                            ReaderReadingDirection.RightToLeft
                        } else {
                            ReaderReadingDirection.LeftToRight
                        }
                    entries[storedEntry.key] = Entry(
                        preferences = ReaderSessionPreferences(direction, mode),
                        touchedAtMillis = storedEntry.touchedAtMillis
                    )
                }
                loadedDirectoryPath = directoryPath
            }
            removeExpired(nowMillis)
        }
    }

    @Synchronized
    fun get(key: String, nowMillis: Long): ReaderSessionPreferences? {
        removeExpired(nowMillis)
        val entry = entries[key] ?: return null
        entries[key] = entry.copy(touchedAtMillis = nowMillis)
        return entry.preferences
    }

    @Synchronized
    fun put(key: String, preferences: ReaderSessionPreferences, nowMillis: Long) {
        removeExpired(nowMillis)
        entries[key] = Entry(preferences, nowMillis)
        trimToMaximumSize()
    }

    suspend fun persist(directory: File) = diskMutex.withLock {
        val snapshot = synchronized(this) {
            entries.map { (key, entry) ->
                StoredEntry(
                    key = key,
                    readingDirection = entry.preferences.readingDirection.name,
                    invertMode = entry.preferences.invertMode.name,
                    touchedAtMillis = entry.touchedAtMillis
                )
            }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                directory.mkdirs()
                directory.resolve(ReaderSessionPreferenceCacheFileName)
                    .writeText(json.encodeToString(snapshot))
            }.onFailure {
                BunkoLog.w("Could not persist Reader session preference cache.", it)
            }
        }
    }

    suspend fun clear(directory: File) = diskMutex.withLock {
        synchronized(this) {
            entries.clear()
            loadedDirectoryPath = directory.absolutePath
        }
        withContext(Dispatchers.IO) {
            directory.resolve(ReaderSessionPreferenceCacheFileName).delete()
        }
    }

    @Synchronized
    internal fun clearMemoryForTest() {
        entries.clear()
        loadedDirectoryPath = null
    }

    private fun trimToMaximumSize() {
        while (entries.size > MaxEntries) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private fun removeExpired(nowMillis: Long) {
        entries.entries.removeAll { (_, entry) ->
            nowMillis - entry.touchedAtMillis >= ReaderSessionPreferenceTtlMillis
        }
    }
}
