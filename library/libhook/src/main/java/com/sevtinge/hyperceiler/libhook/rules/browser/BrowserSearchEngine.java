/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import android.text.TextUtils;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.rules.searchengine.SearchEngineConfig;
import com.sevtinge.hyperceiler.libhook.rules.searchengine.SearchEngineSync;

import java.util.ArrayList;
import java.util.List;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

/**
 * 浏览器搜索引擎注入。
 * <p>
 * 注入点（反编译确认，手机端百度合作版与平板端 20.6.970814 统一）：
 * <ol>
 * <li>主注入点：SearchEngineSet.initialize(SearchEnginesEntity) before 改写实体 ——
 * browserSearchBox 场景 searchEngines 替换为「内置配置引擎在前 + 原生引擎在后（去重）」，
 * defaultSearchEngineMap 全部值指向配置默认引擎；引擎选择弹窗、当前引擎均以该映射为数据源。
 * 平板端 updateSearchEngineByRemote 会在启动时把当前引擎强制对齐
 * defaultSearchEngineMap[browserSearchBox]（服务端默认，通常 baidu），
 * 因此必须改写该映射，否则用户选择的注入引擎重启后丢失；
 * <li>引擎切换写入点：SearchModuleSettings.setSearchEngineName after 发布当前引擎
 * 到快速搜索（{@link SearchEngineSync}）。
 * </ol>
 * 注：旧版基于 SearchEngineItem 15 参构造的兼容注入点已移除 —— 平板浏览器
 * 20.6.970814 中该构造全库无调用方（死路径），引擎链路统一走
 * SearchEngineSet.initialize + SearchEnginesEntity.SearchEngine。
 * <p>
 * 引擎内容来源（按优先级）：
 * <ol>
 * <li>内置 searchEngines JSON 配置（{@link SearchEngineConfig}，浏览器原生
 * searchengine.json 格式）；
 * <li>模块设置中的自定义覆盖（显示名/搜索链接/图标，非空且不同于界面默认值时生效）。
 * </ol>
 * 全程内存注入，不落地本地存储。注意：必须保留原生引擎（尤其 baidu）——宿主
 * getSearchEngineContentByScene 对未知引擎名会硬编码回落到 get("baidu").toContent()，
 * 缺失即 NPE 闪退；注入条目的 searchEngineName 保持稳定，避免宿主按名称查引擎表时 NPE。
 */
public class BrowserSearchEngine extends BaseHook {

    private static final String TAG = "BrowserSearchEngine";

    // 界面默认值：设置项等于这些值时视为未配置覆盖
    private static final String UI_DEFAULT_LABEL = "必应";
    private static final String UI_DEFAULT_URL = "https://www.bing.com/search?q={searchTerms}";
    private static final String UI_DEFAULT_ICON = "https://www.bing.com/favicon.ico";

    // 自定义覆盖偏好键（非空且不同于界面默认值时生效；浏览器与快速搜索设置页共用）
    private static final String PREF_OVERRIDE_LABEL = "browser_search_engine_label";
    private static final String PREF_OVERRIDE_URL = "browser_search_engine_url";
    private static final String PREF_OVERRIDE_ICON = "browser_search_engine_icon";

    /**
     * 实际引擎链路：SearchEngineSet.initialize(SearchEnginesEntity) 由
     * SearchEnginesEntity（原生 searchengine.json 解析结果）构建各场景映射，
     * 引擎选择弹窗/getSearchEngineContentByScene 均以它为数据源。
     */
    private static final String BROWSER_ENGINE_SET_CLASS =
        "com.android.browser.search.SearchEngineDataProvider$SearchEngineSet";
    private static final String BROWSER_SCENE_CLASS =
        "com.android.browser.search.SearchEnginesEntity$SearchEngineScene";
    private static final String BROWSER_SEARCH_ENGINE_CLASS =
        "com.android.browser.search.SearchEnginesEntity$SearchEngine";
    /** 浏览器引擎管理器（单例入口 getInstance） */
    private static final String BROWSER_PROVIDER_CLASS =
        "com.android.browser.search.SearchEngineDataProvider";
    /** 引擎选择写入点（Kotlin object），用户/服务端切换引擎都会经过 */
    private static final String BROWSER_MODULE_SETTINGS_CLASS =
        "com.android.browser.search.interaction.settings.SearchModuleSettings";

    @Override
    public void init() {
        hookBrowserItem(this, TAG);
    }

    // ==================== 自定义覆盖 ====================

