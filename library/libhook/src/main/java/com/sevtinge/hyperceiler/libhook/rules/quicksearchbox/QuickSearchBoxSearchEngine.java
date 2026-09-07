/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.sevtinge.hyperceiler.libhook.rules.quicksearchbox;

import android.content.Context;
import android.text.TextUtils;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.rules.browser.BrowserSearchEngine;
import com.sevtinge.hyperceiler.libhook.rules.searchengine.SearchEngineConfig;
import com.sevtinge.hyperceiler.libhook.rules.searchengine.SearchEngineSync;
import com.sevtinge.hyperceiler.libhook.utils.api.ContextUtils;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

/**
 * 快速搜索端搜索引擎同步（跟随浏览器，方案：search_engine_sync_hook.md）。
 * <p>
 * 同步链路：浏览器 hook 在引擎构建/切换时把当前引擎 {名称,链接,图标,显示名}
 * 经模块 App 的 Provider 中转写入模块 prefs（{@link SearchEngineSync}），
 * 本端读取并注入。浏览器选择什么引擎，快速搜索就是什么引擎。
 * <p>
 * 手机端注入点（12.9.0.05251 反编译确认）：
 * <ul>
 * <li>DexKit 定位容器类（toString 含 "SearchEngineSet{searchBox="，即混淆类
 * D2.a）的 5 参构造：(searchBox 场景 j, hotRank 场景 j, defaultSearchEngineMap,
 * sceneSearchEngineMap, 状态缓存)。注入方式为「追加而非清空」——把同步引擎
 * 写入 sceneSearchEngineMap 内层映射并追加到场景引擎列表（保留原生引擎，
 * 宿主对缺失引擎有 NPE 风险），defaultSearchEngineMap 值统一改为同步引擎名；
 * <li>构造完成后调用宿主自身的 f.k(引擎名)（写入 current_engine 偏好并刷新
 * UI），f.c()/e() 便从 sceneSearchEngineMap[场景][current_engine] 命中同步引擎；
 * <li>引擎条目类 h 为 10 参构造：(name, channelNo, showIcon, searchUrl,
 * iconUrl, title_zh_CN, title_zh_TW, title_en_US, title_bo_CN, title_ug_CN)。
 * </ul>
 * Pad 端（10.0.0.96 反编译确认）最终指向
 * com.android.quicksearchbox.xiaomi.SearchEngineDataProvider，13 参
 * SearchEngineItem 构造（title_zh_CN/key/searchUriDefault/searchUri/extra/icon/
 * iconHash/queryParameterKey/label/isDefault/isRecommended/localOpen/desktop）；
 * 引擎装配链路为 SearchEngineHelper.updateEngines（key 为条目 label，写入
 * searchEngine.xml 的 current_engine）。宿主 SearchEngineHelper 硬编码黑名单
 * {"bing"} 且 needFilter() 多数情况为 true，label 恰为 "bing" 的引擎会被直接
 * 过滤导致回落百度 —— 本 hook 拦截 needFilter() 恒返回 false 以保证两端引擎一致。
 */
public class QuickSearchBoxSearchEngine extends BaseHook {

    private static final String TAG = "QuickSearchBoxSearchEngine";

    // Pad 端：快速搜索与浏览器最终都指向该 DataProvider
    private static final String PAD_PROVIDER_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider";
    private static final String PAD_ENGINE_ITEM_CLASS = PAD_PROVIDER_CLASS + "$SearchEngineItem";
    private static final String PAD_SEARCH_URI_CLASS = PAD_PROVIDER_CLASS + "$SearchUri";
    private static final String PAD_DESKTOP_CLASS = PAD_PROVIDER_CLASS + "$Desktop";
    /** Pad 端引擎装配管理器（updateEngines 构建引擎集，内含 bing 黑名单） */
    private static final String PAD_SEARCH_HELPER_CLASS = "com.android.quicksearchbox.SearchEngineHelper";

    // 手机端：引擎条目 h（10 参构造）与引擎管理单例 f（f.b(context).k(name) 切换引擎）
    private static final String PHONE_ENGINE_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.h";
    private static final String PHONE_MANAGER_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.f";

