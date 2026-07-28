# HyperCeiler Code Wiki

> 项目路径：`d:\lsp\HyperCeiler-lite`  
> 项目性质：基于 LSPosed / Xposed API 101 的 MIUI/HyperOS 系统定制模块（Android 应用 + Hook 模块一体化）。  
> 最后更新：2026-07-15

---

## 1. 项目概览

HyperCeiler 是一个面向 MIUI / HyperOS 的 LSPosed 模块，通过在不修改 APK 的情况下 Hook 系统框架、SystemUI、安全中心、桌面、浏览器等系统/预装应用，实现状态栏、控制中心、音量、小窗、核心破解、动画、日志导出等深度定制功能。

- **应用包名**：`com.sevtinge.hyperceiler`
- **构建系统**：Gradle Kotlin DSL + Android Gradle Plugin 9.2.1
- **编译 SDK**：37（App）/ 36（Library）
- **最低 SDK**：35（Android 15+）
- **目标 SDK**：37
- **Java/Kotlin 工具链**：JDK 21
- **Gradle Wrapper**：9.5.0
- **代码语言**：Java + Kotlin

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          App 进程                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Splash/主页   │  │ 设置/偏好     │  │ 日志查看/导出         │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                  │                       │             │
│  ┌──────▼──────────────────▼───────────────────────▼─────────┐  │
│  │                      Application                          │  │
│  │  - 初始化 PrefsBridge / LogManager / FrameworkStatus       │  │
│  │  - 绑定 XposedService（LSPosed）                           │  │
│  └────────────────────┬──────────────────────────────────────┘  │
└───────────────────────┼─────────────────────────────────────────┘
                        │ SharedPreferences (Remote)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Hook 进程                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              XposedInitEntry (XposedModule)               │  │
│  │  - onModuleLoaded / onSystemServerStarting / onPackageReady│  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           │ invokeInit                          │
│  ┌────────────────────────▼─────────────────────────────────┐  │
│  │  BaseLoad 子类（SystemUIB / SystemFrameworkB / ...）       │  │
│  │  - onPackageLoaded() 中 initHook(new XXXHook(), enabled)   │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           │                                     │
│  ┌────────────────────────▼─────────────────────────────────┐  │
│  │              BaseHook 子类（具体 Hook 规则）               │  │
│  │  - init() / initDexKit() 中调用 EzxHelpUtils / DexKit      │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 核心设计思想

1. **多模块分离**：App UI、通用库、Hook 运行时、注解处理器彼此独立。
2. **注解驱动**：通过 `@HookBase` 标注目标应用/系统，编译期由 `HookBaseProcessor` 生成 `DataBase` 索引。
3. **运行时匹配**：`XposedInitEntry` 遍历 `DataBase`，由 `ModuleMatcher` 按包名/SDK/OS/设备类型/安全模式决定是否加载。
4. **DexKit 并行化**：需要反混淆的 Hook 先缓存，再在 `BaseLoad` 中通过线程池并行执行 `initDexKit()`。
5. **双进程_prefs**：App 进程写本地 SharedPreferences 并同步到 LSPosed 远程 prefs；Hook 进程只读远程 prefs。

---

## 3. 模块结构与职责

| 模块 | 路径 | 类型 | 主要职责 |
|------|------|------|----------|
| `app` | `app/` | Android Application | 用户界面、设置、主页、日志、安全模式、Xposed 服务绑定、崩溃捕获。 |
| `library:common` | `library/common/` | Android Library | MIUIX UI 组件封装、通用工具、日志（`AndroidLog`）、`PrefsBridge`、跨进程 prefs 通知。 |
| `library:core` | `library/core/` | Android Library | 核心公共能力：应用图标加载、Room 数据库、 provision（引导页）依赖聚合。 |
| `library:libhook` | `library/libhook/` | Android Library | Hook 运行时核心：BaseHook、BaseLoad、XposedInitEntry、ModuleMatcher、DexKit 封装、所有 Hook 规则。 |
| `library:processor` | `library/processor/` | Java Library | 注解处理器，处理 `@HookBase` 并生成 `DataBase.java`。 |
| `library:xposed-api-101` | `library/xposed-api-101/` | Android Library | 对 libxposed API 101 的封装/适配。 |
| `library:hidden-api` | `library/hidden-api/` | Android Library | Android 隐藏 API 存根（compileOnly）。 |
| `library:provision` | `library/provision/` | Android Library | OOBE / 引导页相关（被 core 依赖）。 |

