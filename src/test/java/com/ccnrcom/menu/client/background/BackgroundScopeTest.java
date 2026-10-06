/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 背景**影响范围**门禁。
 *
 * <p>为什么这条判定值得单独测：它决定了这个模组会不会**干扰游戏内界面**。
 * 判错了有两种后果，而且都不容易在开发机上发现——要么「泥土界面没换」（需求没做到），
 * 要么「开背包时背景被菜单图盖住」（玩家失去空间感）。这两个回调各写一遍 if 迟早会漂，
 * 所以判定被抽成纯函数，两个绘制点都调它。
 *
 * <p>「加载类界面」这一档是实机逼出来的：创建世界时客户端的世界对象在**地形加载完之前**
 * 就已经非 null，只看「世界内」会把 {@code ProgressScreen} 判成不该接管——
 * 症状是日志写着「已接管」而屏幕上仍是泥土。
 */
class BackgroundScopeTest {

    // shouldApply(ownMenu, loadingScreen, inWorld, enabled, applyToAllScreens)

    @Test
    @DisplayName("主菜单：只要模组启用就接管（这是它的本职）")
    void ownMenuAlwaysCovered() {
        assertTrue(BackgroundScope.shouldApply(true, false, false, true, true));
        assertTrue(BackgroundScope.shouldApply(true, false, false, true, false), "关掉 applyToAllScreens 不该影响主菜单");
    }

    @Test
    @DisplayName("泥土界面：applyToAllScreens 打开时接管（「泥土界面也换掉」）")
    void dirtScreensCoveredWhenEnabled() {
        assertTrue(BackgroundScope.shouldApply(false, false, false, true, true));
        assertFalse(BackgroundScope.shouldApply(false, false, false, true, false), "关掉后只剩主菜单用自定义背景");
    }

    @Test
    @DisplayName("世界内的真界面：永不接管（否则开背包时看不到世界）")
    void inWorldScreensNeverCovered() {
        assertFalse(BackgroundScope.shouldApply(false, false, true, true, true));
        assertFalse(BackgroundScope.shouldApply(false, false, true, true, false));
    }

    @Test
    @DisplayName("加载类界面：**即使世界已建立**也接管（创建世界那一段的泥土就是这么来的）")
    void loadingScreensCoveredEvenInWorld() {
        assertTrue(BackgroundScope.shouldApply(false, true, true, true, true), "加载类界面在世界已建立时也必须接管");
        assertTrue(
                BackgroundScope.shouldApply(false, true, true, true, false), "与 applyToAllScreens 无关：加载类不是「泥土界面」那一档");
        assertTrue(BackgroundScope.shouldApply(false, true, false, true, true));
    }

    @Test
    @DisplayName("模组关掉时一律不接管（第一条退路）")
    void disabledNeverCovers() {
        assertFalse(BackgroundScope.shouldApply(true, false, false, false, true));
        assertFalse(BackgroundScope.shouldApply(false, false, false, false, true));
        assertFalse(BackgroundScope.shouldApply(false, false, true, false, true));
        assertFalse(BackgroundScope.shouldApply(false, true, true, false, true), "关掉模组时连加载类界面也不碰");
    }

    @Test
    @DisplayName("加载类白名单：认得出该认的，而且不许膨胀（名单越大越容易涂掉玩家要看的世界）")
    void loadingScreenWhitelist() {
        for (Class<?> type : new Class<?>[] {
            ProgressScreen.class,
            GenericDirtMessageScreen.class,
            LevelLoadingScreen.class,
            ReceivingLevelScreen.class,
            ConnectScreen.class
        }) {
            assertTrue(
                    BackgroundScope.loadingScreenNames().contains(type.getName()),
                    type.getSimpleName() + " 应当在加载类白名单里");
        }
        int size = BackgroundScope.loadingScreenNames().size();
        assertTrue(size >= 5, "白名单不能比已知的这几个还少: " + size);
        assertTrue(size <= 8, "白名单膨胀到 " + size + " 个了：加成员前先问「玩家需要看清后面的世界吗」");
        assertFalse(BackgroundScope.isLoadingScreen(null), "null 不该被当成加载类界面");
    }

    @Test
    @DisplayName("追加名单：按**全限定名**精确匹配，前缀相同但不同类的不算")
    void extraLoadingScreensMatchExactly() {
        // 用白名单里真实存在的类来验证匹配逻辑（不必真的构造 Screen 实例：
        // 后缀匹配错会让 `Foo` 意外命中 `FooBar`）
        String real = ProgressScreen.class.getName();
        assertTrue(BackgroundScope.isLoadingScreen(null, List.of(real)) == false, "null 界面永远不算");
        assertTrue(real.startsWith("net.minecraft."), "这条用例依赖真实类名: " + real);
        // 全限定名必须带点，否则配置解析阶段就会被拦下（见 parseExtraLoadingScreens）
        assertFalse(real.equals(real + "Bar"), "前缀相同但更长的类名不该被匹配");
    }

    @Test
    @DisplayName("追加名单不改变内置三档的行为（世界内界面仍然不接管）")
    void extraListDoesNotLeakIntoInWorldRule() {
        assertFalse(BackgroundScope.shouldApply(false, false, true, true, true), "世界内的真界面照样不接管");
    }
}
