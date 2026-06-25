/*
 * This file is part of HyperCeiler.
 */
package com.sevtinge.hyperceiler.libhook.app.Browser;

import com.hchen.database.HookBase;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.rules.browser.BlockDialog;
import com.sevtinge.hyperceiler.libhook.rules.browser.Configuration;
import com.sevtinge.hyperceiler.libhook.rules.browser.DisableUpdateCheck;
import com.sevtinge.hyperceiler.libhook.rules.browser.BrowserSearchEngine;
import com.sevtinge.hyperceiler.libhook.rules.browser.SkipSplash;
import com.sevtinge.hyperceiler.libhook.rules.browser.HideHomepageTopBar;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;

@HookBase(targetPackage = "com.android.browser", minSdk = 36)
public class Browser extends BaseLoad {
    @Override
    public void onPackageLoaded() {
        initHook(new DisableUpdateCheck(), PrefsBridge.getBoolean("browser_disable_update_check"));
        initHook(new SkipSplash(), PrefsBridge.getBoolean("browser_skip_splash"));
        initHook(new Configuration(), PrefsBridge.getBoolean("browser_configuration"));
        initHook(new HideHomepageTopBar(), PrefsBridge.getBoolean("browser_hide_homepage_top_bar"));
        initHook(new BlockDialog(), PrefsBridge.getBoolean("browser_block_dialog"));
        initHook(new BrowserSearchEngine(), PrefsBridge.getBoolean("browser_search_engine"));
    }
}