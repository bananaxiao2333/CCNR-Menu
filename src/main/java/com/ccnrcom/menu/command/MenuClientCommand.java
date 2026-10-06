/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.command;

import com.ccnrcom.menu.client.MenuScreen;
import com.ccnrcom.menu.config.BackgroundSpec;
import com.ccnrcom.menu.config.MarkSpec;
import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuConfigStore;
import com.ccnrcom.menu.config.MenuElement;
import com.ccnrcom.menu.config.SlideSpec;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 客户端命令 {@code /ccnr_menu}。
 *
 * <p>为什么必须有一个命令：这个模组的所有输入都是**文件**，而这个文件在 {@code config/ccnr_menu/} 里。
 * 没有命令时，玩家排查「我配的背景为什么没生效」只能靠翻日志；有了它，
 * {@code /ccnr_menu status} 就能把「当前读到的是什么」摊在聊天框里——
 * 缺哪个文件、哪个字段不认识、最终退化成了哪种背景，一目了然。
 *
 * <p>是**客户端命令**（{@link RegisterClientCommandsEvent}）：它不需要服务端授权，
 * 也就不该出现在服务器的命令列表里。
 */
@Mod.EventBusSubscriber(modid = MenuConfig.MOD_ID, value = Dist.CLIENT)
public final class MenuClientCommand {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private MenuClientCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ccnr_menu")
                .executes(MenuClientCommand::status)
                .then(Commands.literal("status").executes(MenuClientCommand::status))
                .then(Commands.literal("reload").executes(ctx -> {
                    MenuConfigIO.LoadResult result = MenuConfigStore.reload();
                    // 正开着自定义菜单时顺带重建屏幕：背景是按配置指纹共享的、会自己跟着换，
                    // 但**元素布局**是屏幕构造时算好的，不重建就会「背景变了、按钮还是旧的」
                    if (Minecraft.getInstance().screen instanceof MenuScreen) {
                        Minecraft.getInstance().setScreen(new MenuScreen(result.config()));
                    }
                    reply(
                            ctx,
                            Component.translatable(
                                    "ccnr_menu.command.reloaded",
                                    result.config().background().kind().name().toLowerCase(java.util.Locale.ROOT),
                                    result.warnings().size()));
                    return 1;
                }))
                .then(Commands.literal("open").executes(ctx -> {
                    MenuConfigIO.LoadResult result = MenuConfigStore.current();
                    Minecraft.getInstance().setScreen(new MenuScreen(result.config()));
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    // 显式恢复出厂：迁移只处理「从未改过」的文件，改过的人需要这个入口
                    boolean ok = MenuConfigIO.resetToDefault();
                    if (!ok) {
                        reply(ctx, Component.translatable("ccnr_menu.command.reset_failed"));
                        return 0;
                    }
                    MenuConfigIO.LoadResult result = MenuConfigStore.reload();
                    if (Minecraft.getInstance().screen instanceof MenuScreen) {
                        Minecraft.getInstance().setScreen(new MenuScreen(result.config()));
                    }
                    reply(ctx, Component.translatable("ccnr_menu.command.reset_ok"));
                    return 1;
                }))
                .then(Commands.literal("where").executes(ctx -> {
                    reply(ctx, Component.literal(MenuConfigIO.configDir().toString()));
                    return 1;
                }));
        event.getDispatcher().register(root);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        MenuConfigStore.reloadIfChanged();
        MenuConfigIO.LoadResult result = MenuConfigStore.current();
        MenuConfig config = result.config();

        reply(ctx, Component.translatable("ccnr_menu.command.status.header"));
        reply(
                ctx,
                Component.literal("  配置文件: " + result.file() + (Files.isRegularFile(result.file()) ? "" : "  [不存在]")));
        reply(ctx, Component.literal("  生效: " + (config.enabled() ? "是" : "否（已按原版菜单运行）")));
        reply(
                ctx,
                Component.literal("  按钮: "
                        + (config.vanillaButtons()
                                ? "原版（vanillaButtons=true）"
                                : "自定义 " + config.buttonCount() + " 个 / 元素 " + config.elementCount() + " 个（含容器内）")
                        + "  外观="
                        + config.theme().buttonStyle().name().toLowerCase(java.util.Locale.ROOT)));
        reply(
                ctx,
                Component.literal("  背景范围: "
                        + (config.applyToAllScreens() ? "主菜单 + 所有泥土背景界面（世界内的界面不动）" : "仅主菜单（applyToAllScreens=false）")));

