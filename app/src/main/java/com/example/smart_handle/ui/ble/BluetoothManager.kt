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
import android.bluetooth.BluetoothManager as SystemBluetoothManager

object BluetoothManager {

    interface Listener {
        fun onLog(msg: String) {}
        fun onStateChanged(connected: Boolean, deviceName: String?) {}
        fun onReadyToWrite(ready: Boolean) {}
    }

    private var listener: Listener? = null
    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private val bluetoothManager: SystemBluetoothManager? by lazy {
        ctx.getSystemService(SystemBluetoothManager::class.java)
    }

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    private var isConnected = false
    private var connectedDeviceName: String? = null
    private var isReadyToWrite = false

    private val SERVICE_UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abc")

    private val CHAR_UUID =
        UUID.fromString("abcd1234-1234-1234-1234-abcdef123456")

    private const val TARGET_ADDRESS = "1C:C3:AB:D1:48:46"

    fun attachListener(newListener: Listener?) {
        listener = newListener
        newListener?.onStateChanged(isConnected, connectedDeviceName)
        newListener?.onReadyToWrite(isReadyToWrite)
    }

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val a = adapter

        if (a == null || !a.isEnabled) {
            listener?.onLog("❌ 블루투스 OFF")
            listener?.onStateChanged(false, null)
            return
        }

        listener?.onLog("🔗 MAC 주소로 직접 연결 시도\n$TARGET_ADDRESS")

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null
        writeChar = null
        isReadyToWrite = false
        listener?.onReadyToWrite(false)

        try {
            val device = a.getRemoteDevice(TARGET_ADDRESS)
            connectedDeviceName = "SmartHandle"

            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(ctx, false, gattCallback)
            }

            listener?.onLog("🔗 connectGatt 호출 완료")
        } catch (e: Exception) {
            listener?.onLog("❌ 직접 연결 실패: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null
        writeChar = null
        isConnected = false
        connectedDeviceName = null
        isReadyToWrite = false

        listener?.onReadyToWrite(false)
        listener?.onStateChanged(false, null)
        listener?.onLog("🔌 연결 해제됨")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            listener?.onLog("상태변경 status=$status newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                isConnected = false
                isReadyToWrite = false
                writeChar = null

                listener?.onReadyToWrite(false)
                listener?.onStateChanged(false, connectedDeviceName)
                listener?.onLog("❌ GATT 오류 status=$status")

                try {
                    gatt.close()
                } catch (_: Exception) {}

                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                connectedDeviceName = "SmartHandle"
                this@BluetoothManager.gatt = gatt

                listener?.onStateChanged(true, connectedDeviceName)
                listener?.onLog("✅ GATT 연결됨 → 서비스 검색")

                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                isReadyToWrite = false
                writeChar = null

                listener?.onReadyToWrite(false)
                listener?.onStateChanged(false, connectedDeviceName)
                listener?.onLog("❌ 연결 해제됨")

                try {
                    gatt.close()
                } catch (_: Exception) {}
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            listener?.onLog("서비스 검색 결과 status=$status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                listener?.onLog("⚠ 서비스 검색 실패")
                return
            }

            val service = gatt.getService(SERVICE_UUID)

            if (service == null) {
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                listener?.onLog("⚠ SERVICE 없음")
                return
            }

            val char = service.getCharacteristic(CHAR_UUID)

            if (char == null) {
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                listener?.onLog("⚠ CHARACTERISTIC 없음")
                return
            }

            writeChar = char
            isReadyToWrite = true

            listener?.onLog("📡 Characteristic 준비됨")
            listener?.onReadyToWrite(true)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendText(text: String): Boolean {
        val ch = writeChar ?: run {
            listener?.onLog("⚠ writeChar 아직 준비 안됨")
            return false
        }

        val g = gatt ?: run {
            listener?.onLog("⚠ gatt 연결 안됨")
            return false
        }

        return try {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = text.toByteArray()

            val ok = g.writeCharacteristic(ch)
            listener?.onLog("📤 $text → $ok")

            ok
        } catch (e: Exception) {
            listener?.onLog("❌ 전송 실패: ${e.message}")
            false
        }
    }
}