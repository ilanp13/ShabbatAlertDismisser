package com.ilanp13.shabbatalertdismisser.shared

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

class ShabbatCalculator(
    private val latitude: Double,
    private val longitude: Double
) {

    /**
     * Check if right now is during Shabbat.
     * Shabbat starts: Friday sunset minus [candleLightingMinutes]
     * Shabbat ends: Saturday sunset plus [havdalahMinutes]
     */
    fun isShabbatNow(candleLightingMinutes: Int = 18, havdalahMinutes: Int = 40): Boolean {
        val now = Calendar.getInstance()
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        return when (dayOfWeek) {
            Calendar.FRIDAY -> {
                val sunset = getSunsetTime(now)
                if (sunset != null) {
                    sunset.add(Calendar.MINUTE, -candleLightingMinutes)
                    now.after(sunset)
                } else false
            }
            Calendar.SATURDAY -> {
                val sunset = getSunsetTime(now)
                if (sunset != null) {
                    sunset.add(Calendar.MINUTE, havdalahMinutes)
                    now.before(sunset)
                } else false
            }
            else -> false
        }
    }

    /**
     * Get Shabbat start and end times for the upcoming (or current) Shabbat.
     */
    fun getShabbatTimes(
        candleLightingMinutes: Int = 18,
        havdalahMinutes: Int = 40
    ): Pair<Calendar, Calendar>? {
        val now = Calendar.getInstance()

        // Find next Friday
        val friday = (now.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val saturday = (friday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val fridaySunset = getSunsetTime(friday) ?: return null
        val saturdaySunset = getSunsetTime(saturday) ?: return null

        fridaySunset.add(Calendar.MINUTE, -candleLightingMinutes)
        saturdaySunset.add(Calendar.MINUTE, havdalahMinutes)

        return Pair(fridaySunset, saturdaySunset)
    }

    /**
     * Public accessor for sunset time (used by HolidayCalculator).
     */
    fun getSunsetTimePublic(date: Calendar): Calendar? = getSunsetTime(date)

    /**
     * Calculate sunset time for a given date using NOAA algorithm.
     */
    private fun getSunsetTime(date: Calendar): Calendar? {
        val tz = TimeZone.getDefault()
        val tzOffset = tz.getOffset(date.timeInMillis) / 3600000.0

        val dayOfYear = date.get(Calendar.DAY_OF_YEAR)
        val year = date.get(Calendar.YEAR)

        // Julian century
        val jd = getJulianDay(year, date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH))
        val jc = (jd - 2451545.0) / 36525.0

        // Sun's geometric mean longitude & anomaly
        val geomMeanLongSun = (280.46646 + jc * (36000.76983 + 0.0003032 * jc)) % 360
        val geomMeanAnomSun = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)

        // Eccentricity of Earth's orbit
        val eccentEarthOrbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)

        // Sun equation of center
        val sunEqOfCtr = sin(Math.toRadians(geomMeanAnomSun)) *
                (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
                sin(Math.toRadians(2 * geomMeanAnomSun)) * (0.019993 - 0.000101 * jc) +
                sin(Math.toRadians(3 * geomMeanAnomSun)) * 0.000289

        val sunTrueLong = geomMeanLongSun + sunEqOfCtr

        // Sun apparent longitude
        val omega = 125.04 - 1934.136 * jc
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Mean obliquity of ecliptic
        val meanObliqEcliptic = 23 + (26 + (21.448 - jc *
                (46.815 + jc * (0.00059 - jc * 0.001813))) / 60) / 60
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos(Math.toRadians(omega))

        // Sun declination
        val sunDeclin = Math.toDegrees(
            asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(sunAppLong)))
        )

        // Equation of time
        val varY = tan(Math.toRadians(obliqCorr / 2)).pow(2)
        val eqOfTime = 4 * Math.toDegrees(
            varY * sin(2 * Math.toRadians(geomMeanLongSun)) -
                    2 * eccentEarthOrbit * sin(Math.toRadians(geomMeanAnomSun)) +
                    4 * eccentEarthOrbit * varY * sin(Math.toRadians(geomMeanAnomSun)) *
                    cos(2 * Math.toRadians(geomMeanLongSun)) -
                    0.5 * varY * varY * sin(4 * Math.toRadians(geomMeanLongSun)) -
                    1.25 * eccentEarthOrbit * eccentEarthOrbit *
                    sin(2 * Math.toRadians(geomMeanAnomSun))
        )

        // Hour angle for sunset (90.833° = standard refraction)
        val zenith = 90.833
        val haArg = cos(Math.toRadians(zenith)) /
                (cos(Math.toRadians(latitude)) * cos(Math.toRadians(sunDeclin))) -
                tan(Math.toRadians(latitude)) * tan(Math.toRadians(sunDeclin))

        if (haArg > 1 || haArg < -1) return null  // No sunset (polar)

        val hourAngle = Math.toDegrees(acos(haArg))

        // Sunset in minutes from midnight (UTC)
        val solarNoon = (720 - 4 * longitude - eqOfTime + tzOffset * 60) / 1440
        val sunsetFraction = solarNoon + hourAngle * 4 / 1440

        val sunsetMinutes = sunsetFraction * 1440
        val hours = (sunsetMinutes / 60).toInt()
        val minutes = (sunsetMinutes % 60).toInt()

        return (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
        }
    }

    private fun getJulianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y--; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + b - 1524.5
    }
}
