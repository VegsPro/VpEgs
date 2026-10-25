package com.vpegs.ms.data

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

enum class Protocol(val display: String, val color: Long) {
    VLESS("VLESS", 0xFF0EA5E9),
    VLESS_REALITY("VLESS Reality", 0xFF38BDF8),
    VMESS("VMess", 0xFF06B6D4),
    TROJAN("Trojan", 0xFFF43F5E),
    SHADOWSOCKS("Shadowsocks", 0xFFFBBF24),
    HYSTERIA2("Hysteria2", 0xFF22C55E),
    TUIC("TUIC", 0xFFA78BFA)
}

@Immutable
data class ServerNode(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val protocol: Protocol,
    val latencyMs: Int? = null,
    val isFavorite: Boolean = false,
    val isSelected: Boolean = false,
    val address: String = "",
    val port: Int = 443,
    val rawConfig: String = "",
    val subscriptionId: String = ""
)

@Immutable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 6,
    val serversCount: Int = 0,
    /** Traffic from subscription-userinfo header */
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expireAt: Long = 0, // unix seconds
    /** Free-form announce lines from provider (HAPP-style) */
    val announce: String = "",
    val supportUrl: String = "",
    val webPageUrl: String = ""
)

data class AppProxyItem(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false,
    val proxyEnabled: Boolean = true,
    val icon: Drawable? = null
)

@Immutable
data class ConnectionStats(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val durationSec: Long = 0,
    val speedUp: String = "0 B/s",
    val speedDown: String = "0 B/s"
)

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }
