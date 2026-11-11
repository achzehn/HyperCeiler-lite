# 05 App 界面与数据

本文件覆盖 `:app` 模块的界面体系、启动链、搜索索引和日志系统。

## 1. 启动链

```
Launcher 图标
  └─ <activity-alias .ui.LauncherActivity> → SplashActivity
       ├─ AppCrashStore.hasPendingCrash(this) → CrashActivity（先看崩溃）
       ├─ OobeUtils.isProvisioned(this) == true  → HomePageActivity
       └─ 否则                                    → DefaultActivity（provision 模块的引导）
```

`SplashActivity`（[SplashActivity.java](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/java/com/sevtinge/hyperceiler/ui/SplashActivity.java)）只做分发，且 `startActivity` 后立即 `finish()`。

Manifest 关键声明：

- `SplashActivity` 带 `android.intent.category.MODULE_SETTINGS`（LSPosed 里点模块「打开」会走这里）；
- `HomePageActivity` 使用 `HomePageTheme`；
- `.dashboard.SubSettings` 为设置子页统一宿主。

## 2. Application

[Application.java](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/java/com/sevtinge/hyperceiler/Application.java) 继承 `fan.app.Application` 并实现 `XposedServiceHelper.OnServiceListener`。

### 2.1 生命周期

| 时机 | 动作 |
| --- | --- |
| `attachBaseContext(base)` | `LanguageHelper.wrapContext(base)`（语言切换）+ `AppInitializer.attach(this)` |
| `onCreate()` | `AppInitializer.initOnAppCreate` → `syncHookAvailabilityIfUnlocked` → `FrameworkStatusManager.init` → `LogManager.init(this, DeviceInfoBuilder::build, XposedLogLoader::syncLogsToDatabaseSync)` → `setupCrashHandler` |
| `onServiceBind(service)` | `setModuleActivated(true)` → `PrefsBridge.setRemotePrefs(service.getRemotePreferences(REMOTE_PREFS_GROUP))` → `syncHookAvailabilityIfUnlocked` → `FrameworkStatusManager.onServiceBound` → `ScopeManager.setService` → `LSPosedScopeHelper.reloadScope` → `refreshHomePageBanner` |
| `onServiceDied(service)` | `setModuleActivated(false)` → `PrefsBridge.setRemotePrefs(null)` → `FrameworkStatusManager.onServiceDied` → `ScopeManager.clearService` → `refreshHomePageBanner` |

`isModuleActivated` 是 `public static volatile` 标志，供各处判断「LSPosed 是否已连接」。

`setModuleActivated` 同时更新 `PermissionSettingsFragment.isModuleActive` 并调用 `XposedActivateHelper.onActivationChanged()`，让引导页/主页的激活状态提示即时刷新。

`syncHookAvailabilityIfUnlocked()`：仅当 `UserManager.isUserUnlocked()` 时调用 `OobeUtils.syncHookAvailability(this)`，避免解锁前读不到数据导致误判。

### 2.2 崩溃兜底

```java
Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
    AppCrashStore.persist(getApplicationContext(), ex);   // 落盘
    AndroidLog.e("Crash", "App crash captured", ex);
    defaultHandler.uncaughtException(thread, ex);          // 继续默认处理
});
```

崩溃信息落盘后，下次启动由 `SplashActivity` 读到并跳 `CrashActivity`。

## 3. 启动任务编排

`app/.../home/task/`

| 类 | 职责 |
| --- | --- |
| `AppInitializer` | 三个阶段入口：`attach`（最早）、`initOnAppCreate`、`initOnActivityCreate(activity, context)`，每次都 `TaskRunner.getInstance().start()` |
| `AppTaskManager` | `attach` / `setupAppTasks` / `setupActivityTasks` 分别登记任务 |
| `Task` | 任务单元（含依赖关系） |
| `TaskRunner` | 单例，按依赖顺序执行任务 |

设计意图：把「Shell 初始化、语言包、权限检查、公告拉取」等非 UI 工作从 `onCreate` 中拆出并按依赖调度，而不是串行阻塞主线程。

## 4. 主页结构

[HomePageActivity.java](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/java/com/sevtinge/hyperceiler/ui/HomePageActivity.java) 实现 `ActivityCallback`、`IResult`、`PreferenceFragment.OnPreferenceStartFragmentCallback`。

### 4.1 onCreate 顺序

