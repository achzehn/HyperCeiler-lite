/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2023 HowieHChen, howie.dev@outlook.com
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

public class DisableUpdateCheck extends BaseHook {

    private Method mDoInBackgroundMethod;
    private Method mOnPostExecuteMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        List<Class<?>> miMarketUpdateClasses = optionalMemberList("mi_market_update_class", new IDexKitList() {
            @Override
            public BaseDataList<?> dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("MarketUpdateAgent", "packageName")
                    )
                );
            }
        });

        if (!miMarketUpdateClasses.isEmpty()) {
            String className = miMarketUpdateClasses.get(0).getName();
            
            mDoInBackgroundMethod = optionalMember("disable_update_1", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .name("doInBackground")
                            .returnType(Object.class)
                            .declaredClass(ClassMatcher.create()
                                .className(className)
                            )
                        )
                    ).singleOrNull();
                }
            });

            mOnPostExecuteMethod = optionalMember("disable_update_2", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    return bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .name("onPostExecute")
                            .paramCount(1)
                            .declaredClass(ClassMatcher.create()
                                .className(className)
                            )
                        )
                    ).singleOrNull();
                }
            });
        }

        return true;
    }

    @Override
    public void init() {
        if (mDoInBackgroundMethod != null) {
            hookMethod(mDoInBackgroundMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(1);
                }
            });
        }

        if (mOnPostExecuteMethod != null) {
            hookMethod(mOnPostExecuteMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        }
    }
}