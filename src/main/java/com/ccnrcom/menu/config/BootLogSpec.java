/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.BootMeter;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.VAlign;
import java.util.List;

/**
 * 启动日志那一层（{@code menu.json} 里的 {@code bootLog}）——纯数据，可单测。
 *
 * <p>它**不是**一种背景类型，而是压在背景与图标之间的独立一层：背景图在下、日志在中、
 * 图标与按钮在上。做成背景类型的话，元素图标与按钮永远只能压在它下面，
 * 「图标盖住日志」这个层级就没法表达。
 *
 * @param enabled 关掉后这一层完全不出现（默认关：默认配置必须「装上等于没装」）
 * @param builtin 内置驱动名（{@code assets/ccnr_menu/bootlog/<名>.txt}），空串表示不用内置
 * @param file 自定义驱动（相对 {@code config/ccnr_menu/}）；非空时优先于 {@code builtin}
 * @param spinner 「进行中」那一行的文案变体（先 {@code ***} 再 {@code OK}）；空表表示不插这一行
 * @param spacingMs 两行之间的节奏：越小打越快
 * @param scale 文字缩放倍数（1.0 = 原版字号）
 * @param x 横向锚点分数；{@code align: right} 时它是文字块的**右边缘**
 * @param y 纵向锚点分数；日志从这一行往下长，超出时自动向上顶（永远贴底）
 * @param align 锚点语义：{@code x} 落在文字块的哪一条竖边上。**所有界面共用这一个**——
 *     曾经做过「主菜单靠右、其它界面靠左 + 滑动过渡」，实测在窄窗口下会把整块推出屏幕，
 *     而且两块内容位置不同反而更难读，已改回单一锚点。
 * @param valign 纵向锚点语义
 * @param color 统一文字色；{@link #NO_COLOR} 表示按行类型上色（绿/黄/红）
 * @param meter 进度条外观（{@code systemd}/{@code bracket}/{@code plymouth}）
 * @param spinnerWidth 进度条宽度（字符数）
 * @param onAllScreens 原版那些泥土界面上是否也显示这一层（默认 true：它本来就是通用层）
 */
public record BootLogSpec(
        boolean enabled,
        String builtin,
        String file,
        List<String> spinner,
        long spacingMs,
        float scale,
        double x,
        double y,
        Align align,
        VAlign valign,
        int color,
        BootMeter meter,
        int spinnerWidth,
        boolean onAllScreens) {

    /** 「按行类型上色」的哨兵值。 */
    public static final int NO_COLOR = 0x01000000;

    /** 默认：关着，什么都不显示。 */
    public static final BootLogSpec NONE = new BootLogSpec(
            false,
            "",
            "",
            List.of(),
            95,
            1f,
            0.02,
            0.04,
            Align.LEFT,
            VAlign.TOP,
            NO_COLOR,
            BootMeter.SYSTEMD,
            12,
            true);

    /**
     * 内置的那一份（{@code bootlog/ubuntu.txt}）：靠左，所有界面同一个位置。
     *
     * <p>靠左 + {@code y: 0.04} 是实测下来最稳的一套：文字块宽度随行内容变化，
     * 靠左时它只向右生长，永远不会被推出屏幕（靠右则会随宽度往左爬，
     * 窄窗口下会整块移出可视区——这是踩过的坑）。
     */
    public static BootLogSpec builtinDefault() {
        return new BootLogSpec(
                true,
                "ubuntu",
                "",
                List.of(
                        "A start job is running for Hold until boot process finishes up",
                        "[  OK  ] Reached target Cloud-init target"),
                95,
                1f,
                0.02,
                0.04,
                Align.LEFT,
                VAlign.TOP,
                NO_COLOR,
                BootMeter.SYSTEMD,
                12,
                true);
    }

    public BootLogSpec {
        builtin = builtin == null ? "" : builtin.trim();
        file = file == null ? "" : file.trim();
        spinner = spinner == null ? List.of() : List.copyOf(spinner);
        if (spacingMs < 0) spacingMs = 0;
        if (scale <= 0f) scale = 1f;
        if (align == null) align = Align.LEFT;
        if (valign == null) valign = VAlign.TOP;
        if (meter == null) meter = BootMeter.SYSTEMD;
        if (spinnerWidth < 1 || spinnerWidth > 40) spinnerWidth = Math.min(40, Math.max(1, spinnerWidth));
    }

    /** 这一层有没有内容可画（没开、也没给任何驱动时就不画）。 */
    public boolean hasSource() {
        return enabled && (!file.isEmpty() || !builtin.isEmpty());
    }

    /** 统一颜色是否生效。 */
    public boolean hasColor() {
        return color != NO_COLOR && ColorSpec.alpha(color) > 0;
    }

    /** 有没有定义「进行中」那一行。 */
    public boolean hasSpinner() {
        return !spinner.isEmpty();
    }

    /** 屏幕上的行距（像素）：原版行高乘缩放，且不小于字号本身（否则会糊成一片）。 */
    public int lineHeight(int baseLineHeight) {
        return Math.max(baseLineHeight, Math.round(baseLineHeight * scale));
    }
}
