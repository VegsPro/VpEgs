package com.vpegs.ms.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * SOCKS-only config for Xray. TUN is handled by hev-socks5-tunnel.
 * AndroidLibXrayLite build does NOT support protocol "tun" inbound
 * (fails: "failed to build inbound").
 */
object TunConfigAdapter {

    /** Common ad / tracker domains (compact list). */
    private val AD_DOMAINS = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "pagead2.googlesyndication.com", "adservice.google.com",
        "ads.google.com", "ad.doubleclick.net", "static.doubleclick.net",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "facebook.com/tr", "connect.facebook.net", "graph.facebook.com",
        "ads.facebook.com", "an.facebook.com",
        "ads.twitter.com", "ads-twitter.com", "static.ads-twitter.com",
        "ads.linkedin.com", "ads.yahoo.com", "advertising.com",
        "adnxs.com", "adsrvr.org", "adsafeprotected.com", "adform.net",
        "advertising.com", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "unity3d.com", "mopub.com", "chartboost.com", "vungle.com",
        "startappexchange.com", "taboola.com", "outbrain.com", "criteo.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "yandexadexchange.net", "an.yandex.ru", "ads.yandex.ru",
        "mc.yandex.ru", "advertising.yandex.ru", "adfox.ru",
        "vk.com/ads", "ads.vk.com", "top-fwz1.mail.ru", "ad.mail.ru",
        "reklama.mail.ru", "ad.my.com", "target.my.com",
        "pubmatic.com", "openx.net", "rubiconproject.com", "casalemedia.com",
        "moatads.com", "branch.io", "adjust.com", "appsflyer.com",
        "sentry-cdn.com"
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
                        JSONObject()
                            .put("udp", true)
                            .put("auth", "noauth")
                    )
                    .put(
                        "sniffing",
                        JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                            .put("routeOnly", true)
                    )
            )
        )
        out.put("outbounds", cleaned)

        val rules = JSONArray()
        // Private IPs → direct
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

        // User domain bypass → direct
        val domains = bypassDomains.map { normalizeDomain(it) }.filter { it.isNotBlank() }.distinct()
        if (domains.isNotEmpty()) {
            val arr = JSONArray()
            for (d in domains) {
                arr.put("domain:$d")
                arr.put("full:$d")
            }
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("domain", arr)
                    .put("outboundTag", "direct")
            )
        }

        // Ad domains → blackhole
        if (blockAds) {
            val arr = JSONArray()
            for (d in AD_DOMAINS) {
                arr.put("domain:$d")
            }
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
        // strip scheme
        s = s.removePrefix("https://").removePrefix("http://")
        // strip path / query / fragment
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        // strip credentials
        if ('@' in s) s = s.substringAfter('@')
        // strip port (not IPv6)
        if (s.count { it == ':' } == 1) s = s.substringBefore(':')
        s = s.trim().trim('.')
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        // basic sanity
        if (s.length < 3 || '.' !in s) return ""
        if (s.any { it.isWhitespace() }) return ""
        return s
    }
}
