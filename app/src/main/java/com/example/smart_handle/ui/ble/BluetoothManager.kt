package com.example.smart_handle.ui.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * BLE 매니저
 * - "BikeHandle" 같은 ESP32 광고 이름을 스캔해서 자동 연결
 * - GATT 연결 후 write characteristic까지 찾아 저장
 * - 연결 상태 / 로그를 listener로 UI에 전달
 */
class BluetoothManager(private val context: Context) {

    interface Listener {
        fun onLog(msg: String) {}
        fun onStateChanged(connected: Boolean, deviceName: String?)
        fun onReadyToWrite(ready: Boolean)
    }

    var listener: Listener? = null

    private val bluetoothManager: android.bluetooth.BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null

    // 🔷 ESP32에서 설정한 Service / Characteristic UUID
    //    → ESP32 코드와 반드시 동일해야 함
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val CHAR_UUID = UUID.fromString("abcd1234-1234-1234-1234-abcdef123456")

    // 🔷 스캔할 때 허용할 기기 이름들
    private val targetNames = setOf("BikeHandle", "ESP32 Haptic", "SmartHandle")

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        if (adapter == null || adapter?.isEnabled != true) {
            listener?.onLog("❌ 블루투스 비활성화됨")
            listener?.onStateChanged(false, null)
            return
        }

        listener?.onLog("🔍 스캔 시작")
        try {
            adapter!!.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        adapter!!.bluetoothLeScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        listener?.onLog("🔌 연결 해제 시도")

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}

        gatt = null
        writeChar = null

        listener?.onReadyToWrite(false)
        listener?.onStateChanged(false, null)
    }

    /**
     * ✅ ESP32로 문자열 전송 ("L", "R" 명령)
     * - Android 13 이상에서 안정적으로 작동하도록 수정
     * - 연결 끊김 방지 (WRITE_TYPE_NO_RESPONSE 사용)
     */
    @SuppressLint("MissingPermission")
    fun sendText(text: String): Boolean {
        val ch = writeChar ?: run {
            listener?.onLog("⚠️ writeChar 아직 준비 안됨")
            return false
        }
        val g = gatt ?: run {
            listener?.onLog("⚠️ gatt 연결 안됨")
            return false
        }

        try {
            // ✅ 끊김 방지 핵심
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = text.toByteArray(Charsets.UTF_8)

            val ok = g.writeCharacteristic(ch)
            listener?.onLog("📤 write(\"$text\") -> $ok")

            if (!ok) {
                listener?.onLog("⚠️ 전송 실패 (다시 시도 필요)")
            }

            return ok
        } catch (e: Exception) {
            listener?.onLog("❌ 전송 중 오류: ${e.message}")
            return false
        }
    }

    // ===================== 내부 콜백 =====================

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: return

            if (name in targetNames) {
                listener?.onLog("✅ 대상 발견: $name → 연결 시도 중")
                try {
                    adapter?.bluetoothLeScanner?.stopScan(this)
                } catch (_: Exception) {}

                gatt = device.connectGatt(context, false, gattCallback)
                listener?.onStateChanged(false, name)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            listener?.onLog("❌ 스캔 실패 code=$errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                listener?.onLog("✅ GATT 연결됨 → 서비스 검색 중")
                listener?.onStateChanged(true, gatt.device?.name)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                listener?.onLog("❌ GATT 연결 해제됨")
                writeChar = null
                listener?.onReadyToWrite(false)
                listener?.onStateChanged(false, gatt.device?.name)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                listener?.onLog("⚠️ SERVICE_UUID ($SERVICE_UUID) 없음")
                listener?.onReadyToWrite(false)
                return
            }

            val ch = service.getCharacteristic(CHAR_UUID)
            if (ch == null) {
                listener?.onLog("⚠️ CHAR_UUID ($CHAR_UUID) 없음")
                listener?.onReadyToWrite(false)
                return
            }

            writeChar = ch
            listener?.onLog("📡 write Characteristic 준비 완료")
            listener?.onReadyToWrite(true)
        }
    }
}
