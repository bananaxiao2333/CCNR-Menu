/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.ui.Fit;
import com.ccnrcom.menu.ui.FrameClock;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.ccnrcom.menu.ui.SpriteSheet;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 精灵图序列帧背景（{@code background.type = sheet}）：**推荐的动画方案**。
 *
 * <p>一张 PNG 里横竖排好若干格，每格是一帧；播放时只改采样区域（UV），
 * 于是每帧**零解码、零上传**，全部由 GPU 采样完成。相比 GIF：
 *
 * <table border="1">
 *   <caption>两种动画背景的取舍</caption>
 *   <tr><th></th><th>sheet</th><th>GIF</th></tr>
 *   <tr><td>运行时开销</td><td>每帧一次 blit</td><td>每帧一次整图上传（几百 KB~MB）</td></tr>
 *   <tr><td>内存</td><td>整张 PNG</td><td>解码后 <b>宽×高×4×帧数</b></td></tr>
 *   <tr><td>制作难度</td><td>需要拼图（脚本/工具）</td><td>导出即用</td></tr>
 * </table>
 *
 * <p>起点时间取对象创建时刻，因此每次打开主菜单动画都从头播——这比「接着上次的相位」更符合
 * 「刚进菜单」的观感。
 */
public final class SheetBackground implements BackgroundRenderer {

    private final FileTexture texture;
    private final SpriteSheet sheet;
    private final FrameClock clock;
    private final Fit fit;
    private final int tint;
    private final float opacity;
    private final long startMs = Util.getMillis();

    public SheetBackground(FileTexture texture, BackgroundSpec spec) {
        this.texture = texture;
        this.sheet = spec.sheet();
        this.clock = sheet.clock();
        this.fit = spec.fit();
        this.tint = spec.tint();
        this.opacity = spec.opacity();
    }

    /** 当前应显示的帧号（诊断输出用）。 */
    public int currentFrame() {
        return clock.frameAt(Util.getMillis() - startMs);
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        int frame = clock.frameAt(Util.getMillis() - startMs);
        MenuGeometry.Rect src =
                MenuGeometry.frameUv(frame, sheet.cols(), sheet.rows(), texture.width(), texture.height());
        TextureDraw.drawFitted(
                gfx,
                texture.location(),
                texture.width(),
                texture.height(),
                src,
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
