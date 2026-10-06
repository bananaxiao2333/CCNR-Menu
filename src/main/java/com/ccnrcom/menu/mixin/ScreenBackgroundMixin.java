/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.mixin;

import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把**泥土背景的那一次绘制**换成配置里的背景——全项目唯一一处混入，也是最底层的一处。
 *
 * <p>原版把「画泥土」收口在两个入口上：
 *
 * <pre>
 * Screen.renderBackground()        → 有世界时画渐变，否则调 renderDirtBackground()
 * Screen.renderDirtBackground()    → setColor(0.25 灰) → blit(整屏泥土) → setColor(白) → 发事件
 * 直接调 renderDirtBackground() 的：GenericDirtMessageScreen、ReceivingLevelScreen
 * </pre>
 *
 * <p>两条入口都拦：blit 那一次换成我们的背景（覆盖「画泥土」的界面），
 * {@code renderBackground} 的 HEAD 只处理**加载类界面**（世界对象已建立时原版走渐变分支，
 * 拦 blit 会漏掉它们——创建世界、加载地形、连接服务器都是这种）。
 *
 * <p><b>为什么注入点是「泥土那一次 blit」而不是方法 HEAD 或渲染事件</b>：
 * 界面的 {@code render()} 第一句通常就是 {@code renderBackground()}，掐掉整个被它调用的方法，
 * 等于把它后面画列表、按钮、标题的代码一并跳过——正是**选项整片消失**那次事故的成因。
 * 换掉那一次贴图就只换像素，方法其余部分照常执行，后面画的一切自然压在我们上面。
 *
 * <p><b>SRG 名必须对着运行时 jar 查，不能靠猜（这里踩过，代价是整个混入静默失效）</b>：
 * 1.20.1 的 {@code GuiGraphics} 有两个长得极像的贴图重载——
 * <pre>
 * m_280398_(ResourceLocation;IIIFFIIII)V   ← 10 参，renderDirtBackground 用的就是这个
 * m_280411_(ResourceLocation;IIIIFFIIII)V  ← 11 参，TextureDraw 用的那个
 * </pre>
 * 第一版把 target 写成了 {@code m_280411_}，于是这个 {@code @Redirect} **一条都命中不到**。
 * 改这里之前先 {@code javap -p net/minecraft/client/gui/GuiGraphics.class} 看一眼。
 *
 * <p><b>日志是这套东西唯一能自查的地方，而且必须两边都记</b>：混入写错、范围判错、
 * 界面根本没调 {@code renderBackground()}——这三种故障在屏幕上长得一模一样（都是泥土）。
 * 所以这里对**每个界面类**各记一行，接管与不接管都记，不接管那行带上四项判据。
 * 排查「某个界面还是泥土」时先看这两行，不要靠猜。
 */
@Mixin(value = Screen.class, remap = false)
public abstract class ScreenBackgroundMixin {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    /** 记过日志的界面（每种界面一行，避免每帧刷屏）。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * 换掉泥土：只拦「往整屏画那张 dirt 贴图」的那一次 blit。
     *
     * <p>回调的参数顺序就是 {@code m_280398_} 的形参顺序，**少一个都会让混入失效**：
     * {@code (texture, x, y, blitOffset, u, v, width, height, texWidth, texHeight)}。
     *
     * <p>{@code remap = false} + SRG 名：不依赖 refmap，也就不存在「开发环境正常、打包后静默失效」那条路。
     */
    @Redirect(
            method = "m_280039_",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;m_280398_(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V"))
    private void ccnr_menu$replaceDirtBlit(
            GuiGraphics gfx,
            ResourceLocation texture,
            int x,
            int y,
            int blitOffset,
            float u,
            float v,
            int width,
            int height,
            int texWidth,
            int texHeight) {
        Screen screen = (Screen) (Object) this;
        String name = screen.getClass().getName();
        if (!ScreenBackgrounds.shouldReplace(screen)) {
            // 不接管：照原样画泥土。**不碰颜色**——原版此刻刚设好 0.25 灰，
            // 而方法末尾那句「复位成白」紧跟在这次调用之后，会照常执行。
            logOnce("skip:" + name, "未接管泥土背景: {}（{}）", name, ScreenBackgrounds.scopeReason(screen));
            gfx.blit(texture, x, y, blitOffset, u, v, width, height, texWidth, texHeight);
            return;
        }
        logOnce("take:" + name, "已接管泥土背景: {}", name);
        // 先把原版压下来的 0.25 灰复位，否则我们的背景也会被压暗。
        // 不需要再复位一次：原版紧跟其后的 setColor(1,1,1,1) 就是这条路径的收尾。
        gfx.setColor(1f, 1f, 1f, 1f);
        ScreenBackgrounds.drawFor(screen, gfx);
    }

    /**
     * 有世界时的渐变背景（原版「世界内界面」那一层）。
     *
     * <p>只对**加载类界面**动手：创建世界时客户端的世界对象在**地形加载完之前**就已存在，
     * 那时原版走的是渐变分支而不是泥土分支，只拦 blit 会漏掉它。
     * 世界内的正常界面（暂停、背包）一律不碰——那是玩家眼前的世界。
     *
     * <p>进这个分支的加载类界面在日志里与泥土那条用**同一个 key**，所以「已接管泥土背景: X」
     * 在两条路径上都只会出现一次，读日志时不用分辨它是从哪条来的。
     */
    @Inject(method = "m_280273_", at = @At("HEAD"), cancellable = true)
    private void ccnr_menu$replaceGradientBackground(GuiGraphics gfx, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!ScreenBackgrounds.isLoadingScreen(screen)) return;
        if (ScreenBackgrounds.tryDrawForLoading(screen, gfx)) {
            logOnce(
                    "take:" + screen.getClass().getName(),
                    "已接管泥土背景: {}",
                    screen.getClass().getName());
            ci.cancel();
        }
    }

    /** 每个界面类的每个结论只记一次（渲染是每帧都调，不设闸门会刷爆日志）。 */
    private static void logOnce(String key, String message, Object... args) {
        if (LOGGED.add(key)) LOGGER.info("[CCNR-Menu] " + message, args);
    }
}
