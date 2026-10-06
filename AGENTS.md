# AGENTS.md

## 🚫 硬性禁令（用户明确要求）

- **禁止启动服务器/客户端做测试**：不要跑 `./gradlew runServer` / `runClient`，也不要起任何 Minecraft 进程。
  本项目位于用户自己的机器上，起进程会占用资源、污染运行目录、干扰用户正在进行的实机测试。
- 因此**运行时行为一律由用户实机验证**，AI 侧只做到：`build`（编译 + spotless）、
  `test -PrunTests`、静态自检（grep），以及**把可测的纯逻辑抽出来加测试**。
- 这条禁令反过来决定了架构：几何、计时、GIF 解码、配置解析、动作白名单**全部**放在无 MC import
  的纯类里（`ui` 包、`gif` 包、`config` 包的模型类），否则「不准起客户端」就等于「什么都不验证」。

## Commands

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME=/Users/bananaxiao/Documents/MirageV/mod/CCNR-Com/.gradle-home   # 共享 2GB 缓存（勿提交）

./gradlew build              # 编译 + spotlessCheck 门禁，产出 build/libs/ccnr_menu-*.jar
./gradlew spotlessApply      # 唯一格式化入口（提交前必跑）
./gradlew test -PrunTests    # 单元测试（不加 -PrunTests 会被跳过！）
./gradlew compileJava        # 只验语法，最快
```

## 版本号（规范全文见 docs/04）

- 格式 `<主版本>.<功能批次>.<修订号>`；**唯一真源**是 `gradle.properties` 的 `mod_version`。
- CHANGELOG 标题写法以 CCNR-RP 为准：`## <版本>（<摘要>）`（全角括号、不带方括号）。
- 交付一次改动 → bump → CHANGELOG 顶部补条目 → README「当前版本」同步 → docs/04 的三处同步。
- `mods.toml` 用 `${file.jarVersion}` 注入，**不要**手写第二个版本号。
- 门禁 `VersionConsistencyTest` 会拦住不同步；`MenuScreenMappingTest`、
  `LangFileTest`、`ProjectMetadataTest` 分别守住界面白名单、语言键与元数据。

## Architecture

- `com.ccnrcom.menu` 入口 `CCNRMenuMod`（**纯客户端**：`FMLEnvironment.dist != CLIENT` 时直接退出）。
- **接管主菜单的方式是事件，不是 mixin**：`ScreenEvent.Opening` + `setNewScreen`
  （`TitleScreenHook`）。不写 mixin 是刻意的——它少一类「开发环境正常、打包后静默失效」的故障，
  也不会与别人对 `TitleScreen` 的 mixin 打架。
- **背景有两个绘制点、一份实现**：`MenuScreen.render`（自己的菜单）与
  `ScreenBackgroundHook`（`ScreenEvent.Init.Post` → 挂一件 `ScreenBackgroundRenderable`，
  原版泥土界面）。
  两者都调 `ScreenBackgrounds.render(...)`，范围判定都走纯类 `BackgroundScope`。
  背景实例按「参数 + 素材修改时间」的指纹共享，**进入世界**时释放。
- 包布局与数据流见 [docs/00](docs/00-总览.md) §3/§4；背景方案取舍见 [docs/02](docs/02-背景与动画.md)；
  配置字段全表见 [docs/03](docs/03-菜单配置.md)。
- **无任何外部模组依赖**（不引用 CCNR-RP / CCNR-PM / CCNR-Com，也不依赖 MCEF）。
  动画 GIF 用 JDK 自带的 `ImageIO`；精灵图走原版贴图管线。
- 纯类（可直接 JUnit 测）：`ui` 包全部、`gif` 包全部、`config` 的
  `MenuConfig` / `BackgroundSpec` / `SlideSpec` / `MarkSpec` / `MenuElement` / `MenuAction` /
  `MenuThemeSpec` / `MenuDefaults`。

## 开发纪律（完整版见 docs/01）

- **先读后写、修根因**：按「现象→调用链→根因→影响面→最小改动→实现→验证」推进；
  禁止用「每帧重建贴图」「延时重试」掩盖状态错误。
- **降级只有一个收口处**：`BackgroundFactory`。素材缺失/路径越界/格式不对/过大
  一律落到 `VanillaBackground` **并打一条能定位原因的日志**。
- **配置错误不静默**：颜色写错用默认色 + 警告；动作写错**跳过该按钮** + 警告；
  绝不留下「看起来能用但什么都不发生」的按钮。
- **对称清理**：贴图用 `TextureManager.release` 释放（不是直接 close）；
  `MenuScreen.init()` 会被反复调用（缩放/资源重载），所以那里要先释放上一轮的素材贴图，
  而**背景只建一次**；释放统一在 `removed()`。
- **`GuiGraphics.setColor` 用完必须复位**（`TextureDraw` 用 `finally`），
  否则颜色会泄漏到之后所有界面。
- **不要调用 `super.render()`**（`MenuScreen`）：`Screen.render` 会画泥土/渐变背景，把动画盖掉。
- **界面里禁止裸 `0x` 色值**：颜色只从 `MenuTheme` 取。
- **质量**：无占位/空壳/伪异步逻辑；**未实机验证的必须明说**；提交前查 `git diff`。

## Gotchas

- **构建环境**：需 JDK 21（系统默认不是它，务必 export JAVA_HOME），且 `GRADLE_USER_HOME`
  必须指向共享缓存（Forge 1.20.1 的 userdev 产物都在那里，指错会触发长时间重新反编译）。
- **测试门控**：`build` **不跑测试**；验收必须显式 `-PrunTests`。
  GIF 相关测试用 headless AWT（`build.gradle` 已设 `java.awt.headless=true`），不需要显示设备。
- **`blit` 的重载必须带贴图尺寸**：`blit(ResourceLocation, x, y, w, h)` 那个 7 参数重载
  假定贴图是 256×256，用在我们的动态贴图上会画错。本项目一律走
  `TextureDraw.blit/drawFitted`（内部用 11 参数重载，显式传 `textureWidth/Height`）。
- **`NativeImage` 的像素字节序**：AWT 的 `getRGB` 给 `0xAARRGGBB`，而 NativeImage 的 RGBA
  内存对应 `0xAABBGGRR`，**R 与 B 必须交换**。漏了这一步的症状是「JPEG 的天空发橙、PNG 正常」，
  极难联想到编码顺序。实现只有一处：`FileTexture.fromArgb`。
- **GIF 的 `delayTime` 单位是 1/100 秒**，且 `< 2cs` 的帧按 100ms 处理（浏览器语义）。
- **GIF 内存**：`宽×高×4×帧数`，不是文件体积。1920×1080×60 帧 = 475MB →
  `GifBudget` 会缩到 1/3。改预算常量前先读 docs/02 §3。
- **GIF 帧不一定是整幅**：优化过的 GIF 只写「变化的小块」，必须做画布合成 + disposal
  （`GifComposer`）。只读第一帧的实现在静态图上完全正常，只有动图才暴露。
- **`ScreenEvent.Opening` 的 `setNewScreen` 不会递归**：我们换成的是 `MenuScreen`（不是 TitleScreen）。
- **服务端行为**：Forge 1.20.1 的 `mods.toml` **没有** `clientSideOnly`（1.20.2+ 才有），
  这个 jar 在专用服务端上也会被加载；入口用 `FMLEnvironment.dist` 判掉，服务端不读配置、不加载客户端类。
  千万不要为了「服务端不加载」去写 `clientSideOnly`——它会被忽略，只会让人误判。
- **Realms 不在白名单里**：`RealmsMainScreen` 不在客户端 jar 中（独立库），反射去开它会把问题推到运行时。
- **语言键**：`text` 以 `ccnr_menu.` 开头才会被当成语言键翻译；`LangFileTest` 会扫描
  代码与内置配置里出现的键名并要求两个语言包都有。
- **`MenuAction.SCREENS` 与 `MenuActions.screenFor` 必须同步**：两处都不编译失败，
  `MenuScreenMappingTest` 是唯一的守卫。
- **原版界面的背景不靠事件、靠那件 Renderable**：`ScreenEvent.BackgroundRendered` 理论上
  在原版画完之后才发、盖上去即可，但实测对所有二级界面都不生效；现在的注入点见下面那条
  「原版界面的背景注入点」。不要因为「事件更正统」就把 Renderable 那条删掉。
- **世界内的界面永不接管背景**：`BackgroundScope` 里那条 `inWorld` 判据不能删——
  否则开背包时动画背景会盖住世界（`BackgroundScopeTest` 专门守着这一条）。
