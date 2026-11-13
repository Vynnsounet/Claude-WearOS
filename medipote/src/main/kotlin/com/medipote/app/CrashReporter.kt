package com.medipote.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the last crash on the device so the user can share it: without it, a crash on a phone we
 * don't have is impossible to diagnose. Nothing is ever sent automatically (the app has no Internet
 * permission); the report only leaves the phone if the user shares it.
 */
object CrashReporter {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val report = buildString {
            appendLine("MédiPote $version (${context.packageName})")
            appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Date : ${LocalDateTime.now()}")
            appendLine("Thread : ${thread.name}")
            appendLine()
            append(trace.take(20_000))
        }
        File(context.filesDir, FILE).writeText(report)
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** Offers to share the report of the previous crash, with a plain dialog that doesn't depend on Compose. */
    fun offerReport(activity: Activity) {
        val report = pending(activity) ?: return
        AlertDialog.Builder(activity)
            .setTitle("MédiPote s'est fermé de façon inattendue")
            .setMessage(
                "Désolé ! Tu peux partager le rapport technique de l'erreur (modèle du téléphone, version " +
                    "d'Android, détail de l'erreur) pour aider à corriger le problème.",
            )
            .setPositiveButton("Partager le rapport") { _, _ ->
                clear(activity)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Rapport d'erreur MédiPote")
                    .putExtra(Intent.EXTRA_TEXT, report)
                activity.startActivity(Intent.createChooser(send, "Partager le rapport"))
            }
            .setNegativeButton("Ignorer") { _, _ -> clear(activity) }
            .setCancelable(false)
            .show()
    }
}
