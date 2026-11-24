# 技术设计

## 阶段 1：构建与入口

依赖版本集中在根 [gradle.properties](../../gradle.properties)，Java 与 Kotlin 目标由根 [build.gradle](../../build.gradle) 的 `javaTarget` 统一配置。三个模块均启用 Kotlin JVM 插件，Kotlin toolchain 和 JVM target 与 Java 目标一致。

Mod ID 由根 `gradle.properties` 的 `mod_id` 统一提供：Gradle 构建脚本从该属性派生产物名、资源命名空间与 native 库名，Kotlin 侧引用 [InGameConsole.kt](../../common/src/main/kotlin/com/nts/ingamecnl/InGameConsole.kt) 的 `MOD_ID`、`MOD_NAME`。资源目录在源码中写作 `assets/mod_id/`，由各模块 `processResources` 的 `eachFile` 改写为 `assets/<mod_id>/`；`common` 的 `mixins.json` 也在这一步改名为 `<mod_id>.mixins.json`；语言文件的键前缀与平台 metadata 使用 `${mod_id}` 占位符，由 `expand` 展开。

Kotlin 编译输出属于各模块的主源码集，随现有 `jar`、`sourcesJar` 和 Architectury 平台转换任务输出。平台 `shadowBundle` 只包含 `common` 转换产物，不展开传递运行依赖。

| 平台 | Kotlin 运行时 | 加载入口 |
| --- | --- | --- |
| Fabric | 独立安装 Fabric Language Kotlin，构建依赖与 metadata 版本来自同一属性 | 普通 Kotlin `class` 实现 `ClientModInitializer`，使用默认 Java 适配器 |
| NeoForge | 安装 Kotlin for Forge 6.5.0，由其提供 Kotlin 2.4.21 运行时 | 普通 Kotlin `class` 使用 `@Mod`，限定 `Dist.CLIENT`，`modLoader` 为 `kotlinforforge` |

NeoForge 发布 JAR 不嵌入 Kotlin 标准库，也不生成 Jar-in-Jar 标准库条目；玩家需与 NeoForge 一起安装 Kotlin for Forge。现阶段不使用 Kotlin 反射或协程运行时。

Fabric 的 `environment` 为 `*`，客户端与服务端入口分别初始化；NeoForge 入口可见于双端，按 `FMLEnvironment.getDist()` 分支注册客户端界面与服务端命令模式。平台依赖声明为双端，客户端专用类不在服务端初始化路径中加载。

