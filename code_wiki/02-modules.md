# 02 模块与依赖

## 1. Gradle 工程结构

`settings.gradle.kts` 中启用 7 个模块（另有 2 个已注释）：

```
HyperCeiler（rootProject）
├── :app                     APK 应用（配置界面 + native so 打包）
├── :library:libhook         LSPosed 注入逻辑（Xposed 入口 + 488 个规则）
├── :library:core            设置页框架、Dashboard、热重载/作用域工具
├── :library:common          公共工具（PrefsBridge、日志、Shell、通用 View）
├── :library:provision       首次启动引导（状态机 + 动画）
├── :library:processor       @HookBase 注解处理器
└── :library:hidden-api      系统隐藏 API 桩（namespace = com.android.internal）
（已注释：:library:hook、:library:xposed-api-101）
```

## 2. 模块依赖图

```
            ┌──────────────┐
            │    :app      │
            └──┬───────┬───┘
               │       │
      ┌────────▼──┐  ┌─▼──────────────┐
      │ :library: │  │ :library:      │
      │  core     │  │  common        │
      └──┬────┬───┘  └─▲──────────────┘
         │    │        │
         │  ┌─▼────────┴──┐
         │  │ :library:    │
         │  │  provision   │
         │  └──┬───────────┘
         │     │
         │  ┌──▼──────────┐        ┌───────────────────┐
         └─►│ :library:    │──api──►│ :library:         │
            │  libhook     │        │  processor        │
            └──┬───────────┘        └───────────────────┘
               │ compileOnlyApi
            ┌──▼──────────┐
            │ :library:   │
            │  hidden-api │
            └─────────────┘
```

实际声明（各 `build.gradle.kts`）：

| 模块 | 依赖 |
| --- | --- |
| `:app` | `implementation(projects.library.core)`、`implementation(projects.library.common)`、viewpager2、expansion、Room（runtime/ktx + `annotationProcessor room-compiler`） |
| `:library:core` | `api(projects.library.common)`、`api(projects.library.provision)`、`api(libs.appiconloader)` |
| `:library:provision` | `api(projects.library.common)`、`api(projects.library.libhook)` |
| `:library:libhook` | `api(projects.library.processor)`、`api(projects.library.common)`、`annotationProcessor(projects.library.processor)`、`compileOnlyApi(projects.library.hiddenApi)`、`compileOnlyApi(libs.libxposed.api)`、`api(libs.libxposed.service)`、dexkit、ezhooktool、hiddenapibypass、gson、hyperfocusapi、superlyricapi、lunarcalendar、AndroidX core/fragment/recyclerview/coordinatorlayout/constraintlayout |
| `:library:common` | `api(libs.bundles.miuix)`（16 个 `fan.miuix:*`）、`compileOnlyApi(libs.libxposed.api)` |
| `:library:processor` | `java-library`，仅 auto-service |
| `:library:hidden-api` | `implementation(libs.annotation)` |

注意 `provision → libhook` 这条边：引导流程需要读取 libhook 的常量和 `DataBase` 信息来展示支持的功能/机型，因此 `core → provision → libhook` 构成间接依赖，`libhook` 的内容最终会进入 APK。这也解释了为什么 `libhook` 必须保证类加载安全（不能在静态初始化时触碰 Android 隐藏 API）。

## 3. 各模块职责

### 3.1 `:app`（`app/src/main/java/com/sevtinge/hyperceiler/`）

