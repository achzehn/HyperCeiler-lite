/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.sevtinge.hyperceiler.libhook.rules.quicksearchbox;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
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

public class QuickSearchBoxSearchEngine extends BaseHook {

    private static final String TAG = "QuickSearchBoxSearchEngine";

    private static final String PHONE_ENGINE_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.h";
    private static final String PHONE_DESERIALIZER_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.g";
    private static final String PHONE_PROVIDER_CLASS = "com.android.browser.search.SearchEngineDataProvider";
    private static final String PHONE_ENGINE_SET_CLASS = "com.android.browser.search.SearchEngineDataProvider$SearchEngineSet";
    private static final String PHONE_BROWSER_ENGINE_CLASS = "com.android.browser.search.SearchEnginesEntity$SearchEngine";
    private static final String PAD_PROVIDER_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider";
    private static final String PAD_ENGINE_ITEM_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider$SearchEngineItem";
    private static final String PAD_SEARCH_URI_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider$SearchUri";
    private static final String PAD_DESKTOP_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider$Desktop";

    private static final String BING_NAME = "bing";
    private static final String BING_CHANNEL = "";
    private static final boolean BING_SHOW_ICON = true;
    private static final String BING_SEARCH_URL = "https://www.bing.com/search?q={searchTerms}";
    private static final String BING_ICON_URL = "https://www.bing.com/favicon.ico";
    private static final String BING_TITLE_ZH = "必应";
    private static final String BING_TITLE_TW = "Bing";
    private static final String BING_TITLE_US = "Bing";
    private static final String BING_TITLE_BO = "Bing";
    private static final String BING_TITLE_UG = "Bing";

    private Class<?> mPhoneEngineClass;
    private Class<?> mPhoneDeserializerClass;
    private Class<?> mPhoneProviderClass;
    private Class<?> mPhoneEngineSetClass;
    private Class<?> mPhoneBrowserEngineClass;
    private Class<?> mDexPhoneEngineSetClass;
    private Constructor<?> mPhoneEngineConstructor;

    private Class<?> mPadProviderClass;
    private Constructor<?> mPadEngineItemConstructor;
    private Constructor<?> mPadSearchUriConstructor;
    private Constructor<?> mPadDesktopConstructor;

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
        if (initPadSearchBoxHook()) {
            XposedLog.i(TAG, "Hook mode: Pad SearchBox");
            return;
        }

        if (initPhoneSearchBoxHook()) {
            XposedLog.i(TAG, "Hook mode: Phone SearchBox");
            return;
        }

