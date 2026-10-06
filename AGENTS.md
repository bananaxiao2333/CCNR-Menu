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
  `ScreenBackgroundHook`（`ScreenEvent.BackgroundRendered`，原版泥土界面）。
  两者都调 `ScreenBackgrounds.render(...)`，范围判定都走纯类 `BackgroundScope`。
  背景实例按「参数 + 素材修改时间」的指纹共享，**进入世界**时释放。
- 包布局与数据流见 [docs/00](docs/00-总览.md) §3/§4；背景方案取舍见 [docs/02](docs/02-背景与动画.md)；
  配置字段全表见 [docs/03](docs/03-菜单配置.md)。
- **无任何外部模组依赖**（不引用 CCNR-RP / CCNR-PM / CCNR-Com，也不依赖 MCEF）。
  动画 GIF 用 JDK 自带的 `ImageIO`；精灵图走原版贴图管线。
- 纯类（可直接 JUnit 测）：`ui` 包全部、`gif` 包全部、`config` 的
  `MenuConfig` / `BackgroundSpec` / `MenuElement` / `MenuAction` / `MenuThemeSpec`。

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
- **`ScreenEvent.BackgroundRendered` 在原版画完之后才发**：拿到事件时屏幕上是泥土图，
  我们**盖上去**即可（不需要 mixin 去拦原版方法）。它的 `getGuiGraphics()` 才是画布。
- **世界内的界面永不接管背景**：`BackgroundScope` 里那条 `inWorld` 判据不能删——
  否则开背包时动画背景会盖住世界（`BackgroundScopeTest` 专门守着这一条）。
- **容器子元素的 `x`/`y` 不生效**：位置由 `MenuGeometry.stack` 决定，只有 `offsetX`/`offsetY` 还有效。
  改容器逻辑前先跑 `MenuGeometryTest` 的竖列用例（左/中/右对齐、自动列宽、间距不计最后一个）。
- **动画图片元素的宽高比按单帧算**：整张精灵图 4096×684（6:1）而单帧 512×171（3:1），
  用整图比例会**把图标压扁一半**。实现见 `MenuScreen.sourceSize`。
- **内置素材是生成的，不要手改**：`assets/ccnr_menu/presets/` 由 `scripts/make-icon-presets.py`
  从 CCNR 图标的 SVG 渲染而来（需要 Chrome + python3，不需要 PIL）。
  改了生成脚本就要重跑并提交产物，`PresetAssetsTest` 会把「配置引用」与「磁盘文件」双向对齐。
- **`MenuConfigIO.PRESET_FILES` 是发布的素材清单**：新素材必须同时出现在清单与资源目录里，
  否则玩家拿不到（或首次启动就报缺失）。
- **不要改动 CCNR-RP / CCNR-PM / CCNR-Com**：所有变更限本仓库。
