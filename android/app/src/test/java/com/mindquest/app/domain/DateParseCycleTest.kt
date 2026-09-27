package com.mindquest.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checklist errands said the way people say them: a date, a day of the month, or a point on
 * a cycle. A reminder on the wrong half of a six-month cycle is six months late.
 */
class DateParseCycleTest {

    /** Sunday 27 September 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 27, 10, 0)

    private fun due(p: DateParse.Parsed): LocalDateTime? =
        p.dueAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

    @Test fun aOneOffByADate() {
        val p = DateParse.parse("milk by 28th September", now)
        assertEquals("milk", p.text)
        assertEquals(LocalDateTime.of(2026, 9, 28, 9, 0), due(p))
        assertEquals(null, p.repeat)
    }

    @Test fun everyMonthAtAPlainNumberIsThatDay() {
        val p = DateParse.parse("haircut every month at 15", now)
        assertEquals("haircut", p.text)
        assertEquals("monthly", p.repeat)
        assertEquals(LocalDateTime.of(2026, 10, 15, 9, 0), due(p))
    }

    @Test fun aClockTimeIsStillATime() {
        val p = DateParse.parse("pay maid every month on 1st at 7pm", now)
        assertEquals("monthly", p.repeat)
        assertEquals(LocalDateTime.of(2026, 10, 1, 19, 0), due(p))
    }

    @Test fun twoPointsOnASixMonthCycleTakeTheNextOne() {
        val p = DateParse.parse("health checkup every 6 months july 5 then january 5", now)
        assertEquals("health checkup", p.text)
        assertEquals("halfyearly", p.repeat)
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), due(p))
    }

    @Test fun onePastPointOnACycleFindsItsNextTurn() {
        val p = DateParse.parse("health checkup every 6 months from 5th july", now)
        assertEquals("health checkup", p.text)
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), due(p))
    }

    @Test fun yearlyStaysOnItsDay() {
        val p = DateParse.parse("renew car insurance every year on 3rd march", now)
        assertEquals("yearly", p.repeat)
        assertEquals(LocalDateTime.of(2027, 3, 3, 9, 0), due(p))
    }

    @Test fun startingNamesTheFirstOne() {
        val p = DateParse.parse("sip every month starting 5th january", now)
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), due(p))
    }
}
