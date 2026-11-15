package com.vpegs.ms.core

import android.content.Context
import com.vpegs.ms.data.Protocol
import com.vpegs.ms.data.SecureStore
import com.vpegs.ms.data.ServerNode
import java.util.UUID

/**
 * Import share links, Xray JSON, WireGuard/AmneziaWG .conf.
 * WG/Amnezia stored encrypted; active tunnel still Xray-based.
 */
object ConfigImporter {

    data class Result(
        val servers: List<ServerNode>,
        val configs: Map<String, String>,
        val message: String,
        val subscriptionUrl: String? = null
    )

    fun importText(ctx: Context, raw: String, subName: String = "Импорт"): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result(emptyList(), emptyMap(), "Пусто")

        if (text.startsWith("http://") || text.startsWith("https://")) {
            return Result(emptyList(), emptyMap(), "URL подписки", subscriptionUrl = text)
        }

        if (text.contains("[Interface]") && text.contains("[Peer]")) {
            val id = "wg-" + UUID.randomUUID().toString().take(8)
            val isAmnezia = text.contains("Jc") || text.contains("Amnezia", ignoreCase = true) ||
                text.contains("H1") && text.contains("H2")
            val node = ServerNode(
                id = id,
                name = if (isAmnezia) "AmneziaWG · $subName" else "WireGuard · $subName",
                country = "",
                flag = "🔐",
                protocol = Protocol.VLESS, // placeholder enum; protocol display via name
                address = extractWgEndpointHost(text) ?: "wireguard",
                port = extractWgEndpointPort(text),
                rawConfig = text,
                subscriptionId = "import-local"
            )
            SecureStore.putConfig(ctx, id, text)
            return Result(
                listOf(node),
                emptyMap(),
                "WireGuard/AmneziaWG сохранён в шифрованном хранилище. " +
                    "Туннель VpEgs сейчас на Xray (VLESS/VMess/Trojan/SS). " +
                    "Отдельный WG-core — следующий этап."
            )
        }

        val parsed = LinkParser.parseSubscriptionBody(text, "import-local", subName)
        if (parsed.isEmpty()) {
            LinkParser.parse(text, "import-local")?.let {
                SecureStore.putConfig(ctx, it.node.id, it.configJson)
                return Result(listOf(it.node), mapOf(it.node.id to it.configJson), "Импорт: 1 сервер")
            }
            return Result(emptyList(), emptyMap(), "Не удалось распознать конфиг")
        }
        val servers = parsed.map { it.node }
        val configs = parsed.associate { it.node.id to it.configJson }
        configs.forEach { (id, json) -> SecureStore.putConfig(ctx, id, json) }
        return Result(servers, configs, "Импорт: ${servers.size} сервер(ов)")
    }

    private fun extractWgEndpointHost(conf: String): String? {
        val line = conf.lineSequence().firstOrNull { it.trim().startsWith("Endpoint", ignoreCase = true) }
            ?: return null
        val v = line.substringAfter("=").trim()
        return v.substringBefore(':').trim().ifBlank { null }
    }

    private fun extractWgEndpointPort(conf: String): Int {
        val line = conf.lineSequence().firstOrNull { it.trim().startsWith("Endpoint", ignoreCase = true) }
            ?: return 51820
        val v = line.substringAfter("=").trim()
        return v.substringAfter(':', "51820").toIntOrNull() ?: 51820
    }
}
