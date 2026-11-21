# 技术设计

## 阶段 1：构建与入口

依赖版本集中在根 [gradle.properties](../../gradle.properties)，Java 与 Kotlin 目标由根 [build.gradle](../../build.gradle) 的 `javaTarget` 统一配置。三个模块均启用 Kotlin JVM 插件，Kotlin toolchain 和 JVM target 与 Java 目标一致。

Kotlin 编译输出属于各模块的主源码集，随现有 `jar`、`sourcesJar` 和 Architectury 平台转换任务输出。平台 `shadowBundle` 只包含 `common` 转换产物，不展开传递运行依赖。

| 平台 | Kotlin 运行时 | 加载入口 |
| --- | --- | --- |
| Fabric | 独立安装 Fabric Language Kotlin，构建依赖与 metadata 版本来自同一属性 | 普通 Kotlin `class` 实现 `ClientModInitializer`，使用默认 Java 适配器 |
| NeoForge | 安装 Kotlin for Forge 6.5.0，由其提供 Kotlin 2.4.21 运行时 | 普通 Kotlin `class` 使用 `@Mod`，限定 `Dist.CLIENT`，`modLoader` 为 `kotlinforforge` |

NeoForge 发布 JAR 不嵌入 Kotlin 标准库，也不生成 Jar-in-Jar 标准库条目；玩家需与 NeoForge 一起安装 Kotlin for Forge。现阶段不使用 Kotlin 反射或协程运行时。

Fabric 的 `environment` 为 `client`，只有客户端入口。NeoForge 入口限定客户端分发环境，依赖声明对应客户端侧。共享入口由各加载器在客户端初始化时调用一次。

| 文件 | 职责 |
| --- | --- |
| [InGameConsole.kt](../../common/src/main/kotlin/com/nts/ingamecnl/InGameConsole.kt) | Mod 标识常量 |
| [ConsoleClient.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/ConsoleClient.kt) | 共享初始化入口 `ConsoleClient.initialize()`、全局按键、模式状态与 view-only 生命周期 |
| [ConsoleScreen.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleScreen.kt) | 控制台 `Screen` 生命周期、输入、模式切换、命令执行与收起动画 |
| [ConsolePanelRenderer.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsolePanelRenderer.kt) | 普通面板与仅查看叠加层的背景、标题、状态行和输入区绘制 |
| [LogView.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/LogView.kt) | 日志区滚动锚点、新增计数、跟随提示命中与可见行绘制 |
| [LogLayout.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/LogLayout.kt) | 日志行排版、像素宽度换行与行高计算 |
| [ConsoleMode.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleMode.kt) | 控制台模式、前缀与切换手势 |
| [ConsoleState.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/ConsoleState.kt) | 跨界面保留的模式状态与日志/命令草稿 |
| [PanelTransition.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/gui/PanelTransition.kt) | 面板展开/收起目标与按时间计算的展开比例 |
| [LogCapture.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogCapture.kt) | Log4j Appender、客户端 tick 接收与记录文本限制 |
| [LogInbox.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogInbox.kt) | 有界接收队列、序号分配与入队时的筛选快照 |
| [LogHistory.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogHistory.kt) | 有界原始历史、实时视图与淘汰计数 |
| [LogRecord.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogRecord.kt) | 不可变日志记录 |
| [DisplayFilter.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/DisplayFilter.kt) | 级别与关键词筛选规则 |
| [FilterCommand.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/FilterCommand.kt) | `filter` 命令解析与规则应用 |
| [LogLevel.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/log/LogLevel.kt) | 界面级别与 Log4j 级别映射 |
| [ConsoleConfig.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/config/ConsoleConfig.kt) | Cloth Config 配置模型、默认值、配置页面与筛选同步 |
| [KeyboardHandlerMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/KeyboardHandlerMixin.kt) | 全局键盘与字符输入接入 |
| [MouseHandlerMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/MouseHandlerMixin.kt) | 全局鼠标热键接入 |
| [MinecraftScreenMixin.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/mixin/MinecraftScreenMixin.kt) | 覆盖前界面的挂起、恢复与生命周期接入 |
| [InGameConsoleFabric.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/InGameConsoleFabric.kt) | Fabric 客户端生命周期接入 |
| [ModMenuIntegration.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/ModMenuIntegration.kt) | Fabric Mod Menu 配置入口，未安装 Mod Menu 时不加载 |
| [InGameConsoleNeoForge.kt](../../neoforge/src/main/kotlin/com/nts/ingamecnl/neoforge/InGameConsoleNeoForge.kt) | NeoForge 客户端生命周期接入 |

