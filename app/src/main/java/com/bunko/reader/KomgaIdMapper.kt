package com.bunko.reader

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Stable bidirectional mapping between Komga string ids and the Int ids used
 * throughout Bunko's (Kavita-shaped) UI layer.
 *
 * Forward mapping is a deterministic salted FNV-1a hash, so the same Komga id
 * always yields the same Int — even across process restarts. Reverse lookups
 * are served from in-memory tables populated on every forward mapping, so any
 * id the app has seen (libraries, series, books, collections, read lists,
 * synthetic person/genre/tag ids) resolves back without persistence.
 */
object KomgaIdMapper {
    private const val SALT_LIBRARY = "komga:library:"
    private const val SALT_SERIES = "komga:series:"
    private const val SALT_VOLUME = "komga:volume:"
    private const val SALT_BOOK = "komga:book:"
    private const val SALT_COLLECTION = "komga:collection:"
    private const val SALT_READLIST = "komga:readlist:"
    private const val SALT_PERSON = "komga:person:"
    private const val SALT_GENRE = "komga:genre:"
    private const val SALT_TAG = "komga:tag:"
    private const val SALT_PUBLISHER = "komga:publisher:"

    private val libraries = IdTable()
    private val series = IdTable()
    private val volumes = IdTable()
    private val books = IdTable()
    private val collections = IdTable()
    private val readLists = IdTable()
    private val persons = IdTable()
    private val genres = IdTable()
    private val tags = IdTable()
    private val publishers = IdTable()

    fun libraryId(komgaId: String): Int = libraries.map(SALT_LIBRARY, komgaId)
    fun seriesId(komgaId: String): Int = series.map(SALT_SERIES, komgaId)
    fun bookId(komgaId: String): Int = books.map(SALT_BOOK, komgaId)
    fun collectionId(komgaId: String): Int = collections.map(SALT_COLLECTION, komgaId)
    fun readListId(komgaId: String): Int = readLists.map(SALT_READLIST, komgaId)
    fun personId(key: String): Int = persons.map(SALT_PERSON, key)
    fun genreId(name: String): Int = genres.map(SALT_GENRE, name.lowercase())
    fun tagId(name: String): Int = tags.map(SALT_TAG, name.lowercase())
    fun publisherId(name: String): Int = publishers.map(SALT_PUBLISHER, name.lowercase())

    /** Deterministic volume id for a series (Komga books have no volumes). */
    fun volumeIdForSeries(komgaSeriesId: String): Int =
        volumes.map(SALT_VOLUME, komgaSeriesId)

    fun komgaLibraryId(id: Int): String? = libraries.reverse(id)
    fun komgaSeriesId(id: Int): String? = series.reverse(id)
    fun komgaBookId(id: Int): String? = books.reverse(id)
    fun komgaCollectionId(id: Int): String? = collections.reverse(id)
    fun komgaReadListId(id: Int): String? = readLists.reverse(id)
    fun personKey(id: Int): String? = persons.reverse(id)
    fun genreName(id: Int): String? = genres.reverse(id)
    fun tagName(id: Int): String? = tags.reverse(id)
    fun publisherName(id: Int): String? = publishers.reverse(id)

    /** Best-effort: is this Int a known Komga-mapped id of any kind? */
    fun isMapped(id: Int): Boolean {
        return libraries.reverse(id) != null ||
            series.reverse(id) != null ||
            volumes.reverse(id) != null ||
            books.reverse(id) != null ||
            collections.reverse(id) != null ||
            readLists.reverse(id) != null
    }

    // --- persistence across restarts (deep links, downloads, reader resume) ---

    @Volatile
    private var persistDir: File? = null
    private val persistExecutor = Executors.newSingleThreadScheduledExecutor()
    private val pendingPersist = AtomicReference<ScheduledFuture<*>?>(null)

    /**
     * Loads previously persisted id pairs. Safe to call repeatedly; also invoked
     * lazily from [ServerBackend] so every entry point is covered.
     */
    fun init(context: Context) {
        if (persistDir != null) return
        persistDir = context.applicationContext.filesDir
        runCatching { loadPersisted() }.onFailure {
            BunkoLog.w("Could not load Komga id map.", it)
        }
    }

    private fun persistFile(): File? = persistDir?.let { File(it, "komga_id_map.json") }

    private fun schedulePersist() {
        if (persistDir == null) return
        pendingPersist.getAndSet(
            persistExecutor.schedule(
                { runCatching { writePersisted() } },
                3,
                TimeUnit.SECONDS
            )
        )?.cancel(false)
    }

    private fun writePersisted() {
        val file = persistFile() ?: return
        val root = org.json.JSONObject()
        fun putTable(name: String, table: IdTable) {
            val obj = org.json.JSONObject()
            for ((key, id) in table.snapshot()) obj.put(key, id)
            root.put(name, obj)
        }
        putTable("libraries", libraries)
        putTable("series", series)
        putTable("volumes", volumes)
        putTable("books", books)
        putTable("collections", collections)
        putTable("readLists", readLists)
        putTable("persons", persons)
        putTable("genres", genres)
        putTable("tags", tags)
        putTable("publishers", publishers)
        val tmp = File(file.parent, "${file.name}.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun loadPersisted() {
        val file = persistFile() ?: return
        if (!file.isFile) return
        val root = org.json.JSONObject(file.readText())
        fun loadTable(name: String, table: IdTable) {
            val obj = root.optJSONObject(name) ?: return
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                table.restore(key, obj.optInt(key, 0))
            }
        }
        loadTable("libraries", libraries)
        loadTable("series", series)
        loadTable("volumes", volumes)
        loadTable("books", books)
        loadTable("collections", collections)
        loadTable("readLists", readLists)
        loadTable("persons", persons)
        loadTable("genres", genres)
        loadTable("tags", tags)
        loadTable("publishers", publishers)
    }

    private class IdTable {
        private val forward = ConcurrentHashMap<String, Int>()
        private val backward = ConcurrentHashMap<Int, String>()

        fun map(salt: String, key: String): Int {
            require(key.isNotBlank()) { "Komga id must not be blank" }
            forward[key]?.let { return it }
            var candidate = stableId(salt + key)
            // Linear-probe on collision with a *different* key (deterministic).
            while (true) {
                val existing = backward[candidate]
                if (existing == null || existing == key) break
                candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
            }
            forward[key] = candidate
            backward[candidate] = key
            // New pair: persist in the background so restarts resolve it.
            this@KomgaIdMapper.schedulePersist()
            return candidate
        }

        fun reverse(id: Int): String? = backward[id]

        fun snapshot(): Map<String, Int> = HashMap(forward)

        fun restore(key: String, id: Int) {
            if (key.isBlank() || id == 0) return
            // Deterministic ids win: only fill slots the live map hasn't claimed,
            // and never overwrite a different live pairing.
            val liveForward = forward[key]
            if (liveForward != null) return
            val liveBackward = backward[id]
            if (liveBackward != null && liveBackward != key) return
            forward[key] = id
            backward[id] = key
        }
    }

    private fun stableId(input: String): Int {
        // FNV-1a 32-bit, constrained to positive Ints; 0 is reserved for "none".
        var hash = 0x811c9dc5.toInt()
        for (b in input.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (b.toInt() and 0xff)
            hash *= 0x01000193
        }
        val positive = hash and 0x7fffffff
        return if (positive == 0) 1 else positive
    }
}