| 文件 | 职责 |
| --- | --- |
| [InGameConsole.kt](../../common/src/main/kotlin/com/nts/ingamecnl/InGameConsole.kt) | Mod 标识常量 |
| [ConsoleClient.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/ConsoleClient.kt) | 共享初始化入口 `ConsoleClient.initialize()`、全局按键、模式状态、仅查看与自动展开叠加层生命周期 |
| [ConsoleScreen.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleScreen.kt) | 控制台 `Screen` 生命周期、输入、模式切换、命令执行与收起动画 |
| [ConsolePanelRenderer.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsolePanelRenderer.kt) | 普通面板、仅查看叠加层与自动展开叠加层的背景、内容、状态行和输入区绘制 |
| [Timeline.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/Timeline.kt) | 时间线条目类型、数据源接口与公共配色 |
| [TimelineView.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/TimelineView.kt) | 所有模式共用的时间线排版、换行、着色、滚动与跟随提示 |
| [LogTimelineSource.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/LogTimelineSource.kt) | 客户端日志历史的时间线数据源 |
| [LogLayout.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/LogLayout.kt) | 日志行排版、像素宽度换行与行高计算 |
| [ConsoleMode.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleMode.kt) | 控制台模式、前缀与切换手势 |
| [ConsoleState.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleState.kt) | 跨界面保留的模式状态、日志/命令草稿与输入历史 |
| [InputHistory.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/InputHistory.kt) | 每个模式独立的会话内输入历史，跳过空白与连续重复项 |
| [PanelTransition.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/PanelTransition.kt) | 面板展开/收起目标与按时间计算的展开比例 |
| [LogCapture.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogCapture.kt) | Log4j Appender、客户端 tick 接收与记录文本限制 |
| [LogInbox.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogInbox.kt) | 有界接收队列、序号分配与入队时的筛选快照 |
| [LogHistory.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogHistory.kt) | 有界原始历史、实时视图、淘汰计数与查询快照 |
| [LogPage.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogPage.kt) | 1024 条分页、封存快照与序号范围 |
| [LogRecord.kt](../../common/src/main/kotlin/com/nts/ingamecnl/log/LogRecord.kt) | 不可变日志记录 |
| [DisplayFilter.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/DisplayFilter.kt) | 级别与关键词筛选规则 |
| [FilterCommand.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/FilterCommand.kt) | `filter` 命令解析与规则应用 |
| [AutoSplashCommand.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/AutoSplashCommand.kt) | `autosplash filter` 命令解析 |
| [GrepCommand.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/grep/GrepCommand.kt) | `grep` 参数、引号与时间解析 |
| [GrepSearch.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/grep/GrepSearch.kt) | 后台分页扫描、命中位图、取消与结果索引 |
| [GrepView.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/grep/GrepView.kt) | 结果阅读区绘制、滚动与按键 |
| [GrepNative.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/grep/GrepNative.kt) | 按平台加载 FFM 库、工作区与批量 ASCII 扫描 |
| [LogLevel.kt](../../common/src/main/kotlin/com/nts/ingamecnl/log/LogLevel.kt) | 界面级别与 Log4j 级别映射 |
| [LogLimits.kt](../../common/src/main/kotlin/com/nts/ingamecnl/log/LogLimits.kt) | 日志条数与文本量默认值、上下限 |
| [ConsoleConfig.kt](../../common/src/main/kotlin/com/nts/ingamecnl/config/ConsoleConfig.kt) | Cloth Config 配置模型、默认值与访问器，供客户端与服务端命令授权共用 |
| [ConfigScreenFactory.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/config/ConfigScreenFactory.kt) | 客户端配置页面创建 |
| [ConfigFilterSync.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/config/ConfigFilterSync.kt) | 配置加载/保存与日志筛选、容量同步 |
| [ServerCommandResult.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerCommandResult.kt) | 服务端命令状态、结果值与反馈 |
| [ServerCommandExecutor.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerCommandExecutor.kt) | 服务端控制台源、命令解析执行与反馈捕获 |
| [CommandSession.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/CommandSession.kt) | 命令会话、待返回表与超时 |
| [LocalCommandSession.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/LocalCommandSession.kt) | 主机本地命令会话 |
| [CommandAccess.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/CommandAccess.kt) | 主机与局域网客户端的命令模式授权 |
| [ServerCommandHandler.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerCommandHandler.kt) | 服务端请求校验与执行 |
| [CommandNetwork.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/CommandNetwork.kt) | 命令能力与请求/响应网络包 |
| [RemoteCommandSession.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/command/RemoteCommandSession.kt) | 非主机客户端命令会话 |
| [RemoteCommandCapability.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/command/RemoteCommandCapability.kt) | 远程命令能力探测、令牌与会话状态 |
| [CommandTranscript.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/CommandTranscript.kt) | 有界命令时间线数据源与结果文案 |
| [ServerConsoleData.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ServerConsoleData.kt) | 混合模式服务端日志原始缓冲、过滤、命令条目与查询快照 |
| [ServerCommandConfig.kt](../../common/src/main/kotlin/com/nts/ingamecnl/config/ServerCommandConfig.kt) | 专用服务器命令开关、名单、限流、会话与日志配置 |
| [DedicatedCommandAccess.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/DedicatedCommandAccess.kt) | 专用服务器名单与 UUID/IP 校验 |
| [ServerCommandSessions.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerCommandSessions.kt) | 专用服务器短期能力令牌 |
| [ServerRequestGuard.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerRequestGuard.kt) | 命令请求频率限制与请求 ID 去重 |
| [CommandProtocol.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/CommandProtocol.kt) | v2 能力与会话日志批次数据结构 |
| [ServerLogStreamer.kt](../../common/src/main/kotlin/com/nts/ingamecnl/command/ServerLogStreamer.kt) | 服务端日志订阅、续传与每 tick 推送 |
| [ServerLogBuffer.kt](../../common/src/main/kotlin/com/nts/ingamecnl/server/log/ServerLogBuffer.kt) | 服务端日志环形缓冲、序号与快照 |
| [ServerLogCapture.kt](../../common/src/main/kotlin/com/nts/ingamecnl/server/log/ServerLogCapture.kt) | 服务端独立 Log4j 采集 Appender |
| [ServerCommandMode.kt](../../common/src/main/kotlin/com/nts/ingamecnl/server/ServerCommandMode.kt) | 服务端初始化、管理命令、tick 推送与清理 |
| [InGameConsoleFabricServer.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/InGameConsoleFabricServer.kt) | Fabric 专用服务器入口 |
| [NeoForgeClientSetup.kt](../../neoforge/src/main/kotlin/com/nts/ingamecnl/neoforge/client/NeoForgeClientSetup.kt) | NeoForge 客户端初始化 |
| [KeyboardHandlerMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/KeyboardHandlerMixin.kt) | 全局键盘与字符输入接入 |
| [MouseHandlerMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/MouseHandlerMixin.kt) | 全局鼠标热键接入 |
| [MinecraftScreenMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/MinecraftScreenMixin.kt) | 覆盖前界面的挂起、恢复与生命周期接入 |
| [InGameConsoleFabric.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/InGameConsoleFabric.kt) | Fabric 客户端生命周期接入 |
| [ModMenuIntegration.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/ModMenuIntegration.kt) | Fabric Mod Menu 配置入口，未安装 Mod Menu 时不加载 |
| [InGameConsoleNeoForge.kt](../../neoforge/src/main/kotlin/com/nts/ingamecnl/neoforge/InGameConsoleNeoForge.kt) | NeoForge 客户端生命周期接入 |

