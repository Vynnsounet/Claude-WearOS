package com.medipote.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the person's API key is kept: out of the app's data (so never in a backup), and encrypted. */
interface KeyVault {
    fun load(): String?
    fun save(key: String)
    fun clear()
}

/** The key is encrypted with a key of the phone's Android Keystore, which cannot be taken out of the phone. */
class AndroidKeyVault(context: Context) : KeyVault {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun secret(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun load(): String? = runCatching {
        val stored = prefs.getString(VALUE, null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secret(create = false) ?: return null, GCMParameterSpec(128, bytes.copyOfRange(0, IV)))
        String(cipher.doFinal(bytes, IV, bytes.size - IV), Charsets.UTF_8)
        // A key copied to another phone cannot be read there: it simply counts as missing.
    }.getOrNull()

    override fun save(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret(create = true))
        val encrypted = cipher.iv + cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8))
        prefs.edit().putString(VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "medipote_ai_key"
        const val FILE = "ai_key"
        const val VALUE = "k"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV = 12
    }
}

/** For tests. */
class MemoryKeyVault(private var key: String? = null) : KeyVault {
    override fun load() = key
    override fun save(key: String) { this.key = key.trim() }
    override fun clear() { key = null }
}
