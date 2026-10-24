package com.vpegs.ms.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.vpegs.ms.MainActivity
import com.vpegs.ms.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * System VPN service. Creates TUN interface so VpEgs appears in
 * Android "VPN" list (like Happ / INCY).
 *
 * Note: full Xray/sing-box core would process packets here.
 * This implementation establishes the VPN profile and keeps
 * the interface alive for system integration + UI status.
 */
class VpEgsVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpn(intent.getStringExtra(EXTRA_SERVER_NAME) ?: "VpEgs")
            ACTION_DISCONNECT -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn(serverName: String) {
        if (running.get()) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(serverName))

        val builder = Builder()
            .setSession("VpEgs · $serverName")
            .setMtu(1500)
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setBlocking(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            tunInterface = builder.establish()
            if (tunInterface == null) {
                stopSelf()
                return
            }
            running.set(true)
            isConnected = true
            currentServerName = serverName
            sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra("connected", true))

            // Keep TUN alive / drain packets (placeholder for core)
            worker = thread(name = "vpegs-tun") {
                val fd = tunInterface ?: return@thread
                val input = FileInputStream(fd.fileDescriptor)
                val output = FileOutputStream(fd.fileDescriptor)
                val buffer = ByteBuffer.allocate(32767)
                while (running.get()) {
                    try {
                        val len = input.channel.read(buffer)
                        if (len > 0) {
                            buffer.flip()
                            // In full client: pass to Xray/sing-box engine
                            buffer.clear()
                        } else {
                            Thread.sleep(50)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private fun stopVpn() {
        running.set(false)
        worker?.interrupt()
        worker = null
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        isConnected = false
        currentServerName = null
        sendBroadcast(Intent(ACTION_STATE_CHANGED).putExtra("connected", false))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "VpEgs VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(server: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText("$server · ${getString(R.string.vpn_notification_text)}")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.vpegs.ms.CONNECT"
        const val ACTION_DISCONNECT = "com.vpegs.ms.DISCONNECT"
        const val ACTION_STATE_CHANGED = "com.vpegs.ms.STATE"
        const val EXTRA_SERVER_NAME = "server_name"
        const val CHANNEL_ID = "vpegs_vpn"
        const val NOTIFICATION_ID = 42

        @Volatile var isConnected: Boolean = false
        @Volatile var currentServerName: String? = null
    }
}
