#!/usr/bin/env python3
"""由 CCNR 图标源文件生成菜单素材预设（放进模组 jar 的 assets/ccnr_menu/presets/）。

素材来源（**只读**，不改动源文件）：
  /Users/bananaxiao/Documents/MirageV/CCNR图标/
    ├── CCNR图标_H-IMGnTXT横版图标文字_动画版.svg   ← 横版图标 + 入场动画（CSS @keyframes）
    └── CCNR背景.png                                ← 1920x1080 背景
  ~/Downloads/Image_*.png                           ← 轮播用的照片（可选，见下）

产出（两套配色）：
  logo_wide_white.png        横版图标静态图（浅色图形 + 品牌青描边，配深色背景）
  logo_wide_intro_white.png  入场动画精灵图（8 列 x 10 行 = 80 帧，每帧 512x171）
  logo_wide_mono.png         横版图标静态图（**全白单色**）
  logo_wide_intro_mono.png   入场动画精灵图（全白单色，同上网格）
  background.png             背景图（原样拷贝）
  slides/01.jpg …            轮播图（压到 1920 宽、JPEG，见下）

`mono` 是什么：把 SVG 里的两个主题变量 `--ccnr-ink` 与 `--ccnr-accent` **同时覆写成 #FFFFFF**，
于是整枚图标只剩一种颜色，只有透明度在塑形。

轮播图为什么要压：原始 PNG 是 1920x1009 / 2560x1440 的照片，单张 3~5MB、六张共 20MB。
压成 1920 宽的 JPEG（质量 82）后单张 300~500KB，画质在菜单背景上看不出差别，
而 jar 从 25MB 降到 5MB 左右。**必须用 macOS 自带的 `sips`**（脚本本来就已经是 macOS-only：
无头 Chrome 的路径是写死的）。
轮播图是可选的：`SLIDE_SRC_DIR` 里没有匹配文件时脚本会跳过这一步并说明
（素材仍在，只是本机没有源图）。

帧率：80 帧 / 2.6s ≈ 30fps。此前是 32 帧 ≈ 12.3fps，入场那几下快动作（pop/draw）能看出顿。
网格受贴图边长 4096 限制：每帧 512 宽 → 最多 8 列；行数取 10 行 = 4096x1710（约 28MB 显存）。

为什么要在**构建期**渲染而不是运行时解析 SVG：
  Minecraft 不认识 SVG。动画是 CSS @keyframes，只能在浏览器里跑。所以把动画
  **预渲染成精灵图**——运行时只是一张 PNG + 换 UV，零解析、零依赖（见 docs/02）。

依赖：Google Chrome（headless 截图）+ python3 标准库 + macOS `sips`（只用于压缩轮播图）。
**不需要 PIL**（自带 PNG 读写）。
用法：
  python3 scripts/make-icon-presets.py                # 全部重做（162 次无头 Chrome 启动，约 6~10 分钟）
  python3 scripts/make-icon-presets.py --only-slides  # 只重做轮播图（换照片时用这条，几秒钟）
"""

import io
import os
import shutil
import struct
import subprocess
import sys
import time
import zlib

HOME = os.path.expanduser("~")
SRC = os.environ.get("CCNR_ICON_DIR", os.path.join(HOME, "Documents/MirageV/CCNR图标"))
SLIDE_SRC = os.environ.get("CCNR_SLIDE_DIR", os.path.join(HOME, "Downloads"))
SLIDE_PREFIX = "Image_"
SLIDE_MAX_WIDTH = 1920
SLIDE_QUALITY = 82
SIPS = "/usr/bin/sips"
MODULE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(MODULE, "src/main/resources/assets/ccnr_menu/presets")
TMP = os.path.join(MODULE, "build/icon-presets")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

ANIMATED_SVG = "CCNR图标_H-IMGnTXT横版图标文字_动画版.svg"
BACKGROUND_PNG = "CCNR背景.png"

# 静态版不再单独需要：动画版的最后一帧就是静止态（脚本按 INTRO_SECONDS 取满整段），
# 所以旧版用的 «..._图标文字.svg» 已经不读了。

