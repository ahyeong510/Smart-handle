package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.smart_handle.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class NavigationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_navigation, container, false)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val adapter = NavigationPagerAdapter(requireActivity())
        viewPager.adapter = adapter

        // ✅ EditText 포커스 방해 방지 (필수)
        viewPager.isUserInputEnabled = true  // false 로 바꾸면 스와이프 완전히 막힘
        viewPager.offscreenPageLimit = 3     // 모든 탭 미리 로드 (EditText 안정화)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "길찾기"
                1 -> tab.text = "운동 경로"
                2 -> tab.text = "관광지 코스"
            }
        }.attach()

        return view
    }
}
