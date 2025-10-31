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

    private var ble: BluetoothManager? = null
    private var isConnected = false
    private var readyToWrite = false
    private var connectedName: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
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

        ble = BluetoothManager(requireContext()).also { it.listener = this }

        btnConnect.setOnClickListener {
            if (isConnected) {
                ble?.disconnect()
                setUiDisconnected()
            } else {
                ensurePermissionsAndConnect()
            }
        }

        btnLeft.setOnClickListener {
            if (readyToWrite) {
                ble?.sendText("L")
                setStatus("📤 L 전송됨")
            } else setStatus("⚠️ 연결/서비스 준비 필요")
        }

        btnRight.setOnClickListener {
            if (readyToWrite) {
                ble?.sendText("R")
                setStatus("📤 R 전송됨")
            } else setStatus("⚠️ 연결/서비스 준비 필요")
        }

        setUiDisconnected()
    }

    private fun ensurePermissionsAndConnect() {
        val needs = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needs += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needs += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needs += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (needs.isNotEmpty()) permissionLauncher.launch(needs.toTypedArray())
        else startConnectFlow()
    }

    private fun startConnectFlow() {
        setStatus("🔍 기기 검색 중…")
        ble?.startScanAndConnect()
    }

    // ===== BluetoothManager.Listener =====
    override fun onLog(msg: String) {
        // 필요시 Logcat에 출력하거나 textStatus 누적 표시
    }

    override fun onStateChanged(connected: Boolean, deviceName: String?) {
        isConnected = connected
        connectedName = deviceName
        if (connected) {
            requireActivity().runOnUiThread {
                btnConnect.text = "연결 해제"
                val name = deviceName ?: "ESP32"
                textStatus.text = "$name\n연결됨 ✅"
            }
        } else {
            requireActivity().runOnUiThread { setUiDisconnected() }
        }
    }

    override fun onReadyToWrite(ready: Boolean) {
        readyToWrite = ready
        if (ready) setStatus("📡 BLE 서비스 준비 완료")
    }

    private fun setUiDisconnected() {
        btnConnect.text = "기기 연결"
        readyToWrite = false
        setStatus("기기 연결 안됨 ✖")
    }

    private fun setStatus(msg: String) {
        val name = if (isConnected) (connectedName ?: "ESP32") + "\n" else ""
        textStatus.text = name + msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ble?.disconnect()
        ble = null
    }
}
