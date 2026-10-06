/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.Locale;

/**
 * 按钮外观（纯枚举，可单测）。
 *
 * <p>两种外观对应两种完全不同的设计意图：
 *
 * <ul>
 *   <li>{@link #SOLID}：有底色 + 描边 + 左侧强调竖条。适合**图片背景**——背景的明暗不可控，
 *       一块稳定的底色是文字可读性的保障。</li>
 *   <li>{@link #TEXT}：**只有文字**，不画任何底色/描边。适合**纯色或自绘背景**——
 *       在一张黑底动画背景上，再压一排半透明方块只会把背景切碎；
 *       文字直接浮在背景上反而更干净（前提是背景作者保证对比度）。</li>
 * </ul>
 *
 * <p>为什么是配置项而不是「自动判断背景类型」：外观是**设计决定**，不是可以推导出来的结论。
 * 一张亮色图片背景配纯文字按钮完全可以是有意为之，模组不该替作者否决它。
 */
public enum ButtonStyle {

    /** 底色 + 描边 + 强调竖条（默认）。 */
    SOLID,
    /** 无背景纯文字。 */
    TEXT;

    /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
    public static ButtonStyle parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "solid", "filled", "box", "button" -> SOLID;
            case "text", "plain", "flat", "bare", "label" -> TEXT;
            default -> null;
        };
    }

    /** 可用取值（警告文案用，避免文案与代码漂开）。 */
    public static String names() {
        return "solid/text";
    }
}
