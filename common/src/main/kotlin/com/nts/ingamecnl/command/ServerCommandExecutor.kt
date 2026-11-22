package com.nts.ingamecnl.command

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSource
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

internal object ServerCommandExecutor {
    private const val MAX_FEEDBACK_LINES = 64
    private const val MAX_FEEDBACK_CHARS = 16_000

    fun execute(server: MinecraftServer, command: String): ServerCommandResult {
        val source = CapturingCommandSource()
        val commandSource = server.createCommandSourceStack().withSource(source)
        return try {
            val parsed = server.commands.dispatcher.parse(command, commandSource)
            val value = server.commands.dispatcher.execute(parsed)
            ServerCommandResult(CommandResultStatus.EXECUTED, value, source.messages())
        } catch (exception: CommandSyntaxException) {
            source.fail(exception.message ?: exception.javaClass.simpleName)
            ServerCommandResult(CommandResultStatus.FAILED, feedback = source.messages())
        } catch (exception: Exception) {
            source.fail(exception.message ?: exception.javaClass.simpleName)
            ServerCommandResult(CommandResultStatus.FAILED, feedback = source.messages())
        }
    }

    private class CapturingCommandSource : CommandSource {
        private val messages = mutableListOf<Component>()
        private var characters = 0

        override fun sendSystemMessage(message: Component) {
            val length = message.string.length
            if (messages.size >= MAX_FEEDBACK_LINES || characters + length > MAX_FEEDBACK_CHARS) return
            messages += message
            characters += length
        }

        override fun acceptsSuccess(): Boolean = true

        override fun acceptsFailure(): Boolean = true

        override fun shouldInformAdmins(): Boolean = false

        override fun alwaysAccepts(): Boolean = true

        fun fail(message: String) {
            sendSystemMessage(Component.literal(message))
        }

        fun messages(): List<Component> = messages.toList()
    }
}