# 横版画板是 3:1（SVG viewBox 1536x512）。精灵图每帧 512x171 是它的 1/3 缩放：
# 菜单里图标显示宽度约 300 GUI 单位，按 GUI scale 2 计约 600 物理像素，
# 512 宽的源够清晰，同时整张贴图 4096x1710（约 28MB）不至于太占显存。
FRAME_W, FRAME_H = 512, 171
COLS, ROWS = 8, 10
FRAMES = COLS * ROWS
# 入场动画总时长约 2.6s（最长的一条 animation: delay 1.98s + duration 0.50s）。
# 80 帧 / 2.6s ≈ 30fps，配置里写 frameMs=33 即可。
INTRO_SECONDS = 2.60
STATIC_W, STATIC_H = 1536, 512

# 主题变量在 SVG 的 <style> 里：#ccnrLogo.theme-dark 定义浅色图形 + 品牌青强调色。
# `mono` 不是源文件里已有的主题，而是本脚本注入的一条规则（源文件只读，不改）。
THEME_DARK_RULE = "#ccnrLogo.theme-dark  { --ccnr-ink: #E6EDF3; --ccnr-accent: #4FD1E0; }"
MONO_RULE = "    #ccnrLogo.theme-mono  { --ccnr-ink: #FFFFFF; --ccnr-accent: #FFFFFF; }\n"

HARNESS = """<!doctype html><html><head><meta charset="utf-8">
<style>html,body{{margin:0;padding:0;background:transparent;overflow:hidden}}
#stage{{width:{w}px;height:{h}px}}
#stage > svg {{width:{w}px;height:{h}px;display:block;}}</style></head>
<body><div id="stage">{svg}</div>
<script>
window.addEventListener('load',function(){{
  var T={t};
  if (T>=0) document.getAnimations().forEach(function(a){{a.pause();a.currentTime=T*1000;}});
}});
</script></body></html>"""


# ----------------------------------------------------------------------
# PNG 读写（8 位，非隔行；只够本项目用）
# ----------------------------------------------------------------------

