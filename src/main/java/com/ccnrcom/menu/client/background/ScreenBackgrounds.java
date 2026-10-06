/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.client.ui.MenuTheme;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.BootLogSpec;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuConfigStore;
import com.ccnrcom.menu.ui.BootLogKind;
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

    /** 指纹多久复查一次（按**绘制帧数**算，不引时钟；约 2 秒）。 */
    private static final int STAT_INTERVAL_FRAMES = 120;

    private static String signature;
    private static BackgroundRenderer renderer;
    private static MarkRenderer mark;
    /**
     * 启动日志那一层。层级是**背景图 → 日志 → 图标**：日志压在背景之上、标志与元素之下。
     * 它不能做成一种背景类型——那样所有元素都只能压在它上面，正好反了。
     */
    private static BootLogRenderer bootLog;

    private static MenuTheme theme;

    /** 上一次算指纹用的配置对象（身份比较：同一个对象就不必再去读一遍磁盘）。 */
    private static MenuConfig lastConfig;

    /** 距离下一次复查素材 mtime 还有几帧。 */
    private static int statCountdown;

    /**
     * 本帧已经画过背景的那个 {@link GuiGraphics}——用来保证**一帧只画一层**。
     *
     * <p>背景有两个可能触发点（界面的 {@code renderBackground}、列表的 {@code render}），
     * 有些界面两个都会走（`JoinMultiplayerScreen` 就是），叠两遍会把压暗层与半透明水印
     * 画深一倍。判据只能是「同一帧」，而 {@code GameRenderer.render} 每帧都
     * {@code new GuiGraphics(...)} 再交给界面，所以**对象身份就是帧身份**。
     */
    private static GuiGraphics lastGfx;

    private ScreenBackgrounds() {}

    /**
     * 这一帧的背景是不是已经在同一个 {@link GuiGraphics} 上画过了。
     *
     * @see #lastGfx 为什么对象身份可以当帧号用
     */
    public static boolean alreadyDrawnThisFrame(GuiGraphics gfx) {
        return lastGfx == gfx;
    }

    /**
     * 为一个界面绘制背景（含压暗层与居中标志）。不满足接管条件时**什么都不做**（原版背景照旧）。
     *
     * @param config 当前生效配置（调用方应传 {@code MenuConfigStore.current()}，
     *     这样两个绘制点用的是同一份，不会来回触发重建）
     */
    public static void render(GuiGraphics gfx, Screen screen, MenuConfig config, boolean ownMenu, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null;
        // 加载类界面（创建世界、加载地形、连接服务器）永远接管：那些屏幕上没有「玩家要看的世界」，
        // 而创建世界时 level 在**地形加载完之前**就已经非 null，只看 inWorld 会把它们误判成世界内
        if (!BackgroundScope.shouldApply(
                ownMenu,
                BackgroundScope.isLoadingScreen(screen, config.background().extraLoadingScreens()),
                inWorld,
                config.enabled(),
                config.applyToAllScreens())) return;

        int width = screen.width;
        int height = screen.height;
        if (width <= 0 || height <= 0) return;

        ensure(config);
        // 层级（下往上）：背景图 → 启动日志 → 压暗层 → 图标/标志。
        // 日志在压暗层**之下**是刻意的：它是背景的一部分，不该亮过前景文字。
        if (renderer != null) renderer.render(gfx, width, height, partialTick, 1f);
        if (bootLog != null) bootLog.render(gfx, minecraft.font, width, height, 1f, ownMenu);
        if (theme != null) theme.drawBackdrop(gfx, width, height, 1f);
        // 标志画在压暗层**之上**：一个白色 logo 被压成灰的，看起来就像没加载出来。
        // 让位的判据只能是**正在等待**那个瞬态，绝不能是「日志里有没有行」——
        // 后者只增不减（按一个按钮就多一行），而要走到非主菜单的界面又必须先按一次按钮，
        // 于是「非主菜单上的居中标志」在第一次点击之后就永远不再出现（实测踩过）。
        if (mark != null && !waiting()) mark.render(gfx, width, height, 1f, ownMenu);
        lastGfx = gfx; // 记下「这一帧已经画过了」，见 alreadyDrawnThisFrame
    }

    /**
     * 按配置指纹确保实例存在（指纹变了就换掉旧的）。
     *
     * <p>这里有两级缓存，都是为了**别在渲染循环里碰磁盘**：① 配置对象身份没变就不重算指纹；
     * ② 身份没变也每 {@value #STAT_INTERVAL_FRAMES} 帧复查一次素材 mtime
     * （「作者换了图但参数没变」也必须能被发现，否则看起来就像模组坏了）。
     * 每帧都去读一遍素材 mtime 是每帧十几次 stat 系统调用，纯属白烧。
     */
    private static void ensure(MenuConfig config) {
        if (config == lastConfig && statCountdown-- > 0) return;
        statCountdown = STAT_INTERVAL_FRAMES;
        lastConfig = config;

        String wanted = signatureOf(config);
        if (wanted.equals(signature)) return;

        close();
        signature = wanted;
        theme = new MenuTheme(config.theme());
        renderer = BackgroundFactory.create(config.background());
        mark = new MarkRenderer(config.mark());
        bootLog = config.bootLog().hasSource() ? new BootLogRenderer(config.bootLog()) : null;
        LOGGER.debug("[CCNR-Menu] 背景已重建（指纹 {}）: {}", wanted, config.background().kind());
    }

    /**
     * 背景指纹：所有影响画面的参数 + 素材文件的修改时间 + **配色** + **居中标志**。
     *
     * <p>带上修改时间是为了「作者换了图但参数没变」这种情况——只看参数的话，
     * 玩家会以为模组坏了（改了文件却毫无反应）。
     *
     * <p>带上配色是因为压暗层（{@code theme.backdrop}）也由这个实例持有：
     * 只改配色不改背景时，指纹不变就不会重建，改了 {@code theme} 却看不到变化。
     * {@link MenuConfig#theme()} 与 {@link MenuConfig#mark()} 都是纯数据（int + 枚举 + 字符串），
     * 不会出现「同一个配置两次指纹不同」的身份哈希问题。
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
                .append(spec.slide())
                .append('|')
                // 追加的加载类界面也进指纹：它决定「哪些界面会被接管」，漏了就是「改了配置没反应」
                .append(String.join(",", spec.extraLoadingScreens()))
                .append('|')
                .append(config.theme())
                .append('|')
                .append(config.mark())
                .append('|')
                // 启动日志要进指纹：改了驱动文件或字段却不重建的话，日志会停在旧内容上
                .append(bootLogSignature(config.bootLog()))
                .append('|');
        for (String asset : spec.assetFiles()) {
            try {
                Path file = MenuConfigIO.resolveAsset(asset);
                sb.append(file)
                        .append('@')
                        .append(MenuConfigIO.lastModified(file))
                        .append(';');
            } catch (IllegalArgumentException e) {
                sb.append("bad:").append(asset).append(';');
            }
        }
        return sb.toString();
    }

    /**
     * 启动日志的指纹：只带**参数**（内容由驱动文件的 mtime 决定，而 mtime 已经在上面
     * 逐个 {@code assetFiles()} 进过指纹了——但那是背景的素材，不是日志的驱动）。
     * 所以这里必须把驱动文件自己的 mtime 也算进来，否则「改了驱动文件没反应」。
     */
    private static String bootLogSignature(BootLogSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(spec.enabled())
                .append('|')
                .append(spec.builtin())
                .append('|')
                .append(spec.file())
                .append('|')
                .append(spec.spacingMs())
                .append('|')
                .append(spec.scale())
                .append('|')
                .append(spec.x())
                .append('|')
                .append(spec.y())
                .append('|')
                .append(spec.align())
                .append('|')
                .append(spec.valign())
                .append('|')
                .append(spec.color())
                .append('|')
                .append(spec.meter())
                .append('|')
                .append(spec.onAllScreens())
                .append('|')
                .append(spec.spinnerWidth())
                .append('|');
        for (String line : spec.spinner()) sb.append(line).append('\u0001');
        if (!spec.file().isEmpty()) {
            try {
                Path file = MenuConfigIO.resolveAsset(spec.file());
                sb.append(file).append('@').append(MenuConfigIO.lastModified(file));
            } catch (IllegalArgumentException e) {
                sb.append("bad:").append(spec.file());
            }
        }
        return sb.toString();
    }

    /**
     * 现在是不是真有「正在等待」的进度条（连接服务器、加载存档）——只有这时才给标志让位。
     *
     * <p>判据必须落在**瞬态**上。曾经用的是「日志里有没有事件行」（{@code feed.size() > 0}），
     * 而那个计数只增不减：按一个按钮就多一行，而任何非主菜单的界面都得先按一次主菜单按钮才能到达。
     * 结果是标志在主菜单之外**从来不会出现**——配置里 {@code mark.onMainMenu: false} 的用意
     * （「主菜单有自己的图标，这块只给别的界面」）被整个抵消掉了。
     */
    private static boolean waiting() {
        return bootLog != null && bootLog.feed().spinning();
    }

    /**
     * 混入用的入口：**这一帧该不该由我们接管这块屏幕的背景**。
     *
     * <p>只回答「该不该」，**不画**——绘制的入口有两个：
     * {@link #drawFor(Screen, GuiGraphics)}（泥土那一次 blit 被换掉时调）与
     * {@link #tryDrawForLoading(Screen, GuiGraphics)}（有世界时的渐变分支，只对加载类界面）。
     * 为什么拆开：泥土那一次 blit 的调用点已经拿到了 {@link GuiGraphics}，
     * 而「渐变分支」需要在原版画之前就决定要不要 cancel，两处的时机不同。
     */
    public static boolean shouldReplace(Screen screen) {
        MenuConfig config = MenuConfigStore.current().config();
        boolean inWorld = Minecraft.getInstance().level != null;
        if (!BackgroundScope.shouldOverrideNow(screen, false, inWorld, config.enabled(), config.applyToAllScreens())) {
            return false;
        }
        // 自己的菜单不走这条路（MenuScreen 在 MenuScreen.render 里画，避免同一帧画两遍）
        return !(screen instanceof com.ccnrcom.menu.client.MenuScreen);
    }

    /** 这个界面是不是「加载类」（混入里那条只对加载类生效的分支要用）。 */
    public static boolean isLoadingScreen(Screen screen) {
        MenuConfig config = MenuConfigStore.current().config();
        return BackgroundScope.isLoadingScreen(screen, config.background().extraLoadingScreens());
    }

    /**
     * 诊断用：把范围判定的四项输入摊平成一行。
     *
     * <p>为什么值得留：「某个界面还是泥土」这个症状有四种成因（模组没启用 / 关掉了全局接管 /
     * 被当成世界内界面 / 加载类名单没命中），它们在屏幕上长得一模一样。
     * 把这四项直接写进日志，「没接管」就不用靠猜了。
     */
    public static String scopeReason(Screen screen) {
        MenuConfig config = MenuConfigStore.current().config();
        boolean inWorld = Minecraft.getInstance().level != null;
        return "enabled=" + config.enabled()
                + " applyToAllScreens=" + config.applyToAllScreens()
                + " 世界内=" + inWorld
                + " 加载类="
                + BackgroundScope.isLoadingScreen(screen, config.background().extraLoadingScreens());
    }

    /** 按当前配置画一层背景（泥土 blit 被换掉时调）。 */
    public static void drawFor(Screen screen, GuiGraphics gfx) {
        if (!shouldReplace(screen)) return;
        render(gfx, screen, MenuConfigStore.current().config(), false, 1f);
    }

    /** 渐变分支：是加载类界面就由我们画并返回 true（调用方 cancel 原版）。 */
    public static boolean tryDrawForLoading(Screen screen, GuiGraphics gfx) {
        if (!isLoadingScreen(screen)) return false;
        drawFor(screen, gfx);
        return true;
    }

    /**
     * 记一行事件日志（模组别处调用：按钮点击、连接成功/失败……）。
     *
     * <p>没开启动日志、或还没建出渲染器时**什么都不做**——事件源不该关心「日志层在不在」。
     */
    public static void logEvent(String text, BootLogKind kind) {
        if (bootLog != null) bootLog.feed().log(text, kind);
    }

    /** 开一条「等待中」的进度条（连接服务器、加载存档……）。 */
    public static void spinEvent(String text) {
        if (bootLog != null) bootLog.feed().spin(text);
    }

    /** 等待成功：收掉进度条并记一行 OK。 */
    public static void spinOk(String text) {
        if (bootLog != null) bootLog.feed().spinOk(text);
    }

    /** 等待失败：收掉进度条并记一行 FAILED。 */
    public static void spinFail(String text) {
        if (bootLog != null) bootLog.feed().spinFail(text);
    }

    /** 释放当前背景（进入世界、或配置变更时调用）；幂等。 */
    public static void release() {
        close();
        signature = null;
        lastConfig = null;
        statCountdown = 0;
    }

    private static void close() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (mark != null) {
            mark.close();
            mark = null;
        }
        if (bootLog != null) {
            bootLog.close();
            bootLog = null;
        }
        theme = null;
    }
}