平台模块分别依赖 `common`，`common` 不依赖平台模块。共享实现使用 Architectury 的 `KeyMappingRegistry` 注册按键。客户端 Mixin 由 [mixins.json](../../common/src/main/resources/mixins.json) 声明，使用 Kotlin 编写并优先采用 MixinExtras 注入；输入与界面规则统一由 Kotlin 入口负责。

包边界按“是否只在客户端运行”划分：`com.nts.ingamecnl.config`、`com.nts.ingamecnl.command`、`com.nts.ingamecnl.log` 与 `com.nts.ingamecnl.server` 中的配置、命令执行、授权、协议与服务端日志不持有客户端界面；`client.config`、`client.command`、`client.gui` 只放配置页面、客户端命令会话、混合模式数据与界面。内置服务端与客户端同进程运行时，服务端侧可直接读取根配置中的局域网授权设置；专用服务器使用独立服务端配置。

Cloth Config 版本由根 `gradle.properties` 的 `cloth_config_version` 统一管理。`common` 以 `compileOnly` 使用公共 API artifact，Fabric 与 NeoForge 分别引入平台 artifact；平台 JAR 提供运行时的配置目录实现，因此不把公共 artifact 作为运行依赖。Mod Menu 是 Fabric 的可选运行依赖，构建时引入以编译入口类，发布 metadata 只声明 `suggests`。

## 阶段 1：状态与界面

`ConsoleClient` 是平台入口与 Mixin 的唯一共享入口，全局按键、模式状态、仅查看叠加层与自动展开叠加层生命周期由它持有；`gui` 包负责界面状态、排版、滚动与绘制，模式与草稿类型对 `client` 包可见。共享状态随客户端初始化建立，由客户端线程读写；每次打开日志/命令面板创建一个 `Screen`，模式、草稿与输入历史独立于界面实例保留。关闭面板不重置模式；同一客户端会话内重新打开时恢复上次模式与未提交输入，客户端重启后回到仅查看。

开关、向上/向下翻页和回到底部四个按键通过 `KeyMappingRegistry` 注册到 `ingamecnl:console` 分类，可在原版按键设置中改绑：开关默认 grave accent，向上/向下翻页默认 PageUp/PageDown，回到底部默认 End。Shift+Tab 固定用于切换模式，Esc 固定用于关闭控制台，两者不注册为独立按键条目。键盘与鼠标 Mixin 在原始输入处理入口消费匹配的按键，主菜单、世界选择、聊天、物品栏和游戏中均可使用；按键设置正在捕获新绑定时优先接收事件。

开关由 GLFW 的按下事件触发，保存正在按住的键或鼠标按钮，释放时清除。按住期间消费对应重复事件、释放事件与键盘字符事件，每次按下只开关一次，开关键不进入输入框。

日志与命令模式打开时保存原 `Screen`，通过 `Minecraft.setScreen` 安装控制台。生命周期桥接在覆盖期间挂起原界面的 `removed`，恢复时跳过重复 `added` 和初始化；尺寸改变时调用 `resize`。原界面继续更新和绘制，键鼠输入由控制台接收。游戏切换到其他界面时正常移除挂起界面，关闭面板时恢复原界面、草稿和焦点。

