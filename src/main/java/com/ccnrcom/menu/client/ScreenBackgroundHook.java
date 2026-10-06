/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigStore;
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
 * 把背景铺到**原版界面**上（泥土背景 + 全景图之外的其它界面）。
 *
 * <p>注入点是 {@link ScreenEvent.BackgroundRendered}：Forge 在 {@code Screen.renderBackground()}
 * 画完之后、以及 {@code renderDirtBackground()} 画完之后各发一次。也就是说我们拿到事件时，
 * 屏幕上已经是泥土图（或世界内的渐变），这时把背景**盖上去**即可——不需要 mixin 去拦原版方法，
 * 也不会影响原版自己后续的绘制。
 *
 * <p>覆盖范围由 {@link com.ccnrcom.menu.client.background.BackgroundScope} 决定：
 * 泥土背景的界面换掉，世界内的界面（暂停、背包……）**不动**。
 * 自己的菜单屏幕不走这里（它在 {@link MenuScreen} 里画，避免同一帧画两遍）。
 */
@Mod.EventBusSubscriber(modid = MenuConfig.MOD_ID, value = Dist.CLIENT)
public final class ScreenBackgroundHook {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private ScreenBackgroundHook() {}

    @SubscribeEvent
    public static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        Screen screen = event.getScreen();
        if (screen instanceof MenuScreen) return; // 自己的菜单自己画
        // 这里必须用 current()：菜单屏幕也用同一份，两边取值不同会让背景每帧重建一次
        ScreenBackgrounds.render(
                event.getGuiGraphics(),
                screen,
                MenuConfigStore.current().config(),
                false,
                Minecraft.getInstance().getFrameTime());
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
}
