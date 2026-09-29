package com.example.kernelsustyleuikit.data.model

/**
 * 连接协议。登录页把 `http://` / `https://` 做成切换按钮，
 * 用户把整串网址粘进地址框时会自动识别并切换到这里。
 */
enum class CcScheme(val value: String) {
    Http("http"),
    Https("https");

    companion object {
        /** 解析 `http` / `http://` / `HTTP://` 等写法 */
        fun of(raw: String?): CcScheme? {
            val cleaned = raw?.trim()?.lowercase()?.removeSuffix("://") ?: return null
            if (cleaned.isEmpty()) return null
            return values().firstOrNull { it.value == cleaned }
        }

        /** 默认协议：内网多为自签/无证书走 http，外网走 https */
        fun defaultFor(kind: CcEndpointKind): CcScheme =
            if (kind == CcEndpointKind.Lan) Http else Https
    }
}

/** 端口不合法的原因 */
enum class CcPortError {
    /** 含非数字字符 */
    NotNumber,

    /** 超出 1..65535 */
    OutOfRange,
}

/** 登录页 / 添加服务器对话框的地址校验结果 */
enum class CcAddressError {
    /** 内外网都没填 */
    Empty,

    /** 填了内网地址但没填端口 */
    LanPortRequired,

    /** 端口不合法 */
    PortInvalid,
}

/**
 * 三段式服务器地址：`协议 + 主机 + 端口`。
 *
 * 端口单独存放（界面上是独立输入框），因此 [host] 里**不应**再带端口；
 * 但用户可能把 `10.0.0.53:11987` 甚至整串网址粘进主机框，
 * [normalized] / [toUrl] 会兜底剥离，保证最终请求的 URL 正确。
 */
data class CcAddress(
    val scheme: CcScheme = CcScheme.Http,
    val host: String = "",
    val port: String = "",
) {
    /** 主机为空即视为未填写（只填了端口不算） */
    val isBlank: Boolean get() = host.isBlank()

    /** 端口校验结果；端口为空返回 null（是否必填由调用方决定） */
    val portError: CcPortError?
        get() {
            val raw = port.trim()
            if (raw.isEmpty()) return null
            if (raw.any { !it.isDigit() }) return CcPortError.NotNumber
            val value = raw.toLongOrNull() ?: return CcPortError.OutOfRange
            return if (value in 1..65535) null else CcPortError.OutOfRange
        }

    /** 主机框里残留的协议前缀 */
    val embeddedScheme: CcScheme?
        get() = if (host.contains("://")) CcScheme.of(host.substringBefore("://")) else null

    /** 主机框里残留的端口 */
    val embeddedPort: String?
        get() = splitHost(host).second.takeIf { it.isNotEmpty() }

    /** 剥离主机框里残留的协议与端口（不改动显式填写的端口框） */
    fun normalized(): CcAddress {
        val raw = host.trim()
        val actualScheme = embeddedScheme ?: scheme
        val withoutScheme = if (raw.contains("://")) raw.substringAfter("://") else raw
        val (hostOnly, portOnly) = splitHost(withoutScheme)
        return copy(
            scheme = actualScheme,
            host = hostOnly.trim(),
            port = port.trim().ifEmpty { portOnly },
        )
    }

    /**
     * 组装成可直接请求的 URL。
     *
     * 主机为空或端口非法时返回 null；端口为空则不带端口
     * （由服务端按协议默认端口 80 / 443 处理）。
     */
    fun toUrl(): String? {
        val target = normalized()
        if (target.portError != null) return null

        val slash = target.host.indexOf('/')
        val authority = (if (slash >= 0) target.host.substring(0, slash) else target.host).trim()
        val path = if (slash >= 0) target.host.substring(slash) else ""
        if (authority.isEmpty()) return null

        val head = "${target.scheme.value}://$authority"
        return (if (target.port.isEmpty()) head else "$head:${target.port}") + path
    }

    companion object {

        /**
         * 校验内外网两个地址框。
         *
         * 规则（与产品约定一致）：两个地址填任意一个即可登录；
         * 只要填了内网地址，端口就是必填的；外网端口可留空。
         */
        fun validate(lan: CcAddress, wan: CcAddress): CcAddressError? {
            val lanTarget = lan.normalized()
            val wanTarget = wan.normalized()
            if (lanTarget.isBlank && wanTarget.isBlank) return CcAddressError.Empty
            if (lanTarget.portError != null || wanTarget.portError != null) {
                return CcAddressError.PortInvalid
            }
            if (!lanTarget.isBlank && lanTarget.port.isEmpty()) {
                return CcAddressError.LanPortRequired
            }
            return null
        }

        /**
         * 组装端点列表，内网在前。
         *
         * kind 由输入框位置决定（填在内网框就是内网），不再按 IP 段猜测 ——
         * 否则内网用域名时会被判成外网，导致内网优先与 Wi-Fi 绑定失效。
         */
        fun endpoints(lan: CcAddress, wan: CcAddress): List<CcEndpoint> = buildList {
            lan.normalized().toUrl()?.let { add(CcEndpoint(it, CcEndpointKind.Lan)) }
            wan.normalized().toUrl()?.let { add(CcEndpoint(it, CcEndpointKind.Wan)) }
        }

        /** 解析整串网址，如 `https://cc.example.com:666` */
        fun parse(input: String, fallbackScheme: CcScheme = CcScheme.Http): CcAddress {
            val raw = input.trim()
            val hasScheme = raw.contains("://")
            val scheme = if (hasScheme) {
                CcScheme.of(raw.substringBefore("://")) ?: fallbackScheme
            } else {
                fallbackScheme
            }
            val rest = if (hasScheme) raw.substringAfter("://") else raw
            val (hostOnly, portOnly) = splitHost(rest)
            return CcAddress(scheme = scheme, host = hostOnly.trim(), port = portOnly)
        }

        /**
         * 主机框的输入处理：识别「整串网址」并拆成三段。
         *
         * 返回 null 表示按普通主机名处理（调用方直接把原文写回主机字段）。
         * 只有两种情况会拆分，避免用户逐字输入 `host:1` 时被抢走后续按键：
         * 1. 输入里出现了 `://`（完整网址）；
         * 2. 一次插入多个字符（粘贴 / 输入法整词上屏）。
         */
        fun parseHostInput(
            newValue: String,
            oldValue: String,
            fallbackScheme: CcScheme,
        ): CcAddress? {
            val raw = newValue.trim()
            if (raw.isEmpty()) return null
            val hasScheme = raw.contains("://")
            val pasted = newValue.length - oldValue.length > 1
            if (!hasScheme && !pasted) return null
            return parse(raw, fallbackScheme)
        }

        /**
         * 在 `host[:port][/path]` 里切出端口。
         *
         * **路径部分直接丢弃**：网页端地址常带 `/#/home` 这类前端路由
         * （如 `http://10.0.0.53:11987/#/home`），它对 API 请求没有意义，
         * 粘进来会让主机名变脏。
         */
        private fun splitHost(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            val slash = trimmed.indexOf('/')
            val authority = if (slash >= 0) trimmed.substring(0, slash) else trimmed
            if (authority.endsWith("]")) return authority to "" // IPv6 无端口
            val colon = authority.lastIndexOf(':')
            if (colon < 0) return authority to ""
            val maybePort = authority.substring(colon + 1)
            if (maybePort.isEmpty() || maybePort.any { !it.isDigit() }) return authority to ""
            return authority.substring(0, colon) to maybePort
        }
    }
}
