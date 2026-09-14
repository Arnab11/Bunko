package com.bunko.reader.offline

import kotlinx.serialization.Serializable

@Serializable
data class LocalFolder(
    val uriString: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis()
)
