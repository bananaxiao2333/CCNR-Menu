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
 * 轮播时刻表门禁。
 *
 * <p>轮播有两种只有肉眼能发现的故障：**静止**（参数算死，永远停在第 0 张）与**跳变**
 * （周期边界上进度倒回去，画面会猛地缩一下）。两种都不会抛异常，所以都要靠断言守住。
 * 尤其是边界连续性——它是「看着像小瑕疵、实际是设计错了」的典型。
 */
class SlideTimelineTest {

    private static final int HOLD = 1000;
    private static final int FADE = 200;

    private static SlideTimeline timeline(int count) {
        return new SlideTimeline(count, HOLD, FADE, true);
    }

    @Test
    @DisplayName("刚开场：第 0 张全屏，没有正在淡入的下一张")
    void startsOnFirstSlide() {
        SlideTimeline.State state = timeline(3).stateAt(0);
        assertEquals(0, state.index());
        assertEquals(-1, state.incoming());
        assertEquals(0f, state.fade());
        assertFalse(state.crossFading());
    }

    @Test
    @DisplayName("停留期间一直显示同一张（不会提前切走）")
    void holdsWithinOneSlide() {
        SlideTimeline time = timeline(3);
        for (long t = 0; t < HOLD - FADE; t += 50) {
            assertEquals(0, time.stateAt(t).index(), "t=" + t + " 不该换图");
            assertFalse(time.stateAt(t).crossFading(), "t=" + t + " 还没到淡入窗口");
        }
    }

    @Test
    @DisplayName("末尾交叉淡入：fade 从 0 连续走到接近 1，下一张就是后一张")
    void crossFadesAtTheTail() {
        SlideTimeline time = timeline(3);
        SlideTimeline.State start = time.stateAt(HOLD - FADE);
        assertEquals(0, start.index());
        assertEquals(1, start.incoming());
        assertEquals(0f, start.fade(), 0.001f, "淡入窗口的起点就是 0");

        SlideTimeline.State mid = time.stateAt(HOLD - FADE / 2);
        assertEquals(0.5f, mid.fade(), 0.01f, "走到一半就是半透明");

        SlideTimeline.State end = time.stateAt(HOLD - 1);
        assertEquals(1, end.incoming());
        assertTrue(end.fade() > 0.98f, "窗口末尾几乎完全换成下一张，实际 " + end.fade());
    }

    @Test
    @DisplayName("周期边界连续：上一张的 fade 走到 1 的那一刻，正好等于下一张从头开始的那一刻")
    void boundaryIsContinuous() {
        SlideTimeline time = timeline(3);
        // 边界前一刻：第 0 张 + 第 1 张完全淡入
        SlideTimeline.State before = time.stateAt(HOLD - 1);
        assertEquals(0, before.index());
        assertEquals(1, before.incoming());
        assertTrue(before.fade() > 0.99f);
        float incomingProgressBefore = before.incomingProgress();

        // 边界后一刻：第 1 张成了主角，进度从同一个值接着走
        SlideTimeline.State after = time.stateAt(HOLD);
        assertEquals(1, after.index());
        assertEquals(-1, after.incoming());
        assertEquals(incomingProgressBefore, after.progress(), 0.01f, "切换的一瞬间进度必须接得上，否则画面会在换图的那一帧猛地缩一下");
    }

    @Test
    @DisplayName("索引按周期递增、走完一轮回到第 0 张")
    void advancesAndLoops() {
        SlideTimeline time = timeline(3);
        assertEquals(0, time.stateAt(0).index());
        assertEquals(1, time.stateAt(HOLD).index());
        assertEquals(2, time.stateAt(2L * HOLD).index());
        assertEquals(0, time.stateAt(3L * HOLD).index(), "一轮 3 张，第 3 个周期回到第 0 张");
        assertEquals(1, time.stateAt(4L * HOLD + 10).index());
        assertEquals(3L * HOLD, time.cycleMs());
    }

    @Test
    @DisplayName("不循环：播到最后一张就停在它上面（进度停在 1，不会跳回第 0 张）")
    void stopsOnLastSlideWhenNotLooping() {
        SlideTimeline time = new SlideTimeline(3, HOLD, FADE, false);
        SlideTimeline.State end = time.stateAt(100L * HOLD);
        assertEquals(2, end.index(), "停在第 2 张");
        assertEquals(-1, end.incoming(), "最后一张后面没有下一张可淡入");
        assertEquals(0f, end.fade());
        assertEquals(1f, end.progress(), 0.001f, "运镜走到头就停住");
    }

    @Test
    @DisplayName("只有一张图时没有交叉淡入（自己跟自己淡没有意义）")
    void singleSlideNeverCrossFades() {
        SlideTimeline.State state = timeline(1).stateAt(HOLD - 1);
        assertEquals(0, state.index());
        assertEquals(-1, state.incoming());
        assertEquals(0f, state.fade());
    }

    @Test
    @DisplayName("边界输入：0 张、fadeMs 比 holdMs 还长、负时间都不炸")
    void degenerateInputs() {
        SlideTimeline empty = timeline(0);
        assertEquals(-1, empty.stateAt(1234).index());
        assertEquals(0L, empty.cycleMs());

        // fadeMs 比 holdMs 长时会被收窄到 holdMs（配置层也会拦，这里保证构造出来的实例自洽）
        SlideTimeline clamped = new SlideTimeline(2, 500, 900, true);
        assertEquals(500, clamped.fadeMs());
        assertEquals(0, clamped.stateAt(-999).index(), "负时间当 0 处理");
        assertTrue(clamped.stateAt(499).crossFading());
    }

    @Test
    @DisplayName("运镜进度随时间单调不减（放大/偏移只能朝一个方向走，否则画面会来回抽）")
    void progressIsMonotonic() {
        SlideTimeline time = timeline(3);
        float last = -1f;
        for (long t = 0; t < HOLD; t += 25) {
            float p = time.stateAt(t).progress();
            assertTrue(p >= last - 0.0001f, "t=" + t + " 的进度倒退了：" + last + " → " + p);
            assertTrue(p >= 0f && p <= 1f, "进度必须在 0..1 之间，实际 " + p);
            last = p;
        }
    }
}
