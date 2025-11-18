package com.example.smart_handle.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.smart_handle.R
import com.example.smart_handle.ui.ble.BluetoothManager

class DeviceFragment : Fragment(), BluetoothManager.Listener {

    private lateinit var textStatus: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button

    private var readyToWrite = false
    private var isConnected = false
    private var connectedName: String? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) startConnectFlow()
            else setStatus("⚠️ 권한 거부됨")
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_device, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textStatus = view.findViewById(R.id.textStatus)
        btnConnect = view.findViewById(R.id.btnConnect)
        btnLeft = view.findViewById(R.id.btnLeft)
        btnRight = view.findViewById(R.id.btnRight)

        // 🔥 이 화면이 BLE 상태를 받도록 연결
        BluetoothManager.listener = this

        btnConnect.setOnClickListener {
            if (isConnected) {
                setStatus("연결 유지됨 (주행에서 사용)")
            } else {
                ensurePermissions()
            }
        }

        btnLeft.setOnClickListener {
            if (readyToWrite) BluetoothManager.sendText("L")
            setStatus("📤 L 전송됨")
        }

        btnRight.setOnClickListener {
            if (readyToWrite) BluetoothManager.sendText("R")
            setStatus("📤 R 전송됨")
        }
    }

    private fun ensurePermissions() {
        val needs = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.BLUETOOTH_SCAN

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needs += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (needs.isEmpty()) startConnectFlow()
        else permLauncher.launch(needs.toTypedArray())
    }

    private fun startConnectFlow() {
        setStatus("🔍 기기 검색 중…")
        BluetoothManager.startScanAndConnect()
    }

    override fun onStateChanged(connected: Boolean, deviceName: String?) {
        isConnected = connected
        connectedName = deviceName

        requireActivity().runOnUiThread {
            if (connected) {
                btnConnect.text = "연결됨"
                textStatus.text = "${deviceName ?: "기기"}\n연결됨"
            } else {
                btnConnect.text = "기기 연결"
                textStatus.text = "연결 끊김"
            }
        }
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
        if (ready) setStatus("📡 전송 준비됨")
    }

    override fun onLog(msg: String) {
        // 필요하면 로그 출력
    }

    private fun setStatus(msg: String) {
        val name = if (isConnected) "${connectedName ?: "기기"}\n" else ""
        textStatus.text = name + msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🔥 BLE를 끊으면 안 됨
        BluetoothManager.listener = null
    }
}
