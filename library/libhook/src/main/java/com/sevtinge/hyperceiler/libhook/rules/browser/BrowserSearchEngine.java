/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import java.lang.reflect.Field;
import java.util.Map;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class BrowserSearchEngine extends BaseHook {

    private static final String BING_URL = "https://www.bing.com/search?q={searchTerms}";
    private static final String BING_ICON_URL = "https://www.bing.com/favicon.ico";

    @Override
    public void init() {
        hookSearchEnginesEntitySearchEngineGetSearchUrl();
        hookSearchEngineItemDeserialize();
        hookSearchEngineDataProviderGetInstance();
        hookSearchEngineSetInitialize();
    }

    private void hookSearchEnginesEntitySearchEngineGetSearchUrl() {
        Class<?> searchEngineClass = findClassIfExists("com.android.browser.search.SearchEnginesEntity$SearchEngine");
        if (searchEngineClass != null) {
            hookAllMethods(searchEngineClass, "getSearchUrl", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object result = param.getResult();
                    if (result instanceof String) {
                        param.setResult(BING_URL);
                    }
                }
            });
        }
    }

    private void hookSearchEngineItemDeserialize() {
        Class<?> searchEngineItemClass = findClassIfExists("com.android.browser.search.SearchEngineDataProvider.SearchEngineItem");
        if (searchEngineItemClass != null) {
            hookAllMethods(searchEngineItemClass, "deserialize", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object result = param.getResult();
                    if (result != null) {
                        setEngineFields(result);
                    }
                }
            });
        }
    }

    private void hookSearchEngineDataProviderGetInstance() {
        Class<?> dataProviderClass = findClassIfExists("com.android.browser.search.SearchEngineDataProvider");
        if (dataProviderClass != null) {
            hookAllMethods(dataProviderClass, "getInstance", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object result = param.getResult();
                    if (result != null) {
                        replaceAllSearchUrls(result);
                    }
                }
            });
        }
    }

    private void hookSearchEngineSetInitialize() {
        Class<?> searchEngineSetClass = findClassIfExists("com.android.browser.search.SearchEngineDataProvider.SearchEngineSet");
        if (searchEngineSetClass != null) {
            hookAllMethods(searchEngineSetClass, "initialize", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object result = param.getResult();
                    if (result != null) {
                        replaceAllSearchUrlsInSet(result);
                    }
                }
            });
        }
    }

    private void replaceAllSearchUrls(Object dataProvider) {
        try {
            Field engineSetField = dataProvider.getClass().getDeclaredField("mEngineSet");
            engineSetField.setAccessible(true);
            Object engineSet = engineSetField.get(dataProvider);
            if (engineSet != null) {
                replaceAllSearchUrlsInSet(engineSet);
            }
        } catch (Exception ignored) {}
    }

    private void replaceAllSearchUrlsInSet(Object engineSet) {
        try {
            Field searchBoxField = engineSet.getClass().getDeclaredField("searchBox");
            searchBoxField.setAccessible(true);
            Object searchBox = searchBoxField.get(engineSet);
            
            if (searchBox instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> searchBoxMap = (Map<String, Object>) searchBox;
                
                for (Object engine : searchBoxMap.values()) {
                    setEngineFields(engine);
                }
            }
        } catch (Exception ignored) {}
    }

    private void setSearchUrlField(Object engine, String url) {
        try {
            Field searchUrlField = engine.getClass().getDeclaredField("searchUrl");
            searchUrlField.setAccessible(true);
            searchUrlField.set(engine, url);
        } catch (Exception e1) {
            try {
                Field searchUriField = engine.getClass().getDeclaredField("search_uri");
                searchUriField.setAccessible(true);
                searchUriField.set(engine, url);
            } catch (Exception e2) {
                try {
                    Field[] fields = engine.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        field.setAccessible(true);
                        if (field.getName().toLowerCase().contains("search") && 
                            field.getName().toLowerCase().contains("url")) {
                            field.set(engine, url);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void setIconUrlField(Object engine, String iconUrl) {
        try {
            Field iconUrlField = engine.getClass().getDeclaredField("iconUrl");
            iconUrlField.setAccessible(true);
            iconUrlField.set(engine, iconUrl);
        } catch (Exception e) {
            try {
                Field[] fields = engine.getClass().getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.getName().toLowerCase().contains("icon") && 
                        field.getName().toLowerCase().contains("url")) {
                        field.set(engine, iconUrl);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void setEngineFields(Object engine) {
        setSearchUrlField(engine, BING_URL);
        setIconUrlField(engine, BING_ICON_URL);
    }
}