        XposedLog.e(TAG, "SearchBox search engine classes not found");
    }

    private boolean initPhoneSearchBoxHook() {
        mPhoneEngineClass = findClassIfExists(PHONE_ENGINE_CLASS);
        mPhoneDeserializerClass = findClassIfExists(PHONE_DESERIALIZER_CLASS);
        if (mPhoneEngineClass == null || mPhoneDeserializerClass == null) {
            XposedLog.w(TAG, "Phone SearchBox classes not found");
            return false;
        }

        XposedLog.i(TAG, "Found Phone SearchBox engine class: " + mPhoneEngineClass.getName());
        findPhoneEngineConstructor();
        // 注释掉构造函数Hook以避免StackOverflowError
        // hookPhoneEngineConstructor通过newInstance创建Bing引擎会触发递归调用
        // hookDexPhoneEngineSet已经在before阶段修改了容器内容
        hookPhoneDeserializer();
        hookDexPhoneEngineSet();
        hookPhoneListProvider();
        return true;
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

    private void hookPhoneEngineConstructor() {
        try {
            hookAllConstructors(mPhoneEngineClass, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object bingEngine = createPhoneBingEngine();
                    if (bingEngine != null) {
                        param.setResult(bingEngine);
                        XposedLog.d(TAG, "Replaced SearchEngine constructor result with Bing");
                    }
                }
            });
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook Phone SearchBox engine constructor", e);
        }
    }

    private void replacePhoneConstructorArgs(Object[] args) {
        if (args == null || args.length < 10) return;
        args[0] = BING_NAME;
        args[1] = BING_CHANNEL;
        args[2] = BING_SHOW_ICON;
        args[3] = BING_SEARCH_URL;
        args[4] = BING_ICON_URL;
        args[5] = BING_TITLE_ZH;
        args[6] = BING_TITLE_TW;
        args[7] = BING_TITLE_US;
        args[8] = BING_TITLE_BO;
        args[9] = BING_TITLE_UG;
    }

    private void hookPhoneDeserializer() {
        if (mPhoneDeserializerClass == null) {
            XposedLog.w(TAG, "Phone SearchBox deserializer class not found: " + PHONE_DESERIALIZER_CLASS);
            return;
        }

        try {
            hookAllMethods(mPhoneDeserializerClass, "h", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object bingEngine = createPhoneBingEngine();
                    if (bingEngine != null) {
                        param.setResult(bingEngine);
                    }
                }
            });
            XposedLog.i(TAG, "Hooked Phone SearchBox deserializer h()");
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook Phone SearchBox deserializer", e);
        }
    }

    private void hookDexPhoneEngineSet() {
        if (mDexPhoneEngineSetClass == null) {
            XposedLog.w(TAG, "DexKit Phone SearchBox SearchEngineSet not found");
            return;
        }

        try {
            hookAllConstructors(mDexPhoneEngineSetClass, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    XposedLog.d(TAG, "SearchEngineSet constructor args count: " + args.length);
                    for (int i = 0; i < args.length; i++) {
                        XposedLog.d(TAG, "  arg[" + i + "]: " + (args[i] != null ? args[i].getClass().getName() + " val=" + args[i] : "null"));
                    }
                    if (args.length != 5) return;
                    Object bingEngine = createPhoneBingEngine();
                    if (bingEngine != null) {
                        keepOnlyBingInHostContainer(args[0], bingEngine);
                        keepOnlyBingInHostContainer(args[1], bingEngine);
                        keepOnlyBingInSceneMap(args[3], bingEngine);
                    } else {
                        XposedLog.w(TAG, "createPhoneBingEngine failed, fallback to modifying existing engines");
                        modifyAllEnginesInHostContainer(args[0]);
                        modifyAllEnginesInHostContainer(args[1]);
                        modifyAllEnginesInSceneMap(args[3]);
                    }
                    if (args[2] instanceof Map) {
                        XposedLog.d(TAG, "arg[2] is default engine name Map, setting all to bing...");
                        setAllMapValuesToString(args[2], BING_NAME);
                    }
                    if (args[4] instanceof Map) {
                        XposedLog.d(TAG, "arg[4] is scene engine Map, modifying all engine objects to Bing...");
                        modifyAllEnginesInMapToBing(args[4]);
                    }
                }
            });
            XposedLog.i(TAG, "Hooked DexKit Phone SearchBox SearchEngineSet constructors");
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook DexKit Phone SearchBox SearchEngineSet", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void pruneMap(Object mapObj, String key, Object value) {
        try {
            java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) mapObj;
            map.clear();
            map.put(key, value);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void setAllMapValuesToString(Object mapObj, String value) {
        try {
            java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) mapObj;
            for (Object key : new java.util.ArrayList<>(map.keySet())) {
                map.put(key, value);
            }
            XposedLog.d(TAG, "Set all map values to '" + value + "', keys: " + map.keySet());
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to set all map values to string", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void setAllMapValuesToObject(Object mapObj, Object value) {
        try {
            java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) mapObj;
            for (Object key : new java.util.ArrayList<>(map.keySet())) {
                map.put(key, value);
            }
            XposedLog.d(TAG, "Set all map values to Bing engine, keys: " + map.keySet());
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to set all map values to object", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void modifyAllEnginesInMapToBing(Object mapObj) {
        try {
            java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) mapObj;
            int count = 0;
            for (Object key : map.keySet()) {
                Object engine = map.get(key);
                if (engine != null) {
                    modifyEngineToBing(engine);
                    count++;
                }
            }
            XposedLog.d(TAG, "Modified " + count + " engines in map to Bing, keys: " + map.keySet());
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to modify all engines in map to Bing", e);
        }
    }

    private void modifyEngineToBing(Object engine) {
        if (engine == null) return;
        Class<?> clazz = engine.getClass();
        XposedLog.d(TAG, "Modifying engine class: " + clazz.getName());
        try {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                String name = field.getName();
                Class<?> type = field.getType();
                try {
                    if (type == String.class) {
                        String lowerName = name.toLowerCase();
                        if (lowerName.contains("search") && lowerName.contains("url")) {
                            field.set(engine, BING_SEARCH_URL);
                            XposedLog.d(TAG, "  Set field " + name + " to Bing search URL");
                        } else if (lowerName.contains("icon") && lowerName.contains("url")) {
                            field.set(engine, BING_ICON_URL);
                            XposedLog.d(TAG, "  Set field " + name + " to Bing icon URL");
                        } else if (lowerName.contains("name") || (lowerName.contains("engine") && lowerName.contains("search"))) {
                            Object oldVal = field.get(engine);
                            if (oldVal != null && (oldVal.equals("baidu") || oldVal.equals("Baidu") || oldVal.equals("百度"))) {
                                field.set(engine, BING_NAME);
                                XposedLog.d(TAG, "  Set field " + name + " from " + oldVal + " to " + BING_NAME);
                            }
                        } else if (lowerName.contains("title") || lowerName.contains("label")) {
                            Object oldVal = field.get(engine);
                            if (oldVal != null && (oldVal.equals("百度") || oldVal.equals("Baidu") || oldVal.equals("baidu"))) {
                                field.set(engine, BING_TITLE_ZH);
                                XposedLog.d(TAG, "  Set field " + name + " from " + oldVal + " to " + BING_TITLE_ZH);
                            }
                        }
                    } else if (type == boolean.class) {
                        String lowerName = name.toLowerCase();
                        if (lowerName.contains("default") || lowerName.contains("check") || lowerName.contains("selected")) {
                            field.setBoolean(engine, true);
                            XposedLog.d(TAG, "  Set boolean field " + name + " to true");
                        }
                    }
                } catch (Exception e) {
                    XposedLog.d(TAG, "  Failed to set field " + name + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to modify engine to Bing", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void modifyAllEnginesInHostContainer(Object container) {
        if (container == null) return;
        Field mapField = findFirstFieldByType(container.getClass(), Map.class);
        if (mapField != null) {
            try {
                mapField.setAccessible(true);
                Object mapObj = mapField.get(container);
                if (mapObj instanceof Map) {
                    Map<Object, Object> map = (Map<Object, Object>) mapObj;
                    for (Object key : new java.util.ArrayList<>(map.keySet())) {
                        Object engine = map.get(key);
                        if (engine != null) {
                            modifyEngineToBing(engine);
                        }
                    }
                    XposedLog.d(TAG, "Modified all engines in host container map");
                }
            } catch (Exception ignored) {}
        }
        Field listField = findFirstFieldByType(container.getClass(), List.class);
        if (listField != null) {
            try {
                listField.setAccessible(true);
                Object listObj = listField.get(container);
                if (listObj instanceof List) {
                    List<Object> list = (List<Object>) listObj;
                    for (Object engine : list) {
                        if (engine != null) {
                            modifyEngineToBing(engine);
                        }
                    }
                    XposedLog.d(TAG, "Modified all engines in host container list");
                }
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void modifyAllEnginesInSceneMap(Object sceneMapObj) {
        if (!(sceneMapObj instanceof Map)) return;
        Map<Object, Object> sceneMap = (Map<Object, Object>) sceneMapObj;
        for (Object key : sceneMap.keySet()) {
            Object nestedMapObj = sceneMap.get(key);
            if (nestedMapObj instanceof Map) {
                Map<Object, Object> nestedMap = (Map<Object, Object>) nestedMapObj;
                for (Object engineKey : new java.util.ArrayList<>(nestedMap.keySet())) {
                    Object engine = nestedMap.get(engineKey);
                    if (engine != null) {
                        modifyEngineToBing(engine);
                    }
                }
            }
        }
        XposedLog.d(TAG, "Modified all engines in scene map");
    }

    @SuppressWarnings("unchecked")
    private void keepOnlyBingInSceneMap(Object sceneMapObj, Object bingEngine) {
        if (!(sceneMapObj instanceof Map)) return;
        Map<Object, Object> sceneMap = (Map<Object, Object>) sceneMapObj;
        XposedLog.d(TAG, "Scene map keys: " + sceneMap.keySet());
        for (Object key : sceneMap.keySet()) {
            keepOnlyBingInNestedMap(sceneMap.get(key), bingEngine);
        }
    }

    @SuppressWarnings("unchecked")
    private void keepOnlyBingInNestedMap(Object mapObj, Object bingEngine) {
        if (!(mapObj instanceof Map)) return;
        Map<Object, Object> map = (Map<Object, Object>) mapObj;
        map.clear();
        map.put(BING_NAME, bingEngine);
    }

    @SuppressWarnings("unchecked")
    private void keepOnlyBingInHostContainer(Object container, Object bingEngine) {
        if (container == null) return;
        int mapCount = 0;
        int listCount = 0;
        Field mapField = findFirstFieldByType(container.getClass(), Map.class);
        if (mapField != null) {
            try {
                mapField.setAccessible(true);
                Object mapObj = mapField.get(container);
                if (mapObj instanceof Map) {
                    Map<Object, Object> map = (Map<Object, Object>) mapObj;
                    map.clear();
                    map.put(BING_NAME, bingEngine);
                    mapCount = 1;
                }
            } catch (Exception ignored) {}
        }

        Field listField = findFirstFieldByType(container.getClass(), List.class);
        if (listField != null) {
            try {
                listField.setAccessible(true);
                Object listObj = listField.get(container);
                if (listObj instanceof List) {
                    List<Object> list = (List<Object>) listObj;
                    list.clear();
                    list.add(bingEngine);
                    listCount = 1;
                }
            } catch (Exception ignored) {}
        }

        for (Field intField : container.getClass().getDeclaredFields()) {
            if (intField.getType() == int.class) {
                try {
                    intField.setAccessible(true);
                    intField.setInt(container, 1);
                } catch (Exception ignored) {}
            }
        }
        XposedLog.d(TAG, "Host container pruned: map=" + mapCount + " list=" + listCount);
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

    private void hookPhoneListProvider() {
        mPhoneProviderClass = findClassIfExists(PHONE_PROVIDER_CLASS);
        mPhoneEngineSetClass = findClassIfExists(PHONE_ENGINE_SET_CLASS);
        mPhoneBrowserEngineClass = findClassIfExists(PHONE_BROWSER_ENGINE_CLASS);
        if (mPhoneProviderClass == null || mPhoneEngineSetClass == null || mPhoneBrowserEngineClass == null) {
            XposedLog.w(TAG, "Phone SearchBox list provider classes not found");
            return;
        }

        try {
            hookAllMethods(mPhoneProviderClass, "initEngineSet", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    prunePhoneProviderEngineSet(param.getThisObject());
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox initEngineSet hook skipped", e);
        }

        try {
            hookAllMethods(mPhoneEngineSetClass, "initialize", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    prunePhoneEngineSet(param.getResult());
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox SearchEngineSet.initialize hook skipped", e);
        }

        try {
            hookAllMethods(mPhoneProviderClass, "getEngineSet", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    prunePhoneEngineSet(param.getResult());
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox getEngineSet hook skipped", e);
        }

        try {
            hookAllMethods(mPhoneProviderClass, "getSearchEngines", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    prunePhoneProviderEngineSet(param.getThisObject());
                    param.setResult(new String[]{BING_NAME});
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox getSearchEngines hook skipped", e);
        }

        try {
            hookAllMethods(mPhoneProviderClass, "getSearchEngineList", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    prunePhoneProviderEngineSet(param.getThisObject());
                    Object result = param.getResult();
                    if (result instanceof List && !((List<?>) result).isEmpty()) {
                        Object bean = ((List<?>) result).get(0);
                        setField(bean, "mLabel", BING_TITLE_ZH);
                        setField(bean, "mName", BING_NAME);
                        setField(bean, "mIsCheck", true);
                        setField(bean, "isCustom", false);
                        param.setResult(createSingleList(bean));
                    }
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox getSearchEngineList hook skipped", e);
        }

        try {
            hookAllMethods(mPhoneProviderClass, "getSearchEngine", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(BING_NAME);
                }
            });
            hookAllMethods(mPhoneProviderClass, "getItemTitle", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(BING_TITLE_ZH);
                }
            });
            XposedLog.i(TAG, "Hooked Phone SearchBox list provider");
        } catch (Exception e) {
            XposedLog.w(TAG, "Phone SearchBox title/current engine hook skipped", e);
        }
    }

    private void prunePhoneProviderEngineSet(Object provider) {
        if (provider == null) return;
        try {
            Field engineSetField = findField(provider.getClass(), "mEngineSet");
            if (engineSetField == null) return;
            engineSetField.setAccessible(true);
            prunePhoneEngineSet(engineSetField.get(provider));
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to prune Phone SearchBox provider engine set", e);
        }
    }

    private void prunePhoneEngineSet(Object engineSet) {
        if (engineSet == null) return;
        Object bingEngine = createPhoneBrowserBingEngine();
        if (bingEngine == null) return;
        replacePhoneEngineMap(engineSet, "searchBox", bingEngine);
        replacePhoneEngineMap(engineSet, "searchFound", bingEngine);
        replacePhoneEngineMap(engineSet, "hotRank", bingEngine);
        replacePhoneEngineMap(engineSet, "boxPreset", bingEngine);
        replacePhoneEngineMap(engineSet, "underBoxPreset", bingEngine);
        replacePhoneEngineMap(engineSet, "searchWidget", bingEngine);
        replaceDefaultSearchEngineMap(engineSet);
    }

    @SuppressWarnings("unchecked")
    private void replacePhoneEngineMap(Object engineSet, String fieldName, Object bingEngine) {
        try {
            Field field = findField(engineSet.getClass(), fieldName);
            if (field == null) return;
            field.setAccessible(true);
            Object value = field.get(engineSet);
            if (value instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) value;
                map.clear();
                map.put(BING_NAME, bingEngine);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void replaceDefaultSearchEngineMap(Object engineSet) {
        try {
            Field field = findField(engineSet.getClass(), "defaultSearchEngine");
            if (field == null) return;
            field.setAccessible(true);
            Object value = field.get(engineSet);
            if (value instanceof Map) {
                Map<Object, Object> map = (Map<Object, Object>) value;
                if (map.isEmpty()) {
                    map.put("browserSearchBox", BING_NAME);
                } else {
                    for (Object key : new ArrayList<>(map.keySet())) {
                        map.put(key, BING_NAME);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private Object createPhoneBrowserBingEngine() {
        if (mPhoneBrowserEngineClass == null) return null;
        try {
            Object engine = mPhoneBrowserEngineClass.getDeclaredConstructor().newInstance();
            setByMethodOrField(engine, "SearchEngineName", "searchEngineName", BING_NAME);
            setByMethodOrField(engine, "ShowIcon", "showIcon", BING_SHOW_ICON);
            setByMethodOrField(engine, "SearchUrl", "searchUrl", BING_SEARCH_URL);
            setByMethodOrField(engine, "IconUrl", "iconUrl", BING_ICON_URL);
            setByMethodOrField(engine, "ChannelNo", "channelNo", BING_CHANNEL);
            setByMethodOrField(engine, "Title_zh_CN", "title_zh_CN", BING_TITLE_ZH);
            setByMethodOrField(engine, "Title_zh_TW", "title_zh_TW", BING_TITLE_TW);
            setByMethodOrField(engine, "Title_en_US", "title_en_US", BING_TITLE_US);
            setByMethodOrField(engine, "Title_bo_CN", "title_bo_CN", BING_TITLE_BO);
            setByMethodOrField(engine, "Title_ug_CN", "title_ug_CN", BING_TITLE_UG);
            return engine;
        } catch (Exception e) {
            XposedLog.w(TAG, "Failed to create Phone browser SearchBox Bing engine", e);
            return null;
        }
    }

    private void setByMethodOrField(Object target, String setterSuffix, String fieldName, Object value) {
        try {
            Method method = target.getClass().getDeclaredMethod("set" + setterSuffix, value instanceof Boolean ? boolean.class : String.class);
            method.setAccessible(true);
            method.invoke(target, value);
            return;
        } catch (Exception ignored) {}
        setField(target, fieldName, value);
    }

    private boolean initPadSearchBoxHook() {
        mPadProviderClass = findClassIfExists(PAD_PROVIDER_CLASS);
        if (mPadProviderClass == null) {
            XposedLog.w(TAG, "Pad SearchBox provider class not found: " + PAD_PROVIDER_CLASS);
            return false;
        }

        XposedLog.i(TAG, "Found Pad SearchBox provider class: " + mPadProviderClass.getName());
        findPadConstructors();
        hookPadDataProvider();
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

    private void hookPadDataProvider() {
        try {
            hookAllMethods(mPadProviderClass, "initData", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    replacePadEngineData(param.getThisObject());
                }
            });
        } catch (Exception e) {
            XposedLog.w(TAG, "Pad SearchBox initData hook skipped", e);
        }

        try {
            hookAllMethods(mPadProviderClass, "getSearchEngines", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object bingEngine = createPadBingEngine();
                    if (bingEngine == null) {
                        Object result = param.getResult();
                        if (result instanceof List && !((List<?>) result).isEmpty()) {
                            bingEngine = ((List<?>) result).get(0);
                            modifyPadEngine(bingEngine);
                        }
                    }
                    if (bingEngine != null) {
                        param.setResult(createSingleList(bingEngine));
                    }
                }
            });
            XposedLog.i(TAG, "Hooked Pad SearchBox getSearchEngines()");
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook Pad SearchBox getSearchEngines", e);
        }
    }

    private Object createPhoneBingEngine() {
        if (mPhoneEngineConstructor == null) return null;
        try {
            return mPhoneEngineConstructor.newInstance(
                BING_NAME,
                BING_CHANNEL,
                BING_SHOW_ICON,
                BING_SEARCH_URL,
                BING_ICON_URL,
                BING_TITLE_ZH,
                BING_TITLE_TW,
                BING_TITLE_US,
                BING_TITLE_BO,
                BING_TITLE_UG
            );
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to create Phone SearchBox Bing engine", e);
            return null;
        }
    }

    private Object createPadBingEngine() {
        if (mPadEngineItemConstructor == null) return null;
        try {
            List<Object> searchUris = new ArrayList<>(1);
            if (mPadSearchUriConstructor != null) {
                searchUris.add(mPadSearchUriConstructor.newInstance("default", BING_SEARCH_URL));
            }
            Object desktop = mPadDesktopConstructor == null ? null : mPadDesktopConstructor.newInstance(BING_SEARCH_URL, 1);
            return mPadEngineItemConstructor.newInstance(
                BING_TITLE_ZH,
                BING_NAME,
                BING_SEARCH_URL,
                searchUris,
                "",
                BING_ICON_URL,
                "",
                "q",
                BING_TITLE_ZH,
                true,
                true,
                new ArrayList<>(),
                desktop
            );
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to create Pad SearchBox Bing engine", e);
            return null;
        }
    }

    private void replacePadEngineData(Object provider) {
        if (provider == null) return;
        Object bingEngine = createPadBingEngine();
        if (bingEngine == null) return;

        try {
            Field engineDataField = findField(provider.getClass(), "mEngineData");
            if (engineDataField == null) return;
            engineDataField.setAccessible(true);
            Object engineData = engineDataField.get(provider);
            if (engineData == null) return;
            Field searchEngineDatasField = findField(engineData.getClass(), "searchEngineDatas");
            if (searchEngineDatasField == null) return;
            searchEngineDatasField.setAccessible(true);
            searchEngineDatasField.set(engineData, createSingleList(bingEngine));
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to replace Pad SearchBox engine data", e);
        }
    }

    private List<Object> createSingleList(Object item) {
        List<Object> list = new ArrayList<>(1);
        if (item != null) {
            list.add(item);
        }
        return list;
    }

    private void modifyPadEngine(Object engine) {
        if (engine == null) return;

        List<Object> searchUris = new ArrayList<>(1);
        try {
            if (mPadSearchUriConstructor != null) {
                searchUris.add(mPadSearchUriConstructor.newInstance("default", BING_SEARCH_URL));
            }
        } catch (Exception ignored) {}

        Object desktop = null;
        try {
            if (mPadDesktopConstructor != null) {
                desktop = mPadDesktopConstructor.newInstance(BING_SEARCH_URL, 1);
            }
        } catch (Exception ignored) {}

        setField(engine, "title_zh_CN", BING_TITLE_ZH);
        setField(engine, "key", BING_NAME);
        setField(engine, "searchUriDefault", BING_SEARCH_URL);
        setField(engine, "searchUri", searchUris);
        setField(engine, "extra", "");
        setField(engine, "icon", BING_ICON_URL);
        setField(engine, "iconHash", "");
        setField(engine, "queryParameterKey", "q");
        setField(engine, "label", BING_TITLE_ZH);
        setField(engine, "isDefault", true);
        setField(engine, "isRecommended", true);
        setField(engine, "localOpen", new ArrayList<>());
        setField(engine, "desktop", desktop);
    }

    private void setField(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
            }
        } catch (Exception ignored) {}
    }

    private Field findField(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return findField(clazz.getSuperclass(), fieldName);
        }
    }
}
