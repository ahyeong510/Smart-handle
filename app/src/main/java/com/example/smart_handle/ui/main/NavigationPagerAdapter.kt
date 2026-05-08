package com.example.smart_handle.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class NavigationPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FindRouteFragment()       // 길찾기
            1 -> FitnessRouteFragment()    // 운동경로
            2 -> TourRouteFragment()      // 관광코스
            else -> FindRouteFragment()
        }
    }
}
