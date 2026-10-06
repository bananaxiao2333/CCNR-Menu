/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.SpriteSheet;
import com.ccnrcom.menu.ui.VAlign;
import java.util.List;
import java.util.Locale;

/**
 * 菜单元素（纯数据，可单测）。
 *
 * <p>四种元素共用一套摆放字段（{@code x}/{@code y} 分数 + {@code align}/{@code valign}），
 * 这样「把按钮往右挪一点」在四种元素上是同一个动作，不需要分别学四套坐标语义。
 * {@code column} 是唯一的例外：它是个**容器**，自身按上面的规则摆放，
 * 里面的子元素由容器负责排布（不再看各自的 {@code x}/{@code y}）。
 *
 * @param type 元素类型
 * @param text 文案；以 {@code ccnr_menu.} 开头按语言键翻译，否则按字面量显示
 * @param file 图片文件（仅 {@code IMAGE}）
 * @param action 点击动作（仅 {@code BUTTON}）
 * @param x 横向锚点分数 0..1
 * @param y 纵向锚点分数 0..1
 * @param align 横向锚点位置
 * @param valign 纵向锚点位置
 * @param width 宽（像素；{@link #AUTO} 表示按类型默认或按内容自适应）
 * @param height 高（像素；{@link #AUTO} 表示按类型默认或按内容自适应）
 * @param offsetX 摆好之后的横向微调（像素，正数向右）
 * @param offsetY 摆好之后的纵向微调（像素，正数向下）
 * @param scale 文字缩放倍数（仅 {@code LABEL}）
 * @param color 颜色（{@link #NO_COLOR} 表示用主题色）
 * @param shadow 文字是否带阴影
 * @param sheet 序列帧参数（仅 {@code IMAGE}，为 {@code null} 表示静态图）
 * @param column 竖列容器参数（仅 {@code COLUMN}，为 {@code null} 表示不是容器）
 */
