package com.bunko.reader

import android.util.Log

object BunkoLog {
    private const val Tag = "Bunko"

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(Tag, message)
        } else {
            Log.w(Tag, message, throwable)
        }
    }
}