- **容器子元素的 `x`/`y` 不生效**：位置由 `MenuGeometry.stack` 决定，只有 `offsetX`/`offsetY` 还有效。
  改容器逻辑前先跑 `MenuGeometryTest` 的竖列用例（左/中/右对齐、自动列宽、间距不计最后一个）。
- **动画图片元素的宽高比按单帧算**：整张精灵图 3360×990（≈3.4:1）而单帧 420×99（≈4.24:1），
  用整图比例会把图标压扁。实现见 `MenuScreen.sourceSize`。
- **内置素材是生成的，不要手改**：`assets/ccnr_menu/presets/` 由 `scripts/make-icon-presets.py`
  从 CCNR 图标的 SVG 渲染而来（需要 Chrome + python3，不需要 PIL；162 次无头 Chrome 启动，
  跑一轮约 6~10 分钟，放后台跑）。
  改了生成脚本就要重跑并提交产物，`PresetAssetsTest` 会把「配置引用」与「磁盘文件」双向对齐。
- **轮播背景（`type: "slideshow"`）的时刻表在纯类 `ui/SlideTimeline`，运镜几何在
  `MenuGeometry.kenBurnsF`**。三条不能破的约束：
  ① **周期边界上进度必须接得上**（`incomingProgress` 在淡入末尾 == 下一张成为主角时的
  `progress`），否则换图那一帧画面会猛地缩一下——`SlideTimelineTest` 专门守着这一条；
  ② **只保留「当前 + 下一张」两张贴图**，切换完成后立刻释放其余（11 张 1080p 贴图 = 90MB 显存）；
  ③ **下一张要提前一个周期异步解码**，否则解码那几十毫秒正好落在切图那一帧上，肉眼可见。
  线程边界与 GIF 一致：工作线程只调 `FileTexture.decodeFile`（纯计算），
  `FileTexture.fromImage` 必须回渲染线程。
- **`FileTexture` 的所有权约定**：`fromImage` **成功时**接管 `NativeImage`，
  **失败时**仍归调用方（调用方负责 `close()`）。写成这样是为了让失败路径只有一处收尾，
  不会出现「两处都关一次」或「两处都没关」。改这里前先看 `load` 的实现。
- **`mark` 画在压暗层之上、原版控件之下**（`ScreenBackgrounds.render` 的顺序是
  背景 → 压暗层 → 标志）：画在压暗层下面会被压成灰的，跟没加载出来一样；
  画在原版控件上面就会盖住设置页面的选项列表。
- **`theme.buttonStyle: "text"` 下按钮不写 `width` 时宽度贴着文字**（`MenuScreen.buttonWidth`）：
  纯文字按钮的热区看不见，留 200 宽的隐形矩形会让鼠标停在文字右边也变色。
  另外 `text` 外观的**焦点提示是文字下划线**——「焦点与悬停必须用两个视觉通道」这条纪律
  不能因为换了外观就丢掉。
- **`MenuDefaults.LEGACY_DEFAULTS` 是「版本 → 默认配置原文」的映射**，不是一份随便写的清单：
  每个键都要能在 CHANGELOG 里查到那个版本（`MenuDefaultsTest` 会核对）。
  新增条目时把**当时那份文件原样**贴进来，不要顺手美化——它是给迁移代码比对用的指纹。
  历史默认里出现「现在已删除的类型/字段」是正常的（0.3.0 那份就用了 0.4.0 移除的 `text` 背景），
  所以门禁只允许这一类警告，其余一律判红。往文本块里贴 JSON 时**缩进要对齐到 16 空格**，
  否则 javac 会报 `trailing white space will be removed`。
- **`MenuConfigIO.PRESET_FILES` 是发布的素材清单**：新素材必须同时出现在清单与资源目录里，
  否则玩家拿不到（或首次启动就报缺失）。它可以含子目录（`slides/01.jpg`），
  清单与磁盘的比对是**递归**的（`filesOnDisk` 用相对路径）。
- **内置素材按「内容」比较后再释放**（`copyPreset`）：名字不变、内容换了（轮播换图、
  图标重渲染）时必须更新，否则老玩家永远停在旧素材上。0.4.1 踩过：作者要求删掉的轮播图
  还在转，新补进来的那张根本没出现——jar 是对的，玩家配置目录里的旧字节是错的。
  **不要改回「已存在就跳过」**。覆盖前留 `.bak`。清单里的名字由模组管理；
  作者自己的素材换个文件名即可（配置里的路径是任意相对路径）。
