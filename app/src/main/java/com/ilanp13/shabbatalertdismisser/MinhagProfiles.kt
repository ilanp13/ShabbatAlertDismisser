package com.ilanp13.shabbatalertdismisser

object MinhagProfiles {

    data class Profile(val key: String, val minutes: Int, val display: String)

    val candleLighting = listOf(
        Profile("ashkenaz",       18, "אשכנז / Ashkenaz — 18 min"),
        Profile("kise_rachamim",  20, "כיסא רחמים (Kise Rachamim) — 20 min"),
        Profile("or_hachaim",     20, "אור החיים (Or HaChaim) — 20 min"),
        Profile("ben_ish_chai",   20, "בן איש חי (Ben Ish Chai) — 20 min"),
        Profile("jerusalem_30",   30, "ירושלים (Jerusalem) — 30 min"),
        Profile("jerusalem_40",   40, "ירושלים מחמיר (Jerusalem strict) — 40 min"),
    )

    val havdalah = listOf(
        Profile("geonim",      25, "גאונים (Geonim) — 25 min"),
        Profile("standard",    40, "Standard — 40 min"),
        Profile("rabenu_tam",  72, "רבנו תם (Rabenu Tam) — 72 min"),
    )

    fun candleKeyFor(minutes: Int): String =
        candleLighting.firstOrNull { it.minutes == minutes }?.key ?: "ashkenaz"

    fun havdalahKeyFor(minutes: Int): String =
        havdalah.firstOrNull { it.minutes == minutes }?.key ?: "standard"
}
