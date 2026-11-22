package com.nts.ingamecnl.client.grep

import com.nts.ingamecnl.log.LogLevel
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal data class GrepQuery(
    val keyword: String,
    val level: LogLevel,
    val startMillis: Long?,
    val endMillis: Long?,
    val startText: String?,
    val endText: String?,
) {
    fun matches(record: com.nts.ingamecnl.client.log.LogRecord): Boolean {
        if (record.level != level) return false
        if (startMillis != null && record.timeMillis < startMillis) return false
        if (endMillis != null && record.timeMillis > endMillis) return false
        if (record.message.contains(keyword, ignoreCase = true)) return true
        return record.throwableText?.contains(keyword, ignoreCase = true) == true
    }
}

internal enum class GrepCommandError {
    UNTERMINATED_QUOTE,
    UNEXPECTED_ARGUMENT,
    UNKNOWN_OPTION,
    MISSING_VALUE,
    DUPLICATE_OPTION,
    MISSING_KEYWORD,
    MISSING_LEVEL,
    EMPTY_KEYWORD,
    INVALID_LEVEL,
    INVALID_TIME,
    START_AFTER_END,
}

internal sealed interface GrepCommandParse {
    data class Parsed(val query: GrepQuery) : GrepCommandParse
    data class Error(val error: GrepCommandError, val detail: String? = null) : GrepCommandParse
}

private val SHORT_TIME = Regex("""\d{2}:\d{2}:\d{2}""")
private val FULL_TIME = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")
private val TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
private val FULL_DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

internal fun parseGrepCommand(
    input: String,
    now: ZonedDateTime = ZonedDateTime.now(),
): GrepCommandParse {
    val tokens: List<String>
    val tokenized = tokenize(input)
    if (tokenized == null) {
        return GrepCommandParse.Error(GrepCommandError.UNTERMINATED_QUOTE)
    }
    tokens = tokenized
    if (tokens.isEmpty() || !tokens[0].equals("grep", ignoreCase = true)) {
        return GrepCommandParse.Error(GrepCommandError.UNEXPECTED_ARGUMENT, tokens.firstOrNull())
    }

    var keyword: String? = null
    var level: LogLevel? = null
    var startText: String? = null
    var endText: String? = null
    val seen = mutableSetOf<String>()
    var index = 1
    while (index < tokens.size) {
        val option = tokens[index]
        if (!option.startsWith("-")) {
            return GrepCommandParse.Error(GrepCommandError.UNEXPECTED_ARGUMENT, option)
        }
        if (!seen.add(option)) {
            return GrepCommandParse.Error(GrepCommandError.DUPLICATE_OPTION, option)
        }
        val value = tokens.getOrNull(index + 1)
            ?: return GrepCommandParse.Error(GrepCommandError.MISSING_VALUE, option)
        if (value.startsWith("-")) {
            return GrepCommandParse.Error(GrepCommandError.MISSING_VALUE, option)
        }
        when (option) {
            "-k" -> {
                if (value.isEmpty()) return GrepCommandParse.Error(GrepCommandError.EMPTY_KEYWORD)
                keyword = value
            }
            "-l" -> level = LogLevel.parse(value)
                ?: return GrepCommandParse.Error(GrepCommandError.INVALID_LEVEL, value)
            "-t1" -> startText = value
            "-t2" -> endText = value
            else -> return GrepCommandParse.Error(GrepCommandError.UNKNOWN_OPTION, option)
        }
        index += 2
    }

    val actualKeyword = keyword ?: return GrepCommandParse.Error(GrepCommandError.MISSING_KEYWORD)
    val actualLevel = level ?: return GrepCommandParse.Error(GrepCommandError.MISSING_LEVEL)
    val start = if (startText != null) {
        parseTime(startText, now) ?: return GrepCommandParse.Error(GrepCommandError.INVALID_TIME, startText)
    } else {
        null
    }
    val end = if (endText != null) {
        parseTime(endText, now) ?: return GrepCommandParse.Error(GrepCommandError.INVALID_TIME, endText)
    } else {
        null
    }
    if (start != null && end != null && start > end) {
        return GrepCommandParse.Error(GrepCommandError.START_AFTER_END)
    }
    return GrepCommandParse.Parsed(
        GrepQuery(actualKeyword, actualLevel, start, end, startText, endText),
    )
}

private fun parseTime(value: String, now: ZonedDateTime): Long? = try {
    val local = when {
        SHORT_TIME.matches(value) -> LocalDateTime.of(now.toLocalDate(), LocalTime.parse(value, TIME_ONLY))
        FULL_TIME.matches(value) -> LocalDateTime.parse(value, FULL_DATE_TIME)
        else -> return null
    }
    val zone = now.zone
    if (zone.rules.getValidOffsets(local).size != 1) return null
    local.atZone(zone).toInstant().toEpochMilli()
} catch (_: DateTimeException) {
    null
}

private fun tokenize(input: String): List<String>? {
    val tokens = ArrayList<String>()
    val token = StringBuilder()
    var quoted = false
    var tokenStarted = false
    var index = 0
    while (index < input.length) {
        val char = input[index]
        if (quoted) {
            if (char == '"') {
                quoted = false
            } else {
                token.append(char)
            }
            index++
            continue
        }
        when {
            char == '"' -> {
                if (tokenStarted) return null
                quoted = true
                tokenStarted = true
            }
            char.isWhitespace() -> {
                if (tokenStarted) {
                    tokens.add(token.toString())
                    token.setLength(0)
                    tokenStarted = false
                }
            }
            else -> {
                token.append(char)
                tokenStarted = true
            }
        }
        index++
    }
    if (quoted) return null
    if (tokenStarted) tokens.add(token.toString())
    return tokens
}
