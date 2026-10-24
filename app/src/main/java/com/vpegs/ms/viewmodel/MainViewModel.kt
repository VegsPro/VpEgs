package com.vpegs.ms.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpegs.ms.data.*
import com.vpegs.ms.vpn.VpEgsVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel : ViewModel() {

    var connState by mutableStateOf(ConnState.DISCONNECTED)
        private set
    var selectedServer by mutableStateOf<ServerNode?>(null)
        private set
    var stats by mutableStateOf(ConnectionStats())
        private set
    var isScanningBest by mutableStateOf(false)
        private set
    var isPinging by mutableStateOf(false)
        private set
    var lastPingMs by mutableStateOf<Int?>(null)
        private set

    var subscriptions by mutableStateOf(
        listOf(
            Subscription("s1", "Основная", "https://sub.example.com/vpegs", serversCount = 12, autoUpdate = true),
            Subscription("s2", "Hysteria резерв", "https://hy2.example.com/sub", serversCount = 6, autoUpdate = true)
        )
    )
        private set

    var servers by mutableStateOf(sampleServers())
        private set

    var appProxyList by mutableStateOf(sampleApps())
        private set

    var searchQuery by mutableStateOf("")
        private set
    var showOnlyFavorites by mutableStateOf(false)
        private set
    var autoUpdateOnLaunch by mutableStateOf(true)
        private set
    var globalProxyMode by mutableStateOf(true)
        private set
    var invertAppProxy by mutableStateOf(false)
        private set

    val filteredServers: List<ServerNode>
        get() {
            var list = servers
            if (showOnlyFavorites) list = list.filter { it.isFavorite }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase()
                list = list.filter {
                    it.name.lowercase().contains(q) ||
                    it.country.lowercase().contains(q) ||
                    it.protocol.display.lowercase().contains(q)
                }
            }
            return list.sortedBy { it.latencyMs ?: 9999 }
        }

    init {
        selectedServer = servers.firstOrNull { it.isSelected } ?: servers.minByOrNull { it.latencyMs ?: 9999 }
        if (autoUpdateOnLaunch) viewModelScope.launch { delay(600); refreshAll(true) }
    }

    // ——— VPN control ———
    fun prepareAndToggle(activity: Activity) {
        if (connState == ConnState.CONNECTED || connState == ConnState.CONNECTING) {
            disconnect(activity)
            return
        }
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, REQ_VPN)
        } else {
            connect(activity)
        }
    }

    fun onVpnPermissionResult(activity: Activity, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) connect(activity)
    }

    private fun connect(ctx: Context) {
        val server = selectedServer ?: return
        connState = ConnState.CONNECTING
        viewModelScope.launch {
            delay(900)
            val i = Intent(ctx, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_CONNECT
                putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${server.flag} ${server.name}")
            }
            ctx.startForegroundService(i)
            connState = ConnState.CONNECTED
            startStats()
        }
    }

    private fun disconnect(ctx: Context) {
        connState = ConnState.DISCONNECTING
        viewModelScope.launch {
            delay(400)
            ctx.startService(Intent(ctx, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            connState = ConnState.DISCONNECTED
            stats = ConnectionStats()
        }
    }

    private fun startStats() {
        viewModelScope.launch {
            var up = 0L; var down = 0L; var sec = 0L
            while (connState == ConnState.CONNECTED) {
                delay(1000); sec++
                val u = (60_000..380_000).random().toLong()
                val d = (180_000..2_200_000).random().toLong()
                up += u; down += d
                stats = ConnectionStats(up, down, sec, fmtSpeed(u), fmtSpeed(d))
            }
        }
    }

    // ——— Server actions ———
    fun selectServer(s: ServerNode) {
        servers = servers.map { it.copy(isSelected = it.id == s.id) }
        selectedServer = s.copy(isSelected = true)
    }

    fun toggleFavorite(id: String) {
        servers = servers.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        if (selectedServer?.id == id) selectedServer = selectedServer?.copy(isFavorite = !selectedServer!!.isFavorite)
    }

    fun pingCurrent() {
        if (isPinging) return
        isPinging = true
        viewModelScope.launch {
            delay((200..700).random().toLong())
            val ms = (18..160).random()
            lastPingMs = ms
            selectedServer?.let { s ->
                servers = servers.map { if (it.id == s.id) it.copy(latencyMs = ms) else it }
                selectedServer = selectedServer?.copy(latencyMs = ms)
            }
            isPinging = false
        }
    }

    fun refreshCurrentServer() {
        viewModelScope.launch {
            delay(500)
            servers = servers.map {
                it.copy(latencyMs = (15..200).random())
            }
            selectedServer = servers.find { it.id == selectedServer?.id }
            lastPingMs = selectedServer?.latencyMs
        }
    }

    /** Scan all servers, pick lowest latency (first to answer simulation) */
    fun selectBestServer() {
        if (isScanningBest) return
        isScanningBest = true
        viewModelScope.launch {
            // Simulate parallel probe
            delay(1200)
            servers = servers.map { it.copy(latencyMs = (12..180).random()) }
            val best = servers.minByOrNull { it.latencyMs ?: 9999 }
            if (best != null) {
                selectServer(best)
                lastPingMs = best.latencyMs
            }
            isScanningBest = false
        }
    }

    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) delay(500)
            subscriptions = subscriptions.map {
                it.copy(lastUpdated = System.currentTimeMillis(), serversCount = (it.serversCount + (-1..2).random()).coerceAtLeast(1))
            }
            servers = servers.map { it.copy(latencyMs = (15..210).random()) }
            selectedServer = servers.find { it.id == selectedServer?.id } ?: servers.minByOrNull { it.latencyMs ?: 9999 }
        }
    }

    fun updateSearch(q: String) { searchQuery = q }
    fun toggleFavoritesFilter() { showOnlyFavorites = !showOnlyFavorites }
    fun updateAutoUpdateOnLaunch(v: Boolean) { autoUpdateOnLaunch = v }
    fun updateGlobalProxyMode(v: Boolean) { globalProxyMode = v }
    fun updateInvertAppProxy(v: Boolean) { invertAppProxy = v }

    fun addSubscription(name: String, url: String) {
        val sub = Subscription(UUID.randomUUID().toString(), name.ifBlank { "Подписка ${subscriptions.size + 1}" }, url, serversCount = (4..14).random())
        subscriptions = subscriptions + sub
        val extra = (1..sub.serversCount).map { i ->
            ServerNode(
                UUID.randomUUID().toString(),
                "${sub.name} #$i",
                listOf("🇩🇪 Германия", "🇳🇱 Нидерланды", "🇫🇮 Финляндия", "🇫🇷 Франция", "🇺🇸 США").random(),
                listOf("🇩🇪", "🇳🇱", "🇫🇮", "🇫🇷", "🇺🇸").random(),
                Protocol.entries.random(),
                (20..150).random()
            )
        }
        servers = servers + extra
    }

    fun removeSubscription(id: String) { subscriptions = subscriptions.filter { it.id != id } }
    fun toggleSubAutoUpdate(id: String) {
        subscriptions = subscriptions.map { if (it.id == id) it.copy(autoUpdate = !it.autoUpdate) else it }
    }
    fun toggleAppProxy(pkg: String) {
        appProxyList = appProxyList.map { if (it.packageName == pkg) it.copy(proxyEnabled = !it.proxyEnabled) else it }
    }

    fun fmtBytes(b: Long) = when {
        b >= 1_073_741_824 -> "%.2f GB".format(b / 1_073_741_824.0)
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }
    fun fmtDuration(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
    private fun fmtSpeed(bps: Long) = when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.0f KB/s".format(bps / 1_000.0)
        else -> "$bps B/s"
    }

    private fun sampleServers() = listOf(
        ServerNode("1", "Paris Fast", "🇫🇷 Франция", "🇫🇷", Protocol.VLESS_REALITY, 37, true, true),
        ServerNode("2", "Frankfurt", "🇩🇪 Германия", "🇩🇪", Protocol.HYSTERIA2, 28, true),
        ServerNode("3", "Amsterdam", "🇳🇱 Нидерланды", "🇳🇱", Protocol.VLESS, 41),
        ServerNode("4", "Helsinki", "🇫🇮 Финляндия", "🇫🇮", Protocol.TUIC, 52),
        ServerNode("5", "London", "🇬🇧 UK", "🇬🇧", Protocol.TROJAN, 48),
        ServerNode("6", "New York", "🇺🇸 США", "🇺🇸", Protocol.VMESS, 95),
        ServerNode("7", "Singapore", "🇸🇬 Сингапур", "🇸🇬", Protocol.SHADOWSOCKS, 118),
        ServerNode("8", "Tokyo", "🇯🇵 Япония", "🇯🇵", Protocol.VLESS_REALITY, 132),
        ServerNode("9", "Warsaw", "🇵🇱 Польша", "🇵🇱", Protocol.HYSTERIA2, 39),
        ServerNode("10", "Stockholm", "🇸🇪 Швеция", "🇸🇪", Protocol.VLESS, 44),
        ServerNode("11", "Istanbul", "🇹🇷 Турция", "🇹🇷", Protocol.TROJAN, 61),
        ServerNode("12", "Dubai", "🇦🇪 ОАЭ", "🇦🇪", Protocol.VLESS_REALITY, 78)
    )

    private fun sampleApps() = listOf(
        AppProxyItem("com.android.chrome", "Chrome", false, true),
        AppProxyItem("org.telegram.messenger", "Telegram", false, true),
        AppProxyItem("com.whatsapp", "WhatsApp", false, false),
        AppProxyItem("com.instagram.android", "Instagram", false, true),
        AppProxyItem("com.google.android.youtube", "YouTube", false, true),
        AppProxyItem("ru.yandex.browser", "Яндекс", false, false),
        AppProxyItem("com.vkontakte.android", "ВКонтакте", false, false),
        AppProxyItem("ru.sberbankmobile", "Сбербанк", false, false),
        AppProxyItem("com.spotify.music", "Spotify", false, true),
        AppProxyItem("com.discord", "Discord", false, true)
    )

    companion object { const val REQ_VPN = 1001 }
}
