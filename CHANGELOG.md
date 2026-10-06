# 更新日志

## 0.4.0（图片轮播背景 + 泥土页面居中标志；删掉黑底彩色文字背景）

用户这一轮的反馈：背景改成**多张图片轮播**（切换时淡入淡出，每张再放大一点点并缓慢往右偏移）；
上一版那套「黑底彩色文字」**删掉**；**泥土页面**也要全部换掉，背景用同一套轮播、
正中间放一个**白色横版服务器图标**。

### 做了什么

- **删除** `background.type = "text"` 与它的一整套实现（`config/TextSpec`、`ui/TextWave`、
  `client/background/TextBackground` 及对应测试）。用户明确否掉了它，留着就是没人维护的死代码。
  老配置里还写着 `type: "text"` 的会退回原版全景图并给出「类型不认识」的警告——不静默。
- **新增 `background.type = "slideshow"`**：多图轮播。
  - `slides` 图片列表（`background.file` 那套单文件逻辑不适用，见 `assetFiles()`）；
  - `holdMs` 每张停留、`fadeMs` 末尾交叉淡入；
  - `zoom` / `panX` / `panY` 运镜：每张图**各自**从 1.0 倍开始缓慢推近、向右偏移
    （连续走完整条时间轴的话，切到第 5 张时画面已经放大到 1.4 倍，一出现就是糊的）；
  - `loop` 播完是否回到第一张，`false` 时停在最后一张。
  - 时刻表在纯类 `ui/SlideTimeline`（可单测，`SlideTimelineTest` 9 条），
    运镜几何在 `MenuGeometry.kenBurns`。
- **内存与卡顿**：屏幕上任何时刻最多只看得见 2 张，所以只保留「当前 + 下一张」两张贴图，
  切换完成后立刻释放其余；下一张在**上一个停留周期开始时**就丢给工作线程解码
  （1920 宽的 JPEG 要几十毫秒，放在切图那一帧上就是一次肉眼可见的顿）。
  线程边界与 GIF 那边一致：工作线程只做纯计算（`FileTexture.decodeFile`），
  建贴图与上传经 `Minecraft.execute` 回到渲染线程（`FileTexture.fromImage`）。
- **新增 `mark` 配置块**：泥土页面正中间的标志（`file`/`width`/`opacity`/`x`/`y`/`onMainMenu`）。
  默认只画在**非主菜单**的界面上——主菜单左列里已经有一个图标，正中间再画一个就是两个 logo 打架。
  绘制顺序是「背景 → 压暗层 → 标志」：标志画在压暗层之下会被压成灰的，看起来像没加载出来。
- **轮播素材随模组发布**：`~/Downloads/Image_*.png` 六张照片，由生成脚本压成 1920 宽的 JPEG
  （质量 82，单张 160~465KB，合计 1.6MB，放进 `presets/slides/`）。
  原始 PNG 六张共 20MB，压完 jar 从 25MB 降到 5MB 左右。
  脚本新增 `--only-slides`：换照片时不必重跑 162 次无头 Chrome 截图。
- **升级迁移**：`MenuDefaults` 新增 0.3.0 那份默认，所以从 0.3.0 升上来、
  且**从未改过** `menu.json` 的玩家会自动换成新的轮播默认（旧文件备份 `.bak`）。

### 旧配置会怎样

- 0.1.0 / 0.2.1 / 0.3.0 的默认配置（**且未经修改**）升级时自动替换为新默认，旧文件备份 `.bak`；
- 自己改过的配置**一律不动**，需要新版默认请执行 `/ccnr_menu reset`（会先备份 `.bak`）；
- 自己配了 `background.type: "text"` 的：这个类型已经没有了，会退回原版全景图并给出警告
  （`docs/03` 里给了改用 `slideshow` 或 `image` 的做法）；
- 没写 `mark` 的配置不会在泥土页面上画任何东西（默认不画，不是「默认画点什么」）。

## 0.3.0（布局靠左 + 无背景纯文字按钮 + 全白图标 30fps + 黑底彩色文字背景）

