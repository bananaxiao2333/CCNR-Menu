/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.MarkSpec;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.ui.MenuGeometry;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把 {@link MarkSpec} 里那一个居中标志画上屏。
 *
 * <p>它是**内容**而不是背景，但归 {@link ScreenBackgrounds} 持有：泥土页面没有自己的屏幕类，
 * 唯一稳定的绘制点就是背景钩子，所以贴图的生命周期跟着背景一起走
 * （与背景同一份指纹、同一个释放时机，不会出现「背景换了标志还留着」）。
 *
 * <p>绘制顺序是刻意的：**背景 → 压暗层 → 标志**。压暗层是给文字让路的东西，
 * 标志要是画在它下面就会被一起压暗——一个白色 logo 被压成灰的，看起来像没加载出来。
 *
 * <p>贴图懒加载：第一次真的要画的时候才读文件。整个配置里没开标志的人，
 * 连一次文件访问都不会发生。
 */
public final class MarkRenderer {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private final MarkSpec spec;

    private FileTexture texture;
    private boolean attempted;

    public MarkRenderer(MarkSpec spec) {
        this.spec = spec == null ? MarkSpec.NONE : spec;
    }

    /** 加载失败**不重试**（缺文件是作者的问题，不是每帧都要报一次的运行时故障）。 */
    private FileTexture texture() {
        if (texture != null || attempted) return texture;
        attempted = true;
        try {
            java.nio.file.Path file = MenuConfigIO.resolveAsset(spec.file());
            if (!java.nio.file.Files.isRegularFile(file)) {
                LOGGER.warn("[CCNR-Menu] mark 素材不存在，泥土页面上不会画标志: {}（请把文件放进 {}）", file, MenuConfigIO.configDir());
                return null;
            }
            texture = FileTexture.load(file, FileTexture.keyFor(file, MenuConfigIO.lastModified(file)));
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] mark 素材加载失败，泥土页面上不会画标志: {} —— {}", spec.file(), e.toString());
        }
        return texture;
    }

    /**
     * 画标志。
     *
     * @param ownMenu 当前是不是本模组自己的主菜单（决定 {@code onMainMenu} 是否生效）
     */
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float alpha, boolean ownMenu) {
        if (!spec.enabled()) return;
        if (ownMenu && !spec.onMainMenu()) return;
        FileTexture tex = texture();
        if (tex == null) return;

        int w = spec.width() == MarkSpec.AUTO ? tex.width() : spec.width();
        int h = Math.max(1, Math.round(w * tex.height() / (float) tex.width()));
        MenuGeometry.Rect rect =
                MenuGeometry.place(spec.x(), spec.y(), spec.align(), spec.valign(), w, h, screenWidth, screenHeight);

        TextureDraw.drawAt(
                gfx,
                tex.location(),
                new MenuGeometry.Rect(0, 0, tex.width(), tex.height()),
                rect,
                tex.width(),
                tex.height(),
                com.ccnrcom.menu.ui.ColorSpec.WHITE,
                spec.opacity() * alpha);
    }

    /** 释放贴图；**必须幂等**。 */
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        attempted = false;
    }
}
