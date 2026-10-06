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
 * GIF 时间轴门禁（每帧时长可以不同）。
 *
 * <p>为什么不能复用 {@link FrameClock}：GIF 允许逐帧不同的 {@code delayTime}。
 * 用统一帧长去播，节奏会明显走样；而「节奏不对」正是那种一眼能看出、却很难定位的问题，
 * 所以用「给定时刻 → 帧号」的断言把它钉住。
 */
class FrameTimelineTest {

    @Test
    @DisplayName("按逐帧时长求帧号")
    void frameByDelays() {
        // 三帧：100ms / 300ms / 50ms，总长 450ms
        FrameTimeline timeline = new FrameTimeline(new int[] {100, 300, 50}, true);
        assertEquals(3, timeline.frameCount());
        assertEquals(450, timeline.totalMs());

        assertEquals(0, timeline.frameAt(0));
        assertEquals(0, timeline.frameAt(99));
        assertEquals(1, timeline.frameAt(100));
        assertEquals(1, timeline.frameAt(399));
        assertEquals(2, timeline.frameAt(400));
        assertEquals(2, timeline.frameAt(449));
    }

    @Test
    @DisplayName("循环时取模回绕；不循环时停在最后一帧")
    void loopAndClamp() {
        FrameTimeline looping = new FrameTimeline(new int[] {100, 300, 50}, true);
        assertEquals(0, looping.frameAt(450));
        assertEquals(1, looping.frameAt(550));
        assertEquals(0, looping.frameAt(450 * 1000));

        FrameTimeline once = new FrameTimeline(new int[] {100, 300, 50}, false);
        assertEquals(2, once.frameAt(450));
        assertEquals(2, once.frameAt(1_000_000));
    }

    @Test
    @DisplayName("空/非法时长数组不会产生除零或越界")
    void degenerateInputs() {
        FrameTimeline empty = new FrameTimeline(new int[0], true);
        assertEquals(1, empty.frameCount());
        assertEquals(0, empty.frameAt(12_345));
        assertEquals(100, empty.totalMs());

        FrameTimeline zeros = new FrameTimeline(new int[] {0, 0}, true);
        assertEquals(0, zeros.frameAt(0));
        assertEquals(1, zeros.frameAt(1), "0 时长按 1ms 处理（避免除零或永远停在第一帧）");
    }

    @Test
    @DisplayName("startOf 给出一帧的开始时刻（诊断与逐帧调试用）")
    void startOf() {
        FrameTimeline timeline = new FrameTimeline(new int[] {100, 300, 50}, true);
        assertEquals(0, timeline.startOf(0));
        assertEquals(100, timeline.startOf(1));
        assertEquals(400, timeline.startOf(2));
        assertEquals(0, timeline.startOf(3), "越界帧号按取模处理");
        assertEquals(300, timeline.delayMs(1));
    }

    @Test
    @DisplayName("单帧动画：任何时刻都是第 0 帧")
    void singleFrame() {
        FrameTimeline timeline = new FrameTimeline(new int[] {40}, true);
        assertEquals(0, timeline.frameAt(0));
        assertEquals(0, timeline.frameAt(999_999));
        assertTrue(timeline.totalMs() > 0);
        assertFalse(timeline.frameCount() > 1);
    }
}