### 3.1 `app` 模块关键包

| 包/路径 | 说明 |
|---------|------|
| `com.sevtinge.hyperceiler.Application` | 应用入口，绑定 XposedService，初始化日志、崩溃处理。 |
| `com.sevtinge.hyperceiler.ui` | `SplashActivity`、`HomePageActivity`、`LauncherActivity`。 |
| `com.sevtinge.hyperceiler.home` | 主页 Fragment、Banner、导航、Header、任务初始化。 |
| `com.sevtinge.hyperceiler.settings` | 设置页 Fragment（设置主界面、开发调试页）。 |
| `com.sevtinge.hyperceiler.about` | 关于页、贡献者、设备信息卡片。 |
| `com.sevtinge.hyperceiler.log` | 日志数据库（Room）、日志查看器、导出压缩包。 |
| `com.sevtinge.hyperceiler.home.safemode` | 安全模式配置、崩溃记录、CrashActivity/ExceptionCrashActivity。 |
| `com.sevtinge.hyperceiler.search` | 功能搜索（Room FTS）。 |

### 3.2 `library:libhook` 模块关键包

| 包/路径 | 说明 |
|---------|------|
| `libhook.base` | `BaseHook`、`BaseLoad`、`XposedInitEntry`、`ModuleMatcher`、生成的 `DataBase`。 |
| `libhook.app.*` | 每个目标应用一个 `BaseLoad` 子类，负责聚合该应用下的 Hook。 |
| `libhook.rules.*` | 具体 Hook 实现，按目标应用/系统组件分目录。 |
| `libhook.utils.hookapi.tool` | `EzxHelpUtils`（类/字段/方法/Hook 封装）。 |
| `libhook.utils.hookapi.dexkit` | `DexKit` / `DexKitCacheManager` 反混淆门面。 |
| `libhook.safecrash` | `CrashMonitor` 系统级崩溃监控与安全模式处理。 |
| `libhook.callback` | Hook 回调接口（`IMethodHook`、`IReplaceHook`、`ICrashHandler`）。 |

---

## 4. 关键类与函数说明

### 4.1 Hook 入口与生命周期

#### `XposedInitEntry` (`library/libhook/.../base/XposedInitEntry.java`)

LSPosed 模块入口，继承 `XposedModule`。

| 方法 | 说明 |
|------|------|
| `onModuleLoaded(ModuleLoadedParam)` | 模块自身加载时初始化 EzXposed、BaseLoad、Prefs。 |
| `onSystemServerStarting(SystemServerStartingParam)` | 系统服务进程启动时加载框架级 Hook（CorePatch、FlagSecure、CrashMonitor）。 |
| `onPackageReady(PackageReadyParam)` | 普通应用进程首包准备就绪时，触发该包对应的 `BaseLoad`。 |
| `invokeInit(...)` | 遍历 `DataBase.get()`，通过 `ModuleMatcher` 过滤后反射实例化并调用 `BaseLoad.onLoad()`。 |
| `isHookEnabled()` | 检查 `allow_hook` 与框架校验状态，决定是否加载 Hook。 |

#### `BaseLoad` (`library/libhook/.../base/BaseLoad.java`)

每个目标应用/系统对应一个子类，负责管理该作用域下的所有 Hook。

