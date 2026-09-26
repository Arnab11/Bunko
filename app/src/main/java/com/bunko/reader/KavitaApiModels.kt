package com.bunko.reader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginDto(
    val username: String,
    val password: String,
    val apiKey: String? = null
)

@Serializable
data class UserDto(
    val username: String? = null,
    val roles: List<String>? = null,
    @SerialName("token") val token: String? = null
)

@Serializable
data class UpdateWantToReadDto(
    val seriesIds: List<Int>
)

@Serializable
data class ReadingListDto(
    val id: Int,
    val title: String? = null,
    val itemCount: Int? = null
)

@Serializable
data class CreateReadingListDto(
    val title: String
)

@Serializable
data class UpdateReadingListBySeriesDto(
    val seriesId: Int,
    val readingListId: Int
)

@Serializable
data class RefreshSeriesDto(
    val libraryId: Int,
    val seriesId: Int,
    val forceUpdate: Boolean = true,
    val forceColorscape: Boolean = false
)

@Serializable
data class LibraryDto(
    val id: Int,
    val name: String,
    val type: Int? = null,
    val coverImage: String? = null,
    val folders: List<String> = emptyList()
)

@Serializable
data class SearchResultGroupDto(
    val series: List<SearchResultDto> = emptyList(),
    val collections: List<CollectionDto> = emptyList(),
    val readingLists: List<ReadingListDto> = emptyList(),
    val persons: List<PersonDto> = emptyList(),
    val genres: List<GenreTagDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class SearchResultDto(
    val seriesId: Int,
    val name: String,
    val originalName: String? = null,
    val sortName: String? = null,
    val localizedName: String? = null,
    val libraryName: String? = null,
    val libraryId: Int? = null,
    val releaseYear: Int? = null,
    val volumeCount: Int? = null,
    val chapterCount: Int? = null
)

@Serializable
data class CollectionDto(
    val id: Int,
    val title: String,
    val itemCount: Int? = null
)

@Serializable
data class SeriesByIdsDto(
    val seriesIds: List<Int>
)

@Serializable
data class ReadingListItemDto(
    val id: Int? = null,
    val seriesId: Int,
    val seriesName: String? = null,
    val libraryId: Int? = null
)

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val originalName: String? = null,
    val localizedName: String? = null,
    val libraryId: Int? = null,
    val libraryName: String? = null,
    val pages: Int? = null,
    val pagesRead: Int? = null,
    val totalReads: Int? = null,
    val minHoursToRead: Int? = null,
    val maxHoursToRead: Int? = null,
    val avgHoursToRead: Float? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val format: Int? = null,
    val volumes: List<VolumeDto> = emptyList()
)

@Serializable
data class BookInfoDto(
    val pages: Int? = null,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val libraryId: Int? = null,
    val bookTitle: String? = null,
    val chapterTitle: String? = null,
    val seriesName: String? = null
)

@Serializable
data class BookChapterItemDto(
    val title: String? = null,
    val part: String? = null,
    val page: Int? = null,
    val children: List<BookChapterItemDto> = emptyList()
)

@Serializable
data class GroupedSeriesDto(
    val seriesId: Int,
    val seriesName: String? = null,
    val libraryId: Int? = null,
    val chapterId: Int? = null,
    val volumeId: Int? = null,
    val count: Int? = null
)

@Serializable
data class SeriesFilterV2Dto(
    val statements: List<SeriesFilterStatementDto> = emptyList(),
    val combination: Int = 1,
    val limitTo: Int = 0
)

@Serializable
data class SeriesFilterStatementDto(
    val comparison: Int,
    val field: Int,
    val value: String
)

@Serializable
data class VolumeDto(
    val id: Int,
    val name: String? = null,
    val number: JsonElement? = null,
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class ChapterDto(
    val id: Int,
    val title: String? = null,
    val number: JsonElement? = null,
    val sortOrder: Float? = null,
    val pages: Int? = null,
    val pagesRead: Int? = null,
    val totalReads: Int? = null,
    val volumeId: Int? = null,
    val titleName: String? = null,
    val volumeTitle: String? = null,
    val summary: String? = null,
    val releaseDate: String? = null,
    val minHoursToRead: Int? = null,
    val maxHoursToRead: Int? = null,
    val avgHoursToRead: Float? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val format: Int? = null,
    val writers: List<PersonDto>? = null,
    val coverArtists: List<PersonDto>? = null,
    val pencillers: List<PersonDto>? = null,
    val inkers: List<PersonDto>? = null,
    val colorists: List<PersonDto>? = null,
    val letterers: List<PersonDto>? = null,
    val editors: List<PersonDto>? = null,
    val translators: List<PersonDto>? = null,
    val isSpecial: Boolean = false
)

@Serializable
data class SeriesMetadataDto(
    val summary: String? = null,
    val genres: List<GenreTagDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val writers: List<PersonDto> = emptyList(),
    val coverArtists: List<PersonDto> = emptyList(),
    val pencillers: List<PersonDto> = emptyList(),
    val inkers: List<PersonDto> = emptyList(),
    val colorists: List<PersonDto> = emptyList(),
    val letterers: List<PersonDto> = emptyList(),
    val editors: List<PersonDto> = emptyList(),
    val translators: List<PersonDto> = emptyList(),
    val publishers: List<PersonDto> = emptyList(),
    val imprints: List<PersonDto> = emptyList(),
    val publicationStatus: Int? = null,
    val releaseYear: Int? = null,
    val language: String? = null
)

@Serializable
data class PersonDto(
    val id: Int? = null,
    val name: String? = null
)

@Serializable
data class GenreTagDto(
    val id: Int? = null,
    val title: String? = null
)

@Serializable
data class TagDto(
    val id: Int? = null,
    val title: String? = null
)

@Serializable
data class ChapterInfoDto(
    val chapterId: Int? = null,
    val pages: Int? = null,
    val pageDimensions: List<FileDimensionDto> = emptyList()
)

@Serializable
data class FileDimensionDto(
    val width: Int? = null,
    val height: Int? = null,
    val pageNumber: Int? = null,
    val fileName: String? = null,
    val isWide: Boolean = false
)

@Serializable
data class UserReadingProfileDto(
    val readingDirection: Int? = null,
    // ReadingProfileKind: 0 = Default (no per-series direction), 1 = User, 2 = Implicit.
    val kind: Int? = null
)

@Serializable
data class ProgressDto(
    val libraryId: Int,
    val seriesId: Int,
    val volumeId: Int?,
    val chapterId: Int,
    val pageNum: Int,
    val bookScrollId: String? = null
)

@Serializable
data class MarkChapterReadDto(
    val seriesId: Int,
    val chapterId: Int,
    val generateReadingSession: Boolean
)

@Serializable
data class MarkVolumesReadDto(
    val seriesId: Int,
    val volumeIds: List<Int> = emptyList(),
    val chapterIds: List<Int> = emptyList(),
    val generateReadingSession: Boolean = false
)

@Serializable
data class BookmarkDto(
    val id: Int? = null,
    val page: Int = 0,
    val volumeId: Int = 0,
    val seriesId: Int = 0,
    val chapterId: Int = 0,
    val imageOffset: Int? = null,
    val xPath: String? = null,
    val series: SeriesDto? = null,
    val chapterTitle: String? = null
)

@Serializable
data class SeriesRatingDto(
    val seriesId: Int? = null,
    val userRating: Float = 0f,
    val rating: Float = 0f
)

@Serializable
data class UpdateSeriesRatingDto(
    val seriesId: Int,
    val userRating: Float,
    val userReview: String? = null
)
