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

    private var isConnected = false
    private var readyToWrite = false
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

        // 처음 들어왔을 때 기본 상태
        btnLeft.isEnabled = false
        btnRight.isEnabled = false
        setStatus("기기 연결 안됨 ✖")

        // 🔥 BLE listener 연결 (싱글톤 상태를 바로 UI에 반영)
        //  - BluetoothManager.attachListener 안에서
        //    현재 연결 상태 / readyToWrite 상태를 즉시 한 번 호출해 줌
        BluetoothManager.attachListener(this)

        btnConnect.setOnClickListener {
            if (!isConnected) {
                ensurePermissions()
            } else {
                // 연결 된 상태에서 눌러도 BLE를 끊지 않음
                setStatus("이미 연결됨")
            }
        }

        btnLeft.setOnClickListener {
            if (readyToWrite) {
                BluetoothManager.sendText("L")
                setStatus("📤 L 전송됨")
            } else {
                setStatus("⚠️ 아직 전송 준비 안됨")
            }
        }

        btnRight.setOnClickListener {
            if (readyToWrite) {
                BluetoothManager.sendText("R")
                setStatus("📤 R 전송됨")
            } else {
                setStatus("⚠️ 아직 전송 준비 안됨")
            }
        }
    }

    /** 🔥 권한 체크 */
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

    /** 🔥 스캔 + 연결 시작 */
    private fun startConnectFlow() {
        setStatus("🔍 기기 검색 중…")
        BluetoothManager.startScanAndConnect()
    }

    // ======================================================
    // BLE Listener 콜백
    // ======================================================

    override fun onStateChanged(connected: Boolean, deviceName: String?) {
        isConnected = connected
        connectedName = deviceName

        // 🔐 프래그먼트/뷰가 살아 있을 때만 UI 건드리기
        if (!isAdded || view == null) return

        activity?.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread

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

        if (!isAdded || view == null) return

        activity?.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread

            btnLeft.isEnabled = ready
            btnRight.isEnabled = ready

            if (ready) {
                setStatus("📡 전송 준비됨")
            } else {
                setStatus("전송 불가")
            }
        }
    }

    override fun onLog(msg: String) {
        if (!isAdded || view == null) return

        activity?.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread
            // 필요하면 textStatus 나 별도 로그뷰에 추가해서 디버깅
            // textStatus.append("\n$msg")
        }
    }

    private fun setStatus(msg: String) {
        val prefix = if (isConnected) "${connectedName ?: "기기"}\n" else ""
        textStatus.text = prefix + msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🔥 연결은 유지하고, 화면만 listener 해제
        BluetoothManager.attachListener(null)
    }
}
