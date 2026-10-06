/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 内存预算门禁。
 *
 * <p>守的是一个很具体的坑：GIF 的**文件体积**与**解压后的内存**完全不成比例。
 * 一个 3MB 的 1920×1080×60 帧 GIF 解出来接近 475MB，足以把游戏拖进 GC 风暴，
 * 而报错只会说「内存不足」，指不到那张图上。所以「超预算就整倍缩小」这条规则必须被断言钉住，
 * 尤其是「缩放倍数的计算结果」——它决定了玩家加一张大图之后游戏还能不能开。
 */
class GifBudgetTest {

    private static final long MB = 1024L * 1024L;

    @Test
    @DisplayName("小图不缩（scale=1）")
    void smallImageUnscaled() {
        assertEquals(1, GifBudget.scaleFor(640, 360, 20, GifBudget.DEFAULT_BYTES));
        assertEquals(640L * 360 * 4 * 20, GifBudget.estimateBytes(640, 360, 20, 1));
    }

    @Test
    @DisplayName("1920x1080x60 帧（约 475MB）缩到 1/3 后落进 96MB 预算")
    void largeGifIsDownscaled() {
        long raw = GifBudget.estimateBytes(1920, 1080, 60, 1);
        assertTrue(raw > 400 * MB, "原始估算应当远超预算: " + raw / MB + "MB");

        int scale = GifBudget.scaleFor(1920, 1080, 60, GifBudget.DEFAULT_BYTES);

        assertEquals(3, scale);
        assertTrue(GifBudget.estimateBytes(1920, 1080, 60, scale) <= GifBudget.DEFAULT_BYTES);
        assertEquals(640, GifBudget.scaled(1920, scale));
        assertEquals(360, GifBudget.scaled(1080, scale));
    }

    @Test
    @DisplayName("缩到上限仍然超预算时返回上限（由解码器拒绝并给出提示）")
    void cappedAtMaxScale() {
        int scale = GifBudget.scaleFor(4096, 4096, GifBudget.MAX_FRAMES, 1024);
        assertEquals(GifBudget.MAX_SCALE, scale);
        assertTrue(GifBudget.estimateBytes(4096, 4096, GifBudget.MAX_FRAMES, scale) > 1024);
    }

    @Test
    @DisplayName("非法输入不会崩：0/负数预算走默认值，尺寸与帧数至少为 1")
    void degenerateInputs() {
        assertTrue(GifBudget.scaleFor(100, 100, 1, 0) >= 1);
        assertEquals(1, GifBudget.scaled(0, 1), "0 像素也要剩 1 像素（贴图尺寸不能是 0）");
        assertEquals(100, GifBudget.scaled(100, 0), "scale=0 视为 1（不缩）");
        assertEquals(4, GifBudget.estimateBytes(1, 1, 1, 1));
    }
}
