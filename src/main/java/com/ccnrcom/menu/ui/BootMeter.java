/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.Locale;

/**
 * 进度条的**长相**（纯数据）。
 *
 * <p>三种形状对应三种真实见到的「启动卡住」画面：
 * {@link #SYSTEMD} 是 Ubuntu 控制台上的 {@code [ ***   ]}；
 * {@link #BRACKET} 是 Debian/initramfs 的 {@code [####    ]}；
 * {@link #PLYMOUTH} 是图形启动画面的无括号圆点 {@code ••••••·····}（用 {@code *} 与 {@code .} 代替）。
 *
 * @param left 左括号（可空）
 * @param right 右括号（可空）
 * @param filled 已填充字符
 * @param empty 未填充字符
 * @param width 括号内宽度（字符数）
 */
public record BootMeter(String left, String right, char filled, char empty, int width) {

    /** {@code [ ***     ]}：Ubuntu 控制台默认。 */
    public static final BootMeter SYSTEMD = new BootMeter("[", "]", '*', ' ', 12);

    /** {@code [####    ]}：initramfs / Debian 风格。 */
    public static final BootMeter BRACKET = new BootMeter("[", "]", '#', '-', 20);

    /** {@code ***······}：Plymouth 图形启动的无括号圆点。 */
    public static final BootMeter PLYMOUTH = new BootMeter("", "", '*', '.', 16);

    private static final int MIN_WIDTH = 1;
    private static final int MAX_WIDTH = 200;

    public BootMeter {
        left = left == null ? "" : left;
        right = right == null ? "" : right;
        if (width < MIN_WIDTH || width > MAX_WIDTH) width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, width));
    }

    /** 画这一时刻的进度条。{@code progress} 越界会被收敛（配置/计时出错也不该画出负数长度）。 */
    public String bar(float progress) {
        float p = Math.min(1f, Math.max(0f, progress));
        int filled = Math.round(p * width);
        StringBuilder sb = new StringBuilder(left.length() + width + right.length());
        sb.append(left);
        for (int i = 0; i < width; i++) sb.append(i < filled ? filled() : empty());
        return sb.append(right).toString();
    }

    /** 配置名 → 形状；认不出返回 {@link #SYSTEMD}。 */
    public static BootMeter parse(String raw) {
        if (raw == null) return SYSTEMD;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "bracket", "hash", "initramfs" -> BRACKET;
            case "plymouth", "dots", "kernel" -> PLYMOUTH;
            default -> SYSTEMD;
        };
    }
}
