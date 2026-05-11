package com.example.smart_handle.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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

    // LED
    private lateinit var spinnerLedDirection: Spinner
    private lateinit var spinnerLedCount: Spinner
    private lateinit var btnLedSend: Button

    private var isConnected = false
    private var readyToWrite = false
    private var connectedName: String? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }

            if (granted) {
                startConnectFlow()
            } else {
                setStatus("⚠️ 권한 거부됨")
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

        spinnerLedDirection = view.findViewById(R.id.spinnerLedDirection)
        spinnerLedCount = view.findViewById(R.id.spinnerLedCount)
        btnLedSend = view.findViewById(R.id.btnLedSend)

        btnLeft.isEnabled = false
        btnRight.isEnabled = false
        btnLedSend.isEnabled = false

        setStatus("기기 연결 안됨 ✖")

        BluetoothManager.attachListener(this)

        setupLedUi()

        btnConnect.setOnClickListener {
            if (!isConnected) {
                ensurePermissions()
            } else {
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

        btnLedSend.setOnClickListener {

            if (!readyToWrite) {
                setStatus("⚠️ 아직 전송 준비 안됨")
                return@setOnClickListener
            }

            val direction = spinnerLedDirection.selectedItem.toString()
            val count = spinnerLedCount.selectedItem.toString()

            val command = "$direction,$count\n"

            val ok = BluetoothManager.sendText(command)

            if (ok) {
                setStatus("📤 LED 전송: $direction,$count")
            } else {
                setStatus("⚠️ LED 전송 실패")
            }
        }
    }

    private fun setupLedUi() {

        val directions = listOf("L", "R", "S")

        val directionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            directions
        )

        spinnerLedDirection.adapter = directionAdapter

        updateCountSpinner("L")

        spinnerLedDirection.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selected = directions[position]

                    updateCountSpinner(selected)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun updateCountSpinner(direction: String) {

        val counts = if (direction == "S") {
            listOf("0")
        } else {
            listOf("1", "2", "3")
        }

        val countAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            counts
        )

        spinnerLedCount.adapter = countAdapter
    }

    /** 권한 체크 */
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

        } else {

            if (
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needs += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }

        if (needs.isEmpty()) {
            startConnectFlow()
        } else {
            permLauncher.launch(needs.toTypedArray())
        }
    }

    /** 스캔 + 연결 */
    private fun startConnectFlow() {

        setStatus("🔍 기기 검색 중…")

        BluetoothManager.startScanAndConnect()
    }

    // ======================================================
    // BLE Listener
    // ======================================================

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

                textStatus.text =
                    "${deviceName ?: "기기"}\n연결됨"

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
            btnLedSend.isEnabled = ready

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

            // 필요 시 로그 출력 가능
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