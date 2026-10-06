# 更新日志

## 0.2.1（修升级路径：新的默认配置进不去）

用户报「装了 0.2.0，一点变更都没有」。查日志：`[CCNR-Menu] 配置已加载: 背景=VANILLA 按钮=原版 自定义元素=0`。

### 根因

`MenuConfigIO.ensureDefaults()` 的规则是「**绝不覆盖已存在的配置文件**」（作者改过的文件不能被抹掉），
但这条规则有个副作用：**更新模组版本时新的默认配置永远进不去**。
0.1.0 生成的 `menu.json`（`vanillaButtons: true` + 原版全景图 + 空元素）在 0.2.0 启动时被原样保留，
于是菜单看上去和原版一样——而日志一切正常，玩家没有任何线索。

### 修法

- 升级时识别「**未被修改过的旧版默认配置**」并替换为新版默认，旧文件备份为 `menu.json.bak`。
  判据在纯类 `MenuDefaults`：**Gson 解析后结构完全一致**才算未修改（玩家重排缩进/键顺序仍算未修改，
  改过一个值、加过一个字段、动过一个元素都一律不碰）。`MenuDefaultsTest` 把这条判据的宽窄两个方向都钉住，
  另有一条门禁确保「历史默认清单里只能是原版外观的配置」——判宽一格就会覆盖玩家的布局。
- 新增 `/ccnr_menu reset`：显式恢复出厂（先把当前文件备份为 `.bak`）。
  迁移只处理从未改过的文件，改过配置的人需要这个入口。
- 接管主菜单的日志从 DEBUG 提到 **INFO**：排查「装了但没变化」时第一句要确认的就是「到底接管了没有」，
  而玩家手上只有 `latest.log`（DEBUG 不进这个文件）。

## 0.2.0（主菜单右侧竖列布局 + 泥土界面统一换背景 + 内置 CCNR 素材）

按用户要求把首页定成「横版大图标在上、一列按钮在下、整块靠右、右边缘对齐」，
并把**泥土背景**与**全景图**一起换掉。

### 做了什么

- **背景范围扩大到所有「泥土界面」**：用 `ScreenEvent.BackgroundRendered`（Forge 在
  `Screen.renderBackground()` 与 `renderDirtBackground()` 画完之后各发一次的钩子）把背景盖上去，
  于是世界选择、多人列表、设置、语言、Mod 列表这些原来画泥土图的界面也统一成同一张背景。
  **世界内的界面（暂停、背包、箱子）刻意不动**——它们的背景是玩家眼前的世界，盖掉会失去空间感。
  判定抽成纯类 `BackgroundScope`，两个绘制点共用（`BackgroundScopeTest` 覆盖四档组合）。
- **背景改成全模组共享一份**（`ScreenBackgrounds`）：按「全部参数 + 素材文件修改时间」组成指纹持有唯一实例。
  主菜单 → 设置 → 返回这条最常见的路径上，旧实现会两次重复解码同一张 GIF；
  现在只在指纹变化（改配置/换文件）时重建，并在**进入世界**时释放几十 MB 的动画帧。
- **新增 `column` 竖列容器**：整块按分数锚点摆放，子元素在列内竖直堆叠 + 按 `childAlign` 对齐。
  「图标与按钮列右边缘对齐」= 列靠右摆放 + 列内 `right` 对齐两步，不需要任何魔法数字
  （`MenuGeometry.stack` 是纯函数，`MenuGeometryTest` 覆盖左/中/右与自动列宽）。
- **图片元素支持序列帧动画**：`image` 现在可以带 `animation`（与背景共用 `AnimationParser`），
  于是**横版图标的入场动画**能在菜单里播放（`loop: false` = 播一次停在末帧）。
  修了一个会踩的坑：动画图标的宽高比必须按**单帧**算，不是按整张精灵图
  （4096×684 的整图是 6:1，单帧是 3:1，用错会把图标压扁一半）。
- **内置素材预设**：把 CCNR 图标的 SVG 动画**预渲染**成贴图随 jar 发布
  （`scripts/make-icon-presets.py` 用无头 Chrome 逐帧截图再拼成 8×4 精灵图），
  首次运行时自动释放到 `config/ccnr_menu/`，**已存在则不覆盖**：
  `background.png`、`logo_wide_black/white.png`、`logo_wide_intro_black/white.png`。
  两种配色分别对应图标源文件里的 `theme-light`（近黑墨色，配浅背景）与
  `theme-dark`（近白 + 品牌青 `#4FD1E0`，配深背景）——默认配置用的是前者（灰底 + 深色图形，对比度最高）。
