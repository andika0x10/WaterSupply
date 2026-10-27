package com.andika.watersupply

import com.andika.watersupply.data.*
import com.andika.watersupply.reminders.ReminderSchedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderScheduleTest {
    private val now = ZonedDateTime.of(2026, 10, 27, 10, 15, 0, 0, ZoneId.of("Asia/Jakarta"))
    @Test fun disabledHasNoAlarm() { assertNull(ReminderSchedule.next(Preferences(), now, false)) }
    @Test fun intervalStaysAnchoredAfterRecording() {
        assertEquals(now.withHour(11).withMinute(0), ReminderSchedule.next(Preferences(enabled = true), now, false))
    }
    @Test fun reachedTargetSkipsToday() {
        assertEquals(now.plusDays(1).withHour(8).withMinute(0), ReminderSchedule.next(Preferences(enabled = true), now, true))
    }
    @Test fun customTimesAreSortedAndPastSlotsSkipped() {
        val p = Preferences(enabled = true, mode = ReminderMode.CUSTOM, times = listOf(960, 540, 750, 750))
        assertEquals(now.withHour(12).withMinute(30), ReminderSchedule.next(p, now, false))
        assertEquals(now.plusDays(1).withHour(9).withMinute(0), ReminderSchedule.next(p, now.withHour(23), false))
    }
    @Test fun exactBoundaryIsNotRepeated() {
        assertEquals(now.withHour(12).withMinute(0), ReminderSchedule.next(Preferences(enabled = true), now.withHour(11).withMinute(0), false))
    }
    @Test fun scheduleUsesLocalZoneAcrossDaylightSaving() {
        val paris = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, ZoneId.of("Europe/Paris"))
        val next = ReminderSchedule.next(Preferences(enabled = true), paris, false)!!
        assertEquals(8, next.hour)
        assertEquals(ZoneOffset.ofHours(2), next.offset)
    }
    @Test fun targetChangeDoesNotRewritePreviousDays() {
        val date = LocalDate.of(2026, 10, 27)
        val targets = listOf(DailyTarget("2026-10-26", 16), DailyTarget("2026-10-27", 20))
        assertEquals(16, targetFor(targets, date.minusDays(1)))
        assertEquals(20, targetFor(targets, date))
        assertEquals(20, targetFor(targets, date.plusDays(1)))
        assertEquals(16, targetFor(targets, date.minusDays(2)))
    }
    @Test fun halfGlassesAndOverTargetRemainAccurate() {
        assertEquals("0,5", glasses(1))
        assertEquals("1,5", glasses(3))
        val date = LocalDate.now()
        val state = WaterState(drinks = List(9) { Drink(it.toLong(), 0, date.toString(), 2) }, date = date)
        assertEquals(18, state.total)
        assertEquals(16, state.target)
    }
    @Test fun lateAlarmOutsideWindowIsSkipped() {
        assertFalse(ReminderSchedule.isActive(Preferences(enabled = true), now.withHour(23)))
    }
    @Test fun beforeStartAndAfterEndUseCorrectDay() {
        val p = Preferences(enabled = true)
        assertEquals(now.withHour(8).withMinute(0), ReminderSchedule.next(p, now.withHour(7), false))
        assertEquals(now.plusDays(1).withHour(8).withMinute(0), ReminderSchedule.next(p, now.withHour(22), false))
    }
    @Test fun loweringProgressBelowTargetResumesRemainingSlots() {
        val p = Preferences(enabled = true)
        val paused = ReminderSchedule.next(p, now, true)!!
        val resumed = ReminderSchedule.next(p, now, false)!!
        assertTrue(resumed.isBefore(paused))
        assertEquals(now.toLocalDate(), resumed.toLocalDate())
    }
}
