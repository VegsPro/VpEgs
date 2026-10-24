package com.vpegs.ms.core

import android.util.Base64
import com.vpegs.ms.data.Protocol
import com.vpegs.ms.data.ServerNode
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID

/**
 * Parses common share links into ServerNode + Xray outbound JSON.
 * Supports: vless://, vmess://, trojan://, ss://
 */
object LinkParser {

    data class Parsed(
        val node: ServerNode,
        val configJson: String
    )

    fun parse(link: String, subscriptionId: String = ""): Parsed? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://", true) -> parseVless(trimmed, subscriptionId)
            trimmed.startsWith("vmess://", true) -> parseVmess(trimmed, subscriptionId)
            trimmed.startsWith("trojan://", true) -> parseTrojan(trimmed, subscriptionId)
            trimmed.startsWith("ss://", true) -> parseSs(trimmed, subscriptionId)
            else -> null
        }
    }

    /** Split multi-line / base64 subscription body into links */
    fun parseSubscriptionBody(body: String, subscriptionId: String, subName: String): List<Parsed> {
        val text = tryDecodeBase64(body.trim()) ?: body
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                parse(line, subscriptionId)?.let { p ->
                    p.copy(
                        node = p.node.copy(
                            name = if (p.node.name.isBlank()) "$subName" else p.node.name,
                            subscriptionId = subscriptionId
                        )
                    )
                }
            }
            .toList()
    }

    private fun parseVless(link: String, subId: String): Parsed? {
        // vless://uuid@host:port?params#name
        try {
            val noScheme = link.removePrefix("vless://").removePrefix("VLESS://")
            val hashIdx = noScheme.indexOf('#')
            val name = if (hashIdx >= 0) URLDecoder.decode(noScheme.substring(hashIdx + 1), "UTF-8") else "VLESS"
            val main = if (hashIdx >= 0) noScheme.substring(0, hashIdx) else noScheme
            val qIdx = main.indexOf('?')
            val userHost = if (qIdx >= 0) main.substring(0, qIdx) else main
            val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
            val at = userHost.lastIndexOf('@')
            if (at < 0) return null
            val uuid = userHost.substring(0, at)
            val hostPort = userHost.substring(at + 1)
            val colon = hostPort.lastIndexOf(':')
            val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
            val port = if (colon >= 0) hostPort.substring(colon + 1).toIntOrNull() ?: 443 else 443
            val params = parseQuery(query)

            val security = params["security"] ?: "none"
            val network = params["type"] ?: "tcp"
            val outbound = XrayConfigBuilder.vlessOutbound(
                address = host,
                port = port,
                uuid = uuid,
                flow = params["flow"] ?: "",
                encryption = params["encryption"] ?: "none",
                network = network,
                security = security,
                sni = params["sni"] ?: params["host"] ?: "",
                fp = params["fp"] ?: "",
                pbk = params["pbk"] ?: "",
                sid = params["sid"] ?: "",
                spx = params["spx"] ?: "",
                path = params["path"] ?: "",
                host = params["host"] ?: "",
                alpn = params["alpn"] ?: ""
            )
            val proto = if (security == "reality") Protocol.VLESS_REALITY else Protocol.VLESS
            val node = ServerNode(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { host },
                country = name,
                flag = guessFlag(name, host),
                protocol = proto,
                address = host,
                port = port,
                rawConfig = link,
                subscriptionId = subId
            )
            return Parsed(node, XrayConfigBuilder.build(outbound))
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseVmess(link: String, subId: String): Parsed? {
        try {
            val b64 = link.removePrefix("vmess://").removePrefix("VMESS://")
            val json = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            val o = JSONObject(json)
            val host = o.optString("add")
            val port = o.optInt("port", 443)
            val uuid = o.optString("id")
            val name = o.optString("ps").ifBlank { host }
            val outbound = XrayConfigBuilder.vmessOutbound(
                address = host,
                port = port,
                uuid = uuid,
                alterId = o.optInt("aid", 0),
                security = o.optString("scy", "auto"),
                network = o.optString("net", "tcp"),
                path = o.optString("path"),
                host = o.optString("host"),
                tls = o.optString("tls")
            )
            val node = ServerNode(
                id = UUID.randomUUID().toString(),
                name = name,
                country = name,
                flag = guessFlag(name, host),
                protocol = Protocol.VMESS,
                address = host,
                port = port,
                rawConfig = link,
                subscriptionId = subId
            )
            return Parsed(node, XrayConfigBuilder.build(outbound))
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseTrojan(link: String, subId: String): Parsed? {
        try {
            val noScheme = link.removePrefix("trojan://").removePrefix("TROJAN://")
            val hashIdx = noScheme.indexOf('#')
            val name = if (hashIdx >= 0) URLDecoder.decode(noScheme.substring(hashIdx + 1), "UTF-8") else "Trojan"
            val main = if (hashIdx >= 0) noScheme.substring(0, hashIdx) else noScheme
            val qIdx = main.indexOf('?')
            val userHost = if (qIdx >= 0) main.substring(0, qIdx) else main
            val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
            val at = userHost.lastIndexOf('@')
            if (at < 0) return null
            val password = userHost.substring(0, at)
            val hostPort = userHost.substring(at + 1)
            val colon = hostPort.lastIndexOf(':')
            val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
            val port = if (colon >= 0) hostPort.substring(colon + 1).toIntOrNull() ?: 443 else 443
            val params = parseQuery(query)
            val outbound = XrayConfigBuilder.trojanOutbound(
                address = host,
                port = port,
                password = password,
                sni = params["sni"] ?: params["peer"] ?: host,
                network = params["type"] ?: "tcp"
            )
            val node = ServerNode(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { host },
                country = name,
                flag = guessFlag(name, host),
                protocol = Protocol.TROJAN,
                address = host,
                port = port,
                rawConfig = link,
                subscriptionId = subId
            )
            return Parsed(node, XrayConfigBuilder.build(outbound))
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseSs(link: String, subId: String): Parsed? {
        try {
            // ss://base64(method:pass)@host:port#name  OR ss://base64(method:pass@host:port)
            val noScheme = link.removePrefix("ss://").removePrefix("SS://")
            val hashIdx = noScheme.indexOf('#')
            val name = if (hashIdx >= 0) URLDecoder.decode(noScheme.substring(hashIdx + 1), "UTF-8") else "SS"
            val main = if (hashIdx >= 0) noScheme.substring(0, hashIdx) else noScheme
            val at = main.lastIndexOf('@')
            val method: String
            val password: String
            val host: String
            val port: Int
            if (at >= 0) {
                val userPart = String(Base64.decode(main.substring(0, at), Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
                val hp = main.substring(at + 1)
                val colon = userPart.indexOf(':')
                method = if (colon >= 0) userPart.substring(0, colon) else "aes-256-gcm"
                password = if (colon >= 0) userPart.substring(colon + 1) else userPart
                val c2 = hp.lastIndexOf(':')
                host = if (c2 >= 0) hp.substring(0, c2) else hp
                port = if (c2 >= 0) hp.substring(c2 + 1).toIntOrNull() ?: 8388 else 8388
            } else {
                val decoded = String(Base64.decode(main, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
                // method:pass@host:port
                val at2 = decoded.lastIndexOf('@')
                val user = decoded.substring(0, at2)
                val hp = decoded.substring(at2 + 1)
                val colon = user.indexOf(':')
                method = user.substring(0, colon)
                password = user.substring(colon + 1)
                val c2 = hp.lastIndexOf(':')
                host = hp.substring(0, c2)
                port = hp.substring(c2 + 1).toInt()
            }
            val outbound = XrayConfigBuilder.shadowsocksOutbound(host, port, method, password)
            val node = ServerNode(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { host },
                country = name,
                flag = guessFlag(name, host),
                protocol = Protocol.SHADOWSOCKS,
                address = host,
                port = port,
                rawConfig = link,
                subscriptionId = subId
            )
            return Parsed(node, XrayConfigBuilder.build(outbound))
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null
            else URLDecoder.decode(it.substring(0, i), "UTF-8") to
                URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
    }

    private fun tryDecodeBase64(s: String): String? {
        return try {
            val cleaned = s.replace("\n", "").replace("\r", "").replace(" ", "")
            String(Base64.decode(cleaned, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
        } catch (_: Exception) {
            null
        }
    }

    private fun guessFlag(name: String, host: String): String {
        val n = (name + " " + host).lowercase()
        return when {
            "germany" in n || "de" in n || "frankfurt" in n || "герман" in n -> "🇩🇪"
            "nether" in n || "amsterdam" in n || "nl" in n || "нидерл" in n -> "🇳🇱"
            "finland" in n || "helsinki" in n || "финл" in n -> "🇫🇮"
            "france" in n || "paris" in n || "франц" in n -> "🇫🇷"
            "uk" in n || "london" in n || "britain" in n -> "🇬🇧"
            "usa" in n || "united states" in n || "new york" in n || "america" in n -> "🇺🇸"
            "singapore" in n || "sg" in n || "сингап" in n -> "🇸🇬"
            "japan" in n || "tokyo" in n || "япон" in n -> "🇯🇵"
            "poland" in n || "warsaw" in n || "польш" in n -> "🇵🇱"
            "turkey" in n || "istanbul" in n || "турц" in n -> "🇹🇷"
            "sweden" in n || "stockholm" in n -> "🇸🇪"
            else -> "🌐"
        }
    }
}
