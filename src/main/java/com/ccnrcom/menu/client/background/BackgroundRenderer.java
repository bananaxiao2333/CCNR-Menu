/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 一类背景的绘制器。
 *
 * <p>生命周期与所有权：由 {@link BackgroundFactory} 创建，由**持有它的屏幕**负责 {@link #close()}。
 * 之所以不让绘制器自己去全局缓存，是因为背景可能很占内存（一张动画 GIF 解码后几十 MB），
 * 而主菜单一关、玩家进了游戏就不该再占着：所有权跟着屏幕走，屏幕关闭即释放，边界最清楚。
 */
public interface BackgroundRenderer {

    /**
     * 绘制整屏背景。
     *
     * @param alpha 整体不透明度（淡入用），实现方应把它叠到自己的不透明度上
     */
    void render(GuiGraphics gfx, int screenWidth, int screenHeight, float partialTick, float alpha);

    /**
     * 是否已经可以绘制。
     *
     * <p>动画 GIF 的解码在工作线程上，返回 {@code false} 时实现方应当画出**兜底背景**
     * （原版全景图），而不是留一片黑——「先黑一下再出现」比「先看到原版再切过去」更像故障。
     */
    default boolean ready() {
        return true;
    }

    /** 释放 GPU 与堆内存资源；**必须幂等**（屏幕可能在重载/关闭时被调多次）。 */
    void close();
}
