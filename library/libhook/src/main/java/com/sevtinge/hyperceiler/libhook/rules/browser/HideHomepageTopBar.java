/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of XiaomiHelper project
 * Copyright (C) 2026 HowieHChen, howie.dev@outlook.com
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import android.view.View;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import com.sevtinge.hyperceiler.libhook.callback.IMethodHook;

import io.github.kyuubiran.ezxhelper.xposed.common.HookParam;

public class HideHomepageTopBar extends BaseHook {

    @Override
    public void init() {
        hookHomePageTopBar();
    }

    private void hookHomePageTopBar() {
        String[] classNames = {
            "com.android.browser.homepage.simplified.home.SimplifiedHomeFragment",
            "com.android.browser.homepage.HomePageFragment",
            "com.android.browser.homepage.simplified.SimplifiedHomeFragment"
        };

        for (String className : classNames) {
            Class<?> homePageClass = findClassIfExists(className);
            if (homePageClass != null) {
                hookAllConstructors(homePageClass, new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        Object instance = param.getResult();
                        if (instance == null) {
                            instance = param.getThisObject();
                        }
                        if (instance != null) {
                            hideTopBar(instance);
                        }
                    }
                });

                hookAllMethods(homePageClass, "onCreateView", new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        Object instance = param.getThisObject();
                        if (instance != null) {
                            hideTopBar(instance);
                        }
                    }
                });

                hookAllMethods(homePageClass, "onViewCreated", new IMethodHook() {
                    @Override
                    public void after(HookParam param) {
                        Object instance = param.getThisObject();
                        if (instance != null) {
                            hideTopBar(instance);
                        }
                    }
                });
            }
        }
    }

    private void hideTopBar(Object instance) {
        String[] fieldNames = {"mTopView", "topView", "mTopBar", "topBar", "mHeaderView", "headerView", "mHeaderBar", "headerBar"};
        for (String fieldName : fieldNames) {
            try {
                Object topView = getObjectField(instance, fieldName);
                if (topView instanceof View) {
                    ((View) topView).setVisibility(View.GONE);
                    break;
                }
            } catch (Exception ignored) {}
        }
    }
}