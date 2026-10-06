/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 启动日志时刻表：只守那几条「肉眼看不出、错了才发现」的边界。
 *
 * <p>为什么值这些断言：这套时间计算全在渲染循环里跑，错了没有异常、没有日志，
 * 只有「时间戳少打了一位」「进度条每次换文案都从零开始」这类要盯着屏幕才看得出的毛病。
 */
class BootLogTimelineTest {

    private static BootLogTimeline sample() {
        BootLogTimeline.Builder b = new BootLogTimeline.Builder();
        b.speed(100).meter(BootMeter.SYSTEMD);
        b.line("[    0.000000] Linux version 6.8.0-45-generic", BootLogKind.KERNEL);
        b.line("[    0.500000] Memory: 32G available", BootLogKind.KERNEL);
        b.line("Started Journal Service.", BootLogKind.OK);
        b.line("Failed to start ccnr-agent.service.", BootLogKind.FAIL);
        return b.build();
    }

    /** 刚开始：一行都没有（不能一上来就把整份日志糊在屏幕上）。 */
    @Test
    void startsEmpty() {
        assertTrue(sample().at(0, 10).visibleLines() <= 1);
    }

    /** 一行是**逐字**打出来的，不是整行蹦出来。 */
    @Test
    void revealsCharByChar() {
        BootLogTimeline t = sample();
        String atStart = t.at(0, 10).rows().get(0).text();
        String later = t.at(10, 10).rows().get(0).text();
        assertTrue(atStart.length() < later.length(), "0ms 与 10ms 打出的字符数应当不同: " + atStart.length());
    }

    /** 可见窗口是「最后 rows 行」：日志往上顶，老行不会重新出现。 */
    @Test
    void windowKeepsLastRows() {
        BootLogTimeline t = sample();
        BootLogSnapshot all = t.at(t.totalMs(), Integer.MAX_VALUE);
        int total = all.visibleLines();
        BootLogSnapshot tail = t.at(t.totalMs(), 2);
        assertEquals(2, tail.visibleLines());
        assertEquals(all.rows().subList(total - 2, total), tail.rows());
    }

    /**
     * 内核时间戳由时刻表**现算**，且是**按行**算的。
     *
     * <p>两个方向都要钉住：
     * ① 第一行（原文 0.000000、且它就排在 0ms）打出的就是 {@code 0.000000}——
     *    这条曾经误报过：初版断言写的是「原文那句不该出现」，而开场第 0 秒的值字面相同；
     * ② 后面的行必须**大于 0**，即时间戳真的在前进，而不是一屏照抄 0.000000。
     */
    @Test
    void kernelStampFollowsLine() {
        BootLogTimeline t = sample();
        List<BootLogRow> rows = t.at(t.totalMs(), Integer.MAX_VALUE).rows();
        var kernel = rows.stream().filter(r -> r.kind() == BootLogKind.KERNEL).toList();
        assertTrue(kernel.size() >= 2, "样例里应当有至少两行内核行");

        assertTrue(
                kernel.get(0).text().startsWith("[    0.000000] "),
                "第一行内核行（原文 0.000000）应当打出 0.000000: " + kernel.get(0).text());
        double last = BootLogTimeline.parseStamp(kernel.get(kernel.size() - 1).text());
        assertTrue(last > 0.4, "最后一行内核行的时间戳应当明显大于 0（时间戳要前进）: " + last);
    }

    /**
     * 已印出来的行，时间戳**不许再变**。
     *
     * <p>真实内核日志里 {@code [    1.020000]} 是印上去那一刻定死的，之后再刷新屏幕也不会改。
     * 早先的写法把「现在几点」当作时间戳，于是每一行都在**改写历史**——
     * 同一行在 t=2.5s 与 t=3.0s 会印出两个不同的值，这是最容易被一眼看穿的假。
     */
    @Test
    void printedStampsNeverChange() {
        BootLogTimeline t = sample();
        long late = t.totalMs();
        java.util.Map<String, String> bodyToStamp = new java.util.HashMap<>();
        for (BootLogRow row : t.at(late, Integer.MAX_VALUE).rows()) {
            if (row.kind() != BootLogKind.KERNEL) continue;
            bodyToStamp.put(
                    row.text(), row.text().substring(0, Math.min(14, row.text().length())));
        }

        // 更晚的时刻再取一遍：同一行的正文必须还带着同一个时间戳
        for (BootLogRow row : t.at(late + 5000, Integer.MAX_VALUE).rows()) {
            if (row.kind() != BootLogKind.KERNEL) continue;
            String expected = bodyToStamp.get(row.text());
            if (expected == null) continue; // 这一行是这段时间里新出现的，不在比对范围
            assertTrue(row.text().startsWith(expected), "已印出的内核行时间戳变了（历史被改写）: " + expected + " → " + row.text());
        }
    }

