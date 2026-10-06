/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */
package com.ccnrcom.menu.client.background;

import com.ccnrcom.menu.config.BootLogSpec;
import com.ccnrcom.menu.config.MenuConfigIO;
import com.ccnrcom.menu.ui.BootLogFeed;
import com.ccnrcom.menu.ui.BootLogLines;
import com.ccnrcom.menu.ui.BootLogRow;
import com.ccnrcom.menu.ui.BootLogTimeline;
import com.ccnrcom.menu.ui.MenuGeometry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 启动日志那一层：**背景图之上、图标与按钮之下**，而且**所有被接管的界面都有**。
 *
 * <p>它不是一种背景类型（{@code BackgroundFactory} 的一支）：层级要能表达「图标盖住日志」，
 * 做成背景的话所有元素都只能压在它上面，正好反了。所以它和 {@link MarkRenderer} 一样
 * 由 {@link ScreenBackgrounds} 单独持有，绘制顺序在 {@code ScreenBackgrounds.render} 里写死。
 *
 * <p>五条实现要点：
 *
 * <ol>
 *   <li><b>时间基点在构造时一次性定死</b>：主菜单 → 设置 → 返回这条路上背景实例按指纹复用，
 *       日志不会重头再打一遍。只有配置或驱动文件真的变了才重建——那时重头打正是想要的。</li>
 *   <li><b>两种内容合流</b>：写死的剧本（{@link BootLogTimeline}）在前，运行期事件
 *       （{@link BootLogFeed}）在后，接成一条线一起滚；两者的时间戳都是**按行冻结**的。</li>
 *   <li><b>自动贴底</b>：屏幕放不下时取末尾那几行——日志滚动的语义就是「永远看最新」。</li>
 *   <li><b>一个对齐管所有界面</b>：靠左（默认）时文字只向右生长，永远不会被推出屏幕；
 *       曾经做过「主菜单靠右 / 其它界面靠左 + 滑动」，实测在窄窗口下整块移出可视区，已回退。</li>
 *   <li><b>没有光标</b>：终端光标在游戏里是持续的视觉噪音（用户实测后要求去掉）。</li>
 * </ol>
 */
public final class BootLogRenderer {

    private static final Logger LOGGER = LogManager.getLogger("ccnr_menu");

    /** 一屏最多画多少行（GUI 缩放到 1 时能塞进上百行，那是会掉帧的）。 */
    private static final int MAX_ROWS = 64;

    /**
     * 剧本永远保留最后这么多行。
     *
     * <p>为什么要有这个下限：窗口是**剧本 + 事件**合起来裁的，事件一多就会把剧本整段挤掉、
     * 而且看起来像「日志不再滚动了」（其实是一直在往上顶）。留一个下限之后，
     * 剧本稳定占住它该有的位置，事件从它下面接。
     */
    private static final int SCRIPT_MIN_ROWS = 3;

    private final BootLogSpec spec;
    private final BootLogTimeline timeline;
    private final BootLogFeed feed;
    private final long originMs;

    public BootLogRenderer(BootLogSpec spec) {
        this.spec = spec;
        this.originMs = Util.getMillis();
        // 事件流用**同一只钟**（Util.getMillis = 进程启动以来的毫秒）。
        // 早先它自己默认 System.nanoTime，于是事件行的时间戳印出了开机秒数（444 万）。
        this.feed = new BootLogFeed(originMs, spec.spinnerWidth(), () -> Util.getMillis());

        BootLogTimeline built = BootLogTimeline.empty();
        if (spec.hasSource()) {
            try {
                List<BootLogLines.Entry> entries = load(spec);
                built = BootLogLines.toTimeline(entries, spec.spacingMs(), spec.spinner(), spec.meter());
                if (entries.isEmpty()) {
                    LOGGER.warn("[CCNR-Menu] 启动日志是空的，这一层不会显示任何内容（{}）", sourceName(spec));
                }
            } catch (Exception e) {
                // 降级收口：驱动读不到 = 剧本不画，事件行与背景照常
                LOGGER.warn("[CCNR-Menu] 启动日志读取失败，剧本部分已跳过: {} —— {}", sourceName(spec), e.toString());
            }
        }
        this.timeline = built;
    }

    /** 事件流：模组别处往这里塞行、开进度条。 */
    public BootLogFeed feed() {
        return feed;
    }

    /** 剧本行数（诊断输出用）。 */
    public int lineCount() {
        return timeline.lineCount();
    }

    /** 有没有内容可画（剧本或事件任意一个有）。 */
    public boolean hasContent() {
        return !timeline.isEmpty() || feed.size() > 0 || feed.spinning();
    }

