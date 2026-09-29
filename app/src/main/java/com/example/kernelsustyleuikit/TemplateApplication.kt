package com.example.kernelsustyleuikit

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.kernelsustyleuikit.data.local.CcAppIcon
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

lateinit var templateApp: TemplateApplication

class TemplateApplication : Application(), ViewModelStoreOwner {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    /**
     * 全局 OkHttp 客户端。
     *
     * 用 `by lazy` 而不是 `lateinit`：`onCreate` 在 Direct Boot（未解锁）时会
     * 提前 return，那条路径上 `lateinit` 永远不会被赋值，之后任何访问都会抛
     * `UninitializedPropertyAccessException`。惰性初始化还顺带规避了
     * `cacheDir` 在未解锁时不可用的问题（首次访问必然已解锁）。
     */
    val okhttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
            .addInterceptor { block ->
                block.proceed(
                    block.request().newBuilder()
                        .header("User-Agent", "KernelSUStyleUIKit/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }
            .build()
    }

    private val appViewModelStore by lazy { ViewModelStore() }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        templateApp = this

        if (!isUserUnlocked()) {
            return
        }

        // 收敛桌面图标状态：保证恰好一个 activity-alias 启用。
        // 全部禁用会让桌面图标整个消失，多个同时启用则让 launcher 解析歧义。
        CcAppIcon.ensureValid()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val prefs = this.getSharedPreferences("settings", MODE_PRIVATE)
            val enable = prefs.getBoolean("enable_predictive_back", false)
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
