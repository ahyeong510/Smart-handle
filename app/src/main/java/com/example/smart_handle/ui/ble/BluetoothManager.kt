package com.example.smart_handle.ui.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

object BluetoothManager {

    interface Listener {
        fun onLog(msg: String) {}
        fun onStateChanged(connected: Boolean, deviceName: String?) {}
        fun onReadyToWrite(ready: Boolean) {}
    }

    var listener: Listener? = null

    private lateinit var ctx: Context
    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private val bluetoothManager by lazy {
        ctx.getSystemService(BluetoothManager::class.java)
    }
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private val SERVICE_UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val CHAR_UUID =
        UUID.fromString("abcd1234-1234-1234-1234-abcdef123456")

    private val targetNames = setOf("BikeHandle", "ESP32 Haptic", "SmartHandle")

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            listener?.onStateChanged(false, null)
            listener?.onLog("❌ 블루투스 꺼져 있음")
            return
        }

        listener?.onLog("🔍 스캔 시작")

        try {
            a.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        a.bluetoothLeScanner?.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: return

            if (name in targetNames) {
                listener?.onLog("✨ 발견 → 연결 시도: $name")

                try {
                    adapter?.bluetoothLeScanner?.stopScan(this)
                } catch (_: Exception) {}

                gatt = device.connectGatt(ctx, false, gattCallback)
                listener?.onStateChanged(false, name)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener?.onLog("✅ 연결됨 → 서비스 검색")
                listener?.onStateChanged(true, gatt.device?.name)
                gatt.discoverServices()
            } else {
                listener?.onLog("❌ 연결 해제됨")
                writeChar = null
                listener?.onReadyToWrite(false)
                listener?.onStateChanged(false, gatt.device?.name)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener?.onLog("⚠ SERVICE 없음")
                listener?.onReadyToWrite(false)
                return
            }

            val char = service.getCharacteristic(CHAR_UUID)
            if (char == null) {
                listener?.onLog("⚠ CHAR 없음")
                listener?.onReadyToWrite(false)
                return
            }

            writeChar = char
            listener?.onReadyToWrite(true)
            listener?.onLog("📡 Characteristic 준비됨")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendText(text: String): Boolean {
        val ch = writeChar ?: return false
        val g = gatt ?: return false

        return try {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = text.toByteArray()

            val ok = g.writeCharacteristic(ch)
            listener?.onLog("📤 전송 \"$text\" → $ok")
            ok
        } catch (e: Exception) {
            listener?.onLog("❌ 전송 실패: ${e.message}")
            false
        }
    }
}
