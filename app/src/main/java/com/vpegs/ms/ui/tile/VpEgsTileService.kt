package com.vpegs.ms.ui.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.vpn.VpEgsVpnService

@RequiresApi(Build.VERSION_CODES.N)
class VpEgsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val on = isVpnReallyOn()
        if (on) {
            startService(Intent(this, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            VpEgsVpnService.isConnected = false
            applyState(tile, false)
        } else {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
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
                toast("Выдайте VPN-разрешение в приложении")
            } else {
                val p = getSharedPreferences("vpegs_widget", MODE_PRIVATE)
                val name = p.getString("server_name", null)
                    ?: VpEgsVpnService.currentServerName
                    ?: "VpEgs"
                val cfg = p.getString("config_json", null)
                    ?: VpEgsVpnService.currentConfigJson
                if (cfg.isNullOrBlank()) {
                    toast("Сначала выберите сервер в приложении")
                    startActivity(
                        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    startForegroundService(Intent(this, VpEgsVpnService::class.java).apply {
                        action = VpEgsVpnService.ACTION_CONNECT
                        putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, name)
                        putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                    })
                    applyState(tile, true)
                }
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        applyState(tile, isVpnReallyOn())
    }

    private fun isVpnReallyOn(): Boolean {
        if (!VpEgsVpnService.isConnected) return false
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.any { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Exception) {
            VpEgsVpnService.isConnected
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
        // Transparent "V" — system draws the circular QS background
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_v)
        tile.updateTile()
    }

    private fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
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
