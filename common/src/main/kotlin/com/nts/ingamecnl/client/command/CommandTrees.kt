package com.nts.ingamecnl.client.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.nts.ingamecnl.client.gui.ConsoleMode
import com.nts.ingamecnl.log.LogLevel
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.core.RegistryAccess
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.flag.FeatureFlagSet

/**
 * 客户端持有的命令树。
 *
 * 原版候选列表固定从 `ClientPacketListener.getCommands()` 取命令树，而 Brigadier 生成候选时并不检查节点权限，
 * 本地命令与服务端命令因此必须分属不同的树，否则候选会互相污染。
 */
internal object CommandTrees {
    /** 日志模式下的本地命令。 */
    val LOCAL_COMMANDS = listOf(COMMAND_FILTER, COMMAND_GREP, COMMAND_AUTOSPLASH)

    /** 混合模式下带冒号前缀的本地命令，混合模式不接受 autosplash。 */
    val MIXED_LOCAL_COMMANDS = listOf(COMMAND_FILTER, COMMAND_GREP)

    private val LEVELS = SuggestionProvider<ClientSuggestionProvider> { _, builder ->
        LogLevel.entries.forEach { builder.suggest(it.label.lowercase()) }
        builder.buildFuture()
    }

    /** 日志模式下的本地命令树。 */
    val local: CommandDispatcher<ClientSuggestionProvider> by lazy { buildLocal("", LOCAL_COMMANDS) }

    /** 混合模式下带冒号前缀的本地命令树。 */
    val prefixed: CommandDispatcher<ClientSuggestionProvider> by lazy { buildLocal(":", MIXED_LOCAL_COMMANDS) }

    /** 服务端按自身最高权限下发的完整命令树，收到之前为空。 */
    var server: CommandDispatcher<ClientSuggestionProvider>? = null
        private set

    /** 命令模式没有可用服务端命令树时使用的空树，避免回退到客户端自己的命令表。 */
    private val EMPTY = CommandDispatcher<ClientSuggestionProvider>()

    fun onServerTree(
        packet: ClientboundCommandsPacket,
        registryAccess: RegistryAccess,
        enabledFeatures: FeatureFlagSet,
    ) {
        val context = CommandBuildContext.simple(registryAccess, enabledFeatures)
        server = CommandDispatcher(packet.getRoot(context, NODE_BUILDER))
    }

    fun clearServer() {
        server = null
    }

    /**
     * 按当前模式与输入前缀挑选命令树：日志模式用本地命令树，混合模式的冒号输入用本地命令树，
     * 单人游戏用集成服务器的命令树，其余情况只用本模组下发的命令树。
     *
     * 远程服务器上拿不到本模组的命令树时返回空树而不是回退到 `ClientPacketListener` 的命令表，
     * 否则候选会来自玩家自身权限可见的原版指令，实际却无法通过控制台执行。
     */
    fun select(
        mode: ConsoleMode,
        mixed: Boolean,
        singleplayer: Boolean,
        text: String,
        original: CommandDispatcher<ClientSuggestionProvider>,
    ): CommandDispatcher<ClientSuggestionProvider> = when {
        mode != ConsoleMode.COMMAND -> local
        mixed && text.trimStart().startsWith(":") -> prefixed
        singleplayer -> original
        else -> server ?: EMPTY
    }

    /** 命令模式下当前可用的服务端命令树，未就绪时为空树。 */
    fun serverOrEmpty(): CommandDispatcher<ClientSuggestionProvider> = server ?: EMPTY

    private fun buildLocal(prefix: String, names: List<String>): CommandDispatcher<ClientSuggestionProvider> {
        val dispatcher = CommandDispatcher<ClientSuggestionProvider>()
        for (name in names) {
            dispatcher.register(
                when (name) {
                    COMMAND_FILTER -> filterNode(prefix)
                    COMMAND_GREP -> grepNode(prefix)
                    else -> autosplashNode(prefix)
                },
            )
        }
        return dispatcher
    }

    private fun filterNode(prefix: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
        literal(prefix + COMMAND_FILTER)
            .then(literal("level").then(argument("level", StringArgumentType.word()).suggests(LEVELS)))
            .then(literal("keyword").then(argument("keyword", StringArgumentType.greedyString())))
            .then(literal("reset").then(literal("level")).then(literal("keyword")))
            .then(literal("show"))

    private fun grepNode(prefix: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
        literal(prefix + COMMAND_GREP)
            .then(literal("-k").then(argument("keyword", StringArgumentType.greedyString())))
            .then(literal("-l").then(argument("level", StringArgumentType.word()).suggests(LEVELS)))
            .then(literal("-t1").then(argument("start", StringArgumentType.word())))
            .then(literal("-t2").then(argument("end", StringArgumentType.greedyString())))

    private fun autosplashNode(prefix: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
        literal(prefix + COMMAND_AUTOSPLASH)
            .then(literal("filter").then(argument("keyword", StringArgumentType.greedyString())))

    private fun literal(name: String): LiteralArgumentBuilder<ClientSuggestionProvider> =
        LiteralArgumentBuilder.literal(name)

    private fun argument(name: String, type: StringArgumentType) =
        RequiredArgumentBuilder.argument<ClientSuggestionProvider, String>(name, type)

    /** 按原版 `ClientPacketListener` 的方式还原服务端下发的命令树。 */
    private val NODE_BUILDER = object : ClientboundCommandsPacket.NodeBuilder<ClientSuggestionProvider> {
        override fun createLiteral(name: String): ArgumentBuilder<ClientSuggestionProvider, *> =
            LiteralArgumentBuilder.literal(name)

        override fun createArgument(
            name: String,
            type: ArgumentType<*>,
            id: Identifier?,
        ): ArgumentBuilder<ClientSuggestionProvider, *> {
            @Suppress("UNCHECKED_CAST")
            val argumentType = type as ArgumentType<Any>
            val builder = RequiredArgumentBuilder.argument<ClientSuggestionProvider, Any>(name, argumentType)
            if (id != null) builder.suggests(SuggestionProviders.getProvider(id))
            return builder
        }

        override fun configure(
            builder: ArgumentBuilder<ClientSuggestionProvider, *>,
            executable: Boolean,
            restricted: Boolean,
        ): ArgumentBuilder<ClientSuggestionProvider, *> {
            // 服务端下发的命令树不标记受限，控制台按自己的权限体系放行，因此无需附加原版的受限判定。
            if (executable) builder.executes { 0 }
            return builder
        }
    }

    private const val COMMAND_FILTER = "filter"
    private const val COMMAND_GREP = "grep"
    private const val COMMAND_AUTOSPLASH = "autosplash"
}
