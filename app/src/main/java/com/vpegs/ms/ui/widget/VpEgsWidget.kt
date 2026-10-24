package com.vpegs.ms.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import com.vpegs.ms.vpn.VpEgsVpnService

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
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_vpegs)

            val connected = VpEgsVpnService.isConnected
            val name = VpEgsVpnService.currentServerName ?: "Выберите сервер"
            views.setTextViewText(R.id.txt_country, if (connected) name else "Отключено")
            views.setTextViewText(R.id.txt_ping, if (connected) "● Online" else "Нажмите ▶")

            // Open app
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            // Toggle
            views.setOnClickPendingIntent(R.id.btn_toggle, actionPi(context, "com.vpegs.ms.ACTION_TOGGLE", 1))
            views.setOnClickPendingIntent(R.id.btn_ping, actionPi(context, "com.vpegs.ms.ACTION_PING", 2))
            views.setOnClickPendingIntent(R.id.btn_refresh, actionPi(context, "com.vpegs.ms.ACTION_REFRESH", 3))
            views.setOnClickPendingIntent(R.id.btn_best, actionPi(context, "com.vpegs.ms.ACTION_BEST", 4))

            manager.updateAppWidget(id, views)
        }

        private fun actionPi(ctx: Context, action: String, req: Int): PendingIntent {
            val i = Intent(ctx, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

class WidgetActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.vpegs.ms.ACTION_TOGGLE" -> {
                // Open app to handle VPN permission properly
                val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(open)
            }
            "com.vpegs.ms.ACTION_PING",
            "com.vpegs.ms.ACTION_REFRESH",
            "com.vpegs.ms.ACTION_BEST" -> {
                val open = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("widget_action", intent.action)
                context.startActivity(open)
            }
        }
        // Refresh widget UI
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, VpEgsWidgetReceiver::class.java))
        ids.forEach { VpEgsWidgetReceiver.updateWidget(context, mgr, it) }
    }
}
