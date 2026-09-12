package com.bunko.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore("bunko_search_history")

class SearchHistoryStore(private val context: Context) {
    private val keyRecentQueries = stringPreferencesKey("recent_queries")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    val recentQueries: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        val encoded = prefs[keyRecentQueries].orEmpty()
        if (encoded.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(listSerializer, encoded) }.getOrDefault(emptyList())
        }
    }

    suspend fun record(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[keyRecentQueries]
                ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val next = listOf(normalized) + current.filterNot { it.equals(normalized, ignoreCase = true) }
            prefs[keyRecentQueries] = json.encodeToString(listSerializer, next.take(MaxRecentSearchQueries))
        }
    }
}

internal const val MaxRecentSearchQueries = 10
