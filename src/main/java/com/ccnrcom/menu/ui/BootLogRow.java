/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 某一时刻的一行（**纯数据**，上屏与单测都吃这个）。
 *
 * @param kind 行类型（决定左侧标记与颜色）
 * @param text 这一行当前**已经打出来的**文字（逐字显露，返回时已截好）
 * @param meter 进度条状态；没有进度条时为 {@code null}
 * @param progress 进度条填充比例 0..1；没有进度条时为 0
 * @param partial 这一行还没打完（正在逐字出现）——末行光标闪不闪看它
 */
public record BootLogRow(BootLogKind kind, String text, BootMeter meter, float progress, boolean partial) {

    /** 没有进度条的行。 */
    public static BootLogRow plain(BootLogKind kind, String text, boolean partial) {
        return new BootLogRow(kind, text, null, 0f, partial);
    }
}
