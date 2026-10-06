/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

/**
 * 解码完成的 GIF（纯数据）。
 *
 * <p>像素格式 {@code 0xAARRGGBB}（AWT 惯例）。**没有 close()**：这些都是 Java 堆上的 int[]，
 * 没有原生资源可释放；但它们的总量受 {@link GifBudget} 约束，且客户端应当在转成贴图后
 * 尽快**丢掉对本对象的引用**（不要同时握着 int[] 和 NativeImage[] 两份，那是双倍内存）。
 *
 * @param width 画布宽（已计入缩放）
 * @param height 画布高（已计入缩放）
 * @param delaysMs 每帧显示时长（毫秒，与 {@code frames} 等长）
 * @param frames 每帧的像素（长度 {@code width*height}）
 * @param scale 为塞进预算所做的整倍缩放（1 = 原始尺寸）
 */
public record GifFrames(int width, int height, int[] delaysMs, int[][] frames, int scale) {

    public int frameCount() {
        return frames.length;
    }

    /** 播完一轮的总时长。 */
    public long totalMs() {
        long total = 0;
        for (int d : delaysMs) total += d;
        return total;
    }

    /** 平均每帧时长（诊断输出用）。 */
    public int averageDelayMs() {
        int n = Math.max(1, delaysMs.length);
        return (int) (totalMs() / n);
    }
}
