package com.bunko.reader

import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object MangaFormat {
    const val Epub = 3
    const val Pdf = 4
}

internal fun withPdfExtraction(url: String, extractPdf: Boolean): String =
    if (extractPdf) "$url&extractPdf=true" else url

internal fun pdfExtractionCallFactory(client: OkHttpClient): Call.Factory {
    // Kavita extracts the entire PDF before responding. Keep connection limits, but let
    // the reader's coroutine cancel the wait instead of timing out during extraction.
    val extractionClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    return Call.Factory { request ->
        val url = request.url
        val extractsPdf = request.method == "GET" &&
            url.queryParameter("extractPdf") == "true" &&
            (url.encodedPath.endsWith("/api/Reader/chapter-info") ||
                url.encodedPath.endsWith("/api/Reader/image"))
        (if (extractsPdf) extractionClient else client).newCall(request)
    }
}
