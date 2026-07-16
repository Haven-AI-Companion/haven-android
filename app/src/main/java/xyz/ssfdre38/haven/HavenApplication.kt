package xyz.ssfdre38.haven

import android.app.Application
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HavenApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Build the crash report text
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val timestamp = sdf.format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))

                val report = buildString {
                    appendLine("=== Haven Crash Report ===")
                    appendLine("Time:    $timestamp")
                    appendLine("Thread:  ${thread.name}")
                    appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("App:     ${packageName}")
                    appendLine()
                    appendLine("--- Stack Trace ---")
                    appendLine(sw.toString())
                }

                // Save to Downloads folder (always accessible in file managers)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val havenCrashDir = File(downloadsDir, "HavenCrashLogs")
                havenCrashDir.mkdirs()

                val fileName = "haven_crash_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
                val crashFile = File(havenCrashDir, fileName)
                crashFile.writeText(report)

            } catch (e: Exception) {
                // Never let the crash logger itself crash the app silently
                e.printStackTrace()
            }

            // Always call the original handler so Android still shows the crash dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
