/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 轮播配置与居中标志的解析门禁。
 *
 * <p>这一层要守住的是「作者写错了要说出来」：轮播配成空列表、`fadeMs` 比 `holdMs` 还长、
 * 标志文件名为空——这些都不会抛异常，只会在屏幕上表现为「就是不动」或「什么都没有」，
 * 而作者会以为是渲染坏了。
 */
class SlideSpecTest {

    /** 造一个**根对象**（带 {@code background}），给 {@code BackgroundSpec.parse} 用。 */
    private static JsonObject background(String body) {
        return JsonUtil.GSON.fromJson("{\"background\": {" + body + "}}", JsonObject.class);
    }

    /** 造一个 {@code background} 对象本身，给 {@code SlideSpec.parse} 用。 */
    private static JsonObject slideBody(String body) {
        return JsonUtil.GSON.fromJson("{" + body + "}", JsonObject.class);
    }

    @Test
    @DisplayName("轮播：slides 按序读入，空项跳过并警告")
    void parsesSlides() {
        List<String> warnings = new ArrayList<>();
        BackgroundSpec spec = BackgroundSpec.parse(
                background("\"type\": \"slideshow\", \"slides\": [\"a.jpg\", \"\", \"b.jpg\"]"), warnings);

        assertEquals(BackgroundSpec.Kind.SLIDESHOW, spec.kind());
        assertEquals(List.of("a.jpg", "b.jpg"), spec.slides());
        assertEquals(1, warnings.size(), "空项要报出来: " + warnings);
        assertTrue(warnings.get(0).contains("slides[1]"), warnings.get(0));
        // 轮播不需要单文件素材，但素材清单必须把每一张都带上
        assertFalse(spec.needsFile());
        assertEquals(List.of("a.jpg", "b.jpg"), spec.assetFiles());
    }

