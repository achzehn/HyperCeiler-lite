# 04 配置与数据流

配置系统是 App 与注入进程之间唯一的耦合点，也是排查「开关不生效」类问题时的第一现场。

## 1. 存储模型

| 项 | 值 |
| --- | --- |
| SharedPreferences 文件名 | `hyperceiler_prefs`（`PrefsBridge.PREFS_NAME`） |
| LSPosed remote group | `hyperceiler_prefs_remote`（`PrefsBridge.REMOTE_PREFS_GROUP`） |
| 存储位置 | **设备保护存储**（`createDeviceProtectedStorageContext()`），保证解锁前也能读到 |
| key 前缀 | `prefs_key_`（`wrap()` 自动补全，重复调用幂等） |
| 写入方 | 仅 App 进程（`putByApp` / `removeByApp` / `clearAllByApp`） |
| 读取方 | App 进程读物理句柄；注入进程读 remote 句柄 |

## 2. PrefsBridge 类结构

`library/common/src/main/java/com/sevtinge/hyperceiler/common/utils/PrefsBridge.java`

### 2.1 三种初始化

```java
initForApp(Context baseContext)       // App：isHookProcess=false，打开 device-protected storage
initForHook(SharedPreferences remote) // 注入进程：isHookProcess=true，仅只读 remote，清空 hook cache
setRemotePrefs(SharedPreferences r)   // App 绑定 LSPosed service 后注入，并 syncPhysicalToRemote()
```

`getImpl()` 的选择顺序：

```
isHookProcess && mRemotePrefs != null → mRemotePrefs
否则 → mPhysicalPrefs（App 物理句柄）优先，其次 mRemotePrefs
```

### 2.2 读取 API

| 方法 | 说明 |
| --- | --- |
| `getBoolean(key[, def])` | 默认 `false` |
| `getString(key, def)` | 取不到返回 def |
| `getStringAsInt(key, def)` | 字符串配置存数字（RadioList/ListPreference 常见），解析失败回落 def |
| `getInt` / `getLong` / `getFloat` / `getStringSet` / `getAll` | 常规类型 |

读取前会先查 hook 进程内的 `sHookCache`（`putHookCache` 写入的临时可见值），用于兼容旧 `mPrefsMap` 的「刷新本地可见值」语义。

### 2.3 写入 API

```java
putByApp(key, value)   // 1) 物理 prefs 提交 2) 有 remote 则同步提交 3) notifyPrefChanged
removeByApp(key)
clearAllByApp()
put(key, value)        // 兼容别名 → putByApp
remove(key) / clearAll()  // 兼容别名
```

`warnAndSkipIfHookWrite(operation, key)` 是写入护栏：注入进程调用写入会被直接忽略，并对同一 `operation:key` 只告警一次（`sWarnedHookWrites` 去重），避免日志刷屏。原因：LSPosed remote prefs 在 hook 侧是只读实现，且跨进程写入会绕过 App 的同步逻辑。

`putByApp` 的一个关键注释：

> The provider reads the committed physical file. A missing/read-only remote endpoint must not hide a successful local write from system_server's provider observers.

即：**remote 写失败不影响本地写入与变更通知**，`notifyPrefChanged` 一定执行，保证 `system_server` 里的 Provider 观察者能感知。

### 2.4 框架状态批量写入

```java
putFrameworkStatusByApp(allowHook, reason, name, version, versionCode, apiVersion, detail)
```

一次性写入 7 个键（`framework_api_allow_hook`、`framework_check_reason`、`framework_check_name`、`framework_check_version`、`framework_check_version_code`、`framework_check_api_version`、`framework_check_detail`）。

- 用 `sFrameworkStatusWriteLock` 串行化，避免并发写半套状态；
- 每个 store（physical / remote）各用**一次** Editor 提交，尽量缩小跨进程不一致窗口；
- `framework_api_allow_hook` 只在值变化时通知（对齐旧实现的监听行为）；
- 语义上「门禁结论」和「所检查的框架身份」是一个 revision，必须成对更新，否则 `FrameworkHookGate` 会拿新结论比旧指纹。

### 2.5 物理 → 远程全量同步

`syncPhysicalToRemote()` 在 `setRemotePrefs` 时调用：

```
remote 有而 physical 没有   → remote.remove(key)
值不一致（Objects.equals）  → performPut(remoteEdit, key, physicalValue)
有变化才 commit，并逐个 notifyPrefChanged
捕获 UnsupportedOperationException（远程只读）→ 仅告警
```

作用：App 在未激活 LSPosed（remote 为 null）期间的开关改动，会在下次服务绑定后补齐。

### 2.6 变更通知

`notifyPrefChanged(key, prefType)` 通过 `ContentResolver.notifyChange` 广播两类 URI：

