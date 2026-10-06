/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.SpriteSheet;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;

/**
 * 序列帧参数解析（背景与图片元素**共用**这一份）。
 *
 * <p>为什么必须共用：动画参数的字段名（cols/rows/frames/fps/frameMs/loop/speed）只要在两处各写一遍，
 * 迟早会出现「背景能用 fps、图标只能用 frameMs」这类只有作者自己知道的差异。
 * 校验规则（缩放、越界）也只有一份。
 *
 * <p>宽容读法（刻意的，不是偷懒）：
 * <ul>
 *   <li>参数可以写在 {@code animation} 子对象里，也可以直接写在父对象上（历史配置与手写都更省事）；</li>
 *   <li>{@code fps} 与 {@code frameMs} 二选一，{@code fps} 优先被读成每帧时长；</li>
 *   <li>{@code frameTime} 是 {@code frameMs} 的历史别名。</li>
 * </ul>
 */
public final class AnimationParser {

    private AnimationParser() {}

    /**
     * 解析序列帧参数。
     *
     * @param owner 父对象（{@code background} 或某个元素）
     * @param where 警告前缀（如 {@code background} / {@code elements[0]}）
     */
    public static SpriteSheet parse(JsonObject owner, List<String> warnings, String where) {
        boolean nested = owner.has("animation") && owner.get("animation").isJsonObject();
        JsonObject source = nested ? owner.getAsJsonObject("animation") : owner;

        int cols = (int) clamp(JsonUtil.num(source, "cols", 1), 1, 512);
        int rows = (int) clamp(JsonUtil.num(source, "rows", 1), 1, 512);
        int frames = (int) clamp(JsonUtil.num(source, "frames", 0), 0, 262144);

        int frameMs = (int) clamp(JsonUtil.num(source, "frameMs", JsonUtil.num(source, "frameTime", -1)), -1, 600000);
        if (frameMs < 0) {
            // 允许写 fps，读起来更直观
            double fps = JsonUtil.dbl(source, "fps", 0);
            frameMs = fps > 0 ? (int) Math.round(1000.0 / fps) : SpriteSheet.DEFAULT.frameMs();
        }

        boolean loop = JsonUtil.bool(source, "loop", true);
        double rawSpeed = JsonUtil.dbl(source, "speed", 1.0);
        if (rawSpeed < SpriteSheet.MIN_SPEED || rawSpeed > SpriteSheet.MAX_SPEED) {
            warnings.add(where + ".speed 超出范围: " + rawSpeed + "（" + SpriteSheet.MIN_SPEED + "~" + SpriteSheet.MAX_SPEED
                    + "）→ 已收敛到边界");
        }
        float speed = (float) clamp(rawSpeed, SpriteSheet.MIN_SPEED, SpriteSheet.MAX_SPEED);
        return new SpriteSheet(cols, rows, frames, frameMs, loop, speed);
    }

    /** 是否写了动画参数（元素据此决定要不要走序列帧那条路）。 */
    public static boolean present(JsonObject owner) {
        if (owner == null) return false;
        if (owner.has("animation") && owner.get("animation").isJsonObject()) return true;
        // 直接写在元素上的写法：至少要有一个只有序列帧才认识的字段，避免把 button 的 width 误判成动画
        return owner.has("cols")
                || owner.has("frames")
                || owner.has("fps")
                || owner.has("frameMs")
                || owner.has("frameTime");
    }

    /** 子元素在列宽之内的对齐方式（不认识时返回 {@code null}，由调用方决定警告还是默认）。 */
    public static Align childAlign(String raw) {
        return Align.parse(raw);
    }

    static String lower(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
