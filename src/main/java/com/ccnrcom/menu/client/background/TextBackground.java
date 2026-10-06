/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.TextSpec;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.ccnrcom.menu.ui.TextWave;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 黑底彩色文字背景（{@code background.type = "text"}）。
 *
 * <p>它不加载任何素材：先在整屏铺一层底色（默认纯黑），再在上面按
 * {@link TextSpec} 画出分段装饰文字，并做两件事：
 *
 * <ol>
 *   <li><b>颜色流动</b>：每个字符按 {@link TextWave#colorFor} 在调色板里取色，
 *       色带随时间整体推进；</li>
 *   <li><b>逐字出现</b>：按 {@link TextWave#visibleCount} 决定当前画到第几个字，
 *       {@code loop: false} 时写一遍就停，颜色继续流动。</li>
 * </ol>
 *
 * <p>两个实现上的选择值得写下来：
 *
 * <ul>
 *   <li><b>逐字符 {@code drawString} 是这里唯一可行的画法</b>：原版字体没有「按字符给颜色」的接口，
 *       而一次性画整行就只能整行同色。代价是每帧几百次 {@code drawString}，
 *       对这个量级（几十到几百个字符）完全不是问题。</li>
 *   <li><b>每行只 push 一次 pose</b>：缩放是**整行共享**的，所以先把原点挪到行首、按 {@code scale}
 *       缩放一次，之后每个字形都在未缩放的坐标系里按累加宽度摆放。
 *       逐字形 push/pop 会让每帧多出几百次矩阵操作，且完全没有必要。</li>
 * </ul>
 *
 * <p>字符宽度用 {@code font.width(单个字形)} 累加，而不是除以字符数求平均——
 * 原版字体是**比例字体**（{@code i} 与 {@code W} 宽度不同，CJK 更宽），
 * 按平均宽度摆放会让长句子越到后面偏得越多。
 */
public final class TextBackground implements BackgroundRenderer {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private final BackgroundSpec spec;
    private final TextSpec text;
    private final long startMs = Util.getMillis();

    /** 内容比屏幕宽时只警告一次（窗口拖动时不该刷屏，也不该悄悄裁掉）。 */
    private boolean overflowLogged;

    public TextBackground(BackgroundSpec spec) {
        this.spec = spec;
        this.text = spec.text();
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        gfx.fill(0, 0, screenWidth, screenHeight, ColorSpec.scaleAlpha(spec.color(), alpha));
        if (text.segments().isEmpty() || screenWidth <= 0 || screenHeight <= 0) return;

        Font font = Minecraft.getInstance().font;
        List<String> lines = text.lines();
        int[] offsets = text.lineOffsets();

        int unitWidth = 0;
        for (String line : lines) unitWidth = Math.max(unitWidth, font.width(line));

        // 字号是绝对像素，而 GUI 尺寸随窗口变化；小窗口下宁可整体小一号，也不要切掉半行字
        int scale = TextWave.fitScale(text.scale(), unitWidth, screenWidth);
        if (scale != text.scale() && !overflowLogged) {
            overflowLogged = true;
            LOGGER.info(
                    "[CCNR-Menu] 文字背景在 {} 像素宽的界面上放不下 1:{} 的字号（1 倍宽 {}），已自动降到 1:{}；"
                            + "想让字号固定就调小 background.text.scale 或减少字数",
                    screenWidth,
                    text.scale(),
                    unitWidth,
                    scale);
        }

        int lineHeight = font.lineHeight * scale;
        int gap = text.lineGap();
        int blockWidth = unitWidth * scale;
        int blockHeight = lines.size() * lineHeight + (lines.size() - 1) * gap;
        if (blockWidth > screenWidth && !overflowLogged) {
            overflowLogged = true;
            LOGGER.warn(
                    "[CCNR-Menu] 文字背景即使缩到 1 倍仍有 {} 像素宽，超过当前界面宽度 {}，会被裁掉——" + "请减少 background.text.segments 的字数",
                    blockWidth,
                    screenWidth);
        }

        MenuGeometry.Rect block = MenuGeometry.place(
                text.x(), text.y(), text.align(), text.valign(), blockWidth, blockHeight, screenWidth, screenHeight);

        long cycle = TextWave.cycleMs(text.totalChars(), text.charMs(), text.holdMs());
        long elapsed = TextWave.looped(Util.getMillis() - startMs, cycle, text.loop());
        int visible = TextWave.visibleCount(text.totalChars(), elapsed, text.charMs());

        PoseStack pose = gfx.pose();
        for (int li = 0; li < lines.size(); li++) {
            int lineY = block.y() + li * (lineHeight + gap);
            pose.pushPose();
            pose.translate(block.x(), lineY, 0f);
            pose.scale(scale, scale, 1f);

            int x = 0;
            int index = offsets[li];
            String line = lines.get(li);
            int length = line.length();
            for (int ci = 0; ci < length; ) {
                int codePoint = line.codePointAt(ci);
                int charCount = Character.charCount(codePoint);
                String glyph = line.substring(ci, ci + charCount);
                if (index < visible) {
                    int color = TextWave.colorFor(text.palette(), index, elapsed, text.stepMs(), ColorSpec.WHITE);
                    gfx.drawString(font, glyph, x, 0, ColorSpec.scaleAlpha(color, alpha), text.shadow());
                }
                x += font.width(glyph);
                ci += charCount;
                index++;
            }
            pose.popPose();
        }
    }

    @Override
    public void close() {
        // 没有需要释放的资源：这里不持有贴图、不解码任何文件
    }
}
