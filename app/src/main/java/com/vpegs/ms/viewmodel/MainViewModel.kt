package com.vpegs.ms.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpegs.ms.data.*
import com.vpegs.ms.vpn.VpEgsVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var subscriptions by mutableStateOf<List<Subscription>>(emptyList())
        private set

    var servers by mutableStateOf<List<ServerNode>>(emptyList())
        private set

    var appProxyList by mutableStateOf<List<AppProxyItem>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    var appSearch by mutableStateOf("")
        private set

    val filteredApps: List<AppProxyItem>
        get() {
            if (appSearch.isBlank()) return appProxyList
            val q = appSearch.lowercase()
            return appProxyList.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

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
        selectedServer = null
        // no demo servers — list fills only from user subscriptions
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
            // Sync widget prefs
            try {
                ctx.getSharedPreferences("vpegs_widget", Context.MODE_PRIVATE).edit()
                    .putString("server_name", "${server.flag} ${server.name}")
                    .putInt("last_ping", server.latencyMs ?: 0)
                    .apply()
            } catch (_: Exception) {}
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
        val subId = UUID.randomUUID().toString()
        val subName = name.ifBlank { "Подписка ${subscriptions.size + 1}" }
        // Placeholder nodes until real subscription fetch is wired to Xray core.
        // Count is illustrative; replace with parsed nodes from URL response.
        val count = (3..8).random()
        val sub = Subscription(subId, subName, url, serversCount = count, autoUpdate = true)
        subscriptions = subscriptions + sub
        val flags = listOf("🇩🇪" to "Германия", "🇳🇱" to "Нидерланды", "🇫🇮" to "Финляндия", "🇫🇷" to "Франция", "🇺🇸" to "США", "🇬🇧" to "UK", "🇵🇱" to "Польша", "🇸🇬" to "Сингапур")
        val extra = (1..count).map { i ->
            val (flag, country) = flags.random()
            ServerNode(
                id = UUID.randomUUID().toString(),
                name = "$country · $subName",
                country = "$flag $country",
                flag = flag,
                protocol = Protocol.entries.random(),
                latencyMs = null,
                subscriptionId = subId
            )
        }
        servers = servers + extra
    }

    fun removeSubscription(id: String) {
        subscriptions = subscriptions.filter { it.id != id }
        servers = servers.filter { it.subscriptionId != id }
        if (selectedServer?.subscriptionId == id) {
            selectedServer = servers.firstOrNull()
            servers = servers.map { it.copy(isSelected = it.id == selectedServer?.id) }
        }
    }

    /** Servers grouped by subscription for HAPP-like UI */
    fun serversBySubscription(): List<Pair<Subscription, List<ServerNode>>> {
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
        return subscriptions.mapNotNull { sub ->
            val nodes = list.filter { it.subscriptionId == sub.id }
                .sortedBy { it.latencyMs ?: 9999 }
            if (nodes.isEmpty() && searchQuery.isNotBlank()) null
            else sub to nodes
        }.filter { (_, nodes) -> nodes.isNotEmpty() || searchQuery.isBlank() }
    }
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


    fun updateAppSearch(q: String) { appSearch = q }

    fun loadInstalledApps(context: Context) {
        if (appProxyList.isNotEmpty() || appsLoading) return
        appsLoading = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    val enabledMap = appProxyList.associate { it.packageName to it.proxyEnabled }
                    apps
                        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                        .map { ai ->
                            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val name = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { ai.packageName }
                            val icon = try { pm.getApplicationIcon(ai) } catch (_: Exception) { null }
                            AppProxyItem(
                                packageName = ai.packageName,
                                appName = name,
                                isSystem = isSystem,
                                proxyEnabled = enabledMap[ai.packageName] ?: true,
                                icon = icon
                            )
                        }
                        .sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))
                }
                appProxyList = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                appsLoading = false
            }
        }
    }

    companion object { const val REQ_VPN = 1001 }
}
