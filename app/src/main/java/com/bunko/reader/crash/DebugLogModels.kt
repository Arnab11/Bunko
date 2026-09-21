package com.bunko.reader.crash

enum class DebugLogLevel(
    val code: String,
    val label: String
) {
    Verbose("V", "Verbose"),
    Debug("D", "Debug"),
    Info("I", "Info"),
    Warn("W", "Warn"),
    Error("E", "Error")
}

data class DebugLogEntry(
    val id: String,
    val timeMillis: Long,
    val timestamp: String,
    val level: DebugLogLevel,
    val tag: String,
    val message: String,
    val pid: Int? = null,
    val tid: Int? = null
)

data class DebugLogSnapshot(
    val entries: List<DebugLogEntry>,
    val source: String,
    val rawLineCount: Int
)
