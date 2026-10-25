package com.vpegs.ms.data

import android.content.Context
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
                .put("address", s.address)
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
                        latencyMs = if (lat < 0) null else lat,
                        isFavorite = o.optBoolean("isFavorite", false),
                        isSelected = o.optBoolean("isSelected", false),
                        address = o.optString("address"),
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
}
