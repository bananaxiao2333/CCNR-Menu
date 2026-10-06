/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 帧计时（纯逻辑，可单测）：把「已经过去了多少毫秒」映射成「该显示第几帧」。
 *
 * <p>刻意做成纯函数而不是「渲染时自增一个计数器」：计数器版本的行为依赖**渲染帧率**——
 * 同一份动画在 30fps 与 240fps 的机器上播得一样快慢，甚至在同一台机器上因卡顿而变速。
 * 用绝对时间求帧号则与帧率无关，也正是 GIF 的 delayTime 语义（每帧的显示时长是时间，不是帧数）。
 */
public final class FrameClock {

    private final int frames;
    private final long frameMs;
    private final boolean loop;

    /**
     * @param frames 总帧数（至少 1）
     * @param frameMs 每帧显示时长（毫秒，至少 1）
     * @param loop 是否循环；不循环时最后一帧停住
     */
    public FrameClock(int frames, long frameMs, boolean loop) {
        this.frames = Math.max(1, frames);
        this.frameMs = Math.max(1L, frameMs);
        this.loop = loop;
    }

    /** 该时刻应显示的帧号（0 起）。{@code elapsedMs < 0} 按 0 处理。 */
    public int frameAt(long elapsedMs) {
        long t = Math.max(0L, elapsedMs);
        long index = t / frameMs;
        if (loop) return (int) (index % frames);
        return (int) Math.min(index, frames - 1L);
    }

    /** 播完一轮的总时长。 */
    public long totalMs() {
        return frames * frameMs;
    }

    /** 不循环时是否已经播完。 */
    public boolean finished(long elapsedMs) {
        return !loop && elapsedMs >= totalMs();
    }

    public int frames() {
        return frames;
    }

    public long frameMs() {
        return frameMs;
    }

    public boolean loop() {
        return loop;
    }
}
