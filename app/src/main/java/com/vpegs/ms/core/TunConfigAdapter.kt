package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/**
 * SOCKS-only config for Xray. TUN is handled by hev-socks5-tunnel.
 * Ad-block: small domain list only (large lists freeze UI / break Xray routing).
 * Domain bypass: SNI sniffing + resolved IPs → direct.
 */
object TunConfigAdapter {

    /** Hard cap — more domains = huge JSON, ANR, Xray parse fail. */
    private const val MAX_AD_DOMAINS = 120
    private const val MAX_BYPASS_DOMAINS = 40

    fun adapt(
        rawJson: String,
        bypassDomains: List<String> = emptyList(),
        blockAds: Boolean = false,
        adDomains: List<String> = emptyList(),
        adWhitelist: List<String> = emptyList(),
        localProxyLan: Boolean = false
    ): String {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return rawJson
        }

        val srcOutbounds = root.optJSONArray("outbounds") ?: JSONArray()
        if (srcOutbounds.length() == 0) return rawJson

        val supported = setOf("vless", "vmess", "trojan", "shadowsocks")
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
            val o = srcOutbounds.optJSONObject(0) ?: return rawJson
            val copy = JSONObject(o.toString())
            copy.put("tag", "proxy")
            cleaned.put(copy)
        }

        cleaned.put(
            JSONObject().put("tag", "direct").put("protocol", "freedom")
                .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        )
        cleaned.put(
            JSONObject().put("tag", "block").put("protocol", "blackhole")
                .put("settings", JSONObject())
        )

        val domains = bypassDomains.map { normalizeDomain(it) }
            .filter { it.isNotBlank() }.distinct().take(MAX_BYPASS_DOMAINS)
        val resolvedIps = resolveDomainIps(domains)
        val whitelist = adWhitelist.map { normalizeDomain(it) }
            .filter { it.isNotBlank() }.distinct().take(50)

        // Prefer compact builtin; never inject tens of thousands of rules
        val adsBase = if (adDomains.isEmpty()) AdBlockEngine.BUILTIN else adDomains
        val ads = adsBase.map { normalizeDomain(it) }
            .filter { it.isNotBlank() && it !in whitelist }
            .distinct()
            .take(MAX_AD_DOMAINS)

        val out = JSONObject()
        out.put("log", JSONObject().put("loglevel", "warning"))

        val dnsServers = JSONArray()
        if (domains.isNotEmpty()) {
            dnsServers.put(
                JSONObject()
                    .put("address", "localhost")
                    .put("domains", JSONArray().apply {
                        domains.forEach {
                            put("domain:$it")
                            put("full:$it")
                        }
                    })
            )
        }
        dnsServers.put("1.1.1.1").put("8.8.8.8")
        out.put(
            "dns",
            JSONObject()
                .put("servers", dnsServers)
                .put("queryStrategy", "UseIPv4")
        )

        out.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "socks-in")
                    .put("port", 10808)
                    .put("listen", if (localProxyLan) "0.0.0.0" else "127.0.0.1")
                    .put("protocol", "socks")
                    .put(
                        "settings",
                        JSONObject().put("udp", true).put("auth", "noauth")
                    )
                    .put(
                        "sniffing",
                        JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                            .put("routeOnly", false)
                    )
            )
        )
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

        if (domains.isNotEmpty()) {
            val domainArr = JSONArray()
            for (d in domains) {
                domainArr.put("domain:$d")
                domainArr.put("full:$d")
                domainArr.put("full:www.$d")
            }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", domainArr)
                    .put("outboundTag", "direct")
            )
        }
        if (resolvedIps.isNotEmpty()) {
            val ipArr = JSONArray()
            resolvedIps.forEach { ipArr.put(it) }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", ipArr)
                    .put("outboundTag", "direct")
            )
        }

        if (blockAds && whitelist.isNotEmpty()) {
            val arr = JSONArray()
            whitelist.forEach {
                arr.put("domain:$it")
                arr.put("full:$it")
            }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", arr)
                    .put("outboundTag", "proxy")
            )
        }

        // Lightweight ad block only — no geosite (missing tags break routing), no huge lists
        if (blockAds && ads.isNotEmpty()) {
            val arr = JSONArray()
            ads.forEach { arr.put("domain:$it") }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", arr)
                    .put("outboundTag", "block")
            )
        }

        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "tcp,udp")
                .put("outboundTag", "proxy")
        )

        out.put(
            "routing",
            JSONObject()
                .put("domainStrategy", "IPIfNonMatch")
                .put("rules", rules)
        )

        return out.toString()
    }

    fun normalizeDomain(input: String): String {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return ""
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in s) s = s.substringAfter('@')
        if (s.count { it == ':' } == 1) s = s.substringBefore(':')
        s = s.trim().trim('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        if (s.length < 3 || '.' !in s) return ""
        if (s.any { it.isWhitespace() }) return ""
        return s
    }

    private fun resolveDomainIps(domains: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (d in domains) {
            try {
                InetAddress.getAllByName(d).forEach { addr ->
                    val host = addr.hostAddress ?: return@forEach
                    if (':' !in host) out.add(host)
                }
            } catch (_: Exception) {}
        }
        return out.toList()
    }
}
