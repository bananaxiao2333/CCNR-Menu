/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu;

import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuConfigStore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 模组入口。
 *
 * <p>本模组是**纯客户端**的：所有功能都发生在主菜单的渲染与输入上，服务端没有任何逻辑，
 * 也没有网络包。因此入口只做一件事——把配置准备好（首次运行生成 {@code menu.json}），
 * 并在启动日志里报告一次「读到的背景是什么」，方便对比「我改了配置但界面没变」。
 *
 * <p>Forge 1.20.1 的 {@code mods.toml} **没有** {@code clientSideOnly} 这个字段（1.20.2+ 才有），
 * 所以这个 jar 在专用服务端上也会被加载。做法是用 {@link FMLEnvironment#dist} 直接判掉：
 * 服务端上一次配置都不读、一个类都不加载客户端相关的（{@link Dist} 注解的订阅者由 Forge 自己跳过）。
 */
@Mod(MenuConfig.MOD_ID)
public final class CCNRMenuMod {

    private static final Logger LOGGER = LogManager.getLogger(MenuConfig.MOD_ID);

    public CCNRMenuMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("[CCNR-Menu] 这是客户端模组，服务端不加载任何功能（可以安全地不装）");
            return;
        }
        // 启动即读一次：让「配置有问题」在日志里第一时间出现，而不是等到玩家打开主菜单
        MenuConfigIO.LoadResult result = MenuConfigStore.reload();
        LOGGER.info(
                "[CCNR-Menu] 已就绪：配置 {}，背景 {}，按钮 {}",
                result.file(),
                result.config().background().kind(),
                result.config().vanillaButtons()
                        ? "原版"
                        : "自定义 " + result.config().buttonCount() + " 个");
    }
}
