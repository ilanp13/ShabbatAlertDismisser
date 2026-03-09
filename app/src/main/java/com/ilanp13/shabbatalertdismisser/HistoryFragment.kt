package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvHistoryCount: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var adapter: HistoryAdapter

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        recyclerView = view.findViewById(R.id.recyclerHistory)
        tvHistoryCount = view.findViewById(R.id.tvHistoryCount)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        btnClearHistory = view.findViewById(R.id.btnClearHistory)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter()
        recyclerView.adapter = adapter

        btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        try {
            val historyJson = prefs.getString("dismiss_history", "[]") ?: "[]"
            val array = JSONArray(historyJson)

            val records = mutableListOf<DismissRecord>()
            for (i in (array.length() - 1) downTo 0) {
                val obj = array.getJSONObject(i)
                records.add(DismissRecord(
                    timestampMs = obj.getLong("timestampMs"),
                    packageName = obj.getString("packageName"),
                    buttonText = obj.getString("buttonText"),
                    windowText = obj.getString("windowText")
                ))
            }

            adapter.submitList(records)
            updateCountDisplay(records.size)

            recyclerView.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
            tvEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            adapter.submitList(emptyList())
            updateCountDisplay(0)
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        }
    }

    private fun updateCountDisplay(count: Int) {
        tvHistoryCount.text = if (count == 0) {
            getString(R.string.history_no_dismissals)
        } else {
            getString(R.string.history_summary_format, count)
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.clear_history_title))
            .setMessage(getString(R.string.clear_history_message))
            .setPositiveButton(getString(R.string.clear_history_confirm)) { _, _ ->
                clearHistory()
            }
            .setNegativeButton(getString(R.string.clear_history_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun clearHistory() {
        prefs.edit().putString("dismiss_history", "[]").apply()
        loadHistory()
    }

    inner class HistoryAdapter : androidx.recyclerview.widget.ListAdapter<DismissRecord, HistoryAdapter.ViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<DismissRecord>() {
            override fun areItemsTheSame(oldItem: DismissRecord, newItem: DismissRecord) =
                oldItem.timestampMs == newItem.timestampMs

            override fun areContentsTheSame(oldItem: DismissRecord, newItem: DismissRecord) =
                oldItem == newItem
        }
    ) {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            private val tvPackage: TextView = itemView.findViewById(R.id.tvPackage)
            private val tvButton: TextView = itemView.findViewById(R.id.tvButton)
            private val tvWindowText: TextView = itemView.findViewById(R.id.tvWindowText)

            fun bind(record: DismissRecord) {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                tvTime.text = fmt.format(Date(record.timestampMs))
                tvPackage.text = getPackageDisplayName(record.packageName)
                tvButton.text = record.buttonText
                tvWindowText.text = record.windowText
            }

            private fun getPackageDisplayName(packageName: String): String {
                return when {
                    packageName.contains("android.cellbroadcast") -> "AOSP"
                    packageName.contains("samsung") -> "Samsung"
                    packageName.contains("google") -> "Google"
                    else -> packageName.substringAfterLast(".").take(20)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }
}