| 方法/字段 | 说明 |
|-----------|------|
| `static init(XposedModule)` | 初始化 Xposed 运行时句柄、日志、EzXposed。 |
| `onLoad(PackageReadyParam)` / `onLoad(SystemServerStartingParam)` | 重置静态上下文（ClassLoader、PackageName、lpparam）、加载模块资源、执行 Hook。 |
| `onPackageLoaded()` | **抽象方法**，子类实现，调用 `initHook(...)`。 |
| `initHook(BaseHook)` / `initHook(BaseHook, boolean)` / `initHook(BaseHook, BooleanSupplier)` | 按条件注册 Hook；自动处理 DexKit 缓存与并行初始化。 |
| `getClassLoader()` / `getPackageName()` / `getLpparam()` / `getSystemServerParam()` / `getXposed()` | 静态访问当前加载上下文。 |
| `SYSTEM_SERVER` | 常量 `"system"`，标识系统服务作用域。 |

#### `BaseHook` (`library/libhook/.../base/BaseHook.java`)

所有具体 Hook 规则的基类。

| 方法 | 说明 |
|------|------|
| `init()` | **抽象方法**，子类实现具体 Hook 逻辑。 |
| `useDexKit()` | 是否使用 DexKit，默认 `false`。 |
| `initDexKit()` | DexKit 解析阶段，仅允许调用 `requiredMember` / `optionalMember` / `requiredMemberList` / `optionalMemberList`。 |
| `requiredMember(key, finder)` / `optionalMember(...)` | 在 `initDexKit()` 中查找单个 Method/Field/Class。 |
| `requiredMemberList(...)` / `optionalMemberList(...)` | 查找成员列表。 |
| `findClass*` / `findAndHookMethod*` / `findAndChainMethod*` / `hookAllMethods*` / `hookAllConstructors*` | 类查找与 Hook 便捷方法。 |
| `getObjectField` / `setObjectField` / `callMethod` / `callStaticMethod` | 反射字段/方法操作。 |
| `registerApplicationHook()` / `runOnApplicationAttach(...)` | Application attach 生命周期监听。 |
| `setResReplacement` / `setDensityReplacement` / `setObjectReplacement` | 资源替换（依赖 `ResourcesTool`）。 |
| `debugProtect` / `debugRethrow` | 开发调试用的 callback 异常包装。 |

### 4.2 模块匹配与注解处理

