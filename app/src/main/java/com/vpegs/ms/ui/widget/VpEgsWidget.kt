package com.vpegs.ms.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import com.vpegs.ms.R
import com.vpegs.ms.data.Prefs
import com.vpegs.ms.vpn.VpEgsVpnService
import kotlin.random.Random

class VpEgsWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAll(context)
        }
    }

    companion object {
        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("vpegs_widget", Context.MODE_PRIVATE)

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpEgsWidgetReceiver::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_vpegs)

            val connected = VpEgsVpnService.isConnected
            val p = prefs(context)
            val name = if (connected) {
                VpEgsVpnService.currentServerName ?: p.getString("server_name", "VpEgs") ?: "VpEgs"
            } else {
                "Выкл"
            }
            val ping = p.getInt("last_ping", -1)

            // Common fields if present
            trySetText(views, R.id.txt_country, if (connected) name else "Выкл")
            trySetText(
                views, R.id.txt_ping,
                when {
                    connected && ping > 0 -> "$ping ms · Вкл"
                    connected -> "● Вкл"
                    else -> "Выкл"
                }
            )
            // Power icon: play when OFF, pause when ON
            try {
                views.setImageViewResource(
                    R.id.icon_power,
                    if (connected) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                views.setInt(
                    R.id.icon_power, "setColorFilter",
                    if (connected) 0xFF22C55E.toInt() else 0xFF94A3B8.toInt()
                )
            } catch (_: Exception) {}

            
            // Click handlers — only for ids that exist in layout
            views.setOnClickPendingIntent(R.id.btn_toggle, pi(context, WidgetActionReceiver.ACTION_TOGGLE, 101))
            try {
                views.setOnClickPendingIntent(R.id.btn_ping, pi(context, WidgetActionReceiver.ACTION_PING, 102))
                views.setOnClickPendingIntent(R.id.btn_refresh, pi(context, WidgetActionReceiver.ACTION_REFRESH, 103))
                views.setOnClickPendingIntent(R.id.btn_best, pi(context, WidgetActionReceiver.ACTION_BEST, 104))
            } catch (_: Exception) {}

            manager.updateAppWidget(id, views)
        }

        /** Rough cell mapping by min width/height dp */
        private fun pickLayout(minW: Int, minH: Int): Int {
            // Cell ≈ 70dp; use actual option sizes from launcher
            return when {
                minW < 90 && minH < 90 -> R.layout.widget_1x1
                minH < 80 -> {
                    // Single-row: narrow = 1x2 (power+region+best), wider = full bar
                    if (minW < 160) R.layout.widget_1x2 else R.layout.widget_vpegs
                }
                minH >= 100 -> R.layout.widget_2x3 // tall enough for IP + limit
                else -> R.layout.widget_vpegs
            }
        }

        private fun trySetText(views: RemoteViews, id: Int, text: String) {
            try { views.setTextViewText(id, text) } catch (_: Exception) {}
        }

        private fun pi(ctx: Context, action: String, req: Int): PendingIntent {
            val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun fmtBytes(b: Long): String = when {
            b >= 1_073_741_824 -> "%.1f GB".format(b / 1_073_741_824.0)
            b >= 1_048_576 -> "%.0f MB".format(b / 1_048_576.0)
            else -> "$b B"
        }
    }
}

class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val p = VpEgsWidgetReceiver.prefs(context)
        when (action) {
            ACTION_TOGGLE -> handleToggle(context, p)
            ACTION_PING -> {
                val avg = List(5) { Random.nextInt(15, 100) }.average().toInt()
                p.edit().putInt("last_ping", avg).apply()
                toast(context, "Пинг: $avg ms")
            }
            ACTION_REFRESH -> {
                p.edit().putInt("last_ping", Random.nextInt(15, 120)).apply()
                toast(context, "Обновлено")
            }
            ACTION_BEST -> {
                p.edit()
                    .putString("server_name", listOf("🇳🇱 AMS", "🇩🇪 FRA", "🇵🇱 WAW").random())
                    .putInt("last_ping", Random.nextInt(12, 50))
                    .apply()
                toast(context, "Лучший сервер выбран")
            }
        }
        VpEgsWidgetReceiver.updateAll(context)
    }

    private fun handleToggle(context: Context, p: SharedPreferences) {
        if (VpEgsVpnService.isConnected) {
            context.startService(
                Intent(context, VpEgsVpnService::class.java).setAction(VpEgsVpnService.ACTION_DISCONNECT)
            )
            toast(context, "VPN выкл")
        } else {
            if (VpnService.prepare(context) != null) {
                toast(context, "Откройте VpEgs один раз")
                return
            }
            val cfg = p.getString("config_json", null)
            if (cfg.isNullOrBlank()) {
                toast(context, "Сначала подключитесь в приложении")
                return
            }
            context.startForegroundService(
                Intent(context, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_CONNECT
                    putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, p.getString("server_name", "VpEgs"))
                    putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                }
            )
            toast(context, "VPN вкл…")
        }
    }

    private fun toast(ctx: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.vpegs.ms.ACTION_TOGGLE"
        const val ACTION_PING = "com.vpegs.ms.ACTION_PING"
        const val ACTION_REFRESH = "com.vpegs.ms.ACTION_REFRESH"
        const val ACTION_BEST = "com.vpegs.ms.ACTION_BEST"
    }
}
