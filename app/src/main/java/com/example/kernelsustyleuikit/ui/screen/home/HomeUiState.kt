package com.example.kernelsustyleuikit.ui.screen.home

import androidx.compose.runtime.Immutable
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.ui.component.cc.CcSegment

/**
 * 主页（卡片仪表盘）状态。
 *
 * 卡片顺序由本地配置持久化；每张卡片的 id 形如：
 * `temp|<deviceUid>|<channel>`、`fan|<deviceUid>|<channel>`、`alert`、`mode`、`overview`。
 */
@Immutable
data class HomeUiState(
    val connected: Boolean = false,
    val errorMessage: String? = null,
    val daemonVersion: String? = null,
    /** 形如「内网 · 10.0.0.53 · 8ms」 */
    val endpointLabel: String? = null,
    val sseConnected: Boolean = false,
    val deviceCount: Int = 0,
    val modeCount: Int = 0,
    val alertTotal: Int = 0,
    val alertActive: Int = 0,
    val cards: List<HomeCardUi> = emptyList(),
    /** 采样节拍，驱动趋势线重绘 */
    val historyTick: Long = 0L,
    /**
     * 已连上服务器、但设备信息还没拉回来（冷启动后的头一两秒）。
     *
     * 此时显示加载态而不是空列表 —— 否则冷启动会先闪一下「没有设备」，
     * 等元数据到了再突然冒出十几张卡片。
     */
    val isLoading: Boolean = false,
)

@Immutable
sealed interface HomeCardUi {
    val id: String

    /** 主标题（左上） */
    val title: String

    /** 副标题（标题下方） */
    val subtitle: String?

    /** 温度卡：通道名 + 来源设备 + 当前温度 + 近期趋势线 */
    @Immutable
    data class Temperature(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        val deviceUid: String,
        val channel: String,
        /** 设备类型，用于温度四档配色判定 */
        val deviceType: String,
        val value: Double?,
        val trend: List<Double>,
    ) : HomeCardUi

    /** 风扇卡：风扇名 + 所属设备 + 转速 + 占空比进度条 */
    @Immutable
    data class Fan(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        val deviceUid: String,
        val channel: String,
        val rpm: Int?,
        val duty: Double?,
    ) : HomeCardUi

    /** 报警卡：报警总数 + 是否全部正常 + 最近日志 */
    @Immutable
    data class AlertSummary(
        override val id: String,
        override val title: String,
        val total: Int,
        val active: Int,
        val recent: List<CcAlertLog>,
    ) : HomeCardUi {
        override val subtitle: String? get() = null
    }

    /** 模式卡：可直接切换的散热模式列表，交互与冷却页保持一致 */
    @Immutable
    data class ModeSummary(
        override val id: String,
        override val title: String,
        val currentMode: String?,
        /** 当前模式的 uid，供分段控件高亮 */
        val currentModeUid: String? = null,
        val modeCount: Int,
        /** 可切换的模式；为空时退回纯展示（无可用模式） */
        val modes: List<CcSegment> = emptyList(),
    ) : HomeCardUi {
        override val subtitle: String? get() = null
    }

    /** 网络状态卡：端点、连接状态、设备与报警数 */
    @Immutable
    data class Overview(
        override val id: String,
        override val title: String,
        val deviceCount: Int,
        val alertCount: Int,
        val daemonVersion: String?,
        val endpointLabel: String?,
        /** 是否已连上服务器 */
        val connected: Boolean = false,
        /** SSE 实时通道是否正常 */
        val sseConnected: Boolean = false,
    ) : HomeCardUi {
        override val subtitle: String? get() = null
    }
}

/** 主页回调集合 */
@Immutable
data class HomeActions(
    val onTemperatureClick: (HomeCardUi.Temperature) -> Unit = {},
    val onFanClick: (HomeCardUi.Fan) -> Unit = {},
    val onAlertClick: () -> Unit = {},
    val onModeClick: () -> Unit = {},
    /** 直接切换散热模式（首页模式卡的分段控件） */
    val onSwitchMode: (String) -> Unit = {},
    val onOverviewClick: () -> Unit = {},
)
