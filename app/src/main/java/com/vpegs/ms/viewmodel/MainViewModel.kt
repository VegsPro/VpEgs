package com.vpegs.ms.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.VpnService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpegs.ms.BuildConfig
import com.vpegs.ms.core.AppUpdater
import com.vpegs.ms.data.*
import com.vpegs.ms.vpn.VpEgsVpnService
import com.vpegs.ms.ui.tile.VpEgsTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

enum class ServerSort(val title: String) {
    AS_IN_SUB("Как в подписке"),
    NAME_AZ("От А до Я"),
    NAME_ZA("От Я до А"),
    PING_ASC("От быстрых"),
    PING_DESC("От медленных");
}

class MainViewModel : ViewModel() {

    var connState by mutableStateOf(ConnState.DISCONNECTED)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var themeId by mutableStateOf(com.vpegs.ms.ui.theme.AppThemeId.DARK_BLUE)
        private set
    var pingSeconds by mutableStateOf(5)
        private set
    /** true if system VPN is up and it is our service */
    var systemVpnActive by mutableStateOf(false)
        private set
    var otherVpnActive by mutableStateOf(false)
        private set
    var publicIp by mutableStateOf<String?>(null)
        private set
    var ipLoading by mutableStateOf(false)
        private set
    var bgUpdateEnabled by mutableStateOf(false)
        private set
    var bgUpdateHours by mutableStateOf(6)
        private set
    var selectedServer by mutableStateOf<ServerNode?>(null)
        private set
    var stats by mutableStateOf(ConnectionStats())
        private set
    var isScanningBest by mutableStateOf(false)
        private set
    var bestScanStatus by mutableStateOf("")
        private set
    var bestScanProgress by mutableStateOf(0f)
        private set
    var workProgress by mutableStateOf(0f)
        private set
    var workStatus by mutableStateOf("")
        private set

