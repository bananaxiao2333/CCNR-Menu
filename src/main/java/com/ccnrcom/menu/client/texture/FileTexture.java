/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * 把一个**游戏目录外的图片文件**变成可绘制的贴图。
 *
 * <p>为什么不能直接用 {@code blit(ResourceLocation, ...)}：那条路只认资源包里的资源
 * （{@code assets/<命名空间>/textures/...}），而玩家的背景图放在 {@code config/ccnr_menu/} 里。
 * 所以必须自己解码成像素、塞进 {@link DynamicTexture}、注册进 {@code TextureManager}，
 * 之后才能用资源位置绘制。这与 CCNR-PM 处理服务端下发头像贴图的做法是同一套路（那边是 base64，
 * 这边是文件）。
 *
 * <p>**对称清理**：注册进 TextureManager 的贴图是 GPU 资源。{@link #close()} 用
 * {@code TextureManager.release} 而非直接 {@code close()}——两者都会释放 GL 纹理 id，
 * 但只有前者会把名字从表里摘掉；重复调 release 由 MC 内部的 safeClose 保证安全。
 *
 * <p>线程：**只能在渲染线程上创建**（构造 DynamicTexture 会做 GL 上传）。GIF 那条路因此
 * 把解码放在工作线程、把建贴图放回渲染线程；轮播背景同理，用 {@link #decodeFile} +
 * {@link #fromImage} 把这两步拆开。
 */
public final class FileTexture implements AutoCloseable {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单边像素上限（与 GIF 的预算上限一致）。 */
    public static final int MAX_DIMENSION = 4096;

    /** PNG 文件头，用于走 NativeImage 的快路径。 */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    private final ResourceLocation location;
    private final int width;
    private final int height;
    private final DynamicTexture texture;
    private boolean closed;

    private FileTexture(ResourceLocation location, int width, int height, DynamicTexture texture) {
        this.location = location;
        this.width = width;
        this.height = height;
        this.texture = texture;
    }

    /**
     * 从文件加载并注册一张贴图。
     *
     * @param file 图片文件（PNG 走原生解码；其余格式走 ImageIO，含 GIF 的第一帧）
     * @param key 贴图名（同文件同修改时间应给出同一个 key，避免重复加载）
     * @throws IOException 文件不可读、不是图片、尺寸超限
     */
    public static FileTexture load(Path file, String key) throws IOException {
        NativeImage image = decodeFile(file);
        try {
            return fromImage(image, key);
        } catch (Exception e) {
            // fromImage 失败时所有权仍在我们手上，这里必须收尾（否则解码出来的像素泄漏）
            image.close();
            throw e;
        }
    }

    /**
     * 把文件解码成 RGBA 像素。
     *
     * <p>**不碰 GL、不读游戏状态**，所以可以在工作线程上调用——这是轮播背景能「提前一张异步解码」
     * 的前提（解码 1920 宽的 JPEG 要几十毫秒，放在渲染线程上就是一次可见的顿）。
     */
    public static NativeImage decodeFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) throw new IOException("空文件: " + file);
        return decode(bytes, file);
    }

    /**
     * 已解码的像素 → 注册好的贴图。**必须在渲染线程上调用**（构造 {@link DynamicTexture} 会走 GL）。
     *
     * <p>所有权约定：**成功时** {@code image} 归贴图管（{@link #close()} 会释放它），
     * **失败时**仍归调用方，由调用方负责 {@code image.close()}。写成这样是为了让失败路径只有一处收尾，
     * 不会出现「两处都关一次」或「两处都没关」。
     */
    public static FileTexture fromImage(NativeImage image, String key) throws IOException {
        checkDimensions(image.getWidth(), image.getHeight(), Path.of(key));
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = new ResourceLocation("ccnr_menu", "bg/" + sanitize(key));
        try {
            Minecraft.getInstance().getTextureManager().register(location, texture);
        } catch (RuntimeException e) {
            // 注册失败：贴图对象没进 TextureManager，谁都不会替它收尾
            texture.close();
            throw e;
        }
        return new FileTexture(location, image.getWidth(), image.getHeight(), texture);
    }

    /**
     * 新建一张空白贴图（GIF 逐帧写入用）。
     *
     * <p>为什么 GIF 不「每帧新建一张贴图」：贴图是 GPU 资源，每帧创建/销毁会让显存分配器
     * 每 100ms 抖一次（症状是动画播放时帧生成时间出现规律尖峰）。正确做法是**一张贴图 + 每帧重传像素**。
     *
     * @throws IOException 尺寸非法
     */
    public static FileTexture blank(int width, int height, String key) throws IOException {
        checkDimensions(width, height, Path.of(key));
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = new ResourceLocation("ccnr_menu", "bg/" + sanitize(key));
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return new FileTexture(location, width, height, texture);
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
    }

    /** 解码成 RGBA 的 NativeImage。 */
    private static NativeImage decode(byte[] bytes, Path file) throws IOException {
        if (isPng(bytes)) {
            // NativeImage.read 只认 PNG，且自带尺寸校验（不需要额外的解压炸弹防护）
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) throw new IOException("PNG 解码失败: " + file);
            checkDimensions(image.getWidth(), image.getHeight(), file);
            return image;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (in == null) throw new IOException("无法读取: " + file);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("不是可识别的图片格式: " + file);
            ImageReader reader = readers.next();
            try {
                // 先只读文件头拿尺寸：超大图应当在分配 BufferedImage 之前就被拒绝
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                checkDimensions(w, h, file);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IOException("图片解码失败: " + file);
                return fromBufferedImage(decoded);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void checkDimensions(int w, int h, Path file) throws IOException {
        if (w < 1 || h < 1) throw new IOException("图片尺寸异常: " + w + "x" + h + " (" + file + ")");
        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            throw new IOException("图片过大: " + w + "x" + h + "（单边上限 " + MAX_DIMENSION + "）: " + file);
        }
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_MAGIC.length) return false;
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) return false;
        }
        return true;
    }

    /**
     * AWT 图 → NativeImage。
     *
     * <p>Byte 序必须交换：{@code BufferedImage.getRGB} 给出的是 {@code 0xAARRGGBB}，
     * 而 NativeImage 的 RGBA 内存布局对应的打包 int 是 {@code 0xAABBGGRR}。
     * 漏掉这一步的症状是**红蓝互换**（JPEG 的天空变成橙红色），而 PNG 因为走原生解码不受影响——
     * 于是它只会在某些格式的图上出现，很难联想到编码顺序。
     */
    public static NativeImage fromBufferedImage(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        return fromArgb(source.getRGB(0, 0, w, h, null, 0, w), w, h);
    }

    /**
     * {@code 0xAARRGGBB} 的像素数组 → NativeImage（GIF 帧走这条路）。
     *
     * <p>与 {@link #fromBufferedImage} 共用同一处字节序交换：这个转换写错一次就会让
     * **所有** 非 PNG 素材红蓝互换，只写一份才不会被漏掉。
     */
    public static NativeImage fromArgb(int[] argb, int width, int height) {
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int c = argb[row + x];
                out.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c & 0x00FF0000) >> 16) | ((c & 0x000000FF) << 16));
            }
        }
        return out;
    }

    public ResourceLocation location() {
        return location;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** 贴图内的像素数据（GIF 逐帧上传时直接往这里写，避免每帧新建贴图）。 */
    public NativeImage pixels() {
        return texture.getPixels();
    }

    /** 把当前像素内容上传到 GPU。 */
    public void upload() {
        texture.upload();
    }

    /** 释放 GPU 资源；重复调用安全。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            Minecraft.getInstance().getTextureManager().release(location);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 释放背景贴图失败: {}", e.toString());
        }
    }

    /**
     * 生成一个「同文件同修改时间 → 同名字」的贴图名。
     *
     * <p>名字里带上路径与修改时间：玩家换了背景图之后，新贴图会得到新名字，
     * 不会撞上仍在缓存里的旧贴图（症状会是「改了图但界面没变」）。
     */
    public static String keyFor(Path file, long modifiedAt) {
        return file.getFileName() + "-" + Integer.toHexString((file.toAbsolutePath() + "@" + modifiedAt).hashCode());
    }

    private static String sanitize(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return sb.toString();
    }
}
