package com.mindquest.app.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutMathTest {

    @Test fun boundsCoverOnlyTheSelectedPixels() {
        // 4 × 3, with the object at (1,1) and (2,1).
        val mask = floatArrayOf(
            0f, 0f, 0f, 0f,
            0f, 0.9f, 0.8f, 0f,
            0f, 0.2f, 0f, 0f,
        )
        assertArrayEquals(intArrayOf(1, 1, 2, 1), CutoutMath.bounds(mask, 4, 3))
    }

    @Test fun nothingSelectedIsNoCutout() {
        assertNull(CutoutMath.bounds(FloatArray(12) { 0.1f }, 4, 3))
    }

    @Test fun edgesAreSoft() {
        assertEquals(0, CutoutMath.alpha(0.2f))
        assertEquals(255, CutoutMath.alpha(0.9f))
        assertTrue(CutoutMath.alpha(0.5f) in 100..160)
    }

    @Test fun resampleKeepsTheShape() {
        val small = floatArrayOf(1f, 0f, 0f, 1f) // 2 × 2 diagonal
        val big = CutoutMath.resample(small, 2, 2, 4, 4)
        assertEquals(1f, big[0], 0f)
        assertEquals(0f, big[3], 0f)
        assertEquals(1f, big[15], 0f)
    }

    @Test fun aTapOnATurnedPicture() {
        // A 100 × 20 strip centred at (50, 50), turned 90°: now tall and thin.
        assertTrue(CutoutMath.hits(50f, 90f, 50f, 50f, 100f, 20f, 90f))
        assertFalse(CutoutMath.hits(90f, 50f, 50f, 50f, 100f, 20f, 90f))
        assertTrue(CutoutMath.hits(90f, 50f, 50f, 50f, 100f, 20f, 0f))
    }
}
