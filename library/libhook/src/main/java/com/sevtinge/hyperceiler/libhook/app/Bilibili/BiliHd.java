/*
 * This file is part of HyperCeiler.
 */
package com.sevtinge.hyperceiler.libhook.app.Bilibili;

import com.hchen.database.HookBase;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.rules.bilibili.BiLiveInvisible;
import com.sevtinge.hyperceiler.libhook.rules.bilibili.RemoveBlock;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;

@HookBase(targetPackage = "tv.danmaku.bilibilihd")
public class BiliHd extends BaseLoad {
    @Override
    public void onPackageLoaded() {
        initHook(new BiLiveInvisible(), PrefsBridge.getBoolean("bilibili_live_invisible"));
        initHook(new RemoveBlock(), PrefsBridge.getBoolean("bilibili_remove_block"));
    }
}
