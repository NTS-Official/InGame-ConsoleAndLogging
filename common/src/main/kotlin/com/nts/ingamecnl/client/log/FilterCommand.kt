package com.nts.ingamecnl.client.log

sealed interface FilterCommand {
    data class SetLevel(val level: LogLevel) : FilterCommand

    data class SetKeyword(val keyword: String) : FilterCommand

    data object ResetAll : FilterCommand

    data object ResetLevel : FilterCommand

    data object ResetKeyword : FilterCommand

    data object Show : FilterCommand
}

enum class FilterCommandError {
    UNKNOWN_COMMAND,
    MISSING_SUBCOMMAND,
    UNKNOWN_SUBCOMMAND,
    MISSING_LEVEL,
    INVALID_LEVEL,
    MISSING_KEYWORD,
    TOO_MANY_ARGUMENTS,
}

sealed interface FilterCommandParse {
    data class Parsed(val command: FilterCommand) : FilterCommandParse

    data class Error(val error: FilterCommandError, val detail: String? = null) : FilterCommandParse
}

fun DisplayFilter.apply(command: FilterCommand): DisplayFilter? = when (command) {
    is FilterCommand.SetLevel -> copy(level = command.level)
    is FilterCommand.SetKeyword -> copy(keyword = command.keyword)
    FilterCommand.ResetAll -> DisplayFilter()
    FilterCommand.ResetLevel -> copy(level = null)
    FilterCommand.ResetKeyword -> copy(keyword = null)
    FilterCommand.Show -> null
}

private val WHITESPACE = Regex("\\s+")

fun parseFilterCommand(input: String): FilterCommandParse {
    val tokens = input.trim().split(WHITESPACE, limit = 3)
    if (!tokens[0].equals("filter", ignoreCase = true)) {
        return FilterCommandParse.Error(FilterCommandError.UNKNOWN_COMMAND)
    }
    if (tokens.size == 1) {
        return FilterCommandParse.Error(FilterCommandError.MISSING_SUBCOMMAND)
    }
    val argument = tokens.getOrNull(2)?.trim().orEmpty()
    return when (tokens[1].lowercase()) {
        "level" -> parseLevel(argument)
        "keyword" -> if (argument.isEmpty()) {
            FilterCommandParse.Error(FilterCommandError.MISSING_KEYWORD)
        } else {
            FilterCommandParse.Parsed(FilterCommand.SetKeyword(argument))
        }
        "reset" -> parseReset(argument)
        "show" -> if (argument.isEmpty()) {
            FilterCommandParse.Parsed(FilterCommand.Show)
        } else {
            FilterCommandParse.Error(FilterCommandError.TOO_MANY_ARGUMENTS)
        }
        else -> FilterCommandParse.Error(FilterCommandError.UNKNOWN_SUBCOMMAND)
    }
}

private fun parseLevel(argument: String): FilterCommandParse {
    if (argument.isEmpty()) return FilterCommandParse.Error(FilterCommandError.MISSING_LEVEL)
    if (argument.any(Char::isWhitespace)) {
        return FilterCommandParse.Error(FilterCommandError.TOO_MANY_ARGUMENTS)
    }
    val level = LogLevel.parse(argument)
        ?: return FilterCommandParse.Error(FilterCommandError.INVALID_LEVEL, argument)
    return FilterCommandParse.Parsed(FilterCommand.SetLevel(level))
}

private fun parseReset(argument: String): FilterCommandParse = when {
    argument.isEmpty() -> FilterCommandParse.Parsed(FilterCommand.ResetAll)
    argument.equals("level", ignoreCase = true) -> FilterCommandParse.Parsed(FilterCommand.ResetLevel)
    argument.equals("keyword", ignoreCase = true) -> FilterCommandParse.Parsed(FilterCommand.ResetKeyword)
    argument.any(Char::isWhitespace) -> FilterCommandParse.Error(FilterCommandError.TOO_MANY_ARGUMENTS)
    else -> FilterCommandParse.Error(FilterCommandError.UNKNOWN_SUBCOMMAND)
}
