package com.geneo.clockoverlay

import android.content.Context

/** One entry in the daily schedule -- either a numbered period ("Period 1") or a
 *  named block ("Diary Checking", "Extra Class"). Times are stored as minutes since
 *  midnight so they're trivial to compare against the current time. */
data class ScheduleSlot(val label: String, val startMinutes: Int, val endMinutes: Int) {
    fun startLabel() = minutesToLabel(startMinutes)
    fun endLabel() = minutesToLabel(endMinutes)

    companion object {
        /** Formats minutes-since-midnight as 4-digit 24-hour time with NO colon,
         *  e.g. 515 -> "0835", 795 -> "1315" -- matches how times are entered here. */
        fun minutesToLabel(total: Int): String {
            val h = total / 60
            val m = total % 60
            return String.format("%02d%02d", h, m)
        }

        /** Parses a 4-digit 24-hour time with no colon (e.g. "0835", "1315") into
         *  minutes since midnight, or null if it's not a valid 4-digit HHMM value. */
        fun parseTimeToMinutes(text: String): Int? {
            val digits = text.trim()
            if (digits.length != 4 || digits.any { !it.isDigit() }) return null
            val h = digits.substring(0, 2).toIntOrNull() ?: return null
            val m = digits.substring(2, 4).toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}

/** Small wrapper around SharedPreferences: overlay position, and the daily schedule
 *  (period 1-8 plus Diary Checking / Extra Class), editable from the Edit Schedule
 *  screen. Same schedule applies every day -- no per-day timetable. */
object Prefs {
    private const val FILE = "geneo_clock_prefs"
    private const val KEY_X = "overlay_x"
    private const val KEY_Y = "overlay_y"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun savePosition(ctx: Context, x: Int, y: Int) {
        prefs(ctx).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun getX(ctx: Context, default: Int) = prefs(ctx).getInt(KEY_X, default)
    fun getY(ctx: Context, default: Int) = prefs(ctx).getInt(KEY_Y, default)

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    // ---------- period reminders on/off ----------
    // Independent of the clock itself -- lets the popup + bell be silenced without
    // stopping the whole overlay. Defaults to on (matches original behavior).

    fun isReminderEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_REMINDER_ENABLED, true)
    fun setReminderEnabled(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()
    }

    // ---------- daily schedule ----------
    // Stored as "label|start|end;label|start|end;...".

    /** The starting schedule: 4 periods before lunch, 4 after, then Diary Checking
     *  and Extra Class -- used the first time the app runs, and restorable any time
     *  from the Edit Schedule screen's "Reset to defaults" button. */
    val DEFAULT_SCHEDULE: List<ScheduleSlot> = listOf(
        ScheduleSlot("Period 1", 8 * 60 + 35, 9 * 60 + 5),     // 0835-0905
        ScheduleSlot("Period 2", 9 * 60 + 5, 9 * 60 + 35),     // 0905-0935
        ScheduleSlot("Period 3", 9 * 60 + 35, 10 * 60 + 5),    // 0935-1005
        ScheduleSlot("Period 4", 10 * 60 + 5, 10 * 60 + 35),   // 1005-1035
        ScheduleSlot("Lunch Break", 10 * 60 + 35, 10 * 60 + 55), // 1035-1055
        ScheduleSlot("Period 5", 10 * 60 + 55, 11 * 60 + 30),  // 1055-1130
        ScheduleSlot("Period 6", 11 * 60 + 30, 12 * 60 + 5),   // 1130-1205
        ScheduleSlot("Period 7", 12 * 60 + 5, 12 * 60 + 40),   // 1205-1240
        ScheduleSlot("Period 8", 12 * 60 + 40, 13 * 60 + 15),  // 1240-1315
        ScheduleSlot("Diary Checking", 13 * 60 + 15, 13 * 60 + 30), // 1315-1330
        ScheduleSlot("Extra Class", 13 * 60 + 30, 14 * 60 + 15),    // 1330-1415
    )

    fun getSchedule(ctx: Context): List<ScheduleSlot> {
        val raw = prefs(ctx).getString(KEY_SCHEDULE, "") ?: ""
        if (raw.isBlank()) return DEFAULT_SCHEDULE
        val parsed = raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size != 3) return@mapNotNull null
            val start = parts[1].toIntOrNull() ?: return@mapNotNull null
            val end = parts[2].toIntOrNull() ?: return@mapNotNull null
            ScheduleSlot(parts[0], start, end)
        }
        return if (parsed.isEmpty()) DEFAULT_SCHEDULE else parsed.sortedBy { it.startMinutes }
    }

    fun saveSchedule(ctx: Context, slots: List<ScheduleSlot>) {
        val raw = slots.sortedBy { it.startMinutes }
            .joinToString(";") { "${it.label}|${it.startMinutes}|${it.endMinutes}" }
        prefs(ctx).edit().putString(KEY_SCHEDULE, raw).apply()
    }

    fun resetScheduleToDefault(ctx: Context) {
        saveSchedule(ctx, DEFAULT_SCHEDULE)
    }
}
