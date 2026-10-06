/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 启动日志的**时刻表**：某一时刻该露出到第几行、每行露出到第几个字符、进度条走到几分、
 * 内核时间戳该是多少。
 *
 * <p>为什么单独一个纯类：这套「逐字打字 + 行累积 + 时间戳前进」最容易写错的都是**边界**——
 * 刚好换行那一帧、刚好打完最后一个字符那一帧。放在渲染代码里只能靠肉眼看，而肉眼看不出
 * 「少打了一个字符」。这里可以直接断言。
 *
 * <p>三条规则：
 * <ol>
 *   <li><b>时间是单调的</b>：{@link #at(long, int)} 对同一个 {@code ms} 永远给出同一份快照，
 *       不读时钟、不读随机数——否则窗口缩放重建背景时画面会跳一下。</li>
 *   <li><b>行只会往上走</b>：可见窗口是「最后 {@code rows} 行」，老行不会重新出现。</li>
 *   <li><b>内核时间戳自己前进</b>：原文里的 {@code [    0.000000]} 只是**占位**，
 *       真实值 = 该行在原文里的秒数 × {@code kernelSpacing} + 当前时刻。
 *       照抄原文会让一屏全是 {@code 0.000000}（那正是内核日志里最假的一处）。</li>
 * </ol>
 */
public final class BootLogTimeline {

    /** 一行打完的速度（毫秒/字符）：约 900 字符/秒，快，但看得出在逐字打。 */
    public static final long MS_PER_CHAR = 1;

    /** 进度条文案的轮换节奏（毫秒）。 */
    public static final long SPIN_MS = 900;

    /** 内核时间戳的默认换算系数：原文秒数 × 它就是这一行在动画里的真实时刻。 */
    public static final double KERNEL_SPACING = 0.9;

    private final List<Line> lines;
    private final List<Spinner> spinners;
    private final long totalMs;

    private BootLogTimeline(List<Line> lines, List<Spinner> spinners, long totalMs) {
        this.lines = List.copyOf(lines);
        this.spinners = List.copyOf(spinners);
        this.totalMs = totalMs;
    }

    /**
     * 取某一时刻的可见画面。
     *
     * @param rows 屏幕能放几行；超出时向上顶（日志是滚动的）
     */
    public BootLogSnapshot at(long ms, int rows) {
        long t = Math.max(0, ms);
        List<BootLogRow> all = new ArrayList<>(lines.size());
        for (Line line : lines) {
            if (line.startMs() > t) break;
            all.add(line.rowAt(t));
        }
        int from = rows >= Integer.MAX_VALUE ? 0 : Math.max(0, all.size() - Math.max(1, rows));
        List<BootLogRow> visible = List.copyOf(all.subList(from, all.size()));

        BootLogRow spinner = null;
        for (Spinner spin : spinners) {
            if (spin.startMs() > t) break;
            spinner = spin.rowAt(t);
        }
        return new BootLogSnapshot(visible, lines.size(), spinner, t);
    }

    /** 不分页的便捷重载（单测用）。 */
    public BootLogSnapshot at(long ms) {
        return at(ms, Integer.MAX_VALUE);
    }

    /** 日志总时长（最后一行打完的时刻）。 */
    public long totalMs() {
        return totalMs;
    }

    /** 总行数。 */
    public int lineCount() {
        return lines.size();
    }

    /** 没有可显示的行（驱动没读到 / 驱动是空的）。 */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** 空日志（配置没写、文件读失败时的兜底：什么都不画，交给下层背景）。 */
    public static BootLogTimeline empty() {
        return new BootLogTimeline(List.of(), List.of(), 0L);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 一行。
     *
     * @param stampOffset 原文内核时间戳（秒）；{@code NaN} 表示这行不该带时间戳（systemd 段）
     */
    private record Line(
            long startMs, long revealMs, String text, BootLogKind kind, double stampOffset, BootMeter meter) {

        BootLogRow rowAt(long t) {
            long elapsed = t - startMs;
            String body = text;
            if (revealMs > 0 && elapsed < revealMs) {
                int chars = (int) (text.length() * elapsed / (double) revealMs);
                body = text.substring(0, Math.min(text.length(), Math.max(0, chars)));
            }
            boolean partial = body.length() < text.length();
            // 时间戳用**这一行自己的开始时刻**，不是"现在几点"。
            // 用现在几点的话，已经印出来的行每帧都在改历史——真实内核日志里
            // [    1.020000] 是印上去就定死的，改历史是凡人一眼就能看出的假。
            if (!Double.isNaN(stampOffset)) body = stampAt(startMs, stampOffset) + body;
            if (meter == null) return BootLogRow.plain(kind, body, partial);
            float progress = meterProgress(elapsed, kind);
            return new BootLogRow(kind, body + " " + meter.bar(progress), meter, progress, partial);
        }

        /**
         * 内核时间戳：{@code [    3.500000]} 这种 12 字符宽的右对齐格式。
         * 宽度写死 12 是刻意的——真实内核日志就是这个宽度，列不齐一眼就假。
         */
        private static String stampAt(long ms, double offsetSeconds) {
            return "[" + String.format(Locale.ROOT, "%12.6f", offsetSeconds + ms / 1000d) + "] ";
        }
    }

    /**
     * 一行「进行中」的作业：{@code variants} 每隔 {@link #SPIN_MS} 换一条文案
     * （真实系统就是这么刷的：{@code A start job is running (3s / no limit)} → {@code [  OK  ]}）。
     *
     * <p>文案仍然是**逐字打出**的：整条换掉的话看起来像幻灯片，不像终端。
     */
    private record Spinner(long startMs, List<String> variants, List<Long> starts, BootLogKind kind, BootMeter meter) {

        BootLogRow rowAt(long t) {
            int index = 0;
            for (int i = 0; i < starts.size(); i++) {
                if (starts.get(i) <= t) index = i;
            }
            String text = variants.get(index);
            long localStart = starts.get(index);
            long revealMs = Math.max(0, text.length() * MS_PER_CHAR);
            String body = text;
            long elapsed = t - localStart;
            if (revealMs > 0 && elapsed < revealMs) {
                int chars = (int) (text.length() * elapsed / (double) revealMs);
                body = text.substring(0, Math.max(0, chars));
            }
            // 进度条按**整条作业**的进度走，不按当前这句文案——换句文案不该把进度条打回零
            float progress = meterProgress(t - startMs, kind);
            // 进度条那一行的标记由**调用方**给（它才知道这一行结束了没有），这里不拼，
            // 否则上屏那边再拼一次就是两个 [  OK  ]
            return new BootLogRow(
                    kind, body + " " + meter.bar(progress), meter, progress, body.length() < text.length());
        }
    }

    /**
     * 进度条填充：真实系统里「A start job is running」是一格一格长的，到某个比例就压住不动。
     * 一直涨满再回退（或者每句文案都从零开始）是最假的两种画法。
     */
    private static float meterProgress(long elapsedMs, BootLogKind kind) {
        int stages = Math.max(1, kind.stages());
        return Math.min(kind.targetProgress(), elapsedMs / (float) (SPIN_MS * stages));
    }

    /** 原文里的 {@code [    0.000000]} 解析成秒；不是内核行返回 {@code NaN}。 */
    public static double parseStamp(String text) {
        if (text == null || !text.startsWith("[")) return Double.NaN;
        int end = text.indexOf(']');
        if (end < 6 || end > 18) return Double.NaN;
        try {
            return Double.parseDouble(text.substring(1, end).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 去掉原文开头的 {@code [    0.000000] }，只留正文（时间戳由上屏那边按时刻重算）。 */
    public static String stripStamp(String text) {
        if (Double.isNaN(parseStamp(text))) return text;
        return text.substring(text.indexOf(']') + 1).stripLeading();
    }

    /** 逐行拼装。 */
    public static final class Builder {

        private final List<Line> lines = new ArrayList<>();
        private final List<Spinner> spinners = new ArrayList<>();
        private long cursorMs;
        private long perLineMs = 95;
        private double kernelSpacing = KERNEL_SPACING;
        private BootMeter meter = BootMeter.SYSTEMD;

        /**
         * 追加一行。内核行（以 {@code [ 秒数]} 开头）会自动：① 把原文时间戳换成偏移量，
         * ② 让这一行的出现时刻也跟着这个偏移走（内核行不该等前面每行 95ms 排完）。
         */
        public Builder line(String text, BootLogKind kind) {
            double stamp = parseStamp(text);
            String body = stripStamp(text);
            long start = cursorMs;
            if (!Double.isNaN(stamp)) start = Math.max(cursorMs, Math.round(stamp * kernelSpacing * 1000d));
            long reveal = Math.max(0, body.length() * MS_PER_CHAR);
            lines.add(new Line(start, reveal, body, kind, stamp, null));
            cursorMs = Math.max(cursorMs, start + reveal) + perLineMs;
            return this;
        }

        /**
         * 追加一组「进行中」的文案：{@code variants} 每隔 {@link #SPIN_MS} 换一条。
         * 真实系统就是这么刷的（先 {@code A start job is running (3s / no limit)}，
         * 再变成越来越长的 {@code [ ***     ]}）。
         */
        public Builder spinner(List<String> variants, String limit, BootLogKind kind) {
            List<String> texts = new ArrayList<>(variants.size());
            List<Long> starts = new ArrayList<>(variants.size());
            for (int i = 0; i < variants.size(); i++) {
                String variant = variants.get(i);
                texts.add(limit == null || limit.isBlank() ? variant : variant + " (" + (i + 1) + "s / " + limit + ")");
                starts.add(cursorMs + (long) i * SPIN_MS);
            }
            spinners.add(new Spinner(cursorMs, List.copyOf(texts), List.copyOf(starts), kind, meter));
            cursorMs += (long) variants.size() * SPIN_MS;
            return this;
        }

        /** 两行之间的节奏（毫秒）。 */
        public Builder speed(long perLineMs) {
            this.perLineMs = Math.max(0, perLineMs);
            return this;
        }

        /** 内核时间戳的换算系数：1.0 = 照抄原文秒数。 */
        public Builder kernelSpacing(double spacing) {
            this.kernelSpacing = Math.max(0.01, spacing);
            return this;
        }

        public Builder meter(BootMeter meter) {
            this.meter = meter == null ? BootMeter.SYSTEMD : meter;
            return this;
        }

        public BootLogTimeline build() {
            long end = 0;
            for (Line line : lines) end = Math.max(end, line.startMs() + line.revealMs());
            for (Spinner spin : spinners)
                end = Math.max(end, spin.startMs() + (long) spin.variants().size() * SPIN_MS);
            return new BootLogTimeline(lines, spinners, end);
        }
    }
}
