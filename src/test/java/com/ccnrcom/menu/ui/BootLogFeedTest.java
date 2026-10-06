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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 运行期事件流：按钮点击、连接成功/失败、世界加载。
 *
 * <p>守的三条都是「只在实机上偶发、看日志看不出」的性质：
 * 时间戳按**发生时刻**冻结、进度条不回退也不撒谎涨满、行数有上限。
 */
class BootLogFeedTest {

    /** 假时钟：秒数可控，断言就不用 sleep。 */
    private static final class FakeClock {
        final AtomicLong ms = new AtomicLong(1_000_000);

        BootLogFeed feed(int width) {
            return new BootLogFeed(ms.get(), width, ms::get);
        }

        void advance(long delta) {
            ms.addAndGet(delta);
        }
    }

    @Test
    @DisplayName("记一行就多一行，时间戳随发生时刻前进且此后不再变")
    void linesCarryFrozenStamps() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);

        feed.log("按下：进入服务器 → connect:mc.example.com");
        clock.advance(1500);
        feed.log("已加入 mc.example.com", BootLogKind.OK);

        List<BootLogRow> rows = feed.rows();
        assertEquals(2, rows.size());
        assertEquals(
                "[    0.000000] 按下：进入服务器 → connect:mc.example.com", rows.get(0).text());
        assertTrue(rows.get(1).text().startsWith("[    1.500000] "), rows.get(1).text());
        assertEquals(BootLogKind.OK, rows.get(1).kind());

        // 再走 10 秒：已经记下的两行必须逐字不变
        clock.advance(10_000);
        assertEquals(rows, feed.rows(), "事件行的时间戳冻结了才不会被每帧改写");
    }

    @Test
    @DisplayName("空行与 null 不记（别在日志里制造空行）")
    void ignoresBlank() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);
        feed.log(null);
        feed.log("   ");
        feed.log("", BootLogKind.OK);
        assertEquals(0, feed.size());
        assertTrue(feed.rows().isEmpty());
    }

    @Test
    @DisplayName("进度条：等待中单调涨、封顶 95%、成功/失败后立刻消失")
    void spinnerLifecycle() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);

        assertFalse(feed.spinning());
        assertNull(feed.spinnerRow());

        feed.spin("正在连接 mc.example.com");
        assertTrue(feed.spinning());

        float previous = -1f;
        for (int i = 0; i < 400; i++) {
            clock.advance(100);
            feed.tick();
            BootLogRow row = feed.spinnerRow();
            assertNotNull(row, "等待中那一行必须一直在");
            assertTrue(row.progress() >= previous, "进度条回退了: " + previous + " → " + row.progress());
            previous = row.progress();
        }
        assertTrue(previous <= 0.95f, "等待没结束就涨满是在撒谎: " + previous);
        assertTrue(previous > 0.5f, "等了 40 秒还几乎没动: " + previous);
        assertTrue(feed.spinnerRow().text().contains("["), "进度条要画在方括号里");

        feed.spinOk("已加入 mc.example.com");
        assertFalse(feed.spinning());
        assertNull(feed.spinnerRow());
        assertEquals(BootLogKind.OK, feed.rows().get(feed.size() - 1).kind());
    }

    @Test
    @DisplayName("「正在等待」只认进度条本身：记了一行不算在等（居中标志靠这一条才不会被永久挡掉）")
    void loggedLinesAreNotWaiting() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);
        assertFalse(feed.spinning());

        feed.log("单人游戏 → 打开界面");
        assertEquals(1, feed.size(), "这一行确实记下来了");
        assertFalse(feed.spinning(), "记行不等于在等待——它只增不减，当成等待就会把标志永久挡住");

        feed.spin("正在连接 mc.example.com");
        assertTrue(feed.spinning());
        feed.spinCancel();
        assertFalse(feed.spinning());
        assertEquals(1, feed.size(), "取消等待不该顺手留下一行");
    }

    @Test
    @DisplayName("失败收尾：进度条消失，留下一条 FAILED")
    void spinnerFailure() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);
        feed.spin("正在连接 mc.example.com");
        feed.spinFail("连接已关闭");
        assertFalse(feed.spinning());
        assertEquals(BootLogKind.FAIL, feed.rows().get(0).kind());
    }

    @Test
    @DisplayName("行数有上限：长时间挂机不会把绘制拖死，且保留的是最新那些行")
    void keepsNewestLinesOnly() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);
        for (int i = 0; i < 260; i++) {
            feed.log("第 " + i + " 行");
            clock.advance(10);
        }
        assertTrue(feed.size() <= 200, "行数上限没生效: " + feed.size());
        String last = feed.rows().get(feed.size() - 1).text();
        assertTrue(last.endsWith("第 259 行"), "保留的应当是最新的行: " + last);
    }

    @Test
    @DisplayName("清空：行与进度条一起收掉（进世界前收尾用）")
    void clearResetsEverything() {
        FakeClock clock = new FakeClock();
        BootLogFeed feed = clock.feed(12);
        feed.log("一行");
        feed.spin("等待中");
        feed.clear();
        assertEquals(0, feed.size());
        assertFalse(feed.spinning());
    }
}
