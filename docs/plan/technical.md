# 技术设计

## 阶段 1：构建与入口

依赖版本集中在根 [gradle.properties](../../gradle.properties)，Java 与 Kotlin 目标由根 [build.gradle](../../build.gradle) 的 `javaTarget` 统一配置。三个模块均启用 Kotlin JVM 插件，Kotlin toolchain 和 JVM target 与 Java 目标一致。

Kotlin 编译输出属于各模块的主源码集，随现有 `jar`、`sourcesJar` 和 Architectury 平台转换任务输出。平台 `shadowBundle` 只包含 `common` 转换产物，不展开传递运行依赖。

| 平台 | Kotlin 运行时 | 加载入口 |
| --- | --- | --- |
| Fabric | 独立安装 Fabric Language Kotlin，构建依赖与 metadata 版本来自同一属性 | 普通 Kotlin `class` 实现 `ClientModInitializer`，使用默认 Java 适配器 |
| NeoForge | Loom `include` 嵌入 Kotlin 标准库，显式绑定发布用 `shadowJar` | 普通 Kotlin `class` 使用 `@Mod`，限定 `Dist.CLIENT`，保留 `javafml` |

NeoForge 发布 JAR 应包含 `META-INF/jarjar/metadata.json` 及其引用的标准库 JAR。标准库保留为嵌套库，不复制类到外层产物；现阶段不使用 Kotlin 反射或协程运行时。

Fabric 的 `environment` 为 `client`，只有客户端入口。NeoForge 入口限定客户端分发环境，依赖声明对应客户端侧。共享入口由各加载器在客户端初始化时调用一次。

| 文件 | 职责 |
| --- | --- |
| [InGameConsole.kt](../../common/src/main/kotlin/com/nts/ingamecnl/InGameConsole.kt) | Mod 标识常量 |
| [ConsoleClient.kt](../../common/src/main/kotlin/com/nts/ingamecnl/client/ConsoleClient.kt) | 共享初始化入口 `ConsoleClient.initialize()`、客户端状态和控制台界面 |
| [KeyboardHandlerMixin.java](../../common/src/main/java/com/nts/ingamecnl/mixin/client/KeyboardHandlerMixin.java) | 全局键盘与字符输入接入 |
| [MouseHandlerMixin.java](../../common/src/main/java/com/nts/ingamecnl/mixin/client/MouseHandlerMixin.java) | 全局鼠标热键接入 |
| [MinecraftScreenMixin.java](../../common/src/main/java/com/nts/ingamecnl/mixin/client/MinecraftScreenMixin.java) | 覆盖前界面的挂起、恢复与生命周期接入 |
| [InGameConsoleFabric.kt](../../fabric/src/main/kotlin/com/nts/ingamecnl/fabric/InGameConsoleFabric.kt) | Fabric 客户端生命周期接入 |
| [InGameConsoleNeoForge.kt](../../neoforge/src/main/kotlin/com/nts/ingamecnl/neoforge/InGameConsoleNeoForge.kt) | NeoForge 客户端生命周期接入 |

平台模块分别依赖 `common`，`common` 不依赖平台模块。共享实现使用 Architectury 的 `KeyMappingRegistry` 注册按键。客户端 Mixin 由 [ingamecnl.mixins.json](../../common/src/main/resources/ingamecnl.mixins.json) 声明，使用 Java 进行事件桥接，输入与界面规则统一由 Kotlin 入口负责。

## 阶段 1：状态与界面

`ConsoleClient` 是平台入口与 Mixin 的唯一共享入口，模式、草稿和控制台 `Screen` 类型保持文件私有。共享状态随客户端初始化建立，由客户端线程读写；每次打开面板创建一个 `Screen`，草稿独立于界面实例保留。

热键默认使用 grave accent，分类标识为 `ingamecnl:console`。键盘与鼠标 Mixin 在原始输入处理入口消费匹配的热键，主菜单、世界选择、聊天、物品栏和游戏中均可打开；按键设置正在捕获新绑定时优先接收事件。

开关由 GLFW 的按下事件触发，保存正在按住的键或鼠标按钮，释放时清除。按住期间消费对应重复事件、释放事件与键盘字符事件，每次按下只开关一次，开关键不进入输入框。

打开时保存原 `Screen`，通过 `Minecraft.setScreen` 安装控制台。生命周期桥接在覆盖期间挂起原界面的 `removed`，恢复时跳过重复 `added` 和初始化；尺寸改变时调用 `resize`。原界面继续更新和绘制，键鼠输入由控制台接收。游戏切换到其他界面时正常移除挂起界面，关闭面板时恢复原界面、草稿和焦点。

`ConsoleScreen` 使用目标版本的 `extractRenderState(GuiGraphicsExtractor, …)` 绘制，输入事件使用 `KeyEvent`、`MouseButtonEvent`。面板覆盖屏幕上方约 65% 区域，不绘制全屏模糊背景，`isPauseScreen()` 返回 `false`。

