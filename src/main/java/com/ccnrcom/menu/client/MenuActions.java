/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.config.MenuAction;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.LanguageSelectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.client.gui.ModListScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 执行菜单按钮的动作。
 *
 * <p>这里是**安全边界**：配置里的字符串（尤其是 {@code url} 与 {@code connect}）最终会走到
 * 「交给操作系统的默认程序打开」与「发起网络连接」这两件事上。白名单校验在
 * {@link MenuAction#parse} 阶段完成（那时发现问题就跳过按钮并给出警告），本类只负责执行；
 * 执行侧**也不放宽**——{@code url} 一律交给 {@code Util.getPlatform().openUri}，
 * 没有任何「猜协议」的补救逻辑。
 *
 * <p>界面映射与 {@link MenuAction#SCREENS} 是一对：那边列出的 id 必须在这里都有分支，
 * 反过来这里的分支也不该超出白名单（{@code MenuActionScreensTest} 会把两者钉在一起）。
 */
public final class MenuActions {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private MenuActions() {}

    /**
     * 执行动作。
     *
     * @param parent 当前屏幕（作为下一个屏幕的返回父级）
     */
    public static void run(Screen parent, MenuAction action) {
        if (action == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        switch (action.kind()) {
            case NONE -> {
                // action:"none" 的按钮刻意什么都不做——这是**写出来的**行为（例如占位一张还没做的子菜单），
                // 不是解析失败后的静默降级
            }
            case QUIT -> minecraft.stop();
            case SCREEN -> {
                Screen target = screenFor(minecraft, parent, action.value());
                if (target != null) minecraft.setScreen(target);
            }
            case CONNECT -> connect(minecraft, parent, action.value());
            case URL -> openUri(action.value());
            case COPY -> minecraft.keyboardHandler.setClipboard(action.value());
        }
    }

    /**
     * 原版界面映射。
     *
     * <p>只有 {@link MenuAction#SCREENS} 里列出的 id 会走到这里；未知 id 记一条警告并**不打开任何界面**
     * ——两处白名单漂移时乱开界面比什么都不做更难排查。
     */
    private static Screen screenFor(Minecraft minecraft, Screen parent, String id) {
        return switch (id) {
            case "singleplayer" -> new SelectWorldScreen(parent);
                // 与原版一致：关掉多人游戏警告的人直接进服务器列表，否则先看警告页
            case "multiplayer" -> minecraft.options.skipMultiplayerWarning
                    ? new JoinMultiplayerScreen(parent)
                    : new SafetyScreen(parent);
            case "options" -> new OptionsScreen(parent, minecraft.options);
            case "language" -> new LanguageSelectScreen(parent, minecraft.options, minecraft.getLanguageManager());
            case "accessibility" -> new AccessibilityOptionsScreen(parent, minecraft.options);
            case "mods" -> new ModListScreen(parent);
            case "credits" -> new CreditsAndAttributionScreen(parent);
            default -> {
                LOGGER.warn("[CCNR-Menu] 未知的界面 id: {}（MenuAction.SCREENS 与 MenuActions.screenFor 不一致）", id);
                yield null;
            }
        };
    }

    /** 直连服务器（地址合法性已在解析阶段校验过）。 */
    private static void connect(Minecraft minecraft, Screen parent, String address) {
        try {
            ServerAddress parsed = ServerAddress.parseString(address);
            // lan=false：这不是局域网服务器，标记错会让服务器列表里出现一条假的局域网记录
            ServerData data = new ServerData(address, address, false);
            ConnectScreen.startConnecting(parent, minecraft, parsed, data, false);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 直连失败: {} —— {}", address, e.toString());
        }
    }

    private static void openUri(String url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 打开链接失败: {} —— {}", url, e.toString());
        }
    }
}
