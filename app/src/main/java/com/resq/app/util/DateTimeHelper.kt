package com.resq.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for date and time formatting in RESQ.
 */
@Singleton
class DateTimeHelper @Inject constructor() {

    fun getCurrentDateFormatted(languageCode: String = "en"): String {
        val locale = getLocale(languageCode)
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", locale)
        return dateFormat.format(Date())
    }

    fun getCurrentTimeFormatted(use24Hour: Boolean = false): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return timeFormat.format(Date())
    }

    fun getCurrentDateTimeFormatted(languageCode: String = "en", use24Hour: Boolean = false): String {
        val locale = getLocale(languageCode)
        val pattern = if (use24Hour) "EEEE, MMMM d, yyyy • HH:mm" else "EEEE, MMMM d, yyyy • h:mm a"
        val dateTimeFormat = SimpleDateFormat(pattern, locale)
        return dateTimeFormat.format(Date())
    }

    fun getGreeting(languageCode: String = "en"): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (languageCode.lowercase()) {
            "tl", "fil" -> when (hour) {
                in 0..11 -> "Magandang Umaga"
                in 12..17 -> "Magandang Hapon"
                else -> "Magandang Gabi"
            }
            else -> when (hour) {
                in 0..11 -> "Good Morning"
                in 12..17 -> "Good Afternoon"
                else -> "Good Evening"
            }
        }
    }

    fun getTimeBasedSafetyMessage(languageCode: String = "en"): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when (languageCode.lowercase()) {
            "tl", "fil" -> when (hour) {
                in 0..5 -> "Manatiling ligtas sa gabi."
                in 6..11 -> "Maging handa para sa araw na ito."
                in 12..17 -> "Maging alerto sa iyong paligid."
                in 18..21 -> "Ihanda ang iyong tahanan para sa gabi."
                else -> "Tiyakin ang kaligtasan bago matulog."
            }
            else -> when (hour) {
                in 0..5 -> "Stay safe during the night."
                in 6..11 -> "Be prepared for the day ahead."
                in 12..17 -> "Stay alert to your surroundings."
                in 18..21 -> "Prepare your home for the night."
                else -> "Ensure safety before sleeping."
            }
        }
    }

    fun formatTimestamp(timestamp: Long, languageCode: String = "en"): String {
        val locale = getLocale(languageCode)
        val dateFormat = SimpleDateFormat("MMM d, yyyy", locale)
        return dateFormat.format(Date(timestamp))
    }

    fun getRelativeTime(timestamp: Long, languageCode: String = "en"): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when (languageCode.lowercase()) {
            "tl", "fil" -> when {
                seconds < 60 -> "Ngayon lang"
                minutes < 60 -> "$minutes minuto ang nakalipas"
                hours < 24 -> "$hours oras ang nakalipas"
                days < 7 -> "$days araw ang nakalipas"
                else -> formatTimestamp(timestamp, languageCode)
            }
            else -> when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "$minutes minutes ago"
                hours < 24 -> "$hours hours ago"
                days < 7 -> "$days days ago"
                else -> formatTimestamp(timestamp, languageCode)
            }
        }
    }

    private fun getLocale(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            "tl", "fil" -> Locale("fil", "PH")
            else -> Locale.ENGLISH
        }
    }
}