#### `@HookBase` (`library/processor/.../HookBase.java`)

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface HookBase {
    String targetPackage();
    int minSdk() default -1;
    int maxSdk() default -1;
    float minOSVersion() default -1F;
    float maxOSVersion() default -1F;
    int deviceType() default 0; // 0=ALL, 1=PAD_ONLY, 2=PHONE_ONLY
}
```

#### `HookBaseProcessor` (`library/processor/.../HookBaseProcessor.java`)

- 使用 `com.google.auto.service.AutoService` 注册。
- 编译期扫描所有 `@HookBase` 注解。
- 生成 `com.sevtinge.hyperceiler.libhook.base.DataBase` 类，包含 `HashMap<String, DataBase> get()`。
- Map 的 key 为 `BaseLoad` 子类全限定名，value 为注解元数据。

#### `ModuleMatcher` (`library/libhook/.../base/ModuleMatcher.java`)

`shouldLoad(DataBase data, String packageName)` 按以下顺序过滤：

1. **安全模式**：检查该包是否被配置为安全模式（跳过所有 Hook）。
2. **安全检查**：对 `com.miui.securitycenter`、`com.android.camera`、`com.miui.home` 检测修改/版本，非调试模式下不匹配则跳过。
3. **包名匹配**：精确匹配 > `VariousSystemApps` 系统应用通配 > 无精确匹配时的 `VariousThirdApps` 兜底。
4. **SDK 版本**：`minSdk <= currentSdk <= maxSdk`。
5. **OS 版本**：`minOSVersion <= HyperOSVersion <= maxOSVersion`。
6. **设备类型**：PAD / PHONE / ALL。

### 4.3 工具与 API 封装

#### `EzxHelpUtils` (`library/libhook/.../utils/hookapi/tool/EzxHelpUtils.kt`)

Kotlin `object`，对 EzXHelper 和 libxposed API 做了一层 Java 友好的静态封装：

- 类查找：`findClass*` / `findClassIfExists*`
- 字段查找与读写：实例/静态、Object/Boolean/Int/Long/Float
- 方法调用：`callMethod*` / `callStaticMethod*` / `findMethodBestMatch*` / `findMethodExactIfExists*`
- Hook：`hookMethod*` / `chain*` / `findAndChainMethod*` / `hookAllMethods*` / `hookAllConstructors*` / `returnConstant` / `DO_NOTHING`
- Application 生命周期：`registerApplicationHook` / `runOnApplicationAttach`

#### `DexKit` (`library/libhook/.../utils/hookapi/dexkit/DexKit.java`)

薄 Java 门面，委托给 `DexKitCacheManager`：

| 方法 | 说明 |
|------|------|
| `ready(PackageReadyParam, tag)` | 初始化 DexKit 会话。 |
| `findMember(key, finder)` | 查找单个成员并缓存。 |
| `findMemberList(key, finder)` | 查找成员列表并缓存。 |
| `close()` | 释放原生桥并刷新缓存。 |
| `deleteAllCache(context, scopeList)` | 从设置界面清除缓存文件。 |

### 4.4 App 核心类

#### `Application` (`app/.../Application.java`)

- 继承 `fan.app.Application`，实现 `XposedServiceHelper.OnServiceListener`。
- `attachBaseContext`：多语言包装 + `AppInitializer.attach`。
- `onCreate`：初始化日志、崩溃处理、Framework 状态、OOBE 同步。
- `onServiceBind`：设置模块激活状态，同步远程 prefs，刷新 Banner。
- `onServiceDied`：清理状态。
- `setupCrashHandler`：捕获未处理异常，写入 `AppCrashStore`。

#### `PrefsBridge` (`library/common/.../utils/PrefsBridge.java`)

双进程 SharedPreferences 桥：

- `initForApp(Context)`：App 进程初始化本地物理 prefs（设备加密存储）。
- `initForHook(SharedPreferences)` / `setRemotePrefs(...)`：Hook 进程绑定 LSPosed 远程 prefs。
- `putByApp(key, value)` / `removeByApp(key)` / `clearAllByApp()`：仅 App 进程写入，自动同步到远程并通知 ContentObserver。
- `getBoolean` / `getString` / `getInt` / `getLong` / `getFloat` / `getStringSet`：自动根据进程选择读取源，支持 Hook 进程临时缓存。

#### `LogManager` (`app/.../log/LogManager.java`)

- 单例，必须在 `Application` 中初始化。
- 拦截 App 自身 `AndroidLog` 输出并写入 Room 数据库。
- 提供查询、清空、导出 zip（含设备信息、作用域、LSPosed 日志）。
- 关键常量：`APP_MODULE = "App"`、`FILTERED_MODULE = "Xposed"`。导出文件名为 `hyperceiler_logs_yyyyMMdd_HHmmss.zip`。

### 4.5 安全模式与崩溃处理

#### `CrashMonitor` (`library/libhook/.../safecrash/CrashMonitor.kt`)

在 SystemServer 中初始化：

- 允许模块自身后台启动 Activity（Hook BAL / ActivityStarter）。
- Hook `AppErrors.handleAppCrashInActivityController`，监听作用域应用崩溃。
- Hook `PackageWatchdog` 的 RescueParty 逻辑，触发安全模式。
- 记录崩溃时间窗口，达到阈值后调用 `SafeModeHandler.onCrashDetected()`。

#### `SafeModeHandler` / `CrashScope` / `SafeModeFragment`

- `CrashScope`：定义安全模式作用域别名（systemui、home、settings、center、demo）与 prop/config key。
- `SafeModeFragment`：设置页中手动开关安全模式。
- `AppCrashStore`：App 进程崩溃本地记录，启动时跳转 `ExceptionCrashActivity`。

---

## 5. Hook 注册与加载全流程

```
LSPosed 加载模块
    │
    ▼
