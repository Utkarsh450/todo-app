package com.utkarsh.todo.feature.quickadd.domain

import com.utkarsh.todo.feature.tasks.domain.Priority
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

enum class TokenKind { DAY, PROJECT, PRIORITY, REPEAT }

/** A recognised piece of the input, shown as a coloured chip under the text field. */
data class QuickAddToken(val kind: TokenKind, val label: String)

data class QuickAddResult(
    /** Input with all recognised tokens removed. */
    val title: String,
    val dueAt: ZonedDateTime?,
    val projectName: String?,
    val priority: Priority?,
    val repeat: String?,
    val tokens: List<QuickAddToken>,
    /** True when the day came with a clock time ("fri 3pm", "5pm"); false for a bare day or no date. */
    val timeSpecified: Boolean = false,
)

/**
 * Parses natural quick-add text such as `Review design tokens tomorrow 9am #Design !high every Sunday`.
 *
 * Supported tokens: a day word (today, tomorrow, tonight, weekday names) optionally followed by a
 * time, a bare time (`5pm`), `#project`, `!urgent|high|normal|low`, and `every <unit>`.
 */
class QuickAddParser @Inject constructor(private val clock: Clock) {

    /**
     * Only the token [kinds] are recognised; anything else stays in the title untouched, so a note or an
     * event keeps words like "!high" or "every monday" that mean nothing to it.
     */
    fun parse(input: String, kinds: Set<TokenKind> = TokenKind.entries.toSet()): QuickAddResult {
        val spans = mutableListOf<IntRange>()
        val tokens = mutableListOf<QuickAddToken>()
        val claimed = StringBuilder(input) // matched ranges are blanked so later patterns cannot reuse them

        fun claim(match: MatchResult) {
            spans += match.range
            for (i in match.range) claimed.setCharAt(i, ' ')
        }

        // Repeat first: "every sunday" must not be read as a due day.
        var repeat: String? = null
        REPEAT.takeIf { TokenKind.REPEAT in kinds }?.find(claimed)?.let { m ->
            repeat = m.value.trim().split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            tokens += QuickAddToken(TokenKind.REPEAT, repeat!!)
            claim(m)
        }

        var priority: Priority? = null
        PRIORITY.takeIf { TokenKind.PRIORITY in kinds }?.find(claimed)?.let { m ->
            priority = Priority.valueOf(m.groupValues[1].uppercase())
            tokens += QuickAddToken(TokenKind.PRIORITY, m.groupValues[1].lowercase().replaceFirstChar(Char::uppercase))
            claim(m)
        }

        var project: String? = null
        PROJECT.takeIf { TokenKind.PROJECT in kinds }?.find(claimed)?.let { m ->
            project = m.groupValues[1]
            tokens += QuickAddToken(TokenKind.PROJECT, m.groupValues[1])
            claim(m)
        }

        var due: ZonedDateTime? = null
        var timeSpecified = false
        val now = ZonedDateTime.now(clock)
        val dayMatch = if (TokenKind.DAY in kinds) DAY.find(claimed) else null
        if (dayMatch != null) {
            val time = timeOf(dayMatch.groupValues[2], dayMatch.groupValues[3], dayMatch.groupValues[4])
            due = resolveDay(dayMatch.groupValues[1].lowercase(), time, now)
            timeSpecified = time != null
            tokens += QuickAddToken(TokenKind.DAY, dayMatch.value.trim())
            claim(dayMatch)
        } else if (TokenKind.DAY in kinds) {
            TIME.find(claimed)?.let { m ->
                timeOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { time ->
                    val today = now.with(time)
                    due = if (today.isAfter(now)) today else today.plusDays(1)
                    timeSpecified = true
                    tokens += QuickAddToken(TokenKind.DAY, m.value.trim())
                    claim(m)
                }
            }
        }

        val title = spans.sortedByDescending { it.first }
            .fold(StringBuilder(input)) { sb, r -> sb.replace(r.first, r.last + 1, " ") }
            .toString().replace(Regex("\\s+"), " ").trim()

        return QuickAddResult(title, due, project, priority, repeat, tokens, timeSpecified)
    }

    private fun timeOf(hour: String, minute: String, meridiem: String): LocalTime? {
        if (hour.isEmpty() || meridiem.isEmpty()) return null
        val h = hour.toInt()
        val m = minute.ifEmpty { "0" }.toInt()
        if (h !in 1..12 || m !in 0..59) return null
        val h24 = (h % 12) + if (meridiem.equals("pm", ignoreCase = true)) 12 else 0
        return LocalTime.of(h24, m)
    }

    private fun resolveDay(word: String, time: LocalTime?, now: ZonedDateTime): ZonedDateTime {
        val today: LocalDate = now.toLocalDate()
        val (date, defaultTime) = when (word) {
            "today" -> today to DEFAULT_TIME
            "tonight" -> today to TONIGHT_TIME
            "tomorrow" -> today.plusDays(1) to DEFAULT_TIME
            else -> {
                val target = weekday(word)
                var ahead = (target.value - today.dayOfWeek.value + 7) % 7
                if (ahead == 0) ahead = 7 // the same weekday means next week
                today.plusDays(ahead.toLong()) to DEFAULT_TIME
            }
        }
        return date.atTime(time ?: defaultTime).atZone(now.zone)
    }

    private fun weekday(word: String): DayOfWeek = when (word.take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
        val TONIGHT_TIME: LocalTime = LocalTime.of(20, 0)

        val REPEAT = Regex("""\bevery\s+(?:\d+\s+)?[a-z]+\b""", RegexOption.IGNORE_CASE)
        val PRIORITY = Regex("""!(urgent|high|normal|low)\b""", RegexOption.IGNORE_CASE)
        val PROJECT = Regex("""#(\w+)""")
        val DAY = Regex(
            """\b(today|tomorrow|tonight|mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:rs(?:day)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b(?:\s+(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s?(am|pm)\b)?""",
            RegexOption.IGNORE_CASE,
        )
        val TIME = Regex("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s?(am|pm)\b""", RegexOption.IGNORE_CASE)
    }
}
