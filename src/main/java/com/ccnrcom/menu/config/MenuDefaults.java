/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
 *
 * <p><b>清单为什么带版本号</b>：最初这份清单只允许放「原版外观」的配置，
 * 用「长得像不像原版」当成「是不是我们发布的」的替身判据。0.2.1 之后默认菜单本身就是
 * 一套自定义布局了，替身判据随之失效——真正要防的是「有人凭印象塞进一份**从没发布过**的配置」。
 * 所以现在每条都必须标注**它随哪个版本发布**，`MenuDefaultsTest` 会去 CHANGELOG 里核对这个版本
 * 确实存在。凭印象编一个版本号很容易被这条门禁拦住（CHANGELOG 里查无此版）。
 */
public final class MenuDefaults {

    /**
     * 历史默认配置：{@code 版本 → 当时随模组发布的默认 menu.json 原文}。
     *
     * <p>按版本从旧到新排列。新增条目时把**当时那份文件原样**贴进来（可从
     * {@code git show <tag>:src/main/resources/assets/ccnr_menu/defaults/menu.json} 取），
     * 不要「顺手美化一下」——它不是给人读的示例，是给迁移代码比对用的指纹。
     */
    private static final Map<String, String> LEGACY_DEFAULTS = shippedDefaults();

    private MenuDefaults() {}