    /**
     * 画这一层。
     *
     * @param ownMenu 当前是不是本模组自己的主菜单（决定用哪套锚点语义、以及 {@code onAllScreens}）
     */
    public void render(GuiGraphics gfx, Font font, int screenWidth, int screenHeight, float alpha, boolean ownMenu) {
        if (!spec.enabled()) return;
        if (!ownMenu && !spec.onAllScreens()) return;

        feed.tick();

        int lineHeight = spec.lineHeight(font.lineHeight);
        int rows = Math.max(1, Math.min(MAX_ROWS, (screenHeight - 4) / lineHeight));
        List<BootLogRow> visible = collect(rows);
        if (visible.isEmpty()) return;

        // 单一锚点：所有界面同一个位置（MenuGeometry.place 与元素共用一套几何）
        MenuGeometry.Rect block = MenuGeometry.place(
                spec.x(),
                spec.y(),
                spec.align(),
                spec.valign(),
                blockWidth(visible, font),
                Math.max(1, visible.size() * lineHeight),
                screenWidth,
                screenHeight);

        for (int i = 0; i < visible.size(); i++) {
            BootLogRow row = visible.get(i);
            int y = block.y() + i * lineHeight;
            if (y + lineHeight < 0 || y > screenHeight) continue;
            String text = row.kind().markerFor(!row.partial()) + row.text();
            int color = withAlpha(spec.hasColor() ? spec.color() : row.kind().color(), alpha);
            drawScaled(gfx, font, text, block.x(), y, color);
        }
    }

    /**
     * 合成这一帧要画的行：**剧本在前、事件在后**，再取末尾 {@code rows} 行（自动贴底）。
     *
     * <p>「等待中」那一行永远排在最后——它是唯一在动的东西，位置必须稳定。
     */
    private List<BootLogRow> collect(int rows) {
        // 事件那部分单独算：它最多占 rows - 3 行，剩下的留给剧本
        List<BootLogRow> events = new ArrayList<>(feed.rows());
        BootLogRow spinner = feed.spinnerRow();
        if (spinner != null) events.add(spinner);

        List<BootLogRow> out = new ArrayList<>(rows);
        int scriptRoom = Math.max(Math.min(SCRIPT_MIN_ROWS, rows), rows - events.size());
        if (scriptRoom > 0 && !timeline.isEmpty()) {
            long elapsed = Util.getMillis() - originMs;
            // 交给时刻表自己裁窗口：它就是「最后 N 行」，超出时整条日志往上顶
            out.addAll(timeline.at(elapsed, scriptRoom).rows());
        }
        if (!events.isEmpty()) {
            int eventRoom = Math.max(0, rows - out.size());
            out.addAll(eventRoom >= events.size() ? events : events.subList(events.size() - eventRoom, events.size()));
        }
        if (out.size() > rows) out = new ArrayList<>(out.subList(out.size() - rows, out.size()));
        return out;
    }

    private void drawScaled(GuiGraphics gfx, Font font, String text, float x, int y, int color) {
        if (spec.scale() == 1f) {
            gfx.drawString(font, text, Math.round(x), y, color, false);
            return;
        }
        // 缩放只能靠 PoseStack：drawString 的字号不可配
        var pose = gfx.pose();
        pose.pushPose();
        try {
            pose.translate(x / spec.scale(), y / spec.scale(), 0f);
            pose.scale(spec.scale(), spec.scale(), 1f);
            gfx.drawString(font, text, 0, 0, color, false);
        } finally {
            pose.popPose();
        }
    }

    /** 块宽：整块边界由最长那行决定（靠右对齐时才有意义）。 */
    private static int blockWidth(List<BootLogRow> rows, Font font) {
        int w = 0;
        for (BootLogRow row : rows) {
            w = Math.max(w, font.width(row.kind().markerFor(!row.partial()) + row.text()));
        }
        return w;
    }

    private static List<BootLogLines.Entry> load(BootLogSpec spec) throws Exception {
        List<String> warnings = new ArrayList<>();
        List<BootLogLines.Entry> entries;
        if (!spec.file().isEmpty()) {
            Path file = MenuConfigIO.resolveAsset(spec.file());
            if (!Files.isRegularFile(file)) {
                LOGGER.warn("[CCNR-Menu] 启动日志驱动不存在: {}（把文件放进 {}）", file, MenuConfigIO.configDir());
                return List.of();
            }
            entries = BootLogLines.parse(Files.readString(file, StandardCharsets.UTF_8), warnings);
        } else {
            String path = "assets/" + com.ccnrcom.menu.config.MenuConfig.MOD_ID + "/bootlog/" + spec.builtin() + ".txt";
            entries = BootLogLines.loadResource(BootLogRenderer.class.getClassLoader(), path);
        }
        for (String w : warnings) LOGGER.warn("[CCNR-Menu] {} —— {}", sourceName(spec), w);
        return entries;
    }

    private static String sourceName(BootLogSpec spec) {
        return spec.file().isEmpty() ? "内置 " + spec.builtin() : spec.file();
    }

    /** 把整体不透明度叠到行颜色上（淡入用）。 */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.min(1f, Math.max(0f, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 释放：这一层没有 GPU 资源（不占贴图），留着这个方法是让清理对称。 */
    public void close() {
        // 日志全部是每次绘制现算的 CPU 数据，没有需要释放的东西
    }

    /** 给 {@code /ccnr_menu status} 的一句话。 */
    public String describe() {
        String src = sourceName(spec);
        if (timeline.isEmpty() && feed.size() == 0) return "启动日志: 空（" + src + "）";
        return "启动日志: 剧本 " + timeline.lineCount() + " 行 + 事件 " + feed.size() + " 行，驱动 " + src
                + (spec.onAllScreens() ? "，所有接管界面" : "，仅主菜单") + "，锚点 " + spec.align()
                + " x=" + spec.x() + " y=" + spec.y();
    }
}
