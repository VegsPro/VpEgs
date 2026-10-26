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
    var lastError by mutableStateOf<String?>(null)
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

    /** serverId -> full Xray config JSON */
    private val configByServerId = mutableMapOf<String, String>()


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

    private var appContext: Context? = null
    private var loaded = false

    /** Call once from UI with applicationContext */
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        appContext = ctx.applicationContext
        try {
            subscriptions = Prefs.loadSubscriptions(ctx)
            servers = Prefs.loadServers(ctx)
            configByServerId.clear()
            configByServerId.putAll(Prefs.loadConfigs(ctx))
            globalProxyMode = Prefs.loadGlobalProxy(ctx)
            invertAppProxy = Prefs.loadInvertProxy(ctx)
            autoUpdateOnLaunch = Prefs.loadAutoUpdate(ctx)
            val selId = Prefs.loadSelectedId(ctx)
            selectedServer = servers.find { it.id == selId } ?: servers.firstOrNull { it.isSelected }
            if (selectedServer != null) {
                servers = servers.map { it.copy(isSelected = it.id == selectedServer!!.id) }
            }
            if (VpEgsVpnService.isConnected) {
                connState = ConnState.CONNECTED
                startStats()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persist() {
        val ctx = appContext ?: return
        try {
            Prefs.saveSubscriptions(ctx, subscriptions)
            Prefs.saveServers(ctx, servers)
            Prefs.saveConfigs(ctx, configByServerId.toMap())
            Prefs.saveSelectedId(ctx, selectedServer?.id)
            Prefs.saveSettings(ctx, globalProxyMode, invertAppProxy, autoUpdateOnLaunch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val cfg = configByServerId[server.id]
        if (cfg.isNullOrBlank()) {
            lastError = "Нет конфига у сервера — обнови подписку"
            connState = ConnState.DISCONNECTED
            return
        }
        lastError = null
        connState = ConnState.CONNECTING
        val app = ctx.applicationContext
        val filter = android.content.IntentFilter(VpEgsVpnService.ACTION_STATE_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val connected = intent?.getBooleanExtra("connected", false) == true
                val err = intent?.getStringExtra("error")
                try { app.unregisterReceiver(this) } catch (_: Exception) {}
                if (!err.isNullOrBlank()) lastError = err
                if (connected) {
                    connState = ConnState.CONNECTED
                    startStats()
                } else {
                    connState = ConnState.DISCONNECTED
                    if (lastError == null) lastError = "VPN не поднялся"
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        val i = Intent(app, VpEgsVpnService::class.java).apply {
            action = VpEgsVpnService.ACTION_CONNECT
            putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${server.flag} ${server.name}")
            putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
        }
        app.startForegroundService(i)
        try {
            app.getSharedPreferences("vpegs_widget", Context.MODE_PRIVATE).edit()
                .putString("server_name", "${server.flag} ${server.name}")
                .putString("config_json", cfg)
                .apply()
        } catch (_: Exception) {}
        // timeout
        viewModelScope.launch {
            delay(15_000)
            if (connState == ConnState.CONNECTING) {
                try { app.unregisterReceiver(receiver) } catch (_: Exception) {}
                lastError = "Таймаут подключения"
                connState = ConnState.DISCONNECTED
            }
        }
    }

    private fun disconnect(ctx: Context) {
        connState = ConnState.DISCONNECTING
        lastError = null
        viewModelScope.launch {
            ctx.startService(Intent(ctx, VpEgsVpnService::class.java).apply {
                action = VpEgsVpnService.ACTION_DISCONNECT
            })
            delay(500)
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
        persist()
    }

    fun toggleFavorite(id: String) {
        servers = servers.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        if (selectedServer?.id == id) selectedServer = selectedServer?.copy(isFavorite = !selectedServer!!.isFavorite)
        persist()
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
    fun updateAutoUpdateOnLaunch(v: Boolean) { autoUpdateOnLaunch = v; persist() }
    fun updateGlobalProxyMode(v: Boolean) { globalProxyMode = v; persist() }
    fun updateInvertAppProxy(v: Boolean) { invertAppProxy = v; persist() }

    fun addSubscription(name: String, url: String) {
        val subId = UUID.randomUUID().toString()
        val subName = name.ifBlank { "Подписка ${subscriptions.size + 1}" }
        val sub = Subscription(subId, subName, url, serversCount = 0, autoUpdate = true)
        subscriptions = subscriptions + sub
        // Fetch + parse in background
        viewModelScope.launch {
            try {
                val fr = withContext(Dispatchers.IO) { fetchSubscription(url) }
                val body = fr.body
                val list = com.vpegs.ms.core.LinkParser.parseSubscriptionBody(body, subId, subName).toMutableList()
                if (list.isEmpty()) {
                    com.vpegs.ms.core.LinkParser.parse(url.trim(), subId)?.let { list.add(it) }
                }
                list.forEach { configByServerId[it.node.id] = it.configJson }
                val nodes = list.map { it.node }
                servers = servers + nodes
                subscriptions = subscriptions.map {
                    if (it.id == subId) it.copy(
                        name = if (fr.title.isNotBlank()) fr.title else it.name,
                        serversCount = nodes.size,
                        lastUpdated = System.currentTimeMillis(),
                        uploadBytes = fr.upload,
                        downloadBytes = fr.download,
                        totalBytes = fr.total,
                        expireAt = fr.expire,
                        announce = fr.announce,
                        webPageUrl = fr.webPage,
                        updateIntervalHours = fr.updateInterval
                    ) else it
                }
                if (selectedServer == null && nodes.isNotEmpty()) {
                    selectServer(nodes.first())
                } else {
                    persist()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class FetchResult(
        val body: String,
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expire: Long = 0,
        val title: String = "",
        val announce: String = "",
        val webPage: String = "",
        val updateInterval: Int = 6
    )

    private fun fetchSubscription(url: String): FetchResult {
        if (url.startsWith("vless://") || url.startsWith("vmess://") || url.startsWith("trojan://") || url.startsWith("ss://")) {
            return FetchResult(body = url)
        }
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "v2rayNG/1.8.19").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: ""
            // subscription-userinfo: upload=; download=; total=; expire=
            var up = 0L; var down = 0L; var total = 0L; var exp = 0L
            val ui = resp.header("subscription-userinfo") ?: resp.header("Subscription-Userinfo")
            if (ui != null) {
                ui.split(";").forEach { part ->
                    val kv = part.trim().split("=")
                    if (kv.size == 2) {
                        val v = kv[1].trim().toLongOrNull() ?: 0L
                        when (kv[0].trim().lowercase()) {
                            "upload" -> up = v
                            "download" -> down = v
                            "total" -> total = v
                            "expire" -> exp = v
                        }
                    }
                }
            }
            fun hdr(name: String) = resp.header(name) ?: resp.header(name.lowercase()) ?: ""
            val titleB64 = hdr("profile-title").removePrefix("base64:")
            val title = try {
                if (titleB64.isNotBlank()) String(android.util.Base64.decode(titleB64, android.util.Base64.DEFAULT))
                else ""
            } catch (_: Exception) { "" }
            val announceB64 = hdr("announce").removePrefix("base64:")
            val announce = try {
                if (announceB64.isNotBlank() && hdr("announce").startsWith("base64:"))
                    String(android.util.Base64.decode(announceB64, android.util.Base64.DEFAULT))
                else hdr("announce")
            } catch (_: Exception) { hdr("announce") }
            val interval = hdr("profile-update-interval").toIntOrNull() ?: 6
            return FetchResult(
                body = body, upload = up, download = down, total = total, expire = exp,
                title = title, announce = announce, webPage = hdr("profile-web-page-url"),
                updateInterval = interval
            )
        }
    }

    fun removeSubscription(id: String) {
        val toRemove = servers.filter { it.subscriptionId == id }.map { it.id }
        toRemove.forEach { configByServerId.remove(it) }
        subscriptions = subscriptions.filter { it.id != id }
        servers = servers.filter { it.subscriptionId != id }
        if (selectedServer?.subscriptionId == id) {
            selectedServer = servers.firstOrNull()
            servers = servers.map { it.copy(isSelected = it.id == selectedServer?.id) }
        }
        persist()
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
        persist()
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
