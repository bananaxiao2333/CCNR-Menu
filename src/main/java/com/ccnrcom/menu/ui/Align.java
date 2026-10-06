/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/** 水平锚点：{@code x} 分数落在元素的哪一条竖边上（默认居中）。 */
public enum Align {
    LEFT,
    CENTER,
    RIGHT;

    /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
    public static Align parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "left", "start" -> LEFT;
            case "center", "centre", "middle" -> CENTER;
            case "right", "end" -> RIGHT;
            default -> null;
        };
    }
}
