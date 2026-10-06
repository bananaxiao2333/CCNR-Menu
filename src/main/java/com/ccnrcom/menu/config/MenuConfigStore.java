/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.config;

import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 客户端手里的「当前生效配置」。
 *
 * <p>线程约定：**只在客户端主线程访问**（加载发生在屏幕 {@code init()} 与命令执行时）。
 * 不做同步是刻意的——这些状态本来就只属于渲染线程，加锁只会掩盖「在工作线程里读配置」这种真正的错误。
 *
 * <p>热重载策略：每次打开主菜单时比较一次文件修改时间，**只在文件真的变了**才重新读取。
 * 这样「改完配置回标题界面看一眼」是即时生效的，而稳态下不会反复做磁盘 IO。
 */
public final class MenuConfigStore {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    private static MenuConfigIO.LoadResult current;

    private MenuConfigStore() {}

    /** 当前配置（首次访问时加载）。 */
    public static MenuConfigIO.LoadResult current() {
        if (current == null) {
            current = MenuConfigIO.load();
            logWarnings(current);
        }
        return current;
    }

    /** 强制重新读取（{@code /ccnr_menu reload}）。 */
    public static MenuConfigIO.LoadResult reload() {
        current = MenuConfigIO.load();
        logWarnings(current);
        return current;
    }

    /** 文件被改动过才重新读取；返回当前（可能是新的）结果。 */
    public static MenuConfigIO.LoadResult reloadIfChanged() {
        MenuConfigIO.LoadResult existing = current();
        long modified = MenuConfigIO.lastModified(existing.file());
        if (modified != existing.modifiedAt()) {
            LOGGER.info("[CCNR-Menu] 检测到配置变更，重新加载: {}", existing.file());
            return reload();
        }
        return existing;
    }

    /** 配置来源文件（诊断输出用）。 */
    public static Path file() {
        return current().file();
    }

    private static void logWarnings(MenuConfigIO.LoadResult result) {
        for (String warning : result.warnings()) {
            LOGGER.warn("[CCNR-Menu] 配置警告: {}", warning);
        }
        LOGGER.info(
                "[CCNR-Menu] 配置已加载: 背景={} 按钮={} 自定义元素={} 警告={} 条",
                result.config().background().kind(),
                result.config().vanillaButtons() ? "原版" : "自定义",
                result.config().elements().size(),
                result.warnings().size());
    }
}
