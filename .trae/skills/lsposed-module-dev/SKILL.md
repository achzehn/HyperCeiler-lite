---
name: "lsposed-module-dev"
description: "Provides guidance and best practices for developing LSPosed modules, including setup, hooking, and debugging. Use when user asks to create, write, or analyze LSPosed/XPosed modules."
---

# LSPosed Module Development Guide

## Overview

This skill helps you develop LSPosed (a.k.a. Xposed) modules for Android. LSPosed is a modern Xposed framework that uses hooking to modify system and app behavior at runtime without modifying APKs.

## Core Concepts

### Xposed Module Lifecycle
1. **App Process Loading**: The host app loads the module's Application class
2. **Service Binding**: onServiceBind() is called when XposedService connects
3. **System Server Hooks**: Hook system_server processes (for system-wide modifications)
4. **Package Hooks**: Hook specific app packages when they are loaded

### Key Classes & Architecture

#### Entry Point: Application.java
- Implements XposedServiceHelper.OnServiceListener
- Declared in AndroidManifest.xml as the Application class
- Used category android:name="de.robv.android.xposed.category.MODULE_SETTINGS" for settings

Common pattern:
`java
public class Application extends android.app.Application
    implements XposedServiceHelper.OnServiceListener {

    @Override
    public void onServiceBind(@NonNull XposedService service) {
        // Set remote preferences
        PrefsBridge.setRemotePrefs(service.getRemotePreferences(PREFS_GROUP));
    }

    @Override
    public void onServiceDied(@NonNull XposedService service) {
        // Clean up resources
    }
}
`

#### Hook Base Classes

**BaseHook** provides the foundation:
- Method-level hooks using IMethodHook callback interface
- Class loading hooks via XposedInterface
- Package ready hooks via PackageReadyParam
- System server hooks via SystemServerStartingParam
- DexKit-based method resolution for obfuscated apps

### Project Structure (Recommended)

`
your-module/
├── app/
│   ├── build.gradle.kts         # Module application config
│   └── src/main/
│       ├── AndroidManifest.xml   # Declare module, activities
│       ├── assets/               # Config files, fonts, tips
│       └── java/
│           └── your/package/
│               ├── Application.java  # xposed entry
│               ├── hook/             # Hook implementations
│               │   ├── system/       # SystemUI, SystemFramework
│               │   ├── apps/         # App-specific hooks
│               │   └── utils/        # Hook utilities
│               └── ui/               # Configuration UI
├── library/
│   └── libhook/                 # Shared hook library
│       └── src/main/java/
│           ├── base/            # BaseHook, ModuleMatcher, XposedInitEntry
│           ├── callback/        # IMethodHook, IReplaceHook
│           └── rules/           # Organized by target app/feature
│               ├── systemui/
│               ├── home/
│               └── securitycenter/
└── signing.properties           # APK signing config
`

### AndroidManifest.xml Key Elements

`xml
<application android:description="@string/xposed_description">
    <activity android:name=".ui.SplashActivity">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="de.robv.android.xposed.category.MODULE_SETTINGS" />
        </intent-filter>
    </activity>
</application>
`

### Build Configuration (build.gradle.kts)

`kotlin
android {
    compileSdk = 37  // Android 16
    defaultConfig {
        minSdk = 35
        targetSdk = 37
    }
    packaging {
        resources {
            merges += listOf("META-INF/xposed/*")
        }
    }
}
`

## Hook Development Patterns

### 1. Method Hook (IMethodHook)
`java
// Hooking a method before invocation
hookMethod(method, IMethodHook.Before -> { param ->
    // Modify arguments
    param.args[0] = newValue;
});

// Hooking a method after invocation
hookMethod(method, IMethodHook.After -> { param ->
    Object result = param.getResult();
    param.setResult(modifiedResult);
});
`

### 2. Key Tool Libraries
- **DexKit**: For finding deobfuscated methods in classes
- **EzXHelper**: Library by KyuuBiran for simplifying Xposed hooks
- **PrefsBridge**: Remote preferences bridge between hook and UI

### 3. Resolution of Obfuscated Methods with DexKit
`java
// DexKit-based method resolution
List<MatchMethod> methods = dexKit.findMethod(methodFinder -> {
    methodFinder.usingParameters(String.class);
    methodFinder.returnType(Boolean.TYPE);
});
`

## Build & Sign

### signing.properties
`
storePassword=<your_password>
keyAlias=<your_alias>
keyPassword='<your_password>'
storeFile=../key.jks
`

## Debugging

1. **Check Logs**: Use logcat | grep -i xposed to see Xposed messages
2. **Safe Mode**: Implement crash handler to handle hook crashes gracefully
3. **XposedLogLoader**: Loads and syncs Xposed logs into a local database

## Related Concepts

- **Xposed Framework vs LSPosed**: LSPosed is the modern successor optimized for Android 12+
- **Module Scope**: Define which apps/processes the module should hook into
- **System Server**: Android's central system process - hooks here affect system-wide behavior
- **Package Ready**: Callback when a specific app package is loaded into a process

## See Also

- DexKit library for obfuscation-resistant method finding
- EzXHelper library for simplified hooking API