    @Test
    @DisplayName("轮播：slides 是空的必须警告（否则屏幕上只剩一块纯色，作者却以为配好了）")
    void emptySlidesWarn() {
        List<String> warnings = new ArrayList<>();
        BackgroundSpec spec = BackgroundSpec.parse(background("\"type\": \"slideshow\""), warnings);
        assertEquals(BackgroundSpec.Kind.SLIDESHOW, spec.kind());
        assertTrue(spec.slides().isEmpty());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("slides"), warnings.get(0));
    }

    @Test
    @DisplayName("轮播：slides 不是数组时忽略并警告（而不是当成一张叫 [ 的图）")
    void nonArraySlidesWarn() {
        List<String> warnings = new ArrayList<>();
        BackgroundSpec spec =
                BackgroundSpec.parse(background("\"type\": \"slideshow\", \"slides\": \"a.jpg\""), warnings);
        assertTrue(spec.slides().isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("字符串数组")), warnings.toString());
    }

    @Test
    @DisplayName("轮播：单张图也是合法的（等于一张会缓慢推近的静态背景），不报警告")
    void singleSlideIsLegal() {
        List<String> warnings = new ArrayList<>();
        BackgroundSpec spec =
                BackgroundSpec.parse(background("\"type\": \"slideshow\", \"slides\": [\"only.jpg\"]"), warnings);
        assertEquals(1, spec.slides().size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("节奏参数会被收敛到合法区间；fadeMs 比 holdMs 长时收窄并警告")
    void timingIsClamped() {
        List<String> warnings = new ArrayList<>();
        SlideSpec ok = SlideSpec.parse(slideBody("\"holdMs\": 5000, \"fadeMs\": 800"), warnings);
        assertEquals(5000, ok.holdMs());
        assertEquals(800, ok.fadeMs());
        assertTrue(warnings.isEmpty(), warnings.toString());

        List<String> tooLong = new ArrayList<>();
        SlideSpec narrowed = SlideSpec.parse(slideBody("\"holdMs\": 2000, \"fadeMs\": 9000"), tooLong);
        assertEquals(2000, narrowed.fadeMs(), "淡入时长收窄到停留时长");
        assertEquals(1, tooLong.size(), "必须说明为什么收窄: " + tooLong);

        assertEquals(
                SlideSpec.MIN_HOLD_MS,
                SlideSpec.parse(slideBody("\"holdMs\": 0"), new ArrayList<>()).holdMs());
        assertEquals(
                SlideSpec.MAX_HOLD_MS,
                SlideSpec.parse(slideBody("\"holdMs\": 99999999"), new ArrayList<>())
                        .holdMs());
    }

    @Test
    @DisplayName("运镜参数会被收敛：放大不为负、偏移不超出屏幕")
    void motionIsClamped() {
        assertEquals(
                0f,
                SlideSpec.parse(slideBody("\"zoom\": -3"), new ArrayList<>()).zoom());
        assertEquals(
                SlideSpec.MAX_ZOOM,
                SlideSpec.parse(slideBody("\"zoom\": 99"), new ArrayList<>()).zoom());
        assertEquals(
                SlideSpec.MAX_PAN,
                SlideSpec.parse(slideBody("\"panX\": 99"), new ArrayList<>()).panX());
        assertEquals(
                -SlideSpec.MAX_PAN,
                SlideSpec.parse(slideBody("\"panX\": -99"), new ArrayList<>()).panX());
        assertTrue(
                SlideSpec.parse(slideBody("\"panX\": 0.07"), new ArrayList<>()).panX() > 0f);
    }

    @Test
    @DisplayName("循环开关读得到（默认循环）")
    void loopFlag() {
        assertTrue(SlideSpec.parse(slideBody(""), new ArrayList<>()).loop());
        assertFalse(
                SlideSpec.parse(slideBody("\"loop\": false"), new ArrayList<>()).loop());
    }

    @Test
    @DisplayName("轮播类型的别名都能认")
    void kindAliases() {
        assertEquals(BackgroundSpec.Kind.SLIDESHOW, BackgroundSpec.Kind.parse("slideshow"));
        assertEquals(BackgroundSpec.Kind.SLIDESHOW, BackgroundSpec.Kind.parse("Carousel"));
        assertEquals(BackgroundSpec.Kind.SLIDESHOW, BackgroundSpec.Kind.parse("slides"));
        assertEquals(BackgroundSpec.Kind.SLIDESHOW, BackgroundSpec.Kind.parse("rotate"));
        assertFalse(BackgroundSpec.Kind.SLIDESHOW.needsFile(), "轮播要的是一串文件，不该走「file 为空就退回原版」那条防呆");
    }

    // ------------------------------------------------------------------
    // mark：泥土页面上的居中标志
    // ------------------------------------------------------------------

    private static JsonObject root(String body) {
        return JsonUtil.GSON.fromJson("{" + body + "}", JsonObject.class);
    }

    @Test
    @DisplayName("标志：file/width/opacity/位置都读得到")
    void parsesMark() {
        List<String> warnings = new ArrayList<>();
        MarkSpec mark = MarkSpec.parse(
                root("\"mark\": {\"file\": \"logo.png\", \"width\": 420, \"opacity\": 0.9, \"x\": 0.5, \"y\": 0.4}"),
                warnings);

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(mark.enabled());
        assertEquals("logo.png", mark.file());
        assertEquals(420, mark.width());
        assertEquals(0.9f, mark.opacity(), 0.001f);
        assertEquals(0.4, mark.y(), 0.0001);
        assertFalse(mark.onMainMenu(), "默认只画在泥土页面上");
    }

    @Test
    @DisplayName("标志：没写 mark 就是不画；写了但 file 为空要警告")
    void markAbsentOrEmpty() {
        List<String> warnings = new ArrayList<>();
        MarkSpec none = MarkSpec.parse(root("\"enabled\": true"), warnings);
        assertFalse(none.enabled(), "没写 mark 就是不画，也不该报警告");
        assertTrue(warnings.isEmpty(), warnings.toString());

        List<String> emptyWarnings = new ArrayList<>();
        MarkSpec empty = MarkSpec.parse(root("\"mark\": {\"width\": 100}"), emptyWarnings);
        assertFalse(empty.enabled());
        assertEquals(1, emptyWarnings.size(), "写了个空 mark 是作者的笔误，要说出来");
    }

    @Test
    @DisplayName("标志：宽度不合法时退回「按原图宽度」并警告")
    void markWidthFallsBack() {
        List<String> warnings = new ArrayList<>();
        MarkSpec mark = MarkSpec.parse(root("\"mark\": {\"file\": \"a.png\", \"width\": 99999}"), warnings);
        assertEquals(MarkSpec.AUTO, mark.width());
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    @DisplayName("标志：位置分数收敛到 0..1（越界会让标志跑到屏幕外）")
    void markFractionsAreClamped() {
        MarkSpec mark = MarkSpec.parse(root("\"mark\": {\"file\": \"a.png\", \"x\": 9, \"y\": -9}"), new ArrayList<>());
        assertEquals(1.0, mark.x(), 0.0001);
        assertEquals(0.0, mark.y(), 0.0001);
    }
}
