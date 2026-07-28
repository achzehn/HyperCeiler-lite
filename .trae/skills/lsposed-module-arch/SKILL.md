---
name: "lsposed-module-arch"
description: "通用 LSPosed/Xposed 模块开发架构指导。基于分层 Hook 架构（BaseHook/BaseLoad/XposedInitEntry）、注解驱动模块发现、DexKit 反混淆、双进程 Prefs、安全模式与崩溃监控等模式提炼。包含环境自检、libxposed API 版本检测、国内镜像配置、自动升级脚本。Invoke when user is developing, refactoring, or debugging any LSPosed/Xposed module project."
---

# LSPosed Module Architecture Guide

> 这是一套从生产级 LSPosed 模块中提炼出的通用架构模式，适用于任何基于 Xposed API 100+ / LSPosed 的 Android 系统/应用定制模块。

## When to Use

- 新建或重构 LSPosed / Xposed 模块项目。
- 需要设计模块化 Hook 架构（按目标应用/系统组件组织）。
- 需要实现 DexKit 反混淆、双进程偏好同步、安全模式、崩溃监控。
- 需要设置 Gradle 多模块构建、签名、LSPosed 服务绑定。
- 需要调试 Hook 不生效、崩溃循环、作用域匹配失败等问题。

## Recommended Multi-Module Structure

```
root/
├── app/                          # Android Application: UI, settings, logs, safe mode
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml   # Xposed module declaration, activities, providers
│       └── java/com/example/module/
│           ├── Application.java  # XposedServiceHelper.OnServiceListener
│           ├── ui/               # Activities, fragments
│           ├── settings/         # Preference fragments
│           ├── log/              # Log viewer, Room database, export
│           └── safemode/         # Safe mode UI, crash store
│
├── library/common/               # Shared library: UI components, prefs bridge, logging
├── library/core/                 # Core components: database, icon loader, provision
├── library/libhook/              # Hook runtime: all hooks, API wrappers, safety
│   ├── base/
│   │   ├── BaseHook.java         # Base class for all hook rules
│   │   ├── BaseLoad.java         # Per-target-app hook loader
│   │   ├── XposedInitEntry.java  # XposedModule entry point
│   │   ├── ModuleMatcher.java    # Load-condition matcher
│   │   └── DataBase.java         # APT-generated hook metadata index
│   ├── app/                      # One BaseLoad subclass per target package
│   ├── rules/                    # Concrete hooks organized by target/feature
│   ├── utils/hookapi/tool/       # EzxHelpUtils or similar wrapper
│   ├── utils/hookapi/dexkit/     # DexKit facade + cache manager
│   ├── safecrash/                # CrashMonitor, SafeModeHandler, CrashScope
│   └── callback/                 # IMethodHook, IReplaceHook, ICrashHandler
│
├── library/processor/            # Annotation processor: @HookBase -> DataBase
├── library/xposed-api-adapter/   # Optional: adapter for libxposed API version
└── gradle/libs.versions.toml     # Centralized dependency versions
```

## Core Architecture Patterns

### Pattern 1: Three-Layer Hook Registration

```
XposedInitEntry (XposedModule)
    │
    ├── onSystemServerStarting() ──► load framework-level hooks (CorePatch, FlagSecure, CrashMonitor)
    └── onPackageReady() ──────────► invokeInit(packageName)
             │
             ▼
    DataBase.get() ──► ModuleMatcher.shouldLoad(data, packageName)
             │
             ▼
    classLoader.loadClass(className) ──► BaseLoad.onLoad(param)
             │
             ▼
    BaseLoad.onPackageLoaded() ──► initHook(new MyHook(), enabledCondition)
             │
             ▼
    BaseHook.init() ──► findAndHookMethod / DexKit-based hook
```

### Pattern 2: Annotation-Driven Module Discovery

Use a source-retention annotation on each `BaseLoad` subclass:

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface HookBase {
    String targetPackage();          // exact package name, or "system", or wildcard constants
    int minSdk() default -1;
    int maxSdk() default -1;
    float minOSVersion() default -1F;
    float maxOSVersion() default -1F;
    int deviceType() default 0;      // 0=ALL, 1=PAD_ONLY, 2=PHONE_ONLY
}
```

At compile time, an annotation processor (e.g., via `AutoService`) scans all `@HookBase` and generates a `DataBase` class containing a `HashMap<String, DataBase>` from class name to metadata.

At runtime, `XposedInitEntry` iterates this map and delegates to `ModuleMatcher`.

### Pattern 3: ModuleMatcher Conditions

`shouldLoad(data, packageName)` should check in this order:

1. **Safe mode** — skip all hooks for this package if configured.
2. **Security check** — for sensitive apps (e.g., security center, camera, launcher), verify modification / version if desired.
3. **Package match** — exact > system wildcard > third-party wildcard fallback.
4. **SDK version** — `minSdk <= currentSdk <= maxSdk`.
5. **OS version** — `minOSVersion <= osVersion <= maxOSVersion`.
6. **Device type** — PAD / PHONE / ALL.

### Pattern 4: DexKit Parallel Initialization

For hooks that must resolve obfuscated members:

```java
public class ObfuscatedHook extends BaseHook {
    private Method mTargetMethod;

