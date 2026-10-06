/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.mixin;

import com.ccnrcom.menu.client.background.ScreenBackgrounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 列表自己画的那层泥土——**第二个、也是最后一个泥土来源**。
 *
 * <p><b>为什么非拦不可</b>：有些界面**根本不调** {@code Screen.renderBackground()}，
 * 它们的背景完全由列表自己画。原版反编译出来是这样：
 *
 * <pre>
 * SelectWorldScreen.render()                       // 没有 renderBackground
 * ├── list.render()                                // ← 全屏背景就在这里面
 * │   ├── setColor(0.125 灰); blit(dirt, 列表矩形)  // m_280163_（9 参重载）
 * │   ├── 存档条目……
 * │   └── if (renderTopAndBottom)
 * │       setColor(0.25 灰); blit(dirt, 上条); blit(dirt, 下条)   // 补齐整屏
 * ├── searchBox.render(); 标题
 * └── super.render()                               // 按钮
 *
 * JoinMultiplayerScreen.render()                   // 有 renderBackground，由另一个混入处理
 * ├── renderBackground()  ← 这里已经换成我们的背景
 * └── list.render()       ← 于是列表那层泥土纯属多余，还会把我们的背景重新盖成灰的
 * </pre>
 *
 * <p>于是这里做两件事，判定都走 {@link ScreenBackgrounds#shouldReplace}（与另一个混入同一份判据）：
 * <ol>
 *   <li><b>列表的泥土一律不画</b>——接管了这个界面，背景就该由我们负责；</li>
 *   <li><b>整屏背景在这里补一次</b>，但**只在同帧还没画过的时候**——
 *       {@code JoinMultiplayerScreen} 那条路已经由 {@code renderBackground} 画过了，
 *       再画一次会把压暗层与半透明标志叠两遍（水印会明显变深）。</li>
 * </ol>
 *
 * <p><b>「同帧」判据为什么能成立</b>：{@code GameRenderer.render} 每帧都
 * {@code new GuiGraphics(...)} 再交给界面渲染，所以**同一个 GuiGraphics 对象就是同一帧**。
 * 见 {@link ScreenBackgrounds#alreadyDrawnThisFrame}。这里没有别的帧计数器可用，
 * 又必须精确到帧（差一帧就是两遍水印），所以用对象身份。
 *
 * <p>SRG 名同样对着运行时 jar 查（{@code m_88315_} / {@code m_280163_}），
 * 且 {@code @Mixin(remap = false)}——理由与 {@link ScreenBackgroundMixin} 一致。
 */
@Mixin(value = AbstractSelectionList.class, remap = false)
public abstract class SelectionListBackgroundMixin {

    /**
     * 同帧没画过就补一层整屏背景。
     *
     * <p>HEAD 是唯一可用的时机：列表自己那层泥土与所有条目都在这之后画，
     * 所以背景必然落在它们下面（这正是「混到最底下」那条层级契约）。
     */
    @Inject(method = "m_88315_", at = @At("HEAD"))
    private void ccnr_menu$drawBackgroundForListScreen(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        if (!ScreenBackgrounds.shouldReplace(screen)) return;
        if (ScreenBackgrounds.alreadyDrawnThisFrame(gfx)) return;
        ScreenBackgrounds.drawFor(screen, gfx);
    }

    /**
     * 列表自己那层泥土：接管了这个界面就**不画**，否则照原样画。
     *
     * <p>不接管时必须走原调用（含原参数），否则就是「改了别处的界面」——那比泥土更难查。
     */
    @Redirect(
            method = "m_88315_",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;m_280163_(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void ccnr_menu$suppressListDirt(
            GuiGraphics gfx,
            ResourceLocation texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int texWidth,
            int texHeight) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && ScreenBackgrounds.shouldReplace(screen)) return;
        gfx.blit(texture, x, y, u, v, width, height, texWidth, texHeight);
    }
}
