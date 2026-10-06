/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 轮播的时刻表（纯逻辑，可单测）。
 *
 * <p>回答「时刻 t 该画哪一张、下一张淡到了几分、这一张自己走到第几步」。
 * 三个输出各自服务一件事：
 *
 * <ul>
 *   <li>{@code index}：画哪一张（铺满全屏）；</li>
 *   <li>{@code incoming} / {@code fade}：下一张以多大的不透明度**盖在上面**。
 *       交叉淡入的做法是「先画当前这张，再按 {@code fade} 的 alpha 画下一张」，
 *       结果正好是两者的线性插值——不需要同时把两张都设成半透明（那会让中间瞬间露出背景）。</li>
 *   <li>{@code progress} / {@code incomingProgress}：这一张/下一张自己走了多少，
 *       给「缓慢放大 + 偏移」用（见 {@link MenuGeometry#kenBurns}）。</li>
 * </ul>
 *
 * <p>时间轴刻意做成**每张一个等长周期**：第 i 张的周期是
 * {@code [i*period, (i+1)*period)}，淡出排在周期的最后 {@code fadeMs}。
 * 这样「上一张的淡出」与「下一张的淡入」天然是同一段时间，不需要额外对齐；
 * 而且在周期边界上 {@code fade} 恰好等于 1，视觉上完全连续（不会闪一下）。
 */
public record SlideTimeline(int count, int holdMs, int fadeMs, boolean loop) {

    /** 时刻 t 的轮播状态。 */
    public record State(int index, int incoming, float fade, float progress, float incomingProgress) {

        /** 是否需要画第二张（交叉淡入中）。 */
        public boolean crossFading() {
            return incoming >= 0 && fade > 0f;
        }

        /** 没有可画的内容（配置里一张图都没有）。 */
        public static final State EMPTY = new State(-1, -1, 0f, 0f, 0f);
    }

    public SlideTimeline {
        count = Math.max(0, count);
        holdMs = Math.max(1, holdMs);
        fadeMs = Math.max(0, Math.min(holdMs, fadeMs));
    }

    /** 一轮播完需要的总时长；{@code count == 0} 时返回 0（调用方据此跳过取模）。 */
    public long cycleMs() {
        return (long) count * holdMs;
    }

    /** 时刻 {@code elapsedMs} 的状态。 */
    public State stateAt(long elapsedMs) {
        if (count <= 0) return State.EMPTY;
        long t = Math.max(0L, elapsedMs);
        long cycle = cycleMs();
        if (loop) {
            t = Math.floorMod(t, cycle);
        } else {
            // 不循环：走完最后一张就**停在**它上面，而不是跳回第一张
            t = Math.min(t, cycle - 1);
        }
        int index = (int) (t / holdMs);
        if (index >= count) index = count - 1;
        long u = t - (long) index * holdMs;

        int incoming = -1;
        float fade = 0f;
        if (count > 1 && fadeMs > 0) {
            long fadeStart = holdMs - fadeMs;
            if (u >= fadeStart) {
                fade = Math.min(1f, (u - fadeStart) / (float) fadeMs);
                incoming = loop ? (index + 1) % count : index + 1;
                if (incoming >= count) incoming = -1;
                if (incoming < 0) fade = 0f;
            }
        }

        // 每张图自己的进度：从「它开始淡入」算起（也就是上一个周期的 fade 起点），到这里结束
        float progress = clamp01((u + fadeMs) / (float) holdMs);
        float incomingProgress = incoming < 0 ? 0f : clamp01((u - (holdMs - fadeMs)) / (float) holdMs);
        return new State(index, incoming, fade, progress, incomingProgress);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