XposedInitEntry.onModuleLoaded()
    - 初始化 PrefsBridge (remote)
    - BaseLoad.init(this)  // 保存 XposedModule 句柄
    │
    ├── 系统进程 ──► onSystemServerStarting()
    │                 - CrashMonitor 初始化
    │                 - CorePatch / FlagSecure 条件加载
    │                 - invokeInit(system) 遍历 DataBase
    │
    └── 应用进程 ──► onPackageReady()
                      - 仅处理 isFirstPackage()
                      - invokeInit(packageName) 遍历 DataBase

invokeInitInternal(packageName, loader)
    │
    ▼
DataBase.get() 获取所有 BaseLoad 子类元数据
    │
    ▼
ModuleMatcher.shouldLoad(data, packageName)
    - 安全模式 / 安全检查 / 包名 / SDK / OS / 设备类型
    │
    ▼
classLoader.loadClass(className).newInstance()
    │
    ▼
BaseLoad.onLoad(param)
    - 设置 ClassLoader / PackageName / lpparam
    - 加载模块资源
    - executeHook()
        │
        ▼
    onPackageLoaded()  [子类实现]
        │
        ▼
    initHook(new XXXHook(), enabled)
        │
        ├── 无需 DexKit ──► runHookInit(hook) ──► hook.init()
        │
        └── 需要 DexKit ──► 加入 mPendingDexKitHooks
                              │
                              ▼
                    flushPendingDexKitHooks() (线程池并行)
                              │
                              ▼
                    hook.initDexKit() 解析成员
                              │
                              ▼
                    runHookInit(hook) ──► hook.init() 消费成员
```

---

## 6. 主要 Hook 模块分类

### 6.1 按目标应用的 `BaseLoad` 子类

| 类 | 目标包 | 主要职责 |
|----|--------|----------|
| `SystemFrameworkB` | `system` | 核心破解、手势、小窗、音量、显示、系统杂项、平板设置。 |
| `SystemUIB` | `com.android.systemui` | 状态栏、锁屏、控制中心、磁贴、媒体卡片、导航栏、其他。 |
| `VariousSystemApps` | `VariousSystemApps` | 多个 MIUI 系统应用的通用 Hook（对话框、OverScroll、标题折叠）。 |
| `SecurityCenter` | `com.miui.securitycenter` | 安全中心、电池、游戏、视频、应用锁、侧边栏等。 |
| `Home`（桌面） | `com.miui.home` | Dock、抽屉、文件夹、手势、布局、最近任务、图标/标题、小部件。 |
| `Browser` / `QuickSearchBox` / `GetApps` / `MediaEditor` / `Screenshot` / `Backup` / `PackageInstaller` / ... | 对应包名 | 浏览器、搜索、应用商店、相册编辑、截图、备份、安装器等定制。 |

### 6.2 `rules` 目录分类（部分示例）

| 目录 | 代表功能 |
|------|----------|
| `rules/systemui/statusbar/` | 状态栏图标、时钟、网络速度、移动信号、WiFi、电池样式 |
| `rules/systemui/controlcenter/` | 控制中心样式、通知、天气、磁贴、媒体卡片 |
| `rules/systemui/lockscreen/` | 锁屏提示、状态栏隐藏、PIN 打乱、人脸解锁 |
| `rules/systemframework/corepatch/` | 签名/降级/摘要/共享用户/验证代理破解 |
| `rules/systemframework/freeform/` | 小窗数量、黑名单、气泡、前台置顶 |
| `rules/systemframework/display/` | 全局深色、模糊、截图、主题、挖孔 |
| `rules/systemframework/volume/` | 默认音量流、步数、安全音量、首次按下 |
| `rules/home/dock/` / `drawer/` / `folder/` / `gesture/` / `layout/` / `recent/` / `title/` / `widget/` | 桌面各区域定制 |
| `rules/securitycenter/battery/` / `sidebar/` / `beauty/` / `app/` / `other/` | 安全中心各功能定制 |
| `rules/browser/` / `getapps/` / `mediaeditor/` / `screenshot/` / `backup/` | 第三方/系统应用定制 |

---

## 7. 依赖关系

### 7.1 项目模块依赖图

```
app
├── library:core
│   ├── library:common
│   ├── library:provision
│   └── appiconloader
├── library:common
│   └── MIUIX bundle + libxposed-api (compileOnly)
└── room-runtime / room-ktx

