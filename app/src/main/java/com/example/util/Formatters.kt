package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

object Formatters {
    private val latinLocale = Locale.forLanguageTag("ar-u-nu-latn")
    private const val DAY_MS = 86_400_000L

    fun money(value: Double): String {
        val rounded = (value * 100.0).roundToLong() / 100.0
        val clean = if (rounded == 0.0) 0.0 else rounded
        return String.format(Locale.US, "%.2f", clean)
    }

    fun dayStartMillis(epochMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun addDays(epochMillis: Long, deltaDays: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMillis
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isoDay(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(epochMillis))
    }

    fun parseIsoDay(iso: String): Long? {
        return try {
            val parts = iso.trim().split("-")
            if (parts.size != 3) return null
            val cal = Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    fun headerDate(epochMillis: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("EEEE، d MMMM", latinLocale)
        return sdf.format(Date(epochMillis))
    }

    fun shortDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "—"
        val sdf = SimpleDateFormat("d MMM yyyy", latinLocale)
        return sdf.format(Date(epochMillis))
    }

    fun shortDayName(epochMillis: Long): String {
        val sdf = SimpleDateFormat("EEE", latinLocale)
        return sdf.format(Date(epochMillis))
    }

    fun timeOnly(epochMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", latinLocale)
        return sdf.format(Date(epochMillis))
    }

    fun dateTimeShort(epochMillis: Long): String {
        val sdf = SimpleDateFormat("d MMM، HH:mm", latinLocale)
        return sdf.format(Date(epochMillis))
    }

    fun expiryDaysLeft(expiryIso: String): Int? {
        if (expiryIso.isBlank()) return null
        val target = parseIsoDay(expiryIso) ?: return null
        val today = dayStartMillis()
        return ((target - today).toDouble() / DAY_MS.toDouble()).roundToLong().toInt()
    }

    fun expiryText(daysLeft: Int): String = when {
        daysLeft < 0 -> "منتهي منذ ${-daysLeft} يوم"
        daysLeft == 0 -> "ينتهي اليوم"
        else -> "ينتهي خلال $daysLeft يوم"
    }
}