```
1. PersistConfig.isAprilFoolsThemeView → setTheme(HomePageAprilFoolsTheme)
2. !OobeUtils.isProvisioned(...) && !isDebugOobeMode(...) → SplashActivity + finish
3. !isVersionListed() → showUnsupportedVersionDialog()（设备/系统不在支持列表）
4. AppInitializer.initOnActivityCreate(this, this)
5. setContentView(R.layout.activity_home)
6. setupNavigation()
7. restoreCurrentPage(savedInstanceState)   // STATE_CURRENT_PAGE = "home_current_page"
8. ProvisionManager.setProvider(ctx -> NoticeProcessor.process(ctx) → [protocolVersion, privacyVersion])
```

### 4.2 导航样式

```java
mSwitchManager = new SwitchManager(findViewById(R.id.container));
NavigationStyle initialStyle = isFloating ? CAPSULE_ICON : BOTTOM_LABEL;
mSwitchManager.addSwitchView(R.menu.bottom_nav_menu, initialStyle);

LiveData<Boolean> isFloatNavEnabled =
    Settings.Global.getBooleanLiveData(this, AppSettingsStore.KEY_FLOAT_NAV, false);
isFloatNavEnabled.observe(this, enabled -> {
    PrefsBridge.putByApp(AppSettingsStore.PREF_FLOAT_NAV, enabled);  // 镜像到 prefs 供 hook/备份使用
    mSwitchManager.setFloatingStyle(enabled);
});
```

两种导航样式：`CAPSULE_ICON`（悬浮胶囊）与 `BOTTOM_LABEL`（底部文字标签），由 `Settings.Global`（`fan.provider.SettingsProvider`）持久化，并镜像写入 `PrefsBridge`。

### 4.3 内容页

`mViewPager`（`vp_fragments`）+ `HomeContentAdapter` + `SwitchMediator(mSwitchManager, mViewPager, true).attach()` 把底部导航与 ViewPager 双向联动。

相关类：

| 类 | 职责 |
| --- | --- |
| `HomePageFragment` | 单个分组的设置页容器（按 `settings_header.xml` 的 header 生成） |
| `HomePageHeaderHelper` | header 元数据读取与图标/标题加载 |
| `Header` / `IconTitleLoader` | header 数据模型与图标异步加载 |
| `adapter/HomeContentAdapter`、`ProxyHeaderViewAdapter`、`HeaderAdapter` | ViewPager 页面与 header 列表适配 |
| `widget/SwitchManager`、`SwitchView`、`SwitchMediator`、`NavigationStyle`、`OnSwitchChangeListener` | 底部/悬浮导航 |
| `order/CustomOrderManager` 等 | 首页卡片排序（自定义顺序、拖拽、恢复默认） |
| `banner/HomePageBannerManager` | 首页横幅（活动/公告），`invalidateCache` + `requestRefresh` |
| `tips/HomePageTipManager` | 使用提示（`assets/tips/tips-<locale>`） |
| `safemode/*` | 崩溃安全模式入口 |

## 5. 搜索索引

[SearchHelper.java](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/java/com/sevtinge/hyperceiler/search/SearchHelper.java) 把「所有设置项」索引进 Room FTS4 表，支持中文按字模糊与其它语言按词前缀。

### 5.1 索引构建

```
initIndex(context, force)                 // 后台线程
  ├─ dao.getCount() == 0 或 force → rebuildIndex
  └─ rebuildIndex:
       ├─ HomePageFragment.getHomeHeadersResourceId() → scanHeaders
       ├─ 事务内 deleteAll() + insertAll()
       └─ 填充 GROUP_PACKAGE_MAP / GROUP_ICON_MAP
```

`scanHeaders` 解析 `settings_header.xml` 的 `<header>`：`android:fragment`、`android:title`、`android:summary`（此处约定填目标包名）、`app:inflatedXml`（该分组的设置页 XML）、`android:icon`。

`scanEntryXml` 递归扫描设置页 XML：

- `REGISTERED`（静态 `Set<Integer>`）去重，避免同一 XML 被多次索引；
- `myLocation` 记录「分组 → 页面 → 条目」的层级；
- `isPreferenceVisible=false` 的条目跳过；
- `<PreferenceScreen>` 子 fragment 递归下探；
- 生成 `ModEntity`：`title`、`key`、`xmlResId`、`order`、`fragment`、`breadcrumbs`、`catTitleResId`、`groupName`。

### 5.2 查询

```java
public static List<ModEntity> search(Context context, String keyword) {
    if (TextUtils.isEmpty(keyword)) return new ArrayList<>();
    String trimmed = keyword.trim();
    ModDao dao = AppDatabase.getInstance(context).modDao();
    if (containsChinese(trimmed)) return dao.testSearch(trimmed);   // 按字模糊
    return dao.search(trimmed.replace("'", "''") + "*");            // 前缀匹配
}
```

