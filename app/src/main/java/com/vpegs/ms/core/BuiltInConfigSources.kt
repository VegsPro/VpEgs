package com.vpegs.ms.core

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Built-in VpEgs Config — base URL from Firebase, file from host API.
 */
object BuiltInConfigSources {
    const val SUB_ID = "vpegs-builtin"
    const val SUB_NAME = "VpEgs Config ⭐"

    const val FIREBASE_BASE_URL =
        "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"

    /** Folder + file on Go Host */
    const val FOLDER = "VpEgsConfig"
    const val FILENAME = "config.json"
    const val FOLDER_KEY = "root"

    /** Fallback if Firebase is unreachable */
    const val FALLBACK_BASE = "https://vix.mstrblock.workers.dev"

    const val MAX_SERVERS = 150

    /**
     * Resolve live download URL:
     * 1) Firebase base_url.json
     * 2) FALLBACK_BASE
     * → {base}/download/VpEgsConfig/config.json?key=root
     */
    fun resolveDownloadUrl(client: OkHttpClient = defaultClient()): String {
        val base = fetchBaseUrl(client)?.trimEnd('/') ?: FALLBACK_BASE.trimEnd('/')
        return "$base/download/$FOLDER/$FILENAME?key=$FOLDER_KEY"
    }

    fun fetchBaseUrl(client: OkHttpClient = defaultClient()): String? {
        return try {
            val req = Request.Builder()
                .url(FIREBASE_BASE_URL)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val raw = resp.body?.string()?.trim().orEmpty()
                // Firebase may return "https://..." with quotes
                val url = raw.trim().trim('"').trim()
                if (url.startsWith("http")) url else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    /** Legacy constant for UI/migration — not used for fetch anymore. */
    val SUB_URL: String
        get() = "$FALLBACK_BASE/download/$FOLDER/$FILENAME?key=$FOLDER_KEY"
}