| 包 | 职责 | 关键类 |
| --- | --- | --- |
| 根 | 应用生命周期、LSPosed 服务绑定 | `Application` |
| `ui/` | 启动与主页壳 | `SplashActivity`、`LauncherActivity`、`HomePageActivity` |
| `home/` | 主页框架：ViewPager、Header、卡片开关、排序、提示、崩溃安全模式 | `HomePageFragment`、`HomePageHeaderHelper`、`widget/SwitchManager`、`order/CustomOrderManager`、`safemode/CrashService` |
| `home/task/` | 启动任务编排 | `AppInitializer`、`AppTaskManager`、`TaskRunner`、`Task` |
| `search/` | 设置项搜索（Room FTS4 + 拼音） | `SearchHelper`、`PinyinUtils`、`data/ModDao` |
| `log/` | Xposed 日志采集、持久化、查看 | `LogManager`、`XposedLogLoader`、`db/LogRepository`、`LogViewerActivity` |
| `settings/` | 设置/开发选项页 | `SettingsFragment`、`development/DevelopmentFragment` |
| `sub/` | 作用域选择 | `ScopePickerActivity` |
| `about/` | 关于页与背景动效 | `AboutPageFragment`、`controller/BgEffectController` |
| `utils/` | 设备信息、框架状态、激活检测、通知 | `DeviceInfoBuilder`、`FrameworkStatusManager`、`XposedActivateHelper`、`NoticeProcessor` |
| `fan/provider/` | 仿 `SettingsProvider`，供设置页读写 | `SettingsProvider`、`Settings`、`SettingsLiveData` |

App 侧的核心入口见 [05-app-ui.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/05-app-ui.md)。

### 3.2 `:library:libhook`（注入层，核心）

```
com.sevtinge.hyperceiler.libhook
├── base/            XposedInitEntry、BaseLoad、BaseHook、ModuleMatcher、PackageTarget、FrameworkHookGate、HookBridge
├── app/             每个目标包一个 @HookBase 的 BaseLoad 子类（28 个）
├── rules/           实际 hook 规则实现（474 个文件，24 个目标子目录）
├── appbase/         跨规则复用的基类（input/mihome/systemframework/systemui）
├── provider/        SharedPrefsProvider 与 Dock/Home 诊断 Provider
├── safecrash/       CrashScope（安全模式）
├── utils/           设备/属性/资源/日志等工具（api、hookapi、hooks 等子包）
├── callback/        ICrashHandler
└── expansion 等外部 API 的封装使用
```

`rules/` 子目录与目标包对应关系：

| 目录 | 目标 |
| --- | --- |
| `home/` | `com.miui.home`（含 dock/drawer/folder/gesture/layout/mipad/navigation/other/recent/title/widget 子目录） |
| `systemui/` | `com.android.systemui` |
| `systemsettings/` | `com.android.settings` |
| `systemframework/` | `system`（含 `corepatch/`、`display/` 等） |
| `securitycenter/` | `com.miui.securitycenter`（app/battery/beauty/sidebar/…） |
| `getapps/`、`browser/`、`quicksearchbox/`、`updater/`、`packageinstaller/`、`mishare/`、`milink/`、`mirror/`、`backup/`、`huanji/`、`cloudservice/`、`guardprovider/`、`barrage/`、`trustservice/`、`securityadd/`、`phone/`、`incallui/`、`remotecontroller/` | 各自对应包 |
| `bilibili/` | `tv.danmaku.bili`、`com.bilibili.app.blue`、`com.bilibili.app.in`、`tv.danmaku.bilibilihd`（`BiLiveInvisible` 直播隐身、`RemoveBlock` 解除限制） |
| `various/` | 多包共用规则（`VariousSystemApps` / `VariousThirdApps` 兜底） |

### 3.3 `:library:core`（设置页框架）

```
com.sevtinge.hyperceiler
├── dashboard/      SettingsActivity、SettingsBaseActivity、DashboardFragment、SubSettings
├── hooker/         每个功能域的设置入口 Fragment（SystemFrameworkFragment、SystemUISettings 分组…）
├── utils/          HotReloadManager、LSPosedScopeHelper、ScopeManager、SettingsHelper、PersistConfig、DialogHelper…
├── prefs/          Preference 控件与数据绑定
├── callback/、holiday/、weather/、model/、sub/、view/
└── res/xml/        大量设置页布局（system_ui.xml、framework.xml、various*.xml 等）
```

