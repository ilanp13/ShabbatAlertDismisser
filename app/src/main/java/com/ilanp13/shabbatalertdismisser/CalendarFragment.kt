package com.ilanp13.shabbatalertdismisser

import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shows upcoming Shabbatot and Yom Tov days for the rest of the Hebrew year.
 * Only days when phone usage is prohibited:
 *   Shabbat, Rosh Hashana, Yom Kippur, Sukkot (1st), Shmini Atzeret/Simchat Torah,
 *   Pesach (1st & 7th), Shavuot.
 */
class CalendarFragment : Fragment() {

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var minhagLabelView: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header row with settings gear and minhag info
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
        val useRt = prefs.getBoolean("use_rabenu_tam", false)
        val minhagText = "${profile.display} · ${if (useRt) "ר״ת" else "גר״א"}"

        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val gearIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                setMargins(0, 0, dp(8), 0)
            }
            setOnClickListener {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
        minhagLabelView = TextView(requireContext()).apply {
            text = minhagText
            textSize = 12f
            alpha = 0.8f
        }
        headerRow.addView(gearIcon)
        headerRow.addView(minhagLabelView)
        root.addView(headerRow)

        progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
        root.addView(progressBar)

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        this.container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
        }
        scrollView.addView(this.container)
        root.addView(scrollView)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCalendar()
    }

    private var lastMinhagKey: String? = null
    private var lastUseRt: Boolean? = null

    override fun onResume() {
        super.onResume()
        // Reload if minhag settings changed (e.g., after returning from Settings)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentKey = prefs.getString("minhag_key", "ashkenaz")
        val currentRt = prefs.getBoolean("use_rabenu_tam", false)
        if (lastMinhagKey != null && (currentKey != lastMinhagKey || currentRt != lastUseRt)) {
            val profile = MinhagProfiles.byKey(currentKey ?: "ashkenaz")
            minhagLabelView.text = "${profile.display} · ${if (currentRt) "ר״ת" else "גר״א"}"
            loadCalendar()
        }
        lastMinhagKey = currentKey
        lastUseRt = currentRt
    }

    private fun loadCalendar() {
        progressBar.visibility = View.VISIBLE
        Thread {
            val entries = fetchHolyTimes()
            handler.post {
                if (!isAdded) return@post
                progressBar.visibility = View.GONE
                displayEntries(entries)
            }
        }.start()
    }

    data class HolyTimeEntry(
        val name: String,          // Hebrew name (e.g., "פסח א׳", "פרשת שמיני")
        val candleMs: Long,
        val havdalahMs: Long,
        val isYomTov: Boolean,     // true for holidays, false for regular Shabbat
        val hebrewDate: String?    // Hebrew date string (e.g., "15 Nisan 5786")
    )

    private fun fetchHolyTimes(): List<HolyTimeEntry> {
        val ctx = context ?: return emptyList()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
        val useRt = prefs.getBoolean("use_rabenu_tam", false)
        val havMins = if (useRt) profile.rtMins else profile.graMins
        val tzId = TimeZone.getDefault().id

        // Fetch from now through next Rosh Hashana (~6-7 months)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startDate = sdf.format(Date())
        val cal = Calendar.getInstance()
        // Go forward up to 13 months to ensure we cover through next Tishrei
        cal.add(Calendar.MONTH, 13)
        val endDate = sdf.format(cal.time)

        val url = URL(buildString {
            append("https://www.hebcal.com/hebcal?v=1&cfg=json&i=on")
            append("&latitude=$lat&longitude=$lon&tzid=$tzId")
            append("&b=${profile.candleMins}&m=$havMins")
            append("&start=$startDate&end=$endDate")
            append("&c=on&s=on&maj=on&ss=on") // candles, sedrot, major holidays, special shabbatot
        })

        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "ShabbatAlertDismisser/1.0")
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parseHolyTimes(body)
        } catch (e: Exception) {
            android.util.Log.w("CalendarFragment", "Fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseHolyTimes(json: String): List<HolyTimeEntry> {
        val items = JSONObject(json).getJSONArray("items")

        // Store candle info with memo and havdalah with memo
        data class CandleInfo(val timeMs: Long, val memo: String?)
        data class HavdalahInfo(val timeMs: Long, val memo: String?)
        val candles = mutableListOf<CandleInfo>()
        val havdalahs = mutableListOf<HavdalahInfo>()
        // Map of hdate by date string for lookup
        val hdateByDate = mutableMapOf<String, String>()
        val parashaByDate = mutableMapOf<String, String>()
        val holidayByDate = mutableMapOf<String, String>() // date -> Hebrew holiday name

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            when (item.optString("category")) {
                "candles" -> {
                    val memo = item.optString("memo", "").ifEmpty { null }
                    candles.add(CandleInfo(parseDate(item.getString("date")), memo))
                }
                "havdalah" -> {
                    val memo = item.optString("memo", "").ifEmpty { null }
                    havdalahs.add(HavdalahInfo(parseDate(item.getString("date")), memo))
                }
                "parashat" -> {
                    val hdate = item.optString("hdate", "").ifEmpty { null }
                    val dateStr = item.optString("date", "")
                    val hebrew = item.optString("hebrew", "")
                    if (dateStr.isNotEmpty()) {
                        if (hdate != null) hdateByDate[dateStr] = hdate
                        if (hebrew.isNotEmpty()) parashaByDate[dateStr] = hebrew
                    }
                }
                "holiday" -> {
                    val hdate = item.optString("hdate", "").ifEmpty { null }
                    val dateStr = item.optString("date", "")
                    val hebrew = item.optString("hebrew", "")
                    val subcat = item.optString("subcat", "")
                    if (dateStr.isNotEmpty()) {
                        if (hdate != null) hdateByDate[dateStr] = hdate
                        // Store major holiday Hebrew names by date
                        if (hebrew.isNotEmpty() && subcat == "major" && !hebrew.startsWith("ערב")) {
                            holidayByDate[dateStr] = hebrew
                        }
                    }
                }
            }
        }

        candles.sortBy { it.timeMs }
        havdalahs.sortBy { it.timeMs }

        // Pair candles with havdalahs
        val entries = mutableListOf<HolyTimeEntry>()
        var hIdx = 0
        for (candle in candles) {
            while (hIdx < havdalahs.size && havdalahs[hIdx].timeMs <= candle.timeMs) hIdx++
            if (hIdx >= havdalahs.size) break
            val havdalah = havdalahs[hIdx]
            hIdx++

            // Look up what's on the DAY AFTER candle lighting (the actual Shabbat/holiday)
            val cal = Calendar.getInstance()
            cal.timeInMillis = candle.timeMs
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val nextDayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val hdate = hdateByDate[nextDayStr]

            // Check if a major holiday falls on the next day
            val holidayName = holidayByDate[nextDayStr]
            val parashaName = parashaByDate[nextDayStr]
            val isYomTov = holidayName != null && isProhibitedHoliday(holidayName)

            // Holiday takes precedence over parasha
            val hebrewLabel = if (holidayName != null) holidayName
                else parashaName ?: "שבת"

            entries.add(HolyTimeEntry(hebrewLabel, candle.timeMs, havdalah.timeMs, isYomTov, hdate))
        }

        return entries
    }


    /** Check if a Hebrew holiday name is one where phone usage is prohibited */
    private fun isProhibitedHoliday(hebrewName: String): Boolean {
        val prohibited = listOf(
            "ראש השנה", "יום כיפור", "כיפור",
            "סוכות א", "שמיני עצרת", "שמחת תורה",
            "פסח א", "פסח ז",
            "שבועות"
        )
        return prohibited.any { hebrewName.contains(it) }
    }

    private fun displayEntries(entries: List<HolyTimeEntry>) {
        container.removeAllViews()
        val ctx = context ?: return

        if (entries.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "לא נמצאו זמנים"
                textSize = 14f
                setPadding(0, dp(16), 0, 0)
            })
            return
        }

        val density = resources.displayMetrics.density
        val dateFmt = SimpleDateFormat("EEEE d.M", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        var lastMonth = -1

        for (entry in entries) {
            // The actual Shabbat/holiday day is the day AFTER candle lighting
            val actualDayCal = Calendar.getInstance()
            actualDayCal.timeInMillis = entry.candleMs
            actualDayCal.add(Calendar.DAY_OF_YEAR, 1)
            val actualDayMs = actualDayCal.timeInMillis

            // Month header (based on actual day, not candle day)
            val month = actualDayCal.get(Calendar.MONTH)
            if (month != lastMonth) {
                lastMonth = month
                val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                container.addView(TextView(ctx).apply {
                    text = monthFmt.format(Date(actualDayMs))
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(12), 0, dp(4))
                    alpha = 0.7f
                })
            }

            // Entry row
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(4)) }

                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = dp(6).toFloat()
                if (entry.isYomTov) {
                    bg.setColor(android.graphics.Color.argb(30, 255, 193, 7)) // faint yellow
                    bg.setStroke(1, android.graphics.Color.parseColor("#FFC107"))
                } else {
                    bg.setColor(android.graphics.Color.argb(15, 255, 255, 255)) // faint white
                    bg.setStroke(1, android.graphics.Color.argb(40, 255, 255, 255))
                }
                background = bg
            }

            // Left: name + indicator
            val nameCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            nameCol.addView(TextView(ctx).apply {
                text = entry.name
                textSize = 12f
                setTypeface(null, if (entry.isYomTov) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            })
            nameCol.addView(TextView(ctx).apply {
                val gregDate = dateFmt.format(Date(actualDayMs))
                text = if (entry.hebrewDate != null) "$gregDate · ${hebrewDateToHebrew(entry.hebrewDate)}" else gregDate
                textSize = 9f
                alpha = 0.6f
            })

            // Right: times
            val timeCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.END
            }
            val havdalahDisplayMs = ((entry.havdalahMs + 30_000L) / 60_000L) * 60_000L
            timeCol.addView(TextView(ctx).apply {
                text = "🕯 ${timeFmt.format(Date(entry.candleMs))}"
                textSize = 11f
            })
            timeCol.addView(TextView(ctx).apply {
                text = "✡ ${timeFmt.format(Date(havdalahDisplayMs))}"
                textSize = 11f
            })

            row.addView(nameCol)
            row.addView(timeCol)
            container.addView(row)
        }
    }

    private fun hebrewDateToHebrew(hdate: String): String {
        val monthMap = mapOf(
            "Nisan" to "ניסן", "Iyyar" to "אייר", "Sivan" to "סיוון",
            "Tamuz" to "תמוז", "Tammuz" to "תמוז", "Av" to "אב", "Elul" to "אלול",
            "Tishrei" to "תשרי", "Cheshvan" to "חשוון", "Kislev" to "כסלו",
            "Tevet" to "טבת", "Sh'vat" to "שבט", "Shvat" to "שבט",
            "Adar" to "אדר", "Adar I" to "אדר א׳", "Adar II" to "אדר ב׳"
        )
        var result = hdate
        for ((eng, heb) in monthMap) {
            result = result.replace(eng, heb)
        }
        return result
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun parseDate(s: String): Long =
        OffsetDateTime.parse(s).toInstant().toEpochMilli()

    private fun parseDateLoose(s: String): Long {
        return if (s.contains("T")) parseDate(s)
        else {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(s)?.time ?: throw IllegalArgumentException("Invalid date: $s")
        }
    }
}
