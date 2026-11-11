# 03 入口与钩子流程

本文件说明「从 LSPosed 加载模块」到「某条规则真正 hook 成功」的完整调用链，以及热重载与失败处理。

## 1. 入口类

`library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.java`

声明来源 `META-INF/xposed/java_init.list`；实现 libxposed API 102 的 `XposedModule`。关键字段：

| 字段 | 说明 |
| --- | --- |
| `processName` | 当前进程名（`ModuleLoadedParam.getProcessName()`） |
| `mTarget` | 当前 `PackageTarget`（包名 / `ApplicationInfo` / `ClassLoader` / `isSystemServer`） |
| `prefsInited` / `runtimeInitialized` | 双检锁标志位 |
| `EXTRA_MAGIC` / `EXTRA_VERSION` | 热重载 extras 的版本头，布局变化必须递增 |
| `LAUNCHER_PACKAGE` | `"com.miui.home"`，用于桌面进程特判 |

## 2. 生命周期回调

```
onModuleLoaded(param)
  └─ initModule → initializeRuntime(param, true)
        ├─ processName = param.getProcessName()
        ├─ refreshFrameworkCapabilities()      // getFrameworkProperties() → PROP_CAP_REMOTE / PROP_CAP_SYSTEM
        ├─ BaseLoad.init(this)                 // 先 attach 日志，再读配置，保证 initPrefs 失败可见
        ├─ initPrefs()                         // 失败仅告警，稍后 target-ready 重试
        ├─ EzXposed.initOnModuleLoaded(this, param)
        └─ 首次：EzXposed.onTargetReady(this::installCurrentTargetHooks)

onSystemServerStarting(param)                  // system_server 进程
  ├─ if (!systemHooksAvailable) return         // 框架未声明 PROP_CAP_SYSTEM 时整体跳过
  ├─ mTarget = PackageTarget.ofSystemServer(param)
  └─ EzXposed.initOnSystemServerStarting(param)

onPackageReady(param)                          // 普通应用进程
  ├─ super.onPackageReady(param)
  ├─ if (!param.isFirstPackage()) return       // 只处理首个包（多包进程取首个）
  ├─ mTarget = PackageTarget.ofPackageReady(param)
  └─ EzXposed.initOnPackageReady(param)

onHotReloading(param)   → 见 §5
onHotReloaded(param)    → 见 §5
```

要点：**初次加载与热重载共用同一条同步初始化路径** `installCurrentTargetHooks`，通过 `EzXposed.onTargetReady` 注册，避免两条路径行为漂移。

## 3. 同步初始化：installCurrentTargetHooks

```
installCurrentTargetHooks()
  ├─ target == null → IllegalStateException
  ├─ BaseLoad.beginHookInitialization()          // 清空本代失败记录
  ├─ if (!remotePrefsAvailable) return           // 框架未声明 PROP_CAP_REMOTE → 不加载任何规则
  ├─ if (isSystemServer && !systemHooksAvailable) return  // 未声明 PROP_CAP_SYSTEM → 不加载系统规则
  ├─ initPrefs()                                 // 失败 → record*Failure + 抛 IllegalStateException
  ├─ if (!prepareHookLoad(pkg)) {                 // prepareHookLoad 返回 true 表示「跳过」
  │      // 桌面进程且是「prefs 重试成功」→ NativeHomeHooksOS4.onPreferencesReadyAfterRetry()
  │      attachHookLogLevelObserver(isSystemServer)
  │      if (isSystemServer) loadSystemEntryHooks(target)
  │      invokeInit(target)
  │  } else if (!EzXposed.getHotReloadActive()) {
  │      detach()                                   // 本进程无可加载规则 → 停止接收回调（热重载代际不 detach）
  │  }
  └─ BaseLoad.verifyHotReloadInitialization()    // 热重载期间把规则失败转成异常
```

### 3.1 总开关与框架门禁

```java
private boolean isHookEnabled() {
    return PrefsBridge.getBoolean(PREF_ALLOW_HOOK, false)      // allow_hook
        && isFrameworkAllowedForCurrentRuntime();              // framework_api_allow_hook 或指纹门禁
}
```

