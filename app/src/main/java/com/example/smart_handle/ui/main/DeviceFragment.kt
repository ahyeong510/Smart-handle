package com.example.smart_handle.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.smart_handle.R

class DeviceFragment : Fragment() {

    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var leftTestButton: Button
    private lateinit var rightTestButton: Button

    private var isConnected = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_device, container, false)

        statusText = view.findViewById(R.id.statusText)
        connectButton = view.findViewById(R.id.connectButton)
        leftTestButton = view.findViewById(R.id.leftMotorButton)
        rightTestButton = view.findViewById(R.id.rightMotorButton)

        updateConnectionUI()

        connectButton.setOnClickListener {
            isConnected = !isConnected
            updateConnectionUI()
        }

        leftTestButton.setOnClickListener {
            if (isConnected) statusText.text = "왼쪽 모터 테스트 중..."
            else statusText.text = "먼저 기기를 연결해주세요."
        }

        rightTestButton.setOnClickListener {
            if (isConnected) statusText.text = "오른쪽 모터 테스트 중..."
            else statusText.text = "먼저 기기를 연결해주세요."
        }

        return view
    }

    private fun updateConnectionUI() {
        if (isConnected) {
            statusText.text = "ESP32 Haptic\n연결됨 ✅"
            connectButton.text = "연결 해제"
        } else {
            statusText.text = "기기 연결 안됨 ❌"
            connectButton.text = "기기 연결"
        }
    }
}
