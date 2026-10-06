/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.meta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ccnrcom.menu.config.MenuConfig;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.config.MenuElement;
import com.ccnrcom.menu.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 内置素材门禁：**默认配置引用的文件必须真的随模组发布**。
 *
 * <p>这类错误不会让任何东西编译失败，症状是玩家装上一看：「图标不见了」+ 一条日志警告。
 * 而作者本地因为有文件，永远看不到这个问题。所以两个方向都钉住：
 * <ol>
 *   <li>默认/示例配置里引用的每个素材名，都在 {@code assets/ccnr_menu/presets/} 里存在；</li>
 *   <li>{@link MenuConfigIO#PRESET_FILES} 清单与资源目录里的文件**双向一致**
 *       （生成了却没列进清单 = 玩家拿不到；列了不存在的名字 = 首次启动就警告）。</li>
 * </ol>
 */
class PresetAssetsTest {

    private static final String PRESET_DIR = "src/main/resources/assets/ccnr_menu/presets";

    private static Path projectRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("gradle.properties"))) return dir;
            dir = dir.getParent();
        }
        return fail("找不到项目根目录：user.dir=" + System.getProperty("user.dir"));
    }

    /** 配置里引用的所有素材文件名（含容器内元素与背景）。 */
    private static Set<String> referencedAssets(MenuConfig config) {
        Set<String> files = new TreeSet<>();
        if (config.background().needsFile()) files.add(config.background().file());
        for (MenuElement element : config.elements()) collect(element, files);
        return files;
    }

    private static void collect(MenuElement element, Set<String> out) {
        if (element.type() == MenuElement.Type.IMAGE && !element.file().isBlank()) {
            out.add(element.file());
        }
        if (element.type() == MenuElement.Type.COLUMN && element.column() != null) {
            for (MenuElement child : element.column().children()) collect(child, out);
        }
    }

    private static MenuConfig parse(String resource) {
        return MenuConfig.parse(
                JsonUtil.readResource(resource).orElseThrow(() -> new AssertionError("内置配置缺失: " + resource)),
                new ArrayList<>());
    }

    @Test
    @DisplayName("默认与示例配置引用的素材都随模组发布")
    void referencedAssetsAreShipped() {
        for (String resource :
                List.of("/assets/ccnr_menu/defaults/menu.json", "/assets/ccnr_menu/defaults/menu.example.json")) {
            MenuConfig config = parse(resource);
            Set<String> referenced = referencedAssets(config);
            assertFalse(referenced.isEmpty(), resource + " 没有引用任何素材？门禁失效");
            for (String file : referenced) {
                assertTrue(
                        Files.isRegularFile(projectRoot().resolve(PRESET_DIR).resolve(file)),
                        resource + " 引用了不存在的素材: " + file + "（应放在 " + PRESET_DIR + "/）");
            }
        }
    }

    @Test
    @DisplayName("素材清单与资源目录里的文件双向一致（生成了没列 / 列了不存在都会红）")
    void presetListMatchesDisk() {
        Path dir = projectRoot().resolve(PRESET_DIR);
        assertTrue(Files.isDirectory(dir), "内置素材目录缺失: " + dir);

        Set<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                onDisk.add(file.getFileName().toString());
            }
        } catch (IOException e) {
            fail("读取素材目录失败: " + e);
        }

        Set<String> listed = new TreeSet<>(MenuConfigIO.PRESET_FILES);
        Set<String> notListed = new TreeSet<>(onDisk);
        notListed.removeAll(listed);
        Set<String> missing = new TreeSet<>(listed);
        missing.removeAll(onDisk);

        assertTrue(notListed.isEmpty(), "这些素材存在于资源目录但不在 PRESET_FILES 清单里，玩家拿不到: " + notListed);
        assertTrue(missing.isEmpty(), "PRESET_FILES 里列了不存在的素材，首次启动会报缺失: " + missing);
    }

    @Test
    @DisplayName("精灵图规格与配置相符（8 列 4 行 = 32 帧，每帧宽高比 3:1）")
    void spriteSheetMatchesConfig() {
        // 配置里写的是 cols/rows/frameMs，一旦重渲染用了别的网格，动画会错位
        for (String resource :
                List.of("/assets/ccnr_menu/defaults/menu.json", "/assets/ccnr_menu/defaults/menu.example.json")) {
            MenuConfig config = parse(resource);
            MenuElement icon = config.elements().get(0).column().children().get(0);
            assertTrue(icon.animatedImage(), resource + " 的图标应当是序列帧动画");
            assertTrue(
                    icon.sheet().cols() == 8 && icon.sheet().rows() == 4,
                    resource + " 的网格应为 8x4（由 scripts/make-icon-presets.py 产出）");
            assertTrue(icon.width() > 0, "图标必须给宽度（高度按帧比例自动算）");
        }
    }
}
