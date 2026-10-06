/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.ui.TextWave;
import com.ccnrcom.menu.ui.VAlign;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文字背景的配置解析门禁。
 *
 * <p>这一层要守住的是「作者写了什么，渲染层就一定拿得到什么」：
 * 分段拼行、调色板、时间参数任一环节解析丢了，画面上都只会是「字少了/颜色不动」，
 * 而不会有任何异常——正是那种只能靠断言发现的缺陷。
 */
class TextSpecTest {

    private static TextSpec parse(String json, List<String> warnings) {
        JsonObject root = JsonUtil.GSON.fromJson(json, JsonObject.class);
        return TextSpec.parse(root.getAsJsonObject("background"), warnings);
    }

    private static String wrap(String textObjectBody) {
        return "{\"background\": {\"type\": \"text\", " + textObjectBody + "}}";
    }

    @Test
    @DisplayName("分段：left/text/right 拼成整行，只写字符串的简写也认")
    void parsesSegments() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec = parse(
                "{\"background\": {\"type\": \"text\", \"text\": {\"segments\": ["
                        + "{\"left\": \"[===[ \", \"text\": \"CCNR\", \"right\": \" ]===]\", \"offset\": 3},"
                        + "\"裸的一段\""
                        + "]}}}",
                warnings);

