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

public class SwitchEnv extends BaseHook {

    private Method mEnvGetMethod;
    private Method mEnvSetMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mEnvGetMethod = optionalMember("env_get", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .returnType(String.class)
                        .addUsingString("environment_flag_file", StringMatchType.Equals)
                        .addUsingString("environment_flag", StringMatchType.Equals)
                        .addUsingString("0", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });

        mEnvSetMethod = optionalMember("env_set", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .returnType(void.class)
                        .addUsingString("environment_flag_file", StringMatchType.Equals)
                        .addUsingString("environment_flag", StringMatchType.Equals)
                        .addUsingString("3", StringMatchType.Equals)
                    )
                ).singleOrNull();
            }
        });

        return true;
    }

    @Override
    public void init() {
        if (mEnvGetMethod != null) {
            hookMethod(mEnvGetMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    param.setResult("1");
                }
            });
        }

        if (mEnvSetMethod != null) {
            hookMethod(mEnvSetMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    if (args != null && args.length > 0 && args[0] instanceof String) {
                        args[0] = "1";
                    }
                    param.setResult(callMethod(param.getThisObject(), mEnvSetMethod.getName(), args));
                }
            });
        }
    }
}