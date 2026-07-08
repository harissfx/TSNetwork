package com.textsocial.app.util

import android.content.Context
import com.textsocial.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    /**
     * ISO-8601 timestamp for "right now", used only to stamp optimistic
     * (locally-created, not-yet-confirmed-by-server) entities such as a post,
     * comment, story, or message that was just created client-side.
     */
    fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    /** Same as [nowIso] but offset into the future, used e.g. to stamp an optimistic story's expiry. */
    fun isoPlusHours(hours: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(Date(System.currentTimeMillis() + hours * 3_600_000L))
    }

    fun parseToEpochMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        val normalized = normalize(iso) ?: return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            sdf.parse(normalized)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        s = if (s.endsWith("Z")) s.dropLast(1) + "+0000" else s

        val offsetWithColon = Regex("([+-]\\d{2}):(\\d{2})$")
        s = offsetWithColon.replace(s) { m -> "${m.groupValues[1]}${m.groupValues[2]}" }

        if (!Regex("[+-]\\d{4}$").containsMatchIn(s)) {
            s += "+0000"
        }

        val offsetStart = s.length - 5
        if (offsetStart < 0) return null
        val datePart = s.substring(0, offsetStart)
        val offsetPart = s.substring(offsetStart)

        val dotIndex = datePart.indexOf('.')
        val normalizedDatePart = if (dotIndex == -1) {
            "$datePart.000"
        } else {
            val fraction = datePart.substring(dotIndex + 1).take(3).padEnd(3, '0')
            datePart.substring(0, dotIndex) + "." + fraction
        }
        return normalizedDatePart + offsetPart
    }

    fun timeAgoShort(context: Context, iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        val diff = System.currentTimeMillis() - millis
        if (diff < 30_000) return context.getString(R.string.time_now)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes_short, minutes)
            hours < 24 -> context.getString(R.string.time_hours_short, hours)
            days < 7 -> context.getString(R.string.time_days_short, days)
            days < 365 -> SimpleDateFormat("d MMM", Locale.US).format(Date(millis))
            else -> SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(millis))
        }
    }

    fun timeFull(iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        return SimpleDateFormat("d MMM yyyy '·' HH:mm", Locale.US).format(Date(millis))
    }

    fun clockTime(iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
    }

    fun dayLabel(context: Context, iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(target, now) -> context.getString(R.string.day_today)
            isSameDay(target, yesterday) -> context.getString(R.string.day_yesterday)
            target.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale.US).format(Date(millis))
            else -> SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date(millis))
        }
    }

    fun isSameCalendarDay(isoA: String?, isoB: String?): Boolean {
        val a = parseToEpochMillis(isoA) ?: return false
        val b = parseToEpochMillis(isoB) ?: return false
        val calA = Calendar.getInstance().apply { timeInMillis = a }
        val calB = Calendar.getInstance().apply { timeInMillis = b }
        return isSameDay(calA, calB)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun hoursSince(iso: String?): Double {
        val millis = parseToEpochMillis(iso) ?: return 0.0
        val diff = System.currentTimeMillis() - millis
        return (diff.coerceAtLeast(0) / 3_600_000.0)
    }


    fun storyTimeLeft(context: Context, expiresAtIso: String?): String {
        val millis = parseToEpochMillis(expiresAtIso) ?: return ""
        val diff = millis - System.currentTimeMillis()
        if (diff <= 0) return context.getString(R.string.story_expired)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)

        return when {
            hours >= 1 -> context.getString(R.string.story_time_left_hours, hours)
            minutes >= 1 -> context.getString(R.string.story_time_left_minutes, minutes)
            else -> context.getString(R.string.story_time_left_under_minute)
        }
    }
}