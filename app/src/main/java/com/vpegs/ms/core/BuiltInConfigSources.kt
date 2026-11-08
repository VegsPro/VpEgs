package com.vpegs.ms.core

/**
 * Built-in free config mirrors (public GitHub aggregators).
 * Quality is not guaranteed — many nodes die quickly.
 */
object BuiltInConfigSources {
    const val SUB_ID = "vpegs-builtin"
    const val SUB_NAME = "VpEgs Config"
    const val SUB_URL = "vpegs://builtin"

    /**
     * Raw text / base64 subscription URLs aggregated from open GitHub projects.
     * Order: prefer mixed protocol lists that update often.
     */
    val urls: List<String> = listOf(
        // pog7x aggregator mirrors (updated ~hourly)
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/1.txt",
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/3.txt",
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/4.txt",
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/6.txt",
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/10.txt",
        "https://raw.githubusercontent.com/pog7x/vpn-configs/master/githubmirror/22.txt",
        // Alirewa — base64 top lists
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/sub1.txt",
        "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/sub2.txt",
        // Extra public collectors
        "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/sub/sub_merge.txt",
    )

    const val MAX_SERVERS = 120 // keep UI usable
}
