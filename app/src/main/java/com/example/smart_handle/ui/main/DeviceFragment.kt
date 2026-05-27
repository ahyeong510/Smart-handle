package com.example.smart_handle.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

    private lateinit var btnL25: Button
    private lateinit var btnR25: Button
    private lateinit var btnLC50: Button
    private lateinit var btnLC25: Button
    private lateinit var btnRC50: Button
    private lateinit var btnRC25: Button

    private var isConnected = false
    private var readyToWrite = false
    private var connectedName: String? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }

            if (granted) {
                startConnectFlow()
            } else {
                setStatus("⚠️ 권한 거부됨\n앱 설정에서 근처 기기/위치 권한을 허용해줘")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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

        btnL25 = view.findViewById(R.id.btnL25)
        btnR25 = view.findViewById(R.id.btnR25)
        btnLC50 = view.findViewById(R.id.btnLC50)
        btnLC25 = view.findViewById(R.id.btnLC25)
        btnRC50 = view.findViewById(R.id.btnRC50)
        btnRC25 = view.findViewById(R.id.btnRC25)

        btnLeft.isEnabled = false
        btnRight.isEnabled = false
        setLedButtonsEnabled(false)

        setStatus("기기 연결 안됨 ✖")

        BluetoothManager.attachListener(this)

        btnConnect.setOnClickListener {
            if (!isConnected) {
                ensurePermissions()
            } else {
                setStatus("이미 연결됨")
            }
        }

        btnLeft.setOnClickListener {
            sendCommand("L")
        }

        btnRight.setOnClickListener {
            sendCommand("R")
        }

        btnL25.setOnClickListener {
            sendCommand("L25")
        }

        btnR25.setOnClickListener {
            sendCommand("R25")
        }

        btnLC50.setOnClickListener {
            sendCommand("LC50")
        }

        btnLC25.setOnClickListener {
            sendCommand("LC25")
        }

        btnRC50.setOnClickListener {
            sendCommand("RC50")
        }

        btnRC25.setOnClickListener {
            sendCommand("RC25")
        }
    }

    private fun sendCommand(command: String) {
        if (!readyToWrite) {
            setStatus("⚠️ 아직 전송 준비 안됨")
            return
        }

        val ok = BluetoothManager.sendText(command)

        if (ok) {
            setStatus("📤 $command 전송됨")
        } else {
            setStatus("⚠️ $command 전송 실패")
        }
    }

    private fun setLedButtonsEnabled(enabled: Boolean) {
        btnL25.isEnabled = enabled
        btnR25.isEnabled = enabled
        btnLC50.isEnabled = enabled
        btnLC25.isEnabled = enabled
        btnRC50.isEnabled = enabled
        btnRC25.isEnabled = enabled
    }

    private fun ensurePermissions() {
        val needs = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.BLUETOOTH_SCAN
            }

            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.BLUETOOTH_CONNECT
            }

            // 삼성/일부 기기 BLE 스캔 안정화용으로 위치 권한도 같이 요청
            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.ACCESS_FINE_LOCATION
            }

            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.ACCESS_COARSE_LOCATION
            }

        } else {
            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.ACCESS_FINE_LOCATION
            }

            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.ACCESS_COARSE_LOCATION
            }
        }

        if (needs.isEmpty()) {
            startConnectFlow()
        } else {
            permLauncher.launch(needs.toTypedArray())
        }
    }

    private fun startConnectFlow() {
        if (!isLocationEnabled()) {
            setStatus("⚠️ 위치 서비스가 꺼져 있음\n휴대폰 위치를 켠 뒤 다시 연결해줘")
            return
        }

        setStatus("🔍 기기 검색 중…")
        BluetoothManager.startScanAndConnect()
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val mode = Settings.Secure.getInt(
                requireContext().contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        } catch (_: Exception) {
            true
        }
    }

    override fun onStateChanged(
        connected: Boolean,
        deviceName: String?
    ) {
        isConnected = connected
        connectedName = deviceName

        if (!isAdded || view == null) return

        activity?.runOnUiThread {
            if (!isAdded || view == null) return@runOnUiThread

            if (connected) {
                btnConnect.text = "연결됨"
                textStatus.text = "${deviceName ?: "기기"}\n연결됨"
            } else {
                btnConnect.text = "기기 연결"
                textStatus.text = "연결 끊김"

                btnLeft.isEnabled = false
                btnRight.isEnabled = false
                setLedButtonsEnabled(false)
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
            setLedButtonsEnabled(ready)

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
            setStatus(msg)
        }
    }

    private fun setStatus(msg: String) {
        val prefix =
            if (isConnected) {
                "${connectedName ?: "기기"}\n"
            } else {
                ""
            }

        textStatus.text = prefix + msg
    }

    override fun onDestroyView() {
        super.onDestroyView()
        BluetoothManager.attachListener(null)
    }
}