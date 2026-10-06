/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import com.ccnrcom.menu.config.MenuConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把「真的发生了什么事」接进启动日志那一层。
 *
 * <p>为什么这些事件值得接：日志在玩家眼里就是「这台机器正在做什么」。加入服务器的过程中
 * 玩家盯着的是加载界面，而日志那一层（背景钩子挂在所有泥土界面上）正好是屏幕上唯一在说明
 * 现状的东西——**成功还是失败**必须由真实事件决定，不能靠猜或计时器假装。
 *
 * <p>三类事件：
 * <ul>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingIn} → 收掉进度条并记一行 {@code [  OK  ]}；</li>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingOut} → 记一行 {@code [FAILED]}（连接被断开）；</li>
 *   <li>{@link ScreenEvent.Init.Post} 遇到 {@link ReceivingLevelScreen} → 开一条「正在加载世界数据」。</li>
 * </ul>
 *
 * <p>没有 mixin、没有反射：用的都是 Forge 已有的公开事件。这样即使别人的模组也在改主菜单，
 * 也不会与这份实现打架。
 */
@Mod.EventBusSubscriber(modid = MenuConfig.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private ClientEvents() {}

    /**
     * 进入世界：记一行「已连接」，并把菜单背景的素材交还给系统。
     *
     * <p>为什么是「进入世界」而不是「界面关闭」：界面会一个接一个地开（主菜单 → 设置 → 返回），
     * 每次关闭都释放就等于每次都重新解码一张 GIF。进入世界之后几乎不会再出现泥土界面，
     * 正是把几十 MB 动画帧还回去的最佳时机。
     */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        MenuActions.onJoined(currentServerAddress());
        ScreenBackgrounds.release();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MenuActions.onDisconnected();
    }

    /**
     * 世界数据加载界面出现 = 「正在加载」这一段开始了。
     *
     * <p>这里只看界面出现的时刻，**不看它什么时候消失**：加载完成由
     * {@link PlayerEvent.PlayerLoggedInEvent} 给出，那才是「真的进去了」。
     * 用界面消失当成功判据会在「加载失败自动退出」时误报成功。
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof ReceivingLevelScreen) {
            MenuActions.onLoadingWorld();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        boolean local = Minecraft.getInstance().hasSingleplayerServer();
        MenuActions.onJoined(local ? singleplayerName() : currentServerAddress());
    }

    /** 当前服务器地址（单机/未连接时为 {@code null}）。 */
    private static String currentServerAddress() {
        var server = Minecraft.getInstance().getCurrentServer();
        return server == null ? null : server.ip;
    }

    /** 单机存档名（取不到就给一个通用词）。 */
    private static String singleplayerName() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null)
            return Component.translatable("ccnr_menu.log.singleplayer").getString();
        try {
            return server.getWorldData().getLevelName();
        } catch (Exception e) {
            LOGGER.debug("[CCNR-Menu] 取存档名失败: {}", e.toString());
            return Component.translatable("ccnr_menu.log.singleplayer").getString();
        }
    }
}