平台模块分别依赖 `common`，`common` 不依赖平台模块。共享实现使用 Architectury 的 `KeyMappingRegistry` 注册按键。客户端 Mixin 由 [ingamecnl.mixins.json](../../common/src/main/resources/ingamecnl.mixins.json) 声明，使用 Kotlin 编写并优先采用 MixinExtras 注入；输入与界面规则统一由 Kotlin 入口负责。

Cloth Config 版本由根 `gradle.properties` 的 `cloth_config_version` 统一管理。`common` 以 `compileOnly` 使用公共 API artifact，Fabric 与 NeoForge 分别引入平台 artifact；平台 JAR 提供运行时的配置目录实现，因此不把公共 artifact 作为运行依赖。Mod Menu 是 Fabric 的可选运行依赖，构建时引入以编译入口类，发布 metadata 只声明 `suggests`。

## 阶段 1：状态与界面

`ConsoleClient` 是平台入口与 Mixin 的唯一共享入口，全局按键、模式状态和仅查看叠加层生命周期由它持有；`gui` 包负责界面状态、排版、滚动与绘制，模式与草稿类型对 `client` 包可见。共享状态随客户端初始化建立，由客户端线程读写；每次打开日志/命令面板创建一个 `Screen`，草稿独立于界面实例保留。

开关、向上/向下翻页和回到底部四个按键通过 `KeyMappingRegistry` 注册到 `ingamecnl:console` 分类，可在原版按键设置中改绑：开关默认 grave accent，向上/向下翻页默认 PageUp/PageDown，回到底部默认 End。Shift+Tab 固定用于切换模式，Esc 固定用于关闭控制台，两者不注册为独立按键条目。键盘与鼠标 Mixin 在原始输入处理入口消费匹配的按键，主菜单、世界选择、聊天、物品栏和游戏中均可使用；按键设置正在捕获新绑定时优先接收事件。

开关由 GLFW 的按下事件触发，保存正在按住的键或鼠标按钮，释放时清除。按住期间消费对应重复事件、释放事件与键盘字符事件，每次按下只开关一次，开关键不进入输入框。

日志与命令模式打开时保存原 `Screen`，通过 `Minecraft.setScreen` 安装控制台。生命周期桥接在覆盖期间挂起原界面的 `removed`，恢复时跳过重复 `added` 和初始化；尺寸改变时调用 `resize`。原界面继续更新和绘制，键鼠输入由控制台接收。游戏切换到其他界面时正常移除挂起界面，关闭面板时恢复原界面、草稿和焦点。

仅查看模式不安装 `Screen`，通过 `ClientGuiEvent.RENDER_HUD` 在无界面时、`ClientGuiEvent.RENDER_POST` 在其他界面绘制后绘制叠加层。客户端 tick 检查世界变化并在窗口尺寸变化时重排叠加层。叠加层默认占屏幕上方约 65%，不创建输入框、关闭按钮和焦点；`Minecraft` 仍按无界面状态处理游戏输入，世界中鼠标保持抓取，存档外鼠标事件直接到达下层界面。Shift+Tab 与 Esc 由全局键盘接入消费，翻页与回底只调用叠加层的滚动逻辑。

`ConsoleScreen` 使用目标版本的 `extractRenderState(GuiGraphicsExtractor, …)` 绘制，输入事件使用 `KeyEvent`、`MouseButtonEvent`。仅查看叠加层使用配置的高度百分比，日志与命令模式固定为 65%；面板不绘制全屏模糊背景，`isPauseScreen()` 返回 `false`。

