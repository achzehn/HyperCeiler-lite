/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Method;

/**
 * 跳过浏览器开屏广告。
 * <p>
 * 原理（反编译确认，两条路径均汇入 MSA SDK）：
 * SplashAdManager.requestAd（第三方调起）与 SplashActiveAdManager.requestAd
 * （图标冷启动）最终都调用 com.msa.sdk.core.splash.SystemSplashAd.requestAd，
 * 宿主的倒计时进入主界面逻辑（startCountdown）由广告回调
 * onAdDismissed/onAdError 驱动。
 * <p>
 * 因此核心拦截点为 SystemSplashAd.requestAd：before 中立即代为回调
 * listener.onAdError()，宿主随即走"无广告"倒计时进入主界面——
 * 既能去广告，又不会因回调缺失导致开屏白屏卡顿。
 * 直接 no-op 请求入口（回调缺失）会造成开屏白屏卡顿（实测）。
 * <p>
 * 附加：support_passive → false 关闭被动拉起开屏能力。
 */
public class SkipSplash extends BaseHook {

    private static final String TAG = "BrowserAdsD";

    private static final String SYSTEM_SPLASH_AD_CLASS =
        "com.msa.sdk.core.splash.SystemSplashAd";

    private Method mSupportPassiveMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mSupportPassiveMethod = optionalMember("skip_splash_support", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .returnType(boolean.class)
                        .addUsingString("SystemSplashAd", StringMatchType.Equals)
                        .addUsingString("support_passive", StringMatchType.Equals)
                        .addUsingString("content://com.miui.systemAdSolution.extContentProvider/supportPassive", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });

        return true;
    }

    @Override
    public void init() {
        // 核心：拦截 MSA 开屏广告请求，立即回调 onAdError 让宿主走倒计时
        hookSystemSplashAdRequest();

        if (mSupportPassiveMethod != null) {
            hookMethod(mSupportPassiveMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }
    }

    /**
     * Hook SystemSplashAd.requestAd(Context, IAdListener, SplashSdkConfig)：
     * 先以传入 listener 回调 onAdError（宿主随即 startCountdown 进入主界面），
     * 再阻断真实广告请求。
     */
    private void hookSystemSplashAdRequest() {
        Class<?> splashAdClass = findClassIfExists(SYSTEM_SPLASH_AD_CLASS);
        if (splashAdClass == null) {
            android.util.Log.w(TAG, "SystemSplashAd class not found");
            return;
        }
        try {
            hookAllMethods(splashAdClass, "requestAd", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    try {
                        Object[] args = param.getArgs();
                        if (args != null && args.length >= 2 && args[1] != null) {
                            Method onAdError = args[1].getClass().getMethod("onAdError");
                            onAdError.setAccessible(true);
                            onAdError.invoke(args[1]);
                            android.util.Log.i(TAG, "Splash ad request blocked with onAdError");
                        }
                    } catch (Throwable t) {
                        android.util.Log.w(TAG, "onAdError invoke failed", t);
                    }
                    // 阻断真实广告请求
                    param.setResult(null);
                }
            });
            android.util.Log.i(TAG, "SystemSplashAd.requestAd hooked");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to hook SystemSplashAd.requestAd", e);
        }
    }
}
