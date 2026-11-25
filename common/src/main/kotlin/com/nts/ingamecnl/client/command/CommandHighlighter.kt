package com.nts.ingamecnl.client.command

import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.nts.ingamecnl.client.ConsoleClient
import com.nts.ingamecnl.client.grep.GrepCommandError
import com.nts.ingamecnl.client.grep.GrepCommandParse
import com.nts.ingamecnl.client.grep.parseGrepCommand
import com.nts.ingamecnl.client.gui.ConsoleMode
import com.nts.ingamecnl.client.log.AutoSplashCommandError
import com.nts.ingamecnl.client.log.AutoSplashCommandParse
import com.nts.ingamecnl.client.log.FilterCommandError
import com.nts.ingamecnl.client.log.FilterCommandParse
import com.nts.ingamecnl.client.log.parseAutoSplashCommand
import com.nts.ingamecnl.client.log.parseFilterCommand
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider

internal enum class CommandInputState { EMPTY, VALID, INCOMPLETE, INVALID }

/** 判定输入框内容落在哪一类，供输入行着色使用。 */
internal class CommandHighlighter(
    private val minecraft: Minecraft,
    private val mode: () -> ConsoleMode,
) {
    fun stateOf(input: String): CommandInputState {
        val text = input.trim()
        if (text.isEmpty()) return CommandInputState.EMPTY
        val mixed = mode() == ConsoleMode.COMMAND && ConsoleClient.isMixedMode()
        if (text.startsWith(":")) {
            if (!mixed) return CommandInputState.INVALID
            val body = text.substring(1).trim()
            if (body.isEmpty()) return CommandInputState.EMPTY
            return localState(body, CommandTrees.MIXED_LOCAL_COMMANDS)
        }
        if (mode() == ConsoleMode.COMMAND) return serverState(text)
        return localState(text, CommandTrees.LOCAL_COMMANDS)
    }

    private fun localState(text: String, commands: List<String>): CommandInputState {
        val word = text.substringBefore(' ')
        val command = commands.firstOrNull { word.equals(it, ignoreCase = true) }
            ?: return if (commands.any { it.startsWith(word, ignoreCase = true) }) {
                CommandInputState.INCOMPLETE
            } else {
                CommandInputState.INVALID
            }
        return when (command) {
            "filter" -> filterState(text)
            "grep" -> grepState(text)
            else -> autoSplashState(text)
        }
    }

    private fun filterState(text: String): CommandInputState =
        when (val parsed = parseFilterCommand(text)) {
            is FilterCommandParse.Parsed -> CommandInputState.VALID
            is FilterCommandParse.Error -> when (parsed.error) {
                FilterCommandError.MISSING_SUBCOMMAND,
                FilterCommandError.MISSING_LEVEL,
                FilterCommandError.MISSING_KEYWORD,
                -> CommandInputState.INCOMPLETE
                else -> CommandInputState.INVALID
            }
        }

    private fun grepState(text: String): CommandInputState =
        when (val parsed = parseGrepCommand(text)) {
            is GrepCommandParse.Parsed -> CommandInputState.VALID
            is GrepCommandParse.Error -> when (parsed.error) {
                GrepCommandError.UNTERMINATED_QUOTE,
                GrepCommandError.MISSING_VALUE,
                GrepCommandError.MISSING_KEYWORD,
                GrepCommandError.MISSING_LEVEL,
                -> CommandInputState.INCOMPLETE
                else -> CommandInputState.INVALID
            }
        }

    private fun autoSplashState(text: String): CommandInputState =
        when (val parsed = parseAutoSplashCommand(text)) {
            is AutoSplashCommandParse.Parsed -> CommandInputState.VALID
            is AutoSplashCommandParse.Error -> when (parsed.error) {
                AutoSplashCommandError.MISSING_SUBCOMMAND,
                AutoSplashCommandError.MISSING_KEYWORD,
                -> CommandInputState.INCOMPLETE
                else -> CommandInputState.INVALID
            }
        }

    private fun serverState(text: String): CommandInputState {
        val connection = minecraft.connection ?: return CommandInputState.INVALID
        // 单人游戏用集成服务器的命令表；远程服务器只用本模组下发的命令树，未就绪时没有可解析的命令。
        val dispatcher = if (minecraft.isSingleplayer) connection.commands else CommandTrees.serverOrEmpty()
        val provider: ClientSuggestionProvider = connection.suggestionsProvider
        val parsed = try {
            dispatcher.parse(text, provider)
        } catch (_: CommandSyntaxException) {
            return CommandInputState.INVALID
        }
        if (parsed.context.command == null) return CommandInputState.INCOMPLETE
        return if (parsed.reader.remaining.isEmpty()) CommandInputState.VALID else CommandInputState.INCOMPLETE
    }
}
