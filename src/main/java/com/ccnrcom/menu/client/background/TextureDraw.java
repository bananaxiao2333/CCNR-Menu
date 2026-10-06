/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.Fit;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 「把一张贴图铺满屏幕」的**共享绘制入口**（三类背景都走这里）。
 *
 * <p>为什么必须共享：铺屏这件事有三个容易各写各写错的地方——等比缩放取整（用 ceil 还是 floor）、
 * 着色与不透明度怎么叠加、平铺时 blit 的次数上限。分开实现就会出现「图片背景留黑边、
 * 精灵图背景不留」这类只在某一种背景下复现的差异，而它们本该是同一段代码。
 *
 * <p>平铺上限是刻意的**防呆**：一张 4×4 像素的无缝纹理在 1920×1080 上要画
 * {@code 480 × 270 = 129,600} 次 blit，每帧如此——这不是「有点慢」，而是直接把帧率打到个位数。
 * 超过上限时退化为一次拉伸，并打一条日志说明原因。
 */
public final class TextureDraw {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    /** 单帧平铺次数上限。 */
    public static final int MAX_TILES = 1024;

    private static boolean tileLimitWarned;

    private TextureDraw() {}

    /**
     * 把贴图的 {@code src} 区域按 {@code fit} 铺满屏幕。
     *
     * @param tint 与原图相乘的颜色（白色 = 原色）
     * @param alpha 额外的不透明度（0..1，与 {@code tint} 的 alpha 相乘）
     */
    public static void drawFitted(
            GuiGraphics gfx,
            ResourceLocation location,
            int textureWidth,
            int textureHeight,
            MenuGeometry.Rect src,
            Fit fit,
            int screenWidth,
            int screenHeight,
            int tint,
            float alpha) {
        if (location == null || src == null || src.w() <= 0 || src.h() <= 0) return;
        float a = Math.min(1f, Math.max(0f, alpha)) * ColorSpec.alphaF(tint);
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        gfx.setColor(ColorSpec.redF(tint), ColorSpec.greenF(tint), ColorSpec.blueF(tint), a);
        try {
            if (fit == Fit.TILE && tileCount(screenWidth, screenHeight, src) > MAX_TILES) {
                if (!tileLimitWarned) {
                    tileLimitWarned = true;
                    LOGGER.warn(
                            "[CCNR-Menu] 背景平铺次数超过上限 {}（贴图 {}x{}），已改为拉伸铺满；" + "请把 fit 改成 cover 或换更大的无缝纹理",
                            MAX_TILES,
                            src.w(),
                            src.h());
                }
                blitOnce(
                        gfx,
                        location,
                        src,
                        new MenuGeometry.Rect(0, 0, screenWidth, screenHeight),
                        textureWidth,
                        textureHeight);
            } else if (fit == Fit.TILE) {
                int tilesX = MenuGeometry.tileCount(screenWidth, src.w());
                int tilesY = MenuGeometry.tileCount(screenHeight, src.h());
                for (int ty = 0; ty < tilesY; ty++) {
                    for (int tx = 0; tx < tilesX; tx++) {
                        blitOnce(
                                gfx,
                                location,
                                src,
                                new MenuGeometry.Rect(tx * src.w(), ty * src.h(), src.w(), src.h()),
                                textureWidth,
                                textureHeight);
                    }
                }
            } else {
                MenuGeometry.Rect dst = MenuGeometry.fit(fit, src.w(), src.h(), screenWidth, screenHeight);
                blitOnce(gfx, location, src, dst, textureWidth, textureHeight);
            }
        } finally {
            // 必须复位：setColor 是全局状态，忘了复位会让**之后所有**的界面绘制都带上这层颜色
            gfx.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * 把贴图的 {@code src} 像素区按**调用方算好的**目标矩形画上屏（着色 + 不透明度）。
     *
     * <p>存在的理由：{@link #drawFitted} 把「算目标矩形」和「画」绑在一起，
     * 而轮播背景需要在 fit 的结果上再做「推近 + 偏移」（{@link MenuGeometry#kenBurns}），
     * 于是必须能自己拿矩形来画。着色/混合/复位的实现仍然只有这一处，
     * 免得两个绘制入口各自漂移。
     */
    public static void drawAt(
            GuiGraphics gfx,
            ResourceLocation location,
            MenuGeometry.Rect src,
            MenuGeometry.Rect dst,
            int textureWidth,
            int textureHeight,
            int tint,
            float alpha) {
        if (location == null || src == null || dst == null || src.w() <= 0 || src.h() <= 0) return;
        if (dst.w() <= 0 || dst.h() <= 0) return;
        float a = Math.min(1f, Math.max(0f, alpha)) * ColorSpec.alphaF(tint);
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        gfx.setColor(ColorSpec.redF(tint), ColorSpec.greenF(tint), ColorSpec.blueF(tint), a);
        try {
            blit(gfx, location, src, dst, textureWidth, textureHeight);
        } finally {
            // 必须复位：setColor 是全局状态，忘了复位会让**之后所有**的界面绘制都带上这层颜色
            gfx.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** 单次 blit：把贴图的 {@code src} 像素区画进 {@code dst} 屏幕区（元素图片也走这里）。 */
    public static void blit(
            GuiGraphics gfx,
            ResourceLocation location,
            MenuGeometry.Rect src,
            MenuGeometry.Rect dst,
            int textureWidth,
            int textureHeight) {
        gfx.blit(
                location,
                dst.x(),
                dst.y(),
                dst.w(),
                dst.h(),
                (float) src.x(),
                (float) src.y(),
                src.w(),
                src.h(),
                textureWidth,
                textureHeight);
    }

    private static void blitOnce(
            GuiGraphics gfx,
            ResourceLocation location,
            MenuGeometry.Rect src,
            MenuGeometry.Rect dst,
            int textureWidth,
            int textureHeight) {
        blit(gfx, location, src, dst, textureWidth, textureHeight);
    }

    private static long tileCount(int screenWidth, int screenHeight, MenuGeometry.Rect src) {
        return (long) MenuGeometry.tileCount(screenWidth, src.w()) * MenuGeometry.tileCount(screenHeight, src.h());
    }
}
