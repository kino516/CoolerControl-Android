# 二次开发指南

[English](GETTING_STARTED.md) | 简体中文

本文面向**在本仓库基础上继续开发**的人：环境怎么搭、代码放在哪、常见改造怎么做、发布包怎么签。

> 本应用是**遥控端**，不能独立工作 —— 需要先在电脑上运行 [CoolerControl](https://coolercontrol.org) 守护进程。
> 面向使用者的操作说明见 [CoolerControl-使用说明.md](CoolerControl-使用说明.md)。

---

## 1. 准备环境

| 项 | 要求 | 备注 |
|---|---|---|
| JDK | **21** | 根目录 `build.gradle.kts` 中 `sourceCompatibility` / `targetCompatibility` 均为 `VERSION_21`，低于 21 无法编译 |
| Android SDK | **API 37**、**Build Tools 37.0.0** | 在 SDK Manager 中勾选安装；缺失会在配置阶段直接失败 |
| Gradle | **9.5.1** | 由 wrapper 提供，无需单独安装 |

```bash
git clone https://github.com/kino516/CoolerControl-Android.git
cd CoolerControl-Android

# 指向本机 Android SDK（Windows 写法示例：sdk.dir=D:/Android/Sdk）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/CoolerControl_1.0.0_1-debug.apk`

> 文件名格式为 `CoolerControl_<versionName>_<versionCode>`，由 `app/build.gradle.kts` 中的 `base.archivesName` 决定。
> `local.properties` 只用于提供 SDK 路径，已在 `.gitignore` 中，**不要提交**。

---

## 2. 代码放在哪

```text
app/src/main/java/com/example/kernelsustyleuikit/
├── TemplateApplication.kt
├── data/
│   ├── CcGraph.kt                 轻量服务定位器（lazy 单例，未引入 DI 框架）
│   ├── local/                     SharedPreferences（CcPrefs）、Keystore 加密（CcCrypto）、桌面图标（CcAppIcon）
│   ├── model/                     领域模型（设备 / 状态 / 报警 / 模式 / 曲线 / 统计 / 服务器）
│   ├── remote/                    CcApiClient（REST）、CcCookieJar、CcSseClient（手写 SSE）、CcJson（org.json → 模型）
│   ├── repository/                CcRepository 是唯一数据源；SettingsRepository 管本地偏好
│   └── session/                   会话管理（CcSessionManager）与多端点择优（CcEndpointProbe）
└── ui/
    ├── MainActivity.kt            主界面骨架（Pager + 二级页 NavDisplay）
    ├── UiMode.kt                  界面风格枚举与 LocalUiMode
    ├── component/                 通用组件：cc/（跨风格）、miuix/、material/、bottombar/、dialog/、liquid/ …
    ├── navigation3/               Routes.kt（路由声明）、Navigator.kt、DeepLinkResolver.kt
    ├── screen/<feature>/          各功能页，一个功能一个目录
    ├── theme/                     主题与配色
    ├── util/                      格式化与平台工具
    └── viewmodel/                 各页面 ViewModel
```

### 包名为什么和 applicationId 不一样

| 项 | 值 | 作用范围 |
|---|---|---|
| `namespace`（代码包名） | `com.example.kernelsustyleuikit` | 只影响 Kotlin 源码里的 `package` / `import` |
| `applicationId`（应用标识） | `com.coolercontrol.mobile` | 安装包身份、系统与应用市场识别 |

两者不一致是**刻意保留**的：`namespace` 沿用上游 UI Kit，改动它需要重写全部 143 个 Kotlin 文件的 `package` / `import` 及目录结构，风险高而收益低。对外身份由 `applicationId` 决定，改它通常就够了。

### 数据流约定

- **`CcRepository` 是唯一数据源**：页面只从 ViewModel 取数，不要直接持有 `CcApiClient`
- **ViewModel 一律无参构造**，依赖走 `CcGraph`。不要用 `AndroidViewModel`：实测它在 Pager 页面内会因 `CreationExtras` 缺 `APPLICATION_KEY` 闪退
- **SSE 使用进程级作用域**，不随页面销毁而断开

---

## 3. 常见改造任务

### 3.1 改应用名、版本号、图标

| 想改什么 | 改哪里 |
|---|---|
| 应用名 | `app/src/main/res/values/strings.xml` 的 `app_name`（`translatable="false"`，一处即可） |
| 版本号 | 根目录 `build.gradle.kts` 的 `managerVersionCode` / `managerVersionName` |
| 应用图标 | `app/src/main/res/mipmap-*/`（各密度 launcher）、`drawable/ic_launcher_foreground.png`、`drawable/ic_launcher_monochrome.xml` |
| 应用内 Logo | `drawable/ic_logo.png`、`raw/logo.svg` |
| 桌面图标可选样式 | `data/local/CcAppIcon.kt` |

图标素材源文件在仓库根目录 `icon/`（含 1024 / 512 PNG 与 ico），改完按密度导出到 `mipmap-*` 即可。

### 3.2 新增一个页面

**主界面上的页面**（像主页 / 冷却 / 监控那样可左右滑动）与**二级页面**（像设置 / 服务器列表那样压栈）改法不同。

#### 二级页面

1. 建目录与文件：

```text
ui/screen/foo/
├── FooScreen.kt      入口 Composable
├── FooUiState.kt     页面状态（data class + 默认值）
├── FooMiuix.kt       Miuix 风格实现
└── FooMaterial.kt    Material 风格实现
```

2. `FooScreen.kt` 中按当前风格分发 —— 两套实现共用同一份状态与回调，只有渲染层不同：

```kotlin
@Composable
fun FooScreen(navigator: Navigator) {
    val viewModel = viewModel<FooViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (LocalUiMode.current) {
        UiMode.Miuix -> FooMiuix(uiState, navigator)
        UiMode.Material -> FooMaterial(uiState, navigator)
    }
}
```

3. 加 ViewModel：`ui/viewmodel/FooViewModel.kt`，无参构造，需要仓库时用 `CcGraph.repository`。
4. 声明路由：在 `ui/navigation3/Routes.kt` 中按现有写法加一个目的地：

```kotlin
@Parcelize
@Serializable
data object Foo : Route
```

5. 挂载：在 `ui/MainActivity.kt` 的路由分支里补上 `Route.Foo -> FooScreen(navController)`。

#### 主界面页面

主页 / 冷却 / 监控由 `HorizontalPager` 承载，在 `ui/MainActivity.kt` 中按**页索引**挂载（`0 -> HomePager(...)` 等）。新增一页需要同时调整：Pager 页数、挂载分支、底部栏的页列表，以及 `MainActivity` 中依赖页索引的逻辑（例如"在监控页查看"用的 `MONITOR_PAGE_INDEX`）。

### 3.3 接入新的 daemon 接口

1. 在 `data/remote/CcApiClient.kt` 加请求方法（返回 `Result<T>`，统一走内部的 `execute(...)`）
2. 在 `data/model/` 加模型，并在 `data/remote/CcJson.kt` 写解析
3. 在 `data/repository/CcRepository.kt` 暴露为 `StateFlow` 或挂起函数
4. 页面从 ViewModel 取数

字段以官方 OpenAPI 为准。仓库中**没有**内置该文件（调试用的 `_ref/` 已被 `.gitignore` 排除），需要时从 CoolerControl 项目获取后本地放置。

### 3.4 界面风格（Miuix / Material）

- 当前风格由 `LocalUiMode` 提供，默认值见 `ui/UiMode.kt` 的 `UiMode.DEFAULT_VALUE`
- 模糊、悬浮底栏、液态玻璃等开关的默认值在 `data/repository/SettingsRepositoryImpl.kt`
- 跨风格通用组件放 `ui/component/cc/`；仅单一风格使用的组件放 `ui/component/miuix/` 或 `ui/component/material/`

### 3.5 国际化

文案位于 `app/src/main/res/values*/strings.xml`（已含 50 余种语言）。默认 `values/strings.xml` 为英文，简体中文在 `values-zh-rCN/`。新增文案请至少同时补 `values/` 与 `values-zh-rCN/`。

---

## 4. 签名与发布

release 的签名由 `org.lsposed.lsplugin.apksign` 插件负责，它**只读 Gradle 属性**。在用户级 `~/.gradle/gradle.properties` 中配置（该文件不进仓库）：

```properties
KEYSTORE_FILE=/absolute/path/to/your.jks
KEYSTORE_PASSWORD=******
KEY_ALIAS=******
KEY_PASSWORD=******
```

字段名见仓库根目录的 `sign.example.properties`。生成密钥库：

```bash
keytool -genkeypair -v -keystore your.jks \
  -alias your-alias -keyalg RSA -keysize 4096 -validity 10000
```

然后构建：

```bash
./gradlew :app:assembleRelease
```

> **写在 `local.properties` 里是无效的** —— 该文件是 AGP 用来读 SDK 路径的，插件看不到它。
> 也可以用命令行临时传入：
> `./gradlew :app:assembleRelease -PKEYSTORE_FILE=... -PKEYSTORE_PASSWORD=... -PKEY_ALIAS=... -PKEY_PASSWORD=...`

**未配置签名时 release 依然能构建成功，但会回退到 Android 默认的 debug 签名**（实测证书为 `CN=Android Debug`）。该证书是公开的，任何人都能用它冒充你发布更新，**请勿用于正式分发**。

> 密钥库一旦丢失，就无法再覆盖更新已发布的应用，请连同密码一起备份。

---

## 5. 提交前自查

- `./gradlew :app:assembleDebug :app:assembleRelease` 能通过
- 没有把真实 keystore 或密码提交进仓库（`.gitignore` 已排除 `keystore/`、`*.jks`、`*.keystore`、`local.properties`）
- 新增界面文案已补 `values-zh-rCN`

推送或提 PR 时，GitHub Actions 会自动跑一遍构建，配置见 `.github/workflows/build.yml`。

---

## 6. 许可

本项目采用 **GNU General Public License v3.0**。它是衍生作品：界面框架来自 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)，同样遵循 GPL-3.0。

因此**二次开发后对外分发时，必须同样以 GPL-3.0 开源并提供完整源码**。
