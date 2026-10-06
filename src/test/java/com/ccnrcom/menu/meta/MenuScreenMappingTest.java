/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.meta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ccnrcom.menu.config.MenuAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「界面白名单」与「界面映射」必须一一对应。
 *
 * <p>为什么需要一道静态门禁：两处分居不同包（{@code config.MenuAction.SCREENS} 与
 * {@code client.MenuActions.screenFor}），**不一致时谁都不会编译失败**。
 * 症状是配置里写了一个合法的界面 id，按钮点下去却什么都不发生——而玩家只会以为游戏卡了。
 * 这条门禁把「白名单」与「switch 分支」钉在一起，改一处忘了另一处立刻红。
 */
class MenuScreenMappingTest {

    private static final Pattern CASE = Pattern.compile("case\\s+\"([a-z_]+)\"\\s*->");

    private static String menuActionsSource() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("src/main/java/com/ccnrcom/menu/client/MenuActions.java");
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    fail("读取 MenuActions.java 失败: " + e);
                }
            }
            dir = dir.getParent();
        }
        return fail("找不到 MenuActions.java，门禁失效");
    }

    /** {@code screenFor} 里的 switch 区域（避免把别的 switch 的 case 也算进来）。 */
    private static String screenSwitchBody(String source) {
        int start = source.indexOf("screenFor(Minecraft");
        assertTrue(start > 0, "找不到 screenFor 方法，门禁失效");
        int open = source.indexOf("switch (id) {", start);
        assertTrue(open > 0, "screenFor 里找不到 switch (id)，门禁失效");
        int close = source.indexOf("};", open);
        return source.substring(open, close > 0 ? close : source.length());
    }

    private static Set<String> mappedScreens() {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = CASE.matcher(screenSwitchBody(menuActionsSource()));
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    @Test
    @DisplayName("白名单里的每个界面 id 都有对应的分支（否则按钮点了没反应）")
    void everyWhitelistedScreenIsMapped() {
        Set<String> mapped = mappedScreens();
        Set<String> missing = new TreeSet<>(MenuAction.SCREENS);
        missing.removeAll(mapped);

        assertTrue(missing.isEmpty(), "MenuAction.SCREENS 里有 id 在 MenuActions.screenFor 中没有分支: " + missing);
    }

    @Test
    @DisplayName("每个分支的 id 都在白名单里（否则它永远无法被配置触发）")
    void everyMappedScreenIsWhitelisted() {
        Set<String> extra = new TreeSet<>(mappedScreens());
        extra.removeAll(MenuAction.SCREENS);

        assertTrue(extra.isEmpty(), "MenuActions.screenFor 里有白名单之外的 id（配置永远走不到）: " + extra);
    }

    @Test
    @DisplayName("扫描确实扫到了内容（门禁自身不能静默失效）")
    void scanIsNotVacuous() {
        assertTrue(mappedScreens().size() >= 5, "分支数量异常少，扫描正则可能失效: " + mappedScreens());
        assertFalse(MenuAction.SCREENS.contains("realms"), "realms 界面类不在客户端 jar 里，不能放进白名单");
    }
}
