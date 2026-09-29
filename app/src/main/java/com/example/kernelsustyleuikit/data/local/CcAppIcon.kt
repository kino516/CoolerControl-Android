package com.example.kernelsustyleuikit.data.local

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.templateApp

/**
 * 桌面图标样式。
 *
 * 每个样式对应 Manifest 里一个 `activity-alias`，指向同一个 MainActivity，
 * 只有 `android:icon` 不同。**新增图标只需三步**：
 * 1. 在 `colors.xml` 加一个背景色；
 * 2. 在 `mipmap-anydpi/` 加一个 adaptive-icon 描述文件（前景复用
 *    `@drawable/ic_launcher_foreground`）；
 * 3. 在 `AndroidManifest.xml` 加一个 alias，并在下面的 [OPTIONS] 里加一项。
 *
 * **alias 名必须带 `${applicationId}` 前缀**（见 Manifest 注释）：默认的隐式
 * 展开会用 namespace，而这里是用 `context.packageName` 拼组件名的，
 * 两者不一致时 `setComponentEnabledSetting` 会静默失败、图标毫无反应。
 */
data class CcIconOption(
    /** 稳定标识，存进设置用 */
    val id: String,
    /** Manifest 中 alias 的类名后缀 */
    val alias: String,
    @StringRes val labelRes: Int,
    /** 预览用的背景色，与 adaptive-icon 的背景层保持一致 */
    val background: Color,
)

object CcAppIcon {

    val OPTIONS: List<CcIconOption> = listOf(
        CcIconOption("light", ".LauncherLight", R.string.cc_icon_light, Color(0xFFFFFFFF)),
        CcIconOption("dark", ".LauncherDark", R.string.cc_icon_dark, Color(0xFF000000)),
        CcIconOption("indigo", ".LauncherIndigo", R.string.cc_icon_indigo, Color(0xFF1A237E)),
        CcIconOption("teal", ".LauncherTeal", R.string.cc_icon_teal, Color(0xFF00695C)),
        CcIconOption("amber", ".LauncherAmber", R.string.cc_icon_amber, Color(0xFFE65100)),
        CcIconOption("violet", ".LauncherViolet", R.string.cc_icon_violet, Color(0xFF4A148C)),
    )

    val DEFAULT_ID: String = OPTIONS.first().id

    private fun optionOf(id: String?): CcIconOption =
        OPTIONS.firstOrNull { it.id == id } ?: OPTIONS.first()

    /**
     * 读取当前生效的图标样式。
     *
     * 以系统里 alias 的实际启用状态为准（而不是本地存的偏好），
     * 这样即便用户在系统设置里动过，界面显示的也是真实状态。
     */
    fun currentId(): String {
        val context = templateApp
        val manager = context.packageManager
        val enabled = OPTIONS.firstOrNull { option ->
            manager.getComponentEnabledSetting(
                ComponentName(context, context.packageName + option.alias)
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return enabled?.id ?: DEFAULT_ID
    }

    fun select(id: String) {
        val context = templateApp
        val manager = context.packageManager
        val target = optionOf(id)

        OPTIONS.forEach { option ->
            val state = if (option.id == target.id) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            manager.setComponentEnabledSetting(
                ComponentName(context, context.packageName + option.alias),
                state,
                // flags = 0：不传 DONT_KILL_APP，让进程重启后 launcher 重新读取图标
                0,
            )
        }
    }

    /**
     * 启动自检：保证**恰好一个** alias 处于启用状态。
     *
     * 这一步不能省。launcher 靠 `LAUNCHER` intent-filter 找图标：
     * - 一个都没有 → 桌面图标整个消失，且 App 已装却「找不到」
     * - 同时启用多个 → launcher 解析歧义，同样表现异常
     *
     * 历史包版本曾因 alias 命名不一致导致切换静默失败、状态残留成多个启用，
     * 所以每次启动都收敛一次。正常情况下这只是两次读操作，开销可忽略。
     */
    fun ensureValid() {
        val context = templateApp
        val manager = context.packageManager

        val enabled = OPTIONS.filter { option ->
            manager.getComponentEnabledSetting(
                ComponentName(context, context.packageName + option.alias)
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        if (enabled.size != 1) {
            // 多于一个时保留第一个，全无时回到默认，两种情况都重新收敛
            select(enabled.firstOrNull()?.id ?: DEFAULT_ID)
        }
    }
}
