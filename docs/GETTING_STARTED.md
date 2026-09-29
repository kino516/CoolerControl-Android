# Development Guide

English | [简体中文](GETTING_STARTED.zh-CN.md)

This guide is for people **building on top of this repository**: how to set up the environment, where the code lives, how common changes are made, and how release builds get signed.

> This app is a **remote control** and cannot work on its own — the [CoolerControl](https://coolercontrol.org) daemon must be running on a PC first.
> End-user instructions are available in Chinese only: [CoolerControl-使用说明.md](CoolerControl-使用说明.md).

---

## 1. Environment

| Item | Requirement | Note |
|---|---|---|
| JDK | **21** | `sourceCompatibility` / `targetCompatibility` are `VERSION_21` in the root `build.gradle.kts`; anything older fails to compile |
| Android SDK | **API 37**, **Build Tools 37.0.0** | Install both in SDK Manager; a missing platform fails the build at configuration time |
| Gradle | **9.5.1** | Provided by the wrapper, no separate install needed |

```bash
git clone https://github.com/kino516/CoolerControl-Android.git
cd CoolerControl-Android

# Point to your Android SDK (Windows example: sdk.dir=D:/Android/Sdk)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/CoolerControl_1.0.0_1-debug.apk`

> The file name follows `CoolerControl_<versionName>_<versionCode>`, set by `base.archivesName` in `app/build.gradle.kts`.
> `local.properties` only supplies the SDK path. It is listed in `.gitignore` — **do not commit it**.

---

## 2. Code Layout

```text
app/src/main/java/com/example/kernelsustyleuikit/
├── TemplateApplication.kt
├── data/
│   ├── CcGraph.kt                 lightweight service locator (lazy singletons, no DI framework)
│   ├── local/                     SharedPreferences (CcPrefs), Keystore crypto (CcCrypto), launcher icons (CcAppIcon)
│   ├── model/                     domain models (devices / status / alerts / modes / curves / stats / servers)
│   ├── remote/                    CcApiClient (REST), CcCookieJar, CcSseClient (hand-written SSE), CcJson (org.json → models)
│   ├── repository/                CcRepository is the single source of truth; SettingsRepository holds local preferences
│   └── session/                   session management (CcSessionManager) and endpoint selection (CcEndpointProbe)
└── ui/
    ├── MainActivity.kt            main shell (pager + secondary-route NavDisplay)
    ├── UiMode.kt                  UI style enum and LocalUiMode
    ├── component/                 shared components: cc/ (style-agnostic), miuix/, material/, bottombar/, dialog/, liquid/ …
    ├── navigation3/               Routes.kt, Navigator.kt, DeepLinkResolver.kt
    ├── screen/<feature>/          one directory per feature
    ├── theme/                     theme and color scheme
    ├── util/                      formatting and platform helpers
    └── viewmodel/                 per-screen ViewModels
```

### Why `namespace` differs from `applicationId`

| Item | Value | Scope |
|---|---|---|
| `namespace` (code package) | `com.example.kernelsustyleuikit` | Only affects `package` / `import` in Kotlin sources |
| `applicationId` (app identity) | `com.coolercontrol.mobile` | Install identity, system and store recognition |

The mismatch is **intentional**: `namespace` is inherited from the upstream UI Kit, and changing it means rewriting `package` / `import` plus directory structure across all 143 Kotlin files — high risk, low reward. The public identity is decided by `applicationId`, which is usually all you need to change.

### Data-flow conventions

- **`CcRepository` is the single source of truth** — screens read from ViewModels and never hold `CcApiClient` directly
- **ViewModels always take a no-arg constructor** and resolve dependencies through `CcGraph`. Avoid `AndroidViewModel`: it was observed to crash inside pager screens because `CreationExtras` lacks `APPLICATION_KEY`
- **SSE runs in a process-level scope** and is not torn down when a screen goes away

---

## 3. Common Tasks

### 3.1 App name, version, icons

| What to change | Where |
|---|---|
| App name | `app_name` in `app/src/main/res/values/strings.xml` (`translatable="false"`, one place) |
| Version | `managerVersionCode` / `managerVersionName` in the root `build.gradle.kts` |
| Launcher icon | `app/src/main/res/mipmap-*/` (all densities), `drawable/ic_launcher_foreground.png`, `drawable/ic_launcher_monochrome.xml` |
| In-app logo | `drawable/ic_logo.png`, `raw/logo.svg` |
| Optional launcher icon variants | `data/local/CcAppIcon.kt` |

Icon source assets live in the repository root `icon/` directory (1024 / 512 PNGs and ico files); export them per density into `mipmap-*` after editing.

### 3.2 Adding a screen

**Secondary screens** (pushed onto the back stack, like Settings or Servers) and **main pager screens** (swiped horizontally, like Home / Cooling / Monitor) are wired differently.

#### Secondary screen

1. Create the files:

```text
ui/screen/foo/
├── FooScreen.kt      entry composable
├── FooUiState.kt     screen state (data class + defaults)
├── FooMiuix.kt       Miuix implementation
└── FooMaterial.kt    Material implementation
```

2. Dispatch on the current style in `FooScreen.kt` — both implementations share the same state and callbacks, only rendering differs:

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

3. Add `ui/viewmodel/FooViewModel.kt` with a no-arg constructor; use `CcGraph.repository` when you need the repository.
4. Declare the route in `ui/navigation3/Routes.kt`, following the existing pattern:

```kotlin
@Parcelize
@Serializable
data object Foo : Route
```

5. Mount it in `ui/MainActivity.kt` by adding `Route.Foo -> FooScreen(navController)` to the route branch.

#### Main pager screen

Home / Cooling / Monitor live in a `HorizontalPager` and are mounted **by page index** in `ui/MainActivity.kt` (`0 -> HomePager(...)` and so on). Adding one means updating the pager page count, the mounting branch, the bottom-bar page list, and any page-index-dependent logic (for example the `MONITOR_PAGE_INDEX` used by "view in Monitor").

### 3.3 Talking to a new daemon endpoint

1. Add the request to `data/remote/CcApiClient.kt` (return `Result<T>`, go through the internal `execute(...)`)
2. Add the model under `data/model/` and its parsing in `data/remote/CcJson.kt`
3. Expose it from `data/repository/CcRepository.kt` as a `StateFlow` or a suspend function
4. Read it from a ViewModel

Follow the official OpenAPI for field names. That file is **not** bundled here — the `_ref/` scratch directory is excluded by `.gitignore`; fetch it from the CoolerControl project when needed.

### 3.4 UI styles (Miuix / Material)

- The active style comes from `LocalUiMode`; the default is `UiMode.DEFAULT_VALUE` in `ui/UiMode.kt`
- Defaults for blur, floating bottom bar and liquid glass live in `data/repository/SettingsRepositoryImpl.kt`
- Style-agnostic components go in `ui/component/cc/`; style-specific ones go in `ui/component/miuix/` or `ui/component/material/`

### 3.5 Localization

Strings live in `app/src/main/res/values*/strings.xml` (50+ locales). The default `values/strings.xml` is English, Simplified Chinese is in `values-zh-rCN/`. New strings should be added to at least both of those.

---

## 4. Signing and Release

Release signing is handled by the `org.lsposed.lsplugin.apksign` plugin, which reads **Gradle properties only**. Configure them in the user-level `~/.gradle/gradle.properties` (never committed):

```properties
KEYSTORE_FILE=/absolute/path/to/your.jks
KEYSTORE_PASSWORD=******
KEY_ALIAS=******
KEY_PASSWORD=******
```

Field names match `sign.example.properties` in the repository root. To generate a keystore:

```bash
keytool -genkeypair -v -keystore your.jks \
  -alias your-alias -keyalg RSA -keysize 4096 -validity 10000
```

Then build:

```bash
./gradlew :app:assembleRelease
```

> **Putting these values in `local.properties` does not work** — that file is read by AGP for the SDK path, and the plugin cannot see it.
> You can also pass them per-invocation:
> `./gradlew :app:assembleRelease -PKEYSTORE_FILE=... -PKEYSTORE_PASSWORD=... -PKEY_ALIAS=... -PKEY_PASSWORD=...`

**Without signing configuration the release build still succeeds, but falls back to the default Android debug signature** (the certificate is `CN=Android Debug`). That certificate is public — anyone can use it to ship an update that impersonates yours. **Never distribute such a build.**

> If the keystore is lost, you can no longer update an app already published with it. Back it up together with its passwords.

---

## 5. Before You Commit

- `./gradlew :app:assembleDebug :app:assembleRelease` passes
- No real keystore or password is committed (`.gitignore` already excludes `keystore/`, `*.jks`, `*.keystore`, `local.properties`)
- New UI strings were added to `values-zh-rCN`

GitHub Actions runs the build automatically on push and pull requests — see `.github/workflows/build.yml`.

---

## 6. License

This project is licensed under the **GNU General Public License v3.0**. It is a derivative work: the UI framework comes from [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit), also GPL-3.0.

**If you distribute a modified version, you must release it under GPL-3.0 as well and provide the complete source.**
