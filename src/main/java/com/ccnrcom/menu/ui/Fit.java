/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/** 贴图铺满屏幕的方式（背景图的 {@code fit} 字段）。 */
public enum Fit {
    /** 等比放大到**完全覆盖**屏幕，超出部分裁掉。默认值：背景不出现黑边。 */
    COVER,
    /** 等比缩放到**完整可见**，不足的部分留黑边。适合不想裁掉任何内容的插画。 */
    CONTAIN,
    /** 直接拉伸到屏幕尺寸（会变形）。适合本身就是屏幕比例的图。 */
    STRETCH,
    /** 原始尺寸居中显示，不缩放。 */
    CENTER,
    /** 原始尺寸从左上角开始平铺（适合无缝纹理）。 */
    TILE;

    /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
    public static Fit parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "cover", "fill" -> COVER;
            case "contain", "fit" -> CONTAIN;
            case "stretch", "resize" -> STRETCH;
            case "center", "centre" -> CENTER;
            case "tile", "repeat" -> TILE;
            default -> null;
        };
    }
}
