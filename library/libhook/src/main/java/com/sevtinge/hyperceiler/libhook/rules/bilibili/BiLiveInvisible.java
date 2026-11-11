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

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.Method;
import java.util.Map;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * 哔哩哔哩直播隐身观看
 * <p>
 * 移植自 BiLiveInvisible（https://github.com/lzghzr/BiLiveInvisible）
 */
public class BiLiveInvisible extends BaseHook {

    private Method mAddCommonParamMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mAddCommonParamMethod = optionalMember("bili_add_common_param", bridge -> bridge.findMethod(FindMethod.create()
            .searchPackages("com.bilibili.bililive.infra.network.interceptor")
            .matcher(MethodMatcher.create()
                .name("addCommonParam")
            )
        ).singleOrNull());
        return true;
    }

    @Override
    public void init() {
        if (mAddCommonParamMethod != null) {
            hookMethod(mAddCommonParamMethod, new IMethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                public void after(HookParam param) {
                    Object[] args = param.getArgs();
                    if (args.length == 0 || !(args[0] instanceof Map)) return;
                    Map<String, Object> map = (Map<String, Object>) args[0];
                    // 获取弹幕服务器时隐身
                    if (map.containsKey("is_anchor")) {
                        map.remove("access_key");
                    }
                    // 获取用户信息时伪装进入其他房间
                    if (map.containsKey("not_mock_enter_effect")) {
                        ((Map<String, String>) args[0]).replace("room_id", "273022");
                    }
                    // 房间心跳与心跳隐身
                    if (map.containsKey("hb") || map.containsKey("heart_beat")) {
                        map.remove("access_key");
                    }
                }
            });
        }

        // 弹幕连接时将 uid 置 0
        findAndHookMethod("com.alibaba.fastjson.JSONObject", "put", String.class, Object.class,
            new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object[] args = param.getArgs();
                    if (!"uid".equals(args[0])) return;
                    Object thisObject = param.getThisObject();
                    if (thisObject instanceof Map && ((Map<?, ?>) thisObject).containsKey("group")) {
                        args[1] = 0;
                    }
                }
            }
        );
    }
}
