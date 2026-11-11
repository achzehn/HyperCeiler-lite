# 01 整体架构

## 1. 项目定位

HyperCeiler 是面向 Xiaomi HyperOS 的 **LSPosed 模块**：以单个 APK 同时承载「配置界面」与「注入代码」，通过 LSPosed 把代码注入到系统框架（`system`）与多个小米/Android 系统应用进程，对系统行为进行增强或修正。

- README 声明当前支持 **Android 15–16 的 HyperOS**，适配基线为 Android 16 HyperOS 3.0 手机端。
- 仓库同时包含面向 **Android 17 / HyperOS 4** 的实验能力（`doc/experimental-os4-popsicle.md`、`library/libhook/.../rules/*/…OS4*`、`app/src/main/cpp/targets/hyos`），其中 Home/recents 等部分功能依赖单独的 **native companion**（Zygisk Next 模块），APK 本身无法独立提供。

## 2. 三个运行形态

同一个 APK 在不同的宿主里跑不同代码：

```
┌─────────────────────────────┐       ┌──────────────────────────────────────┐
│  App 进程                    │       │  被注入进程（19 个作用域包）             │
│  com.sevtinge.hyperceiler    │       │  system / systemui / settings / home… │
│                             │       │                                      │
│  Application / HomePage     │  配置  │  XposedInitEntry                     │
│  Dashboard 设置页            │ ────► │   → ModuleMatcher                    │
│  Search / Log / Provision   │remote │   → BaseLoad 子类（每包一个）           │
│  PrefsBridge ───────────────┼──────►│   → initHook(BaseHook 规则)           │
│  (本地 dp-storage)           │ prefs │   → BaseHook.init()                  │
└─────────────────────────────┘       └──────────────────────────────────────┘
                                                  │ JNI
                                                  ▼
                                       libHyperCeilerNative.so
                                       （LSPosed native_init / OS4 native hook）
```

1. **App 形态**：`app` 模块，常规 Android 应用（`Application` → `SplashActivity` → 引导或 `HomePageActivity`），提供开关、日志、搜索、作用域选择、热重载按钮。
2. **注入形态**：`library:libhook` 模块，由 LSPosed 加载，入口 `XposedInitEntry`（声明在 `META-INF/xposed/java_init.list`），实现 libxposed API 102 的 `XposedModule` 回调。
3. **Native 形态**：`app/src/main/cpp`，产物 `libHyperCeilerNative.so`（声明在 `META-INF/xposed/native_init.list`），随 APK 打包并通过 LSPosed 加载到注入进程。

## 3. 架构分层

| 层 | Gradle 模块 | 关键内容 |
| --- | --- | --- |
| 表现层 | `app` + `library:core` + `library:provision` | `HomePageActivity`、Dashboard/Preference 设置页、搜索、日志、首次引导 |
| 注入层 | `library:libhook` | `XposedInitEntry`、`BaseLoad`、`BaseHook`、`ModuleMatcher`、488 个规则文件 |
| 公共层 | `library:common` | `PrefsBridge`、日志、`ShellUtils`、通用 View/Widget |
| 编译期 | `library:processor` | `@HookBase` 注解处理器，生成 `DataBase` 注册表 |
| 隐藏 API | `library:hidden-api` | `com.android.internal` 等系统隐藏类桩（compileOnly） |
| Native | `app/src/main/cpp` | `nativehook/` 框架、`targets/home`、`targets/systemui`、`targets/hyos` |

依赖方向是单向的：`app → core → common`、`app → libhook → common`，`libhook` 以 `compileOnlyApi` 依赖 `hidden-api`，`processor` 被 `libhook` 以 `api` + `annotationProcessor` 双重引入。

## 4. 关键设计取舍