- `PREF_ALLOW_HOOK`：App 顶部的总开关，默认 **false**（未在 App 内开启时注入进程什么都不做）。
- `isFrameworkAllowedForCurrentRuntime()`：
  - `framework_api_allow_hook` 为 true 直接放行；
  - 否则调用 `FrameworkHookGate.isAllowed(false, framework_check_reason, runningFingerprint, storedFingerprint)`；
  - 判定逻辑：只有当「上次记录的指纹存在」且「与当前运行指纹不同」时才拒绝（说明系统框架已变化，旧 hook 可能不兼容），并写入 `framework_check_*` 供 App 展示。

### 3.2 system_server 专属入口

`loadSystemEntryHooks(target)` 只对 `system` 进程执行，加载三组不属于 `@HookBase` 表的 hook：

| 组件 | 条件 | 说明 |
| --- | --- | --- |
| `CrashMonitor` | 无条件 | 捕获 system_server 异常并上报（配合 App 崩溃处理） |
| `CorePatch` | `system_framework_core_patch_enable` | 核心破解：`DowngradeCheckPatch` / `AuthCreakPatch` / `DigestCreakPatch` / `ExactSignCheckPatch` / `SharedUserPatch` / `VerificationAgentPatch` |
| `FlagSecure` | `system_other_flag_secure` | 允许截屏/录屏 |

`CorePatch` 定义于 `app/CorePatch/CorePatch.java`，本身不带 `@HookBase`，因此不会进入 `DataBase` 表。

### 3.3 规则分发：invokeInit

```java
protected void invokeInit(PackageTarget target) {
    invokeInitInternal(target.getPackageName(), module -> module.onLoad(target));
}

private void invokeInitInternal(String packageName, ModuleLoader loader) {
    HashMap<String, DataBase> dataMap = DataBase.get();     // 编译期生成
    ClassLoader classLoader = getClass().getClassLoader();
    ModuleMatcher.MatchContext context = buildMatchContext(packageName, dataMap);
    ModuleMatcher matcher = new ModuleMatcher(context);
    dataMap.forEach((className, data) -> {
        if (!matcher.shouldLoad(data, packageName)) return;
        BaseLoad module = (BaseLoad) classLoader.loadClass(className)
                                             .getDeclaredConstructor().newInstance();
        loader.load(module);        // → module.onLoad(target)
    });
}
```

`buildMatchContext` 决定三个关键布尔：`systemServer`（包名 == `"system"`）、`exactMatch`（`DataBase` 中是否存在完全同名 `targetPackage`）、`debugMode`（`development_debug_mode`）。

## 4. 加载条件：ModuleMatcher

`ModuleMatcher.shouldLoad(data, packageName)` 的判断顺序：

1. **安全模式**：`CrashScope` 判定该包处于崩溃安全模式（系统属性 `persist.service.hyperceiler.crash.report` 或 `*_safe_mode_enable` 配置）→ 拒绝。
2. **安全检查**：`SECURITY_CHECK_PACKAGES = {com.miui.securitycenter, com.miui.home}`，且非 system_server：
   - `CheckModifyUtils.getCheckResult()` 为 true（检测到修改环境）
   - 且 `DebugModeUtils.getChooseResult() == 0`
   - 且非 debug 模式 → 拒绝加载。
3. **包名匹配** `matchPackage`：
   - 精确相等；
   - 或 `data.targetPackage` 为 `VARIOUS_SYSTEM_APPS` 且包名属于 `com.miui*` / `com.xiaomi*` / `com.android*` 且非 system_server；
   - 或当前包没有任何精确匹配项时，以 `VARIOUS_THIRD_APPS` 兜底（`VariousThirdApps` 内部再按包名白名单细分）。
4. **SDK 区间** `matchSdkVersion`：`-1` 视为不限，否则要求 `minSdk <= Build.VERSION.SDK_INT <= maxSdk`。
5. **OS 版本区间** `matchOSVersion`：基于 `getHyperOSVersion()`（如 2.0 / 3.0），`-1F` 视为不限。
6. **设备类型** `matchDeviceType`：`DEVICE_ALL=0` / `PAD_ONLY=1` / `PHONE_ONLY=2`，由 `DeviceHelper.Hardware.isPadDevice` 判定。

## 5. BaseLoad：每个目标应用的规则容器

