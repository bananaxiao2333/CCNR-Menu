/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

/**
 * 动画 GIF 解码器（纯 Java，只用 JDK 自带的 ImageIO）。
 *
 * <p>**为什么是这个方案**：动画背景最初的想法是「内嵌一个浏览器渲染 HTML」，
 * 但那要拖进 Chromium（MCEF 一类），客户端首次启动要下载上百 MB 原生库。
 * 而「放一段小动画当背景」这个需求，JDK 自带的 {@code javax.imageio} 就能满足——
 * GIF 解码器是 java.desktop 的标配，**零依赖、零原生库、离线可用**。
 *
 * <p>解码出的像素是 {@code 0xAARRGGBB} 的 int[]，**不碰任何渲染 API**，所以整条解码链路
 * 都能在 JUnit 里验证（项目禁止起客户端）。转成 GPU 贴图是 {@code client} 那一层的事。
 *
 * <p>线程：本类只做纯计算，可以在工作线程上跑（客户端正是这么用的，见
 * {@code client.background.GifBackground}）；它不触碰 GL、不读游戏状态。
 */
public final class GifDecoder {

    /** 小于这个时长的帧按 {@link #DEFAULT_FRAME_MS} 处理。 */
    public static final int MIN_FRAME_MS = 20;

    /**
     * 「时长缺失或不合理」时的兜底帧时长。
     *
     * <p>100ms 不是随手写的：浏览器对 {@code delayTime < 2cs} 的帧一律按 10cs 播放，
     * 因为大量导出工具会把 0 写进去（意思是「越快越好」），照字面执行会变成一秒几百帧的闪烁。
     * 这里沿用同一套规则，动画观感才与玩家在浏览器里看到的一致。
     */
    public static final int DEFAULT_FRAME_MS = 100;

    /** 单帧时长上限（10 分钟）：防止写坏的值让动画看起来像卡死。 */
    public static final int MAX_FRAME_MS = 600_000;

    private static final String GIF_IMAGE_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String GIF_STREAM_FORMAT = "javax_imageio_gif_stream_1.0";

    private GifDecoder() {}

    /** 按默认预算解码。 */
    public static GifFrames decode(Path file) throws IOException {
        return decode(file, GifBudget.DEFAULT_BYTES);
    }

    /**
     * 解码动画 GIF 的所有帧。
     *
     * @param budgetBytes 总像素内存预算；超出时按整倍缩小边长（帧数与时长不变）
     * @throws IOException 文件不可读、不是 GIF、损坏、超出尺寸/帧数/预算上限（消息里带原因与建议）
     */
    public static GifFrames decode(Path file, long budgetBytes) throws IOException {
        if (file == null) throw new IOException("GIF 路径为空");
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new IOException("无法打开文件: " + file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("不是可识别的图片格式: " + file);
            ImageReader reader = readers.next();
            try {
                // ignoreMetadata 必须是 false——帧的位置、时长、disposal 全在元数据里
                reader.setInput(in, false, false);
                String format = reader.getFormatName();
                if (!"gif".equalsIgnoreCase(format)) {
                    throw new IOException("不是动画 GIF（实际格式: " + format + "）；静态图请用 background.type=image");
                }
                return readAll(reader, file, budgetBytes);
            } finally {
                reader.dispose();
            }
        }
    }

