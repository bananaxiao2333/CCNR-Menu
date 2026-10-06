/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.ui;

import com.ccnrcom.menu.config.MenuElement;
import com.ccnrcom.menu.config.MenuThemeSpec;
import com.ccnrcom.menu.ui.ButtonStyle;
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

    /** 悬停时底衬的加倍系数：10% → 15%，让"鼠标在这一行上"多一个通道（不是只靠文字变色）。 */
    private static final float HOVER_BACKDROP_FACTOR = 1.5f;

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
     * 按钮的唯一画法。两种外观（{@link ButtonStyle}）：
     *
     * <ul>
     *   <li>{@code SOLID}：底色 + 1px 描边 + 左侧强调竖条 + **居中**标签。</li>
     *   <li>{@code TEXT}：**只有文字**，不画底色/描边/竖条，标签从矩形**左边缘**起画。
     *       为什么左对齐：纯文字按钮的视觉锚点就是文字的左边，
     *       在没有底色可以参照的情况下居中会让一列按钮的文字参差不齐。</li>
     * </ul>
     *
     * <p>两种外观都遵守同一条纪律：**键盘焦点与鼠标悬停必须用两个不同的视觉通道**表达。
     * 实心底用「内描边 = 焦点、外描边色 = 悬停」；纯文字没有描边可用，
     * 于是用「文字下划线 = 焦点、文字变色 = 悬停」。共用通道的症状是
     * 「Tab 选中之后鼠标再移上去，就看不出到底选中了哪一个」。
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
        int textColor = buttonTextColor(hovered, colorOverride);
        int textY = y1 + (y2 - y1 - font.lineHeight) / 2 + 1;

        // 底衬：画在文字之下、覆盖整个按钮矩形。两种外观都要——
        // text 外观本来一点底色都没有，背景一花文字就淹没；solid 外观的底色是半透明的，
        // 底衬顺带把**列色带**挡住，字不会被色带的边界切着走。
        int back = ColorSpec.scaleAlpha(spec.buttonBackdrop(), hovered ? HOVER_BACKDROP_FACTOR : 1f);
        if (ColorSpec.alpha(back) > 0) gfx.fill(x1, y1, x2, y2, back);

        if (spec.buttonStyle() == ButtonStyle.TEXT) {
            gfx.drawString(font, label, x1, textY, textColor, true);
            if (focused) {
                int underline = textY + font.lineHeight;
                gfx.fill(
                        x1, underline, x1 + font.width(label), underline + 1, ColorSpec.withAlpha(spec.accent(), 0xB0));
            }
            return;
        }

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
        gfx.drawCenteredString(font, label, (x1 + x2) / 2, textY, textColor);
    }

    /** 当前按钮外观（布局要用它决定按钮没写宽度时怎么办）。 */
    public ButtonStyle buttonStyle() {
        return spec.buttonStyle();
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