关键类：

- `SettingsBaseActivity` / `SettingsPreferenceFragment`：统一主题、标题、返回、搜索入口。
- `ScopeManager`（Kotlin object）：持有 `XposedService`，提供 `getScopeSync()`、`normalizeScopePackages()`、`addServiceStateListener()`，所有回调切主线程。
- `HotReloadManager`：封装 libxposed API 102 热重载，`isHotReloadAvailable()` 判定 `service.getApiVersion() >= XposedService.API_102`，`hotReloadByPackage` / `hotReloadAll` 带 15 s 超时兜底。

### 3.4 `:library:common`（公共层）

```
com.sevtinge.hyperceiler.common
├── base/       BaseActivity、BasePreferenceFragment
├── log/        AndroidLog、LogConfigManager、LogLevelManager、LogStatusManager、XposedLog
├── utils/      PrefsBridge、PrefsDataStore、PrefsConfigurator、ShellUtils、PrefsChangeObserver、api/ProjectApi…
├── view/       CircleImageView、DraggableViewPager、CubicEaseOutInterpolater
└── widget/     ItemView、SearchEditText、SpinnerItemView
```

`PrefsBridge` 是 App 与注入进程之间唯一的配置通道，详见 [04-prefs-and-data.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/04-prefs-and-data.md)。

### 3.5 `:library:provision`（首次引导）

```
com.sevtinge.hyperceiler.provision
├── activity/     BaseActivity、BasicSettingsActivity、PermissionSettingsActivity、TermsAndStatementActivity、CongratulationActivity、ProvisionDetailActivity
├── state/        State、StateMachine、BasicState、PermissionState、TermsAndStatementState、CongratulationState、StartupState
├── renderengine/ GlowController、GlowPainter、RenderViewLayout（GLSL 光效）
├── service/      ProvisionAnimService
├── utils/        ProvisionManager、ProvisionStateHolder、NoticeProvider、AnimHelper、BlurUtils
└── widget/       MarkdownView 等
```

`ProvisionManager` 是入口：`HomePageActivity` 在未完成引导时跳 `SplashActivity`，由 `ProvisionManager.setProvider` 注入回调并 `NoticeProcessor.process`。

### 3.6 `:library:processor`（注解处理器）

`HookBaseProcessor`：

- `@AutoService(Processor.class)`、`@SupportedAnnotationTypes("com.hchen.database.HookBase")`、`@SupportedSourceVersion(RELEASE_21)`；
- 首轮（`roundEnv.processingOver()==false`）扫描被 `@HookBase` 标注的类，生成 `com.sevtinge.hyperceiler.libhook.base.DataBase`；
- 生成内容形如 `dataMap.put("<类全名>", new DataBase("<targetPackage>", minSdk, maxSdk, <minOS>F, <maxOS>F, deviceType))`；
- 这是「新增 BaseLoad 子类无需改注册代码」的关键机制。

### 3.7 `:library:hidden-api`（隐藏 API 桩）

`namespace = "com.android.internal"`，仅编译期使用（`compileOnlyApi`），不打包进 APK。包含：

| 文件 | 用途 |
| --- | --- |
| `android/os/PowerExemptionManager.java` | 免电省白名单操作 |
| `android/os/SystemProperties.java` | 隐藏属性接口 |
| `android/provider/MiuiSettings.java` | MIUI/HyperOS 设置键 |
| `android/util/LongArrayQueue.java` | 系统内部集合 |
| `android/view/ViewRootImpl.java` | 窗口根实现细节 |
| `com/android/internal/graphics/drawable/BackgroundBlurDrawable.java` | 背景模糊 |
| `miui/app/MiuiFreeFormManager.java` | 小窗管理 |
| `miui/drm/DrmManager.kt`、`ThemeReceiver.kt` | DRM/主题 |

### 3.8 Native（`:app` 内）