    private Constructor<?> mPadEngineItemConstructor;
    private Constructor<?> mPadSearchUriConstructor;
    private Constructor<?> mPadDesktopConstructor;

    private Class<?> mPhoneEngineClass;
    private Constructor<?> mPhoneEngineConstructor;

    private Class<?> mDexPhoneEngineSetClass;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mDexPhoneEngineSetClass = optionalMember("phone_search_engine_set", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .addUsingString("SearchEngineSet{searchBox=", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });
        XposedLog.i(TAG, "DexKit phone_search_engine_set: " + (mDexPhoneEngineSetClass != null ? mDexPhoneEngineSetClass.getName() : "null"));
        return true;
    }

    @Override
    public void init() {
        boolean hooked = false;
        // 快速搜索进程内若打包了浏览器引擎条目类，与浏览器使用同一套注入钩子
        // （hookBrowserItem 内部有进程守卫，仅浏览器进程生效）
        hooked |= BrowserSearchEngine.hookBrowserItem(this, TAG);
        hooked |= initPadHook();
        hooked |= initPhoneHook();

        if (hooked) {
            XposedLog.i(TAG, "SearchBox search engine sync ready");
        } else {
            XposedLog.e(TAG, "SearchBox search engine classes not found");
        }
    }

    // ==================== 同步引擎数据源 ====================

    /**
     * 生效引擎：浏览器同步数据优先，缺失时回退内置配置。
     */
    private static SearchEngineSync.CurrentEngine effectiveEngine() {
        SearchEngineSync.CurrentEngine synced = SearchEngineSync.readCurrentEngine();
        if (synced != null) {
            return synced;
        }
        SearchEngineConfig.Config config = SearchEngineConfig.load();
        SearchEngineConfig.Engine engine = config == null ? null : config.primary();
        if (engine == null) {
            return null;
        }
        SearchEngineSync.CurrentEngine result = new SearchEngineSync.CurrentEngine();
        result.name = engine.name;
        result.url = engine.url;
        result.icon = engine.icon;
        result.label = !TextUtils.isEmpty(engine.titleZh) ? engine.titleZh
            : !TextUtils.isEmpty(engine.keyword) ? engine.keyword : engine.name;
        return result;
    }

    /** 从搜索链接中解析搜索词参数名，如 .../search?q={searchTerms} → q */
    private static String queryKey(String url) {
        if (TextUtils.isEmpty(url)) {
            return "q";
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return "q";
        }
        for (String pair : url.substring(queryStart + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(eq + 1).contains("{searchTerms}")) {
                return pair.substring(0, eq);
            }
        }
        return "q";
    }

    // ==================== Pad ====================

    private boolean initPadHook() {
        Class<?> providerClass = findClassIfExists(PAD_PROVIDER_CLASS);
        if (providerClass == null) {
            XposedLog.w(TAG, "Pad SearchBox provider class not found: " + PAD_PROVIDER_CLASS);
            return false;
        }

        XposedLog.i(TAG, "Found Pad SearchBox provider class: " + providerClass.getName());
        findPadConstructors();

        try {
            hookAllMethods(providerClass, "initData", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    replacePadEngineData(param.getThisObject());
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Pad SearchBox initData hook skipped", e);
        }

        try {
            hookAllMethods(providerClass, "getSearchEngines", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    keepSinglePadEngine(param);
                }
            });
            XposedLog.i(TAG, "Hooked Pad SearchBox getSearchEngines()");
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook Pad SearchBox getSearchEngines", e);
        }

        // 绕过宿主引擎黑名单（SearchEngineHelper.sBlackList 硬编码 "bing"，
        // needFilter() 为 true 时 label=="bing" 的引擎在 updateEngines 中被剔除，
        // 导致同步引擎丢失并回落百度）：恒返回 false 保证两端搜索引擎一致。
        try {
            Class<?> helperClass = findClassIfExists(PAD_SEARCH_HELPER_CLASS);
            if (helperClass == null) {
                XposedLog.w(TAG, "Pad SearchEngineHelper not found, blacklist filter stays");
            } else {
                hookAllMethods(helperClass, "needFilter", new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        param.setResult(false);
                    }
                });
                XposedLog.i(TAG, "Hooked Pad SearchEngineHelper.needFilter -> false");
            }
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to hook Pad SearchEngineHelper.needFilter", e);
        }
        return true;
    }

    private void findPadConstructors() {
        try {
            Class<?> padEngineItemClass = findClassIfExists(PAD_ENGINE_ITEM_CLASS);
            if (padEngineItemClass != null) {
                for (Constructor<?> constructor : padEngineItemClass.getDeclaredConstructors()) {
                    if (constructor.getParameterTypes().length == 13) {
                        mPadEngineItemConstructor = constructor;
                        mPadEngineItemConstructor.setAccessible(true);
                        XposedLog.i(TAG, "Found Pad SearchBox SearchEngineItem 13-param constructor");
                        break;
                    }
                }
            }

            Class<?> padSearchUriClass = findClassIfExists(PAD_SEARCH_URI_CLASS);
            if (padSearchUriClass != null) {
                mPadSearchUriConstructor = padSearchUriClass.getDeclaredConstructor(String.class, String.class);
                mPadSearchUriConstructor.setAccessible(true);
            }

            Class<?> padDesktopClass = findClassIfExists(PAD_DESKTOP_CLASS);
            if (padDesktopClass != null) {
                mPadDesktopConstructor = padDesktopClass.getDeclaredConstructor(String.class, int.class);
                mPadDesktopConstructor.setAccessible(true);
            }
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to find Pad SearchBox constructors", e);
        }
    }

    /** 引擎列表强制只保留一个同步引擎 */
    private void keepSinglePadEngine(HookParam param) {
        Object engine = createPadEngine();
        if (engine == null) {
            Object result = param.getResult();
            if (result instanceof List && !((List<?>) result).isEmpty()) {
                engine = ((List<?>) result).get(0);
                modifyPadEngine(engine);
            }
        }
        if (engine != null) {
            param.setResult(createSingleList(engine));
        }
    }

    private Object createPadEngine() {
        if (mPadEngineItemConstructor == null) {
            return null;
        }
        SearchEngineSync.CurrentEngine engine = effectiveEngine();
        if (engine == null) {
            return null;
        }
        try {
            List<Object> searchUris = new ArrayList<>(1);
            if (mPadSearchUriConstructor != null) {
                searchUris.add(mPadSearchUriConstructor.newInstance("default", engine.url));
            }
            Object desktop = mPadDesktopConstructor == null ? null : mPadDesktopConstructor.newInstance(engine.url, 1);
            return mPadEngineItemConstructor.newInstance(
                engine.label,
                engine.name,
                engine.url,
                searchUris,
                "",
                engine.icon,
                "",
                queryKey(engine.url),
                engine.label,
                true,
                true,
                new ArrayList<>(),
                desktop
            );
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to create Pad SearchBox engine", e);
            return null;
        }
    }

    private void replacePadEngineData(Object provider) {
        if (provider == null) {
            return;
        }
        Object engine = createPadEngine();
        if (engine == null) {
            return;
        }

        try {
            Field engineDataField = findField(provider.getClass(), "mEngineData");
            if (engineDataField == null) {
                return;
            }
            engineDataField.setAccessible(true);
            Object engineData = engineDataField.get(provider);
            if (engineData == null) {
                return;
            }
            Field searchEngineDatasField = findField(engineData.getClass(), "searchEngineDatas");
            if (searchEngineDatasField == null) {
                return;
            }
            searchEngineDatasField.setAccessible(true);
            searchEngineDatasField.set(engineData, createSingleList(engine));
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to replace Pad SearchBox engine data", e);
        }
    }

    /** 兜底：修改现有引擎对象字段为同步引擎（key 用标识，标题用显示名） */
    private void modifyPadEngine(Object engine) {
        if (engine == null) {
            return;
        }
        SearchEngineSync.CurrentEngine source = effectiveEngine();
        if (source == null) {
            return;
        }

        List<Object> searchUris = new ArrayList<>(1);
        try {
            if (mPadSearchUriConstructor != null) {
                searchUris.add(mPadSearchUriConstructor.newInstance("default", source.url));
            }
        } catch (Exception ignored) {
        }

        Object desktop = null;
        try {
            if (mPadDesktopConstructor != null) {
                desktop = mPadDesktopConstructor.newInstance(source.url, 1);
            }
        } catch (Exception ignored) {
        }

        setField(engine, "title_zh_CN", source.label);
        setField(engine, "key", source.name);
        setField(engine, "searchUriDefault", source.url);
        setField(engine, "searchUri", searchUris);
        setField(engine, "extra", "");
        setField(engine, "icon", source.icon);
        setField(engine, "iconHash", "");
        setField(engine, "queryParameterKey", queryKey(source.url));
        setField(engine, "label", source.label);
        setField(engine, "isDefault", true);
        setField(engine, "isRecommended", true);
        setField(engine, "localOpen", new ArrayList<>());
        setField(engine, "desktop", desktop);
    }

    // ==================== 手机端 ====================

    private boolean initPhoneHook() {
        boolean hooked = false;

        mPhoneEngineClass = findClassIfExists(PHONE_ENGINE_CLASS);
        if (mPhoneEngineClass != null) {
            findPhoneEngineConstructor();
        } else {
            XposedLog.w(TAG, "Phone SearchBox engine class not found");
        }

        if (hookDexPhoneEngineSet()) {
            hooked = true;
        }
        return hooked;
    }

    private void findPhoneEngineConstructor() {
        try {
            for (Constructor<?> constructor : mPhoneEngineClass.getDeclaredConstructors()) {
                if (constructor.getParameterTypes().length == 10) {
                    mPhoneEngineConstructor = constructor;
                    mPhoneEngineConstructor.setAccessible(true);
                    XposedLog.i(TAG, "Found Phone SearchBox engine 10-param constructor");
                    return;
                }
            }
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to find Phone SearchBox engine constructor", e);
        }
    }

    /** 构建同步引擎条目 h（name, channelNo, showIcon, url, icon, 5×title） */
    private Object getPhoneEngine() {
        if (mPhoneEngine == null) {
            mPhoneEngine = createPhoneEngine();
        }
        return mPhoneEngine;
    }

    private Object mPhoneEngine;

    private Object createPhoneEngine() {
        if (mPhoneEngineClass == null || mPhoneEngineConstructor == null) {
            return null;
        }
        SearchEngineSync.CurrentEngine engine = effectiveEngine();
        if (engine == null) {
            return null;
        }
        try {
            String label = !TextUtils.isEmpty(engine.label) ? engine.label : engine.name;
            return mPhoneEngineConstructor.newInstance(
                engine.name,
                engine.name,
                true,
                engine.url,
                engine.icon,
                label,
                label,
                label,
                label,
                label
            );
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to create Phone SearchBox engine", e);
            return null;
        }
    }

    /**
     * Hook DexKit 定位的 SearchEngineSet 构造器（5 参）：
     * args[0] searchBox 场景（内含引擎列表）→ 追加同步引擎（不清空原生列表）；
     * args[2] defaultSearchEngineMap（scene→引擎名 String）→ 值统一改为同步引擎名；
     * args[3] sceneSearchEngineMap（scene→{引擎名→h}）→ 内层映射写入同步引擎；
     * 构造完成后调用宿主 f.k(同步引擎名) 切换当前引擎。
     */
    private boolean hookDexPhoneEngineSet() {
        if (mDexPhoneEngineSetClass == null) {
            XposedLog.w(TAG, "DexKit Phone SearchBox SearchEngineSet not found");
            return false;
        }

        try {
            hookAllConstructors(mDexPhoneEngineSetClass, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object[] args = param.getArgs();
                        if (args.length != 5) {
                            return;
                        }
                        Object engine = getPhoneEngine();
                        SearchEngineSync.CurrentEngine source = effectiveEngine();
                        if (engine == null || source == null) {
                            return;
                        }
                        appendEngineToScene(args[0], engine, source.name);
                        replaceMapValues(args[2], source.name);
                        putEngineIntoSceneMaps(args[3], source.name, engine);
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "SearchEngineSet constructor hook error", t);
                    }
                }

                @Override
                public void after(HookParam param) {
                    try {
                        switchCurrentEngine();
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Switch current engine error", t);
                    }
                }
            });
            XposedLog.i(TAG, "Hooked DexKit Phone SearchBox SearchEngineSet constructors");
            return true;
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook DexKit Phone SearchBox SearchEngineSet", e);
            return false;
        }
    }

    /** 追加同步引擎到场景对象的引擎列表（按名称去重，保留原生条目） */
    private void appendEngineToScene(Object sceneObj, Object engine, String engineName) {
        if (sceneObj == null) {
            return;
        }
        Field listField = findFirstFieldByType(sceneObj.getClass(), List.class);
        if (listField == null) {
            return;
        }
        try {
            listField.setAccessible(true);
            Object listObj = listField.get(sceneObj);
            if (listObj instanceof List) {
                List<Object> list = (List<Object>) listObj;
                boolean exists = false;
                for (Object item : list) {
                    if (item != null && engineName.equals(readEngineName(item))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    list.add(engine);
                }
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Append engine to scene error", t);
        }
    }

    /** 读取引擎条目名称（h.a 字段；混淆名可能变化，失败返回 null） */
    private String readEngineName(Object item) {
        try {
            Field field = item.getClass().getDeclaredField("a");
            field.setAccessible(true);
            Object value = field.get(item);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 调用宿主 f.b(context).k(引擎名) 切换当前引擎（写入 current_engine 并刷新 UI） */
    private void switchCurrentEngine() {
        SearchEngineSync.CurrentEngine source = effectiveEngine();
        if (source == null) {
            return;
        }
        try {
            Context context = ContextUtils.getContextNoError(ContextUtils.FLAG_CURRENT_APP);
            if (context == null) {
                return;
            }
            Class<?> managerClass = findClassIfExists(PHONE_MANAGER_CLASS);
            if (managerClass == null) {
                return;
            }
            Object manager = managerClass.getMethod("b", Context.class).invoke(null, context);
            if (manager == null) {
                return;
            }
            // 仅当当前引擎与同步目标不一致时切换，避免循环刷新
            Method currentMethod = managerClass.getMethod("d");
            currentMethod.setAccessible(true);
            Object current = currentMethod.invoke(manager);
            if (source.name.equals(current)) {
                return;
            }
            Method switchMethod = managerClass.getMethod("k", String.class);
            switchMethod.invoke(manager, source.name);
            XposedLog.i(TAG, "Current engine switched to: " + source.name);
        } catch (Throwable t) {
            XposedLog.w(TAG, "Switch current engine failed", t);
        }
    }

    /** sceneSearchEngineMap（scene→{引擎名→h}）内层映射写入同步引擎 */
    @SuppressWarnings("unchecked")
    private void putEngineIntoSceneMaps(Object mapObj, String engineName, Object engine) {
        if (!(mapObj instanceof Map) || engine == null) {
            return;
        }
        try {
            Map<Object, Object> scenes = (Map<Object, Object>) mapObj;
            for (Object value : new ArrayList<>(scenes.values())) {
                if (value instanceof Map) {
                    ((Map<Object, Object>) value).put(engineName, engine);
                }
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Put engine into scene maps error", t);
        }
    }

    /** Map 值统一改写为同步引擎名（仅当现有值均为 String 时，避免类型污染） */
    @SuppressWarnings("unchecked")
    private void replaceMapValues(Object mapObj, Object value) {
        if (!(mapObj instanceof Map)) {
            return;
        }
        try {
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            if (map.isEmpty()) {
                return;
            }
            for (Object v : map.values()) {
                if (!(v instanceof String)) {
                    return;
                }
            }
            for (Object key : new ArrayList<>(map.keySet())) {
                map.put(key, value);
            }
        } catch (Exception ignored) {
        }
    }

    // ==================== 通用工具 ====================

    private List<Object> createSingleList(Object item) {
        List<Object> list = new ArrayList<>(1);
        if (item != null) {
            list.add(item);
        }
        return list;
    }

    private Field findFirstFieldByType(Class<?> clazz, Class<?> type) {
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private void setField(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) {
            return;
        }
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
            }
        } catch (Exception ignored) {
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        if (clazz == null) {
            return null;
        }
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return findField(clazz.getSuperclass(), fieldName);
        }
    }
}
