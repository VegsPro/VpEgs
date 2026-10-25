package com.vpegs.ms.ui.tile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.vpegs.ms.MainActivity
import com.vpegs.ms.vpn.VpEgsVpnService

/**
 * Quick Settings tile. Label/icon: "VpE"
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
            startService(Intent(this, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            applyState(tile, false, "Выкл")
        } else {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                val open = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("from_tile", true)
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
                    return
                }
                startForegroundService(Intent(this, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_CONNECT
                    putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, name)
                    putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                })
                applyState(tile, true, name.take(16))
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val on = VpEgsVpnService.isConnected
        applyState(
            tile,
            on,
            if (on) (VpEgsVpnService.currentServerName?.take(16) ?: "Вкл") else "Выкл"
        )
    }

    private fun applyState(tile: Tile, on: Boolean, subtitle: String) {
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "VpE"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.icon = Icon.createWithBitmap(textIcon("VpE", on))
        tile.updateTile()
    }

    private fun textIcon(text: String, active: Boolean): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (active) Color.parseColor("#0EA5E9") else Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }
        c.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 28f, 28f, bg)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val y = size / 2f - (tp.descent() + tp.ascent()) / 2f
        c.drawText(text, size / 2f, y, tp)
        return bmp
    }

    private fun toast(msg: String) {
        HandlerMain.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private object HandlerMain {
        private val h = android.os.Handler(android.os.Looper.getMainLooper())
        fun post(r: () -> Unit) = h.post(r)
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
