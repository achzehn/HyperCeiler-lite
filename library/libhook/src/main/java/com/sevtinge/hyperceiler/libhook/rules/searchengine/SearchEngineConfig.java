/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.sevtinge.hyperceiler.libhook.rules.searchengine;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.sevtinge.hyperceiler.common.log.XposedLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索引擎配置（快速搜索 / 浏览器共用）。
 * <p>
 * 配置即代码内置的 JSON 常量，内容格式与目标应用未 hook 时自身读取的
 * searchengine.json 完全一致（defaultSearchEngineMap + searchEngineSceneMap
 * 下场景的 searchEngines 数组，字段 searchEngineName/searchUrl/iconUrl/title_* 等，
 * 参考真实抓包样例）。Hook 的目标就是 searchEngines 中的引擎配置：
 * 两端 hook 将 searchEngines 中的条目注入宿主，全程内存操作，不落地本地存储。
 * <p>
 * 修改内置引擎：直接编辑 {@link #CONFIG_JSON}（增删 searchEngines 条目或调整
 * defaultSearchEngineMap 默认引擎即可）。模块设置中的「显示名/搜索链接/图标」
 * 为可选覆盖项，仅当值非空且不同于界面默认值时生效。
 */
public final class SearchEngineConfig {

    private static final String TAG = "SearchEngineConfig";

    /** 落地场景名（与宿主原生配置一致） */
    public static final String SCENE_BROWSER_SEARCH_BOX = "browserSearchBox";

    /**
     * 内置搜索引擎配置（浏览器原生 searchengine.json 格式）。
     * 默认引擎：必应；如需更换引擎，替换 searchEngines 数组内容即可。
     */
    private static final String CONFIG_JSON = "{\n"
        + "  \"defaultSearchEngineMap\": {\n"
        + "    \"browserSearchBox\": \"bing\"\n"
        + "  },\n"
        + "  \"searchEngineSceneMap\": {\n"
        + "    \"browserSearchBox\": {\n"
        + "      \"searchEngineSceneId\": 0,\n"
        + "      \"homepageSearchEngineCount\": 1,\n"
        + "      \"resetSearchEngineData\": false,\n"
        + "      \"searchEngines\": [\n"
        + "        {\n"
        + "          \"searchEngineName\": \"bing\",\n"
        + "          \"showIcon\": true,\n"
        + "          \"searchUrl\": \"https://www.bing.com/search?q={searchTerms}\",\n"
        + "          \"iconUrl\": \"https://cn.bing.com/favicon.ico\",\n"
        + "          \"title_zh_CN\": \"必应搜索\",\n"
        + "          \"title_zh_TW\": \"必應搜索\",\n"
        + "          \"title_en_US\": \"Bing Search\",\n"
        + "          \"title_bo_CN\": \"Bing Search\",\n"
        + "          \"title_ug_CN\": \"Bing Search\",\n"
        + "          \"keyword\": \"bing\",\n"
        + "          \"channelNo\": \"bing\"\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  }\n"
        + "}";

    private static volatile Config sConfig;

    private SearchEngineConfig() {
    }

    // ==================== 条目模型（双端通用中间表示） ====================

    /** searchEngines 数组中的一个引擎条目 */
    public static final class Engine {
        public String name;      // searchEngineName
        public String url;       // searchUrl
        public String icon;      // iconUrl
        public String titleZh;   // title_zh_CN
        public String titleTw;   // title_zh_TW
        public String titleEn;   // title_en_US
        public String keyword;
        public String channelNo; // 渠道号
        public boolean showIcon = true;
    }

    /** 已解析的配置 */
    public static final class Config {
        public final List<Engine> engines;
        public final String defaultName;

        Config(List<Engine> engines, String defaultName) {
            this.engines = engines;
            this.defaultName = defaultName;
        }

        /** 生效引擎（defaultSearchEngineMap 指定或列表首个） */
        @Nullable
        public Engine primary() {
            Engine target = findByName(defaultName);
            if (target == null && engines != null && !engines.isEmpty()) {
                target = engines.get(0);
            }
            return target;
        }

        /** 按名称精确匹配引擎，未命中返回 null */
        @Nullable
        public Engine findByName(String name) {
            if (TextUtils.isEmpty(name) || engines == null) {
                return null;
            }
            for (Engine engine : engines) {
                if (engine != null && name.equalsIgnoreCase(engine.name)) {
                    return engine;
                }
            }
            return null;
        }
    }

    /**
     * 读取配置（懒解析一次；解析失败返回 null，调用端走内置常量兜底）。
     */
    @Nullable
    public static Config load() {
        Config config = sConfig;
        if (config != null) {
            return config;
        }
        synchronized (SearchEngineConfig.class) {
            if (sConfig == null) {
                sConfig = parse(CONFIG_JSON);
            }
            return sConfig;
        }
    }

    @Nullable
    private static Config parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject sceneMap = root.optJSONObject("searchEngineSceneMap");
            JSONObject scene = sceneMap == null
                ? null : sceneMap.optJSONObject(SCENE_BROWSER_SEARCH_BOX);
            JSONArray engineArray = scene == null ? null : scene.optJSONArray("searchEngines");
            if (engineArray == null || engineArray.length() == 0) {
                XposedLog.w(TAG, "Config searchEngines is empty");
                return null;
            }

            List<Engine> engines = new ArrayList<>();
            for (int i = 0; i < engineArray.length(); i++) {
                JSONObject obj = engineArray.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                Engine engine = new Engine();
                engine.name = obj.optString("searchEngineName", null);
                engine.url = obj.optString("searchUrl", null);
                engine.icon = obj.optString("iconUrl", null);
                engine.titleZh = obj.optString("title_zh_CN", null);
                engine.titleTw = obj.optString("title_zh_TW", null);
                engine.titleEn = obj.optString("title_en_US", null);
                engine.keyword = obj.optString("keyword", null);
                engine.channelNo = obj.optString("channelNo", null);
                engine.showIcon = obj.optBoolean("showIcon", true);
                if (!TextUtils.isEmpty(engine.name) && !TextUtils.isEmpty(engine.url)) {
                    engines.add(engine);
                }
            }
            if (engines.isEmpty()) {
                return null;
            }

            String defaultName = null;
            JSONObject defaultMap = root.optJSONObject("defaultSearchEngineMap");
            if (defaultMap != null) {
                defaultName = defaultMap.optString(SCENE_BROWSER_SEARCH_BOX, null);
            }
            return new Config(engines, defaultName);
        } catch (Throwable t) {
            XposedLog.w(TAG, "Config parse error", t);
            return null;
        }
    }
}
