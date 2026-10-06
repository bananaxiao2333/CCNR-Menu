/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 配置驱动的菜单按钮。
 *
 * <p>为什么继承 {@link AbstractWidget} 而不是自己画方块 + 自己做命中测试：
 * 键盘导航（Tab/方向键）、旁白（narration）、悬停提示、禁用态这些行为在
 * {@code AbstractWidget} 里已经有一份经过验证的实现，自己重做一遍的代价是**只有用键盘的人**
 * 才会发现的缺陷（而作者几乎总是用鼠标）。
 */
public final class MenuButton extends AbstractWidget {

    private final MenuTheme theme;
    private final int colorOverride;
    private final Runnable onPress;

    /**
     * @param colorOverride 文字颜色覆盖（{@code MenuElement.NO_COLOR} = 用主题色）
     * @param onPress 点击回调（动作的执行在 {@code MenuActions}，这里只负责触发）
     */
    public MenuButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            MenuTheme theme,
            int colorOverride,
            Runnable onPress) {
        super(x, y, width, height, label);
        this.theme = theme;
        this.colorOverride = colorOverride;
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        theme.drawButton(
                gfx,
                Minecraft.getInstance().font,
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                isHoveredOrFocused(),
                isFocused(),
                getMessage(),
                colorOverride);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
