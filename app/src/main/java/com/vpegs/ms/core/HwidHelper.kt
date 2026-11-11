package com.vpegs.ms.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Device HWID for subscription panels (Atlanta / similar) that bind configs to a device.
 * Format compatible with INCY-style UUID HWID so already-registered phones keep working.
 */
object HwidHelper {

    private const val PREF = "vpegs_hwid"
    private const val KEY = "hwid_v1"

    fun getOrCreate(ctx: Context): String {
        val sp = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val hwid = generate(ctx)
        sp.edit().putString(KEY, hwid).apply()
        return hwid
    }

    fun shortId(ctx: Context): String = getOrCreate(ctx).take(8)

    fun requestHeaders(ctx: Context): Map<String, String> {
        val hwid = getOrCreate(ctx)
        return mapOf(
            "x-hwid" to hwid,
            "x-device-os" to "Android",
            "x-ver-os" to Build.VERSION.RELEASE.orEmpty().ifBlank { "14" },
            "x-device-model" to listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android" },
            "x-app-version" to try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) { "1.0" }
        )
    }

    @SuppressLint("HardwareIds")
    private fun generate(ctx: Context): String {
        val androidId = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Exception) { "unknown" }
        val raw = listOf(
            androidId,
            Build.MANUFACTURER.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.BRAND.orEmpty(),
            Build.DEVICE.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.BOARD.orEmpty(),
            Build.HARDWARE.orEmpty()
        ).joinToString("|")
        val deviceId = sha256Hex(raw)
        val salted = sha256Hex("incy_hwid_$deviceId")
        return hashToUuid(salted).uppercase(Locale.US)
    }

    private fun sha256Hex(s: String): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return dig.joinToString("") { "%02x".format(it) }
    }

    /** First 32 hex chars of SHA-256 → UUID 8-4-4-4-12 */
    private fun hashToUuid(hex: String): String {
        val h = hex.padEnd(32, '0').take(32)
        return UUID.fromString(
            "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-" +
                "${h.substring(16, 20)}-${h.substring(20, 32)}"
        ).toString()
    }

    /** Body looks like Atlanta/HWID client-stub, not real nodes. */
    fun isStubSubscriptionBody(body: String): Boolean {
        val t = body.trim()
        if (t.isEmpty()) return true
        var text = t
        if (!t.contains("://") && t.length > 40 && !t.startsWith("[") && !t.startsWith("{")) {
            try {
                text = String(android.util.Base64.decode(t, android.util.Base64.DEFAULT))
            } catch (_: Exception) {
                try {
                    text = String(
                        android.util.Base64.decode(
                            t,
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        val low = text.lowercase()
        if ("не поддерживается" in low || "not support" in low || "предел количества устройств" in low) {
            return true
        }
        if ("0.0.0.0" in text || "127.0.0.1" in text) {
            // real host somewhere?
            val hasReal = Regex(
                """@((?!127\.0\.0\.1)(?!0\.0\.0\.0)(?!\[::1\])[a-zA-Z0-9.\-\[\]:]+)"""
            ).containsMatchIn(text)
            if (!hasReal) return true
        }
        // JSON array of only section headers / empty proxy outbounds
        if (t.startsWith("[")) {
            val remarks = Regex(""""remarks"\s*:\s*"([^"]*)"""").findAll(t).map { it.groupValues[1] }.toList()
            if (remarks.isNotEmpty() &&
                remarks.all {
                    it.contains("не поддерживается", true) ||
                        it.contains("ОСНОВНЫЕ", true) ||
                        it.contains("ОБХОД", true) ||
                        it.startsWith("━")
                }
            ) {
                // still might have real outbounds — check vnext addresses
                if ("0.0.0.0" in t && "vnext" in t) {
                    val realAddr = Regex(""""address"\s*:\s*"(?!0\.0\.0\.0)(?!127\.0\.0\.1)[^"]+"""").containsMatchIn(t)
                    if (!realAddr) return true
                }
            }
        }
        return false
    }
}
