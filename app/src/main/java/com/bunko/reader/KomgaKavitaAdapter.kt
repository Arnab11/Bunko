package com.bunko.reader

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class KomgaUnsupportedException(message: String) : UnsupportedOperationException(message)

/** Int route ids for opening a Komga book directly in the reader. */
data class KomgaBookRoute(
    val libraryId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val chapterId: Int
)

/**
 * Implements Bunko's Kavita-shaped [KavitaApi] on top of a Komga server, so every
 * existing screen (Home, shelves, series, detail, reader, search, collections,
 * read lists, downloads) works unchanged when Komga is the active source.
 *
 * Komga string ids are mapped to stable Int ids via [KomgaIdMapper]. Books map
 * to chapters inside a single synthesized volume per series. Features with no
 * Komga equivalent (bookmarks, want-to-read, ratings, EPUB text extraction,
 * cover upload) degrade to empty results or no-ops.
 *
 * Page indexing: Kavita is 0-based, Komga pages/read-progress are 1-based.
 */
class KomgaKavitaAdapter(
    private val komga: KomgaApi,
    private val okHttp: OkHttpClient,
    private val baseUrl: String
) : KavitaApi {

    private val mapper = KomgaIdMapper
    private var librariesCache: List<KomgaLibraryDto>? = null
    private val booksCache = ConcurrentHashMap<String, List<KomgaBookDto>>()
    private val booksByIdCache = ConcurrentHashMap<String, KomgaBookDto>()
    private val manifestCache = ConcurrentHashMap<String, KomgaEpubManifestDto>()
    private val metadataParallelism = Semaphore(8)

    private fun KomgaBookDto.isEpub(): Boolean {
        val profile = media?.mediaProfile.orEmpty()
        val type = media?.mediaType.orEmpty()
        val isEpubBook = profile.equals("EPUB", ignoreCase = true) ||
            type.contains("epub", ignoreCase = true) ||
            url.endsWith(".epub", ignoreCase = true) ||
            name.endsWith(".epub", ignoreCase = true)
        BunkoLog.i("KomgaBookDto.isEpub for '$name': isEpubBook=$isEpubBook, profile=$profile, type=$type, divina=${media?.epubDivinaCompatible}, url=$url")
        return isEpubBook
    }

    private suspend fun komgaBook(bookKomgaId: String): KomgaBookDto {
        booksByIdCache[bookKomgaId]?.let { return it }
        for (books in booksCache.values) {
            val found = books.firstOrNull { it.id == bookKomgaId }
            if (found != null) {
                booksByIdCache[bookKomgaId] = found
                return found
            }
        }
        val fetched = komga.book(bookKomgaId)
        booksByIdCache[bookKomgaId] = fetched
        return fetched
    }

    private suspend fun epubManifest(bookKomgaId: String): KomgaEpubManifestDto {
        manifestCache[bookKomgaId]?.let {
            BunkoLog.i("KomgaKavitaAdapter: epubManifest cached for $bookKomgaId, readingOrder size=${it.readingOrder.size}")
            return it
        }
        val manifest = try {
            val res = komga.bookManifestEpub(bookKomgaId)
            BunkoLog.i("KomgaKavitaAdapter: fetched bookManifestEpub for $bookKomgaId: readingOrder size=${res.readingOrder.size}, toc size=${res.toc.size}")
            res
        } catch (e: Throwable) {
            BunkoLog.w("KomgaKavitaAdapter: bookManifestEpub failed for $bookKomgaId, trying fallback", e)
            try {
                val res = komga.bookManifestFallback(bookKomgaId)
                BunkoLog.i("KomgaKavitaAdapter: fetched bookManifestFallback for $bookKomgaId: readingOrder size=${res.readingOrder.size}")
                res
            } catch (e2: Throwable) {
                BunkoLog.e("KomgaKavitaAdapter: bookManifestFallback failed for $bookKomgaId", e2)
                throw e2
            }
        }
        manifestCache[bookKomgaId] = manifest
        return manifest
    }

    private fun resolveResourceUrl(bookKomgaId: String, href: String): String {
        val cleanHref = href.substringBefore("#").trim()
        if (cleanHref.startsWith("http://", ignoreCase = true) || cleanHref.startsWith("https://", ignoreCase = true)) {
            return cleanHref
        }
        val root = baseUrl.trimEnd('/')
        val path = cleanHref.removePrefix("./").removePrefix("/")
        if (path.startsWith("api/")) {
            return "$root/$path"
        }
        if (path.startsWith("resource/")) {
            return "$root/api/v1/books/$bookKomgaId/$path"
        }
        val decoded = android.net.Uri.decode(path)
        val encoded = decoded.split("/").joinToString("/") { android.net.Uri.encode(it) }
        return "$root/api/v1/books/$bookKomgaId/resource/$encoded"
    }

    // --- helpers ---

    private suspend fun libraries(): List<KomgaLibraryDto> {
        return librariesCache ?: komga.libraries().also { librariesCache = it }
    }

    private suspend fun libraryName(libraryId: String): String? {
        return libraries().firstOrNull { it.id == libraryId }?.name
    }

    private suspend fun seriesBooks(komgaSeriesId: String): List<KomgaBookDto> {
        booksCache[komgaSeriesId]?.let { return it }
        val books = try {
            komga.seriesBooks(komgaSeriesId, unpaged = true).content
        } catch (_: Throwable) {
            komga.seriesBooks(komgaSeriesId, unpaged = null, page = 0, size = 500).content
        }
        booksCache[komgaSeriesId] = books
        return books
    }

    private fun invalidateBooks(komgaSeriesId: String) {
        val cached = booksCache.remove(komgaSeriesId)
        cached?.forEach { booksByIdCache.remove(it.id) }
    }

    private suspend fun allKomgaSeries(): List<KomgaSeriesDto> {
        return try {
            komga.seriesList(body = KomgaSeriesSearchDto(), unpaged = true).content
        } catch (_: Throwable) {
            try {
                komga.seriesList(body = KomgaSeriesSearchDto(), page = 0, size = 500).content
            } catch (_: Throwable) {
                // Pre-1.15 Komga without POST /series/list.
                runCatching { komga.allSeriesLegacy(unpaged = true).content }.getOrDefault(emptyList())
            }
        }
    }

    private fun KomgaSeriesDto.displayName(): String {
        return metadata?.title?.takeIf { it.isNotBlank() }
            ?: name.substringAfterLast('/').substringAfterLast('\\').ifBlank { name }
    }

    private fun KomgaBookDto.displayName(): String {
        val rawTitle = metadata?.title?.trim()?.takeIf { it.isNotBlank() }
            ?: name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').trim().ifBlank { name }
        val series = seriesTitle.trim()
        val num = metadata?.number?.trim()?.takeIf { it.isNotBlank() }
            ?: metadata?.numberSort?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
            ?: if (number > 0) number.toString() else null

        if (series.isNotBlank() && rawTitle.equals(series, ignoreCase = true)) {
            val fileCleanName = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').trim()
            if (fileCleanName.isNotBlank() && !fileCleanName.equals(series, ignoreCase = true)) {
                if (fileCleanName.startsWith(series, ignoreCase = true) && fileCleanName.length > series.length) {
                    val stripped = fileCleanName.substring(series.length).trimStart(' ', '-', ':', '#', '_', '/').trim()
                    if (stripped.isNotBlank()) return stripped
                } else {
                    return fileCleanName
                }
            }
            if (num != null) {
                return "Chapter $num"
            }
        }
        if (series.isNotBlank() && rawTitle.startsWith(series, ignoreCase = true) && rawTitle.length > series.length) {
            val stripped = rawTitle.substring(series.length).trimStart(' ', '-', ':', '#', '_', '/').trim()
            if (stripped.isNotBlank()) {
                return stripped
            }
        }
        return rawTitle
    }

    private suspend fun toSeriesDto(s: KomgaSeriesDto): SeriesDto {
        return SeriesDto(
            id = mapper.seriesId(s.id),
            name = s.displayName(),
            originalName = s.name.takeIf { it != s.displayName() },
            localizedName = s.metadata?.title,
            libraryId = mapper.libraryId(s.libraryId),
            libraryName = libraryName(s.libraryId),
            // Book-level progress keeps Home sorts and progress bars meaningful.
            pages = s.booksCount,
            pagesRead = s.booksReadCount,
            format = null,
            volumes = emptyList()
        )
    }

    private fun toVolumeDto(komgaSeriesId: String, books: List<KomgaBookDto>): VolumeDto {
        // Komga books have no volumes: leave the name blank so chapter cards list
        // individual books instead of a synthetic group label.
        return VolumeDto(
            id = mapper.volumeIdForSeries(komgaSeriesId),
            name = null,
            number = null,
            chapters = books.sortedBy { it.number }.map { toChapterDto(it) }
        )
    }

    private fun toChapterDto(b: KomgaBookDto): ChapterDto {
        val pages = b.media?.pagesCount ?: 0
        val progress = b.readProgress
        val pagesRead = when {
            progress == null -> 0
            progress.completed -> pages
            progress.page > 0 -> progress.page.coerceIn(0, pages)
            else -> 0
        }
        val authorsByRole = b.metadata?.authors.orEmpty().groupBy { it.role.lowercase() }
        fun people(vararg roles: String): List<PersonDto> {
            return roles.flatMap { role ->
                authorsByRole[role].orEmpty().map { author ->
                    PersonDto(id = mapper.personId("$role:${author.name}"), name = author.name)
                }
            }.distinctBy { it.id }
        }
        val chapterNumStr = b.metadata?.number?.trim()?.takeIf { it.isNotBlank() }
            ?: b.metadata?.numberSort?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
            ?: if (b.number > 0) b.number.toString() else null
        return ChapterDto(
            id = mapper.bookId(b.id),
            title = b.displayName(),
            number = chapterNumStr?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
                ?: runCatching { Json.parseToJsonElement(b.number.toString()) }.getOrNull(),
            sortOrder = b.metadata?.numberSort ?: b.number.toFloat(),
            pages = pages,
            pagesRead = pagesRead,
            volumeId = mapper.volumeIdForSeries(b.seriesId),
            summary = b.metadata?.summary,
            releaseDate = b.metadata?.releaseDate,
            // EPUBs flow through Bunko's text engine (spines via the manifest);
            // everything else renders as server-generated page images.
            format = if (b.isEpub()) MangaFormat.Epub else null,
            writers = people("writer"),
            coverArtists = people("cover", "cover artist"),
            pencillers = people("penciller", "penciler"),
            inkers = people("inker"),
            colorists = people("colorist", "colourist"),
            letterers = people("letterer"),
            editors = people("editor"),
            translators = people("translator"),
            isSpecial = false
        )
    }

    private fun toSeriesMetadataDto(s: KomgaSeriesDto): SeriesMetadataDto {
        val m = s.metadata
        fun personList(role: String, names: List<String>): List<PersonDto> {
            return names.filter { it.isNotBlank() }.distinct()
                .map { PersonDto(id = mapper.personId("$role:$it"), name = it) }
        }
        // Authors live on books in Komga; surface series-level contributors best-effort
        // from cached books when available (no extra fetch here).
        val cachedAuthors = booksCache[s.id].orEmpty()
            .flatMap { it.metadata?.authors.orEmpty() }
        fun authorsFor(vararg roles: String): List<PersonDto> {
            return cachedAuthors.filter { a -> roles.any { it.equals(a.role, ignoreCase = true) } }
                .map { PersonDto(id = mapper.personId("${it.role.lowercase()}:${it.name}"), name = it.name) }
                .distinctBy { it.id }
        }
        return SeriesMetadataDto(
            summary = m?.summary,
            genres = m?.genres.orEmpty().filter { it.isNotBlank() }.distinct()
                .map { GenreTagDto(id = mapper.genreId(it), title = it) },
            tags = m?.tags.orEmpty().filter { it.isNotBlank() }.distinct()
                .map { TagDto(id = mapper.tagId(it), title = it) },
            writers = authorsFor("writer"),
            coverArtists = authorsFor("cover", "cover artist"),
            pencillers = authorsFor("penciller", "penciler"),
            inkers = authorsFor("inker"),
            colorists = authorsFor("colorist", "colourist"),
            letterers = authorsFor("letterer"),
            editors = authorsFor("editor"),
            translators = authorsFor("translator"),
            publishers = m?.publisher?.takeIf { it.isNotBlank() }?.let {
                personList("publisher", listOf(it))
            }.orEmpty(),
            imprints = emptyList(),
            publicationStatus = when (m?.status?.uppercase()) {
                "ONGOING" -> 0
                "HIATUS" -> 1
                "COMPLETED" -> 2
                "CANCELLED" -> 3
                "ENDED" -> 4
                else -> null
            },
            releaseYear = null,
            language = m?.language
        )
    }

    private fun komgaSeriesIdOrThrow(id: Int): String {
        return mapper.komgaSeriesId(id)
            ?: throw IllegalArgumentException("Unknown Komga series id $id")
    }

    private fun komgaBookIdOrThrow(id: Int): String {
        return mapper.komgaBookId(id)
            ?: throw IllegalArgumentException("Unknown Komga book id $id")
    }

    // --- KavitaApi implementation ---

    override suspend fun health() {
        libraries()
    }

    override suspend fun login(body: LoginDto): UserDto {
        throw KomgaUnsupportedException("Komga authenticates via the connect dialog, not password login")
    }

    override suspend fun currentUser(): UserDto {
        val key = "komga:$baseUrl"
        ActiveUserCache.getUser(key)?.let { return it }
        val me = komga.currentUser()
        val roles = me.roles.map { it.removePrefix("ROLE_") }
        val dto = UserDto(username = me.email, roles = roles, token = null)
        ActiveUserCache.setUser(key, dto)
        return dto
    }

    override suspend fun userLibraries(): List<LibraryDto> {
        return libraries().map {
            LibraryDto(id = mapper.libraryId(it.id), name = it.name)
        }.sortedBy { it.id }
    }

    override suspend fun scanLibrary(libraryId: Int, force: Boolean) {
        val komgaId = mapper.komgaLibraryId(libraryId)
            ?: throw IllegalArgumentException("Unknown Komga library id $libraryId")
        komga.scanLibrary(komgaId)
    }

    override suspend fun allSeriesV2(
        body: SeriesFilterV2Dto,
        pageNumber: Int?,
        pageSize: Int?
    ): List<SeriesDto> {
        val candidates = resolveFilterCandidates(body)
        val mapped = candidates.map { toSeriesDto(it) }
        val page = (pageNumber ?: 0).coerceAtLeast(0)
        val size = (pageSize ?: 200).coerceAtLeast(1)
        return mapped.drop(page * size).take(size)
    }

    override suspend fun allSeriesV2Response(
        body: SeriesFilterV2Dto,
        pageNumber: Int?,
        pageSize: Int?
    ): Response<List<SeriesDto>> {
        val candidates = resolveFilterCandidates(body)
        val page = (pageNumber ?: 0).coerceAtLeast(0)
        val size = (pageSize ?: 1).coerceAtLeast(1)
        val mapped = candidates.map { toSeriesDto(it) }
        val slice = mapped.drop(page * size).take(size)
        val header = Headers.headersOf(
            "Pagination",
            """{"totalItems":${candidates.size},"totalPages":${(candidates.size + size - 1) / size},"currentPage":$page,"itemsPerPage":$size}"""
        )
        return Response.success(slice, header)
    }

    /**
     * Interprets the small set of Kavita filter shapes Bunko actually emits:
     * library (19), genres (18), tags (6), publisher (10), person roles
     * (8,9,11-17,30,31), collections (7). Anything else matches nothing.
     */
    private suspend fun resolveFilterCandidates(body: SeriesFilterV2Dto): List<KomgaSeriesDto> {
        val statements = body.statements
        if (statements.isEmpty()) return allKomgaSeries()

        val collectionStmt = statements.firstOrNull { it.field == 7 }
        if (collectionStmt != null) {
            val collId = mapper.komgaCollectionId(collectionStmt.value.toIntOrNull() ?: -1)
                ?: return emptyList()
            return try {
                komga.collectionSeries(collId, unpaged = true).content
            } catch (_: Throwable) {
                emptyList()
            }
        }

        val libraryStmt = statements.firstOrNull { it.field == 19 }
        val base: List<KomgaSeriesDto> = if (libraryStmt != null) {
            val libKomgaId = libraryStmt.value.toIntOrNull()?.let { mapper.komgaLibraryId(it) }
                ?: return emptyList()
            try {
                komga.seriesList(body = KomgaSeriesSearchDto(), unpaged = true).content
                    .filter { it.libraryId == libKomgaId }
            } catch (_: Throwable) {
                runCatching {
                    komga.allSeriesLegacy(libraryIds = listOf(libKomgaId), unpaged = true).content
                        .filter { it.libraryId == libKomgaId }
                }.getOrDefault(emptyList())
            }
        } else {
            allKomgaSeries()
        }
        if (base.isEmpty()) return emptyList()

        val personFields = setOf(8, 9, 11, 12, 13, 14, 15, 16, 17, 30, 31)
        val personIds = statements.filter { it.field in personFields }
            .mapNotNull { it.value.toIntOrNull() }.toSet()
        val genreNames = statements.filter { it.field == 18 }
            .mapNotNull { it.value.toIntOrNull()?.let { id -> mapper.genreName(id) } }
            .map { it.lowercase() }.toSet()
        val tagNames = statements.filter { it.field == 6 }
            .mapNotNull { it.value.toIntOrNull()?.let { id -> mapper.tagName(id) } }
            .map { it.lowercase() }.toSet()
        val publisherNames = statements.filter { it.field == 10 }
            .mapNotNull { it.value.toIntOrNull()?.let { id -> mapper.publisherName(id) } }
            .map { it.lowercase() }.toSet()
        val hasImprint = statements.any { it.field == 29 }
        if (hasImprint && statements.size == 1) return emptyList()

        if (personIds.isEmpty() && genreNames.isEmpty() && tagNames.isEmpty() && publisherNames.isEmpty()) {
            return base
        }

        // Per-series matching; metadata is embedded in the list response, authors
        // need the series' books (fetched in parallel, cached by seriesBooks()).
        val orUnion = body.combination == 0
        return coroutineScope {
            base.map { s ->
                async {
                    val matches = matchStatements(s, personIds, genreNames, tagNames, publisherNames, orUnion)
                    if (matches) s else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun matchStatements(
        s: KomgaSeriesDto,
        personIds: Set<Int>,
        genreNames: Set<String>,
        tagNames: Set<String>,
        publisherNames: Set<String>,
        orUnion: Boolean
    ): Boolean {
        val checks = mutableListOf<Boolean>()
        if (genreNames.isNotEmpty()) {
            val seriesGenres = s.metadata?.genres.orEmpty().map { it.lowercase() }.toSet()
            checks.add(genreNames.any { it in seriesGenres })
        }
        if (tagNames.isNotEmpty()) {
            val seriesTags = s.metadata?.tags.orEmpty().map { it.lowercase() }.toSet()
            checks.add(tagNames.any { it in seriesTags })
        }
        if (publisherNames.isNotEmpty()) {
            val publisher = s.metadata?.publisher?.lowercase().orEmpty()
            checks.add(publisherNames.any { it == publisher })
        }
        if (personIds.isNotEmpty()) {
            val books = metadataParallelism.withPermit { seriesBooks(s.id) }
            val bookPersonIds = books.flatMap { b ->
                b.metadata?.authors.orEmpty().map { a -> mapper.personId("${a.role.lowercase()}:${a.name}") }
            }.toSet()
            checks.add(personIds.any { it in bookPersonIds })
        }
        if (checks.isEmpty()) return true
        return if (orUnion) checks.any { it } else checks.all { it }
    }

    override suspend fun series(id: Int): SeriesDto {
        val komgaId = komgaSeriesIdOrThrow(id)
        return toSeriesDto(komga.series(komgaId))
    }

    override suspend fun seriesByIds(body: SeriesByIdsDto): List<SeriesDto> {
        return coroutineScope {
            body.seriesIds.map { id ->
                async {
                    runCatching { series(id) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun seriesMetadata(seriesId: Int): SeriesMetadataDto {
        val komgaId = komgaSeriesIdOrThrow(seriesId)
        // Warm the books cache so contributor credits can be derived.
        runCatching { seriesBooks(komgaId) }
        return toSeriesMetadataDto(komga.series(komgaId))
    }

    override suspend fun onDeck(
        pageNumber: Int?,
        pageSize: Int?,
        libraryId: Int?
    ): List<SeriesDto> {
        val libKomgaId = libraryId?.let { mapper.komgaLibraryId(it) }
        if (libraryId != null && libKomgaId == null) return emptyList()
        val size = (pageSize ?: 12).coerceAtLeast(1)
        val page = (pageNumber ?: 0).coerceAtLeast(0)
        val inProgressBooks = runCatching {
            komga.books(
                libraryIds = libKomgaId?.let { listOf(it) },
                readStatus = listOf("IN_PROGRESS"),
                sort = "readProgress.readDate,desc",
                page = page,
                size = size
            ).content
        }.getOrDefault(emptyList())
        val onDeckBooks = runCatching {
            komga.onDeck(
                libraryIds = libKomgaId?.let { listOf(it) },
                page = page,
                size = size
            ).content
        }.getOrDefault(emptyList())
        val books = (inProgressBooks + onDeckBooks).distinctBy { it.id }
        val seriesIds = books.map { it.seriesId }.distinct()
        return coroutineScope {
            seriesIds.map { sid ->
                async { runCatching { toSeriesDto(komga.series(sid)) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun recentlyAdded(
        body: SeriesFilterV2Dto,
        pageNumber: Int?,
        pageSize: Int?
    ): List<SeriesDto> {
        val libStmt = body.statements.firstOrNull { it.field == 19 }
        val libKomgaId = libStmt?.value?.toIntOrNull()?.let { mapper.komgaLibraryId(it) }
        val size = (pageSize ?: 16).coerceAtLeast(1)
        val page = (pageNumber ?: 0).coerceAtLeast(0)
        val result = komga.newSeries(
            libraryIds = libKomgaId?.let { listOf(it) },
            page = page,
            size = size
        ).content
        return result.map { toSeriesDto(it) }
    }

    override suspend fun recentlyUpdatedSeries(
        pageNumber: Int?,
        pageSize: Int?
    ): List<GroupedSeriesDto> {
        val size = (pageSize ?: 16).coerceAtLeast(1)
        val page = (pageNumber ?: 0).coerceAtLeast(0)
        return komga.updatedSeries(page = page, size = size).content.map { s ->
            GroupedSeriesDto(
                seriesId = mapper.seriesId(s.id),
                seriesName = s.displayName(),
                libraryId = mapper.libraryId(s.libraryId)
            )
        }
    }

    override suspend fun volumes(seriesId: Int): List<VolumeDto> {
        val komgaId = komgaSeriesIdOrThrow(seriesId)
        val books = seriesBooks(komgaId)
        if (books.isEmpty()) return emptyList()
        return listOf(toVolumeDto(komgaId, books))
    }

    override suspend fun seriesChapter(chapterId: Int): ChapterDto {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        return toChapterDto(komga.book(bookKomgaId))
    }

    override suspend fun search(queryString: String, includeChapterAndFiles: Boolean): SearchResultGroupDto {
        val q = queryString.trim()
        if (q.isBlank()) return SearchResultGroupDto()
        val seriesPage = runCatching {
            komga.seriesList(body = KomgaSeriesSearchDto(fullTextSearch = q), page = 0, size = 50).content
        }.getOrDefault(emptyList())
        // Warm caches + derive people/genre/tag chips from matched series.
        val persons = linkedMapOf<Int, PersonDto>()
        val genres = linkedMapOf<Int, GenreTagDto>()
        val tags = linkedMapOf<Int, TagDto>()
        coroutineScope {
            seriesPage.map { s ->
                async {
                    val books = runCatching { seriesBooks(s.id) }.getOrDefault(emptyList())
                    Triple(s, books, s)
                }
            }.awaitAll()
        }.forEach { (s, books, _) ->
            books.flatMap { it.metadata?.authors.orEmpty() }.forEach { a ->
                if (a.name.contains(q, ignoreCase = true)) {
                    val id = mapper.personId("${a.role.lowercase()}:${a.name}")
                    persons.getOrPut(id) { PersonDto(id = id, name = a.name) }
                }
            }
            s.metadata?.genres.orEmpty().filter { it.contains(q, ignoreCase = true) }.forEach { g ->
                val id = mapper.genreId(g)
                genres.getOrPut(id) { GenreTagDto(id = id, title = g) }
            }
            s.metadata?.tags.orEmpty().filter { it.contains(q, ignoreCase = true) }.forEach { t ->
                val id = mapper.tagId(t)
                tags.getOrPut(id) { TagDto(id = id, title = t) }
            }
        }
        val chapters = if (includeChapterAndFiles) {
            runCatching {
                komga.booksList(body = KomgaBookSearchDto(fullTextSearch = q), page = 0, size = 20).content
            }.getOrDefault(emptyList()).map { toChapterDto(it) }
        } else emptyList()
        return SearchResultGroupDto(
            series = seriesPage.map { s ->
                SearchResultDto(
                    seriesId = mapper.seriesId(s.id),
                    name = s.displayName(),
                    originalName = s.name,
                    localizedName = s.metadata?.title,
                    libraryName = libraryName(s.libraryId),
                    libraryId = mapper.libraryId(s.libraryId),
                    chapterCount = s.booksCount
                )
            },
            collections = emptyList(),
            readingLists = emptyList(),
            persons = persons.values.toList(),
            genres = genres.values.toList(),
            tags = tags.values.toList(),
            chapters = chapters
        )
    }

    override suspend fun seriesForChapter(chapterId: Int): SeriesDto {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        val book = komga.book(bookKomgaId)
        return toSeriesDto(komga.series(book.seriesId))
    }

    override suspend fun collections(): List<CollectionDto> {
        return komga.collections().content.map { c ->
            CollectionDto(
                id = mapper.collectionId(c.id),
                title = c.name,
                itemCount = c.seriesIds.size
            )
        }
    }

    override suspend fun chapterSize(chapterId: Int): Long {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        return komga.book(bookKomgaId).sizeBytes
    }

    override suspend fun readingProfile(
        libraryId: Int,
        seriesId: Int,
        skipImplicit: Boolean
    ): UserReadingProfileDto {
        val komgaId = mapper.komgaSeriesId(seriesId)
            ?: return UserReadingProfileDto(readingDirection = null, kind = 0)
        val direction = runCatching { komga.series(komgaId).metadata?.readingDirection }.getOrNull()
        return when (direction?.uppercase()) {
            "LEFT_TO_RIGHT" -> UserReadingProfileDto(readingDirection = 0, kind = 1)
            "RIGHT_TO_LEFT" -> UserReadingProfileDto(readingDirection = 1, kind = 1)
            "VERTICAL", "WEBTOON" -> UserReadingProfileDto(readingDirection = 2, kind = 1)
            else -> UserReadingProfileDto(readingDirection = null, kind = 0)
        }
    }

    override suspend fun chapterInfo(
        chapterId: Int,
        includeDimensions: Boolean,
        extractPdf: Boolean
    ): ChapterInfoDto {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        val book = komgaBook(bookKomgaId)
        if (book.isEpub()) {
            // EPUB flow paginates manifest spines (mirrors Kavita spine counts).
            val spines = runCatching { epubManifest(bookKomgaId).readingOrder.size }.getOrDefault(0)
            return ChapterInfoDto(
                chapterId = chapterId,
                pages = spines,
                pageDimensions = emptyList()
            )
        }
        // Page dimensions drive webtoon auto-detection and strip layout,
        // exactly like Kavita's includeDimensions. Komga numbers pages from 1.
        val dimensions = if (includeDimensions) {
            runCatching { komga.bookPages(bookKomgaId) }.getOrDefault(emptyList()).mapNotNull { page ->
                val index = page.number - 1
                if (index < 0) return@mapNotNull null
                val w = page.width ?: 0
                val h = page.height ?: 0
                FileDimensionDto(
                    width = w,
                    height = h,
                    pageNumber = index,
                    fileName = page.fileName.takeIf { it.isNotBlank() },
                    isWide = w > 0 && h > 0 && w > h
                )
            }
        } else {
            emptyList()
        }
        return ChapterInfoDto(
            chapterId = chapterId,
            pages = book.media?.pagesCount ?: 0,
            pageDimensions = dimensions
        )
    }

    override suspend fun bookPage(chapterId: Int, page: Int): ResponseBody {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        val book = runCatching { komgaBook(bookKomgaId) }.getOrNull()
        BunkoLog.i("KomgaKavitaAdapter: bookPage called for chapterId=$chapterId, page=$page, book=$bookKomgaId, isEpub=${book?.isEpub()}")
        if (book != null && book.isEpub()) {
            val manifest = epubManifest(bookKomgaId)
            val spine = manifest.readingOrder.getOrNull(page)
                ?: throw IOException("Komga EPUB has no spine $page")
            val resourceUrl = resolveResourceUrl(bookKomgaId, spine.href)
            BunkoLog.i("KomgaKavitaAdapter: fetching EPUB resource url: $resourceUrl (spine.href=${spine.href})")
            val request = Request.Builder()
                .url(resourceUrl)
                .get()
                .build()
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    BunkoLog.e("KomgaKavitaAdapter: Komga EPUB resource failed: HTTP ${resp.code} for $resourceUrl")
                    throw IOException("Komga EPUB resource failed: HTTP ${resp.code}")
                }
                val bytes = resp.body?.bytes() ?: throw IOException("Empty Komga EPUB resource")
                BunkoLog.i("KomgaKavitaAdapter: received EPUB spine $page size=${bytes.size} bytes for $bookKomgaId")
                return bytes.toResponseBody("text/html".toMediaTypeOrNull())
            }
        }
        val url = "$baseUrl/api/v1/books/$bookKomgaId/pages/${page + 1}"
        val request = Request.Builder().url(url).get().build()
        okHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Komga page failed: HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IOException("Empty Komga page body")
            return bytes.toResponseBody("image/jpeg".toMediaTypeOrNull())
        }
    }

    override suspend fun bookChapters(chapterId: Int): List<BookChapterItemDto> {
        // EPUB TOC comes from the manifest; other formats render as page
        // images and expose no TOC.
        val bookKomgaId = mapper.komgaBookId(chapterId) ?: return emptyList()
        val book = runCatching { komga.book(bookKomgaId) }.getOrNull()
        if (book == null || !book.isEpub()) return emptyList()
        val manifest = runCatching { epubManifest(bookKomgaId) }.getOrNull()
            ?: return emptyList()
        val spines = manifest.readingOrder.map { it.href }
        fun mapToc(entries: List<KomgaEpubTocEntryDto>): List<BookChapterItemDto> {
            return entries.mapNotNull { entry ->
                val title = entry.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BookChapterItemDto(
                    title = title,
                    part = null,
                    page = spineIndexFor(entry.href, spines),
                    children = mapToc(entry.children)
                )
            }
        }
        return mapToc(manifest.toc)
    }

    private fun spineIndexFor(href: String?, spines: List<String>): Int {
        if (href.isNullOrBlank()) return 0
        val clean = href.substringBefore("#").trim().removePrefix("./").removePrefix("/")
        if (clean.isBlank()) return 0
        val match = spines.indexOfFirst { spine ->
            val cleanSpine = spine.substringBefore("#").trim().removePrefix("./").removePrefix("/")
            cleanSpine == clean ||
                cleanSpine.endsWith("/$clean") ||
                clean.endsWith("/$cleanSpine") ||
                cleanSpine.substringAfterLast("/") == clean.substringAfterLast("/")
        }
        return match.coerceAtLeast(0)
    }

    override suspend fun bookInfo(chapterId: Int): BookInfoDto {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        val book = komga.book(bookKomgaId)
        return BookInfoDto(
            pages = book.media?.pagesCount,
            seriesId = mapper.seriesId(book.seriesId),
            volumeId = mapper.volumeIdForSeries(book.seriesId),
            libraryId = mapper.libraryId(book.libraryId),
            bookTitle = book.displayName(),
            chapterTitle = book.displayName(),
            seriesName = book.seriesTitle
        )
    }

    override suspend fun getProgress(chapterId: Int): ProgressDto {
        val bookKomgaId = komgaBookIdOrThrow(chapterId)
        val book = komga.book(bookKomgaId)
        val progress = book.readProgress
        // Komelia parity: unread AND completed books both (re)start at page 1;
        // Komga stores 1-based pages, Kavita consumes 0-based.
        val pageNum = if (progress == null || progress.completed || progress.page <= 0) {
            0
        } else {
            progress.page - 1
        }
        return ProgressDto(
            libraryId = mapper.libraryId(book.libraryId),
            seriesId = mapper.seriesId(book.seriesId),
            volumeId = mapper.volumeIdForSeries(book.seriesId),
            chapterId = chapterId,
            pageNum = pageNum.coerceAtLeast(0),
            bookScrollId = null
        )
    }

    override suspend fun continuePoint(seriesId: Int): ChapterDto {
        val komgaId = komgaSeriesIdOrThrow(seriesId)
        val books = seriesBooks(komgaId).sortedBy { it.number }
        if (books.isEmpty()) throw IOException("Komga series has no books")
        val inProgress = books.filter { it.readProgress != null && !it.readProgress.completed && it.readProgress.page > 0 }
            .maxByOrNull { it.readProgress?.readDate.orEmpty() }
        val nextUnread = books.firstOrNull { it.readProgress == null || (!it.readProgress.completed && it.readProgress.page <= 0) }
        val next = inProgress ?: nextUnread ?: books.first()
        return toChapterDto(next)
    }

    override suspend fun allBookmarks(body: SeriesFilterV2Dto): List<BookmarkDto> {
        return emptyList()
    }

    override suspend fun bookmark(dto: BookmarkDto) {
        // No Komga equivalent; local reader bookmarks still work.
    }

    override suspend fun unBookmark(dto: BookmarkDto) {
    }

    override suspend fun saveProgress(dto: ProgressDto) {
        val bookKomgaId = mapper.komgaBookId(dto.chapterId) ?: return
        val book = runCatching { komgaBook(bookKomgaId) }.getOrNull()
        val totalPages = if (book?.isEpub() == true) {
            runCatching { epubManifest(bookKomgaId).readingOrder.size }.getOrDefault(book.media?.pagesCount ?: 0)
        } else {
            book?.media?.pagesCount ?: 0
        }
        val komgaPage = if (totalPages > 0) {
            (dto.pageNum + 1).coerceIn(1, totalPages)
        } else {
            (dto.pageNum + 1).coerceAtLeast(1)
        }
        val isCompleted = totalPages > 1 && komgaPage >= totalPages
        runCatching {
            komga.updateReadProgress(
                bookKomgaId,
                KomgaReadProgressUpdateDto(
                    page = komgaPage,
                    completed = if (isCompleted) true else null
                )
            )
        }.onSuccess {
            BunkoLog.i("KomgaKavitaAdapter: updated read progress for $bookKomgaId to page=$komgaPage / $totalPages (completed=$isCompleted)")
        }.onFailure {
            BunkoLog.w("KomgaKavitaAdapter: updateReadProgress failed for $bookKomgaId page=$komgaPage / total=$totalPages", it)
        }
        invalidateBooksForBook(bookKomgaId)
    }

    override suspend fun markChapterRead(dto: MarkChapterReadDto) {
        val bookKomgaId = mapper.komgaBookId(dto.chapterId) ?: return
        val book = runCatching { komgaBook(bookKomgaId) }.getOrNull()
        val totalPages = book?.media?.pagesCount ?: 1
        runCatching {
            komga.updateReadProgress(
                bookKomgaId,
                KomgaReadProgressUpdateDto(
                    page = totalPages,
                    completed = true
                )
            )
        }.onFailure {
            BunkoLog.w("KomgaKavitaAdapter: markChapterRead failed for $bookKomgaId", it)
        }
        invalidateBooksForBook(bookKomgaId)
    }

    override suspend fun markChaptersUnread(dto: MarkVolumesReadDto) {
        dto.chapterIds.forEach { id ->
            val bookKomgaId = mapper.komgaBookId(id) ?: return@forEach
            runCatching {
                komga.deleteReadProgress(bookKomgaId)
            }.onFailure {
                BunkoLog.w("KomgaKavitaAdapter: deleteReadProgress failed for $bookKomgaId", it)
            }
            invalidateBooksForBook(bookKomgaId)
        }
    }

    private suspend fun invalidateBooksForBook(bookKomgaId: String) {
        val cached = booksByIdCache.remove(bookKomgaId)
        val seriesId = cached?.seriesId ?: runCatching { komga.book(bookKomgaId).seriesId }.getOrNull()
        if (seriesId != null) invalidateBooks(seriesId)
    }

    // --- flat book browsing (Komga library screens) ---

    /** All books in a Komga library (client-filtered; robust across server versions). */
    suspend fun booksForLibrary(libraryId: Int): List<KomgaBookDto> {
        val libKomgaId = mapper.komgaLibraryId(libraryId)
            ?: throw IllegalArgumentException("Unknown Komga library id $libraryId")
        val all = try {
            komga.booksList(body = KomgaBookSearchDto(), unpaged = true).content
        } catch (_: Throwable) {
            // Paged fallback accumulates every page.
            val acc = mutableListOf<KomgaBookDto>()
            var page = 0
            while (true) {
                val slice = komga.booksList(body = KomgaBookSearchDto(), page = page, size = 500).content
                acc += slice
                if (slice.size < 500) break
                page++
            }
            acc
        }
        return all.filter { it.libraryId == libKomgaId }
    }

    /** Maps a raw Komga book to the chapter model the UI renders. */
    fun bookToChapter(book: KomgaBookDto): ChapterDto = toChapterDto(book)

    /** Reader route ids for a raw Komga book. */
    fun routeForBook(book: KomgaBookDto): KomgaBookRoute {
        return KomgaBookRoute(
            libraryId = mapper.libraryId(book.libraryId),
            seriesId = mapper.seriesId(book.seriesId),
            volumeId = mapper.volumeIdForSeries(book.seriesId),
            chapterId = mapper.bookId(book.id)
        )
    }

    /** Raw on-deck books (continue reading) without series mapping. */
    suspend fun onDeckBooks(page: Int = 0, size: Int = 24): List<KomgaBookDto> {
        return onDeckBooksPage(page, size).content
    }

    /** Raw latest books (newly added/updated) without series mapping. */
    suspend fun latestBooks(page: Int = 0, size: Int = 24): List<KomgaBookDto> {
        return latestBooksPage(page, size).content
    }

    /** Paged on-deck books with server totals. */
    suspend fun onDeckBooksPage(page: Int = 0, size: Int = 100): KomgaPageDto<KomgaBookDto> {
        val inProgress = runCatching {
            komga.books(
                readStatus = listOf("IN_PROGRESS"),
                sort = "readProgress.readDate,desc",
                page = page,
                size = size
            )
        }.getOrDefault(KomgaPageDto())

        val onDeck = runCatching {
            komga.onDeck(page = page, size = size)
        }.getOrDefault(KomgaPageDto())

        val combined = (inProgress.content + onDeck.content).distinctBy { it.id }
        val total = (inProgress.totalElements + onDeck.totalElements).coerceAtLeast(combined.size.toLong())
        return KomgaPageDto(
            content = combined,
            empty = combined.isEmpty(),
            first = page == 0,
            last = combined.size < size,
            number = page,
            size = size,
            totalElements = total,
            totalPages = if (size > 0) ((total + size - 1) / size).toInt() else 1
        )
    }

    /** Paged latest books with server totals. */
    suspend fun latestBooksPage(page: Int = 0, size: Int = 100): KomgaPageDto<KomgaBookDto> {
        return runCatching {
            komga.latestBooks(page = page, size = size)
        }.getOrDefault(KomgaPageDto())
    }

    /** Reading history books (in-progress and completed books sorted by readDate desc). */
    suspend fun readHistoryBooks(page: Int = 0, size: Int = 24): List<KomgaBookDto> {
        return readHistoryBooksPage(page, size).content
    }

    /** Paged reading history books with fallback to on-deck books. */
    suspend fun readHistoryBooksPage(page: Int = 0, size: Int = 100): KomgaPageDto<KomgaBookDto> {
        return runCatching {
            komga.books(
                readStatus = listOf("IN_PROGRESS", "READ"),
                sort = "readProgress.readDate,desc",
                page = page,
                size = size
            )
        }.getOrElse {
            runCatching {
                komga.onDeck(page = page, size = size)
            }.getOrDefault(KomgaPageDto())
        }
    }

    override suspend fun addSeriesToWantToRead(dto: UpdateWantToReadDto) {
    }

    override suspend fun removeSeriesFromWantToRead(dto: UpdateWantToReadDto) {
    }

    override suspend fun wantToRead(
        body: SeriesFilterV2Dto,
        pageNumber: Int?,
        pageSize: Int?
    ): List<SeriesDto> {
        return emptyList()
    }

    override suspend fun readingLists(
        pageNumber: Int?,
        pageSize: Int?,
        includePromoted: Boolean,
        sortByLastModified: Boolean
    ): List<ReadingListDto> {
        return komga.readLists().content.map { rl ->
            ReadingListDto(
                id = mapper.readListId(rl.id),
                title = rl.name,
                itemCount = rl.bookIds.size
            )
        }
    }

    override suspend fun createReadingList(dto: CreateReadingListDto): ReadingListDto {
        throw KomgaUnsupportedException("Creating read lists is not supported for Komga yet")
    }

    override suspend fun readingListItems(readingListId: Int): List<ReadingListItemDto> {
        val komgaId = mapper.komgaReadListId(readingListId)
            ?: throw IllegalArgumentException("Unknown Komga read list id $readingListId")
        return komga.readListBooks(komgaId, unpaged = true).content.map { b ->
            ReadingListItemDto(
                id = null,
                seriesId = mapper.seriesId(b.seriesId),
                seriesName = b.seriesTitle,
                libraryId = mapper.libraryId(b.libraryId)
            )
        }
    }

    override suspend fun addSeriesToReadingList(dto: UpdateReadingListBySeriesDto) {
        val readListKomgaId = mapper.komgaReadListId(dto.readingListId)
            ?: throw IllegalArgumentException("Unknown Komga read list id ${dto.readingListId}")
        val seriesKomgaId = mapper.komgaSeriesId(dto.seriesId)
            ?: throw IllegalArgumentException("Unknown Komga series id ${dto.seriesId}")
        val current = komga.readListBooks(readListKomgaId, unpaged = true).content.map { it.id }
        val additions = seriesBooks(seriesKomgaId).map { it.id }
        val union = (current + additions).distinct()
        komga.updateReadList(readListKomgaId, KomgaReadListUpdateDto(bookIds = union))
    }

    override suspend fun scanSeries(dto: RefreshSeriesDto) {
        val komgaId = mapper.komgaSeriesId(dto.seriesId) ?: return
        runCatching { komga.analyzeSeries(komgaId) }
    }

    override suspend fun analyzeSeries(dto: RefreshSeriesDto) {
        val komgaId = mapper.komgaSeriesId(dto.seriesId) ?: return
        komga.analyzeSeries(komgaId)
    }

    override suspend fun refreshSeriesMetadata(dto: RefreshSeriesDto) {
        val komgaId = mapper.komgaSeriesId(dto.seriesId) ?: return
        komga.refreshSeriesMetadata(komgaId)
    }

    override suspend fun updateRating(dto: UpdateSeriesRatingDto) {
    }

    override suspend fun seriesRating(seriesId: Int): SeriesRatingDto {
        return SeriesRatingDto(seriesId = seriesId, userRating = 0f, rating = 0f)
    }

    override suspend fun uploadSeriesCover(
        file: MultipartBody.Part,
        seriesId: Int
    ): Response<ResponseBody> {
        return Response.error(501, "".toResponseBody(null))
    }

    override suspend fun resetSeriesCover(seriesId: Int): Response<ResponseBody> {
        return Response.error(501, "".toResponseBody(null))
    }
}
