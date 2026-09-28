package com.mindquest.app.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The model's answer is trusted only as far as it checks out. */
class SmartCaptureTest {

    private val today = LocalDate.of(2026, 9, 28)

    @Test fun jsonIsFoundInsideChatter() {
        val reply = "Sure! Here you go:\n```json\n{\"kind\":\"task\",\"text\":\"buy milk {2 packets}\"}\n```"
        assertEquals("{\"kind\":\"task\",\"text\":\"buy milk {2 packets}\"}", SmartCapture.extractJson(reply))
    }

    @Test fun aTaskWithADateAndTime() {
        val r = SmartCapture.parse(
            """{"kind":"task","text":"call Rahul about the car","date":"2026-10-02","time":"18:30","repeat":null}""",
            today,
        )!!
        assertEquals("task", r.kind)
        assertEquals("Call Rahul about the car", r.text)
        assertEquals(LocalDate.of(2026, 10, 2), r.date)
        assertEquals(LocalTime.of(18, 30), r.time)
        assertNull(r.repeat)
    }

    @Test fun aPastDateOrMadeUpCadenceIsDropped() {
        val r = SmartCapture.parse("""{"kind":"task","text":"x","date":"2026-01-01","repeat":"fortnightly"}""", today)!!
        assertNull(r.date)
        assertNull(r.repeat)
    }

    @Test fun aGoalNeedsItsNumberAndDate() {
        assertNull(SmartCapture.parse("""{"kind":"goal","text":"lose weight","target":null}""", today))
        val g = SmartCapture.parse("""{"kind":"goal","text":"reach 80 kg","target":"80 kg","deadline":"2027-03-31"}""", today)!!
        assertEquals("80 kg", g.target)
    }

    @Test fun nonsenseIsNull() {
        assertNull(SmartCapture.parse("I'm not sure what you mean.", today))
        assertNull(SmartCapture.parse("""{"kind":"poem","text":"roses"}""", today))
    }
}