- `PrefsChangeObserver.PrefToUri.prefToUri(type, key)`：精确类型+key；
- `PrefsChangeObserver.PrefToUri.anyPrefToUri(type, key)`：`PrefType.Any` 通配。

`prefType` 为 null 时四种类型（String / StringSet / Integer / Boolean）全部通知。

## 3. 变更监听链

### 3.1 App 侧 Preference 绑定

`PrefsConfigurator.setup(fragment)`：

```java
fragment.getPreferenceManager().setPreferenceDataStore(new PrefsDataStore());
```

`PrefsDataStore extends PreferenceDataStore`，把每次 `putXxx` 直接转发到 `PrefsBridge.putByApp`，读取转发到 `PrefsBridge.getXxx`。因此设置页不依赖 `PreferenceManager.setSharedPreferencesName`，所有读写都经过桥接层。

### 3.2 注入进程监听

`library/common/.../utils/prefs/PrefsChangeObserver.java`：

- 同时实现 `ContentObserver` 与 `SharedPreferences.OnSharedPreferenceChangeListener`；
- 构造时若 remote 可用，注册 remote prefs 监听（`registerRemoteListenerIfAvailable()`）；
- 同时按 `PrefType` 注册 ContentObserver URI（保留旧平台生命周期语义）；
- 支持 `autoApplyChange` + `PrefType` + `def`，用于「自动把变化应用到某个字段」。

### 3.3 日志级别观察者

`LogStatusManager`：

| 方法 | 时机 |
| --- | --- |
| `syncLogLevelFromPrefs()` | `XposedInitEntry.initPrefs()` 成功后同步一次 |
| `attachHookLogLevelObserver(context)` | `installCurrentTargetHooks` 中，取到 context 后挂载 |
| `detachHookLogLevelObserver()` | 热重载前清理，避免旧代对象残留 |

## 4. SharedPrefsProvider

`library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/provider/SharedPrefsProvider.java`

声明于 `library/libhook/src/main/AndroidManifest.xml`：

```xml
<provider
    android:name=".provider.SharedPrefsProvider"
    android:authorities="com.sevtinge.hyperceiler.provider.sharedprefs"
    android:defaultToDeviceProtectedStorage="true"
    android:directBootAware="true"
    android:exported="true"
    android:grantUriPermissions="true" />
```

用途：让**系统框架/其他进程**（含未绑定 LSPosed service 的场景）能按 URI 读取配置，以及读取 Dock 几何快照。

### 4.1 URI 路由

| URI 模式 | code | 说明 |
| --- | --- | --- |
| `string/*`、`string/*/` | 0 | 读字符串 |
| `string/*/*` | 1 | 读带类型的值 |
| `integer/*`、`integer/*/*` | 2 | 读整数 |
| `boolean/*`、`boolean/*/*` | 3 | 读布尔 |
| `stringset/*` | 4 | 读字符串集合 |
| `pref/*/*` | 7 | 通用 `pref/<type>/<key>` |
| `dock_geometry` | 8 | Dock 几何快照（单行多列） |
| `home_layout` | 9 | 桌面布局快照 |
| `test/*` | 5 | 自检 |
| `shortcut_icon/*` | 6 | 快捷方式图标 |

`query` 中 `pref/<type>/<key>` 分支按 `string|integer|boolean|stringset` 返回 `data` 列（boolean 用 1/0）。

### 4.2 Dock 几何快照

`DOCK_GEOMETRY_KEYS`（8 项，`{key, kind}`，`kind ∈ {b,i,s}`）：

```
home_dock_bg_custom_enable          b
home_dock_add_blur                  s
home_dock_bg_color                  i
home_dock_bg_height                 i
home_dock_bg_margin_horizontal      i
home_dock_bg_margin_bottom          i
home_dock_bg_radius                 i
home_other_home_mode                s
```

对应列名 `DOCK_GEOMETRY_COLUMNS`：

```
custom_enable, add_blur, bg_color, bg_height,
margin_horizontal, margin_bottom, bg_radius, home_mode
```

这些 key 是**未加前缀**的原始名；实际读取时由 `PrefsBridge` 加 `prefs_key_`。

### 4.3 call 方法

| method | 行为 |
| --- | --- |
| `dock_glass_*` | 转发给 `DockGlassHost`（Dock 毛玻璃诊断/控制通道） |
| `hc_debug_put` | 调试写入，见下 |
| 其他 | `super.call(...)` |

`hc_debug_put` 的约束（用于 adb 验证真实写入链路）：

1. 系统属性 `debug.hyperceiler.prefs_write` 必须为 `1`；
2. 调用方 UID 必须是 `2000`（shell）或 `0`（root）；
3. 参数格式 `<type>:<key>:<value>`，type ∈ `boolean|integer|string`。

满足后调用与设置页完全相同的 `PrefsBridge.putBoolean/putInt/putString`，因此物理存储、remote 快照、变更通知的行为与用户点击一致：

