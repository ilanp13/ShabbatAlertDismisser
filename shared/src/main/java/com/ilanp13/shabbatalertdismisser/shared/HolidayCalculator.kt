package com.ilanp13.shabbatalertdismisser.shared

import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Calculates Jewish holidays (Yom Tov days when melacha is forbidden).
 * Uses a simplified Hebrew calendar conversion.
 */
object HolidayCalculator {

    /**
     * Check if right now is during a Yom Tov (from candle lighting eve to havdalah).
     */
    fun isYomTovToday(
        candleLightingMinutes: Int,
        havdalahMinutes: Int,
        latitude: Double,
        longitude: Double
    ): Boolean {
        val now = Calendar.getInstance()
        val calculator = ShabbatCalculator(latitude, longitude)

        // Check if today is Yom Tov
        val todayHebrew = gregorianToHebrew(now)
        if (isYomTovDate(todayHebrew)) {
            val sunset = calculator.getSunsetTimePublic(now)
            if (sunset != null) {
                sunset.add(Calendar.MINUTE, havdalahMinutes)
                if (now.before(sunset)) return true
            } else return true
        }

        // Check if yesterday was erev Yom Tov (we're in the night portion)
        val yesterday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayHebrew = gregorianToHebrew(yesterday)
        // If yesterday is erev YT, check if now is after yesterday's sunset
        val tomorrow = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowHebrew = gregorianToHebrew(tomorrow)

        // Check if tonight starts Yom Tov
        val todayForErev = gregorianToHebrew(now)
        val nextDay = nextHebrewDay(todayForErev)
        if (isYomTovDate(nextDay)) {
            val sunset = calculator.getSunsetTimePublic(now)
            if (sunset != null) {
                sunset.add(Calendar.MINUTE, -candleLightingMinutes)
                if (now.after(sunset)) return true
            }
        }

        return false
    }

    private fun isYomTovDate(hd: HebrewDate): Boolean {
        val m = hd.month
        val d = hd.day
        return when {
            // Rosh Hashana: Tishrei 1-2
            m == 7 && d in 1..2 -> true
            // Yom Kippur: Tishrei 10
            m == 7 && d == 10 -> true
            // Sukkot: Tishrei 15-16 (first days)
            m == 7 && d in 15..16 -> true
            // Shmini Atzeret / Simchat Torah: Tishrei 22-23
            m == 7 && d in 22..23 -> true
            // Pesach: Nisan 15-16, 21-22
            m == 1 && d in 15..16 -> true
            m == 1 && d in 21..22 -> true
            // Shavuot: Sivan 6-7
            m == 3 && d in 6..7 -> true
            else -> false
        }
    }

    data class HebrewDate(val year: Int, val month: Int, val day: Int)

    private fun nextHebrewDay(hd: HebrewDate): HebrewDate {
        // Simplified: just increment day (sufficient for Yom Tov checking)
        return HebrewDate(hd.year, hd.month, hd.day + 1)
    }

    /**
     * Convert Gregorian date to Hebrew date.
     * Algorithm based on the method by Edward Reingold & Nachum Dershowitz.
     */
    fun gregorianToHebrew(cal: Calendar): HebrewDate {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        // Calculate Julian Day Number
        val jdn = gregorianToJDN(year, month, day)

        // Hebrew epoch: JDN of 1 Tishrei 1 (proleptic)
        val hebrewEpoch = 347997L

        // Approximate Hebrew year
        var hYear = ((jdn - hebrewEpoch) * 98496 / 35975351).toInt() + 1

        // Adjust year
        while (jdn >= hebrewNewYear(hYear + 1)) hYear++

        // Find month
        var hMonth: Int
        val firstDayOfYear = hebrewNewYear(hYear)

        if (jdn < hebrewDateToJDN(hYear, 1, 1)) {
            // Before Nisan, we're in the second half of the year
            hMonth = 7 // Start from Tishrei
            while (jdn > hebrewDateToJDN(hYear, hMonth, hebrewMonthDays(hYear, hMonth))) {
                hMonth++
                if (hMonth == 13 || (hMonth == 14 && !isHebrewLeapYear(hYear))) break
            }
        } else {
            hMonth = 1 // Start from Nisan
            while (jdn > hebrewDateToJDN(hYear, hMonth, hebrewMonthDays(hYear, hMonth))) {
                hMonth++
                if (hMonth == 7) break
            }
        }

        val hDay = (jdn - hebrewDateToJDN(hYear, hMonth, 1) + 1).toInt()

        return HebrewDate(hYear, hMonth, hDay)
    }

    private fun gregorianToJDN(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
    }

    private fun hebrewNewYear(hYear: Int): Long {
        // Molad calculation for Tishrei
        val monthsElapsed = (235L * ((hYear - 1) / 19)) +
                (12L * ((hYear - 1) % 19)) +
                ((7L * ((hYear - 1) % 19) + 1) / 19)

        val partsElapsed = 204 + 793 * (monthsElapsed % 1080)
        val hoursElapsed = 5 + 12 * monthsElapsed +
                793 * (monthsElapsed / 1080) +
                partsElapsed / 1080
        val day = 1 + 29 * monthsElapsed + hoursElapsed / 24
        val parts = 1080 * (hoursElapsed % 24) + partsElapsed % 1080

        var altDay = day.toLong()
        val dayOfWeek = (altDay % 7)

        // Postponement rules (dehiyot)
        if (parts >= 19440 ||
            (dayOfWeek == 2L && parts >= 9924 && !isHebrewLeapYear(hYear)) ||
            (dayOfWeek == 1L && parts >= 16789 && isHebrewLeapYear(hYear - 1))) {
            altDay++
        }

        val finalDow = altDay % 7
        if (finalDow == 0L || finalDow == 3L || finalDow == 5L) {
            altDay++
        }

        return altDay + 347996L
    }

    private fun isHebrewLeapYear(hYear: Int): Boolean {
        return ((7 * hYear + 1) % 19) < 7
    }

    private fun hebrewYearDays(hYear: Int): Int {
        return (hebrewNewYear(hYear + 1) - hebrewNewYear(hYear)).toInt()
    }

    private fun hebrewMonthDays(hYear: Int, hMonth: Int): Int {
        return when (hMonth) {
            1 -> 30  // Nisan
            2 -> 29  // Iyar
            3 -> 30  // Sivan
            4 -> 29  // Tammuz
            5 -> 30  // Av
            6 -> 29  // Elul
            7 -> 30  // Tishrei
            8 -> if (hebrewYearDays(hYear) % 10 != 5) 30 else 29  // Cheshvan
            9 -> if (hebrewYearDays(hYear) % 10 == 3) 30 else 29  // Kislev
            10 -> 29  // Tevet
            11 -> 30  // Shvat
            12 -> if (isHebrewLeapYear(hYear)) 30 else 29  // Adar I
            13 -> 29  // Adar II (only in leap years)
            else -> 30
        }
    }

    private fun hebrewDateToJDN(hYear: Int, hMonth: Int, hDay: Int): Long {
        var jdn = hebrewNewYear(hYear) // 1 Tishrei

        // Add days for months from Tishrei to target month
        if (hMonth >= 7) {
            var m = 7
            while (m < hMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
        } else {
            // Add months Tishrei(7) through Adar
            var m = 7
            val lastMonth = if (isHebrewLeapYear(hYear)) 13 else 12
            while (m <= lastMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
            // Then Nisan(1) to target
            m = 1
            while (m < hMonth) {
                jdn += hebrewMonthDays(hYear, m)
                m++
            }
        }

        jdn += hDay - 1
        return jdn
    }
}
