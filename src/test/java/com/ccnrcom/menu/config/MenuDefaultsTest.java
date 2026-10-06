/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 升级迁移门禁。
 *
 * <p>这条门禁是 0.2.0 真实事故的产物：本模组「绝不覆盖已存在的配置」，
 * 于是从 0.1.0 升级上来的玩家拿到的是**旧版默认配置**（原版外观），
 * 现象是「装上了但菜单一点变化都没有」，而日志一切正常。
 * 现在升级时会识别「未被修改过的旧默认」并替换——替换对象的判据必须精确到
 * 「结构完全一致」，判宽了就会覆盖玩家自己写好的布局。
 *
 * <p>清单里为什么必须标版本号：最初这条门禁用「长得像不像原版」当替身判据，
 * 因为「原版外观」当时恰好是我们唯一发布过的默认。0.2.1 起默认菜单本身就是一套自定义布局，
 * 替身判据随之失效。真正要防的是「有人凭印象塞进一份**从没发布过**的配置」，
 * 所以现在每条都要标注发布版本，并去 CHANGELOG 里核对这个版本真的存在。
 */
class MenuDefaultsTest {

    @Test
    @DisplayName("未修改过的旧默认配置（0.1.0 那份）能被识别")
    void detectsLegacyDefault() {
        assertTrue(MenuDefaults.isLegacyUnmodified(diskDefaultFrom("0.1.0")), "0.1.0 的默认配置必须被识别出来");
    }

    @Test
    @DisplayName("0.2.0/0.2.1 那份右侧竖列默认也要被识别（否则从 0.2 升级上来的人看不到新布局）")
    void detectsLegacyDefaultOf02x() {
        assertTrue(MenuDefaults.isLegacyUnmodified(diskDefaultFrom("0.2.1")), "0.2.1 的默认配置必须被识别出来——它是新默认菜单生效的前提");
    }

    @Test
    @DisplayName("格式不同但结构相同也算未修改（玩家可能重排过缩进/键顺序）")
    void structureComparisonIgnoresFormatting() {
        assertTrue(MenuDefaults.isLegacyUnmodified(
                "{\"elements\":[],\"background\":{\"type\":\"vanilla\"},\"vanillaButtons\":true,\"enabled\":true}"));
        assertTrue(
                MenuDefaults.isLegacyUnmodified(
                        """
                {
                    "enabled":     true,
                    "vanillaButtons": true,
                    "background": { "type": "vanilla" },
                    "elements": [ ]
                }
                """));
    }

    @Test
    @DisplayName("玩家改过的配置一律不动（改一个值、加一个字段、加一个元素都不算）")
    void neverTouchesCustomizedConfigs() {
        // 改了背景类型
        assertFalse(MenuDefaults.isLegacyUnmodified(
                "{\"enabled\":true,\"vanillaButtons\":true,\"background\":{\"type\":\"gif\"},\"elements\":[]}"));
        // 关了模组
        assertFalse(MenuDefaults.isLegacyUnmodified(
                "{\"enabled\":false,\"vanillaButtons\":true,\"background\":{\"type\":\"vanilla\"},\"elements\":[]}"));
        // 加了元素
        assertFalse(MenuDefaults.isLegacyUnmodified(
                "{\"enabled\":true,\"vanillaButtons\":true,\"background\":{\"type\":\"vanilla\"},"
                        + "\"elements\":[{\"type\":\"button\",\"text\":\"a\",\"action\":\"quit\"}]}"));
        // 多了一个字段（哪怕值等于默认值）
        assertFalse(
                MenuDefaults.isLegacyUnmodified(
                        "{\"enabled\":true,\"vanillaButtons\":true,\"background\":{\"type\":\"vanilla\"},\"elements\":[],\"theme\":{}}"));
        // 在 0.2.1 那份默认上只把整块挪了个位置：结构与默认不再一致，迁移必须放手
        assertFalse(
                MenuDefaults.isLegacyUnmodified(diskDefaultFrom("0.2.1").replace("\"x\": 0.955", "\"x\": 0.9")),
                "玩家微调过的布局被当成默认替换掉，等于把他的改动抹了");
    }

    @Test
    @DisplayName("坏文件/空文件不会被当成旧默认（迁移不碰它们）")
    void neverTouchesBrokenFiles() {
        assertFalse(MenuDefaults.isLegacyUnmodified(null));
        assertFalse(MenuDefaults.isLegacyUnmodified(""));
        assertFalse(MenuDefaults.isLegacyUnmodified("   "));
        assertFalse(MenuDefaults.isLegacyUnmodified("{ 坏掉的 json"));
        assertFalse(MenuDefaults.isLegacyUnmodified("[]"));
        assertFalse(MenuDefaults.isLegacyUnmodified("\"just a string\""));
    }

    @Test
    @DisplayName("清单里的每条历史默认都标注了一个**发布过的版本**，且解析干净")
    void legacyEntriesAreRecordedReleases() {
        String changelog = readProjectFile("CHANGELOG.md");
        assertTrue(MenuDefaults.shippedVersions().size() >= 2, "历史默认至少要有 0.1.0 与 0.2.1 两条");
        for (Map.Entry<String, String> entry : MenuDefaults.legacyDefaults().entrySet()) {
            String version = entry.getKey();
            assertTrue(
                    version.matches("\\d+\\.\\d+\\.\\d+"), "历史默认的键必须是三段版本号，实际: " + version + "（它要被拿去和 CHANGELOG 核对）");
            assertTrue(
                    changelog.contains("## " + version),
                    "历史默认标注的版本 " + version + " 在 CHANGELOG 里查无此版——" + "凭印象编一个版本号，就等于凭空往迁移清单里塞一份会覆盖玩家布局的配置");

            List<String> warnings = new ArrayList<>();
            MenuConfig config = MenuConfig.parse(JsonUtil.GSON.fromJson(entry.getValue(), JsonObject.class), warnings);
            assertTrue(warnings.isEmpty(), version + " 这份历史默认解析出了警告，它不可能真的发布过: " + warnings);
            assertTrue(config.buttonCount() > 0 || config.vanillaButtons(), version + " 这份历史默认一个按钮都没有（发布出去等于玩家退不出游戏）");
        }
    }

    @Test
    @DisplayName("当前随模组发布的默认配置**不是**历史默认（否则升级会把自己换掉）")
    void currentDefaultIsNotLegacy() {
        String current = JsonUtil.readResource("/assets/ccnr_menu/defaults/menu.json")
                .map(JsonUtil.GSON::toJson)
                .orElseThrow(() -> new AssertionError("内置默认配置缺失"));
        assertFalse(MenuDefaults.isLegacyUnmodified(current), "当前默认配置与历史默认撞了：升级迁移会在每次启动时反复替换它");
    }

    /** 取某个版本发布过的内置默认配置（真源就是迁移清单本身，避免测试里再抄一份）。 */
    private static String diskDefaultFrom(String version) {
        String recorded = MenuDefaults.legacyDefaults().get(version);
        if (recorded == null) throw new IllegalArgumentException("未知版本: " + version);
        return recorded;
    }

    /** 读项目根目录下的文件（找不到就失败，静默跳过等于没有门禁）。 */
    private static String readProjectFile(String name) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AssertionError("读取失败: " + candidate + " —— " + e);
                }
            }
            dir = dir.getParent();
        }
        throw new AssertionError("找不到项目文件: " + name + "（user.dir=" + System.getProperty("user.dir") + "）");
    }
}
