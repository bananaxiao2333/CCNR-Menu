/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 原版全景图背景。
 *
 * <p>这是 {@code background.type = vanilla} 的实现，也是**其它背景类型的兜底**：
 * 素材文件缺失、格式不对、解码失败时都退到这里。理由很简单——菜单必须能看、能点，
 * 「背景坏了」绝不该升级成「主菜单打不开」。
 *
 * <p>绘制方式照抄原版 {@code TitleScreen}：先让 {@link PanoramaRenderer} 转全景，
 * 再用 16×128 的渐变遮罩铺满屏幕（那张遮罩是竖条渐变，拉伸后就是上下压暗的暗角效果）。
 * 遮罩这一笔不能省，否则按钮文字会压在明亮的全景上而看不清。
 */
public final class VanillaBackground implements BackgroundRenderer {

    /** 原版的全景遮罩贴图（{@code TitleScreen} 里是私有的，这里按同一路径引用）。 */
    private static final ResourceLocation PANORAMA_OVERLAY =
            new ResourceLocation("textures/gui/title/background/panorama_overlay.png");

    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        float a = Math.min(1f, Math.max(0f, alpha));
        panorama.render(partialTick, a);
        RenderSystem.enableBlend();
        gfx.setColor(1f, 1f, 1f, a);
        // 参数含义：把贴图的 (0,0)-(16,128) 区域拉伸到整屏
        gfx.blit(PANORAMA_OVERLAY, 0, 0, screenWidth, screenHeight, 0f, 0f, 16, 128, 16, 128);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void close() {
        // 原版全景由原版自己管理（TitleScreen.CUBE_MAP 是静态共享资源），这里没有任何所有权
    }
}
