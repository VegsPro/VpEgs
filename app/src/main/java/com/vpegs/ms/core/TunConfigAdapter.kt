package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress

/**
 * SOCKS-only config for Xray. TUN is handled by hev-socks5-tunnel.
 * Domain bypass: sniffing SNI + explicit IP rules from resolved hosts
 * (TUN/SOCKS often connects by IP, so domain-only rules miss).
 */
object TunConfigAdapter {

    private val AD_DOMAINS = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "pagead2.googlesyndication.com", "adservice.google.com",
        "ads.google.com", "ad.doubleclick.net", "static.doubleclick.net",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "facebook.com/tr", "connect.facebook.net", "ads.facebook.com",
        "ads.twitter.com", "ads-twitter.com", "adnxs.com", "adsrvr.org",
        "adform.net", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "mopub.com", "taboola.com", "outbrain.com", "criteo.com",
        "yandexadexchange.net", "an.yandex.ru", "ads.yandex.ru", "adfox.ru",
        "ads.vk.com", "ad.mail.ru", "reklama.mail.ru", "target.my.com",
        "pubmatic.com", "openx.net", "rubiconproject.com", "moatads.com",
        "adjust.com", "appsflyer.com", "branch.io"
    )

    fun adapt(
        rawJson: String,
        bypassDomains: List<String> = emptyList(),
        blockAds: Boolean = false
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

        val domains = bypassDomains.map { normalizeDomain(it) }.filter { it.isNotBlank() }.distinct()
        val resolvedIps = resolveDomainIps(domains)

        val out = JSONObject()
        out.put("log", JSONObject().put("loglevel", "warning"))

        // Prefer system/local resolution for bypassed domains so IPs match direct path
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
                    .put("listen", "127.0.0.1")
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
                            // false: override dest to sniffed domain so domain rules match reliably
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

        // IP fallback: TUN/SOCKS often sees only IPs — force those to direct
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

        if (blockAds) {
            val arr = JSONArray()
            AD_DOMAINS.forEach { arr.put("domain:$it") }
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
                .put("domainMatcher", "mph")
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

    /** Resolve A records so bypass works when tunnel sees IPs only. */
    private fun resolveDomainIps(domains: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (d in domains) {
            try {
                InetAddress.getAllByName(d).forEach { addr ->
                    val host = addr.hostAddress ?: return@forEach
                    if (':' !in host) out.add(host) // IPv4 only for simplicity
                }
                // common www
                try {
                    InetAddress.getAllByName("www.$d").forEach { addr ->
                        val host = addr.hostAddress ?: return@forEach
                        if (':' !in host) out.add(host)
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
        return out.toList()
    }
}
