/*
 * This file is part of HyperCeiler.
 * Based on BiLiveInvisible by lzghzr (https://github.com/lzghzr/BiLiveInvisible)
 */
package com.sevtinge.hyperceiler.libhook.rules.bilibili;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Method;
import java.util.Map;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class BiLiveInvisible extends BaseHook {

    private Method mAddCommonParamMethod;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mAddCommonParamMethod = optionalMember("bili_add_common_param", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                return bridge.findMethod(FindMethod.create()
                    .searchPackages("com.bilibili.bililive.infra.network.interceptor")
                    .matcher(MethodMatcher.create()
                        .name("addCommonParam")
                    )
                ).singleOrNull();
            }
        });
        return true;
    }

    @Override
    public void init() {
        // Hook addCommonParam: 移除 access_key 实现隐身
        if (mAddCommonParamMethod != null) {
            hookMethod(mAddCommonParamMethod, new IMethodHook() {
                @Override
                public void after(HookParam param) {
                    Object[] args = param.getArgs();
                    if (args[0] instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) args[0];
                        // 获取弹幕服务器时隐身
                        if (map.containsKey("is_anchor")) {
                            map.remove("access_key");
                        }
                        // 获取用户信息时伪装进其他房间
                        if (map.containsKey("not_mock_enter_effect")) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> stringMap = (Map<String, String>) args[0];
                            stringMap.replace("room_id", "273022");
                        }
                        // 房间心跳隐身
                        if (map.containsKey("hb")) {
                            map.remove("access_key");
                        }
                        // 心跳隐身
                        if (map.containsKey("heart_beat")) {
                            map.remove("access_key");
                        }
                    }
                }
            });
        }

        // Hook fastjson JSONObject.put: 弹幕连接时替换 uid 为 0
        try {
            findAndHookMethod("com.alibaba.fastjson.JSONObject", "put", String.class, Object.class,
                new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        Object[] args = param.getArgs();
                        if (args[0] != null && args[0].equals("uid")) {
                            Object thisObj = param.getThisObject();
                            if (thisObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, ?> map = (Map<String, ?>) thisObj;
                                if (map.containsKey("group")) {
                                    args[1] = 0;
                                }
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // fastjson 可能不存在于所有 B 站版本
        }
    }
}
