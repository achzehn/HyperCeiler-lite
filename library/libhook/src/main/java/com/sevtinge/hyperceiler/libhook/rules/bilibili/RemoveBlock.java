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
package com.sevtinge.hyperceiler.libhook.rules.bilibili;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * 解除哔哩哔哩限制
 * <p>
 * 移植自 MRGA（https://github.com/kazutoiris/MRGA）
 */
public class RemoveBlock extends BaseHook {

    private static final String BATCHED_SP_IMPL = "com.bilibili.lib.blkv.internal.sp.BatchedSpImpl";

    @Override
    public void init() {
        // 解除用户封禁状态
        hookAllMethods(BATCHED_SP_IMPL, "getBoolean", new IMethodHook() {
            @Override
            public void before(HookParam param) {
                Object[] args = param.getArgs();
                if (args.length > 0 && args[0] instanceof String key && key.startsWith("user_blocked")) {
                    param.setResult(false);
                }
            }
        });

        // 跳过用户状态检查，绕过漫游功能限制
        hookAllMethods(BATCHED_SP_IMPL, "getLong", new IMethodHook() {
            @Override
            public void before(HookParam param) {
                Object[] args = param.getArgs();
                if (args.length > 0 && args[0] instanceof String key && key.startsWith("user_status_last_check_time")) {
                    param.setResult(Long.MAX_VALUE);
                }
            }
        });
    }
}
