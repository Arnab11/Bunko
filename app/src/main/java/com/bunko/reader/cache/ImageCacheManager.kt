package com.bunko.reader.cache

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ImageCacheUsage(
    val coverBytes: Long,
    val readerBytes: Long,
    val coverLimitBytes: Long,
    val readerLimitBytes: Long
)

internal object ImageCacheManager {
    suspend fun usage(context: Context): ImageCacheUsage = withContext(Dispatchers.IO) {
        val budget = imageCacheBudget(context)
        ImageCacheUsage(
            coverBytes = cacheDirectorySize(context.cacheDir.resolve(CoverImageCacheDirectory)),
            readerBytes = cacheDirectorySize(context.cacheDir.resolve(ReaderPageCacheDirectory)),
            coverLimitBytes = budget.coverDiskBytes,
            readerLimitBytes = budget.readerDiskBytes
        )
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearCoverCache(context: Context) = withContext(Dispatchers.IO) {
        context.imageLoader.memoryCache?.clear()
        context.imageLoader.diskCache?.clear()
    }

    suspend fun clearReaderCache(context: Context) = withContext(Dispatchers.IO) {
        // Reader owns this loader and disposes it before navigation can reach Settings.
        context.cacheDir.resolve(ReaderPageCacheDirectory).deleteRecursively()
    }

    suspend fun clearAll(context: Context) {
        clearCoverCache(context)
        clearReaderCache(context)
    }
}
