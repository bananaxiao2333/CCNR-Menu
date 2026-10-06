/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.BootMeter;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.ccnrcom.menu.ui.SpriteSheet;
import com.ccnrcom.menu.ui.VAlign;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 主菜单配置（纯数据，可单测）。
 *
 * <p>整份菜单由这一个对象描述：背景、配色、标题、按钮、按钮的位置与动作。改菜单**不需要重新编译**，
 * 也不需要重启游戏（{@code /ccnr_menu reload}）。这是刻意的：菜单是给服主/整合包作者反复调的东西，
 * 每次微调都要重新打包是一个没人会用的工作流。
 *
 * @param enabled 关掉后本模组完全不介入，主菜单就是原版的（出问题时的第一条退路）
 * @param vanillaButtons 用**原版那套按钮**（单人/多人/设置/退出 + Mods）而不是配置里的按钮；
 *     想要「只换背景」时把它设成 {@code true} 并让 {@code elements} 为空即可
 * @param background 背景
 * @param theme 配色
 * @param elements 配置出来的元素（按下标顺序即绘制顺序）
 * @param applyToAllScreens 除了主菜单，是否也把这份背景铺到**原版那些泥土背景的界面**上
 *     （世界选择、多人列表、设置、语言、Mod 列表……）。默认 {@code true}：
 *     「换掉泥土界面和全景图界面」本来就是同一件事——一个服务器只想看到自己的一张背景。
 *     世界内的界面（暂停、背包等，背景是世界本身）**始终不动**。
 * @param mark 居中标志（默认只画在泥土页面等**非主菜单**的接管界面上；主菜单自己有元素布局）
 * @param bootLog 启动日志那一层（背景图之上、图标与按钮之下；默认关）
 */