    private static GifFrames readAll(ImageReader reader, Path file, long budgetBytes) throws IOException {
        int frameCount = reader.getNumImages(true);
        if (frameCount < 1) throw new IOException("GIF 里没有任何帧: " + file);
        if (frameCount > GifBudget.MAX_FRAMES) {
            throw new IOException("GIF 帧数过多: " + frameCount + "（上限 " + GifBudget.MAX_FRAMES + " 帧，" + "背景动画请用更短的循环）");
        }

        int[] canvas = canvasSize(reader, frameCount);
        int width = canvas[0];
        int height = canvas[1];
        if (width < 1 || height < 1) throw new IOException("GIF 逻辑屏幕尺寸异常: " + width + "x" + height);
        if (width > GifBudget.MAX_DIMENSION || height > GifBudget.MAX_DIMENSION) {
            throw new IOException("GIF 尺寸过大: " + width + "x" + height + "（单边上限 " + GifBudget.MAX_DIMENSION + "）");
        }

        int scale = GifBudget.scaleFor(width, height, frameCount, budgetBytes);
        if (GifBudget.estimateBytes(width, height, frameCount, scale) > budgetBytesOrDefault(budgetBytes)) {
            throw new IOException("GIF 太大: " + width + "x" + height + " × " + frameCount + " 帧，即使缩小 "
                    + scale + " 倍仍超出内存预算（" + (budgetBytesOrDefault(budgetBytes) / (1024 * 1024))
                    + "MB）；请缩短帧数或减小尺寸");
        }

        int[] buffer = GifComposer.newCanvas(width, height);
        int[] snapshot = null;
        int[][] frames = new int[frameCount][];
        int[] delays = new int[frameCount];
        int previousDisposal = DISPOSAL_KEEP;
        int[] previousRect = null;

        for (int i = 0; i < frameCount; i++) {
            FrameMeta meta = FrameMeta.read(reader.getImageMetadata(i));
            // 上一帧的 disposal 在画本帧**之前**生效（GIF 规范的处理顺序）
            if (i > 0) applyDisposal(previousDisposal, previousRect, buffer, snapshot, width, height);
            if (meta.disposal == DISPOSAL_RESTORE_PREVIOUS) snapshot = buffer.clone();

            BufferedImage image = reader.read(i);
            if (image == null) throw new IOException("第 " + i + " 帧读取失败: " + file);
            int fw = image.getWidth();
            int fh = image.getHeight();
            int px = meta.left;
            int py = meta.top;
            if (fw == width && fh == height) {
                // 有的读取器直接返回整幅画布：此时帧坐标就没有意义了，必须忽略，
                // 否则整幅画会被再平移一次（表现为动画一路往右下角跑）
                px = 0;
                py = 0;
            }
            int[] pixels = image.getRGB(0, 0, fw, fh, null, 0, fw);
            GifComposer.drawFrame(buffer, width, height, pixels, px, py, fw, fh);

            frames[i] = scale == 1 ? buffer.clone() : GifComposer.downscale(buffer, width, height, scale);
            delays[i] = meta.delayMs();
            previousDisposal = meta.disposal;
            previousRect = new int[] {px, py, fw, fh};
        }

        return new GifFrames(GifBudget.scaled(width, scale), GifBudget.scaled(height, scale), delays, frames, scale);
    }

    /** 逻辑屏幕尺寸：优先取流元数据，取不到再退回第一帧的声明尺寸。 */
    private static int[] canvasSize(ImageReader reader, int frameCount) throws IOException {
        try {
            IIOMetadata stream = reader.getStreamMetadata();
            if (stream != null) {
                IIOMetadataNode root = (IIOMetadataNode) stream.getAsTree(GIF_STREAM_FORMAT);
                int w = attr(root, "logicalScreenWidth", -1);
                int h = attr(root, "logicalScreenHeight", -1);
                if (w > 0 && h > 0) return new int[] {w, h};
            }
        } catch (Exception e) {
            // 流元数据读不到不是错误：下面还有两条回退路径
        }
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        if (w > 0 && h > 0) return new int[] {w, h};
        // 最后一招：用最大帧的声明矩形兜住
        int maxW = 0;
        int maxH = 0;
        for (int i = 0; i < frameCount; i++) {
            maxW = Math.max(maxW, reader.getWidth(i));
            maxH = Math.max(maxH, reader.getHeight(i));
        }
        return new int[] {maxW, maxH};
    }

