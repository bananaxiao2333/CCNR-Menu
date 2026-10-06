/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccnrcom.menu.ui.Align;
import com.ccnrcom.menu.ui.ButtonStyle;
import com.ccnrcom.menu.ui.ColorSpec;
import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 配置解析门禁。
 *
 * <p>两条最重要的断言不是「能解析」，而是：
 * <ol>
 *   <li><b>随模组发布的默认配置与示例配置必须解析出 0 条警告</b>：示例是玩家抄的模板，
 *       它自己要是有问题，玩家抄过去只会得到一堆莫名其妙的警告。这条断言把「文档示例」
 *       和「解析器」钉在一起——改了字段名却忘了改示例，测试立刻红。</li>
 *   <li><b>一个按钮都没有的配置必须被拦下</b>：主菜单没有按钮等于玩家进不去设置、退不出游戏。
 *       这是本模组唯一允许的静默降级，方向必须是「退回原版按钮」。</li>
 * </ol>
 */
class MenuConfigTest {

    private static JsonObject parse(String json) {
        return JsonUtil.GSON.fromJson(json, JsonObject.class);
    }

    private static JsonObject resource(String path) {
        JsonObject object = JsonUtil.readResource(path).orElse(null);
        assertNotNull(object, "内置资源缺失: " + path);
        return object;
    }

    @Test
    @DisplayName("随模组发布的默认配置：左侧竖列（图标在上、按钮在下、左边缘对齐）+ 黑底彩色文字背景 + 纯文字按钮")
    void defaultResourceIsClean() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(resource("/assets/ccnr_menu/defaults/menu.json"), warnings);

        assertTrue(warnings.isEmpty(), "默认配置不应有警告: " + warnings);
        assertTrue(config.enabled(), "默认应当启用（否则换上毫无效果）");
        assertFalse(config.vanillaButtons(), "默认用的是配置出来的按钮");
        assertTrue(config.applyToAllScreens(), "默认把泥土界面也一起换掉（这就是「都替换掉」）");

        // 背景：黑底 + 彩色文字动画（不加载任何素材）
        assertEquals(BackgroundSpec.Kind.TEXT, config.background().kind(), "默认背景是文字背景");
        assertFalse(config.background().needsFile(), "文字背景不应该需要素材文件");
        assertEquals(0xFF000000, config.background().color(), "底色是纯黑");
        assertEquals(3, config.background().text().segments().size(), "默认三段（装饰符号 [ ] = - +）");
        assertTrue(config.background().text().totalChars() > 0, "分段里得有字");
        assertTrue(config.background().text().palette().length >= 2, "彩色文字至少要有两个颜色才谈得上流动");
        assertFalse(config.background().text().loop(), "默认只写一遍，之后颜色继续流动（循环重写会一直抢注意力）");

        // 外观：无背景纯文字
        assertEquals(ButtonStyle.TEXT, config.theme().buttonStyle(), "默认按钮是纯文字外观");
        assertEquals(0, ColorSpec.alpha(config.theme().backdrop()), "背景本来就是黑的，不该再压一层遮罩");

