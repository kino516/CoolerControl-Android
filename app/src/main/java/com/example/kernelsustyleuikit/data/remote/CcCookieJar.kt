package com.example.kernelsustyleuikit.data.remote

import android.content.Context
import com.example.kernelsustyleuikit.data.local.CcCrypto
import com.example.kernelsustyleuikit.templateApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 每个端点一份会话 Cookie，并持久化到 SharedPreferences。
 *
 * 内外网两个端点**是否共享会话取决于反向代理拓扑**（单实例直连时通用，
 * 多后端时会返回 401），因此不能假设通用：会话按 (serverId, endpointUrl) 分别保存，
 * 切换端点时先尝试复用已知会话，失败再静默重登。
 */
class CcCookieJar(
    private val baseUrl: HttpUrl,
    /** 形如 `serverId|endpointUrl`，保证不同端点互不覆盖 */
    private val storeKey: String,
) : CookieJar {

    private val cache = ConcurrentHashMap<String, Cookie>()

    init {
        load()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        cookies.forEach { cache[it.name] = it }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cache.values.filter { it.matches(url) }

    /** 导出为 `Cookie` 头，用于跨端点尝试复用会话 */
    fun exportHeader(): String? =
        cache.values
            .filter { it.matches(baseUrl) }
            .joinToString("; ") { "${it.name}=${it.value}" }
            .takeIf { it.isNotEmpty() }

    /** 导入来自其它端点的 Cookie 头（拓扑共享会话时可零成本切换） */
    fun importHeader(header: String) {
        if (header.isBlank()) return
        header.split(';').forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@forEach
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            cache[name] = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(baseUrl.host)
                .path("/")
                .build()
        }
        persist()
    }

    fun clear() {
        cache.clear()
        // 直接删键而不是写入空串，避免 SharedPreferences 里留下无意义的空记录
        prefs().edit().remove(storeKey).apply()
    }

    fun isEmpty(): Boolean = cache.isEmpty()

    private fun prefs() =
        templateApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist() {
        val encoded = cache.values.joinToString("\n") { it.toString() }
        // 复用密码同一套 Keystore AES-GCM：会话 Cookie 与密码**同等敏感** ——
        // 它能绕过密码直接调用 daemon API，明文落盘会让「密码已加密」失去意义
        val stored = CcCrypto.encrypt(encoded) ?: return
        prefs().edit().putString(storeKey, stored).apply()
    }

    private fun load() {
        val saved = prefs().getString(storeKey, null) ?: return
        // 兼容历史明文数据：解不开就按明文解析，下次 persist() 时自动升级为密文
        val plain = CcCrypto.decrypt(saved) ?: saved
        plain.split('\n').forEach { line ->
            if (line.isBlank()) return@forEach
            val cookie = Cookie.parse(baseUrl, line) ?: return@forEach
            cache[cookie.name] = cookie
        }
    }

    private companion object {
        const val PREFS_NAME = "cc_sessions"
    }
}
