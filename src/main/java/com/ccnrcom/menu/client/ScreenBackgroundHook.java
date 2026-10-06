/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.client.background.ScreenBackgroundRenderable;
import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigStore;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把背景铺到**原版界面**上（泥土背景的那些界面：世界选择、多人列表、设置、语言、Mod 列表……）。
 *
 * <p>注入点是 {@link ScreenEvent.Init.Post}：往界面自己的 {@code renderables} 列表**下标 0**
 * 插一件 {@link ScreenBackgroundRenderable}。原版界面的绘制顺序是「{@code renderBackground()}
 * 画泥土 → 自己画标题 → {@code super.render()} 遍历 {@code renderables}」，所以它正好落在
 * **泥土之上、控件之下**——既盖住泥土，又不会压住选项列表。
 *
 * <p>为什么不用 {@link ScreenEvent.BackgroundRendered}（那是更「正统」的注入点）：实测所有二级界面
 * 都还是泥土，而日志里没有报错、事件本身也确实会被原版 {@code renderDirtBackground()} 发出来。
 * 现在这条路不依赖任何事件派发：只要界面走正常流程就一定生效，而且症状可解释
 * （没生效只有两种可能：界面没走 {@code super.render()}，或者根本没进来——两者都有日志）。
 *
 * <p>覆盖范围由 {@link com.ccnrcom.menu.client.background.BackgroundScope} 决定：
 * 泥土背景的界面换掉，世界内的界面（暂停、背包……）**不动**。
 * 自己的菜单屏幕不走这里（它在 {@link MenuScreen} 里画，避免同一帧画两遍）。
 */
@Mod.EventBusSubscriber(modid = MenuConfig.MOD_ID, value = Dist.CLIENT)
public final class ScreenBackgroundHook {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    /**
     * 已经记过日志的界面（每种界面一行，避免刷屏）。
     *
     * <p>为什么值得留一条 INFO：这个模组唯一「玩家看得见、日志里却什么都没有」的故障就是
     * 「界面没被接管」，而那一行正是排查的起点。
     */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private ScreenBackgroundHook() {}

    /** 给原版界面挂上「画背景」的那件 Renderable。 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof MenuScreen) return;

        if (!ScreenBackgrounds.appliesToVanillaScreen(screen)) {
            MenuConfig config = MenuConfigStore.current().config();
            logOnce(
                    "skip:" + screen.getClass().getName(),
                    "未接管界面背景: {}（enabled={} applyToAllScreens={} 世界内={}）",
                    screen.getClass().getSimpleName(),
                    config.enabled(),
                    config.applyToAllScreens(),
                    Minecraft.getInstance().level != null);
            return;
        }
        if (screen.renderables.stream().anyMatch(r -> r instanceof ScreenBackgroundRenderable)) return;
        // 下标 0：renderables 按下标绘制，排最前才会落在泥土之上、控件之下
        screen.renderables.add(0, new ScreenBackgroundRenderable(screen));
        logOnce(
                "hook:" + screen.getClass().getName(),
                "已接管界面背景: {}",
                screen.getClass().getName());
    }

    /**
     * 进入世界时释放背景素材。
     *
     * <p>为什么是「进入世界」而不是「界面关闭」：界面会一个接一个地开（主菜单 → 设置 → 返回），
     * 每次关闭都释放就等于每次都重新解码一张 GIF。而进入世界之后几乎不会再出现泥土界面，
     * 正是把几十 MB 动画帧交还给系统的最佳时机。
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ScreenBackgrounds.release();
        LOGGER.debug("[CCNR-Menu] 已进入世界，释放菜单背景素材");
    }

    private static void logOnce(String key, String message, Object... args) {
        if (LOGGED.add(key)) LOGGER.info("[CCNR-Menu] " + message, args);
    }
}