    /** 时间戳必须随**行**前进：后出现的行时间戳更大（否则一屏全一样大）。 */
    @Test
    void stampsGrowLineByLine() {
        BootLogTimeline t = sample();
        double previous = -1;
        for (BootLogRow row : t.at(t.totalMs(), Integer.MAX_VALUE).rows()) {
            if (row.kind() != BootLogKind.KERNEL) continue;
            double stamp = BootLogTimeline.parseStamp(row.text());
            assertTrue(stamp > previous, "后出现的行时间戳应当更大: " + previous + " → " + stamp);
            previous = stamp;
        }
    }

    /** 非内核行不该长出时间戳。 */
    @Test
    void plainLinesHaveNoStamp() {
        BootLogTimeline t = sample();
        for (BootLogRow row : t.at(t.totalMs(), Integer.MAX_VALUE).rows()) {
            if (row.kind() == BootLogKind.KERNEL) continue;
            assertFalse(row.text().startsWith("["), "非内核行不该有时间戳: " + row.text());
        }
    }

    /** 进度的两条纪律：**单调不减**，且**换文案时不回零**（按整条作业算，不按当前那句）。 */
    @Test
    void meterIsMonotonicAcrossVariants() {
        BootLogTimeline.Builder b = new BootLogTimeline.Builder();
        b.spinner(
                List.of("A start job is running for Hold until boot", "[  OK  ] Reached target"),
                "no limit",
                BootLogKind.RUN);
        BootLogTimeline t = b.build();

        float previous = -1f;
        for (long ms = 0; ms <= t.totalMs(); ms += 100) {
            BootLogRow spin = t.at(ms, 10).spinner();
            assertNotNull(spin, "进度条在整段时间里都该在: " + ms);
            assertTrue(spin.progress() >= previous, "进度条回退了: " + ms + " " + spin.progress() + " < " + previous);
            assertTrue(spin.progress() <= 0.9f, "进行中的行不该涨满: " + spin.progress());
            previous = spin.progress();
        }
    }

    /** 进度条文案本身也要逐字打出来（整句换掉看起来像幻灯片，不像终端）。 */
    @Test
    void spinnerRevealsCharByChar() {
        BootLogTimeline.Builder b = new BootLogTimeline.Builder();
        b.spinner(List.of("A start job is running for Hold until boot"), "", BootLogKind.RUN);
        BootLogTimeline t = b.build();
        assertTrue(t.at(0, 10).spinner().text().length()
                < t.at(20, 10).spinner().text().length());
    }

    /** 没有进行中的作业时 {@code spinner} 必须是 {@code null}（调用方靠它决定要不要画标记）。 */
    @Test
    void spinnerIsNullWhenNothingRuns() {
        assertNull(sample().at(5000, 10).spinner());
        assertNull(BootLogTimeline.empty().at(5000, 10).spinner());
        assertTrue(BootLogTimeline.empty().at(5000, 10).rows().isEmpty());
    }

    /** 标记列宽固定：{@code [  OK  ]} 与 {@code [FAILED]} 必须一样宽，否则整列对不齐。 */
    @Test
    void markersAreSameWidth() {
        assertEquals(BootLogKind.OK.marker().length(), BootLogKind.FAIL.marker().length());
        assertEquals(BootLogKind.RUN.marker().length(), BootLogKind.OK.marker().length());
        assertEquals(BootLogKind.RUN.marker(), BootLogKind.RUN.markerFor(false));
        assertEquals(BootLogKind.OK.marker(), BootLogKind.RUN.markerFor(true));
    }

    /**
     * 同一时刻取两次，窗口必须**逐字相同**。
     *
     * <p>这是 purity 的性质：{@code at(ms, rows)} 不读时钟、不读随机数。一旦有人在里面
     * 掺进「当前时间」或随机量，画面就会在窗口缩放重建背景时莫名其妙地跳一下——
     * 而且只有那一种操作能复现。
     */
    @Test
    void sameInstantIsStable() {
        BootLogTimeline t = sample();
        for (long ms : new long[] {0, 250, 1500, 5000}) {
            assertEquals(t.at(ms, 5).rows(), t.at(ms, 5).rows(), "同一时刻两次取出来的窗口不一致: " + ms);
        }
    }
}
