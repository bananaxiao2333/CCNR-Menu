/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client;

import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import com.ccnrcom.menu.client.background.TextureDraw;
import com.ccnrcom.menu.client.texture.FileTexture;
import com.ccnrcom.menu.client.ui.MenuButton;
import com.ccnrcom.menu.client.ui.MenuTheme;
import com.ccnrcom.menu.config.MenuAction;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuConfigStore;
import com.ccnrcom.menu.config.MenuElement;
import com.ccnrcom.menu.ui.ButtonStyle;
import com.ccnrcom.menu.ui.FrameClock;
import com.ccnrcom.menu.ui.MenuGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 本模组的主菜单屏幕。
 *
 * <p>它替换原版 {@code TitleScreen}（替换点在 {@link TitleScreenHook}），把「背景 + 元素布局」
 * 全部交给 {@link MenuConfig}。三件必须知道的事：
 *
 * <ol>
 *   <li><b>不调用 {@code super.render(...)}</b>：{@code Screen.render} 会调用
 *       {@code renderBackground}，在「没有进入世界」时会画**泥土背景**，正好盖住我们的背景。
 *       因此这里自己遍历 {@code renderables} 画控件，顺序也由自己掌控
 *       （背景 → 压暗层 → 图片元素 → 文字元素 → 按钮 → 淡入遮罩）。</li>
 *   <li><b>{@code init()} 会被反复调用</b>（窗口缩放、资源重载），所以元素几何每次都要重算，
 *       而元素贴图在重算前先释放——否则每缩放一次窗口就多泄漏一批贴图。
 *       **背景反过来**：它由 {@link ScreenBackgrounds} 按配置指纹共享持有，
 *       屏幕既不创建也不释放（窗口缩放显然不该重新解码一张 GIF）。</li>
 *   <li><b>布局是递归的</b>：{@code column} 容器先把自己量出来、摆到位置上，
 *       再把子元素按「列宽内对齐 + 竖直堆叠」摆放。子元素的 {@code x}/{@code y} 在容器里不起作用
 *       （位置由容器决定），只有 {@code offsetX}/{@code offsetY} 仍然生效。</li>
 * </ol>
 */
public final class MenuScreen extends Screen {

    /** 淡入时长。做法是盖一层逐渐透明的黑，因此背景与按钮一起淡入。 */
    private static final int FADE_IN_MS = 450;

    /** 原版按钮的行距。 */
    private static final int VANILLA_ROW_HEIGHT = 24;

    /** 纯文字按钮贴着文字时留的一点左右余量（0 会让热区正好等于字形宽度，太窄）。 */
    private static final int TEXT_BUTTON_PADDING = 4;

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private final MenuConfig config;
    private final MenuTheme theme;
    private final long openedAt = Util.getMillis();

    private final List<PlacedLabel> labels = new ArrayList<>();
    private final List<PlacedImage> images = new ArrayList<>();
    /** 列背后的色带（画在最前，于是图标与按钮都在它之上）。 */
    private final List<PlacedBar> bars = new ArrayList<>();
    /** 元素图片的贴图（与 {@link #images} 同生命周期，随屏幕一起释放）。 */
    private final List<FileTexture> elementTextures = new ArrayList<>();

    /** 按元素实例缓存贴图：量尺寸与放置各会走一遍，缓存保证只加载一次。 */
    private final Map<MenuElement, FileTexture> textureByElement = new IdentityHashMap<>();

    /** 加载失败过的元素，避免同一个缺失文件被反复报警告。 */
    private final List<MenuElement> failedImages = new ArrayList<>();

    /** 本轮布局里超出屏幕的元素（汇总成一条日志，避免窗口拖动时刷屏）。 */
    private final List<String> overflow = new ArrayList<>();

    public MenuScreen(MenuConfig config) {
        super(Component.translatable("ccnr_menu.screen.title"));
        this.config = config;
        this.theme = new MenuTheme(config.theme());
    }

    @Override
    protected void init() {
        // init() 会因窗口缩放重跑：旧一轮的几何与贴图必须先清掉
        labels.clear();
        images.clear();
        bars.clear();
        releaseElementTextures();

        if (config.vanillaButtons()) {
            buildVanillaButtons();
            return;
        }
        for (MenuElement element : config.elements()) {
            MenuGeometry.Size size = measure(element);
            MenuGeometry.Rect rect = MenuGeometry.place(
                            element.x(),
                            element.y(),
                            element.align(),
                            element.valign(),
                            size.w(),
                            size.h(),
                            width,
                            height)
                    .translate(element.offsetX(), element.offsetY());
            place(element, rect);
        }
    }

    // ------------------------------------------------------------------
    // 量尺寸
    // ------------------------------------------------------------------

