<div align="center">

# CCNR-Menu 主菜单自定义模组（Forge 1.20.1）

用**一份 JSON 配置**改掉服务器的 Minecraft 主菜单：背景、配色、标题、按钮的文字·位置·动作。

**当前版本：`0.2.1`**（开发期；版本号规范见 [docs/04-版本号规范.md](docs/04-版本号规范.md)）

</div>

---

## 这个模组解决什么问题

服务器的招牌、公告群号、要进的那个 IP，本来都只能靠玩家自己记。原版主菜单能改的东西只有
资源包里的贴图，而「把全景图换成我们自己的动图、把按钮换成『进入服务器』」这件事，
要么装一个几十 MB 的浏览器引擎模组（MCEF 那一类，客户端首次启动要下载 Chromium），
要么放弃。

本模组走的是**零依赖**路线：动画背景用 JDK 自带的 `ImageIO` 解码 GIF，
精灵图动画走原版贴图管线，**不需要任何原生库、不需要前置模组**，一个 jar 丢进 `mods/` 就能用。

| | 原版 | 浏览器引擎方案 | CCNR-Menu |
| --- | --- | --- | --- |
| 换背景 | 只能换资源包贴图 | 可以，但要下载上百 MB Chromium | 图片 / 动画 GIF / 精灵图 / 纯色 |
| 换按钮 | 不行 | 可以（HTML 里写） | 配置文件里写 |
| 前置 | 无 | MCEF + 原生库 | **无** |
| 改完生效 | 重启 | 重启 | `/ccnr_menu reload` |
| 服务端 | — | — | 纯客户端，装不装都一样 |

---

## 首页长什么样

默认布局就是「**横版大图标在上、一列按钮在下、整块靠右、右边缘对齐**」：

```
                       ┌───────────────────────────────┐
                       │      CCNR 横版图标（动画）      │   ← 位置：x=0.955 靠右
                       └───────────────────────────────┘
                                     ┌───────────────┐
                                     │   进入服务器   │
                                     ├───────────────┤
                                     │   单人游戏     │   ← 按钮列右边缘
                                     ├───────────────┤      与图标右边缘对齐
                                     │     设置       │
                                     ├───────────────┤
                                     │   退出游戏     │
                                     └───────────────┘
```

做法是配置里的一个 `column` 容器：整块按分数锚点靠右摆放（`align: right`），
列内用 `childAlign: right` 让子元素右边缘对齐。想改成居中/靠左只需改这两个字段。

## 功能一览

- **背景**（`background.type`）
  - `vanilla` 原版全景图（默认，装上等于没装）
  - `image` 单张静态图片（PNG / JPG / GIF 首帧）
  - `sheet` **精灵图序列帧**（一张 PNG + 行列数；每帧零解码零上传，最省）
  - `gif` **动画 GIF**（导入即用；逐帧时长按 GIF 自带节奏，超内存预算自动整倍缩小）
  - `color` / `none` 纯色 / 黑屏
  - 铺屏方式 `cover` / `contain` / `stretch` / `center` / `tile`，另有 `tint` 与 `opacity`
- **元素**（`elements`）：`button`（可点，带动作）、`label`（文字，可缩放）、`image`（装饰图）
  - 定位用 0~1 的分数锚点 + `align`/`valign`，另有 `offsetX`/`offsetY` 像素微调
- **动作**（`action`）：`screen:<原版界面>`、`connect:<地址>`、`url:<https 链接>`、`copy:<文本>`、`quit`、`none`
- **配色**（`theme`）：按钮三态颜色、左侧强调条、文字层、压暗层
- **容器**：`column` 竖列（列宽 / 间距 `gap` / 列内对齐 `childAlign`），子元素可再嵌套
- **动画图标**：`image` 元素也支持 `animation`（序列帧），入场动画可以播一次停在末帧
- **背景范围**（`applyToAllScreens`，默认开）：**泥土界面**（世界选择/多人列表/设置/语言/Mod 列表）
  与全景图一起换成同一张背景；**世界内的界面**（暂停、背包）不动
- **内置素材**：随 jar 发布 CCNR 横版图标（静态 + 入场动画精灵图，深浅两套配色）与背景图，
  首次运行自动释放到 `config/ccnr_menu/`（**已存在则不覆盖**）
- **`vanillaButtons: true`**：只换背景，按钮保持原版那套（单人/多人/设置/退出/Mods）
- **命令**：`/ccnr_menu status`（当前读到的一切 + 所有警告）、`reload`、`open`、`where`

---

## 安装

1. 把 `ccnr_menu-<版本>.jar` 放进 `.minecraft/mods/`（**只需客户端**）；
2. 启动一次游戏，会自动生成：
   - `config/ccnr_menu/menu.json` —— 生效的配置（默认 = 原版外观）
   - `config/ccnr_menu/menu.example.json` —— 带全套示例的参考文件（**不参与加载**，随便改）
3. 想要自己的素材就放进 `config/ccnr_menu/` 并照着 [docs/03-菜单配置.md](docs/03-菜单配置.md) 改 `menu.json`
   （不放也行——内置素材已经能让菜单出图）；
4. 回到主菜单看一眼，或执行 `/ccnr_menu reload`。

> 改坏了也不会进不去游戏：配置有问题时模组会退回「原版按钮 + 原版背景」，
> 并把原因写进日志与 `/ccnr_menu status`。

---

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/00-总览.md](docs/00-总览.md) | 架构、包布局、数据流、与其它 CCNR 仓库的关系 |
| [docs/01-工程规范.md](docs/01-工程规范.md) | 开发纪律（先读后写、线程边界、对称清理、可测性） |
| [docs/02-背景与动画.md](docs/02-背景与动画.md) | 背景方案怎么选、GIF 解码与内存预算、怎么做精灵图 |
| [docs/03-菜单配置.md](docs/03-菜单配置.md) | **配置字段全表 + 完整示例** |
| [docs/04-版本号规范.md](docs/04-版本号规范.md) | 版本号怎么编、写在哪几处、门禁是什么 |

---

## 构建（开发者）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME=/Users/bananaxiao/Documents/MirageV/mod/CCNR-Com/.gradle-home

./gradlew build              # 编译 + spotlessCheck 门禁，产出 build/libs/ccnr_menu-*.jar
./gradlew test -PrunTests    # 单元测试（不加 -PrunTests 会被跳过）
./gradlew spotlessApply      # 唯一格式化入口（提交前必跑）
```

## 许可

MIT，见 [LICENSE](LICENSE)。
