package com.medipote.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.medipote.app.notifications.ReminderScheduler
import com.medipote.app.repository
import java.util.concurrent.TimeUnit

/** Backups on the device side: files picked by the user, the weekly automatic copy, restoring. */
object BackupManager {
    private const val PREFS = "backup"
    private const val KEY_PASSWORD = "auto_password"
    private const val WORK = "auto_backup"
    private const val WORK_NOW = "auto_backup_now"
    /** Automatic backups kept in the folder. */
    private const val KEEP = 4

    fun export(context: Context, uri: Uri, password: CharArray?) {
        val repo = context.repository
        val bytes = Backup.encode(repo.state.value, DocumentStore.dir(context), password, repo.now())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Impossible d'écrire le fichier")
        repo.update { it.copy(settings = it.settings.copy(backup = it.settings.backup.copy(lastManual = repo.now()))) }
    }

    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw Backup.NotABackup()

    /** Replaces all data and documents; this device's own backup settings are kept. */
    fun restore(context: Context, content: Backup.Content) {
        val repo = context.repository
        val current = repo.state.value.settings.backup
        Backup.restoreDocuments(DocumentStore.dir(context), content.documents)
        val state = content.state.copy(settings = content.state.settings.copy(backup = current))
        repo.replace(state)
        ReminderScheduler.reschedule(context, state, repo.now())
    }

    // ── Automatic backup ──

    fun setPassword(context: Context, password: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (password.isNullOrEmpty()) remove(KEY_PASSWORD) else putString(KEY_PASSWORD, password)
        }
    }

    private fun password(context: Context): CharArray? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PASSWORD, null)?.toCharArray()

    /** Remembers the folder picked by the user (permission kept across restarts). */
    fun useFolder(context: Context, tree: Uri): String {
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        return folderName(context, tree) ?: "Dossier choisi"
    }

    private fun folderName(context: Context, tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (enabled) {
            work.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
            )
        } else {
            work.cancelUniqueWork(WORK)
        }
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build())
    }

    /** Writes a new automatic backup in the chosen folder and removes the oldest ones. */
    fun runAuto(context: Context) {
        val repo = context.repository
        val settings = repo.state.value.settings.backup
        val tree = settings.folderUri?.let(Uri::parse) ?: return
        val result = runCatching {
            val resolver = context.contentResolver
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            val now = repo.now()
            val bytes = Backup.encode(repo.state.value, DocumentStore.dir(context), if (settings.encrypted) password(context) else null, now)
            val file = DocumentsContract.createDocument(resolver, parent, Backup.MIME, Backup.fileName(now, auto = true))
                ?: error("Impossible de créer le fichier")
            resolver.openOutputStream(file, "wt")?.use { it.write(bytes) } ?: error("Impossible d'écrire le fichier")
            // Keep the most recent automatic backups only.
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val autos = mutableListOf<Pair<String, String>>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    if (name.startsWith("MediPote-auto-") && name.endsWith(".medipote")) autos += it.getString(0) to name
                }
            }
            autos.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
                runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
            }
            now
        }
        repo.update { s ->
            val b = s.settings.backup
            s.copy(
                settings = s.settings.copy(
                    backup = result.fold(
                        { b.copy(lastAuto = it, lastError = null) },
                        { b.copy(lastError = "Dernière sauvegarde automatique impossible : vérifie que le dossier existe encore.") },
                    ),
                ),
            )
        }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val state = applicationContext.repository.state.value
        if (state.settings.backup.folderUri == null) return Result.success()
        BackupManager.runAuto(applicationContext)
        return Result.success()
    }
}
