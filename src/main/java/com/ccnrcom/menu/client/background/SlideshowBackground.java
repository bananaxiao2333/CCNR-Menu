/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.SlideSpec;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.ccnrcom.menu.ui.SlideTimeline;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 多图轮播背景（{@code background.type = slideshow}）。
 *
 * <p>每张图按 {@link SlideSpec} 的节奏出现：停留一段时间，在末尾与下一张**交叉淡入**，
 * 期间这张图自己在缓慢推近并向右偏移（{@link MenuGeometry#kenBurns}）。
 * 时刻表本身在纯类 {@link SlideTimeline} 里，本类只负责「按时刻表把像素画对」。
 *
 * <p>三条关键设计：
 *
 * <ol>
 *   <li><b>只留当前与下一张两张贴图</b>，其它一律释放。6 张 1920×1080 的贴图是 50MB 显存，
 *       而屏幕上任何时刻最多只看得见 2 张。释放时机是「切换完成之后」，
 *       也就是新的当前/下一张都确定下来的那一刻。</li>
 *   <li><b>下一张提前一个停留周期就开始异步解码</b>。1920 宽的 JPEG 解码+上传约几十毫秒，
 *       放在切换的那一帧上就是一次肉眼可见的顿。提前 9 秒开始，工作线程绰绰有余
 *       （与 {@code GifBackground} 同一套做法：工作线程只做纯计算，
 *       建贴图与上传经 {@code Minecraft.execute} 回到渲染线程）。</li>
 *   <li><b>没就绪时画原版全景图</b>而不是黑屏：第一张是同步加载的（在构造时），
 *       所以这条兜底只在「图片全挂了」的时候才会被看到。</li>
 * </ol>
 *
 * <p>**对称清理**：{@link #close()} 释放所有贴图；工作线程解完发现 {@code closed} 就直接丢结果。
 */
public final class SlideshowBackground implements BackgroundRenderer {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private final BackgroundSpec spec;
    private final SlideSpec slide;
    private final SlideTimeline timeline;
    private final List<String> slides;
    private final VanillaBackground fallback = new VanillaBackground();

    /** 已就绪的贴图（下标 → 贴图）。只在渲染线程上读写。 */
    private final Map<Integer, FileTexture> textures = new HashMap<>();

    /** 正在工作线程上解码的下标，避免同一张被重复解码。 */
    private final java.util.Set<Integer> pending = new java.util.HashSet<>();

    /** 已经明确失败的素材（解码不了/文件不在），不再重试、不再刷屏。 */
    private final java.util.Set<Integer> failed = new java.util.HashSet<>();

    private final long startMs = Util.getMillis();

    private boolean closed;

    public SlideshowBackground(BackgroundSpec spec) {
        this.spec = spec;
        this.slide = spec.slide();
        this.slides = spec.slides();
        this.timeline = new SlideTimeline(slides.size(), slide.holdMs(), slide.fadeMs(), slide.loop());
        // 第一张同步加载：它在第一帧就要上屏，异步的话开场会先闪一下原版全景图
        // （这一步的几十毫秒被 MenuScreen 的淡入遮罩盖住，看不见）
        loadNow(0);
        LOGGER.info(
                "[CCNR-Menu] 轮播背景就绪: {} 张，每张 {}ms（末尾 {}ms 交叉淡入），放大 {}%，横向偏移 {}%{}",
                slides.size(),
                slide.holdMs(),
                slide.fadeMs(),
                Math.round(slide.zoom() * 100f),
                Math.round(slide.panX() * 100f),
                slide.loop() ? "" : "，播完停在最后一张");
    }

    @Override
    public void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha) {
        if (slides.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            fallback.render(gfx, screenWidth, screenHeight, partialTick, alpha);
            return;
        }

        SlideTimeline.State state = timeline.stateAt(Util.getMillis() - startMs);
        FileTexture current = textures.get(state.index());
        if (current == null) {
            // 当前这张还没好：先让原版全景图顶着，等它好了自然切过来
            fallback.render(gfx, screenWidth, screenHeight, partialTick, alpha);
            keep(state.index(), nextOf(state.index()));
            return;
        }

        draw(gfx, current, state.progress(), screenWidth, screenHeight, alpha);

        // 交叉淡入：把下一张按 fade 的不透明度**盖**在当前这张上——结果正好是两者的线性插值
        if (state.crossFading()) {
            FileTexture incoming = textures.get(state.incoming());
            if (incoming != null) {
                draw(gfx, incoming, state.incomingProgress(), screenWidth, screenHeight, alpha * state.fade());
            }
        }
        keep(state.index(), nextOf(state.index()));
    }

    /**
     * 当前这张之后会轮到哪一张（用来**提前**解码）。
     *
     * <p>注意它必须按「当前」算，而不能用 {@link SlideTimeline.State#incoming()}：
     * 后者只在淡入窗口里才有值，等到那时候才开始解码，那几十毫秒正好落在切图那一帧上——
     * 而那正是最不该卡的一帧。提前一整个停留周期（默认 9 秒）开始，工作线程绰绰有余。
     */
    private int nextOf(int index) {
        if (index < 0 || slides.size() < 2) return -1;
        int next = index + 1;
        if (next >= slides.size()) {
            // 不循环时最后一张后面没有下一张，就别白解码第一张
            return slide.loop() ? 0 : -1;
        }
        return next;
    }

    /** 画一张：先按 fit 算目标矩形，再按这张图自己的进度做推近与偏移。 */
    private void draw(
            GuiGraphics gfx, FileTexture texture, float progress, int screenWidth, int screenHeight, float alpha) {
        MenuGeometry.Rect src = new MenuGeometry.Rect(0, 0, texture.width(), texture.height());
        MenuGeometry.Rect base =
                MenuGeometry.fit(spec.fit(), texture.width(), texture.height(), screenWidth, screenHeight);
        MenuGeometry.Rect dst = MenuGeometry.kenBurns(
                base, slide.zoom(), slide.panX(), slide.panY(), progress, screenWidth, screenHeight);
        TextureDraw.drawAt(
                gfx,
                texture.location(),
                src,
                dst,
                texture.width(),
                texture.height(),
                spec.tint(),
                spec.opacity() * alpha);
    }

    /**
     * 保证这两张的贴图在位，并释放其它所有贴图。
     *
     * <p>{@code next} 在这里被**提前**解码（提前一整个停留周期），这样真要淡入时它早就好了。
     */
    private void keep(int current, int next) {
        if (next >= 0) loadAsync(next);
        textures.entrySet().removeIf(entry -> {
            if (entry.getKey() == current || entry.getKey() == next) return false;
            entry.getValue().close();
            return true;
        });
    }

    /** 同步加载（渲染线程）：构造时给第一张用。 */
    private void loadNow(int index) {
        if (index < 0 || index >= slides.size() || textures.containsKey(index) || failed.contains(index)) return;
        try {
            Path file = resolve(index);
            if (file == null) return;
            textures.put(index, FileTexture.load(file, FileTexture.keyFor(file, MenuConfigIO.lastModified(file))));
        } catch (Exception e) {
            failed.add(index);
            LOGGER.warn("[CCNR-Menu] 轮播素材加载失败，已跳过这一张: {} —— {}", slides.get(index), e.toString());
        }
    }

    /**
     * 异步加载：工作线程只做**纯计算**（读文件 + 解码成 {@code NativeImage}），
     * 建贴图与上传经 {@code Minecraft.execute} 回到渲染线程（GL 调用只能在渲染线程）。
     */
    private void loadAsync(int index) {
        if (index < 0 || index >= slides.size() || pending.contains(index) || failed.contains(index)) return;
        if (textures.containsKey(index)) return;
        Path file = resolve(index);
        if (file == null) return;

        pending.add(index);
        String key = FileTexture.keyFor(file, MenuConfigIO.lastModified(file));
        Thread worker = new Thread(
                () -> {
                    try {
                        handoffDecoded(index, FileTexture.decodeFile(file), key);
                    } catch (Throwable t) {
                        handoff(() -> onFailed(index, t));
                    }
                },
                "ccnr-menu-slide");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 解码结果带着 {@code NativeImage} 一起交回渲染线程。
     *
     * <p>单独一层是因为「游戏正在退出」时主线程执行器可能已经停了，那时 {@code execute} 会抛异常，
     * 而它恰好发生在 catch 块里——再抛一次就成了一条没人处理的线程异常。
     * 此时**必须把解出来的像素收尾**：这条路径上贴图还没建，没人会替它释放。
     */
    private void handoffDecoded(int index, NativeImage image, String key) {
        try {
            Minecraft.getInstance().execute(() -> onDecoded(index, image, key));
        } catch (Throwable t) {
            image.close();
            pending.remove(index);
            LOGGER.debug("[CCNR-Menu] 轮播解码结果无法交回渲染线程（游戏可能正在退出），已丢弃: {}", t.toString());
        }
    }

    /** 渲染线程：解码完成，建贴图并上传。 */
    private void onDecoded(int index, NativeImage image, String key) {
        pending.remove(index);
        if (closed) {
            image.close();
            return;
        }
        try {
            textures.put(index, FileTexture.fromImage(image, key));
        } catch (Exception e) {
            // fromImage 失败时像素的所有权还在我们手上
            image.close();
            onFailed(index, e);
        }
    }

    private void onFailed(int index, Throwable t) {
        pending.remove(index);
        if (failed.add(index)) {
            LOGGER.warn(
                    "[CCNR-Menu] 轮播素材加载失败，已跳过这一张: {} —— {}",
                    index < slides.size() ? slides.get(index) : String.valueOf(index),
                    t.toString());
        }
    }

    /** 解析出绝对路径；文件不在/路径越界时记一次警告并返回 {@code null}。 */
    private Path resolve(int index) {
        String name = slides.get(index);
        Path file;
        try {
            file = MenuConfigIO.resolveAsset(name);
        } catch (IllegalArgumentException e) {
            onFailed(index, e);
            return null;
        }
        if (!Files.isRegularFile(file)) {
            onFailed(index, new java.io.FileNotFoundException("文件不存在: " + file));
            return null;
        }
        return file;
    }

    /**
     * 把结果交回渲染线程。
     *
     * <p>单独包一层是因为「游戏正在退出」时主线程执行器可能已经停了：那时 {@code execute} 会抛异常，
     * 而它恰好发生在 catch 块里。此时**丢弃结果是正确的**——这条路径上一个资源都还没创建。
     */
    private void handoff(Runnable task) {
        try {
            Minecraft.getInstance().execute(task);
        } catch (Throwable t) {
            LOGGER.debug("[CCNR-Menu] 轮播解码结果无法交回渲染线程（游戏可能正在退出），已丢弃: {}", t.toString());
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (FileTexture texture : textures.values()) texture.close();
        textures.clear();
        pending.clear();
    }
}
