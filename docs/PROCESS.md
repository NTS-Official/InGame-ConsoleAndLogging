# 开发进度

阶段 1 与阶段 2 的代码已落地，Fabric 和 NeoForge 的完整构建均已通过。Fabric 开发客户端已确认默认仅查看叠加层与日志模式界面可显示，配置页面已接入且配置文件、Fabric Mod Menu 与 NeoForge 模组列表配置入口已就绪；界面实现已拆分到 `client/gui`，NeoForge 改用 Kotlin for Forge，拆分后两端开发客户端均可启动到标题界面。游戏内交互与视觉验证待完成，下一项功能是历史查询（`grep`）。

## 已实现

- `common`、`fabric`、`neoforge` 均已配置 Kotlin。两个平台的客户端入口调用共享的 `ConsoleClient.initialize()`。NeoForge 使用 Kotlin for Forge 6.5.0 提供 Kotlin 运行时，发布 JAR 不嵌入标准库。
- [ConsoleClient.kt](../common/src/main/kotlin/com/nts/ingamecnl/client/ConsoleClient.kt) 管理全局按键、面板状态和仅查看生命周期；`gui` 包拆分控制台界面、日志排版/滚动、普通面板与叠加层绘制。[客户端 Mixin](../common/src/main/kotlin/com/nts/ingamecnl/client/mixin) 接入键鼠事件与原界面的恢复。
- 控制台默认以仅查看模式打开：叠加层占屏幕上方约 65%，不安装 `Screen`，世界中保持鼠标抓取，存档外鼠标点击穿透；翻页、回底、切换和关闭仍由键盘处理。按 Shift+Tab 进入日志或命令模式后，面板同样占约 65% 并接收键鼠输入。
- 开关、向上/向下翻页和回到底部四个按键注册到 `ingamecnl:console` 分类，可在原版按键设置中改绑；Shift+Tab 固定切换模式，Esc 固定关闭控制台。
- 面板可从主菜单、世界选择、聊天、物品栏和游戏中打开，包含顶部线性开合、10% 背景和 65% 高度，三种模式表现一致。日志与命令模式下波浪键写入输入框，仅查看模式下波浪键开关叠加层；进入世界时面板保持展开。命令模式始终在切换循环中；没有本地内置服务端时显示命令模式不可用提示。
- 接入 Cloth Config：Fabric 的 Mod Menu 配置按钮或 NeoForge 模组列表的配置按钮打开配置页面，可调整日志筛选、日志颜色和仅查看叠加层的高度/动画时长/不透明度；配置保存到 `config/ingamecnl.json`，`filter` 命令与配置筛选保持同步。
- 阶段 2 接入 Log4j Appender、有界接收队列和原始历史。日志模式按接收序号显示 Loom 风格的日志行，支持自动换行、底部自动跟随、翻页、`End` 回底和新增记录计数。
- 日志模式的 `filter level`、`filter keyword`、`filter reset` 与 `filter show` 已接入。筛选规则以入队顺序生效，已显示的记录保持原样，隐藏记录仍保留在原始历史中。
- 命令模式的 Enter 仍只显示提示并保留草稿；服务端命令执行待接入。

## 阶段 2 验证

`common` 的 Kotlin 编译、`fabric` 与 `neoforge` 的完整构建均已通过。Fabric 开发客户端可启动并显示默认仅查看叠加层、实时日志列表与筛选状态行，切换到日志模式后显示输入界面；Mod Menu 18.0.2 与 Cloth Config 26.1.154 均已加载，配置文件生成默认值。NeoForge 开发客户端通过 Kotlin for Forge 6.5.0 启动，加载 Cloth Config，配置入口已注册，未出现配置或元数据异常。配置页面由 Fabric Mod Menu 与 NeoForge 模组列表提供；颜色值已改为 RGB 并迁移旧配置。界面拆分与 Kotlin for Forge 改造后，Fabric 与 NeoForge 开发客户端均启动到标题界面；两端运行日志除 Mod Menu 在开发环境的图标提示外无异常。

待验证：

- 世界中仅查看模式的移动、视角和攻击/使用，以及存档外鼠标点击穿透。
- 四个按键改绑后的开关、翻页与回底行为，以及固定 Shift+Tab 切换、Esc 关闭。
- 日志/命令模式波浪键写入输入框、仅查看模式波浪键开关叠加层，以及进入世界时面板保持展开。
- 翻页、`End`、滚轮和新增计数在持续产生日志时的行为。
- 修改筛选后旧记录保持原样，新记录按新规则显示。
- 队列丢弃与历史淘汰计数。
- Fabric Mod Menu 与 NeoForge 模组列表配置入口、配置保存后筛选/颜色/叠加层参数的生效情况。
- GUI 缩放、窗口尺寸变化与中文文案的视觉检查。

## 下一步

按[实施顺序](plan/README.md#实施顺序)接入 `grep` 历史查询与阅读区，再接入阶段 4 的自动预览和阶段 5 的服务端终端。模块边界和开发约定见 [AGENTS.md](../AGENTS.md)。