    @Override protected boolean useDexKit() { return true; }

    @Override protected boolean initDexKit() {
        mTargetMethod = requiredMember("targetMethod", bridge ->
            bridge.findMethod(FindMethod.create()
                .matcher(MethodMatcher.create()
                    .declaredClass("com.target.ObfuscatedClass")
                    .returnType("boolean")
                    .paramTypes(String.class))
            ).single()
        );
        return mTargetMethod != null;
    }

    @Override public void init() {
        hookMethod(mTargetMethod, new IMethodHook() { ... });
    }
}
```

Rules:
- Only call `requiredMember` / `optionalMember` inside `initDexKit()`.
- Consume cached members in `init()`.
- Never trigger DexKit for the first time inside a hook callback.
- `BaseLoad` should batch pending DexKit hooks and run them in parallel (thread pool), then call `init()` for each.

### Pattern 5: Dual-Process Preference Bridge

Because the Hook process and App process are separate, preferences must be bridged:

- **App process**: writes to local `SharedPreferences` (device-protected storage), then syncs to LSPosed remote prefs via `XposedService.getRemotePreferences(...)`.
- **Hook process**: receives remote `SharedPreferences` in `XposedInitEntry`; **read-only**.
- Expose a utility like `PrefsBridge`:
  - `initForApp(Context)` / `initForHook(SharedPreferences remote)`
  - `getBoolean` / `getString` / `getInt` / `getFloat` / `getStringSet`
  - `putByApp(key, value)` — app-only writes with remote sync
- Never call `edit().put*` from the Hook process.

### Pattern 6: Safe Mode & Crash Monitor

Implement a `CrashMonitor` inside `SystemServer` (via `onSystemServerStarting`) to:

- Hook `AppErrors.handleAppCrashInActivityController` to detect crashes in scope apps.
- Hook `PackageWatchdog` / RescueParty to intercept automatic app rollback/restart.
- Record crash timestamps in a time window; when threshold exceeded, trigger `SafeModeHandler`.
- `SafeModeHandler` writes a system property / preference flag so that `ModuleMatcher` skips the package on next load.
- Provide a UI fragment (e.g., `SafeModeFragment`) for manual toggling.

App-level crash handler:
- `Application` registers `UncaughtExceptionHandler`, persists to local `CrashStore`, and on next launch shows a crash activity.

## Build & Environment

### Gradle (Kotlin DSL) Essentials

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
}

android {
    compileSdk = 37
    defaultConfig {
        minSdk = 35
        targetSdk = 37
    }
    packaging {
        resources { merges += listOf("META-INF/xposed/*") }
    }
}

dependencies {
    implementation(projects.library.core)
    implementation(projects.library.common)
    implementation(projects.library.libhook)
}
```

```kotlin
// library/libhook/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    api(projects.library.common)
    api(projects.library.processor)
    annotationProcessor(projects.library.processor)

    api(libs.libxposed.api)
    api(libs.libxposed.service)
    api(libs.dexkit)
    api(libs.ezxhelper)
    api(libs.hiddenapibypass)
}
```

### Signing

Support both `signing.properties` and environment variables:

```kotlin
val properties = rootProject.file("signing.properties").takeIf { it.exists() }?.let {
    Properties().apply { load(it.inputStream()) }
}

fun getString(prop: String, env: String): String =
    properties?.getProperty(prop)
        ?: System.getenv(env)
        ?: ""
```

### Environment Checklist

- JDK 21 (`JAVA_HOME`)
- Android SDK platform 37 + build-tools 37.0.0
- Git (for version info in build config)
- Optional: GitHub Packages credentials if pulling private artifacts

### libxposed API Version Reference

| Artifact | Group ID | Latest Version | Maven Repository |
|----------|----------|----------------|------------------|
| libxposed-api | `io.github.libxposed` | 102.0.0 | Maven Central |
| libxposed-service | `io.github.libxposed` | 102.0.0 | Maven Central |

**Gradle libs.versions.toml 配置：**