    /**
     * 应用自定义覆盖到配置引擎（就地修改）：
     * 显示名/搜索链接/图标，非空且不同于界面默认值才视为已配置。
     */
    private static void applyOverride(SearchEngineConfig.Engine engine) {
        String overrideLabel = overridden(PREF_OVERRIDE_LABEL, UI_DEFAULT_LABEL);
        if (overrideLabel != null) {
            engine.titleZh = overrideLabel;
        }
        String overrideUrl = overridden(PREF_OVERRIDE_URL, UI_DEFAULT_URL);
        if (overrideUrl != null) {
            engine.url = overrideUrl;
        }
        String overrideIcon = overridden(PREF_OVERRIDE_ICON, UI_DEFAULT_ICON);
        if (overrideIcon != null) {
            engine.icon = overrideIcon;
        }
    }

    /** 读取覆盖项：非空且不同于界面默认值时返回值，否则返回 null */
    private static String overridden(String key, String uiDefault) {
        String value = PrefsBridge.getString(key, "").trim();
        return (!TextUtils.isEmpty(value) && !value.equals(uiDefault)) ? value : null;
    }

    // ==================== 浏览器 Hook（手机/平板统一） ====================

    /**
     * Hook 浏览器主注入点（仅在浏览器进程中激活：快速搜索进程内存在同名引擎条目类，
     * 其"全网搜索/百度/抖音"等系统条目不可替换，否则导致快速搜索闪退）。
     */
    public static boolean hookBrowserItem(BaseHook hook, String logTag) {
        if (!"com.android.browser".equals(BaseLoad.getPackageName())) {
            XposedLog.i(logTag, "Skip browser engine hook in process: " + BaseLoad.getPackageName());
            return false;
        }
        android.util.Log.i("SearchEngineSyncD", "hookBrowserItem entry, pkg=" + BaseLoad.getPackageName());
        boolean hooked = hookEngineSetInitialize(hook, logTag);
        android.util.Log.i("SearchEngineSyncD", "hookEngineSetInitialize result=" + hooked);
        return hooked;
    }

    // ==================== 主注入点：SearchEngineSet.initialize ====================

    /**
     * Hook 静态方法 SearchEngineSet.initialize(SearchEnginesEntity)：
     * 在构建各场景映射前改写传入实体：
     * <ul>
     * <li>browserSearchBox 场景 searchEngines 改为「配置引擎在前 + 原生引擎在后（去重）」；
     * <li>defaultSearchEngineMap 全部值指向配置默认引擎 —— 平板浏览器
     * updateSearchEngineByRemote 会在启动图标预载完成后把当前引擎强制对齐
     * defaultSearchEngineMap[browserSearchBox]，不改写则注入引擎每次启动被切回服务端默认。
     * </ul>
     * <p>
     * 注意：必须保留原生引擎（尤其 baidu）——宿主 getSearchEngineContentByScene
     * 对未知引擎名会硬编码回落到 get("baidu").toContent()，缺失即 NPE 闪退。
     */
    private static boolean hookEngineSetInitialize(BaseHook hook, String logTag) {
        Class<?> setClass = hook.findClassIfExists(BROWSER_ENGINE_SET_CLASS);
        if (setClass == null) {
            XposedLog.w(logTag, "Browser engine set class not found: " + BROWSER_ENGINE_SET_CLASS);
            return false;
        }
        try {
            hook.hookAllMethods(setClass, "initialize", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object[] args = param.getArgs();
                        if (args == null || args.length < 1 || args[0] == null) {
                            return;
                        }
                        rewriteSearchEnginesEntity(args[0]);
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "EngineSet initialize hook error", t);
                    }
                }

