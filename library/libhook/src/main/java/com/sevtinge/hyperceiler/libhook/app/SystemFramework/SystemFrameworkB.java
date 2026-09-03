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
package com.sevtinge.hyperceiler.libhook.app.SystemFramework;

import com.hchen.database.HookBase;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseLoad;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.corepatch.AllowUpdateSystemApp;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.corepatch.BypassIsolationViolation;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.corepatch.BypassSignCheckForT;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.corepatch.DisableLowApiCheckForB;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.others.DisableRemoveFingerprintSensorConfig;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.display.AllDarkMode;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.freeform.DisableFreeformBlackList;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.freeform.FreeFormCount;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.freeform.FreeformBubble;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.freeform.OpenAppInFreeForm;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.others.AllowDisableProtectedPackage;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.others.AntiQues;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.others.DeleteOnPostNotification;
import com.sevtinge.hyperceiler.libhook.rules.systemframework.others.DisablePinVerifyPer72h;

@HookBase(targetPackage = "system", minSdk = 36)
public class SystemFrameworkB extends BaseLoad {

    @Override
    public void onPackageLoaded() {
        // 核心破解（包管理服务）
        initHook(BypassSignCheckForT.INSTANCE,
            (PrefsBridge.getBoolean("system_framework_core_patch_auth_creak") || PrefsBridge.getBoolean("system_framework_core_patch_disable_integrity"))
            && PrefsBridge.getBoolean("system_framework_core_patch_enable")
        );
        initHook(new BypassIsolationViolation(), PrefsBridge.getBoolean("system_framework_core_patch_bypass_isolation_violation"));
        initHook(new AllowUpdateSystemApp(), PrefsBridge.getBoolean("system_framework_core_patch_allow_update_system_app"));
        initHook(new DisableLowApiCheckForB(), PrefsBridge.getBoolean("system_framework_disable_low_api_check"));

        // 修复 A16 移植包开启核心破解后掉指纹，仅作备选项
        initHook(DisableRemoveFingerprintSensorConfig.INSTANCE, PrefsBridge.getBoolean("system_framework_core_patch_unloss_fingerprint"));

        // 小窗
        initHook(new FreeFormCount(), PrefsBridge.getBoolean("system_framework_freeform_count"));
        initHook(new DisableFreeformBlackList(), PrefsBridge.getBoolean("system_framework_disable_freeform_blacklist"));
        initHook(new FreeformBubble(), PrefsBridge.getBoolean("system_framework_freeform_bubble"));
        initHook(new OpenAppInFreeForm(), PrefsBridge.getBoolean("system_framework_freeform_jump"));

        // 显示
        initHook(new AllDarkMode(), PrefsBridge.getBoolean("system_framework_allow_all_dark_mode"));

        // 其它
        initHook(new AntiQues(), PrefsBridge.getBoolean("system_settings_anti_ques"));
        initHook(new DisablePinVerifyPer72h(), PrefsBridge.getBoolean("system_framework_disable_72h_verify"));
        initHook(DeleteOnPostNotification.INSTANCE, PrefsBridge.getBoolean("system_other_delete_on_post_notification"));
        initHook(new AllowDisableProtectedPackage(), PrefsBridge.getBoolean("system_framework_allow_disable_protected_package"));
    }
}
