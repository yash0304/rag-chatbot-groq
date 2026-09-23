package com.mindquest.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The date reader decides when a reminder goes off. A wrong answer here is a missed
 * reminder, and nothing on screen would say so — hence tests, pinned to a fixed "now" so
 * they don't depend on the day they happen to run.
 */
class DateParseTest {

    /** Thursday 10 September 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 10, 10, 0)

    private fun due(raw: String): LocalDateTime? =
        DateParse.parse(raw, now).dueAt?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }

    @Test fun dayAndMonthName() {
        val p = DateParse.parse("buy sugar by 21st September", now)
        assertEquals("buy sugar", p.text)
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), due("buy sugar by 21st September"))
    }

    @Test fun aQuantityIsNotMistakenForTheDate() {
        val p = DateParse.parse("buy 2 kg sugar by 21st September", now)
        assertEquals("buy 2 kg sugar", p.text)
        assertEquals(LocalDateTime.of(2026, 9, 21, 9, 0), due("buy 2 kg sugar by 21st September"))
    }

    @Test fun aDateAlreadyPastMeansNextYear() {
        assertEquals(LocalDateTime.of(2027, 9, 1, 9, 0), due("renew insurance on 1st September"))
    }

    @Test fun tomorrowWithATime() {
        val p = DateParse.parse("call mom tomorrow at 5pm", now)
        assertEquals("call mom", p.text)
        assertEquals(LocalDateTime.of(2026, 9, 11, 17, 0), due("call mom tomorrow at 5pm"))
    }

    @Test fun aBareTimeAlreadyPastMeansTomorrow() {
        assertEquals(LocalDateTime.of(2026, 9, 11, 9, 0), due("take vitamins at 9am"))
    }

    @Test fun weekdayIsTheNextOne() {
        assertEquals(LocalDateTime.of(2026, 9, 11, 9, 0), due("meeting on friday"))
        // "On Thursday" said on a Thursday means next week's, not the half-gone today.
        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 0), due("gym on thursday"))
    }

    @Test fun rentOnTheFifthEveryMonth() {
        val p = DateParse.parse("pay rent on the 5th every month", now)
        assertEquals("pay rent", p.text)
        assertEquals("monthly", p.repeat)
        // The 5th of September has gone, so the first one is October's.
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 0), due("pay rent on the 5th every month"))
    }

    @Test fun fifthOfEveryMonthLeavesNoStrayWords() {
        val p = DateParse.parse("pay rent on the 5th of every month", now)
        assertEquals("pay rent", p.text)
        assertEquals("monthly", p.repeat)
    }

    @Test fun aBareDayStillAheadThisMonth() {
        assertEquals(LocalDateTime.of(2026, 9, 25, 9, 0), due("pay the electricity bill on the 25th"))
    }

    @Test fun everySundayIsWeeklyStartingThisSunday() {
        val p = DateParse.parse("call mom every sunday", now)
        assertEquals("call mom", p.text)
        assertEquals("weekly", p.repeat)
        assertEquals(LocalDateTime.of(2026, 9, 13, 9, 0), due("call mom every sunday"))
    }

    @Test fun everyDayAtATime() {
        val p = DateParse.parse("take vitamins every day at 9pm", now)
        assertEquals("take vitamins", p.text)
        assertEquals("daily", p.repeat)
        assertEquals(LocalDateTime.of(2026, 9, 10, 21, 0), due("take vitamins every day at 9pm"))
    }

    @Test fun halfMonthlyIsNotReadAsMonthly() {
        val p = DateParse.parse("weigh in half monthly from the 16th", now)
        assertEquals("halfmonthly", p.repeat)
        assertEquals("halfmonthly", DateParse.parse("pay the maid twice a month on the 1st", now).repeat)
    }

    @Test fun aRepeatWordWithNoDateLeavesTheSentenceAlone() {
        val p = DateParse.parse("daily standup notes", now)
        assertEquals("daily standup notes", p.text)
        assertNull(p.dueAt)
        assertNull(p.repeat)
    }

    @Test fun noDateAtAll() {
        val p = DateParse.parse("buy milk", now)
        assertEquals("buy milk", p.text)
        assertNull(p.dueAt)
    }

    @Test fun inTwoHours() {
        assertEquals(LocalDateTime.of(2026, 9, 10, 12, 0), due("check the oven in 2 hours"))
    }

    @Test fun tonightDefaultsToEvening() {
        assertEquals(LocalDateTime.of(2026, 9, 10, 20, 0), due("water the plants tonight"))
    }
}
