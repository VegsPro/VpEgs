package com.vpegs.ms.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.vpegs.ms.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer APK, downloads once to cache, installs via FileProvider.
 * Download path is optimized: OkHttp pool, large buffers, minimal UI churn.
 */
object AppUpdater {
    private const val API =
        "https://api.github.com/repos/VegsPro/VpEgs/releases/latest"
    private const val PREFS = "vpegs_update"
    private const val KEY_DISMISSED = "dismissed_version_code"
    private const val KEY_DECLINED = "declined_count_version"
    private const val KEY_DECLINED_N = "declined_n"
    private const val KEY_CACHED_CODE = "cached_apk_code"

    /** Shared client — connection reuse between check + download. */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // no overall cap for big APK
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    data class RemoteInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val tag: String
    )

    fun cacheDir(ctx: Context): File =
        File(ctx.cacheDir, "updates").also { it.mkdirs() }

    fun apkFile(ctx: Context, code: Int): File =
        File(cacheDir(ctx), "vpegs-$code.apk")

    fun isDismissed(ctx: Context, code: Int): Boolean =
        prefs(ctx).getInt(KEY_DISMISSED, 0) >= code

    fun dismiss(ctx: Context, code: Int) {
        prefs(ctx).edit().putInt(KEY_DISMISSED, code).apply()
    }

    fun canOfferInstall(ctx: Context, code: Int): Boolean {
        val p = prefs(ctx)
        val v = p.getInt(KEY_DECLINED, 0)
        val n = p.getInt(KEY_DECLINED_N, 0)
        if (v != code) return true
        return n < 2
    }

    fun recordDecline(ctx: Context, code: Int) {
        val p = prefs(ctx)
        val v = p.getInt(KEY_DECLINED, 0)
        val n = if (v == code) p.getInt(KEY_DECLINED_N, 0) + 1 else 1
        p.edit().putInt(KEY_DECLINED, code).putInt(KEY_DECLINED_N, n).apply()
        if (n >= 2) dismiss(ctx, code)
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isNewer(remote: RemoteInfo): Boolean =
        remote.versionCode > BuildConfig.VERSION_CODE

    fun hasCachedApk(ctx: Context, code: Int): Boolean {
        val f = apkFile(ctx, code)
        return f.exists() && f.length() > 100_000L
    }

    suspend fun fetchLatest(): RemoteInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VpEgs/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                var code = 0
                var name = tag.removePrefix("v")
                tag.removePrefix("v").toIntOrNull()?.let { code = it }
                val bodyText = json.optString("body", "")
                Regex("""versionCode\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(bodyText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?.let { code = maxOf(code, it) }
                Regex("""versionName\s*[=:]\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
                    .find(bodyText)?.groupValues?.getOrNull(1)?.let { name = it }

                val assets = json.optJSONArray("assets") ?: return@withContext null
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name", "")
                    val url = a.optString("browser_download_url", "")
                    if (n.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkUrl = url
                        // Prefer app-debug.apk / release names with version
                        if (n.contains("debug", true) || n.contains("release", true) ||
                            n.contains("vpegs", true)
                        ) break
                    }
                }
                if (apkUrl.isBlank()) return@withContext null
                if (code <= 0) {
                    // Fallback: parse from asset name vpegs-48.apk / -v48
                    Regex("""(\d{2,})""").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?.let { code = it }
                }
                if (code <= 0) code = BuildConfig.VERSION_CODE + 1
                RemoteInfo(code, name.ifBlank { tag }, apkUrl, tag)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fast path: OkHttp stream → FileOutputStream with 512 KiB buffer.
     * Progress callbacks throttled (~every 2%) to avoid UI jank.
     */
    suspend fun download(
        ctx: Context,
        info: RemoteInfo,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val out = apkFile(ctx, info.versionCode)
        if (hasCachedApk(ctx, info.versionCode)) {
            onProgress(1f)
            return@withContext out
        }
        val tmp = File(out.parentFile, "${out.name}.part")
        try {
            if (tmp.exists()) tmp.delete()
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "VpEgs/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/octet-stream")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buf = ByteArray(512 * 1024) // 512 KiB
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt()
                                // Update UI only when percent changes by ≥2
                                if (pct >= lastPct + 2 || pct >= 100) {
                                    lastPct = pct
                                    onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            } else if (read % (2L * 1024 * 1024) < buf.size) {
                                // unknown size: pulse every ~2 MB
                                onProgress((read % (20L * 1024 * 1024)).toFloat() / (20f * 1024 * 1024))
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            if (!out.exists() || out.length() < 100_000L) {
                out.delete()
                return@withContext null
            }
            prefs(ctx).edit().putInt(KEY_CACHED_CODE, info.versionCode).apply()
            onProgress(1f)
            out
        } catch (_: Exception) {
            try { tmp.delete() } catch (_: Exception) {}
            null
        }
    }

    fun install(activity: Activity, apk: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteCached(ctx: Context, installedCode: Int) {
        try {
            cacheDir(ctx).listFiles()?.forEach { f ->
                if (f.name.endsWith(".apk") || f.name.endsWith(".part")) {
                    val c = Regex("""vpegs-(\d+)""").find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (c == null || c <= installedCode) f.delete()
                }
            }
            prefs(ctx).edit().putInt(KEY_CACHED_CODE, 0).apply()
        } catch (_: Exception) {}
    }
}
