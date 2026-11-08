package com.mysocial.mesh

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class for MySocial providing global uncaught exception safety,
 * crash telemetry logging to local disk, and application lifecycle initialization.
 */
class MySocialApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashSafetyNet()
    }

    private fun installCrashSafetyNet() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrashDump(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash dump to disk", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrashDump(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTraceString = sw.toString()

        val dumpContent = buildString {
            appendLine("=========================================")
            appendLine("MYSOCIAL FATAL CRASH REPORT")
            appendLine("Timestamp: $timestamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "No message"}")
            appendLine("-----------------------------------------")
            appendLine("Stack Trace:")
            appendLine(stackTraceString)
            appendLine("=========================================")
        }

        val crashFile = File(filesDir, CRASH_DUMP_FILENAME)
        crashFile.writeText(dumpContent)
        Log.e(TAG, "Fatal crash captured and persisted to ${crashFile.absolutePath}")
    }

    companion object {
        private const val TAG = "MySocialApp"
        const val CRASH_DUMP_FILENAME = "crash_dump.txt"

        fun getLatestCrashDump(context: Context): String? {
            val file = File(context.filesDir, CRASH_DUMP_FILENAME)
            return if (file.exists() && file.length() > 0) {
                try {
                    file.readText()
                } catch (_: Exception) {
                    null
                }
            } else null
        }

        fun clearCrashDump(context: Context) {
            try {
                val file = File(context.filesDir, CRASH_DUMP_FILENAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }
}