public record MenuElement(
        Type type,
        String text,
        String file,
        MenuAction action,
        double x,
        double y,
        Align align,
        VAlign valign,
        int width,
        int height,
        int offsetX,
        int offsetY,
        float scale,
        int color,
        boolean shadow,
        SpriteSheet sheet,
        Column column) {

    /** 元素类型。 */
    public enum Type {
        /** 可点击按钮。 */
        BUTTON,
        /** 静态文字。 */
        LABEL,
        /** 图片（可以是序列帧动画，见 {@link #sheet}）。 */
        IMAGE,
        /** 竖列容器：把子元素自上而下排成一列。 */
        COLUMN;

        /** 大小写不敏感解析；不认识时返回 {@code null}。 */
        public static Type parse(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "button", "btn" -> BUTTON;
                case "label", "text" -> LABEL;
                case "image", "img", "logo", "picture" -> IMAGE;
                case "column", "col", "stack", "vbox" -> COLUMN;
                default -> null;
            };
        }
    }

    /**
     * 竖列容器参数。
     *
     * <p>为什么需要容器而不是「每个元素各自算坐标」：一列按钮要**互相**对齐才有设计感，
     * 而用分数坐标手动对齐在换分辨率时必然错位（0.5 的 1px 偏差在 1080p 与 1440p 上不是同一个东西）。
     * 容器的语义是「这块整体放哪 + 内部怎么排」，两者分开，改一个不会牵动另一个。
     *
     * @param children 子元素（自上而下，绘制与命中顺序同此序）
     * @param gap 相邻子元素的间距（像素）
     * @param childAlign 子元素在**列宽之内**的横向对齐方式。
     *     {@code right} 时图标与按钮的右边缘对齐——这正是「整块靠右、图标与按钮列右对齐」的做法
     * @param bar 列背后的纵向色带（为 {@code null} 表示不画）
     */
    public record Column(List<MenuElement> children, int gap, Align childAlign, Bar bar) {

        public Column {
            children = children == null ? List.of() : List.copyOf(children);
            childAlign = childAlign == null ? Align.CENTER : childAlign;
        }
    }

    /**
     * 列背后那条**满屏高**的色带。
     *
     * <p>它是容器属性而不是一种元素，因为它的位置和宽度都由这一列自己决定：写成独立元素的话，
     * 作者得手写一个宽度，而按钮宽度是随文案变的（{@code text} 外观下宽度贴着文字）——
     * 改一个字，色带就和按钮错开。
     *
     * @param color 颜色（含 alpha；20% 黑 = {@code #33000000}）
     * @param fullColumnWidth {@code true} 用整列宽（和图标同宽），
     *     {@code false} 用**按钮们实际占据的范围**（默认，也就是「宽度覆盖按钮列」）
     * @param padding 左右各留多少像素。**必须是正数**：按钮（{@code text} 外观）的宽度就是
     *     文字的宽度，色带贴着文字边界会在视觉上「压住」两端，看着像被裁掉了一块
     */
    public record Bar(int color, boolean fullColumnWidth, int padding) {}

    /** 尺寸/颜色用「未指定」哨兵值。0 也是合法颜色（全透明），因此颜色哨兵不能是 0。 */
    public static final int AUTO = -1;

    /** 「未指定颜色」：用主题色。 */
    public static final int NO_COLOR = Integer.MIN_VALUE;

    /** 按钮默认尺寸（与原版主菜单一致）。 */
    public static final int DEFAULT_BUTTON_WIDTH = 200;

    public static final int DEFAULT_BUTTON_HEIGHT = 20;

    /** 文字缩放区间：太小看不清，太大就会盖住整个屏幕。 */
    public static final float MIN_SCALE = 0.25f;

    public static final float MAX_SCALE = 8f;

    /** 容器嵌套深度上限（防止配置写出病态结构）。 */
    public static final int MAX_DEPTH = 4;

    /** 列内间距的上限（像素）。 */
    public static final int MAX_GAP = 400;

    /** 色带的默认颜色：20% 黑（能压住亮背景，又不至于把底图糊掉）。 */
    public static final int DEFAULT_BAR_COLOR = 0x33000000;

    /** 色带左右默认留白（像素）。 */
    public static final int DEFAULT_BAR_PADDING = 10;

    /** 色带左右留白上限（像素）。 */
    public static final int MAX_BAR_PADDING = 200;

    public MenuElement {
        type = type == null ? Type.BUTTON : type;
        text = text == null ? "" : text;
        file = file == null ? "" : file;
        action = action == null ? MenuAction.NONE_ACTION : action;
        align = align == null ? Align.CENTER : align;
        valign = valign == null ? VAlign.MIDDLE : valign;
        scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
        sheet = type == Type.IMAGE ? sheet : null;
        column = type == Type.COLUMN ? column : null;
    }

    /** 文案是否是语言键（以 {@code ccnr_menu.} 开头）。 */
    public boolean isLangKey() {
        return text.startsWith(MenuConfig.LANG_PREFIX);
    }

    /** 是否自带颜色（否则用主题色）。 */
    public boolean hasOwnColor() {
        return color != NO_COLOR;
    }

    /** 按钮宽度：未指定时用默认值。 */
    public int buttonWidth() {
        return width == AUTO ? DEFAULT_BUTTON_WIDTH : width;
    }

    /** 按钮高度：未指定时用默认值。 */
    public int buttonHeight() {
        return height == AUTO ? DEFAULT_BUTTON_HEIGHT : height;
    }

    /** 是否是「点了会做事」的按钮（诊断输出用）。 */
    public boolean actionable() {
        return type == Type.BUTTON && action.kind() != MenuAction.Kind.NONE;
    }

    /** 是否是动画图片元素。 */
    public boolean animatedImage() {
        return type == Type.IMAGE && sheet != null;
    }

    /** 递归统计按钮个数（容器里的按钮也算，见 {@link MenuConfig#buttonCount()}）。 */
    public int countButtons() {
        if (type == Type.BUTTON) return 1;
        if (type != Type.COLUMN || column == null) return 0;
        int total = 0;
        for (MenuElement child : column.children()) total += child.countButtons();
        return total;
    }

    /** 递归统计元素个数（诊断输出用）。 */
    public int countAll() {
        if (type != Type.COLUMN || column == null) return 1;
        int total = 1;
        for (MenuElement child : column.children()) total += child.countAll();
        return total;
    }
}
