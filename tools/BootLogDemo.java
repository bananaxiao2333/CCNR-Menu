/*
 * Copyright (c) 2026 CCNR
 * SPDX-License-Identifier: MIT
 */

import com.ccnrcom.menu.ui.BootLogKind;
import java.util.List;
import com.ccnrcom.menu.ui.BootLogRow;
import com.ccnrcom.menu.ui.BootLogSnapshot;
import com.ccnrcom.menu.ui.BootLogTimeline;

/**
 * 启动日志背景的**纯逻辑预览**（终端里跑，不起 Minecraft）。
 *
 * <pre>
 * cd CCNR-Menu
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
 * $JAVA_HOME/bin/javac -encoding UTF-8 -d /tmp/bootlog \
 *     src/main/java/com/ccnrcom/menu/ui/BootMeter.java src/main/java/com/ccnrcom/menu/ui/BootLog*.java tools/BootLogDemo.java
 * $JAVA_HOME/bin/java -cp /tmp/bootlog BootLogDemo             # 播一遍（约 16 秒）
 * $JAVA_HOME/bin/java -cp /tmp/bootlog BootLogDemo 3500 150    # 只看第 3.5 秒那一帧
 * </pre>
 *
 * <p>它证明三件事：**行怎么随时间出现、进度条怎么动、靠左排布长什么样**。
 * 上屏那一层（颜色、字体、右边界）是 {@code BootLogBackground} 的事，这里用 ANSI 代替。
 */
public final class BootLogDemo {

    private static final String[] KERNEL = {
        "[    0.000000] Linux version 6.8.0-45-generic (buildd@lcy02-amd64-100) (x86_64-linux-gnu-gcc-13.2.0 (Ubuntu 13.2.0-23ubuntu1) 13.2.0, GNU ld) #45-Ubuntu SMP PREEMPT_DYNAMIC",
        "[    0.000000] Command line: BOOT_IMAGE=/boot/vmlinuz-6.8.0-45-generic root=UUID=8f2a9c41-6b7e-4d02-9c1f-3e5b7a0d2c88 ro quiet splash vt.handoff=7",
        "[    0.000000] BIOS-provided physical RAM map:",
        "[    0.000000] BIOS-e820: [mem 0x0000000000000000-0x000000000009fbff] usable",
        "[    0.000000] NX (Execute Disable) protection: active",
        "[    0.000000] DMI: ASUSTeK COMPUTER INC. ROG STRIX X570-I GAMING, BIOS 5003 01/13/2024",
        "[    0.021000] tsc: Detected 3800.000 MHz processor",
        "[    0.092000] Memory: 32548640K/33470848K available (16384K kernel code, 2764K rwdata)",
        "[    0.118000] SLUB: HWalign=64, Order=0-3, MinObjects=0, CPUs=16, Nodes=1",
        "[    0.204000] smpboot: CPU0: AMD Ryzen 9 5950X 16-Core Processor (family: 0x19, model: 0x21)",
        "[    0.312000] ACPI: Core revision 20230628",
        "[    0.498000] pci 0000:00:01.1: [1022:1483] type 01 class 0x060400",
        "[    0.612000] usb usb1: New USB device found, idVendor=1d6b, idProduct=0002",
        "[    0.744000] nvme nvme0: 8/0/0 default/read/poll queues",
        "[    0.861000] EXT4-fs (nvme0n1p2): mounted filesystem with ordered data mode. Quota mode: none.",
        "[    1.020000] systemd[1]: systemd 255.4-1ubuntu8.4 running in system mode (+PAM +AUDIT +SELINUX)",
    };

    /** systemd 段：{前缀, 服务名, 后缀, 状态}。 */
    private static final String[][] UNITS = {
        {"Reached target", "Basic System", "", "ok"},
        {"Started", "Journal Service", "", "ok"},
        {"Starting", "Load Kernel Modules", "", "run"},
        {"Finished", "Load Kernel Modules", "", "ok"},
        {"Mounted", "Kernel Configuration File System", "", "ok"},
        {"Started", "udev Kernel Device Manager", "", "ok"},
        {"Reached target", "Local FS", "", "ok"},
        {"Started", "Rule-based Manager for Device Events and Files", "", "ok"},
        {"Starting", "Network Manager", "", "run"},
        {"Started", "Authorization Manager", "", "ok"},
        {"Started", "Daemon for power management", "", "ok"},
        {"Started", "D-Bus System Message Bus", "", "ok"},
        {"Started", "Network Manager", "1.46.0", "ok"},
        {"Started", "User Login Management", "", "ok"},
        {"Started", "Permit User Sessions", "", "ok"},
        {"Started", "Record System Boot/Shutdown in UTMP", "", "ok"},
        {"Started", "GNOME Display Manager", "", "ok"},
        {"Reached target", "Graphical Interface", "", "ok"},
        {"Failed to start", "ccnr-agent.service", "exit code 203/EXEC", "fail"},
        {"", "nginx.service", "active (running) since Thu 2026-10-08 03:14:22 CST", "info"},
        {"", "mysql.service", "active (running) since Thu 2026-10-08 03:14:19 CST", "info"},
        {"", "ccnr-rp.service", "active (running) since Thu 2026-10-08 03:14:24 CST", "info"},
        {"Reached target", "Multi-User System", "", "ok"},
    };

