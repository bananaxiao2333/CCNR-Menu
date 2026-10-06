/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.Locale;

/**
 * 启动日志里一行属于哪一类：决定它左边那个 systemd 标记、颜色、进度条怎么走。
 *
 * <p>纯枚举，没有任何 MC import：颜色给的是 **ARGB 打包 int**（与 {@link ColorSpec} 同一套），
 * 上屏时由 {@code BootLogRenderer} 交给 {@code GuiGraphics.drawString}，纯逻辑这边只用它做单测。
 *
 * <p>颜色是为了「一眼看出哪几行不对」而选的：成功绿 / 失败红 / 进行中黄三者的对比度都够，
 * 且与主题色（青）区分得开——否则「状态用颜色区分」等于没区分。缺任何一个通道都不行：
 * 只靠颜色区分等于对色盲用户不可读，所以**标记本身也带字**（{@code [  OK  ]} / {@code [FAILED]}）。
 */
public enum BootLogKind {

    /**
     * 内核行（{@code [    0.000000] dmesg 正文}）：整体偏暗，是背景噪音，不该抢注意力。
     *
     * <p>标记是**空串**——内核行的时间戳由 {@link BootLogTimeline} 按时刻现算，
     * 写在这里就会和它叠成两个（本类踩过这个坑）。
     */
    KERNEL("", 0xFFB8B8C0, "37"),

    /** systemd 成功：{@code [  OK  ]}。 */
    OK("[  OK  ] ", 0xFF5FD75F, "32"),

    /** systemd 进行中：{@code [ ***  ]}，走完变 {@code [  OK  ]}。 */
    RUN("[ ***  ] ", 0xFFE6C34A, "33"),

    /** systemd 失败：{@code [FAILED]}。 */
    FAIL("[FAILED] ", 0xFFE05A5A, "31"),

    /** 纯文本行（服务状态、自定义说明）。 */
    INFO("", 0xFFE6E6EE, "97"),

    /** 强调行（版本、主机名、横幅）。 */
    ACCENT("", 0xFF4FD1E0, "36");

    /** 括号内固定 6 格，与真实 systemd 输出一致（列对不齐一眼就假）。 */
    private static final int MARKER_CELLS = 6;

    private final String marker;
    private final int color;
    private final String ansi;

    BootLogKind(String marker, int color, String ansi) {
        this.marker = marker;
        this.color = color;
        this.ansi = ansi;
    }

    /** 这一类的固定标记（只有 {@link #KERNEL}/{@link #INFO}/{@link #ACCENT} 是空串）。 */
    public String marker() {
        return switch (this) {
            case OK -> bracket("OK");
            case FAIL -> bracket("FAILED");
            case RUN -> bracket("***");
            default -> marker;
        };
    }

    /**
     * 按「这一行是否已经结束」给标记——这是让 {@code [ ***  ]} 变成 {@code [  OK  ]} 的地方。
     * 只画一个静态标记等于进度条永远停在开头，一眼假。
     */
    public String markerFor(boolean done) {
        return this == RUN && done ? bracket("OK") : marker();
    }

    /** ARGB 颜色（上屏用）。 */
    public int color() {
        return color;
    }

    /** 终端预览用的 ANSI 码（{@code tools/BootLogDemo} 用，游戏里用不到）。 */
    public String ansi() {
        return ansi;
    }

    /** 进度条分几段走：真实系统的 {@code [ ***  ]} 是一格一格长的，不是平滑填充。 */
    public int stages() {
        return switch (this) {
            case OK, FAIL -> 8;
            default -> 12;
        };
    }

    /** 这一行结束时进度条停在哪一格（0..1）；进行中的行**不该涨满**——涨满就是在撒谎。 */
    public float targetProgress() {
        return this == RUN ? 0.9f : 1f;
    }

    private static String bracket(String inner) {
        int left = (MARKER_CELLS - inner.length()) / 2;
        int right = MARKER_CELLS - inner.length() - left;
        return "[" + " ".repeat(left) + inner + " ".repeat(right) + "] ";
    }

    /** 配置里的状态名 → 枚举；认不出返回 {@link #INFO}（配置错误不静默，由调用方记警告）。 */
    public static BootLogKind parse(String raw) {
        if (raw == null) return INFO;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "kernel", "dmesg" -> KERNEL;
            case "ok", "done" -> OK;
            case "run", "running", "progress" -> RUN;
            case "fail", "failed", "error" -> FAIL;
            case "accent", "hl", "highlight" -> ACCENT;
            default -> INFO;
        };
    }

    /** 状态名是否被认识（配置校验用：认不出要打警告，不能悄悄变 INFO）。 */
    public static boolean isKnown(String raw) {
        if (raw == null) return true;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "kernel",
                    "dmesg",
                    "ok",
                    "done",
                    "run",
                    "running",
                    "progress",
                    "fail",
                    "failed",
                    "error",
                    "info",
                    "text",
                    "accent",
                    "hl",
                    "highlight" -> true;
            default -> false;
        };
    }
}