- **默认配色改用品牌青** `#4FD1E0`（取自图标 `--ccnr-accent`），按钮强调条与图标同色。
- 新增 `applyToAllScreens`（默认 `true`）、`childAlign`、`gap` 三个配置字段；
  `/ccnr_menu status` 增加「背景范围」一行；`/ccnr_menu reload` 会顺带重建已打开的菜单（否则背景变了、按钮还是旧的）。

### 门禁（`./gradlew test -PrunTests`）

新增 `BackgroundScopeTest`（4 条范围判定）、`PresetAssetsTest`（3 条：默认配置引用的素材必须随模组发布、
素材清单与磁盘文件双向一致、精灵图网格与配置相符）、`MenuGeometryTest` 的竖列排布（3 条）、
`MenuConfigTest` 的容器与动画图标（7 条）。合计 **110** 个测试。

## 0.1.0（首个版本：配置驱动的主菜单 + 三种动画背景）

从零建立本模组：把原版主菜单换成一份 JSON 配置描述的菜单，并第一次交付「轻量动画背景」这条路。

### 做了什么

- **接管主菜单**：用 Forge 的 `ScreenEvent.Opening` + `setNewScreen` 替换 `TitleScreen`，
  **不写任何 mixin**（少一类「开发环境正常、打包后失效」的故障，也不会和别人的 TitleScreen mixin 打架）。
- **背景**：`vanilla` / `image` / `sheet`（精灵图序列帧）/ `gif`（动画 GIF）/ `color` / `none`，
  另有 `fit`、`tint`、`opacity`。
- **动画 GIF 解码**（`gif` 包）：纯 Java，只用 JDK 自带的 `ImageIO`；
  按规范的顺序处理**部分帧**与 `disposalMethod`（画完清块 / 还原上一帧），
  逐帧时长按 GIF 自带的 `delayTime`，`< 20ms` 的帧按 100ms 规范（与浏览器一致）。
- **内存预算**（`GifBudget`）：解码**之前**就算清「宽×高×4×帧数」，
  超预算就整倍缩小边长（帧数与时长不变）。一个 1920×1080×60 帧的 GIF 原始要 475MB，
  会被缩到 1/3（约 55MB）——不这么做，玩家从网上随手存一张动图就能把游戏拖进 OOM。
- **逐帧懒转换**：GIF 帧只在**第一次播放到它时**才转成贴图（单帧约 0.1~1ms），
  避免「打开菜单瞬间搬运 N 帧像素」造成的卡顿。
- **元素与动作**：`button` / `label` / `image`，动作为
  `screen:<原版界面>`、`connect:`、`url:`、`copy:`、`quit`、`none`。
- **`url` 只允许 http/https**，`connect` 只允许 `主机[:端口]`：
  配置可以被转发，`file:` 会让客户端去打开本机文件。
- **防呆**：配置里一个 `button` 都没有时自动退回原版按钮并警告——
  一个没有按钮的主菜单会让玩家进不去设置、退不出游戏。
- **`/ccnr_menu status|reload|open|where`**：把「当前读到的是什么、缺哪个文件、哪些字段不认识」摊在聊天框里。

### 门禁（`./gradlew test -PrunTests`）

`MenuConfigTest`（含「默认配置与示例配置必须 0 警告」「没有按钮要退回原版」）、
`MenuActionTest`（含 `file:`/`javascript:` 必须被拒）、`MenuGeometryTest`（cover 取整不留黑线）、
`GifDecoderTest`（写一张 GIF 再解回来，验帧数/时长/像素）、`GifComposerTest`、
`GifBudgetTest`、`FrameClockTest`、`FrameTimelineTest`、`SpriteSheetTest`、`ColorSpecTest`、
`LangFileTest`、`MenuScreenMappingTest`、`ProjectMetadataTest`、`VersionConsistencyTest`。

### 已知边界（刻意不做）

- 只接管**主菜单**；暂停菜单、死亡界面等不动（背景/按钮那套配置只对主菜单生效）。
- 不做 Realms 按钮：那个界面类不在客户端 jar 里，用反射去开它只会把问题推到运行时。
- 不作视频背景、不作 HTML 渲染：前者要拖 FFmpeg 一类的原生依赖，后者要拖 Chromium，
  两个都和「轻量」直接冲突（取舍见 docs/02）。
