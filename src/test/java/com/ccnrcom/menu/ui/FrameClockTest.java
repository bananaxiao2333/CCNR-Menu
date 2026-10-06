/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 帧计时与精灵图几何门禁。
 *
 * <p>这里守的是「动画节奏」：播放必须由**绝对时间**决定，而不是「每次渲染自增一帧」。
 * 后者在不同帧率的机器上速度不同，还会因卡顿而变速——这种缺陷只在实测时才看得出来，
 * 所以必须由「给定时刻 → 帧号」的纯函数测试来钉死。
 */
class FrameClockTest {

    @Test
    @DisplayName("按时间求帧号：与渲染帧率无关")
    void frameByTime() {
        FrameClock clock = new FrameClock(4, 100, true);
        assertEquals(0, clock.frameAt(0));
        assertEquals(0, clock.frameAt(99));
        assertEquals(1, clock.frameAt(100));
        assertEquals(3, clock.frameAt(399));
    }

    @Test
    @DisplayName("循环动画取模，不循环时停在最后一帧")
    void loopAndClamp() {
        FrameClock looping = new FrameClock(4, 100, true);
        assertEquals(0, looping.frameAt(400));
        assertEquals(1, looping.frameAt(500));
        assertEquals(0, looping.frameAt(1_000_000), "10000 帧号对 4 取模回到第 0 帧");
        assertEquals(3, looping.frameAt(999_900), "不是整数轮次时停在轮内对应的帧");

        FrameClock once = new FrameClock(4, 100, false);
        assertEquals(3, once.frameAt(400));
        assertEquals(3, once.frameAt(99_999));
        assertTrue(once.finished(400));
        assertFalse(once.finished(399));
        assertFalse(looping.finished(99_999), "循环动画永远不「播完」");
    }

    @Test
    @DisplayName("非法参数被收敛：至少 1 帧、每帧至少 1ms，负时间按 0 处理")
    void degenerateInputs() {
        FrameClock clock = new FrameClock(0, 0, true);
        assertEquals(1, clock.frames());
        assertEquals(1, clock.frameMs());
        assertEquals(0, clock.frameAt(-500));
        assertEquals(1, clock.totalMs(), "1 帧 × 1ms");
    }
}
