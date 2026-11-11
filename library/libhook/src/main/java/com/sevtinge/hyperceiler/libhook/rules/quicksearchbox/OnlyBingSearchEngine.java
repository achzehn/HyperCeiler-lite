/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
*/
package com.sevtinge.hyperceiler.libhook.rules.quicksearchbox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.R;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.utils.api.BitmapUtils;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.AppsTool;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.lingqiqi5211.ezhooktool.xposed.EzXposed;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * 快速搜索（com.android.quicksearchbox）搜索引擎配置篡改：只保留 Bing。
 * <p>
 * 同时覆盖手机版与 Pad 版两套完全不同的实现：
 * <ul>
 * <li>手机版（13.1.0.09179）：搜索引擎配置由混淆类 SearchEngineSet（toString 含
 * "SearchEngineSet{searchBox="，即 E2.a）持有，其 5 参构造为
 * (searchBox 场景 j, hotRank 场景 j, defaultSearchEngineMap, sceneSearchEngineMap,
 * clientSceneInfo)。在构造前把两个场景的引擎表重建为仅含 Bing，并同步
 * defaultSearchEngineMap / sceneSearchEngineMap；构造后调用宿主自身的
 * f.b(context).k("bing") 切换当前引擎。引擎条目类 h 为 10 参构造；其图标位图字段
 * 直接写入模块内置 Bing 图标（showIcon 置真，iconUrl 留空以避免 Glide 覆盖）。</li>
 * <li>Pad 版（10.0.0.96）：数据源为 com.android.quicksearchbox.xiaomi.
 * SearchEngineDataProvider，引擎装配在 SearchEngineHelper.updateEngines。宿主硬编码
 * 黑名单 {"bing"} 且 needFilter() 大多为真，会过滤掉 label=="bing" 的引擎，这里
 * 拦截 needFilter() 恒返回 false，并让 getSearchEngines()/initData() 只产出单个
 * Bing 条目（13 参 SearchEngineItem，iconBitmap 写模块图标）。</li>
 * </ul>
 */
public class OnlyBingSearchEngine extends BaseHook {

    private static final String BING = "bing";
    private static final String BING_TITLE = "必应";
    private static final String BING_SEARCH_URL = "https://cn.bing.com/search?q={searchTerms}";
    private static final String BING_QUERY_KEY = "q";

    // 手机版：引擎条目 h（10 参构造）、引擎管理单例 f（f.b(context).k(name) 切换引擎）
    private static final String PHONE_ENGINE_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.h";
    private static final String PHONE_MANAGER_CLASS = "com.android.quicksearchbox.xiaomi.searchengine.f";
    private static final String SCENE_SEARCH_BOX = "globalSearchSearchBox";
    private static final String SCENE_HOT_LIST = "globalSearchHotList";

    // Pad 版：数据源 DataProvider、引擎装配管理器 SearchEngineHelper
    private static final String PAD_PROVIDER_CLASS = "com.android.quicksearchbox.xiaomi.SearchEngineDataProvider";
    private static final String PAD_ENGINE_ITEM_CLASS = PAD_PROVIDER_CLASS + "$SearchEngineItem";
    private static final String PAD_SEARCH_URI_CLASS = PAD_PROVIDER_CLASS + "$SearchUri";
    private static final String PAD_DESKTOP_CLASS = PAD_PROVIDER_CLASS + "$Desktop";
    private static final String PAD_SEARCH_HELPER_CLASS = "com.android.quicksearchbox.SearchEngineHelper";

    private Class<?> mPhoneEngineClass;
    private Constructor<?> mPhoneEngineConstructor;
    private Class<?> mDexPhoneEngineSetClass;

    private Constructor<?> mPadEngineItemConstructor;
    private Constructor<?> mPadSearchUriConstructor;
    private Constructor<?> mPadDesktopConstructor;

