# 07 构建、签名与运行

本篇覆盖「把仓库变成一个可安装的模块 APK」以及「装到设备上让它生效」的完整链路：工具链版本、Gradle 配置与任务、签名与凭据、Native 构建开关、Hyos companion 打包、产物路径、安装与调试手段、CI 流程。

---

## 1. 构建环境

### 1.1 版本矩阵

| 组件 | 版本 | 来源 |
| --- | --- | --- |
| Gradle Wrapper | 9.7.1 | [gradle-wrapper.properties](file:///d:/lsp/HyperCeiler-lite-fresh/gradle/wrapper/gradle-wrapper.properties) |
| Android Gradle Plugin | 9.4.0 | [libs.versions.toml](file:///d:/lsp/HyperCeiler-lite-fresh/gradle/libs.versions.toml#L3) |
| Kotlin | 2.4.10 | 同上 |
| Java toolchain | 25 | [app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L202-L208) |
| compileSdk / buildTools | 37 / 37.0.0 | [app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L43-L45) |
| minSdk / targetSdk | 35 / 37 | [app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L49-L50) |
| CMake | 3.22.1 | [CMakeLists.txt](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/cpp/CMakeLists.txt#L1) |
| C++ 标准 | C++20（`-std=c++20 -fvisibility=hidden`） | [app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L75-L79) |
| NDK | 28.2.13676358（测试脚本与 companion 脚本的评审版本） | [tests/PORTABILITY.md](file:///d:/lsp/HyperCeiler-lite-fresh/tests/PORTABILITY.md#L15) |
| ABI | 仅 `arm64-v8a` | [app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L70-L73) |

### 1.2 关键 gradle.properties

[gradle.properties](file:///d:/lsp/HyperCeiler-lite-fresh/gradle.properties) 中的非默认项：

| 配置 | 值 | 作用 |
| --- | --- | --- |
| `org.gradle.jvmargs` | `-Xmx4g -Dfile.encoding=UTF-8` | 编译期（含 LSParanoid 处理与 R8）需要的内存 |
| `org.gradle.parallel` | `true` | 并行构建多模块 |
| `org.gradle.caching` | `true` | 开启构建缓存 |
| `android.enableBuildConfigAsBytecode` | `true` | `BuildConfig` 以字节码形式生成（配合 R8 增量优化） |
| `android.r8.gradual.support` / `android.r8.maxWorkers` | `true` / `4` | R8 渐进式支持与并行度 |
| `android.suppressUnsupportedCompileSdk` | `37.0` | 关闭 compileSdk 37 的不支持警告 |
| `org.gradle.java.installations.paths` | `D:\SDKs\jdk-21.0.12+8,D:\winget_packages\dev\jdk-25` | 本机（当前开发环境）JDK 探测路径；换机后需按实际路径调整 |

> 注意：`gradle.properties` 里的 `org.gradle.java.installations.paths` 是本机绝对路径，属于本地环境相关配置。CI 不使用该项，而是通过 `actions/setup-java` 提供 JDK 25。

### 1.3 依赖仓库顺序

[dependencyResolutionManagement](file:///d:/lsp/HyperCeiler-lite-fresh/settings.gradle.kts#L50-L70) 采用 `FAIL_ON_PROJECT_REPOS`（模块内禁止自行声明仓库），仓库顺序为：

1. 阿里云镜像 `public` / `google` / `gradle-plugin`（注释说明：部分 artifact 在 google()/mavenCentral() 上会被重定向到 github.com release assets，在本网络环境下不可达并会导致构建卡死）；
2. `google()`、`mavenCentral()`、`mavenLocal()`；
3. GitHub Packages `maven.pkg.github.com/ReChronoRain/HyperCeiler`（**需要凭据**）；
4. `jitpack.io`、`api.xposed.info`。

### 1.4 GitHub Packages 凭据（构建前置条件）

`settings.gradle.kts` 在**配置阶段**就会调用 [loadGprCredentials()](file:///d:/lsp/HyperCeiler-lite-fresh/settings.gradle.kts#L5-L38)，因此凭据缺失会直接导致 Gradle 配置失败。解析顺序：

1. 环境变量 `GIT_ACTOR` + `GIT_TOKEN`（两者都非空）；
2. 根目录 `signing.properties` 中的 `gpr.user` + `gpr.key`；
3. 都没有 → 抛 `GradleException` 并给出提示。

---

## 2. Gradle 任务

### 2.1 构建变体

[app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L140-L181) 定义 4 个 buildType：

| 变体 | 任务 | `versionNameSuffix` | `GIT_HASH` | 签名 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `release` | `assembleRelease` | `-<yyyyMMdd>` | 短 hash | `hasProperties`（有凭据）/ `debug` | 发布版 |
| `beta` | `assembleBeta` | `-<yyyyMMdd>` | 长 hash | 同上 | 公测版 |
| `canary` | `assembleCanary` | `-<短hash>-r<versionCode>` | 长 hash | 同上 | main 分支日常构建 |
| `debug` | `assembleDebug` | `-<MMddHHmm>-r<versionCode>` | 长 hash | 无凭据时为 debug key | `isMinifyEnabled = false` |

`release` / `beta` / `canary` 均启用 `optimization.enable = true`（R8）。所有变体都会注入 `buildConfigField`：`GIT_HASH`、`GIT_CODE`、`BUILD_TIME`、`BUILD_OS_NAME`、`BUILD_USER_NAME`、`BUILD_JAVA_VERSION`、`GIT_BRANCH`（[defaultConfig](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L54-L68)）。

### 2.2 版本号规则

- `versionName = "2.10.166"`（[硬编码](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L52)，升版本时需改此处）；
- `versionCode = 5 + gitCommitCount`（`git rev-list --count HEAD`）——**版本号依赖 git 历史**，浅克隆会得到错误值；
- `GIT_BRANCH` 由 `origin` URL + 当前分支拼接而成（形如 `ReChronoRain/HyperCeiler:main`）。

### 2.3 归档名

[afterEvaluate](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L185-L195) 根据 `gradle.startParameter.taskNames` 中第一个含 `assemble` 的任务名推导构建类型，设置 `archivesName`：

```
HyperCeiler-<versionName><versionNameSuffix>
```

例如执行 `assembleCanary` 时产物名形如 `HyperCeiler-2.10.166-<短hash>-r<versionCode>.apk`。因此**产物名与所执行的任务直接相关**，直接跑 `build` 会回落到默认的 `debug` 后缀。

### 2.4 产物路径

| 变体 | 目录 |
| --- | --- |
| debug | `app/build/outputs/apk/debug/` |
| canary | `app/build/outputs/apk/canary/` |
| beta | `app/build/outputs/apk/beta/` |
| release | `app/build/outputs/apk/release/` |

Native 相关中间产物：`app/.cxx/`、`app/build/intermediates/`。

### 2.5 常用命令

```bash
# 本机构建（需先准备 signing.properties 或 GIT_ACTOR/GIT_TOKEN）
./gradlew :app:assembleDebug
./gradlew :app:assembleCanary
./gradlew :app:assembleRelease

# 清理
./gradlew clean

# Host 侧 native 测试（无需设备、无需 Android SDK）
./gradlew :app:hostNativeTests
./gradlew :app:hostNativeTests -PlibappSo=/path/to/libapp.so
```

`hostNativeTests` 是 [注册在 app 模块的 Exec 任务](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L233-L243)，`workingDir` 设为根工程目录，实际执行 `bash tests/run_host_native_tests.sh [libapp.so]`。细节见 [08-testing.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/08-testing.md)。

---

## 3. 签名与凭据

### 3.1 signing.properties

模板见 [signing.properties.example](file:///d:/lsp/HyperCeiler-lite-fresh/signing.properties.example)：

```properties
storeFile=
storePassword=
keyAlias=
keyPassword=

gpr.user=
gpr.key=
```

前四项用于 APK 签名，后两项用于 GitHub Packages 依赖拉取。该文件已在 [.gitignore](file:///d:/lsp/HyperCeiler-lite-fresh/.gitignore#L16) 中忽略，**不要提交**。

### 3.2 签名配置行为

[signingConfigs.create("hasProperties")](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L125-L138)：

- 四个字段通过 `getString(prop,env,prompt)` 解析，优先级为「`signing.properties` → 环境变量 → 控制台交互输入」；
- 依次尝试环境变量名：`STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`；
- v1–v4 签名全部开启；
- **重要分支**：若根目录不存在 `signing.properties`，`signingConfigs` 里不填任何字段，且各 buildType 会回落到 `signingConfigs.findByName("debug")`——即**无凭据时 release/canary 也会用 debug key 签名**，产物可安装但不能用于正式分发。

### 3.3 CI 中的签名

CI 通过临时生成 `signing.properties` + base64 解码 keystore 完成签名（[ci_build.yml](file:///d:/lsp/HyperCeiler-lite-fresh/.github/workflows/ci_build.yml#L61-L71)）：

```bash
touch signing.properties
echo storePassword=${{ secrets.KEY_STORE_PASSWORD }} >> signing.properties
echo keyAlias=${{ secrets.ALIAS }} >> signing.properties
echo keyPassword='${{ secrets.KEY_STORE_PASSWORD }}' >> signing.properties
echo storeFile=../key.jks >> signing.properties
echo ${{ secrets.SIGNING_KEY }} | base64 --decode > key.jks
```

`storeFile=../key.jks` 是相对于 `app/` 模块目录解析的，因此 keystore 实际落在仓库根目录。同时导出 `GIT_ACTOR` / `GIT_TOKEN` 供 `settings.gradle.kts` 拉取 GitHub Packages 依赖。

---

## 4. Native 构建

### 4.1 默认行为

`externalNativeBuild.cmake.path = app/src/main/cpp/CMakeLists.txt`，随 `assemble*` 自动触发（NDK 需已安装，ABI 仅 arm64-v8a）。默认产出：

| 库名 | 说明 |
| --- | --- |
| `libHyperCeilerNative.so` | 主库。库名是**三重契约**：必须同时匹配 `META-INF/xposed/native_init.list` 中的条目、`CMakeLists.txt` 的 `add_library` 名、以及 `NativeHomeHooksOS4.kt` 中 `System.loadLibrary` 的参数 |
| `libHyperCeilerAmbientOS4.so` | 仅在 `NOT HYPERCEILER_HYOS_BACKEND` 时额外产出（`targets/systemui/ambient_color_os4.cpp`） |

### 4.2 CMake 选项

| 选项 | 默认 | 效果 |
| --- | --- | --- |
| [`HYPERCEILER_HYOS_BACKEND`](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/cpp/CMakeLists.txt#L12-L26) | `OFF` | 置 `ON` 时输出名改为 `HyperCeilerHyos`，追加 `targets/hyos/*`、`home_startup`、`recents_runtime`、`home_report_hooks` 等源文件与 4 个 `.S` 汇编，并定义 `HYPERCEILER_HYOS_BACKEND=1`。需要 `HYPERCEILER_ZYGISK_NEXT_API_DIR` 下存在 `zygisk_next_api.h`，否则 **CMake 直接 FATAL_ERROR** |
| `HYPERCEILER_ZYGISK_NEXT_API_DIR` | 无 | Zygisk Next API 4 SDK 头文件目录（仅 `HYOS_BACKEND=ON` 时需要） |
| [`HYPERCEILER_DOCK_NATIVE_MOTION`](file:///d:/lsp/HyperCeiler-lite-fresh/app/src/main/cpp/CMakeLists.txt#L71-L77) | `ON` | 编译 Dock 原生动效（`dock_native_hooks.cpp`、`dock_native_motion.cpp`、`dock_native_motion_arm64.S`）并定义宏 |

CMake 选项需通过 `-D` 传入。在 Gradle 构建中可通过 `externalNativeBuild` 的 `arguments` 或 `cmake.cppFlags` 传递；目前仓库内**没有**为这两个选项提供 Gradle 侧的属性入口，正式构建走默认值（Dock 动效 ON、Hyos OFF）。Hyos 后端由独立脚本直接调用 CMake（见 4.3）。

### 4.3 Hyos companion 打包

`HYPERCEILER_HYOS_BACKEND=ON` 的构建不由 Gradle 负责，而是由 [tools/build_hyos_companion.py](file:///d:/lsp/HyperCeiler-lite-fresh/tools/build_hyos_companion.py) 直接驱动 CMake + Ninja，并把结果打成 Magisk/KernelSU 模块目录。该脚本是**一次性的实验性 companion 构建器**，具备若干强约束：

```bash
python3 tools/build_hyos_companion.py \
  --source-dir  <git 检出目录> \
  --build-dir   <cmake 构建目录> \
  --sdk         <zygisk_next_api.h 所在目录> \
  --ndk         <NDK 目录（评审版本 28.2.13676358）> \
  --cmake       cmake \
  --ninja       ninja \
  --stage       <输出模块目录> \
  --label       test<N> \
  --report      <报告 json 路径>
```

关键行为与校验：

| 环节 | 约束 |
| --- | --- |
| `--label` | 必须匹配 `test[1-9][0-9]*` |
| `--stage` / `--report` | **必须不存在**（拒绝覆盖） |
| SDK 头文件 | `zygisk_next_api.h` 的 SHA256 必须等于 `957922ae...25c8`（评审过的 API 4） |
| CMake 参数 | `ANDROID_ABI=arm64-v8a`、`ANDROID_PLATFORM=android-35`、`ANDROID_STL=c++_static`、`CMAKE_BUILD_TYPE=RelWithDebInfo`、`-DHYPERCEILER_HYOS_BACKEND=ON` |
| 动态符号 | `llvm-readelf` 必须找到 `OBJECT GLOBAL DEFAULT ... zn_module` |
| `DT_NEEDED` | 只允许 `libandroid/libbinder_ndk/liblog/libm/libdl/libc`，且**禁止 `libc++_shared.so`**（必须静态链接 C++ 运行时） |
| 源码完整性 | 构建前后对 `app/src/main/cpp` 全量文件做 SHA256 快照比对，构建期间源码被改动即失败 |
| 安装脚本 | 对 `customize.sh` 执行 `sh -n` 语法检查 |
| 产物 | `strip --strip-debug` 后复制到 `lib/arm64-v8a/libHyperCeilerHyos.so`；附带 `LICENSE-AGPL.txt`、`NOTICE-LLVM.txt`、`SOURCE-AND-API.txt` |

打包模板位于 [packaging/hyos-companion/](file:///d:/lsp/HyperCeiler-lite-fresh/packaging/hyos-companion)：

| 文件 | 作用 |
| --- | --- |
| `module.prop.in` | 模块元信息模板，`@LABEL@` / `@VERSION_CODE@` 由脚本替换；声明仅适配 `popsicle` + `OS4.0.0.44.XPBCNXM` |
| `customize.sh` | 安装时校验：必须 BOOTMODE 安装、`ARCH=arm64`、`API=37`、指纹匹配 `Xiaomi/popsicle/*`、incremental 等于 `OS4.0.0.44.XPBCNXM`、`/system_ext/bin/hyos_spawner` 存在 |
| `zn_modules.txt` | Zygisk Next 模块映射：`path=/system_ext/bin/hyos_spawner lib/arm64-v8a/libHyperCeilerHyos.so` |
| `skip_mount` | 空标记文件，跳过 OverlayFS 挂载 |

脚本输出报告中的 `device_status` 固定为 `NOT_TESTED`，并显式声明「staging 可安装 ≠ 已完成设备注入验证」。**该脚本只做构建与暂存，从不执行安装。**

---

## 5. 安装与运行

### 5.1 前提

- 设备已 Root，并安装/启用 [LSPosed](https://github.com/LSPosed/LSPosed)；
- 系统为 Android 15–16 的 Xiaomi HyperOS（README 声明；OS4/Android 17 为实验支持）；
- 设备 ABI 为 arm64-v8a（APK 只带该 ABI）。

### 5.2 安装步骤

1. 安装 APK：`adb install -r app/build/outputs/apk/<variant>/*.apk`，或以模块方式在 LSPosed 中安装；
2. 在 LSPosed 管理器中启用 HyperCeiler 模块；
3. 打开 HyperCeiler App，进入「作用域」勾选需要生效的应用（候选集为全部已安装应用，首次进入时默认勾选 `scope.list` 中的 19 个推荐作用域，见 [01-architecture.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/01-architecture.md)，也可直接采用 LSPosed 推荐作用域）；
4. 重启作用域应用（需要 Root）；`module.prop` 中 `autoHotReload=true`，在支持的 LSPosed 版本上改开关可直接热重载而无需重启应用；
5. 在 App 内逐项打开功能开关——**开关写入后需要让目标进程重新读取配置**（热重载或重启作用域应用）。

### 5.3 运行期契约文件

APK 必须包含（由 `library/libhook/src/main/resources/META-INF/xposed/` 提供，并在 packaging 中 `merges += "META-INF/xposed/*"` 且 `excludes += "**"`，即除 xposed 目录外不合并任何资源）：

| 文件 | 内容 |
| --- | --- |
| `java_init.list` | `com.sevtinge.hyperceiler.libhook.base.XposedInitEntry` |
| `native_init.list` | `libHyperCeilerNative.so` |
| `scope.list` | 23 个作用域包名 |
| `module.prop` | `minApiVersion=102`、`targetApiVersion=102`、`autoHotReload=true`、`staticScope=false`、`exceptionMode=protective` |

打包细节：[app/build.gradle.kts](file:///d:/lsp/HyperCeiler-lite-fresh/app/build.gradle.kts#L93-L108)。`androidResources` 额外传入 `--allow-reserved-package-id --package-id 0x36`（避免与系统包 ID 冲突）；`jniLibs.useLegacyPackaging=false`（so 不压缩解包）、`dex.useLegacyPackaging=true`。

### 5.4 调试与配置写入

注入进程对配置**只读**（`PrefsBridge` 在 hook 侧写入会被 `warnAndSkipIfHookWrite` 拦截）。需要人工注入调试值时用 App 侧提供的 Provider：

```bash
adb shell content call \
  --uri content://com.sevtinge.hyperceiler.shared_prefs \
  --method hc_debug_put \
  --arg prefs_key_<key> --arg <value>
```

多条约束（键前缀、允许的 key 白名单、值的类型限制）见 [04-prefs-and-data.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/04-prefs-and-data.md) 的 `hc_debug_put` 小节。

其他常用排查手段：

| 目的 | 手段 |
| --- | --- |
| 看模块加载/hook 结果 | LSPosed 日志；App 内「日志」页设置等级为 Debug |
| 确认框架状态 | `PrefsBridge.putFrameworkStatusByApp` 写入的 7 个状态键（见 04） |
| 进入安全模式 | CrashScope（见 [03](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/03-hook-entry-and-flow.md)）——连续崩溃时自动跳过 hook |
| 验证配置同步 | `prefs_key_` 前缀键同时存在于本地 `hyperceiler_prefs` 与 remote `hyperceiler_prefs_remote` |

---

## 6. CI 流程

### 6.1 ci_build.yml

[ci_build.yml](file:///d:/lsp/HyperCeiler-lite-fresh/.github/workflows/ci_build.yml) 三个 job：

| Job | Runner | 触发 | 行为 |
| --- | --- | --- | --- |
| `build_app` | `macos-latest` | push main / PR / 手动 | JDK 25；main 分支跑 `assembleCanary`，PR 跑 `assembleDebug`；找 APK → 输出 SHA256 到 step summary → `upload-artifact` |
| `push_to_canary_group` | `ubuntu-latest` | push main（非 tag、无 `[skp]`/`s#`） | 下载 artifact，用 Python 生成 commit caption（含作者合并、1024 字符裁剪、多级降级），curl 发 Telegram `sendDocument` |
| `push_to_debug_group` | `ubuntu-latest` | PR | 发 Telegram `sendMediaGroup` |
| `native_host_tests` | `ubuntu-latest` | 同 workflow | `bash tests/run_host_native_tests.sh`；失败时上传 `/tmp/nativehook-host-tests/*.log` |

`paths-ignore` 过滤了 `.github/**`（但保留 `!.github/workflows/**`）、`.idea/`、`doc/*`、`*.md`、`imgs/*`、`app/src/assets/**`、`crowdin.yml`——**纯文档改动不触发构建**。

`build_app` 的 `if` 条件会跳过含 `[release]` / `[beta]` / `app: update version` 的提交（这些交给 release workflow）。

### 6.2 release_build.yml

[release_build.yml](file:///d:/lsp/HyperCeiler-lite-fresh/.github/workflows/release_build.yml) 三个 job：

| Job | 触发条件 | 行为 |
| --- | --- | --- |
| `set_tag_version` | commit message 以 `app: update version <A> to <B>` 开头 | 解析出 `B`，打 tag `B` 并 push |
| `build` | commit message 含 `[release]` 或 `[beta]` | 对应跑 `assembleRelease` / `assembleBeta`，上传 artifact |
| `post_release` | 同上 | `git describe --tags` 取当前/上一个 tag，`git log --format='%aN'` 汇总贡献者，生成 release notes，用 `softprops/action-gh-release` 建 Release 并附 APK |

因此版本发布的完整动作是：改 `versionName` → 提交信息写成 `app: update version <旧> to <新>`（自动打 tag）→ 需要出包时再提交含 `[release]` 的消息。

### 6.3 覆盖率缺口（CI 明确记录）

`native_host_tests` 跑在 `ubuntu-latest`（x86_64），而 ARM64 汇编 harness 测试 `DockNativeArm64Test` 需要 **Linux + aarch64** 主机，因此**在 CI 中被跳过**（脚本打印 `SKIP`，不伪装成通过）。这是已知缺口，需要自备 aarch64 环境或依赖 `tests/arm64-emulation/run.py`（QEMU user-mode）补充。详见 [08-testing.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/08-testing.md)。

---

## 7. 常见构建失败定位

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 配置阶段 `Missing GitHub credentials` | 缺 `GIT_ACTOR`/`GIT_TOKEN` 且无 `signing.properties` | 补环境变量或创建 `signing.properties` |
| 依赖下载卡死 | 部分 artifact 被重定向到 github.com | 已由阿里云镜像前置缓解；仍失败时检查网络代理 |
| `versionCode` 异常小 / `GIT_BRANCH` 为 `unknown` | 浅克隆 | 使用 `fetch-depth: 0`（CI 已配置） |
| `HYPERCEILER_HYOS_BACKEND` 构建报 `Supply the pinned Zygisk Next API 4 SDK header directory` | 未提供 `HYPERCEILER_ZYGISK_NEXT_API_DIR` 或该目录无 `zygisk_next_api.h` | 提供正确的 SDK 头文件目录 |
| companion 脚本报 `SDK header differs from reviewed API4` | `zygisk_next_api.h` 版本不符 | 使用 pinned commit 对应的头文件 |
| 产物名带 `-debug` 后缀但不是 debug 构建 | 执行的不是 `assemble<Variant>` 而是 `build` | 用 `assembleCanary` 等显式任务 |
| 无凭据时 release APK 仍能装但签名异常 | 回落 debug key | 补齐 `signing.properties` 后重建 |

---

## 相关文档

- 模块划分与依赖：[02-modules.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/02-modules.md)
- Native 源码组织与契约：[06-native.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/06-native.md)
- 测试运行方式与环境变量：[08-testing.md](file:///d:/lsp/HyperCeiler-lite-fresh/code_wiki/08-testing.md)