`containsChinese` 覆盖 CJK 基本区（`0x4E00-0x9FFF`）、扩展 A（`0x3400-0x4DBF`）、兼容区（`0xF900-0xFAFF`）。

`getLocaleResources(context)` 用 `LanguageHelper.getCurrentLocale(context)` 重建资源配置，保证索引标题与当前界面语言一致。

### 5.3 数据结构

`search/data/`

| 类 | 说明 |
| --- | --- |
| `AppDatabase` | Room 数据库单例 |
| `ModEntity` | 索引行实体 |
| `ModFtsEntity` | FTS4 虚拟表实体 |
| `ModDao` | `search(query)` / `testSearch(query)` / `getCount()` / `deleteAll()` / `insertAll()` |

`search/PinyinUtils`：拼音转换，用于拼音首字母搜索。`search/widget/FlowLayout`：搜索历史标签流式布局。`search/SearchResultAdapter`：结果列表。

`SearchHelper.getGroupPackageMap()` / `getGroupIconMap()` 把「分组 → 包名」「分组 → 图标资源」暴露给 UI 与（间接）作用域跳转。

## 6. 日志系统

`app/.../log/`

### 6.1 结构

| 类 | 职责 |
| --- | --- |
| `LogManager` | 单例；初始化 `LogRepository` 与 `LogStatusManager`；安装日志拦截器 |
| `LogRepository` | 数据库访问封装；全 App 唯一 DB 入口，异步写统一走 `executeIo` 守卫调度 |
| `db/LogDatabase` / `LogDao` / `LogEntry` | Room 实体与 DAO |
| `LogViewerActivity` + `LogListFragment` / `LogPagerAdapter` / `LogAdapter` | 日志查看界面（分页、过滤、搜索、复制、导出） |
| `LogLevelFilter` / `HighLightUtils` / `SimpleTextWatcher` | 过滤、高亮、搜索框 |
| `XposedLogLoader` | 从 LSPosed 日志中同步模块日志到本地库 |
| `DeviceInfoProvider` / `DeviceInfoBuilder` | 导出时附带的设备信息 |

### 6.2 拦截链

```java
AndroidLog.setLogListener((level, tag, message) -> {
    String finalLevel = "Crash".equals(tag) ? "C" : level;
    String processIds = Process.myUid() + ":" + Process.myPid() + ":" + Process.myTid();
    LogEntry entry = new LogEntry(...);   // Module = "App"
    // 入库
});
```

即：App 内所有 `AndroidLog` 输出都会被落库，崩溃日志级别记为 `C`。带 `Throwable` 的重载写入文件日志时会通过 `formatThrowable`（`Log.getStackTraceString`）附上完整堆栈，保证导出的运行日志可用于定位崩溃根因。

### 6.3 初始化

```java
LogManager.init(context, DeviceInfoProvider provider, Runnable xposedLogSyncer)
```

- 首次创建单例（`LogRepository.init` + `initLogInterceptor`）；
- 注册设备信息提供者；
- 首次调用时为 `LogStatusManager.init(context, dataDir, xposedLogSyncer, null)`，其中 `xposedLogSyncer` 由 `Application` 传入 `XposedLogLoader::syncLogsToDatabaseSync`。

### 6.4 落库可用性（Android 17 / targetSdk 37）

日志「落库不可用」会连带影响模块功能，因此这条链路有三处硬约束：

- `LogRepository` 构造时先建好 `getDatabasePath(...)` 的父目录再 `Room.databaseBuilder`：Room 是懒打开，Android 17 起不会替调用方补建 `databases/` 上级目录，缺失时首次打开直接失败；
- 7 个异步写（`insertLog`、`deleteLogsByModule`、`clearAllLogs`、`autoTrim`、`syncXposedLogs`、`clearLogs`、`trimDatabase`）统一经 `executeIo` 提交，任务内异常用 `android.util.Log` 记录而非 `AndroidLog`——后者会被 §6.2 的拦截器再写回本仓库，形成无限入队；
- `LogStatusManager.init` 的健康检查线程捕获全部 `Throwable`（失败置 `IS_LOGGER_ALIVE = false`），`healthCheckLatch` 必定释放，监听器回调单独兜底，避免日志能力异常时把整个初始化流程卡死。

### 6.5 日志服务健康检查（`LoggerHealthChecker`）

`isLoggerAlive()` 加权计分，`confidence >= 75` 视为可用：

