/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.TextWave;
import com.ccnrcom.menu.ui.VAlign;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 「黑底彩色文字」背景的内容与动画参数（纯数据，可单测）。
 *
 * <p>这是 {@code background.type = "text"} 时用的背景：不加载任何图片，
 * 直接在一块纯色（默认纯黑）上画出**分段**的装饰文字，并按时间流动颜色、逐字出现。
 * 存在的理由很实际：一张 1920×1080 的背景图动辄几 MB，而它的内容常常就是几行字——
 * 这几行字完全可以是「文本 + 参数」而不是「像素」。
 *
 * <p>三个概念分清楚：
 *
 * <ul>
 *   <li><b>分段（{@link Segment}）</b>：一行就是一段。每段由 {@code left}/{@code text}/{@code right}
 *       拼成，装饰符号（{@code [ ] = - +} 这类）**由作者直接写在 left/right 里**，
 *       而不是由模组从某个固定样式里挑——样式表永远猜不到作者想要
 *       {@code [===[ CCNR ]===]} 还是 {@code +--- CCNR ---+}。</li>
 *   <li><b>调色板（{@code palette}）</b>：彩色文字的取色表。整块文字的字符按下标在调色板里循环取色，
 *       随时间整体推进（见 {@link TextWave#colorFor}）。</li>
 *   <li><b>整块的摆放（{@code x}/{@code y}/{@code align}/{@code valign}）</b>：
 *       与 {@link MenuElement} 完全同一套语义，省得学第二套坐标。</li>
 * </ul>
 *
 * @param segments 分段（自上而下一行一段）
 * @param palette 调色板（ARGB；空表示用 {@link #DEFAULT_PALETTE}）
 * @param x 横向锚点分数 0..1
 * @param y 纵向锚点分数 0..1
 * @param align 横向锚点位置
 * @param valign 纵向锚点位置
 * @param scale 字号放大倍数（原版字体是整数倍**像素放大**，1 = 原大小）
 * @param lineGap 行距（像素）
 * @param charMs 每字出现间隔（0 = 整块立刻出现）
 * @param stepMs 颜色流动每前进一格的时间
 * @param holdMs 整块写完后的停留时间（仅 {@code loop} 时有意义）
 * @param loop 是否循环「重写一遍」；{@code false} 时只写一次，颜色继续流动
 * @param shadow 文字是否带阴影（纯色背景上通常不需要）
 */
public record TextSpec(
        List<Segment> segments,
        int[] palette,
        double x,
        double y,
        Align align,
        VAlign valign,
        int scale,
        int lineGap,
        int charMs,
        int stepMs,
        int holdMs,
        boolean loop,
        boolean shadow) {

    /**
     * 一个分段（一行）。
     *
     * @param left 文字左边的装饰（原样拼在文字前，例如 {@code "[===[ "}）
     * @param text 正文
     * @param right 文字右边的装饰
     * @param offset 这一段在调色板里的起始偏移。多段用不同偏移，几行文字就是不同颜色，
     *     而不是整块同色——这是「分段」在配色上的意义
     */
    public record Segment(String left, String text, String right, int offset) {

        public Segment {
            left = left == null ? "" : left;
            text = text == null ? "" : text;
            right = right == null ? "" : right;
        }

        /** 拼好的整行文字（含装饰）。 */
        public String line() {
            return left + text + right;
        }

        /** 本段字符数。 */
        public int length() {
            return line().codePointCount(0, line().length());
        }
    }

    /** 默认调色板：品牌青打头，接着几个高饱和度色，最后收在近白——一条能循环的色带。 */
    public static final int[] DEFAULT_PALETTE = {0xFF4FD1E0, 0xFF7CF7C4, 0xFFFFD166, 0xFFFF8AB8, 0xFF9B8CFF, 0xFFE6EDF3
    };

    /** 默认分段：品牌名 / 定位 / 地址，三行，装饰符号只用了 {@code [ ] = - +}。 */
    public static final List<Segment> DEFAULT_SEGMENTS = List.of(
            new Segment("[===[ ", "CCNR 服务器", " ]===]", 0),
            new Segment("+--- ", "生存 · 创造 · 长期运营", " ---+", 2),
            new Segment("[===[ ", "play.ccnr.example", " ]===]", 4));

    /** 空的分段表（配置没写 {@code text} 对象时用它——背景就只剩一块纯色）。 */
    public static final TextSpec EMPTY = new TextSpec(
            List.of(), DEFAULT_PALETTE, 0.5, 0.5, Align.CENTER, VAlign.MIDDLE, 2, 12, 45, 90, 2600, false, false);

    /** 字号上限：再大就会一行吃掉整个屏幕。 */
    public static final int MAX_SCALE = 8;

    /** 分段数上限：背景是背景，不该变成一整页文章。 */
    public static final int MAX_SEGMENTS = 32;

    /** 调色板长度上限。 */
    public static final int MAX_PALETTE = 64;

    /** 分段文字的字符数上限（单段）。 */
    public static final int MAX_SEGMENT_CHARS = 512;

    public TextSpec {
        segments = segments == null ? List.of() : List.copyOf(segments);
        palette = (palette == null || palette.length == 0) ? DEFAULT_PALETTE.clone() : palette.clone();
        align = align == null ? Align.CENTER : align;
        valign = valign == null ? VAlign.MIDDLE : valign;
        scale = Math.min(MAX_SCALE, Math.max(1, scale));
        lineGap = Math.min(400, Math.max(0, lineGap));
        charMs = TextWave.clampCharMs(charMs);
        stepMs = TextWave.clampStep(stepMs);
        holdMs = TextWave.clampHoldMs(holdMs);
    }

    /** 拼好的各行文字（含装饰），绘制顺序即下标顺序。 */
    public List<String> lines() {
        List<String> out = new ArrayList<>(segments.size());
        for (Segment segment : segments) out.add(segment.line());
        return List.copyOf(out);
    }

    /** 整块文字的字符总数（跨行连续计数，用于颜色流动与打字动画）。 */
    public int totalChars() {
        int total = 0;
        for (Segment segment : segments) total += segment.length();
        return total;
    }

    /** 每一行在整块里的起始字符下标（与 {@link #lines()} 一一对应）。 */
    public int[] lineOffsets() {
        int[] offsets = new int[segments.size()];
        int running = 0;
        for (int i = 0; i < segments.size(); i++) {
            offsets[i] = running;
            running += segments.get(i).length();
        }
        return offsets;
    }

    /**
     * 指纹：所有影响画面的字段（用字符串表示，不含数组的 {@code toString} 身份哈希）。
     *
     * <p>背景实例是按指纹共享的——指纹漏字段的症状是「改了配置却不变」，
     * 所以这里宁可啰嗦也不省略。
     */
    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(x)
                .append(',')
                .append(y)
                .append(',')
                .append(align)
                .append(',')
                .append(valign)
                .append(",s")
                .append(scale)
                .append(",g")
                .append(lineGap)
                .append(",c")
                .append(charMs)
                .append(",t")
                .append(stepMs)
                .append(",h")
                .append(holdMs)
                .append(loop ? ",loop" : ",once")
                .append(shadow ? ",shadow" : ",flat")
                .append(",p[");
        for (int color : palette) sb.append(color).append(' ');
        sb.append("],lines[");
        for (Segment segment : segments) {
            sb.append(segment.offset()).append(':').append(segment.line()).append('|');
        }
        return sb.append(']').toString();
    }

    /** 从 {@code background} 对象里解析 {@code text} 子对象；缺失返回 {@link #EMPTY}。 */
    public static TextSpec parse(JsonObject background, List<String> warnings) {
        if (background == null
                || !background.has("text")
                || !background.get("text").isJsonObject()) {
            return EMPTY;
        }
        JsonObject o = background.getAsJsonObject("text");

        List<Segment> segments = parseSegments(o, warnings);

        int[] palette = parsePalette(o, warnings);

        Align align = Align.CENTER;
        String rawAlign = JsonUtil.str(o, "align", null);
        if (rawAlign != null) {
            Align parsed = Align.parse(rawAlign);
            if (parsed == null) {
                warnings.add("background.text.align 不认识: '" + rawAlign + "'（可用 left/center/right）→ 已用 center");
            } else {
                align = parsed;
            }
        }

        VAlign valign = VAlign.MIDDLE;
        String rawVAlign = JsonUtil.str(o, "valign", null);
        if (rawVAlign != null) {
            VAlign parsed = VAlign.parse(rawVAlign);
            if (parsed == null) {
                warnings.add("background.text.valign 不认识: '" + rawVAlign + "'（可用 top/middle/bottom）→ 已用 middle");
            } else {
                valign = parsed;
            }
        }

        int scale = (int) clamp(JsonUtil.num(o, "scale", EMPTY.scale()), 1, MAX_SCALE);
        int lineGap = (int) clamp(JsonUtil.num(o, "lineGap", EMPTY.lineGap()), 0, 400);
        int charMs = (int) clamp(JsonUtil.num(o, "charMs", EMPTY.charMs()), 0, TextWave.MAX_CHAR_MS);
        int stepMs = (int) clamp(JsonUtil.num(o, "stepMs", EMPTY.stepMs()), TextWave.MIN_STEP_MS, TextWave.MAX_STEP_MS);
        int holdMs = (int) clamp(JsonUtil.num(o, "holdMs", EMPTY.holdMs()), 0, TextWave.MAX_HOLD_MS);

        return new TextSpec(
                segments,
                palette,
                JsonUtil.dbl(o, "x", EMPTY.x()),
                JsonUtil.dbl(o, "y", EMPTY.y()),
                align,
                valign,
                scale,
                lineGap,
                charMs,
                stepMs,
                holdMs,
                JsonUtil.bool(o, "loop", EMPTY.loop()),
                JsonUtil.bool(o, "shadow", EMPTY.shadow()));
    }

    private static List<Segment> parseSegments(JsonObject o, List<String> warnings) {
        List<Segment> segments = new ArrayList<>();
        if (o == null || !o.has("segments") || !o.get("segments").isJsonArray()) return segments;
        JsonArray array = o.getAsJsonArray("segments");
        for (int i = 0; i < array.size(); i++) {
            if (segments.size() >= MAX_SEGMENTS) {
                warnings.add("background.text.segments 超过 " + MAX_SEGMENTS + " 段，多余的已忽略");
                break;
            }
            JsonElement el = array.get(i);
            if (el == null || el.isJsonNull()) continue;
            if (el.isJsonPrimitive()) {
                // 只写一个字符串 = 无装饰的一段，是最常见的写法，必须支持
                segments.add(new Segment("", el.getAsString(), "", segments.size()));
                continue;
            }
            if (!el.isJsonObject()) {
                warnings.add("background.text.segments[" + i + "] 既不是字符串也不是对象 → 已跳过该段");
                continue;
            }
            JsonObject seg = el.getAsJsonObject();
            String text = JsonUtil.str(seg, "text", "");
            if (text.codePointCount(0, text.length()) > MAX_SEGMENT_CHARS) {
                warnings.add("background.text.segments[" + i + "].text 超过 " + MAX_SEGMENT_CHARS + " 字，已截断");
                text = truncate(text, MAX_SEGMENT_CHARS);
            }
            segments.add(new Segment(JsonUtil.str(seg, "left", ""), text, JsonUtil.str(seg, "right", ""), (int)
                    clamp(JsonUtil.num(seg, "offset", segments.size()), 0, MAX_PALETTE)));
        }
        return segments;
    }

    private static int[] parsePalette(JsonObject o, List<String> warnings) {
        if (o == null || !o.has("palette") || !o.get("palette").isJsonArray()) return DEFAULT_PALETTE.clone();
        JsonArray array = o.getAsJsonArray("palette");
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < array.size() && colors.size() < MAX_PALETTE; i++) {
            JsonElement el = array.get(i);
            String raw = el == null || el.isJsonNull() ? null : el.getAsString();
            Integer parsed = ColorSpec.parse(raw);
            if (parsed == null) {
                warnings.add("background.text.palette[" + i + "] 不是合法颜色: '" + raw + "' → 已跳过该色");
                continue;
            }
            colors.add(parsed);
        }
        if (colors.isEmpty()) {
            warnings.add("background.text.palette 里没有一个合法颜色 → 已用内置调色板");
            return DEFAULT_PALETTE.clone();
        }
        int[] out = new int[colors.size()];
        for (int i = 0; i < out.length; i++) out[i] = colors.get(i);
        return out;
    }

    private static String truncate(String text, int codePoints) {
        int end = text.offsetByCodePoints(0, Math.min(codePoints, text.codePointCount(0, text.length())));
        return text.substring(0, end);
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.min(max, Math.max(min, v));
    }
}
