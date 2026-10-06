/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ccnrcom.menu.util.JsonUtil;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 语言包一致性门禁。
 *
 * <p>三件事必须成立，否则会出现只有外国玩家才会碰到的缺陷：
 * <ol>
 *   <li>zh_cn 与 en_us 的键集合**完全相同**；</li>
 *   <li>没有「值等于键」的占位翻译（缺键时客户端就把键名原样显示，等于没翻译）；</li>
 *   <li>代码里引用到的每个键都真的存在——这条最重要，因为「两侧一起丢」时同步性检查是绿的。</li>
 * </ol>
 */
class LangFileTest {

    private static final String[] LANGS = {"zh_cn", "en_us"};

    /** 命令与界面用到的关键文案（新增文案时把新键补进来）。 */
    private static final String[] REQUIRED_KEYS = {
        "ccnr_menu.screen.title",
        "ccnr_menu.command.reloaded",
        "ccnr_menu.command.status.header",
        "ccnr_menu.command.status.no_warnings",
        "ccnr_menu.command.status.warnings",
        "ccnr_menu.example.subtitle",
    };

    private static JsonObject load(String lang) {
        String path = "/assets/ccnr_menu/lang/" + lang + ".json";
        try (InputStream in = LangFileTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "语言文件缺失: " + path);
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonUtil.GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            throw new AssertionError("读取语言文件失败: " + path, e);
        }
    }

    private static Set<String> keys(JsonObject object) {
        return new TreeSet<>(object.keySet());
    }

    @Test
    @DisplayName("zh_cn 与 en_us 的键集合完全一致")
    void keysetsMatch() {
        Set<String> zh = keys(load("zh_cn"));
        Set<String> en = keys(load("en_us"));

        Set<String> missingInEn = new TreeSet<>(zh);
        missingInEn.removeAll(en);
        Set<String> missingInZh = new TreeSet<>(en);
        missingInZh.removeAll(zh);

        assertTrue(missingInEn.isEmpty(), "en_us 缺少这些键: " + missingInEn);
        assertTrue(missingInZh.isEmpty(), "zh_cn 缺少这些键: " + missingInZh);
    }

    @Test
    @DisplayName("不存在「值等于键」或空白的占位翻译")
    void noPlaceholderValues() {
        for (String lang : LANGS) {
            JsonObject object = load(lang);
            for (String key : object.keySet()) {
                String value = object.get(key).getAsString();
                assertFalse(value.equals(key), lang + " 的 " + key + " 是占位翻译（值等于键）");
                assertFalse(value.isBlank(), lang + " 的 " + key + " 是空翻译");
            }
        }
    }

    @Test
    @DisplayName("命令与界面的关键文案都已翻译")
    void requiredKeysPresent() {
        for (String lang : LANGS) {
            JsonObject object = load(lang);
            for (String key : REQUIRED_KEYS) {
                assertTrue(object.has(key), lang + " 缺少必需键: " + key);
            }
        }
    }

    @Test
    @DisplayName("格式占位符数量在中英文之间一致（否则会抛格式化异常）")
    void placeholderCountsMatch() {
        JsonObject zh = load("zh_cn");
        JsonObject en = load("en_us");
        for (String key : zh.keySet()) {
            if (!en.has(key)) continue;
            assertEquals(
                    countPlaceholders(zh.get(key).getAsString()),
                    countPlaceholders(en.get(key).getAsString()),
                    "占位符数量不一致: " + key);
        }
    }

    /**
     * 代码与内置配置里引用到的每个文案键，都必须在两个语言包里存在。
     *
     * <p>扫描范围是「代码 + 内置配置」，**刻意跳过语言文件本身**：语言文件里的键名同样是
     * {@code ccnr_menu.*} 字面量，把它们算成「引用」会让这条断言永远成立（等于没有门禁）。
     *
     * <p>为什么要连内置配置一起扫：示例配置里写了一个语言键
     * （{@code ccnr_menu.example.subtitle}）。如果语言包里没有它，玩家抄走示例后看到的就是
     * 一串红字原始键名——而这类问题只有把界面打开才看得见。
     */
    @Test
    @DisplayName("代码与内置配置引用的文案键都在语言包里")
    void everyReferencedKeyExists() {
        Set<String> referenced = new TreeSet<>();
        Pattern literal = Pattern.compile("\"(ccnr_menu\\.[a-z0-9_.]+)\"");
        Path root = projectRootOrNull();
        assertFalse(root == null, "找不到项目根目录，门禁失效");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.toString().replace('\\', '/');
                if (!name.endsWith(".java") && !name.endsWith(".json")) continue;
                if (name.contains("/build/") || name.contains("/run/")) continue;
                // 语言文件自身的键名不是「引用」
                if (name.contains("/assets/ccnr_menu/lang/")) continue;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = literal.matcher(text);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // 以点结尾的是**动态前缀**（如 "ccnr_menu.tab." + id），不是键本身
                    if (!key.endsWith(".")) referenced.add(key);
                }
            }
        } catch (Exception e) {
            fail("扫描源码失败: " + e);
        }
        assertFalse(referenced.isEmpty(), "没有扫描到任何文案键，门禁失效");
        // 至少要扫到界面与命令里的键，否则说明扫描规则失效（例如路径过滤写错）
        assertTrue(
                referenced.contains("ccnr_menu.screen.title") && referenced.contains("ccnr_menu.example.subtitle"),
                "扫描结果不含预期的键，门禁失效: " + referenced);

        for (String lang : LANGS) {
            JsonObject object = load(lang);
            Set<String> missing = new TreeSet<>();
            for (String key : referenced) {
                if (!object.has(key)) missing.add(key);
            }
            assertTrue(missing.isEmpty(), lang + " 缺少被引用的键: " + missing);
        }
    }

    private static int countPlaceholders(String value) {
        int count = 0;
        for (int i = 0; i + 1 < value.length(); i++) {
            if (value.charAt(i) == '%' && value.charAt(i + 1) == 's') count++;
        }
        return count;
    }

    private static Path projectRootOrNull() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java"))) return dir;
            dir = dir.getParent();
        }
        return null;
    }
}