public record MenuConfig(
        boolean enabled,
        boolean vanillaButtons,
        BackgroundSpec background,
        MenuThemeSpec theme,
        List<MenuElement> elements,
        boolean applyToAllScreens,
        MarkSpec mark,
        BootLogSpec bootLog) {

    /** 本模组的 modid，同时也是文案键前缀。 */
    public static final String MOD_ID = "ccnr_menu";

    /** 文案键前缀（{@code text} 以此开头时按语言键翻译）。 */
    public static final String LANG_PREFIX = "ccnr_menu.";

    public MenuConfig {
        background = background == null ? BackgroundSpec.DEFAULT : background;
        theme = theme == null ? MenuThemeSpec.DEFAULT : theme;
        elements = elements == null ? List.of() : List.copyOf(elements);
        mark = mark == null ? MarkSpec.NONE : mark;
        bootLog = bootLog == null ? BootLogSpec.NONE : bootLog;
    }

    /**
     * 随模组附带的默认配置：**与安装前完全一致的观感**（原版全景图 + 原版按钮）。
     *
     * <p>刻意不默认开动画：默认值应该「装上就等于没装」，任何视觉变化都必须是玩家自己配出来的。
     * 想让菜单变样的做法见 README 与 docs/03。
     */
    public static MenuConfig template() {
        return new MenuConfig(
                true,
                true,
                BackgroundSpec.DEFAULT,
                MenuThemeSpec.DEFAULT,
                List.of(),
                true,
                MarkSpec.NONE,
                BootLogSpec.NONE);
    }

    /**
     * 配置坏掉时的兜底：**必须是能玩的菜单**。
     *
     * <p>这是本模组唯一「静默降级」的地方，而且降级方向是固定的：退回原版按钮。
     * 理由：配置解析失败时如果还按残缺的 {@code elements} 画，最坏的可能是画出一个
     * **一个按钮都没有的主菜单**——玩家进不去单人、进不去设置、也退不出游戏，只能杀进程。
     * 宁可丑，不可锁死。
     */
    public static MenuConfig fallback() {
        return new MenuConfig(
                true,
                true,
                BackgroundSpec.DEFAULT,
                MenuThemeSpec.DEFAULT,
                List.of(),
                true,
                MarkSpec.NONE,
                BootLogSpec.NONE);
    }

    /** 是否存在配置出来的按钮（用于诊断输出与防呆）。 */
    public int buttonCount() {
        int total = 0;
        for (MenuElement element : elements) total += element.countButtons();
        return total;
    }

    /** 元素总数（含容器里的子元素，诊断输出用）。 */
    public int elementCount() {
        int total = 0;
        for (MenuElement element : elements) total += element.countAll();
        return total;
    }

    /**
     * 从 JSON 解析。无法使用的元素**逐个跳过并记警告**（警告会出现在 {@code /ccnr_menu status} 与日志里），
     * 不做「静默变成空按钮」这种降级。
     */
    public static MenuConfig parse(JsonObject root, List<String> warnings) {
        if (root == null) return fallback();
        boolean enabled = JsonUtil.bool(root, "enabled", true);
        boolean vanillaButtons = JsonUtil.bool(root, "vanillaButtons", false);
        boolean applyToAllScreens = JsonUtil.bool(root, "applyToAllScreens", true);
        BackgroundSpec background = BackgroundSpec.parse(root, warnings);
        MenuThemeSpec theme = MenuThemeSpec.parse(root, warnings);
        MarkSpec mark = MarkSpec.parse(root, warnings);
        BootLogSpec bootLog = parseBootLog(root, warnings);

        List<MenuElement> elements = parseElements(root, "elements", 0, warnings);

        MenuConfig config =
                new MenuConfig(enabled, vanillaButtons, background, theme, elements, applyToAllScreens, mark, bootLog);

        // 防呆：一个按钮都没有的菜单会把玩家锁在主界面（进不去设置、退不出游戏）。
        if (config.buttonCount() == 0 && !vanillaButtons) {
            warnings.add("配置里没有任何 button 元素 → 已自动改用原版按钮（否则主菜单会没有按钮，玩家退不出游戏）");
            config = new MenuConfig(true, true, background, theme, elements, applyToAllScreens, mark, bootLog);
        }
        return config;
    }

    /**
     * 解析 {@code bootLog} 那一块（背景图之上、图标之下的那一层）。
     *
     * <p>三个刻意的取舍：
     * <ul>
     *   <li>既没有 {@code builtin} 也没有 {@code file} 时，光开 {@code enabled} 不显示任何东西——
     *       于是解析这里就直接警告一句。「开了没反应」是最难查的一类故障。</li>
     *   <li>{@code spinner} 是**文案数组**而不是开关：真实终端那一行本来就会换文案
     *       （{@code A start job is running (3s / no limit)} → {@code [  OK  ] ...}），
     *       写死一句就少一半味道。缺省时用内置那一对。</li>
     *   <li>{@code color} 缺省是 {@link BootLogSpec#NO_COLOR}（按行类型上色）。想整片白就写
     *       {@code "color": "#FFFFFF"}——写死白色会让「状态用颜色区分」这件事直接消失。</li>
     * </ul>
     */
    private static BootLogSpec parseBootLog(JsonObject root, List<String> warnings) {
        JsonObject o =
                root.has("bootLog") && root.get("bootLog").isJsonObject() ? root.getAsJsonObject("bootLog") : null;
        if (o == null) return BootLogSpec.NONE;

        boolean enabled = JsonUtil.bool(o, "enabled", false);
        String builtin = JsonUtil.str(o, "builtin", "").trim();
        String file = JsonUtil.str(o, "file", "").trim();
        BootLogSpec defaults = BootLogSpec.builtinDefault();

        List<String> spinner = new ArrayList<>();
        JsonElement rawSpinner = o.get("spinner");
        if (rawSpinner == null) {
            spinner.addAll(defaults.spinner());
        } else if (rawSpinner.isJsonArray()) {
            for (JsonElement item : rawSpinner.getAsJsonArray()) {
                if (!item.isJsonPrimitive()) {
                    warnings.add("bootLog.spinner 里有非字符串项 → 已跳过");
                    continue;
                }
                String text = item.getAsString().strip();
                if (!text.isEmpty()) spinner.add(text);
            }
        } else {
            warnings.add("bootLog.spinner 必须是字符串数组 → 已用内置的那一对");
            spinner.addAll(defaults.spinner());
        }

        long spacing = JsonUtil.num(o, "spacingMs", defaults.spacingMs());
        if (spacing < 0) {
            warnings.add("bootLog.spacingMs 为负 → 已按 0 处理");
            spacing = 0;
        }
        double scale = JsonUtil.dbl(o, "scale", defaults.scale());
        if (scale < 0.1 || scale > 8) {
            warnings.add("bootLog.scale 超出 0.1~8 → 已收敛到边界值");
            scale = Math.min(8, Math.max(0.1, scale));
        }

        int color = BootLogSpec.NO_COLOR;
        if (o.has("color")) {
            String raw = JsonUtil.str(o, "color", "");
            color = ColorSpec.parseOr(raw, BootLogSpec.NO_COLOR);
            if (color == BootLogSpec.NO_COLOR) warnings.add("bootLog.color 解析失败 → 已改为按行类型上色: " + raw);
        }

        String rawMeter = JsonUtil.str(o, "meter", "systemd").trim();
        BootMeter meter = BootMeter.parse(rawMeter);
        if (!"systemd".equalsIgnoreCase(rawMeter)
                && !"bracket".equalsIgnoreCase(rawMeter)
                && !"plymouth".equalsIgnoreCase(rawMeter)) {
            warnings.add("bootLog.meter 认不出（可用 systemd/bracket/plymouth）→ 已用 systemd: " + rawMeter);
        }

        if (enabled && builtin.isEmpty() && file.isEmpty()) {
            warnings.add("bootLog.enabled=true 但既没有 builtin 也没有 file → 这一层不会显示任何内容");
        }

        // 锚点语义：所有界面共用（曾经按屏切换，实测在窄窗口下会把整块推出屏幕，已回退）
        Align align =
                Align.parse(JsonUtil.str(o, "align", defaults.align().name().toLowerCase(java.util.Locale.ROOT)));
        if (align == null) {
            warnings.add("bootLog.align 认不出（可用 left/center/right）→ 已用 left");
            align = Align.LEFT;
        }
        if (o.has("allScreensAlign") || o.has("switchMs")) {
            warnings.add("bootLog.allScreensAlign / switchMs 已废弃（所有界面共用同一个锚点）→ 已忽略");
        }

        long spinnerWidth = JsonUtil.num(o, "spinnerWidth", defaults.spinnerWidth());
        if (spinnerWidth < 1 || spinnerWidth > 40) {
            warnings.add("bootLog.spinnerWidth 超出 1~40 → 已收敛");
            spinnerWidth = Math.min(40, Math.max(1, spinnerWidth));
        }

        return new BootLogSpec(
                enabled,
                builtin,
                file,
                spinner,
                spacing,
                (float) scale,
                JsonUtil.dbl(o, "x", defaults.x()),
                JsonUtil.dbl(o, "y", defaults.y()),
                align,
                VAlign.parse(JsonUtil.str(o, "valign", "top")),
                color,
                meter,
                (int) spinnerWidth,
                JsonUtil.bool(o, "onAllScreens", defaults.onAllScreens()));
    }

    /** 解析一个元素数组（顶层与容器子元素共用这一份）。 */
    private static List<MenuElement> parseElements(JsonObject owner, String key, int depth, List<String> warnings) {
        List<MenuElement> elements = new ArrayList<>();
        if (owner == null || !owner.has(key)) return elements;
        JsonElement el = owner.get(key);
        if (!el.isJsonArray()) {
            warnings.add(key + " 不是数组 → 已忽略");
            return elements;
        }
        JsonArray array = el.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String where = key + "[" + i + "]";
            JsonElement item = array.get(i);
            if (!item.isJsonObject()) {
                warnings.add(where + " 不是对象 → 已跳过");
                continue;
            }
            MenuElement parsed = parseElement(item.getAsJsonObject(), where, depth, warnings);
            if (parsed != null) elements.add(parsed);
        }
        return elements;
    }

    /** 解析单个元素；不可用返回 {@code null}（调用方跳过并已有警告）。 */
    private static MenuElement parseElement(JsonObject o, String where, int depth, List<String> warnings) {
        String rawType = JsonUtil.str(o, "type", "button");
        MenuElement.Type type = MenuElement.Type.parse(rawType);
        if (type == null) {
            warnings.add(where + ": type 不认识: '" + rawType + "'（可用 button/label/image/column）→ 已跳过");
            return null;
        }

        String text = JsonUtil.str(o, "text", "");
        String file = JsonUtil.str(o, "file", "").trim();
        if (type == MenuElement.Type.COLUMN) {
            if (depth >= MenuElement.MAX_DEPTH) {
                warnings.add(where + ": 容器嵌套超过 " + MenuElement.MAX_DEPTH + " 层 → 已跳过（病态配置，别再往里套了）");
                return null;
            }
        } else if (type == MenuElement.Type.IMAGE) {
            if (file.isEmpty()) {
                warnings.add(where + ": image 元素缺少 file → 已跳过");
                return null;
            }
        } else if (text.isBlank()) {
            warnings.add(where + ": " + rawType + " 元素缺少 text → 已跳过");
            return null;
        }

        MenuAction action = MenuAction.NONE_ACTION;
        if (type == MenuElement.Type.BUTTON) {
            try {
                action = MenuAction.parse(JsonUtil.str(o, "action", null), JsonUtil.str(o, "value", null));
            } catch (IllegalArgumentException e) {
                // 跳过而不是「留一个点了没反应的按钮」：看起来能用但什么都不发生的按钮，
                // 玩家只会以为是游戏卡了，而作者得不到任何提示
                warnings.add(where + ": " + e.getMessage() + " → 已跳过这个按钮");
                return null;
            }
        }

        double x = fraction(o, "x", 0.5, where, warnings);
        double y = fraction(o, "y", 0.5, where, warnings);

        String rawAlign = JsonUtil.str(o, "align", null);
        Align align = Align.parse(rawAlign);
        if (align == null) {
            if (rawAlign != null) {
                warnings.add(where + ".align 不认识: '" + rawAlign + "'（可用 left/center/right）→ 已用 center");
            }
            align = Align.CENTER;
        }

        String rawValign = JsonUtil.str(o, "valign", null);
        VAlign valign = VAlign.parse(rawValign);
        if (valign == null) {
            if (rawValign != null) {
                warnings.add(where + ".valign 不认识: '" + rawValign + "'（可用 top/middle/bottom）→ 已用 middle");
            }
            valign = VAlign.MIDDLE;
        }

        int width = (int) JsonUtil.num(o, "width", MenuElement.AUTO);
        int height = (int) JsonUtil.num(o, "height", MenuElement.AUTO);
        if (width != MenuElement.AUTO && (width < 1 || width > 4096)) {
            warnings.add(where + ".width 超出范围: " + width + " → 已用默认值");
            width = MenuElement.AUTO;
        }
        if (height != MenuElement.AUTO && (height < 1 || height > 4096)) {
            warnings.add(where + ".height 超出范围: " + height + " → 已用默认值");
            height = MenuElement.AUTO;
        }

        double rawScale = JsonUtil.dbl(o, "scale", 1.0);
        float scale = (float) rawScale;
        if (rawScale < MenuElement.MIN_SCALE || rawScale > MenuElement.MAX_SCALE) {
            warnings.add(where + ".scale 超出范围: " + rawScale + "（" + MenuElement.MIN_SCALE + "~" + MenuElement.MAX_SCALE
                    + "）→ 已收敛到边界");
        }

        int offsetX = (int) JsonUtil.num(o, "offsetX", 0);
        int offsetY = (int) JsonUtil.num(o, "offsetY", 0);
        if (Math.abs(offsetX) > 4096 || Math.abs(offsetY) > 4096) {
            warnings.add(where + ".offsetX/offsetY 过大: " + offsetX + "," + offsetY + " → 已归零（元素会被推到屏幕外）");
            offsetX = 0;
            offsetY = 0;
        }

        int color = MenuElement.NO_COLOR;
        String rawColor = JsonUtil.str(o, "color", null);
        if (rawColor != null) {
            Integer parsed = ColorSpec.parse(rawColor);
            if (parsed == null) {
                warnings.add(where + ".color 不是合法颜色: '" + rawColor + "' → 已用主题色");
            } else {
                color = parsed;
            }
        }

        boolean shadow = JsonUtil.bool(o, "shadow", true);

        // 图片元素的序列帧参数（与背景共用 AnimationParser：字段名与校验只有一份）
        SpriteSheet sheet = null;
        if (type == MenuElement.Type.IMAGE && AnimationParser.present(o)) {
            sheet = AnimationParser.parse(o, warnings, where);
        }

        // 容器：递归解析子元素（子元素里的 x/y 不生效，位置由容器决定）
        MenuElement.Column column = null;
        if (type == MenuElement.Type.COLUMN) {
            List<MenuElement> children = parseElements(o, "children", depth + 1, warnings);
            if (children.isEmpty()) {
                // 空容器画出来什么都没有：与其留一块看不见的空白，不如明确报出来
                warnings.add(where + ": column 容器没有可用的子元素（children 缺失或全部被跳过）→ 已跳过");
                return null;
            }
            int gap = (int) JsonUtil.num(o, "gap", 8);
            if (gap < 0 || gap > MenuElement.MAX_GAP) {
                warnings.add(where + ".gap 超出范围: " + gap + "（0~" + MenuElement.MAX_GAP + "）→ 已收敛到边界");
                gap = Math.max(0, Math.min(MenuElement.MAX_GAP, gap));
            }
            String rawChildAlign = JsonUtil.str(o, "childAlign", null);
            Align childAlign = AnimationParser.childAlign(rawChildAlign);
            if (childAlign == null) {
                if (rawChildAlign != null) {
                    warnings.add(where + ".childAlign 不认识: '" + rawChildAlign + "'（可用 left/center/right）→ 已用 center");
                }
                childAlign = Align.CENTER;
            }
            MenuElement.Bar bar = parseBar(o, where, warnings);
            column = new MenuElement.Column(children, gap, childAlign, bar);
        }

        return new MenuElement(
                type, text, file, action, x, y, align, valign, width, height, offsetX, offsetY, scale, color, shadow,
                sheet, column);
    }

    /**
     * 解析列背后的色带 {@code bar}。
     *
     * <p>写错了**只警告不改变形状**：色带是装饰，缺了不影响菜单能不能用；但如果作者写了
     * {@code bar} 却什么都没出现，那条警告就是唯一的解释。
     */
    private static MenuElement.Bar parseBar(JsonObject o, String where, List<String> warnings) {
        if (!o.has("bar") || !o.get("bar").isJsonObject()) return null;
        JsonObject bar = o.getAsJsonObject("bar");
        // 注意不能拿「结果 == 默认值」当解析失败的判据：#33000000 本来就等于默认值
        int color = MenuElement.DEFAULT_BAR_COLOR;
        String rawColor = JsonUtil.str(bar, "color", null);
        if (rawColor != null) {
            Integer parsed = ColorSpec.parse(rawColor);
            if (parsed == null) {
                warnings.add(where + ".bar.color 不是合法颜色: '" + rawColor + "' → 已用 20% 黑");
            } else {
                color = parsed;
            }
        }
        int padding = (int) JsonUtil.num(bar, "padding", MenuElement.DEFAULT_BAR_PADDING);
        if (padding < 0 || padding > MenuElement.MAX_BAR_PADDING) {
            warnings.add(where + ".bar.padding 超出范围: " + padding + "（0~" + MenuElement.MAX_BAR_PADDING + "）→ 已收敛到边界");
            padding = Math.max(0, Math.min(MenuElement.MAX_BAR_PADDING, padding));
        }
        String raw = JsonUtil.str(bar, "width", "buttons");
        boolean columnWidth;
        if ("buttons".equalsIgnoreCase(raw)) {
            columnWidth = false;
        } else if ("column".equalsIgnoreCase(raw)) {
            columnWidth = true;
        } else {
            warnings.add(where + ".bar.width 不认识: '" + raw + "'（可用 buttons/column）→ 已用 buttons");
            columnWidth = false;
        }
        return new MenuElement.Bar(color, columnWidth, padding);
    }

    private static double fraction(JsonObject o, String key, double def, String where, List<String> warnings) {
        double v = JsonUtil.dbl(o, key, def);
        if (v < 0 || v > 1) {
            warnings.add(where + "." + key + " 应在 0~1 之间: " + v + " → 已收敛到边界（元素会贴边而不是跑到屏幕外）");
        }
        return MenuGeometry.clampFraction(v);
    }
}