```toml
[versions]
libxposed-api = "102.0.0"
libxposed-service = "102.0.0"
dexkit = "1.2.0"
ezxhelper = "1.1.0"
hiddenapibypass = "4.3"

[libraries]
libxposed-api = { group = "io.github.libxposed", name = "api", version.ref = "libxposed-api" }
libxposed-service = { group = "io.github.libxposed", name = "service", version.ref = "libxposed-service" }
dexkit = { group = "org.luckypray", name = "DexKit", version.ref = "dexkit" }
ezxhelper = { group = "com.github.KyuubiRan", name = "EzXHelper", version.ref = "ezxhelper" }
hiddenapibypass = { group = "me.weishu", name = "FreeReflection", version.ref = "hiddenapibypass" }

[plugins]
android-application = { id = "com.android.application", version = "8.7.0" }
android-library = { id = "com.android.library", version = "8.7.0" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version = "2.0.0" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version = "2.0.0" }
google-devtools-ksp = { id = "com.google.devtools.ksp", version = "2.0.0-1.0.25" }
```

### China Mirror Configuration (国内镜像)

由于网络原因，强烈建议配置国内镜像加速下载。

**1. Gradle 全局镜像 (~/.gradle/init.gradle)**

```groovy
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/google/' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
        maven { url 'https://jitpack.io' }
        mavenCentral()
        google()
    }
}
```

**2. Gradle Wrapper 国内镜像 (gradle/wrapper/gradle-wrapper.properties)**

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.5-all.zip
# 或使用阿里云
# distributionUrl=https\://mirrors.aliyun.com/maven-central/org/gradle/gradle/9.5/gradle-9.5-all.zip
```

**3. 项目级 settings.gradle.kts 镜像配置**

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
```

**4. Android SDK 国内镜像环境变量**

```powershell
$env:ANDROID_SDK_REPOSITORIES="https://mirrors.tuna.tsinghua.edu.cn/android/repository/"
```

### Environment Auto-Check Script

**PowerShell 脚本 (check-env.ps1)**

```powershell
$ErrorActionPreference = "Stop"

function Test-CommandExists {
    param([string]$Command)
    $exists = $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
    return $exists
}

function Test-JdkVersion {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -match 'version "(\d+)\.') {
        $major = [int]$matches[1]
        if ($major -ge 21) {
            Write-Host "✅ JDK version: $javaVersion" -ForegroundColor Green
            return $true
        } else {
            Write-Host "❌ JDK version $major is too old, requires JDK 21+" -ForegroundColor Red
            return $false
        }
    }
    Write-Host "❌ Cannot detect JDK version" -ForegroundColor Red
    return $false
}

function Test-AndroidSdk {
    if (-not $env:ANDROID_HOME) {
        Write-Host "❌ ANDROID_HOME environment variable is not set" -ForegroundColor Red
        return $false
    }
    $platform37 = Test-Path "$env:ANDROID_HOME\platforms\android-37"
    $buildTools = Test-Path "$env:ANDROID_HOME\build-tools\37.0.0"
    $cmdlineTools = Test-Path "$env:ANDROID_HOME\cmdline-tools\latest"
    
    if ($platform37 -and $buildTools -and $cmdlineTools) {
        Write-Host "✅ Android SDK at $env:ANDROID_HOME" -ForegroundColor Green
        return $true
    } else {
        Write-Host "❌ Android SDK incomplete:" -ForegroundColor Red
        Write-Host "   - platforms/android-37: $(if ($platform37) { '✅' } else { '❌' })"
        Write-Host "   - build-tools/37.0.0: $(if ($buildTools) { '✅' } else { '❌' })"
        Write-Host "   - cmdline-tools/latest: $(if ($cmdlineTools) { '✅' } else { '❌' })"
        return $false
    }
}

function Test-GradleWrapper {
    if (Test-Path "./gradlew.bat") {
        Write-Host "✅ Gradle Wrapper exists" -ForegroundColor Green
        return $true
    }
    Write-Host "❌ gradlew.bat not found in current directory" -ForegroundColor Red
    return $false
}

function Test-Git {
    if (Test-CommandExists "git") {
        $gitVersion = git --version
        Write-Host "✅ Git: $gitVersion" -ForegroundColor Green
        return $true
    }
    Write-Host "❌ Git is not installed" -ForegroundColor Red
    return $false
}

Write-Host "`n===== LSPosed Module Development Environment Check =====`n" -ForegroundColor Cyan

$results = @()
$results += Test-JdkVersion
$results += Test-AndroidSdk
$results += Test-GradleWrapper
$results += Test-Git

Write-Host "`n===== Summary =====`n" -ForegroundColor Cyan
if ($results -notcontains $false) {
    Write-Host "✅ All checks passed! Ready to build LSPosed modules." -ForegroundColor Green
    exit 0
} else {
    Write-Host "❌ Some checks failed. Please fix the issues above." -ForegroundColor Red
    exit 1
}
```

### Environment Auto-Upgrade Script

**PowerShell 脚本 (setup-env.ps1)**