- `app/src/main/cpp/CMakeLists.txt`：主 target `HyperCeilerNative`；非 HYOS 构建额外产出 `HyperCeilerAmbientOS4`；可选 `HyperCeilerHyos`。
- `nativehook/`：可复用的 ELF 解析/内联 hook 框架（`elf_image.h`、`got_hook_backend.h`、`hook_bank.h`、`page_guard.h`、`resolver.h`、`arm64_decode.h`、`memory_io.h`，含 `xz/` 解压）。
- `targets/home/`：桌面（Flutter/Dart）相关的启动、布局、网格、Dock、最近任务 hook（`tweaks/` 内含 `scanner.cpp`、`symtab.cpp`、`htcache.cpp`、`ht_plan.cpp`、`image.cpp`、`blob.cpp`）。
- `targets/systemui/`：`ambient_color_os4.cpp`（OS4 环境光/取色）。
- `targets/hyos/`：Zygisk Next companion 入口（`hyos_entry`、`hyos_control`、`hyos_report_runtime`、arm64 汇编 trampoline）。

详见 [06-native.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/06-native.md)。

## 4. 第三方依赖清单

来源：`gradle/libs.versions.toml`

| 类别 | 依赖 | 版本 |
| --- | --- | --- |
| 构建 | AGP | 9.4.0 |
| 构建 | Kotlin | 2.4.10 |
| 构建 | LSParanoid（字符串/类名混淆） | 0.6.0 |
| 构建 | AutoService | 1.1.1 |
| AndroidX | core / core-ktx | 1.19.0 |
| AndroidX | fragment | 1.9.0 |
| AndroidX | recyclerview | 1.4.0 |
| AndroidX | viewpager2 | 1.1.0 |
| AndroidX | Room（runtime/ktx/compiler） | 2.8.5 |
| UI | Miuix bundle（`fan.miuix:*`，16 个模块） | 由 bundle 统一 |
| UI | appiconloader | 1.5.0 |
| Xposed | `libxposed:api` / `libxposed:service` | 102.0.0 |
| Xposed | DexKit | 2.2.0 |
| Xposed | EzHookTool（core + hook-xposed-102） | 1.3.0 |
| Xposed | hiddenapibypass | 6.1 |
| 其他 | gson | 2.14.0 |
| 其他 | hyperfocusapi | 2.0 |
| 其他 | superLyricApi | 3.4 |
| 其他 | lunarcalendar | `latest.release` |
| 注解 | androidx annotation | 1.10.0 |

仓库来源（`settings.gradle.kts`，`FAIL_ON_PROJECT_REPOS`）：阿里云镜像（public / google / gradle-plugin）→ google → mavenCentral → mavenLocal → GitHub Packages（`ReChronoRain/HyperCeiler`，需 GPR 凭据）→ jitpack → `api.xposed.info`。

GPR 凭据加载逻辑：`loadGprCredentials()` 依次读环境变量 `GIT_ACTOR`/`GIT_TOKEN` 或根目录 `signing.properties` 的 `gpr.user`/`gpr.key`，缺失时抛 `GradleException`（因为依赖里有发布在 GitHub Packages 的私有构件）。

## 5. 资源与打包约定

- R 类非传递（`android.nonTransitiveRClass=true`），每个 library 只含自身资源。
- `:app` 使用保留包 ID：`--allow-reserved-package-id --package-id 0x36`。
- `:app` 的 `packaging.resources.merges += "META-INF/xposed/*"`，`excludes += "**"` 后由 merges 白名单放行；`dex.useLegacyPackaging = true`；`jniLibs.useLegacyPackaging = false`。
- `:app` 的 `tasks.withType<PackageAndroidArtifact>` 在打包前清空 `appMetadata`（规避 AGP 9 的 metadata 问题）。
- `afterEvaluate` 中根据当前 assemble 任务推导 `archivesName = HyperCeiler-<versionName><suffix>`。
