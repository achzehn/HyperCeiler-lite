---
alwaysApply: true
---

# 项目踩坑记录

## 日志排错
- `AndroidLog` 的文件日志（app_runtime 导出包）曾只写入异常 message 不含堆栈，导致导出日志无法定位崩溃根因；已修复（notifyListener 走 `formatThrowable` 补全堆栈）。新增带 Throwable 的日志重载时必须走 `formatThrowable`。
- 导出日志包中 lspd 文件名含冒号（如 `verbose_2026-11-07T08:54:34.log`），Windows 解压/读取时冒号会被替换为下划线，注意路径匹配。

## Activity / Fragment
- `Fragment.instantiate` 反射加载类时，`NoClassDefFoundError`、`ExceptionInInitializerError` 等 Error 不会被 `catch (Exception)` 捕获，会在 Activity onCreate 阶段直接触发 "Unable to start activity" 闪退；涉及动态加载 Fragment/类的捕获一律用 `catch (Throwable)`。
- 异步回调后弹对话框必须校验 `isFinishing/isDestroyed`，否则出现 `BadTokenException`（token not valid）。

## 设置项 key 一致性
- 删除/重命名 preference XML 中的 `android:key` 前，必须全局搜索 Java/Kotlin 侧 `findPreference(...)` 与 Hook 侧 key 使用；`SafeModeFragment` 曾因 XML 删了 `prefs_key_home_safe_mode_enable` 而 `findPreference` 返回 null，在 `setChecked` 处 NPE，表现为 SubSettings「Unable to start activity」闪退。

## libxposed API 102（LSPosed）
- EzHookTool 的 `HookFactory` 默认 `automaticIdEnabled=true`，并自动把 hook 登记进热重载批次；**不要**再给 `Hooks.*` / DSL 安装的 hook 手工 `trackHookHandle`，否则 `prepareHotReload` 会提前 unhook，EzHookTool 恢复阶段拿到失效句柄。
- `Chain.getArgs()` 是只读 `List`，改参数必须 `toArray()` 复制后 `chain.proceed(newArgs)`；直接 `set()` 会抛 `UnsupportedOperationException`。
- 远端配置与 system_server hook 分别依赖框架声明的 `PROP_CAP_REMOTE` / `PROP_CAP_SYSTEM`（`getFrameworkProperties()`），缺失时只能降级跳过，不能假定框架一定提供。
- `ExceptionMode` 默认 `DEFAULT`＝跟随 `module.prop` 的 `exceptionMode`，生产环境用 `protective`；入口类与 `BaseLoad` 子类必须配 R8 keep + `-adaptresourcefilecontents META-INF/xposed/java_init.list`，否则混淆后入口列表失效。

## Git 远端与推送
- 远端必须使用 SSH（`git@github.com:achzehn/HyperCeiler-lite.git`）：单次推送改动文件数上千（如 `refresh` 分支 1668 个文件）时，HTTPS 走 GitHub 的 HTTP 接口会超限失败（`RPC failed` / `413` / remote end hung up），SSH 无该限制，可一次推完。
- 本仓库 `remote.origin.fetch` 曾被限制为 `+refs/heads/main:refs/remotes/origin/main`，导致其他分支无远端跟踪引用、`git fetch` 只更新 FETCH_HEAD；已改为标准 `+refs/heads/*:refs/remotes/origin/*`。新建远端分支后需 `git fetch --prune` 再 `git branch -u origin/<分支>`。

## ADB 无线调试（HyperOS）
- HyperOS 设备"无线调试"的连接端口（`adb-tls-connect`）每次开关/重进页面都会轮换（如 36211→38041），旧端口直接连接失败；授权弹窗随连接断开被 adbd 取消，表现为"弹窗一闪而过"。排查端口优先用 `adb mdns services` 而不是靠人肉读屏。
- HyperOS 默认限制普通 logcat（main 缓冲为空），`adb shell logcat -d` 无输出，不要依赖设备端日志排查连接/授权问题。
- 稳定方案：插 USB 授权一次后执行 `adb tcpip 5555`，之后直接 `adb connect <ip>:5555`（重启手机前有效，重启后需重插 USB 再开）；无线连接会复用 USB 已授权的 RSA key，不再弹授权框。
- 终端命令结束后 adb server 会被回收，下一条命令重新 `connect` 时设备已掉线；`adb connect` 与 `adb install` 必须在**同一条命令**里执行，否则报 `device '<ip>:<port>' not found`。
