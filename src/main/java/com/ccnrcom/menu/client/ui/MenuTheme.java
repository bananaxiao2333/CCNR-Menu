/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.ui;

import com.ccnrcom.menu.config.MenuElement;
import com.ccnrcom.menu.config.MenuThemeSpec;
import com.ccnrcom.menu.ui.ColorSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 菜单的绘制令牌与**共享绘制入口**（颜色全部来自配置，禁止在界面代码里写裸 {@code 0x} 色值）。
 *
 * <p>沿用 CCNR 系列的界面纪律：同类构件只有一处画法。按钮、文字、压暗层都从这里走，
 * 这样「按钮悬停色改了但文字颜色忘了改」这类只在一处发生的不一致就不会出现。
 */
public final class MenuTheme {

    private final MenuThemeSpec spec;

    public MenuTheme(MenuThemeSpec spec) {
        this.spec = spec == null ? MenuThemeSpec.DEFAULT : spec;
    }

    public MenuThemeSpec spec() {
        return spec;
    }

    /** 背景之上、内容之下的压暗层（保证文字在任何动画背景上都读得清）。 */
    public void drawBackdrop(GuiGraphics gfx, int screenWidth, int screenHeight, float alpha) {
        int color = ColorSpec.scaleAlpha(spec.backdrop(), Math.min(1f, Math.max(0f, alpha)));
        if (ColorSpec.alpha(color) == 0) return;
        gfx.fill(0, 0, screenWidth, screenHeight, color);
    }

    /**
     * 按钮的唯一画法：底色 + 1px 描边 + 左侧强调竖条 + 居中标签。
     *
     * <p>键盘焦点（{@code focused}）用**内描边**表示，鼠标悬停用**外描边颜色**表示：
     * 两者可能同时成立（Tab 选中后鼠标又移上去），用同一个视觉通道表达会看不出到底选中了谁。
     */
    public void drawButton(
            GuiGraphics gfx,
            Font font,
            int x1,
            int y1,
            int x2,
            int y2,
            boolean hovered,
            boolean focused,
            Component label,
            int colorOverride) {
        int fill = hovered ? spec.buttonFillHover() : spec.buttonFill();
        int border = hovered ? spec.buttonBorderHover() : spec.buttonBorder();
        gfx.fill(x1, y1, x2, y2, fill);
        outlined(gfx, x1, y1, x2, y2, border);
        // 左侧强调竖条：颜色恒为 accent（悬停不变），它是这套视觉语言的固定构件
        int accent = spec.accent();
        gfx.fill(x1, y1, x1 + 2, y2, ColorSpec.scaleAlpha(accent, hovered ? 1f : 0.55f));
        if (focused) {
            outlined(gfx, x1 + 2, y1 + 2, x2 - 2, y2 - 2, ColorSpec.withAlpha(accent, 0x80));
        }
        int textColor = buttonTextColor(hovered, colorOverride);
        int textY = y1 + (y2 - y1 - font.lineHeight) / 2 + 1;
        gfx.drawCenteredString(font, label, (x1 + x2) / 2, textY, textColor);
    }

    /** 按钮文字色：自带颜色优先，否则按悬停取主题色。 */
    public int buttonTextColor(boolean hovered, int colorOverride) {
        if (colorOverride != MenuElement.NO_COLOR) return colorOverride;
        return hovered ? spec.buttonTextHover() : spec.buttonText();
    }

    /** 文字元素颜色：自带颜色优先，否则用主题文字色。 */
    public int labelColor(int colorOverride) {
        return colorOverride != MenuElement.NO_COLOR ? colorOverride : spec.labelText();
    }

    /**
     * 带缩放的文字（标题/标语用）：{@code scale > 1} 时原版字体是**像素放大**，
     * 所以必须按缩放后的尺寸摆放，否则居中会偏。
     */
    public void drawText(
            GuiGraphics gfx, Font font, Component text, int x, int y, float scale, int color, boolean shadow) {
        if (scale == 1f) {
            gfx.drawString(font, text, x, y, color, shadow);
            return;
        }
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0f);
        gfx.pose().scale(scale, scale, 1f);
        gfx.drawString(font, text, 0, 0, color, shadow);
        gfx.pose().popPose();
    }

    /** 1px 描边（四边分别 fill，不依赖圆角渲染后端）。 */
    public static void outlined(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color) {
        gfx.fill(x1, y1, x2, y1 + 1, color);
        gfx.fill(x1, y2 - 1, x2, y2, color);
        gfx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        gfx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }
}
