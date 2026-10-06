/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

/**
 * 「哪些界面该被本模组接管背景」的判定（纯逻辑，可单测）。
 *
 * <p>为什么把它单独抽出来：这条判定同时被两个绘制点使用（自己的菜单屏幕、原版界面的背景事件），
 * 而且它决定了模组的**影响范围**——判断错了要么背景没换（需求没做到），
 * 要么把游戏内的界面也涂掉（玩家看不清世界）。这种「范围」逻辑必须能被断言，
 * 不能藏在两个渲染回调的 if 里各写一遍。
 *
 * <p>三档：
 * <ul>
 *   <li>自己的菜单屏幕：只要模组启用就接管（这就是它的本职）；</li>
 *   <li>原版那些**泥土背景**的界面（无世界时打开的一切：世界选择、多人列表、设置、语言、Mod 列表……）：
 *       {@code applyToAllScreens} 打开时接管——「换掉泥土界面」与「换掉全景图」本来就是同一件事；</li>
 *   <li>**世界内的界面**（暂停、背包、箱子……）：永不接管。它们的背景是玩家眼前的世界，
 *       盖掉它会让玩家在开背包时失去空间感（而且原版自己也只画一层淡淡的渐变）。</li>
 * </ul>
 */
public final class BackgroundScope {

    private BackgroundScope() {}

    /** 该界面是否应当由本模组绘制背景。 */
    public static boolean shouldApply(boolean ownMenu, boolean inWorld, boolean enabled, boolean applyToAllScreens) {
        if (!enabled) return false;
        if (ownMenu) return true;
        return applyToAllScreens && !inWorld;
    }
}
