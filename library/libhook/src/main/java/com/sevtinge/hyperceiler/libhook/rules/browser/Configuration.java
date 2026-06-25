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
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKitList;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.base.BaseData;
import org.luckypray.dexkit.result.BaseDataList;

import java.lang.reflect.Method;
import java.util.List;

public class Configuration extends BaseHook {

    private Method mPrefShowSugSwitchViewMethod;
    private Method mRecPopupCardSwitchMethod;
    private Method mPrefPushPopDialogMethod;
    private Method mDefaultPageRealTimeHotSpotSwitchMethod;
    private Method mDefaultPageGuessYouWantSwitchMethod;
    private Method mAdAppDownloadExitSwitchMethod;
    private Method mAdAppDownloadPushSwitchMethod;
    private Method mAdAppDownloadHomeSwitchMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        List<Class<?>> configCenterClasses = optionalMemberList("config_center_class", new IDexKitList() {
            @Override
            public BaseDataList<?> dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("pref_ad_open_ad_more", "ad_app_download_exit_switch")
                    )
                );
            }
        });

        if (!configCenterClasses.isEmpty()) {
            String className = configCenterClasses.get(0).getName();
            ClassMatcher classMatcher = ClassMatcher.create().className(className);

            mPrefShowSugSwitchViewMethod = optionalMember("pref_show_sug_switch_view", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("pref_show_sug_switch_view")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mRecPopupCardSwitchMethod = optionalMember("rec_popup_card_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("rec_popup_card_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mPrefPushPopDialogMethod = optionalMember("pref_push_pop_dialog", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("pref_push_pop_dialog", "activity")
                        )
                    ).singleOrNull();
                }
            });

            mDefaultPageRealTimeHotSpotSwitchMethod = optionalMember("default_page_real_time_hot_spot_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("default_page_real_time_hot_spot_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mDefaultPageGuessYouWantSwitchMethod = optionalMember("default_page_guess_you_want_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("default_page_guess_you_want_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mAdAppDownloadExitSwitchMethod = optionalMember("ad_app_download_exit_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("ad_app_download_exit_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mAdAppDownloadPushSwitchMethod = optionalMember("ad_app_download_push_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("ad_app_download_push_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });

            mAdAppDownloadHomeSwitchMethod = optionalMember("ad_app_download_home_switch", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("ad_app_download_home_switch")
                            .paramCount(0)
                            .declaredClass(classMatcher)
                        )
                    ).singleOrNull();
                }
            });
        }

        return true;
    }

    @Override
    public void init() {
        if (mPrefShowSugSwitchViewMethod != null) {
            hookMethod(mPrefShowSugSwitchViewMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(true);
                }
            });
        }

        if (mRecPopupCardSwitchMethod != null) {
            hookMethod(mRecPopupCardSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        if (mPrefPushPopDialogMethod != null) {
            hookMethod(mPrefPushPopDialogMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        }

        if (mDefaultPageRealTimeHotSpotSwitchMethod != null) {
            hookMethod(mDefaultPageRealTimeHotSpotSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        if (mDefaultPageGuessYouWantSwitchMethod != null) {
            hookMethod(mDefaultPageGuessYouWantSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        if (mAdAppDownloadExitSwitchMethod != null) {
            hookMethod(mAdAppDownloadExitSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        if (mAdAppDownloadPushSwitchMethod != null) {
            hookMethod(mAdAppDownloadPushSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }

        if (mAdAppDownloadHomeSwitchMethod != null) {
            hookMethod(mAdAppDownloadHomeSwitchMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(false);
                }
            });
        }
    }
}