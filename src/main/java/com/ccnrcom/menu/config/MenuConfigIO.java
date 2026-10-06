/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 菜单配置的读写（{@code config/ccnr_menu/}）。
 *
 * <p>目录布局：
 * <pre>
 *   config/ccnr_menu/menu.json           ← 真正生效的配置（首次运行自动生成）
 *   config/ccnr_menu/menu.example.json   ← 带全套示例的参考文件（**不参与加载**，随便改）
 *   config/ccnr_menu/&lt;你的素材&gt;.gif/png  ← 背景文件放这里
 * </pre>
 *
 * <p>为什么单独给一个 {@code menu.example.json} 而不是把示例写在 {@code menu.json} 里：
 * 生效的配置里塞满注释性内容，会让「哪些字段真的在起作用」变得含糊；
 * 而 JSON 没有注释语法，用假字段当注释又会被当成真配置。两个文件各司其职最清楚。
 */
public final class MenuConfigIO {

    public static final String DIR_NAME = "ccnr_menu";
    public static final String FILE_NAME = "menu.json";
    public static final String EXAMPLE_FILE_NAME = "menu.example.json";

    private static final String DEFAULT_RESOURCE = "/assets/ccnr_menu/defaults/menu.json";
    private static final String EXAMPLE_RESOURCE = "/assets/ccnr_menu/defaults/menu.example.json";
    private static final String PRESET_RESOURCE_DIR = "/assets/ccnr_menu/presets/";

    /**
     * 随模组发布的内置素材（首次运行拷进配置目录）。
     *
     * <p>为什么内置而不是让作者自己去导出：这些素材是从 CCNR 图标的 SVG 动画
     * **预渲染**出来的（见 {@code scripts/make-icon-presets.py}），手上有源文件的人不需要它，
     * 而只想把服务器菜单装起来的人需要它。拷进去之后就是普通文件，作者想换直接覆盖即可
     * （本模组**从不覆盖已存在的文件**）。
     */
    /**
     * 随模组发布的素材清单。
     *
     * <p>**公开**是有意的：`PresetAssetsTest` 用它把「清单」与「资源目录里真实存在的文件」
     * 双向对齐——生成了新素材却忘了列进来（玩家拿不到）、或列了一个不存在的名字（首次启动就报缺失）
     * 都是不会让编译失败的错误，只能靠门禁守。
     */
    public static final List<String> PRESET_FILES = List.of(
            "background.png",
            "logo_wide_white.png",
            "logo_wide_intro_white.png",
            "logo_wide_mono.png",
            "logo_wide_intro_mono.png",
            "slides/01.jpg",
            "slides/02.jpg",
            "slides/03.jpg",
            "slides/04.jpg",
            "slides/05.jpg",
            "slides/06.jpg",
            "slides/07.jpg",
            "slides/08.jpg",
            "slides/09.jpg",
            "slides/10.jpg",
            "slides/11.jpg");

    /**
     * 内置启动日志驱动（{@code bootlog/<名>.txt}）：**不属于** {@link #PRESET_FILES}。
     *
     * <p>区别在于「谁拥有它」：presets 里的素材由模组管理（内容换了就覆盖），
     * 而启动日志驱动是给作者**抄一份改**的模板——模组读的是 jar 里那一份（{@code bootLog.builtin}），
     * 作者想改就把配置目录里这份复制成自己的名字、再让 {@code bootLog.file} 指向它。
     * 所以它只释放一次，之后永不覆盖。
     */
    private static final List<String> BOOTLOG_DRIVERS = List.of("ubuntu.txt");

    private static final String BOOTLOG_RESOURCE_DIR = "/assets/ccnr_menu/bootlog/";

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private MenuConfigIO() {}

