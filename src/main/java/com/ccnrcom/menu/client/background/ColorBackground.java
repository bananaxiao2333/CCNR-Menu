/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.ui.ColorSpec;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 纯色背景（{@code background.type = color}，以及 {@code none} 的黑屏）。
 *
 * <p>存在的理由不是「好看」，而是**排障与降级**：想确认「菜单本身有没有正常渲染」时，
 * 一个纯色背景能把「贴图没加载」与「界面没画出来」两件事分开。
 */
public final class ColorBackground implements BackgroundRenderer {

    private final int color;

    public ColorBackground(int color) {
        this.color = color;
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        gfx.fill(0, 0, screenWidth, screenHeight, ColorSpec.scaleAlpha(color, Math.min(1f, Math.max(0f, alpha))));
    }

    @Override
    public void close() {
        // 无资源
    }
}