仅查看模式不安装 `Screen`，通过 `ClientGuiEvent.RENDER_HUD` 在无界面时、`ClientGuiEvent.RENDER_POST` 在其他界面绘制后绘制叠加层。客户端 tick 检查世界变化并在窗口尺寸变化时重排叠加层。叠加层默认占屏幕上方约 50%，不创建输入框、关闭按钮和焦点；`Minecraft` 仍按无界面状态处理游戏输入，世界中鼠标保持抓取，存档外鼠标事件直接到达下层界面。Shift+Tab 与 Esc 由全局键盘接入消费，翻页与回底只调用叠加层的滚动逻辑。

自动展开叠加层同样不安装 `Screen`，由 `RENDER_HUD`/`RENDER_POST` 在仅查看叠加层之前绘制。它默认占 25% 高度，不绘制标题、状态行、输入框、关闭按钮和底部提示；默认只接收 Esc 提前收起，其他键鼠事件直接放行。Esc 收起可关闭，关闭后 Esc 完全交给游戏；手动 Esc 收起会记录静默截止时间，静默期内不再触发。手动控制台、仅查看面板或配置界面打开时暂停自动展开。

`ConsoleScreen` 使用目标版本的 `extractRenderState(GuiGraphicsExtractor, …)` 绘制，输入事件使用 `KeyEvent`、`MouseButtonEvent`。面板高度取配置的百分比，仅查看、日志与命令模式共用；自动展开叠加层使用自身的 10%～100% 高度设置；面板不绘制全屏模糊背景，`isPauseScreen()` 返回 `false`。

Shift+Tab 在界面层和全局键盘接入中消费：仅查看模式进入日志模式，日志模式进入命令模式，命令模式返回仅查看模式；三种模式始终在循环中。从按下到释放只切换一次。模式切换时替换输入框内容并将焦点交还输入框，日志与命令的草稿分别保存；仅查看模式没有草稿。日志与命令各自维护最多 200 条会话内输入历史；输入行聚焦时 ↑/↓ 在历史与当前草稿之间移动，越过最新一条恢复进入历史前的草稿。窗口尺寸改变时仅重排既有控件，保留文本、选区与焦点。所有文案使用语言资源，固定前缀与行尾切换提示在输入框外绘制；输入区域预留提示宽度，窄窗采用简短提示。

面板背景 alpha 取配置的不透明度，三种模式与自动展开共用；文本维持正常透明度。输入框不绘制默认底色，关闭按钮使用无背景的文字按钮。

`PanelTransition` 保存起始展开比例、展开或收起目标及单调时钟起始值，按完整行程耗时线性计算当前位置，耗时取配置的动画时长并用于三种模式与自动展开。仅查看叠加层首次创建后先重置展开起点再播放滑入；从仅查看切换到日志/命令模式时直接以展开状态显示，不重复播放动画。提前关闭和收起途中反向展开均从当前位置匀速移动，不跳回动画端点。每帧同步更新控件位置与可见区域裁剪，鼠标命中使用相同位置。

面板不随客户端世界对象变化自动关闭，进入世界后保持展开。日志与命令模式下开关热键的字符输入优先于收起；仅查看模式下开关热键切换叠加层。Esc、关闭按钮和仅查看模式的开关热键启动收起；收起期间停用输入编辑和关闭按钮，清除 tooltip，完成后通过 `Minecraft.setScreen(previousScreen)` 恢复原界面；原界面为空时返回游戏。

命令模式在主机本地与内置服务端就绪时可用；非主机客户端或专用服务器客户端只有收到 v2 能力响应并获授权后可用，否则状态行显示等待授权或不可用。专用服务器授权后命令模式显示为黄色的混合模式。命令模式始终保留在模式循环中。日志模式的 Enter 提交 `filter`、`autosplash filter` 或 `grep` 命令，命令模式的 Enter 交给命令会话；混合模式以 `:` 开头的输入改在本地解析 `:filter`/`:grep`。

## 记录与状态所有权

核心类型按下表拆分；纯规则不持有 Minecraft、Log4j 或线程执行器。

| 类型 | 内容 | 所有权 |
| --- | --- | --- |
| `LogRecord` | 接收序号、事件时间、级别、线程名、logger、消息、异常文本与截断状态 | 不可变值，可跨线程传递 |
| `DisplayFilter` | 可选精确级别与关键词；匹配函数 | 不可变值 |
| `HistoryQuery` | 关键词、级别、可选起止时间 | 不可变值 |
| `ConsoleMode` | 仅查看、日志或命令模式、前缀与对应输入状态 | 客户端线程 |
| `PanelState` | 收起、预览、展开及阅读位置 | 客户端线程 |
| `SearchResult` | 查询条件、查询时的历史范围、有序匹配记录 | 单次查询与结果阅读区 |
| `ServerSession` | 当前内置服务端实例及会话标识、待返回任务 | 当前世界会话 |

