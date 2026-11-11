# 08 测试体系

HyperCeiler 的测试集中在仓库根目录 [`tests/`](file:///d:/lsp/HyperCeiler-lite-fresh/tests)，采用**主机侧（host-side）回归**思路：不连手机、不刷机、不注入 LSPosed，而是把**未经改动的生产源码**编译到桌面环境，用可控的 stub / fixture 驱动其真实算法与状态机。

本项目的每条测试 README 都显式声明了 **"不证明什么"**——这不是免责声明，而是该体系的设计前提：host 测试验证的是「逻辑正确性」，设备验收必须另行完成。理解这条边界是读懂本篇的关键。

---

## 1. 测试哲学与边界

| host 测试可以证明 | host 测试不能证明 |
| --- | --- |
| 生产源码能编译（真实 SDK / API 102 / EzHook / Kotlin 编译器） | ART 上的真实字节码行为 |
| 几何计算、策略判定、状态机、动效投影的数值正确 | SurfaceFlinger 事务顺序、GPU 渲染 |
| 偏好读写路径、类型/null 保留、写护栏 | LSPosed 注入与 remote prefs 真实传输 |
| Native 解析器/ELF 映射/补丁计划对真实 ROM 字节的结果 | Android/Hyos 注入、真实进程策略、SELinux |
| 汇编 trampoline 在 AArch64 环境下的寄存器/NZCV 保留 | 真机上的长时稳定性、耗电、恢复能力 |
| 负向对照（negative control）能正确失败 | 真实渲染、手势、触摸 |

因此大量 README 会把 `device_status` 标记为 `NOT_TESTED`，或强调「本套测试不作为真机验收结果」。撰写/阅读结论时**不要**把 host 全绿等同于功能可用。

### 1.1 生产源码优先（production-source-first）

大多数 Java/Kotlin 套件的做法是：**直接编译生产文件**，只对 Android 系统类、Binder、Looper、偏好传输等做最小 stub，而**不复刻**被测算法。例如：

- `os4-init-prefs` 编译未修改的 `XposedInitEntry`、`PrefsBridge`、`BaseLoad`、`PackageTarget`，并链接真实的 libxposed API 102.0.0 JAR 与 EzHookTool 1.3.0 的 `TargetReadyDispatcher`；
- `hyos-control` 的 `control_test.cpp` 直接 `#include` 真实 `hyos_control.cpp`；
- `home-dock-window` 的 Java 套件编译 13 个真实 Dock 规则类。

### 1.2 负向对照（negative control）

多套测试提供 `--negative-controls` 或 `--production <原始文件>` 参数：把**改动前的源码**喂给同一套测试，必须**编译通过但在特定断言上失败**。用来证明测试真的能捕捉缺陷，而不是恒真。

```bash
# 例：用改动前的 XposedInitEntry 跑同一套测试
git show 5e4686069:library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/base/XposedInitEntry.java > /tmp/XposedInitEntry.java
python3 tests/os4-init-prefs/run.py --source /tmp/XposedInitEntry.java

# 例：NativeHomeHooksOS4 基线对照
python3 tests/native-home-dock-listener/run.py --production /path/to/original/NativeHomeHooksOS4.kt
```

### 1.3 输出目录必须全新

绝大多数 Python runner 要求 `--output <新目录>`，拒绝向已存在目录写入。这样做是为了保留历史证据（例如 `validation/hyos-control-blocking-v1/` 保存了第一版实现失败的 stalled-Binder 用例），避免「通过后就覆盖失败记录」。

### 1.4 失败不降级

项目反复强调的原则：**缺失的 fixture、无法解析的符号、不适用场景都算失败或显式 SKIP，绝不伪装成通过**。典型例子：

- `home-layout-native`：缺失/歧义符号直接失败，不报为成功的 skip；
- `home-layout-native` 的 legacy Dart 结构化诊断在 OS4 fixture 上**确实失败**，README 要求「保持这个失败可见，不要总结为全部通过」；
- CI 的 `DockNativeArm64Test` 在 x86_64 上打印 `SKIP`，明确记录为覆盖率缺口。

---

## 2. Host Native 测试运行器

### 2.1 入口

```bash
# 直接调用
bash tests/run_host_native_tests.sh [libapp.so ...]

# 通过 Gradle
./gradlew :app:hostNativeTests
./gradlew :app:hostNativeTests -PlibappSo=/path/to/libapp.so
```

[run_host_native_tests.sh](file:///d:/lsp/HyperCeiler-lite-fresh/tests/run_host_native_tests.sh) 的编译参数：

| 项 | 值 |
| --- | --- |
| 编译器 | `${CXX:-clang++}` |
| 参数 | `-std=c++20 -O2 -Wall -Wextra -Werror` |
| 头文件 | `-I app/src/main/cpp` |
| 非 Linux 额外宏 | `-DNHK_NO_PROC_MAPS`（page-guard reader 回落实现） |
| 输出目录 | `${TMPDIR:-/tmp}/nativehook-host-tests` |
| 退出码 | 失败用例数（0 = 全通过） |

脚本内部只有两个原语：`build <name> <source> [extra]` 与 `run <name> [args]`，编译日志写 `$OUT/<name>.build.log`，运行日志写 `$OUT/<name>.log`。

### 2.2 用例清单

| 测试 | 源文件 | 依赖 |
| --- | --- | --- |
| `NativeHookElfTest` | `tests/nativehook/NativeHookElfTest.cpp` | 无外部 fixture |
| `NativeHookGotTest` | `tests/nativehook/NativeHookGotTest.cpp` | 同上 |
| `NativeHookBankTest` | `tests/nativehook/NativeHookBankTest.cpp` | 同上 |
| `NativeHookCoreResolverTest` | `tests/nativehook/NativeHookCoreResolverTest.cpp` | 同上 |
| `NativeHookPageGuardTest` | `tests/nativehook/NativeHookPageGuardTest.cpp` | 同上（非 Linux 走 `NHK_NO_PROC_MAPS`） |
| `NativeHookImageViewTest` | `tests/nativehook/NativeHookImageViewTest.cpp` | 同上 |
| `DockNativeRuntimeTest` | `tests/home-dock-window/DockNativeRuntimeTest.cpp` | — |
| `DockNativeContainerTest` | `tests/home-dock-window/DockNativeContainerTest.cpp` | — |
| `DockNativeResolverTest` | `tests/home-dock-window/DockNativeResolverTest.cpp` | 可选传入真实 `libapp.so` |
| `NativeHookDartContractTest` | `tests/nativehook/NativeHookDartContractTest.cpp` | 可选传入真实 `libapp.so` |
| `NativeHookFlutterImageTest` | `tests/nativehook/NativeHookFlutterImageTest.cpp` | 需 `NHK_FLUTTER_LIB`，否则报告 SKIP |
| `DockNativeArm64Test` | `tests/home-dock-window/DockNativeArm64Test.cpp` + `DockNativeArm64Harness.S` + 生产 `dock_native_motion_arm64.S` | **仅 Linux + aarch64**，否则 SKIP |

两个"可选真实输入"的测试在没有参数时**仍会验证解码器**，只跳过依赖镜像的断言——这是设计上的降级执行，不是失败。

`DockNativeArm64Test` 直接执行生产 `dock_native_motion_arm64.S` 的 ARM64 指令，覆盖寄存器/NZCV 保留、Dart 栈保留、tagged/compressed pointer、immutable bank、scene 过滤、标量更新、独立解锁投影、非阻塞 eventfd 通知、非法指针拒绝计数。

### 2.3 脚本里的两个历史坑（保留为注释）

1. `NHK_FLUTTER_LIB` 分支刻意写成**两个 `if/else` 而不是数组 gather**：bash 3.2（macOS 默认）在 `set -u` 下对 `"${empty[@]}"` 报错，会导致脚本提前退出。
2. ARM64 harness 的判定必须是 **`Linux && aarch64`**：只判 OS 不够，因为 `ubuntu-latest` 是 x86_64。

---

## 3. 可移植性与外部输入

[tests/PORTABILITY.md](file:///d:/lsp/HyperCeiler-lite-fresh/tests/PORTABILITY.md) 定义统一的路径解析，实现位于 [hyperceiler_test_paths.py](file:///d:/lsp/HyperCeiler-lite-fresh/tests/hyperceiler_test_paths.py)。**导入该模块只解析路径**，不下载工具、不建目录、不启动 Android 服务。

### 3.1 环境变量表

| 环境变量 | 未设置时的默认值 |
| --- | --- |
| `HYPERCEILER_BUILD_CACHE` | `~/.cache/hyperceiler-build` |
| `JAVA_HOME` | `<build cache>/jdk-25.0.4.1` |
| `ANDROID_HOME` | `ANDROID_SDK_ROOT`，其次 `<build cache>/android-sdk` |
| `ANDROID_NDK_HOME` | `<Android SDK>/ndk/28.2.13676358` |
| `HYPERCEILER_TEST_DATA` | 当前 checkout 的父目录 |
| `KOTLIN_HOME` | `<test data>/tools-cache/kotlin-2.4.10/kotlinc` |
| `HYPERCEILER_ROM_ROOT` | `<test data>/firmware` |
| `HYPERCEILER_EXTRACTED_ROM_ROOT` | `<ROM root>/extracted` |
| `HYPERCEILER_SNAPSHOT_ROOT` | build cache |

要点：

- 覆盖值需用**绝对路径**；`~` 会被展开（`Path.expanduser()`）；
- 既有 CLI 参数仍有效：`--build-cache` 选择该 runner 的缓存，显式 `JAVA_HOME` / `ANDROID_HOME` / `HYPERCEILER_SNAPSHOT_ROOT` 仍会选中对应工具或输入；
- 路径覆盖**不会移除版本要求**：Android 编译脚本仍要求 API 37（`platforms/android-37.0`）、build-tools 37.0.0、以及对应 NDK；
- Python 依赖见 [tests/requirements.txt](file:///d:/lsp/HyperCeiler-lite-fresh/tests/requirements.txt)（仅 `defusedxml==0.7.1`）。

### 3.2 缓存所需 JAR

基于缓存的 Java runner 期望存在：

```
<build cache>/changed-hooks-compile/api-102.0.0.jar
<build cache>/hook-xposed-102-1.3.0.jar
<build cache>/core-1.3.0.jar          # 部分 runner 需要
<build cache>/gradle-home/            # 部分 runner 使用其中的 Kotlin 2.4.10 JAR
```

### 3.3 外部输入（不在版本库中）

仓库自带 checked-in fixture，但**不包含**小米 ROM 镜像、提取出的 APK/so、历史生成报告、历史编译的 peer class、以及全部构建工具。需要的目录结构：

```text
<test data>/
  acceptance/                 历史提取与回归输出
  tools/                      被引用的提取辅助脚本
  emulation/                  probe 构建输入（含仅测试用密钥）
<ROM root>/
  components/                 提取出的系统包与库
  launcher-native/lib/arm64-v8a/
  indexes/
  component-inventory.json
<extracted ROM root>/
  system_ext/lib64/           原始 vendor 库
  system/system/lib64/
<snapshot root>/
  source-os4-hyos-test*/       精确命名的源码/构建快照
<build cache>/
  changed-hooks-compile/
  gradle-home/
  arm64-emulation/root/       Linux ARM64 工具链与 QEMU
```

每个 runner 会自行声明所需的精确子路径；文件缺失或输入哈希校验失败，说明必须补齐输入或重新生成。**固件与派生的 native 二进制不得进入版本控制**（[.gitignore](file:///d:/lsp/HyperCeiler-lite-fresh/.gitignore) 中未列入，属约定约束）。

### 3.4 平台限制

- ARM64 工具链与 native runner 需要 **Linux / WSL**；
- `tests/arm64-emulation/run.py` 要求 Python 3.11+、`qemu-aarch64-static`、Ubuntu GCC 13 AArch64 交叉工具链；
- Kotlin 套件可传 `--java /path/to/java.exe` 以支持 WSL 调用 Windows JDK，必要时用 `HC_TEST_TMPDIR` 指定 Windows 挂载盘上的编译输出目录。

---

## 4. 测试目录分类索引

`tests/` 下共 **61 个测试目录**（另有 `PORTABILITY.md`、`hyperceiler_test_paths.py`、`run_host_native_tests.sh`、`requirements.txt` 四个根级文件）。按主题分类如下。

### 4.1 入口链、配置与框架状态

| 目录 | 验证内容 | 关键点 |
| --- | --- | --- |
| `os4-init-prefs` | 偏好初始化与 Home 恢复（Java 18 例 + native 11 例） | 编译未修改的 `XposedInitEntry`/`PrefsBridge`/`BaseLoad`/`PackageTarget`；每例启动新 JVM 避免状态污染；含 `--source` 负向对照 |
| `framework-status-transaction` | `FrameworkStatusManager` 单次 Editor commit 发布授权位 + 6 个元数据字段 | 明确声明**不是分布式事务**；物理 commit 失败会抑制 remote 写入与观察者通知；`--manager-source` 传入旧版 manager 作负向对照 |
| `activation-dialog` | `XposedActivateHelper` 激活对话框状态机 | 覆盖 2s 截止后的绑定、10s 非激活宽限、旋转、服务恢复、非 Activity context |
| `dock-style-refresh` | `PrefsBridge` 写入/通知 + Dock reveal style 刷新门控 | 覆盖 remote 缺失/只读/commit 失败、1000 并发事件、30s 回落、失败闩锁释放 |
| `android17-log-fixes` | Android 17 日志相关修复验证脚本（`verify_issue1.py` 等） | 脚本形式，非统一 runner |

### 4.2 Dock 窗口与玻璃层（Home）

| 目录 | 验证内容 |
| --- | --- |
| `home-dock-window` | 13 个 Java 类（`DockWindowPolicy`、`DockGlassPreset`、`DockRecentsMotion`、`DockUnlockReveal`、`DockGlassRetryPolicy`、`DockGlassSurfaceLease`、`DockGlassProcessPolicy`、`DockWallpaperEndpoint`、`DockNativeMotion`、`DockNativeMotionEndpoint`、`DockGlassRecoveryGate`、`DockRotationPolicy`、`DockGlassGeometry`）+ 4 个 C++ 测试 + ARM64 harness |
| `dock-style-refresh` | 见 4.1 |

`home-dock-window` 的 README 与三份补充文档（`NATIVE_DYNAMIC_RESOLUTION.md`、`GLASS_LIFETIME_REGRESSION.md`、`OS4_GLASS_NOTES.md`）记录了玻璃层实现的关键决策：SurfacePackage 释前显式 reparent 到 null、退役后拒绝迟到 attach、失败的 detach 保留句柄、warmup 期 alpha `1/255`、在 Greeze 冻结批次中只过滤 HyperCeiler 自身 UID/PID、OneKeyClean 白名单只加自身等。**这些约束在真机验收时需额外检查 SurfaceFlinger 中不累积旧层**。

### 4.3 Home 布局与启动（Native/Hyos）

| 目录 | 验证内容 | 备注 |
| --- | --- | --- |
| `home-layout-native` | 几何、folder 快照、indicator 策略、容量回滚、符号缓存重置、tweaks 七族补丁计划 | 需要 `CXX`（C++20）+ C 编译器 + Python 3；从 `.gnu_debugdata` 独立读取真实符号地址比对；`run.sh` / `run_tweaks_fixture.sh` / `run_symbol_reset.sh` |
| `home-startup` | Hyos 首快照布局 owner（15 个授权网关、794 个输出、39 个指令位置、15 个 detour、97 个 guard range） | 生产 gateway 在 AArch64 QEMU 下执行；明确列出「不做的事」：不开文件、不查 Binder、不解析符号表、不建线程、不 sleep |
| `home-startup-lifetime` | 启动 hook 生命周期（5 例） | 从 checked-in guard/plan 表构造合成镜像，**不需要私有固件**；含源码副本负向对照（移除生命周期守卫必须编译通过但在特定断言失败） |
| `home-startup-snapshot` | 组合快照（Java） | `compile_actual.py` / `run_java.py` / `run_mutations.py` |
| `home-report-native` | report 安装与 ARM64 路径 | `install_test.cpp`、`arm64_test.cpp` + `arm64_probe.S` |
| `home-recents-os4` | Recents 动画倍率（组件 + joint 复现） | 5 个 runner：`generate.py`（绑定 85 span/58 指针）、`run.py`（8208 项 ABI 检查）、`run_once.py`、`run_installer.py`（40 个 fork 隔离用例）、`run_joint.py`（11 场景） |
| `hyos-entry` | Hyos entry（loader/guard/specialization） | `run_arm64.py` 单独执行 ARM64 tail-call/register ABI |
| `hyos-control` | Hyos 控制与权威偏好（native 110 例 + Java + legacy） | 用真实 `std::thread` 执行生产 poll/expiry；无限循环不启动，调度受控；`run_legacy_java.py` 跑 3 个旧布局测试 |
| `hyos-snapshot` | Hyos 快照 | C++ 单测试 |
| `native-home-dock-listener` | `NativeHomeHooksOS4.kt` 偏好监听注册与回调 | 需要 Kotlin 2.4.10 编译器；JNI 故意不可用，测试生产代码对 native 不可用的兜底 |
| `nativehook` | 目标无关 hook 基础设施（ELF/GOT/bank/resolver/page-guard/ImageView/Dart 契约/Flutter 镜像） | 被 `run_host_native_tests.sh` 直接引用 |
| `native-remaining-audit` | 只读审计：OS3 hook 家族在 OS4 ROM 上的迁移状态 | 由 `--project` 选择外部数据根；产出 `acceptance/native-remaining-audit.json`；`not_wired_in_reviewed_os4_home_entry` 是**限定范围的状态**，不代表设备观测到的失败 |

### 4.4 OS4 功能回归（43 个目录）

绝大多数 `os4-*` 目录遵循同一形态：`run.py`（host 状态机）+ `compile_android.py`（真实 SDK37 / API102 编译校验）+ 可选 `verify_rom.py` / `check_rom.py`（ROM 哈希与方法体静态校验）+ `fixtures/`（Android stub 与被测 Kt/Java 的薄适配层）。

按功能分组：

| 分组 | 目录 |
| --- | --- |
| 环境光 / 媒体卡片 | `os4-ambient-color`、`os4-ambient-drawable`、`os4-ambient-light`、`os4-ambient-native-color`、`os4-always-dark`、`os4-media-seekbar-color`、`os4-media-text-size`、`os4-custom-background-drawable`、`os4-custom-background-overlay`、`os4-custom-background-renderer`、`os4-custom-background-runtime` |
| 状态栏 / 通知 / 控制中心 | `os4-carrier-text`、`os4-data-saver`、`os4-network-spacing`、`os4-notification-channel`、`os4-notification-count`、`os4-notification-expansion`、`os4-statusbar-slot-order`、`os4-stereo-icon-key`、`os4-wifi-standard`、`os4-barrage-length` |
| 锁屏 | `os4-pin-layout`、`os4-unlock-hint-os4`、`os4-unlock-hint-oem`、`os4-unlock-hint-decline`、`os4-zen-notification` |
| Home 网格与布局（native） | `os4-home-default-flush`、`os4-home-default-placement`、`os4-home-grid-codec`、`os4-home-grid-entry-marker`、`os4-home-grid-frame-marker`、`os4-home-grid-owner`、`os4-home-grid-preview`、`os4-home-grid-preview-joint`、`os4-home-grid-primary-router`、`os4-home-grid-protocol`、`os4-home-grid-revoke-routing`、`os4-home-grid-secondary-spacing`、`os4-home-grid-singlepoint`、`os4-home-grid-spacing` |
| 其他功能 | `os4-getapps-device`、`os4-never-sleep`、`os4-init-prefs` |

`os4-wifi-standard` 是这套模式的较完整范例：编译完整生产 `WifiStandard.kt`，用**真实 kotlinx-coroutines 1.9.0** 处理挂起/恢复/并发收集/取消，并用 host adapter 提供 OS4 ROM 精确的 R8 discriminator 值（外层 3、内层 2）与 5 输入 CombineUnsafe 布局；最终作者 host 批次 81 例 + 15 个源码副本负向对照，`check_rom.py` 另做 12 个完整方法体与 OTA APK/DEX 哈希校验。

`os4-home-grid-*` 系列大量使用 `.S` 汇编 probe/fixture 组合（`fixture.cpp` + `probe.S` + `run.py`），把 ROM 指令当数据映射后验证解析与路由决策，**不执行** ROM 指令。

### 4.5 无设备 ARM64 执行

| 目录 | 说明 |
| --- | --- |
| `arm64-emulation` | 用 QEMU user-mode 跨编译并运行 native 测试为 **AArch64 Linux ELF**；运行前校验 ELF 架构，保存命令/退出码/日志/二进制哈希 |

```bash
python3 tests/arm64-emulation/run.py \
  --toolchain-root /path/to/extracted-toolchain-root \
  --qemu /path/to/extracted-toolchain-root/usr/bin/qemu-aarch64-static \
  --out /path/to/emulation-results \
  --libapp /path/to/launcher/libapp.so \
  --launcher /path/to/launcher/libapp_launcher.so \
  --flutter /path/to/libhyper_os_flutter.so
```

关键限定（README 明确写出）：

- `libapp.so` / `libapp_launcher.so` / `libhyper_os_flutter.so` 是**数据 fixture**：只解析字节，**从不作为可执行 vendor 代码加载**；
- QEMU 默认 CPU model 是 `max`，提供的是 ARM64 指令环境，**不是真机 SoC 的忠实模型**；
- 运行的是 glibc native 进程，**不是 Android Bionic 环境**；
- 老 Dart 结构化诊断失败会标记 `diagnostic: true`，但仍保留 `FAIL` 与非零退出码，**runner 从不把失败转成通过**。

---

## 5. CI 中的测试

[native_host_tests job](file:///d:/lsp/HyperCeiler-lite-fresh/.github/workflows/ci_build.yml#L373-L387)：

```yaml
native_host_tests:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Run host native tests
      run: bash tests/run_host_native_tests.sh
    - name: Upload test logs
      if: failure()
      uses: actions/upload-artifact@v4
      with:
        name: host-native-test-logs
        path: /tmp/nativehook-host-tests/*.log
```

### 5.1 覆盖率缺口（必须知晓）

| 缺口 | 原因 | 后果 |
| --- | --- | --- |
| `DockNativeArm64Test` 在 CI 中被跳过 | runner 是 x86_64，而该 harness 需执行 AArch64 指令 | **该项无 CI 覆盖**；脚本打印 `SKIP` 而非隐藏 |
| 全部 Java/Kotlin/Python OS4 套件不在 CI 中 | 需要 ROM fixture、冻结 peer class、Kotlin 编译器缓存等外部输入 | 需要本地或自建环境手动运行 |
| `arm64-emulation` 不在 CI 中 | 需要 AArch64 交叉工具链 + QEMU | 作为补充手段手动执行 |

即：**CI 只覆盖 `run_host_native_tests.sh` 这一条线**，其余套件依赖开发者本地按 [PORTABILITY.md](file:///d:/lsp/HyperCeiler-lite-fresh/tests/PORTABILITY.md) 准备环境后手动运行。

---

## 6. 运行测试的推荐姿势

```bash
# 1) 最轻量：host native 测试（无需设备、无需 Android SDK）
bash tests/run_host_native_tests.sh

# 2) 带上真实 fixture
bash tests/run_host_native_tests.sh /path/to/libapp.so
NHK_FLUTTER_LIB=/path/to/libflutter.so bash tests/run_host_native_tests.sh /path/to/libapp.so

# 3) 使用 Portable 路径解析的 Java/Kotlin 套件
export HYPERCEILER_BUILD_CACHE="$HOME/.cache/hyperceiler-build"
export JAVA_HOME="/opt/jdk-25"
export KOTLIN_HOME="/opt/kotlinc"
export ANDROID_HOME="/opt/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
export HYPERCEILER_TEST_DATA="/data/hyperceiler-inputs"

python3 tests/os4-pin-layout/run.py --output /tmp/hc-pin-results
python3 tests/os4-custom-background-renderer/run.py --output /tmp/hc-renderer-results

# 4) 真实 SDK 编译校验（不跑 Android）
python3 tests/os4-wifi-standard/compile_android.py --output /tmp/hc-wifi-compile
```

注意事项：

- `--output` 必须是**未使用过的新目录**；
- 每个测试目录的 `README.md` / `README.txt` 是该套件的权威说明（覆盖范围 + 明确的不覆盖范围），改动相关代码前应连同 README 一起读；
- 使用 `tests/requirements.txt` 安装 Python 依赖（`pip install -r tests/requirements.txt`）；
- 新增/修改测试时，保持「生产源码优先」与「失败不降级」两条既有约定。

---

## 相关文档

- 构建与产物：[07-build-and-run.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/07-build-and-run.md)
- Native 源码结构与契约：[06-native.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/06-native.md)
- 入口链与 hook 流程（理解被测对象）：[03-hook-entry-and-flow.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/03-hook-entry-and-flow.md)
