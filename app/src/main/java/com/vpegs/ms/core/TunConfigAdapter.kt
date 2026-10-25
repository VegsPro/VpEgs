package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal TUN-capable Xray config.
 *
 * CRITICAL: AndroidLibXrayLite only sets env xray.tun.fd — the JSON MUST
 * contain an inbound with protocol "tun" so Xray actually reads the VPN fd.
 * Without it, the system VPN is up but traffic never enters the proxy → same IP.
 */
object TunConfigAdapter {

    fun adapt(rawJson: String): String {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return rawJson
        }

        val srcOutbounds = root.optJSONArray("outbounds") ?: JSONArray()
        if (srcOutbounds.length() == 0) return rawJson

        val supported = setOf("vless", "vmess", "trojan", "shadowsocks")
        val kept = JSONArray()
        var primaryTag = "proxy"

        for (i in 0 until srcOutbounds.length()) {
            val o = srcOutbounds.optJSONObject(i) ?: continue
            val tag = o.optString("tag")
            val proto = o.optString("protocol").lowercase()
            if (proto !in supported) continue
            val copy = JSONObject(o.toString())
            // Normalize first/main outbound to tag "proxy"
            if (kept.length() == 0 || tag == "proxy") {
                copy.put("tag", "proxy")
                primaryTag = "proxy"
                if (tag == "proxy" && kept.length() > 0) {
                    // replace earlier placeholder
                    kept.put(0, copy)
                    continue
                }
            }
            if (kept.length() == 0 || tag == "proxy") {
                if (kept.length() == 0) kept.put(copy)
                else if (tag == "proxy") { /* already replaced */ }
            } else {
                kept.put(copy)
            }
        }

        // Simpler collection
        val cleaned = JSONArray()
        var foundProxy = false
        for (i in 0 until srcOutbounds.length()) {
            val o = srcOutbounds.optJSONObject(i) ?: continue
            val tag = o.optString("tag")
            val proto = o.optString("protocol").lowercase()
            if (proto !in supported) continue
            val copy = JSONObject(o.toString())
            if (!foundProxy && (tag == "proxy" || cleaned.length() == 0)) {
                copy.put("tag", "proxy")
                foundProxy = true
                cleaned.put(copy)
            } else if (tag != "proxy") {
                cleaned.put(copy)
            }
        }
        if (cleaned.length() == 0) {
            // last resort: first outbound
            val o = srcOutbounds.optJSONObject(0) ?: return rawJson
            val copy = JSONObject(o.toString())
            copy.put("tag", "proxy")
            cleaned.put(copy)
        }

        cleaned.put(
            JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject())
        )
        cleaned.put(
            JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", JSONObject())
        )

        val out = JSONObject()
        out.put("log", JSONObject().put("loglevel", "warning"))
        out.put(
            "dns",
            JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIPv4")
        )

        // ★ TUN inbound — reads fd from env xray.tun.fd set by StartLoop
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("tag", "tun-in")
                .put("protocol", "tun")
                .put(
                    "settings",
                    JSONObject()
                        .put("mtu", 1500)
                )
                .put(
                    "sniffing",
                    JSONObject()
                        .put("enabled", true)
                        .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                )
        )
        // Optional local socks for diagnostics
        inbounds.put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", 10808)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        )
        out.put("inbounds", inbounds)
        out.put("outbounds", cleaned)

        val rules = JSONArray()
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("outboundTag", "direct")
                .put(
                    "ip",
                    JSONArray()
                        .put("10.0.0.0/8")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("127.0.0.0/8")
                        .put("169.254.0.0/16")
                )
        )
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "tcp,udp")
                .put("outboundTag", "proxy")
        )
        out.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules)
        )

        return out.toString()
    }
}
