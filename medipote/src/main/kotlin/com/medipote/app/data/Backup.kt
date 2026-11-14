package com.medipote.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A backup file: everything the app knows (data and health documents) in one file.
 *
 * Layout: "MEDIPOTE-BACKUP", a version byte, an encryption flag, then a zip archive
 * ("data.json" and "documents/…"). With a password, the zip is encrypted with AES-256-GCM,
 * with a key derived from the password by PBKDF2 (salt and IV stored after the flag).
 */
object Backup {
    private val MAGIC = "MEDIPOTE-BACKUP".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 120_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    const val MIME = "application/octet-stream"

    class NotABackup : Exception("Ce fichier n'est pas une sauvegarde MédiPote.")
    class WrongPassword : Exception("Mot de passe incorrect.")
    class NeedsPassword : Exception("Cette sauvegarde est protégée par un mot de passe.")

    data class Content(val state: AppState, val documents: Map<String, ByteArray>, val createdAt: LocalDateTime?)

    fun fileName(now: LocalDateTime, auto: Boolean = false) =
        "MediPote-${if (auto) "auto" else "sauvegarde"}-${now.toLocalDate()}_${"%02dh%02d".format(now.hour, now.minute)}.medipote"

    fun encode(state: AppState, documentsDir: File?, password: CharArray?, now: LocalDateTime): ByteArray {
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write(AppRepository.JSON.encodeToString(AppState.serializer(), state).encodeToByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("created.txt"))
            z.write(now.toString().encodeToByteArray())
            z.closeEntry()
            // Only the documents still referenced by the data.
            val names = state.documents.map { it.fileName }.toSet()
            documentsDir?.listFiles()?.filter { it.isFile && it.name in names }?.forEach { f ->
                z.putNextEntry(ZipEntry("documents/${f.name}"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(VERSION.toInt())
        if (password == null || password.isEmpty()) {
            out.write(0)
            out.write(zip.toByteArray())
        } else {
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
            val iv = ByteArray(IV_SIZE).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            out.write(1)
            out.write(salt)
            out.write(iv)
            out.write(cipher.doFinal(zip.toByteArray()))
        }
        return out.toByteArray()
    }

    fun isBackup(bytes: ByteArray) = bytes.size > MAGIC.size + 2 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun isEncrypted(bytes: ByteArray): Boolean {
        if (!isBackup(bytes)) throw NotABackup()
        return bytes[MAGIC.size + 1].toInt() == 1
    }

    fun decode(bytes: ByteArray, password: CharArray?): Content {
        if (!isBackup(bytes)) throw NotABackup()
        if (bytes[MAGIC.size] > VERSION) throw NotABackup()
        var offset = MAGIC.size + 2
        val zip = if (bytes[MAGIC.size + 1].toInt() == 1) {
            if (password == null || password.isEmpty()) throw NeedsPassword()
            val salt = bytes.copyOfRange(offset, offset + SALT_SIZE).also { offset += SALT_SIZE }
            val iv = bytes.copyOfRange(offset, offset + IV_SIZE).also { offset += IV_SIZE }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            try {
                cipher.doFinal(bytes, offset, bytes.size - offset)
            } catch (_: AEADBadTagException) {
                throw WrongPassword()
            }
        } else {
            bytes.copyOfRange(offset, bytes.size)
        }
        var state: AppState? = null
        var created: LocalDateTime? = null
        val documents = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                val data = z.readBytes()
                when {
                    entry.name == "data.json" -> state = AppRepository.JSON.decodeFromString(AppState.serializer(), data.decodeToString())
                    entry.name == "created.txt" -> created = runCatching { LocalDateTime.parse(data.decodeToString()) }.getOrNull()
                    entry.name.startsWith("documents/") -> {
                        val name = entry.name.removePrefix("documents/")
                        // Plain file names only: nothing may be written outside the documents folder.
                        if (name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..") documents[name] = data
                    }
                }
            }
        }
        return Content(state ?: throw NotABackup(), documents, created)
    }

    /** Writes restored documents in place of the current ones. */
    fun restoreDocuments(dir: File, documents: Map<String, ByteArray>) {
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        documents.forEach { (name, data) -> File(dir, name).writeBytes(data) }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
