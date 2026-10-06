/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

/**
 * 动画 GIF 的内存预算（纯逻辑，可单测）。
 *
 * <p>为什么必须有预算：GIF 的「体积」与「解压后的体积」完全是两回事。一个 3MB 的
 * 1920×1080×60 帧 GIF，解码后是 {@code 1920*1080*4*60 ≈ 475MB}——玩家从网上随手存一张动图，
 * 就足以把游戏拖进 GC 风暴甚至 OOM，而报错信息只会说「内存不足」，指不到那张图上。
 *
 * <p>因此这里在**解码之前**先把尺寸算清楚：超预算就整倍缩小边长（帧数、时长一律不动，
 * 动画节奏不变），并把这个倍数交给解码器。选择整倍缩小而不是「按比例缩到某个宽」是为了让
 * 最近邻采样不产生采样漂移（见 {@link GifComposer#downscale}）。
 */
public final class GifBudget {

    /** 默认总预算：96MB。约等于 1280×720 的 26 帧，或 640×360 的 104 帧。 */
    public static final long DEFAULT_BYTES = 96L * 1024 * 1024;

    /** 单边上限（同时是 GPU 侧的安全值：1.20.1 里绝大多数显卡远高于它）。 */
    public static final int MAX_DIMENSION = 4096;

    /** 帧数上限：再多就不像「背景小动画」了，而且每帧都要占内存。 */
    public static final int MAX_FRAMES = 600;

    /** 缩放倍数上限（1 = 不缩，16 = 每边缩到 1/16）。 */
    public static final int MAX_SCALE = 16;

    private GifBudget() {}

    /**
     * 求一个能让总像素内存落进预算的整倍缩放。
     *
     * @return 1..{@link #MAX_SCALE}；即使返回 {@link #MAX_SCALE} 也仍然超预算时，调用方应放弃解码
     */
    public static int scaleFor(int width, int height, int frames, long budgetBytes) {
        long budget = budgetBytes <= 0 ? DEFAULT_BYTES : budgetBytes;
        for (int scale = 1; scale <= MAX_SCALE; scale++) {
            long bytes = estimateBytes(width, height, frames, scale);
            if (bytes <= budget) return scale;
        }
        return MAX_SCALE;
    }

    /** 按缩放倍数估算总内存（4 字节/像素 × 帧数）。 */
    public static long estimateBytes(int width, int height, int frames, int scale) {
        int s = Math.max(1, scale);
        long w = Math.max(1, width / s);
        long h = Math.max(1, height / s);
        return w * h * 4L * Math.max(1, frames);
    }

    /** 缩放后的边长（至少 1 像素）。 */
    public static int scaled(int value, int scale) {
        return Math.max(1, value / Math.max(1, scale));
    }
}
