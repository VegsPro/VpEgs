package com.vpegs.ms.core

/**
 * Built-in VpEgs Config — single remote list (no multi-site scraping).
 */
object BuiltInConfigSources {
    const val SUB_ID = "vpegs-builtin"
    const val SUB_NAME = "VpEgs Config"
    /** Canonical download URL (JSON: { configs: ["vless://...", ...] }). */
    const val SUB_URL =
        "https://vix.mstrblock.workers.dev/download/VpEgsConfig/config.json?key=root"

    /** Keep UI responsive. */
    const val MAX_SERVERS = 150
}
