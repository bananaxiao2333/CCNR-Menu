/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 精灵图（sprite sheet）动画的几何描述（纯逻辑，可单测）。
 *
 * <p>为什么把「精灵图序列帧」作为一等公民，而不是只支持 GIF：
 * 精灵图是**一张普通 PNG**，Minecraft 的贴图管线直接吃它，逐帧只需换 UV 坐标，
 * 每帧**零上传、零解码**——一个 8×8 格、每格 240×135 的循环动画在 GPU 上几乎不花钱。
 * 而 GIF 必须逐帧把像素写进动态贴图再上传，代价高得多（见 docs/02 的对比表）。
 *
 * @param cols 横向格数（至少 1）
 * @param rows 纵向格数（至少 1）
 * @param frames 实际使用的帧数；{@code <= 0} 表示用满 {@code cols*rows}（末行可以留空）
 * @param frameMs 每帧显示时长（毫秒）
 * @param loop 是否循环
 * @param speed 播放速度倍数（{@code 2} = 快一倍，{@code 0.5} = 慢一半）
 */
public record SpriteSheet(int cols, int rows, int frames, int frameMs, boolean loop, float speed) {

    /** 默认：单格、10 帧/秒、循环、原速。 */
    public static final SpriteSheet DEFAULT = new SpriteSheet(1, 1, 0, 100, true, 1f);

    /** 速度的合法区间：太小等于静止，太大看不出内容。 */
    public static final float MIN_SPEED = 0.05f;

    public static final float MAX_SPEED = 20f;

    public SpriteSheet {
        cols = Math.max(1, cols);
        rows = Math.max(1, rows);
        frameMs = (int) Math.max(1L, frameMs);
        speed = Math.min(MAX_SPEED, Math.max(MIN_SPEED, speed));
    }

    /** 网格能容纳的帧数上限。 */
    public int capacity() {
        return cols * rows;
    }

    /** 实际播放的帧数（{@code frames <= 0} 时用满整个网格）。 */
    public int frameCount() {
        int capacity = capacity();
        return frames > 0 ? Math.min(frames, capacity) : capacity;
    }

    /** 计入 {@link #speed} 之后的每帧时长；速度越快，每帧时长越短。 */
    public long scaledFrameMs() {
        return Math.max(1L, Math.round(frameMs / (double) speed));
    }

    /** 按当前参数构造帧计时器。 */
    public FrameClock clock() {
        return new FrameClock(frameCount(), scaledFrameMs(), loop);
    }

    /** 第 {@code frame} 帧所在的列（0 起，行优先）。 */
    public int col(int frame) {
        return Math.floorMod(frame, cols);
    }

    /** 第 {@code frame} 帧所在的行（0 起，行优先）。越界帧按帧数取模，不会返回负数。 */
    public int row(int frame) {
        int wrapped = Math.floorMod(frame, frameCount());
        return wrapped / cols;
    }
}