Shift+Tab 在界面层和全局键盘接入中消费：仅查看模式进入日志模式，日志模式进入命令模式，命令模式返回仅查看模式；三种模式始终在循环中。从按下到释放只切换一次。模式切换时替换输入框内容并将焦点交还输入框，日志与命令的草稿分别保存；仅查看模式没有草稿。窗口尺寸改变时仅重排既有控件，保留文本、选区与焦点。所有文案使用语言资源，固定前缀与行尾切换提示在输入框外绘制；输入区域预留提示宽度，窄窗采用简短提示。

日志与命令模式的背景 alpha 固定为 10% 不透明度，仅查看叠加层使用配置的不透明度；文本维持正常透明度。输入框不绘制默认底色，关闭按钮使用无背景的文字按钮。

`PanelTransition` 保存起始展开比例、展开或收起目标及单调时钟起始值，按完整行程耗时线性计算当前位置。仅查看叠加层使用配置的动画时长，日志与命令模式固定 180 毫秒。提前关闭和收起途中反向展开均从当前位置匀速移动，不跳回动画端点。每帧同步更新控件位置与可见区域裁剪，鼠标命中使用相同位置。

打开面板时记录当前客户端世界对象，存档外允许为空。世界对象改变时立即关闭面板，不恢复旧世界的界面。Esc、关闭按钮和热键先启动收起；收起期间停用输入编辑和关闭按钮，清除 tooltip，完成后通过 `Minecraft.setScreen(previousScreen)` 恢复原界面；原界面为空时返回游戏。

命令模式的可用条件为当前世界非空且仍与打开时一致、客户端连接有效、内置服务端已就绪且仍在运行。命令模式始终保留在模式循环中；条件不满足时状态行显示需要本地内置服务端的提示，Enter 只提供尚未接入的反馈。日志模式的 Enter 提交 `filter` 命令。

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

`LogHistory` 保留原始记录与实时视图两个有界队列，上限为 2000 条或 4 MiB 文本。超过上限时从头部淘汰最旧记录，同时移除实时视图中对应条目，并累计淘汰数。实时视图包含筛选通过的记录，保留入队时快照判断的结果；视图列表按版本号惰性生成快照，仅在记录或可见性变化时重建。丢弃数与淘汰数在状态行分别显示。

规则更新以入队顺序为边界，不使用日志时间戳排序。已经入队但尚未绘制的记录仍使用原规则，之后入队的记录使用新规则；已经显示的记录不重新筛选，隐藏记录仍保留在原始历史中。

实时视图按记录序号保存顶部锚点。未滚动时从最新记录向上取可见内容并自动跟随；上翻后锚点固定，新记录到达或头部淘汰不改变顶部位置；锚点被淘汰时从当前最旧记录继续显示；回到底部时清除锚点。新增计数使用入队时记录的可见总数与锚点快照之差。

日志行以接收序号开头，后接 `[时间] [线程/级别] (logger) 消息`。默认沿用 Loom 控制台配色：时间蓝色，线程和级别按级别着色，logger 青色，ERROR、FATAL 的消息红色；各级颜色可在配置中修改。括号作为普通字符保留。消息按像素宽度换行，续行不重复前缀且从内容区左边开始，单条记录最多排版 8 行，超出部分以省略号结尾。布局只计算可见记录，行高随换行行数变化，滚动位置以记录锚点和可见高度共同确定。仅查看叠加层复用同一布局与滚动锚点，高度使用配置值，默认 65%。

## 配置

配置模型位于 `common` 的 `client/config`，使用 Cloth Config 的 AutoConfig 与 Gson 序列化，文件名为 `ingamecnl`。字段分为筛选、颜色和叠加层三组：筛选保存级别与关键词；颜色保存时间、各级别、logger、普通消息和错误消息的 RGB 值（渲染时补为不透明 ARGB）；叠加层保存高度百分比、动画毫秒数和不透明度百分比。加载时校正关键词空白和数值范围，默认值与既有界面一致。