- **图标素材是裁过透明边距的**：画板 3:1 而图形约 4.25:1，裁掉的是**量出来的**包围盒
  （静态图按自身，精灵图按**所有帧的并集**——逐帧各裁各的会让动画变成「一边缩放一边平移」）。
  所以 `logo_wide_*.png` 是 1259×296、精灵图每帧 420×99（整图 3360×990），
  不是脚本里写的 1536×512 / 512×171（那是**渲染尺寸**）。配置里的 `width` 指的是裁剪后的宽度。
- **`column.bar` 是列属性，不是一种元素**：它的 x 与宽度必须由这一列决定——
  按钮宽度随文案变（`text` 外观贴着文字），写成固定像素或整列宽都会和按钮错开。
  默认 `width: "buttons"` 取**按钮们实际占据的范围**，`"column"` 才是整列宽；
  `padding`（默认 10）是左右留白——**别设 0**：按钮宽度就是文字宽度，贴着文字边界像被裁掉一块。
  色带画在**所有子元素之前**（满屏高），所以图标与按钮都在它之上。
- **轮播素材是压过的，不是原图**：源图由生成脚本用 `sips` 压成 1920 宽的 JPEG
  （11 张 → 2.7MB）。源图清单**写死在 `SLIDE_SOURCES`**，编号即轮播顺序——
  不要改成「扫目录里所有匹配前缀的文件」：实测会把 4 张无关的图一起卷进来，编号还会随排序漂。
  只换照片时跑 `--only-slides`，不要重跑整条脚本（162 次无头 Chrome 截图，6~10 分钟）。
- **慢速运动必须同时满足两件事，缺一个就是「画面在动但一直在抖」**：
  ① **几何不能取整**——运镜走 `MenuGeometry.kenBurnsF`（浮点），把小数部分交给
  `TextureDraw.drawAtF` 里的 `PoseStack`；默认参数下单帧位移只有 0.03 像素，取整就等于
  「每十几帧跳 1 GUI 单位」（GUI scale 2 时一跳 2 物理像素，肉眼极明显）。
  `MenuGeometryTest.kenBurnsIsSubPixel` 守着这条。
  ② **贴图必须是线性过滤**——`DynamicTexture` 默认最近邻，会把亚像素位移重新吸附回整像素；
  轮播贴图建好后要调 `FileTexture.smooth()`（不开 mipmap：`DynamicTexture` 只分配了第 0 级）。
  改这两处时别只看「位置算对了」，算对了也一样抖。
- **原版界面的背景注入点是 `ScreenEvent.Init.Post` + `screen.renderables.add(0, ...)`**
  （`ScreenBackgroundRenderable`），不是 `ScreenEvent.BackgroundRendered`：后者实测对所有二级界面
  都不生效（日志无错、事件也确实会被原版 `renderDirtBackground()` 发出）。位置必须是下标 0——
  原版顺序是「`renderBackground()` 画泥土 → 原版控件遍历 renderables」，插到末尾会盖住所有控件。
  `init()` 会清空 `renderables`，所以每次重建界面都要重新挂。`ScreenBackgroundHook` 对每种界面
  打一行 INFO（`已接管界面背景` / `未接管界面背景（原因）`）——这是「泥土没被换掉」唯一能自查的
  线索，别把那一行删了。
- **背景指纹（`ScreenBackgrounds.signatureOf`）必须覆盖所有影响画面的输入**：背景参数
  （含 `slides` 与 `SlideSpec`）、**每一个**素材文件的 mtime（走 `assetFiles()`，轮播要逐张带上）、
  `config.theme()`（压暗层跟着它走）、以及 `config.mark()`（标志的贴图归同一个实例持有）。
  漏字段的症状是「改了配置却不变」；`SlideSpec` / `MarkSpec` 都是纯数据（int/枚举/字符串），
  直接 `append` 它们安全，但**自己写的数组字段不要用 `toString()`**——
  数组的 `toString` 是身份哈希，同一个配置重建两次会得到不同的指纹。
- **不要改动 CCNR-RP / CCNR-PM / CCNR-Com**：所有变更限本仓库。
