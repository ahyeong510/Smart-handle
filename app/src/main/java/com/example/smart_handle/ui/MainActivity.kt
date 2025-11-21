package com.example.smart_handle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.smart_handle.databinding.ActivityMainBinding
import com.example.smart_handle.ui.ble.BluetoothManager
import com.example.smart_handle.ui.main.DeviceFragment
import com.example.smart_handle.ui.main.NavigationFragment
import com.example.smart_handle.ui.main.SettingFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔥 BLE 싱글톤 초기화
        BluetoothManager.init(this)

        // ✅ 액티비티가 "처음" 만들어질 때만 기본 프래그먼트 넣기
        if (savedInstanceState == null) {
            replaceFragment(NavigationFragment())
            binding.bottomNavigation.selectedItemId = R.id.menu_navigation
            binding.topTitle.text = "Bike Navi"
        }

        // 하단 탭 선택 리스너
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {

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