        BackgroundSpec background = config.background();
        StringBuilder bg = new StringBuilder("  背景: " + background.kind().name().toLowerCase(java.util.Locale.ROOT));
        if (background.needsFile()) {
            bg.append("  file=").append(background.file());
            bg.append("  ").append(fileState(background));
            bg.append("  fit=").append(background.fit().name().toLowerCase(java.util.Locale.ROOT));
            bg.append("  opacity=").append(String.format(java.util.Locale.ROOT, "%.2f", background.opacity()));
            if (background.kind() == BackgroundSpec.Kind.SHEET) {
                bg.append("  网格=")
                        .append(background.sheet().cols())
                        .append('x')
                        .append(background.sheet().rows());
                bg.append("  帧数=").append(background.sheet().frameCount());
                bg.append("  每帧=").append(background.sheet().scaledFrameMs()).append("ms");
            }
        }
        reply(ctx, Component.literal(bg.toString()));

        if (background.kind() == BackgroundSpec.Kind.SLIDESHOW) {
            SlideSpec slide = background.slide();
            reply(
                    ctx,
                    Component.literal("    轮播: " + background.slides().size() + " 张"
                            + "  每张=" + slide.holdMs() + "ms"
                            + "  交叉淡入=" + slide.fadeMs() + "ms"
                            + "  放大=" + Math.round(slide.zoom() * 100f) + "%"
                            + "  右移=" + Math.round(slide.panX() * 100f) + "%"
                            + (slide.loop() ? "  循环" : "  播完停在最后一张")));
            for (String name : background.slides()) {
                reply(ctx, Component.literal("      | " + name + "  " + assetState(name)));
            }
        }

        MarkSpec mark = config.mark();
        if (mark.enabled()) {
            reply(
                    ctx,
                    Component.literal("  标志: " + mark.file()
                            + (mark.width() == MarkSpec.AUTO ? "  width=原图" : "  width=" + mark.width())
                            + "  不透明度=" + String.format(java.util.Locale.ROOT, "%.2f", mark.opacity())
                            + "  锚点=" + mark.align().name().toLowerCase(java.util.Locale.ROOT) + "/"
                            + mark.valign().name().toLowerCase(java.util.Locale.ROOT)
                            + String.format(java.util.Locale.ROOT, "  x=%.3f  y=%.3f", mark.x(), mark.y())
                            + (mark.onMainMenu() ? "  主菜单上也画" : "  只画在泥土界面")
                            + "  " + assetState(mark.file())));
        }

        for (MenuElement element : config.elements()) {
            reply(
                    ctx,
                    Component.literal("    - " + element.type().name().toLowerCase(java.util.Locale.ROOT)
                            + " '" + element.text() + "' " + element.action().describe()
                            + " @" + round(element.x()) + "," + round(element.y())));
        }

        if (result.warnings().isEmpty()) {
            reply(ctx, Component.translatable("ccnr_menu.command.status.no_warnings"));
        } else {
            reply(
                    ctx,
                    Component.translatable(
                            "ccnr_menu.command.status.warnings",
                            result.warnings().size()));
            for (String warning : result.warnings()) {
                reply(ctx, Component.literal("  ! " + warning));
            }
        }
        LOGGER.info(
                "[CCNR-Menu] /ccnr_menu status 已输出（{} 条警告）", result.warnings().size());
        return 1;
    }

    /** 素材文件是否存在（诊断输出必须说清「没生效」是因为文件不在）。 */
    private static String fileState(BackgroundSpec background) {
        try {
            Path file = MenuConfigIO.resolveAsset(background.file());
            return Files.isRegularFile(file) ? "[已找到]" : "[文件不存在]";
        } catch (IllegalArgumentException e) {
            return "[路径越界]";
        }
    }

    /** 单个素材名的状态（轮播与标志共用）。 */
    private static String assetState(String name) {
        try {
            Path file = MenuConfigIO.resolveAsset(name);
            return Files.isRegularFile(file) ? "[已找到]" : "[文件不存在]";
        } catch (IllegalArgumentException e) {
            return "[路径越界]";
        }
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }
}