Shift+Tab 在界面层消费，优先于普通 Tab 焦点导航与自定义开关热键；从按下到释放只切换一次。模式切换时替换输入框内容并将焦点交还输入框，两个草稿分别保存。窗口尺寸改变时仅重排既有控件，保留文本、选区与焦点。所有文案使用语言资源，固定前缀与行尾切换提示在输入框外绘制；输入区域预留提示宽度，窄窗采用简短提示。

背景的 ARGB alpha 使用 10% 不透明度，文本维持正常透明度。输入框不绘制默认底色，关闭按钮使用无背景的文字按钮。

`PanelTransition` 保存起始展开比例、展开或收起目标及单调时钟起始值，按完整行程耗时线性计算当前位置。提前关闭和收起途中反向展开均从当前位置匀速移动，不跳回动画端点。每帧同步更新控件位置与可见区域裁剪，鼠标命中使用相同位置。

打开面板时记录当前客户端世界对象，存档外允许为空。世界对象改变时立即关闭面板，不恢复旧世界的界面。Esc、关闭按钮和热键先启动收起；收起期间停用输入编辑和关闭按钮，清除 tooltip，完成后通过 `Minecraft.setScreen(previousScreen)` 恢复原界面；原界面为空时返回游戏。

命令模式的可用条件为当前世界非空且仍与打开时一致、客户端连接有效、内置服务端已就绪且仍在运行。存档外和远程服务器连接中禁用命令模式；不可用时 Shift+Tab 显示提示并保持日志模式，已进入命令模式时则自动退回并保留命令草稿。阶段 1 的 Enter 只提供尚未接入的反馈。

## 后续阶段：记录与状态所有权

后续类型按下表拆分；纯规则不持有 Minecraft、Log4j 或线程执行器。

| 类型 | 内容 | 所有权 |
| --- | --- | --- |
| `LogRecord` | 接收序号、事件时间、级别、线程名、logger、消息、异常文本与截断状态 | 不可变值，可跨线程传递 |
| `DisplayFilter` | 可选精确级别与关键词；匹配函数 | 不可变值 |
| `HistoryQuery` | 关键词、级别、可选起止时间 | 不可变值 |
| `ConsoleMode` | 日志或命令模式、前缀与对应输入状态 | 客户端线程 |
| `PanelState` | 收起、预览、展开及阅读位置 | 客户端线程 |
| `SearchResult` | 查询条件、查询时的历史范围、有序匹配记录 | 单次查询与结果阅读区 |
| `ServerSession` | 当前内置服务端实例及会话标识、待返回任务 | 当前世界会话 |

消息、异常堆栈、命令回显和命令反馈保留各自的记录类型。原始日志历史用于 `grep`，命令记录进入面板时间线，不回灌 Log4j。

## 后续阶段：日志接收与筛选顺序

Log4j Appender 从事件中提取自己的不可变记录，不跨回调保留 Log4j 可复用事件。格式化消息与异常文本在记录大小限制内完成，随后写入 `LogInbox`。

`LogInbox` 持有有界队列和明确命名的锁；日志入队、接收序号分配和实时筛选规则更新在同一同步边界内完成。每条入队记录携带当时的不可变 `DisplayFilter` 引用。客户端批量取出后将原始记录加入历史，再按该条记录携带的规则决定是否显示。

规则更新以日志入队顺序为边界，不使用日志时间戳排序。已经入队但尚未绘制的日志仍使用原规则；之后接收的日志使用新规则。界面上的 `filter` 更新即时显示当前条件，不重新处理已经显示的记录。

队列达到容量上限时拒绝新日志并累计丢弃数量，不阻塞产生日志的线程。历史达到上限时淘汰最旧记录，同时移除对应实时视图条目；正在阅读的搜索结果独立持有所需记录。丢弃和淘汰数量在界面分别显示。

客户端 tick 以条数与耗时双重预算接收数据，剩余记录留到后续 tick。界面只排版可见记录，布局缓存随记录淘汰、窗口宽度和字体变化失效。

容量项包括队列条数与文本大小、历史条数与文本大小、单条消息与异常文本长度、每 tick 接收预算。具体值按主计划的容量待确定项实施，不通过修改日志框架的全局级别限制内存。

## 后续阶段：本地命令解析

日志模式输入先解析为明确的命令值，再交给对应能力执行。解析错误返回可分类的输入错误，界面展示参数位置和用法；命令解析不直接修改日志列表或服务端状态。

`filter level` 校验预定义级别，`filter keyword` 读取剩余文本，`reset` 和 `show` 使用主计划规定的子命令。`grep` 以参数名解析选项，接受调整后的选项顺序；重复选项、未知选项和缺失值均返回输入错误。

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

两端构建与游戏运行命令见[使用与开发](../usage.md)。阶段 1 需要实际检查热键重绑定、模式切换、草稿保留、窗口缩放、世界退出和输入恢复。

发布产物检查 Kotlin class 与共享资源存在；Fabric metadata 包含匹配的运行依赖，NeoForge metadata 引用的嵌套标准库实际存在。后续验证随对应功能阶段执行，不将测试依赖或测试替身加入生产产物。
