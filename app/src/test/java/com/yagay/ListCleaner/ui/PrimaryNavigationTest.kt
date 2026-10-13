package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrimaryNavigationTest {
    @Test fun primaryNavigationHasThreeTabs() {
        assertEquals(
            listOf(Destination.RULES, Destination.PRIORITY, Destination.DASHBOARD),
            primaryDestinations
        )
    }

    @Test fun rootComponentsRemainNestedUnderRules() {
        assertFalse(primaryDestinations.contains(Destination.TILES))
        assertEquals(Destination.RULES, Destination.TILES.primaryTab())
    }
}
