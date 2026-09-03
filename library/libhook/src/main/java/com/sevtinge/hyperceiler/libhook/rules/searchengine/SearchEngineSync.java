/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.sevtinge.hyperceiler.libhook.rules.searchengine;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.provider.SharedPrefsProvider;
import com.sevtinge.hyperceiler.libhook.utils.api.ContextUtils;

import org.json.JSONObject;

/**
 * 浏览器 → 快速搜索「当前引擎」同步通道。
 * <p>
 * libxposed API 规定远程 prefs 在被 hook 的应用内只读，因此：
 * <ul>
 * <li>发布端（浏览器进程）：读取浏览器当前引擎信息后，经模块 App 的
 * {@link SharedPrefsProvider#call} 中转写入模块 prefs（putByApp 同步远程）；</li>
 * <li>消费端（快速搜索进程）：{@link #readCurrentEngine()} 从远程 prefs 读取。</li>
 * </ul>
 * 载荷为浏览器原生 searchengine.json 的引擎条目词表：
 * {"searchEngineName","searchUrl","iconUrl","title_zh_CN"}。
 */
public final class SearchEngineSync {

    private static final String TAG = "SearchEngineSync";

    /** 浏览器当前引擎落地键（仅经 Provider 中转写入） */
    public static final String PREF_CURRENT_ENGINE_JSON = "browser_current_engine_json";

    private SearchEngineSync() {
    }

    /** 当前引擎条目（双端通用） */
    public static final class CurrentEngine {
        public String name;      // searchEngineName
        public String url;       // searchUrl
        public String icon;      // iconUrl
        public String label;     // title_zh_CN
    }

    /**
     * 发布当前引擎（浏览器端调用；内部走 Provider 中转，需后台线程）。
     * 任何字段缺失视为无效发布。
     */
    public static void publish(@Nullable String name, @Nullable String url,
                               @Nullable String icon, @Nullable String label) {
        android.util.Log.i("SearchEngineSyncD", "publish called: name=" + name + " url=" + url);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) {
            android.util.Log.w("SearchEngineSyncD", "publish skipped: empty name/url");
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.putOpt("searchEngineName", name);
            json.putOpt("searchUrl", url);
            json.putOpt("iconUrl", icon);
            json.putOpt("title_zh_CN", label);
            String payload = json.toString();

            Context context = ContextUtils.getContextNoError(ContextUtils.FLAG_CURRENT_APP);
            if (context == null) {
                android.util.Log.w("SearchEngineSyncD", "publish failed: no context");
                XposedLog.w(TAG, "No context for engine sync publish");
                return;
            }
            Bundle extras = new Bundle();
            extras.putString(SharedPrefsProvider.EXTRA_ENGINE_JSON, payload);
            Object result = context.getContentResolver().call(
                Uri.parse("content://" + SharedPrefsProvider.AUTHORITY),
                SharedPrefsProvider.METHOD_ENGINE_SYNC, null, extras);
            android.util.Log.i("SearchEngineSyncD", "publish done, provider result=" + result);
            XposedLog.i(TAG, "Current engine published: " + name + " -> " + url);
        } catch (Throwable t) {
            android.util.Log.e("SearchEngineSyncD", "publish error", t);
            XposedLog.w(TAG, "Engine sync publish error", t);
        }
    }

    /** 读取浏览器当前引擎（快速搜索端；无同步数据返回 null） */
    @Nullable
    public static CurrentEngine readCurrentEngine() {
        try {
            String json = PrefsBridge.getString(PREF_CURRENT_ENGINE_JSON, null);
            if (TextUtils.isEmpty(json)) {
                return null;
            }
            JSONObject obj = new JSONObject(json);
            CurrentEngine engine = new CurrentEngine();
            engine.name = obj.optString("searchEngineName", null);
            engine.url = obj.optString("searchUrl", null);
            engine.icon = obj.optString("iconUrl", null);
            engine.label = obj.optString("title_zh_CN", null);
            if (TextUtils.isEmpty(engine.name) || TextUtils.isEmpty(engine.url)) {
                return null;
            }
            return engine;
        } catch (Throwable t) {
            XposedLog.w(TAG, "Engine sync read error", t);
            return null;
        }
    }
}
