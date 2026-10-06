/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 动作解析与**白名单**门禁。
 *
 * <p>这些断言是安全边界的一部分，不只是「格式校验」：
 * {@code url} 动作最终会把这个字符串交给 {@code Util.getPlatform().openUri}，
 * 也就是交给操作系统的默认程序。配置可以被人转发（服主发一份「好看的菜单」给玩家），
 * 所以「只允许 http/https」必须在测试里被钉死，而不是写在注释里。
 */
class MenuActionTest {

    @Test
    @DisplayName("空/缺省动作 = 什么都不做")
    void noneVariants() {
        assertEquals(MenuAction.Kind.NONE, MenuAction.parse(null, null).kind());
        assertEquals(MenuAction.Kind.NONE, MenuAction.parse("", null).kind());
        assertEquals(MenuAction.Kind.NONE, MenuAction.parse("none", null).kind());
    }

    @Test
    @DisplayName("退出游戏")
    void quit() {
        assertEquals(MenuAction.Kind.QUIT, MenuAction.parse("quit", null).kind());
    }

    @Test
    @DisplayName("原版界面：screen:xxx 与 vanilla:xxx 两种写法都认，且大小写不敏感")
    void screenVariants() {
        assertEquals("options", MenuAction.parse("screen:options", null).value());
        assertEquals("mods", MenuAction.parse("vanilla:mods", null).value());
        assertEquals(
                "singleplayer", MenuAction.parse("SCREEN:SinglePlayer", null).value());
    }

    @Test
    @DisplayName("action 与 value 分开写（value 提供界面 id）也成立")
    void screenWithSeparateValue() {
        assertEquals("language", MenuAction.parse("screen", "language").value());
    }

    @Test
    @DisplayName("不在白名单里的界面 id 直接拒绝（含 realms：那个界面类不在客户端 jar 里）")
    void unknownScreenRejected() {
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("screen:realms", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("screen:exit", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.screen("realms"));
    }

    @Test
    @DisplayName("直连地址：主机名与可选端口，端口超范围拒绝")
    void connectAddresses() {
        assertEquals(
                "play.example.com",
                MenuAction.parse("connect:play.example.com", null).value());
        assertEquals(
                "127.0.0.1:25565",
                MenuAction.parse("connect", "127.0.0.1:25565").value());
        assertEquals(
                "mc.example.com:25565",
                MenuAction.parse("join:mc.example.com:25565", null).value());

        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("connect:play.example.com:99999", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("connect:", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("connect:has space.com", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("connect:mc.example.com/path", null));
    }

    @Test
    @DisplayName("链接：只接受 http/https（file:、javascript: 一律拒绝）")
    void urlWhitelist() {
        assertEquals(
                "https://example.com/a",
                MenuAction.parse("url:https://example.com/a", null).value());
        assertEquals(
                "http://example.com",
                MenuAction.parse("url", "http://example.com").value());

        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("url:file:///etc/passwd", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("url:javascript:alert(1)", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("url:ftp://example.com", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("url:http://", null));
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("url:http:///nohost", null));
        assertFalse(MenuAction.isValidWebUrl("HTTPS://exa mple.com"));
    }

    @Test
    @DisplayName("链接里的空白与危险字符一律拒绝（避免把垃圾塞给系统浏览器）")
    void urlRejectsWhitespaceAndControlChars() {
        assertFalse(MenuAction.isValidWebUrl("https://example.com/a b"));
        assertFalse(MenuAction.isValidWebUrl("https://example.com/\"quote\""));
        assertFalse(MenuAction.isValidWebUrl("https://example.com/<tag>"));
        // 内嵌的控制字符必须拒绝；首尾空白则先 trim 再判（配置里常有多余空格，
        // 而 trim 之后交给系统浏览器的就是干净地址）
        assertFalse(MenuAction.isValidWebUrl("https://example.com/a\nb"));
        assertTrue(MenuAction.isValidWebUrl("  https://example.com/a  "));
    }

    @Test
    @DisplayName("复制文本：必须给出非空 value")
    void copyNeedsValue() {
        assertEquals(
                MenuAction.Kind.COPY,
                MenuAction.parse("copy:play.example.com", null).kind());
        assertEquals("hello", MenuAction.parse("copy", "hello").value());
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("copy", "   "));
    }

    @Test
    @DisplayName("完全不认识的动作要抛异常（调用方据此跳过按钮并警告）")
    void unknownActionRejected() {
        assertThrows(IllegalArgumentException.class, () -> MenuAction.parse("teleport", null));
    }

    @Test
    @DisplayName("地址校验的边界：端口 1 与 65535 合法，0 与 65536 非法")
    void addressBoundaries() {
        assertTrue(MenuAction.isValidAddress("host:1"));
        assertTrue(MenuAction.isValidAddress("host:65535"));
        assertTrue(MenuAction.isValidAddress("host"));
        assertFalse(MenuAction.isValidAddress("host:0"));
        assertFalse(MenuAction.isValidAddress("host:65536"));
        assertFalse(MenuAction.isValidAddress("host:-1"));
        assertFalse(MenuAction.isValidAddress(""));
    }

    @Test
    @DisplayName("describe() 输出可用于诊断（状态命令直接打印它）")
    void describe() {
        assertEquals("none", MenuAction.parse("none", null).describe());
        assertEquals(
                "CONNECT(play.example.com)",
                MenuAction.parse("connect:play.example.com", null).describe());
    }
}