消息、异常堆栈、命令回显和命令反馈保留各自的记录类型。原始日志历史用于 `grep`，命令记录进入面板时间线，不回灌 Log4j。

## 阶段 2：日志接收与实时视图

`LogCapture` 在客户端初始化时向根 LoggerConfig 添加名为 `ingamecnl-console` 的 `AbstractAppender`，并忽略 appender 异常。回调只把事件转换为不可变 `LogRecord` 后入队，不保留 Log4j 事件，也不在日志线程上排版或绘制。消息截断到 8000 字符，异常文本截断到 16000 字符，并记录截断状态。

`LogInbox` 持有有界队列与明确命名的锁；入队、序号分配和筛选规则更新在同一同步边界内完成。队列上限为 2048 条或 2 MiB 文本，达到上限时拒绝新记录并累计丢弃数，不阻塞日志线程。每条入队记录携带当时的 `DisplayFilter` 快照。客户端每个 tick 最多取出 256 条并最多耗时 2 ms，剩余记录留到后续 tick。

`LogHistory` 保留原始记录与实时视图两个有界队列。条数上限为 `Long` 类型，默认 `Int.MAX_VALUE` 条、最大可设 `Long.MAX_VALUE`；文本量上限默认 128 MiB、最大 2048 MiB，按消息与异常文本的 UTF-8 字节数累计，两者均可在配置的“日志容量”分类调整。超过上限时从头部淘汰最旧记录，同时移除实时视图中对应条目，并累计淘汰数。条数上限提高到 `Int.MAX_VALUE` 后，实际保留量通常先受文本上限约束。实时视图包含筛选通过的记录，保留入队时快照判断的结果；视图列表按版本号惰性生成快照，仅在记录或可见性变化时重建。`accept` 返回记录是否进入实时视图，`LogCapture` 只对返回 true 的记录调用自动展开回调。丢弃数与淘汰数在状态行分别显示。

规则更新以入队顺序为边界，不使用日志时间戳排序。已经入队但尚未绘制的记录仍使用原规则，之后入队的记录使用新规则；已经显示的记录不重新筛选，隐藏记录仍保留在原始历史中。

实时视图按记录序号保存顶部锚点。未滚动时从最新记录向上取可见内容并自动跟随；上翻后锚点固定，新记录到达或头部淘汰不改变顶部位置；锚点被淘汰时从当前最旧记录继续显示；回到底部时清除锚点。新增计数使用入队时记录的可见总数与锚点快照之差。

日志行以接收序号开头，后接 `[时间] [线程/级别] (logger) 消息`。默认沿用 Loom 控制台配色：时间蓝色，线程和级别按级别着色，logger 青色，ERROR、FATAL 的消息红色；各级颜色可在配置中修改。括号作为普通字符保留。消息按像素宽度换行，续行不重复前缀且从内容区左边开始，单条记录最多排版 8 行，超出部分以省略号结尾。布局只计算可见记录，行高随换行行数变化，滚动位置以记录锚点和可见高度共同确定。三种模式与仅查看叠加层复用同一布局与滚动锚点，高度使用配置值，默认 50%。

自动展开以实时视图记录为输入：记录先通过入队时的显示筛选，再检查静默期、级别阈值和可选关键词；关键词在消息与异常文本中忽略大小写匹配。命中后显示只读叠加层并重置停留计时，新的命中继续推迟收起。总开关、触发级别、关键词、高度、停留时间、Esc 收起和静默期来自配置；`autosplash filter` 写入关键词并保存。手动 Esc 收起后按配置进入静默期；手动面板或配置页面优先，自动展开在其打开时收起。

## 阶段 2：内置服务端与专用服务器命令模式

客户端加入世界时，如果 `Minecraft.singleplayerServer` 已启动且就绪，创建 `LocalCommandSession`；否则请求远端能力并创建 `RemoteCommandSession`。退出世界、断开连接或服务端停止时关闭会话、清空待返回表、混合模式日志与命令时间线。远端能力由主机或专用服务器的 v2 通道返回；专用服务器使用独立配置 `ingamecnl-server.json` 与服务端入口。

