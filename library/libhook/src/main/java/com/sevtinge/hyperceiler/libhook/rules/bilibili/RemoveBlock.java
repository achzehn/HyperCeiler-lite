/*
 * This file is part of HyperCeiler.
 * Based on Make RoamingX Great Again by kazutoiris (https://github.com/kazutoiris/MRGA)
 */
package com.sevtinge.hyperceiler.libhook.rules.bilibili;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class RemoveBlock extends BaseHook {

    @Override
    public void init() {
        // Hook BatchedSpImpl.getBoolean: 解除用户封禁状态
        try {
            hookAllMethods("com.bilibili.lib.blkv.internal.sp.BatchedSpImpl", "getBoolean",
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object[] args = param.getArgs();
                        if (args.length > 0 && args[0] instanceof String) {
                            String key = (String) args[0];
                            if (key.startsWith("user_blocked")) {
                                param.setResult(false);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // BatchedSpImpl 可能不存在于所有 B 站版本
        }

        // Hook BatchedSpImpl.getLong: 跳过用户状态检查
        try {
            hookAllMethods("com.bilibili.lib.blkv.internal.sp.BatchedSpImpl", "getLong",
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object[] args = param.getArgs();
                        if (args.length > 0 && args[0] instanceof String) {
                            String key = (String) args[0];
                            if (key.startsWith("user_status_last_check_time")) {
                                param.setResult(Long.MAX_VALUE);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // BatchedSpImpl 可能不存在于所有 B 站版本
        }
    }
}
