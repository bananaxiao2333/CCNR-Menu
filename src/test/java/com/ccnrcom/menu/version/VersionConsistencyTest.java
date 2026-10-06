/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.version;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 版本号一致性门禁（规范见 docs/04）。
 *
 * <p>为什么值得一道门禁：版本号散落在四处（{@code gradle.properties} 真源、CHANGELOG、README、
 * 以及 docs/04 自己），漏掉任何一处都不是构建失败，而是**玩家装错版本却无从察觉**。
 * 找不到项目根目录时直接失败而不是跳过——静默跳过的门禁等于没有门禁。
 */
class VersionConsistencyTest {

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private static Path projectRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("gradle.properties")) && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return fail("找不到项目根目录（应含 gradle.properties 与 src/）：user.dir=" + System.getProperty("user.dir"));
    }

    private static String read(Path root, String name) {
        try {
            return Files.readString(root.resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("读取失败: " + name + " —— " + e);
        }
    }

    private static String sourceVersion(Path root) {
        for (String line : read(root, "gradle.properties").lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("mod_version=")) {
                return trimmed.substring("mod_version=".length()).trim();
            }
        }
        return fail("gradle.properties 里没有 mod_version");
    }

    @Test
    @DisplayName("mod_version 是规范要求的 <主>.<功能批次>.<修订> 三段数字")
    void versionShape() {
        String version = sourceVersion(projectRoot());
        assertTrue(
                VERSION.matcher(version).matches(),
                "mod_version 必须是三段数字（形如 0.1.0），实际: " + version + "（规范见 docs/04 §1）");
    }

    @Test
    @DisplayName("CHANGELOG 顶部第一个版本号与 mod_version 一致")
    void changelogMatchesSource() {
        Path root = projectRoot();
        String expected = sourceVersion(root);
        String found = null;
        for (String line : read(root, "CHANGELOG.md").split("\n", -1)) {
            if (!line.startsWith("## ")) continue;
            Matcher matcher = VERSION.matcher(line);
            if (matcher.find()) {
                found = matcher.group();
                break;
            }
        }
        assertTrue(found != null, "CHANGELOG 里找不到任何带版本号的标题");
        assertTrue(
                expected.equals(found),
                "CHANGELOG 顶部版本(" + found + ") 与 mod_version(" + expected + ") 不一致（docs/04 §4）");
    }

    @Test
    @DisplayName("README 里同步了当前版本号")
    void readmeMentionsVersion() {
        Path root = projectRoot();
        String expected = sourceVersion(root);
        assertTrue(
                read(root, "README.md").contains(expected),
                "README 未出现当前版本号 " + expected + "——请同步「当前版本」一行（docs/04 §3）");
    }

    @Test
    @DisplayName("mods.toml 用 ${file.jarVersion} 注入，不手写第二个版本号")
    void modsTomlDoesNotDuplicateVersion() {
        Path root = projectRoot();
        Path modsToml = root.resolve("src/main/resources/META-INF/mods.toml");
        assertTrue(Files.isRegularFile(modsToml), "mods.toml 缺失: " + modsToml);
        String body;
        try {
            body = Files.readString(modsToml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // 上面的断言已覆盖，不会走到这里
        }
        assertTrue(
                body.contains("version=\"${file.jarVersion}\""),
                "mods.toml 的 [[mods]] version 应为 ${file.jarVersion}（docs/04 §3）");

        String expected = sourceVersion(root);
        for (String line : body.lines().toList()) {
            String trimmed = line.trim();
            // 依赖项的 versionRange 里出现版本区间是正常的，只查本模组自身
            if (trimmed.startsWith("version=") && !trimmed.contains("${file.jarVersion}")) {
                assertTrue(!trimmed.contains(expected), "mods.toml 里手写了当前版本号 " + expected + "，应改为 ${file.jarVersion}");
            }
        }
    }

    @Test
    @DisplayName("docs/04 存在于且内容足够（规范本身也要被保住）")
    void specDocumentExists() {
        Path spec = projectRoot().resolve("docs/04-版本号规范.md");
        assertTrue(Files.isRegularFile(spec), "缺少 docs/04-版本号规范.md");
        try {
            assertTrue(Files.readAllLines(spec, StandardCharsets.UTF_8).size() > 20, "docs/04 内容过少，规范形同虚设");
        } catch (IOException e) {
            fail("读取 docs/04 失败: " + e);
        }
    }

    @Test
    @DisplayName("docs/04 里引用的本项目版本号与 mod_version 一致（规范文档最容易悄悄漂）")
    void specDocumentVersionMatchesSource() {
        Path root = projectRoot();
        String expected = sourceVersion(root);
        String body;
        try {
            body = Files.readString(root.resolve("docs/04-版本号规范.md"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("读取 docs/04 失败: " + e);
            return;
        }

        // ① 真源行的写法（§3 同步表）：mod_version=X
        Matcher rule = Pattern.compile("mod_version=(\\d+\\.\\d+\\.\\d+)").matcher(body);
        int seen = 0;
        while (rule.find()) {
            seen++;
            assertTrue(
                    expected.equals(rule.group(1)), "docs/04 把真源写成了 mod_version=" + rule.group(1) + "，实际是 " + expected);
        }
        assertTrue(seen > 0, "docs/04 里找不到 mod_version=…——§3 的同步表被删了？");

        // ② §2 的结论行
        String conclusion =
                body.lines().filter(line -> line.contains("符合上表")).findFirst().orElse(null);
        assertTrue(conclusion != null, "docs/04 §2 找不到「符合上表」那句结论");
        assertTrue(conclusion.contains(expected), "docs/04 §2 的结论仍写着旧版本（应为 " + expected + "）：" + conclusion.trim());

        // ③ §6 对照表里本仓库那一行
        Matcher row = Pattern.compile("\\*\\*CCNR-Menu\\*\\*（本仓库）\\s*\\|\\s*`(\\d+\\.\\d+\\.\\d+)`")
                .matcher(body);
        assertTrue(row.find(), "docs/04 §6 找不到带「（本仓库）」的 CCNR-Menu 版本行");
        assertTrue(expected.equals(row.group(1)), "docs/04 §6 的 CCNR-Menu 版本(" + row.group(1) + ") 没同步到 " + expected);
    }
}