    // In-app update (GitHub Releases)
    var updateAvailable by mutableStateOf(false)
        private set
    var updateDownloaded by mutableStateOf(false)
        private set
    var updateDownloading by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0f)
        private set
    var updateVersionName by mutableStateOf("")
        private set
    var updateVersionCode by mutableStateOf(0)
        private set
    var showUpdateBanner by mutableStateOf(false)
        private set
    var updateApkPath by mutableStateOf<String?>(null)
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
    var serverSort by mutableStateOf(ServerSort.AS_IN_SUB)
        private set
    var autoUpdateOnLaunch by mutableStateOf(true)
        private set
    var globalProxyMode by mutableStateOf(true)
        private set
    var invertAppProxy by mutableStateOf(false)
        private set
    var showServerProtocol by mutableStateOf(true)
        private set
    var showServerIp by mutableStateOf(true)
        private set
    var glassEffect by mutableStateOf(true)
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
            pingSeconds = Prefs.loadPingSeconds(ctx)
            bgUpdateEnabled = Prefs.loadBgUpdate(ctx)
            bgUpdateHours = Prefs.loadBgUpdateHours(ctx)
            showServerProtocol = Prefs.loadShowServerProtocol(ctx)
            showServerIp = Prefs.loadShowServerIp(ctx)
            glassEffect = Prefs.loadGlassEffect(ctx)
            com.vpegs.ms.worker.SubUpdateWorker.schedule(ctx, bgUpdateHours, bgUpdateEnabled)
            try {
                themeId = com.vpegs.ms.ui.theme.AppThemeId.valueOf(Prefs.loadThemeId(ctx))
            } catch (_: Exception) {
                themeId = com.vpegs.ms.ui.theme.AppThemeId.DARK_BLUE
            }
            val selId = Prefs.loadSelectedId(ctx)
            selectedServer = servers.find { it.id == selId } ?: servers.firstOrNull { it.isSelected }
            if (selectedServer != null) {
                servers = servers.map { it.copy(isSelected = it.id == selectedServer!!.id) }
            }
            if (VpEgsVpnService.isConnected) {
                connState = ConnState.CONNECTED
                startStats()
            }
            // Background: GitHub update check + download
            checkForAppUpdate(ctx.applicationContext)
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
            lastError = "Нет конфига — обнови подписку"
            connState = ConnState.DISCONNECTED
            return
        }
        lastError = null
        VpEgsVpnService.lastError = null
        VpEgsVpnService.isConnected = false
        connState = ConnState.CONNECTING
        val app = ctx.applicationContext
        val i = Intent(app, VpEgsVpnService::class.java).apply {
            action = VpEgsVpnService.ACTION_CONNECT
            putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${server.flag} ${server.name}")
            putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
        }
        try {
            app.startForegroundService(i)
        } catch (e: Exception) {
            lastError = "startService: ${e.message}"
            connState = ConnState.DISCONNECTED
            return
        }
        try {
            app.getSharedPreferences("vpegs_widget", Context.MODE_PRIVATE).edit()
                .putString("server_name", "${server.flag} ${server.name}")
                .putString("config_json", cfg)
                .apply()
        } catch (_: Exception) {}

        viewModelScope.launch {
            // Poll service state up to 20s
            repeat(40) {
                delay(500)
                if (VpEgsVpnService.isConnected) {
                    lastError = null
                    connState = ConnState.CONNECTED
                    startStats()
                    refreshPublicIp()
                    appContext?.let { VpEgsTileService.requestUpdate(it) }
                    return@launch
                }
                val err = VpEgsVpnService.lastError
                if (!err.isNullOrBlank()) {
                    lastError = err
                    connState = ConnState.DISCONNECTED
                    return@launch
                }
            }
            lastError = VpEgsVpnService.lastError ?: "Таймаут (сервис молчит)"
            connState = ConnState.DISCONNECTED
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
            refreshPublicIp()
            appContext?.let { VpEgsTileService.requestUpdate(it) }
        }
    }

    private fun startStats() {
        viewModelScope.launch {
            val baseRx0 = TrafficStats.getTotalRxBytes()
            val baseTx0 = TrafficStats.getTotalTxBytes()
            val baseRx: Long = if (baseRx0 < 0L) 0L else baseRx0
            val baseTx: Long = if (baseTx0 < 0L) 0L else baseTx0
            var prevRx = baseRx
            var prevTx = baseTx
            var sec = 0L
            while (connState == ConnState.CONNECTED) {
                delay(1000)
                sec++
                val rx0 = TrafficStats.getTotalRxBytes()
                val tx0 = TrafficStats.getTotalTxBytes()
                val rx: Long = if (rx0 < 0L) prevRx else rx0
                val tx: Long = if (tx0 < 0L) prevTx else tx0
                val dRx = (rx - prevRx).coerceAtLeast(0L)
                val dTx = (tx - prevTx).coerceAtLeast(0L)
                prevRx = rx
                prevTx = tx
                val totalDown = (rx - baseRx).coerceAtLeast(0L)
                val totalUp = (tx - baseTx).coerceAtLeast(0L)
                stats = ConnectionStats(totalUp, totalDown, sec, fmtSpeed(dTx), fmtSpeed(dRx))
            }
        }
    }

    /** TCP connect latency; null = unreachable */
    private suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 1800): Int? =
        withContext(Dispatchers.IO) {
            if (host.isBlank() || port <= 0) return@withContext null
            try {
                val t0 = System.nanoTime()
                Socket().use { s ->
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                }
                ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
            } catch (_: Exception) {
                null
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

    fun refreshPublicIp() {
        if (ipLoading) return
        ipLoading = true
        viewModelScope.launch {
            val viaVpn = VpEgsVpnService.isConnected
            publicIp = withContext(Dispatchers.IO) {
                try {
                    val clientBuilder = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    // App is excluded from VPN — when connected, force traffic via local SOCKS
                    if (viaVpn) {
                        clientBuilder.proxy(
                            java.net.Proxy(
                                java.net.Proxy.Type.SOCKS,
                                java.net.InetSocketAddress("127.0.0.1", 10808)
                            )
                        )
                    }
                    val client = clientBuilder.build()
                    val urls = listOf(
                        "https://api.ipify.org",
                        "https://ifconfig.me/ip",
                        "https://icanhazip.com"
                    )
                    for (u in urls) {
                        try {
                            val req = okhttp3.Request.Builder().url(u)
                                .header("User-Agent", "curl/8.0").build()
                            client.newCall(req).execute().use { r ->
                                if (r.isSuccessful) {
                                    val body = r.body?.string()?.trim()
                                    if (!body.isNullOrBlank() && body.length < 64) return@withContext body
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    null
                } catch (_: Exception) { null }
            }
            ipLoading = false
            // Cache measured exit IP on the active server
            val ip = publicIp
            val sid = selectedServer?.id
            if (!ip.isNullOrBlank() && sid != null && viaVpn) {
                servers = servers.map {
                    if (it.id == sid) it.copy(exitIp = ip) else it
                }
                selectedServer = selectedServer?.copy(exitIp = ip)
                persist()
            }
            appContext?.getSharedPreferences("vpegs_widget", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putString("last_ip", publicIp ?: "—")?.apply()
            try { com.vpegs.ms.ui.widget.VpEgsWidgetReceiver.updateAll(appContext!!) } catch (_: Exception) {}
        }
    }

    fun pingCurrent() {
        if (isPinging) return
        val s = selectedServer ?: return
        isPinging = true
        workStatus = "Пинг ${s.name}…"
        workProgress = 0.15f
        viewModelScope.launch {
            val samples = mutableListOf<Int>()
            val rounds = pingSeconds.coerceIn(1, 8)
            for (i in 1..rounds) {
                workStatus = "Пинг ${s.name} · замер $i/$rounds"
                workProgress = i.toFloat() / rounds
                val ms = tcpPing(s.address, s.port)
                if (ms != null) samples.add(ms)
                delay(80)
            }
            val avg = if (samples.isNotEmpty()) samples.average().toInt() else null
            lastPingMs = avg
            if (avg != null) {
                servers = servers.map { if (it.id == s.id) it.copy(latencyMs = avg) else it }
                selectedServer = selectedServer?.copy(latencyMs = avg)
            }
            workStatus = if (avg != null) "Пинг: $avg ms" else "Сервер недоступен"
            workProgress = if (avg != null) 1f else 0f
            isPinging = false
            delay(1200)
            if (!isScanningBest) { workStatus = ""; workProgress = 0f }
        }
    }

    fun refreshCurrentServer() {
        pingCurrent()
    }

    /**
     * Parallel TCP probe of all servers.
     * Stages shown in [bestScanStatus]. Only reachable servers compete;
     * winner = lowest RTT. Parallelism limited to 8 to avoid lag.
     */
    fun selectBestServer(activity: android.app.Activity? = null) {
        if (isScanningBest) return
        if (servers.isEmpty()) {
            lastError = "Нет серверов"
            return
        }
        isScanningBest = true
        bestScanProgress = 0f
        bestScanStatus = "1/4 Подготовка списка…"
        workStatus = bestScanStatus
        workProgress = 0f
        viewModelScope.launch {
            try {
                val list = servers.toList()
                val total = list.size.coerceAtLeast(1)
                bestScanStatus = "2/4 Проверка доступности ($total шт.)…"
                workStatus = bestScanStatus
                bestScanProgress = 0.1f
                workProgress = 0.1f

                val sem = Semaphore(8)
                val done = java.util.concurrent.atomic.AtomicInteger(0)
                val results = coroutineScope {
                    list.map { s ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val ms = tcpPing(s.address, s.port, 1600)
                                val n = done.incrementAndGet()
                                // UI updates throttled via Main
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    bestScanProgress = 0.1f + 0.7f * (n.toFloat() / total)
                                    workProgress = bestScanProgress
                                    bestScanStatus = "2/4 Проверка $n/$total · ${s.name.take(28)}"
                                    workStatus = bestScanStatus
                                }
                                s.id to ms
                            }
                        }
                    }.awaitAll()
                }

                bestScanStatus = "3/4 Отбор доступных…"
                workStatus = bestScanStatus
                bestScanProgress = 0.85f
                workProgress = 0.85f

                val reachable = results.mapNotNull { (id, ms) ->
                    if (ms != null) id to ms else null
                }.toMap()

                servers = servers.map { s ->
                    s.copy(latencyMs = reachable[s.id])
                }
                selectedServer = servers.find { it.id == selectedServer?.id }

                val best = servers
                    .filter { it.latencyMs != null }
                    .minByOrNull { it.latencyMs!! }

                bestScanStatus = "4/4 Применение…"
                workStatus = bestScanStatus
                bestScanProgress = 0.95f
                workProgress = 0.95f

                if (best == null) {
                    lastError = "Нет доступных серверов"
                    bestScanStatus = "Ни один сервер не ответил"
                    workStatus = bestScanStatus
                } else {
                    selectServer(best)
                    lastPingMs = best.latencyMs
                    lastError = null
                    bestScanStatus = "Лучший: ${best.name} · ${best.latencyMs} ms"
                    workStatus = bestScanStatus
                    if (connState == ConnState.CONNECTED || VpEgsVpnService.isConnected) {
                        val act = activity
                        if (act != null) {
                            disconnect(act)
                            delay(600)
                            prepareAndToggle(act)
                        } else {
                            appContext?.let { ctx ->
                                ctx.startService(Intent(ctx, VpEgsVpnService::class.java).apply {
                                    action = VpEgsVpnService.ACTION_DISCONNECT
                                })
                                delay(600)
                                val cfg = configByServerId[best.id]
                                if (!cfg.isNullOrBlank()) {
                                    ctx.startForegroundService(Intent(ctx, VpEgsVpnService::class.java).apply {
                                        action = VpEgsVpnService.ACTION_CONNECT
                                        putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${best.flag} ${best.name}")
                                        putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                                    })
                                }
                            }
                        }
                        delay(1200)
                        refreshPublicIp()
                    }
                }
                bestScanProgress = 1f
                workProgress = 1f
            } finally {
                isScanningBest = false
                delay(1800)
                bestScanStatus = ""
                bestScanProgress = 0f
                if (!isPinging) {
                    workStatus = ""
                    workProgress = 0f
                }
            }
        }
    }

    fun refreshAll(silent: Boolean = false) {
        viewModelScope.launch {
            val list = servers.toList()
            if (list.isEmpty()) return@launch
            if (!silent) {
                workStatus = "Проверка серверов…"
                workProgress = 0.05f
            }
            val sem = Semaphore(8)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val total = list.size
            val avgs = coroutineScope {
                list.map { s ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            val ms = tcpPing(s.address, s.port, 1500)
                            val n = done.incrementAndGet()
                            if (!silent) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    workProgress = n.toFloat() / total
                                    workStatus = "Пинг $n/$total · ${s.name.take(24)}"
                                }
                            }
                            s.id to ms
                        }
                    }
                }.awaitAll().toMap()
            }
            servers = servers.map { it.copy(latencyMs = avgs[it.id]) }
            selectedServer = servers.find { it.id == selectedServer?.id }
            lastPingMs = selectedServer?.latencyMs
            subscriptions = subscriptions.map {
                it.copy(
                    lastUpdated = System.currentTimeMillis(),
                    serversCount = servers.count { s -> s.subscriptionId == it.id }
                )
            }
            if (!silent) {
                workStatus = "Готово"
                workProgress = 1f
                delay(800)
                workStatus = ""
                workProgress = 0f
            }
        }
    }

    fun updateSearch(q: String) { searchQuery = q }
    fun toggleFavoritesFilter() { showOnlyFavorites = !showOnlyFavorites }
    fun updateServerSort(s: ServerSort) { serverSort = s }
    fun updateAutoUpdateOnLaunch(v: Boolean) { autoUpdateOnLaunch = v; persist() }
    fun updateGlobalProxyMode(v: Boolean) { globalProxyMode = v; persist() }
    fun updateInvertAppProxy(v: Boolean) {
        if (v == invertAppProxy) return
        invertAppProxy = v
        // Flip toggles visually: was VPN → Напрямую and vice versa
        appProxyList = appProxyList.map { it.copy(proxyEnabled = !it.proxyEnabled) }
        val bypass = appProxyList.filter { !it.proxyEnabled }.map { it.packageName }.toSet()
        appContext?.let {
            Prefs.saveBypassPackages(it, bypass)
            Prefs.saveSettings(it, globalProxyMode, invertAppProxy, autoUpdateOnLaunch)
        }
        persist()
    }
    fun updateShowServerProtocol(v: Boolean) {
        showServerProtocol = v
        appContext?.let { Prefs.saveServersUi(it, showServerProtocol, showServerIp) }
    }
    fun updateShowServerIp(v: Boolean) {
        showServerIp = v
        appContext?.let { Prefs.saveServersUi(it, showServerProtocol, showServerIp) }
    }
    fun updateGlassEffect(v: Boolean) {
        glassEffect = v
        appContext?.let { Prefs.saveGlassEffect(it, v) }
    }

    fun updateThemeId(id: com.vpegs.ms.ui.theme.AppThemeId) {
        themeId = id
        appContext?.let { Prefs.saveThemeId(it, id.name) }
    }
    fun updateBgUpdateEnabled(v: Boolean) {
        bgUpdateEnabled = v
        appContext?.let {
            Prefs.saveBgUpdate(it, bgUpdateEnabled, bgUpdateHours)
            com.vpegs.ms.worker.SubUpdateWorker.schedule(it, bgUpdateHours, bgUpdateEnabled)
        }
    }
    fun updateBgUpdateHours(h: Int) {
        bgUpdateHours = h.coerceIn(1, 24)
        appContext?.let {
            Prefs.saveBgUpdate(it, bgUpdateEnabled, bgUpdateHours)
            com.vpegs.ms.worker.SubUpdateWorker.schedule(it, bgUpdateHours, bgUpdateEnabled)
        }
    }
    fun updatePingSeconds(sec: Int) {
        pingSeconds = sec.coerceIn(1, 15)
        appContext?.let { Prefs.savePingSeconds(it, pingSeconds) }
    }

    fun refreshVpnPresence(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        var vpn = false
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            cm.allNetworks.forEach { n ->
                val caps = cm.getNetworkCapabilities(n)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true) {
                    vpn = true
                }
            }
        }
        systemVpnActive = vpn && VpEgsVpnService.isConnected
        otherVpnActive = vpn && !VpEgsVpnService.isConnected
        if (VpEgsVpnService.isConnected && connState != ConnState.CONNECTED) {
            connState = ConnState.CONNECTED
            startStats()
        } else if (!VpEgsVpnService.isConnected && connState == ConnState.CONNECTED) {
            connState = ConnState.DISCONNECTED
        }
    }

    fun addSubscription(name: String, url: String) {
        val subId = UUID.randomUUID().toString()
        val subName = name.ifBlank { "Подписка ${subscriptions.size + 1}" }
        val sub = Subscription(subId, subName, url, serversCount = 0, autoUpdate = true)
        subscriptions = subscriptions + sub
        // Fetch + parse in background
        viewModelScope.launch {
            try {
                workStatus = "Загрузка подписки…"
                workProgress = 0.2f
                val fr = withContext(Dispatchers.IO) { fetchSubscription(url) }
                workStatus = "Разбор серверов…"
                workProgress = 0.55f
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
                workStatus = "Подписка: ${nodes.size} серверов"
                workProgress = 1f
                delay(900)
                workStatus = ""
                workProgress = 0f
            } catch (e: Exception) {
                e.printStackTrace()
                workStatus = "Ошибка: ${e.message?.take(40)}"
                workProgress = 0f
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

    fun updateSubscription(id: String, name: String, url: String) {
        val old = subscriptions.find { it.id == id } ?: return
        val newUrl = url.ifBlank { old.url }
        val newName = name.ifBlank { old.name }
        // Full refresh: re-fetch body, replace all servers of this sub, traffic, expire, announce
        viewModelScope.launch {
            workStatus = "Обновление подписки…"
            workProgress = 0.15f
            try {
                val fr = withContext(Dispatchers.IO) { fetchSubscription(newUrl) }
                workStatus = "Обновление серверов…"
                workProgress = 0.5f
                val list = com.vpegs.ms.core.LinkParser
                    .parseSubscriptionBody(fr.body, id, newName)
                    .toMutableList()
                if (list.isEmpty()) {
                    com.vpegs.ms.core.LinkParser.parse(newUrl.trim(), id)?.let { list.add(it) }
                }
                // Drop old configs/servers for this subscription
                val oldIds = servers.filter { it.subscriptionId == id }.map { it.id }.toSet()
                oldIds.forEach { configByServerId.remove(it) }
                list.forEach { configByServerId[it.node.id] = it.configJson }
                val nodes = list.map { it.node.copy(subscriptionId = id) }
                val selectedWasThis = selectedServer?.subscriptionId == id
                val prevSelectedId = selectedServer?.id
                servers = servers.filter { it.subscriptionId != id } + nodes
                subscriptions = subscriptions.map {
                    if (it.id != id) it
                    else it.copy(
                        name = fr.title.ifBlank { newName },
                        url = newUrl,
                        serversCount = nodes.size,
                        lastUpdated = System.currentTimeMillis(),
                        uploadBytes = fr.upload,
                        downloadBytes = fr.download,
                        totalBytes = fr.total,
                        expireAt = fr.expire,
                        announce = fr.announce,
                        webPageUrl = fr.webPage,
                        updateIntervalHours = fr.updateInterval,
                        autoUpdate = old.autoUpdate
                    )
                }
                // Keep selection if still present, else first of new list
                val still = servers.find { it.id == prevSelectedId }
                when {
                    still != null -> selectServer(still)
                    selectedWasThis && nodes.isNotEmpty() -> selectServer(nodes.first())
                    else -> persist()
                }
                workStatus = "Подписка обновлена · ${nodes.size} серверов"
                workProgress = 1f
                delay(1000)
                workStatus = ""
                workProgress = 0f
            } catch (e: Exception) {
                e.printStackTrace()
                // Still update name/url locally
                subscriptions = subscriptions.map {
                    if (it.id == id) it.copy(name = newName, url = newUrl) else it
                }
                persist()
                workStatus = "Ошибка обновления: ${e.message?.take(40)}"
                workProgress = 0f
            }
        }
    }

    fun refreshSubscription(id: String) {
        val sub = subscriptions.find { it.id == id } ?: return
        updateSubscription(id, sub.name, sub.url)
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
                .let { nodes ->
                    when (serverSort) {
                        ServerSort.AS_IN_SUB -> nodes
                        ServerSort.NAME_AZ -> nodes.sortedBy { it.name.lowercase() }
                        ServerSort.NAME_ZA -> nodes.sortedByDescending { it.name.lowercase() }
                        ServerSort.PING_ASC -> nodes.sortedBy { it.latencyMs ?: 99999 }
                        ServerSort.PING_DESC -> nodes.sortedByDescending { it.latencyMs ?: -1 }
                    }
                }
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
        // proxyEnabled=false → bypass VPN
        val bypass = appProxyList.filter { !it.proxyEnabled }.map { it.packageName }.toSet()
        val ctx = appContext
        if (ctx != null) {
            Prefs.saveBypassPackages(ctx, bypass)
            Prefs.saveSettings(ctx, globalProxyMode, invertAppProxy, autoUpdateOnLaunch)
        }
        // Auto-reconnect so Builder picks new disallow list
        if (connState == ConnState.CONNECTED || VpEgsVpnService.isConnected) {
            lastError = "Обход: ${bypass.size} прилож. · переподключение…"
            viewModelScope.launch {
                val c = appContext ?: return@launch
                c.startService(Intent(c, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_DISCONNECT
                })
                delay(800)
                val server = selectedServer ?: return@launch
                val cfg = configByServerId[server.id] ?: return@launch
                c.startForegroundService(Intent(c, VpEgsVpnService::class.java).apply {
                    action = VpEgsVpnService.ACTION_CONNECT
                    putExtra(VpEgsVpnService.EXTRA_SERVER_NAME, "${server.flag} ${server.name}")
                    putExtra(VpEgsVpnService.EXTRA_CONFIG_JSON, cfg)
                })
                delay(2000)
                lastError = null
                refreshPublicIp()
            }
        }
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
        if (appsLoading) return
        appsLoading = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    val bypass = Prefs.loadBypassPackages(context)
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
                                // false = bypass VPN (direct)
                                proxyEnabled = ai.packageName !in bypass,
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

    fun checkForAppUpdate(ctx: Context) {
        viewModelScope.launch {
            // Drop APKs for versions we already run
            AppUpdater.deleteCached(ctx, BuildConfig.VERSION_CODE)
            val remote = withContext(Dispatchers.IO) { AppUpdater.fetchLatest() } ?: return@launch
            // Only if strictly newer than installed APK
            if (!AppUpdater.isNewer(remote)) {
                updateAvailable = false
                showUpdateBanner = false
                updateDownloaded = false
                updateApkPath = null
                workStatus = "Установлена актуальная версия"
                kotlinx.coroutines.delay(1500)
                workStatus = ""
                return@launch
            }
            updateVersionCode = remote.versionCode
            updateVersionName = remote.versionName
            updateAvailable = true

            if (AppUpdater.isDismissed(ctx, remote.versionCode) ||
                !AppUpdater.canOfferInstall(ctx, remote.versionCode)
            ) {
                showUpdateBanner = false
                // Settings button only if apk already cached
                if (AppUpdater.hasCachedApk(ctx, remote.versionCode)) {
                    updateDownloaded = true
                    updateApkPath = AppUpdater.apkFile(ctx, remote.versionCode).absolutePath
                }
                return@launch
            }

            showUpdateBanner = true
            // Reuse cache — do not download the same version again
            if (AppUpdater.hasCachedApk(ctx, remote.versionCode)) {
                updateDownloaded = true
                updateDownloading = false
                updateProgress = 1f
                updateApkPath = AppUpdater.apkFile(ctx, remote.versionCode).absolutePath
                return@launch
            }
            updateDownloading = true
            updateProgress = 0f
            val file = AppUpdater.download(ctx, remote) { p -> updateProgress = p }
            updateDownloading = false
            if (file != null) {
                updateDownloaded = true
                updateApkPath = file.absolutePath
                updateProgress = 1f
            }
        }
    }

    fun dismissUpdateBanner(ctx: Context) {
        showUpdateBanner = false
        if (updateVersionCode > 0) AppUpdater.dismiss(ctx, updateVersionCode)
    }

    fun installUpdate(activity: android.app.Activity) {
        val path = updateApkPath ?: return
        val f = java.io.File(path)
        if (!f.exists()) return
        val ok = AppUpdater.install(activity, f)
        if (!ok) {
            lastError = "Не удалось открыть установщик"
        }
    }

    fun declineUpdateInstall(ctx: Context) {
        if (updateVersionCode > 0) AppUpdater.recordDecline(ctx, updateVersionCode)
        if (AppUpdater.isDismissed(ctx, updateVersionCode)) {
            showUpdateBanner = false
            updateAvailable = false
        }
    }

    fun clearUpdateCacheAfterInstall(ctx: Context) {
        AppUpdater.deleteCached(ctx, BuildConfig.VERSION_CODE)
        updateAvailable = false
        updateDownloaded = false
        updateApkPath = null
        showUpdateBanner = false
    }

}
