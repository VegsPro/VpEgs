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
import hev.htproxy.TProxyService
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.HashSet

/**
 * Working path on AndroidLibXrayLite:
 * 1) Xray opens local SOCKS 127.0.0.1:10808  (startLoop(config, 0) — NO tun inbound)
 * 2) hev-socks5-tunnel bridges system TUN fd → that SOCKS
 */
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
                try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
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
            Libv2ray.initCoreEnv(files, "")  // 2nd arg = xudp basekey, must be empty or valid base64
            Libv2ray.touch()
        } catch (e: Throwable) {
            fail("Libv2ray init: ${e.message}"); stopSelf(); return
        }

        // 1) Xray SOCKS only (tunFd = 0)
        val adapted = try {
            TunConfigAdapter.adapt(configJson)
        } catch (e: Exception) {
            fail("adapt: ${e.message}"); stopSelf(); return
        }
        Log.i(TAG, "Xray cfg=${adapted.length}")

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
            fail("newCore: ${e.message}"); stopSelf(); return
        }
        try {
            ctrl.startLoop(adapted, 0)
        } catch (e: Throwable) {
            Log.e(TAG, "startLoop", e)
            fail("Xray: ${e.message?.take(120)}")
            stopSelf(); return
        }
        controller = ctrl

        // Wait for SOCKS port
        var socksReady = false
        repeat(30) {
            if (portOpen(10808)) { socksReady = true; return@repeat }
            Thread.sleep(100)
        }
        if (!socksReady) {
            fail("SOCKS :10808 не открылся")
            stopVpnInternal(); stopSelf(); return
        }
        Log.i(TAG, "SOCKS ready")

        // 2) System TUN
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
                // Per-app routing — NEVER mix addAllowed + addDisallowed (Android ignores rules).
        // Mode A (global ON, invert OFF): disallow bypass packages (+ self).
        // Mode B (global OFF or invert ON): only allow selected packages (never add self).
        try {
            val sp = getSharedPreferences("vpegs_data", MODE_PRIVATE)
            val global = sp.getBoolean("global_proxy", true)
            val invert = sp.getBoolean("invert_proxy", false)
            var bypass = HashSet(sp.getStringSet("bypass_pkgs", emptySet()) ?: emptySet())
            if (bypass.isEmpty()) {
                val str = sp.getString("bypass_pkgs_str", null).orEmpty()
                if (str.isNotBlank()) {
                    bypass = HashSet(str.lines().map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
            val expanded = HashSet<String>()
            for (pkg in bypass) {
                expanded.add(pkg)
                expanded.addAll(relatedPackages(pkg))
            }
            // Always keep ourselves out of the tunnel
            expanded.add(packageName)

            Log.i(TAG, "routing global=$global invert=$invert bypass=${expanded.size}")

            if (global && !invert) {
                // Everything through VPN except expanded (disallow-only mode)
                for (pkg in expanded) {
                    try {
                        builder.addDisallowedApplication(pkg)
                        Log.i(TAG, "disallow $pkg")
                    } catch (e: Exception) {
                        Log.w(TAG, "disallow $pkg: ${e.message}")
                    }
                }
            } else {
                // Allowed-list only: apps that must use VPN.
                // proxyEnabled=true → not in bypass → allow
                // Do NOT call addDisallowedApplication at all in this mode.
                val pm = packageManager
                val candidates = try {
                    pm.getInstalledApplications(0).map { it.packageName }.toSet()
                } catch (_: Exception) { emptySet() }
                val through = if (global && invert) {
                    // invert+global: only packages in list through VPN
                    expanded - packageName
                } else {
                    // global OFF: only apps NOT in bypass
                    candidates - expanded
                }
                var allowed = 0
                for (pkg in through) {
                    if (pkg == packageName) continue
                    try {
                        builder.addAllowedApplication(pkg)
                        allowed++
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "allow-list size=$allowed (of ${through.size})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "routing setup: ${e.message}")
            // Fallback: at least exclude ourselves
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            fail("TUN: ${e.message}"); stopVpnInternal(); stopSelf(); return
        }
        if (pfd == null) {
            fail("TUN=null (закрой другое VPN)")
            stopVpnInternal(); stopSelf(); return
        }
        tunInterface = pfd

        // 3) hev: TUN → SOCKS
        val hevConf = File(filesDir, "hev.yml")
        hevConf.writeText(
            """
            tunnel:
              mtu: 1500
              ipv4: 10.8.0.2
            socks5:
              port: 10808
              address: 127.0.0.1
              udp: 'udp'
            misc:
              connect-timeout: 10000
            """.trimIndent()
        )
        val hevOk = try {
            TProxyService.TProxyStartService(hevConf.absolutePath, pfd.fd)
        } catch (e: Throwable) {
            Log.e(TAG, "hev", e)
            false
        }
        if (!hevOk) {
            fail("hev-tunnel не стартовал")
            stopVpnInternal(); stopSelf(); return
        }

        isConnected = true
        lastError = null
        currentServerName = serverName
        currentConfigJson = configJson
        val bypassCount = try {
            val sp = getSharedPreferences("vpegs_data", MODE_PRIVATE)
            (sp.getStringSet("bypass_pkgs", emptySet()) ?: emptySet()).size
        } catch (_: Exception) { 0 }
        startForeground(NOTIFICATION_ID, buildNotification(serverName, "обход:$bypassCount"))
        Log.i(TAG, "VPN UP (socks+hev)")
        try { com.vpegs.ms.ui.tile.VpEgsTileService.requestUpdate(this) } catch (_: Exception) {}
    }

    private fun portOpen(port: Int): Boolean = try {
        Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", port), 150); true }
    } catch (_: Exception) { false }

    private fun relatedPackages(pkg: String): List<String> {
        val map = mapOf(
            "com.android.chrome" to listOf(
                "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
                "com.google.android.apps.chrome", "com.android.browser"
            ),
            "org.mozilla.firefox" to listOf("org.mozilla.firefox_beta", "org.mozilla.fennec_fdroid"),
            "com.opera.browser" to listOf("com.opera.browser.beta"),
            "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser.beta"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx.beta"),
            "com.brave.browser" to listOf("com.brave.browser_beta")
        )
        val out = mutableListOf<String>()
        map[pkg]?.let { out.addAll(it) }
        // reverse lookup
        for ((k, v) in map) {
            if (pkg in v) {
                out.add(k)
                out.addAll(v)
            }
        }
        return out.distinct()
    }

    private fun fail(msg: String) {
        Log.e(TAG, msg)
        lastError = msg
        isConnected = false
    }

    private fun stopVpnInternal() {
        try { TProxyService.TProxyStopService() } catch (_: Exception) {}
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