命令统一由 `ServerCommandExecutor` 执行：以 `server.createCommandSourceStack().withSource(CapturingCommandSource)` 为基础，保留服务端控制台的所有者权限、世界位置与维度，不绑定玩家实体，因此 `@s` 不是玩家，相对坐标以服务端命令源为准。随后调用命令调度器的 `parse` 与 `execute`；语法错误与执行异常转换为失败反馈，成功时保留返回值。`CapturingCommandSource` 只收集反馈文本，最多 64 条或 16000 字符，并拒绝向管理员广播，避免命令输出再次写入 Log4j。

本地会话通过 `server.executeIfPossible` 切到服务端线程，再通过客户端执行器投递结果；命令请求带自增请求号与 10 秒超时，超时标记为结果未知且不重试，旧世界或已关闭会话的结果直接丢弃。

非主机客户端通过 `ingamecnl:command_capability_request`、`command_capability`、`command_request`、`command_response` 四个 v1 通道与主机通信；专用服务器与新版客户端使用 `command_capability_v2_request`、`command_capability_v2`、`command_request_v2`、`command_response_v2`，以及 `server_log_subscribe_v2`、`server_log_unsubscribe_v2`、`server_log_batch_v2` 日志通道。v1 能力响应只返回是否允许；v2 额外返回短期令牌、TTL、拒绝原因、协议版本、是否为专用服务器混合模式与日志流开关，不泄露名单。只有专用服务器下发令牌；集成服务器（含对局域网开放的单人存档）返回 `mixed=false` 且不下发令牌，客户端继续使用 v1 通道，命令模式保持原名。服务端每次执行前重新校验真实 `ServerPlayer`、令牌、名单与请求频率，不信任客户端上报的身份。主机本人始终允许；局域网总开关关闭时拒绝所有非主机客户端；白名单要求玩家名忽略大小写、UUID 或 IP 精确匹配，任一命中即允许；黑名单命中即拒绝。白名单或黑名单为空时均拒绝所有玩家。专用服务器默认关闭命令模式，管理员用 `/ingamecnl commandmode status|on|off|reload` 管理，要求管理员权限；请求按每玩家分钟窗口、最小间隔和请求 ID 去重，命令执行写入审计日志。远程客户端必须安装本模组，普通远程服务器没有该通道。

命令输入与反馈进入 `CommandTranscript`，上限 200 条或 20000 字符，条目区分输入、输出与错误。所有模式的文本区共用 `TimelineView`，只切换 `TimelineSource`：日志模式用 `LogTimelineSource`，命令模式用 `CommandTranscript`，混合模式用 `ServerConsoleData`。`TimelineView` 用 `Font.split` 按传入内容区宽度换行并贴底显示，滚动偏移按新增行高度补偿，支持 PageUp/PageDown、Home、End 与滚轮；`LogLayout` 负责带序号、时间、级别与 logger 前缀的日志行排版及续行着色。状态行区分本地服务端、已授权局域网主机、等待授权与不可用。

专用服务器授权成功后，命令模式界面显示为黄色的混合模式，但内部仍是 `ConsoleMode.COMMAND`。服务端用独立 Log4j Appender 采集日志到环形缓冲，默认 10000 条或 16 MiB，按序号淘汰；客户端订阅时先收到最近 `logBacklogLines` 条快照，之后服务端每 tick 最多推送 256 条或 64 KiB，客户端发现序号缺口时清空并重新请求。每个订阅在服务端持有待发队列，`lastSequence` 只推进到实际发出的最后一条，避免快照被批次上限截断后误判为已送达。服务端日志连同玩家聊天、加入退出等 root logger 输出一起推送，只进入 `ServerConsoleData`，不进入客户端 `LogCapture`，不触发自动展开。混合模式输入不以 `:` 开头时提交服务端命令；以 `:` 开头时在本地执行 `:filter` 或 `:grep`，不发送到服务端。`:filter` 使用独立的 `DisplayFilter` 快照，只影响后续到达的服务端日志，原始缓冲保留全部记录；状态行与 `:filter show` 同时显示当前规则与显示/隐藏计数，离开混合模式或断开连接时清除筛选状态。`:grep` 对原始缓冲建立 `LogSnapshot`，复用日志查询的后台扫描与阅读区。服务端日志、命令输入与命令输出由同一个 `TimelineView` 排进同一时间线，命令条目最多 200 条或 20000 字符；命令结果、执行失败和本地命令错误都作为时间线条目常驻，不再写界面底部的一次性提示。混合模式上翻后新记录到达时按新增行高度补偿滚动偏移，保持当前阅读位置。

## 配置

