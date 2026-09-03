/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.app;

import com.hchen.database.HookBase;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.BypassAdbInstallVerify;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.RemoveConversationBubbleSettingsRestriction;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.app.AppDisable;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.app.AppRestrict;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.other.DisableRootCheck;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.other.SimplifyMainFragment;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.other.SkipCountDownLimit;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.sidebar.video.DisableRemoveScreenHoldOn;
import com.sevtinge.hyperceiler.libhook.rules.securitycenter.sidebar.video.UnlockVideoSomeFunc;

@HookBase(targetPackage = "com.miui.securitycenter")
public class SecurityCenter extends BaseLoad {


    @Override
    public void onPackageLoaded() {
        // 应用管理
        initHook(new AppRestrict(), PrefsBridge.getBoolean("security_center_app_restrict"));
        initHook(new AppDisable(), PrefsBridge.getBoolean("security_center_app_disable"));

        // 其他
        initHook(SimplifyMainFragment.INSTANCE, PrefsBridge.getBoolean("security_center_simplify_home"));
        initHook(DisableRootCheck.INSTANCE, PrefsBridge.getBoolean("security_center_disable_root_check"));
        initHook(new BypassAdbInstallVerify(), PrefsBridge.getBoolean("security_center_adb_install_verify"));
        initHook(new SkipCountDownLimit(), PrefsBridge.getBoolean("security_center_skip_count_down_limit"));

        // 小窗和气泡通知
        initHook(new RemoveConversationBubbleSettingsRestriction(), PrefsBridge.getBoolean("security_center_remove_conversation_bubble_settings_restriction"));

        // 全局侧边栏
        initHook(DisableRemoveScreenHoldOn.INSTANCE, PrefsBridge.getBoolean("security_center_disable_remove_screen_hold_on"));
        initHook(UnlockVideoSomeFunc.INSTANCE, PrefsBridge.getBoolean("security_center_unlock_s_resolution"));
    }
}
