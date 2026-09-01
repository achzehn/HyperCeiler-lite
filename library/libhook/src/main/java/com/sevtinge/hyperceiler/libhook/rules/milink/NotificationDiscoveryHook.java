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
package com.sevtinge.hyperceiler.libhook.rules.milink;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import java.lang.reflect.Method;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

/**
 * Notification-device discovery compatibility owned by MiLink's UI process.
 * <p>
 * Bypasses the Cast bit only while MiLink evaluates its synchronous local prerequisite.
 * Remote-device capability negotiation continues through the stock implementation.
 */
public class NotificationDiscoveryHook extends BaseHook {
    private static final String DISCOVER_CLASS =
            "com.xiaomi.dist.notification.discover.LyraDiscover";
    private static final String CONTINUITY_SERVICE_NAME_CLASS =
            "com.xiaomi.continuity.ServiceName";

    private final ThreadLocal<Integer> localDiscoveryDepth = new ThreadLocal<>();

    private Method localServiceSupportMethod;
    private Method castSupportMethod;

    @Override
    public void init() {
        try {
            resolveMethods();
            installHooks();
        } catch (Throwable e) {
            debugCallbackError("NotificationDiscoveryHook init", e);
        }
    }

    private void resolveMethods() throws ReflectiveOperationException {
        Class<?> discoverClass = findClass(DISCOVER_CLASS);
        Class<?> serviceNameClass = findClass(CONTINUITY_SERVICE_NAME_CLASS);

        localServiceSupportMethod = discoverClass.getDeclaredMethod(
                "isLocalServiceDataSupport", serviceNameClass);
        localServiceSupportMethod.setAccessible(true);

        castSupportMethod = discoverClass.getDeclaredMethod(
                "isCastServiceSupport", String.class);
        castSupportMethod.setAccessible(true);
    }

    private void installHooks() {
        // Hook isLocalServiceDataSupport to track local discovery depth
        hookMethod(localServiceSupportMethod, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                enterLocalDiscovery();
            }

            @Override
            public void after(HookParam param) {
                exitLocalDiscovery();
            }
        });

        // Hook isCastServiceSupport to bypass Cast bit during local discovery
        hookMethod(castSupportMethod, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                if (isLocalDiscoveryActive()) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    private void enterLocalDiscovery() {
        Integer depth = localDiscoveryDepth.get();
        localDiscoveryDepth.set(depth == null ? 1 : depth + 1);
    }

    private void exitLocalDiscovery() {
        Integer depth = localDiscoveryDepth.get();
        if (depth == null || depth <= 1) {
            localDiscoveryDepth.remove();
        } else {
            localDiscoveryDepth.set(depth - 1);
        }
    }

    private boolean isLocalDiscoveryActive() {
        Integer depth = localDiscoveryDepth.get();
        return depth != null && depth > 0;
    }
}
