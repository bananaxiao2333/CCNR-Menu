/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.meta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模组元数据门禁（{@code mods.toml} / {@code pack.mcmeta} / 资源清单）。
 *
 * <p>这些东西的特点是**不会被编译器检查**：modId 写错、依懒项写成 {@code side=BOTH}、
 * 资源包版本对不上，都要等到游戏启动（或者干脆等到别人装不上）才暴露。
 * 而它们又极容易在复制粘贴里出错，所以用一道静态门禁钉住。
 */
class ProjectMetadataTest {

    private static Path root() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("gradle.properties"))) return dir;
            dir = dir.getParent();
        }
        return fail("找不到项目根目录：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("读取失败: " + path + " —— " + e);
        }
    }

    private static String property(String key) {
        for (String line : read(root().resolve("gradle.properties")).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + "=")) {
                return trimmed.substring(key.length() + 1).trim();
            }
        }
        return fail("gradle.properties 缺少 " + key);
    }

    private static String modsToml() {
        return read(root().resolve("src/main/resources/META-INF/mods.toml"));
    }

    @Test
    @DisplayName("mods.toml 的 modId / 许可 / 版本注入与 gradle.properties 一致")
    void modsTomlIdentity() {
        String body = modsToml();
        String modId = property("mod_id");

        assertTrue(
                body.contains("modId=\"" + modId + "\""),
                "mods.toml 的 modId 与 gradle.properties 不一致（应为 " + modId + "）");
        assertTrue(
                body.contains("license=\"" + property("mod_license") + "\""),
                "mods.toml 的 license 与 gradle.properties 不一致");
        assertTrue(body.contains("version=\"${file.jarVersion}\""), "mods.toml 的版本必须来自构建注入");
        assertTrue(body.contains("loaderVersion=\"[47,)\""), "loaderVersion 必须与 Forge 47.x 一致");
    }

    @Test
    @DisplayName("依赖声明只勾客户端，且不写 Forge 1.20.1 不支持的 clientSideOnly")
    void dependenciesDeclareClientOnly() {
        String body = modsToml();

        assertTrue(body.contains("modId=\"forge\""), "缺少 forge 依赖");
        assertTrue(body.contains("modId=\"minecraft\""), "缺少 minecraft 依赖");
        assertTrue(body.contains("versionRange=\"[1.20.1,1.21)\""), "minecraft 版本区间必须是 [1.20.1,1.21)");

        int clientSides = 0;
        for (String line : body.lines().toList()) {
            if (line.trim().startsWith("side=")) {
                assertTrue(line.contains("side=\"CLIENT\""), "本模组是纯客户端模组，依赖都应声明 side=\"CLIENT\"：" + line);
                clientSides++;
            }
        }
        assertTrue(clientSides >= 2, "应当有 forge 与 minecraft 两条依赖的 side 声明");

        // Forge 1.20.1 的 ModInfo 里没有 clientSideOnly 字段（1.20.2+ 才有）。
        // 写了它不会生效，只会让人误以为「服务端不会加载」，所以刻意断言它不存在。
        assertFalse(body.contains("clientSideOnly"), "Forge 1.20.1 不支持 clientSideOnly，代之以 FMLEnvironment.dist 判断");
    }

    @Test
    @DisplayName("资源包描述与 pack_format 匹配 1.20.1")
    void packMetadata() {
        String body = read(root().resolve("src/main/resources/pack.mcmeta"));
        assertTrue(body.contains("\"pack_format\""), "pack.mcmeta 缺少 pack_format");
        assertTrue(body.contains("15"), "1.20.1 的 pack_format 是 15");
    }

    @Test
    @DisplayName("语言包与内置默认配置都在资源目录里")
    void resourcesPresent() {
        Path assets = root().resolve("src/main/resources/assets/ccnr_menu");
        assertTrue(Files.isRegularFile(assets.resolve("lang/zh_cn.json")), "缺少中文语言包");
        assertTrue(Files.isRegularFile(assets.resolve("lang/en_us.json")), "缺少英文语言包");
        assertTrue(Files.isRegularFile(assets.resolve("defaults/menu.json")), "缺少默认菜单配置模板");
        assertTrue(Files.isRegularFile(assets.resolve("defaults/menu.example.json")), "缺少示例菜单配置");
    }

    @Test
    @DisplayName("交付物不该带上不该提交的东西（build/run 目录由 .gitignore 覆盖）")
    void gitignoreCoversWorkspace() {
        String body = read(root().resolve(".gitignore"));
        for (String entry : new String[] {"build/", "run/", "logs/", ".gradle-home/"}) {
            assertTrue(body.contains(entry), ".gitignore 缺少 " + entry);
        }
    }
}
