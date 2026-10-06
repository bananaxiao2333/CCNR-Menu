/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 菜单按钮的动作（纯逻辑，可单测）。
 *
 * <p>动作是**配置里最危险的一栏**：一个 {@code url} 动作会把这个字符串交给
 * {@code Util.getPlatform().openUri}，即交给操作系统的默认程序打开。因此这里对两类值做严格白名单校验：
 *
 * <ul>
 *   <li>{@code url} 只允许 {@code http://} 与 {@code https://}。**刻意拒绝 {@code file:}**——
 *       配置是可以被随意分发的（服主发给玩家一份「好看的菜单」），而 {@code file:} 会让客户端
 *       去打开本机文件（与 CCNR-PM 对皮肤 URL 的处理同一条教训）。</li>
 *   <li>{@code connect} 只允许 {@code 主机名[:端口]}，字符集与端口范围都受限，
 *       否则拼出的地址会在连接阶段抛出难以定位的异常。</li>
 * </ul>
 *
 * @param kind 动作类别
 * @param value 动作参数（{@code SCREEN} 是界面 id、{@code CONNECT} 是服务器地址、{@code URL}/{@code COPY} 是文本）
 */
public record MenuAction(Kind kind, String value) {

    /** 动作类别。 */
    public enum Kind {
        /** 什么都不做（{@code "none"}）。 */
        NONE,
        /** 打开一个原版界面。 */
        SCREEN,
        /** 直连一个服务器。 */
        CONNECT,
        /** 用系统默认浏览器打开链接。 */
        URL,
        /** 复制文本到剪贴板。 */
        COPY,
        /** 退出游戏。 */
        QUIT
    }

    /** 什么都不做的动作。 */
    public static final MenuAction NONE_ACTION = new MenuAction(Kind.NONE, "");

    /** 退出游戏。 */
    public static final MenuAction QUIT_ACTION = new MenuAction(Kind.QUIT, "");

    /**
     * 构造一个「打开原版界面」的动作。
     *
     * <p>给代码内部用（例如 {@code vanillaButtons} 模式要复用同一套界面映射），校验规则与配置解析完全一致——
     * 不许出现「配置里要校验、代码里可以随便传」的第二条路。
     *
     * @throws IllegalArgumentException id 不在 {@link #SCREENS} 里
     */
    public static MenuAction screen(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!SCREENS.contains(normalized)) {
            throw new IllegalArgumentException("不认识的界面 id: '" + id + "'（可用: " + sortedScreens() + "）");
        }
        return new MenuAction(Kind.SCREEN, normalized);
    }

    /**
     * 允许的原版界面 id（{@code screen:<id>} / {@code vanilla:<id>}）。
     *
     * <p>刻意**不含 {@code realms}**：Realms 的界面类不在客户端 jar 里（它在独立的 realms 库里），
     * 直接引用会在编译期就失败；而用反射去开它，代价是「游戏没登录正版账号时点了按钮崩在哪里」说不清。
     * 需要它的整合包可以自己写一个模组加分支。
     */
    public static final Set<String> SCREENS =
            Set.of("singleplayer", "multiplayer", "options", "language", "accessibility", "mods", "credits");

    /** {@code 主机名[:端口]}；主机名允许字母数字与点横线下划线（够覆盖域名、IPv4、SRV 名）。 */
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9._-]{1,253}(:\\d{1,5})?");

    private static final int MAX_URL_LENGTH = 2048;

    /**
     * 动作的**显示标签**（日志用）：{@code connect:example.com} / {@code screen:multiplayer}。
     *
     * <p>为什么放在这里而不是调用方拼：日志里那一行是玩家唯一能核对的线索
     * （「我点的那个按钮到底做了什么」），标签措辞漂了就等于这条线索废了。
     * 纯函数，可直接单测。
     */
    public String label() {
        String v = value == null ? "" : value;
        return switch (kind) {
            case SCREEN -> "screen:" + v;
            case CONNECT -> "connect:" + v;
            case URL -> "url:" + v;
            case COPY -> "copy";
            case QUIT -> "quit";
            case NONE -> "none";
        };
    }

    /**
     * 解析动作。
     *
     * <p>支持三种写法，后两种是为了让配置读起来更短：
     * <pre>
     *   "action": "screen",  "value": "options"
     *   "action": "screen:options"
     *   "action": "connect:play.example.com"
     * </pre>
     *
     * @param raw {@code action} 字段原样
     * @param value {@code value} 字段原样（可空）；{@code action} 里带了冒号参数时以冒号后的为准
     * @throws IllegalArgumentException 动作不认识或参数不合法（调用方应跳过该元素并记录警告，
     *     而不是让它变成一个点了没反应的按钮）
     */
    public static MenuAction parse(String raw, String value) {
        if (raw == null || raw.isBlank()) return NONE_ACTION;
        String trimmed = raw.trim();
        String head = trimmed;
        String inline = null;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            head = trimmed.substring(0, colon);
            inline = trimmed.substring(colon + 1);
        }
        String arg = (inline != null && !inline.isBlank()) ? inline.trim() : (value == null ? null : value.trim());

        return switch (head.toLowerCase(Locale.ROOT)) {
            case "none", "noop" -> NONE_ACTION;
            case "quit", "exit" -> new MenuAction(Kind.QUIT, "");
            case "screen", "vanilla", "open" -> {
                String id = arg == null ? "" : arg.toLowerCase(Locale.ROOT);
                if (!SCREENS.contains(id)) {
                    throw new IllegalArgumentException("不认识的界面 id: '" + arg + "'（可用: " + sortedScreens() + "）");
                }
                yield new MenuAction(Kind.SCREEN, id);
            }
            case "connect", "join", "server" -> {
                if (!isValidAddress(arg)) {
                    throw new IllegalArgumentException(
                            "服务器地址不合法: '" + arg + "'（应形如 play.example.com 或 play.example.com:25565）");
                }
                yield new MenuAction(Kind.CONNECT, arg);
            }
            case "url", "link" -> {
                if (!isValidWebUrl(arg)) {
                    throw new IllegalArgumentException("链接不合法: '" + arg + "'（只接受 http:// 或 https:// 开头的地址）");
                }
                yield new MenuAction(Kind.URL, arg);
            }
            case "copy" -> {
                if (arg == null || arg.isBlank()) {
                    throw new IllegalArgumentException("copy 动作需要 value（要复制的文本）");
                }
                yield new MenuAction(Kind.COPY, arg);
            }
            default -> throw new IllegalArgumentException("不认识的动作: '" + head + "'");
        };
    }

    /** {@code 主机名[:端口]}。 */
    public static boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) return false;
        if (!ADDRESS.matcher(address).matches()) return false;
        int colon = address.indexOf(':');
        if (colon < 0) return true;
        try {
            int port = Integer.parseInt(address.substring(colon + 1));
            return port >= 1 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 只接受 {@code http(s)://}。
     *
     * <p>判据刻意「先看协议再看好坏」：任何其它协议（{@code file:}、{@code javascript:}、
     * {@code jar:}…）一律拒绝，不试图枚举危险协议——枚举法总会漏。
     */
    public static boolean isValidWebUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty() || u.length() > MAX_URL_LENGTH) return false;
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c <= ' ' || c == '"' || c == '<' || c == '>' || c == '\\' || c == '^' || c == '`' || c == '{'
                    || c == '}' || c == '|') {
                return false;
            }
        }
        String lower = u.toLowerCase(Locale.ROOT);
        String rest;
        if (lower.startsWith("https://")) rest = u.substring(8);
        else if (lower.startsWith("http://")) rest = u.substring(7);
        else return false;
        // 协议之后必须真有个主机名（"http://" 与 "http:///path" 都不算）
        int slash = rest.indexOf('/');
        String host = slash < 0 ? rest : rest.substring(0, slash);
        return !host.isBlank();
    }

    /** 诊断/状态输出用的一行描述。 */
    public String describe() {
        return value == null || value.isBlank() ? kind.name().toLowerCase(Locale.ROOT) : kind + "(" + value + ")";
    }

    private static String sortedScreens() {
        return SCREENS.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}
