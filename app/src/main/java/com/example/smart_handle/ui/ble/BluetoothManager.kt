package com.example.smart_handle.ui.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * 🔥 앱 전체에서 하나만 존재하는 BLE 싱글톤 매니저
 * - 화면(fragment/activity)이 바뀌어도 연결 유지
 * - listener만 교체해서 UI 업데이트
 */
object BluetoothManager {

    // ======================
    // Listener (UI가 교체됨)
    // ======================
    interface Listener {
        fun onLog(msg: String) {}
        fun onStateChanged(connected: Boolean, deviceName: String?) {}
        fun onReadyToWrite(ready: Boolean) {}
    }

    private var listener: Listener? = null

    // ======================
    //  Context 저장
    // ======================
    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    // ======================
    // BLE 헤더
    // ======================
    private val bluetoothManager: SystemBluetoothManager? by lazy {
        ctx.getSystemService(SystemBluetoothManager::class.java)
    }

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    // ✅ 연결 상태 저장 (탭 이동 후에도 사용)
    private var isConnected: Boolean = false
    private var connectedDeviceName: String? = null
    private var isReadyToWrite: Boolean = false

    // ======================
    // UUID
    // ======================
    private val SERVICE_UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abc")

    private val CHAR_UUID =
        UUID.fromString("abcd1234-1234-1234-1234-abcdef123456")

    private val targetNames = setOf("BikeHandle", "ESP32 Haptic", "SmartHandle")

    // ======================
    // listener 붙일 때 현재 상태를 즉시 UI에 전달
    // ======================
    fun attachListener(newListener: Listener?) {
        listener = newListener

        if (newListener != null) {
            newListener.onStateChanged(isConnected, connectedDeviceName)
            newListener.onReadyToWrite(isReadyToWrite)
        }
    }

    // ======================
    // 권한 체크
    // ======================
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

    // ======================
    // 스캔 시작
    // ======================
    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            listener?.onLog("❌ 블루투스 OFF")
            listener?.onStateChanged(false, null)
            return
        }

        listener?.onLog("🔍 스캔 시작")

        try {
            a.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        try {
            a.bluetoothLeScanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            listener?.onLog("❌ 스캔 실패(권한): ${e.message}")
        } catch (e: Exception) {
            listener?.onLog("❌ 스캔 실패: ${e.message}")
        }
    }

    // ======================
    // 연결 해제
    // ======================
    @SuppressLint("MissingPermission")
    fun disconnect() {
        listener?.onLog("🔌 연결 해제 시도")

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
    }

    // ======================
    // 스캔 콜백
    // ======================
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {

            val device: BluetoothDevice = result?.device ?: return
            val name: String = device.name ?: return

            // 디버깅용: 스캔되는 기기 전부 로그
            listener?.onLog("👀 스캔: $name (${device.address})")

            if (name in targetNames) {

                listener?.onLog("✨ 발견: $name → 연결중")

                try {
                    adapter?.bluetoothLeScanner?.stopScan(this)
                } catch (_: Exception) {}

                gatt = device.connectGatt(ctx, false, gattCallback)
                // 실제 연결 여부는 onConnectionStateChange 에서 처리
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onLog("❌ 스캔 실패: $errorCode")
        }
    }

    // ======================
    // GATT Callback
    // ======================
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                connectedDeviceName = gatt.device?.name

                listener?.onLog("✅ GATT 연결됨 → 서비스 검색")
                listener?.onStateChanged(true, connectedDeviceName)

                gatt.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                isReadyToWrite = false
                writeChar = null

                listener?.onLog("❌ 연결 해제됨")
                listener?.onReadyToWrite(false)
                listener?.onStateChanged(false, connectedDeviceName)

                connectedDeviceName = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onLog("⚠ 서비스 검색 실패: $status")
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener?.onLog("⚠ SERVICE 없음: $SERVICE_UUID")
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                return
            }

            val char = service.getCharacteristic(CHAR_UUID)
            if (char == null) {
                listener?.onLog("⚠ CHARACTERISTIC 없음: $CHAR_UUID")
                isReadyToWrite = false
                listener?.onReadyToWrite(false)
                return
            }

            writeChar = char
            isReadyToWrite = true
            listener?.onReadyToWrite(true)
            listener?.onLog("📡 Characteristic 준비됨")
        }
    }

    // ======================
    // 데이터 전송
    // ======================
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
            listener?.onLog("📤 \"$text\" → $ok")

            if (!ok) {
                listener?.onLog("⚠ 전송 실패 (다시 시도 필요)")
            }

            ok
        } catch (e: Exception) {
            listener?.onLog("❌ 전송 실패: ${e.message}")
            false
        }
    }

}

