package com.vpegs.ms.worker

import android.content.Context
import androidx.work.*
import com.vpegs.ms.data.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val subs = Prefs.loadSubscriptions(applicationContext)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            var any = false
            for (sub in subs.filter { it.autoUpdate }) {
                try {
                    val req = Request.Builder()
                        .url(sub.url)
                        .header("User-Agent", "v2rayNG/1.8.19")
                        .build()
                    client.newCall(req).execute().use { r ->
                        if (r.isSuccessful) any = true
                    }
                } catch (_: Exception) {}
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "vpegs_sub_update"

        fun schedule(ctx: Context, hours: Int, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled || hours <= 0) {
                wm.cancelUniqueWork(UNIQUE)
                return
            }
            val req = PeriodicWorkRequestBuilder<SubUpdateWorker>(
                hours.toLong().coerceAtLeast(1), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