### 4.1 单 APK 双身份
模块与配置界面同包名，好处是 `PrefsBridge` 可以直接读写同一份 SharedPreferences（设备保护存储），坏处是注入进程不能依赖 App 的 `R` 与 Activity 类，因此 UI 资源必须放在 `libhook` 自己能加载的位置——见 `BaseLoad.loadModuleResources()`：非自身包时用 `EzResources.inject(activeContext)`，自身包时用 `ContextUtils.getWaitContext`。

### 4.2 编译期注册表代替手写清单
每个目标应用有一个 `BaseLoad` 子类并标注 `@HookBase(targetPackage=..., minSdk=..., maxSdk=..., minOSVersion=..., maxOSVersion=..., deviceType=...)`。注解处理器在编译期生成 `com.sevtinge.hyperceiler.libhook.base.DataBase`：

```java
public final class DataBase {
    public static HashMap<String, DataBase> get() { ... }
    // 每项：targetPackage / minSdk / maxSdk / minOSVersion / maxOSVersion / deviceType
}
```

`XposedInitEntry.invokeInitInternal()` 在运行时遍历该 Map，用 `ModuleMatcher.shouldLoad` 过滤后反射实例化对应的 `BaseLoad` 子类。新增一个受支持应用 = 新增一个带注解的类，无需改任何注册代码。

### 4.3 规则即对象，按开关懒加载
`rules/` 下 488 个类不是全部无条件执行：每个 `BaseLoad.onPackageLoaded()` 里显式 `initHook(new XxxHook(), isXxxEnabled())`。`isXxxEnabled()` 通常来自 `PrefsBridge.getBoolean(...)`，未开启时连类都不会实例化，避免无效开销与误 hook。

### 4.4 热重载优先
`module.prop` 声明 `autoHotReload=true`，入口实现 `onHotReloading` / `onHotReloaded`。为了让重载后不残留旧状态，框架提供了一整套清理与校验机制：

- `BaseHook.registerHotReloadCleanup(...)` 及其 `registerReceiverHotReloadCleanup` / `registerContentObserverHotReloadCleanup` / `registerNetworkCallbackHotReloadCleanup` / `registerActivityLifecycleHotReloadCleanup` / `registerSensorListenerHotReloadCleanup` / `registerHandlerHotReloadCleanup` 变体；
- `BaseLoad.beginHotReloadVerification()` / `verifyHotReloadInitialization()` / `recordHotReloadInitializationFailure()`：任一新 hook 初始化失败即 `IllegalStateException`，阻止把半初始化状态当成成功；
- `XposedInitEntry.prepareHotReloadState()` 把 `AppInfo`、`Context`、运行时状态与版本号打包为 versioned extras（`MAGIC = "HyperCeiler.HotReloadExtras"`、`VERSION = 1`），重载后由 `PackageTarget.restored(...)` 重建；
- 重载前调用 `ThreadPoolManager.shutdownAndAwait(500ms)`、`LogStatusManager.detachHookLogLevelObserver`，避免旧线程与观察者泄漏。

### 4.5 安全模式与框架指纹门禁
- `CrashScope`（`safecrash` 包）通过系统属性 `persist.service.hyperceiler.crash.report` 与配置键（`system_ui_safe_mode_enable`、`home_safe_mode_enable` 等）判断某包是否需要跳过全部 hook，防止崩溃循环。
- `FrameworkHookGate.isAllowed(storedAllow, blockedReason, running, checked)`：当检测到的框架指纹与上次记录不一致时拒绝加载 hook，避免系统更新后旧规则乱 hook 导致 bootloop。
- `ModuleMatcher` 对 `com.miui.securitycenter` / `com.miui.home` 额外做安全检查（`CheckModifyUtils.getCheckResult`），非调试模式下若判定为「已修改环境」则不加载。

## 5. 作用域（scope.list）

`library/libhook/src/main/resources/META-INF/xposed/scope.list` 共 24 项：

