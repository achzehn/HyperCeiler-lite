/*
 * This file is part of HyperCeiler.
 * Based on Xposed-Disable-FLAG_KEEP_SCREEN_ON by w311ang
 */
package com.sevtinge.hyperceiler.libhook.rules.various;

import android.view.WindowManager;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.tool.EzxHelpUtils;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class DisableKeepScreenOn extends BaseHook {

    // 临时诊断日志开关（验证通过后移除）
    private static final boolean DEBUG = true;
    private static final String DTAG = "KSODebug";
    private static final java.util.concurrent.atomic.AtomicInteger sClearedCount =
        new java.util.concurrent.atomic.AtomicInteger();

    private static void dlog(String msg) {
        if (DEBUG) {
            android.util.Log.i(DTAG, msg);
        }
    }

    @Override
    public void init() {
        dlog("init called, isSystemServer=" + BaseLoad.isSystemServer());
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

        // 系统侧（system_server）：解除窗口安全锁定，与参考项目保持一致
        if (BaseLoad.isSystemServer()) {
            try {
                findAndHookMethod("com.android.server.wm.WindowState", "isSecureLocked",
                    returnConstant(false)
                );
            } catch (Throwable t) {
                // 系统版本差异可能不存在该方法
            }
            try {
                findAndHookMethod("com.android.server.wm.WindowManagerService", "isSecureLocked",
                    "com.android.server.wm.WindowState",
                    returnConstant(false)
                );
            } catch (Throwable t) {
                // 系统版本差异可能不存在该方法
            }

            // 系统侧标志位清除：在 WMS 窗口注册/布局的统一入口清除 FLAG_KEEP_SCREEN_ON，
            // 覆盖所有应用（无需按包名注册作用域），使屏幕按系统超时时间正常息屏。
            // WMS 持有息屏唤醒锁的判定依据是 WindowState.mAttrs.flags，每次布局遍历都会重新读取，
            // 因此在 attrs 进入 WMS 前清除标志即可持久生效。
            try {
                // 防止 relayoutWindow 被 JIT 内联导致 Hook 失效（与 FlagSecure 的做法一致）
                EzxHelpUtils.deoptimizeMethods(
                    findClass("com.android.server.wm.WindowManagerService"), "relayoutWindow");
            } catch (Throwable t) {
                XposedLog.w(TAG, "system", "DisableKeepScreenOn: deoptimize relayoutWindow failed", t);
            }

            final IMethodHook clearKeepScreenOn = new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    // 直接修改 attrs 对象本身（addWindow/relayoutWindow 的 attrs 为 Binder 反序列化副本，
                    // 修改后 WMS 会将其持久保存到 WindowState.mAttrs）
                    for (Object arg : param.getArgs()) {
                        if (arg instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                            if ((lp.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
                                lp.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                                int n = sClearedCount.incrementAndGet();
                                if (n <= 10) {
                                    dlog("cleared KEEP_SCREEN_ON from attrs, count=" + n
                                        + ", pkg=" + lp.packageName);
                                }
                            }
                        }
                    }
                }
            };

            // addWindow：窗口首次注册
            try {
                java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> h1 =
                    hookAllMethods("com.android.server.wm.WindowManagerService", "addWindow", clearKeepScreenOn);
                dlog("addWindow hooked=" + h1.size());
            } catch (Throwable t) {
                XposedLog.w(TAG, "system", "DisableKeepScreenOn: hook WMS.addWindow failed", t);
                dlog("addWindow hook FAILED: " + t);
            }

            // relayoutWindow：窗口属性更新（应用每次 relayout 都会把客户端 attrs 同步到 WindowState）
            try {
                java.util.List<io.github.libxposed.api.XposedInterface.HookHandle> h2 =
                    hookAllMethods("com.android.server.wm.WindowManagerService", "relayoutWindow", clearKeepScreenOn);
                dlog("relayoutWindow hooked=" + h2.size());
            } catch (Throwable t) {
                XposedLog.w(TAG, "system", "DisableKeepScreenOn: hook WMS.relayoutWindow failed", t);
                dlog("relayoutWindow hook FAILED: " + t);
            }
        }
    }
}
