/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.VAlign;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * 「接管界面上的居中标志」（纯数据，可单测）。
 *
 * <p>它解决的是一个很具体的落差：模组一装上，**设置、单人游戏、多人列表这些泥土页面**
 * 就只剩下一张背景图，一点机构标识都没有；而它们和主菜单一样是玩家第一眼看到的东西。
 * 主菜单有自己的元素布局（图标 + 按钮），泥土页面没有，所以这边只需要一个居中的图。
 *
 * <p>为什么不做成「任意元素列表」：那需要把主菜单整套布局与绘制搬进背景钩子里，
 * 而实际需求就是「正中间放一个白色横版 logo」。等真的要往泥土页面上加按钮时再升级形状，
 * 比现在就猜一套通用模型便宜得多。
 *
 * <p>为什么默认只画在**非主菜单**的界面上：主菜单的左列里已经有一个图标了，
 * 再在正中间叠一个就是两个 logo 打架。想要两处都有，把 {@code onMainMenu} 打开即可。
 *
 * @param file 图片文件（相对 {@code config/ccnr_menu/}）
 * @param width 显示宽度（像素；{@link #AUTO} = 用原图宽度），高度按原图比例算
 * @param opacity 不透明度 0..1
 * @param x 横向锚点分数 0..1（默认 0.5）
 * @param y 纵向锚点分数 0..1（默认 0.5）
 * @param align {@code x} 指的是标志的哪一条边（默认 {@code center}）。
 *     想放到右下角就写 {@code x: 0.98, y: 0.95, align: right, valign: bottom}——
 *     这时候 {@code x}/{@code y} 是**标志自己的右/下边缘**，留出的余量才是「不贴边」的那个量
 * @param valign {@code y} 指的是标志的哪一条边（默认 {@code middle}）
 * @param onMainMenu 是否也画在主菜单上
 */
public record MarkSpec(
        String file, int width, float opacity, double x, double y, Align align, VAlign valign, boolean onMainMenu) {

    /** 宽度「未指定」。 */
    public static final int AUTO = -1;

    /** 没有标志（配置里没写 {@code mark}，或 {@code file} 为空）。 */
    public static final MarkSpec NONE = new MarkSpec("", AUTO, 1f, 0.5, 0.5, Align.CENTER, VAlign.MIDDLE, false);

    public MarkSpec {
        file = file == null ? "" : file.trim();
        opacity = Math.min(1f, Math.max(0f, opacity));
        x = clamp(x, 0.0, 1.0);
        y = clamp(y, 0.0, 1.0);
        align = align == null ? Align.CENTER : align;
        valign = valign == null ? VAlign.MIDDLE : valign;
    }

    /** 是否配置了一个要画的标志。 */
    public boolean enabled() {
        return !file.isBlank();
    }

    /** 从根对象解析 {@code mark}；缺失返回 {@link #NONE}。 */
    public static MarkSpec parse(JsonObject root, List<String> warnings) {
        if (root == null || !root.has("mark") || !root.get("mark").isJsonObject()) {
            return NONE;
        }
        JsonObject o = root.getAsJsonObject("mark");
        String file = JsonUtil.str(o, "file", "").trim();
        if (file.isEmpty()) {
            warnings.add("mark.file 为空 → 泥土页面上不会画标志（要么填文件名，要么把这个 mark 对象删掉）");
            return NONE;
        }
        int width = (int) JsonUtil.num(o, "width", AUTO);
        if (width != AUTO && (width < 1 || width > 4096)) {
            warnings.add("mark.width 超出 1..4096: " + width + " → 已改为按原图宽度");
            width = AUTO;
        }
        return new MarkSpec(
                file,
                width,
                (float) clamp(JsonUtil.dbl(o, "opacity", 1.0), 0.0, 1.0),
                JsonUtil.dbl(o, "x", 0.5),
                JsonUtil.dbl(o, "y", 0.5),
                Align.parse(JsonUtil.str(o, "align", "center")),
                VAlign.parse(JsonUtil.str(o, "valign", "middle")),
                JsonUtil.bool(o, "onMainMenu", false));
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