`BaseLoad` 是抽象类，24 个子类由 `@HookBase` 注解登记。职责：

### 5.1 静态上下文

```java
public static final String SYSTEM_SERVER = "system";
private static volatile PackageTarget sTarget;
private static volatile XposedInterface sXposed;
private static volatile String sCurrentHookTag;   // 当前正在加载的 BaseLoad 子类名
```

规则类通过 `BaseLoad.getClassLoader()` / `getPackageName()` / `isSystemServer()` / `getTag()` 获取宿主信息；`BaseHook` 再包一层转发。

### 5.2 加载流程

```
module.onLoad(target)
  ├─ synchronized(sLock)：sTarget = target；sCurrentHookTag = 类名；
  │                       mDexKitSessionPrepared = false；mPendingDexKitHooks.clear()
  ├─ loadModuleResources()
  │    ├─ 自身包（com.sevtinge.hyperceiler）→ 跳过
  │    ├─ EzXposed.getAppContextOrNull() 非空 → EzResources.inject(context)
  │    └─ 否则 ContextUtils.getWaitContext(ctx -> EzResources.inject(ctx), isAndroid=系统服务)
  └─ executeHook()
       ├─ onPackageLoaded()                 // 子类实现：按开关 initHook(...)
       ├─ finally flushPendingDexKitHooks() // 并行 initDexKit + 统一 init
       └─ finally closeDexKitSession()      // DexKit.close()（system_server 不启用）
```

资源加载的目的：让规则代码能读取**模块自己的** `R` 资源（字符串、颜色、dimen），而不依赖目标应用资源。

### 5.3 initHook 的三种形态

| 签名 | 语义 |
| --- | --- |
| `initHook(BaseHook hook)` | 无条件初始化 |
| `initHook(BaseHook hook, boolean isInit)` | 由布尔开关决定 |
| `initHook(BaseHook hook, BooleanSupplier condition)` | 惰性求值；执行前会先 flush 已排队的 DexKit hook |

内部流程 `initHookInternal`：

```
if (hook == null || !shouldInit) return;
if (hook.useDexKit() && !isSystemServer()) {
    prepareDexKitSession(hook.TAG);      // 首次触发 DexKit.ready(target, tag)
    mPendingDexKitHooks.add(hook);       // 延后批量执行
    return;
}
flushPendingDexKitHooks();               // 保证执行顺序：非 DexKit hook 前先清空队列
runHookInit(hook);                       // hook.init() + 日志 "Hook Success"
```

DexKit hook 延后执行的原因：DexKit 的查询开销大且可并行，`flushPendingDexKitHooks` 用 `ThreadPoolManager` 并发执行 `initDexKit()`，再在主线程按原顺序 `init()`。任一 `initDexKit` 抛错 → 跳过该 hook 并同时记入「本代初始化失败」与「热重载校验失败」。

### 5.4 失败记录与热重载阻断

| 方法 | 作用 |
| --- | --- |
| `beginHookInitialization()` | 每个目标进程初始化前清空 `sGenerationInitializationFailures` |
| `recordHookInitializationFailure(source, error)` | 记录单条规则失败（初始加载不中断宿主） |
| `getHotReloadBlockReason()` | 有失败记录时返回原因字符串；`onHotReloading` 据此拒绝热重载 |
| `beginHotReloadVerification()` / `recordHotReloadInitializationFailure` / `verifyHotReloadInitialization()` / `endHotReloadVerification()` | 热重载新一代的严格校验：任何规则失败 → `IllegalStateException` |
| `prepareHotReload()` | 转调 `BaseHook.prepareHotReload()`，清空 `sTarget` / `sCurrentHookTag` |

设计意图：初次加载「尽力而为」（单条失败不影响系统启动），但一旦有失败，这一代就不能作为热重载的旧代（因为无法保证原子替换），必须重启目标进程恢复干净状态。

## 6. BaseHook：规则基类

`library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/BaseHook.java`

### 6.1 抽象契约

```java
public abstract class BaseHook {
    public String TAG = getClass().getSimpleName();
    protected abstract void init();              // 子类实现实际 hook
    protected boolean useDexKit() { return false; }
    protected boolean initDexKit() { return true; }   // true=继续 init，false=跳过
}
```

