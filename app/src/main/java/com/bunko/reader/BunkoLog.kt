package com.bunko.reader

import android.util.Log

object BunkoLog {
    private const val Tag = "Bunko"

    fun d(message: String) {
        Log.d(Tag, message)
    }

    fun i(message: String) {
        Log.i(Tag, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(Tag, message)
        } else {
            Log.w(Tag, message, throwable)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(Tag, message)
        } else {
            Log.e(Tag, message, throwable)
        }
    }
}
