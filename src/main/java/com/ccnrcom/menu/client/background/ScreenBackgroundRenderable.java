/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.config.MenuConfigStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

/**
 * 挂在**原版界面自己的 {@code renderables} 列表最前面**的一件「画背景」。
 *
 * <p>为什么需要这条注入点（而不是只靠 {@code ScreenEvent.BackgroundRendered}）：原版界面的
 * 绘制顺序是「{@code renderBackground()} 画泥土 → 自己画标题 → {@code super.render()} 遍历
 * {@code renderables}」，而那件 Renderable 排在最前，于是它正好落在**泥土之上、控件之下**——
 * 既盖住了泥土，又不会压住选项列表。它不依赖任何事件的派发，所以只要界面走正常流程就一定生效。
 *
 * <p>插到下标 0 是刻意的：{@code renderables} 按下标顺序绘制，插到末尾会盖住所有控件。
 * 而 {@code init()} 会清空这个列表，所以每次重建界面都要重新挂（见 {@code ScreenBackgroundHook}）。
 */
public final class ScreenBackgroundRenderable implements Renderable {

    private final Screen screen;

    public ScreenBackgroundRenderable(Screen screen) {
        this.screen = screen;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        ScreenBackgrounds.render(gfx, screen, MenuConfigStore.current().config(), false, partialTick);
    }
}
