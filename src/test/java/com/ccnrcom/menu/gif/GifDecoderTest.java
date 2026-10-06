/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.gif;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

/**
 * GIF 解码端到端门禁（用 JDK 自带的 GIF 编码器造样本，再解回来）。
 *
 * <p>为什么这条测试必须存在：GIF 解码是本模组里**唯一无法靠肉眼快速验证**的部分——
 * 背景图上少一块、颜色错位、帧时长不对，都要等玩家实机看到才发现，而项目禁止起客户端。
 * 于是把「写一张 GIF → 解出来 → 断言帧数/时长/像素」做成了自动化测试。
 *
 * <p>组合逻辑（部分帧、disposal、透明）由 {@link GifComposerTest} 单独覆盖：
 * JDK 的 GIF 编码器只会写整幅帧，造不出部分帧的样本，硬凑不如分两层测。
 */
class GifDecoderTest {

    /** 生成一张带逐帧时长的多帧 GIF。 */
    private static Path writeGif(Path dir, int[][] colors, int[] delaysCentiseconds, int size) throws IOException {
        Path file = dir.resolve("sample.gif");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < colors.length; i++) {
                BufferedImage frame = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        frame.setRGB(x, y, colors[i][0]);
                    }
                }
                writer.writeToSequence(
                        new IIOImage(frame, null, withDelay(writer, param, delaysCentiseconds[i])), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return file;
    }

    /** 设置一帧的 delayTime（单位 1/100 秒）——GIF 元数据是棵 DOM 树，只能这么改。 */
    private static IIOMetadata withDelay(ImageWriter writer, ImageWriteParam param, int delayCentiseconds)
            throws IOException {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, param);
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode gce = find(root, "GraphicControlExtension");
        if (gce == null) {
            gce = new IIOMetadataNode("GraphicControlExtension");
            root.appendChild(gce);
        }
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(delayCentiseconds));
        gce.setAttribute("transparentColorIndex", "0");
        metadata.setFromTree(format, root);
        return metadata;
    }

    private static IIOMetadataNode find(IIOMetadataNode node, String name) {
        for (int i = 0; i < node.getLength(); i++) {
            Node child = node.item(i);
            if (!(child instanceof IIOMetadataNode meta)) continue;
            if (name.equalsIgnoreCase(meta.getNodeName())) return meta;
            IIOMetadataNode deeper = find(meta, name);
            if (deeper != null) return deeper;
        }
        return null;
    }

    @Test
    @DisplayName("两帧 GIF：帧数、每帧时长、尺寸、像素都对")
    void decodesTwoFrames(@TempDir Path dir) throws IOException {
        Path file = writeGif(dir, new int[][] {{0xFFFF0000}, {0xFF0000FF}}, new int[] {50, 10}, 4);

        GifFrames frames = GifDecoder.decode(file);

        assertEquals(2, frames.frameCount());
        assertEquals(4, frames.width());
        assertEquals(4, frames.height());
        assertEquals(1, frames.scale(), "小图不该被缩小");
        assertArrayEquals(new int[] {500, 100}, frames.delaysMs(), "50cs=500ms、10cs=100ms");
        assertEquals(600, frames.totalMs());
        assertEquals(300, frames.averageDelayMs());
        assertEquals(0xFFFF0000, frames.frames()[0][0], "第一帧是红色");
        assertEquals(0xFF0000FF, frames.frames()[1][0], "第二帧是蓝色");
    }

    @Test
    @DisplayName("过短的帧时长按 100ms 处理（大量导出工具会写 0）")
    void tooShortDelaysAreNormalized(@TempDir Path dir) throws IOException {
        Path file = writeGif(dir, new int[][] {{0xFF00FF00}, {0xFFFFFF00}}, new int[] {0, 1}, 4);

        GifFrames frames = GifDecoder.decode(file);

        assertArrayEquals(
                new int[] {GifDecoder.DEFAULT_FRAME_MS, GifDecoder.DEFAULT_FRAME_MS},
                frames.delaysMs(),
                "0 与 1cs 都应当被当成「越快越好」而规范成 100ms");
    }

    @Test
    @DisplayName("超出内存预算时按整倍缩小边长（帧数与时长不变）")
    void downscalesWhenOverBudget(@TempDir Path dir) throws IOException {
        Path file = writeGif(dir, new int[][] {{0xFF112233}, {0xFF445566}}, new int[] {20, 20}, 64);

        GifFrames frames = GifDecoder.decode(file, 4096);

        assertTrue(frames.scale() > 1, "预算只有 4KB，必须缩小");
        assertEquals(64 / frames.scale(), frames.width());
        assertEquals(2, frames.frameCount(), "缩小不能影响帧数");
        assertArrayEquals(new int[] {200, 200}, frames.delaysMs(), "缩小不能影响时长");
        assertTrue(GifBudget.estimateBytes(64, 64, 2, frames.scale()) <= 4096);
    }

    @Test
    @DisplayName("非 GIF 的图片（PNG）被拒绝，且消息告诉作者改用 image 背景")
    void rejectsNonGif(@TempDir Path dir) throws IOException {
        Path png = dir.resolve("static.png");
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", png.toFile());

        IOException error = assertThrows(IOException.class, () -> GifDecoder.decode(png));
        assertTrue(error.getMessage().contains("不是动画 GIF"), error.getMessage());
        assertTrue(error.getMessage().contains("image"), "消息里要给出可行的替代方案: " + error.getMessage());
    }

    @Test
    @DisplayName("根本不是图片的文件被拒绝，且不抛运行时异常")
    void rejectsGarbage(@TempDir Path dir) throws IOException {
        Path junk = dir.resolve("notes.txt");
        Files.writeString(junk, "这不是图片");

        IOException error = assertThrows(IOException.class, () -> GifDecoder.decode(junk));
        assertTrue(error.getMessage().contains("不是可识别的图片格式"), error.getMessage());
    }

    @Test
    @DisplayName("文件不存在时报错（不静默返回空）")
    void rejectsMissingFile(@TempDir Path dir) {
        assertThrows(IOException.class, () -> GifDecoder.decode(dir.resolve("nope.gif")));
    }
}
