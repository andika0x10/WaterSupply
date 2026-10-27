package com.andika.watersupply.reminders

import com.andika.watersupply.data.Preferences
import com.andika.watersupply.data.ReminderMode
import java.time.*

object ReminderSchedule {
    fun next(p: Preferences, now: ZonedDateTime, targetReached: Boolean): ZonedDateTime? {
        if (!p.enabled) return null
        val slots = if (p.mode == ReminderMode.CUSTOM) p.times.distinct().sorted()
            else (p.start..p.end step p.interval).toList()
        for (offset in 0L..2L) {
            if (offset == 0L && targetReached) continue
            val date = now.toLocalDate().plusDays(offset)
            for (minute in slots) {
                val candidate = date.atTime(minute / 60, minute % 60).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }
    fun isActive(p: Preferences, now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        return if (p.mode == ReminderMode.INTERVAL) minute in p.start..p.end
            else p.times.any { minute >= it && minute - it <= 60 }
    }
}