    private static Map<String, String> shippedDefaults() {
        Map<String, String> map = new LinkedHashMap<>();
        // 0.1.0：原版外观（原版那套按钮 + 原版全景图 + 无自定义元素）
        map.put(
                "0.1.0",
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
        // 0.2.0 / 0.2.1：右侧竖列布局 + 背景图 + 内置素材
        map.put(
                "0.2.1",
                """
                {
                  "enabled": true,
                  "vanillaButtons": false,
                  "applyToAllScreens": true,
                  "background": {
                    "type": "image",
                    "file": "background.png",
                    "fit": "cover",
                    "opacity": 1.0
                  },
                  "elements": [
                    {
                      "type": "column",
                      "x": 0.955,
                      "y": 0.5,
                      "align": "right",
                      "valign": "middle",
                      "width": 300,
                      "gap": 8,
                      "childAlign": "right",
                      "children": [
                        {
                          "type": "image",
                          "file": "logo_wide_intro_black.png",
                          "width": 300,
                          "animation": {
                            "cols": 8,
                            "rows": 4,
                            "frameMs": 81,
                            "loop": false
                          }
                        },
                        {
                          "type": "button",
                          "text": "进入服务器",
                          "action": "screen:multiplayer",
                          "width": 200,
                          "height": 20
                        },
                        {
                          "type": "button",
                          "text": "单人游戏",
                          "action": "screen:singleplayer",
                          "width": 200,
                          "height": 20
                        },
                        {
                          "type": "button",
                          "text": "设置",
                          "action": "screen:options",
                          "width": 200,
                          "height": 20
                        },
                        {
                          "type": "button",
                          "text": "退出游戏",
                          "action": "quit",
                          "width": 200,
                          "height": 20
                        }
                      ]
                    }
                  ]
                }
                """);
        // 0.3.0：靠左布局 + 无背景纯文字按钮 + 黑底彩色文字背景（该背景类型已在 0.4.0 移除）
        map.put(
                "0.3.0",
                """
                {
                  "enabled": true,
                  "vanillaButtons": false,
                  "applyToAllScreens": true,
                  "background": {
                    "type": "text",
                    "color": "#000000",
                    "text": {
                      "x": 0.70,
                      "y": 0.5,
                      "align": "center",
                      "valign": "middle",
                      "scale": 2,
                      "lineGap": 12,
                      "charMs": 45,
                      "stepMs": 90,
                      "holdMs": 2600,
                      "loop": false,
                      "shadow": false,
                      "palette": ["#4FD1E0", "#7CF7C4", "#FFD166", "#FF8AB8", "#9B8CFF", "#E6EDF3"],
                      "segments": [
                        {
                          "left": "[===[ ",
                          "text": "CCNR 服务器",
                          "right": " ]===]",
                          "offset": 0
                        },
                        {
                          "left": "+--- ",
                          "text": "生存 · 创造 · 长期运营",
                          "right": " ---+",
                          "offset": 2
                        },
                        {
                          "left": "[===[ ",
                          "text": "play.ccnr.example",
                          "right": " ]===]",
                          "offset": 4
                        }
                      ]
                    }
                  },
                  "theme": {
                    "buttonStyle": "text",
                    "backdrop": "#00000000",
                    "buttonFill": "#B0121216",
                    "buttonFillHover": "#D01E1E26",
                    "buttonBorder": "#FF3C3C46",
                    "buttonBorderHover": "#FF4FD1E0",
                    "buttonText": "#FFE6E6EE",
                    "buttonTextHover": "#FF4FD1E0",
                    "accent": "#FF4FD1E0",
                    "labelText": "#FFCFCFD8"
                  },
                  "elements": [
                    {
                      "type": "column",
                      "x": 0.04,
                      "y": 0.5,
                      "align": "left",
                      "valign": "middle",
                      "width": 300,
                      "gap": 10,
                      "childAlign": "left",
                      "children": [
                        {
                          "type": "image",
                          "file": "logo_wide_intro_mono.png",
                          "width": 300,
                          "animation": {
                            "cols": 8,
                            "rows": 10,
                            "frameMs": 33,
                            "loop": false
                          }
                        },
                        {
                          "type": "button",
                          "text": "进入服务器",
                          "action": "screen:multiplayer"
                        },
                        {
                          "type": "button",
                          "text": "单人游戏",
                          "action": "screen:singleplayer"
                        },
                        {
                          "type": "button",
                          "text": "设置",
                          "action": "screen:options"
                        },
                        {
                          "type": "button",
                          "text": "退出游戏",
                          "action": "quit"
                        }
                      ]
                    }
                  ]
                }
                """);
        // 0.4.0：轮播背景（6 张）+ 泥土页面居中白色横版标志，删掉了黑底彩色文字背景
        map.put(
                "0.4.0",
                """
                {
                  "enabled": true,
                  "vanillaButtons": false,
                  "applyToAllScreens": true,
                  "background": {
                    "type": "slideshow",
                    "fit": "cover",
                    "tint": "#A6A6A6",
                    "opacity": 1.0,
                    "holdMs": 9000,
                    "fadeMs": 1400,
                    "zoom": 0.08,
                    "panX": 0.05,
                    "panY": 0.0,
                    "loop": true,
                    "slides": [
                      "slides/01.jpg",
                      "slides/02.jpg",
                      "slides/03.jpg",
                      "slides/04.jpg",
                      "slides/05.jpg",
                      "slides/06.jpg"
                    ]
                  },
                  "mark": {
                    "file": "logo_wide_mono.png",
                    "width": 420,
                    "opacity": 1.0,
                    "x": 0.5,
                    "y": 0.5,
                    "onMainMenu": false
                  },
                  "theme": {
                    "buttonStyle": "text",
                    "backdrop": "#4D000000",
                    "buttonFill": "#B0121216",
                    "buttonFillHover": "#D01E1E26",
                    "buttonBorder": "#FF3C3C46",
                    "buttonBorderHover": "#FF4FD1E0",
                    "buttonText": "#FFFFFFFF",
                    "buttonTextHover": "#FF4FD1E0",
                    "accent": "#FF4FD1E0",
                    "labelText": "#FFE6E6EE"
                  },
                  "elements": [
                    {
                      "type": "column",
                      "x": 0.04,
                      "y": 0.5,
                      "align": "left",
                      "valign": "middle",
                      "width": 300,
                      "gap": 10,
                      "childAlign": "left",
                      "children": [
                        {
                          "type": "image",
                          "file": "logo_wide_intro_mono.png",
                          "width": 300,
                          "animation": {
                            "cols": 8,
                            "rows": 10,
                            "frameMs": 33,
                            "loop": false
                          }
                        },
                        {
                          "type": "button",
                          "text": "进入服务器",
                          "action": "screen:multiplayer"
                        },
                        {
                          "type": "button",
                          "text": "单人游戏",
                          "action": "screen:singleplayer"
                        },
                        {
                          "type": "button",
                          "text": "设置",
                          "action": "screen:options"
                        },
                        {
                          "type": "button",
                          "text": "退出游戏",
                          "action": "quit"
                        }
                      ]
                    }
                  ]
                }
                """);
        return java.util.Collections.unmodifiableMap(map);
    }
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
        for (String legacy : LEGACY_DEFAULTS.values()) {
            if (actual.equals(JsonParser.parseString(legacy))) return true;
        }
        return false;
    }

    /** 历史默认配置清单（门禁用：检查它们确实对应一个发布过的版本）。 */
    public static Map<String, String> legacyDefaults() {
        return LEGACY_DEFAULTS;
    }

    /** 清单里记录的版本号（门禁与诊断输出用）。 */
    public static Set<String> shippedVersions() {
        return LEGACY_DEFAULTS.keySet();
    }
}
