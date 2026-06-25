/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class BlockDialog extends BaseHook {

    @Override
    public void init() {
        Class<?> controllerClass = findClassIfExists("com.android.browser.Controller");
        if (controllerClass != null) {
            String[] methods = {
                "showHotListWidgetAddDialog",
                "showChildProtectDialog",
                "showShortcutDialog",
                "showCommonWidgetAddDialog"
            };
            for (String method : methods) {
                hookAllMethods(controllerClass, method, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        param.setResult(null);
                    }
                });
            }
        }

        Class<?> aiSearchScanUtilClass = findClassIfExists("com.android.browser.util.AiSearchScanUtil");
        if (aiSearchScanUtilClass != null) {
            hookAllMethods(aiSearchScanUtilClass, "showScanScanGuideDialog", new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    param.setResult(null);
                }
            });
        }
    }
}