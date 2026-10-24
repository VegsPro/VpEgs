package com.vpegs.ms.ui.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.vpn.VpEgsVpnService

/**
 * Quick Settings tile — like Airplane mode / Flashlight.
 * Add via: pull down QS → edit → drag "VpEgs" tile.
 */
@RequiresApi(Build.VERSION_CODES.N)
class VpEgsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        if (VpEgsVpnService.isConnected) {
            // Disconnect
            startService(Intent(this, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            tile.state = Tile.STATE_INACTIVE
            tile.label = "VpEgs"
            tile.subtitle = "Выкл"
            tile.updateTile()
        } else {
            // Need VPN permission — open app if not granted
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                // Can't request from background tile safely on all OEMs — open app
                val open = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("from_tile", true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(open)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(open)
                }
            } else {
                // Permission already granted — connect
                startForegroundService(Intent(this, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_CONNECT
                    putExtra(
                        VpEgsVpnService.EXTRA_SERVER_NAME,
                        VpEgsVpnService.currentServerName ?: "VpEgs"
                    )
                })
                tile.state = Tile.STATE_ACTIVE
                tile.label = "VpEgs"
                tile.subtitle = "Вкл"
                tile.updateTile()
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val on = VpEgsVpnService.isConnected
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "VpEgs"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) {
                VpEgsVpnService.currentServerName?.take(18) ?: "Вкл"
            } else {
                "Выкл"
            }
        }
        // Use lock icon as fallback system icon
        tile.icon = Icon.createWithResource(this, android.R.drawable.ic_lock_lock)
        tile.updateTile()
    }

    companion object {
        /** Call from VpnService when state changes to refresh tile */
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