用户这一轮提了五件事：整体靠左、按钮不要材质只要文字、图标全白、帧率高一点、
背景改成纯黑上用彩色文字做动画（用 `[ ] = - +` 这类符号装饰、分段）。

### 做了什么

- **布局靠左**：默认竖列从 `x: 0.955 / align: right / childAlign: right` 改成
  `x: 0.04 / align: left / childAlign: left`，图标与按钮左边缘对齐。
- **无背景纯文字按钮**：`theme.buttonStyle` 新增，取值 `solid`（默认，原样）与 `text`。
  `text` 不画底色/描边/强调竖条，标签从按钮矩形**左边缘**起画。
  键盘焦点改用**文字下划线**表达——实心底用「内描边 = 焦点」，纯文字没有描边可用，
  但「焦点与悬停必须是两个视觉通道」这条纪律不能因为换了外观就丢掉。
  另外：`text` 外观下按钮不写 `width` 时**宽度贴着文字**。纯文字按钮的热区是看不见的，
  若还留 200 宽的隐形矩形，鼠标停在文字右边两厘米的空白上文字也会变色——玩家会以为按钮坏了。
- **图标全白**：`scripts/make-icon-presets.py` 注入一条 `theme-mono`
  主题规则（`--ccnr-ink` 与 `--ccnr-accent` 同时覆写成 `#FFFFFF`）并产出
  `logo_wide_mono.png` / `logo_wide_intro_mono.png`。数值校验：非透明像素 64160 个，
  不同 RGB **只有 1 种**（`#FFFFFF`），且与彩色版逐像素覆盖数一致（形状没变）。
- **帧率 12.3fps → 30fps**：精灵图网格从 8×4（32 帧 / `frameMs: 81`）改成
  8×10（80 帧 / `frameMs: 33`），整张贴图 4096×1710（约 28MB 显存）。
  入场那几下快动作（pop / draw）原来能看出顿。
- **黑底彩色文字背景**：`background.type` 新增 `text`，零素材。配置见 `background.text`：
  `segments`（分段，每段 `left`/`text`/`right`/`offset`，装饰符号由作者直接写）、
  `palette`（调色板）、`charMs`（逐字写出）、`stepMs`（色带流动）、`holdMs`/`loop`、
  `scale`/`lineGap`/`x`/`y`/`align`/`valign`。新纯类 `ui/TextWave` 管动画数学，
  `ui/ButtonStyle` 管外观枚举，两者都可单测。
  字号是绝对像素而 GUI 尺寸随窗口变化，所以整块放不下时会**按整数倍降到刚放得下**（下限 1 倍）并记一条日志——
  否则一份默认配置在小窗口下就是「字被切了一半」，而作者在自己机器上永远看不到那种窗口。
- **默认配置相应改写**：黑底（`#000000`）彩色文字背景 + 靠左列 + 全白图标 + 纯文字按钮；
  背景本来就黑，`theme.backdrop` 因此设成 `#00000000`（不再压一层遮罩）。
- **升级迁移照旧生效**：`MenuDefaults` 的历史默认清单改成「**版本 → 配置**」，
  新增 0.2.1 那份右侧竖列默认。旧写法用「长得像不像原版」当「是不是我们发布的」的替身判据，
  而 0.2.1 起默认菜单本身就不是原版外观了，替身判据失效。现在每条都必须标注发布版本，
  `MenuDefaultsTest` 去 CHANGELOG 核对这个版本真的存在——凭印象编一个版本号会被拦下。
- 顺带修：背景实例指纹原来不含 `theme`，只改配色时压暗层不会重建。

### 旧配置会怎样

- 老版本默认配置（0.1.0 / 0.2.1 那两份，**且未经修改**）升级时自动替换为新默认，旧文件备份 `.bak`；
- 自己改过的配置**一律不动**，需要新版默认请执行 `/ccnr_menu reset`（会先备份 `.bak`）；
- `theme.buttonStyle` 缺失时按 `solid` 处理，老配置的按钮外观不变；
- 不再发布 `logo_wide_black.png` / `logo_wide_intro_black.png`（配浅色背景的深色图标已无用武之地），
  已经释放到 `config/ccnr_menu/` 的副本不会被删除。

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
