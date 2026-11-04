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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer APK, downloads to cache, installs via FileProvider.
 */
object AppUpdater {
    private const val API =
        "https://api.github.com/repos/VegsPro/VpEgs/releases/latest"
    private const val PREFS = "vpegs_update"
    private const val KEY_DISMISSED = "dismissed_version_code"
    private const val KEY_DECLINED = "declined_count_version"
    private const val KEY_DECLINED_N = "declined_n"

    data class RemoteInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val tag: String
    )

    data class UpdateState(
        val available: Boolean = false,
        val downloaded: Boolean = false,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val versionName: String = "",
        val versionCode: Int = 0,
        val apkFile: File? = null,
        val showBanner: Boolean = false,
        val error: String? = null
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

    /** Returns true if we should still offer install (declined fewer than 2 times for this code). */
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

    fun clearDecline(ctx: Context) {
        prefs(ctx).edit().remove(KEY_DECLINED).remove(KEY_DECLINED_N).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun fetchLatest(): RemoteInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VpEgs/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            var code = 0
            var name = tag.removePrefix("v")
            // CI tags: v41, v42… treat as versionCode
            tag.removePrefix("v").toIntOrNull()?.let { code = it }
            val bodyText = json.optString("body", "")
            Regex("""versionCode\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(bodyText)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code = it }
            Regex("""versionName\s*[=:]\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
                .find(bodyText)?.groupValues?.getOrNull(1)?.let { name = it }
            // tag like v1.8.0 or v40
            if (tag.matches(Regex("""v?\d+\.\d+.*"""))) {
                name = tag.removePrefix("v")
            } else if (tag.matches(Regex("""v?(\d+)"""))) {
                tag.filter { it.isDigit() }.toIntOrNull()?.let { code = maxOf(code, it) }
            }
            // Assets
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val an = a.optString("name")
                if (an.endsWith(".apk", true)) {
                    apkUrl = a.optString("browser_download_url")
                    // versionCode from asset name vpegs-34.apk
                    Regex("""(\d+)\.apk$""").find(an)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                        code = maxOf(code, it)
                    }
                    break
                }
            }
            if (apkUrl.isBlank()) return@withContext null
            // If still same code as tag number only
            if (code <= BuildConfig.VERSION_CODE) {
                // try published_at ordering: use release id as soft signal
                val id = json.optInt("id", 0)
                if (tag.removePrefix("v").all { it.isDigit() }) {
                    code = tag.removePrefix("v").toIntOrNull() ?: code
                } else if (id > 0 && code == BuildConfig.VERSION_CODE) {
                    // compare versionName strings loosely
                }
            }
            RemoteInfo(code, name.ifBlank { tag }, apkUrl, tag)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, info: RemoteInfo, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val out = apkFile(ctx, info.versionCode)
                if (out.exists() && out.length() > 100_000) {
                    onProgress(1f)
                    return@withContext out
                }
                val tmp = File(out.absolutePath + ".part")
                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "VpEgs/${BuildConfig.VERSION_NAME}")
                }
                if (conn.responseCode !in 200..299) return@withContext null
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (out.exists()) out.delete()
                tmp.renameTo(out)
                onProgress(1f)
                out
            } catch (_: Exception) {
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

    fun deleteCached(ctx: Context, code: Int) {
        try {
            apkFile(ctx, code).delete()
            // also purge older
            cacheDir(ctx).listFiles()?.forEach { f ->
                if (f.name.endsWith(".apk") || f.name.endsWith(".part")) {
                    val c = Regex("""vpegs-(\d+)""").find(f.name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (c != null && c <= BuildConfig.VERSION_CODE) f.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun localVersionCode(): Int = BuildConfig.VERSION_CODE
    fun localVersionName(): String = BuildConfig.VERSION_NAME
}
