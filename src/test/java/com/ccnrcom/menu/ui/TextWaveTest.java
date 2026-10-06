/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文字动画数学门禁。
 *
 * <p>这些函数决定「背景到底动没动」，而它们在画面上只有两种失败方式：
 * **完全不动**（参数被算死）与**乱闪**（步长算成 0）。两种都不会抛异常，
 * 所以只能靠断言守住。
 */
class TextWaveTest {

    private static final int[] PALETTE = {0xFF111111, 0xFF222222, 0xFF333333};

    @Test
    @DisplayName("色带随时间整体前移：同一个字符的颜色会变，相邻字符永远差一格")
    void bandAdvancesOverTime() {
        int atStart = TextWave.colorFor(PALETTE, 0, 0, 100, 0xFFFFFFFF);
        int later = TextWave.colorFor(PALETTE, 0, 100, 100, 0xFFFFFFFF);
        assertEquals(PALETTE[0], atStart);
        assertEquals(PALETTE[1], later, "走过一个步长，色带前进一格");
        assertNotEquals(atStart, later, "「颜色不动」是这套背景最可能的故障，必须被钉住");

        // 同一时刻，相邻字符差一格 —— 这才是「色带」而不是「整块一起跳色」
        assertEquals(PALETTE[0], TextWave.colorFor(PALETTE, 0, 0, 100, 0xFFFFFFFF));
        assertEquals(PALETTE[1], TextWave.colorFor(PALETTE, 1, 0, 100, 0xFFFFFFFF));
        assertEquals(PALETTE[2], TextWave.colorFor(PALETTE, 2, 0, 100, 0xFFFFFFFF));
        assertEquals(PALETTE[0], TextWave.colorFor(PALETTE, 3, 0, 100, 0xFFFFFFFF), "色带按调色板长度循环");
    }

    @Test
    @DisplayName("下标可以是负数、时间可以很大（都不会抛异常，也不会取到调色板外）")
    void indexAndTimeWrapSafely() {
        assertEquals(PALETTE[2], TextWave.colorFor(PALETTE, -1, 0, 100, 0xFFFFFFFF));
        assertEquals(PALETTE[1], TextWave.colorFor(PALETTE, -2, 0, 100, 0xFFFFFFFF));
        int far = TextWave.colorFor(PALETTE, 0, Long.MAX_VALUE / 2, 100, 0xFFFFFFFF);
        for (int color : PALETTE) {
            if (color == far) return;
        }
        throw new AssertionError("时间极大时取到了调色板之外的颜色: " + Integer.toHexString(far));
    }

    @Test
    @DisplayName("空调色板返回兜底色（不越界、不抛异常）")
    void emptyPaletteFallsBack() {
        assertEquals(0xFFFFFFFF, TextWave.colorFor(new int[0], 0, 0, 100, 0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, TextWave.colorFor(null, 0, 0, 100, 0xFFFFFFFF));
    }

    @Test
    @DisplayName("打字：第一个字立刻出现，之后每 charMs 多一个，最后停在总字数")
    void typingRevealsOneByOne() {
        assertEquals(1, TextWave.visibleCount(5, 0, 100), "t=0 就该有第一个字，否则切界面会先闪一下空屏");
        assertEquals(1, TextWave.visibleCount(5, 50, 100));
        assertEquals(2, TextWave.visibleCount(5, 100, 100));
        assertEquals(5, TextWave.visibleCount(5, 10_000, 100), "写完之后不会超出总字数");
        assertEquals(5, TextWave.visibleCount(5, 10_000, 0), "charMs=0 表示整块立刻出现");
        assertEquals(0, TextWave.visibleCount(0, 10_000, 100), "没有字就是 0");
    }

    @Test
    @DisplayName("一轮时长 = 写完 + 停留；不循环时时间原样透传")
    void cycleAndLoop() {
        assertEquals(5 * 100 + 200, TextWave.cycleMs(5, 100, 200));
        assertEquals(0, TextWave.cycleMs(0, 100, 200), "没有字就没有轮次（调用方据此跳过取模，避免除零）");

        assertEquals(250, TextWave.looped(250, 700, true));
        assertEquals(50, TextWave.looped(750, 700, true), "超过一轮要绕回来");
        assertEquals(750, TextWave.looped(750, 700, false), "不循环时原样返回");
        assertEquals(750, TextWave.looped(750, 0, true), "轮次为 0 时不能对 0 取模");
    }

    @Test
    @DisplayName("参数收敛：步长不会是 0，每字间隔不会是负数")
    void clampsParameters() {
        assertEquals(TextWave.MIN_STEP_MS, TextWave.clampStep(0));
        assertEquals(TextWave.MIN_STEP_MS, TextWave.clampStep(-100));
        assertEquals(TextWave.MAX_STEP_MS, TextWave.clampStep(Integer.MAX_VALUE));
        assertEquals(100, TextWave.clampStep(100), "合法值不该被改动");

        assertEquals(0, TextWave.clampCharMs(-5));
        assertEquals(TextWave.MAX_CHAR_MS, TextWave.clampCharMs(Integer.MAX_VALUE));
        assertEquals(0, TextWave.clampHoldMs(-1));
        assertEquals(TextWave.MAX_HOLD_MS, TextWave.clampHoldMs(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("放不下时字号降级：只降到刚好放得下的整数倍，且永远不会超过作者写的值")
    void fitScaleShrinksOnly() {
        // 1 倍宽 200：640 宽的界面上 3 倍放得下 → 原样
        assertEquals(3, TextWave.fitScale(3, 200, 640));
        // 500 宽只能放 2 倍
        assertEquals(2, TextWave.fitScale(3, 200, 500));
        // 320 宽只能放 1 倍
        assertEquals(1, TextWave.fitScale(3, 200, 320));
        // 屏幕再大也不放大：作者写的 2 倍就是 2 倍
        assertEquals(2, TextWave.fitScale(2, 10, 100_000));
        // 连 1 倍都放不下时不再降（返回 1，由调用方警告）
        assertEquals(1, TextWave.fitScale(4, 500, 320));
        // 边界：宽度为 0 / 负数不能除零
        assertEquals(2, TextWave.fitScale(2, 0, 320));
        assertEquals(2, TextWave.fitScale(2, 100, 0));
        assertEquals(1, TextWave.fitScale(0, 100, 640), "配置里写 0 倍没有意义，下限是 1");
    }

    @Test
    @DisplayName("默认调色板是能循环的成套色（至少 4 种不同颜色，否则流动看起来像闪烁）")
    void defaultPaletteShape() {
        int[] palette = com.ccnrcom.menu.config.TextSpec.DEFAULT_PALETTE;
        long distinct = java.util.Arrays.stream(palette).distinct().count();
        assertEquals(palette.length, distinct, "默认调色板里不该有重复色");
        assertTrue(distinct >= 4, "默认调色板只有 " + distinct + " 色，太少");
    }
}
