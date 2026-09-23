package com.mindquest.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deciding "goal or errand" decides where a line lands. A goal read as an errand becomes a
 * note with no progress and no check-ins; an errand read as a goal disappears from the to-do
 * list. Both directions are pinned here, starting with the user's own phrasings.
 */
class GoalParseTest {

    /** Wednesday 23 September 2026. */
    private val today = LocalDate.of(2026, 9, 23)

    private fun goal(text: String) = GoalParse.detect(text, today)

    // ---- the user's own examples ----

    @Test fun eightyKgsByMarch2027() {
        val g = goal("80 kgs by March 2027")!!
        assertEquals("Reach 80 kg", g.title)
        assertEquals(80.0, g.target!!, 0.0)
        assertEquals("kg", g.unit)
        assertEquals(LocalDate.of(2027, 3, 31), g.deadline)
    }

    @Test fun oneCroreInrEarnByMarch2027() {
        val g = goal("1crore inr earn by March 2027")!!
        assertEquals("Earn ₹1 crore", g.title)
        assertEquals(1e7, g.target!!, 0.0)
        assertEquals("₹", g.unit)
        assertEquals(LocalDate.of(2027, 3, 31), g.deadline)
    }

    @Test fun buyVegetablesByThursdayIsAnErrand() {
        assertNull(goal("buy vegetables by Thursday"))
    }

    // ---- errands that look a bit like goals ----

    @Test fun aQuantityWithANearDateIsAnErrand() {
        assertNull(goal("buy 2 kg sugar by 21st September"))
        assertNull(goal("pay 1 lakh rent by 5th October"))
    }

    @Test fun doingVerbsAreErrandsEvenFarOff() {
        assertNull(goal("pay 2 lakh by March 2027"))
        assertNull(goal("call the 5 clients by December"))
    }

    @Test fun noNumberOrNoDateIsNotAGoal() {
        assertNull(goal("get fit by March 2027"))
        assertNull(goal("80 kg"))
    }

    // ---- other ways people say goals ----

    @Test fun loseIsAChangeNotALevel() {
        val g = goal("lose 10 kg by December")!!
        assertEquals("Lose 10 kg", g.title)
        assertNull(g.target)
        assertEquals(-10.0, g.change!!, 0.0)
        assertEquals(LocalDate.of(2026, 12, 31), g.deadline)
    }

    @Test fun lakhsAndRupeeSigns() {
        assertEquals(5e6, goal("save 50 lakh in 2 years")!!.target!!, 0.0)
        assertEquals(1.5e6, goal("₹15,00,000 savings by end of 2027")!!.target!!, 0.0)
        assertEquals(2.5e5, goal("earn rs 2.5 lakh by June")!!.target!!, 0.0)
    }

    @Test fun kOnlyMeansRupeesWithACurrency() {
        val steps = goal("walk 10k steps a day by December")!!
        assertEquals("steps", steps.unit)
        assertEquals(1e4, steps.target!!, 0.0)
        val money = goal("save 50k inr by December")!!
        assertEquals("₹", money.unit)
        assertEquals(5e4, money.target!!, 0.0)
    }

    @Test fun countsAndYearOnlyDeadlines() {
        val g = goal("read 20 books by 2027")!!
        assertEquals("Read 20 books", g.title)
        assertEquals(LocalDate.of(2027, 12, 31), g.deadline)
    }

    @Test fun aMonthWithoutAYearIsTheNextOne() {
        // August has already ended this year, so "by August" means August 2027.
        assertEquals(LocalDate.of(2027, 8, 31), goal("reach 75 kg by August")!!.deadline)
        assertEquals(LocalDate.of(2026, 11, 30), goal("reach 75 kg by November")!!.deadline)
    }

    @Test fun aPreciseFarDateIsStillAGoal() {
        assertEquals(LocalDate.of(2027, 3, 31), goal("reach 75 kg by 31st March 2027")!!.deadline)
        // …but a precise date only a week away is an errand-sized deadline.
        assertNull(goal("reach 75 kg by 30th September 2026"))
    }

    @Test fun byMarchTwentiethNamesADayNotAMonth() {
        assertNull(goal("save 50k rupees by March 20th"))
    }

    // ---- checkpoints: mini goals inside a main one ----

    @Test fun ninetyKgsByOctoberFirstIsACheckpoint() {
        val c = GoalParse.checkpoint("90 kgs by October 1st", today)!!
        assertEquals(90.0, c.value, 0.0)
        assertEquals("kg", c.unit)
        assertEquals(LocalDate.of(2026, 10, 1), c.date)
        // …and too close to be a main goal on its own.
        assertNull(goal("90 kgs by October 1st"))
    }

    @Test fun checkpointsTakeMonthsAndDaysAlike() {
        assertEquals(LocalDate.of(2026, 12, 31), GoalParse.checkpoint("85 kg by December", today)!!.date)
        assertEquals(LocalDate.of(2026, 10, 1), GoalParse.checkpoint("90 kg by 1st October", today)!!.date)
        assertEquals(5e5, GoalParse.checkpoint("5 lakh by Diwali 12th November", today)!!.value, 0.0)
    }

    @Test fun errandsAndChangesAreNotCheckpoints() {
        assertNull(GoalParse.checkpoint("buy 2 kg sugar by Friday", today))
        assertNull(GoalParse.checkpoint("lose 2 kg by Friday", today))
        assertNull(GoalParse.checkpoint("90 kg", today))
    }

    // ---- formatting and reading check-ins ----

    @Test fun rupeesAreWrittenTheIndianWay() {
        assertEquals("₹1 crore", GoalParse.format(1e7, "₹"))
        assertEquals("₹12.5 lakh", GoalParse.format(1.25e6, "₹"))
        assertEquals("₹45,000", GoalParse.format(45_000.0, "₹"))
        assertEquals("80 kg", GoalParse.format(80.0, "kg"))
        assertEquals("84.5 kg", GoalParse.format(84.5, "kg"))
    }

    @Test fun checkInValues() {
        assertEquals(84.5, GoalParse.parseValue("84.5", "kg")!!, 0.0)
        assertEquals(1.2e6, GoalParse.parseValue("12 lakh", "₹")!!, 0.0)
        assertEquals(1.2e7, GoalParse.parseValue("1.2 cr", "₹")!!, 0.0)
        assertEquals(1.5e6, GoalParse.parseValue("₹15,00,000", "₹")!!, 0.0)
        assertNull(GoalParse.parseValue("not yet", "kg"))
    }

    @Test fun goalsDoNotSwallowTheDateReader() {
        // The errand still gets its reminder from DateParse, year included.
        val p = DateParse.parse("renew passport by 21st March 2028", java.time.LocalDateTime.of(2026, 9, 23, 10, 0))
        assertNotNull(p.dueAt)
        assertEquals(
            java.time.LocalDateTime.of(2028, 3, 21, 9, 0),
            java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(p.dueAt!!), java.time.ZoneId.systemDefault()),
        )
    }
}
