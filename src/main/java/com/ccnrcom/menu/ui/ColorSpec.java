/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 颜色解析（纯逻辑，可单测）。
 *
 * <p>配置里写的是人写的字符串（{@code "#3AF"}、{@code "#FF3A3AF0"}），而渲染要用的是
 * Minecraft 惯例的 **ARGB 打包 int**（高字节是 alpha，{@code GuiGraphics.fill}/{@code drawString}
 * 都吃这个格式）。
 *
 * <p>为什么解析失败返回 {@code null} 而不是兜底成白色：颜色写错是**作者的错误**，必须能在
 * {@code /ccnr_menu status} 里看见并被指出来；静默变成白色只会让人以为「配置没生效」，
 * 进而去改别的地方。调用方拿到 null 时用默认色并把警告带出去。
 */
public final class ColorSpec {

    /** 不透明白。 */
    public static final int WHITE = 0xFFFFFFFF;

    /** 全透明（{@code fill} 用它等于不画）。 */
    public static final int TRANSPARENT = 0x00000000;

    private ColorSpec() {}

    /**
     * 解析 {@code #RGB} / {@code #RRGGBB} / {@code #AARRGGBB}（{@code #} 与 {@code 0x} 前缀可省略）。
     *
     * @return ARGB 打包 int；不合法返回 {@code null}
     */
    public static Integer parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.isEmpty() || !s.chars().allMatch(ColorSpec::isHexDigit)) return null;
        try {
            return switch (s.length()) {
                case 3 ->
                // #RGB 每位重复一次（#3AF == #33AAFF）
                0xFF000000 | (dup(s.charAt(0)) << 16) | (dup(s.charAt(1)) << 8) | dup(s.charAt(2));
                case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
                    // 8 位必须走 Long：0xFFFFFFFF 超出 int 正数范围，Integer.parseInt 会抛异常
                case 8 -> (int) Long.parseLong(s, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析失败时用 {@code fallback}（调用方应同时给出警告）。 */
    public static int parseOr(String raw, int fallback) {
        Integer parsed = parse(raw);
        return parsed == null ? fallback : parsed;
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int hexDigit(char c) {
        return Character.digit(c, 16);
    }

    private static int dup(char c) {
        int v = hexDigit(c);
        return v * 17;
    }

    // ------------------------------------------------------------------
    // 通道读写
    // ------------------------------------------------------------------

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** 归一化到 0..1（{@code GuiGraphics.setColor} 用这个范围）。 */
    public static float redF(int argb) {
        return red(argb) / 255f;
    }

    public static float greenF(int argb) {
        return green(argb) / 255f;
    }

    public static float blueF(int argb) {
        return blue(argb) / 255f;
    }

    public static float alphaF(int argb) {
        return alpha(argb) / 255f;
    }

    /** 换掉 alpha 通道。 */
    public static int withAlpha(int argb, int a) {
        return (clamp255(a) << 24) | (argb & 0x00FFFFFF);
    }

    /** 按倍数缩放 alpha（用于按不透明度透明度绘制）。 */
    public static int scaleAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, factor)));
    }

    /** {@code 0xRRGGBB} → {@code 0xAARRGGBB}。 */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
