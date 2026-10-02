package com.bunko.reader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- Komga library ---

@Serializable
data class KomgaLibraryDto(
    val id: String = "",
    val name: String = "",
    val unavailable: Boolean = false
)

// --- Komga series ---

@Serializable
data class KomgaSeriesDto(
    val id: String = "",
    val libraryId: String = "",
    val name: String = "",
    val url: String = "",
    val created: String? = null,
    val lastModified: String? = null,
    val fileLastModified: String? = null,
    val booksCount: Int = 0,
    val booksReadCount: Int = 0,
    val booksUnreadCount: Int = 0,
    val booksInProgressCount: Int = 0,
    val oneshot: Boolean = false,
    val metadata: KomgaSeriesMetadataDto? = null,
    val booksMetadata: KomgaSeriesBooksMetadataDto? = null,
    val deleted: Boolean = false
)

@Serializable
data class KomgaSeriesBooksMetadataDto(
    val created: String? = null,
    val lastModified: String? = null,
    val fileLastModified: String? = null
)

@Serializable
data class KomgaSeriesMetadataDto(
    val title: String? = null,
    val titleSort: String? = null,
    val status: String? = null,
    val summary: String? = null,
    val publisher: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val alternateTitles: List<KomgaAlternateTitleDto> = emptyList(),
    val ageRating: Int? = null,
    val language: String? = null,
    val links: List<KomgaLinkDto> = emptyList(),
    val totalBookCount: Int? = null,
    val sharingLabels: List<String> = emptyList(),
    val readingDirection: String? = null
)

@Serializable
data class KomgaAlternateTitleDto(
    val label: String? = null,
    val title: String = ""
)

@Serializable
data class KomgaLinkDto(
    val label: String? = null,
    val url: String = ""
)

// --- Komga books (Kavita chapters/issues map to Komga books) ---

@Serializable
data class KomgaBookDto(
    val id: String = "",
    val seriesId: String = "",
    val seriesTitle: String = "",
    val libraryId: String = "",
    val name: String = "",
    val url: String = "",
    val number: Int = 0,
    val created: String? = null,
    val lastModified: String? = null,
    val fileLastModified: String? = null,
    val sizeBytes: Long = 0L,
    val size: String? = null,
    val media: KomgaMediaDto? = null,
    val metadata: KomgaBookMetadataDto? = null,
    val readProgress: KomgaReadProgressDto? = null,
    val oneshot: Boolean = false,
    val deleted: Boolean = false
)

@Serializable
data class KomgaMediaDto(
    val status: String? = null,
    val mediaType: String? = null,
    val pagesCount: Int = 0,
    val mediaProfile: String? = null,
    val epubDivinaCompatible: Boolean? = null,
    val epubIsKepub: Boolean? = null,
    val comment: String? = null
)

@Serializable
data class KomgaBookMetadataDto(
    val title: String? = null,
    val number: String? = null,
    val numberSort: Float? = null,
    val releaseDate: String? = null,
    val summary: String? = null,
    val authors: List<KomgaAuthorDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val isbn: String? = null,
    val links: List<KomgaLinkDto> = emptyList()
)

@Serializable
data class KomgaAuthorDto(
    val name: String = "",
    val role: String = ""
)

@Serializable
data class KomgaReadProgressDto(
    val page: Int = 0,
    val completed: Boolean = false,
    val readDate: String? = null,
    val created: String? = null,
    val lastModified: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null
)

@Serializable
data class KomgaReadProgressUpdateDto(
    val page: Int? = null,
    val completed: Boolean? = null
)

// --- Komga search ---

@Serializable
data class KomgaSeriesSearchDto(
    val condition: JsonElement? = null,
    val fullTextSearch: String? = null
)

@Serializable
data class KomgaBookSearchDto(
    val condition: JsonElement? = null,
    val fullTextSearch: String? = null
)

@Serializable
data class KomgaPageDto<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0L,
    val totalPages: Int = 0,
    val size: Int = 0,
    val number: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
    val empty: Boolean = true
)

// --- Komga collections / read lists (map to Kavita collections / reading lists) ---

@Serializable
data class KomgaCollectionDto(
    val id: String = "",
    val name: String = "",
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null
)

@Serializable
data class KomgaReadListUpdateDto(
    val bookIds: List<String>? = null,
    val name: String? = null,
    val ordered: Boolean? = null,
    val summary: String? = null
)

// --- Komga EPUB manifest (Readium WebPub shape) ---

@Serializable
data class KomgaEpubManifestDto(
    val readingOrder: List<KomgaEpubLinkDto> = emptyList(),
    val resources: List<KomgaEpubLinkDto> = emptyList(),
    val toc: List<KomgaEpubTocEntryDto> = emptyList()
)

@Serializable
data class KomgaEpubLinkDto(
    val href: String = "",
    val type: String? = null
)

@Serializable
data class KomgaEpubTocEntryDto(
    val title: String? = null,
    val href: String? = null,
    val children: List<KomgaEpubTocEntryDto> = emptyList()
)

// --- Komga book pages (dimensions for webtoon detection/layout) ---

@Serializable
data class KomgaBookPageDto(
    val fileName: String = "",
    val mediaType: String? = null,
    val number: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val size: String? = null,
    val sizeBytes: Long? = null
)

// --- Komga user ---
@Serializable
data class KomgaReadListDto(
    val id: String = "",
    val name: String = "",
    val summary: String? = null,
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val bookIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null
)

// --- Komga user ---

@Serializable
data class KomgaUserDto(
    val id: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val sharedAllLibraries: Boolean = true,
    val sharedLibrariesIds: List<String> = emptyList()
)
