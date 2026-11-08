package com.vpegs.ms.ui.tile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.data.Prefs
import com.vpegs.ms.data.ServerNode
import com.vpegs.ms.vpn.VpEgsVpnService
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Picks the best server (same filters as «Лучший») and connects VPN.
 * Started from QS tile when «При включении выбирать лучший» is on.
 */
class TileBestConnectService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("VpEgs")
            .setContentText("Подбор сервера…")
            .setSmallIcon(R.drawable.ic_tile_v)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)

        scope.launch {
            try {
                if (VpnService.prepare(this@TileBestConnectService) != null) {
                    updateText("Нужно VPN-разрешение — откройте приложение")
                    delay(2000)
                    stopSelfSafe()
                    return@launch
                }
                val best = withContext(Dispatchers.IO) { pickBest() }
                if (best == null) {
                    updateText("Нет доступных серверов")
                    delay(2200)
                    stopSelfSafe()
                    return@launch
                }
                val (node, cfg) = best
                updateText("Подключение: ${node.flag} ${node.name}".take(40))
                getSharedPreferences("vpegs_widget", MODE_PRIVATE).edit()
                    .putString("server_name", "${node.flag} ${node.name}")
                    .putString("config_json", cfg)
                    .putString("server_id", node.id)
                    .apply()
                startForegroundService(
                    Intent(this@TileBestConnectService, VpEgsVpnService::class.java).apply {
                        action = VpEgsVpnService.ACTION_CONNECT
                        putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${node.flag} ${node.name}")
                        putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                    }
                )
                Prefs.saveSelectedId(this@TileBestConnectService, node.id)
                delay(800)
                VpEgsTileService.requestUpdate(this@TileBestConnectService)
            } catch (e: Exception) {
                updateText("Ошибка: ${e.message?.take(40) ?: "сбой"}")
                delay(2000)
            } finally {
                stopSelfSafe()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSelfSafe() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun updateText(text: String) {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("VpEgs")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tile_v)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm?.notify(NOTIF_ID, n)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Подбор сервера", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun pickBest(): Pair<ServerNode, String>? {
        val servers = Prefs.loadServers(this).toMutableList()
        val configs = Prefs.loadConfigs(this)
        if (servers.isEmpty()) return null

        if (Prefs.loadTileOnlyFavorites(this)) {
            servers.retainAll { it.isFavorite }
        }
        if (!Prefs.loadBestAllSubs(this)) {
            val ids = Prefs.loadBestSubIds(this)
            if (ids.isNotEmpty()) servers.retainAll { it.subscriptionId in ids }
        }
        if (!Prefs.loadBestAllowRu(this)) {
            servers.retainAll { !looksRussian(it) }
        }
        if (servers.isEmpty()) return null

        val maxPing = Prefs.loadTileMaxPingMs(this)
        val sem = kotlinx.coroutines.sync.Semaphore(8)
        val results = mutableListOf<Pair<ServerNode, Int>>()
        runBlocking {
            val jobs = servers.map { s ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val ms = tcpPing(s.address, s.port, 1600) ?: return@withPermit
                        if (maxPing > 0 && ms > maxPing) return@withPermit
                        synchronized(results) { results.add(s to ms) }
                    }
                }
            }
            jobs.awaitAll()
        }
        val best = results.minByOrNull { it.second }?.first ?: return null
        val cfg = configs[best.id] ?: best.rawConfig
        if (cfg.isBlank()) return null
        return best to cfg
    }

    private fun looksRussian(s: ServerNode): Boolean {
        val n = (s.name + " " + s.country + " " + s.flag).lowercase()
        if ("🇷🇺" in s.flag || "🇷🇺" in s.name) return true
        if (n.contains("russia") || n.contains("россия") || n.contains("moscow") || n.contains("москва")) return true
        return false
    }

    private fun tcpPing(host: String, port: Int, timeoutMs: Int): Int? {
        if (host.isBlank() || port <= 0) return null
        val t0 = System.currentTimeMillis()
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - t0).toInt()
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "vpegs_tile_pick"
        private const val NOTIF_ID = 7102
    }
}