    private MenuGeometry.Size measure(MenuElement element) {
        return switch (element.type()) {
            case BUTTON -> new MenuGeometry.Size(buttonWidth(element), element.buttonHeight());
            case LABEL -> measureLabel(element);
            case IMAGE -> measureImage(element);
            case COLUMN -> {
                MenuGeometry.Stack stack = stackOf(element);
                yield new MenuGeometry.Size(stack.width(), stack.height());
            }
        };
    }

    /**
     * 按钮宽度：写了就用写的，没写则按外观决定。
     *
     * <p>纯文字按钮（{@code theme.buttonStyle: "text"}）没写宽度时**贴着文字**。
     * 为什么这里必须跟着外观走：纯文字按钮在屏幕上只有一个点击热区是看不见的，
     * 若还给它 200 宽的隐形矩形，鼠标停在文字右边两厘米的空白上文字也会变色——
     * 玩家看到的是「这按钮坏了」。实心底色按钮没有这个问题（底色本身就是热区的提示）。
     */
    private int buttonWidth(MenuElement element) {
        if (element.width() != MenuElement.AUTO) return element.width();
        if (theme.buttonStyle() == ButtonStyle.TEXT) {
            return font.width(text(element)) + TEXT_BUTTON_PADDING;
        }
        return element.buttonWidth();
    }

    private MenuGeometry.Size measureLabel(MenuElement element) {
        Component text = text(element);
        float scale = element.scale();
        // 缩放的文字是**像素放大**，所以尺寸也要按缩放后的算，否则居中会偏
        return new MenuGeometry.Size(Math.round(font.width(text) * scale), Math.round(font.lineHeight * scale));
    }

    /**
     * 图片尺寸：两个都没给就用原图尺寸；只给一个时按原图比例补另一个。
     *
     * <p>为什么只给宽就够：横版 LOGO 这类素材只需要说「宽 360」，高按比例出来即可——
     * 让作者手算高度是没必要的负担，而且算错就会把图标拉变形。
     */
    private MenuGeometry.Size measureImage(MenuElement element) {
        FileTexture texture = imageTexture(element);
        if (texture == null) return new MenuGeometry.Size(0, 0);
        // 比例必须按**源图区域**算：序列帧的源区域是单帧，不是整张精灵图
        // （整张 4096x684 的宽高比是 6:1，而单帧是 3:1——用错了图标会被压扁一半）
        MenuGeometry.Size source = sourceSize(element, texture);
        int w = element.width();
        int h = element.height();
        if (w == MenuElement.AUTO && h == MenuElement.AUTO) return source;
        if (w == MenuElement.AUTO) {
            return new MenuGeometry.Size(Math.max(1, Math.round(h * source.w() / (float) source.h())), h);
        }
        if (h == MenuElement.AUTO) {
            return new MenuGeometry.Size(w, Math.max(1, Math.round(w * source.h() / (float) source.w())));
        }
        return new MenuGeometry.Size(w, h);
    }

    /** 元素的源图区域：静态图为整张贴图，序列帧为第 0 帧。 */
    private MenuGeometry.Size sourceSize(MenuElement element, FileTexture texture) {
        if (!element.animatedImage()) return new MenuGeometry.Size(texture.width(), texture.height());
        MenuGeometry.Rect frame = MenuGeometry.frameUv(
                0, element.sheet().cols(), element.sheet().rows(), texture.width(), texture.height());
        return new MenuGeometry.Size(frame.w(), frame.h());
    }

    /** 计算一个竖列的排布（列宽取配置值，未配置则取最宽的子元素）。 */
    private MenuGeometry.Stack stackOf(MenuElement column) {
        List<MenuElement> children = column.column().children();
        List<MenuGeometry.Size> sizes = new ArrayList<>(children.size());
        for (MenuElement child : children) sizes.add(measure(child));
        return MenuGeometry.stack(
                sizes, column.width(), column.column().gap(), column.column().childAlign());
    }

    // ------------------------------------------------------------------
    // 放置
    // ------------------------------------------------------------------

