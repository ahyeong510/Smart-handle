package com.example.bike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bike.ui.device.DeviceScreen

import com.example.bike.ui.theme.BikeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BikeTheme {
                // 방금 만든 디바이스 화면을 바로 띄웁니다.
                DeviceScreen()
            }
        }
    }
}