library:libhook
├── library:common
├── library:processor (api + annotationProcessor)
├── library:xposed-api-101
├── library:hidden-api (compileOnly)
├── libxposed-api / libxposed-service
├── dexkit / ezxhelper-core / hiddenapibypass
├── gson / hyperfocusapi / superlyricapi / lunarcalendar
└── expansion
```

### 7.2 关键外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Android Gradle Plugin | 9.2.1 | 构建 |
| Gradle Wrapper | 9.5.0 | 构建 |
| lsparanoid | 0.6.0 | Release 构建时混淆/随机化 |
| libxposed-api | 101.0.1 | LSPosed 模块 API |
| libxposed-service | 101.0.0 | LSPosed 服务绑定 |
| DexKit | 2.2.0 | 反混淆查找方法/字段/类 |
| EzXHelper | 3.1.1-rc1 | Xposed Hook 便捷封装 |
| HiddenApiBypass | 6.1 | 绕过 Android 隐藏 API 限制 |
| MIUIX | 1.0.12.5 | MIUI 风格 UI 组件 |
| Room | 2.8.4 | 日志、功能搜索 FTS 数据库 |
| Gson | 2.14.0 | JSON 序列化（崩溃记录等） |

---

## 8. 构建与运行

### 8.1 环境要求

- JDK 21（`JAVA_HOME` 指向 JDK 21）
- Android SDK，已安装 `platforms;android-37` 与 `build-tools;37.0.0`
- Git（构建脚本通过 Git 获取版本号、分支、Commit Hash）
- 可选：GitHub Packages 凭据 `GIT_ACTOR` / `GIT_TOKEN`（读取私有依赖）

### 8.2 常用构建命令

```powershell
# 查看 app 模块任务
./gradlew :app:tasks

# 调试构建
./gradlew :app:assembleDebug

# 发布构建（需要 signing.properties 或环境变量）
./gradlew :app:assembleRelease

