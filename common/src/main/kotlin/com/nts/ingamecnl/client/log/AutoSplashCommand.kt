package com.nts.ingamecnl.client.log

sealed interface AutoSplashCommand {
    data class SetKeyword(val keyword: String) : AutoSplashCommand
}

enum class AutoSplashCommandError {
    MISSING_SUBCOMMAND,
    UNKNOWN_SUBCOMMAND,
    MISSING_KEYWORD,
}

sealed interface AutoSplashCommandParse {
    data class Parsed(val command: AutoSplashCommand) : AutoSplashCommandParse

    data class Error(val error: AutoSplashCommandError) : AutoSplashCommandParse
}

fun parseAutoSplashCommand(input: String): AutoSplashCommandParse {
    val tokens = input.trim().split(Regex("\\s+"), limit = 3)
    if (tokens.size == 1) {
        return AutoSplashCommandParse.Error(AutoSplashCommandError.MISSING_SUBCOMMAND)
    }
    if (!tokens[1].equals("filter", ignoreCase = true)) {
        return AutoSplashCommandParse.Error(AutoSplashCommandError.UNKNOWN_SUBCOMMAND)
    }
    val keyword = tokens.getOrNull(2)?.trim().orEmpty()
    if (keyword.isEmpty()) {
        return AutoSplashCommandParse.Error(AutoSplashCommandError.MISSING_KEYWORD)
    }
    return AutoSplashCommandParse.Parsed(AutoSplashCommand.SetKeyword(keyword))
}
