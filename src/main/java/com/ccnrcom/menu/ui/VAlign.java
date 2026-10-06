/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/** 垂直锚点：{@code y} 分数落在元素的哪一条横边上（默认居中）。 */
public enum VAlign {
    TOP,
    MIDDLE,
    BOTTOM;

    /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
    public static VAlign parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "top", "start" -> TOP;
            case "middle", "center", "centre" -> MIDDLE;
            case "bottom", "end" -> BOTTOM;
            default -> null;
        };
    }
}
