package com.bunko.reader

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface KomgaApi {

    @GET("api/v2/users/me")
    suspend fun currentUser(): KomgaUserDto

    @GET("api/v1/libraries")
    suspend fun libraries(): List<KomgaLibraryDto>

    @GET("api/v1/libraries/{libraryId}")
    suspend fun library(@Path("libraryId") libraryId: String): KomgaLibraryDto

    @POST("api/v1/series/list")
    suspend fun seriesList(
        @Body body: KomgaSeriesSearchDto = KomgaSeriesSearchDto(),
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 200,
        @Query("sort") sort: String? = null,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaSeriesDto>

    // Deprecated GET kept for very old Komga servers without POST /series/list.
    @GET("api/v1/series")
    suspend fun allSeriesLegacy(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 500,
        @Query("unpaged") unpaged: Boolean? = true
    ): KomgaPageDto<KomgaSeriesDto>

    @GET("api/v1/series/new")
    suspend fun newSeries(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 20,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaSeriesDto>

    @GET("api/v1/series/updated")
    suspend fun updatedSeries(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 20,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaSeriesDto>

    @GET("api/v1/series/{seriesId}")
    suspend fun series(@Path("seriesId") seriesId: String): KomgaSeriesDto

    @GET("api/v1/series/{seriesId}/books")
    suspend fun seriesBooks(
        @Path("seriesId") seriesId: String,
        @Query("unpaged") unpaged: Boolean? = true,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
        @Query("sort") sort: String? = null
    ): KomgaPageDto<KomgaBookDto>

    @POST("api/v1/series/{seriesId}/read-progress")
    suspend fun markSeriesRead(@Path("seriesId") seriesId: String)

    @DELETE("api/v1/series/{seriesId}/read-progress")
    suspend fun markSeriesUnread(@Path("seriesId") seriesId: String)

    @POST("api/v1/series/{seriesId}/analyze")
    suspend fun analyzeSeries(@Path("seriesId") seriesId: String)

    @POST("api/v1/series/{seriesId}/metadata/refresh")
    suspend fun refreshSeriesMetadata(@Path("seriesId") seriesId: String)

    @POST("api/v1/libraries/{libraryId}/scan")
    suspend fun scanLibrary(@Path("libraryId") libraryId: String)

    @POST("api/v1/books/list")
    suspend fun booksList(
        @Body body: KomgaBookSearchDto = KomgaBookSearchDto(),
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 200,
        @Query("sort") sort: String? = null,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaBookDto>

    @GET("api/v1/books/ondeck")
    suspend fun onDeck(
        @Query("library_id") libraryIds: List<String>? = null,
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 20,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaBookDto>

    @GET("api/v1/books/latest")
    suspend fun latestBooks(
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 20,
        @Query("unpaged") unpaged: Boolean? = null
    ): KomgaPageDto<KomgaBookDto>

    @GET("api/v1/books/{bookId}")
    suspend fun book(@Path("bookId") bookId: String): KomgaBookDto

    @GET("api/v1/books/{bookId}/next")
    suspend fun nextBook(@Path("bookId") bookId: String): KomgaBookDto

    @GET("api/v1/books/{bookId}/previous")
    suspend fun previousBook(@Path("bookId") bookId: String): KomgaBookDto

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body body: KomgaReadProgressUpdateDto
    )

    @DELETE("api/v1/books/{bookId}/read-progress")
    suspend fun deleteReadProgress(@Path("bookId") bookId: String)

    @GET("api/v1/collections")
    suspend fun collections(
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 200,
        @Query("unpaged") unpaged: Boolean? = true
    ): KomgaPageDto<KomgaCollectionDto>

    @GET("api/v1/collections/{id}/series")
    suspend fun collectionSeries(
        @Path("id") collectionId: String,
        @Query("unpaged") unpaged: Boolean? = true
    ): KomgaPageDto<KomgaSeriesDto>

    @GET("api/v1/readlists")
    suspend fun readLists(
        @Query("page") page: Int? = 0,
        @Query("size") size: Int? = 200,
        @Query("unpaged") unpaged: Boolean? = true
    ): KomgaPageDto<KomgaReadListDto>

    @GET("api/v1/readlists/{id}/books")
    suspend fun readListBooks(
        @Path("id") readListId: String,
        @Query("unpaged") unpaged: Boolean? = true
    ): KomgaPageDto<KomgaBookDto>

    @PATCH("api/v1/readlists/{id}")
    suspend fun updateReadList(
        @Path("id") readListId: String,
        @Body body: KomgaReadListUpdateDto
    )

    // Raw page/thumbnail streams are built as URLs by KomgaClient (auth via
    // query/header), but keep a typed probe for connectivity checks.
    @GET("api/v1/books/{bookId}/pages/{pageNumber}")
    suspend fun bookPageProbe(
        @Path("bookId") bookId: String,
        @Path("pageNumber") pageNumber: Int
    ): Response<ResponseBody>

    @GET("api/v1/books/{bookId}/pages")
    suspend fun bookPages(
        @Path("bookId") bookId: String
    ): List<KomgaBookPageDto>

    // EPUB text flow (mirrors Komga's own reader + Komelia): the WebPub
    // manifest for spine order + TOC, resources for spine HTML and images.
    @Headers("Accept: */*")
    @GET("api/v1/books/{bookId}/manifest/epub")
    suspend fun bookManifestEpub(
        @Path("bookId") bookId: String
    ): KomgaEpubManifestDto

    @Headers("Accept: */*")
    @GET("api/v1/books/{bookId}/manifest")
    suspend fun bookManifestFallback(
        @Path("bookId") bookId: String
    ): KomgaEpubManifestDto

    @Headers("Accept: */*")
    @GET("api/v1/books/{bookId}/resource/{resource}")
    suspend fun bookResourceRaw(
        @Path("bookId") bookId: String,
        @Path(value = "resource", encoded = true) resource: String
    ): ResponseBody
}
