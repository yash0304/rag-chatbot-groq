package com.mindquest.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * How often a recurring mission comes round — daily through yearly.
 *
 * Everything here works in whole calendar periods rather than a fixed number of days,
 * because that is what people mean. "Monthly" is the 1st of each month, not every thirty
 * days; a nudge that drifts two days per quarter stops being a monthly nudge by summer.
 * A period is identified by an index that increases by exactly one each time it turns over,
 * which makes "have I done this one yet" and "is the streak still alive" the same question
 * asked of two numbers.
 */
object Cadences {

    data class Cadence(
        val id: String,
        val label: String,
        /** What one period is called, for "3 months to go" and "2-month streak". */
        val unit: String,
    )

    val all = listOf(
        Cadence("daily", "daily", "day"),
        Cadence("weekdays", "weekdays", "day"),
        Cadence("weekly", "weekly", "week"),
        Cadence("monthly", "monthly", "month"),
        Cadence("quarterly", "quarterly", "quarter"),
        Cadence("halfyearly", "half-yearly", "half-year"),
        Cadence("yearly", "yearly", "year"),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String?): Cadence = byId[id] ?: byId.getValue("daily")

    /**
     * Which period a date falls in. Consecutive periods differ by exactly one, so two dates
     * are in the same period when their indices match and in neighbouring ones when they
     * differ by one — whatever the cadence.
     */
    fun periodIndex(cadence: String, date: LocalDate): Long = when (cadence) {
        // Epoch day 0 was a Thursday; +3 moves the boundary onto Monday.
        "weekly" -> (date.toEpochDay() + 3).floorDiv(7)
        "monthly" -> date.year * 12L + (date.monthValue - 1)
        "quarterly" -> date.year * 4L + (date.monthValue - 1) / 3
        "halfyearly" -> date.year * 2L + (date.monthValue - 1) / 6
        "yearly" -> date.year.toLong()
        else -> date.toEpochDay() // daily, weekdays
    }

    /**
     * Does a check-in now continue the streak that last ran in [lastPeriod]?
     *
     * Weekdays are the exception: Friday to Monday is a three-day gap and obviously still a
     * streak, so they tolerate the weekend rather than counting it as a miss.
     */
    fun advancesStreak(cadence: String, lastPeriod: Long, thisPeriod: Long): Boolean {
        val gap = thisPeriod - lastPeriod
        return if (cadence == "weekdays") gap in 1..3 else gap == 1L
    }

    /** The first day of the period [date] falls in — the day the nudge is due. */
    fun periodStart(cadence: String, date: LocalDate): LocalDate = when (cadence) {
        "weekly" -> date.with(DayOfWeek.MONDAY)
        "monthly" -> date.withDayOfMonth(1)
        "quarterly" -> LocalDate.of(date.year, (date.monthValue - 1) / 3 * 3 + 1, 1)
        "halfyearly" -> LocalDate.of(date.year, (date.monthValue - 1) / 6 * 6 + 1, 1)
        "yearly" -> LocalDate.of(date.year, 1, 1)
        else -> date
    }

    /** The first day of the period after the one [date] falls in. */
    fun nextPeriodStart(cadence: String, date: LocalDate): LocalDate {
        val start = periodStart(cadence, date)
        return when (cadence) {
            "weekly" -> start.plusWeeks(1)
            "monthly" -> start.plusMonths(1)
            "quarterly" -> start.plusMonths(3)
            "halfyearly" -> start.plusMonths(6)
            "yearly" -> start.plusYears(1)
            "weekdays" -> {
                var d = start.plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
                    d = d.plusDays(1)
                }
                d
            }
            else -> start.plusDays(1)
        }
    }

    /**
     * When the next nudge should fire: the current period's due day at [minuteOfDay] if that
     * moment is still ahead, otherwise the next period's. Setting a monthly reminder on the
     * 1st at 9am while it is still 8am therefore fires this morning, not in five weeks.
     */
    fun nextFireAt(
        cadence: String,
        minuteOfDay: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var candidate = LocalDateTime.of(periodStart(cadence, now.toLocalDate()), time)
        if (!candidate.isAfter(now)) {
            candidate = LocalDateTime.of(nextPeriodStart(cadence, now.toLocalDate()), time)
        }
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun millisUntilNextFire(
        cadence: String,
        minuteOfDay: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long = (nextFireAt(cadence, minuteOfDay, now) - System.currentTimeMillis()).coerceAtLeast(1_000L)

    /** "Mar 2027" — a target date is a month, not an appointment, so it reads as one. */
    fun formatTarget(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM yyyy"))
    }.getOrDefault(isoDate)

    /**
     * "5 months to go", "this month", "overdue" — the part of a goal reminder that actually
     * applies pressure. Counted in the cadence's own unit so a monthly mission counts months.
     */
    fun timeLeft(cadence: String, isoTarget: String, today: LocalDate = LocalDate.now()): String? {
        val target = runCatching { LocalDate.parse(isoTarget) }.getOrNull() ?: return null
        val periods = periodIndex(cadence, target) - periodIndex(cadence, today)
        val unit = of(cadence).unit
        return when {
            periods < 0 -> "target date passed"
            periods == 0L -> "this is the last $unit"
            periods == 1L -> "1 $unit to go"
            else -> "$periods ${unit}s to go"
        }
    }

    /** Whole days until the target, for the plainer "by March 2027" line on a card. */
    fun daysUntil(isoTarget: String, today: LocalDate = LocalDate.now()): Long? =
        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(isoTarget)) }.getOrNull()
}