    public static void main(String[] args) {
        // 传 driver 作为第一个参数 = 直接用**随模组发布的那份驱动**渲染，
        // 这样预览的就是玩家真正会看到的内容，而不是 demo 里另抄的一份。
        if (args.length > 0 && args[0].equals("driver")) {
            long atMs = args.length > 1 ? Long.parseLong(args[1]) : 3000;
            int cols = args.length > 2 ? Integer.parseInt(args[2]) : 150;
            int rows = args.length > 3 ? Integer.parseInt(args[3]) : 28;
            BootLogTimeline t = fromDriver();
            if (t.isEmpty()) {
                System.out.println("驱动读不到（要先把 src/main/resources 放进 classpath）");
                return;
            }
            print(t, atMs, cols, rows);
            return;
        }
        long atMs = args.length > 0 ? Long.parseLong(args[0]) : -1;
        int cols = args.length > 1 ? Integer.parseInt(args[1]) : 150;
        int rows = args.length > 2 ? Integer.parseInt(args[2]) : 26;
        BootLogTimeline timeline = build();

        if (atMs >= 0) {
            print(timeline, atMs, cols, rows);
            return;
        }
        for (long t = 0; t <= timeline.totalMs() + 1500; t += 600) {
            System.out.print("\033[H\033[2J");
            print(timeline, t, cols, rows);
            sleep(600);
        }
    }

    /** 读随模组发布的那份驱动（{@code assets/ccnr_menu/bootlog/ubuntu.txt}）并组装时刻表。 */
    static BootLogTimeline fromDriver() {
        try {
            List<com.ccnrcom.menu.ui.BootLogLines.Entry> entries = com.ccnrcom.menu.ui.BootLogLines.loadResource(
                    BootLogDemo.class.getClassLoader(), "assets/ccnr_menu/bootlog/ubuntu.txt");
            return com.ccnrcom.menu.ui.BootLogLines.toTimeline(
                    entries,
                    95,
                    List.of(
                            "A start job is running for Hold until boot process finishes up",
                            "[  OK  ] Reached target Cloud-init target"),
                    com.ccnrcom.menu.ui.BootMeter.SYSTEMD);
        } catch (Exception e) {
            return BootLogTimeline.empty();
        }
    }

    /** 造一份约 16 秒的 Ubuntu 启动日志（内核段 + 卡住的启动任务 + systemd 段 + 一条失败）。 */
    static BootLogTimeline build() {
        BootLogTimeline.Builder b = new BootLogTimeline.Builder();
        for (String line : KERNEL) b.line(line, BootLogKind.KERNEL);
        b.spinner(
                List.of(
                        "A start job is running for Hold until boot process finishes up",
                        "[  OK  ] Reached target Cloud-init target"),
                "no limit",
                BootLogKind.RUN);
        for (String[] u : UNITS) {
            String text = u[2].isEmpty() ? u[0] + " " + u[1] + "." : u[0] + " " + u[1] + " " + u[2] + ".";
            b.line(text, BootLogKind.parse(u[3]));
        }
        return b.build();
    }

    /** 渲染某一时刻的可见画面（靠左，列宽固定）。 */
    static void print(BootLogTimeline timeline, long atMs, int cols, int rows) {
        BootLogSnapshot snap = timeline.at(atMs, rows);
        System.out.println("+" + "-".repeat(cols) + "+");
        for (BootLogRow row : snap.rows()) {
            String line = row.kind().marker() + row.text();
            if (line.length() > cols - 2) line = line.substring(0, cols - 2);
            String pad = " ".repeat(Math.max(0, cols - 2 - line.length()));
            System.out.println("| \033[" + row.kind().ansi() + "m" + line + "\033[0m" + pad + " |");
        }
        for (int i = snap.visibleLines(); i < rows; i++) System.out.println("| " + " ".repeat(cols - 3) + " |");
        System.out.println("+" + "-".repeat(cols) + "+");
        BootLogRow spin = snap.spinner();
        System.out.println("t=" + atMs + "ms  可见行=" + snap.visibleLines() + "/" + snap.totalLines() + "  完成="
                + snap.finished()
                + (spin == null ? "" : "\n进行中: " + spin.text()));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
