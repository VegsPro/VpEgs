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
                    Log.e(TAG, "No config JSON")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startVpn(name, config)
            }
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(serverName: String, configJson: String) {
        if (running) {
            stopVpnInternal()
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(serverName))

        try {
            ensureGeoAssets()
            val files = filesDir.absolutePath
            Libv2ray.initCoreEnv(files, files)
            Libv2ray.touch()
            Log.i(TAG, "Xray version: ${Libv2ray.checkVersionX()}")

            // Force all traffic through proxy (fix subscription routing)
            val adapted = TunConfigAdapter.adapt(configJson)
            Log.d(TAG, "Adapted config length=${adapted.length}")

            val builder = Builder()
                .setSession("VpEgs · $serverName")
                .setMtu(1500)
                .addAddress("10.8.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setBlocking(true)

            // IPv6 capture if supported (prevents IPv6 leak)
            try {
                builder.addAddress("fd00:8:8:8::2", 126)
                builder.addRoute("::", 0)
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            // Allow Xray itself to reach the internet (bypass VPN) — critical
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            tunInterface = builder.establish()
            val pfd = tunInterface
            if (pfd == null) {
                Log.e(TAG, "TUN establish failed")
                stopSelf()
                return
            }

            val cb = object : CoreCallbackHandler {
                override fun onEmitStatus(l: Long, s: String?): Long {
                    Log.d(TAG, "core: $s")
                    return 0
                }
                override fun shutdown(): Long = 0
                override fun startup(): Long = 0
            }
            val ctrl = Libv2ray.newCoreController(cb)
            // fd must stay valid — do NOT detachFd()
            ctrl.startLoop(adapted, pfd.fd)
            controller = ctrl

            running = true
            isConnected = true
            currentServerName = serverName
            currentConfigJson = configJson
            sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra("connected", true))
            try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
            Log.i(TAG, "VPN+Xray started for $serverName")
        } catch (e: Exception) {
            Log.e(TAG, "startVpn failed: ${e.message}", e)
            stopVpn()
        }
    }

    private fun stopVpnInternal() {
        running = false
        try { controller?.stopLoop() } catch (e: Exception) {
            Log.w(TAG, "stopLoop: ${e.message}")
        }
        controller = null
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        isConnected = false
        currentServerName = null
    }

    private fun stopVpn() {
        stopVpnInternal()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra("connected", false))
        try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }

    /** Copy geoip/geosite from AAR assets into filesDir for routing rules */
    private fun ensureGeoAssets() {
        for (name in listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")) {
            val out = File(filesDir, name)
            if (out.exists() && out.length() > 1000) continue
            try {
                assets.open(name).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                Log.i(TAG, "Extracted $name (${out.length()} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Asset $name not available: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "VpEgs VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(server: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText("$server · Xray")
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
