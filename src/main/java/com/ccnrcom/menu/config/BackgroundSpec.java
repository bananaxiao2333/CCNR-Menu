/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.Fit;
import com.ccnrcom.menu.ui.SpriteSheet;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;

/**
 * 背景定义（纯数据，可单测）。
 *
 * @param kind 背景类型
 * @param file 素材文件（相对 {@code config/ccnr_menu/}；{@code VANILLA}/{@code COLOR}/{@code NONE} 时为空）
 * @param fit 铺屏方式
 * @param tint 着色（与原图相乘；白色 = 原色，{@code #808080} = 压暗一半）
 * @param opacity 不透明度 0..1
 * @param color 纯色背景的颜色（仅 {@code COLOR} 使用）
 * @param sheet 序列帧参数（{@code SHEET} 用全部字段；{@code GIF} 只用 {@code loop}/{@code speed}，
 *     帧时长由 GIF 自身的 {@code delayTime} 决定）
 */
public record BackgroundSpec(Kind kind, String file, Fit fit, int tint, float opacity, int color, SpriteSheet sheet) {

    /** 背景类型。 */
    public enum Kind {
        /** 原版全景图（默认：装了这个模组但什么都不配时，菜单和原版一样）。 */
        VANILLA,
        /** 单张静态图片。 */
        IMAGE,
        /** 精灵图序列帧（一张 PNG + 行列数，GPU 直绘，最省）。 */
        SHEET,
        /** 动画 GIF（JDK 自带解码器，逐帧上传）。 */
        GIF,
        /** 纯色。 */
        COLOR,
        /** 不画背景（黑屏，适合配 GLSL/外部录制场景）。 */
        NONE;

        /** 这一种背景是否需要外部素材文件。 */
        public boolean needsFile() {
            return this == IMAGE || this == SHEET || this == GIF;
        }

        /** 大小写不敏感解析；不认识时返回 {@code null}（由调用方决定警告还是默认）。 */
        public static Kind parse(String raw) {
            if (raw == null) return null;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "vanilla", "panorama", "off" -> VANILLA;
                case "image", "picture", "img" -> IMAGE;
                case "sheet", "sprite", "spritesheet", "frames" -> SHEET;
                case "gif", "animated" -> GIF;
                case "color", "colour", "solid" -> COLOR;
                case "none", "empty", "blank" -> NONE;
                default -> null;
            };
        }
    }

    /** 原版全景图；不透明度与着色都是中性值。 */
    public static final BackgroundSpec DEFAULT =
            new BackgroundSpec(Kind.VANILLA, "", Fit.COVER, ColorSpec.WHITE, 1f, 0xFF101014, SpriteSheet.DEFAULT);

    public BackgroundSpec {
        kind = kind == null ? Kind.VANILLA : kind;
        file = file == null ? "" : file;
        fit = fit == null ? Fit.COVER : fit;
        opacity = Math.min(1f, Math.max(0f, opacity));
        sheet = sheet == null ? SpriteSheet.DEFAULT : sheet;
    }

    /** 是否需要一个外部素材文件。 */
    public boolean needsFile() {
        return kind.needsFile();
    }

    /** 是否逐帧动画（需要计时器）。 */
    public boolean animated() {
        return kind == Kind.SHEET || kind == Kind.GIF;
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
            warnings.add("background.type 不认识: '" + rawType + "'（可用 vanilla/image/sheet/gif/color/none）→ 已退回原版全景图");
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
        return new BackgroundSpec(kind, file, fit, tint, opacity, color, sheet);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
