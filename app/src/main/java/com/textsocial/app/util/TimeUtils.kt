package com.textsocial.app.util

import android.content.Context
import com.textsocial.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Util buat ngurusin timestamp yang balik dari Supabase/PostgREST, contohnya
 * "2026-07-05T10:23:45.123456+00:00" atau "2026-07-05T10:23:45Z", terus
 * diubah jadi teks yang gampang dibaca kayak "5m", "2h", atau tanggal penuh.
 *
 * CATATAN: sengaja TIDAK pakai java.time (Instant/OffsetDateTime) karena
 * app ini minSdk 24 dan belum ada core library desugaring dipasang, jadi
 * java.time baru aman dipakai mulai API 26. SimpleDateFormat sudah ada
 * sejak API 1, jadi ini pilihan paling aman tanpa nambah dependency baru.
 */
object TimeUtils {

    /** Ubah timestamp ISO-8601 dari server jadi epoch millis, null kalau gagal di-parse. */
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

    /**
     * Format waktu relatif yang ringkas ala Twitter/X: "now", "5m", "3h", "2d",
     * lalu jatuh ke tanggal singkat kalau sudah lebih dari seminggu.
     * Dipakai di tempat-tempat yang butuh label kecil: feed, notifikasi, list DM, komentar.
     *
     * Butuh [context] supaya bisa ambil teks sesuai bahasa yang aktif (EN/ID).
     */
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

    /** Format lengkap buat halaman detail, contoh: "5 Jul 2026 · 14:32". */
    fun timeFull(iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        return SimpleDateFormat("d MMM yyyy '·' HH:mm", Locale.US).format(Date(millis))
    }

    /** Cuma jam:menit, buat bubble chat, contoh: "14:32". */
    fun clockTime(iso: String?): String {
        val millis = parseToEpochMillis(iso) ?: return ""
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
    }

    /**
     * Label hari buat pemisah tanggal di chat: "Today", "Yesterday", atau "5 Jul 2026".
     * Butuh [context] buat teks "Today"/"Yesterday" sesuai bahasa aktif.
     */
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

    /** True kalau dua timestamp ISO jatuh di tanggal kalender yang sama (dipakai buat separator tanggal di chat). */
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

    /** Berapa jam yang sudah berlalu sejak timestamp, dipakai buat perhitungan skor feed. Minimum 0.0. */
    fun hoursSince(iso: String?): Double {
        val millis = parseToEpochMillis(iso) ?: return 0.0
        val diff = System.currentTimeMillis() - millis
        return (diff.coerceAtLeast(0) / 3_600_000.0)
    }

    /**
     * Sisa waktu tayang story ("23h left" / "45m left"), atau "Expired" kalau sudah lewat.
     * Butuh [context] buat teks yang sesuai bahasa aktif.
     */
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