```bash
adb shell setprop debug.hyperceiler.prefs_write 1
adb shell content call --uri content://com.sevtinge.hyperceiler.provider.sharedprefs \
    --method hc_debug_put --arg boolean:home_layout_workspace_padding_top_enable:true
```

### 4.4 其他 Provider 组件

| 类 | 职责 |
| --- | --- |
| `DockGlassHost` | `call` 分发 Dock 毛玻璃请求 |
| `DockGlassDiagnostics` / `DockDiagnosticJournal` | 诊断数据采集与记录 |
| `HomeLayoutPrefsSnapshot` | 桌面布局相关配置快照（供 native 侧读取） |

## 5. App 侧的 settings Provider

`app/src/main/java/fan/provider/`：仿 AOSP `Settings` 的实现，供设置页/搜索的 `SettingsLiveData` 使用。

| 类 | 说明 |
| --- | --- |
| `SettingsProvider` | `SQLiteOpenHelper("settings.db", 1)`，三张表 `global` / `secure` / `system`，每表 `(_id INTEGER PRIMARY KEY, name TEXT UNIQUE, value TEXT)`；`UriMatcher` 匹配 `global|secure|system` 及 `global/*`；`insert` 用 `CONFLICT_REPLACE` 并 `notifyChange(u)` 与 `notifyChange(u/name)` |
| `Settings` | 常量与 `PERMISSION_READ` / `PERMISSION_WRITE`；`getString/getInt` 等静态读写 |
| `SettingsLiveData` | 把 `Settings` 读取包装成 LiveData，随 ContentObserver 刷新 |

Manifest 声明：

```xml
<provider
    android:name="fan.provider.SettingsProvider"
    android:authorities="hyperceiler.provider.settings"
    android:readPermission="hyperceiler.permission.READ_SETTINGS"
    android:writePermission="hyperceiler.permission.WRITE_SETTINGS" />
```

其中 `READ_SETTINGS` 为 normal 级、`WRITE_SETTINGS` 为 signature 级 —— 允许普通应用读、只允许同签名写。

## 6. 作用域与框架状态（App 侧）

### 6.1 ScopeManager

`library/core/.../utils/ScopeManager.kt`（Kotlin `object`）：

| 成员 | 说明 |
| --- | --- |
| `SYSTEM_SCOPE_PACKAGE = "system"` | 系统框架作用域常量 |
| `setService(service)` / `clearService()` | `Application.onServiceBind/onServiceDied` 调用，并广播状态 |
| `getService()` / `requireService()` | 读取；后者为 null 时 `checkNotNull` 抛异常 |
| `addServiceStateListener(listener, notifyImmediately)` / `removeServiceStateListener` | `CopyOnWriteArraySet` 保存监听者 |
| `getScopeSync()` / `suspend getScope()` | 读 `service.scope`，IO 线程执行，异常返回 null |
| `normalizeScopePackageName(pkg)` | trim + lowercase，空值返回 null |
| `normalizeScopePackages(collection)` | 去空、去重、保持顺序（`LinkedHashSet`） |
| `ScopeCallback` / `ScopeBatchCallback` | 单条/批量操作回调 |

所有状态回调通过主线程 `Handler` 派发（`dispatchServiceState`），避免 UI 线程问题。

### 6.2 FrameworkStatusManager

`app/.../utils/FrameworkStatusManager.java`：App 侧维护「框架指纹是否变化」的状态。

- `Application.onCreate` → `FrameworkStatusManager.init(...)`；
- `onServiceBind` → `onServiceBound()`（触发检查并 `putFrameworkStatusByApp`）；
- `onServiceDied` → `onServiceDied()`；
- 结论被注入进程的 `FrameworkHookGate` 读取，决定是否因框架变化而拒绝加载 hook。

## 7. 常见问题定位

| 现象 | 排查点 |
| --- | --- |
| 开关改了但功能没变 | 1) `allow_hook` 总开关是否为 true；2) LSPosed 中该应用是否已勾选作用域；3) 是否执行了「重启作用域」或热重载；4) `FrameworkHookGate` 是否因指纹变化拒绝（看 App 框架状态提示） |
| 某应用完全不生效 | 1) `CrashScope` 是否处于安全模式；2) `ModuleMatcher` 的 SDK/OS/设备类型区间；3) `securitycenter`/`home` 的安全检查是否拦截 |
| 配置写入丢失 | 检查是否在 hook 进程调用写入（日志会有 `Ignored prefs put ...` 警告） |
| 热重载按钮不可用 | `service.getApiVersion() < 102`（LSPosed 版本过低） |
| 热重载被拒绝 | 看 Xposed 日志：`Hot reload rejected: ...`，常见为当前代存在 hook 初始化失败、native 后端已加载、layout provider 查询未完成 |