def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png: " + path
    pos, idat, pal, trns = 8, b"", None, None
    w = h = ct = None
    while pos < len(data):
        ln, typ = struct.unpack(">I4s", data[pos : pos + 8])
        body = data[pos + 8 : pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, bd, ct, comp, filt, inter = struct.unpack(">IIBBBBB", body)
            assert bd == 8 and inter == 0, f"unsupported bd={bd} interlace={inter}"
        elif typ == b"PLTE":
            pal = body
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
    raw = zlib.decompress(idat)
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    stride = w * nch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p : p + stride])
        p += stride
        if f == 1:
            for i in range(nch, stride):
                line[i] = (line[i] + line[i - nch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                b = prev[i]
                c = prev[i - nch] if i >= nch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride : (y + 1) * stride] = line
        prev = line

    px = []
    for y in range(h):
        base = y * stride
        row = []
        for x in range(w):
            o = base + x * nch
            if ct == 6:
                row.append(tuple(out[o : o + 4]))
            elif ct == 2:
                row.append((out[o], out[o + 1], out[o + 2], 255))
            elif ct == 0:
                g = out[o]
                row.append((g, g, g, 255))
            elif ct == 4:
                g = out[o]
                row.append((g, g, g, out[o + 1]))
            else:
                i = out[o]
                r, g, b = pal[i * 3 : i * 3 + 3]
                a = trns[i] if trns and i < len(trns) else 255
                row.append((r, g, b, a))
        px.append(row)
    return w, h, px


def write_png(path, w, h, pix):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(pix[y][x])

    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        return c + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    out = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    open(path, "wb").write(out)
    print(f"  wrote {os.path.relpath(path, MODULE)}  {w}x{h}")


# ----------------------------------------------------------------------
# 渲染
# ----------------------------------------------------------------------

def shoot(svg, w, h, seconds, tag):
    """在 headless Chrome 里把 SVG 停在第 `seconds` 秒，截一张透明背景的图。"""
    hp = os.path.join(TMP, f"{tag}.html")
    pp = os.path.join(TMP, f"{tag}.png")
    io.open(hp, "w", encoding="utf-8").write(HARNESS.format(svg=svg, w=w, h=h, t=seconds))
    if os.path.exists(pp):
        os.remove(pp)
    prof = f"/tmp/ccnr-menu-preset-{tag}"
    proc = subprocess.Popen(
        [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
         "--no-default-browser-check", f"--user-data-dir={prof}", f"--window-size={w},{h}",
         "--force-device-scale-factor=1", "--default-background-color=00000000",
         f"--screenshot={pp}", "--virtual-time-budget=4000", "file://" + hp],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    # Chrome 截完图进程不退出，所以轮询文件大小直到稳定
    last, stable = -1, 0
    for _ in range(400):
        time.sleep(0.1)
        size = os.path.getsize(pp) if os.path.exists(pp) else -1
        if size > 0 and size == last:
            stable += 1
            if stable >= 3:
                break
        else:
            stable = 0
        last = size
    proc.terminate()
    try:
        proc.wait(timeout=10)
    except Exception:
        proc.kill()
    subprocess.run(["pkill", "-f", prof], capture_output=True)
    if not os.path.exists(pp) or os.path.getsize(pp) == 0:
        raise RuntimeError(f"Chrome 没有产出截图：t={seconds}s tag={tag}")
    return pp


def scale_attr(svg, w, h):
    """把 SVG 根节点的 width/height 换成目标尺寸（viewBox 不变，等于等比缩放）。"""
    import re

    head = svg[: svg.index(">") + 1]
    new_head = re.sub(r'width="[^"]*"', f'width="{w}"', head, count=1)
    new_head = re.sub(r'height="[^"]*"', f'height="{h}"', new_head, count=1)
    return svg.replace(head, new_head, 1)


def with_theme(svg, theme):
    return svg.replace('id="ccnrLogo"', f'id="ccnrLogo" class="{theme}"', 1)


def inject_mono_rule(svg):
    """注入 `mono` 主题规则。

    锚点找不到时**直接退出**而不是「默默按原样渲染」：那样产出的
    `logo_wide_mono.png` 会是一张带品牌青的图，名字却叫 mono——
    这种错不会让任何构建失败，只会在游戏里被看出来。
    """
    if THEME_DARK_RULE not in svg:
        sys.exit(f"源 SVG 里找不到主题规则锚点，无法注入 mono 主题：{THEME_DARK_RULE}")
    return svg.replace(THEME_DARK_RULE, THEME_DARK_RULE + "\n" + MONO_RULE, 1)


def main():
    if "--only-slides" in sys.argv:
        render_slides()
        return
    if not os.path.isfile(CHROME):
        sys.exit(f"找不到 Chrome：{CHROME}（渲染 SVG 需要它）")
    anim_path = os.path.join(SRC, ANIMATED_SVG)
    bg_path = os.path.join(SRC, BACKGROUND_PNG)
    for p in (anim_path, bg_path):
        if not os.path.isfile(p):
            sys.exit(f"图标源文件缺失：{p}（可用环境变量 CCNR_ICON_DIR 指定图标目录）")

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(TMP, exist_ok=True)
    flag = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
    animated = inject_mono_rule(io.open(anim_path, encoding="utf-8").read().replace(flag, ""))

    # 两套配色：theme-dark = 浅色图形 + 品牌青（配深色背景）；theme-mono = 全白单色
    themes = (("theme-dark", "white"), ("theme-mono", "mono"))

    # 1) 静态横版图标
    for theme, name in themes:
        print(f"渲染静态横版图标（{name}）...")
        png = shoot(with_theme(scale_attr(animated, STATIC_W, STATIC_H), theme),
                    STATIC_W, STATIC_H, 99.0, f"static_{name}")
        shutil.copyfile(png, os.path.join(OUT, f"logo_wide_{name}.png"))
        print(f"  wrote presets/logo_wide_{name}.png  {STATIC_W}x{STATIC_H}")

    # 2) 入场动画精灵图：按 INTRO_SECONDS 均匀取 FRAMES 帧，最后一帧即静止态
    for theme, name in themes:
        print(f"渲染 {name} 入场动画 {FRAMES} 帧（{COLS}x{ROWS}，每帧 {FRAME_W}x{FRAME_H}）...")
        sheet = [[(0, 0, 0, 0) for _ in range(COLS * FRAME_W)] for _ in range(ROWS * FRAME_H)]
        for i in range(FRAMES):
            seconds = round(i * INTRO_SECONDS / FRAMES, 4)
            frame_png = shoot(with_theme(scale_attr(animated, FRAME_W, FRAME_H), theme),
                              FRAME_W, FRAME_H, seconds, f"intro_{name}{i:02d}")
            w, h, px = read_png(frame_png)
            assert (w, h) == (FRAME_W, FRAME_H), f"帧尺寸异常 {w}x{h}"
            ox, oy = (i % COLS) * FRAME_W, (i // COLS) * FRAME_H
            for y in range(h):
                sheet[oy + y][ox : ox + w] = px[y]
            print(f"  {name} 帧 {i + 1}/{FRAMES}  t={seconds}s")
        write_png(os.path.join(OUT, f"logo_wide_intro_{name}.png"), COLS * FRAME_W, ROWS * FRAME_H, sheet)

    # 3) 背景图原样拷贝（1920x1080；不重编码，避免二次压缩）
    shutil.copyfile(bg_path, os.path.join(OUT, "background.png"))
    print("  wrote presets/background.png  (拷贝 %s)" % BACKGROUND_PNG)

    # 4) 轮播图：压到 1920 宽 + JPEG
    render_slides()

    print("\n完成。模组首次启动会把这些文件拷进 config/ccnr_menu/（已存在则不覆盖）。")


def render_slides():
    """把 ~/Downloads/Image_*.png 压成 1920 宽的 JPEG 放进 presets/slides/。"""
    if not os.path.isdir(SLIDE_SRC):
        print(f"\n跳过轮播图：源目录不存在 {SLIDE_SRC}（可用 CCNR_SLIDE_DIR 指定）")
        return
    sources = sorted(
        os.path.join(SLIDE_SRC, n)
        for n in os.listdir(SLIDE_SRC)
        if n.startswith(SLIDE_PREFIX) and n.lower().endswith(".png")
    )
    if not sources:
        print(f"\n跳过轮播图：{SLIDE_SRC} 里没有 {SLIDE_PREFIX}*.png")
        return
    if not os.path.isfile(SIPS):
        sys.exit(f"找不到 sips：{SIPS}（压缩轮播图需要 macOS 自带的 sips）")

    target_dir = os.path.join(OUT, "slides")
    os.makedirs(target_dir, exist_ok=True)

    # 先删掉上一次的产物：源图变少时，残留的旧编号会被 PresetAssetsTest 判成「生成了却没进清单」
    for name in os.listdir(target_dir):
        if name.endswith(".jpg"):
            os.remove(os.path.join(target_dir, name))

    print(f"渲染轮播图 {len(sources)} 张（压到 {SLIDE_MAX_WIDTH} 宽，JPEG 质量 {SLIDE_QUALITY}）...")
    total = 0
    for i, source in enumerate(sources, start=1):
        target = os.path.join(target_dir, f"{i:02d}.jpg")
        proc = subprocess.run(
            [SIPS, "-Z", str(SLIDE_MAX_WIDTH), "-s", "format", "jpeg",
             "-s", "formatOptions", str(SLIDE_QUALITY), source, "--out", target],
            capture_output=True)
        if proc.returncode != 0 or not os.path.isfile(target):
            sys.exit(f"sips 转换失败：{source}\n{proc.stderr.decode('utf-8', 'replace')}")
        size = os.path.getsize(target)
        total += size
        print(f"  slides/{i:02d}.jpg  {size / 1024:.0f}KB  源={os.path.basename(source)}")
    print(f"  合计 {total / 1024 / 1024:.1f}MB")


if __name__ == "__main__":
    main()
