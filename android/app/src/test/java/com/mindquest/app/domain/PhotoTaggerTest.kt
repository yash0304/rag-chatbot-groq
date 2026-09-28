package com.mindquest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTaggerTest {

    @Test fun aShopReceipt() {
        val tags = PhotoTags.tagsFromText("DMart\nMilk 2 x 28.00\nGrand Total ₹ 312.00\nPaid UPI")
        assertTrue("receipt" in tags)
    }

    @Test fun anElectricityBill() {
        val tags = PhotoTags.tagsFromText("BSES Rajdhani\nAmount due Rs 2,340\nDue date 12/10/2026")
        assertTrue("bill" in tags)
    }

    @Test fun aVisitingCard() {
        assertEquals(listOf("phone number"), PhotoTags.tagsFromText("Sharma Tyres\nSector 98\n98100 12345"))
    }

    @Test fun aPhotoWithNoWords() {
        assertEquals(emptyList<String>(), PhotoTags.tagsFromText(""))
    }
}
