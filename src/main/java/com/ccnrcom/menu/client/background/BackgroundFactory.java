/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.MenuConfigIO;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 按配置造背景绘制器（唯一入口）。
 *
 * <p>本类是**降级的唯一收口处**：素材缺失、路径越界、格式不对、图片过大、GIF 解码失败——
 * 一律落到 {@link VanillaBackground} 并打一条能定位原因的日志（消息里带上文件名与原因，
 * 而不是笼统的「加载失败」）。菜单本身永远照常可用。
 *
 * <p>所有权：返回的绘制器由调用方（主菜单屏幕）负责 {@code close()}。
 */
public final class BackgroundFactory {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private BackgroundFactory() {}

    /** 造一个背景绘制器。任何失败都返回可用的原版全景图背景。 */
    public static BackgroundRenderer create(BackgroundSpec spec) {
        if (spec == null) return new VanillaBackground();
        return switch (spec.kind()) {
            case VANILLA -> new VanillaBackground();
            case NONE -> new ColorBackground(0xFF000000);
            case COLOR -> new ColorBackground(spec.color());
            case IMAGE -> createImage(spec);
            case SHEET -> createSheet(spec);
            case GIF -> createGif(spec);
        };
    }

    private static BackgroundRenderer createImage(BackgroundSpec spec) {
        Resolved resolved = resolve(spec);
        if (resolved == null) return new VanillaBackground();
        try {
            FileTexture texture = FileTexture.load(resolved.file(), resolved.key());
            return new ImageBackground(texture, spec);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 静态图片背景加载失败，已退回原版全景图: {} —— {}", resolved.file(), e.toString());
            return new VanillaBackground();
        }
    }

    private static BackgroundRenderer createSheet(BackgroundSpec spec) {
        Resolved resolved = resolve(spec);
        if (resolved == null) return new VanillaBackground();
        try {
            FileTexture texture = FileTexture.load(resolved.file(), resolved.key());
            int frameCount = spec.sheet().frameCount();
            if (frameCount < 2) {
                LOGGER.warn(
                        "[CCNR-Menu] 精灵图背景只有 1 帧（cols×rows={}），不会有动画效果",
                        spec.sheet().capacity());
            }
            LOGGER.info(
                    "[CCNR-Menu] 精灵图背景就绪: {}（{}x{}，{} 格 = {} 帧，每帧 {}ms{}）",
                    resolved.file().getFileName(),
                    texture.width(),
                    texture.height(),
                    spec.sheet().cols() + "x" + spec.sheet().rows(),
                    frameCount,
                    spec.sheet().scaledFrameMs(),
                    spec.sheet().loop() ? "" : "，不循环");
            return new SheetBackground(texture, spec);
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 精灵图背景加载失败，已退回原版全景图: {} —— {}", resolved.file(), e.toString());
            return new VanillaBackground();
        }
    }

    private static BackgroundRenderer createGif(BackgroundSpec spec) {
        Resolved resolved = resolve(spec);
        if (resolved == null) return new VanillaBackground();
        // 解码在工作线程上进行；未就绪时这个绘制器会先画原版全景图
        return new GifBackground(spec, resolved.file(), resolved.key());
    }

    /** 解析素材路径并确认文件存在；失败返回 {@code null}（已记日志）。 */
    private static Resolved resolve(BackgroundSpec spec) {
        Path file;
        try {
            file = MenuConfigIO.resolveAsset(spec.file());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[CCNR-Menu] 背景素材路径不可用: {} —— {}", spec.file(), e.getMessage());
            return null;
        }
        if (!Files.isRegularFile(file)) {
            LOGGER.warn("[CCNR-Menu] 背景素材不存在，已退回原版全景图: {}（请把文件放进 {}）", file, MenuConfigIO.configDir());
            return null;
        }
        long modifiedAt = MenuConfigIO.lastModified(file);
        return new Resolved(file, FileTexture.keyFor(file, modifiedAt));
    }

    private record Resolved(Path file, String key) {}
}