    /** {@code config/ccnr_menu/}。 */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(DIR_NAME);
    }

    /** 生效的配置文件。 */
    public static Path configFile() {
        return configDir().resolve(FILE_NAME);
    }

    /** 示例配置文件（不被加载）。 */
    public static Path exampleFile() {
        return configDir().resolve(EXAMPLE_FILE_NAME);
    }

    /**
     * 首次运行时写入默认配置、示例文件与内置素材（已存在则不动——**绝不覆盖玩家改过的文件**）。
     *
     * @return 是否有文件被新写入
     */
    public static boolean ensureDefaults() {
        boolean created = false;
        if (!Files.isRegularFile(configFile())) {
            created |= writeFromResource(DEFAULT_RESOURCE, configFile());
        } else {
            created |= migrateLegacyDefault(configFile());
        }
        if (!Files.isRegularFile(exampleFile())) {
            created |= writeFromResource(EXAMPLE_RESOURCE, exampleFile());
        }
        for (String preset : PRESET_FILES) {
            created |= copyPreset(preset);
        }
        for (String driver : BOOTLOG_DRIVERS) {
            created |= copyBootLogDriver(driver);
        }
        return created;
    }

    /**
     * 升级迁移：把**未被修改过的历史默认配置**换成当前默认。
     *
     * <p>为什么需要这一步：本模组从不覆盖已存在的配置（作者改过的文件不能被抹掉），
     * 于是「更新模组版本」时新的默认配置永远进不去——0.2.0 的反应就是
     * 「装上了但菜单一点变化都没有」，而日志完全正常。判据见 {@link MenuDefaults}：
     * 只有内容与历史默认**完全一致**才替换，玩家动过一个字符就不碰。
     *
     * @return 是否真的替换了
     */
    private static boolean migrateLegacyDefault(Path file) {
        String content;
        try {
            content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[CCNR-Menu] 读取旧配置失败，跳过升级迁移: {} —— {}", file, e.toString());
            return false;
        }
        if (!MenuDefaults.isLegacyUnmodified(content)) return false;

        try {
            Files.copy(
                    file,
                    file.resolveSibling(file.getFileName() + ".bak"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("[CCNR-Menu] 备份旧配置失败，为保证安全不做迁移: {} —— {}", file, e.toString());
            return false;
        }
        boolean ok = writeFromResource(DEFAULT_RESOURCE, file);
        if (ok) {
            LOGGER.info("[CCNR-Menu] 检测到【未被修改过的旧版默认配置】，已替换为新版默认（旧文件备份为 {}）", file.getFileName() + ".bak");
        }
        return ok;
    }

    /**
     * 用内置默认覆盖当前配置（{@code /ccnr_menu reset} 用）。
     *
     * <p>为什么需要它：迁移只处理「从未改过」的文件。改过配置的人想要新版默认时，
     * 不能靠猜他改了哪几行——给他一个**显式**的「恢复出厂」入口，并且先把原文件备份成 {@code .bak}。
     */
    public static boolean resetToDefault() {
        Path file = configFile();
        if (Files.isRegularFile(file)) {
            try {
                Files.copy(
                        file,
                        file.resolveSibling(file.getFileName() + ".bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("[CCNR-Menu] 备份当前配置失败，已取消重置: {} —— {}", file, e.toString());
                return false;
            }
        }
        return writeFromResource(DEFAULT_RESOURCE, file);
    }

    /**
     * 把内置素材拷进配置目录：**文件不存在**（首次释放）或**内容与内置的不同**（模组换了素材）时才写。
     *
     * <p>为什么「已存在就跳过」是错的：素材名字不变但内容换了（轮播换图、图标重渲染）时，
     * 老玩家的配置目录里留着旧字节，于是**永远停在旧素材上**。0.4.1 就踩了这个坑——
     * 作者要求删掉的那张轮播图仍然在转，而新补进来的那张根本没出现过。
     * 判据用**内容**而不是 mtime：拷进来的文件时间戳不可靠。
     *
     * <p>覆盖前把旧文件备份成 {@code .bak}，所以「玩家自己替换过同名素材」不会真的丢东西。
     * 只有 {@link #PRESET_FILES} 里的名字由模组管理，作者自己加的素材换个文件名即可
     * （配置里的路径是任意的相对路径）。
     */
    private static boolean copyPreset(String name) {
        Path target = configDir().resolve(name);
        try (InputStream in = MenuConfigIO.class.getResourceAsStream(PRESET_RESOURCE_DIR + name)) {
            if (in == null) {
                LOGGER.warn("[CCNR-Menu] 内置素材缺失，无法释放 {}（资源: {}）", name, PRESET_RESOURCE_DIR + name);
                return false;
            }
            byte[] shipped = in.readAllBytes();
            boolean existed = Files.isRegularFile(target);
            if (existed) {
                if (java.util.Arrays.equals(Files.readAllBytes(target), shipped)) return false;
                try {
                    Files.copy(
                            target,
                            target.resolveSibling(target.getFileName() + ".bak"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.warn("[CCNR-Menu] 备份旧素材失败，已跳过更新: {} —— {}", name, e.toString());
                    return false;
                }
            }
            Files.createDirectories(target.getParent());
            Files.write(target, shipped);
            LOGGER.info(
                    "[CCNR-Menu] {}内置素材 {}{}",
                    existed ? "已更新" : "已释放",
                    target,
                    existed ? "（旧文件备份为 " + target.getFileName() + ".bak）" : "");
            return true;
        } catch (IOException e) {
            LOGGER.warn("[CCNR-Menu] 释放内置素材失败: {} —— {}", name, e.toString());
            return false;
        }
    }

    /**
     * 释放一份启动日志驱动到 {@code config/ccnr_menu/bootlog/}：**只在文件不存在时**写。
     *
     * <p>刻意不做「内容变了就覆盖」：这份文件的用途就是给作者改（照着它写自己的启动日志）。
     * 覆盖它等于把作者写的东西删掉——那正是 {@code copyPreset} 要避免的另一半问题。
     */
    private static boolean copyBootLogDriver(String name) {
        Path target = configDir().resolve("bootlog").resolve(name);
        if (Files.isRegularFile(target)) return false;
        try (InputStream in = MenuConfigIO.class.getResourceAsStream(BOOTLOG_RESOURCE_DIR + name)) {
            if (in == null) {
                LOGGER.warn("[CCNR-Menu] 内置启动日志缺失，无法释放 {}（资源: {}）", name, BOOTLOG_RESOURCE_DIR + name);
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());
            LOGGER.info("[CCNR-Menu] 已释放启动日志模板 {}", target);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[CCNR-Menu] 释放启动日志模板失败: {} —— {}", name, e.toString());
            return false;
        }
    }

    private static boolean writeFromResource(String resource, Path target) {
        Optional<JsonObject> template = JsonUtil.readResource(resource);
        if (template.isEmpty()) {
            LOGGER.error("[CCNR-Menu] 内置默认配置缺失，无法生成 {}（资源: {}）", target, resource);
            return false;
        }
        boolean ok = JsonUtil.atomicWrite(target, template.get());
        if (ok) LOGGER.info("[CCNR-Menu] 已生成 {}", target);
        return ok;
    }

    /**
     * 读取并解析配置。
     *
     * <p>**任何失败都不抛异常**：返回值里带着可用的配置与一串警告。菜单打不开比菜单不好看严重得多，
     * 所以这里的原则是「无论如何都给出一个能玩的菜单」。
     */
    public static LoadResult load() {
        List<String> warnings = new ArrayList<>();
        ensureDefaults();
        Path file = configFile();
        Optional<JsonObject> json = JsonUtil.readObject(file);
        MenuConfig config;
        if (json.isEmpty()) {
            warnings.add("菜单配置读取失败或不存在 → 已使用内置默认菜单（原版外观），" + "修好后可用 /ccnr_menu reload 重新加载");
            config = MenuConfig.fallback();
        } else {
            config = MenuConfig.parse(json.get(), warnings);
        }
        return new LoadResult(config, List.copyOf(warnings), file, lastModified(file));
    }

    /** 文件最后修改时间；不存在返回 0（供「文件被改动就重载」判据使用）。 */
    public static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 把配置里的素材相对路径解析成绝对路径。
     *
     * <p>**只允许 {@code config/ccnr_menu/} 之内**：配置是可以被转发的（服主发一份好看的菜单给玩家），
     * 允许 {@code ../} 或绝对路径就等于允许别人指定读取你机器上的任意文件。
     *
     * @throws IllegalArgumentException 路径越界
     */
    public static Path resolveAsset(String file) {
        if (file == null || file.isBlank()) throw new IllegalArgumentException("素材路径为空");
        Path base = configDir().toAbsolutePath().normalize();
        Path resolved = base.resolve(file.replace('\\', '/')).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("素材路径越界（只能放在 config/" + DIR_NAME + "/ 内）: " + file);
        }
        return resolved;
    }

    /**
     * 一次加载的结果。
     *
     * @param config 可用的配置（一定有值）
     * @param warnings 需要让玩家看见的问题（{@code /ccnr_menu status} 会原样列出）
     * @param file 配置来源文件
     * @param modifiedAt 加载时该文件的修改时间（用于判断是否需要重载）
     */
    public record LoadResult(MenuConfig config, List<String> warnings, Path file, long modifiedAt) {

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}
