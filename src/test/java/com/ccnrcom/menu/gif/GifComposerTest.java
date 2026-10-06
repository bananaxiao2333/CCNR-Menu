/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * GIF 画布合成门禁（纯逻辑）。
 *
 * <p>这是**最容易被朴素实现写错**的地方，而且错了只在动画上暴露：优化过的 GIF 只写
 * 「与上一帧不同的那一小块」，靠画布残留内容拼出完整画面；再叠加每帧的
 * {@code disposalMethod}（画完清掉 / 还原成上一帧）。于是这里逐条覆盖：
 * 透明像素不覆盖、disposal=2 清块、disposal=3 还原、越界坐标不写坏数组。
 */
class GifComposerTest {

    private static final int C = 0xFF112233;
    private static final int D = 0xFF445566;

    @Test
    @DisplayName("透明像素不覆盖画布（GIF 的透明索引意思是「这里保持原样」）")
    void transparentPixelsAreSkipped() {
        int[] canvas = GifComposer.newCanvas(2, 2);
        canvas[0] = C;
        // 2x1 的帧：第一像素透明、第二像素实心
        int[] frame = {0x00000000, D};

        GifComposer.drawFrame(canvas, 2, 2, frame, 0, 0, 2, 1);

        assertEquals(C, canvas[0], "透明像素必须保留画布原值");
        assertEquals(D, canvas[1], "实心像素正常覆盖");
    }

    @Test
    @DisplayName("部分帧只改自己那一小块（优化的 GIF 靠这个拼图）")
    void partialFrameOnlyTouchesItsRect() {
        int[] canvas = GifComposer.newCanvas(4, 4);
        java.util.Arrays.fill(canvas, C);
        int[] patch = {D};

        GifComposer.drawFrame(canvas, 4, 4, patch, 2, 3, 1, 1);

        assertEquals(D, canvas[3 * 4 + 2], "补丁位置被覆盖");
        assertEquals(C, canvas[0], "其余像素不动");
        assertEquals(1, count(canvas, D), "只应有 1 个像素被改");
    }

    @Test
    @DisplayName("越界坐标被裁剪（损坏的 GIF 不能写坏数组）")
    void outOfBoundsIsClipped() {
        int[] canvas = GifComposer.newCanvas(2, 2);
        java.util.Arrays.fill(canvas, C);
        int[] frame = {D, D, D, D};

        GifComposer.drawFrame(canvas, 2, 2, frame, 1, 1, 2, 2);

        assertEquals(D, canvas[3], "落在画布内的那个像素要画上");
        assertEquals(C, canvas[0], "画布外的像素被丢弃");
        GifComposer.drawFrame(canvas, 2, 2, frame, -5, -5, 2, 2);
        assertEquals(C, canvas[0], "负坐标整块丢弃，不抛异常");
    }

    @Test
    @DisplayName("disposal=2：把上一帧占的矩形清成透明")
    void disposeToBackground() {
        int[] canvas = GifComposer.newCanvas(4, 4);
        java.util.Arrays.fill(canvas, C);
        GifComposer.drawFrame(canvas, 4, 4, new int[] {D}, 1, 1, 1, 1);

        GifComposer.clearRect(canvas, 4, 4, 1, 1, 1, 1);

        assertEquals(0, canvas[1 * 4 + 1], "该像素被清成透明");
        assertEquals(C, canvas[0], "其余像素保留");
    }

    @Test
    @DisplayName("disposal=3：整幅画布还原成上一帧之前的样子（用副本模拟）")
    void disposeToPrevious() {
        int[] canvas = GifComposer.newCanvas(2, 2);
        java.util.Arrays.fill(canvas, C);
        int[] snapshot = canvas.clone();
        GifComposer.drawFrame(canvas, 2, 2, new int[] {D, D, D, D}, 0, 0, 2, 2);
        assertArrayEquals(new int[] {D, D, D, D}, canvas);

        System.arraycopy(snapshot, 0, canvas, 0, canvas.length);

        assertArrayEquals(new int[] {C, C, C, C}, canvas);
    }

    @Test
    @DisplayName("整倍缩小：输出尺寸正确、取最近邻、不越界")
    void downscale() {
        int[] src = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        int[] half = GifComposer.downscale(src, 4, 4, 2);
        assertEquals(4, half.length);
        assertArrayEquals(new int[] {1, 3, 9, 11}, half);

        int[] same = GifComposer.downscale(src, 4, 4, 1);
        assertArrayEquals(src, same);
        assertTrue(same != src, "scale=1 也要返回副本（调用方会长期持有）");

        int[] extreme = GifComposer.downscale(src, 4, 4, 99);
        assertEquals(1, extreme.length, "缩过头也要至少剩 1 像素");
    }

    private static int count(int[] pixels, int value) {
        int n = 0;
        for (int p : pixels) {
            if (p == value) n++;
        }
        return n;
    }
}
