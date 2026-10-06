/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;

/**
 * 随模组发布的**历史默认配置**（用于一次性升级迁移）。
 *
 * <p>为什么需要它：本模组「绝不覆盖已存在的配置文件」——这是对的（作者改过的文件不能被抹掉）。
 * 但它带来一个副作用：**更新模组版本时，新的默认配置永远进不去**，因为旧版本的
 * {@code menu.json} 已经在那儿了。0.2.0 就踩了这个坑：菜单里的默认布局与背景在 0.2.0 才变成
 * CCNR 的样子，而所有从 0.1.0 升级上来的玩家，看到的还是 0.1.0 那份「原版外观」的配置，
 * 现象是**装上模组后菜单一点变化都没有**——而日志一切正常，极难自己定位。
 *
 * <p>迁移的判据刻意收得很窄：**只有当文件内容与某个历史默认完全一致时才替换**。
 * 那意味着玩家从未动过这个文件，替换它在语义上等价于「这个文件从来不存在」。
 * 只要玩家改过一个字符（哪怕只是加了个空行里的字段），就一律不动。
 *
 * <p>比较用 Gson 解析后做结构比较，不用文本比较：玩家可能用编辑器重排过缩进或键顺序，
 * 文本不同但结构相同的文件仍应被认定为「未修改」。
 */
public final class MenuDefaults {

    /**
     * 0.1.0 的默认配置（原版外观：原版按钮 + 原版全景图 + 无自定义元素）。
     *
     * <p>新增条目时必须确认它**确实**是本项目曾经发布过的默认内容，
     * 且它必须是「原版外观」——`MenuDefaultsTest` 会检查这一点，
     * 防止有人把一份真正的自定义配置塞进来（那会在升级时覆盖掉玩家的布局意图）。
     */
    private static final List<String> LEGACY_DEFAULTS = List.of(
            """
            {
              "enabled": true,
              "vanillaButtons": true,
              "background": {
                "type": "vanilla"
              },
              "elements": []
            }
            """);

    private MenuDefaults() {}

    /** 是否与某个历史默认配置**结构完全一致**（即玩家从未改过它）。 */
    public static boolean isLegacyUnmodified(String fileContent) {
        if (fileContent == null || fileContent.isBlank()) return false;
        JsonElement actual;
        try {
            actual = JsonParser.parseString(fileContent);
        } catch (Exception e) {
            // 解析不了就不是「我们的默认配置」——坏文件由 JsonUtil 那边的 .bak 逻辑处理
            return false;
        }
        if (actual == null || !actual.isJsonObject()) return false;
        for (String legacy : LEGACY_DEFAULTS) {
            if (actual.equals(JsonParser.parseString(legacy))) return true;
        }
        return false;
    }

    /** 历史默认配置清单（门禁用：检查它们确实都是「原版外观」）。 */
    public static List<String> legacyDefaults() {
        return LEGACY_DEFAULTS;
    }
}
