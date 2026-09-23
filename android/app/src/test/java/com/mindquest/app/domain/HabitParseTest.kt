package com.mindquest.app.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Habit or reminder decides whether a line gets a streak and a quiet-when-done nudge, or a
 * reminder on its own day. Getting it wrong either way is visible and annoying — a rent
 * reminder that fires on the 1st, or a daily walk with no streak.
 */
class HabitParseTest {

    /** Thursday 10 September 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 10, 10, 0)

    @Test fun everyDayAtATimeIsAHabitWithANudge() {
        val h = HabitParse.detect("walk 5000 steps every day at 9pm", now)!!
        assertEquals("Walk 5000 steps", h.title)
        assertEquals("daily", h.cadence)
        assertEquals(21 * 60, h.minuteOfDay)
    }

    @Test fun noTimeMeansNoNudge() {
        val h = HabitParse.detect("take vitamins daily", now)!!
        assertEquals("Take vitamins", h.title)
        assertNull(h.minuteOfDay)
    }

    @Test fun weekdaysAndPlainWeeklyAreHabits() {
        assertEquals("weekdays", HabitParse.detect("stand-up stretch on weekdays", now)!!.cadence)
        assertEquals("weekly", HabitParse.detect("deep clean the kitchen every week", now)!!.cadence)
    }

    @Test fun aNamedDayIsAReminderNotAHabit() {
        assertNull(HabitParse.detect("call mom every sunday", now))
        assertNull(HabitParse.detect("pay rent on the 5th every month", now))
        assertNull(HabitParse.detect("pay the maid twice a month on the 1st", now))
    }

    @Test fun describingWordsAndPlainLinesAreNotHabits() {
        assertNull(HabitParse.detect("daily standup notes", now))
        assertNull(HabitParse.detect("buy vegetables by Thursday", now))
    }
}