配置模型位于 `common` 的 `config`，使用 Cloth Config 的 AutoConfig 与 Gson 序列化，文件名为 `ingamecnl`；客户端配置页面入口位于 `client/config`。字段分为筛选、日志容量、自动展开、颜色、面板和单人游戏六组：筛选保存级别与关键词；日志容量保存条数上限与文本量上限（MiB）；自动展开保存总开关、触发级别、触发关键词、高度百分比、停留毫秒数、Esc 收起开关和静默秒数；颜色保存时间、各级别、logger、普通消息和错误消息的 RGB 值（渲染时补为不透明 ARGB）；面板保存高度百分比、动画毫秒数和不透明度百分比，三项同时用于仅查看、日志和命令模式；单人游戏保存远程命令总开关、名单模式以及玩家名/IP 名单。加载时校正关键词空白、数值范围与名单空白项，默认值与既有界面一致。

`ConsoleConfig` 在 `ConsoleClient.initialize()` 中注册一次，并把筛选与日志容量同步到 `LogCapture`。配置页面的保存和加载监听器都重新应用筛选与容量；调整容量后立即淘汰超出的最旧记录。日志模式执行 `filter` 命令时反向写入配置并保存，执行 `autosplash filter` 时写入自动展开关键词并保存。颜色、面板高度、动画时长和不透明度直接读取当前配置，不复制到界面状态。

专用服务器配置独立于客户端配置，由 `ServerCommandConfig` 使用 AutoConfig 注册为 `ingamecnl-server`，文件位于服务端 `config/ingamecnl-server.json`。字段分为开关、名单、限流/令牌、审计与日志流：开关默认关闭；名单模式默认白名单，玩家名、UUID、IP 三组名单默认空；限流默认每分钟 20 次、最小间隔 200 毫秒；令牌默认 300 秒；审计与日志流默认开启；日志积压默认 10000 条。配置在服务端初始化时加载，管理命令 `reload` 重新读取；调整后不会改写客户端配置。`on`、`off` 与 `reload` 修改后清空令牌、订阅与限流状态，并向在线玩家重新下发 v2 能力，客户端据此重新握手与订阅。

配置页面由 `AutoConfigClient` 创建；Fabric 的 Mod Menu 入口和 NeoForge 的 `IConfigScreenFactory` 扩展点都把模组列表界面作为父界面。配置界面打开时暂停仅查看与自动展开叠加层绘制，关闭后按原状态恢复。Cloth Config 在 Fabric 与 NeoForge 都是必需运行依赖；Mod Menu 只影响 Fabric 的配置入口，未安装时不影响其他功能。

## 阶段 3：本地命令解析

日志模式输入解析为明确的命令值，再交给对应能力执行。解析错误返回可分类的输入错误，界面展示错误文案并保留草稿；命令成功执行后清空输入。命令解析不直接修改日志列表或服务端状态。

阶段 2 已实现 `filter`：`filter level` 校验预定义级别，`filter keyword` 读取剩余文本，`filter reset`、`filter reset level`、`filter reset keyword` 与 `filter show` 使用主计划规定的子命令。`autosplash filter <文本>` 单独解析并写入自动展开关键词。

`grep` 以参数名解析选项，接受调整后的选项顺序；重复选项、未知选项和缺失值均返回输入错误。`-k` 与 `-l` 必填，`-t1`、`-t2` 可独立省略。带空格的值使用双引号；时间解析采用严格日期校验，按本机时区转换为时间点。短时间在查询提交时补齐当天日期，开始与结束时间使用同一基准日期。

关键词在消息与异常文本中做忽略大小写的普通子串匹配，级别按精确值匹配。时间下界和上界均包含端点，筛选条件按交集组合。查询语法不执行 Shell 展开或系统进程。

## 阶段 3：历史查询与阅读

`LogHistory` 以 1024 条分页保存原始记录；`grep` 提交时封存尾页并取得 `LogSnapshot(pages, fromSequence, toSequence)`，之后仍在接收队列中的记录留给下一次查询。`GrepSearch` 用单线程守护执行器扫描快照，每页生成 64 位命中位图，按页累计命中数；`GrepResult` 通过二分页累计值定位任意命中条目。任务支持取消，结果通过客户端执行器回到界面，旧回调按代次丢弃。

