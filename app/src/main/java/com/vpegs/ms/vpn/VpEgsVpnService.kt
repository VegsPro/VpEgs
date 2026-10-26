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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val name = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "VpEgs"
                val config = intent.getStringExtra(EXTRA_CONFIG_JSON)
                if (config.isNullOrBlank()) {
                    fail("Пустой конфиг")
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Must call startForeground quickly
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification(name, "запуск…"))
                Thread({
                    try {
                        startVpn(name, config)
                    } catch (e: Throwable) {
                        Log.e(TAG, "thread", e)
                        fail(e.message ?: e.javaClass.simpleName)
                        stopVpnInternal()
                        stopSelf()
                    }
                }, "vpegs-vpn").start()
            }
            ACTION_DISCONNECT -> {
                stopVpnInternal()
                isConnected = false
                lastError = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn(serverName: String, configJson: String) {
        if (isConnected) stopVpnInternal()

        ensureGeoAssets()
        val files = filesDir.absolutePath
        try {
            Libv2ray.initCoreEnv(files, files)
            Libv2ray.touch()
        } catch (e: Throwable) {
            fail("Libv2ray init: ${e.message}")
            stopSelf(); return
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { builder.setMetered(false) } catch (_: Exception) {}
        }
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            fail("TUN establish: ${e.message}")
            stopSelf(); return
        }
        if (pfd == null) {
            fail("TUN = null (закрой другое VPN)")
            stopSelf(); return
        }
        tunInterface = pfd
        val fd = pfd.fd
        Log.i(TAG, "TUN fd=$fd")

        val adapted = try {
            TunConfigAdapter.adapt(configJson)
        } catch (e: Exception) {
            fail("adapt: ${e.message}")
            stopVpnInternal(); stopSelf(); return
        }
        Log.i(TAG, "Xray ${try { Libv2ray.checkVersionX() } catch (_: Exception) { "?" }} cfg=${adapted.length}")

        val cb = object : CoreCallbackHandler {
            override fun onEmitStatus(l: Long, s: String?): Long {
                Log.d(TAG, "xray: $s"); return 0
            }
            override fun shutdown(): Long = 0
            override fun startup(): Long = 0
        }

        val ctrl = try {
            Libv2ray.newCoreController(cb)
        } catch (e: Throwable) {
            fail("newCoreController: ${e.message}")
            stopVpnInternal(); stopSelf(); return
        }

        try {
            // Pass TUN fd — lib sets xray.tun.fd; config has protocol:tun inbound
            ctrl.startLoop(adapted, fd)
        } catch (e: Throwable) {
            Log.e(TAG, "startLoop", e)
            fail("Xray startLoop: ${e.message?.take(100)}")
            stopVpnInternal(); stopSelf(); return
        }

        // Confirm core thinks it's running
        val running = try { ctrl.getIsRunning() } catch (_: Exception) { true }
        if (!running) {
            fail("Xray IsRunning=false")
            stopVpnInternal(); stopSelf(); return
        }

        controller = ctrl
        isConnected = true
        lastError = null
        currentServerName = serverName
        currentConfigJson = configJson
        startForeground(NOTIFICATION_ID, buildNotification(serverName, "Xray TUN"))
        Log.i(TAG, "VPN UP")
        try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
    }

    private fun fail(msg: String) {
        Log.e(TAG, msg)
        lastError = msg
        isConnected = false
    }

    private fun stopVpnInternal() {
        try { controller?.stopLoop() } catch (_: Exception) {}
        controller = null
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        isConnected = false
        currentServerName = null
    }

    override fun onDestroy() {
        stopVpnInternal()
        super.onDestroy()
    }

    private fun ensureGeoAssets() {
        for (name in listOf("geoip.dat", "geosite.dat")) {
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
        @Volatile var lastError: String? = null
        @Volatile var currentServerName: String? = null
        @Volatile var currentConfigJson: String? = null
    }
}