```powershell
$ErrorActionPreference = "Stop"

Write-Host "`n===== LSPosed Module Environment Setup =====`n" -ForegroundColor Cyan

function Install-AndroidSdkComponents {
    if (-not $env:ANDROID_HOME) {
        Write-Host "❌ ANDROID_HOME not set, skipping SDK installation" -ForegroundColor Red
        return
    }
    
    $sdkManager = "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
    
    if (-not (Test-Path $sdkManager)) {
        Write-Host "❌ SDK Manager not found at $sdkManager" -ForegroundColor Red
        Write-Host "   Please install Android SDK Command-line Tools first" -ForegroundColor Yellow
        return
    }
    
    Write-Host "📦 Installing Android SDK components..." -ForegroundColor Cyan
    
    & $sdkManager "platforms;android-37" "build-tools;37.0.0" `
        --sdk_root=$env:ANDROID_HOME `
        --no_https
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Android SDK components installed successfully" -ForegroundColor Green
    } else {
        Write-Host "❌ Failed to install Android SDK components" -ForegroundColor Red
    }
}

function Accept-SdkLicenses {
    if (-not $env:ANDROID_HOME) { return }
    
    $sdkManager = "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
    if (-not (Test-Path $sdkManager)) { return }
    
    Write-Host "📝 Accepting Android SDK licenses..." -ForegroundColor Cyan
    
    $yes = New-Object System.Management.Automation.Host.ChoiceDescription "&Yes", "Accept"
    $no = New-Object System.Management.Automation.Host.ChoiceDescription "&No", "Decline"
    $options = [System.Management.Automation.Host.ChoiceDescription[]]($yes, $no)
    
    & $sdkManager --licenses --sdk_root=$env:ANDROID_HOME 2>&1 | ForEach-Object {
        if ($_ -match "Accept?") {
            $host.ui.RawUI.WriteLine("y")
        }
    }
    
    Write-Host "✅ SDK licenses accepted" -ForegroundColor Green
}

function Configure-GradleMirror {
    $gradleHome = "$env:USERPROFILE\.gradle"
    $initFile = "$gradleHome\init.gradle"
    
    if (-not (Test-Path $gradleHome)) {
        New-Item -ItemType Directory -Path $gradleHome | Out-Null
    }
    
    $mirrorConfig = @"
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/google/' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
        maven { url 'https://jitpack.io' }
        mavenCentral()
        google()
    }
}
"@
    
    Set-Content -Path $initFile -Value $mirrorConfig -Encoding UTF8
    Write-Host "✅ Gradle mirror configured at $initFile" -ForegroundColor Green
}

function Update-GradleWrapperMirror {
    $wrapperProps = "./gradle/wrapper/gradle-wrapper.properties"
    
    if (-not (Test-Path $wrapperProps)) {
        Write-Host "⚠️ gradle-wrapper.properties not found in current directory" -ForegroundColor Yellow
        return
    }
    
    $content = Get-Content $wrapperProps -Raw
    $content = $content -replace 'distributionUrl=https\\\://services\.gradle\.org/distributions/(gradle-.+?\.zip)', 
        'distributionUrl=https\://mirrors.cloud.tencent.com/gradle/$1'
    
    Set-Content -Path $wrapperProps -Value $content -NoNewline
    Write-Host "✅ Gradle Wrapper mirror updated to Tencent Cloud" -ForegroundColor Green
}

Install-AndroidSdkComponents
Accept-SdkLicenses
Configure-GradleMirror
Update-GradleWrapperMirror

Write-Host "`n===== Setup Complete =====`n" -ForegroundColor Cyan
Write-Host "Please run: .\check-env.ps1 to verify the environment" -ForegroundColor Yellow
```

## AndroidManifest.xml Minimum Declaration

```xml
<manifest>
    <application
        android:name=".Application"
        android:description="@string/xposed_description">

        <activity android:name=".ui.MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="de.robv.android.xposed.category.MODULE_SETTINGS" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## Development Conventions

1. **One BaseLoad per target package** — keep hook aggregation clean.
2. **One BaseHook per feature** — avoid monolithic hook classes.
3. **Condition registration** — pass a boolean / `BooleanSupplier` to `initHook(...)` so unused hooks are skipped early.
4. **Resource replacement** — if replacing resources, initialize `ResourcesTool` from module APK path in `BaseLoad.onLoad()`.
5. **Logging separation** — Hook process uses `XposedLog`; App process uses `AndroidLog` or standard logging.
6. **Generated code** — never edit `DataBase.java` manually; it is produced by the annotation processor.

## Key References

- [Xposed Framework Documentation](https://api.xposed.info/)
- [DexKit GitHub](https://github.com/LuckyPray/DexKit)
- [EzXHelper GitHub](https://github.com/KyuubiRan/EzXHelper)
