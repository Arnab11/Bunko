package li.mof.kamigura

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KavitaApi {

    @GET("api/Health")
    suspend fun health(): Unit

    @POST("api/Account/login")
    suspend fun login(@Body body: LoginDto): UserDto

    @GET("api/Account")
    suspend fun currentUser(): UserDto

    @GET("api/Library/libraries")
    suspend fun userLibraries(): List<LibraryDto>

    @POST("api/Library/scan")
    suspend fun scanLibrary(
        @Query("libraryId") libraryId: Int,
        @Query("force") force: Boolean = false
    )

    @POST("api/Series/all-v2")
    suspend fun allSeriesV2(
        @Body body: SeriesFilterV2Dto,
        @Query("pageNumber") pageNumber: Int? = 0,
        @Query("pageSize") pageSize: Int? = 200
    ): List<SeriesDto>

    @POST("api/Series/all-v2")
    suspend fun allSeriesV2Response(
        @Body body: SeriesFilterV2Dto,
        @Query("pageNumber") pageNumber: Int? = 0,
        @Query("pageSize") pageSize: Int? = 1
    ): Response<List<SeriesDto>>

    @GET("api/Series/{seriesId}")
    suspend fun series(@Path("seriesId") id: Int): SeriesDto

    @POST("api/Series/series-by-ids")
    suspend fun seriesByIds(@Body body: SeriesByIdsDto): List<SeriesDto>

    @GET("api/Series/metadata")
    suspend fun seriesMetadata(@Query("seriesId") seriesId: Int): SeriesMetadataDto

    @POST("api/Series/on-deck")
    suspend fun onDeck(
        @Query("PageNumber") pageNumber: Int? = 0,
        @Query("PageSize") pageSize: Int? = 12,
        @Query("libraryId") libraryId: Int? = 0
    ): List<SeriesDto>

    @POST("api/Series/recently-added-v2")
    suspend fun recentlyAdded(
        @Body body: SeriesFilterV2Dto = SeriesFilterV2Dto(),
        @Query("PageNumber") pageNumber: Int? = 0,
        @Query("PageSize") pageSize: Int? = 16
    ): List<SeriesDto>

    @POST("api/Series/recently-updated-series")
    suspend fun recentlyUpdatedSeries(
        @Query("PageNumber") pageNumber: Int? = 0,
        @Query("PageSize") pageSize: Int? = 16
    ): List<GroupedSeriesDto>

    @GET("api/Series/volumes")
    suspend fun volumes(@Query("seriesId") seriesId: Int): List<VolumeDto>

    @GET("api/Series/chapter")
    suspend fun seriesChapter(@Query("chapterId") chapterId: Int): ChapterDto

    @GET("api/Search/search")
    suspend fun search(
        @Query("queryString") queryString: String,
        @Query("includeChapterAndFiles") includeChapterAndFiles: Boolean = true
    ): SearchResultGroupDto

    @GET("api/Search/series-for-chapter")
    suspend fun seriesForChapter(@Query("chapterId") chapterId: Int): SeriesDto

    @GET("api/Collection")
    suspend fun collections(): List<CollectionDto>

    @GET("api/Download/chapter-size")
    suspend fun chapterSize(@Query("chapterId") chapterId: Int): Long

    @GET("api/reading-profile/{libraryId}/{seriesId}")
    suspend fun readingProfile(
        @Path("libraryId") libraryId: Int,
        @Path("seriesId") seriesId: Int,
        @Query("skipImplicit") skipImplicit: Boolean = false
    ): UserReadingProfileDto

    @GET("api/Reader/chapter-info")
    suspend fun chapterInfo(
        @Query("chapterId") chapterId: Int,
        @Query("includeDimensions") includeDimensions: Boolean = false,
        @Query("extractPdf") extractPdf: Boolean = false
    ): ChapterInfoDto

    @GET("api/Book/{chapterId}/book-page")
    suspend fun bookPage(
        @Path("chapterId") chapterId: Int,
        @Query("page") page: Int
    ): ResponseBody

    @GET("api/Book/{chapterId}/chapters")
    suspend fun bookChapters(
        @Path("chapterId") chapterId: Int
    ): List<BookChapterItemDto>

    @GET("api/Book/{chapterId}/book-info")
    suspend fun bookInfo(
        @Path("chapterId") chapterId: Int
    ): BookInfoDto

    @GET("api/Reader/get-progress")
    suspend fun getProgress(@Query("chapterId") chapterId: Int): ProgressDto

    @GET("api/Reader/continue-point")
    suspend fun continuePoint(@Query("seriesId") seriesId: Int): ChapterDto

    @POST("api/Reader/all-bookmarks")
    suspend fun allBookmarks(@Body body: SeriesFilterV2Dto = SeriesFilterV2Dto()): List<BookmarkDto>

    @POST("api/Reader/progress")
    suspend fun saveProgress(@Body dto: ProgressDto)

    @POST("api/Reader/mark-chapter-read")
    suspend fun markChapterRead(@Body dto: MarkChapterReadDto)

    @POST("api/Reader/mark-multiple-unread")
    suspend fun markChaptersUnread(@Body dto: MarkVolumesReadDto)

    @POST("api/want-to-read/add-series")
    suspend fun addSeriesToWantToRead(@Body dto: UpdateWantToReadDto)

    @POST("api/want-to-read/remove-series")
    suspend fun removeSeriesFromWantToRead(@Body dto: UpdateWantToReadDto)

    @POST("api/want-to-read/v2")
    suspend fun wantToRead(
        @Body body: SeriesFilterV2Dto = SeriesFilterV2Dto(),
        @Query("PageNumber") pageNumber: Int? = 0,
        @Query("PageSize") pageSize: Int? = 200
    ): List<SeriesDto>

    @POST("api/ReadingList/lists")
    suspend fun readingLists(
        @Query("PageNumber") pageNumber: Int? = 0,
        @Query("PageSize") pageSize: Int? = 200,
        @Query("includePromoted") includePromoted: Boolean = false,
        @Query("sortByLastModified") sortByLastModified: Boolean = false
    ): List<ReadingListDto>

    @POST("api/ReadingList/create")
    suspend fun createReadingList(@Body dto: CreateReadingListDto): ReadingListDto

    @GET("api/ReadingList/items")
    suspend fun readingListItems(@Query("readingListId") readingListId: Int): List<ReadingListItemDto>

    @POST("api/ReadingList/update-by-series")
    suspend fun addSeriesToReadingList(@Body dto: UpdateReadingListBySeriesDto)

    @POST("api/Series/scan")
    suspend fun scanSeries(@Body dto: RefreshSeriesDto)

    @POST("api/Series/analyze")
    suspend fun analyzeSeries(@Body dto: RefreshSeriesDto)

    @POST("api/Series/refresh-metadata")
    suspend fun refreshSeriesMetadata(@Body dto: RefreshSeriesDto)
}
