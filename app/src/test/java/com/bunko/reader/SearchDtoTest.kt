package com.bunko.reader

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun searchResultGroup_decodesSupportedSections() {
        val decoded = json.decodeFromString<SearchResultGroupDto>(
            """
            {
              "libraries": [],
              "series": [
                {
                  "seriesId": 12,
                  "name": "Series A",
                  "originalName": "Series A",
                  "libraryId": 2,
                  "libraryName": "Manga"
                }
              ],
              "collections": [{ "id": 3, "title": "Favorites", "itemCount": 4 }],
              "readingLists": [{ "id": 4, "title": "Read next", "itemCount": 5 }],
              "persons": [{ "id": 5, "name": "Writer" }],
              "genres": [{ "id": 6, "title": "Mystery" }],
              "tags": [{ "id": 7, "title": "School" }],
              "chapters": [{ "id": 8, "titleName": "Chapter Hit", "pages": 10 }],
              "files": [],
              "bookmarks": [],
              "annotations": []
            }
            """.trimIndent()
        )

        assertEquals(12, decoded.series.single().seriesId)
        assertEquals("Favorites", decoded.collections.single().title)
        assertEquals("Read next", decoded.readingLists.single().title)
        assertEquals("Writer", decoded.persons.single().name)
        assertEquals("Mystery", decoded.genres.single().title)
        assertEquals("School", decoded.tags.single().title)
        assertEquals("Chapter Hit", decoded.chapters.single().titleName)
    }
}