        // 布局：一个左对齐的竖列，列内左对齐，第一个子元素是横版大图标
        assertEquals(1, config.elements().size(), "默认布局就是一个竖列容器");
        MenuElement column = config.elements().get(0);
        assertEquals(MenuElement.Type.COLUMN, column.type());
        assertEquals(Align.LEFT, column.align(), "整块靠左");
        assertEquals(Align.LEFT, column.column().childAlign(), "列内左对齐（图标与按钮左边缘对齐）");
        assertEquals(300, column.width(), "列宽固定，这样按钮不会因图标宽度变化而左右跳");
        MenuElement icon = column.column().children().get(0);
        assertEquals(MenuElement.Type.IMAGE, icon.type(), "图标在按钮之上");
        assertTrue(icon.animatedImage(), "默认图标播放入场动画");
        assertEquals("logo_wide_intro_mono.png", icon.file(), "默认用全白单色图标（配黑底彩色文字背景）");
        assertEquals(8, icon.sheet().cols());
        assertEquals(10, icon.sheet().rows(), "80 帧 = 8x10");
        assertEquals(33, icon.sheet().frameMs(), "每帧 33ms ≈ 30fps");
        assertEquals(4, config.buttonCount(), "按钮个数（递归统计容器里的）");
        for (MenuElement child : column.column().children()) {
            if (child.type() == MenuElement.Type.BUTTON) {
                assertEquals(MenuElement.AUTO, child.width(), "纯文字按钮不写宽度：宽度由外观决定（贴着文字，否则看不见的热区比文字宽一大截）");
            }
        }
    }

    @Test
    @DisplayName("随模组发布的示例配置：解析出 0 条警告（示例本身必须是合法的）")
    void exampleResourceIsClean() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(resource("/assets/ccnr_menu/defaults/menu.example.json"), warnings);

        assertTrue(warnings.isEmpty(), "示例配置不应产生任何警告: " + warnings);
        assertFalse(config.vanillaButtons(), "示例应当演示自定义按钮");
        assertEquals(1, config.elements().size(), "示例同样是一个竖列容器");
        assertEquals(4, config.buttonCount(), "示例里的按钮个数");
        assertEquals(BackgroundSpec.Kind.IMAGE, config.background().kind(), "示例演示的是静态图背景 + 压暗");
        assertEquals("background.png", config.background().file());
    }

    @Test
    @DisplayName("配置里一个按钮都没有时，自动退回原版按钮并给出警告（防「退不出游戏」）")
    void noButtonsFallsBackToVanillaButtons() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"vanillaButtons\":false,\"elements\":[{\"type\":\"label\",\"text\":\"hi\"}]}"), warnings);

        assertTrue(config.vanillaButtons(), "没有任何按钮时必须退回原版按钮");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("没有任何 button")), "必须明确警告原因: " + warnings);
    }

    @Test
    @DisplayName("认不出的元素类型被跳过并警告（不留一个点了没反应的按钮）")
    void unknownTypeIsSkipped() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse(
                        "{\"elements\":[{\"type\":\"slider\",\"text\":\"音量\"},{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        assertEquals(1, config.elements().size(), "非法元素应被跳过");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("type 不认识")), warnings.toString());
    }

    @Test
    @DisplayName("动作不合法时跳过该按钮并警告（例如把 file: 当成链接）")
    void invalidActionIsSkipped() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"button\",\"text\":\"x\",\"action\":\"url:file:///etc/passwd\"},"
                        + "{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        assertEquals(1, config.elements().size(), "非法动作的按钮应被跳过");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("链接不合法")), warnings.toString());
    }

    @Test
    @DisplayName("动画背景缺 file 时退回原版全景图并警告")
    void animationWithoutFileFallsBack() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(parse("{\"background\":{\"type\":\"gif\"}}"), warnings);

        assertEquals(BackgroundSpec.Kind.VANILLA, config.background().kind());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("background.file")), warnings.toString());
    }

    @Test
    @DisplayName("精灵图参数：cols/rows/fps 解析成帧数与每帧时长")
    void sheetParameters() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"background\":{\"type\":\"sheet\",\"file\":\"bg.png\","
                        + "\"animation\":{\"cols\":8,\"rows\":4,\"frames\":30,\"fps\":20,\"loop\":false,\"speed\":2}},"
                        + "\"elements\":[{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        BackgroundSpec background = config.background();
        assertEquals(BackgroundSpec.Kind.SHEET, background.kind());
        assertEquals(8, background.sheet().cols());
        assertEquals(4, background.sheet().rows());
        assertEquals(30, background.sheet().frameCount(), "frames 应覆盖 cols*rows");
        assertEquals(50, background.sheet().frameMs(), "fps=20 → 每帧 50ms");
        assertEquals(25, background.sheet().scaledFrameMs(), "speed=2 → 每帧 25ms");
        assertFalse(background.sheet().loop());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("越界的分数锚点会收敛到 0~1 并警告（元素不该跑到屏幕外）")
    void fractionsAreClamped() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"button\",\"text\":\"x\",\"action\":\"quit\",\"x\":7,\"y\":-3}]}"),
                warnings);

        MenuElement element = config.elements().get(0);
        assertEquals(1.0, element.x());
        assertEquals(0.0, element.y());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("应在 0~1")), warnings.toString());
    }

    @Test
    @DisplayName("颜色写错时用默认色并警告（不静默变白）")
    void badColorsWarn() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse(
                        "{\"theme\":{\"accent\":\"不是颜色\"},\"background\":{\"type\":\"color\",\"color\":\"#GGG\"},"
                                + "\"elements\":[{\"type\":\"button\",\"text\":\"x\",\"action\":\"quit\",\"color\":\"nope\"}]}"),
                warnings);

        assertEquals(MenuThemeSpec.DEFAULT.accent(), config.theme().accent(), "主题色应退回默认值");
        assertEquals(MenuElement.NO_COLOR, config.elements().get(0).color(), "元素颜色非法时应标记为「用主题色」");
        assertEquals(3, warnings.size(), "三处颜色问题都该报警告: " + warnings);
    }

    @Test
    @DisplayName("过大的 offset 归零并警告")
    void hugeOffsetsReset() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"button\",\"text\":\"x\",\"action\":\"quit\",\"offsetX\":99999}]}"),
                warnings);

        assertEquals(0, config.elements().get(0).offsetX());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("offsetX")), warnings.toString());
    }

    @Test
    @DisplayName("完全损坏的 JSON → 兜底为「原版按钮」，菜单一定可用")
    void brokenConfigFallsBack() {
        MenuConfig config = MenuConfig.fallback();

        assertTrue(config.enabled());
        assertTrue(config.vanillaButtons(), "兜底必须是能玩的原版菜单");
        assertEquals(BackgroundSpec.Kind.VANILLA, config.background().kind());
        assertTrue(MenuConfig.template().vanillaButtons(), "模板同样必须是原版按钮（装上等于没装）");
    }

    @Test
    @DisplayName("按钮默认尺寸与原版一致（200x20）")
    void buttonDefaults() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"button\",\"text\":\"x\",\"action\":\"quit\"}]}"), warnings);

        MenuElement element = config.elements().get(0);
        assertEquals(200, element.buttonWidth());
        assertEquals(20, element.buttonHeight());
        assertTrue(element.actionable());
    }

    @Test
    @DisplayName("空文案的 button/label 与缺 file 的 image 都会被跳过")
    void emptyElementsAreSkipped() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"button\",\"text\":\"  \"},{\"type\":\"label\"},{\"type\":\"image\"},"
                        + "{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        assertEquals(1, config.elements().size());
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    @DisplayName("竖列容器：子元素递归解析，间距/列宽/列内对齐都读得到")
    void columnParses() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"column\",\"x\":0.9,\"align\":\"right\",\"width\":300,\"gap\":14,"
                        + "\"childAlign\":\"right\",\"children\":["
                        + "{\"type\":\"image\",\"file\":\"logo.png\",\"width\":300},"
                        + "{\"type\":\"button\",\"text\":\"a\",\"action\":\"quit\"},"
                        + "{\"type\":\"button\",\"text\":\"b\",\"action\":\"quit\"}]}]}"),
                warnings);

        MenuElement column = config.elements().get(0);
        assertEquals(MenuElement.Type.COLUMN, column.type());
        assertEquals(300, column.width());
        assertEquals(14, column.column().gap());
        assertEquals(Align.RIGHT, column.column().childAlign());
        assertEquals(3, column.column().children().size());
        // 容器里的按钮也算进防呆判据（否则「有按钮」却会被判成没有）
        assertEquals(2, config.buttonCount());
        assertEquals(4, config.elementCount(), "元素总数含容器自身");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("空容器被跳过并警告（留一个什么都不显示的块不如直接报错）")
    void emptyColumnIsSkipped() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"column\",\"children\":[]},"
                        + "{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        assertEquals(1, config.elements().size());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("children")), warnings.toString());
    }

    @Test
    @DisplayName("容器嵌套超过上限时跳过并警告（防病态配置）")
    void nestedColumnDepthCapped() {
        StringBuilder json = new StringBuilder("{\"elements\":[");
        int depth = MenuElement.MAX_DEPTH + 2;
        for (int i = 0; i < depth; i++) json.append("{\"type\":\"column\",\"children\":[");
        json.append("{\"type\":\"button\",\"text\":\"deep\",\"action\":\"quit\"}");
        for (int i = 0; i < depth; i++) json.append("]}");
        json.append("]}");

        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(parse(json.toString()), warnings);

        assertEquals(0, config.buttonCount(), "超过深度的分支整段被丢弃");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("嵌套")), warnings.toString());
        // 没有任何按钮 → 防呆必须生效，菜单依然可用
        assertTrue(config.vanillaButtons());
    }

    @Test
    @DisplayName("列内对齐写错时退回 center 并警告")
    void badChildAlignWarns() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"column\",\"childAlign\":\"斜着\",\"children\":["
                        + "{\"type\":\"button\",\"text\":\"a\",\"action\":\"quit\"}]}]}"),
                warnings);

        assertEquals(Align.CENTER, config.elements().get(0).column().childAlign());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("childAlign")), warnings.toString());
    }

    @Test
    @DisplayName("图片元素的序列帧参数与背景共用一份解析（fps/loop/speed 都认）")
    void animatedImageElement() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"image\",\"file\":\"logo.png\",\"width\":300,"
                        + "\"animation\":{\"cols\":8,\"rows\":4,\"fps\":12,\"loop\":false,\"speed\":1.5}},"
                        + "{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        MenuElement image = config.elements().get(0);
        assertTrue(image.animatedImage());
        assertEquals(32, image.sheet().frameCount(), "8x4 网格用满 = 32 帧");
        assertEquals(83, image.sheet().frameMs(), "fps=12 → 83ms");
        assertEquals(55, image.sheet().scaledFrameMs(), "speed=1.5 → 55ms");
        assertFalse(image.sheet().loop(), "入场动画只播一次");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("静态图片元素不带 sheet（animatedImage 为 false）")
    void staticImageElement() {
        List<String> warnings = new ArrayList<>();
        MenuConfig config = MenuConfig.parse(
                parse("{\"elements\":[{\"type\":\"image\",\"file\":\"logo.png\"},"
                        + "{\"type\":\"button\",\"text\":\"ok\",\"action\":\"quit\"}]}"),
                warnings);

        assertFalse(config.elements().get(0).animatedImage());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("applyToAllScreens 默认开、可以关")
    void applyToAllScreens() {
        List<String> warnings = new ArrayList<>();
        assertEquals(true, MenuConfig.parse(parse("{}"), warnings).applyToAllScreens());
        assertEquals(
                false,
                MenuConfig.parse(parse("{\"applyToAllScreens\":false}"), warnings)
                        .applyToAllScreens());
    }
}
