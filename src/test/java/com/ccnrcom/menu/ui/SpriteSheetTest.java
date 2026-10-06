/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 精灵图参数门禁：网格、实际帧数、帧时长与速度倍数。 */
class SpriteSheetTest {

    @Test
    @DisplayName("帧数上限是 cols×rows")
    void capacity() {
        assertEquals(32, new SpriteSheet(8, 4, 0, 100, true, 1f).capacity());
        assertEquals(32, new SpriteSheet(8, 4, 0, 100, true, 1f).frameCount(), "frames=0 表示用满网格");
    }

    @Test
    @DisplayName("frames 可以小于网格容量（末行留空是常见做法）")
    void explicitFrameCount() {
        assertEquals(30, new SpriteSheet(8, 4, 30, 100, true, 1f).frameCount());
        assertEquals(32, new SpriteSheet(8, 4, 99, 100, true, 1f).frameCount(), "超过容量的 frames 收敛到容量");
    }

    @Test
    @DisplayName("速度倍数改变每帧时长（2 倍速 = 帧时长减半）")
    void speed() {
        assertEquals(100, new SpriteSheet(2, 2, 0, 100, true, 1f).scaledFrameMs());
        assertEquals(50, new SpriteSheet(2, 2, 0, 100, true, 2f).scaledFrameMs());
        assertEquals(200, new SpriteSheet(2, 2, 0, 100, true, 0.5f).scaledFrameMs());
        assertEquals(1, new SpriteSheet(2, 2, 0, 1, true, 20f).scaledFrameMs(), "再快也不能小于 1ms");
    }

    @Test
    @DisplayName("非法参数被收敛：格数至少 1、每帧至少 1ms、速度有上下限")
    void degenerateInputs() {
        SpriteSheet sheet = new SpriteSheet(0, -3, 0, 0, true, 1000f);
        assertEquals(1, sheet.cols());
        assertEquals(1, sheet.rows());
        assertEquals(1, sheet.frameMs());
        assertEquals(SpriteSheet.MAX_SPEED, sheet.speed());

        SpriteSheet tooSlow = new SpriteSheet(1, 1, 1, 100, true, 0f);
        assertEquals(SpriteSheet.MIN_SPEED, tooSlow.speed());
    }

    @Test
    @DisplayName("行列推导：行优先，越界帧号取模")
    void colRow() {
        SpriteSheet sheet = new SpriteSheet(4, 2, 0, 100, true, 1f);
        assertEquals(0, sheet.col(0));
        assertEquals(0, sheet.row(0));
        assertEquals(3, sheet.col(3));
        assertEquals(0, sheet.row(3));
        assertEquals(0, sheet.col(4));
        assertEquals(1, sheet.row(4));
        assertEquals(0, sheet.col(8), "越界帧号按帧数取模");
    }

    @Test
    @DisplayName("clock() 用实际帧数与缩放后的帧时长")
    void clock() {
        FrameClock clock = new SpriteSheet(4, 4, 6, 100, true, 2f).clock();
        assertEquals(6, clock.frames());
        assertEquals(50, clock.frameMs());
        assertTrue(clock.loop());
    }
}
