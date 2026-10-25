package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adapts provider Xray JSON (Alius etc.) for Android TUN mode.
 *
 * Problem: many subscriptions route only specific domains/inboundTags,
 * so system VPN traffic never hits the proxy and the public IP stays local.
 *
 * Fix: keep all outbounds, force a catch-all rule to proxy/balancer.
 */
object TunConfigAdapter {

    fun adapt(rawJson: String): String {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return rawJson
        }

        // Log
        if (!root.has("log")) {
            root.put("log", JSONObject().put("loglevel", "warning"))
        }

        // DNS: prefer DoH + plain, avoid local ISP leak when possible
        if (!root.has("dns")) {
            root.put(
                "dns",
                JSONObject()
                    .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                    .put("queryStrategy", "UseIPv4")
            )
        }

        // Outbounds must exist
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        if (outbounds.length() == 0) return rawJson

        val proxyTag = pickProxyTag(root, outbounds)
        val hasBalancer = root.optJSONObject("routing")
            ?.optJSONArray("balancers")
            ?.length()?.let { it > 0 } == true
            || hasBalancersInRouting(root)

        // Replace routing with VPN-friendly rules
        val routing = JSONObject()
        routing.put("domainStrategy", "AsIs")

        // Preserve balancers if present
        val originalRouting = root.optJSONObject("routing")
        val balancers = originalRouting?.optJSONArray("balancers")
        if (balancers != null && balancers.length() > 0) {
            routing.put("balancers", balancers)
        }

        val rules = JSONArray()

        // 1) Private LAN direct (no leak into tunnel loop)
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("outboundTag", "direct")
                .put(
                    "ip",
                    JSONArray()
                        .put("geoip:private")
                        .put("0.0.0.0/8")
                        .put("10.0.0.0/8")
                        .put("127.0.0.0/8")
                        .put("169.254.0.0/16")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("224.0.0.0/4")
                        .put("240.0.0.0/4")
                )
        )

        // 2) Catch-all: ALL tcp/udp through proxy or main balancer
        val catchAll = JSONObject()
            .put("type", "field")
            .put("network", "tcp,udp")
        if (balancers != null && balancers.length() > 0) {
            var balTag: String? = null
            for (i in 0 until balancers.length()) {
                val t = balancers.optJSONObject(i)?.optString("tag") ?: continue
                if (t == "balancer-main") { balTag = t; break }
            }
            if (balTag == null) {
                balTag = balancers.optJSONObject(0)?.optString("tag")
            }
            if (!balTag.isNullOrBlank()) {
                catchAll.put("balancerTag", balTag)
            } else {
                catchAll.put("outboundTag", proxyTag)
            }
        } else {
            catchAll.put("outboundTag", proxyTag)
        }
        rules.put(catchAll)

        routing.put("rules", rules)
        root.put("routing", routing)

        // Ensure direct + block outbounds exist
        ensureOutbound(outbounds, "direct", "freedom")
        ensureOutbound(outbounds, "block", "blackhole")
        root.put("outbounds", outbounds)

        // Inbounds: keep minimal local socks for diagnostics (TUN is injected by lib)
        root.put(
            "inbounds",
            JSONArray().put(
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
                            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                    )
            )
        )

        // Drop observatory noise that can delay/fail on mobile
        root.remove("burstObservatory")
        root.remove("observatory")

        return root.toString()
    }

    private fun hasBalancersInRouting(root: JSONObject): Boolean {
        val r = root.optJSONObject("routing") ?: return false
        return (r.optJSONArray("balancers")?.length() ?: 0) > 0
    }

    private fun pickProxyTag(root: JSONObject, outbounds: JSONArray): String {
        for (i in 0 until outbounds.length()) {
            val o = outbounds.optJSONObject(i) ?: continue
            val tag = o.optString("tag")
            val proto = o.optString("protocol")
            if (tag == "proxy") return "proxy"
            if (proto in listOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "hysteria2", "wireguard")) {
                if (tag.isNotBlank() && tag !in listOf("direct", "block", "dns-out")) return tag
            }
        }
        // first non-direct
        for (i in 0 until outbounds.length()) {
            val o = outbounds.optJSONObject(i) ?: continue
            val tag = o.optString("tag")
            if (tag.isNotBlank() && tag != "direct" && tag != "block") return tag
        }
        return "proxy"
    }

    private fun ensureOutbound(outbounds: JSONArray, tag: String, protocol: String) {
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag") == tag) return
        }
        outbounds.put(
            JSONObject()
                .put("tag", tag)
                .put("protocol", protocol)
                .put("settings", JSONObject())
        )
    }
}
