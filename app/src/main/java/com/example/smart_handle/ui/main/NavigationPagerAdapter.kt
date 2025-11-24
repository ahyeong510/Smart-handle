package com.example.smart_handle.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.bikenavi.ui.TourFragment
import com.example.smart_handle.ui.main.FitnessRouteFragment
import com.example.smart_handle.ui.main.FindRouteFragment


class NavigationPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FindRouteFragment()       // 길찾기
            1 -> FitnessRouteFragment()    // 운동경로
            2 -> TourFragment()       // 관광코스
            else -> FindRouteFragment()
        }
    }
}