    private Bitmap mBingIcon;

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
        XposedLog.i(TAG, "DexKit phone_search_engine_set: "
            + (mDexPhoneEngineSetClass != null ? mDexPhoneEngineSetClass.getName() : "null"));
        return true;
    }

    @Override
    public void init() {
        boolean hooked = false;
        hooked |= initPhoneHook();
        hooked |= initPadHook();

        if (hooked) {
            XposedLog.i(TAG, "QuickSearchBox only bing search engine ready");
        } else {
            XposedLog.e(TAG, "QuickSearchBox search engine classes not found");
        }
    }

    // ==================== 手机版 ====================

    private boolean initPhoneHook() {
        mPhoneEngineClass = findClassIfExists(PHONE_ENGINE_CLASS);
        if (mPhoneEngineClass != null) {
            findPhoneEngineConstructor();
        } else {
            XposedLog.w(TAG, "Phone search engine class not found: " + PHONE_ENGINE_CLASS);
        }

        boolean hooked = hookPhoneEngineSet();
        hooked |= hookPhoneCurrentEngine();
        return hooked;
    }

    private void findPhoneEngineConstructor() {
        try {
            for (Constructor<?> constructor : mPhoneEngineClass.getDeclaredConstructors()) {
                if (constructor.getParameterTypes().length == 10) {
                    mPhoneEngineConstructor = constructor;
                    mPhoneEngineConstructor.setAccessible(true);
                    XposedLog.i(TAG, "Found Phone search engine 10-param constructor");
                    return;
                }
            }
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to find Phone search engine constructor", e);
        }
    }

    /**
     * Hook SearchEngineSet 构造：把两个场景的引擎表重建为仅含 Bing，
     * defaultSearchEngineMap / sceneSearchEngineMap 全部指向 Bing。
     */
    private boolean hookPhoneEngineSet() {
        if (mDexPhoneEngineSetClass == null) {
            XposedLog.w(TAG, "DexKit Phone SearchEngineSet not found");
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
                        applyPhoneBingOnly(args);
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Rebuild phone search engine set error", t);
                    }
                }

                @Override
                public void after(HookParam param) {
                    try {
                        if (param.getArgs().length == 5) {
                            switchPhoneCurrentEngine();
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Switch phone current engine error", t);
                    }
                }
            });
            XposedLog.i(TAG, "Hooked Phone SearchEngineSet constructors");
            return true;
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to hook Phone SearchEngineSet", e);
            return false;
        }
    }

    private void applyPhoneBingOnly(Object[] args) {
        Object bing = createPhoneEngine();
        if (bing == null) {
            return;
        }
        // args[0] searchBox 场景、args[1] hotRank 场景：清空引擎表，只留 Bing
        trimPhoneScene(args[0], bing);
        trimPhoneScene(args[1], bing);
        // args[2] defaultSearchEngineMap（场景 -> 引擎名）
        fillPhoneDefaultMap(args[2]);
        // args[3] sceneSearchEngineMap（场景 -> {引擎名 -> 条目}）
        fillPhoneSceneMap(args[3], bing);
    }

    /** 场景实体 j：a 为 LinkedHashMap（引擎名->条目），b 为引擎 List，两者都要只留 Bing */
    private void trimPhoneScene(Object scene, Object bing) {
        if (scene == null) {
            return;
        }
        Field mapField = findFirstFieldByType(scene.getClass(), Map.class);
        if (mapField != null) {
            try {
                mapField.setAccessible(true);
                Object value = mapField.get(scene);
                if (value instanceof Map) {
                    Map<Object, Object> map = (Map<Object, Object>) value;
                    map.clear();
                    map.put(BING, bing);
                }
            } catch (Throwable t) {
                XposedLog.w(TAG, "Trim phone scene map error", t);
            }
        }

        Field listField = findFirstFieldByType(scene.getClass(), List.class);
        if (listField != null) {
            try {
                listField.setAccessible(true);
                Object value = listField.get(scene);
                if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    list.clear();
                    list.add(bing);
                }
            } catch (Throwable t) {
                XposedLog.w(TAG, "Trim phone scene list error", t);
            }
        }
    }

    private void fillPhoneDefaultMap(Object mapObj) {
        if (!(mapObj instanceof Map)) {
            return;
        }
        try {
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            if (map.isEmpty()) {
                map.put(SCENE_SEARCH_BOX, BING);
                map.put(SCENE_HOT_LIST, BING);
                return;
            }
            for (Object key : new ArrayList<>(map.keySet())) {
                map.put(key, BING);
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Fill phone default engine map error", t);
        }
    }

    private void fillPhoneSceneMap(Object mapObj, Object bing) {
        if (!(mapObj instanceof Map)) {
            return;
        }
        try {
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            if (map.isEmpty()) {
                map.put(SCENE_SEARCH_BOX, createSingleEngineMap(bing));
                map.put(SCENE_HOT_LIST, createSingleEngineMap(bing));
                return;
            }
            for (Map.Entry<Object, Object> entry : new ArrayList<>(map.entrySet())) {
                if (entry.getValue() instanceof Map) {
                    Map<Object, Object> inner = (Map<Object, Object>) entry.getValue();
                    inner.clear();
                    inner.put(BING, bing);
                } else {
                    map.put(entry.getKey(), createSingleEngineMap(bing));
                }
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Fill phone scene engine map error", t);
        }
    }

    private LinkedHashMap<Object, Object> createSingleEngineMap(Object bing) {
        LinkedHashMap<Object, Object> map = new LinkedHashMap<>();
        map.put(BING, bing);
        return map;
    }

    /**
     * 构建 Bing 引擎条目 h（10 参：name, channelNo, showIcon, searchUrl, iconUrl,
     * title_zh_CN, title_zh_TW, title_en_US, title_bo_CN, title_ug_CN）。
     * iconUrl 留空（Glide 加载失败不会覆盖已写入的位图），图标直接写入位图字段。
     */
    private Object createPhoneEngine() {
        if (mPhoneEngineConstructor == null) {
            return null;
        }
        try {
            Object engine = mPhoneEngineConstructor.newInstance(
                BING,
                BING,
                true,
                BING_SEARCH_URL,
                "",
                BING_TITLE,
                BING_TITLE,
                "Bing",
                BING_TITLE,
                BING_TITLE
            );
            setEngineBitmap(engine);
            return engine;
        } catch (Throwable t) {
            XposedLog.e(TAG, "Failed to create Phone Bing engine", t);
            return null;
        }
    }

    private void switchPhoneCurrentEngine() {
        try {
            Class<?> managerClass = findClassIfExists(PHONE_MANAGER_CLASS);
            Context context = EzXposed.getAppContext();
            if (managerClass == null || context == null) {
                return;
            }
            Object manager = managerClass.getMethod("b", Context.class).invoke(null, context);
            if (manager == null) {
                return;
            }
            managerClass.getMethod("k", String.class).invoke(manager, BING);
        } catch (Throwable t) {
            XposedLog.w(TAG, "Switch phone current engine failed", t);
        }
    }

    /** 已保存的当前引擎可能是 baidu 等，强制读取为 Bing，避免回退到已被移除的引擎 */
    private boolean hookPhoneCurrentEngine() {
        try {
            hookAllMethods(PHONE_MANAGER_CLASS, "d", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(BING);
                }
            });
            XposedLog.i(TAG, "Hooked Phone current engine -> bing");
            return true;
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook Phone current engine", t);
            return false;
        }
    }

    // ==================== Pad 版 ====================

    private boolean initPadHook() {
        Class<?> providerClass = findClassIfExists(PAD_PROVIDER_CLASS);
        if (providerClass == null) {
            XposedLog.w(TAG, "Pad provider class not found: " + PAD_PROVIDER_CLASS);
            return false;
        }
        findPadConstructors();

        boolean hooked = false;
        try {
            hookAllMethods(providerClass, "getSearchEngines", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    keepSinglePadEngine(param);
                }
            });
            hooked = true;
        } catch (Throwable t) {
            XposedLog.e(TAG, "Failed to hook Pad getSearchEngines", t);
        }

        try {
            hookAllMethods(providerClass, "initData", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    replacePadEngineData(param.getThisObject());
                }
            });
            hooked = true;
        } catch (Throwable t) {
            XposedLog.w(TAG, "Pad initData hook skipped", t);
        }

        // 宿主 SearchEngineHelper.sBlackList 硬编码 "bing"，needFilter() 为 true 时
        // label=="bing" 的引擎会在 updateEngines 中被剔除并回落百度：恒返回 false。
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
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to hook Pad SearchEngineHelper.needFilter", t);
        }
        return hooked;
    }

    private void findPadConstructors() {
        try {
            Class<?> itemClass = findClassIfExists(PAD_ENGINE_ITEM_CLASS);
            if (itemClass != null) {
                for (Constructor<?> constructor : itemClass.getDeclaredConstructors()) {
                    if (constructor.getParameterTypes().length == 13) {
                        mPadEngineItemConstructor = constructor;
                        mPadEngineItemConstructor.setAccessible(true);
                        break;
                    }
                }
            }

            Class<?> searchUriClass = findClassIfExists(PAD_SEARCH_URI_CLASS);
            if (searchUriClass != null) {
                mPadSearchUriConstructor = searchUriClass.getDeclaredConstructor(String.class, String.class);
                mPadSearchUriConstructor.setAccessible(true);
            }

            Class<?> desktopClass = findClassIfExists(PAD_DESKTOP_CLASS);
            if (desktopClass != null) {
                mPadDesktopConstructor = desktopClass.getDeclaredConstructor(String.class, int.class);
                mPadDesktopConstructor.setAccessible(true);
            }
        } catch (Throwable t) {
            XposedLog.e(TAG, "Failed to find Pad constructors", t);
        }
    }

    /** 引擎列表强制只保留单个 Bing（getSearchEngines 的返回值） */
    private void keepSinglePadEngine(HookParam param) {
        Object engine = createPadEngine();
        if (engine != null) {
            param.setResult(createSingleList(engine));
        }
    }

    /**
     * 构建 Bing 条目 SearchEngineItem（13 参：title_zh_CN, key, searchUriDefault,
     * searchUri, extra, icon, iconHash, queryParameterKey, label, isDefault,
     * isRecommended, localOpen, desktop）。
     */
    private Object createPadEngine() {
        if (mPadEngineItemConstructor == null) {
            return null;
        }
        try {
            List<Object> searchUris = new ArrayList<>(1);
            if (mPadSearchUriConstructor != null) {
                searchUris.add(mPadSearchUriConstructor.newInstance("default", BING_SEARCH_URL));
            }
            Object desktop = mPadDesktopConstructor == null
                ? null : mPadDesktopConstructor.newInstance(BING_SEARCH_URL, 1);
            Object engine = mPadEngineItemConstructor.newInstance(
                BING_TITLE,
                BING,
                BING_SEARCH_URL,
                searchUris,
                "",
                "",
                "",
                BING_QUERY_KEY,
                BING,
                true,
                true,
                new ArrayList<>(),
                desktop
            );
            setEngineBitmap(engine);
            return engine;
        } catch (Throwable t) {
            XposedLog.e(TAG, "Failed to create Pad Bing engine", t);
            return null;
        }
    }

    /** 兜底：数据初始化完成后把 DataProvider 内的引擎数据替换为单个 Bing */
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
            Field datasField = findField(engineData.getClass(), "searchEngineDatas");
            if (datasField == null) {
                return;
            }
            datasField.setAccessible(true);
            Object datas = datasField.get(engineData);
            if (datas instanceof List) {
                List<Object> list = (List<Object>) datas;
                list.clear();
                list.add(engine);
            } else {
                datasField.set(engineData, createSingleList(engine));
            }
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to replace Pad engine data", t);
        }
    }

    // ==================== 通用工具 ====================

    /** 把模块内置 Bing 图标写入引擎条目的位图字段（h.f / SearchEngineItem.iconBitmap） */
    private void setEngineBitmap(Object engine) {
        if (engine == null) {
            return;
        }
        Bitmap icon = getBingIcon();
        if (icon == null) {
            return;
        }
        Field field = findFirstFieldByType(engine.getClass(), Bitmap.class);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(engine, icon);
        } catch (Throwable t) {
            XposedLog.w(TAG, "Failed to set engine bitmap", t);
        }
    }

    private Bitmap getBingIcon() {
        if (mBingIcon != null) {
            return mBingIcon;
        }
        Context context = EzXposed.getAppContext();
        if (context == null) {
            return null;
        }
        Drawable drawable = AppsTool.getModuleRes(context)
            .getDrawable(R.drawable.ic_search_engine_bing, context.getTheme());
        if (drawable == null) {
            return null;
        }
        mBingIcon = BitmapUtils.drawableToBitmap(drawable);
        return mBingIcon;
    }

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
}