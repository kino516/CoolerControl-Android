package com.example.kernelsustyleuikit.data.local

import android.content.Context
import com.example.kernelsustyleuikit.data.model.CcEndpoint
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.model.CcServer
import com.example.kernelsustyleuikit.templateApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * CoolerControl 客户端的本地配置。
 *
 * 沿用 UI Kit 既有的 SharedPreferences 方案（与 `SettingsRepositoryImpl` 同一模式），
 * 不引入 DataStore / Room。密码一律以 Keystore 密文形式落盘。
 */
class CcPrefs {

    private val prefs by lazy {
        templateApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ---------- 服务器 ----------

    var servers: List<CcServer>
        get() = decodeServers(prefs.getString(KEY_SERVERS, null))
        set(value) = prefs.edit().putString(KEY_SERVERS, encodeServers(value)).apply()

    var activeServerId: String?
        get() = prefs.getString(KEY_ACTIVE_SERVER, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_SERVER, value).apply()

    /** 上次成功使用的端点，冷启动时优先尝试 */
    var lastEndpointUrl: String?
        get() = prefs.getString(KEY_LAST_ENDPOINT, null)
        set(value) = prefs.edit().putString(KEY_LAST_ENDPOINT, value).apply()

    fun addServer(server: CcServer) {
        servers = servers.filterNot { it.id == server.id } + server
    }

    fun removeServer(serverId: String) {
        servers = servers.filterNot { it.id == serverId }
        if (activeServerId == serverId) activeServerId = null
    }

    fun activeServer(): CcServer? = servers.firstOrNull { it.id == activeServerId }

    // ---------- 连接开关 ----------

    /** 自动选择最快地址（内网优先） */
    var autoSelectEndpoint: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENDPOINT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENDPOINT, value).apply()

    // 明文 HTTP 固定允许（内网多为自签证书或无证书），不再提供开关。
    // 实际放行由 network_security_config.xml 的 base-config 决定。

    // ---------- 主页卡片 ----------

    /**
     * 卡片顺序。每项是一个卡片实例 id：
     * - `temp|<deviceUid>|<channel>`
     * - `fan|<deviceUid>|<channel>`
     * - `alert` / `mode` / `overview`
     *
     * 空列表表示尚未初始化，由界面按设备自动生成默认布局。
     */
    var dashboardCards: List<String>
        get() = decodeStringList(prefs.getString(KEY_DASHBOARD_CARDS, null))
        set(value) = prefs.edit().putString(KEY_DASHBOARD_CARDS, encodeStringList(value)).apply()

    // ---------- 冷却 / 监控区块顺序 ----------

    var coolingSections: List<String>
        get() = decodeStringList(prefs.getString(KEY_COOLING_SECTIONS, null))
            .ifEmpty { DEFAULT_COOLING_SECTIONS }
        set(value) = prefs.edit().putString(KEY_COOLING_SECTIONS, encodeStringList(value)).apply()

    var monitorSections: List<String>
        get() = decodeStringList(prefs.getString(KEY_MONITOR_SECTIONS, null))
            .ifEmpty { DEFAULT_MONITOR_SECTIONS }
        set(value) = prefs.edit().putString(KEY_MONITOR_SECTIONS, encodeStringList(value)).apply()

    // ---------- 显隐配置 ----------

    /** 时间温度表选中的通道，格式 `deviceUid|channel` */
    var monitorChannels: List<String>
        get() = decodeStringList(prefs.getString(KEY_MONITOR_CHANNELS, null))
        set(value) = prefs.edit().putString(KEY_MONITOR_CHANNELS, encodeStringList(value)).apply()

    /** 时间温度表的时间窗（分钟）：1 / 5 / 15 / 60 */
    var monitorRangeMinutes: Int
        get() = prefs.getInt(KEY_MONITOR_RANGE, 5)
        set(value) = prefs.edit().putInt(KEY_MONITOR_RANGE, value).apply()

    /** 冷却页隐藏的风扇，格式 `deviceUid|channel` */
    var hiddenFans: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_FANS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_FANS, value).apply()

    /** 冷却页隐藏的曲线 uid */
    var hiddenCurves: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_CURVES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_CURVES, value).apply()

    // ---------- 数据清理 ----------

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // ---------- 编解码 ----------

    private fun encodeServers(list: List<CcServer>): String {
        val array = JSONArray()
        list.forEach { server ->
            val endpoints = JSONArray()
            server.endpoints.forEach { endpoint ->
                endpoints.put(
                    JSONObject()
                        .put("url", endpoint.url)
                        .put("kind", endpoint.kind.name)
                )
            }
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("password", server.passwordEnc)
                    .put("endpoints", endpoints)
            )
        }
        return array.toString()
    }

    private fun decodeServers(raw: String?): List<CcServer> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val endpointsArray = obj.optJSONArray("endpoints")
                val endpoints = if (endpointsArray == null) {
                    emptyList()
                } else {
                    (0 until endpointsArray.length()).mapNotNull { j ->
                        val e = endpointsArray.optJSONObject(j) ?: return@mapNotNull null
                        val url = e.optString("url")
                        if (url.isBlank()) return@mapNotNull null
                        val kind = runCatching {
                            CcEndpointKind.valueOf(e.optString("kind"))
                        }.getOrDefault(CcEndpointKind.guess(url))
                        CcEndpoint(url = url, kind = kind)
                    }
                }
                CcServer(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    endpoints = endpoints,
                    passwordEnc = obj.optString("password"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeStringList(list: List<String>): String = JSONArray(list).toString()

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotEmpty() } }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "cc_settings"
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE_SERVER = "active_server"
        private const val KEY_LAST_ENDPOINT = "last_endpoint"
        private const val KEY_AUTO_ENDPOINT = "auto_select_endpoint"
        private const val KEY_DASHBOARD_CARDS = "dashboard_cards"
        private const val KEY_COOLING_SECTIONS = "cooling_sections"
        private const val KEY_MONITOR_SECTIONS = "monitor_sections"
        private const val KEY_MONITOR_CHANNELS = "monitor_channels"
        private const val KEY_MONITOR_RANGE = "monitor_range_minutes"
        private const val KEY_HIDDEN_FANS = "hidden_fans"
        private const val KEY_HIDDEN_CURVES = "hidden_curves"

        val DEFAULT_COOLING_SECTIONS = listOf("modes", "profiles", "fans", "stress")
        val DEFAULT_MONITOR_SECTIONS = listOf("chart", "alerts", "recent", "stats", "rename")

        fun newServerId(): String = UUID.randomUUID().toString()

        /** 卡片实例 id 构造 */
        fun cardIdTemp(deviceUid: String, channel: String) = "temp|$deviceUid|$channel"
        fun cardIdFan(deviceUid: String, channel: String) = "fan|$deviceUid|$channel"
        const val CARD_ALERT = "alert"
        const val CARD_MODE = "mode"
        const val CARD_OVERVIEW = "overview"
    }
}