    private void place(MenuElement element, MenuGeometry.Rect rect) {
        if (rect.w() > 0 && rect.h() > 0 && MenuGeometry.overflows(rect, width, height)) {
            overflow.add(label(element) + "(" + rect.x() + "," + rect.y() + " " + rect.w() + "x" + rect.h() + ")");
        }
        switch (element.type()) {
            case BUTTON -> addRenderableWidget(new MenuButton(
                    rect.x(),
                    rect.y(),
                    Math.max(1, rect.w()),
                    Math.max(1, rect.h()),
                    text(element),
                    theme,
                    element.color(),
                    () -> MenuActions.run(this, element.action(), text(element))));
            case LABEL -> {
                if (rect.w() > 0 && rect.h() > 0) labels.add(new PlacedLabel(element, text(element), rect));
            }
            case IMAGE -> {
                FileTexture texture = imageTexture(element);
                if (texture != null && rect.w() > 0 && rect.h() > 0) {
                    FrameClock clock = element.animatedImage()
                            ? new FrameClock(
                                    element.sheet().frameCount(),
                                    element.sheet().scaledFrameMs(),
                                    element.sheet().loop())
                            : null;
                    images.add(new PlacedImage(texture, rect, clock, Util.getMillis(), element));
                }
            }
            case COLUMN -> placeColumn(element, rect);
        }
    }

    private void placeColumn(MenuElement column, MenuGeometry.Rect rect) {
        List<MenuElement> children = column.column().children();
        MenuGeometry.Stack stack = stackOf(column);
        // 色带要盖住「按钮们实际占据的范围」，所以先摆一遍子元素、顺手把按钮的范围量出来
        int buttonLeft = Integer.MAX_VALUE;
        int buttonRight = Integer.MIN_VALUE;
        for (int i = 0; i < children.size(); i++) {
            MenuElement child = children.get(i);
            MenuGeometry.Rect childRect =
                    stack.childRects().get(i).translate(rect.x(), rect.y()).translate(child.offsetX(), child.offsetY());
            if (child.type() == MenuElement.Type.BUTTON) {
                buttonLeft = Math.min(buttonLeft, childRect.x());
                buttonRight = Math.max(buttonRight, childRect.x2());
            }
            place(child, childRect);
        }
        MenuElement.Bar bar = column.column().bar();
        if (bar != null) {
            boolean buttons = !bar.fullColumnWidth() && buttonLeft <= buttonRight;
            int x = buttons ? buttonLeft - bar.padding() : rect.x();
            int w = buttons ? (buttonRight - buttonLeft) + bar.padding() * 2 : stack.width();
            // 满屏高：色带是「这一列在这儿」的视觉锚点，跟着列高走会随元素增减忽长忽短
            bars.add(new PlacedBar(new MenuGeometry.Rect(x, 0, Math.max(1, w), this.height), bar.color()));
        }
    }

    /**
     * 加载（并缓存）元素图片的贴图；失败返回 {@code null} 且只警告一次。
     *
     * <p>失败的图片元素**不会**退化成别的样子（不留空按钮、不换占位图）：
     * 它只是不出现，并在日志与 {@code /ccnr_menu status} 里说清是哪个文件找不到。
     */
    private FileTexture imageTexture(MenuElement element) {
        if (textureByElement.containsKey(element)) return textureByElement.get(element);
        if (failedImages.contains(element)) return null;
        try {
            Path file = MenuConfigIO.resolveAsset(element.file());
            if (!Files.isRegularFile(file)) {
                LOGGER.warn("[CCNR-Menu] 菜单图片不存在: {}（把文件放进 {}）", file, MenuConfigIO.configDir());
                failedImages.add(element);
                return null;
            }
            FileTexture texture = FileTexture.load(file, FileTexture.keyFor(file, MenuConfigIO.lastModified(file)));
            textureByElement.put(element, texture);
            elementTextures.add(texture);
            return texture;
        } catch (Exception e) {
            LOGGER.warn("[CCNR-Menu] 菜单图片加载失败: {} —— {}", element.file(), e.toString());
            failedImages.add(element);
            return null;
        }
    }

    /**
     * 原版那套按钮（{@code vanillaButtons: true}）。
     *
     * <p>位置与尺寸照抄原版主菜单：单人/多人各 200 宽、居中，下面两行各两个 98 宽的按钮
     * （设置 | 退出游戏，Mods | 空位）。这样「只想换背景」的人得到的是**熟悉的菜单**，
     * 不会因为按钮挪了位置而找不到东西。
     *
     * <p>刻意**不做** Realms 按钮（界面类不在客户端 jar 里，见 {@code MenuAction.SCREENS} 的注释）。
     */
    private void buildVanillaButtons() {
        int centerX = this.width / 2 - 100;
        int firstRow = this.height / 4 + 48;

        addRenderableWidget(
                vanillaButton(centerX, firstRow, 200, "menu.singleplayer", MenuAction.screen("singleplayer")));
        addRenderableWidget(vanillaButton(
                centerX, firstRow + VANILLA_ROW_HEIGHT, 200, "menu.multiplayer", MenuAction.screen("multiplayer")));
        addRenderableWidget(vanillaButton(
                centerX, firstRow + VANILLA_ROW_HEIGHT * 2, 98, "menu.options", MenuAction.screen("options")));
        addRenderableWidget(vanillaButton(
                this.width / 2 + 2, firstRow + VANILLA_ROW_HEIGHT * 2, 98, "menu.quit", MenuAction.QUIT_ACTION));
        addRenderableWidget(vanillaButton(
                centerX, firstRow + VANILLA_ROW_HEIGHT * 3, 98, "fml.menu.mods", MenuAction.screen("mods")));
    }

