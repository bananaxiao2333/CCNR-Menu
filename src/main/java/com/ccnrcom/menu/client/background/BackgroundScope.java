/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import java.util.List;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * 「哪些界面该被本模组接管背景」的判定（纯逻辑，可单测）。
 *
 * <p>为什么把它单独抽出来：这条判定同时被两个绘制点使用（自己的菜单屏幕、原版界面的背景事件），
 * 而且它决定了模组的**影响范围**——判断错了要么背景没换（需求没做到），
 * 要么把游戏内的界面也涂掉（玩家看不清世界）。这种「范围」逻辑必须能被断言，
 * 不能藏在两个渲染回调的 if 里各写一遍。
 *
 * <p>四档：
 * <ul>
 *   <li>自己的菜单屏幕：只要模组启用就接管；</li>
 *   <li><b>{@link #LOADING_SCREENS 加载类界面}</b>（创建世界、加载地形、连接服务器……）：
 *       **永远接管**，哪怕世界对象已经建起来了——原因见下；</li>
 *   <li>原版那些**泥土背景**的界面（无世界时打开的一切：世界选择、多人列表、设置、语言、Mod 列表……）：
 *       {@code applyToAllScreens} 打开时接管；</li>
 *   <li>**世界内的界面**（暂停、背包、箱子……）：永不接管。它们的背景是玩家眼前的世界，
 *       盖掉它会让玩家在开背包时失去空间感（原版自己也只画一层淡淡的渐变）。</li>
 * </ul>
 *
 * <p><b>为什么需要「加载类」这一档</b>：原来的判据只有「世界是否已建立」（{@code level != null}），
 * 而创建世界时客户端的世界对象在**地形还没加载完**之前就已经存在。于是
 * {@code ProgressScreen}/{@code GenericDirtMessageScreen} 明明挂上了背景 Renderable，
 * 却被判成「世界内」→ 不画 → 屏幕上仍是泥土。用户实机看到的正是这一条
 * （日志里「已接管界面背景: ProgressScreen」之后那一段出现泥土）。
 * 加载类界面的语义是「世界还没准备好，屏幕上没有任何玩家要看的世界」，所以它们不该用那条判据。
 *
 * <p>思路与 FancyMenu 一致：**按界面身份判定，而不是靠一个全局状态去猜**。
 * 区别是这里只白名单「加载类」这一小撮，其余仍按「泥土背景 vs 世界背景」分——
 * 白名单越大，越容易把玩家真正要看的世界涂掉。
 */
public final class BackgroundScope {

    /**
     * 永远接管的「加载类」界面。
     *
     * <p>它们的共同点：都是**过渡**界面，屏幕上的内容全部是临时绘制的进度/提示，
     * 没有任何「玩家要看的世界」。加新成员前先问一句：玩家在这个界面上需要看清后面的世界吗？
     * 需要就别加。
     */
    private static final List<Class<? extends Screen>> LOADING_SCREENS = List.of(
            ProgressScreen.class, // 创建世界 / 保存并退出的进度条
            GenericDirtMessageScreen.class, // 「正在生成世界」那种只有一行字的消息页
            LevelLoadingScreen.class, // 加载地形（左侧地图 + 进度条）
            ReceivingLevelScreen.class, // 正在接收世界数据
            ConnectScreen.class); // 连接服务器

    private BackgroundScope() {}

    /**
     * 该界面是否应当由本模组绘制背景。
     *
     * @param loadingScreen 这个界面是否属于 {@link #LOADING_SCREENS}
     */
    public static boolean shouldApply(
            boolean ownMenu, boolean loadingScreen, boolean inWorld, boolean enabled, boolean applyToAllScreens) {
        if (!enabled) return false;
        if (ownMenu) return true;
        if (loadingScreen) return true;
        return applyToAllScreens && !inWorld;
    }

    /**
     * 渲染期判定：这个界面在**这一帧**该不该由我们画背景。
     *
     * <p>与 {@link #shouldApply} 分开的理由：这里的调用点在最底层（泥土绘制的收口处），
     * 那里只有「界面对象 + 它是不是我们的菜单 + 世界在不在 + 配置」这几样东西，
     * 而加载类名单区分了内置与配置追加两种来源。混入与事件钩子共用这一个函数，
     * 免得两边各判一次、判法漂移——那种漂移的症状是「有的界面接管了，有的没有」。
     */
    public static boolean shouldOverrideNow(
            Screen screen, boolean ownMenu, boolean inWorld, boolean enabled, boolean applyToAllScreens) {
        if (screen == null) return false;
        List<String> extra = com.ccnrcom.menu.config.MenuConfigStore.current()
                .config()
                .background()
                .extraLoadingScreens();
        return shouldApply(ownMenu, isLoadingScreen(screen, extra), inWorld, enabled, applyToAllScreens);
    }

    /** 这个界面是否属于「加载类」（永远接管那一档）——只认内置名单。 */
    public static boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        for (Class<? extends Screen> type : LOADING_SCREENS) {
            if (type.isInstance(screen)) return true;
        }
        return false;
    }

    /**
     * 同 {@link #isLoadingScreen(Screen)}，但额外认配置里追加的类名。
     *
     * <p>为什么要留这个口子：别的模组会引入自己的加载界面（整合包里很常见），
     * 那些界面上同样是「没有玩家要看的世界、却露出泥土」。让作者按**类名**把它们加进来，
     * 比等我们一个个认全现实得多。
     *
     * @param extraClassNames 追加的界面类名（全限定名，例如 {@code com.example.LoadingScreen}）
     */
    public static boolean isLoadingScreen(Screen screen, List<String> extraClassNames) {
        if (isLoadingScreen(screen)) return true;
        if (screen == null || extraClassNames == null) return false;
        String actual = screen.getClass().getName();
        for (String name : extraClassNames) {
            if (name == null) continue;
            String trimmed = name.trim();
            // 全限定名精确匹配：前缀匹配会让 `...screens.Foo` 意外命中 `...screens.FooBar`
            if (!trimmed.isEmpty() && trimmed.equals(actual)) return true;
        }
        return false;
    }

    /** 加载类界面的类名清单（诊断输出用）。 */
    public static List<String> loadingScreenNames() {
        return LOADING_SCREENS.stream().map(Class::getName).toList();
    }
}