# 仅构建 libhook 模块
./gradlew :library:libhook:assembleDebug
```

### 8.3 签名配置

在 `app/build.gradle.kts` 中：

- 优先读取项目根目录 `signing.properties`：
  - `storeFile`
  - `storePassword`
  - `keyAlias`
  - `keyPassword`
- 若不存在，回退到环境变量 `STORE_FILE`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- 若都没有，Debug 使用自动生成的 debug keystore。

### 8.4 安装与调试

1. 构建成功后 APK 位于 `app/build/outputs/apk/<variant>/HyperCeiler-<versionName>-<suffix>.apk`。
2. 通过 ADB 安装：`adb install -r app/build/outputs/apk/debug/...apk`。
3. 在 LSPosed 管理器中启用模块并选择作用域。
4. 查看 LSPosed 日志或 App 内 LogViewer。

### 8.5 构建变体

| 变体 | 说明 |
|------|------|
| `debug` | 关闭 R8/混淆，附加构建时间后缀，便于调试。 |
| `release` | 启用优化，使用短 Git Hash，附加日期后缀。 |
| `beta` | 与 release 类似，使用长 Git Hash。 |
| `canary` | 附加 Git Hash + versionCode。 |

---

## 9. 安全与调试机制

### 9.1 安全模式

- **触发方式**：
  - 手动：在设置页 `SafeModeFragment` 开启。
  - 自动：`CrashMonitor` 检测到某作用域应用短时间内连续崩溃达到阈值。
- **生效位置**：`ModuleMatcher.isInSafeMode()` 在加载流程最前端拦截，跳过该包所有 Hook。
- **Prop 键**：`persist.sys.hyperceiler.safemode`（通过 `CrashScope.PROP_SAFE_MODE`）。

### 9.2 框架校验

`XposedInitEntry.isFrameworkAllowedForCurrentRuntime()` 比较当前框架名称/版本/版本码与上次被阻止的框架信息，允许用户在被屏蔽的框架更新后临时恢复 Hook。

### 9.3 崩溃捕获

- **App 进程**：`Application.setupCrashHandler` 捕获未处理异常，写入 `AppCrashStore`，下次启动 `SplashActivity` 跳转 `ExceptionCrashActivity`。
- **Hook 进程**：`CrashMonitor` 在 SystemServer 中 Hook `AppErrors` 与 `PackageWatchdog`，触发安全模式并阻止系统 RescueParty 重启。

### 9.4 调试日志

- `XposedLog`：Hook 进程日志，可按模块/包名/级别过滤。
- `AndroidLog`：App 进程日志。
- `LogStatusManager`：统一日志级别控制与同步。

---

## 10. 关键文件索引

| 文件 | 说明 |
|------|------|
| `build.gradle.kts` | 根项目插件声明。 |
| `settings.gradle.kts` | 模块包含、仓库、GitHub Packages 凭据。 |
| `gradle/libs.versions.toml` | 依赖版本中心。 |
| `app/build.gradle.kts` | App 构建、签名、版本、BuildConfig。 |
| `app/src/main/AndroidManifest.xml` | 权限、Activity、Service、Provider、Xposed 模块声明。 |
| `library/libhook/build.gradle.kts` | Hook 模块构建与依赖。 |
| `library/libhook/src/main/AndroidManifest.xml` | SharedPrefsProvider 声明。 |
| `library/libhook/.../base/XposedInitEntry.java` | Xposed 入口。 |
| `library/libhook/.../base/BaseLoad.java` | 应用级 Hook 加载器。 |
| `library/libhook/.../base/BaseHook.java` | Hook 基类。 |
| `library/libhook/.../base/ModuleMatcher.java` | 模块匹配器。 |
| `library/processor/.../HookBase.java` | `@HookBase` 注解。 |
| `library/processor/.../HookBaseProcessor.java` | 生成 `DataBase` 的注解处理器。 |
| `library/libhook/.../utils/hookapi/tool/EzxHelpUtils.kt` | Hook API 封装。 |
| `library/libhook/.../utils/hookapi/dexkit/DexKit.java` | DexKit 门面。 |
| `library/common/.../utils/PrefsBridge.java` | 双进程 prefs 桥。 |
| `app/.../Application.java` | 应用入口。 |
| `app/.../log/LogManager.java` | 日志管理。 |
| `app/.../home/safemode/SafeModeFragment.java` | 安全模式 UI。 |
| `library/libhook/.../safecrash/CrashMonitor.kt` | 系统崩溃监控。 |

---

## 11. 开发约定

1. **新增 Hook**：创建 `BaseHook` 子类，在对应 `BaseLoad` 子类的 `onPackageLoaded()` 中通过 `initHook(new YourHook(), enabled)` 注册。
2. **DexKit 使用**：重写 `useDexKit() = true`，在 `initDexKit()` 中解析成员，在 `init()` 中消费成员；禁止在 Hook 回调中首次触发 DexKit。
3. **Prefs 写入**：仅允许在 App 进程通过 `PrefsBridge.putByApp` 写入；Hook 进程只读。
4. **日志输出**：Hook 进程使用 `XposedLog.*()`，App 进程使用 `AndroidLog.*()`。
5. **安全模式**：若某 Hook 可能导致目标应用循环崩溃，应确保其崩溃能被 `CrashMonitor` 识别并进入安全模式。