    private static void applyDisposal(int disposal, int[] rect, int[] buffer, int[] snapshot, int width, int height) {
        if (rect == null) return;
        switch (disposal) {
            case DISPOSAL_BACKGROUND -> GifComposer.clearRect(
                    buffer, width, height, rect[0], rect[1], rect[2], rect[3]);
            case DISPOSAL_RESTORE_PREVIOUS -> {
                if (snapshot != null && snapshot.length == buffer.length) {
                    System.arraycopy(snapshot, 0, buffer, 0, buffer.length);
                }
            }
            default -> {
                // KEEP（不处理）：画布上保留这一帧的内容，下一帧直接叠上去
            }
        }
    }

    private static long budgetBytesOrDefault(long budgetBytes) {
        return budgetBytes <= 0 ? GifBudget.DEFAULT_BYTES : budgetBytes;
    }

    // ------------------------------------------------------------------
    // 帧元数据
    // ------------------------------------------------------------------

    /** GIF 的 disposal 取值（0/1 等价：都表示「不处理」）。 */
    private static final int DISPOSAL_KEEP = 1;

    private static final int DISPOSAL_BACKGROUND = 2;
    private static final int DISPOSAL_RESTORE_PREVIOUS = 3;

    /**
     * 一帧的元数据。
     *
     * <p>注意 {@code disposalMethod} 在 JDK 的元数据树里既可能是数字（"2"）也可能是名字
     * （"restoreToBackgroundColor"）——两种写法都要认，否则一半的 GIF 会被当成「不处理」而糊成一片。
     */
    private record FrameMeta(int left, int top, int delayMs, int disposal) {

        static FrameMeta read(IIOMetadata metadata) {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(GIF_IMAGE_FORMAT);
            int left = attr(root, "imageLeftPosition", 0);
            int top = attr(root, "imageTopPosition", 0);
            IIOMetadataNode gce = find(root, "GraphicControlExtension");
            int delay = normalizeDelay(gce == null ? 0 : attr(gce, "delayTime", 0));
            int disposal = parseDisposal(gce);
            return new FrameMeta(Math.max(0, left), Math.max(0, top), delay, disposal);
        }

        /** delayTime 的单位是 1/100 秒。 */
        private static int normalizeDelay(int centiseconds) {
            long ms = centiseconds * 10L;
            if (ms < MIN_FRAME_MS) return DEFAULT_FRAME_MS;
            return (int) Math.min(ms, MAX_FRAME_MS);
        }

        private static int parseDisposal(IIOMetadataNode gce) {
            if (gce == null || gce.getAttribute("disposalMethod").isEmpty()) return DISPOSAL_KEEP;
            String raw = gce.getAttribute("disposalMethod").trim();
            try {
                int value = Integer.parseInt(raw);
                // 0 与 1 都是「什么都不做」
                return value <= DISPOSAL_KEEP ? DISPOSAL_KEEP : value;
            } catch (NumberFormatException e) {
                return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
                    case "restoretobackgroundcolor", "restorebackground", "background" -> DISPOSAL_BACKGROUND;
                    case "restoretoprevious", "previous" -> DISPOSAL_RESTORE_PREVIOUS;
                    default -> DISPOSAL_KEEP;
                };
            }
        }
    }

    /** 取节点上的整数属性（缺失/不可解析时用默认值）。 */
    private static int attr(IIOMetadataNode node, String name, int def) {
        if (node == null) return def;
        String raw = node.getAttribute(name);
        if (raw == null || raw.isEmpty()) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 深度优先查找第一个同名子节点（GIF 里 GraphicControlExtension 的位置不固定）。 */
    private static IIOMetadataNode find(IIOMetadataNode node, String name) {
        if (node == null) return null;
        for (int i = 0; i < node.getLength(); i++) {
            org.w3c.dom.Node child = node.item(i);
            if (!(child instanceof IIOMetadataNode metaChild)) continue;
            if (name.equalsIgnoreCase(metaChild.getNodeName())) return metaChild;
            IIOMetadataNode deeper = find(metaChild, name);
            if (deeper != null) return deeper;
        }
        return null;
    }
}
