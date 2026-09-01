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

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;
import com.sevtinge.hyperceiler.libhook.utils.NotificationClickSession;

import java.lang.reflect.Method;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

/**
 * Notification-click circulation compatibility owned by MiLink's cross-device process.
 * <p>
 * Converts one notification click into one native PIN_APP transaction and consumes MiLink's
 * immediately following duplicate passive-stream command on the same worker thread.
 */
public class NotificationClickFlowHook extends BaseHook {
    private static final String MIUI_SYNERGY_SDK_CLASS =
            "com.xiaomi.mirror.synergy.MiuiSynergySdk";
    private static final String RELAY_APP_CALLBACK_CLASS =
            "com.xiaomi.mirror.synergy.MiuiSynergySdk$RelayAppCallback";
    private static final String NOTIFICATION_TRANS_CLIENT_CLASS =
            "com.xiaomi.dist.notification.trans.client.NotifTransClient";
    private static final String NOTIFICATION_MESSAGE_CLASS =
            "com.xiaomi.dist.notification.common.data.NotificationMessage";
    private static final String NOTIFICATION_TRANS_CALLBACK_CLASS =
            "com.xiaomi.dist.notification.trans.client.callback.INotificationTransCallback";
    private static final String MIRROR_PROVIDER_AUTHORITY =
            "com.xiaomi.mirror.callprovider";
    private static final String PIN_APP_PROVIDER_METHOD = "performPinIconClick";

    private final NotificationClickSession clickSession = new NotificationClickSession();

    private Method launchNotificationSinkMethod;
    private Method relaySuccessMethod;
    private Method relayFailureMethod;
    private Method publishNotificationMethod;
    private Method getPackageNameMethod;
    private Method publishResultMethod;

    @Override
    public void init() {
        try {
            resolveMethods();
            installHooks();
        } catch (Throwable e) {
            debugCallbackError("NotificationClickFlowHook init", e);
        }
    }

    private void resolveMethods() throws ReflectiveOperationException {
        Class<?> synergySdkClass = findClass(MIUI_SYNERGY_SDK_CLASS);
        Class<?> relayCallbackClass = findClass(RELAY_APP_CALLBACK_CLASS);
        Class<?> transClientClass = findClass(NOTIFICATION_TRANS_CLIENT_CLASS);
        Class<?> notificationMessageClass = findClass(NOTIFICATION_MESSAGE_CLASS);
        Class<?> transCallbackClass = findClass(NOTIFICATION_TRANS_CALLBACK_CLASS);

        launchNotificationSinkMethod = synergySdkClass.getDeclaredMethod(
                "launchAppFromPendingIntentSink",
                Context.class, String.class, String.class, relayCallbackClass);
        launchNotificationSinkMethod.setAccessible(true);

        relaySuccessMethod = relayCallbackClass.getDeclaredMethod("onSuccess");
        relaySuccessMethod.setAccessible(true);

        relayFailureMethod = relayCallbackClass.getDeclaredMethod("onFailure", int.class);
        relayFailureMethod.setAccessible(true);

        publishNotificationMethod = transClientClass.getDeclaredMethod(
                "handleNotification",
                String.class, notificationMessageClass, transCallbackClass);
        publishNotificationMethod.setAccessible(true);

        getPackageNameMethod = notificationMessageClass.getDeclaredMethod("getPackageName");
        getPackageNameMethod.setAccessible(true);

        publishResultMethod = transCallbackClass.getDeclaredMethod(
                "onResult", int.class, Object.class);
        publishResultMethod.setAccessible(true);
    }

    private void installHooks() {
        // Hook handleNotification to consume duplicate passive notification stream
        hookMethod(publishNotificationMethod, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                String deviceId = (String) param.getArgs()[0];
                Object message = param.getArgs()[1];
                String packageName;
                try {
                    packageName = (String) getPackageNameMethod.invoke(message);
                } catch (Throwable e) {
                    clickSession.clear();
                    debugCallbackError("Could not correlate the follow-up notification command", e);
                    return;
                }

                if (!clickSession.consumeMatching(
                        deviceId,
                        packageName,
                        SystemClock.elapsedRealtime())) {
                    return;
                }

                notifyPublishSuccess(param.getArgs()[2]);
                param.setResult(null);
            }
        });

        // Hook launchAppFromPendingIntentSink to handle notification click
        hookMethod(launchNotificationSinkMethod, new IMethodHook() {
            @Override
            public void before(HookParam param) {
                Object contextValue = param.getArgs()[0];
                String deviceId = (String) param.getArgs()[1];
                String packageName = (String) param.getArgs()[2];
                if (!(contextValue instanceof Context context)
                        || !NotificationClickSession.isValidNotificationClick(deviceId, packageName)) {
                    return;
                }

                boolean dispatched = dispatchPinApp(context, deviceId, packageName);
                // Stock still calls handleNotification after this callback; consume that paired command.
                clickSession.begin(deviceId, packageName, SystemClock.elapsedRealtime());
                notifyRelayResult(param.getArgs()[3], dispatched);
                if (dispatched) {
                    param.setResult(null);
                }
            }
        });
    }

    private boolean dispatchPinApp(Context context, String deviceId, String packageName) {
        try {
            Bundle extras = new Bundle();
            extras.putString("remoteDeviceId", deviceId);
            extras.putString("package", packageName);
            Bundle result = context.getContentResolver().call(
                    MIRROR_PROVIDER_AUTHORITY,
                    PIN_APP_PROVIDER_METHOD,
                    null,
                    extras);
            return result != null;
        } catch (Throwable e) {
            debugCallbackError("Could not dispatch notification through PIN_APP", e);
        }
        return false;
    }

    private void notifyRelayResult(Object callback, boolean success) {
        if (callback == null) return;
        try {
            if (success) {
                relaySuccessMethod.invoke(callback);
            } else {
                relayFailureMethod.invoke(callback, -1);
            }
        } catch (Throwable e) {
            debugCallbackError("Could not report the PIN_APP dispatch result", e);
        }
    }

    private void notifyPublishSuccess(Object callback) {
        if (callback == null) return;
        try {
            publishResultMethod.invoke(callback, 1, null);
        } catch (Throwable e) {
            debugCallbackError("Could not acknowledge the consumed notification command", e);
        }
    }
}
