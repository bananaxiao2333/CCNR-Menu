/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.ui.MenuTheme;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 全模组**唯一**的背景持有者与绘制入口。
 *
 * <p>为什么做成共享实例，而不是「每个屏幕各自建一个」：背景（尤其动画 GIF）解码一次要几百毫秒、
 * 解码后可能占几十 MB。主菜单 → 设置 → 返回主菜单这条最常见的路径上，如果每个屏幕各自建一份，
 * 就会**两次重复解码**同一张图。这里按「配置指纹」持有唯一实例，谁要画都走 {@link #render}。
 *
 * <p>两条生命周期规则：
 * <ol>
 *   <li><b>指纹变了就重建</b>：指纹 = 背景的全部参数 + 素材文件的修改时间。玩家改了配置、
 *       或换掉了背景文件，下一次绘制就会拿到新背景（不需要重启，也不需要手动清缓存）。</li>
 *   <li><b>进入世界时释放</b>：玩家进游戏之后几乎不会再看到泥土界面，
 *       这时把几十 MB 的动画帧交还给系统；下次真的需要（例如退出世界回到标题）会按需重建。</li>
 * </ol>
 *
 * <p>绘制顺序也是在这里定的：**背景 → 压暗层**。压暗层是文字可读性的保障
 * （高亮背景会让白字看不清），它跟着配置里的 {@code theme.backdrop} 走。
 * 原版界面的控件随后由原版自己画在这一层之上。
 */
public final class ScreenBackgrounds {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private static String signature;
    private static BackgroundRenderer renderer;
    private static MenuTheme theme;

    private ScreenBackgrounds() {}

    /**
     * 为一个界面绘制背景（含压暗层）。不满足接管条件时**什么都不做**（原版背景照旧）。
     *
     * @param config 当前生效配置（调用方应传 {@code MenuConfigStore.current()}，
     *     这样两个绘制点用的是同一份，不会来回触发重建）
     */
    public static void render(GuiGraphics gfx, Screen screen, MenuConfig config, boolean ownMenu, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        if (!BackgroundScope.shouldApply(ownMenu, inWorld, config.enabled(), config.applyToAllScreens())) return;

        int width = screen.width;
        int height = screen.height;
        if (width <= 0 || height <= 0) return;

        ensure(config);
        if (renderer == null) return;
        renderer.render(gfx, width, height, partialTick, 1f);
        if (theme != null) theme.drawBackdrop(gfx, width, height, 1f);
    }

    /** 按配置指纹确保实例存在（指纹变了就换掉旧的）。 */
    private static void ensure(MenuConfig config) {
        String wanted = signatureOf(config);
        if (wanted.equals(signature)) return;

        close();
        signature = wanted;
        theme = new MenuTheme(config.theme());
        renderer = BackgroundFactory.create(config.background());
        LOGGER.debug("[CCNR-Menu] 背景已重建（指纹 {}）: {}", wanted, config.background().kind());
    }

    /**
     * 背景指纹：所有影响画面的参数 + 素材文件的修改时间 + **配色**。
     *
     * <p>带上修改时间是为了「作者换了图但参数没变」这种情况——只看参数的话，
     * 玩家会以为模组坏了（改了文件却毫无反应）。
     *
     * <p>带上配色是因为压暗层（{@code theme.backdrop}）也由这个实例持有：
     * 只改配色不改背景时，指纹不变就不会重建，改了 {@code theme} 却看不到变化。
     * {@link MenuConfig#theme()} 与 {@link BackgroundSpec#text()} 都是纯数据
     * （int + 枚举 + 字符串），{@code signature()} 里刻意不放过任何数组字段。
     */
    private static String signatureOf(MenuConfig config) {
        BackgroundSpec spec = config.background();
        StringBuilder sb = new StringBuilder();
        sb.append(spec.kind())
                .append('|')
                .append(spec.fit())
                .append('|')
                .append(spec.tint())
                .append('|')
                .append(spec.opacity())
                .append('|')
                .append(spec.color())
                .append('|')
                .append(spec.sheet().cols())
                .append('x')
                .append(spec.sheet().rows())
                .append('@')
                .append(spec.sheet().frameMs())
                .append('*')
                .append(spec.sheet().speed())
                .append(spec.sheet().loop() ? 'L' : 'O')
                .append('|')
                .append(spec.text().signature())
                .append('|')
                .append(config.theme())
                .append('|');
        if (spec.needsFile()) {
            try {
                Path file = MenuConfigIO.resolveAsset(spec.file());
                sb.append(file).append('@').append(MenuConfigIO.lastModified(file));
            } catch (IllegalArgumentException e) {
                sb.append("bad:").append(spec.file());
            }
        }
        return sb.toString();
    }

    /** 释放当前背景（进入世界、或配置变更时调用）；幂等。 */
    public static void release() {
        close();
        signature = null;
    }

    private static void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        theme = null;
    }
}
