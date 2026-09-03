/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */

package com.sevtinge.hyperceiler.libhook.rules.securitycenter.other;

import android.os.Bundle;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class SkipCountDownLimit extends BaseHook {

    @Override
    public void init() {
        findAndHookMethod("com.miui.permcenter.privacymanager.model.InterceptBaseActivity", "onCreate", Bundle.class, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                Bundle bundle = (Bundle) param.getArgs()[0];
                if (bundle == null) {
                    bundle = new Bundle();
                    param.getArgs()[0] = bundle;
                }
                bundle.putInt("KET_STEP_COUNT", 0);
                bundle.putBoolean("KEY_ALLOW_ENABLE", true);
            }
        });
    }
}
