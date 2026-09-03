package com.sevtinge.hyperceiler.libhook.rules.hongguo

import android.app.Application
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import com.sevtinge.hyperceiler.libhook.base.BaseLoad

/**
 * 红果短剧综合增强 Hook。
 *
 * 业务逻辑移植自独立模块 xyz.kejiyu.hongguo（libxposed API 102 Chain 风格），
 * 通过 BaseLoad.getXposed() 获取 XposedInterface 句柄，无需改造为 EzXHelper 风格。
 *
 * 开关说明（两层门控）：
 * - HyperCeiler 侧：PrefsBridge "hongguo_enable" 决定是否安装 Hook（默认关闭）。
 * - 宿主侧：红果应用内设置页入口 / 通知栏菜单打开模块面板，控制各子功能（面板开关为
 *   宿主进程内自持久化 SharedPreferences，独立于 HyperCeiler prefs）。
 */
class HongGuoHooks : BaseHook() {

    override fun init() {
        val process = currentProcessName()
        if (process.contains(":sandboxed_process") ||
            process.contains(":privileged_process") ||
            process.contains(":renderer")
        ) {
            LogUtil.info("WebView 沙箱/渲染进程，跳过 Hook 安装: $process")
            return
        }
        LogUtil.init(process)
        val classLoader = BaseLoad.getClassLoader() ?: return
        val pkg = BaseLoad.getPackageName() ?: return
        Hooks.installBusinessHooks(classLoader, pkg)
    }

    private fun currentProcessName(): String = try {
        Application.getProcessName() ?: ""
    } catch (_: Throwable) {
        ""
    }
}
