package com.mindquest.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cadences decide when a mission nudge fires and whether a streak survives. Both fail
 * silently when wrong — a monthly reminder that drifts off the 1st, a streak that resets
 * for no reason — so the calendar rules are pinned down here.
 */
class CadencesTest {

    private val zone = ZoneId.systemDefault()
    private fun at(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    // ---- periods ----

    @Test fun aMonthIsOnePeriodFromFirstToLast() {
        val sep1 = Cadences.periodIndex("monthly", LocalDate.of(2026, 9, 1))
        val sep30 = Cadences.periodIndex("monthly", LocalDate.of(2026, 9, 30))
        val oct1 = Cadences.periodIndex("monthly", LocalDate.of(2026, 10, 1))
        assertEquals(sep1, sep30)
        assertEquals(sep1 + 1, oct1)
    }

    @Test fun weeksRunMondayToSunday() {
        val mon = Cadences.periodIndex("weekly", LocalDate.of(2026, 9, 7))
        val sun = Cadences.periodIndex("weekly", LocalDate.of(2026, 9, 13))
        val nextMon = Cadences.periodIndex("weekly", LocalDate.of(2026, 9, 14))
        assertEquals(mon, sun)
        assertEquals(mon + 1, nextMon)
    }

    @Test fun decemberToJanuaryIsOneStep() {
        for (c in listOf("monthly", "quarterly", "halfyearly", "yearly")) {
            val dec = Cadences.periodIndex(c, LocalDate.of(2026, 12, 31))
            val jan = Cadences.periodIndex(c, LocalDate.of(2027, 1, 1))
            assertEquals(c, dec + 1, jan)
        }
    }

    // ---- streaks ----

    @Test fun weekdaysForgiveTheWeekend() {
        val fri = Cadences.periodIndex("weekdays", LocalDate.of(2026, 9, 11))
        val mon = Cadences.periodIndex("weekdays", LocalDate.of(2026, 9, 14))
        assertTrue(Cadences.advancesStreak("weekdays", fri, mon))
        assertFalse(Cadences.advancesStreak("daily", fri, mon))
    }

    @Test fun monthlyStreakCountsMonthsNotDays() {
        // Done on the 1st of September and the 31st of October: sixty days apart, but
        // consecutive months, so the streak continues.
        val sep = Cadences.periodIndex("monthly", LocalDate.of(2026, 9, 1))
        val oct = Cadences.periodIndex("monthly", LocalDate.of(2026, 10, 31))
        val nov = Cadences.periodIndex("monthly", LocalDate.of(2026, 11, 15))
        assertEquals(4, GameEngine.computeStreak("monthly", 3, sep, oct))
        assertEquals(1, GameEngine.computeStreak("monthly", 3, sep, nov)) // skipped October
        assertEquals(3, GameEngine.computeStreak("monthly", 3, sep, sep)) // same month
        assertEquals(1, GameEngine.computeStreak("monthly", 0, null, sep)) // first ever
    }

    // ---- when the nudge fires ----

    /** Thursday 10 September 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 10, 10, 0)

    @Test fun monthlyFiresOnTheFirst() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), at(Cadences.nextFireAt("monthly", 9 * 60, now)))
    }

    @Test fun onTheFirstBeforeTheTimeItFiresToday() {
        val morning = LocalDateTime.of(2026, 10, 1, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), at(Cadences.nextFireAt("monthly", 9 * 60, morning)))
    }

    @Test fun longerCadencesLandOnTheirFirstDay() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), at(Cadences.nextFireAt("quarterly", 9 * 60, now)))
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), at(Cadences.nextFireAt("halfyearly", 9 * 60, now)))
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), at(Cadences.nextFireAt("yearly", 9 * 60, now)))
    }

    @Test fun weeklyFiresOnMonday() {
        assertEquals(LocalDateTime.of(2026, 9, 14, 9, 0), at(Cadences.nextFireAt("weekly", 9 * 60, now)))
    }

    @Test fun dailyLaterTodayOrTomorrow() {
        assertEquals(LocalDateTime.of(2026, 9, 10, 21, 0), at(Cadences.nextFireAt("daily", 21 * 60, now)))
        assertEquals(LocalDateTime.of(2026, 9, 11, 9, 0), at(Cadences.nextFireAt("daily", 9 * 60, now)))
    }

    @Test fun weekdaysSkipTheWeekend() {
        val fridayEvening = LocalDateTime.of(2026, 9, 11, 22, 0)
        assertEquals(LocalDateTime.of(2026, 9, 14, 9, 0), at(Cadences.nextFireAt("weekdays", 9 * 60, fridayEvening)))
    }

    // ---- half-monthly: the 1st and the 16th ----

    @Test fun halfMonthsSplitOnTheSixteenth() {
        val sep15 = Cadences.periodIndex("halfmonthly", LocalDate.of(2026, 9, 15))
        val sep16 = Cadences.periodIndex("halfmonthly", LocalDate.of(2026, 9, 16))
        val sep30 = Cadences.periodIndex("halfmonthly", LocalDate.of(2026, 9, 30))
        val oct1 = Cadences.periodIndex("halfmonthly", LocalDate.of(2026, 10, 1))
        assertEquals(sep15 + 1, sep16)
        assertEquals(sep16, sep30)
        assertEquals(sep30 + 1, oct1)
    }

    @Test fun halfMonthlyFiresOnTheFirstAndSixteenth() {
        // Thursday 10 September → the 16th; from the 16th evening → 1 October.
        assertEquals(LocalDateTime.of(2026, 9, 16, 9, 0), at(Cadences.nextFireAt("halfmonthly", 9 * 60, now)))
        val sixteenthEvening = LocalDateTime.of(2026, 9, 16, 20, 0)
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), at(Cadences.nextFireAt("halfmonthly", 9 * 60, sixteenthEvening)))
    }

    @Test fun halfMonthlyNotesAlternateFifteenDaysApart() {
        val fifth = LocalDateTime.of(2026, 9, 5, 9, 0)
        val twentieth = Cadences.advance("halfmonthly", fifth)
        assertEquals(LocalDateTime.of(2026, 9, 20, 9, 0), twentieth)
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), Cadences.advance("halfmonthly", twentieth))
    }

    // ---- repeating notes ----

    @Test fun rentStaysOnTheFifthWhenPaidLate() {
        val due = millis(LocalDateTime.of(2026, 9, 5, 9, 0))
        val paidOnSeventh = millis(LocalDateTime.of(2026, 9, 7, 12, 0))
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), at(Cadences.nextOccurrence("monthly", due, paidOnSeventh)))
    }

    @Test fun tickedWeeksLateItSkipsAheadInsteadOfComingBackOverdue() {
        val due = millis(LocalDateTime.of(2026, 9, 5, 9, 0))
        val muchLater = millis(LocalDateTime.of(2026, 12, 20, 9, 0))
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), at(Cadences.nextOccurrence("monthly", due, muchLater)))
    }

    @Test fun theThirtyFirstClampsToTheMonthsEnd() {
        assertEquals(
            LocalDateTime.of(2026, 2, 28, 9, 0),
            Cadences.advance("monthly", LocalDateTime.of(2026, 1, 31, 9, 0)),
        )
    }

    // ---- goal countdown ----

    @Test fun monthsToGo() {
        val today = LocalDate.of(2026, 9, 23)
        assertEquals("6 months to go", Cadences.timeLeft("monthly", "2027-03-01", today))
        assertEquals("this is the last month", Cadences.timeLeft("monthly", "2026-09-30", today))
        assertEquals("target date passed", Cadences.timeLeft("monthly", "2026-08-01", today))
    }
}
