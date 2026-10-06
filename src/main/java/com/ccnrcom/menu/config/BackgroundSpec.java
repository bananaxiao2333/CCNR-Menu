/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.Fit;
import com.ccnrcom.menu.ui.SpriteSheet;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 背景定义（纯数据，可单测）。
 *
 * @param kind 背景类型
 * @param file 素材文件（相对 {@code config/ccnr_menu/}；{@code VANILLA}/{@code COLOR}/{@code NONE}
 *     以及 {@code SLIDESHOW} 时为空，轮播走 {@link #slides()}）
 * @param fit 铺屏方式
 * @param tint 着色（与原图相乘；白色 = 原色，{@code #808080} = 压暗一半）
 * @param opacity 不透明度 0..1
 * @param color 纯色背景的颜色（{@code COLOR} 用它）
 * @param sheet 序列帧参数（{@code SHEET} 用全部字段；{@code GIF} 只用 {@code loop}/{@code speed}，
 *     帧时长由 GIF 自身的 {@code delayTime} 决定）
 * @param slides 轮播的图片列表（仅 {@code SLIDESHOW}）
 * @param slide 轮播的节奏与运镜（仅 {@code SLIDESHOW}）
 */
public record BackgroundSpec(
        Kind kind,
        String file,
        Fit fit,
        int tint,
        float opacity,
        int color,
        SpriteSheet sheet,
        List<String> slides,
        SlideSpec slide) {

    /** 背景类型。 */
    public enum Kind {
        /** 原版全景图（默认：装了这个模组但什么都不配时，菜单和原版一样）。 */
        VANILLA,
        /** 单张静态图片。 */
        IMAGE,
        /** 多张图片轮播（淡入淡出 + 缓慢放大 + 缓慢偏移，见 {@link SlideSpec}）。 */
        SLIDESHOW,
        /** 精灵图序列帧（一张 PNG + 行列数，GPU 直绘，最省）。 */
        SHEET,
        /** 动画 GIF（JDK 自带解码器，逐帧上传）。 */
        GIF,
        /** 纯色。 */
        COLOR,
        /** 不画背景（黑屏，适合配 GLSL/外部录制场景）。 */
        NONE;

        /**
         * 这一种背景是否需要**单个**素材文件（{@code file} 字段）。
         *
         * <p>{@code SLIDESHOW} 刻意返回 {@code false}：它要的是一串文件而不是一个，
         * 混进这个判据会让「file 为空 → 退回原版全景图」那条防呆把轮播误杀。
         */
        public boolean needsFile() {
            return this == IMAGE || this == SHEET || this == GIF;
        }

        /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
        public static Kind parse(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "vanilla", "panorama", "off" -> VANILLA;
                case "image", "picture", "img" -> IMAGE;
                case "slideshow", "slides", "carousel", "rotate" -> SLIDESHOW;
                case "sheet", "sprite", "spritesheet", "frames" -> SHEET;
                case "gif", "animated" -> GIF;
                case "color", "colour", "solid" -> COLOR;
                case "none", "empty", "blank" -> NONE;
                default -> null;
            };
        }
    }

    /** 原版全景图；不透明度与着色都是中性值。 */
    public static final BackgroundSpec DEFAULT = new BackgroundSpec(
            Kind.VANILLA,
            "",
            Fit.COVER,
            ColorSpec.WHITE,
            1f,
            0xFF101014,
            SpriteSheet.DEFAULT,
            List.of(),
            SlideSpec.DEFAULT);

    public BackgroundSpec {
        kind = kind == null ? Kind.VANILLA : kind;
        file = file == null ? "" : file;
        fit = fit == null ? Fit.COVER : fit;
        opacity = Math.min(1f, Math.max(0f, opacity));
        sheet = sheet == null ? SpriteSheet.DEFAULT : sheet;
        slides = slides == null ? List.of() : List.copyOf(slides);
        slide = slide == null ? SlideSpec.DEFAULT : slide;
    }

    /** 是否需要一个外部素材文件。 */
    public boolean needsFile() {
        return kind.needsFile();
    }

    /**
     * 背景要用到的**全部**素材文件（诊断与门禁用）。
     *
     * <p>单文件类型给一个元素，轮播给整串；这样「素材清单」在调用方只有一份逻辑。
     */
    public List<String> assetFiles() {
        if (kind == Kind.SLIDESHOW) return slides;
        return needsFile() && !file.isBlank() ? List.of(file) : List.of();
    }

    /** 从配置解析；缺失返回 {@link #DEFAULT}。 */
    public static BackgroundSpec parse(JsonObject root, List<String> warnings) {
        if (root == null || !root.has("background") || !root.get("background").isJsonObject()) {
            return DEFAULT;
        }
        JsonObject o = root.getAsJsonObject("background");
        String rawType = JsonUtil.str(o, "type", "vanilla");
        Kind kind = Kind.parse(rawType);
        if (kind == null) {
            warnings.add("background.type 不认识: '" + rawType
                    + "'（可用 vanilla/image/slideshow/sheet/gif/color/none）→ 已退回原版全景图");
            kind = Kind.VANILLA;
        }
        String file = JsonUtil.str(o, "file", "").trim();

        String rawFit = JsonUtil.str(o, "fit", null);
        Fit fit = Fit.COVER;
        if (rawFit != null) {
            Fit parsed = Fit.parse(rawFit);
            if (parsed == null) {
                warnings.add("background.fit 不认识: '" + rawFit + "'（可用 cover/contain/stretch/center/tile）→ 已用 cover");
            } else {
                fit = parsed;
            }
        }

        int tint = ColorSpec.WHITE;
        String rawTint = JsonUtil.str(o, "tint", null);
        if (rawTint != null) {
            Integer parsed = ColorSpec.parse(rawTint);
            if (parsed == null) {
                warnings.add("background.tint 不是合法颜色: '" + rawTint + "' → 已用白色（原色）");
            } else {
                tint = parsed;
            }
        }

        float opacity = (float) clamp(JsonUtil.dbl(o, "opacity", 1.0), 0.0, 1.0);

        int color = DEFAULT.color();
        String rawColor = JsonUtil.str(o, "color", null);
        if (rawColor != null) {
            Integer parsed = ColorSpec.parse(rawColor);
            if (parsed == null) {
                warnings.add("background.color 不是合法颜色: '" + rawColor + "' → 已用深灰");
            } else {
                color = parsed;
            }
        }

        List<String> slides = parseSlides(o, kind, warnings);
        SlideSpec slide = SlideSpec.parse(o, warnings);

        // 序列帧参数与图片元素共用同一份解析（字段名/校验只有一处，见 AnimationParser）
        boolean animatedObject = o.has("animation") && o.get("animation").isJsonObject();
        SpriteSheet sheet = (kind == Kind.SHEET || animatedObject)
                ? AnimationParser.parse(o, warnings, "background")
                : SpriteSheet.DEFAULT;

        if (kind.needsFile() && file.isEmpty()) {
            warnings.add("background.file 为空 → 已退回原版全景图（" + kind.name().toLowerCase(Locale.ROOT) + " 背景需要一个素材文件）");
            kind = Kind.VANILLA;
        }
        if (kind == Kind.SHEET && sheet.frameCount() < 2) {
            warnings.add("background: sheet 背景的 cols×rows 只有一帧，不会有动画效果（cols=" + sheet.cols() + ", rows=" + sheet.rows()
                    + "）");
        }
        return new BackgroundSpec(kind, file, fit, tint, opacity, color, sheet, slides, slide);
    }

    /**
     * 解析轮播的图片列表。
     *
     * <p>单张图片的轮播是合法的（等于一个会缓慢放大/偏移的静态背景），所以不报警告；
     * 但**空列表**必须报——那会画出一片纯色，而作者以为自己配了轮播。
     */
    private static List<String> parseSlides(JsonObject o, Kind kind, List<String> warnings) {
        List<String> slides = new ArrayList<>();
        JsonElement raw = o == null ? null : o.get("slides");
        if (raw != null && !raw.isJsonNull()) {
            if (!raw.isJsonArray()) {
                warnings.add("background.slides 必须是字符串数组（例如 [\"a.jpg\", \"b.jpg\"]）→ 已忽略");
            } else {
                JsonArray array = raw.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    JsonElement el = array.get(i);
                    if (el == null || el.isJsonNull()) continue;
                    String name = el.getAsString().trim();
                    if (name.isEmpty()) {
                        warnings.add("background.slides[" + i + "] 是空字符串 → 已跳过该张");
                        continue;
                    }
                    slides.add(name);
                }
            }
        }
        // 「没写 slides」与「写了个空数组」对玩家是同一件事：一张图都没有。
        // 两者都必须说——否则屏幕上只剩一块纯色，而作者以为轮播配好了。
        if (kind == Kind.SLIDESHOW && slides.isEmpty()) {
            warnings.add("background.type 是 slideshow，但 background.slides 里一张图都没有 → 屏幕上只会剩一块纯色底");
        }
        return slides;
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
