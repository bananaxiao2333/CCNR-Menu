/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动日志驱动文件的解析：把一份文本变成「行 + 类型」。
 *
 * <p>格式（一行一条，{@code #} 开头是注释，空行忽略）：
 *
 * <pre>
 * [ok]     Started Journal Service.
 * [kernel] [    0.000000] Linux version 6.8.0-45-generic
 *          没有方括号前缀的行 = info
 * </pre>
 *
 * <p>为什么让作者用文本文件而不是在 {@code menu.json} 里塞一长串字符串数组：
 * 内核日志里全是反斜杠、引号、逗号（{@code UUID=...}、{@code [mem 0x…]}），
 * 塞进 JSON 之后每一条都要转义一次，而转义错了只会得到「有一行显示得不对」——
 * 这类错误没人查得动。文本文件里写什么就是什么。
 *
 * <p>纯类：不碰文件系统也不碰游戏，{@link #parse(String)} 可以直接单测。
 */
public final class BootLogLines {

    /** 一行解析结果：类型 + 去掉前缀后的正文。 */
    public record Entry(BootLogKind kind, String text) {}

    private BootLogLines() {}

    /** 从类路径读一份内置驱动；读不到返回空表（调用方负责记日志，不静默）。 */
    public static List<Entry> loadResource(ClassLoader loader, String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new IOException("内置启动日志不存在: " + path);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            return parse(sb.toString());
        }
    }

    /**
     * 解析全文。**不认识的类型名按 info 处理**并由调用方记警告
     * （{@link #parse(String, List)} 会把它们收进 {@code warnings}）。
     */
    public static List<Entry> parse(String text) {
        return parse(text, new ArrayList<>());
    }

    /**
     * @param warnings 收集「认不出的状态名」这类问题；配置错误不静默是项目纪律
     */
    public static List<Entry> parse(String text, List<String> warnings) {
        List<Entry> entries = new ArrayList<>();
        if (text == null) return entries;
        for (String raw : text.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            BootLogKind kind = BootLogKind.INFO;
            String body = line;
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end > 0) {
                    String name = line.substring(1, end).trim();
                    body = line.substring(end + 1).stripLeading();
                    if (!BootLogKind.isKnown(name)) {
                        warnings.add("启动日志里有认不出的状态名 [" + name + "]，该行按 info 处理（可用: kernel/ok/run/fail/info/accent）");
                    } else {
                        kind = BootLogKind.parse(name);
                    }
                    // 内核行的正文里**保留**原文那个秒数：它是这一行在动画里何时出现的
                    // 唯一依据（时刻表按它排队）。摘掉它的是 Builder.line——
                    // 它一边取走秒数一边把文本存成不含时间戳的正文，于是上屏时
                    // 只会有一次「现算的时间戳」，而不是两份叠在一起。
                }
            }
            if (body.isEmpty()) continue;
            entries.add(new Entry(kind, body));
        }
        return entries;
    }

    /** 组装成时刻表。{@code spinnerLines} 为空时不加「进行中」那一行。 */
    public static BootLogTimeline toTimeline(
            List<Entry> entries, long spacingMs, List<String> spinnerLines, BootMeter meter) {
        BootLogTimeline.Builder b =
                new BootLogTimeline.Builder().speed(spacingMs).meter(meter);
        // 内核行带着原文秒数进来（Builder 取走它定节奏，并把文本换成不含时间戳的正文），
        // 其余行按 speed 顺序排队。**不要**在这里给内核行补一个 "0.000000" 占位：
        // 那会让所有内核行都从 0 开始排队（一屏时间戳全一样），而且上屏时和现算的那份叠成两个。
        for (Entry e : entries) {
            b.line(e.text(), e.kind());
        }
        if (spinnerLines != null && !spinnerLines.isEmpty()) {
            b.spinner(spinnerLines, null, BootLogKind.RUN);
        }
        return b.build();
    }
}
