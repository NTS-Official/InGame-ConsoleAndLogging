package com.nts.ingamecnl.command

import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.synchronization.SuggestionProviders
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.server.MinecraftServer

/**
 * 为控制台构造一份与玩家游戏权限无关的完整服务端命令树。
 *
 * 原版 `Commands.sendCommands` 用玩家自己的命令源裁剪命令树，非 OP 玩家看不到 `op`、`gamemode` 等指令。
 * 控制台走的是本模组独立的权限体系（`ingamecnl-server.json` 白/黑名单），命令树因此按服务端最高权限
 * 重建后单独下发给控制台用户。
 */
internal object ConsoleCommandTree {
    private val INSPECTOR = object : ClientboundCommandsPacket.NodeInspector<CommandSourceStack> {
        override fun suggestionId(node: ArgumentCommandNode<CommandSourceStack, *>): net.minecraft.resources.Identifier =
            SuggestionProviders.getName(node.customSuggestions)

        override fun isExecutable(node: CommandNode<CommandSourceStack>): Boolean = node.command != null

        override fun isRestricted(node: CommandNode<CommandSourceStack>): Boolean = false
    }

    fun packet(server: MinecraftServer): ClientboundCommandsPacket =
        ClientboundCommandsPacket(build(server), INSPECTOR)

    private fun build(server: MinecraftServer): RootCommandNode<CommandSourceStack> {
        val source = server.createCommandSourceStack()
        val root = RootCommandNode<CommandSourceStack>()
        val redirects = HashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>>()
        redirects[server.commands.dispatcher.root] = root
        fillUsableCommands(server.commands.dispatcher.root, root, source, redirects)
        return root
    }

    /** 复刻 `Commands.fillUsableCommands`：按 source 权限裁剪，并重建 redirect 映射。 */
    private fun fillUsableCommands(
        node: CommandNode<CommandSourceStack>,
        output: CommandNode<CommandSourceStack>,
        source: CommandSourceStack,
        redirects: MutableMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>>,
    ) {
        for (child in node.children) {
            if (!child.canUse(source)) continue
            val builder = child.createBuilder()
            val redirect = builder.redirect
            if (redirect != null) builder.redirect(redirects[redirect])
            val built = builder.build()
            redirects[child] = built
            output.addChild(built)
            if (child.children.isNotEmpty()) {
                fillUsableCommands(child, built, source, redirects)
            }
        }
    }
}
