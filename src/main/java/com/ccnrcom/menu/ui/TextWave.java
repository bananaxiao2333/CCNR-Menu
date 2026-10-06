/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

/**
 * 「彩色文字」背景的动画数学（纯逻辑，可单测）。
 *
 * <p>只做两件事，都不碰任何渲染 API：
 *
 * <ol>
 *   <li><b>颜色流动</b>：整块文字的第 {@code index} 个字符，在时刻 {@code t} 取调色板里的哪一个颜色。
 *       规则是 {@code palette[(index + t/stepMs) mod n]}——沿文字从左到右是一条色带，
 *       随时间整体向前推。这是这类「跑马灯式科技感」最常见的做法，也是唯一一种
 *       **不需要逐字符关键帧**就能读懂的做法：调色板就是全部输入。</li>
 *   <li><b>打字出现</b>：整块文字的第 {@code index} 个字符在时刻 {@code t} 是否已经出现。
 *       逐字出现把「一大段字」变成「一句话正在被写出来」，注意力顺序与阅读顺序一致。</li>
 * </ol>
 *
 * <p>为什么把它抽成纯类：这两个函数决定了「动画看起来对不对」，而它们完全可以在 JUnit 里断言
 * （色带是否真的在走、打字是否会停在最后一个字）。
 * 项目禁止起客户端验证，所以凡是能变成纯函数的东西都必须变成纯函数。
 */
public final class TextWave {

    /** 颜色流的步长下限：再快就是闪烁了（也是防止配置写 0 之后除零）。 */
    public static final int MIN_STEP_MS = 10;

    /** 颜色流的步长上限：10 秒换一格已经慢到看不出是动画。 */
    public static final int MAX_STEP_MS = 10_000;

    /** 每字出现间隔的上限（再慢就等不到整句写完）。 */
    public static final int MAX_CHAR_MS = 5_000;

    /** 停留时长的上限（10 分钟，够长了；避免配置写出一个天文数字把取模算糊）。 */
    public static final int MAX_HOLD_MS = 600_000;

    private TextWave() {}

    /**
     * 第 {@code index} 个字符当前的颜色。
     *
     * @param palette 调色板（非空才有效；空时返回 {@code fallback}）
     * @param index 字符在整块文字里的下标（跨行连续，0 起）
     * @param elapsedMs 已经过去的时间（毫秒，可为任意大）
     * @param stepMs 色带每前进一格的时间（会被收敛到合法区间）
     * @param fallback 调色板为空时的兜底色
     */
    public static int colorFor(int[] palette, int index, long elapsedMs, int stepMs, int fallback) {
        if (palette == null || palette.length == 0) return fallback;
        long step = clampStep(stepMs);
        long advance = Math.floorDiv(Math.max(0L, elapsedMs), step);
        int slot = Math.floorMod((long) index + advance, palette.length);
        return palette[slot];
    }

    /**
     * 时刻 {@code elapsedMs} 时已经出现的字符数（0..{@code length}）。
     *
     * <p>第 0 个字符**立刻**出现（{@code t=0} 返回 1）：否则「空屏一瞬」在切界面时会被看成闪烁。
     *
     * @param charMs 每个字之间的间隔；{@code <= 0} 表示整块直接出现（不淡出打字效果）
     */
    public static int visibleCount(int length, long elapsedMs, int charMs) {
        int total = Math.max(0, length);
        if (total == 0) return 0;
        if (charMs <= 0) return total;
        long steps = Math.max(0L, elapsedMs) / charMs + 1L;
        return (int) Math.min(total, steps);
    }

    /**
     * 一整轮（写完整句 + 停留）的时长；用于 {@code loop: true} 时对时间取模。
     *
     * @return 毫秒；{@code length} 为 0 时返回 0（调用方应据此跳过取模，否则会除零）
     */
    public static long cycleMs(int length, int charMs, int holdMs) {
        int total = Math.max(0, length);
        if (total == 0) return 0L;
        long typing = (long) total * Math.max(0, charMs);
        return typing + Math.max(0, holdMs);
    }

    /** 把时间收敛到一轮之内（{@code loop: false} 时原样返回）。 */
    public static long looped(long elapsedMs, long cycleMs, boolean loop) {
        if (!loop || cycleMs <= 0L) return Math.max(0L, elapsedMs);
        return Math.floorMod(Math.max(0L, elapsedMs), cycleMs);
    }

    /** 色带步长收敛到合法区间。 */
    public static int clampStep(int stepMs) {
        return Math.min(MAX_STEP_MS, Math.max(MIN_STEP_MS, stepMs));
    }

    /** 每字间隔收敛到合法区间。 */
    public static int clampCharMs(int charMs) {
        return Math.min(MAX_CHAR_MS, Math.max(0, charMs));
    }

    /** 停留时长收敛到合法区间。 */
    public static int clampHoldMs(int holdMs) {
        return Math.min(MAX_HOLD_MS, Math.max(0, holdMs));
    }

    /**
     * 在「不超出屏幕宽度」的前提下能用的最大整数放大倍数（下限 1）。
     *
     * <p>为什么不干脆让作者自己保证放得下：字号是**绝对像素**，而 GUI 尺寸随窗口与缩放变化
     * （小窗口下可能只有 320×240 个 GUI 单位）。作者在自己机器上永远看不到那种窗口，
     * 而一份「在最小分辨率下被切掉半行字」的默认配置最终会被玩家上报。
     *
     * <p>为什么是**整数**倍、而且只降不升：原版字体的放大是整倍像素放大，非整数倍会让字形
     * 边缘出现半像素；返回值不会超过 {@code configured}——「作者写的字号」是设计意图，
     * 屏幕再大也不该自作主张放大。
     *
     * @param configured 配置里的放大倍数
     * @param unitWidth 整块文字在**1 倍**下的宽度（像素）
     * @param screenWidth 可用宽度（像素）
     */
    public static int fitScale(int configured, int unitWidth, int screenWidth) {
        int wanted = Math.max(1, configured);
        if (unitWidth <= 0 || screenWidth <= 0) return wanted;
        return Math.min(wanted, Math.max(1, screenWidth / unitWidth));
    }
}
