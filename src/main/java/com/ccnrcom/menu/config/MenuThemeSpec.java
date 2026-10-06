/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * 菜单配色（纯数据，可单测）。
 *
 * <p>默认值是一套「深色半透明 + 天蓝强调」的现代配色：半透明底让背景动画透出来，
 * 而 {@code backdrop} 那层压暗是**可读性的保障**——一张高亮/高对比的动图背景会让白字看不清，
 * 因此默认压一层 40% 的黑，而不是靠作者自己记得调。
 *
 * @param backdrop 背景之上、文字之下的压暗层（{@code #00000000} 表示不压）
 * @param buttonFill 按钮底色
 * @param buttonFillHover 按钮悬停底色
 * @param buttonBorder 按钮描边
 * @param buttonBorderHover 按钮悬停描边
 * @param buttonText 按钮文字
 * @param buttonTextHover 按钮悬停文字
 * @param accent 强调色（按钮左侧竖条）
 * @param labelText 标题/文字元素颜色
 */
public record MenuThemeSpec(
        int backdrop,
        int buttonFill,
        int buttonFillHover,
        int buttonBorder,
        int buttonBorderHover,
        int buttonText,
        int buttonTextHover,
        int accent,
        int labelText) {

    /**
     * 默认配色：深色半透明 + **CCNR 图标自带的品牌青**（{@code #4FD1E0}）。
     *
     * <p>强调色取自图标动画版里的 {@code theme-dark} 变体（{@code --ccnr-accent: #4FD1E0}），
     * 这样菜单按钮的强调条与图标是同一套色，不需要作者自己去比对色值。
     */
    public static final MenuThemeSpec DEFAULT = new MenuThemeSpec(
            0x66000000, 0xB0121216, 0xD01E1E26, 0xFF3C3C46, 0xFF4FD1E0, 0xFFE6E6EE, 0xFFFFFFFF, 0xFF4FD1E0, 0xFFCFCFD8);

    /** 从 {@code theme} 对象解析；缺失/非法字段用默认值并记警告。 */
    public static MenuThemeSpec parse(JsonObject root, List<String> warnings) {
        if (root == null || !root.has("theme") || !root.get("theme").isJsonObject()) {
            return DEFAULT;
        }
        JsonObject o = root.getAsJsonObject("theme");
        return new MenuThemeSpec(
                color(o, "backdrop", DEFAULT.backdrop(), warnings),
                color(o, "buttonFill", DEFAULT.buttonFill(), warnings),
                color(o, "buttonFillHover", DEFAULT.buttonFillHover(), warnings),
                color(o, "buttonBorder", DEFAULT.buttonBorder(), warnings),
                color(o, "buttonBorderHover", DEFAULT.buttonBorderHover(), warnings),
                color(o, "buttonText", DEFAULT.buttonText(), warnings),
                color(o, "buttonTextHover", DEFAULT.buttonTextHover(), warnings),
                color(o, "accent", DEFAULT.accent(), warnings),
                color(o, "labelText", DEFAULT.labelText(), warnings));
    }

    private static int color(JsonObject o, String key, int def, List<String> warnings) {
        String raw = JsonUtil.str(o, key, null);
        if (raw == null) return def;
        Integer parsed = ColorSpec.parse(raw);
        if (parsed == null) {
            warnings.add(
                    "theme." + key + " 不是合法颜色: '" + raw + "'（写法 #RRGGBB 或 #AARRGGBB）→ 已用默认色 " + ColorSpec.toHex(def));
            return def;
        }
        return parsed;
    }
}