`ConsoleConfig` 在 `ConsoleClient.initialize()` 中注册一次，并把筛选同步到 `LogCapture`。配置页面的保存和加载监听器都重新应用筛选；日志模式执行 `filter` 命令时反向写入配置并保存。颜色、高度、动画时长和不透明度直接读取当前配置，不复制到界面状态。

配置页面由 `AutoConfigClient` 创建；Fabric 的 Mod Menu 入口和 NeoForge 的 `IConfigScreenFactory` 扩展点都把模组列表界面作为父界面。配置界面打开时暂停仅查看叠加层绘制，关闭后按原状态恢复。Cloth Config 在 Fabric 与 NeoForge 都是必需运行依赖；Mod Menu 只影响 Fabric 的配置入口，未安装时不影响其他功能。

## 阶段 3：本地命令解析

日志模式输入解析为明确的命令值，再交给对应能力执行。解析错误返回可分类的输入错误，界面展示错误文案并保留草稿；命令成功执行后清空输入。命令解析不直接修改日志列表或服务端状态。

阶段 2 已实现 `filter`：`filter level` 校验预定义级别，`filter keyword` 读取剩余文本，`filter reset`、`filter reset level`、`filter reset keyword` 与 `filter show` 使用主计划规定的子命令。`grep` 解析留待阶段 3。

`grep` 以参数名解析选项，接受调整后的选项顺序；重复选项、未知选项和缺失值均返回输入错误。

`grep` 的 `-k` 和 `-l` 按当前语法提供，`-t1`、`-t2` 可独立省略。带空格的值使用双引号；时间解析采用严格日期校验，按本机时区转换为时间点。短时间在查询提交时补齐当天日期，开始与结束时间使用同一基准日期。

关键词在消息与异常文本中做忽略大小写的普通子串匹配，级别按精确值匹配。时间下界和上界均包含端点，筛选条件按交集组合。查询语法不执行 Shell 展开或系统进程。

## 后续阶段：历史查询与阅读

查询提交时在客户端线程确定保留历史的有序记录引用及其范围。查询只覆盖提交时仍保留的记录，读取原始历史，不读取实时视图；查询期间的新日志不加入结果。

查询由单个查询执行器完成，当前面板最多持有一个活动查询和一个阅读结果。关闭阅读区、启动新查询或退出客户端时取消旧查询；执行器在记录遍历中检查取消，结果返回客户端时核对查询标识，忽略已替换任务的返回值。

搜索结果只保留不可变记录引用，不复制整份日志文本。查询和阅读期间允许被历史淘汰的记录继续由当前结果持有，关闭时释放；活动查询与结果占用纳入容量安排。

阅读区优先消费方向键、翻页键和退出键。实时日志继续接收，更新实时面板的新增计数，不切换焦点或覆盖阅读区。退出阅读后恢复原输入草稿与实时阅读位置。

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

阶段 2 还需要在持续产生日志时检查跟随、翻页、回底与新增计数，检查修改筛选前后旧记录保持不变、后续记录按新规则显示，并分别触发队列丢弃与历史淘汰确认计数。仅查看模式需要在世界中确认游戏继续接收移动、视角和攻击/使用，在存档外确认鼠标点击穿透叠加层，并检查翻页、回底、Shift+Tab 切换与 Esc 关闭。配置需要在 Fabric Mod Menu 与 NeoForge 模组列表两个入口检查保存、筛选同步、颜色和叠加层参数。两个加载器的开发客户端都需要运行检查；视觉检查由使用者完成。

发布产物检查 Kotlin class 与共享资源存在；Fabric metadata 包含匹配的运行依赖，NeoForge metadata 的 `modLoader` 为 `kotlinforforge` 且不引用嵌套标准库。后续验证随对应功能阶段执行，不将测试依赖或测试替身加入生产产物。
