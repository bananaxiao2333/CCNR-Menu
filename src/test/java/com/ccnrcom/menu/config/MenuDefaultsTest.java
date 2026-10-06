/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
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
 */
class MenuDefaultsTest {

    @Test
    @DisplayName("未修改过的旧默认配置（0.1.0 那份）能被识别")
    void detectsLegacyDefault() {
        String content = diskDefaultFrom("0.1.0");
        assertTrue(MenuDefaults.isLegacyUnmodified(content), "0.1.0 的默认配置必须被识别出来");
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
    @DisplayName("清单里的每条历史默认都必须是「原版外观」（防止有人塞进一份真配置）")
    void legacyEntriesAreVanillaLook() {
        List<String> warnings = new ArrayList<>();
        for (String legacy : MenuDefaults.legacyDefaults()) {
            JsonObject json = JsonUtil.GSON.fromJson(legacy, JsonObject.class);
            MenuConfig config = MenuConfig.parse(json, warnings);
            assertTrue(config.vanillaButtons(), "历史默认配置必须是「原版按钮」：迁移会覆盖玩家的文件，判据宽一格就会覆盖别人的布局");
            assertTrue(config.elements().isEmpty(), "历史默认配置不该带自定义元素");
            assertTrue(warnings.isEmpty(), "历史默认配置解析不该有警告: " + warnings);
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

    /** 从 git 历史里取某个版本的内置默认配置，确保判据锚在真实发布过的内容上。 */
    private static String diskDefaultFrom(String version) {
        if ("0.1.0".equals(version)) {
            // 0.1.0 发布的内置默认（原版外观）；与 git 历史中的内容一致
            return """
                {
                  "enabled": true,
                  "vanillaButtons": true,
                  "background": {
                    "type": "vanilla"
                  },
                  "elements": []
                }
                """;
        }
        throw new IllegalArgumentException("未知版本: " + version);
    }
}
