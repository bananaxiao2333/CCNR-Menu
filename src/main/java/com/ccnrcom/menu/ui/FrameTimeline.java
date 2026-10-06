/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 逐帧时长**不一致**的动画时间轴（纯逻辑，可单测）。
 *
 * <p>为什么不能直接用 {@link FrameClock}：GIF 允许每帧有不同的 {@code delayTime}
 * （手绘逐帧动画经常给关键帧更长的停留时间）。用「统一的每帧时长」去播这种 GIF，
 * 节奏会明显走样——快的地方糊成一团，慢的地方像卡住。{@link FrameClock} 仍然用于精灵图，
 * 那里的帧时长本来就是配置里的一个固定值。
 */
public final class FrameTimeline {

    /** 帧时长的兜底值（与 GIF 解码器的规则一致）。 */
    public static final int DEFAULT_FRAME_MS = 100;

    private final int[] delays;
    private final long[] starts;
    private final long total;
    private final boolean loop;

    /**
     * @param delays 每帧时长（毫秒）；空数组按「一帧 {@value #DEFAULT_FRAME_MS}ms」处理
     * @param loop 是否循环
     */
    public FrameTimeline(int[] delays, boolean loop) {
        this.delays = (delays == null || delays.length == 0) ? new int[] {DEFAULT_FRAME_MS} : delays.clone();
        this.starts = new long[this.delays.length];
        this.loop = loop;
        long acc = 0;
        for (int i = 0; i < this.delays.length; i++) {
            this.starts[i] = acc;
            acc += Math.max(1, this.delays[i]);
        }
        this.total = acc;
    }

    public int frameCount() {
        return delays.length;
    }

    public long totalMs() {
        return total;
    }

    public int delayMs(int frame) {
        return delays[Math.floorMod(frame, delays.length)];
    }

    /** 该时刻应显示的帧号。不循环时超出总时长就停在最后一帧。 */
    public int frameAt(long elapsedMs) {
        if (delays.length == 1) return 0;
        long t = Math.max(0L, elapsedMs);
        if (loop) {
            t %= total;
        } else if (t >= total) {
            return delays.length - 1;
        }
        // 二分找「起始时间 <= t 的最后一帧」
        int low = 0;
        int high = delays.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts[mid] <= t) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /** 该帧开始显示的时刻（相对动画起点）。 */
    public long startOf(int frame) {
        return starts[Math.floorMod(frame, delays.length)];
    }
}
