# 06 Native 层

Native 代码位于 `app/src/main/cpp`，随 APK 打包为 `libHyperCeilerNative.so`（可选 `libHyperCeilerAmbientOS4.so` / `libHyperCeilerHyos.so`），只提供 `arm64-v8a`。

## 1. 目录布局

```
app/src/main/cpp/
├── CMakeLists.txt
├── nativehook/                 与目标无关的 hook 基础设施
│   ├── nhk_base.h
│   ├── elf_image.h             ELF 解析
│   ├── native_image.h          已加载镜像信息
│   ├── memory_io.h             内存读写
│   ├── page_guard.h            页保护（mprotect 管理）
│   ├── got_hook_backend.h      GOT/PLT 级 hook
│   ├── hook_bank.h             hook 记录与回滚
│   ├── resolver.h              符号/特征解析
│   ├── arm64_decode.h          AArch64 指令解码
│   └── xz/                     LZMA/XZ 解压（压缩特征表用）
└── targets/
    ├── home/                   系统桌面（Flutter/Dart + Rust）
    │   ├── hyperceiler_home.cpp        主入口：native_init / JNI
    │   ├── home_startup.*             启动阶段网关
    │   ├── home_layout_*              布局（网格/图标尺寸/间距）
    │   ├── home_report_hooks.*        上报屏蔽
    │   ├── home_workspace_*           工作区几何与快照
    │   ├── home_hotseat_capacity.h    热座位容量
    │   ├── home_folder_geometry.h     文件夹几何
    │   ├── home_indicator_pair.h      页面指示器
    │   ├── dock_native_*              Dock 原生动效
    │   ├── recents_*                  最近任务
    │   ├── probe_guard.h              探测护栏
    │   └── tweaks/                    符号+签名驱动的代码补丁（Apache-2.0 第三方）
    ├── systemui/
    │   ├── ambient_color_os4.cpp      OS4 环境光/取色后端
    │   ├── ambient_bindings.generated.h
    │   └── ambient_sha256.h
    └── hyos/                          Zygisk Next companion
        ├── hyos_entry.cpp
        ├── hyos_control.cpp
        ├── hyos_report_runtime.cpp
        ├── hyos_snapshot.h
        ├── hyos_runtime_guard.h
        ├── hyos_startup_reason.h
        └── hyos_app_entry_arm64.S
```

