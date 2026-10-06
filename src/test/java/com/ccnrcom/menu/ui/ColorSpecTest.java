/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 颜色解析门禁。
 *
 * <p>重点是「不合法的输入必须返回 null」而不是偷偷给个白色：颜色写错时界面会照常画出来，
 * 只是颜色不是作者想要的——不返回 null 就没有任何地方能发现这个错误。
 */
class ColorSpecTest {

    @Test
    @DisplayName("六位写法补成不透明")
    void sixDigits() {
        assertEquals(0xFFFF0000, (int) ColorSpec.parse("#FF0000"));
        assertEquals(0xFF6EC6FF, (int) ColorSpec.parse("#6EC6FF"));
        assertEquals(0xFF6EC6FF, (int) ColorSpec.parse("6ec6ff"));
        assertEquals(0xFF6EC6FF, (int) ColorSpec.parse("0x6EC6FF"));
    }

    @Test
    @DisplayName("八位写法带 alpha（0xFFFFFFFF 不能当成越界）")
    void eightDigits() {
        assertEquals(0xFFFFFFFF, (int) ColorSpec.parse("#FFFFFFFF"));
        assertEquals(0x80000000, (int) ColorSpec.parse("#80000000"));
        assertEquals(0x00FFFFFF, (int) ColorSpec.parse("#00FFFFFF"));
    }

    @Test
    @DisplayName("三位缩写每位重复一次")
    void threeDigits() {
        assertEquals(0xFFFF0000, (int) ColorSpec.parse("#F00"));
        assertEquals(0xFF33AAFF, (int) ColorSpec.parse("#3AF"));
    }

    @Test
    @DisplayName("非法输入返回 null（长度、字符、空）")
    void invalid() {
        assertEquals(null, ColorSpec.parse(null));
        assertEquals(null, ColorSpec.parse(""));
        assertEquals(null, ColorSpec.parse("  "));
        assertEquals(null, ColorSpec.parse("#12345"));
        assertEquals(null, ColorSpec.parse("#GGGGGG"));
        assertEquals(null, ColorSpec.parse("红色"));
        assertEquals(null, ColorSpec.parse("#1234567"));
    }

    @Test
    @DisplayName("parseOr 在非法时用默认值")
    void parseOr() {
        assertEquals(0xFF123456, ColorSpec.parseOr("nope", 0xFF123456));
        assertEquals(0xFF123456, ColorSpec.parseOr("#123456", 0xFFFFFFFF));
    }

    @Test
    @DisplayName("通道读写与 alpha 缩放")
    void channels() {
        int color = 0x80402010;
        assertEquals(0x80, ColorSpec.alpha(color));
        assertEquals(0x40, ColorSpec.red(color));
        assertEquals(0x20, ColorSpec.green(color));
        assertEquals(0x10, ColorSpec.blue(color));
        assertEquals(0x40, ColorSpec.withAlpha(color, 0x40) >>> 24);
        assertEquals(0x40, ColorSpec.scaleAlpha(color, 0.5f) >>> 24);
        assertEquals(1f, ColorSpec.alphaF(0xFFFFFFFF), 1e-6);
    }

    @Test
    @DisplayName("alpha 缩放的边界：不会溢出也不会变负")
    void alphaScalingBoundaries() {
        assertEquals(255, ColorSpec.scaleAlpha(0xFFFFFFFF, 2f) >>> 24);
        assertEquals(0, ColorSpec.scaleAlpha(0xFFFFFFFF, 0f) >>> 24);
        assertEquals(0, ColorSpec.scaleAlpha(0xFFFFFFFF, -5f) >>> 24);
        assertEquals(0, ColorSpec.withAlpha(0xFFFFFFFF, -10) >>> 24);
    }

    @Test
    @DisplayName("toHex 输出 8 位（诊断输出会用到）")
    void toHex() {
        assertEquals("#FF6EC6FF", ColorSpec.toHex(0xFF6EC6FF));
        assertTrue(ColorSpec.toHex(0x00000000).startsWith("#"));
    }
}
