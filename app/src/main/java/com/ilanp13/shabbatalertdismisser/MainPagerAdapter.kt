package com.ilanp13.shabbatalertdismisser

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> StatusFragment()
            1 -> CalendarFragment()
            2 -> HistoryFragment()
            3 -> MapFragment()
            4 -> AlertsFragment()
            else -> StatusFragment()
        }
    }
}
