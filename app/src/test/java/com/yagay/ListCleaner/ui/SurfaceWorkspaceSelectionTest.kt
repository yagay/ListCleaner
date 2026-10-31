package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.runtime.ActiveNotificationRow
import com.yagay.ListCleaner.runtime.RegisteredChannelRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** List operators and notification categories must behave like Entry/Root cleanup. */
class SurfaceWorkspaceSelectionTest {
    private val rows = listOf(
        ActiveNotificationRow("a", "com.example.chat", "messages", 0, false, "msg"),
        ActiveNotificationRow("b", "com.example.chat", "promotions", 0, false, "promo"),
        ActiveNotificationRow("c", "com.example.mail", "inbox", 0, false, "email"),
        ActiveNotificationRow("d", "com.example.phone", "calls", 0, true, "call"),
    )

    @Test fun groupByAppAndChannelWithoutDroppingNotificationRows() {
        val apps = groupActiveNotifications(rows, ActiveNotificationGrouping.APPLICATION)
        assertEquals(3, apps.size)
        assertEquals(2, apps.first { it.key == "com.example.chat" }.rows.size)
        val channels = groupActiveNotifications(rows, ActiveNotificationGrouping.CHANNEL)
        assertEquals(4, channels.size)
        assertEquals(4, channels.flatMap { it.rows }.size)
    }

    @Test fun platformCategoryGivesDistinctEmailMessagePromotionAndCallGroups() {
        val categories = groupActiveNotifications(rows, ActiveNotificationGrouping.CATEGORY)
        assertEquals(setOf("msg", "promo", "email", "call"),
            categories.map { it.key }.toSet())
        assertTrue(categories.first { it.key == "call" }.rows.all { it.protected })
    }

    @Test fun allViewKeepsIndividualNotificationKeys() {
        val individual = groupActiveNotifications(rows, ActiveNotificationGrouping.ALL)
        assertEquals(rows.map { it.key }, individual.map { it.key })
    }

    @Test fun savedUnverifiedChannelsRemainEditableAndBulkSelectable() {
        val pkg = "com.example.chat"
        val present = RegisteredChannelRow(pkg, "messages", "Messages", 3)
        val merged = notificationRuleChannelRows(
            pkg, listOf(present),
            hidden = setOf("$pkg|messages", "$pkg|legacy"),
            locked = setOf("$pkg|legacy", "$pkg|admin"),
        )
        assertEquals(listOf("messages", "admin", "legacy"), merged.map { it.channelId })
        assertEquals(merged.size, merged.map { it.channelId }.distinct().size)
        assertTrue(notificationRuleChannelRows("com.example.other", emptyList(),
            setOf("$pkg|legacy"), emptySet()).isEmpty())
    }

    @Test fun sharedSurfaceFilterMatchesMainCleanupFiltering() {
        assertTrue(surfaceFilterAllows(UiFilter.ALL, false, false))
        assertTrue(surfaceFilterAllows(UiFilter.SHOW_SELECTED, true, false))
        assertTrue(surfaceFilterAllows(UiFilter.HIDE_SELECTED, false, false))
        assertTrue(surfaceFilterAllows(UiFilter.LOCKED, false, true))
        assertFalse(surfaceFilterAllows(UiFilter.LOCKED, true, false))
        assertFalse(surfaceFilterAllows(UiFilter.SHOW_SELECTED, false, false))
    }
}
