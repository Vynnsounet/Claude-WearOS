package com.claude.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore(name = "settings")

/**
 * Stores the Anthropic API key in app-private storage on the watch.
 *
 * Debug builds fall back to `claude.apiKey` from local.properties until a key
 * is saved or removed in Settings.
 */
class ApiKeyStore(private val context: Context) {

    /** The current key, or an empty string when none is set. */
    val apiKey: Flow<String> = context.settings.data
        .map { prefs -> (prefs[KEY] ?: BuildConfig.DEV_API_KEY).trim() }
        .distinctUntilChanged()

    suspend fun save(key: String) {
        context.settings.edit { it[KEY] = key.trim() }
    }

    /** Remove the key. An explicit empty value also hides the debug fallback key. */
    suspend fun clear() {
        context.settings.edit { it[KEY] = "" }
    }

    companion object {
        private val KEY = stringPreferencesKey("anthropic_api_key")

        /** Show enough of a key to recognise it without exposing it. */
        fun mask(key: String): String =
            if (key.length <= 12) "••••" else key.take(7) + "…" + key.takeLast(4)
    }
}