关键词与字段均为 ASCII 且原生库可用时，`GrepSearch` 按 64 条一批调用 `GrepNative` 的 comb 字面量扫描；非 ASCII 字段、超出批容量或原生加载/调用失败时回退 JVM 判定，同一查询不会混用两种大小写规则。`common/build.gradle` 在构建时用 Zig 交叉编译 Windows、Linux、macOS 的 x86_64/aarch64 与 Android arm64 动态库，打入发布 JAR；Android 目标使用 NDK 的 libc/sysroot、按 API 26 构建，缺少 NDK 时跳过该目标。`GrepNative` 按 `os.name`、`os.arch` 与 Android 运行环境选择对应资源，缺失、只支持 arm64、ABI 不符或 Android JVM 不提供 FFM 时回退 JVM。

`GrepView` 只读展示结果，顶部为查询条件与快照时间范围，底部为当前位置；支持逐条/翻页/首尾/横向滚动与 q/Esc 退出。结果不插入后续日志、不触发自动展开，历史淘汰不会改变已封存页面。原生后端仍未做并行分块和定量选路；七个平台的库已完成交叉构建，Windows x86_64 完成独立 FFM 调用验证，Android arm64 已在 Android 17 / SM8850 / Fold Craft Launcher / Java 25 环境验证，Linux/macOS 与客户端内查询仍待运行确认。[grep 历史搜索架构](grep.md)记录后续步骤。

## 后续阶段：服务端会话与命令反馈

客户端为每次内置服务端建立独立会话标识。提交的命令和补全请求携带该标识及服务端实例，经服务端执行器调度；在服务端线程确认实例与会话仍有效后，使用该版本的命令分发器执行或查询补全。

命令源以 `server.createCommandSourceStack()` 的控制台上下文为基础，仅替换反馈接收端。原生命令源的权限、位置、维度和执行实体语义保留，界面标明控制台身份；不将客户端玩家直接作为命令执行者。

目标版本的权限接口使用 `PermissionSet`，不套用旧版整数权限方法。命令文本反馈通过专用 `CommandSource` 接收，执行结果回调与文本反馈分别记录，并以会话标识送回客户端。管理员广播策略在命令接入阶段显式配置，直接反馈只进入控制台一次。

待返回表由会话拥有：提交时登记，服务端结果返回后由客户端移除；世界退出时停止接受请求、取消未执行请求并结束等待，已经在服务端执行的命令不通过取消尝试回滚。会话关闭后，迟到返回不写入新世界。

提交前会话失效归为未执行；已执行后的业务失败按命令结果显示；服务端停止或连接结束导致无法确定结果时标记结果未知，不自动重发。用户提示与内部异常诊断分别生成。

## 后续阶段：预览和渲染

收起、预览、展开是明确的面板状态，搜索阅读属于展开面板内部的焦点状态。动画根据状态变化的起始时间与目标高度计算，不用多组布尔值拼接显示逻辑。

动画、预览停留和最长持续时间使用单调时钟；规则计算接收当前时间值。新日志只在实时筛选通过且满足提示条件时推进预览。阅读搜索结果或已展开操作时不自动切换面板状态。

HUD 与展开界面共享日志布局，裁剪绘制到可见区域。窗口缩放、字体或资源重载后重新计算排版；原始记录与查询结果不受排版缓存影响。

## 验证入口

两端构建与游戏运行命令见[使用与开发](../usage.md)。阶段 1 需要实际检查四个按键的重绑定、固定 Shift+Tab 切换、Esc 关闭、三种模式切换、草稿保留、窗口缩放、世界退出和输入恢复。

阶段 2 还需要在持续产生日志时检查跟随、翻页、回底与新增计数，检查修改筛选前后旧记录保持不变、后续记录按新规则显示，并分别触发队列丢弃与历史淘汰确认计数。仅查看模式需要在世界中确认游戏继续接收移动、视角和攻击/使用，在存档外确认鼠标点击穿透叠加层，并检查翻页、回底、Shift+Tab 切换与 Esc 关闭。自动展开需要确认级别与关键词同时满足才触发、停留期间新命中重新计时、Esc 可提前收起且其他键鼠被放行，手动面板与配置界面打开时不触发。配置需要在 Fabric Mod Menu 与 NeoForge 模组列表两个入口检查保存、筛选同步、颜色、自动展开和叠加层参数。两个加载器的开发客户端都需要运行检查；视觉检查由使用者完成。

发布产物检查 Kotlin class 与共享资源存在；Fabric metadata 包含匹配的运行依赖，NeoForge metadata 的 `modLoader` 为 `kotlinforforge` 且不引用嵌套标准库。后续验证随对应功能阶段执行，不将测试依赖或测试替身加入生产产物。
