/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 背景**影响范围**门禁。
 *
 * <p>为什么这条判定值得单独测：它决定了这个模组会不会**干扰游戏内界面**。
 * 判错了有两种后果，而且都不容易在开发机上发现——要么「泥土界面没换」（需求没做到），
 * 要么「开背包时背景被菜单图盖住」（玩家失去空间感）。这两个回调各写一遍 if 迟早会漂，
 * 所以判定被抽成纯函数，两个绘制点都调它。
 */
class BackgroundScopeTest {

    @Test
    @DisplayName("主菜单：只要模组启用就接管（这是它的本职）")
    void ownMenuAlwaysCovered() {
        assertTrue(BackgroundScope.shouldApply(true, false, true, true));
        assertTrue(BackgroundScope.shouldApply(true, false, true, false), "关掉 applyToAllScreens 不该影响主菜单");
    }

    @Test
    @DisplayName("泥土界面：applyToAllScreens 打开时接管（「泥土界面也换掉」）")
    void dirtScreensCoveredWhenEnabled() {
        assertTrue(BackgroundScope.shouldApply(false, false, true, true));
        assertFalse(BackgroundScope.shouldApply(false, false, true, false), "关掉后只剩主菜单用自定义背景");
    }

    @Test
    @DisplayName("世界内的界面：永不接管（否则开背包时看不到世界）")
    void inWorldScreensNeverCovered() {
        assertFalse(BackgroundScope.shouldApply(false, true, true, true));
        assertFalse(BackgroundScope.shouldApply(false, true, true, false));
    }

    @Test
    @DisplayName("模组关掉时一律不接管（第一条退路）")
    void disabledNeverCovers() {
        assertFalse(BackgroundScope.shouldApply(true, false, false, true));
        assertFalse(BackgroundScope.shouldApply(false, false, false, true));
        assertFalse(BackgroundScope.shouldApply(false, true, false, true));
    }
}
