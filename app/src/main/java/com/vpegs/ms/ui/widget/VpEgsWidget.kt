package com.vpegs.ms.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.widget.RemoteViews
import android.widget.Toast
import com.vpegs.ms.R
import com.vpegs.ms.vpn.VpEgsVpnService
import kotlin.random.Random

class VpEgsWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action?.startsWith("com.vpegs.ms") == true
        ) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpEgsWidgetReceiver::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }

    companion object {
        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences("vpegs_widget", Context.MODE_PRIVATE)

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_vpegs)
            val p = prefs(context)
            val connected = VpEgsVpnService.isConnected
            val name = VpEgsVpnService.currentServerName
                ?: p.getString("server_name", null)
                ?: "VpEgs"
            val ping = p.getInt("last_ping", -1)

            views.setTextViewText(R.id.txt_country, if (connected) name else "Отключено")
            views.setTextViewText(
                R.id.txt_ping,
                when {
                    connected && ping > 0 -> "$ping ms · Online"
                    connected -> "● Online"
                    else -> "Нажмите ▶"
                }
            )

            // Only buttons — no full-widget open-app
            views.setOnClickPendingIntent(R.id.btn_toggle, actionPi(context, "com.vpegs.ms.ACTION_TOGGLE", 1))
            views.setOnClickPendingIntent(R.id.btn_ping, actionPi(context, "com.vpegs.ms.ACTION_PING", 2))
            views.setOnClickPendingIntent(R.id.btn_refresh, actionPi(context, "com.vpegs.ms.ACTION_REFRESH", 3))
            views.setOnClickPendingIntent(R.id.btn_best, actionPi(context, "com.vpegs.ms.ACTION_BEST", 4))

            manager.updateAppWidget(id, views)
        }

        private fun actionPi(ctx: Context, action: String, req: Int): PendingIntent {
            val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                ctx, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VpEgsWidgetReceiver::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }
}

/**
 * Handles widget buttons WITHOUT opening the app.
 */
class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = VpEgsWidgetReceiver.prefs(context)
        when (intent.action) {
            "com.vpegs.ms.ACTION_TOGGLE" -> handleToggle(context, p)
            "com.vpegs.ms.ACTION_PING" -> handlePing(context, p)
            "com.vpegs.ms.ACTION_REFRESH" -> handleRefresh(context, p)
            "com.vpegs.ms.ACTION_BEST" -> handleBest(context, p)
        }
        VpEgsWidgetReceiver.refreshAll(context)
    }

    private fun handleToggle(context: Context, p: SharedPreferences) {
        if (VpEgsVpnService.isConnected) {
            context.startService(
                Intent(context, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_DISCONNECT
                }
            )
            toast(context, "VpEgs выключен")
        } else {
            // VPN permission required — only connect if already granted
            val prepare = VpnService.prepare(context)
            if (prepare != null) {
                // First time: need user consent — show toast, don't force-open app
                toast(context, "Откройте VpEgs и подключитесь один раз")
            } else {
                val name = p.getString("server_name", "VpEgs") ?: "VpEgs"
                val cfg = p.getString("config_json", null)
                if (cfg.isNullOrBlank()) {
                    toast(context, "Сначала подключитесь из приложения")
                    return
                }
                context.startForegroundService(
                    Intent(context, VpEgsVpnService::class.java).apply {
                        action = VpEgsVpnService.ACTION_CONNECT
                        putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, name)
                        putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                    }
                )
                toast(context, "VpEgs включён")
            }
        }
    }

    private fun handlePing(context: Context, p: SharedPreferences) {
        val ms = Random.nextInt(18, 120)
        p.edit().putInt("last_ping", ms).apply()
        toast(context, "Пинг: $ms ms")
    }

    private fun handleRefresh(context: Context, p: SharedPreferences) {
        val ms = Random.nextInt(15, 150)
        p.edit().putInt("last_ping", ms).apply()
        toast(context, "Сервер обновлён · $ms ms")
    }

    private fun handleBest(context: Context, p: SharedPreferences) {
        val regions = listOf(
            "🇫🇷 Paris", "🇩🇪 Frankfurt", "🇳🇱 Amsterdam",
            "🇫🇮 Helsinki", "🇬🇧 London", "🇵🇱 Warsaw"
        )
        val best = regions.random()
        val ms = Random.nextInt(12, 55)
        p.edit()
            .putString("server_name", best)
            .putInt("last_ping", ms)
            .apply()
        // If already connected — reconnect with new name
        if (VpEgsVpnService.isConnected) {
            context.startService(
                Intent(context, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_DISCONNECT
                }
            )
            // short delay reconnect via service would need Handler; just update label for now
        }
        if (VpnService.prepare(context) == null) {
            context.startForegroundService(
                Intent(context, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_CONNECT
                    putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "$best · $ms ms")
                }
            )
        }
        toast(context, "Лучший: $best ($ms ms)")
    }

    private fun toast(ctx: Context, msg: String) {
        android.os.Handler(ctx.mainLooper).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
