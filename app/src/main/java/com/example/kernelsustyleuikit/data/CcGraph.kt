package com.example.kernelsustyleuikit.data

import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.repository.CcRepository
import com.example.kernelsustyleuikit.data.session.CcSessionManager

/**
 * 轻量服务定位器（不引入任何 DI 框架，与 UI Kit 的 `templateApp` 单例风格一致）。
 *
 * 全部 `lazy`：首次访问时才创建，此时 `TemplateApplication.onCreate` 已执行完毕，
 * `templateApp` 可用。
 */
object CcGraph {

    val prefs: CcPrefs by lazy { CcPrefs() }

    val session: CcSessionManager by lazy { CcSessionManager(prefs) }

    val repository: CcRepository by lazy { CcRepository(prefs, session) }
}