典型 DexKit 规则（`rules/browser/DebugMode.java`）：

```java
@Override protected boolean useDexKit() { return true; }

@Override protected boolean initDexKit() {
    mDebugModeMethod1 = requiredMember("DebugMode1", bridge -> bridge.findMethod(
        FindMethod.create().matcher(MethodMatcher.create()
            .usingStrings("pref_key_debug_mode_new").returnType(boolean.class))).singleOrNull());
    return true;      // false 则本 hook 被跳过
}

@Override protected void init() { /* hook(mDebugModeMethod1, ...) */ }
```

`requiredMember(name, supplier)` / `requiredMemberList(...)`：查不到即抛异常（该 hook 记为失败）；`optionalMember` / `optionalMemberList`：查不到返回 null/空集合，允许降级。

### 6.2 热重载清理注册

规则若注册了系统级监听器，必须通过以下 API 登记清理动作，否则热重载后会残留旧代监听器（持有旧 ClassLoader，导致内存泄漏或重复回调）：

| API | 用途 |
| --- | --- |
| `registerHotReloadCleanup(Runnable)` | 通用清理 |
| `registerReceiverHotReloadCleanup(...)` | 注销 BroadcastReceiver |
| `registerContentObserverHotReloadCleanup(...)` | 注销 ContentObserver |
| `registerNetworkCallbackHotReloadCleanup(...)` | 注销 NetworkCallback |
| `registerActivityLifecycleHotReloadCleanup(...)` | 注销 ActivityLifecycleCallbacks |
| `registerSensorListenerHotReloadCleanup(...)` | 注销 SensorEventListener |
| `registerHandlerHotReloadCleanup(...)` | 移除 Handler 回调 |

`BaseHook.ensureApplicationHookInstalled()` 注册的 `Application.attach` hook 由 EzHookTool 统一跟踪，热重载时按 `executable + 自动 id` 原子替换，无需再手工 `trackHookHandle`（重复登记会在 `prepareHotReload` 提前 unhook，导致 EzHookTool 恢复阶段操作已失效句柄）。

运行时状态可跨代保留：`putHotReloadRuntimeState(key, value)` / `getHotReloadRuntimeState(key)`；入口通过 `snapshotHotReloadRuntimeState()` 打包、`restoreHotReloadRuntimeState(state)` 还原。

### 6.3 反射与工具能力

`BaseHook` 内置大量宿主操作工具（供规则直接调用）：

- 类/成员查找：`findClass`、`findClassIfExists`、`findField`、`findMethodBestMatch`；
- 读写：`getObjectField` / `setObjectField` / `getBooleanField` / `setBooleanField` / `getIntField` / `setIntField` 等；
- 调用：`callMethod`、`callStaticMethod`、`newInstance`；
- 附加数据：`setAdditionalInstanceField` / `getAdditionalInstanceField`（按对象挂载状态）；
- 上下文：`getClassLoader()`、`getPackageName()`、`getLpparam()`、`getSystemParam()`、`xposed`；
- 应用级 hook：`ensureApplicationHookInstalled(...)`（`APPLICATION_HOOKS` 保证只装一次）。

## 7. 新增一个 Hook 规则的步骤

以「浏览器某功能」为例：

1. 在 `rules/browser/` 新建 `MyFeature.java extends BaseHook`，实现 `init()`（需要 DexKit 时覆写 `useDexKit()` + `initDexKit()`）。
2. 在 `library/core/src/main/res/xml/browser.xml` 增加 `<SwitchPreference>`，key 例如 `browser_my_feature`。
3. 在 `app/.../res/xml/settings_header.xml` 确认该分组已登记（决定搜索结果与分组图标）。
4. 在 `app/Browser.java` 的 `onPackageLoaded()` 增加：
   ```java
   initHook(new MyFeature(), PrefsBridge.getBoolean("browser_my_feature"));
   ```
5. 若规则注册了监听器/回调，补 `registerXxxHotReloadCleanup(...)`。
6. 编译后在 LSPosed 中对该应用「重启作用域」或用 App 的热重载按钮验证。

新增**受支持应用**则：新建 `app/Xxx.java extends BaseLoad` + `@HookBase(targetPackage = "…", minSdk/maxSdk/minOSVersion/maxOSVersion/deviceType)`，并把包名加进 `scope.list` 与 README 作用域表。注解处理器会自动把新类写进 `DataBase`。