        assertTrue(warnings.isEmpty(), "不该有警告: " + warnings);
        assertEquals(2, spec.segments().size());
        assertEquals("[===[ CCNR ]===]", spec.lines().get(0), "装饰与正文必须原样拼成一行");
        assertEquals(3, spec.segments().get(0).offset());
        assertEquals("裸的一段", spec.lines().get(1), "只写字符串 = 无装饰的一段");
        assertEquals(1, spec.segments().get(1).offset(), "简写段的调色板偏移默认是它自己的下标（几行就是几个颜色）");
    }

    @Test
    @DisplayName("跨行的字符下标连续（色带才能整块流动，而不是每行各自从头开始）")
    void lineOffsetsAreContinuous() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec = parse(wrap("\"text\": {\"segments\": [\"abc\", \"de\", \"f\"]}"), warnings);

        assertEquals(6, spec.totalChars());
        assertEquals(0, spec.lineOffsets()[0]);
        assertEquals(3, spec.lineOffsets()[1]);
        assertEquals(5, spec.lineOffsets()[2]);
    }

    @Test
    @DisplayName("CJK 字符按码点计数（按 char 计数会让色带在中文段落上每行错开）")
    void countsByCodePoint() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec = parse(wrap("\"text\": {\"segments\": [\"中文\", \"ab\"]}"), warnings);
        assertEquals(4, spec.totalChars(), "两个中文字 = 2 个字，不是 4 个");
        assertEquals(2, spec.lineOffsets()[1]);
    }

    @Test
    @DisplayName("调色板：合法色按序读入，非法色跳过并警告；一个都不合法时退回内置调色板")
    void parsesPalette() {
        List<String> warnings = new ArrayList<>();
        TextSpec ok = parse(wrap("\"text\": {\"palette\": [\"#112233\", \"#445566\"]}"), warnings);
        assertTrue(warnings.isEmpty());
        assertEquals(2, ok.palette().length);
        assertEquals(0xFF112233, ok.palette()[0]);

        List<String> bad = new ArrayList<>();
        TextSpec mixed = parse(wrap("\"text\": {\"palette\": [\"#112233\", \"不是颜色\"]}"), bad);
        assertEquals(1, mixed.palette().length, "非法色被跳过，合法的仍然生效");
        assertEquals(1, bad.size(), "非法色必须被指出来，而不是静默丢掉");
        assertTrue(bad.get(0).contains("palette[1]"), "警告要能定位到是第几个: " + bad);

        List<String> allBad = new ArrayList<>();
        TextSpec fallback = parse(wrap("\"text\": {\"palette\": [\"x\"]}"), allBad);
        assertEquals(TextSpec.DEFAULT_PALETTE.length, fallback.palette().length, "全非法时用内置调色板");
        assertEquals(2, allBad.size(), "既要说颜色非法，也要说已退回内置调色板");
    }

    @Test
    @DisplayName("时间参数会被收敛到合法区间（配错不该画出闪烁或静止）")
    void timeParametersAreClamped() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec = parse(
                wrap("\"text\": {\"stepMs\": 0, \"charMs\": -5, \"holdMs\": 99999999, \"scale\": 99, \"lineGap\": -3}"),
                warnings);

        assertEquals(TextWave.MIN_STEP_MS, spec.stepMs(), "0 步长会让颜色每帧乱跳，收敛到下界");
        assertEquals(0, spec.charMs(), "负的每字间隔等于一次性出现");
        assertEquals(TextWave.MAX_HOLD_MS, spec.holdMs());
        assertEquals(TextSpec.MAX_SCALE, spec.scale());
        assertEquals(0, spec.lineGap());
        assertTrue(warnings.isEmpty(), "收敛是设计好的行为，不是作者的错误，不该报警告: " + warnings);
    }

    @Test
    @DisplayName("对齐写错时退回默认并警告")
    void badAlignmentWarns() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec =
                parse(wrap("\"text\": {\"segments\": [\"a\"], \"align\": \"左边\", \"valign\": \"上下\"}"), warnings);

        assertEquals(Align.CENTER, spec.align());
        assertEquals(VAlign.MIDDLE, spec.valign());
        assertEquals(2, warnings.size(), "两个字段都要各报一条: " + warnings);
    }

    @Test
    @DisplayName("没写 text 对象 → 空分段（背景只剩一块纯色），不报错")
    void missingTextObjectIsEmpty() {
        List<String> warnings = new ArrayList<>();
        TextSpec spec = parse("{\"background\": {\"type\": \"text\", \"color\": \"#000000\"}}", warnings);
        assertTrue(spec.segments().isEmpty());
        assertTrue(warnings.isEmpty(), "没写 text 是合法的（纯色底就是结果），警告由 BackgroundSpec 统一发");
    }

    @Test
    @DisplayName("text 类型但分段为空 → 由 BackgroundSpec 给出「只剩一块纯色底」的警告")
    void emptySegmentsWarnAtBackgroundLevel() {
        List<String> warnings = new ArrayList<>();
        JsonObject root = JsonUtil.GSON.fromJson(wrap("\"text\": {}"), JsonObject.class);
        BackgroundSpec spec = BackgroundSpec.parse(root, warnings);

        assertEquals(BackgroundSpec.Kind.TEXT, spec.kind());
        assertEquals(0xFF000000, spec.color(), "文字背景的底色默认纯黑");
        assertEquals(1, warnings.size(), "应当只剩「分段为空」这一条: " + warnings);
        assertTrue(warnings.get(0).contains("segments"), warnings.get(0));
    }

    @Test
    @DisplayName("text 是新的背景类型别名，且不需要素材文件")
    void kindAliasesAndNoFile() {
        assertEquals(BackgroundSpec.Kind.TEXT, BackgroundSpec.Kind.parse("text"));
        assertEquals(BackgroundSpec.Kind.TEXT, BackgroundSpec.Kind.parse("ASCII"));
        assertFalse(BackgroundSpec.Kind.TEXT.needsFile(), "文字背景不该向作者要文件");
    }

    @Test
    @DisplayName("指纹覆盖所有会影响画面的字段（漏字段的症状是「改配置却不变」）")
    void signatureCoversFields() {
        TextSpec base = TextSpec.EMPTY;
        String signature = base.signature();

        assertNotEquals(signature, withStep(base, 200).signature(), "stepMs 要进指纹");
        assertNotEquals(
                signature,
                withSegments(base, List.of(new TextSpec.Segment("", "x", "", 0)))
                        .signature(),
                "分段要进指纹");
        assertNotEquals(
                signature, withPalette(base, new int[] {0xFF000000, 0xFFFFFFFF}).signature(), "调色板要进指纹");
        // 指纹不能依赖数组的 toString（那是身份哈希，同一个配置重建两次会得到不同的串）
        assertEquals(
                withPalette(base, new int[] {0xFF000000, 0xFFFFFFFF}).signature(),
                withPalette(base, new int[] {0xFF000000, 0xFFFFFFFF}).signature(),
                "同样的调色板必须给出同样的指纹");
    }

    private static TextSpec withStep(TextSpec spec, int stepMs) {
        return new TextSpec(
                spec.segments(),
                spec.palette(),
                spec.x(),
                spec.y(),
                spec.align(),
                spec.valign(),
                spec.scale(),
                spec.lineGap(),
                spec.charMs(),
                stepMs,
                spec.holdMs(),
                spec.loop(),
                spec.shadow());
    }

    private static TextSpec withSegments(TextSpec spec, List<TextSpec.Segment> segments) {
        return new TextSpec(
                segments,
                spec.palette(),
                spec.x(),
                spec.y(),
                spec.align(),
                spec.valign(),
                spec.scale(),
                spec.lineGap(),
                spec.charMs(),
                spec.stepMs(),
                spec.holdMs(),
                spec.loop(),
                spec.shadow());
    }

    private static TextSpec withPalette(TextSpec spec, int[] palette) {
        return new TextSpec(
                spec.segments(),
                palette,
                spec.x(),
                spec.y(),
                spec.align(),
                spec.valign(),
                spec.scale(),
                spec.lineGap(),
                spec.charMs(),
                spec.stepMs(),
                spec.holdMs(),
                spec.loop(),
                spec.shadow());
    }

    @Test
    @DisplayName("内置默认分段只用了 [ ] = - + 这几种装饰符号（用户点名的装饰集）")
    void defaultSegmentsUseBracketSymbols() {
        StringBuilder decorations = new StringBuilder();
        for (TextSpec.Segment segment : TextSpec.DEFAULT_SEGMENTS) {
            decorations.append(segment.left()).append(segment.right());
        }
        String text = decorations.toString();
        assertTrue(text.contains("["), "要有 [");
        assertTrue(text.contains("]"), "要有 ]");
        assertTrue(text.contains("="), "要有 =");
        assertTrue(text.contains("-"), "要有 -");
        assertTrue(text.contains("+"), "要有 +");
    }

    @Test
    @DisplayName("颜色按 ARGB 读入（#000000 是**不透明黑**，不是全透明）")
    void colorsAreOpaqueByDefault() {
        assertEquals(0xFF000000, (int) ColorSpec.parse("#000000"));
        assertEquals(255, ColorSpec.alpha(ColorSpec.parse("#000000")), "六位色值的 alpha 必须补成 FF");
    }
}
