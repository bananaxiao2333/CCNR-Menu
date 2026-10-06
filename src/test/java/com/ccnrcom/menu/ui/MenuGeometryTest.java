/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 摆放与缩放几何门禁（纯逻辑）。
 *
 * <p>为什么这类测试值得写：菜单「看起来不对」几乎全是几何问题（图片留黑边、按钮压在一起、
 * 动画错位），而它们**完全可以脱离游戏验证**。项目禁止起客户端，所以几何必须先能被断言，
 * 才谈得上「改完是对的」。
 */
class MenuGeometryTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;

    @Test
    @DisplayName("cover：至少覆盖整屏（宁可多一点点，也不能留黑边）")
    void coverCoversEverything() {
        MenuGeometry.Rect rect = MenuGeometry.fit(Fit.COVER, 1920, 1080, SCREEN_W, SCREEN_H);
        assertEquals(SCREEN_W, rect.w());
        assertEquals(SCREEN_H, rect.h());

        // 4:3 的图放进 16:9 的屏幕：宽度必须溢出，否则左右会出现黑边
        MenuGeometry.Rect narrow = MenuGeometry.fit(Fit.COVER, 800, 600, SCREEN_W, SCREEN_H);
        assertTrue(narrow.w() >= SCREEN_W, "cover 必须覆盖整宽: " + narrow);
        assertTrue(narrow.h() >= SCREEN_H, "cover 必须覆盖整高: " + narrow);
        assertTrue(narrow.x() <= 0 && narrow.y() <= 0, "溢出的部分要对称地裁在两侧: " + narrow);
    }

    @Test
    @DisplayName("cover：取整方向保证不会出现 1 像素黑线")
    void coverRoundingHasNoSeam() {
        // 1000x1000 图放进 1920x1081：等比缩放后不是整数，取整方向错了会留缝
        MenuGeometry.Rect rect = MenuGeometry.fit(Fit.COVER, 1000, 1000, 1920, 1081);
        assertTrue(rect.w() >= 1920);
        assertTrue(rect.h() >= 1081);
        assertTrue(rect.x() <= 0);
        assertTrue(rect.y() <= 0);
    }

    @Test
    @DisplayName("contain：完整可见且不超出屏幕")
    void containFitsInside() {
        MenuGeometry.Rect rect = MenuGeometry.fit(Fit.CONTAIN, 800, 600, SCREEN_W, SCREEN_H);
        assertTrue(rect.w() <= SCREEN_W);
        assertTrue(rect.h() <= SCREEN_H);
        assertEquals(4.0 / 3.0, rect.w() / (double) rect.h(), 0.01, "contain 必须保持比例");
        assertTrue(rect.x() >= 0 && rect.y() >= 0, "居中偏移不应为负: " + rect);
    }

    @Test
    @DisplayName("stretch 铺满、center 原始尺寸居中、tile 返回单块尺寸")
    void otherFits() {
        assertEquals(
                new MenuGeometry.Rect(0, 0, SCREEN_W, SCREEN_H),
                MenuGeometry.fit(Fit.STRETCH, 100, 50, SCREEN_W, SCREEN_H));
        assertEquals(
                new MenuGeometry.Rect((SCREEN_W - 100) / 2, (SCREEN_H - 50) / 2, 100, 50),
                MenuGeometry.fit(Fit.CENTER, 100, 50, SCREEN_W, SCREEN_H));
        assertEquals(new MenuGeometry.Rect(0, 0, 16, 16), MenuGeometry.fit(Fit.TILE, 16, 16, SCREEN_W, SCREEN_H));
    }

    @Test
    @DisplayName("fit 遇到非法尺寸不会崩（返回整屏）")
    void fitHandlesZeroSizes() {
        assertEquals(
                new MenuGeometry.Rect(0, 0, SCREEN_W, SCREEN_H), MenuGeometry.fit(Fit.COVER, 0, 0, SCREEN_W, SCREEN_H));
        assertEquals(new MenuGeometry.Rect(0, 0, 0, 0), MenuGeometry.fit(Fit.COVER, 10, 10, 0, 0));
    }

    @Test
    @DisplayName("place：align/valign 决定分数锚点落在元素的哪条边上")
    void placeAnchors() {
        // 居中
        assertEquals(
                new MenuGeometry.Rect((SCREEN_W - 200) / 2, (SCREEN_H - 20) / 2, 200, 20),
                MenuGeometry.place(0.5, 0.5, Align.CENTER, VAlign.MIDDLE, 200, 20, SCREEN_W, SCREEN_H));
        // 左对齐 + 顶部对齐：锚点就是左上角
        assertEquals(
                new MenuGeometry.Rect(192, 108, 200, 20),
                MenuGeometry.place(0.1, 0.1, Align.LEFT, VAlign.TOP, 200, 20, SCREEN_W, SCREEN_H));
        // 右对齐 + 底部对齐：锚点落在右下角
        assertEquals(
                new MenuGeometry.Rect(1920 - 200, 1080 - 20, 200, 20),
                MenuGeometry.place(1.0, 1.0, Align.RIGHT, VAlign.BOTTOM, 200, 20, SCREEN_W, SCREEN_H));
    }

    @Test
    @DisplayName("place：越界分数被收敛（元素不会跑到屏幕外）")
    void placeClampsFraction() {
        assertEquals(
                MenuGeometry.place(0.0, 0.0, Align.LEFT, VAlign.TOP, 10, 10, SCREEN_W, SCREEN_H),
                MenuGeometry.place(-5.0, -5.0, Align.LEFT, VAlign.TOP, 10, 10, SCREEN_W, SCREEN_H));
        assertEquals(
                MenuGeometry.place(1.0, 1.0, Align.LEFT, VAlign.TOP, 10, 10, SCREEN_W, SCREEN_H),
                MenuGeometry.place(9.0, 9.0, Align.LEFT, VAlign.TOP, 10, 10, SCREEN_W, SCREEN_H));
    }

    @Test
    @DisplayName("translate：像素微调（原版那种两个 98 宽按钮之间的 2px 缝）")
    void translate() {
        MenuGeometry.Rect left = MenuGeometry.place(0.5, 0.5, Align.RIGHT, VAlign.MIDDLE, 98, 20, SCREEN_W, SCREEN_H)
                .translate(-2, 0);
        MenuGeometry.Rect right = MenuGeometry.place(0.5, 0.5, Align.LEFT, VAlign.MIDDLE, 98, 20, SCREEN_W, SCREEN_H)
                .translate(2, 0);
        assertEquals(4, right.x() - left.x2(), "两个按钮之间应当正好留 4px");
        assertEquals(left, left.translate(0, 0), "零位移不应产生新对象语义");
    }

    @Test
    @DisplayName("frameUv：网格取整后每格大小一致，最后一格贴住右下角")
    void frameUv() {
        // 8x4 的网格放在 1920x1080 的贴图上：每格 240x270
        assertEquals(new MenuGeometry.Rect(0, 0, 240, 270), MenuGeometry.frameUv(0, 8, 4, 1920, 1080));
        assertEquals(new MenuGeometry.Rect(240, 0, 240, 270), MenuGeometry.frameUv(1, 8, 4, 1920, 1080));
        assertEquals(new MenuGeometry.Rect(0, 270, 240, 270), MenuGeometry.frameUv(8, 8, 4, 1920, 1080));
        assertEquals(new MenuGeometry.Rect(1680, 810, 240, 270), MenuGeometry.frameUv(31, 8, 4, 1920, 1080));

        // 尺寸除不尽时，最后一格贴边（不让余数像素永远画不到）
        MenuGeometry.Rect last = MenuGeometry.frameUv(7, 8, 1, 1001, 100);
        assertEquals(1001, last.x2(), "最后一列必须贴住右边界");
    }

    @Test
    @DisplayName("frameUv：越界帧号取模而不是崩")
    void frameUvWraps() {
        assertEquals(MenuGeometry.frameUv(0, 4, 4, 400, 400), MenuGeometry.frameUv(16, 4, 4, 400, 400));
        assertEquals(MenuGeometry.frameUv(15, 4, 4, 400, 400), MenuGeometry.frameUv(-1, 4, 4, 400, 400));
    }

    @Test
    @DisplayName("tileCount：含不完整的那一次")
    void tileCount() {
        assertEquals(120, MenuGeometry.tileCount(1920, 16));
        assertEquals(1, MenuGeometry.tileCount(10, 16));
        assertEquals(1, MenuGeometry.tileCount(1920, 0));
    }

    @Test
    @DisplayName("stack 右对齐：所有子元素右边缘对齐（「图标与按钮列右对齐」就是这一步）")
    void stackAlignsRightEdges() {
        // 图标 320 宽、按钮 220 宽，列宽 360
        MenuGeometry.Stack stack = MenuGeometry.stack(
                java.util.List.of(
                        new MenuGeometry.Size(320, 100),
                        new MenuGeometry.Size(220, 20),
                        new MenuGeometry.Size(220, 20)),
                360,
                10,
                Align.RIGHT);

        assertEquals(360, stack.width());
        assertEquals(100 + 10 + 20 + 10 + 20, stack.height(), "高度是子元素高之和 + 之间的间距");
        for (MenuGeometry.Rect rect : stack.childRects()) {
            assertEquals(360, rect.x2(), "每个子元素的右边缘都贴在列右侧: " + rect);
        }
        assertEquals(0, stack.childRects().get(0).y(), "第一个子元素在列顶");
        assertEquals(110, stack.childRects().get(1).y(), "第二个在「第一个高 + 间距」处");
        assertEquals(140, stack.childRects().get(2).y());
    }

    @Test
    @DisplayName("stack 左对齐 / 居中 / 自动列宽")
    void stackOtherAlignments() {
        java.util.List<MenuGeometry.Size> sizes =
                java.util.List.of(new MenuGeometry.Size(100, 10), new MenuGeometry.Size(60, 10));

        MenuGeometry.Stack left = MenuGeometry.stack(sizes, 200, 0, Align.LEFT);
        assertEquals(0, left.childRects().get(0).x());
        assertEquals(0, left.childRects().get(1).x());

        MenuGeometry.Stack center = MenuGeometry.stack(sizes, 200, 0, Align.CENTER);
        assertEquals(50, center.childRects().get(0).x());
        assertEquals(70, center.childRects().get(1).x());

        // 列宽未给（<=0）时取最宽的子元素
        MenuGeometry.Stack auto = MenuGeometry.stack(sizes, 0, 4, Align.RIGHT);
        assertEquals(100, auto.width());
        assertEquals(100, auto.childRects().get(0).x2());
        assertEquals(100, auto.childRects().get(1).x2());
        assertEquals(24, auto.height(), "两个 10 高的元素 + 一个 4 的间距");
    }

    @Test
    @DisplayName("stack 边界：空列表、负间距、单个子元素")
    void stackDegenerate() {
        MenuGeometry.Stack empty = MenuGeometry.stack(java.util.List.of(), 100, 10, Align.RIGHT);
        assertEquals(0, empty.height());
        assertTrue(empty.childRects().isEmpty());

        MenuGeometry.Stack one =
                MenuGeometry.stack(java.util.List.of(new MenuGeometry.Size(50, 30)), 100, 10, Align.CENTER);
        assertEquals(30, one.height(), "只有一个子元素时不该多算一个间距");
        assertEquals(25, one.childRects().get(0).x());

        MenuGeometry.Stack negativeGap = MenuGeometry.stack(
                java.util.List.of(new MenuGeometry.Size(10, 10), new MenuGeometry.Size(10, 10)), 10, -5, Align.LEFT);
        assertEquals(20, negativeGap.height(), "负间距按 0 处理（元素之间不能重叠）");
    }

    @Test
    @DisplayName("overflows：小 GUI 下宽竖列会被裁（这是要靠日志说出来的情况）")
    void overflowDetection() {
        // 默认布局：360 宽的列，右边缘在 0.955 处。427 宽的 GUI 放得下
        MenuGeometry.Rect fits = MenuGeometry.place(0.955, 0.5, Align.RIGHT, VAlign.MIDDLE, 360, 240, 427, 240);
        assertFalse(MenuGeometry.overflows(fits, 427, 240));

        // 320 宽的 GUI 放不下（左边缘变成负数）
        MenuGeometry.Rect clipped = MenuGeometry.place(0.955, 0.5, Align.RIGHT, VAlign.MIDDLE, 360, 240, 320, 240);
        assertTrue(clipped.x() < 0);
        assertTrue(MenuGeometry.overflows(clipped, 320, 240));

        // 上下超出同样算超出
        assertTrue(MenuGeometry.overflows(new MenuGeometry.Rect(0, -5, 10, 10), 427, 240));
        assertTrue(MenuGeometry.overflows(new MenuGeometry.Rect(0, 235, 10, 10), 427, 240));
        assertFalse(MenuGeometry.overflows(new MenuGeometry.Rect(0, 0, 427, 240), 427, 240));
    }

    @Test
    @DisplayName("kenBurns：进度 0 时与原地一模一样，进度 1 时放大且向右偏移")
    void kenBurnsMovesForward() {
        // 1920x1080 的图铺到 1920x1080 的屏幕上，cover 之后正好整屏
        MenuGeometry.Rect base = MenuGeometry.fit(Fit.COVER, 1920, 1080, 1920, 1080);
        assertEquals(new MenuGeometry.Rect(0, 0, 1920, 1080), base);

        MenuGeometry.Rect start = MenuGeometry.kenBurns(base, 0.08f, 0.05f, 0f, 0f, 1920, 1080);
        assertEquals(base, start, "进度 0 时必须与不运镜完全一致（否则切图那一下会跳）");

        MenuGeometry.Rect end = MenuGeometry.kenBurns(base, 0.08f, 0.05f, 0f, 1f, 1920, 1080);
        assertTrue(end.w() > base.w(), "要放大一点点");
        assertTrue(end.h() > base.h());
        assertTrue(end.x() > base.x(), "要缓慢往右偏移");
    }

    @Test
    @DisplayName("kenBurns：围绕中心放大（左右各让出相同的量），否则画面会往右下角跑")
    void kenBurnsZoomsAroundCenter() {
        MenuGeometry.Rect base = new MenuGeometry.Rect(0, 0, 1000, 500);
        MenuGeometry.Rect zoomed = MenuGeometry.kenBurns(base, 0.2f, 0f, 0f, 1f, 1000, 500);
        assertEquals(1200, zoomed.w());
        assertEquals(600, zoomed.h());
        assertEquals(-100, zoomed.x(), "左边让出 100");
        assertEquals(100, zoomed.x2() - base.x2(), "右边也多出 100");
        assertEquals(-50, zoomed.y());
    }

    @Test
    @DisplayName("kenBurns：偏移按屏幕尺寸的比例算，分辨率变了观感一致")
    void kenBurnsPanScalesWithScreen() {
        MenuGeometry.Rect small =
                MenuGeometry.kenBurns(new MenuGeometry.Rect(0, 0, 320, 240), 0f, 0.1f, 0f, 1f, 320, 240);
        MenuGeometry.Rect large =
                MenuGeometry.kenBurns(new MenuGeometry.Rect(0, 0, 1920, 1080), 0f, 0.1f, 0f, 1f, 1920, 1080);
        assertEquals(32, small.x(), "320 宽的 10%");
        assertEquals(192, large.x(), "1920 宽的 10%");
    }

    @Test
    @DisplayName("kenBurns：进度越界/NaN 都被收敛（配置写错了也不该画出错位画面）")
    void kenBurnsClampsProgress() {
        MenuGeometry.Rect base = new MenuGeometry.Rect(0, 0, 100, 100);
        assertEquals(
                MenuGeometry.kenBurns(base, 0.5f, 0f, 0f, 1f, 100, 100),
                MenuGeometry.kenBurns(base, 0.5f, 0f, 0f, 99f, 100, 100),
                "进度大于 1 按 1 处理");
        assertEquals(
                MenuGeometry.kenBurns(base, 0.5f, 0f, 0f, 0f, 100, 100),
                MenuGeometry.kenBurns(base, 0.5f, 0f, 0f, -5f, 100, 100),
                "负进度按 0 处理");
        assertEquals(base, MenuGeometry.kenBurns(base, 0.5f, 0f, 0f, Float.NaN, 100, 100), "NaN 按 0 处理");
    }
}