| 检查项 | 权重 | 判定 |
| --- | --- | --- |
| `checkLspdDumpFiles` | 80 | 读 `files/log/lspd/log` 下 `modules_*` / `verbose_*`；有文件且非全零字节即满分 |
| `checkLogcat` | 20 | 写一条 `HyperCeilerLogManager:D` 探针再 `logcat -d -s` 读回；读到得满分，读不到得半分 |

- 无 root 时只跑 logcat，且 logcat 通过即直接抬到阈值（此时 LSPosed 目录不可达，不能据此降级）；
- 只校验落盘文件是否存在且非空，**不解析 LSPosed 日志内容格式**——原始日志行格式随 LSPosed 版本变化，解析会被误判成服务异常；
- 达到阈值但 `< 80` 记为 `DEGRADED`，`< 75` 但 `> 0` 记为 `UNHEALTHY`，`0` 记为 `DEAD`；`formatStatus()` 给弹窗，`formatDetailedStatus()` 附带 `logcat=` / `lspd_dump=` 诊断串。


导出：`formatLogEntryForCopy(entry)`、`generateZipFileName()`。

## 7. 设置页体系

来自 `:library:core` 的 `dashboard/`：

| 类 | 说明 |
| --- | --- |
| `SettingsActivity` | 通用设置宿主 |
| `SettingsBaseActivity` | 统一主题/标题/返回 |
| `SettingsPreferenceFragment` | Preference 基类，可用 `PrefsConfigurator.setup(this)` 接管数据源 |
| `SubSettings` | 子设置页（`app` Manifest 中 `.dashboard.SubSettings`） |
| `DashboardFragment` | Dashboard 卡片容器 |
| `DashboardFuncHintHelper` | 功能提示（首次进入某页时的说明） |
| `DashboardPreferencePageLockHelper` | 页面锁定（如未激活模块时禁用） |

设置页资源位置：

- 通用/各功能域 XML：`library/core/src/main/res/xml/*.xml`（`framework.xml`、`system_ui.xml`、`various*.xml`、`browser.xml`…）
- 首页 header 定义：`app/src/main/res/xml/settings_header.xml`
- App 级设置：`app/src/main/res/xml/prefs_settings.xml`、`prefs_development*.xml`、`prefs_about*.xml`、`prefs_help.xml`

`settings_header.xml` 是**搜索索引与分组信息的源头**，新增功能分组必须在这里登记。

## 8. 作用域选择

`app/.../sub/ScopePickerActivity` + `library/core/.../utils/LSPosedScopeHelper` + `ScopeManager`：

- 读取 `service.scope` 展示当前作用域；
- 候选列表来自 `AppDataManager.getAppInfo(MODE_SCOPE)`，即**全部已安装应用**（而非仅 `scope.list`）；`system` 伪应用固定置顶；
- 当当前作用域为空（首次进入）时，`loadRecommendedScopePackages()` 直接读取 APK 内 `META-INF/xposed/scope.list` 作为默认勾选集；
- 用户勾选后通过 `ScopeManager.ScopeCallback` / `ScopeBatchCallback` 回写；
- `normalizeScopePackages` 统一 trim/lowercase/去重。

## 9. 关于页与彩蛋

`app/.../about/`

| 类 | 说明 |
| --- | --- |
| `AboutPageFragment` | 关于页主体 |
| `AboutSettingsFragment` / `AboutContributorsFragment` | 设置项与贡献者列表 |
| `widget/VersionCard`、`VersionNameCard`、`DeviceInfoCard`、`DeviceNameCard` | 版本/设备信息卡片 |
| `controller/AboutAnimationController`、`BgEffectController`、`BgEffectPainter`、`BgEffectDataManager` | 背景动效（`res/raw/bg_frag.glsl`） |

`app/.../utils/EggHelper.kt`、`library/core/.../holiday/`、`library/core/.../weather/` 提供节日/天气彩蛋，`PersistConfig.isAprilFoolsThemeView` 控制愚人节主题。

## 10. 默认值恢复

`PrefsConfigurator`（`library/common`）：

| 方法 | 行为 |
| --- | --- |
| `setup(fragment)` | 挂载 `PrefsDataStore`（接管所有读写） |
| `reset(fragment, xmlResId)` | 清空 `hyperceiler_prefs` → `PreferenceManager.setDefaultValues(...)` → 重建 PreferenceScreen |
| `performReset(fragment, currentXmlId)` | 不清空，仅按 XML 恢复默认值并刷新 UI |

注意 `reset` 会清空**全部**配置（不只是当前页），`performReset` 才是当前页语义。
