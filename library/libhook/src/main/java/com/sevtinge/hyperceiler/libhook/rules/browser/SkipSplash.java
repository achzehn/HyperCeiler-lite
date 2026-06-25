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
import java.util.List;

public class SkipSplash extends BaseHook {

    private Method mThirdPartyLaunchAdMethod;
    private Method mIconLaunchAdMethod;
    private Method mSupportPassiveMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mThirdPartyLaunchAdMethod = optionalMember("skip_splash_third", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .returnType(void.class)
                        .paramCount(1)
                        .addUsingString("onTrackAppOpenThird appLaunchWay:", StringMatchType.Equals)
                        .addUsingString("第三方调起", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });

        mIconLaunchAdMethod = optionalMember("skip_splash_icon", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .returnType(void.class)
                        .addUsingString("SplashActiveAdManager", StringMatchType.Equals)
                        .addUsingString("requestAd", StringMatchType.Equals)
                        .addUsingString("msa_request", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });

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
        if (mThirdPartyLaunchAdMethod != null) {
            hookMethod(mThirdPartyLaunchAdMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        }

        if (mIconLaunchAdMethod != null) {
            hookMethod(mIconLaunchAdMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        }

        if (mSupportPassiveMethod != null) {
            hookMethod(mSupportPassiveMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        hookSplashAdManagerWhiteList();
    }

    private void hookSplashAdManagerWhiteList() {
        try {
            Class<?> splashAdManagerClass = findClassIfExists("com.android.browser.splash.SplashAdManager");
            if (splashAdManagerClass != null) {
                hookAllMethods(splashAdManagerClass, "inWhiteList", new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        param.setResult(true);
                    }
                });
            }
        } catch (Exception ignored) {}
    }
}