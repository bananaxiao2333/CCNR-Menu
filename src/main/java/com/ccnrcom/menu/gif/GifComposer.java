/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

/**
 * GIF 画布合成（纯逻辑，可单测）。
 *
 * <p>为什么需要它：GIF 的每一帧**不一定**是一整幅图。优化过的 GIF（ezgif 之类的工具默认就会这么做）
 * 只把「与上一帧不同的那一小块」写进帧里，靠画布上残留的内容拼出完整画面；再叠加每帧的
 * {@code disposalMethod}（画完之后这一块要不要清掉、要不要还原成上一帧的样子）。
 * 只读「第一帧」或「每帧各自独立」的朴素实现会得到一堆碎片，且**只有动图才会暴露**——
 * 静态看一眼是好的，所以这类缺陷只能靠合成逻辑本身的测试来防。
 *
 * <p>像素格式统一为 AWT 的 {@code 0xAARRGGBB}（与 {@code BufferedImage.getRGB} 一致），
 * 转成渲染用的 ABGR 是客户端那一层的事。
 */
public final class GifComposer {

    private GifComposer() {}

    /** 新建全透明画布。 */
    public static int[] newCanvas(int width, int height) {
        return new int[Math.max(1, width * height)];
    }

    /**
     * 把一帧画到画布上。
     *
     * <p>两条规则：
     * <ul>
     *   <li>**透明像素不覆盖**画布（GIF 的透明索引是「这里保持原样」，不是「这里涂透明」）；
     *       不这么做会把上一帧的内容擦出一堆洞。</li>
     *   <li>越界像素直接丢弃（损坏的 GIF 里帧坐标可以超出逻辑屏幕，不能让它写坏数组）。</li>
     * </ul>
     */
    public static void drawFrame(
            int[] canvas, int canvasW, int canvasH, int[] framePixels, int px, int py, int pw, int ph) {
        if (canvas == null || framePixels == null || pw <= 0 || ph <= 0) return;
        for (int y = 0; y < ph; y++) {
            int cy = py + y;
            if (cy < 0 || cy >= canvasH) continue;
            for (int x = 0; x < pw; x++) {
                int cx = px + x;
                if (cx < 0 || cx >= canvasW) continue;
                int argb = framePixels[y * pw + x];
                if ((argb >>> 24) == 0) continue;
                canvas[cy * canvasW + cx] = argb;
            }
        }
    }

    /** 把一块矩形清成透明（GIF 的 {@code disposalMethod = 2}）。 */
    public static void clearRect(int[] canvas, int canvasW, int canvasH, int x, int y, int rw, int rh) {
        if (canvas == null || rw <= 0 || rh <= 0) return;
        for (int yy = 0; yy < rh; yy++) {
            int cy = y + yy;
            if (cy < 0 || cy >= canvasH) continue;
            for (int xx = 0; xx < rw; xx++) {
                int cx = x + xx;
                if (cx < 0 || cx >= canvasW) continue;
                canvas[cy * canvasW + cx] = 0;
            }
        }
    }

    /**
     * 最近邻整倍缩小（{@code scale = 2} 表示边长减半）。
     *
     * <p>为什么用最近邻而不是双线性：这一步只在「GIF 太大、超出内存预算」时发生，
     * 目的是**保住帧数与动画节奏**而不是保住画质；双线性要按像素做浮点插值，
     * 一个 4K×60 帧的 GIF 会因此在启动时多花几秒。
     */
    public static int[] downscale(int[] src, int srcW, int srcH, int scale) {
        int s = Math.max(1, scale);
        if (s == 1) return src.clone();
        int dstW = Math.max(1, srcW / s);
        int dstH = Math.max(1, srcH / s);
        int[] dst = new int[dstW * dstH];
        for (int y = 0; y < dstH; y++) {
            int sy = Math.min(srcH - 1, y * s);
            for (int x = 0; x < dstW; x++) {
                int sx = Math.min(srcW - 1, x * s);
                dst[y * dstW + x] = src[sy * srcW + sx];
            }
        }
        return dst;
    }
}
