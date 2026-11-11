# HyperCeiler Code Wiki

面向 Xiaomi HyperOS 的 LSPosed 功能增强模块（Lite 分支）的项目知识库。本 wiki 面向「需要快速理解代码、定位改动点、完成构建与验证」的开发者，按主题拆分，建议按顺序阅读。

## 导航

| 文档 | 内容 |
| --- | --- |
| [01-architecture.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/01-architecture.md) | 整体架构、进程模型、两次运行形态、关键技术选型 |
| [02-modules.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/02-modules.md) | Gradle 模块职责、模块依赖图、目录结构、第三方依赖清单 |
| [03-hook-entry-and-flow.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/03-hook-entry-and-flow.md) | Xposed 入口、模块匹配、`BaseLoad` / `BaseHook`、热重载流程 |
| [04-prefs-and-data.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/04-prefs-and-data.md) | 配置桥 `PrefsBridge`、SharedPreferences Provider、框架状态、作用域管理 |
| [05-app-ui.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/05-app-ui.md) | App 侧界面体系、主页、搜索索引、日志、崩溃安全模式、引导流程 |
| [06-native.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/06-native.md) | Native 层（JNI、nativehook、Home/SystemUI targets、Hyos companion） |
| [07-build-and-run.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/07-build-and-run.md) | 构建环境、Gradle 任务、签名与凭据、安装运行、CI |
| [08-testing.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/08-testing.md) | Host 侧测试体系、环境变量、测试目录索引 |

## 一句话概括

HyperCeiler 是一个 **Android 应用（Compose/Miuix UI）+ Xposed 注入代码 + Native so** 三合一的 APK：

- 应用进程：提供功能开关配置界面，配置写入本地 `hyperceiler_prefs` 并经 LSPosed remote prefs 同步到被注入进程；
- 注入进程（`system`、`com.android.systemui`、`com.android.settings` 等 19 个作用域包）：`XposedInitEntry` 读取配置、按 `@HookBase` 生成的 `DataBase` 表匹配目标，逐个执行 hook 规则；
- Native 层：`libHyperCeilerNative.so` 承担 LSPosed `native_init` 的 JNI 注册，以及 OS4（Android 17）Home/SystemUI 的符号级 native hook。

## 快速事实卡

| 项 | 值 |
| --- | --- |
| 应用包名 / 模块 ID | `com.sevtinge.hyperceiler` |
| 版本 | `2.10.166`（`versionCode = 5 + gitCommitCount`） |
| 支持系统 | Android 15–16 的 HyperOS（README）；`doc/experimental-os4-popsicle.md` 记录 Android 17 / OS4 实验能力 |
| minSdk / targetSdk / compileSdk | 35 / 37 / 37（buildTools 37.0.0） |
| ABI | 仅 `arm64-v8a` |
| LSPosed API | `minApiVersion=102`、`targetApiVersion=102`、`autoHotReload=true`、`staticScope=false`、`exceptionMode=protective` |
| 注入入口 | `com.sevtinge.hyperceiler.libhook.base.XposedInitEntry` |
| Native 入口 | `libHyperCeilerNative.so` |
| 作用域数量 | 23（`library/libhook/src/main/resources/META-INF/xposed/scope.list`） |
| 构建工具链 | AGP 9.4.0、Kotlin 2.4.10、Java 25、CMake 3.22.1 + C++20 |

## 阅读路线建议

- 只想改某个功能规则：先读 [03](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/03-hook-entry-and-flow.md) 的「新增一个 Hook 规则」小节，再在 `library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/<目标>/` 下按现有类仿写。
- 想改设置页/开关：读 [05](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/05-app-ui.md) 与 [04](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/04-prefs-and-data.md)。
- 想打包发布：读 [07](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/07-build-and-run.md)。
- 想动 native：读 [06](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/06-native.md)，并先看 `tests/` 下对应的 host 测试。

## 文档维护约定

- 本目录为项目级知识库；代码改动影响到入口链、模块依赖、构建方式时，需同步更新对应文件。
- 链接统一使用仓库绝对路径，便于在编辑器中直接跳转。
- 事实性内容以当前仓库代码为准；`doc/`、`docs/` 下的设计/实验记录仅作背景参考。
