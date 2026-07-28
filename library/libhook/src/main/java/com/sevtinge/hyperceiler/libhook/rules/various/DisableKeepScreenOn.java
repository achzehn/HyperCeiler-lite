/*
 * This file is part of HyperCeiler.
 * Based on Xposed-Disable-FLAG_KEEP_SCREEN_ON by w311ang
 */
package com.sevtinge.hyperceiler.libhook.rules.various;

import android.view.WindowManager;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class DisableKeepScreenOn extends BaseHook {

    @Override
    public void init() {
        // Hook Window.setFlags: 清除 FLAG_KEEP_SCREEN_ON 标志位
        try {
            findAndHookMethod("android.view.Window", "setFlags", int.class, int.class,
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        int flags = (int) param.getArgs()[0];
                        flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        param.getArgs()[0] = flags;
                    }
                }
            );
        } catch (Throwable t) {
            // Window.setFlags 一定存在，此 catch 仅做防御
        }

        // Hook View.setKeepScreenOn: 强制设为 false
        try {
            findAndHookMethod("android.view.View", "setKeepScreenOn", boolean.class,
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        param.getArgs()[0] = false;
                    }
                }
            );
        } catch (Throwable t) {
            // View.setKeepScreenOn 一定存在
        }

        // Hook WindowManagerGlobal.addView: 清除 LayoutParams 中的 FLAG_KEEP_SCREEN_ON
        try {
            hookAllMethods("android.view.WindowManagerGlobal", "addView",
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        if (param.getArgs().length >= 2 && param.getArgs()[1] instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.getArgs()[1];
                            lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // WindowManagerGlobal 可能因版本差异不存在某些重载
        }

        // Hook WindowManagerGlobal.updateViewLayout: 清除 LayoutParams 中的 FLAG_KEEP_SCREEN_ON
        try {
            findAndHookMethod("android.view.WindowManagerGlobal", "updateViewLayout",
                "android.view.View", "android.view.ViewGroup$LayoutParams",
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        if (param.getArgs()[1] instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.getArgs()[1];
                            lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // 不同 Android 版本方法签名可能不同
        }
    }
}
