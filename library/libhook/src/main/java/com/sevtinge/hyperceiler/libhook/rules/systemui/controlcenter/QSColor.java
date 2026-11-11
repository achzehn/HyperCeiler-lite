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
package com.sevtinge.hyperceiler.libhook.rules.systemui.controlcenter;

import static com.sevtinge.hyperceiler.libhook.base.BaseHook.findAndHookConstructor;

import android.content.Context;

import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

public class QSColor extends BaseHook {
    private static boolean small = false;
    private static int color = -1;

    public static void load() {
        small = PrefsBridge.getBoolean("system_ui_control_center_qs_open_color");
        color = PrefsBridge.getInt("system_ui_control_center_qs_color", -1);
    }

    @Override
    public void init() {
        load();
        if (small) {
            findAndHookConstructor("com.android.systemui.qs.tileimpl.MiuiQSIconViewImpl", Context.class, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    setObjectField(param.getThisObject(), "mIconColorEnabled", color);
                    setObjectReplacement("com.android.systemui", "color", "qs_tile_icon_enabled_color", color);
                }
            });
        }
    }
}
