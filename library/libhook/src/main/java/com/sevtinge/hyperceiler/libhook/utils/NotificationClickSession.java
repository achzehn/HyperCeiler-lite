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
package com.sevtinge.hyperceiler.libhook.utils;

/**
 * Correlates MiLink's two synchronous halves of one notification click.
 *
 * <p>The state is thread-confined because Xiaomi runs both calls sequentially on the same
 * single-thread executor. Consumption is one-shot even on a mismatch, and the short expiry keeps
 * a vendor control-flow change from affecting a later, unrelated publish on the pooled thread.</p>
 */
public final class NotificationClickSession {
    public static final long MAX_AGE_MILLIS = 5_000L;

    private final ThreadLocal<PendingClick> pending = new ThreadLocal<>();

    public void begin(String deviceId, String packageName, long nowMillis) {
        if (!isValidNotificationClick(deviceId, packageName)) {
            pending.remove();
            return;
        }
        pending.set(new PendingClick(deviceId, packageName, nowMillis));
    }

    public boolean consumeMatching(String deviceId, String packageName, long nowMillis) {
        PendingClick click = pending.get();
        pending.remove();
        if (click == null) {
            return false;
        }
        long ageMillis = nowMillis - click.createdAtMillis;
        return ageMillis >= 0
                && ageMillis <= MAX_AGE_MILLIS
                && click.deviceId.equals(deviceId)
                && click.packageName.equals(packageName);
    }

    public void clear() {
        pending.remove();
    }

    public static boolean isValidNotificationClick(String deviceId, String packageName) {
        return deviceId != null
                && !deviceId.isEmpty()
                && packageName != null
                && !packageName.isEmpty();
    }

    private static final class PendingClick {
        private final String deviceId;
        private final String packageName;
        private final long createdAtMillis;

        PendingClick(String deviceId, String packageName, long createdAtMillis) {
            this.deviceId = deviceId;
            this.packageName = packageName;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
