package com.vpegs.ms.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for sensitive config blobs (Android Keystore-backed).
 * Falls back to plain prefs if crypto init fails.
 */
object SecureStore {
    private const val NAME = "vpegs_secure"

    private fun prefs(ctx: Context): SharedPreferences {
        return try {
            val master = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                NAME,
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            ctx.getSharedPreferences(NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    fun putConfig(ctx: Context, id: String, json: String) {
        prefs(ctx).edit().putString("cfg_$id", json).apply()
    }

    fun getConfig(ctx: Context, id: String): String? =
        prefs(ctx).getString("cfg_$id", null)

    fun removeConfig(ctx: Context, id: String) {
        prefs(ctx).edit().remove("cfg_$id").apply()
    }

    fun putSecret(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString("s_$key", value).apply()
    }

    fun getSecret(ctx: Context, key: String): String? =
        prefs(ctx).getString("s_$key", null)
}
