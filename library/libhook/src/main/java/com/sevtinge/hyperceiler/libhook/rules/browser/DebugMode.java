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

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Method;

public class DebugMode extends BaseHook {

    private Method mDebugModeMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mDebugModeMethod = optionalMember("get_debug_mode", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .name("getDebugMode")
                        .returnType(boolean.class)
                        .addUsingString("pref_key_debug_mode", StringMatchType.StartsWith)
                    )
                ).singleOrNull();
            }
        });
        return true;
    }

    @Override
    public void init() {
        if (mDebugModeMethod != null) {
            hookMethod(mDebugModeMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult(true);
                }
            });
        }
    }
}