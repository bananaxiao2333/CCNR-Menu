/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.ui.Fit;
import com.ccnrcom.menu.ui.MenuGeometry;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 静态图片背景（{@code background.type = image}）。
 *
 * <p>贴图在构造时就同步加载完（一张图的解码是毫秒级），所以没有「未就绪」状态，
 * 也不需要兜底背景——加载失败的情形由 {@link BackgroundFactory} 处理成原版全景图。
 */
public final class ImageBackground implements BackgroundRenderer {

    private final FileTexture texture;
    private final Fit fit;
    private final int tint;
    private final float opacity;

    public ImageBackground(FileTexture texture, BackgroundSpec spec) {
        this.texture = texture;
        this.fit = spec.fit();
        this.tint = spec.tint();
        this.opacity = spec.opacity();
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        TextureDraw.drawFitted(
                gfx,
                texture.location(),
                texture.width(),
                texture.height(),
                new MenuGeometry.Rect(0, 0, texture.width(), texture.height()),
                fit,
                screenWidth,
                screenHeight,
                tint,
                opacity * alpha);
    }

    @Override
    public void close() {
        texture.close();
    }
}
