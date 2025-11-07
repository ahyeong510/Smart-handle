package com.example.smart_handle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.smart_handle.databinding.ActivityMainBinding
import com.example.smart_handle.ui.main.DeviceFragment
import com.example.smart_handle.ui.main.NavigationFragment
import com.example.smart_handle.ui.main.SettingFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 앱 처음 실행 시 네비게이션 탭부터 표시
        replaceFragment(NavigationFragment())
        binding.topTitle.text = "Bike Navi"

        // 하단 탭 선택 리스너
        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.menu_navigation -> {
                    replaceFragment(NavigationFragment())
                    binding.topTitle.text = "Bike Navi"
                }
                R.id.menu_device -> {
                    replaceFragment(DeviceFragment())
                    binding.topTitle.text = "Device"
                }
                R.id.menu_setting -> {
                    replaceFragment(SettingFragment())
                    binding.topTitle.text = "Setting"
                }
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
