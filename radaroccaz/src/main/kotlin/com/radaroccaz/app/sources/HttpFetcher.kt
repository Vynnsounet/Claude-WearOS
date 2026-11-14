package com.radaroccaz.app.sources

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Plain HTTP GET with the headers a normal browser sends. It does not try to get around
 * anti-bot protections: a challenge page is reported as [SourceBlockedException] so the
 * app can offer to open the search on the site instead.
 */
class HttpFetcher(private val client: OkHttpClient = defaultClient()) {

    suspend fun get(url: String): String = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.6")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            when {
                response.code == 403 || response.code == 429 || isChallenge(body) ->
                    throw SourceBlockedException("Le site a demandé une vérification anti-robot (code ${response.code}).")
                !response.isSuccessful -> throw IOException("Réponse HTTP ${response.code}")
                else -> body
            }
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        private val challengeMarkers = listOf("captcha-delivery.com", "geo.captcha-delivery", "cf-challenge", "challenge-platform", "px-captcha")

        /** Captcha pages are small and point to a challenge provider. */
        fun isChallenge(body: String): Boolean =
            body.length < 60_000 && challengeMarkers.any { body.contains(it, ignoreCase = true) }
    }
}
