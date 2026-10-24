package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a minimal Xray config JSON that:
 * - opens SOCKS inbound on 127.0.0.1:10808 (optional when using TUN fd)
 * - routes all traffic via the given outbound
 * - uses TUN when tunFd path is used by core (StartLoop with fd)
 */
object XrayConfigBuilder {

    fun build(outbound: JSONObject, dns: List<String> = listOf("1.1.1.1", "8.8.8.8")): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        val dnsObj = JSONObject()
        dnsObj.put("servers", JSONArray(dns))
        root.put("dns", dnsObj)

        // When using StartLoop(config, tunFd), core attaches to TUN.
        // Still provide socks inbound for diagnostics.
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", 10808)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
                .put("sniffing", JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                )
        )
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(outbound)
        // freedom / blackhole fallbacks
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

        val routing = JSONObject()
        routing.put("domainStrategy", "AsIs")
        routing.put(
            "rules",
            JSONArray().put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "proxy")
                    .put("network", "tcp,udp")
            )
        )
        root.put("routing", routing)

        return root.toString()
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
        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", encryption.ifBlank { "none" })
        if (flow.isNotBlank()) user.put("flow", flow)

        val settings = JSONObject()
            .put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("users", JSONArray().put(user))
            ))

        val stream = JSONObject().put("network", network.ifBlank { "tcp" })
        when (security) {
            "reality" -> {
                stream.put("security", "reality")
                val rs = JSONObject()
                if (sni.isNotBlank()) rs.put("serverName", sni)
                if (fp.isNotBlank()) rs.put("fingerprint", fp)
                if (pbk.isNotBlank()) rs.put("publicKey", pbk)
                if (sid.isNotBlank()) rs.put("shortId", sid)
                if (spx.isNotBlank()) rs.put("spiderX", spx)
                stream.put("realitySettings", rs)
            }
            "tls" -> {
                stream.put("security", "tls")
                val ts = JSONObject()
                if (sni.isNotBlank()) ts.put("serverName", sni)
                if (fp.isNotBlank()) ts.put("fingerprint", fp)
                if (alpn.isNotBlank()) ts.put("alpn", JSONArray(alpn.split(",")))
                stream.put("tlsSettings", ts)
            }
            else -> stream.put("security", "none")
        }
        if (network == "ws") {
            val ws = JSONObject()
            if (path.isNotBlank()) ws.put("path", path)
            if (host.isNotBlank()) ws.put("headers", JSONObject().put("Host", host))
            stream.put("wsSettings", ws)
        }
        if (network == "grpc") {
            val grpc = JSONObject()
            if (path.isNotBlank()) grpc.put("serviceName", path)
            stream.put("grpcSettings", grpc)
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", settings)
            .put("streamSettings", stream)
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
            .put("network", network)
            .put("security", "tls")
            .put("tlsSettings", JSONObject().apply {
                if (sni.isNotBlank()) put("serverName", sni)
            })
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", settings)
            .put("streamSettings", stream)
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
        val user = JSONObject()
            .put("id", uuid)
            .put("alterId", alterId)
            .put("security", security)
        val settings = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("users", JSONArray().put(user))
            )
        )
        val stream = JSONObject().put("network", network)
        if (tls == "tls") {
            stream.put("security", "tls")
            stream.put("tlsSettings", JSONObject().apply {
                if (host.isNotBlank()) put("serverName", host)
            })
        }
        if (network == "ws") {
            stream.put("wsSettings", JSONObject().apply {
                if (path.isNotBlank()) put("path", path)
                if (host.isNotBlank()) put("headers", JSONObject().put("Host", host))
            })
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", settings)
            .put("streamSettings", stream)
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
                    .put("method", method)
                    .put("password", password)
            )
        )
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", settings)
    }
}
