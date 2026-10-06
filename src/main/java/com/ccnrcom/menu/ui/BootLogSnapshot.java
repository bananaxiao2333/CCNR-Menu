/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.List;

/**
 * 某一时刻的**可见画面**（纯数据，直接单测）。
 *
 * <p>为什么快照里返回的是「已经裁好的行」而不是让绘制方自己去算：可见窗口的进退
 * （打满了要往上顶、行太长要裁）与逐字显露、进度条填充都是**同一套时间计算**，
 * 分成两处一定会漂移——那种偏差只在「刚好第 27 行出现的那一帧」才看得见，实机上极难复现。
 *
 * @param rows 当前可见的行（自上而下，已经是最后 {@code rows} 行）
 * @param totalLines 日志总行数（诊断与单测用）
 * @param spinner 当前正在进行的那条作业（缺省 {@code null}）；与 {@code rows} 里的同一条是**同一份数据**，
 *     单独给一份是为了让「只有进度条在动、没有新行」的帧也能被单测断言
 * @param elapsedMs 这一帧的日志时间
 */
public record BootLogSnapshot(List<BootLogRow> rows, int totalLines, BootLogRow spinner, long elapsedMs) {

    /** 便捷构造：进度条整行文本由 {@link BootLogTimeline} 在内部拼好。 */
    public BootLogSnapshot {}

    /** 当前可见行数。 */
    public int visibleLines() {
        return rows.size();
    }

    /** 是否还有行没出现（用来决定末尾光标闪不闪）。 */
    public boolean finished() {
        return rows.stream().noneMatch(BootLogRow::partial) && spinner == null;
    }
}