## 2. 构建配置（CMakeLists.txt）

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(HyperCeilerNative LANGUAGES CXX)
add_library(HyperCeilerNative SHARED targets/home/hyperceiler_home.cpp)
```

### 2.1 库名是契约

注释明确写出三重绑定关系：

- 库名必须等于 `META-INF/xposed/native_init.list` 里的 `libHyperCeilerNative.so`；
- 必须等于 `NativeHomeHooksOS4` 中 `System.loadLibrary("HyperCeilerNative")` 的名字；
- LSPosed 的 native-hook 契约**不会**自动加载库，只有模块自己在目标进程里 `dlopen` 时才回调 `native_init`。

### 2.2 源码集合

| 条件 | 追加源码 |
| --- | --- |
| 总是 | `targets/home/hyperceiler_home.cpp`、`home_layout_targets.cpp`、`home_layout_config.cpp`、`home_layout_hooks.cpp`、`home_layout_dart_arm64.S` |
| 总是 | `targets/home/tweaks/*.cpp` + `tweaks/xz/*.c` |
| `HYPERCEILER_DOCK_NATIVE_MOTION=ON`（默认） | `dock_native_hooks.cpp`、`dock_native_motion.cpp`、`dock_native_motion_arm64.S`，并定义宏 `HYPERCEILER_DOCK_NATIVE_MOTION=1` |
| `HYPERCEILER_HYOS_BACKEND=ON` | 输出名改为 `HyperCeilerHyos`；追加 `hyos_entry/hyos_control/hyos_app_entry_arm64.S/hyos_report_runtime`、`home_startup(.cpp/.S)`、`recents_runtime/recents_gateway_arm64.S`、`home_report_hooks/home_report_dart_arm64.S`；链接 `zygisk_next_api.h` 目录 |
| `NOT HYPERCEILER_HYOS_BACKEND` | 额外产出 `HyperCeilerAmbientOS4`（`targets/systemui/ambient_color_os4.cpp`，链接 `dl log`） |

`enable_language(ASM)` 与 `enable_language(C)` 分别在需要汇编/第三方 C 源时开启。

### 2.3 Dart trampoline 需要手写汇编

> The Dart-side trampolines are hand-written: they follow the Dart AOT register rules (x15 stack, x16/x17 scratch) rather than AAPCS.

因此 `home_layout_dart_arm64.S`、`home_report_dart_arm64.S`、`dock_native_motion_arm64.S`、`recents_gateway_arm64.S` 是独立汇编源，不能与 C 内联汇编互换。

### 2.4 第三方源码的告警豁免

`targets/home/tweaks/` 是 Apache-2.0 第三方代码，编译选项单独设置：

```cmake
COMPILE_OPTIONS "-Wno-everything;-Wnon-pod-varargs;-Wformat;-Werror=format-security"
```

注释记录了一次真实事故：`-Wno-everything` 同时关掉了默认开启且未被任何 group 恢复的 `-Wnon-pod-varargs`，导致 `std::string` 被传给 `%s`（`entry.cpp:721`）的未定义行为漏检，最终在设备上 `SIGTRAP/BRKPT`，桌面 3 秒内连续 4 次崩溃并进入自身安全模式。因此**按名恢复**该类告警——豁免只针对第三方风格，不针对正确性。

### 2.5 编译与链接

| 项 | 值 |
| --- | --- |
| `cxx_std_20` | 是 |
| 编译选项 | `-Wall -Wextra -Werror -fvisibility=hidden` |
| 链接库 | `android binder_ndk log` |
| Ambient 链接库 | `dl log` |
| 默认可见性 | hidden；导出符号需 `[[gnu::visibility("default")]] [[gnu::used]]` |

`app/build.gradle.kts` 另加 `cppFlags += ["-std=c++20", "-fvisibility=hidden"]`、`abiFilters += "arm64-v8a"`。

## 3. JNI 与 native_init

`targets/home/hyperceiler_home.cpp`

### 3.1 导出符号

| 符号 | 用途 |
| --- | --- |
| `native_init(const NativeApiEntries*)` | LSPosed native-hook 契约入口，返回 `on_library_loaded` 回调 |
| `JNI_OnLoad` | 返回 `JNI_VERSION_1_6` |
| `Java_..._NativeHomeHooksOS4_nativeConfigure` | 下发 `highDeviceLevel` / `disablePrestart` / `softGlass` 三个开关 |
| `Java_..._NativeHomeHooksOS4_nativeStatus` | 返回当前 native 状态码 |
| `Java_..._NativeHomeHooksOS4_nativeSetDockEnabled` | 单独下发 Dock 开关 |
| `hyperceiler_home_runtime_options(...)` | C++ 侧统一设置入口（5 个开关） |
| `hyperceiler_home_after_startup()` | 仅 Hyos：first-layout 结束后启动 Dock 动效 |

JNI 函数名硬编码了 Kotlin 对象的全限定名，`NativeHomeHooksOS4.kt` 的注释明确说明：**没有 `RegisterNatives` 表**，重命名/移动该类必须同步改 `hyperceiler_home.cpp` 中的符号名。

### 3.2 native_init 逻辑

```cpp
NativeOnModuleLoaded native_init(const NativeApiEntries *entries) {
    if (entries == nullptr || entries->hook_func == nullptr) return nullptr;
    const bool spawner = is_hyos_spawner_process() && !is_launcher_process();
    g_hook_function = entries->hook_func;
    g_unhook_function = entries->unhook_func;
    if (spawner || is_launcher_process()) install_property_hook();
#ifndef HYPERCEILER_HYOS_BACKEND
    if (is_launcher_process()) resolve_home_layout_targets();
    if (is_launcher_process()) { home_layout_prepare_for_launcher_child();
                                 start_home_layout_hooks("native-init", ...); }
    if (is_launcher_process()) { hometweaks::HomeTweaksPrepareForLauncherChild();
                                 hometweaks::StartHomeTweaks(); }
    if (spawner) install_process_name_hook();
#ifdef HYPERCEILER_DOCK_NATIVE_MOTION
    if (is_launcher_process()) start_dock_native_motion(...);
#endif
#endif
    return on_library_loaded;
}
```

设计要点：

- `property_hook` 必须在 `libapp_launcher/libapp` 静态初始化**之前**装好（它们会缓存系统属性）；`on_library_loaded` 回调作为链接顺序异常时的重试路径。
- `spawner` 判定：Hyos 派生的 Rust 子进程 `/proc/self/exe` 仍是 `hyos_spawner`，其进程身份已经是 Home，因此不安装父级 `setprogname` 观察者。
- 正常路径下 spawner 内不含 `libapp.so`，所以 Dock 动效只在 launcher 进程启动，避免在 fork 出 launcher 前耗尽重试预算。
- 返回 `on_library_loaded` 在无关作用域进程里无害，但 HYOS 在 `native_init` 返回后才特化进程时是必需的。

### 3.3 Kotlin 侧调用者

`library/libhook/.../rules/home/other/NativeHomeHooksOS4.kt`

- `@Keep object`，`external fun` 声明三个 native 方法；
- 常量：`LIBRARY = "HyperCeilerNative"`、`LIBRARY_FILE = "libHyperCeilerNative.so"`、`DOCK_ENABLED_KEY = "home_dock_bg_custom_enable"`、`DOCK_ENABLED_STORAGE_KEY = "prefs_key_home_dock_bg_custom_enable"`；
- 两个入口 `init()`（来自 `HomePhone` 规则）与 `ensureLoadedFromModuleEntry()`（模块入口，先于规则门禁）都汇入 `ensureLoaded()`；
- `AtomicBoolean` 防重入：`loadStarted`、`diagnosticsStarted`、`dockWatchStarted`；
- `preferencesRecoveryPending/Running` 用于 prefs 未就绪时延后重试（对应 `XposedInitEntry` 中 `onPreferencesReadyAfterRetry()`）；
- `LOG_TAG = "HyperCeiler.NativeHome"` 是**无条件** logcat 标签，保证 prefs/日志级别异常时仍可读。

## 4. nativehook 基础设施

| 头文件 | 职责 |
| --- | --- |
| `nhk_base.h` | 公共类型、日志、错误码 |
| `elf_image.h` | 从内存解析 ELF（节表、符号表、动态段） |
| `native_image.h` | 已加载 so 的基址/大小/路径信息 |
| `memory_io.h` | 安全读写目标内存，处理跨页访问 |
| `page_guard.h` | 临时改页权限（`mprotect`），RAII 恢复 |
| `got_hook_backend.h` | GOT/PLT 表项替换式 hook |
| `hook_bank.h` | hook 记录、批量回滚、防止重复安装 |
| `resolver.h` | 按符号名/字节特征定位函数 |
| `arm64_decode.h` | AArch64 指令解码（用于构造 trampoline 与校验补丁点） |
| `xz/*` | XZ 解压（压缩后的特征表） |

这些模块是 host 可测的——`tests/nativehook/` 下有针对 ELF、GOT、bank、resolver、page guard、Dart 契约的独立测试。

## 5. targets/home：桌面

HyperOS 桌面是 Flutter（Dart AOT）+ Rust 混合体，native 侧需要处理三类目标：

1. **Dart AOT 函数**：通过 `home_layout_dart_targets.h` 描述的目标表定位，用遵循 Dart AOT 寄存器约定的手写 trampoline 插入 hook。
2. **ELF 符号/特征**：`home_layout_elf_targets.h` + `resolver.h`，要求特征**恰好匹配一次**，不携带固定地址。
3. **libapp（Flutter 引擎/Rust）**：`home_startup`、`recents_*` 使用入口网关与生命周期护栏。

### 5.1 tweaks 子系统（`targets/home/tweaks/`）

| 文件 | 职责 |
| --- | --- |
| `entry.cpp` | 对外入口（`StartHomeTweaks` 等） |
| `image.cpp` / `image.h` | 目标镜像加载与地址映射 |
| `scanner.cpp` / `scanner.h` | 特征扫描 |
| `symtab.cpp` / `symtab.h` | 符号表解析 |
| `blob.cpp` / `blob.h` | 压缩特征块（配合 `xz/`） |
| `ht_plan.cpp` / `ht_plan.h` | 补丁计划构建与校验 |
| `htcache.cpp` / `htcache.h` | 计划缓存 |
| `signatures.h` | 内置签名表 |
| `a64.h` / `common.h` | 指令与公共定义 |

设计原则（CMake 注释）：*"the resolver locates every site from the image's own symbol table or from a signature that must match exactly once, so nothing here carries a fixed address."*

### 5.2 Dock 原生动效

`dock_native_hooks.cpp` / `dock_native_motion.cpp` / `dock_native_motion_arm64.S` / `dock_native_resolver.h` / `dock_native_runtime.h` / `dock_native_layout.h`。

由 `HYPERCEILER_DOCK_NATIVE_MOTION` 控制（默认 ON）。`nativeSetDockEnabled` 的必要性（代码注释）：

> The launcher-native motion chain is started by process name, so the Dock preference has to be pushed in explicitly: without this a launcher inside the module's scope keeps its health worker (measured at 22% of a core before the A-group fixes) running for a Dock nobody enabled.

即：动效链按进程名启动，若不下发开关，用户没开 Dock 也会持续跑健康检查 worker（实测约占单核 22%）。native 侧对失败采取 **fail-open**（推送失败保持启用），避免误关。

### 5.3 护栏

| 头文件 | 作用 |
| --- | --- |
| `probe_guard.h` | 探测阶段防重入/防并发 |
| `home_layout_unwind.h` | 布局 hook 的栈展开信息 |
| `home_layout_code_variants.h` | 多代码变体匹配（ROM 差异） |
| `home_layout_knobs.h` | 可调参数（间距、尺寸） |
| `home_startup_plan.generated.h` | 生成的启动计划（含目标特定字节签名） |
| `recents_guard.generated.h` | 生成的最近任务护栏表 |
| `home_workspace_snapshot.h` | 工作区几何一致性快照 |

`*.generated.h` 来自对特定 ROM OTA 的离线提取（见 `tests/` 下 `generate_plan.py`、`extract.py` 等脚本），不是手写常量。

## 6. targets/systemui：环境光/取色（OS4）

`ambient_color_os4.cpp` + `ambient_bindings.generated.h` + `ambient_sha256.h`

- 单独编译为 `libHyperCeilerAmbientOS4.so`，链接 `dl log`；
- 由 Kotlin 侧 `AmbientNativeColorOS4` **按需加载**（不在 Hyos companion 中）；
- 提供 `pauseForHotReload()` / `resumeAfterRejectedHotReload()`，供 `XposedInitEntry.onHotReloading` 冻结后端（因为 ART 不重新绑定已加载 JNI 库到新 ClassLoader）；
- `ambient_sha256.h` 用于对目标镜像做哈希校验，`ambient_bindings.generated.h` 保存目标特定的绑定表。

## 7. targets/hyos：Zygisk Next companion（可选）

仅当 `HYPERCEILER_HYOS_BACKEND=ON` 时构建，输出 `libHyperCeilerHyos.so`（脚本侧由 `tools/build_hyos_companion.py` 打包成 Magisk/KernelSU 模块）。

| 文件 | 职责 |
| --- | --- |
| `hyos_entry.cpp` | companion 入口 |
| `hyos_control.cpp` | 控制通道（开关下发、特性查询，`hyos_control::feature_enabled(kDock)`） |
| `hyos_report_runtime.cpp` | 上报运行时 |
| `hyos_app_entry_arm64.S` | 应用侧 trampoline |
| `hyos_snapshot.h` | 一致性快照 |
| `hyos_runtime_guard.h` | 运行时护栏（防越界/防重入） |
| `hyos_startup_reason.h` | 启动原因枚举 |

打包侧 `packaging/hyos-companion/`：

| 文件 | 作用 |
| --- | --- |
| `customize.sh` | 安装脚本，限制 `BOOTMODE`、arm64、API 37、`popsicle` 指纹、`OS4.0.0.44.XPBCNXM`、`/system_ext/bin/hyos_spawner` 存在 |
| `module.prop.in` | 模块属性模板 |
| `skip_mount` | 跳过挂载标记 |
| `zn_modules.txt` | Zygisk Next 模块清单 |

构建脚本 `tools/build_hyos_companion.py`：

- 参数 `--source-dir`、`--build-dir`、`--sdk`、`--ndk`（默认 `28.2.13676358`）、`--cmake`（默认 `--ninja`）、`--ndk-host-tag`、`--stage`、`--label`（`testN` 校验）、`--report`；
- `stage` / `report` 目录已存在时**拒绝覆盖**；
- 对 `zygisk_next_api.h` 做 `expected_sdk` 的 sha256 校验。

## 8. 关键约束汇总

| 约束 | 原因 |
| --- | --- |
| 库名必须是 `libHyperCeilerNative.so` | LSPosed `native_init.list` + `System.loadLibrary` 契约 |
| 不用 `RegisterNatives` | 直接依赖 JNI 符号名，改名需同步 cpp |
| 只有模块自己 `dlopen` 才触发 `native_init` | LSPosed 不自动加载 |
| Dart trampoline 手写汇编 | Dart AOT 使用 x15 栈、x16/x17 scratch，非 AAPCS |
| 特征必须唯一匹配 | 不携带固定地址，避免 ROM 差异导致错 patch |
| 已加载 native 后端阻止热重载 | ART 不会把已加载 JNI 库绑定到新 ClassLoader |
| 第三方源码按名恢复 `-Wnon-pod-varargs` 等 | 曾有 `std::string` 传 `%s` 导致 launcher 崩溃 |
| 部分能力需 companion | Home/Weather 的 native 路径依赖 Zygisk Next API 4，APK 单独无法提供 |

## 9. 延伸阅读

- OS4 目标机型、40 个功能组与撤销语义：`doc/experimental-os4-popsicle.md`
- Native host 测试：`tests/nativehook/`、`tests/home-layout-native/`、`tests/home-recents-os4/`、`tests/home-startup*/`、`tests/hyos-*/`、`tests/os4-*/`
- 测试运行方式见 [08-testing.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/08-testing.md)
