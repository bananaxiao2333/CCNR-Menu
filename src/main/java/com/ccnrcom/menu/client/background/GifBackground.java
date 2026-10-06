/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.gif.GifBudget;
import com.ccnrcom.menu.gif.GifDecoder;
import com.ccnrcom.menu.gif.GifFrames;
import com.ccnrcom.menu.ui.FrameTimeline;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 动画 GIF 背景（{@code background.type = gif}）：**导入即用**的那条路。
 *
 * <p>三条关键设计：
 *
 * <ol>
 *   <li><b>解码在工作线程上</b>（{@code ccnr-menu-gif}）：一张 1080p、几十帧的 GIF 解码要几百毫秒，
 *       放在渲染线程上就是主菜单卡住一下。工作线程只做纯计算（{@link GifDecoder} 不碰 GL、
 *       不读游戏状态），解出来的 {@link GifFrames} 是不可变数据。</li>
 *   <li><b>建贴图与上传都回渲染线程</b>：GL 调用必须在渲染线程，所以工作线程解完后
 *       通过 {@code Minecraft.execute(...)} 回到主线程再建贴图。</li>
 *   <li><b>逐帧懒转换</b>：不一次性把 N 帧都转成 {@link NativeImage}（那会在打开菜单的瞬间
 *       做 N×宽×高的像素搬运），而是**用到哪帧转哪帧**。一个 10fps 的动画在头一秒内就把所有帧
 *       转完了，而单帧的转换代价只有约 0.1~1ms，肉眼看不出卡顿。</li>
 * </ol>
 *
 * <p>未就绪时画原版全景图（{@link #fallback}），而不是黑屏：黑屏会让人以为菜单坏了。
 * 解码失败也停在原版全景图上，并打一条带原因的日志。
 *
 * <p>**对称清理**：屏幕关闭时 {@link #close()} 会丢弃解码结果并释放贴图；
 * 若此时工作线程还在跑，回调看到 {@code closed} 就直接丢掉结果，不会重建任何资源。
 * 这也是「回到主菜单会重新解码一次」的原因——为了不让几十 MB 的帧数据跟着玩家进游戏，
 * 这个代价值得。
 */
public final class GifBackground implements BackgroundRenderer {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private final BackgroundSpec spec;
    private final Path file;
    private final VanillaBackground fallback = new VanillaBackground();

    /**
     * 解码结果。**只在渲染线程上读写**：工作线程算完之后经 {@link #handoff} 交回渲染线程，
     * 所以这里不需要任何同步（工作线程从头到尾只碰自己的局部变量）。
     */
    private GifFrames decoded;

    private String failure;

    private FrameTimeline timeline;
    private FileTexture texture;
    private NativeImage[] frameCache;
    private int uploadedFrame = -1;
    private long startMs;
    private boolean closed;

    public GifBackground(BackgroundSpec spec, Path file, String textureKey) {
        this.spec = spec;
        this.file = file;
        startDecode(textureKey);
    }

    private void startDecode(String textureKey) {
        Thread worker = new Thread(
                () -> {
                    try {
                        GifFrames frames = GifDecoder.decode(file, GifBudget.DEFAULT_BYTES);
                        handoff(() -> onDecoded(frames, textureKey));
                    } catch (Throwable t) {
                        LOGGER.warn("[CCNR-Menu] GIF 背景解码失败: {} —— {}", file, t.toString());
                        handoff(() -> failure = t.toString());
                    }
                },
                "ccnr-menu-gif");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 把结果交回渲染线程。
     *
     * <p>单独包一层是因为「游戏正在退出」时主线程执行器可能已经停了：那时 {@code execute} 会抛异常，
     * 而它恰好发生在 catch 块里——再抛一次就成了一条没人处理的线程异常。
     * 此时**丢弃结果是正确的**：这条路径上一个资源都还没创建。
     */
    private void handoff(Runnable task) {
        try {
            Minecraft.getInstance().execute(task);
        } catch (Throwable t) {
            LOGGER.debug("[CCNR-Menu] GIF 解码结果无法交回渲染线程（游戏可能正在退出），已丢弃: {}", t.toString());
        }
    }

    /** 渲染线程：解码完成，建立贴图与时间轴。 */
    private void onDecoded(GifFrames frames, String textureKey) {
        if (closed) {
            // 屏幕已经关掉了：结果直接丢弃（帧数据是纯堆内存，GC 会回收）
            return;
        }
        try {
            this.texture = FileTexture.blank(frames.width(), frames.height(), textureKey);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] GIF 背景贴图创建失败: {} —— {}", file, e.toString());
            this.failure = e.toString();
            return;
        }
        this.frameCache = new NativeImage[frames.frameCount()];
        this.timeline = new FrameTimeline(frames.delaysMs(), spec.sheet().loop());
        this.decoded = frames;
        this.startMs = Util.getMillis();
        LOGGER.info(
                "[CCNR-Menu] GIF 背景就绪: {}x{}，{} 帧，平均每帧 {}ms{}",
                frames.width(),
                frames.height(),
                frames.frameCount(),
                frames.averageDelayMs(),
                frames.scale() > 1 ? "（已按 1/" + frames.scale() + " 缩小以适配内存预算）" : "");
    }

    @Override
    public boolean ready() {
        return texture != null && timeline != null;
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        GifFrames frames = decoded;
        FrameTimeline localTimeline = timeline;
        FileTexture localTexture = texture;
        if (frames == null || localTimeline == null || localTexture == null) {
            fallback.render(gfx, screenWidth, screenHeight, partialTick, alpha);
            return;
        }
        int frame = localTimeline.frameAt(Util.getMillis() - startMs);
        if (frame != uploadedFrame) {
            uploadFrame(frames, frame);
            uploadedFrame = frame;
        }
        TextureDraw.drawFitted(
                gfx,
                localTexture.location(),
                localTexture.width(),
                localTexture.height(),
                new MenuGeometry.Rect(0, 0, localTexture.width(), localTexture.height()),
                spec.fit(),
                screenWidth,
                screenHeight,
                spec.tint(),
                spec.opacity() * alpha);
    }

    /** 把第 {@code frame} 帧写进贴图并上传（同样的帧不重复上传）。 */
    private void uploadFrame(GifFrames frames, int frame) {
        NativeImage image = frameCache[frame];
        if (image == null) {
            image = FileTexture.fromArgb(frames.frames()[frame], frames.width(), frames.height());
            frameCache[frame] = image;
        }
        texture.pixels().copyFrom(image);
        texture.upload();
    }

    /** 解码失败的原因（诊断输出用）；没失败返回 {@code null}。 */
    public String failureReason() {
        return failure;
    }

    /** 素材文件是否还在（诊断输出用）。 */
    public boolean fileExists() {
        return Files.isRegularFile(file);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        decoded = null;
        timeline = null;
        if (frameCache != null) {
            for (NativeImage image : frameCache) {
                if (image != null) image.close();
            }
            frameCache = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
        uploadedFrame = -1;
    }
}
