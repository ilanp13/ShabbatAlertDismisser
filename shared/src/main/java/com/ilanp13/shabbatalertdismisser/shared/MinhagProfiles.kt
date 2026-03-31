package com.ilanp13.shabbatalertdismisser.shared

object MinhagProfiles {

    /**
     * Each profile encodes:
     *   candleMins — minutes before sunset for candle lighting (Hebcal 'b' param)
     *   graMins    — minutes after sunset for standard end-of-Shabbat (Hebcal 'm' param)
     *   rtMins     — minutes after sunset for Rabenu Tam
     *
     * graMins values are calibrated from published calendar data (Feb, Israel).
     * Hebcal's own sunset algorithm is used, so results improve on fixed-minute-only approach.
     */
    data class Profile(
        val key: String,
        val display: String,
        val candleMins: Int,
        val graMins: Int,
        val rtMins: Int = 72
    )

    val all = listOf(
        Profile("ashkenaz",      "אשכנז / Ashkenaz",                18, 42),
        Profile("or_hachaim",    "אור החיים (Or HaChaim)",           20, 31),
        Profile("kise_rachamim", "כיסא רחמים (Kise Rachamim)",       20, 37, 73),
        Profile("ben_ish_chai",  "בן איש חי (Ben Ish Chai)",         20, 40),
        Profile("jerusalem",     "ירושלים (Jerusalem)",               40, 40),
    )

    fun byKey(key: String): Profile = all.firstOrNull { it.key == key } ?: all.first()
}
