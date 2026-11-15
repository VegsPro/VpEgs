package com.vpegs.ms.core

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import com.vpegs.ms.data.Prefs
import com.vpegs.ms.vpn.VpEgsVpnService

/**
 * Auto-connect VPN on untrusted networks (Wi‑Fi / cellular).
 */
object NetworkAutoConnect {
    @Volatile private var registered = false
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(ctx: Context) {
        if (registered) return
        if (!Prefs.loadAutoConnect(ctx)) return
        val app = ctx.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                maybeConnect(app, cm, network)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                maybeConnect(app, cm, network)
            }
        }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(req, cb)
            callback = cb
            registered = true
        } catch (_: Exception) {}
    }

    fun stop(ctx: Context) {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        callback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        callback = null
        registered = false
    }

    private fun maybeConnect(ctx: Context, cm: ConnectivityManager, network: Network) {
        if (!Prefs.loadAutoConnect(ctx)) return
        if (VpEgsVpnService.isConnected) return
        val caps = cm.getNetworkCapabilities(network) ?: return
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (!wifi && !cell) return

        if (Prefs.loadAutoConnectUntrustedOnly(ctx) && wifi) {
            val ssid = currentSsid(ctx)?.trim('"') ?: return
            val trusted = Prefs.loadTrustedWifi(ctx)
            if (ssid in trusted) return // home / trusted — skip
        }

        // Trigger connect via service if we have last config
        val cfg = VpEgsVpnService.currentConfigJson
        val name = VpEgsVpnService.currentServerName
        if (cfg.isNullOrBlank()) {
            // Ask app process via broadcast
            try {
                ctx.sendBroadcast(Intent("com.vpegs.ms.ACTION_AUTO_CONNECT").setPackage(ctx.packageName))
            } catch (_: Exception) {}
            return
        }
        try {
            val i = Intent(ctx, VpEgsVpnService::class.java)
                .setAction(VpEgsVpnService.ACTION_CONNECT)
                .putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, name ?: "Auto")
                .putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        } catch (_: Exception) {}
    }

    private fun currentSsid(ctx: Context): String? {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid
        } catch (_: Exception) {
            null
        }
    }
}
