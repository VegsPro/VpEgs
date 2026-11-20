package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds minimal Xray configs. Sanitizes share-link quirks that break core
 * ("failed to build outbound config with tag proxy").
 */
object XrayConfigBuilder {

    fun build(outbound: JSONObject, dns: List<String> = listOf("1.1.1.1", "8.8.8.8")): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("dns", JSONObject().put("servers", JSONArray(dns)))

        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", 10808)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
                .put(
                    "sniffing",
                    JSONObject()
                        .put("enabled", true)
                        .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                )
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(sanitizeOutbound(outbound))
        outbounds.put(
            JSONObject()
                .put("tag", "direct")
                .put("protocol", "freedom")
                .put("settings", JSONObject())
        )
        outbounds.put(
            JSONObject()
                .put("tag", "block")
                .put("protocol", "blackhole")
                .put("settings", JSONObject())
        )
        root.put("outbounds", outbounds)

        root.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "field")
                            .put("outboundTag", "proxy")
                            .put("network", "tcp,udp")
                    )
                )
        )
        return root.toString()
    }

    /**
     * Drop fields that AndroidLibXrayLite often rejects.
     */
    fun sanitizeOutbound(raw: JSONObject): JSONObject {
        val o = JSONObject(raw.toString())
        o.put("tag", "proxy")
        val proto = o.optString("protocol").lowercase()

        when (proto) {
            "vless" -> sanitizeVless(o)
            "vmess" -> sanitizeVmess(o)
            "trojan" -> { /* mostly fine */ }
            "shadowsocks" -> sanitizeSs(o)
        }

        // Never pass mux/sockopt that some cores reject
        o.remove("mux")
        o.remove("sockopt")
        return o
    }

    private fun sanitizeVless(o: JSONObject) {
        val settings = o.optJSONObject("settings") ?: return
        val vnext = settings.optJSONArray("vnext") ?: return
        for (i in 0 until vnext.length()) {
            val node = vnext.optJSONObject(i) ?: continue
            val users = node.optJSONArray("users") ?: continue
            for (j in 0 until users.length()) {
                val u = users.optJSONObject(j) ?: continue
                // Standard VLESS encryption is only "none"
                val enc = u.optString("encryption", "none")
                if (enc != "none") u.put("encryption", "none")
                // flow only valid with vision + tls/reality + tcp
                val flow = u.optString("flow")
                if (flow.isNotBlank() && !flow.contains("vision")) {
                    u.remove("flow")
                }
            }
        }

        val stream = o.optJSONObject("streamSettings") ?: return
        var network = stream.optString("network", "tcp").ifBlank { "tcp" }
        // normalize aliases
        if (network == "h2" || network == "http") network = "tcp"
        stream.put("network", network)

        var security = stream.optString("security", "none").ifBlank { "none" }
        if (security !in setOf("none", "tls", "reality")) security = "none"
        stream.put("security", security)

        // flow requires tcp + (tls|reality)
        if (security == "none" || network != "tcp") {
            val vnext2 = settings.optJSONArray("vnext")
            if (vnext2 != null) {
                for (i in 0 until vnext2.length()) {
                    val users = vnext2.optJSONObject(i)?.optJSONArray("users") ?: continue
                    for (j in 0 until users.length()) {
                        users.optJSONObject(j)?.remove("flow")
                    }
                }
            }
        }

        if (security == "reality") {
            val rs = stream.optJSONObject("realitySettings") ?: JSONObject()
            // Without publicKey Reality cannot build
            if (rs.optString("publicKey").isBlank()) {
                stream.put("security", "none")
                stream.remove("realitySettings")
            } else {
                // fingerprint defaults
                if (rs.optString("fingerprint").isBlank()) rs.put("fingerprint", "chrome")
                stream.put("realitySettings", rs)
            }
        }

        if (security == "tls") {
            val ts = stream.optJSONObject("tlsSettings") ?: JSONObject()
            if (ts.optString("fingerprint").isBlank() && ts.has("fingerprint")) {
                // leave empty ok
            }
            stream.put("tlsSettings", ts)
        }

        // Clean network-specific junk
        when (network) {
            "ws" -> {
                stream.remove("grpcSettings")
                stream.remove("tcpSettings")
            }
            "grpc" -> {
                stream.remove("wsSettings")
                stream.remove("tcpSettings")
            }
            else -> {
                stream.remove("wsSettings")
                stream.remove("grpcSettings")
            }
        }
        o.put("streamSettings", stream)
        o.put("settings", settings)
    }

    private fun sanitizeVmess(o: JSONObject) {
        val stream = o.optJSONObject("streamSettings") ?: return
        var network = stream.optString("network", "tcp").ifBlank { "tcp" }
        if (network == "h2") network = "ws"
        stream.put("network", network)
        o.put("streamSettings", stream)
    }

    private fun sanitizeSs(o: JSONObject) {
        val settings = o.optJSONObject("settings") ?: return
        val servers = settings.optJSONArray("servers") ?: return
        for (i in 0 until servers.length()) {
            val s = servers.optJSONObject(i) ?: continue
            // method required
            if (s.optString("method").isBlank()) s.put("method", "chacha20-ietf-poly1305")
        }
    }

    fun vlessOutbound(
        address: String,
        port: Int,
        uuid: String,
        flow: String = "",
        encryption: String = "none",
        network: String = "tcp",
        security: String = "none",
        sni: String = "",
        fp: String = "",
        pbk: String = "",
        sid: String = "",
        spx: String = "",
        path: String = "",
        host: String = "",
        alpn: String = ""
    ): JSONObject {
        // Share-links sometimes put PQ/hybrid garbage into encryption=
        val enc = if (encryption == "none" || encryption.isBlank()) "none" else "none"
        var net = network.ifBlank { "tcp" }.lowercase()
        if (net == "h2" || net == "http") net = "tcp"
        var sec = security.ifBlank { "none" }.lowercase()
        if (sec !in setOf("none", "tls", "reality")) sec = "none"

        // vision flow only with tls/reality + tcp
        val useFlow = flow.contains("vision") && net == "tcp" && (sec == "tls" || sec == "reality")

        // Reality without key → fall back to none (better than crash)
        if (sec == "reality" && pbk.isBlank()) sec = "none"

        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", enc)
        if (useFlow) user.put("flow", if (flow.contains("vision")) "xtls-rprx-vision" else flow)

        val settings = JSONObject()
            .put(
                "vnext",
                JSONArray().put(
                    JSONObject()
                        .put("address", address)
                        .put("port", port)
                        .put("users", JSONArray().put(user))
                )
            )

        val stream = JSONObject().put("network", net)
        when (sec) {
            "reality" -> {
                stream.put("security", "reality")
                val rs = JSONObject()
                if (sni.isNotBlank()) rs.put("serverName", sni)
                rs.put("fingerprint", fp.ifBlank { "chrome" })
                rs.put("publicKey", pbk)
                if (sid.isNotBlank()) rs.put("shortId", sid)
                if (spx.isNotBlank()) rs.put("spiderX", spx)
                stream.put("realitySettings", rs)
            }
            "tls" -> {
                stream.put("security", "tls")
                val ts = JSONObject()
                if (sni.isNotBlank()) ts.put("serverName", sni)
                if (fp.isNotBlank()) ts.put("fingerprint", fp)
                if (alpn.isNotBlank()) {
                    ts.put("alpn", JSONArray(alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
                }
                // allowInsecure helps broken free nodes
                ts.put("allowInsecure", true)
                stream.put("tlsSettings", ts)
            }
            else -> stream.put("security", "none")
        }
        if (net == "ws") {
            val ws = JSONObject()
            // path often polluted with "?Telegram…" — keep only path part
            val cleanPath = path.substringBefore("?").ifBlank { "/" }
            ws.put("path", cleanPath)
            if (host.isNotBlank()) ws.put("headers", JSONObject().put("Host", host))
            stream.put("wsSettings", ws)
        }
        if (net == "grpc") {
            val grpc = JSONObject()
            if (path.isNotBlank()) grpc.put("serviceName", path.substringBefore("?"))
            stream.put("grpcSettings", grpc)
        }

        return sanitizeOutbound(
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put("settings", settings)
                .put("streamSettings", stream)
        )
    }

    fun trojanOutbound(
        address: String,
        port: Int,
        password: String,
        sni: String = "",
        network: String = "tcp"
    ): JSONObject {
        val settings = JSONObject().put(
            "servers",
            JSONArray().put(
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("password", password)
            )
        )
        val stream = JSONObject()
            .put("network", network.ifBlank { "tcp" })
            .put("security", if (sni.isNotBlank()) "tls" else "none")
        if (sni.isNotBlank()) {
            stream.put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", sni)
                    .put("allowInsecure", true)
            )
        }
        return sanitizeOutbound(
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "trojan")
                .put("settings", settings)
                .put("streamSettings", stream)
        )
    }

    fun vmessOutbound(
        address: String,
        port: Int,
        uuid: String,
        alterId: Int = 0,
        security: String = "auto",
        network: String = "tcp",
        path: String = "",
        host: String = "",
        tls: String = ""
    ): JSONObject {
        val settings = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("id", uuid)
                                .put("alterId", alterId)
                                .put("security", security.ifBlank { "auto" })
                        )
                    )
            )
        )
        val stream = JSONObject().put("network", network.ifBlank { "tcp" })
        if (tls == "tls" || tls == "true") {
            stream.put("security", "tls")
            val ts = JSONObject().put("allowInsecure", true)
            if (host.isNotBlank()) ts.put("serverName", host)
            stream.put("tlsSettings", ts)
        } else {
            stream.put("security", "none")
        }
        if (network == "ws") {
            val ws = JSONObject()
            ws.put("path", path.substringBefore("?").ifBlank { "/" })
            if (host.isNotBlank()) ws.put("headers", JSONObject().put("Host", host))
            stream.put("wsSettings", ws)
        }
        return sanitizeOutbound(
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vmess")
                .put("settings", settings)
                .put("streamSettings", stream)
        )
    }

    fun shadowsocksOutbound(
        address: String,
        port: Int,
        method: String,
        password: String
    ): JSONObject {
        val settings = JSONObject().put(
            "servers",
            JSONArray().put(
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("method", method.ifBlank { "chacha20-ietf-poly1305" })
                    .put("password", password)
            )
        )
        return sanitizeOutbound(
            JSONObject()
                .put("tag", "proxy")
                .put("protocol", "shadowsocks")
                .put("settings", settings)
        )
    }
}
