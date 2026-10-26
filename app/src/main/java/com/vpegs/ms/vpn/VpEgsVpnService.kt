package com.vpegs.ms.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.core.TunConfigAdapter
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream

/**
 * Pure Xray TUN mode:
 * 1) VpnService.establish() → TUN fd
 * 2) Libv2ray StartLoop(config, fd) sets xray.tun.fd
 * 3) Config has inbound protocol "tun" so Xray reads the fd
 */
class VpEgsVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var controller: CoreController? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val name = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "VpEgs"
                val config = intent.getStringExtra(EXTRA_CONFIG_JSON)
                if (config.isNullOrBlank()) {
                    broadcast(false, "Пустой конфиг")
                    stopSelf()
                    return START_NOT_STICKY
                }
                Thread { startVpn(name, config) }.start()
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(serverName: String, configJson: String) {
        if (running) stopVpnInternal()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(serverName, "запуск…"))

        try {
            ensureGeoAssets()
            val files = filesDir.absolutePath
            Libv2ray.initCoreEnv(files, files)
            Libv2ray.touch()

            // 1) System TUN first
            val builder = Builder()
                .setSession("VpEgs · $serverName")
                .setMtu(1500)
                .addAddress("10.8.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setBlocking(true)

            try {
                builder.addAddress("fd00:8:8:8::2", 126)
                builder.addRoute("::", 0)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            // Critical: Xray outbound must NOT go through TUN (loop)
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

            tunInterface = builder.establish()
            val pfd = tunInterface
            if (pfd == null) {
                broadcast(false, "Android не выдал TUN (закрой другое VPN)")
                stopSelf()
                return
            }
            val fd = pfd.fd
            Log.i(TAG, "TUN established fd=$fd")

            // 2) Xray config with tun inbound
            val adapted = TunConfigAdapter.adapt(configJson)
            Log.i(TAG, "Xray ${Libv2ray.checkVersionX()} cfg=${adapted.length}")
            Log.d(TAG, "cfg preview: ${adapted.take(400)}")

            val cb = object : CoreCallbackHandler {
                override fun onEmitStatus(l: Long, s: String?): Long {
                    Log.d(TAG, "xray: $s"); return 0
                }
                override fun shutdown(): Long = 0
                override fun startup(): Long = 0
            }
            val ctrl = Libv2ray.newCoreController(cb)

            // Also set both env names before start (belt and suspenders)
            try {
                System.setProperty("xray.tun.fd", fd.toString())
            } catch (_: Exception) {}

            try {
                ctrl.startLoop(adapted, fd)
            } catch (e: Exception) {
                Log.e(TAG, "startLoop failed", e)
                stopVpnInternal()
                broadcast(false, "Xray: ${e.message?.take(80)}")
                stopSelf()
                return
            }
            controller = ctrl

            if (!ctrl.isRunning) {
                Log.e(TAG, "core not running after startLoop")
                stopVpnInternal()
                broadcast(false, "Xray не запустился")
                stopSelf()
                return
            }

            running = true
            isConnected = true
            currentServerName = serverName
            currentConfigJson = configJson
            startForeground(NOTIFICATION_ID, buildNotification(serverName, "Xray TUN"))
            broadcast(true, null)
            try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
            Log.i(TAG, "VPN fully up via Xray TUN")
        } catch (e: Exception) {
            Log.e(TAG, "startVpn", e)
            stopVpnInternal()
            broadcast(false, e.message?.take(80) ?: "ошибка VPN")
            stopSelf()
        }
    }

    private fun stopVpnInternal() {
        running = false
        try { controller?.stopLoop() } catch (_: Exception) {}
        controller = null
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        isConnected = false
        currentServerName = null
    }

    private fun stopVpn() {
        stopVpnInternal()
        broadcast(false, null)
        try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun broadcast(connected: Boolean, error: String?) {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .putExtra("connected", connected)
                .putExtra("error", error)
        )
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }

    private fun ensureGeoAssets() {
        for (name in listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")) {
            val out = File(filesDir, name)
            if (out.exists() && out.length() > 1000) continue
            try {
                assets.open(name).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "asset $name: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "VpEgs VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(server: String, detail: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText("$server · $detail")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "VpEgsVpn"
        const val ACTION_CONNECT = "com.vpegs.ms.CONNECT"
        const val ACTION_DISCONNECT = "com.vpegs.ms.DISCONNECT"
        const val ACTION_STATE_CHANGED = "com.vpegs.ms.STATE"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_CONFIG_JSON = "config_json"
        const val CHANNEL_ID = "vpegs_vpn"
        const val NOTIFICATION_ID = 42

        @Volatile var isConnected: Boolean = false
        @Volatile var currentServerName: String? = null
        @Volatile var currentConfigJson: String? = null
    }
}
