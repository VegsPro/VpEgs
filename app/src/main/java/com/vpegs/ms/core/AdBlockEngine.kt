package com.vpegs.ms.core

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Network-level ad blocking helpers (no MITM).
 * Sources: geosite categories (in Xray) + optional downloaded domain lists.
 */
object AdBlockEngine {

    private const val LIST_FILE = "adblock_domains.txt"
    private const val META_FILE = "adblock_meta.txt"

    /** Remote domain lists (one domain per line or AdGuard DNS syntax). */
    private val REMOTE_URLS = listOf(
        // AdGuard DNS filter (domain rules)
        "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
        // Fallback compact trackers
        "https://raw.githubusercontent.com/AdguardTeam/AdguardSDNSFilter/master/Filters/filter.txt"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Built-in high-value ad/tracker domains if download fails. */
    val BUILTIN = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "pagead2.googlesyndication.com", "adservice.google.com", "ads.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "ads.facebook.com", "an.facebook.com", "connect.facebook.net",
        "ads.twitter.com", "ads-twitter.com", "adnxs.com", "adsrvr.org",
        "adform.net", "criteo.com", "taboola.com", "outbrain.com",
        "applovin.com", "unityads.unity3d.com", "mopub.com", "vungle.com",
        "adcolony.com", "chartboost.com", "adjust.com", "appsflyer.com",
        "branch.io", "moatads.com", "scorecardresearch.com",
        "yandexadexchange.net", "an.yandex.ru", "ads.yandex.ru", "adfox.ru",
        "mc.yandex.ru", "ads.vk.com", "ad.mail.ru", "reklama.mail.ru",
        "target.my.com", "pubmatic.com", "openx.net", "rubiconproject.com"
    )

    /** geosite tags used when geosite.dat is present (very effective). */
    val GEOSITE_ADS = listOf(
        "geosite:category-ads-all",
        "geosite:category-ads",
        "geosite:google-ads",
        "geosite:category-ads-ir"
    )

    fun listFile(ctx: Context) = File(ctx.filesDir, LIST_FILE)
    fun metaFile(ctx: Context) = File(ctx.filesDir, META_FILE)

    fun loadDomains(ctx: Context): List<String> {
        val f = listFile(ctx)
        if (!f.exists() || f.length() < 16) return BUILTIN
        return try {
            f.readLines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '.' in it }
                .distinct()
        } catch (_: Exception) {
            BUILTIN
        }
    }

    fun lastUpdateInfo(ctx: Context): String {
        val m = metaFile(ctx)
        if (!m.exists()) return "список: встроенный"
        return try {
            m.readText().trim().ifBlank { "список: локальный" }
        } catch (_: Exception) {
            "список: локальный"
        }
    }

    /**
     * Download & parse remote filters → domain set on disk.
     * @return number of domains saved
     */
    fun updateLists(ctx: Context): Int {
        val domains = LinkedHashSet<String>()
        domains.addAll(BUILTIN)
        var sourceOk = 0
        for (url in REMOTE_URLS) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "VpEgs/1.0").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    parseFilterBody(body, domains)
                    sourceOk++
                }
            } catch (_: Exception) {
            }
            if (domains.size > 80_000) break // hard cap for mobile
        }
        if (domains.size <= BUILTIN.size && sourceOk == 0) {
            return -1
        }
        // Cap stored rules for Xray config size / memory
        val trimmed = domains.take(25_000)
        listFile(ctx).writeText(trimmed.joinToString("\n"))
        metaFile(ctx).writeText(
            "обновлено · ${trimmed.size} доменов · источников: $sourceOk"
        )
        return trimmed.size
    }

    /**
     * Parse hosts / AdGuard DNS / plain domain lines.
     * Examples:
     *  ||ads.example.com^
     *  0.0.0.0 tracker.com
     *  127.0.0.1 ads.com
     *  example.com
     */
    fun parseFilterBody(body: String, out: MutableSet<String>) {
        for (raw in body.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") || line.startsWith("[")) continue
            // AdGuard / ABP domain rule
            if (line.startsWith("||")) {
                line = line.removePrefix("||")
                line = line.substringBefore('^').substringBefore('$').substringBefore('/')
                line = line.trim().lowercase()
                if (isValidDomain(line)) out.add(line)
                continue
            }
            // Skip cosmetic / complex
            if (line.contains("##") || line.contains("#@#") || line.startsWith("/")) continue
            if (line.startsWith("@@")) continue // whitelist handled separately later
            // hosts format
            if (line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val d = parts[1].trim().lowercase()
                    if (isValidDomain(d)) out.add(d)
                }
                continue
            }
            // plain domain
            if (!line.contains(' ') && !line.contains('*') && isValidDomain(line)) {
                out.add(line.lowercase())
            }
        }
    }

    private fun isValidDomain(d: String): Boolean {
        if (d.length < 4 || d.length > 180) return false
        if ('.' !in d) return false
        if (d.startsWith(".") || d.endsWith(".")) return false
        if (d.any { it.isWhitespace() }) return false
        // no IPs
        if (d.all { it.isDigit() || it == '.' }) return false
        return true
    }
}
