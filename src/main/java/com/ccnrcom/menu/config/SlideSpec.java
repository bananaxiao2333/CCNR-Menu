/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * 轮播的节奏与运镜（纯数据，可单测）。
 *
 * <p>三件事分开表达，因为它们各自调起来是独立的：
 *
 * <ul>
 *   <li>{@code holdMs} / {@code fadeMs}：**节奏**——每张停多久、切换用多久淡入淡出；</li>
 *   <li>{@code zoom} / {@code panX} / {@code panY}：**运镜**——每张图在自己这段时间里
 *       放大多少、往哪偏多少。</li>
 * </ul>
 *
 * <p>为什么运镜是「每张图各自从头走一遍」而不是整条时间轴连续走：连续走的话，
 * 切到第 5 张时画面已经放大到 1.4 倍了，第 5 张一出现就是糊的。每张各自从 1.0 开始，
 * 才有「这张照片在缓慢推近」的感觉。
 *
 * @param holdMs 每张图显示多久（毫秒；淡入淡出包含在这段时间的末尾）
 * @param fadeMs 交叉淡入的时长（毫秒）
 * @param zoom 每张图在显示期间放大的比例（{@code 0.08} = 放大到 1.08 倍；0 = 不放大）
 * @param panX 每张图在显示期间向右偏移的比例（相对屏幕宽度；负数向左）
 * @param panY 每张图在显示期间向下偏移的比例（相对屏幕高度；负数向上）
 * @param loop 播到最后一张之后是否回到第一张
 */
public record SlideSpec(int holdMs, int fadeMs, float zoom, float panX, float panY, boolean loop) {

    /** 默认：每张 9 秒、1.4 秒交叉淡入、缓慢推近并向右移一点、循环。 */
    public static final SlideSpec DEFAULT = new SlideSpec(9000, 1400, 0.08f, 0.05f, 0f, true);

    /** 停留时长的合法区间：太快看不清，太慢像卡住。 */
    public static final int MIN_HOLD_MS = 400;

    public static final int MAX_HOLD_MS = 300_000;

    /** 淡入时长的上限（再长就是在慢性溶解）。 */
    public static final int MAX_FADE_MS = 10_000;

    /** 放大比例上限：再大就不是「一点点」了，而且会明显糊。 */
    public static final float MAX_ZOOM = 1.0f;

    /** 偏移比例上限（相对屏幕，1.0 = 整整一个屏幕）。 */
    public static final float MAX_PAN = 1.0f;

    public SlideSpec {
        holdMs = Math.min(MAX_HOLD_MS, Math.max(MIN_HOLD_MS, holdMs));
        fadeMs = Math.min(MAX_FADE_MS, Math.max(0, fadeMs));
        zoom = Math.min(MAX_ZOOM, Math.max(0f, zoom));
        panX = Math.min(MAX_PAN, Math.max(-MAX_PAN, panX));
        panY = Math.min(MAX_PAN, Math.max(-MAX_PAN, panY));
    }

    /** 每张图的周期 = 停留时长（淡入淡出发生在停留的末尾，不额外占时间）。 */
    public long periodMs() {
        return holdMs;
    }

    /** 从 {@code background} 对象里解析轮播参数。 */
    public static SlideSpec parse(JsonObject background, List<String> warnings) {
        if (background == null) return DEFAULT;
        int holdMs = (int) clamp(JsonUtil.num(background, "holdMs", DEFAULT.holdMs()), MIN_HOLD_MS, MAX_HOLD_MS);
        int fadeMs = (int) clamp(JsonUtil.num(background, "fadeMs", DEFAULT.fadeMs()), 0, MAX_FADE_MS);
        if (fadeMs > holdMs) {
            warnings.add("background.fadeMs (" + fadeMs + "ms) 比 holdMs (" + holdMs + "ms) 还长 → 已按 holdMs 收窄，"
                    + "否则每一张都没露过完整的脸");
            fadeMs = holdMs;
        }
        return new SlideSpec(
                holdMs,
                fadeMs,
                (float) clamp(JsonUtil.dbl(background, "zoom", DEFAULT.zoom()), 0, MAX_ZOOM),
                (float) clamp(JsonUtil.dbl(background, "panX", DEFAULT.panX()), -MAX_PAN, MAX_PAN),
                (float) clamp(JsonUtil.dbl(background, "panY", DEFAULT.panY()), -MAX_PAN, MAX_PAN),
                JsonUtil.bool(background, "loop", DEFAULT.loop()));
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
