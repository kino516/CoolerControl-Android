package com.example.kernelsustyleuikit.data.model

/** 端点类型：内网 / 外网 */
enum class CcEndpointKind {
    Lan,
    Wan;

    companion object {
        /** 私网 IP 段与 localhost 判定为内网，其余（域名等）视为外网 */
        fun guess(url: String): CcEndpointKind {
            val host = url.substringAfter("://", url)
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
            val isLan = host == "localhost" ||
                host == "127.0.0.1" ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
            return if (isLan) Lan else Wan
        }
    }
}

/** 一个可访问地址 */
data class CcEndpoint(
    val url: String,
    val kind: CcEndpointKind = CcEndpointKind.guess(url),
)

/**
 * 一台服务器：可配多个端点（内网 + 外网），密码以 Keystore AES-GCM 加密后保存。
 *
 * 密码只以密文形式存在于本模型中（字段名带 `Enc` 以示区分）。
 */
data class CcServer(
    val id: String,
    val name: String,
    val endpoints: List<CcEndpoint>,
    val passwordEnc: String,
)

/** 端点探测结果 */
data class CcProbeResult(
    val endpoint: CcEndpoint,
    val reachable: Boolean,
    val latencyMs: Long,
)