```
system
com.android.browser
com.android.quicksearchbox
com.bilibili.app.blue
com.bilibili.app.in
tv.danmaku.bili
tv.danmaku.bilibilihd
com.android.settings
com.android.systemui
com.android.updater
com.milink.service
com.miui.backup
com.miui.cloudservice
com.miui.guardprovider
com.miui.huanji
com.miui.mishare.connectivity
com.miui.packageinstaller
com.miui.securityadd
com.miui.securitycenter
com.sohu.inputmethod.sogou.xiaomi
com.xiaomi.barrage
com.xiaomi.market
com.xiaomi.mirror
com.xiaomi.trustservice
```

注意 `com.miui.home`（系统桌面）**不在** static scope 列表中，`staticScope=false` 表示作用域可动态调整；桌面相关 hook 通过 LSPosed 中用户勾选的实际作用域生效，代码里以 `XposedInitEntry.LAUNCHER_PACKAGE = "com.miui.home"` 特判。

## 6. 版本与兼容分支

代码里存在大量按 SDK / OS / 设备类型分流的实现，命名后缀是约定：

| 后缀 | 含义 | 例 |
| --- | --- | --- |
| `V` | Android 15（SDK 35，Vanilla/Viceroy 分支） | `SystemFrameworkV`（`maxSdk=35`）、`SystemUIV`、`SystemSettingsV` |
| `B` | Android 16（SDK 36，Baklava） | `SystemFrameworkB`（`minSdk=36`）、`SystemUIB`、`SystemSettingsB` |
| `OS4` | HyperOS 4 / Android 17 实验分支 | `NativeHomeHooksOS4`、`HomeLayoutNativeEndpointOS4`、`HomeGestureOS4` |
| `New` | 同功能的新实现（保留旧类对照） | `DockCustomNew`、`MoreBatteryInfoNew` |

`ModuleMatcher.matchSdkVersion` / `matchOSVersion` / `matchDeviceType` 据此决定某 `BaseLoad` 是否在当前设备上生效；`DEVICE_ALL=0`、`PAD_ONLY=1`、`PHONE_ONLY=2`。

## 7. 数据流总览

```
用户拨动开关（App）
   │ PrefsBridge.putByApp(key, value)
   ▼
本地 SharedPreferences（hyperceiler_prefs，device protected storage）
   │ + LSPosed remote prefs（hyperceiler_prefs_remote）
   ▼
被注入进程：XposedInitEntry.initPrefs() → getRemotePreferences(REMOTE_PREFS_GROUP)
   │（先由 getFrameworkProperties() 校验 PROP_CAP_REMOTE，缺失则不加载规则并告警）
   ▼
isXxxEnabled() → 决定是否 initHook(...)
   ▼
BaseHook.init() → EzHookTool / DexKit / 反射 hook 目标方法
```

写入方向是单向的：注入进程只读 remote prefs，App 进程负责写；`PrefsBridge.putByApp` 在检测到自身处于 hook 进程时会 warn 并跳过写入（防止跨进程写坏配置）。

## 8. 与 LSPosed 的契约文件

`library/libhook/src/main/resources/META-INF/xposed/`：

| 文件 | 内容 |
| --- | --- |
| `java_init.list` | `com.sevtinge.hyperceiler.libhook.base.XposedInitEntry` |
| `native_init.list` | `libHyperCeilerNative.so` |
| `scope.list` | 23 个作用域包 |
| `module.prop` | `minApiVersion=102`、`targetApiVersion=102`、`autoHotReload=true`、`staticScope=false`、`exceptionMode=protective` |

`app/build.gradle.kts` 中通过 `packaging { resources { merges += "META-INF/xposed/*" } }` 把这些文件合入 APK；`dex { useLegacyPackaging = true }` 保证 libxposed 能按预期读取 dex。

## 9. 延伸阅读

- 入口细节 → [03-hook-entry-and-flow.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/03-hook-entry-and-flow.md)
- 模块划分 → [02-modules.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/02-modules.md)
- OS4 实验能力与限制 → `doc/experimental-os4-popsicle.md`
