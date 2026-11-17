package com.vpegs.ms.core

import com.vpegs.ms.vpn.VpEgsVpnService
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Lightweight tunnel health check (SOCKS port). Low frequency to avoid jank.
 */
object HealthMonitor {
    @Volatile private var running = false
    @Volatile var lastOkMs: Long = 0L
    @Volatile var lastFailMs: Long = 0L
    @Volatile var statusText: String = "—"

    fun start(onDead: () -> Unit) {
        if (running) return
        running = true
        thread(name = "vpegs-health", isDaemon = true) {
            while (running) {
                try {
                    if (!VpEgsVpnService.isConnected) {
                        statusText = "VPN выкл"
                        Thread.sleep(10_000)
                        continue
                    }
                    val ok = checkSocks()
                    if (ok) {
                        lastOkMs = System.currentTimeMillis()
                        VpEgsVpnService.lastHandshakeOk = lastOkMs
                        statusText = "Туннель OK"
                    } else {
                        lastFailMs = System.currentTimeMillis()
                        statusText = "Нет handshake"
                        if (lastOkMs > 0 && lastFailMs - lastOkMs > 30_000) {
                            onDead()
                        }
                    }
                } catch (_: Exception) {}
                Thread.sleep(10_000)
            }
        }
    }

    fun stop() { running = false }

    private fun checkSocks(): Boolean = try {
        Socket().use { s ->
            s.soTimeout = 600
            s.connect(InetSocketAddress("127.0.0.1", 10808), 600)
            true
        }
    } catch (_: Exception) {
        false
    }
}
