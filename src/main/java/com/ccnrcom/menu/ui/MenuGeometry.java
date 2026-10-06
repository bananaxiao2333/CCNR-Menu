/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单几何计算（纯逻辑，可单测）。
 *
 * <p>这里只有「矩形怎么摆」这一件事，**不碰任何渲染 API**。原因很实际：摆放错误（按钮压在一起、
 * 背景留黑边、缩放后错位）是这类模组最常见的缺陷，而它完全可以脱离游戏验证——
 * 项目禁止起客户端做测试，所以几何必须先能被 JUnit 断言，才谈得上「改完是对的」。
 */
public final class MenuGeometry {

    /** 屏幕像素矩形（左上角 + 宽高）。允许超出屏幕（cover 裁切、负偏移居中都会这样）。 */
    public record Rect(int x, int y, int w, int h) {
        public int x2() {
            return x + w;
        }

        public int y2() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x2() && py >= y && py < y2();
        }

        /** 平移（元素的像素微调走这里，保持「摆放」与「微调」两件事分开）。 */
        public Rect translate(int dx, int dy) {
            return dx == 0 && dy == 0 ? this : new Rect(x + dx, y + dy, w, h);
        }
    }

    /** 纯尺寸（量出来的或配置写死的），用于竖列排布。 */
    public record Size(int w, int h) {

        public Size {
            w = Math.max(0, w);
            h = Math.max(0, h);
        }
    }

    /**
     * 竖列排布的结果。
     *
     * @param width 整列宽（配置给定，或取子元素里最宽的那个）
     * @param height 整列高（各子元素高之和 + 间距）
     * @param childRects 各子元素**相对列左上角**的矩形
     */
    public record Stack(int width, int height, List<Rect> childRects) {}

    /**
     * 把一串子元素自上而下排成一列，并按 {@code childAlign} 在列宽内对齐。
     *
     * <p>为什么「列宽」要么由配置给定、要么取最宽的子元素：由配置给定时，
     * 一列按钮里混一个更宽的图标也不会把按钮挤得左右乱跳；取最宽者则是最省心的默认
     * （对齐的参照物就是最宽的那个）。两种都不需要作者手算坐标。
     *
     * <p>{@code childAlign = RIGHT} 时所有子元素右边缘对齐——「整块靠右、图标与按钮列右对齐」
     * 就是「列本身靠右摆放 + 列内右对齐」这两步，不需要任何魔法数字。
     */
    public static Stack stack(List<Size> sizes, int columnWidth, int gap, Align childAlign) {
        int width = columnWidth > 0
                ? columnWidth
                : sizes.stream().mapToInt(Size::w).max().orElse(0);
        int spacing = Math.max(0, gap);
        Align align = childAlign == null ? Align.CENTER : childAlign;
        List<Rect> rects = new ArrayList<>(sizes.size());
        int y = 0;
        for (Size size : sizes) {
            int x =
                    switch (align) {
                        case LEFT -> 0;
                        case CENTER -> (width - size.w()) / 2;
                        case RIGHT -> width - size.w();
                    };
            rects.add(new Rect(x, y, size.w(), size.h()));
            y += size.h() + spacing;
        }
        // 最后一个子元素后面不加间距，否则容器高度会多算一个 gap（居中对齐就会整体偏上）
        int height = sizes.isEmpty() ? 0 : Math.max(0, y - spacing);
        return new Stack(width, height, List.copyOf(rects));
    }

    private MenuGeometry() {}

    /**
     * 把 {@code srcW × srcH} 的贴图按 {@code fit} 摆进 {@code screenW × screenH} 的屏幕。
     *
     * <p>为什么 COVER 用 {@code ceil}、CONTAIN 用 {@code floor}：浮点缩放后取整会各差不到 1 像素，
     * 方向取反就会在边缘留下一条**随机出现的黑线**（取决于分辨率）。原则是让结果宁可多一点点
     * 也不要少一点点——COVER 多出来的部分被裁掉，CONTAIN 少掉的部分本来就在屏幕内。
     */
    public static Rect fit(Fit fit, int srcW, int srcH, int screenW, int screenH) {
        if (srcW <= 0 || srcH <= 0 || screenW <= 0 || screenH <= 0) {
            return new Rect(0, 0, Math.max(0, screenW), Math.max(0, screenH));
        }
        switch (fit) {
            case STRETCH -> {
                return new Rect(0, 0, screenW, screenH);
            }
            case CENTER -> {
                return new Rect((screenW - srcW) / 2, (screenH - srcH) / 2, srcW, srcH);
            }
            case TILE -> {
                return new Rect(0, 0, srcW, srcH);
            }
            case COVER -> {
                double scale = Math.max(screenW / (double) srcW, screenH / (double) srcH);
                int w = (int) Math.ceil(srcW * scale);
                int h = (int) Math.ceil(srcH * scale);
                return new Rect((screenW - w) / 2, (screenH - h) / 2, w, h);
            }
            case CONTAIN -> {
                double scale = Math.min(screenW / (double) srcW, screenH / (double) srcH);
                int w = (int) Math.floor(srcW * scale);
                int h = (int) Math.floor(srcH * scale);
                return new Rect((screenW - w) / 2, (screenH - h) / 2, w, h);
            }
            default -> {
                return new Rect(0, 0, screenW, screenH);
            }
        }
    }

    /**
     * 按分数锚点摆一个 {@code w × h} 的元素。
     *
     * @param xFrac 0..1 的横向分数（{@code align} 决定它是左边缘、中线还是右边缘）
     * @param yFrac 0..1 的纵向分数（{@code valign} 决定它是上边缘、中线还是下边缘）
     */
    public static Rect place(
            double xFrac, double yFrac, Align align, VAlign valign, int w, int h, int screenW, int screenH) {
        double anchorX = clampFraction(xFrac) * screenW;
        double anchorY = clampFraction(yFrac) * screenH;
        double left =
                switch (align) {
                    case LEFT -> anchorX;
                    case RIGHT -> anchorX - w;
                    case CENTER -> anchorX - w / 2.0;
                };
        double top =
                switch (valign) {
                    case TOP -> anchorY;
                    case BOTTOM -> anchorY - h;
                    case MIDDLE -> anchorY - h / 2.0;
                };
        return new Rect((int) Math.round(left), (int) Math.round(top), w, h);
    }

    /**
     * 精灵图里第 {@code frame} 帧在贴图中的像素区域。
     *
     * <p>尺寸用**整数除法**取每格大小：贴图尺寸不是格数整数倍时（例如宽 1000 放 8 格 = 125，或宽 1001），
     * 整数除法会留下几个永远画不到的边缘像素。处理方式是把**最后一列/行贴到贴图边界**，
     * 由那一格多吸收几个像素——反之（用浮点乘回去）会让每一格的边界落在半个像素上，
     * 采样时把邻格内容吸进来，表现为「动画边缘有一条抖动的杂色」。
     */
    public static Rect frameUv(int frame, int cols, int rows, int sheetW, int sheetH) {
        int c = Math.max(1, cols);
        int r = Math.max(1, rows);
        int frameW = Math.max(1, sheetW / c);
        int frameH = Math.max(1, sheetH / r);
        int wrapped = Math.floorMod(frame, Math.max(1, c * r));
        int col = wrapped % c;
        int row = wrapped / c;
        int u = (col == c - 1) ? Math.max(0, sheetW - frameW) : col * frameW;
        int v = (row == r - 1) ? Math.max(0, sheetH - frameH) : row * frameH;
        return new Rect(u, v, frameW, frameH);
    }

    /** 平铺模式需要的重复次数（含不完整的那一次）。 */
    public static int tileCount(int screenLen, int tileLen) {
        if (tileLen <= 0) return 1;
        return Math.max(1, (screenLen + tileLen - 1) / tileLen);
    }

    /**
     * 元素是否**部分或全部**落在屏幕外。
     *
     * <p>为什么值得单独一条纯函数：菜单元素用的是绝对像素尺寸，而 GUI 尺寸随窗口与缩放变化
     * （小窗口下可能只有 320×240 个 GUI 单位）。一个 360 宽的竖列在那种屏幕上必然被裁——
     * 纯函数让「会不会被裁」可以被断言，调用方则把它变成一条日志而不是无声的裁切。
     */
    public static boolean overflows(Rect rect, int screenW, int screenH) {
        return rect.x() < 0 || rect.y() < 0 || rect.x2() > screenW || rect.y2() > screenH;
    }

    /** 分数锚点收敛到 0..1（越界值会让元素跑到屏幕外，应当被配置层拦下）。 */
    public static double clampFraction(double v) {
        if (Double.isNaN(v)) return 0.5;
        return Math.min(1.0, Math.max(0.0, v));
    }
}