    private MenuButton vanillaButton(int x, int y, int width, String langKey, MenuAction action) {
        return new MenuButton(
                x,
                y,
                width,
                20,
                Component.translatable(langKey),
                theme,
                MenuElement.NO_COLOR,
                () -> MenuActions.run(this, action, Component.translatable(langKey)));
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 背景与压暗层走共享入口（与其它界面同一份实现，避免两条渲染路径漂移）
        ScreenBackgrounds.render(gfx, this, MenuConfigStore.current().config(), true, partialTick);

        for (PlacedBar bar : bars) {
            gfx.fill(bar.rect().x(), bar.rect().y(), bar.rect().x2(), bar.rect().y2(), bar.color());
        }
        for (PlacedImage image : images) {
            drawImage(gfx, image);
        }
        PoseStack pose = gfx.pose();
        for (PlacedLabel label : labels) {
            float scale = label.element().scale();
            // 缩放的文字按**缩放后**的矩形摆放：否则 scale>1 的标题会偏到左边
            pose.pushPose();
            pose.translate(label.rect().x() / scale, label.rect().y() / scale, 0f);
            pose.scale(scale, scale, 1f);
            gfx.drawString(
                    this.font,
                    label.text(),
                    0,
                    0,
                    theme.labelColor(label.element().color()),
                    label.element().shadow());
            pose.popPose();
        }

        // 刻意不调用 super.render：它会画泥土/渐变背景，把动画背景盖掉
        for (Renderable renderable : this.renderables) {
            renderable.render(gfx, mouseX, mouseY, partialTick);
        }

        // 淡入：盖一层逐渐透明的黑（放在最后，于是背景与按钮一起淡入）
        float fade = fadeAlpha();
        if (fade < 1f) {
            gfx.fill(0, 0, this.width, this.height, ((int) ((1f - fade) * 255f)) << 24);
        }
    }

    /** 画一个图片元素：静态图直接用整张，序列帧按时间取当前帧的 UV。 */
    private void drawImage(GuiGraphics gfx, PlacedImage image) {
        FileTexture texture = image.texture();
        MenuGeometry.Rect src = new MenuGeometry.Rect(0, 0, texture.width(), texture.height());
        if (image.clock() != null && image.element().sheet() != null) {
            int frame = image.clock().frameAt(Util.getMillis() - image.startMs());
            src = MenuGeometry.frameUv(
                    frame,
                    image.element().sheet().cols(),
                    image.element().sheet().rows(),
                    texture.width(),
                    texture.height());
        }
        TextureDraw.blit(gfx, texture.location(), src, image.rect(), texture.width(), texture.height());
    }

    /** 淡入系数：0 → 1。 */
    private float fadeAlpha() {
        long elapsed = Util.getMillis() - openedAt;
        return Math.min(1f, elapsed / (float) FADE_IN_MS);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void removed() {
        // 只释放自己拥有的东西（元素贴图）；背景是共享实例，不归本屏幕管
        releaseElementTextures();
    }

    private void releaseElementTextures() {
        for (FileTexture texture : elementTextures) {
            texture.close();
        }
        elementTextures.clear();
        textureByElement.clear();
        failedImages.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // 主菜单上一个「按 Esc 就消失」的界面会露出黑屏，原版也不允许
        return false;
    }

    /** 元素的可读名字（日志用：按钮给文案，其它给类型）。 */
    private static String label(MenuElement element) {
        return element.type() == MenuElement.Type.BUTTON && !element.text().isBlank()
                ? element.text()
                : element.type().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 配置里的文字：{@code ccnr_menu.} 前缀按语言键翻译，否则按字面量显示。 */
    private static Component text(MenuElement element) {
        return element.isLangKey() ? Component.translatable(element.text()) : Component.literal(element.text());
    }

    /** 已摆好位置的色带。 */
    private record PlacedBar(MenuGeometry.Rect rect, int color) {}

    /** 已摆好位置的文字元素。 */
    private record PlacedLabel(MenuElement element, Component text, MenuGeometry.Rect rect) {}

    /** 已摆好位置的图片元素（{@code clock} 非空表示这是序列帧动画）。 */
    private record PlacedImage(
            FileTexture texture, MenuGeometry.Rect rect, FrameClock clock, long startMs, MenuElement element) {}
}