                @Override
                public void after(HookParam param) {
                    // 不在此处发布：initialize 于主线程 onCreate 期间多次触发，
                    // Provider 访问会与宿主启动竞争。引擎切换发布由
                    // SearchModuleSettings.setSearchEngineName 钩子负责。
                }
            });
            XposedLog.i(logTag, "Hooked SearchEngineSet.initialize");
            hookSearchModuleSettings(hook, logTag);
            return true;
        } catch (Exception e) {
            XposedLog.e(logTag, "Failed to hook SearchEngineSet.initialize", e);
            return false;
        }
    }

    /**
     * Hook SearchModuleSettings.setSearchEngineName（引擎切换写入点）：
     * 用户在弹窗中选择引擎/宿主强制对齐默认引擎后向快速搜索发布新引擎。
     */
    private static void hookSearchModuleSettings(BaseHook hook, String logTag) {
        Class<?> settingsClass = hook.findClassIfExists(BROWSER_MODULE_SETTINGS_CLASS);
        if (settingsClass == null) {
            XposedLog.w(logTag, "SearchModuleSettings not found, engine switch sync disabled");
            return;
        }
        try {
            hook.hookAllMethods(settingsClass, "setSearchEngineName", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    publishCurrentEngine();
                }
            });
            XposedLog.i(logTag, "Hooked SearchModuleSettings.setSearchEngineName");
        } catch (Exception e) {
            XposedLog.w(logTag, "Failed to hook setSearchEngineName", e);
        }
    }

    /** 把 SearchEnginesEntity 的 browserSearchBox 场景与默认引擎映射改写为内置配置 */
    private static void rewriteSearchEnginesEntity(Object entity) throws Exception {
        SearchEngineConfig.Config config = SearchEngineConfig.load();
        if (config == null || config.engines == null || config.engines.isEmpty()) {
            return;
        }
        String primaryName = null;
        SearchEngineConfig.Engine primary = config.primary();
        if (primary != null) {
            primaryName = primary.name;
        }

        // 1) searchEngineSceneMap.browserSearchBox.searchEngines → 配置引擎在前 + 原生引擎在后
        Object sceneMap = invokeGetter(entity, "getSearchEngineSceneMap");
        if (!(sceneMap instanceof java.util.Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.Map<Object, Object> scenes = (java.util.Map<Object, Object>) sceneMap;
        Class<?> loaderClass = entity.getClass().getClassLoader()
            .loadClass(BROWSER_SCENE_CLASS);
        Class<?> engineClass = entity.getClass().getClassLoader()
            .loadClass(BROWSER_SEARCH_ENGINE_CLASS);
        Object scene = scenes.get(SearchEngineConfig.SCENE_BROWSER_SEARCH_BOX);
        List<Object> injected = buildBrowserEngines(engineClass, config.engines);
        if (scene == null) {
            scene = loaderClass.newInstance();
            setField(scene, "searchEngineSceneId", 0L);
            setField(scene, "homepageSearchEngineCount", injected.size());
            setField(scene, "resetSearchEngineData", false);
            setField(scene, "searchEngines", injected);
            scenes.put(SearchEngineConfig.SCENE_BROWSER_SEARCH_BOX, scene);
        } else {
            Object listObj = invokeGetter(scene, "getSearchEngines");
            if (listObj instanceof List) {
                List<Object> original = (List<Object>) listObj;
                List<Object> merged = mergeEngines(engineClass, injected, original);
                original.clear();
                original.addAll(merged);
            } else {
                setField(scene, "searchEngines", injected);
            }
            // 场景重置标记清零，避免浏览器侧把改写后的数据再次覆盖
            setField(scene, "resetSearchEngineData", false);
        }

        // 2) defaultSearchEngineMap → 全部指向配置默认引擎。
        //    平板浏览器 20.6.970814 的 updateSearchEngineByRemote 在启动图标预载完成后
        //    把当前引擎强制对齐 defaultSearchEngineMap[browserSearchBox]（服务端默认，
        //    通常 baidu），不改写则注入引擎每次启动被切回；用户在浏览器内主动切换时
        //    overrideDataByChoose 会持久化其选择，与本改写互不冲突。
        if (primaryName != null) {
            Object defaultMap = invokeGetter(entity, "getDefaultSearchEngineMap");
            if (defaultMap instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> defaults = (java.util.Map<Object, Object>) defaultMap;
                for (Object key : new ArrayList<>(defaults.keySet())) {
                    defaults.put(key, primaryName);
                }
            }
        }

        // 注意：此处不要访问 SearchEngineDataProvider（getInstance 会触发宿主 DI
        // 服务图初始化，在主线程 initialize 钩子中执行曾造成 onCreate 卡顿 7 秒白屏）。
        XposedLog.i(TAG, "SearchEnginesEntity rewritten: " + injected.size()
            + " injected engine(s)");
    }

    /**
     * 合并引擎列表：配置引擎在前，原生引擎随后（按 searchEngineName 去重，
     * 保留原生条目供宿主兜底逻辑使用）。
     */
    private static List<Object> mergeEngines(Class<?> engineClass,
                                             List<Object> injected,
                                             List<Object> original) {
        List<Object> merged = new ArrayList<>(injected.size() + original.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            for (Object engine : injected) {
                String name = readEngineName(engine);
                if (name != null && seen.add(name.toLowerCase())) {
                    merged.add(engine);
                }
            }
            for (Object engine : original) {
                if (engine == null) {
                    continue;
                }
                String name = readEngineName(engine);
                if (name == null || seen.add(name.toLowerCase())) {
                    merged.add(engine);
                }
            }
        } catch (Throwable t) {
            // 合并异常时至少保证原生列表原样保留
            XposedLog.w(TAG, "Merge engines error, keep original", t);
            return original;
        }
        return merged;
    }

    /** 读取 SearchEngine 的 searchEngineName 字段 */
    private static String readEngineName(Object engine) {
        try {
            java.lang.reflect.Method m = engine.getClass().getMethod("getSearchEngineName");
            Object value = m.invoke(engine);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 依据内置配置构建浏览器 SearchEngine 对象（字段公开 setter） */
    private static List<Object> buildBrowserEngines(Class<?> engineClass,
                                                    List<SearchEngineConfig.Engine> engines) throws Exception {
        List<Object> result = new ArrayList<>(engines.size());
        for (SearchEngineConfig.Engine engine : engines) {
            applyOverride(engine);
            Object obj = engineClass.newInstance();
            String label = !TextUtils.isEmpty(engine.titleZh) ? engine.titleZh
                : !TextUtils.isEmpty(engine.keyword) ? engine.keyword : engine.name;
            invokeSetter(obj, "setSearchEngineName", engine.name);
            invokeSetter(obj, "setShowIcon", engine.showIcon);
            invokeSetter(obj, "setSearchUrl", engine.url);
            invokeSetter(obj, "setIconUrl", engine.icon);
            invokeSetter(obj, "setTitle_zh_CN", !TextUtils.isEmpty(engine.titleZh) ? engine.titleZh : label);
            invokeSetter(obj, "setTitle_zh_TW", !TextUtils.isEmpty(engine.titleTw) ? engine.titleTw : label);
            invokeSetter(obj, "setTitle_en_US", !TextUtils.isEmpty(engine.titleEn) ? engine.titleEn : label);
            invokeSetter(obj, "setTitle_bo_CN", label);
            invokeSetter(obj, "setTitle_ug_CN", label);
            invokeSetter(obj, "setChannelNo", !TextUtils.isEmpty(engine.channelNo) ? engine.channelNo : engine.name);
            result.add(obj);
        }
        return result;
    }

    // ==================== 当前引擎发布（浏览器 → 快速搜索） ====================

    private static final java.util.concurrent.ScheduledExecutorService sPublishExecutor =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static volatile long sLastPublishTime;
    private static final long PUBLISH_MIN_INTERVAL_MS = 2000;

    /**
     * 读取浏览器当前引擎并发布到快速搜索（后台线程执行，含防抖）。
     * 信息来源：SearchEngineDataProvider（getSearchEngine/getSearchUri/
     * getItemTitle + engineSet.searchBox 图标）。
     */
    private static void publishCurrentEngine() {
        long now = System.currentTimeMillis();
        if (now - sLastPublishTime < PUBLISH_MIN_INTERVAL_MS) {
            return;
        }
        sLastPublishTime = now;
        // 延迟发布：避开宿主启动期（DI 服务图初始化）造成锁竞争
        sPublishExecutor.schedule(() -> {
            try {
                Class<?> providerClass = findProviderClass();
                android.util.Log.i("SearchEngineSyncD", "publish task start, providerClass=" + (providerClass != null));
                if (providerClass == null) {
                    return;
                }
                Object provider = providerClass.getMethod("getInstance").invoke(null);
                if (provider == null) {
                    return;
                }
                String name = (String) invokeGetter(provider, "getSearchEngine");
                if (TextUtils.isEmpty(name)) {
                    return;
                }
                String url = (String) invokeGetter(provider, "getSearchUri", name);
                String label = (String) invokeGetter(provider, "getItemTitle", name);
                String icon = readProviderIconUrl(provider, name);
                SearchEngineSync.publish(name, url, icon, label);
            } catch (Throwable t) {
                XposedLog.w(TAG, "Publish current engine error", t);
            }
        }, 3, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static Class<?> findProviderClass() {
        BrowserSearchEngine holder = new BrowserSearchEngine();
        return holder.findClassIfExists(BROWSER_PROVIDER_CLASS);
    }

    /** 从 engineSet.searchBox 读取引擎图标 URL（失败返回 null） */
    private static String readProviderIconUrl(Object provider, String name) {
        try {
            Object set = invokeGetter(provider, "getEngineSet");
            if (set == null) {
                return null;
            }
            Object searchBox = readField(set, "searchBox");
            if (searchBox instanceof java.util.Map) {
                Object engine = ((java.util.Map<?, ?>) searchBox).get(name);
                if (engine != null) {
                    Object icon = invokeGetter(engine, "getIconUrl");
                    return icon instanceof String ? (String) icon : null;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ==================== 反射工具 ====================

    private static Object readField(Object target, String name) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeGetter(Object target, String method) throws Exception {
        java.lang.reflect.Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private static Object invokeGetter(Object target, String method, Object arg) throws Exception {
        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterTypes().length == 1
                && m.getParameterTypes()[0].isInstance(arg)) {
                return m.invoke(target, arg);
            }
        }
        return null;
    }

    private static void invokeSetter(Object target, String method, Object value) throws Exception {
        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterTypes().length == 1) {
                Class<?> type = m.getParameterTypes()[0];
                if (type == boolean.class) {
                    m.invoke(target, value instanceof Boolean ? value : Boolean.TRUE);
                } else if (type.isInstance(value)) {
                    m.invoke(target, value);
                }
                return;
            }
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
    }
}
