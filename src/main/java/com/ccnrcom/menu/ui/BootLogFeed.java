/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * 运行期事件流：模组别处在「真的发生了什么事」时往这里塞一行，启动日志照着显示。
 *
 * <p>为什么要单独一个纯类而不是把行塞进 {@link BootLogTimeline}：两者的时间语义完全不同。
 * 时刻表是**预先写死**的一份剧本（带自己的节奏、进度条整行），事件流是**事后追加**的、
 * 时间戳按发生的先后递增。混成一个类之后，「哪一行还会变」这件事就没人说得清了。
 *
 * <p>它同时承担进度条（{@link #spin}/{@link #spinOk}/{@link #spinFail}）：
 * 真实终端里「等待中」那一行是**唯一在动**的一行，跟随事件结束而换成 OK/FAILED——
 * 所以它必须和事件流同一个时间原点，否则时间戳会跳。
 *
 * <p>线程约定：只允许客户端主线程调用（事件、按钮、渲染都在主线程）。这里不做同步——
 * 加锁只会掩盖「在工作线程里记日志」这种真正的错误。
 */
public final class BootLogFeed {

    /** 最多留多少行（只增不减的列表在长时间挂机后会把内存和绘制都拖死）。 */
    private static final int MAX_LINES = 200;

    private final List<Entry> entries = new ArrayList<>();
    private final long originMs;
    private final int spinnerWidth;
    /** 时钟（毫秒）；默认 {@code System.nanoTime}，单测注入假时钟——时间相关的断言不能靠 sleep。 */
    private final LongSupplier clock;

    private String spinText;
    private BootLogKind spinKind = BootLogKind.RUN;
    private long spinSinceMs;
    private float spinProgress;

    private record Entry(String text, BootLogKind kind, double atSeconds) {}

    /**
     * 时钟与原点**必须由调用方给**：这一层是要和剧本（走 {@code Util.getMillis()}，
     * 即进程启动以来的毫秒）混在一条时间轴上的。早先这里默认用 {@code System.nanoTime()}
     * （机器**开机**以来的毫秒），于是事件行的时间戳印出了开机秒数——
     * 实测症状是「日志后面的时间戳变成 444 万」。
     */
    public BootLogFeed(long originMs, int spinnerWidth, LongSupplier clockMs) {
        this.originMs = originMs;
        this.spinnerWidth = spinnerWidth;
        this.clock = clockMs;
    }

    /** 记一行（{@code INFO}）。 */
    public void log(String text) {
        log(text, BootLogKind.INFO);
    }

    /** 记一行；空/null 文本忽略（别在日志里制造空行）。 */
    public void log(String text, BootLogKind kind) {
        if (text == null || text.isBlank()) return;
        entries.add(new Entry(text.strip(), kind == null ? BootLogKind.INFO : kind, seconds()));
        if (entries.size() > MAX_LINES) entries.remove(0);
    }

    /** 开一条「等待中」的进度条（文案自己带方括号前缀时就别重复写标记）。 */
    public void spin(String text) {
        if (text == null || text.isBlank()) return;
        this.spinText = text.strip();
        this.spinKind = BootLogKind.RUN;
        this.spinSinceMs = now();
        this.spinProgress = 0f;
    }

    /** 等待结束且成功：收掉进度条并记一行 OK。 */
    public void spinOk(String text) {
        spinText = null;
        log(text, BootLogKind.OK);
    }

    /** 等待结束但失败：收掉进度条并记一行 FAILED。 */
    public void spinFail(String text) {
        spinText = null;
        log(text, BootLogKind.FAIL);
    }

    /** 收掉进度条而不记任何结果（例如界面被关掉）。 */
    public void spinCancel() {
        spinText = null;
    }

    /** 有没有正在等待的那一行。 */
    public boolean spinning() {
        return spinText != null;
    }

    /**
     * 推进进度条；每帧调一次。
     *
     * <p>涨到 95% 就压住不动：等待是真的没结束，涨满就是在撒谎（而 100% 只该由
     * {@link #spinOk} 给出）。速度按「目标 15 秒走完」算，比真实 systemd 稍快一点。
     */
    public void tick() {
        if (spinText != null) spinProgress = Math.min(0.95f, spinProgress + 1f / (15f * 20f));
    }

    /** 当前可见行（时间戳按发生时刻冻结，不会随刷新而变化）。 */
    public List<BootLogRow> rows() {
        List<BootLogRow> rows = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            rows.add(BootLogRow.plain(e.kind(), stamp(e.atSeconds()) + e.text(), false));
        }
        return rows;
    }

    /** 正在等待的那一行（含进度条），没有则 {@code null}。 */
    public BootLogRow spinnerRow() {
        if (spinText == null) return null;
        BootMeter meter = new BootMeter("[", "]", '*', ' ', spinnerWidth);
        return new BootLogRow(spinKind, spinText + " " + meter.bar(spinProgress), meter, spinProgress, true);
    }

    /** 已经记了多少行。 */
    public int size() {
        return entries.size();
    }

    /** 清空（进入世界前收尾、或换配置重建时用）。 */
    public void clear() {
        entries.clear();
        spinText = null;
        spinProgress = 0f;
    }

    /** 与内核日志同一套时间戳格式：{@code [    1.020000]}，12 字符宽右对齐。 */
    public static String stamp(double seconds) {
        return "[" + String.format(Locale.ROOT, "%12.6f", seconds) + "] ";
    }

    private double seconds() {
        return (now() - originMs) / 1000d;
    }

    private long now() {
        return clock.getAsLong();
    }
}
