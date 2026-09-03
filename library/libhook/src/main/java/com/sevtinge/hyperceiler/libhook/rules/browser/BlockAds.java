/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import android.view.View;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

/**
 * 浏览器去广告（聚合规则）。
 * <p>
 * 当前覆盖：
 * <ul>
 * <li>下载弹窗游戏推广：保存文件弹窗内"等待下载时，先玩一局"小游戏推荐卡
 * （com.android.browser.minigame.view.GameRecommendCardView）→ 直接隐藏。</li>
 * </ul>
 */
public class BlockAds extends BaseHook {

    private static final String TAG = "BlockAds";

    /** 下载弹窗内的小游戏推荐卡 */
    private static final String GAME_CARD_VIEW_CLASS =
        "com.android.browser.minigame.view.GameRecommendCardView";

    @Override
    public void init() {
        android.util.Log.i("BrowserAdsD", "BlockAds init");
        blockDownloadGameCard();
    }

    /** 隐藏下载弹窗内的小游戏推荐卡片 */
    private void blockDownloadGameCard() {
        Class<?> cardClass = findClassIfExists(GAME_CARD_VIEW_CLASS);
        if (cardClass == null) {
            XposedLog.w(TAG, "Game recommend card view not found: " + GAME_CARD_VIEW_CLASS);
            return;
        }
        try {
            hookAllConstructors(cardClass, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        Object view = param.getThisObject();
                        if (view instanceof View) {
                            ((View) view).setVisibility(View.GONE);
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Hide game card error", t);
                    }
                }
            });
            // 双保险：即使卡片被重新显示，列表数据绑定时再次隐藏
            hookAllMethods(cardClass, "setGameList", new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    try {
                        Object view = param.getThisObject();
                        if (view instanceof View) {
                            ((View) view).setVisibility(View.GONE);
                        }
                    } catch (Throwable t) {
                        XposedLog.w(TAG, "Hide game card on bind error", t);
                    }
                }
            });
            XposedLog.i(TAG, "Download dialog game card blocked");
        } catch (Exception e) {
            XposedLog.e(TAG, "Failed to block game card", e);
        }
    }
}
