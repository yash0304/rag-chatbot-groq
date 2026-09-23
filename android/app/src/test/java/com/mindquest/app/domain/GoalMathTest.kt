package com.mindquest.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMathTest {

    /** Wednesday 23 September 2026; March 2027 is six months on. */
    private val today = LocalDate.of(2026, 9, 23)
    private val march2027 = LocalDate.of(2027, 3, 31)

    @Test fun weightIsMeasuredFromTheFirstWeighIn() {
        val s = GoalMath.status(80.0, "kg", march2027, listOf(86.0, 84.0), today)
        assertEquals(86.0, s.start!!, 0.0)
        assertEquals(84.0, s.latest!!, 0.0)
        assertEquals(1f / 3f, s.fraction!!, 1e-4f)
        assertEquals(-4.0, s.remaining!!, 1e-9)
        assertEquals(6L, s.monthsLeft)
        // Four kilos over this month and the six after it.
        assertEquals(-4.0 / 7, s.perMonth!!, 1e-9)
        assertEquals("lose 0.57 kg a month", GoalMath.describePace(s.perMonth!!, "kg"))
        assertFalse(s.reached)
    }

    @Test fun moneyIsMeasuredFromZero() {
        val s = GoalMath.status(1e7, "₹", march2027, listOf(1.2e6), today)
        assertEquals(0.12f, s.fraction!!, 1e-4f)
        assertEquals("₹88 lakh to go", GoalMath.describeRemaining(s.remaining!!, "₹"))
        assertEquals("₹12.57 lakh a month", GoalMath.describePace(s.perMonth!!, "₹"))
    }

    @Test fun moneyWithNoReadingsYetStillHasAPace() {
        val s = GoalMath.status(1e7, "₹", march2027, emptyList(), today)
        assertEquals(0f, s.fraction!!, 0f)
        assertEquals(1e7 / 7, s.perMonth!!, 1e-6)
    }

    @Test fun weightWithNoReadingsWaitsForTheFirst() {
        val s = GoalMath.status(80.0, "kg", march2027, emptyList(), today)
        assertNull(s.fraction)
        assertNull(s.perMonth)
    }

    @Test fun reachingTheTargetEndsIt() {
        val s = GoalMath.status(80.0, "kg", march2027, listOf(86.0, 79.8), today)
        assertTrue(s.reached)
        assertEquals(1f, s.fraction!!, 0f)
        assertNull(s.perMonth)
    }

    @Test fun directionComesFromTheStart() {
        assertTrue(GoalMath.isReached(65.0, "kg", start = 60.0, latest = 65.2))
        assertFalse(GoalMath.isReached(65.0, "kg", start = 60.0, latest = 64.0))
        assertTrue(GoalMath.isReached(80.0, "kg", start = 90.0, latest = 79.5))
    }
}
