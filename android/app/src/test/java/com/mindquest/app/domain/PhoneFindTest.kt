package com.mindquest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A number in a thought is tapped to call; anything else that looks numeric must not be. */
class PhoneFindTest {

    @Test fun aMobileWrittenWithASpace() {
        val p = PhoneFind.find("puncture wala at 98 sector 98100 12345")
        assertEquals(listOf("9810012345"), p.map { it.dial })
    }

    @Test fun countryCodeAndDashes() {
        assertEquals(listOf("+919810012345"), PhoneFind.find("Theos +91-98100-12345").map { it.dial })
    }

    @Test fun aLandlineWithItsStdCode() {
        assertEquals(listOf("01204567890"), PhoneFind.find("clinic 0120 4567890").map { it.dial })
    }

    @Test fun shortNumbersAreNotPhones() {
        assertEquals(emptyList<String>(), PhoneFind.find("HDFC ATM on sector 104, 5000 steps, ₹2500").map { it.dial })
    }

    @Test fun theMapSearchLeavesTheNumberOut() {
        assertEquals("puncture wala at 98 sector", PhoneFind.withoutPhones("puncture wala at 98 sector 98100 12345"))
    }
}
