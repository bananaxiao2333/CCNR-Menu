/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuConfigStore;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把原版主菜单换成 {@link MenuScreen}。
 *
 * <p>**为什么用事件而不是 Mixin**：Forge 1.20.1 的 {@link ScreenEvent.Opening} 提供了
 * {@code setNewScreen}，足以替换掉正在打开的原版界面。走事件有三个好处：
 * ① 不需要 mixin 配置与 refmap，少一类「开发环境正常、打包后失效」的故障；
 * ② 与原版类没有字节码耦合，Minecraft 小版本更新不会静默失效；
 * ③ 只拦截「正在打开 TitleScreen」这一个条件，其它界面（以及别人对 TitleScreen 的 mixin）
 * 完全不受影响。
 *
 * <p>拦截条件刻意收得很紧：
 * <ul>
 *   <li>只认 {@code TitleScreen}——进入世界后的暂停菜单、死亡界面等一律不动；</li>
 *   <li>配置里 {@code enabled=false} 时不拦截，玩家立刻回到纯原版主菜单
 *       （这也是排查「装了模组主菜单不对劲」的第一条退路）。</li>
 * </ul>
 *
 * <p>这里还会顺手做一次「配置变了就重载」的检查：玩家改完 {@code menu.json} 回到主菜单就能看到效果，
 * 不必重开游戏。
 */
@Mod.EventBusSubscriber(modid = MenuConfig.MOD_ID, value = Dist.CLIENT)
public final class TitleScreenHook {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private TitleScreenHook() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof TitleScreen)) return;
        MenuConfigIO.LoadResult result = MenuConfigStore.reloadIfChanged();
        if (!result.config().enabled()) return;
        // 换成自己的屏幕（不是 TitleScreen），因此不会再次触发本事件，不存在递归
        event.setNewScreen(new MenuScreen(result.config()));
        // 用 INFO 而不是 DEBUG：排查「装了但没变化」时，第一句要确认的就是「到底接管了没有」，
        // 而玩家手上只有 latest.log（DEBUG 不进这个文件）
        LOGGER.info(
                "[CCNR-Menu] 已接管主菜单（背景={}，按钮={}）",
                result.config().background().kind(),
                result.config().vanillaButtons()
                        ? "原版"
                        : "自定义 " + result.config().buttonCount() + " 个");
    }
}
