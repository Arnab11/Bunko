package com.bunko.reader.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import com.bunko.reader.BuildConfig
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal object CrashReportStore {
    private const val TAG = "CrashReportStore"
    const val EXTRA_REPORT_ID = "com.bunko.reader.crash.EXTRA_REPORT_ID"
    const val EXTRA_THROWABLE_CLASS = "com.bunko.reader.crash.EXTRA_THROWABLE_CLASS"
    const val EXTRA_ERROR_MESSAGE = "com.bunko.reader.crash.EXTRA_ERROR_MESSAGE"
    const val EXTRA_THREAD_NAME = "com.bunko.reader.crash.EXTRA_THREAD_NAME"

    private val handlingCrash = AtomicBoolean(false)

    fun install(application: Application) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (handlingCrash.compareAndSet(false, true)) {
                try {
                    val reportId = capture(application, thread, throwable)
                    val intent = Intent(application, CrashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(EXTRA_REPORT_ID, reportId)
                        putExtra(EXTRA_THROWABLE_CLASS, throwable.javaClass.name)
                        putExtra(EXTRA_ERROR_MESSAGE, throwable.message ?: throwable.localizedMessage ?: "Unknown error")
                        putExtra(EXTRA_THREAD_NAME, thread.name)
                    }
                    application.startActivity(intent)
                    Process.killProcess(Process.myPid())
                    System.exit(10)
                } catch (captureError: Throwable) {
                    Log.e(TAG, "Unable to save the full crash report", captureError)
                    originalHandler?.uncaughtException(thread, throwable)
                }
            } else {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun capture(
        context: Context,
        thread: Thread,
        throwable: Throwable
    ): String {
        val directory = reportDirectory(context)
        val metadataFile = AtomicFile(File(directory, "metadata.json"))
        metadataFile.delete()
        val reportId = UUID.randomUUID().toString()

        writeAtomically(File(directory, "exception.txt")) { writer ->
            writer.appendLine("==========================================")
            writer.appendLine("           BUNKO CRASH REPORT             ")
            writer.appendLine("==========================================")
            writer.appendLine("Report ID: $reportId")
            writer.appendLine("Captured: ${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).format(Date())}")
            writer.appendLine("Process ID: ${Process.myPid()}")
            writer.appendLine("Device uptime: ${SystemClock.elapsedRealtime()} ms since boot")
            writer.appendLine("Crash thread: ${thread.name} (id=${thread.id}, state=${thread.state})")
            writer.appendLine()
            writer.appendLine("=== Device & Environment ===")
            writer.appendLine(collectDeviceInfo())
            writer.appendLine()
            writer.appendLine("=== Full Exception Chain ===")
            writeThrowable(writer, throwable)
            writer.appendLine()
            writer.appendLine("=== All Thread Stacks at Crash ===")
            try {
                Thread.getAllStackTraces().entries.sortedBy { it.key.name }.forEach { (currentThread, stack) ->
                    writer.appendLine(
                        "${currentThread.name} (id=${currentThread.id}, state=${currentThread.state}, " +
                            "daemon=${currentThread.isDaemon}, priority=${currentThread.priority})"
                    )
                    stack.forEach { writer.appendLine("    at $it") }
                    writer.appendLine()
                }
            } catch (error: Exception) {
                writer.appendLine("Thread snapshot unavailable: $error")
            }
        }

        writeMetadata(
            directory,
            JSONObject()
                .put("reportId", reportId)
                .put("processId", Process.myPid())
                .put("throwableClass", throwable.javaClass.name)
                .put("errorMessage", throwable.message ?: "")
                .put("threadName", thread.name)
                .put("completed", false)
        )
        return reportId
    }

    private fun writeThrowable(
        writer: BufferedWriter,
        throwable: Throwable
    ) {
        val visited = IdentityHashMap<Throwable, Boolean>()
        val pending = ArrayDeque<Pair<String, Throwable>>()
        pending.addLast("Exception" to throwable)
        while (pending.isNotEmpty()) {
            val (relationship, current) = pending.removeLast()
            writer.appendLine("$relationship: ${current.javaClass.name}: ${current.message}")
            if (visited.put(current, true) != null) {
                writer.appendLine("    [Circular or previously printed exception reference]")
                continue
            }
            current.stackTrace.forEach { writer.appendLine("    at $it") }
            current.cause?.let { pending.addLast("Caused by" to it) }
            current.suppressed.reversed().forEach { pending.addLast("Suppressed by ${current.javaClass.name}" to it) }
        }
    }

    @Synchronized
    fun complete(
        context: Context,
        intent: Intent
    ): File {
        val directory = reportDirectory(context)
        val reportIdFromIntent = intent.getStringExtra(EXTRA_REPORT_ID)
        val metadata = runCatching {
            AtomicFile(File(directory, "metadata.json")).openRead().bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull()
        val reportId = reportIdFromIntent
            ?: runCatching { UUID.fromString(metadata?.optString("reportId")) }.getOrNull()?.toString()
            ?: UUID.randomUUID().toString()

        val report = File(directory, "bunko-crash-$reportId.txt")
        if (metadata != null && metadata.optString("reportId") == reportId && metadata.optBoolean("completed") && report.isFile) {
            return report
        }

        val hasFullException = File(directory, "exception.txt").isFile
        writeAtomically(report) { writer ->
            if (hasFullException) {
                AtomicFile(File(directory, "exception.txt")).openRead().bufferedReader().use { it.copyTo(writer) }
            } else {
                writer.appendLine("Full on-disk exception capture was unavailable. Basic intent details:")
                writer.appendLine("Exception class: ${intent.getStringExtra(EXTRA_THROWABLE_CLASS)}")
                writer.appendLine("Message: ${intent.getStringExtra(EXTRA_ERROR_MESSAGE)}")
                writer.appendLine("Thread: ${intent.getStringExtra(EXTRA_THREAD_NAME)}")
                writer.appendLine()
                writer.appendLine(collectDeviceInfo())
            }

            writer.appendLine()
            writer.appendLine("=== Available Logcat Buffers ===")
            val arguments = mutableListOf("logcat", "-b", "main", "-b", "system", "-b", "crash", "-d", "-v", "threadtime", "-t", "1000")
            try {
                val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
                try {
                    process.inputStream.bufferedReader().use { it.copyTo(writer) }
                    val exitCode = process.waitFor()
                    if (exitCode != 0) writer.appendLine("Logcat exited with status $exitCode")
                } finally {
                    process.destroy()
                }
            } catch (error: Exception) {
                writer.appendLine("Logcat unavailable: $error")
            }
        }

        writeMetadata(
            directory,
            (metadata ?: JSONObject()).put("reportId", reportId).put("completed", true)
        )

        directory.listFiles { file -> file.name.startsWith("bunko-crash-") && file.extension == "txt" }
            ?.sortedByDescending(File::lastModified)
            ?.drop(5)
            ?.forEach { it.delete() }

        return report
    }

    fun collectDeviceInfo(): String = buildString {
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Security patch: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Hardware: ${Build.HARDWARE}")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Build fingerprint: ${Build.FINGERPRINT}")
        appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")
        val runtime = Runtime.getRuntime()
        appendLine("Heap bytes: used=${runtime.totalMemory() - runtime.freeMemory()}, max=${runtime.maxMemory()}")
        appendLine("Native heap bytes: ${android.os.Debug.getNativeHeapAllocatedSize()}")
    }

    private fun reportDirectory(context: Context): File =
        File(context.noBackupFilesDir, "crash-reports").apply {
            check(isDirectory || mkdirs()) { "Unable to create crash report directory" }
        }

    private fun writeMetadata(
        directory: File,
        metadata: JSONObject
    ) = writeAtomically(File(directory, "metadata.json")) { it.write(metadata.toString()) }

    private fun writeAtomically(
        file: File,
        write: (BufferedWriter) -> Unit
    ) {
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            val writer = output.bufferedWriter(Charsets.UTF_8)
            write(writer)
            writer.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}