## 8. 热重载流程

### 8.1 onHotReloading（旧代 → 拒绝/接受）

```
onHotReloading(param)
  ├─ layoutHost = target.isSystemServer()
  │    └─ HomeLayoutNativeEndpointOS4.pauseForHotReload() 失败 → resumeAfterRejectedHotReload() + return false
  ├─ ambientHost = packageName == "com.android.systemui"
  │    └─ AmbientNativeColorOS4.pauseForHotReload() 失败 → 提示需重启 SystemUI + return false
  └─ prepareHotReloadState(param)
       ├─ BaseLoad.getHotReloadBlockReason() != null → 拒绝
       ├─ buildHotReloadExtras()   // MAGIC / VERSION / ApplicationInfo / AppContext / runtimeState
       ├─ EzXposed.handleHotReloading(param, extras)  → 失败拒绝
       └─ prepareForHotReload()
            ├─ ThreadPoolManager.shutdownAndAwait(500ms)   // 先停旧代线程
            ├─ BaseLoad.prepareHotReload()
            └─ LogStatusManager.detachHookLogLevelObserver()
```

两个 native 暂停点解决的是同一类问题：**ART 不会把已加载的 JNI 库绑定到新的模块 ClassLoader**，所以必须在类加载器切换前冻结 native 后端；已加载的场景直接拒绝热重载并要求重启进程。

`buildHotReloadExtras` 对普通应用进程强制要求 `ApplicationInfo` 非空（否则 DexKit 无法重建缓存路径），system_server 允许为 null。

### 8.2 onHotReloaded（新代初始化）

```
onHotReloaded(param)
  ├─ initializeRuntime(param, true)          // 幂等；不重放 onModuleLoaded，需手动重建
  ├─ BaseLoad.beginHotReloadVerification()
  ├─ EzXposed.restoreHotReloadedAutomatically(this, param, extras -> {
  │      HotReloadExtras restored = restoreHotReloadExtras(extras);
  │      if (restored == null) throw new IllegalStateException(...);   // 版本/长度不符整体拒绝
  │      restoreEzXposedRuntime(restored);
  │  })
  ├─ BaseLoad.verifyHotReloadInitialization()   // 有失败 → 抛异常
  └─ finally BaseLoad.endHotReloadVerification()
```

`restoreEzXposedRuntime` 依次恢复 `processName`、`AppContext`、`BaseHook` 运行时状态，再用 `PackageTarget.restored(...)` 重建 `mTarget`（普通应用必须由 extras 提供 `ApplicationInfo`）。

`EXTRA_MAGIC`/`EXTRA_VERSION` 校验不通过时**整体拒绝**，不做部分降级——避免跨版本槽位误读。

## 9. App 侧触发热重载

`library/core/.../utils/HotReloadManager.java`：

- `isHotReloadAvailable()`：`ScopeManager.getService().getApiVersion() >= XposedService.API_102`；
- `getRunningTargets()`：列出本模块当前注入的目标；
- `hotReloadByPackage(pkg, callback)` / `hotReloadAll(callback)`：发起显式热重载，结果回调切主线程；
- `HOT_RELOAD_TIMEOUT_MS = 15_000L`：framework 回调丢失时避免页面永久停留在「正在重载」。

## 10. 安全模式（CrashScope）

`library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/safecrash/CrashScope.kt`

- 系统属性 `PROP_SAFE_MODE = "persist.service.hyperceiler.crash.report"`；
- `scopeMap` 把别名映射到配置键：`systemui`、`settings`、`home`、`center`、`demo`；
- `safeModeConfigMap` 对应 `system_ui_safe_mode_enable`、`home_safe_mode_enable` 等开关；
- `getCrashingAliases()` 返回当前处于安全模式的包别名，`ModuleMatcher` 在第一步就据此跳过全部 hook。

App 侧崩溃链路：注入进程异常 → `CrashReceiver`（`permission=hyperceiler.permission.APP_CRASH`，action `hyperceiler.intent.action.APP_CRASH`）→ `CrashService` / `CrashActivity` 记录并提示用户关闭最近启用的功能。
