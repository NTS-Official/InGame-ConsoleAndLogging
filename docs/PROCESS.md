# 开发进度

阶段 1 与阶段 2 的代码已落地，Fabric 和 NeoForge 的完整构建均已通过。游戏内交互与视觉验证待完成，下一项功能是历史查询（`grep`）。

## 已实现

- `common`、`fabric`、`neoforge` 均已配置 Kotlin。两个平台的客户端入口调用共享的 `ConsoleClient.initialize()`。
- [ConsoleClient.kt](../common/src/main/kotlin/com/nts/ingamecnl/client/ConsoleClient.kt) 管理全局热键、面板状态和两份输入草稿。[客户端 Mixin](../common/src/main/java/com/nts/ingamecnl/mixin/client) 接入键鼠事件与原界面的恢复。
- 面板可从主菜单、世界选择、聊天、物品栏和游戏中打开。当前包括顶部线性开合、10% 背景不透明度、`Shift+Tab` 切换和输入行提示。命令模式仅在本地内置服务端就绪时可用。
- 阶段 2 接入 Log4j Appender、有界接收队列和原始历史。日志模式按接收序号显示 Loom 风格的日志行，支持自动换行、底部自动跟随、翻页、`End` 回底和新增记录计数。
- 日志模式的 `filter level`、`filter keyword`、`filter reset` 与 `filter show` 已接入。筛选规则以入队顺序生效，已显示的记录保持原样，隐藏记录仍保留在原始历史中。
- 命令模式的 Enter 仍只显示提示并保留草稿；服务端命令执行待接入。

## 阶段 2 验证

`common` 的 Kotlin 编译、`fabric` 与 `neoforge` 的完整构建均已通过。Fabric 开发客户端可启动并显示实时日志列表与筛选状态行。

待验证：

- 翻页、`End`、滚轮和新增计数在持续产生日志时的行为。
- 修改筛选后旧记录保持原样，新记录按新规则显示。
- 队列丢弃与历史淘汰计数。
- NeoForge 开发客户端。
- GUI 缩放、窗口尺寸变化与中文文案的视觉检查。

## 下一步

按[实施顺序](plan/README.md#实施顺序)接入 `grep` 历史查询与阅读区，再接入阶段 4 的自动预览和阶段 5 的服务端终端。模块边界和开发约定见 [AGENTS.md](../AGENTS.md)。
