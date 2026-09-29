# CoolerControl for Android

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B-3DDC84.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg)]()

[CoolerControl](https://coolercontrol.org) 的第三方 Android 客户端 —— **在手机上掌控电脑的散热**。

实时查看 CPU / 硬盘 / 主板温度，远程切换散热模式、调节风扇转速、配置温度报警与压力测试。

> ⚠️ 本应用是**遥控端**，不能独立工作。需要先在电脑上运行 CoolerControl 守护进程（daemon）。

---

## 特性

| 模块 | 能力 |
|---|---|
| **主页** | 卡片式仪表盘：温度、风扇、报警、当前模式、网络状态，支持拖动排序与按需增减 |
| **冷却** | 切换散热模式、套用风扇曲线、手动调速、运行压力测试、电源模式联动 |
| **监控** | 实时温度曲线（1/5/15/60 分钟窗口）、报警规则与静默、历史统计、设备与硬件详情 |
| **重命名** | 设备与通道改名，立即同步到网页端 |
| **多服务器** | 同时保存多台服务器，内外网双地址自动择优 |
| **外观** | 两套界面风格（Miuix / Material）、深色模式、Monet 取色、15 种强调色、6 款桌面图标 |

### 连接可靠性

- **内外网双地址**：分别填写内网与外网地址，内网不通时自动切换到外网
- **连接诊断**：逐地址探测可达性与延迟，支持手动强制指定
- **实时推送**：基于 SSE 的长连接，断线自动退避重连
- **网络感知**：切换 Wi-Fi / 移动数据时自动重新择优

### 安全

- **密码加密落盘**：使用 Android Keystore + AES-GCM，密钥不出安全芯片
- **会话隔离**：会话 Cookie 按 `服务器 + 端点` 分别保存，同样加密存储
- **备份排除**：凭据不会进入云备份
- **最小权限**：仅申请网络相关权限

---

## 截图

> 截图待补充。欢迎提交 PR 帮忙补充各界面截图。

---

## 系统要求

| 项目 | 要求 |
|---|---|
| Android 版本 | **12 (API 31)** 及以上 |
| 推荐版本 | Android 16+（内网访问更稳定，见下方说明） |
| 存储空间 | 约 10 MB |
| 服务端 | 已安装并运行 [CoolerControl](https://coolercontrol.org) 守护进程 |

### ⚠️ 关于 Android 16+ 的「本地网络」权限

从 Android 16 (API 36) 起，访问局域网需要 **`ACCESS_LOCAL_NETWORK`** 运行时权限。

**缺少该权限时，系统会静默丢弃发往 `10.x` / `192.168.x` 等内网地址的流量** ——
表现为「连接超时」，但用浏览器访问同一地址却完全正常。这是最容易让人困惑的一个坑。

应用内提供了引导入口，也可以手动开启：
**系统设置 → 应用 → CoolerControl → 权限 → 本地网络**

---

## 构建

### 环境要求

- JDK **21**
- Android SDK **API 37**（Build Tools 37.0.0）
- Gradle 由 wrapper 提供，无需单独安装

### 步骤

```bash
git clone https://github.com/<你的用户名>/<仓库名>.git
cd <仓库名>

# 指向你的 Android SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 发布版签名

release 构建通过 `local.properties` 或 `~/.gradle/gradle.properties` 读取签名配置：

```properties
KEYSTORE_FILE=/absolute/path/to/your.jks
KEYSTORE_PASSWORD=******
KEY_ALIAS=******
KEY_PASSWORD=******
```

生成密钥库：

```bash
keytool -genkeypair -v -keystore your.jks \
  -alias your-alias -keyalg RSA -keysize 4096 -validity 10000
```

> ⚠️ 未配置时 release 会回退到 **debug 签名**，该签名是公开的，**请勿用于正式发布**。
>
> 参考字段名见 `sign.example.properties`。

---

## 使用

1. 在电脑上启动 CoolerControl 守护进程，记下访问地址（如 `http://10.0.0.53:11987`）
2. 打开应用，填写地址与密码（用户名固定 `CCAdmin`）
3. 地址由三部分组成：**协议** + **主机** + **端口**
   - 协议可在 `http` / `https` 间切换
   - 直接粘贴完整网址（如 `http://10.0.0.53:11987/#/home`）会自动拆分，路径部分被忽略
4. 内网与外网填**任意一个**即可登录；都填时优先走内网

详细操作说明见 [docs/CoolerControl-使用说明.md](docs/CoolerControl-使用说明.md)。

---

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 | Kotlin 2.4.0 |
| UI | Jetpack Compose（Miuix + Material 3 双风格） |
| 架构 | MVVM + Repository，Navigation 3 |
| 网络 | OkHttp 5（手写 SSE 解析，未引入 okhttp-sse） |
| 异步 | kotlinx.coroutines / Flow |
| 安全 | Android Keystore（AES-GCM） |
| 构建 | AGP 9.2.1 · Gradle 9.5.1 · R8 |

---

## 项目结构

```
app/src/main/java/com/example/kernelsustyleuikit/
├── data/
│   ├── local/        本地存储（SharedPreferences、Keystore 加密、桌面图标）
│   ├── model/        数据模型与解析
│   ├── remote/       OkHttp 客户端、Cookie 管理、SSE、网络绑定
│   ├── repository/   数据仓库
│   └── session/      会话管理与端点探测
├── ui/
│   ├── component/    自定义组件（含 cc/ 业务组件）
│   ├── screen/       各页面（Miuix / Material 双实现）
│   ├── theme/        主题
│   ├── util/         格式化与工具
│   └── viewmodel/    ViewModel
└── TemplateApplication.kt
```

文档见根目录 `01~04` 开头的 Markdown 与 [`docs/`](docs/)。

---

## 参与贡献

欢迎提交 Issue 与 PR。

提交前请确保：

```bash
./gradlew :app:assembleRelease
```

能够通过，且未引入新的 lint 错误。

---

## 许可

本项目采用 **GNU General Public License v3.0**，详见 [LICENSE](LICENSE)。

界面框架基于 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) 构建，同样遵循 GPL-3.0。

---

## 致谢

- [CoolerControl](https://coolercontrol.org) —— 优秀的开源风扇控制守护进程
- [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) —— 本项目界面框架的来源
- [Miuix](https://github.com/top.yukonga.miuix.kmp) —— Compose 组件库
