/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.config.BootLogSpec;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.ui.BootLogKind;
import com.ccnrcom.menu.ui.BootLogLines;
import com.ccnrcom.menu.ui.BootLogTimeline;
import com.ccnrcom.menu.util.JsonUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 内置启动日志的门禁：**配置里写的 {@code builtin} 名必须真的随模组发布**。
 *
 * <p>和 {@code PresetAssetsTest} 守的是同一类错误：名字对不上不会让编译失败，
 * 症状只是「日志那一层不出现」，而作者本地有文件、永远看不到。
 */
class BootLogDriverTest {

    private static final String DRIVER_DIR = "src/main/resources/assets/ccnr_menu/bootlog";

    private static String resource(String path) {
        try (InputStream in = BootLogDriverTest.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static MenuConfig config(String resource) {
        return MenuConfig.parse(
                JsonUtil.readResource(resource).orElseThrow(() -> new AssertionError("内置配置缺失: " + resource)),
                new ArrayList<>());
    }

    @Test
    @DisplayName("默认与示例配置里的 builtin 驱动都随模组发布")
    void builtinDriversAreShipped() {
        for (String cfg :
                List.of("/assets/ccnr_menu/defaults/menu.json", "/assets/ccnr_menu/defaults/menu.example.json")) {
            BootLogSpec spec = config(cfg).bootLog();
            if (spec.builtin().isEmpty()) continue;
            String path = "/assets/ccnr_menu/bootlog/" + spec.builtin() + ".txt";
            assertTrue(resource(path) != null, cfg + " 指向了不存在的内置驱动: " + path);
        }
    }

    @Test
    @DisplayName("默认配置里这一层是关着的（装上等于没装的纪律）")
    void offByDefault() {
        assertFalse(config("/assets/ccnr_menu/defaults/menu.json").bootLog().enabled());
    }

    @Test
    @DisplayName("内置驱动能被解析成行，且没有认不出的状态名")
    void driverParsesCleanly() {
        String text = resource("/assets/ccnr_menu/bootlog/ubuntu.txt");
        assertTrue(text != null, "内置驱动缺失");

        List<String> warnings = new ArrayList<>();
        List<BootLogLines.Entry> entries = BootLogLines.parse(text, warnings);
        assertTrue(entries.size() >= 20, "内置驱动行数太少: " + entries.size());
        assertEquals(List.of(), warnings, "内置驱动里有认不出的状态名");

        // 内核行必须成组出现：一份启动日志只有 systemd 段是很怪的
        long kernel =
                entries.stream().filter(e -> e.kind() == BootLogKind.KERNEL).count();
        assertTrue(kernel >= 5, "内核行太少: " + kernel);
        assertTrue(entries.stream().anyMatch(e -> e.kind() == BootLogKind.FAIL), "至少要有一条 [FAILED]：状态色全靠它才看得出区分");
    }

    /** 内核行的时间戳格式：{@code [    3.500000] }（12 字符宽右对齐，与真实 dmesg 一致）。 */
    private static final java.util.regex.Pattern KERNEL_STAMP =
            java.util.regex.Pattern.compile("^\\[\\s*\\d+\\.\\d{6}\\] ");

    /**
     * 驱动 → 时刻表：内核行**有且只有一个**由时刻表现算的时间戳，其它行一个都不该有。
     *
     * <p>判据必须锚在行首并带上格式：内核正文里本来就含方括号（{@code BIOS-e820: [mem …]}），
     * 数括号个数的写法会把它一起数进去——本测试第一版就是这么误报的。
     */
    @Test
    @DisplayName("驱动 → 时刻表：内核行只有一个现算的时间戳，其它行没有")
    void timelineKeepsStampsAndMarkers() {
        List<BootLogLines.Entry> entries =
                BootLogLines.parse(resource("/assets/ccnr_menu/bootlog/ubuntu.txt"), new ArrayList<>());
        BootLogTimeline timeline =
                BootLogLines.toTimeline(entries, 40, List.of(), com.ccnrcom.menu.ui.BootMeter.SYSTEMD);
        assertFalse(timeline.isEmpty(), "时刻表不该是空的");

        var matcher = KERNEL_STAMP.matcher("");
        for (var row : timeline.at(timeline.totalMs(), Integer.MAX_VALUE).rows()) {
            if (row.kind() == BootLogKind.KERNEL) {
                assertTrue(matcher.reset(row.text()).find(), "内核行要以现算的时间戳开头: " + row.text());
                assertFalse(
                        row.text().contains("[    0.000000] ") && !row.text().startsWith("[    0.000000] "),
                        "原文占位时间戳没被摘掉: " + row.text());
            } else {
                assertFalse(matcher.reset(row.text()).find(), "非内核行不该有时间戳: " + row.text());
            }
        }
    }

    @Test
    @DisplayName("内置驱动的行数在窗口上限内（64 行）：超了就是**每帧都在画屏幕外的行**")
    void driverFitsRowCap() {
        List<BootLogLines.Entry> entries =
                BootLogLines.parse(resource("/assets/ccnr_menu/bootlog/ubuntu.txt"), new ArrayList<>());
        assertTrue(
                entries.size() <= 64, "内置驱动 " + entries.size() + " 行，超过了 MAX_ROWS=64；" + "多出来的行只会在 GUI 缩放调小时被看见，属于纯浪费");
    }
}
