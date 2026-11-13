package com.vpegs.ms.data

import android.content.Context
import java.util.HashSet
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for subscriptions, servers, Xray configs and settings.
 */
object Prefs {
    private const val NAME = "vpegs_data"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun saveSubscriptions(ctx: Context, list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("url", s.url)
                .put("lastUpdated", s.lastUpdated)
                .put("autoUpdate", s.autoUpdate)
                .put("updateIntervalHours", s.updateIntervalHours)
                .put("serversCount", s.serversCount)
                .put("uploadBytes", s.uploadBytes)
                .put("downloadBytes", s.downloadBytes)
                .put("totalBytes", s.totalBytes)
                .put("expireAt", s.expireAt)
                .put("announce", s.announce)
                .put("supportUrl", s.supportUrl)
                .put("webPageUrl", s.webPageUrl)
            )
        }
        sp(ctx).edit().putString("subscriptions", arr.toString()).apply()
    }

    fun loadSubscriptions(ctx: Context): List<Subscription> {
        val raw = sp(ctx).getString("subscriptions", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Subscription(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    lastUpdated = o.optLong("lastUpdated", System.currentTimeMillis()),
                    autoUpdate = o.optBoolean("autoUpdate", true),
                    updateIntervalHours = o.optInt("updateIntervalHours", 6),
                    serversCount = o.optInt("serversCount", 0),
                    uploadBytes = o.optLong("uploadBytes", 0),
                    downloadBytes = o.optLong("downloadBytes", 0),
                    totalBytes = o.optLong("totalBytes", 0),
                    expireAt = o.optLong("expireAt", 0),
                    announce = o.optString("announce", ""),
                    supportUrl = o.optString("supportUrl", ""),
                    webPageUrl = o.optString("webPageUrl", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveServers(ctx: Context, list: List<ServerNode>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject()
                .put("id", s.id)
                .put("name", s.name)
                .put("country", s.country)
                .put("flag", s.flag)
                .put("protocol", s.protocol.name)
                .put("latencyMs", s.latencyMs ?: -1)
                .put("isFavorite", s.isFavorite)
                .put("isSelected", s.isSelected)
                .put("address", s.address).put("exitIp", s.exitIp)
                .put("port", s.port)
                .put("rawConfig", s.rawConfig)
                .put("subscriptionId", s.subscriptionId)
            )
        }
        sp(ctx).edit().putString("servers", arr.toString()).apply()
    }

    fun loadServers(ctx: Context): List<ServerNode> {
        val raw = sp(ctx).getString("servers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    val proto = try {
                        Protocol.valueOf(o.optString("protocol", "VLESS"))
                    } catch (_: Exception) { Protocol.VLESS }
                    val lat = o.optInt("latencyMs", -1)
                    ServerNode(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        country = o.optString("country"),
                        flag = o.optString("flag", "🌐"),
                        protocol = proto,
                        latencyMs = if (!o.has("latencyMs")) null else if (lat < 0) -1 else lat,
                        isFavorite = o.optBoolean("isFavorite", false),
                        isSelected = o.optBoolean("isSelected", false),
                        address = o.optString("address"),
                        exitIp = o.optString("exitIp"),
                        port = o.optInt("port", 443),
                        rawConfig = o.optString("rawConfig"),
                        subscriptionId = o.optString("subscriptionId")
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveConfigs(ctx: Context, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        sp(ctx).edit().putString("configs", obj.toString()).apply()
    }

    fun loadConfigs(ctx: Context): Map<String, String> {
        val raw = sp(ctx).getString("configs", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveSelectedId(ctx: Context, id: String?) {
        sp(ctx).edit().putString("selected_id", id).apply()
    }

    fun loadSelectedId(ctx: Context): String? =
        sp(ctx).getString("selected_id", null)

    fun saveSettings(
        ctx: Context,
        globalProxy: Boolean,
        invertProxy: Boolean,
        autoUpdate: Boolean
    ) {
        sp(ctx).edit()
            .putBoolean("global_proxy", globalProxy)
            .putBoolean("invert_proxy", invertProxy)
            .putBoolean("auto_update", autoUpdate)
            .apply()
    }

    fun loadGlobalProxy(ctx: Context) = sp(ctx).getBoolean("global_proxy", true)
    fun loadInvertProxy(ctx: Context) = sp(ctx).getBoolean("invert_proxy", false)
    fun loadAutoUpdate(ctx: Context) = sp(ctx).getBoolean("auto_update", true)

    fun saveThemeId(ctx: Context, id: String) {
        sp(ctx).edit().putString("theme_id", id).apply()
    }
    fun loadThemeId(ctx: Context): String =
        sp(ctx).getString("theme_id", "DARK_BLUE") ?: "DARK_BLUE"

    fun savePingSeconds(ctx: Context, sec: Int) {
        sp(ctx).edit().putInt("ping_seconds", sec.coerceIn(1, 15)).apply()
    }
    fun loadPingSeconds(ctx: Context): Int =
        sp(ctx).getInt("ping_seconds", 5).coerceIn(1, 15)

    fun saveBypassPackages(ctx: Context, pkgs: Set<String>) {
        val copy = HashSet(pkgs)
        sp(ctx).edit()
            .putStringSet("bypass_pkgs", copy)
            .putString("bypass_pkgs_str", copy.sorted().joinToString("\n"))
            .apply()
    }
    fun saveDomainBypass(ctx: Context, domains: List<String>) {
        sp(ctx).edit().putString("domain_bypass", domains.joinToString("\n")).apply()
    }
    fun loadDomainBypass(ctx: Context): List<String> {
        val s = sp(ctx).getString("domain_bypass", "") ?: ""
        return s.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun saveAdBlockPackages(ctx: Context, pkgs: Set<String>) {
        sp(ctx).edit()
            .putStringSet("adblock_pkgs", pkgs)
            .putString("adblock_pkgs_str", pkgs.joinToString("\n"))
            .apply()
    }
    fun loadAdBlockPackages(ctx: Context): Set<String> {
        val set = sp(ctx).getStringSet("adblock_pkgs", null)
        if (!set.isNullOrEmpty()) return set
        val str = sp(ctx).getString("adblock_pkgs_str", null).orEmpty()
        if (str.isBlank()) return emptySet()
        return str.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun loadBypassPackages(ctx: Context): Set<String> {
        val fromSet = sp(ctx).getStringSet("bypass_pkgs", null)?.let { HashSet(it) } ?: HashSet()
        val fromStr = sp(ctx).getString("bypass_pkgs_str", null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        return if (fromSet.isNotEmpty()) fromSet else HashSet(fromStr)
    }

    fun saveBgUpdate(ctx: Context, enabled: Boolean, hours: Int) {
        sp(ctx).edit().putBoolean("bg_update", enabled).putInt("bg_update_hours", hours.coerceIn(1, 48)).apply()
    }
    fun loadBgUpdate(ctx: Context) = sp(ctx).getBoolean("bg_update", false)
    fun loadBgUpdateHours(ctx: Context) = sp(ctx).getInt("bg_update_hours", 6).coerceIn(1, 48)

    fun saveServersUi(ctx: Context, showProtocol: Boolean, showIp: Boolean) {
        sp(ctx).edit()
            .putBoolean("show_server_protocol", showProtocol)
            .putBoolean("show_server_ip", showIp)
            .apply()
    }
    fun loadShowServerProtocol(ctx: Context) = sp(ctx).getBoolean("show_server_protocol", true)
    fun loadShowServerIp(ctx: Context) = sp(ctx).getBoolean("show_server_ip", true)

    fun saveGlassEffect(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean("glass_effect", enabled).apply()
    }
    fun loadGlassEffect(ctx: Context): Boolean = sp(ctx).getBoolean("glass_effect", true)

    fun saveSendHwid(ctx: Context, enabled: Boolean) {
        sp(ctx).edit().putBoolean("send_hwid", enabled).apply()
    }
    fun loadSendHwid(ctx: Context): Boolean = sp(ctx).getBoolean("send_hwid", false)

    fun saveSubOrder(ctx: Context, ids: List<String>) {
        sp(ctx).edit().putString("sub_order", ids.joinToString("|")).apply()
    }
    fun loadSubOrder(ctx: Context): List<String> {
        val s = sp(ctx).getString("sub_order", "") ?: ""
        return if (s.isBlank()) emptyList() else s.split("|").filter { it.isNotBlank() }
    }
    fun saveShowBuiltin(ctx: Context, show: Boolean) {
        sp(ctx).edit().putBoolean("show_builtin", show).apply()
    }
    fun loadShowBuiltin(ctx: Context): Boolean = sp(ctx).getBoolean("show_builtin", true)

    fun saveExpandedSubs(ctx: Context, ids: Set<String>) {
        sp(ctx).edit().putString("expanded_subs", ids.joinToString("|")).apply()
    }
    fun loadExpandedSubs(ctx: Context): Set<String> {
        val s = sp(ctx).getString("expanded_subs", null) ?: return emptySet()
        return s.split("|").filter { it.isNotBlank() }.toSet()
    }

    fun saveBestSettings(ctx: Context, allowRu: Boolean, allSubs: Boolean, subIds: Set<String>) {
        sp(ctx).edit()
            .putBoolean("best_allow_ru", allowRu)
            .putBoolean("best_all_subs", allSubs)
            .putString("best_sub_ids", subIds.joinToString("|"))
            .apply()
    }
    fun loadBestAllowRu(ctx: Context) = sp(ctx).getBoolean("best_allow_ru", true)
    fun loadBestAllSubs(ctx: Context) = sp(ctx).getBoolean("best_all_subs", true)
    fun saveTileSettings(
        ctx: Context,
        useBest: Boolean,
        showSubtitle: Boolean,
        notifyPick: Boolean,
        onlyFavorites: Boolean,
        maxPingMs: Int
    ) {
        sp(ctx).edit()
            .putBoolean("tile_use_best", useBest)
            .putBoolean("tile_show_subtitle", showSubtitle)
            .putBoolean("tile_notify_pick", notifyPick)
            .putBoolean("tile_only_favorites", onlyFavorites)
            .putInt("tile_max_ping_ms", maxPingMs)
            .apply()
    }
    fun loadTileUseBest(ctx: Context) = sp(ctx).getBoolean("tile_use_best", true)
    fun loadTileShowSubtitle(ctx: Context) = sp(ctx).getBoolean("tile_show_subtitle", true)
    fun loadTileNotifyPick(ctx: Context) = sp(ctx).getBoolean("tile_notify_pick", true)
    fun loadTileOnlyFavorites(ctx: Context) = sp(ctx).getBoolean("tile_only_favorites", false)
    fun loadTileMaxPingMs(ctx: Context) = sp(ctx).getInt("tile_max_ping_ms", 0)

    fun loadBestSubIds(ctx: Context): Set<String> {
        val s = sp(ctx).getString("best_sub_ids", "") ?: ""
        return s.split("|").filter { it.isNotBlank() }.toSet()
    }
}


