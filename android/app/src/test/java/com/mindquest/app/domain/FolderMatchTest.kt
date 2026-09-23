package com.mindquest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderMatchTest {

    private val folders = listOf("Tuesday vegetable market", "Diwali gifts", "Car service")

    @Test fun vegetablesFindsTheVegetableMarket() {
        assertEquals(0, FolderMatch.best("buy vegetables by Thursday", folders))
    }

    @Test fun pluralsMatchEitherWay() {
        assertEquals(1, FolderMatch.best("gift for Riya", folders))
    }

    @Test fun daysNeverCount() {
        // Sharing "Tuesday" with the market folder isn't a reason to file there.
        assertNull(FolderMatch.best("call the plumber on Tuesday", folders))
    }

    @Test fun nothingSharedMeansNoFolder() {
        assertNull(FolderMatch.best("pay electricity bill", folders))
        assertNull(FolderMatch.best("", folders))
    }

    @Test fun shortWordsDoNotMatchLongOnes() {
        // "car" is too short to count, and "cart" is not "car" with an ending.
        assertNull(FolderMatch.best("add milk to cart", folders))
    }
}
