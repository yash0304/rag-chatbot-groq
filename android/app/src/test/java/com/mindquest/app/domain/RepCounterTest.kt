package com.mindquest.app.domain

import com.mindquest.app.domain.RepCounter.Exercise
import com.mindquest.app.domain.RepCounter.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class RepCounterTest {

    private fun run(e: Exercise, angles: List<Float?>, stepMs: Long = 200): Int {
        val c = RepCounter(e)
        angles.forEachIndexed { i, a -> c.feed(a, i * stepMs) }
        return c.count
    }

    @Test fun twoSquats() {
        assertEquals(2, run(Exercise.Squats, listOf(175f, 140f, 95f, 80f, 120f, 170f, 150f, 90f, 160f)))
    }

    @Test fun hoveringAtTheLineIsNotARep() {
        // Wobbling between 110° and 130° never reaches down (<100) or back up (>155).
        assertEquals(0, run(Exercise.Squats, listOf(170f, 130f, 110f, 125f, 112f, 128f, 115f)))
    }

    @Test fun halfASquatDoesntCount() {
        assertEquals(0, run(Exercise.Squats, listOf(175f, 90f, 130f, 95f, 140f)))
    }

    @Test fun jumpingJacksGoTheOtherWay() {
        assertEquals(2, run(Exercise.JumpingJacks, listOf(20f, 90f, 160f, 30f, 170f, 25f)))
    }

    @Test fun framesWithoutAPersonAreSkipped() {
        assertEquals(1, run(Exercise.PushUps, listOf(170f, null, 80f, null, null, 165f)))
    }

    @Test fun tooFastIsJitter() {
        assertEquals(1, run(Exercise.Curls, listOf(165f, 40f, 160f, 40f, 160f), stepMs = 50))
    }

    @Test fun angleOfAStraightAndABentJoint() {
        assertEquals(180f, RepCounter.angle(Point(0f, 0f), Point(1f, 0f), Point(2f, 0f)), 0.01f)
        assertEquals(90f, RepCounter.angle(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f)), 0.01f)
    }
}
