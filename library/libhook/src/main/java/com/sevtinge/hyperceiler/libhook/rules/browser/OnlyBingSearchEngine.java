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
package com.sevtinge.hyperceiler.libhook.rules.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.R;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.utils.api.BitmapUtils;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.AppsTool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.github.lingqiqi5211.ezhooktool.xposed.EzXposed;
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class OnlyBingSearchEngine extends BaseHook {

    private static final String BING = "bing";
    private static final String BING_SEARCH_URL = "https://cn.bing.com/search?q={searchTerms}";

    private static final String[] SCENES = {
        "browserSearchBox",
        "browserSearchFound",
        "browserWholeNetworkHotList",
        "browserBoxPreset",
        "browserUnderTheBoxPreSet",
        "browserSearchWidget"
    };

    private Bitmap mBingIcon;

    @Override
    public void init() {
        // 本地 raw 配置与云控下发配置最终都会走 SearchEngineSet.initialize，
        // 在这里统一改写为仅含 Bing 的配置，无需改动落地文件。
        findAndHookMethod(
            "com.android.browser.search.SearchEngineDataProvider$SearchEngineSet",
            "initialize",
            "com.android.browser.search.SearchEnginesEntity",
            new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        applyBingOnly(param.getArgs()[0]);
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Failed to apply bing only search engine", t);
                    }
                }
            }
        );

        // 已保存的当前搜索引擎可能是 baidu 等，强制读取为 bing，避免回退到已被移除的引擎。
        findAndHookMethod(
            "com.android.browser.BrowserSettings",
            "getSearchEngineName",
            new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(BING);
                }
            }
        );

        // 上层的 SearchEngineSettings 在设置服务未就绪时会兜底返回 baidu，
        // 该值会传到 getSearchEngineContentByScene 并触发空指针，这里一并纠正。
        findAndHookMethod(
            "com.android.browser.search.interaction.settings.SearchModuleSettings",
            "getSearchEngineName",
            new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(BING);
                }
            }
        );

        // 浏览器内置资源中没有 bing 图标，这里直接返回模块内置图标。
        findAndHookMethod(
            "com.android.browser.search.SearchEngineDataProvider",
            "getIconByType",
            "java.lang.String",
            "com.android.browser.search.SearchEngineDataProvider$IconType",
            new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    if (!BING.equals(args[0]) || !"SEARCH_ENGINE".equals(String.valueOf(args[1]))) {
                        return;
                    }
                    Bitmap icon = getBingIcon();
                    if (icon != null) {
                        param.setResult(icon);
                    }
                }
            }
        );
    }

    private void applyBingOnly(Object entity) {
        if (entity == null) {
            return;
        }
        Class<?> engineClass = findClassIfExists("com.android.browser.search.SearchEnginesEntity$SearchEngine");
        Class<?> sceneClass = findClassIfExists("com.android.browser.search.SearchEnginesEntity$SearchEngineScene");
        if (engineClass == null || sceneClass == null) {
            return;
        }

        Object bing = newInstance(engineClass);
        callMethod(bing, "setSearchEngineName", BING);
        callMethod(bing, "setSearchUrl", BING_SEARCH_URL);
        callMethod(bing, "setShowIcon", true);
        callMethod(bing, "setIconUrl", "");
        callMethod(bing, "setChannelNo", "");
        callMethod(bing, "setTitle_zh_CN", "必应");
        callMethod(bing, "setTitle_zh_TW", "Bing");
        callMethod(bing, "setTitle_en_US", "Bing");

        ConcurrentHashMap<String, Object> sceneMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> defaultMap = new ConcurrentHashMap<>();
        for (String sceneId : SCENES) {
            Object scene = newInstance(sceneClass);
            setIntField(scene, "homepageSearchEngineCount", 1);
            setLongField(scene, "searchEngineSceneId", 0L);
            List<Object> engines = new ArrayList<>();
            engines.add(bing);
            setObjectField(scene, "searchEngines", engines);
            sceneMap.put(sceneId, scene);
            defaultMap.put(sceneId, BING);
        }
        setObjectField(entity, "searchEngineSceneMap", sceneMap);
        setObjectField(entity, "defaultSearchEngineMap", defaultMap);
    }

    private Bitmap getBingIcon() {
        if (mBingIcon != null) {
            return mBingIcon;
        }
        Context context = EzXposed.getAppContext();
        if (context == null) {
            return null;
        }
        Drawable drawable = AppsTool.getModuleRes(context).getDrawable(R.drawable.ic_search_engine_bing, context.getTheme());
        if (drawable == null) {
            return null;
        }
        mBingIcon = BitmapUtils.drawableToBitmap(drawable);
        return mBingIcon;
    }
}
