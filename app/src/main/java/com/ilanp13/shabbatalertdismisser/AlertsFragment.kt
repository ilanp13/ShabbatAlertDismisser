package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnRefreshAlerts: Button
    private lateinit var btnClearAlerts: Button
    private lateinit var btnRefetch24h: Button
    private lateinit var pbAlertsLoading: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvLastRefreshed: TextView
    private lateinit var adapter: AlertsAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var lastRefreshMs = 0L
    private var refreshTimestampRunnable: Runnable? = null
    private var currentAlerts = mutableListOf<AlertCacheService.CachedAlert>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerAlerts)
        btnRefreshAlerts = view.findViewById(R.id.btnRefreshAlerts)
        btnClearAlerts = view.findViewById(R.id.btnClearAlerts)
        btnRefetch24h = view.findViewById(R.id.btnRefetch24h)
        pbAlertsLoading = view.findViewById(R.id.pbAlertsLoading)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        tvLastRefreshed = view.findViewById(R.id.tvLastRefreshed)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = AlertsAdapter()
        recyclerView.adapter = adapter

        btnRefreshAlerts.setOnClickListener {
            refreshAlerts()
        }

        btnClearAlerts.setOnClickListener {
            clearAlerts()
        }

        btnRefetch24h.setOnClickListener {
            refetch24h()
        }

        loadAlerts()
    }

    override fun onResume() {
        super.onResume()
        startRefreshTimestampUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopRefreshTimestampUpdate()
    }

    private fun loadAlerts() {
        // Show full 24h cache history, filtered by selected alert types
        val cachedAlerts = AlertCacheService.getLast24Hours(requireContext())
            .filter { AlertTypeFilter.shouldShow(requireContext(), it.type) }
        val sortedAlerts = cachedAlerts.sortedByDescending { it.timestampMs }
        adapter.submitList(sortedAlerts)
        updateEmptyState(sortedAlerts.isEmpty())
    }

    private fun refreshAlerts() {
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false
        btnClearAlerts.isEnabled = false
        btnRefetch24h.isEnabled = false

        Thread {
            val result = RedAlertService.fetch()

            when (result) {
                is RedAlertService.FetchResult.Success -> {
                    if (result.alert != null) {
                        // Real alert from API - save to cache
                        AlertCacheService.save(requireContext(), result.alert)
                    }
                }
                is RedAlertService.FetchResult.Unavailable -> {
                    // API error - keep existing cache
                }
            }

            handler.post {
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true
                btnClearAlerts.isEnabled = true
                btnRefetch24h.isEnabled = true
                lastRefreshMs = System.currentTimeMillis()
                updateLastRefreshedTime()
                loadAlerts()
            }
        }.start()
    }

    private fun clearAlerts() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putString("alert_cache", "[]").apply()
        loadAlerts()
    }

    private fun refetch24h() {
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false
        btnClearAlerts.isEnabled = false
        btnRefetch24h.isEnabled = false

        Thread {
            // Clear the cache
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putString("alert_cache", "[]").apply()

            // Fetch historical alerts from the last 24 hours
            val historyAlerts = RedAlertService.fetchHistory()
            for (alert in historyAlerts) {
                AlertCacheService.save(requireContext(), alert)
            }

            handler.post {
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true
                btnClearAlerts.isEnabled = true
                btnRefetch24h.isEnabled = true
                lastRefreshMs = System.currentTimeMillis()
                updateLastRefreshedTime()
                loadAlerts()
            }
        }.start()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun startRefreshTimestampUpdate() {
        stopRefreshTimestampUpdate()
        refreshTimestampRunnable = object : Runnable {
            override fun run() {
                if (lastRefreshMs > 0) {
                    updateLastRefreshedTime()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        refreshTimestampRunnable?.let { handler.post(it) }
    }

    private fun stopRefreshTimestampUpdate() {
        refreshTimestampRunnable?.let { handler.removeCallbacks(it) }
        refreshTimestampRunnable = null
    }

    private fun updateLastRefreshedTime() {
        if (lastRefreshMs > 0) {
            val now = System.currentTimeMillis()
            val diffSec = (now - lastRefreshMs) / 1000
            val timeStr = when {
                diffSec == 0L -> "just now"
                diffSec < 60 -> "$diffSec sec ago"
                diffSec < 3600 -> "${diffSec / 60} min ago"
                else -> "${diffSec / 3600} hour ago"
            }
            tvLastRefreshed.text = getString(R.string.alert_refresh_timestamp, timeStr)
            tvLastRefreshed.visibility = View.VISIBLE
        }
    }

    // Adapter for displaying alerts
    inner class AlertsAdapter : androidx.recyclerview.widget.ListAdapter<AlertCacheService.CachedAlert, AlertsAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<AlertCacheService.CachedAlert>() {
            override fun areItemsTheSame(oldItem: AlertCacheService.CachedAlert, newItem: AlertCacheService.CachedAlert) =
                oldItem.timestampMs == newItem.timestampMs

            override fun areContentsTheSame(oldItem: AlertCacheService.CachedAlert, newItem: AlertCacheService.CachedAlert) =
                oldItem == newItem
        }
    ) {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTime: TextView = itemView.findViewById(R.id.tvAlertTime)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvAlertTitle)
            private val tvRegions: TextView = itemView.findViewById(R.id.tvAlertRegions)
            private val tvDescription: TextView = itemView.findViewById(R.id.tvAlertDescription)

            fun bind(alert: AlertCacheService.CachedAlert) {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                tvTime.text = fmt.format(Date(alert.timestampMs))
                tvTitle.text = alert.title
                tvRegions.text = alert.regions.joinToString(", ")
                tvDescription.text = alert.description
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }
}
