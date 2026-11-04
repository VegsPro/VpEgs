package com.vpegs.ms.ui.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.vpn.VpEgsVpnService

@RequiresApi(Build.VERSION_CODES.N)
class VpEgsTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshTile() }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
        // Re-check shortly — system VPN network appears with a delay
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 400)
        mainHandler.postDelayed(refreshRunnable, 1200)
    }

    override fun onStopListening() {
        mainHandler.removeCallbacks(refreshRunnable)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val on = isVpEgsOn()
        if (on) {
            // Disconnect our VPN
            startService(Intent(this, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            // Optimistic UI; service confirms via requestUpdate
            applyState(tile, false)
            mainHandler.postDelayed(refreshRunnable, 500)
            mainHandler.postDelayed(refreshRunnable, 1500)
        } else {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                openApp()
                toast("Выдайте VPN-разрешение в приложении")
                return
            }
            val p = getSharedPreferences("vpegs_widget", MODE_PRIVATE)
            val name = p.getString("server_name", null)
                ?: VpEgsVpnService.currentServerName
                ?: "VpEgs"
            val cfg = p.getString("config_json", null)
                ?: VpEgsVpnService.currentConfigJson
            if (cfg.isNullOrBlank()) {
                toast("Сначала выберите сервер в приложении")
                openApp()
                return
            }
            startForegroundService(Intent(this, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_CONNECT
                putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, name)
                putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
            })
            applyState(tile, true)
            mainHandler.postDelayed(refreshRunnable, 600)
            mainHandler.postDelayed(refreshRunnable, 1800)
        }
    }

    private fun openApp() {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(open)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(open)
            }
        } catch (_: Exception) {
            startActivity(open)
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        applyState(tile, isVpEgsOn())
    }

    /**
     * Prefer our service flag; fall back to system VPN only if flag says on
     * (avoids showing ACTIVE when another VPN is connected but VpEgs is off).
     */
    private fun isVpEgsOn(): Boolean {
        if (VpEgsVpnService.isConnected) return true
        // Service process may have been killed while OS still holds the VPN interface —
        // only count as on if package is the active VPN owner when possible.
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
            // If any VPN is up but our flag is false, treat as OFF for this tile
            // (user may have another VPN). Tile should only reflect VpEgs.
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun applyState(tile: Tile, on: Boolean) {
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "V"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) {
                VpEgsVpnService.currentServerName?.take(18) ?: "Вкл"
            } else {
                "Выкл"
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_v)
        try {
            tile.updateTile()
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun requestUpdate(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val component = android.content.ComponentName(context, VpEgsTileService::class.java)
                    requestListeningState(context, component)
                } catch (_: Exception) {}
            }
        }
    }
}
