package com.monaflow.app.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimeTest {

    @Test
    fun `next occurrence later today`() {
        val now = LocalDateTime.of(2026, 10, 26, 8, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 9, 0),
            ReminderTime.nextOccurrence(9 * 60, now),
        )
    }

    @Test
    fun `next occurrence rolls to tomorrow when time passed`() {
        val now = LocalDateTime.of(2026, 10, 26, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 9, 0),
            ReminderTime.nextOccurrence(9 * 60, now),
        )
    }

    @Test
    fun `next occurrence crosses month and year`() {
        val now = LocalDateTime.of(2026, 12, 31, 23, 30)
        assertEquals(
            LocalDateTime.of(2027, 1, 1, 0, 0),
            ReminderTime.nextOccurrence(0, now),
        )
    }

    @Test
    fun `exactly now rolls to tomorrow`() {
        val now = LocalDateTime.of(2026, 10, 26, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 9, 0),
            ReminderTime.nextOccurrence(9 * 60, now),
        )
    }

    @Test
    fun `out of range minutes clamped`() {
        val now = LocalDateTime.of(2026, 10, 26, 8, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 23, 59),
            ReminderTime.nextOccurrence(1500, now),
        )
    }

    @Test
    fun `warranty notify at 30 7 1 0 days only`() {
        listOf(30L, 7L, 1L, 0L).forEach { days ->
            assertTrue(ReminderTime.warrantyShouldNotify(days, null, 100))
        }
        assertFalse(ReminderTime.warrantyShouldNotify(15, null, 100))
        assertFalse(ReminderTime.warrantyShouldNotify(30, 100, 100)) // 当天已提醒过
        assertFalse(ReminderTime.warrantyShouldNotify(-5, null, 100)) // 已过期很久不再提醒
    }
}
